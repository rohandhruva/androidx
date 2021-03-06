/*
 * Copyright (C) 2021 The Android Open Source Project
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

package androidx.health.services.client.impl.response

import android.os.Parcel
import android.os.Parcelable
import androidx.health.services.client.data.DataPoint

/**
 * Response sent on MeasureCallback when new [DataPoints] [DataPoint] are available.
 *
 * @hide
 */
public data class DataPointsResponse(val dataPoints: List<DataPoint>) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeTypedList(dataPoints)
    }

    public companion object {
        @JvmField
        public val CREATOR: Parcelable.Creator<DataPointsResponse> =
            object : Parcelable.Creator<DataPointsResponse> {
                override fun createFromParcel(source: Parcel): DataPointsResponse? {
                    val list = ArrayList<DataPoint>()
                    source.readTypedList(list, DataPoint.CREATOR)
                    return DataPointsResponse(list)
                }

                override fun newArray(size: Int): Array<DataPointsResponse?> {
                    return arrayOfNulls(size)
                }
            }
    }
}
