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
package com.xwallet.ui.navigation

import android.util.Base64
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.xwallet.R
import com.xwallet.breadbox.BreadBox
import com.xwallet.legacy.presenter.settings.NotificationSettingsController
import com.xwallet.logger.logError
import com.xwallet.tools.util.BRConstants
import com.xwallet.tools.util.EventUtils
import com.xwallet.tools.util.Link
import com.xwallet.tools.util.ServerBundlesHelper
import com.xwallet.tools.util.asLink
import com.xwallet.tools.util.btc
import com.xwallet.ui.addwallets.AddWalletsController
import com.xwallet.ui.auth.AuthenticationController
import com.xwallet.ui.changehandlers.BottomSheetChangeHandler
import com.xwallet.ui.changehandlers.DialogChangeHandler
import com.xwallet.ui.controllers.AlertDialogController
import com.xwallet.ui.controllers.SignalController
import com.xwallet.ui.disabled.DisabledController
import com.xwallet.ui.home.HomeController
import com.xwallet.ui.importwallet.ImportController
import com.xwallet.ui.login.LoginController
import com.xwallet.ui.notification.InAppNotificationActivity
import com.xwallet.ui.onboarding.OnBoardingController
import com.xwallet.ui.pin.InputPinController
import com.xwallet.ui.provekey.PaperKeyProveController
import com.xwallet.ui.receive.ReceiveController
import com.xwallet.ui.scanner.ScannerController
import com.xwallet.ui.send.SendSheetController
import com.xwallet.ui.settings.SettingsController
import com.xwallet.ui.settings.about.AboutController
import com.xwallet.ui.settings.analytics.ShareDataController
import com.xwallet.ui.settings.currency.DisplayCurrencyController
import com.xwallet.ui.settings.fastsync.FastSyncController
import com.xwallet.ui.settings.fingerprint.FingerprintSettingsController
import com.xwallet.ui.settings.logview.LogcatController
import com.xwallet.ui.settings.logview.MetadataViewer
import com.xwallet.ui.settings.nodeselector.NodeSelectorController
import com.xwallet.ui.settings.segwit.EnableSegWitController
import com.xwallet.ui.settings.segwit.LegacyAddressController
import com.xwallet.ui.settings.wipewallet.WipeWalletController
import com.xwallet.ui.showkey.ShowPaperKeyController
import com.xwallet.ui.sync.SyncBlockchainController
import com.xwallet.ui.txdetails.TxDetailsController
import com.xwallet.ui.wallet.BrdWalletController
import com.xwallet.ui.wallet.WalletController
import com.xwallet.ui.web.WebController
import com.xwallet.ui.writedownkey.WriteDownKeyController
import com.xwallet.ui.uistaking.StakingController
import com.xwallet.ui.uigift.CreateGiftController
import com.xwallet.ui.uigift.ShareGiftController
import com.xwallet.util.CryptoUriParser
import com.xwallet.util.isBrd
import com.platform.HTTPServer
import com.platform.util.AppReviewPromptManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.kodein.di.KodeinAware
import org.kodein.di.android.closestKodein
import org.kodein.di.erased.instance
import java.util.Locale

