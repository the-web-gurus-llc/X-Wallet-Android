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

import com.xwallet.breadbox.BreadBox
import com.breadwallet.crypto.WalletManagerSyncDepth.FROM_CREATION
import com.xwallet.ui.sync.SyncBlockchain.E
import com.xwallet.ui.sync.SyncBlockchain.F
import drewcarlson.mobius.flow.subtypeEffectHandler
import kotlinx.coroutines.flow.first

fun createSyncBlockchainHandler(breadBox: BreadBox) = subtypeEffectHandler<F, E> {
    addFunction<F.SyncBlockchain> { effect ->
        val wallet = breadBox.wallet(effect.currencyCode).first()
        wallet.walletManager.syncToDepth(FROM_CREATION)
        E.OnSyncStarted
    }
}
