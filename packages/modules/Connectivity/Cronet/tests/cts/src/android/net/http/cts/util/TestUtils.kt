/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.http.cts.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.http.UrlResponseInfo
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeThat

fun skipIfNoInternetConnection(context: Context) {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    assumeNotNull(
        "This test requires a working Internet connection", connectivityManager!!.activeNetwork
    )
}

fun assertOKStatusCode(info: UrlResponseInfo) {
    assertEquals("Status code must be 200 OK", 200, info.httpStatusCode)
}

fun assumeOKStatusCode(info: UrlResponseInfo) {
    assumeThat("Status code must be 200 OK", info.httpStatusCode, equalTo(200))
}
