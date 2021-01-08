/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.ui.platform

import android.view.View
import android.view.ViewParent
import androidx.compose.runtime.CompositionReference
import androidx.compose.runtime.PausableMonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.dispatch.AndroidUiDispatcher
import androidx.compose.runtime.dispatch.MonotonicFrameClock
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewTreeLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The [CompositionReference] that should be used as a parent for compositions at or below
 * this view in the hierarchy. Set to non-`null` to provide a [CompositionReference]
 * for compositions created by child views, or `null` to fall back to any [CompositionReference]
 * provided by ancestor views.
 *
 * See [findViewTreeCompositionReference].
 */
var View.compositionReference: CompositionReference?
    get() = getTag(R.id.androidx_compose_ui_view_composition_reference) as? CompositionReference
    set(value) {
        setTag(R.id.androidx_compose_ui_view_composition_reference, value)
    }

/**
 * Returns the parent [CompositionReference] for this point in the view hierarchy, or `null`
 * if none can be found.
 *
 * See [compositionReference] to get or set the parent [CompositionReference] for
 * a specific view.
 */
fun View.findViewTreeCompositionReference(): CompositionReference? {
    var found: CompositionReference? = compositionReference
    if (found != null) return found
    var parent: ViewParent? = parent
    while (found == null && parent is View) {
        found = parent.compositionReference
        parent = parent.getParent()
    }
    return found
}

/**
 * A factory for creating an Android window-scoped [Recomposer]. See [createRecomposer].
 */
@InternalComposeUiApi
fun interface WindowRecomposerFactory {
    /**
     * Get a [Recomposer] for the window where [windowRootView] is at the root of the window's
     * [View] hierarchy. The factory is responsible for establishing a policy for
     * [shutting down][Recomposer.shutDown] the returned [Recomposer]. [windowRootView] will
     * hold a hard reference to the returned [Recomposer] until it [joins][Recomposer.join]
     * after shutting down.
     */
    fun createRecomposer(windowRootView: View): Recomposer

    companion object {
        /**
         * A [WindowRecomposerFactory] that creates **lifecycle-aware** [Recomposer]s.
         *
         * Returned [Recomposer]s will be bound to the [ViewTreeLifecycleOwner] registered
         * at the [root][View.getRootView] of the view hierarchy and run
         * [recomposition][Recomposer.runRecomposeAndApplyChanges] and composition effects on the
         * [AndroidUiDispatcher.CurrentThread] for the window's UI thread. The associated
         * [MonotonicFrameClock] will only produce frames when the [Lifecycle] is at least
         * [Lifecycle.State.STARTED], causing animations and other uses of [MonotonicFrameClock]
         * APIs to suspend until a **visible** frame will be produced.
         */
        val LifecycleAware: WindowRecomposerFactory = WindowRecomposerFactory { rootView ->
            rootView.createLifecycleAwareViewTreeRecomposer()
        }

        /**
         * A [WindowRecomposerFactory] that always returns references to the global,
         * soon to be deprecated, application main thread-bound [Recomposer.current].
         * The existence of this API/constant is only temporary while other Compose UI
         * infrastructure migrates to [LifecycleAware] recomposers.
         */
        @Deprecated(
            "Global Recomposer.current will be removed without replacement; prefer LifecycleScoped"
        )
        val Global: WindowRecomposerFactory = WindowRecomposerFactory {
            Recomposer.current()
        }
    }
}

@InternalComposeUiApi
object WindowRecomposerPolicy {
    // TODO: Change default to LifecycleScoped when code expecting Recomposer.current() migrates
    @Suppress("DEPRECATION")
    @Volatile
    private var factory: WindowRecomposerFactory = WindowRecomposerFactory.Global

    fun setWindowRecomposerFactory(factory: WindowRecomposerFactory) {
        this.factory = factory
    }

    internal fun createAndInstallWindowRecomposer(rootView: View): Recomposer {
        val newRecomposer = factory.createRecomposer(rootView)
        rootView.setTag(R.id.androidx_compose_ui_view_composition_reference, newRecomposer)
        GlobalScope.launch(
            rootView.handler.asCoroutineDispatcher("windowRecomposer cleanup").immediate
        ) {
            newRecomposer.join()
            val viewTagRecomposer =
                rootView.getTag(R.id.androidx_compose_ui_view_composition_reference)
            if (viewTagRecomposer === newRecomposer) {
                rootView.setTag(R.id.androidx_compose_ui_view_composition_reference, null)
            }
        }
        return newRecomposer
    }
}

/**
 * Get or lazily create a [Recomposer] for this view's window. The view must be attached
 * to a window with a [ViewTreeLifecycleOwner] registered at the root to access this property.
 */
@OptIn(InternalComposeUiApi::class)
internal val View.windowRecomposer: Recomposer
    get() {
        check(isAttachedToWindow) {
            "Cannot locate windowRecomposer; View $this is not attached to a window"
        }
        val rootView = rootView
        return when (val rootParentRef = rootView.compositionReference) {
            null -> WindowRecomposerPolicy.createAndInstallWindowRecomposer(rootView)
            is Recomposer -> rootParentRef
            else -> error("root viewTreeParentCompositionReference is not a Recomposer")
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
private fun View.createLifecycleAwareViewTreeRecomposer(): Recomposer {
    val currentThreadContext = AndroidUiDispatcher.CurrentThread
    val pausableClock = currentThreadContext[MonotonicFrameClock]?.let {
        PausableMonotonicFrameClock(it).apply { pause() }
    }
    val contextWithClock = currentThreadContext + (pausableClock ?: EmptyCoroutineContext)
    val recomposer = Recomposer(contextWithClock)
    val runRecomposeScope = CoroutineScope(contextWithClock)
    val viewTreeLifecycleOwner = checkNotNull(ViewTreeLifecycleOwner.get(this)) {
        "ViewTreeLifecycleOwner not found from $this"
    }
    viewTreeLifecycleOwner.lifecycle.addObserver(
        LifecycleEventObserver { _, event ->
            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (event) {
                Lifecycle.Event.ON_CREATE ->
                    // Undispatched launch since we've configured this scope
                    // to be on the UI thread
                    runRecomposeScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        recomposer.runRecomposeAndApplyChanges()
                    }
                Lifecycle.Event.ON_START -> pausableClock?.resume()
                Lifecycle.Event.ON_STOP -> pausableClock?.pause()
                Lifecycle.Event.ON_DESTROY -> {
                    recomposer.shutDown()
                }
            }
        }
    )
    return recomposer
}