@Suppress("TooManyFunctions")
class RouterNavigator(
    private val routerProvider: () -> Router
) : Navigator, NavigationTargetHandlerSpec, KodeinAware {

    private val router get() = routerProvider()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val kodein by closestKodein {
        checkNotNull(router.activity?.applicationContext)
    }

    private val breadBox by instance<BreadBox>()
    private val uriParser by instance<CryptoUriParser>()

    override fun navigateTo(target: INavigationTarget) =
        patch(target as NavigationTarget)

    fun Controller.asTransaction(
        popChangeHandler: ControllerChangeHandler? = FadeChangeHandler(),
        pushChangeHandler: ControllerChangeHandler? = FadeChangeHandler()
    ) = RouterTransaction.with(this)
        .popChangeHandler(popChangeHandler)
        .pushChangeHandler(pushChangeHandler)

    override fun wallet(effect: NavigationTarget.Wallet) {
        val walletController = when {
            effect.currencyCode.isBrd() -> BrdWalletController()
            else -> WalletController(effect.currencyCode)
        }
        router.pushController(
            RouterTransaction.with(walletController)
                .popChangeHandler(HorizontalChangeHandler())
                .pushChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun back() {
        if (!router.handleBack()) {
            router.activity?.onBackPressed()
        }
    }

    override fun brdRewards() {
        val rewardsUrl = HTTPServer.getPlatformUrl(HTTPServer.URL_REWARDS)
        router.pushController(
            WebController(rewardsUrl).asTransaction(
                VerticalChangeHandler(),
                VerticalChangeHandler()
            )
        )
    }

    override fun reviewBrd() {
        com.xwallet.tools.util.EventUtils.pushEvent(com.xwallet.tools.util.EventUtils.EVENT_REVIEW_PROMPT_GOOGLE_PLAY_TRIGGERED)
        AppReviewPromptManager.openGooglePlay(checkNotNull(router.activity))
    }

    override fun buy() {
        val url = String.format(
            BRConstants.CURRENCY_PARAMETER_STRING_FORMAT,
            HTTPServer.getPlatformUrl(HTTPServer.URL_BUY),
            btc.toUpperCase(Locale.ROOT)
        )
        val webTransaction =
            WebController(url).asTransaction(
                VerticalChangeHandler(),
                VerticalChangeHandler()
            )

        when (router.backstack.lastOrNull()?.controller()) {
            is HomeController -> router.pushController(webTransaction)
            else -> {
                router.setBackstack(
                    listOf(
                        HomeController().asTransaction(),
                        webTransaction
                    ),
                    VerticalChangeHandler()
                )
            }
        }
    }

    override fun trade() {
        val url = HTTPServer.getPlatformUrl(HTTPServer.URL_TRADE)
        router.pushController(
            WebController(url).asTransaction(
                VerticalChangeHandler(),
                VerticalChangeHandler()
            )
        )
    }

    override fun menu(effect: NavigationTarget.Menu) {
        router.pushController(
            RouterTransaction.with(SettingsController(effect.settingsOption))
                .popChangeHandler(VerticalChangeHandler())
                .pushChangeHandler(VerticalChangeHandler())
        )
    }

    override fun addWallet() {
        router.pushController(
            RouterTransaction.with(AddWalletsController())
                .popChangeHandler(HorizontalChangeHandler())
                .pushChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun sendSheet(effect: NavigationTarget.SendSheet) {
        val controller = when {
            effect.cryptoRequestUrl != null -> SendSheetController(effect.cryptoRequestUrl!!)
            else -> SendSheetController(effect.currencyId)
        }
        router.pushController(RouterTransaction.with(controller))
    }

    override fun receiveSheet(effect: NavigationTarget.ReceiveSheet) {
        val controller = ReceiveController(effect.currencyCode)
        router.pushController(RouterTransaction.with(controller))
    }

    override fun viewTransaction(effect: NavigationTarget.ViewTransaction) {
        val controller = TxDetailsController(effect.currencyId, effect.txHash)
        router.pushController(RouterTransaction.with(controller))
    }

    override fun deepLink(effect: NavigationTarget.DeepLink) {
        scope.launch(Dispatchers.Main) {
            val link = effect.url?.asLink(breadBox, uriParser) ?: effect.link
            if (link == null) {
                logError("Failed to parse url, ${effect.url}")
                showLaunchScreen(effect.authenticated)
            } else {
                processDeepLink(effect, link)
            }
        }
    }

    private fun showLaunchScreen(isAuthenticated: Boolean) {
        if (!router.hasRootController()) {
            val root = if (isAuthenticated) {
                HomeController()
            } else {
                LoginController(showHome = true)
            }
            router.setRoot(root.asTransaction())
        }
    }

    override fun goToInAppMessage(effect: NavigationTarget.GoToInAppMessage) {
        InAppNotificationActivity.start(checkNotNull(router.activity), effect.inAppMessage)
    }

    override fun supportPage(effect: NavigationTarget.SupportPage) {
        router.pushController(
            WebController(effect.asSupportUrl()).asTransaction(
                BottomSheetChangeHandler(),
                BottomSheetChangeHandler()
            )
        )
    }

    override fun setPin(effect: NavigationTarget.SetPin) {
        val transaction = RouterTransaction.with(
            InputPinController(
                onComplete = effect.onComplete,
                pinUpdate = !effect.onboarding,
                skipWriteDown = effect.skipWriteDownKey
            )
        ).pushChangeHandler(HorizontalChangeHandler())
            .popChangeHandler(HorizontalChangeHandler())
        if (effect.onboarding) {
            router.setBackstack(listOf(transaction), HorizontalChangeHandler())
        } else {
            router.pushController(transaction)
        }
    }

    override fun home() {
        router.setBackstack(
            listOf(RouterTransaction.with(HomeController())), HorizontalChangeHandler()
        )
    }

    override fun brdLogin() {
        router.pushController(
            RouterTransaction.with(LoginController())
                .popChangeHandler(FadeChangeHandler())
                .pushChangeHandler(FadeChangeHandler())
        )
    }

    override fun authentication(effect: NavigationTarget.Authentication) {
        val res = checkNotNull(router.activity).resources
        val controller = AuthenticationController(
            mode = effect.mode,
            title = res.getString(effect.titleResId ?: R.string.VerifyPin_title),
            message = res.getString(effect.messageResId ?: R.string.VerifyPin_continueBody)
        )
        router.pushController(RouterTransaction.with(controller))
    }

    override fun alertDialog(effect: NavigationTarget.AlertDialog) {
        val res = checkNotNull(router.activity).resources
        val message = effect.message ?: effect.messageResId?.let {
            res.getString(it, *effect.messageArgs.toTypedArray())
        } ?: ""
        val controller = AlertDialogController(
            dialogId = effect.dialogId,
            message = message,
            title = effect.title ?: effect.titleResId?.run(res::getString) ?: "",
            positiveText = effect.positiveButtonResId?.run(res::getString),
            negativeText = effect.negativeButtonResId?.run(res::getString),
            textInputPlaceholder = effect.textInputPlaceholder
                ?: effect.textInputPlaceholderResId?.run(res::getString)
        )
        router.pushController(RouterTransaction.with(controller))
    }

    override fun disabledScreen() {
        router.pushController(
            RouterTransaction.with(DisabledController())
                .pushChangeHandler(FadeChangeHandler())
                .popChangeHandler(FadeChangeHandler())
        )
    }

    override fun qRScanner() {
        val controller = ScannerController()
        controller.targetController = router.backstack.lastOrNull()?.controller()
        router.pushController(
            RouterTransaction.with(controller)
                .pushChangeHandler(FadeChangeHandler())
                .popChangeHandler(FadeChangeHandler())
        )
    }

    override fun writeDownKey(effect: NavigationTarget.WriteDownKey) {
        router.pushController(
            RouterTransaction.with(WriteDownKeyController(effect.onComplete, effect.requestAuth))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun paperKey(effect: NavigationTarget.PaperKey) {
        router.pushController(
            RouterTransaction.with(ShowPaperKeyController(effect.phrase, effect.onComplete))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun paperKeyProve(effect: NavigationTarget.PaperKeyProve) {
        router.pushController(
            RouterTransaction.with(PaperKeyProveController(effect.phrase, effect.onComplete))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun about() {
        router.pushController(
            AboutController().asTransaction(
                HorizontalChangeHandler(),
                HorizontalChangeHandler()
            )
        )
    }

    override fun displayCurrency() {
        router.pushController(
            RouterTransaction.with(DisplayCurrencyController())
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun notificationsSettings() {
        router.pushController(
            NotificationSettingsController().asTransaction(
                HorizontalChangeHandler(),
                HorizontalChangeHandler()
            )
        )
    }

    override fun shareDataSettings() {
        router.pushController(
            ShareDataController().asTransaction(
                HorizontalChangeHandler(),
                HorizontalChangeHandler()
            )
        )
    }

    override fun fingerprintSettings() {
        router.pushController(
            RouterTransaction.with(FingerprintSettingsController())
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun wipeWallet() {
        router.pushController(
            RouterTransaction.with(WipeWalletController())
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun onBoarding() {
        router.pushController(
            RouterTransaction.with(OnBoardingController())
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun importWallet() {
        router.pushController(
            ImportController().asTransaction(
                HorizontalChangeHandler(),
                HorizontalChangeHandler()
            )
        )
    }

    override fun importWallet(effect: NavigationTarget.ImportWalletWithKey) {
        router.pushController(
            ImportController(effect.privateKey, effect.isPasswordProtected)
                .asTransaction(
                    HorizontalChangeHandler(),
                    HorizontalChangeHandler()
                )
        )
    }

    override fun syncBlockchain(effect: NavigationTarget.SyncBlockchain) {
        router.pushController(
            RouterTransaction.with(SyncBlockchainController(effect.currencyCode))
                .popChangeHandler(HorizontalChangeHandler())
                .pushChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun bitcoinNodeSelector() {
        router.pushController(
            RouterTransaction.with(NodeSelectorController())
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun enableSegWit() {
        router.pushController(
            RouterTransaction.with(EnableSegWitController())
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun legacyAddress() {
        router.pushController(
            RouterTransaction.with(LegacyAddressController())
        )
    }

    override fun fastSync(effect: NavigationTarget.FastSync) {
        router.pushController(
            RouterTransaction.with(FastSyncController(effect.currencyCode))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun transactionComplete() {
        val res = checkNotNull(router.activity).resources
        router.replaceTopController(
            RouterTransaction.with(
                SignalController(
                    title = res.getString(R.string.Alerts_sendSuccess),
                    description = res.getString(R.string.Alerts_sendSuccessSubheader),
                    iconResId = R.drawable.ic_check_mark_white
                )
            )
        )
    }

    override fun nativeApiExplorer() {
        val url = "file:///android_asset/native-api-explorer.html"
        router.pushController(RouterTransaction.with(WebController(url)))
    }

    override fun aTMMap(effect: NavigationTarget.ATMMap) {
        router.pushController(
            WebController(effect.url, effect.mapJson).asTransaction(
                VerticalChangeHandler(),
                VerticalChangeHandler()
            )
        )
    }

    override fun signal(effect: NavigationTarget.Signal) {
        val res = checkNotNull(router.activity).resources
        router.pushController(
            SignalController(
                title = res.getString(effect.titleResId),
                description = res.getString(effect.messageResId),
                iconResId = effect.iconResId
            ).asTransaction()
        )
    }

    private inline fun Router.pushWithStackIfEmpty(
        topTransaction: RouterTransaction,
        isAuthenticated: Boolean,
        createStack: () -> List<RouterTransaction>
    ) {
        if (backstackSize <= 1) {
            val stack = if (isAuthenticated) {
                createStack()
            } else {
                createStack() + LoginController(showHome = false).asTransaction()
            }
            setBackstack(stack, FadeChangeHandler())
        } else {
            pushController(topTransaction)
            if (!isAuthenticated) {
                pushController(LoginController(showHome = false).asTransaction())
            }
        }
    }

    private fun processDeepLink(effect: NavigationTarget.DeepLink, link: Link) {
        val isTopLogin = router.backstack.lastOrNull()?.controller() is LoginController
        if (isTopLogin && effect.authenticated) {
            router.popCurrentController()
        }
        when (link) {
            is Link.CryptoRequestUrl -> {
                val sendController = SendSheetController(link).asTransaction()
                router.pushWithStackIfEmpty(sendController, effect.authenticated) {
                    listOf(
                        HomeController().asTransaction(),
                        WalletController(link.currencyCode).asTransaction(
                            popChangeHandler = HorizontalChangeHandler(),
                            pushChangeHandler = HorizontalChangeHandler()
                        ),
                        sendController
                    )
                }
            }
            is Link.BreadUrl.ScanQR -> {
                val controller = ScannerController().asTransaction()
                router.pushWithStackIfEmpty(controller, effect.authenticated) {
                    listOf(
                        HomeController().asTransaction(),
                        controller
                    )
                }
            }
            is Link.ImportWallet -> {
                val controller = ImportController(
                    privateKey = link.privateKey,
                    isPasswordProtected = link.passwordProtected
                ).asTransaction()
                router.pushWithStackIfEmpty(controller, effect.authenticated) {
                    listOf(
                        HomeController().asTransaction(),
                        controller
                    )
                }
            }
            is Link.PlatformUrl -> {
                val controller = WebController(link.url).asTransaction()
                router.pushWithStackIfEmpty(controller, effect.authenticated) {
                    listOf(
                        HomeController().asTransaction(),
                        controller
                    )
                }
            }
            is Link.PlatformDebugUrl -> {
                val context = router.activity!!.applicationContext
                if (!link.webBundleUrl.isNullOrBlank()) {
                    com.xwallet.tools.util.ServerBundlesHelper.setWebPlatformDebugURL(link.webBundleUrl)
                } else if (!link.webBundle.isNullOrBlank()) {
                    com.xwallet.tools.util.ServerBundlesHelper.setDebugBundle(
                        context,
                        com.xwallet.tools.util.ServerBundlesHelper.Type.WEB,
                        link.webBundle
                    )
                }

                showLaunchScreen(effect.authenticated)
            }
            Link.BreadUrl.ScanQR -> {
                val controller = ScannerController().asTransaction()
                router.pushWithStackIfEmpty(controller, effect.authenticated) {
                    listOf(
                        HomeController().asTransaction(),
                        controller
                    )
                }
            }
            is Link.WalletPairUrl -> {
                showLaunchScreen(effect.authenticated)
            }
            else -> {
                logError("Failed to route deeplink, going Home.")
                showLaunchScreen(effect.authenticated)
            }
        }
    }

    override fun logcatViewer() {
        pushSingleInstance { LogcatController() }
    }

    override fun metadataViewer() {
        pushSingleInstance { MetadataViewer() }
    }

    override fun staking(effect: NavigationTarget.Staking) {
        router.pushController(RouterTransaction.with(StakingController(effect.currencyId)))
    }

    override fun createGift(effect: NavigationTarget.CreateGift) {
        router.pushController(RouterTransaction.with(CreateGiftController(effect.currencyId)))
    }

    override fun shareGift(effect: NavigationTarget.ShareGift) {
        val controller = ShareGiftController(
            txHash = effect.txHash,
            giftUrl = effect.giftUrl,
            recipientName = effect.recipientName,
            giftAmount = effect.giftAmount,
            giftAmountFiat = effect.giftAmountFiat,
            pricePerUnit = effect.pricePerUnit
        )
        val transaction = RouterTransaction.with(controller)
                .popChangeHandler(DialogChangeHandler())
                .pushChangeHandler(DialogChangeHandler())
        if (effect.replaceTop) {
            router.replaceTopController(transaction)
        } else {
            router.pushController(transaction)
        }
    }

    private inline fun <reified T : Controller> pushSingleInstance(
        crossinline controller: () -> T
    ) {
        if (router.backstack.none { it.controller() is T }) {
            router.pushController(RouterTransaction.with(controller()))
        }
    }
}
