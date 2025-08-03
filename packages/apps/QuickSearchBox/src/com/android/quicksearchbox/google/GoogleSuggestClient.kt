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
package com.android.quicksearchbox.google

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import com.android.quicksearchbox.Config
import com.android.quicksearchbox.R
import com.android.quicksearchbox.Source
import com.android.quicksearchbox.SourceResult
import com.android.quicksearchbox.SuggestionCursor
import com.android.quicksearchbox.util.NamedTaskExecutor
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException

/** Use network-based Google Suggests to provide search suggestions. */
class GoogleSuggestClient(
  context: Context?,
  uiThread: Handler?,
  iconLoader: NamedTaskExecutor,
  config: Config
) : AbstractGoogleSource(context, uiThread, iconLoader) {
  private var mSuggestUri: String?
  private val mConnectTimeout: Int

  @get:Override
  override val intentComponent: ComponentName
    get() = ComponentName(context!!, GoogleSearch::class.java)

  @Override
  override fun queryInternal(query: String?): SourceResult? {
    return query(query)
  }

  @Override
  override fun queryExternal(query: String?): SourceResult? {
    return query(query)
  }

  /**
   * Queries for a given search term and returns a cursor containing suggestions ordered by best
   * match.
   */
  private fun query(query: String?): SourceResult? {
    if (TextUtils.isEmpty(query)) {
      return null
    }
    if (!isNetworkConnected) {
      Log.i(LOG_TAG, "Not connected to network.")
      return null
    }
    var connection: HttpURLConnection? = null
    try {
      val encodedQuery: String = URLEncoder.encode(query, "UTF-8")
      if (mSuggestUri == null) {
        val l: Locale = Locale.getDefault()
        val language: String = GoogleSearch.getLanguage(l)
        mSuggestUri = context?.getResources()!!.getString(R.string.google_suggest_base, language)
      }
      val suggestUri = mSuggestUri + encodedQuery
      if (DBG) Log.d(LOG_TAG, "Sending request: $suggestUri")
      val url: URL = URI.create(suggestUri).toURL()
      connection = url.openConnection() as HttpURLConnection
      connection.setConnectTimeout(mConnectTimeout)
      connection.setRequestProperty("User-Agent", USER_AGENT)
      connection.setRequestMethod("GET")
      connection.setDoInput(true)
      connection.connect()
      val inputStream: InputStream = connection.getInputStream()
      if (connection.getResponseCode() == 200) {

        /* Goto http://www.google.com/complete/search?json=true&q=foo
         * to see what the data format looks like. It's basically a json
         * array containing 4 other arrays. We only care about the middle
         * 2 which contain the suggestions and their popularity.
         */
        val reader = BufferedReader(InputStreamReader(inputStream))
        val sb: StringBuilder = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
          sb.append(line).append("\n")
        }
        reader.close()
        val results = JSONArray(sb.toString())
        val suggestions: JSONArray = results.getJSONArray(1)
        val popularity: JSONArray = results.getJSONArray(2)
        if (DBG) Log.d(LOG_TAG, "Got " + suggestions.length().toString() + " results")
        return GoogleSuggestCursor(this, query, suggestions, popularity)
      } else {
        if (DBG) Log.d(LOG_TAG, "Request failed " + connection.getResponseMessage())
      }
    } catch (e: UnsupportedEncodingException) {
      Log.w(LOG_TAG, "Error", e)
    } catch (e: IOException) {
      Log.w(LOG_TAG, "Error", e)
    } catch (e: JSONException) {
      Log.w(LOG_TAG, "Error", e)
    } finally {
      if (connection != null) connection.disconnect()
    }
    return null
  }

  @Override
  override fun refreshShortcut(shortcutId: String?, extraData: String?): SuggestionCursor? {
    return null
  }

  private val isNetworkConnected: Boolean
    get() {
      val actNC = activeNetworkCapabilities
      return actNC != null && actNC.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
  private val activeNetworkCapabilities: NetworkCapabilities?
    get() {
      val connectivityManager =
        context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
      val activeNetwork = connectivityManager.getActiveNetwork()
      return connectivityManager.getNetworkCapabilities(activeNetwork)
    }

  private class GoogleSuggestCursor(
    source: Source,
    userQuery: String?,
    suggestions: JSONArray,
    popularity: JSONArray
  ) : AbstractGoogleSourceResult(source, userQuery!!) {
    /* Contains the actual suggestions */
    private val mSuggestions: JSONArray

    /* This contains the popularity of each suggestion
     * i.e. 165,000 results. It's not related to sorting.
     */
    private val mPopularity: JSONArray

    @get:Override
    override val count: Int
      get() = mSuggestions.length()

    @get:Override
    override val suggestionQuery: String?
      get() =
        try {
          mSuggestions.getString(position)
        } catch (e: JSONException) {
          Log.w(LOG_TAG, "Error parsing response: $e")
          null
        }

    @get:Override
    override val suggestionText2: String?
      get() =
        try {
          mPopularity.getString(position)
        } catch (e: JSONException) {
          Log.w(LOG_TAG, "Error parsing response: $e")
          null
        }

    init {
      mSuggestions = suggestions
      mPopularity = popularity
    }
  }

  companion object {
    private const val DBG = false
    private const val LOG_TAG = "GoogleSearch"
    private val USER_AGENT = "Android/" + Build.VERSION.RELEASE

    // TODO: this should be defined somewhere
    private const val HTTP_TIMEOUT = "http.conn-manager.timeout"
  }

  init {
    mConnectTimeout = config.httpConnectTimeout
    // NOTE:  Do not look up the resource here;  Localization changes may not have completed
    // yet (e.g. we may still be reading the SIM card).
    mSuggestUri = null
  }
}
