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
package com.android.quicksearchbox.util

import java.io.IOException

/** An interface that can issue HTTP GET / POST requests with timeouts. */
interface HttpHelper {
  @Throws(IOException::class, HttpException::class) operator fun get(request: GetRequest?): String?

  @Throws(IOException::class, HttpException::class)
  operator fun get(url: String?, requestHeaders: MutableMap<String, String>?): String?

  @Throws(IOException::class, HttpException::class) fun post(request: PostRequest?): String?

  @Throws(IOException::class, HttpException::class)
  fun post(url: String?, requestHeaders: MutableMap<String, String>?, content: String?): String?
  fun setConnectTimeout(timeoutMillis: Int)
  fun setReadTimeout(timeoutMillis: Int)
  open class GetRequest {
    /** Gets the request URI. */
    /** Sets the request URI. */
    var url: String? = null

    /**
     * Gets the request headers.
     *
     * @return The response headers. May return `null` if no headers are set.
     */
    var headers: MutableMap<String, String>? = null
      private set

    /** Creates a new request. */
    constructor()

    /**
     * Creates a new request.
     *
     * @param url Request URI.
     */
    constructor(url: String?) {
      this.url = url
    }

    /**
     * Sets a request header.
     *
     * @param name Header name.
     * @param value Header value.
     */
    fun setHeader(name: String, value: String) {
      if (headers == null) {
        headers = mutableMapOf()
      }
      headers?.put(name, value)
    }
  }

  class PostRequest : GetRequest {
    var content: String? = null

    constructor()
    constructor(url: String?) : super(url)
  }

  /** A HTTP exception. */
  class HttpException(
    /** Gets the HTTP response status code. */
    val statusCode: Int,
    /** Gets the HTTP response reason phrase. */
    val reasonPhrase: String
  ) : IOException("$statusCode $reasonPhrase")

  /** An interface for URL rewriting. */
  interface UrlRewriter {
    fun rewrite(url: String): String
  }
}
