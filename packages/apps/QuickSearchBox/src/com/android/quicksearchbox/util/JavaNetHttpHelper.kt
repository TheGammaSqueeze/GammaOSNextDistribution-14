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

import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** Simple HTTP client API. */
class JavaNetHttpHelper(rewriter: HttpHelper.UrlRewriter, userAgent: String) : HttpHelper {
  private var mConnectTimeout = 0
  private var mReadTimeout = 0
  private val mUserAgent: String
  private val mRewriter: HttpHelper.UrlRewriter

  /**
   * Executes a GET request and returns the response content.
   *
   * @param request Request.
   * @return The response content. This is the empty string if the response contained no content.
   * @throws IOException If an IO error occurs.
   * @throws HttpException If the response has a status code other than 200.
   */
  @Throws(IOException::class, HttpHelper.HttpException::class)
  override operator fun get(request: HttpHelper.GetRequest?): String? {
    return get(request?.url, request?.headers)
  }

  /**
   * Executes a GET request and returns the response content.
   *
   * @param url Request URI.
   * @param requestHeaders Request headers.
   * @return The response content. This is the empty string if the response contained no content.
   * @throws IOException If an IO error occurs.
   * @throws HttpException If the response has a status code other than 200.
   */
  @Throws(IOException::class, HttpHelper.HttpException::class)
  override operator fun get(url: String?, requestHeaders: MutableMap<String, String>?): String? {
    var c: HttpURLConnection? = null
    return try {
      c = createConnection(url!!, requestHeaders)
      c.setRequestMethod("GET")
      c.connect()
      getResponseFrom(c)
    } finally {
      if (c != null) {
        c.disconnect()
      }
    }
  }

  @Override
  @Throws(IOException::class, HttpHelper.HttpException::class)
  override fun post(request: HttpHelper.PostRequest?): String? {
    return post(request?.url, request?.headers, request?.content)
  }

  @Throws(IOException::class, HttpHelper.HttpException::class)
  override fun post(
    url: String?,
    requestHeaders: MutableMap<String, String>?,
    content: String?
  ): String? {
    var mRequestHeaders: MutableMap<String, String>? = requestHeaders
    var c: HttpURLConnection? = null
    return try {
      if (mRequestHeaders == null) {
        mRequestHeaders = mutableMapOf()
      }
      mRequestHeaders.put("Content-Length", Integer.toString(content?.length ?: 0))
      c = createConnection(url!!, mRequestHeaders)
      c.setDoOutput(content != null)
      c.setRequestMethod("POST")
      c.connect()
      if (content != null) {
        val writer = OutputStreamWriter(c.getOutputStream())
        writer.write(content)
        writer.close()
      }
      getResponseFrom(c)
    } finally {
      if (c != null) {
        c.disconnect()
      }
    }
  }

  @Throws(IOException::class, HttpHelper.HttpException::class)
  private fun createConnection(url: String, headers: Map<String, String>?): HttpURLConnection {
    val u = URL(mRewriter.rewrite(url))
    if (DBG) Log.d(TAG, "URL=$url rewritten='$u'")
    val c: HttpURLConnection = u.openConnection() as HttpURLConnection
    if (headers != null) {
      for (e in headers.entries) {
        val name: String = e.key
        val value: String = e.value
        if (DBG) Log.d(TAG, "  $name: $value")
        c.addRequestProperty(name, value)
      }
    }
    c.addRequestProperty(USER_AGENT_HEADER, mUserAgent)
    if (mConnectTimeout != 0) {
      c.setConnectTimeout(mConnectTimeout)
    }
    if (mReadTimeout != 0) {
      c.setReadTimeout(mReadTimeout)
    }
    return c
  }

  @Throws(IOException::class, HttpHelper.HttpException::class)
  private fun getResponseFrom(c: HttpURLConnection): String {
    if (c.getResponseCode() != HttpURLConnection.HTTP_OK) {
      throw HttpHelper.HttpException(c.getResponseCode(), c.getResponseMessage())
    }
    if (DBG) {
      Log.d(
        TAG,
        "Content-Type: " + c.getContentType().toString() + " (assuming " + DEFAULT_CHARSET + ")"
      )
    }
    val reader = BufferedReader(InputStreamReader(c.getInputStream(), DEFAULT_CHARSET))
    val string: StringBuilder = StringBuilder()
    val chars = CharArray(BUFFER_SIZE)
    var bytes: Int
    while (reader.read(chars).also { bytes = it } != -1) {
      string.append(chars, 0, bytes)
    }
    return string.toString()
  }

  override fun setConnectTimeout(timeoutMillis: Int) {
    mConnectTimeout = timeoutMillis
  }

  override fun setReadTimeout(timeoutMillis: Int) {
    mReadTimeout = timeoutMillis
  }

  /** A Url rewriter that does nothing, i.e., returns the url that is passed to it. */
  class PassThroughRewriter : HttpHelper.UrlRewriter {
    @Override
    override fun rewrite(url: String): String {
      return url
    }
  }

  companion object {
    private const val TAG = "QSB.JavaNetHttpHelper"
    private const val DBG = false
    private const val BUFFER_SIZE = 1024 * 4
    private const val USER_AGENT_HEADER = "User-Agent"
    private const val DEFAULT_CHARSET = "UTF-8"
  }

  /**
   * Creates a new HTTP helper.
   *
   * @param rewriter URI rewriter
   * @param userAgent User agent string, e.g. "MyApp/1.0".
   */
  init {
    mUserAgent = userAgent + " (" + Build.DEVICE + " " + Build.ID + ")"
    mRewriter = rewriter
  }
}
