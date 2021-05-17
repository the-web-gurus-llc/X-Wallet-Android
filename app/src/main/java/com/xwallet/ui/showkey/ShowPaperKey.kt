/**
 * BreadWallet
 *
 * Created by Pablo Budelli <pablo.budelli@breadwallet.com> on 10/10/19.
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
package com.xwallet.ui.showkey

import com.xwallet.ui.navigation.NavigationEffect
import com.xwallet.ui.navigation.NavigationTarget
import com.xwallet.ui.navigation.OnCompleteAction
import io.sweers.redacted.annotation.Redacted

object ShowPaperKey {
    data class M(
            @Redacted val phrase: List<String>,
            val onComplete: OnCompleteAction?,
            val currentWord: Int = 0
    ) {
        companion object {
            fun createDefault(
                phrase: List<String>,
                onComplete: OnCompleteAction?
            ) = M(phrase, onComplete)
        }
    }

    sealed class E {

        object OnNextClicked : E()
        object OnPreviousClicked : E()
        object OnCloseClicked : E()

        data class OnPageChanged(val position: Int) : E()
    }

    sealed class F {

        object GoToHome : F(), NavigationEffect {
            override val navigationTarget = NavigationTarget.Home
        }
        object GoToBuy : F(), NavigationEffect {
            override val navigationTarget = NavigationTarget.Buy
        }
        object GoBack : F(), NavigationEffect {
            override val navigationTarget = NavigationTarget.Back
        }
        data class GoToPaperKeyProve(
            @Redacted val phrase: List<String>,
            val onComplete: OnCompleteAction
        ) : F(), NavigationEffect {
            override val navigationTarget = NavigationTarget.PaperKeyProve(
                phrase,
                onComplete
            )
        }
    }
}
