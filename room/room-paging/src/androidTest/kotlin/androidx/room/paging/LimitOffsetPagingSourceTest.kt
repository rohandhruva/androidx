/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.room.paging

import android.database.Cursor
import androidx.arch.core.executor.testing.CountingTaskExecutorRule
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomSQLiteQuery
import androidx.room.util.CursorUtil
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val tableName: String = "TestItem"

@RunWith(AndroidJUnit4::class)
@SmallTest
class LimitOffsetPagingSourceTest {

    @JvmField
    @Rule
    val countingTaskExecutorRule = CountingTaskExecutorRule()

    private lateinit var database: LimitOffsetTestDb
    private lateinit var dao: TestItemDao
    private val itemsList = createItemsForDb(0, 100)

    @Before
    fun init() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LimitOffsetTestDb::class.java,
        ).build()
        dao = database.dao
    }

    @After
    fun tearDown() {
        database.close()
        // At the end of all tests, query executor should be idle (transaction thread released).
        countingTaskExecutorRule.drainTasks(500, TimeUnit.MILLISECONDS)
        assertThat(countingTaskExecutorRule.isIdle).isTrue()
    }

    @Test
    fun test_itemCount() {
        dao.addAllItems(itemsList)
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        runBlocking {
            // count query is executed on first load
            pagingSource.load(
                createLoadParam(LoadType.REFRESH)
            )
            assertThat(pagingSource.itemCount.get()).isEqualTo(100)
        }
    }

    @Test
    fun test_itemCountWithSuppliedLimitOffset() {
        dao.addAllItems(itemsList)
        val pagingSource = LimitOffsetPagingSourceImpl(
            db = database,
            queryString = "SELECT * FROM $tableName ORDER BY id ASC LIMIT 60 OFFSET 30",
        )
        runBlocking {
            // count query is executed on first load
            pagingSource.load(
                createLoadParam(LoadType.REFRESH)
            )
            // should be 60 instead of 100
            assertThat(pagingSource.itemCount.get()).isEqualTo(60)
        }
    }

    @Test
    fun dbInsert_pagingSourceInvalidates() {
        dao.addAllItems(itemsList)
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        runBlocking {
            // load once to register db observers
            pagingSource.load(createLoadParam(LoadType.REFRESH))
            assertThat(pagingSource.invalid).isFalse()
            // paging source should be invalidated when insert into db
            val result = dao.addTestItem(TestItem(101))
            countingTaskExecutorRule.drainTasks(500, TimeUnit.MILLISECONDS)
            assertThat(result).isEqualTo(101)
            assertTrue(pagingSource.invalid)
        }
    }

    @Test
    fun dbDelete_pagingSourceInvalidates() {
        dao.addAllItems(itemsList)
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        runBlocking {
            // load once to register db observers
            pagingSource.load(createLoadParam(LoadType.REFRESH))
            assertThat(pagingSource.invalid).isFalse()
            // paging source should be invalidated when delete from db
            dao.deleteTestItem(TestItem(50))
            countingTaskExecutorRule.drainTasks(5, TimeUnit.SECONDS)
            assertTrue(pagingSource.invalid)
        }
    }

    @Test
    fun invalidDbQuery_pagingSourceDoesNotInvalidate() {
        dao.addAllItems(itemsList)
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        runBlocking {
            // load once to register db observers
            pagingSource.load(createLoadParam(LoadType.REFRESH))
            assertThat(pagingSource.invalid).isFalse()

            val result = dao.deleteTestItem(TestItem(1000))

            // invalid delete. Should have 0 items deleted and paging source remains valid
            assertThat(result).isEqualTo(0)
            assertFalse(pagingSource.invalid)
        }
    }

    @Test
    fun load_initialLoad() {
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        runBlocking {
            // test empty load
            var result = pagingSource.load(
                createLoadParam(LoadType.REFRESH)
            ) as PagingSource.LoadResult.Page

            assertTrue(result.data.isEmpty())
            // now add data
            dao.addAllItems(itemsList)
            result = pagingSource.load(
                createLoadParam(LoadType.REFRESH)
            ) as PagingSource.LoadResult.Page

            assertThat(result.data).containsExactlyElementsIn(
                itemsList.subList(0, 15)
            )
        }
    }

    @Test
    fun load_initialLoadWithInitialKey() {
        dao.addAllItems(itemsList)
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        // refresh with initial key = 20
        runBlocking {
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.REFRESH,
                    key = 20,
                )
            ) as PagingSource.LoadResult.Page
            // item in pos 21-35 (TestItemId 20-34) loaded
            assertThat(result.data).containsExactlyElementsIn(
                itemsList.subList(20, 35)
            )
        }
    }

    @Test
    fun load_initialLoadWithSuppliedLimitOffset() {
        dao.addAllItems(itemsList)
        val pagingSource = LimitOffsetPagingSourceImpl(
            db = database,
            queryString = "SELECT * FROM $tableName ORDER BY id ASC LIMIT 10 OFFSET 30",
        )
        runBlocking {
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.REFRESH,
                    key = null,
                )
            ) as PagingSource.LoadResult.Page
            // default initial loadSize = 15 starting from index 0.
            // user supplied limit offset should cause initial loadSize = 10, starting from index 30
            assertThat(result.data).containsExactlyElementsIn(
                itemsList.subList(30, 40)
            )
            // check that no append/prepend can be triggered after this terminal load
            assertThat(result.nextKey).isNull()
            assertThat(result.prevKey).isNull()
            assertThat(result.itemsBefore).isEqualTo(0)
            assertThat(result.itemsAfter).isEqualTo(0)
        }
    }

    @Test
    fun load_oneAdditionalQueryArguments() {
        dao.addAllItems(itemsList)
        val pagingSource = LimitOffsetPagingSourceImpl(
            db = database,
            queryString =
                "SELECT * FROM $tableName WHERE id < 50 ORDER BY id ASC",
        )
        // refresh with initial key = 40
        runBlocking {
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.REFRESH,
                    key = 40,
                )
            ) as PagingSource.LoadResult.Page
            // initial loadSize = 15, but limited by id < 50, should only load items 40 - 50
            assertThat(result.data).containsExactlyElementsIn(
                itemsList.subList(40, 50)
            )
            // should have 50 items fulfilling condition of id < 50 (TestItem id 0 - 49)
            assertThat(pagingSource.itemCount.get()).isEqualTo(50)
        }
    }

    @Test
    fun load_multipleQueryArguments() {
        dao.addAllItems(itemsList)
        val pagingSource = LimitOffsetPagingSourceImpl(
            db = database,
            queryString =
                "SELECT * " +
                    "FROM $tableName " +
                    "WHERE id > 50 AND value LIKE 'item 90'" +
                    "ORDER BY id ASC",
        )
        runBlocking {
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.REFRESH,
                    key = null,
                )
            ) as PagingSource.LoadResult.Page
            assertThat(result.data).containsExactly(itemsList[90])
            assertThat(pagingSource.itemCount.get()).isEqualTo(1)
        }
    }

    @Test
    fun load_InvalidUserSuppliedOffset_returnEmpty() {
        dao.addAllItems(itemsList)
        val pagingSource = LimitOffsetPagingSourceImpl(
            db = database,
            queryString = "SELECT * FROM $tableName ORDER BY id ASC LIMIT 10 OFFSET 500",
        )
        runBlocking {
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.REFRESH,
                    key = null,
                )
            ) as PagingSource.LoadResult.Page
            // invalid OFFSET = 500 should return empty data
            assertThat(result.data).isEmpty()

            // check that no append/prepend can be triggered
            assertThat(pagingSource.itemCount.get()).isEqualTo(0)
            assertThat(result.nextKey).isNull()
            assertThat(result.prevKey).isNull()
            assertThat(result.itemsBefore).isEqualTo(0)
            assertThat(result.itemsAfter).isEqualTo(0)
        }
    }

    @Test
    fun load_UserSuppliedNegativeLimit() {
        dao.addAllItems(itemsList)
        val pagingSource = LimitOffsetPagingSourceImpl(
            db = database,
            queryString = "SELECT * FROM $tableName ORDER BY id ASC LIMIT -1",
        )
        runBlocking {
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.REFRESH,
                    key = null,
                )
            ) as PagingSource.LoadResult.Page
            // ensure that it respects SQLite's default behavior for negative LIMIT
            assertThat(result.data).containsExactlyElementsIn(
                itemsList.subList(0, 15)
            )
            // should behave as if no LIMIT were set
            assertThat(pagingSource.itemCount.get()).isEqualTo(100)
            assertThat(result.nextKey).isEqualTo(15)
            assertThat(result.prevKey).isNull()
            assertThat(result.itemsBefore).isEqualTo(0)
            assertThat(result.itemsAfter).isEqualTo(85)
        }
    }

    @Test
    fun invalidInitialKey_dbEmpty_returnsEmpty() {
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        runBlocking {
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.REFRESH,
                    key = 101,
                )
            ) as PagingSource.LoadResult.Page
            assertThat(result.data).isEmpty()
        }
    }

    @Test
    fun invalidInitialKey_keyTooLarge_returnsEmpty() {
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        dao.addAllItems(itemsList)
        runBlocking {
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.REFRESH,
                    key = 101,
                )
            ) as PagingSource.LoadResult.Page
            assertThat(result.data).isEmpty()
        }
    }

    @Test
    fun invalidInitialKey_negativeKey() {
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        dao.addAllItems(itemsList)
        runBlocking {
            // should throw error when initial key is negative
            val expectedException = assertFailsWith<IllegalArgumentException> {
                pagingSource.load(
                    createLoadParam(
                        LoadType.REFRESH,
                        key = -1,
                    )
                )
            }
            // default message from Paging 3 for negative initial key
            assertThat(expectedException.message).isEqualTo(
                "itemsBefore cannot be negative"
            )
        }
    }

    @Test
    fun append_middleOfList() {
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        dao.addAllItems(itemsList)
        // to bypass check for initial load and run as non-initial load
        pagingSource.itemCount.set(100)
        runBlocking {
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.APPEND,
                    key = 20,
                )
            ) as PagingSource.LoadResult.Page
            // item in pos 21-25 (TestItemId 20-24) loaded
            assertThat(result.data).containsExactlyElementsIn(
                itemsList.subList(20, 25)
            )
            assertThat(result.nextKey).isEqualTo(25)
            assertThat(result.prevKey).isEqualTo(20)
        }
    }

    @Test
    fun append_availableItemsLessThanLoadSize() {
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        dao.addAllItems(itemsList)
        // to bypass check for initial load and run as non-initial load
        pagingSource.itemCount.set(100)
        runBlocking {
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.APPEND,
                    key = 97,
                )
            ) as PagingSource.LoadResult.Page
            // item in pos 98-100 (TestItemId 97-99) loaded
            assertThat(result.data).containsExactlyElementsIn(
                itemsList.subList(97, 100)
            )
            assertThat(result.nextKey).isEqualTo(null)
            assertThat(result.prevKey).isEqualTo(97)
        }
    }

    @Test
    fun load_consecutiveAppend() {
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        dao.addAllItems(itemsList)
        // to bypass check for initial load and run as non-initial load
        pagingSource.itemCount.set(100)
        runBlocking {
            // first prepend
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.APPEND,
                    key = 30,
                )
            ) as PagingSource.LoadResult.Page
            // TestItemId 30-34 loaded
            assertThat(result.data).containsExactlyElementsIn(
                itemsList.subList(30, 35)
            )
            // second prepend using nextKey from previous load
            val result2 = pagingSource.load(
                createLoadParam(
                    LoadType.APPEND,
                    key = result.nextKey,
                )
            ) as PagingSource.LoadResult.Page
            // TestItemId 35 - 39 loaded
            assertThat(result2.data).containsExactlyElementsIn(
                itemsList.subList(35, 40)
            )
        }
    }

    @Test
    fun prepend_middleOfList() {
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        dao.addAllItems(itemsList)
        // to bypass check for initial load and run as non-initial load
        pagingSource.itemCount.set(100)
        runBlocking {
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.PREPEND,
                    key = 30,
                )
            ) as PagingSource.LoadResult.Page

            assertThat(result.data).containsExactlyElementsIn(
                itemsList.subList(25, 30)
            )
            assertThat(result.nextKey).isEqualTo(30)
            assertThat(result.prevKey).isEqualTo(25)
        }
    }

    @Test
    fun prepend_availableItemsLessThanLoadSize() {
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        dao.addAllItems(itemsList)
        // to bypass check for initial load and run as non-initial load
        pagingSource.itemCount.set(100)
        runBlocking {
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.PREPEND,
                    key = 3,
                )
            ) as PagingSource.LoadResult.Page
            // items in pos 0 - 2 (TestItemId 0 - 2) loaded
            assertThat(result.data).containsExactlyElementsIn(
                itemsList.subList(0, 3)
            )
            assertThat(result.nextKey).isEqualTo(3)
            assertThat(result.prevKey).isEqualTo(null)
        }
    }

    @Test
    fun load_consecutivePrepend() {
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        dao.addAllItems(itemsList)
        // to bypass check for initial load and run as non-initial load
        pagingSource.itemCount.set(100)
        runBlocking {
            // first prepend
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.PREPEND,
                    key = 20,
                )
            ) as PagingSource.LoadResult.Page
            // items pos 16-20 (TestItemId 15-19) loaded
            assertThat(result.data).containsExactlyElementsIn(
                itemsList.subList(15, 20)
            )
            // second prepend using prevKey from previous load
            val result2 = pagingSource.load(
                createLoadParam(
                    LoadType.PREPEND,
                    key = result.prevKey,
                )
            ) as PagingSource.LoadResult.Page
            // items pos 11-15 (TestItemId 10 - 14) loaded
            assertThat(result2.data).containsExactlyElementsIn(
                itemsList.subList(10, 15)
            )
        }
    }

    @Test
    fun test_itemsBefore() {
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        dao.addAllItems(itemsList)
        runBlocking {
            // for initial load
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.REFRESH,
                    key = 50,
                )
            ) as PagingSource.LoadResult.Page
            // initial loads items in pos 51 - 65, should have 50 items before
            assertThat(result.itemsBefore).isEqualTo(50)

            // prepend from initial load
            val result2 = pagingSource.load(
                createLoadParam(
                    LoadType.PREPEND,
                    key = result.prevKey,
                )
            ) as PagingSource.LoadResult.Page
            // prepend loads items in pos 46 - 50, should have 45 item before
            assertThat(result2.itemsBefore).isEqualTo(45)

            // append from initial load
            val result3 = pagingSource.load(
                createLoadParam(
                    LoadType.APPEND,
                    key = result.nextKey,
                )
            ) as PagingSource.LoadResult.Page
            // append loads items in position 66 - 70 , should have 65 item before
            assertThat(result3.itemsBefore).isEqualTo(65)
        }
    }

    @Test
    fun test_itemsAfter() {
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        dao.addAllItems(itemsList)
        runBlocking {
            // for initial load
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.REFRESH,
                    key = 30,
                )
            ) as PagingSource.LoadResult.Page
            // initial loads items in position 31 - 45, should have 55 items after
            assertThat(result.itemsAfter).isEqualTo(55)

            // prepend from initial load
            val result2 = pagingSource.load(
                createLoadParam(
                    LoadType.PREPEND,
                    key = result.prevKey,
                )
            ) as PagingSource.LoadResult.Page
            // prepend loads items in position 26 - 30, should have 70 item after
            assertThat(result2.itemsAfter).isEqualTo(70)

            // append from initial load
            val result3 = pagingSource.load(
                createLoadParam(
                    LoadType.APPEND,
                    key = result.nextKey,
                )
            ) as PagingSource.LoadResult.Page
            // append loads items in position 46 - 50 , should have 50 item after
            assertThat(result3.itemsAfter).isEqualTo(50)
        }
    }

    @Test
    fun test_getRefreshKey() {
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        dao.addAllItems(itemsList)
        runBlocking {
            // initial load
            val result = pagingSource.load(
                createLoadParam(
                    LoadType.REFRESH,
                    key = null,
                )
            ) as PagingSource.LoadResult.Page
            // 15 items loaded, assuming anchorPosition = 14 as the last item loaded
            var refreshKey = pagingSource.getRefreshKey(
                PagingState(
                    pages = listOf(result),
                    anchorPosition = 14,
                    config = CONFIG,
                    leadingPlaceholderCount = 0
                )
            )
            // should load around anchor position
            // Initial load size = 15, refresh key should be (15/2 = 7) items
            // before anchorPosition (14 - 7 = 7)
            assertThat(refreshKey).isEqualTo(7)

            // append after refresh
            val result2 = pagingSource.load(
                createLoadParam(
                    LoadType.APPEND,
                    key = result.nextKey,
                )
            ) as PagingSource.LoadResult.Page

            assertThat(result2.data).isEqualTo(
                itemsList.subList(15, 20)
            )
            refreshKey = pagingSource.getRefreshKey(
                PagingState(
                    pages = listOf(result, result2),
                    // 20 items loaded, assume anchorPosition = 19 as the last item loaded
                    anchorPosition = 19,
                    config = CONFIG,
                    leadingPlaceholderCount = 0
                )
            )
            // initial load size 15. Refresh key should be (15/2 = 7) items before anchorPosition
            // (19 - 7 = 12)
            assertThat(refreshKey).isEqualTo(12)
        }
    }

    @Test
    fun test_jumpSupport() {
        val pagingSource = LimitOffsetPagingSourceImpl(database)
        assertTrue(pagingSource.jumpingSupported)
    }

    private fun createLoadParam(
        loadType: LoadType,
        key: Int? = null,
        initialLoadSize: Int = CONFIG.initialLoadSize,
        pageSize: Int = CONFIG.pageSize,
        placeholdersEnabled: Boolean = CONFIG.enablePlaceholders
    ): PagingSource.LoadParams<Int> {
        return when (loadType) {
            LoadType.REFRESH -> {
                PagingSource.LoadParams.Refresh(
                    key = key,
                    loadSize = initialLoadSize,
                    placeholdersEnabled = placeholdersEnabled
                )
            }
            LoadType.APPEND -> {
                PagingSource.LoadParams.Append(
                    key = key ?: -1,
                    loadSize = pageSize,
                    placeholdersEnabled = placeholdersEnabled
                )
            }
            LoadType.PREPEND -> {
                PagingSource.LoadParams.Prepend(
                    key = key ?: -1,
                    loadSize = pageSize,
                    placeholdersEnabled = placeholdersEnabled
                )
            }
        }
    }

    private fun createItemsForDb(startId: Int, count: Int): List<TestItem> {
        return List(count) {
            TestItem(
                id = it + startId,
            )
        }
    }

    companion object {
        val CONFIG = PagingConfig(
            pageSize = 5,
            enablePlaceholders = true,
            initialLoadSize = 15
        )
    }
}

class LimitOffsetPagingSourceImpl(
    db: RoomDatabase,
    queryString: String = "SELECT * FROM $tableName ORDER BY id ASC",
) : LimitOffsetPagingSource<TestItem>(
    sourceQuery = RoomSQLiteQuery.acquire(
        queryString,
        0
    ),
    db = db,
    tables = arrayOf("$tableName")
) {

    override fun convertRows(cursor: Cursor): List<TestItem> {
        val cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(cursor, "id")
        val data = mutableListOf<TestItem>()
        while (cursor.moveToNext()) {
            val tmpId = cursor.getInt(cursorIndexOfId)
            data.add(TestItem(tmpId))
        }
        return data
    }
}
