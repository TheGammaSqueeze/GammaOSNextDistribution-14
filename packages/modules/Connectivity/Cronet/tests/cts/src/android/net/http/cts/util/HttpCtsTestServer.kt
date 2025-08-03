/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.webkit.cts.CtsTestServer
import java.net.URI
import org.apache.http.HttpEntityEnclosingRequest
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.HttpVersion
import org.apache.http.message.BasicHttpResponse

private const val ECHO_BODY_PATH = "/echo_body"

/** Extends CtsTestServer to handle POST requests and other test specific requests */
class HttpCtsTestServer(context: Context) : CtsTestServer(context) {

    val echoBodyUrl: String = baseUri + ECHO_BODY_PATH
    val successUrl: String = getAssetUrl("html/hello_world.html")

    override fun onPost(req: HttpRequest): HttpResponse? {
        val path = URI.create(req.requestLine.uri).path
        var response: HttpResponse? = null

        if (path.startsWith(ECHO_BODY_PATH)) {
            if (req !is HttpEntityEnclosingRequest) {
                return BasicHttpResponse(
                    HttpVersion.HTTP_1_0,
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Expected req to be of type HttpEntityEnclosingRequest but got ${req.javaClass}"
                )
            }

            response = BasicHttpResponse(HttpVersion.HTTP_1_0, HttpStatus.SC_OK, null)
            response.entity = req.entity
            response.addHeader("Content-Length", req.entity.contentLength.toString())
        }

        return response
    }
}
