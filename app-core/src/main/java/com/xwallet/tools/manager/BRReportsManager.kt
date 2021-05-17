/**
 * BreadWallet
 *
 * Created by Mihail Gutan on <mihail@breadwallet.com> 10/17/17.
 * Copyright (c) 2017 breadwallet LLC
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
package com.xwallet.tools.manager

import com.xwallet.logger.Logger
import com.xwallet.logger.logDebug
import com.xwallet.logger.logError
import com.xwallet.logger.logInfo
import com.xwallet.logger.logVerbose
import com.xwallet.logger.logWarning
import com.xwallet.logger.logWtf
import com.google.firebase.crashlytics.FirebaseCrashlytics

object BRReportsManager : Logger {
    @JvmStatic
    @JvmOverloads
    fun reportBug(er: Throwable?, crash: Boolean = false) {
        if (er == null) return
        logError("reportBug: ", er)
        try {
            FirebaseCrashlytics.getInstance().recordException(er)
        } catch (e: Exception) {
            logError("reportBug: failed to report to FireBase: ", e)
        }
        if (crash) throw er
    }

    override fun debug(message: String, vararg data: Any?) {
        logDebug(message, *data)
        log("D: $message", data)
    }

    override fun info(message: String, vararg data: Any?) {
        logInfo(message, *data)
        log("I: $message", data)
    }

    override fun verbose(message: String, vararg data: Any?) {
        logVerbose(message, *data)
        log("V: $message", data)
    }

    override fun warning(message: String, vararg data: Any?) {
        logWarning(message, *data)
        log("W: $message", data)
    }

    override fun wtf(message: String, vararg data: Any?) {
        logWtf(message, *data)
        log("WTF: $message", data)
    }

    override fun error(message: String, vararg data: Any?) {
        logError(message, *data)
        log("E: $message", data)
    }

    private fun log(message: String, data: Array<out Any?>) {
        FirebaseCrashlytics.getInstance().apply {
            log(message)
            data.filterIsInstance<Throwable>().forEach(::recordException)
        }
    }
}