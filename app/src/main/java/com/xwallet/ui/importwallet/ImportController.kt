/**
 * BreadWallet
 *
 * Created by Drew Carlson <drew.carlson@breadwallet.com> on 12/3/19.
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
package com.xwallet.ui.importwallet

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.bluelinelabs.conductor.RouterTransaction
import com.xwallet.R
import com.xwallet.tools.util.Link
import com.xwallet.ui.BaseMobiusController
import com.xwallet.ui.ViewEffect
import com.xwallet.ui.controllers.AlertDialogController
import com.xwallet.ui.flowbind.clicks
import com.xwallet.ui.importwallet.Import.CONFIRM_IMPORT_DIALOG
import com.xwallet.ui.importwallet.Import.E
import com.xwallet.ui.importwallet.Import.F
import com.xwallet.ui.importwallet.Import.IMPORT_SUCCESS_DIALOG
import com.xwallet.ui.importwallet.Import.M
import com.xwallet.ui.importwallet.Import.M.LoadingState
import com.xwallet.ui.scanner.ScannerController
import kotlinx.android.synthetic.main.controller_import_wallet.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import org.kodein.di.Kodein
import org.kodein.di.direct
import org.kodein.di.erased.bind
import org.kodein.di.erased.instance
import org.kodein.di.erased.singleton

private const val PRIVATE_KEY = "private_key"
private const val PASSWORD_PROTECTED = "password_protected"

@Suppress("TooManyFunctions")
class ImportController(
    args: Bundle? = null
) : BaseMobiusController<M, E, F>(args),
    ScannerController.Listener,
    AlertDialogController.Listener,
    PasswordController.Listener {

    constructor(
        privateKey: String,
        isPasswordProtected: Boolean
    ) : this(
        bundleOf(
            PRIVATE_KEY to privateKey,
            PASSWORD_PROTECTED to isPasswordProtected
        )
    )

    override val layoutId = R.layout.controller_import_wallet

    override val init = ImportInit
    override val update = ImportUpdate
    override val defaultModel = M.createDefault(
        privateKey = argOptional(PRIVATE_KEY),
        isPasswordProtected = arg(PASSWORD_PROTECTED, false)
    )

    override val kodein by Kodein.lazy {
        extend(super.kodein)

        bind<WalletImporter>() with singleton { WalletImporter() }
    }

    override val flowEffectHandler
        get() = createImportHandler(
            direct.instance(),
            direct.instance()
        )

    override fun bindView(modelFlow: Flow<M>): Flow<E> {
        modelFlow
            .map { it.loadingState }
            .distinctUntilChanged()
            .onEach { state ->
                val isLoading = state != LoadingState.IDLE
                // Disable navigation
                scan_button.isEnabled = !isLoading
//                faq_button.isEnabled = !isLoading
                close_button.isEnabled = !isLoading

                // Set loading visibility
                scan_button.isGone = isLoading
                progressBar.isVisible = isLoading
                label_import_status.isVisible = isLoading

                // Set loading message
                val messageId = when (state) {
                    LoadingState.ESTIMATING,
                    LoadingState.VALIDATING -> R.string.Import_checking
                    LoadingState.SUBMITTING -> R.string.Import_importing
                    else -> null
                }
                messageId?.let(label_import_status::setText)
            }
            .launchIn(uiBindScope)

        return merge(
            close_button.clicks().map { E.OnCloseClicked },
//            faq_button.clicks().map { E.OnFaqClicked },
            scan_button.clicks().map { E.OnScanClicked }
        )
    }

    override fun handleBack(): Boolean {
        return currentModel.isLoading
    }

    override fun onPasswordConfirmed(password: String) {
        E.OnPasswordEntered(password)
            .run(eventConsumer::accept)
    }

    override fun onPasswordCancelled() {
        E.OnImportCancel
            .run(eventConsumer::accept)
    }

    override fun onLinkScanned(link: Link) {
        if (link is Link.ImportWallet) {
            E.OnKeyScanned(link.privateKey, link.passwordProtected)
                .run(eventConsumer::accept)
        }
    }

    override fun onPositiveClicked(
            dialogId: String,
            controller: AlertDialogController,
            result: AlertDialogController.DialogInputResult
    ) {
        when (dialogId) {
            CONFIRM_IMPORT_DIALOG -> E.OnImportConfirm
            IMPORT_SUCCESS_DIALOG -> E.OnCloseClicked
            else -> null
        }?.run(eventConsumer::accept)
    }

    override fun onDismissed(
            dialogId: String,
            controller: AlertDialogController,
            result: AlertDialogController.DialogInputResult
    ) {
        onDismissOrNegative(dialogId)
    }

    override fun onNegativeClicked(
            dialogId: String,
            controller: AlertDialogController,
            result: AlertDialogController.DialogInputResult
    ) {
        onDismissOrNegative(dialogId)
    }

    private fun onDismissOrNegative(dialogId: String) {
        when (dialogId) {
            CONFIRM_IMPORT_DIALOG -> E.OnImportCancel
            IMPORT_SUCCESS_DIALOG -> E.OnCloseClicked
            else -> null
        }?.run(eventConsumer::accept)
    }

    override fun handleViewEffect(effect: ViewEffect) {
        if (effect is F.ShowPasswordInput) {
            router.pushController(RouterTransaction.with(PasswordController()))
        }
    }
}
