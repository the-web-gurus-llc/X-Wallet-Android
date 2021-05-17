/**
 * BreadWallet
 *
 * Created by Drew Carlson <drew.carlson@breadwallet.com> on 1/14/20.
 * Copyright (c) 2020 breadwallet LLC
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
package com.xwallet.ui.sync

import com.xwallet.R
import com.xwallet.tools.util.BRConstants
import com.xwallet.ui.navigation.NavigationEffect
import com.xwallet.ui.navigation.NavigationTarget
import com.xwallet.util.CurrencyCode

object SyncBlockchain {
    data class M(val currencyCode: CurrencyCode)

    sealed class E {
        object OnFaqClicked : E()
        object OnSyncClicked : E()
        object OnConfirmSyncClicked : E()

        object OnSyncStarted : E()
    }

    sealed class F {
        data class SyncBlockchain(
            val currencyCode: CurrencyCode
        ) : F()


        sealed class Nav(
            override val navigationTarget: NavigationTarget
        ) : F(), NavigationEffect {

            object ShowSyncConfirmation : Nav(
                NavigationTarget.AlertDialog(
                    messageResId = R.string.ReScan_footer,
                    titleResId = R.string.ReScan_alertTitle,
                    positiveButtonResId = R.string.ReScan_alertAction,
                    negativeButtonResId = R.string.Button_cancel
                )
            )

            object GoToHome : Nav(NavigationTarget.Home)

            data class GoToSyncFaq(
                val currencyCode: CurrencyCode
            ) : Nav(NavigationTarget.SupportPage(BRConstants.FAQ_RESCAN))
        }
    }
}
