package com.xwallet.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.agoda.kakao.screen.Screen.Companion.onScreen
import com.xwallet.R
import com.xwallet.util.BaseTestCase
import com.xwallet.util.KHomeScreen
import com.xwallet.util.KIntroScreen
import com.xwallet.util.KWriteDownScreen
import com.xwallet.util.OnBoardingScreen
import com.xwallet.util.WebScreen
import com.xwallet.util.setApplicationPin
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class OnBoardingTests : BaseTestCase() {

    @Test
    fun testSkip() {
        return before {
            launchActivity()
        }.after { clearData() }.run {
            step("Click Get started") {
                onScreen<KIntroScreen> {
                    getStarted { click() }
                }
            }

            step("Click Skip") {
                onScreen<OnBoardingScreen> {
                    skip { click() }
                }
            }

            flakySafely {
                setApplicationPin()
            }

            step("Close write down phrase") {
                onScreen<KWriteDownScreen> {
                    close { click() }
                }
            }

            step("Confirm wallet is loaded") {
                onScreen<KHomeScreen> {
                    totalAssets {
                        hasAnyText()
                    }
                }
            }
        }
    }

    @Test
    fun testBrowse() {
        return before {
            launchActivity()
        }.after { clearData() }.run {
            step("Click Get started") {
                onScreen<KIntroScreen> {
                    getStarted { click() }
                }
            }

            step("Swipe to last page") {
                onScreen<OnBoardingScreen> {
                    pager {
                        isAtPage(0)
                        swipeLeft()
                        isAtPage(1)
                        swipeLeft()
                        isAtPage(2)
                    }
                }
            }

            step("Click browse") {
                onScreen<OnBoardingScreen> {
                    flakySafely {
                        lastScreenText { hasText(R.string.OnboardingPageFour_title) }
                    }
                    browse { click() }
                }
            }

            flakySafely {
                setApplicationPin()
            }

            step("Close write down phrase") {
                onScreen<KWriteDownScreen> {
                    close { click() }
                }
            }

            step("Confirm wallet is loaded") {
                onScreen<KHomeScreen> {
                    totalAssets {
                        hasAnyText()
                    }
                }
            }
        }
    }

    @Test
    fun testBuy() {
        return before {
            launchActivity()
        }.after { clearData() }.run {
            step("Click Get started") {
                onScreen<KIntroScreen> {
                    getStarted { click() }
                }
            }

            step("Swipe to last page") {
                onScreen<OnBoardingScreen> {
                    pager {
                        isAtPage(0)
                        swipeLeft()
                        isAtPage(1)
                        swipeLeft()
                        isAtPage(2)
                    }
                }
            }

            step("Click buy") {
                onScreen<OnBoardingScreen> {
                    flakySafely {
                        lastScreenText { hasText(R.string.OnboardingPageFour_title) }
                    }
                    buy { click() }
                }
            }

            flakySafely {
                setApplicationPin()
            }

            step("Close write down phrase") {
                onScreen<KWriteDownScreen> {
                    close { click() }
                }
            }

            step("Confirm buy screen is displayed") {
                onScreen<WebScreen> {
                    rootView!!.isDisplayed()
                }
            }
        }
    }
}
