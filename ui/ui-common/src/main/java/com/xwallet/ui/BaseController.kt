/**
 * BreadWallet
 *
 * Created by Drew Carlson <drew.carlson@breadwallet.com> on 8/12/19.
 * Copyright (c) 2019 breadwallet LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.xwallet.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.viewbinding.ViewBinding
import com.bluelinelabs.conductor.Controller
import com.xwallet.util.errorHandler
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import org.kodein.di.KodeinAware
import org.kodein.di.android.closestKodein
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A simple controller that automatically inflates the
 * [layoutId] and implements [LayoutContainer].
 */
@Suppress("TooManyFunctions")
abstract class BaseController(
    args: Bundle? = null
) : Controller(args), KodeinAware, LayoutContainer {

    protected val controllerScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + errorHandler("controllerScope")
    )
    protected val viewAttachScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + errorHandler("viewAttachScope")
    )
    protected val viewCreatedScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + errorHandler("viewCreatedScope")
    )

    /** Provides the Activity Kodein instance. */
    override val kodein by closestKodein {
        checkNotNull(activity) {
            "Controller cannot access Kodein bindings until attached to an Activity."
        }
    }

    private val resettableDelegates = mutableListOf<ResettableDelegate<*>>()

    final override var containerView: View? = null
        private set

    /** The layout id to be used for inflating this controller's view. */
    open val layoutId: Int = -1

    /** Called when the view has been inflated and [containerView] is set. */
    open fun onCreateView(view: View) {
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        val viewBindingDelegates = resettableDelegates.filterIsInstance<ViewBindingDelegate<ViewBinding>>()

        return if (viewBindingDelegates.isEmpty()) {
            check(layoutId > 0) { "Must set layoutId or override onCreateView." }
            inflater.inflate(layoutId, container, false).apply {
                containerView = this
                onCreateView(this)
            }
        } else {
            check(viewBindingDelegates.size == 1) { "Only one ViewBinding delegate can be created" }
            return viewBindingDelegates.single().create(inflater).root.also(::onCreateView)
        }
    }

    override fun onDestroyView(view: View) {
        resetDelegatesFor(ResetCallback.ON_DESTROY_VIEW)
        viewCreatedScope.coroutineContext.cancelChildren()
        super.onDestroyView(view)
        clearFindViewByIdCache()
        containerView = null
    }

    override fun onDetach(view: View) {
        resetDelegatesFor(ResetCallback.ON_DETACH)
        viewAttachScope.coroutineContext.cancelChildren()
        super.onDetach(view)
    }

    override fun onDestroy() {
        super.onDestroy()
        resetDelegatesFor(ResetCallback.ON_DESTROY)
        controllerScope.coroutineContext.cancelChildren()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> argOptional(key: String, default: T? = null): T? =
        (args[key] as T) ?: default

    /** Returns the value for [key] stored in the [args] [Bundle] as [T]. */
    @Suppress("UNCHECKED_CAST")
    fun <T> arg(key: String, default: T? = null): T =
        checkNotNull(args[key] ?: default) {
            "No value for '$key' and no default provided (or it was null)."
        } as T

    /** Display a [Toast] message of [resId] with a short duration. */
    fun toast(resId: Int) = Toast.makeText(checkNotNull(applicationContext), resId, Toast.LENGTH_SHORT).show()

    /** Display a [Toast] message of [text] with a short duration. */
    fun toast(text: String) = Toast.makeText(checkNotNull(applicationContext), text, Toast.LENGTH_SHORT).show()

    /** Display a [Toast] message of [resId] with a long duration. */
    fun toastLong(resId: Int) = Toast.makeText(checkNotNull(applicationContext), resId, Toast.LENGTH_LONG).show()

    /** Display a [Toast] message of [text] with a long duration. */
    fun toastLong(text: String) = Toast.makeText(checkNotNull(applicationContext), text, Toast.LENGTH_LONG).show()

    fun requireContext(): Context = checkNotNull(applicationContext) {
        "requireContext() cannot be called before onAttach(..)"
    }

    inline fun <reified T> findListener(): T? =
        (targetController as? T)
            ?: (parentController as? T)
            ?: (router.backstack.dropLast(1).lastOrNull()?.controller() as? T)

    private fun resetDelegatesFor(callback: ResetCallback) {
        resettableDelegates.forEach { delegate ->
            if (delegate.resetCallback == callback) {
                delegate.reset()
            }
        }
    }

    /**
     * Produce a [ViewBinding] [T] during [onCreateView] and use the
     * [ViewBinding.getRoot] as the [Controller.getView].
     *
     * The binding will be released in [onDestroyView].
     *
     * *NOTE:* The binding only exists between [onCreateView] and [onDestroyView]
     */
    protected fun <T : ViewBinding> viewBinding(
        block: (LayoutInflater) -> T
    ): ReadOnlyProperty<BaseController, T> {
        return ViewBindingDelegate(block).also(resettableDelegates::add)
    }

    enum class ResetCallback {
        ON_DESTROY_VIEW, ON_DETACH, ON_DESTROY
    }

    private open inner class ResettableDelegate<T>(
            val resetCallback: ResetCallback,
            protected open val produceValue: () -> T = { error("produceValue not implemented") }
    ) : ReadOnlyProperty<BaseController, T> {

        protected var value: T? = null

        override fun getValue(thisRef: BaseController, property: KProperty<*>): T {
            if (value == null) {
                value = produceValue()
            }
            return value!!
        }

        fun reset() {
            value = null
        }
    }

    private inner class ViewBindingDelegate<T : ViewBinding>(
        private val createBinding: (LayoutInflater) -> T
    ) : ResettableDelegate<T>(ResetCallback.ON_DESTROY_VIEW) {

        fun create(inflater: LayoutInflater): T {
            check(value == null) { "ViewBinding has already been created." }
            value = createBinding(inflater)
            return value!!
        }

        override fun getValue(thisRef: BaseController, property: KProperty<*>): T {
            return checkNotNull(value) { "ViewBinding has not been created yet." }
        }
    }
}
