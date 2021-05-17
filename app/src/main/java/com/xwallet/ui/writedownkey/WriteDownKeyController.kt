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
package com.xwallet.ui.writedownkey

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import com.xwallet.R
import com.xwallet.tools.util.BRConstants
import com.xwallet.ui.BaseMobiusController
import com.xwallet.ui.auth.AuthenticationController
import com.xwallet.ui.flowbind.clicks
import com.xwallet.ui.navigation.OnCompleteAction
import com.xwallet.ui.writedownkey.WriteDownKey.E
import com.xwallet.ui.writedownkey.WriteDownKey.F
import com.xwallet.ui.writedownkey.WriteDownKey.M
import kotlinx.android.synthetic.main.controller_write_down.*
import kotlinx.android.synthetic.main.email_prompt.close_button
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import org.kodein.di.direct
import org.kodein.di.erased.instance

class WriteDownKeyController(args: Bundle? = null) :
    BaseMobiusController<M, E, F>(args),
    AuthenticationController.Listener {

    companion object {
        private const val EXTRA_ON_COMPLETE = "on-complete"
        private const val EXTRA_REQUEST_AUTH = "request-brd-auth"
    }

    constructor(
            doneAction: OnCompleteAction,
            requestAuth: Boolean
    ) : this(
        bundleOf(
            EXTRA_ON_COMPLETE to doneAction.name,
            EXTRA_REQUEST_AUTH to requestAuth
        )
    )

    init {
        registerForActivityResult(BRConstants.SHOW_PHRASE_REQUEST_CODE)
    }

    private val onComplete = OnCompleteAction.valueOf(arg(EXTRA_ON_COMPLETE))
    private val requestAuth = arg<Boolean>(EXTRA_REQUEST_AUTH)

    override val layoutId: Int = R.layout.controller_write_down
    override val defaultModel = M.createDefault(onComplete, requestAuth)
    override val update = WriteDownKeyUpdate
    override val flowEffectHandler
        get() = createWriteDownKeyHandler(direct.instance())

    override fun bindView(modelFlow: Flow<M>): Flow<E> = merge(
//        faq_button.clicks().map { E.OnFaqClicked },
        close_button.clicks().map { E.OnCloseClicked },
        button_write_down.clicks().map { E.OnWriteDownClicked }
    )

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BRConstants.SHOW_PHRASE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            eventConsumer.accept(E.OnUserAuthenticated)
        }
    }

    override fun onAuthenticationSuccess() {
        eventConsumer.accept(E.OnUserAuthenticated)
    }
}
