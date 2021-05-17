/**
 * BreadWallet
 *
 * Created by Ahsan Butt <ahsan.butt@breadwallet.com> on 8/1/19.
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
package com.xwallet.ui.home

import android.content.Context
import com.xwallet.breadbox.BreadBox
import com.xwallet.breadbox.WalletState
import com.xwallet.breadbox.currencyId
import com.xwallet.breadbox.toBigDecimal
import com.breadwallet.crypto.WalletManagerState
import com.xwallet.ext.throttleLatest
import com.xwallet.model.Experiments
import com.xwallet.model.InAppMessage
import com.xwallet.model.TokenItem
import com.xwallet.repository.ExperimentsRepositoryImpl
import com.xwallet.repository.MessagesRepository
import com.xwallet.repository.RatesRepository
import com.xwallet.tools.manager.BRSharedPrefs
import com.xwallet.tools.manager.ConnectivityState
import com.xwallet.tools.manager.ConnectivityStateProvider
import com.xwallet.tools.security.BrdUserManager
import com.xwallet.tools.util.BRConstants
import com.xwallet.tools.util.CurrencyUtils
import com.xwallet.tools.util.EventUtils
import com.xwallet.tools.util.SupportManager
import com.xwallet.tools.util.TokenUtil
import com.xwallet.tools.util.Utils
import com.xwallet.ui.home.HomeScreen.E
import com.xwallet.ui.home.HomeScreen.F
import com.xwallet.util.usermetrics.UserMetricsUtil
import com.xwallet.platform.interfaces.AccountMetaDataProvider
import com.platform.interfaces.WalletProvider
import com.platform.util.AppReviewPromptManager
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import drewcarlson.mobius.flow.subtypeEffectHandler
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.suspendCancellableCoroutine
import java.math.BigDecimal
import java.util.Locale
import kotlin.coroutines.resume
import com.breadwallet.crypto.Wallet as CryptoWallet

private const val PROMPT_DISMISSED_FINGERPRINT = "fingerprint"

private const val WALLET_UPDATE_THROTTLE = 2_000L

fun createHomeScreenHandler(
    context: Context,
    breadBox: BreadBox,
    ratesRepo: RatesRepository,
    brdUser: BrdUserManager,
    walletProvider: WalletProvider,
    accountMetaDataProvider: AccountMetaDataProvider,
    connectivityStateProvider: ConnectivityStateProvider,
    supportManager: SupportManager
) = subtypeEffectHandler<F, E> {
    addConsumer<F.SaveEmail> { effect ->
        com.xwallet.util.usermetrics.UserMetricsUtil.makeEmailOptInRequest(context, effect.email)
        BRSharedPrefs.putEmailOptIn(true)
    }
    addFunction<F.DismissPrompt> { effect ->
        when (effect.promptItem) {
            PromptItem.FINGER_PRINT -> {
                BRSharedPrefs.putPromptDismissed(PROMPT_DISMISSED_FINGERPRINT, true)
            }
//            PromptItem.EMAIL_COLLECTION -> {
//                BRSharedPrefs.putEmailOptInDismissed(true)
//            }
        }
        E.CheckForPrompt
    }
    addTransformer<F.LoadPrompt> {
        // TODO: Move this logic elsewhere, a generic PromptManager
        BRSharedPrefs.promptChanges().mapLatest {
            val promptId = when {
//                BRSharedPrefs.appRatePromptShouldPromptDebug -> PromptItem.RATE_APP
//                !BRSharedPrefs.getEmailOptIn()
//                    && !BRSharedPrefs.getEmailOptInDismissed() -> {
//                    PromptItem.EMAIL_COLLECTION
//                }
                brdUser.pinCodeNeedsUpgrade() -> PromptItem.UPGRADE_PIN
                !BRSharedPrefs.getPhraseWroteDown() -> PromptItem.PAPER_KEY
//                AppReviewPromptManager.shouldPrompt() -> PromptItem.RATE_APP
                (!BRSharedPrefs.unlockWithFingerprint
                    && com.xwallet.tools.util.Utils.isFingerprintAvailable(context)
                    && !BRSharedPrefs.getPromptDismissed(PROMPT_DISMISSED_FINGERPRINT)) -> {
                    PromptItem.FINGER_PRINT
                }
                // BRSharedPrefs.getScanRecommended(iso = "BTC") -> PromptItem.RECOMMEND_RESCAN
                else -> null
            }
            if (promptId != null) {
                com.xwallet.tools.util.EventUtils.pushEvent(getPromptName(promptId) + com.xwallet.tools.util.EventUtils.EVENT_PROMPT_SUFFIX_DISPLAYED)
            }
            E.OnPromptLoaded(promptId)
        }
    }
    addTransformer<F.LoadConnectivityState> {
        connectivityStateProvider.state().mapLatest { state ->
            E.OnConnectionUpdated(state == ConnectivityState.Connected)
        }
    }
    addConsumer<F.TrackEvent> { effect ->
        com.xwallet.tools.util.EventUtils.pushEvent(effect.eventName, effect.attributes)
    }
    addFunction<F.CheckInAppNotification> {
        E.OnInAppNotificationProvided(context.loadInAppMessage())
    }
    addConsumer<F.UpdateWalletOrder> { effect ->
        accountMetaDataProvider.reorderWallets(effect.orderedCurrencyIds)
    }
    addConsumer<F.RecordPushNotificationOpened> { effect ->
        com.xwallet.tools.util.EventUtils.pushEvent(
            com.xwallet.tools.util.EventUtils.EVENT_MIXPANEL_APP_OPEN,
            mapOf(com.xwallet.tools.util.EventUtils.EVENT_ATTRIBUTE_CAMPAIGN_ID to effect.campaignId)
        )
        com.xwallet.tools.util.EventUtils.pushEvent(com.xwallet.tools.util.EventUtils.EVENT_PUSH_NOTIFICATION_OPEN)
    }
    addAction<F.CheckIfShowBuyAndSell> {
        val showBuyAndSell =
            ExperimentsRepositoryImpl.isExperimentActive(Experiments.BUY_SELL_MENU_BUTTON)
                && BRSharedPrefs.getPreferredFiatIso() == BRConstants.USD
        com.xwallet.tools.util.EventUtils.pushEvent(
            com.xwallet.tools.util.EventUtils.EVENT_EXPERIMENT_BUY_SELL_MENU_BUTTON,
            mapOf(com.xwallet.tools.util.EventUtils.EVENT_ATTRIBUTE_SHOW to showBuyAndSell.toString())
        )
        E.OnShowBuyAndSell(showBuyAndSell)
    }
    addFunction<F.LoadIsBuyBellNeeded> {
        val isBuyBellNeeded =
            ExperimentsRepositoryImpl.isExperimentActive(Experiments.BUY_NOTIFICATION) &&
                com.xwallet.tools.util.CurrencyUtils.isBuyNotificationNeeded()
        E.OnBuyBellNeededLoaded(isBuyBellNeeded)
    }

    addTransformer<F.LoadEnabledWallets> {
        walletProvider.enabledWallets().mapLatest { wallets ->
            val fiatIso = BRSharedPrefs.getPreferredFiatIso()
            val enabledWallets = wallets.mapNotNull { currencyId ->
                val token = TokenUtil.getTokenItems()
                    .find { currencyId.equals(it.currencyId, true) }
                if (token == null) {
                    null
                } else {
                    val state = breadBox.walletState(token.symbol).first()
                    token.asWallet(state, fiatIso, ratesRepo)
                }
            }
            E.OnEnabledWalletsUpdated(enabledWallets)
        }
    }

    addTransformer<F.LoadWallets> { effects ->
        effects.combine(ratesRepo.changes()) { _, _ -> Unit }
            .flatMapLatest { breadBox.wallets() }
            .throttleLatest(WALLET_UPDATE_THROTTLE)
            .mapLatest { wallets ->
                val fiatIso = BRSharedPrefs.getPreferredFiatIso()
                E.OnWalletsUpdated(wallets.map {
                    val name = TokenUtil.tokenForCode(it.currency.code)?.name
                    it.asWallet(name, fiatIso, ratesRepo)
                })
            }
    }

    addTransformer<F.LoadSyncStates> { effects ->
        effects.flatMapLatest { breadBox.currencyCodes() }
            .flatMapLatest { currencyCodes ->
                currencyCodes.map {
                    breadBox.walletSyncState(it)
                        .throttleLatest(WALLET_UPDATE_THROTTLE)
                        .map { syncState ->
                            E.OnWalletSyncProgressUpdated(
                                currencyCode = syncState.currencyCode,
                                progress = syncState.percentComplete,
                                syncThroughMillis = syncState.timestamp,
                                isSyncing = syncState.isSyncing
                            )
                        }
                }.merge()
            }
    }
    addAction<F.ClearRateAppPrompt> {
        AppReviewPromptManager.dismissPrompt()
    }
    addAction<F.SaveDontShowMeRateAppPrompt> {
        AppReviewPromptManager.neverAskAgain()
    }
    addConsumer<F.SubmitSupportForm> { effect ->
        supportManager.submitEmailRequest(body = effect.feedback)
    }
}

private suspend fun Context.loadInAppMessage(): InAppMessage =
    suspendCancellableCoroutine { continuation ->
        val notification = MessagesRepository.getInAppNotification(this)
        if (notification == null) {
            continuation.cancel()
        } else {
            // If the notification contains an image we need to pre fetch it
            // to avoid showing the image space empty while we fetch the image
            // while the notification is shown.
            if (notification.imageUrl == null) {
                continuation.resume(notification)
            } else {
                Picasso.get().load(notification.imageUrl).fetch(object : Callback {
                    override fun onSuccess() {
                        continuation.resume(notification)
                    }

                    override fun onError(exception: Exception) {
                        continuation.cancel()
                    }
                })
            }
        }
    }

private fun getPromptName(prompt: PromptItem): String = when (prompt) {
    PromptItem.FINGER_PRINT -> com.xwallet.tools.util.EventUtils.PROMPT_TOUCH_ID
    PromptItem.PAPER_KEY -> com.xwallet.tools.util.EventUtils.PROMPT_PAPER_KEY
    PromptItem.UPGRADE_PIN -> com.xwallet.tools.util.EventUtils.PROMPT_UPGRADE_PIN
    PromptItem.RECOMMEND_RESCAN -> com.xwallet.tools.util.EventUtils.PROMPT_RECOMMEND_RESCAN
//    PromptItem.EMAIL_COLLECTION -> com.xwallet.tools.util.EventUtils.PROMPT_EMAIL
//    PromptItem.RATE_APP -> com.xwallet.tools.util.EventUtils.PROMPT_RATE_APP
}

private fun TokenItem.asWallet(
    walletState: WalletState,
    fiatIso: String,
    ratesRepo: RatesRepository
): Wallet {
    val currencyCode = symbol.toLowerCase(Locale.ROOT)
    return Wallet(
        currencyId = currencyId,
        currencyCode = currencyCode,
        currencyName = name,
        fiatPricePerUnit = ratesRepo.getFiatPerCryptoUnit(currencyCode, fiatIso),
        priceChange = ratesRepo.getPriceChange(currencyCode),
        state = when (walletState) {
            WalletState.Loading -> Wallet.State.LOADING
            else -> Wallet.State.UNINITIALIZED
        },
        startColor = startColor,
        endColor = endColor
    )
}

private fun CryptoWallet.asWallet(
    name: String?,
    fiatIso: String,
    ratesRepo: RatesRepository
): Wallet {
    val tokenItem = TokenUtil.tokenForCode(currency.code)
    val balanceBig = balance.toBigDecimal()
    return Wallet(
        currencyId = currencyId,
        currencyName = name ?: currency.name,
        currencyCode = currency.code,
        fiatPricePerUnit = ratesRepo.getFiatPerCryptoUnit(currency.code, fiatIso),
        balance = balanceBig,
        fiatBalance = ratesRepo.getFiatForCrypto(balanceBig, currency.code, fiatIso)
            ?: BigDecimal.ZERO,
        syncProgress = 0f, // will update via sync events
        syncingThroughMillis = 0L, // will update via sync events
        priceChange = ratesRepo.getPriceChange(currency.code),
        state = Wallet.State.READY,
        isSyncing = walletManager.state == WalletManagerState.SYNCING(),
        startColor = tokenItem?.startColor,
        endColor = tokenItem?.endColor,
        isSupported = tokenItem?.isSupported ?: true
    )
}
