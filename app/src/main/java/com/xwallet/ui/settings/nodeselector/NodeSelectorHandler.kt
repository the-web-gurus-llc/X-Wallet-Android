/**
 * BreadWallet
 *
 * Created by Pablo Budelli <pablo.budelli@breadwallet.com> on 11/14/19.
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
package com.xwallet.ui.settings.nodeselector

import com.xwallet.breadbox.BreadBox
import com.xwallet.breadbox.containsCurrency
import com.xwallet.breadbox.currencyId
import com.xwallet.breadbox.getPeerOrNull
import com.xwallet.logger.logError
import com.xwallet.mobius.bindConsumerIn
import com.xwallet.tools.manager.BRSharedPrefs
import com.xwallet.tools.util.btc
import com.xwallet.ui.settings.nodeselector.NodeSelector.E
import com.xwallet.ui.settings.nodeselector.NodeSelector.F
import com.xwallet.util.errorHandler
import com.spotify.mobius.Connection
import com.spotify.mobius.functions.Consumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

class NodeSelectorHandler(
    private val output: Consumer<E>,
    private val breadBox: BreadBox
) : Connection<F>,
    CoroutineScope {

    override val coroutineContext = SupervisorJob() + Dispatchers.Default + errorHandler()

    init {
        breadBox.wallet(btc)
            .map { it.walletManager.state }
            .distinctUntilChanged()
            .map { E.OnConnectionStateUpdated(it) }
            .bindConsumerIn(output, this)
    }

    override fun accept(effect: F) {
        when (effect) {
            F.LoadConnectionInfo -> loadConnectionInfo()
            F.SetToAutomatic -> setToAutomatic()
            is F.SetCustomNode -> setToManual(effect)
        }
    }

    override fun dispose() {
        coroutineContext.cancel()
    }

    private fun loadConnectionInfo() {
        val node = BRSharedPrefs.getTrustNode(iso = btc.toUpperCase(Locale.ROOT))
        val event = if (node.isNullOrBlank()) {
            E.OnConnectionInfoLoaded(NodeSelector.Mode.AUTOMATIC)
        } else {
            E.OnConnectionInfoLoaded(NodeSelector.Mode.MANUAL, node)
        }
        output.accept(event)
    }

    private fun setToAutomatic() {
        BRSharedPrefs.putTrustNode(
            iso = btc.toUpperCase(Locale.ROOT),
            trustNode = ""
        )
        launch {
            val walletManager = breadBox.wallet(btc).first().walletManager
            walletManager.connect(null)
        }
        output.accept(E.OnConnectionInfoLoaded(NodeSelector.Mode.AUTOMATIC))
    }

    private fun setToManual(effect: F.SetCustomNode) {
        BRSharedPrefs.putTrustNode(
            iso = btc.toUpperCase(Locale.ROOT),
            trustNode = effect.node
        )
        launch {
            val wallet = breadBox
                .wallet(btc)
                .first()
            val system = checkNotNull(breadBox.getSystemUnsafe())
            val network = system.networks.find { it.containsCurrency(wallet.currencyId) }

            val networkPeer = network?.getPeerOrNull(effect.node)
            if (networkPeer != null) {
                wallet.walletManager.connect(networkPeer)
                output.accept(E.OnConnectionInfoLoaded(NodeSelector.Mode.MANUAL, effect.node))
            } else {
                logError("Failed to create network peer")
            }
        }
    }
}
