/**
 * BreadWallet
 *
 * Created by Pablo Budelli <pablo.budelli@breadwallet.com> on 12/6/19.
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
package com.xwallet.ui.settings.fastsync

import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import androidx.core.os.bundleOf
import com.xwallet.R
import com.xwallet.effecthandler.metadata.MetaDataEffect
import com.xwallet.effecthandler.metadata.MetaDataEffectHandler
import com.xwallet.effecthandler.metadata.MetaDataEvent
import com.xwallet.model.SyncMode
import com.xwallet.tools.util.TokenUtil
import com.xwallet.ui.BaseMobiusController
import com.xwallet.ui.controllers.AlertDialogController
import com.xwallet.ui.flowbind.checked
import com.xwallet.ui.flowbind.clicks
import com.xwallet.ui.settings.fastsync.FastSync.E
import com.xwallet.ui.settings.fastsync.FastSync.F
import com.xwallet.ui.settings.fastsync.FastSync.M
import com.xwallet.util.CurrencyCode
import com.spotify.mobius.Connectable
import drewcarlson.mobius.flow.subtypeEffectHandler
import drewcarlson.mobius.flow.transform
import kotlinx.android.synthetic.main.controller_fast_sync.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import org.kodein.di.direct
import org.kodein.di.erased.instance
import java.util.Locale

private const val CURRENCY_CODE = "currency_code"

class FastSyncController(
    args: Bundle
) : BaseMobiusController<M, E, F>(args),
    AlertDialogController.Listener {

    constructor(currencyCode: CurrencyCode) : this(
        bundleOf(CURRENCY_CODE to currencyCode)
    )

    override val layoutId = R.layout.controller_fast_sync
    override val defaultModel = M.createDefault(arg(CURRENCY_CODE))
    override val init = FastSyncInit
    override val update = FastSyncUpdate
    override val flowEffectHandler
        get() = subtypeEffectHandler<F, E> {
            addFunctionSync<F.LoadCurrencyIds>(Default) {
                E.OnCurrencyIdsUpdated(
                    TokenUtil.getTokenItems()
                        .associateBy { it.symbol.toLowerCase(Locale.ROOT) }
                        .mapValues { it.value.currencyId }
                )
            }
            addTransformer<F.MetaData> { effects ->
                effects
                    .map { effect ->
                        when (effect) {
                            is F.MetaData.SetSyncMode ->
                                MetaDataEffect.UpdateWalletMode(
                                    effect.currencyId,
                                    effect.mode.walletManagerMode
                                )
                            is F.MetaData.LoadSyncModes ->
                                MetaDataEffect.LoadWalletModes
                        }
                    }
                    .transform(Connectable<MetaDataEffect, MetaDataEvent> { consumer ->
                        MetaDataEffectHandler(consumer, direct.instance(), direct.instance())
                    })
                    .mapNotNull { event ->
                        when (event) {
                            is MetaDataEvent.OnWalletModesUpdated ->
                                E.OnSyncModesUpdated(
                                    event.modeMap
                                        .filterKeys { currencyId -> currencyId.isNotBlank() }
                                        .mapValues { entry ->
                                            SyncMode.fromWalletManagerMode(entry.value)
                                        }
                                )
                            else -> null
                        }
                    }
            }

        }

    override fun bindView(modelFlow: Flow<M>): Flow<E> {
        modelFlow
            .map { it.fastSyncEnable }
            .distinctUntilChanged()
            .onEach { isChecked ->
                switch_fast_sync.isChecked = isChecked
            }
            .launchIn(uiBindScope)
        return merge(
            back_btn.clicks().map { E.OnBackClicked },
            switch_fast_sync.checked().map { E.OnFastSyncChanged(it) },
            bindLearnMoreLink()
        )
    }

    override fun onPositiveClicked(
            dialogId: String,
            controller: AlertDialogController,
            result: AlertDialogController.DialogInputResult
    ) {
        eventConsumer.accept(E.OnDisableFastSyncConfirmed)
    }

    override fun onNegativeClicked(
            dialogId: String,
            controller: AlertDialogController,
            result: AlertDialogController.DialogInputResult
    ) {
        eventConsumer.accept(E.OnDisableFastSyncCanceled)
    }

    override fun onDismissed(
            dialogId: String,
            controller: AlertDialogController,
            result: AlertDialogController.DialogInputResult
    ) {
        eventConsumer.accept(E.OnDisableFastSyncCanceled)
    }

    private fun bindLearnMoreLink() = callbackFlow<E> {
        val act = checkNotNull(activity)
        val message: String = act.getString(R.string.WalletConnectionSettings_explanatoryText)
        val clickableText = act.getString(R.string.WalletConnectionSettings_link)
        val linkPos = message.lastIndexOf(clickableText)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                offer(E.OnLearnMoreClicked)
            }
        }
        if (linkPos != -1) {
            // TODO(DROID-1624): WalletConnectionSettings_link is not correct in CJK translations, ignore link for now.
            description.text = SpannableString(message).apply {
                setSpan(
                    clickableSpan,
                    linkPos,
                    linkPos + clickableText.length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        description.movementMethod = LinkMovementMethod.getInstance()
        awaitClose()
    }
}
