/**
 * BreadWallet
 *
 * Created by Drew Carlson <drew.carlson@breadwallet.com> on 1/17/20.
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
package com.xwallet.ui.settings.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import com.bluelinelabs.conductor.RouterTransaction
import com.xwallet.BuildConfig
import com.xwallet.R
import com.xwallet.tools.manager.BRClipboardManager
import com.xwallet.tools.manager.BRSharedPrefs
import com.xwallet.tools.util.BRConstants
import com.xwallet.tools.util.EmailTarget
import com.xwallet.tools.util.SupportManager
import com.xwallet.ui.BaseController
import com.xwallet.ui.home.HomeController
import kotlinx.android.synthetic.main.controller_about.*
import org.kodein.di.erased.instance
import java.util.Locale

private const val VERSION_CLICK_COUNT_FOR_BACKDOOR = 5

class AboutController(args: Bundle? = null) : BaseController(args) {

    override val layoutId = R.layout.controller_about

    private var versionClickedCount = 0
    private val supportManager: SupportManager by instance()

    override fun onCreateView(view: View) {
        super.onCreateView(view)
        val res = checkNotNull(resources)

        back_button.setOnClickListener {
            router.popCurrentController()
        }

        info_text.text = String.format(
            Locale.getDefault(),
            res.getString(R.string.About_footer),
            BuildConfig.VERSION_NAME,
            BuildConfig.BUILD_VERSION
        )

        info_text.setOnClickListener {
            versionClickedCount++
            if (versionClickedCount >= VERSION_CLICK_COUNT_FOR_BACKDOOR) {
                versionClickedCount = 0
                supportManager.submitEmailRequest(EmailTarget.ANDROID_TEAM)
            }
        }

        reddit_share_button.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BRConstants.URL_REDDIT)))
        }
        twitter_share_button.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BRConstants.URL_TWITTER)))
        }
        blog_share_button.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BRConstants.URL_BLOG)))
        }
        policy_text.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BRConstants.URL_PRIVACY_POLICY)))
        }

//        brd_rewards_id.text = BRSharedPrefs.getWalletRewardId()
//        brd_copy.setOnClickListener {
//            BRClipboardManager.putClipboard(brd_rewards_id.text.toString())
//            toast(R.string.Receive_copied)
//        }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        versionClickedCount = 0
    }

    override fun handleBack(): Boolean {
        if (router.backstackSize == 1) {
            router.replaceTopController(RouterTransaction.with(HomeController()))
        }
        return super.handleBack()
    }
}
