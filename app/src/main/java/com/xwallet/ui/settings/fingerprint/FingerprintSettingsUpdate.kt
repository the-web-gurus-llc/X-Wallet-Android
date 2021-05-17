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
package com.xwallet.ui.settings.fingerprint

import com.xwallet.ui.settings.fingerprint.FingerprintSettings.E
import com.xwallet.ui.settings.fingerprint.FingerprintSettings.F
import com.xwallet.ui.settings.fingerprint.FingerprintSettings.M
import com.spotify.mobius.Next
import com.spotify.mobius.Next.next
import com.spotify.mobius.Update

object FingerprintSettingsUpdate : Update<M, E, F>, FingerprintSettingsUpdateSpec {

    override fun update(
        model: M,
        event: E
    ): Next<M, F> = patch(model, event)

    override fun onBackClicked(model: M)
        : Next<M, F> =
        Next.dispatch(
            setOf(F.GoBack)
        )

    override fun onFaqClicked(model: M)
        : Next<M, F> =
        Next.dispatch(
            setOf(F.GoToFaq)
        )

    override fun onAppUnlockChanged(
        model: M,
        event: E.OnAppUnlockChanged
    ): Next<M, F> {
        val updatedModel = model.copy(
            unlockApp = event.enable,
            sendMoneyEnable = event.enable,
            sendMoney = (event.enable && model.sendMoney)
        )
        return next(
            updatedModel,
            setOf(
                F.UpdateFingerprintSetting(
                    updatedModel.unlockApp,
                    updatedModel.sendMoney
                )
            )
        )
    }

    override fun onSendMoneyChanged(
        model: M,
        event: E.OnSendMoneyChanged
    ): Next<M, F> {
        return next(
            model.copy(sendMoney = event.enable),
            setOf(
                F.UpdateFingerprintSetting(
                    model.unlockApp,
                    event.enable
                )
            )
        )
    }

    override fun onSettingsLoaded(
        model: M,
        event: E.OnSettingsLoaded
    ): Next<M, F> {
        return next(
            model.copy(
                unlockApp = event.unlockApp,
                sendMoney = event.sendMoney,
                sendMoneyEnable = event.unlockApp
            )
        )
    }
}
