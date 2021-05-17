/**
 * BreadWallet
 *
 * Created by Shivangi Gandhi on <shivangi@brd.com> 6/7/18.
 * Copyright (c) 2018 breadwallet LLC
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
package com.xwallet.app

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

typealias ApplicationLifecycleListener = (Lifecycle.Event) -> Unit

/**
 * Use the [ApplicationLifecycleObserver] to listen for application lifecycle events.
 */
class ApplicationLifecycleObserver : LifecycleObserver {
    /**
     * Called when the application is first created.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        Log.d(com.xwallet.app.ApplicationLifecycleObserver.Companion.TAG, "onCreate")
        onLifeCycleEvent(Lifecycle.Event.ON_CREATE)
    }

    /**
     * Called when the application is brought into the foreground.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        Log.d(com.xwallet.app.ApplicationLifecycleObserver.Companion.TAG, "onStart")
        onLifeCycleEvent(Lifecycle.Event.ON_START)
    }

    /**
     * Called when the application is put into the background.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        Log.d(com.xwallet.app.ApplicationLifecycleObserver.Companion.TAG, "onStop")
        onLifeCycleEvent(Lifecycle.Event.ON_STOP)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        Log.d(com.xwallet.app.ApplicationLifecycleObserver.Companion.TAG, "onDestroy")
        onLifeCycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    /**
     * Passes a lifecycle event to all registered listeners.
     *
     * @param event The lifecycle event that has just occurred.
     */
    private fun onLifeCycleEvent(event: Lifecycle.Event) {
        com.xwallet.app.ApplicationLifecycleObserver.Companion.listeners.forEach { it.invoke(event) }
    }

    companion object {
        private val TAG = com.xwallet.app.ApplicationLifecycleObserver::class.java.simpleName
        private val listeners = mutableListOf<com.xwallet.app.ApplicationLifecycleListener>()

        /**
         * Registers an application lifecycle listener to receive lifecycle events.
         *
         * @param listener The listener to register.
         */
        fun addApplicationLifecycleListener(listener: com.xwallet.app.ApplicationLifecycleListener) {
            if (!com.xwallet.app.ApplicationLifecycleObserver.Companion.listeners.contains(listener)) {
                com.xwallet.app.ApplicationLifecycleObserver.Companion.listeners.add(listener)
            }
        }

        /**
         * Unregisters an application lifecycle listener from receiving lifecycle events.
         *
         * @param listener The listener to unregister.
         */
        fun removeApplicationLifecycleListener(listener: com.xwallet.app.ApplicationLifecycleListener?) {
            if (com.xwallet.app.ApplicationLifecycleObserver.Companion.listeners.contains(listener)) {
                com.xwallet.app.ApplicationLifecycleObserver.Companion.listeners.remove(listener)
            }
        }
    }
}