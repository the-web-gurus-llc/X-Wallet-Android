/**
 * BreadWallet
 *
 * Created by Pablo Budelli <pablo.budelli@breadwallet.com> on 6/10/19.
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
package com.xwallet.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

/**
 * In-app message model.
 */
@Parcelize
data class InAppMessage(
        val id: String,
        val type: Type,
        val messageId: String,
        val title: String,
        val body: String,
        val actionButtonText: String?,
        val actionButtonUrl: String?,
        val imageUrl: String?
) : Parcelable {

    enum class Type {
        IN_APP_NOTIFICATION, // Notifications that will be shown while the user is using the app.
        UNKNOWN;

        companion object {
            fun fromString(value: String): Type = when (value) {
                "inApp" -> IN_APP_NOTIFICATION
                else -> UNKNOWN
            }
        }
    }
}