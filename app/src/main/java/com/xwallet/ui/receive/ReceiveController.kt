/**
 * BreadWallet
 *
 * Created by Drew Carlson <drew.carlson@breadwallet.com> on 10/15/19.
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
package com.xwallet.ui.receive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import com.xwallet.R
import com.xwallet.legacy.presenter.customviews.BRKeyboard
import com.xwallet.legacy.presenter.entities.CryptoRequest
import com.xwallet.logger.logError
import com.xwallet.tools.animation.SlideDetector
import com.xwallet.tools.animation.UiUtils
import com.xwallet.tools.manager.BRSharedPrefs
import com.xwallet.tools.qrcode.QRUtils
import com.xwallet.ui.BaseMobiusController
import com.xwallet.ui.ViewEffect
import com.xwallet.ui.changehandlers.BottomSheetChangeHandler
import com.xwallet.ui.flowbind.clicks
import com.xwallet.ui.receive.ReceiveScreen.E
import com.xwallet.ui.receive.ReceiveScreen.F
import com.xwallet.ui.receive.ReceiveScreen.M
import com.xwallet.ui.send.formatFiatForInputUi
import com.xwallet.util.CryptoUriParser
import com.xwallet.util.CurrencyCode
import com.spotify.mobius.First.first
import com.spotify.mobius.Init
import drewcarlson.mobius.flow.FlowTransformer
import kotlinx.android.synthetic.main.controller_receive.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.kodein.di.direct
import org.kodein.di.erased.instance
import java.util.Currency

private const val EXTRA_CURRENCY_CODE = "currency_code"
private const val EXTRA_RECEIVE = "is_receive"
private const val HIDE_COPY_MESSAGE_DELAY_MS: Long = 2 * 1000 // 2 Seconds

class ReceiveController(args: Bundle) : BaseMobiusController<M, E, F>(args) {

    constructor(
        currencyCode: CurrencyCode,
        isReceive: Boolean = true
    ) : this(
        bundleOf(
            EXTRA_CURRENCY_CODE to currencyCode,
            EXTRA_RECEIVE to isReceive
        )
    )

    init {
        overridePushHandler(BottomSheetChangeHandler())
        overridePopHandler(BottomSheetChangeHandler())
    }

    private val currencyCode: String = arg(EXTRA_CURRENCY_CODE)
    private val cryptoUriParser by instance<CryptoUriParser>()

    override val layoutId = R.layout.controller_receive

    override val defaultModel =
        M.createDefault(
            currencyCode = currencyCode,
            fiatCurrencyCode = BRSharedPrefs.getPreferredFiatIso()
        )
    override val init = Init<M, F> { model ->
        first(
            model, setOf(
                F.LoadWalletInfo(model.currencyCode),
                F.LoadExchangeRate(model.currencyCode)
            )
        )
    }
    override val update = ReceiveUpdate
    override val flowEffectHandler: FlowTransformer<F, E>
        get() = createReceiveHandler(
            checkNotNull(applicationContext),
            direct.instance()
        )

    override fun onCreateView(view: View) {
        super.onCreateView(view)
        title.setText(R.string.UnlockScreen_myAddress)
        signal_layout.removeView(copied_layout)
        signal_layout.layoutTransition = com.xwallet.tools.animation.UiUtils.getDefaultTransition()
        keyboard.setBRButtonBackgroundResId(R.drawable.keyboard_white_button)
        keyboard.setDeleteImage(R.drawable.ic_delete_black)
        keyboard.setBRKeyboardColor(R.color.white)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        signal_layout.setOnTouchListener(SlideDetector(router, signal_layout))
    }

    override fun onDetach(view: View) {
        signal_layout.setOnTouchListener(null)
        super.onDetach(view)
    }

    override fun bindView(modelFlow: Flow<M>): Flow<E> {
        return merge(
//            faq_button.clicks().map { E.OnFaqClicked },
            share_button.clicks().map { E.OnShareClicked },
            close_button.clicks().map { E.OnCloseClicked },
            qr_image.clicks().map { E.OnCopyAddressClicked },
            textInputAmount.clicks().map { E.OnAmountClicked },
            background_layout.clicks().map { E.OnCloseClicked },
            address_text.clicks().map { E.OnCopyAddressClicked },
            iso_button.clicks().map { E.OnToggleCurrencyClicked },
            keyboard.bindInput()
        )
    }

    private fun com.xwallet.legacy.presenter.customviews.BRKeyboard.bindInput() = callbackFlow<E> {
        setOnInsertListener { key ->
            offer(
                when {
                    key.isEmpty() -> E.OnAmountChange.Delete
                    key[0] == '.' -> E.OnAmountChange.AddDecimal
                    Character.isDigit(key[0]) -> E.OnAmountChange.AddDigit(key.toInt())
                    else -> return@setOnInsertListener
                }
            )
        }
        awaitClose { setOnInsertListener(null) }
    }

    @Suppress("LongMethod", "ComplexMethod")
    override fun M.render() {
        val res = checkNotNull(resources)

        ifChanged(M::sanitizedAddress, address_text::setText)
        ifChanged(
            M::rawAmount,
            M::fiatCurrencyCode
        ) {
            val formattedAmount = if (isAmountCrypto || rawAmount.isBlank()) {
                rawAmount
            } else {
                rawAmount.formatFiatForInputUi(fiatCurrencyCode)
            }
            textInputAmount.setText(formattedAmount)
        }

        ifChanged(
            M::isAmountCrypto,
            M::currencyCode,
            M::fiatCurrencyCode
        ) {
            iso_button.text = when {
                isAmountCrypto -> currencyCode.toUpperCase()
                else -> "%s (%s)".format(
                    fiatCurrencyCode.toUpperCase(),
                    Currency.getInstance(fiatCurrencyCode).symbol
                )
            }
        }

        ifChanged(M::currencyCode) {
            title.text = "%s %s".format(
                res.getString(R.string.Receive_title),
                currencyCode.toUpperCase()
            )
        }

        ifChanged(
            M::receiveAddress,
            M::amount
        ) {
            if (receiveAddress.isNotBlank()) {
                val request = com.xwallet.legacy.presenter.entities.CryptoRequest.Builder()
                    .setAddress(receiveAddress)
                    .setAmount(amount)
                    .build()
                viewAttachScope.launch(Dispatchers.Main) {
                    cryptoUriParser.createUrl(currencyCode, request)?.let { uri ->
                        if (!QRUtils.generateQR(activity, uri.toString(), qr_image)) {
                            logError("failed to generate qr image for address")
                        }
                    }
                }
            } else {
                qr_image.setImageDrawable(null)
            }
        }

        ifChanged(M::isRequestAmountSupported) {
            amount_layout.isVisible = isRequestAmountSupported
        }

        ifChanged(M::isAmountEditVisible) {
            keyboard_layout.isVisible = isAmountEditVisible
        }
    }

    override fun handleViewEffect(effect: ViewEffect) {
        when (effect) {
            is F.ShowCopiedMessage -> showCopiedMessage()
            is F.ShareRequest -> shareAddress(effect)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == com.xwallet.tools.qrcode.QRUtils.WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_ID) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                eventConsumer.accept(E.OnShareClicked)
            }
        }
    }

    private fun showCopiedMessage() {
        viewCreatedScope.launch(Dispatchers.Main) {
            if (signal_layout.indexOfChild(copied_layout) == -1) {
                signal_layout.addView(
                    copied_layout,
                    signal_layout.indexOfChild(share_button)
                )
            }
            delay(HIDE_COPY_MESSAGE_DELAY_MS)
            signal_layout.removeView(copied_layout)
        }
    }

    private fun shareAddress(effect: F.ShareRequest) {
        val context = checkNotNull(applicationContext)
        val writePerm =
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (writePerm == PackageManager.PERMISSION_GRANTED) {
            val cryptoRequest = com.xwallet.legacy.presenter.entities.CryptoRequest.Builder()
                .setAddress(effect.address)
                .setAmount(effect.amount)
                .build()
            viewAttachScope.launch(Dispatchers.Main) {
                cryptoUriParser.createUrl(currencyCode, cryptoRequest)
                    ?.let { cryptoUri ->
                        com.xwallet.tools.qrcode.QRUtils.sendShareIntent(
                            context,
                            cryptoUri.toString(),
                            effect.address,
                            effect.walletName
                        )
                    }?.run(::startActivity)
            }
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                com.xwallet.tools.qrcode.QRUtils.WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_ID
            )
        }
    }
}
