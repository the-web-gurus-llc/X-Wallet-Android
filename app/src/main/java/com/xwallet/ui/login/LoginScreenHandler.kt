/**
 * BreadWallet
 *
 * Created by Pablo Budelli <pablo.budelli@breadwallet.com> on 10/25/19.
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
package com.xwallet.ui.login

import android.content.Context
import com.xwallet.tools.manager.BRSharedPrefs
import com.xwallet.tools.security.BrdUserManager
import com.xwallet.tools.security.isFingerPrintAvailableAndSetup
import com.xwallet.tools.util.EventUtils
import com.xwallet.ui.login.LoginScreen.E
import com.xwallet.ui.login.LoginScreen.F
import drewcarlson.mobius.flow.subtypeEffectHandler

fun createLoginScreenHandler(
    context: Context,
    brdUser: BrdUserManager
) = subtypeEffectHandler<F, E> {
    addAction<F.UnlockBrdUser> {
        brdUser.unlock()
    }

    addFunction<F.CheckFingerprintEnable> {
        E.OnFingerprintEnabled(
            enabled = isFingerPrintAvailableAndSetup(context) && BRSharedPrefs.unlockWithFingerprint
        )
    }

    addConsumer<F.TrackEvent> { effect ->
        com.xwallet.tools.util.EventUtils.pushEvent(effect.eventName, effect.attributes)
    }
}