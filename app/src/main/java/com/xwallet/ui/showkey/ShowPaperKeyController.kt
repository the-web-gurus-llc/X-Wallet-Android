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
package com.xwallet.ui.showkey

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.support.RouterPagerAdapter
import com.xwallet.R
import com.xwallet.logger.logInfo
import com.xwallet.ui.BaseController
import com.xwallet.ui.BaseMobiusController
import com.xwallet.ui.flowbind.clicks
import com.xwallet.ui.navigation.OnCompleteAction
import com.xwallet.ui.showkey.ShowPaperKey.E
import com.xwallet.ui.showkey.ShowPaperKey.F
import com.xwallet.ui.showkey.ShowPaperKey.M
import com.xwallet.util.DefaultOnPageChangeListener
import drewcarlson.mobius.flow.subtypeEffectHandler
import kotlinx.android.synthetic.main.controller_paper_key.*
import kotlinx.android.synthetic.main.fragment_word_item.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

class ShowPaperKeyController(args: Bundle) : BaseMobiusController<M, E, F>(args) {

    companion object {
        private const val EXTRA_PHRASE = "phrase"
        private const val EXTRA_ON_COMPLETE = "on-complete"
        private const val NAVIGATION_BUTTONS_WEIGHT = 1
        private const val BUTTONS_LAYOUT_WEIGHT_SUM_DEFAULT = 2.0f
        private const val BUTTONS_LAYOUT_WEIGHT_SUM_SINGLE = 1.0f
    }

    constructor(
        phrase: List<String>,
        onComplete: OnCompleteAction? = null
    ) : this(
        bundleOf(
            EXTRA_PHRASE to phrase,
            EXTRA_ON_COMPLETE to onComplete?.name
        )
    )

    private val phrase: List<String> = arg(EXTRA_PHRASE)
    private val onComplete = argOptional<String>(EXTRA_ON_COMPLETE)
        ?.run(OnCompleteAction::valueOf)

    override val layoutId = R.layout.controller_paper_key
    override val defaultModel = M.createDefault(phrase, onComplete)
    override val update = ShowPaperKeyUpdate
    override val flowEffectHandler = subtypeEffectHandler<F, E> { }

    override fun bindView(modelFlow: Flow<M>): Flow<E> {
        return merge(
            next_button.clicks().map { E.OnNextClicked },
            previous_button.clicks().map { E.OnPreviousClicked },
            close_button.clicks().map { E.OnCloseClicked },
            callbackFlow<E> {
                val listener = object : DefaultOnPageChangeListener() {
                    override fun onPageSelected(position: Int) {
                        logInfo("phrase: phrase: " + phrase);

                        offer(E.OnPageChanged(position))
                    }
                }
                words_pager.addOnPageChangeListener(listener)
                awaitClose {
                    words_pager.removeOnPageChangeListener(listener)
                }
            }
        )
    }

    override fun M.render() {
        ifChanged(M::phrase) {
            words_pager.adapter = WordPagerAdapter(this@ShowPaperKeyController, phrase)
        }
        ifChanged(M::currentWord) {
            words_pager.currentItem = currentWord
            item_index.text = resources?.getString(
                R.string.WritePaperPhrase_step,
                currentWord + 1,
                phrase.size
            )
            updateButtons(currentWord > 0)
        }
    }

    /** Show or hide the "Previous" button used to navigate the ViewPager. */
    private fun updateButtons(showPrevious: Boolean) {
        val nextButtonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        if (!showPrevious) {
            buttons_layout.weightSum = BUTTONS_LAYOUT_WEIGHT_SUM_SINGLE

            nextButtonParams.weight = NAVIGATION_BUTTONS_WEIGHT.toFloat()
            nextButtonParams.gravity = Gravity.CENTER_HORIZONTAL
            nextButtonParams.setMargins(
                resources!!.getDimension(R.dimen.margin).toInt(),
                0,
                resources!!.getDimension(R.dimen.margin).toInt(),
                0
            )
            next_button.layoutParams = nextButtonParams
            next_button.height = resources!!.getDimension(R.dimen.large_button_height).toInt()

            previous_button.visibility = View.GONE
        } else {
            buttons_layout.weightSum = BUTTONS_LAYOUT_WEIGHT_SUM_DEFAULT

            nextButtonParams.weight = NAVIGATION_BUTTONS_WEIGHT.toFloat()
            nextButtonParams.setMargins(0, 0, resources!!.getDimension(R.dimen.margin).toInt(), 0)
            next_button.layoutParams = nextButtonParams
            next_button.height = resources!!.getDimension(R.dimen.large_button_height).toInt()

            val previousButtonParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            previousButtonParams.weight = NAVIGATION_BUTTONS_WEIGHT.toFloat()
            previousButtonParams.setMargins(
                resources!!.getDimension(R.dimen.margin).toInt(),
                0,
                0,
                0
            )
            previous_button.layoutParams = previousButtonParams
            previous_button.visibility = View.VISIBLE
            previous_button.height = resources!!.getDimension(R.dimen.large_button_height).toInt()
        }
    }
}

class WordPagerAdapter(
    host: Controller,
    private val words: List<String>
) : RouterPagerAdapter(host) {
    override fun configureRouter(router: Router, position: Int) {
        router.replaceTopController(RouterTransaction.with(WordController(words[position])))
    }

    override fun getCount() = words.size
}

class WordController(args: Bundle? = null) : BaseController(args) {
    companion object {
        private const val EXT_WORD = "word"
    }

    constructor(word: String) : this(
        bundleOf(EXT_WORD to word)
    )

    override val layoutId = R.layout.fragment_word_item
    override fun onCreateView(view: View) {
        super.onCreateView(view)
        word_button.text = arg(EXT_WORD)
    }
}
