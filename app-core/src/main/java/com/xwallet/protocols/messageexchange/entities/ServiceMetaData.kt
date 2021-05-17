/**
 * BreadWallet
 *
 * Created by Mihail Gutan on <mihail@breadwallet.com> 7/18/18.
 * Copyright (c) 2018 breadwallet LLC
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
package com.xwallet.protocols.messageexchange.entities

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.util.HashMap

@Parcelize
class ServiceMetaData(
    var url: String,
    var name: String,
    var hash: String,
    var createdTime: String,
    var updatedTime: String,
    var logoUrl: String,
    var description: String,
    var domains: List<String>,
    var capabilities: List<Capability>
) : MetaData("") {

    @Parcelize
    class Capability(
        var name: String,
        var scopes: HashMap<String, String>,
        var description: String
    ) : Parcelable
}