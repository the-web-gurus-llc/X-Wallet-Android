/**
 * BreadWallet
 *
 * Created by Drew Carlson <drew.carlson@breadwallet.com> on 8/14/20.
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
package com.xwallet.ui.showkey

import com.spotify.mobius.Next

interface ShowPaperKeyUpdateSpec {
    fun patch(model: ShowPaperKey.M, event: ShowPaperKey.E): Next<ShowPaperKey.M, ShowPaperKey.F> = when (event) {
        ShowPaperKey.E.OnNextClicked -> onNextClicked(model)
        ShowPaperKey.E.OnPreviousClicked -> onPreviousClicked(model)
        ShowPaperKey.E.OnCloseClicked -> onCloseClicked(model)
        is ShowPaperKey.E.OnPageChanged -> onPageChanged(model, event)
    }

    fun onNextClicked(model: ShowPaperKey.M): Next<ShowPaperKey.M, ShowPaperKey.F>

    fun onPreviousClicked(model: ShowPaperKey.M): Next<ShowPaperKey.M, ShowPaperKey.F>

    fun onCloseClicked(model: ShowPaperKey.M): Next<ShowPaperKey.M, ShowPaperKey.F>

    fun onPageChanged(model: ShowPaperKey.M, event: ShowPaperKey.E.OnPageChanged): Next<ShowPaperKey.M, ShowPaperKey.F>
}