/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.testutils

import android.net.Uri
import com.android.net.module.util.ArrayTrackRecord
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * A minimal HTTP server running on a random available port.
 *
 * @param host The host to listen to, or null to listen on all hosts
 * @param port The port to listen to, or 0 to auto select
 */
class TestHttpServer
    @JvmOverloads constructor(host: String? = null, port: Int = 0) : NanoHTTPD(host, port) {
    // Map of URL path -> HTTP response code
    private val responses = HashMap<Request, Response>()

    /**
     * A record of all requests received by the server since it was started.
     */
    val requestsRecord = ArrayTrackRecord<Request>()

    /**
     * A request received by the test server.
     */
    data class Request(
        val path: String,
        val method: Method = Method.GET,
        val queryParameters: String = ""
    ) {
        /**
         * Returns whether the specified [Uri] matches parameters of this request.
         */
        fun matches(uri: Uri) = (uri.path ?: "") == path && (uri.query ?: "") == queryParameters
    }

    /**
     * Add a response for GET requests with the path and query parameters of the specified [Uri].
     */
    fun addResponse(
        uri: Uri,
        statusCode: Response.IStatus,
        headers: Map<String, String>? = null,
        content: String = ""
    ) {
        addResponse(Request(uri.path
                ?: "", Method.GET, uri.query ?: ""),
                statusCode, headers, content)
    }

    /**
     * Add a response for the given request.
     */
    fun addResponse(
        request: Request,
        statusCode: Response.IStatus,
        headers: Map<String, String>? = null,
        content: String = ""
    ) {
        val response = newFixedLengthResponse(statusCode, "text/plain", content)
        headers?.forEach {
            (key, value) -> response.addHeader(key, value)
        }
        responses[request] = response
    }

    override fun serve(session: IHTTPSession): Response {
        val request = Request(session.uri
                ?: "", session.method, session.queryParameterString ?: "")
        requestsRecord.add(request)

        // For PUT and POST, call parseBody to read InputStream before responding.
        if (Method.PUT == session.method || Method.POST == session.method) {
            try {
                session.parseBody(HashMap())
            } catch (e: Exception) {
                when (e) {
                    is IOException, is ResponseException -> e.toResponse()
                    else -> throw e
                }
            }
        }

        // Default response is a 404
        return responses[request] ?: super.serve(session)
    }

    fun Exception.toResponse() =
        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", this.toString())
}
