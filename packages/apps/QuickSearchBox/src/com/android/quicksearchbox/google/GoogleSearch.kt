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

import android.app.Activity
import android.app.PendingIntent
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import android.text.TextUtils
import android.util.Log
import com.android.common.Search
import com.android.quicksearchbox.QsbApplication
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.Locale

/**
 * This class is purely here to get search queries and route them to the global
 * [Intent.ACTION_WEB_SEARCH].
 */
class GoogleSearch : Activity() {
  // Used to figure out which domain to base search requests
  // on.
  private var mSearchDomainHelper: SearchBaseUrlHelper? = null

  @Override
  protected override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val intent: Intent? = getIntent()
    val action: String? = if (intent != null) intent.getAction() else null

    // This should probably be moved so as to
    // send out the request to /checksearchdomain as early as possible.
    mSearchDomainHelper = QsbApplication.get(this).searchBaseUrlHelper
    if (Intent.ACTION_WEB_SEARCH.equals(action) || Intent.ACTION_SEARCH.equals(action)) {
      handleWebSearchIntent(intent)
    }
    finish()
  }

  private fun handleWebSearchIntent(intent: Intent?) {
    val launchUriIntent: Intent? = createLaunchUriIntentFromSearchIntent(intent)

    @Suppress("DEPRECATION")
    val pending: PendingIntent? =
      intent?.getParcelableExtra(SearchManager.EXTRA_WEB_SEARCH_PENDINGINTENT)
    if (pending == null || !launchPendingIntent(pending, launchUriIntent)) {
      launchIntent(launchUriIntent)
    }
  }

  private fun createLaunchUriIntentFromSearchIntent(intent: Intent?): Intent? {
    val query: String? = intent?.getStringExtra(SearchManager.QUERY)
    if (TextUtils.isEmpty(query)) {
      Log.w(TAG, "Got search intent with no query.")
      return null
    }

    // If the caller specified a 'source' url parameter, use that and if not use default.
    val appSearchData: Bundle? = intent?.getBundleExtra(SearchManager.APP_DATA)
    var source: String? = GoogleSearch.Companion.GOOGLE_SEARCH_SOURCE_UNKNOWN
    if (appSearchData != null) {
      source = appSearchData.getString(Search.SOURCE)
    }

    // The browser can pass along an application id which it uses to figure out which
    // window to place a new search into. So if this exists, we'll pass it back to
    // the browser. Otherwise, add our own package name as the application id, so that
    // the browser can organize all searches launched from this provider together.
    var applicationId: String? = intent?.getStringExtra(Browser.EXTRA_APPLICATION_ID)
    if (applicationId == null) {
      applicationId = getPackageName()
    }
    return try {
      val searchUri =
        (mSearchDomainHelper!!.searchBaseUrl.toString() +
          "&source=android-" +
          source +
          "&q=" +
          URLEncoder.encode(query, "UTF-8"))
      val launchUriIntent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUri))
      launchUriIntent.putExtra(Browser.EXTRA_APPLICATION_ID, applicationId)
      launchUriIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      launchUriIntent
    } catch (e: UnsupportedEncodingException) {
      Log.w(TAG, "Error", e)
      null
    }
  }

  private fun launchIntent(intent: Intent?) {
    try {
      Log.i(TAG, "Launching intent: " + intent?.toUri(0))
      startActivity(intent)
    } catch (ex: ActivityNotFoundException) {
      Log.w(TAG, "No activity found to handle: $intent")
    }
  }

  private fun launchPendingIntent(pending: PendingIntent, fillIn: Intent?): Boolean {
    return try {
      pending.send(this, Activity.RESULT_OK, fillIn)
      true
    } catch (ex: PendingIntent.CanceledException) {
      Log.i(TAG, "Pending intent cancelled: $pending")
      false
    }
  }

  companion object {
    private const val TAG = "GoogleSearch"
    private const val DBG = false

    // "source" parameter for Google search requests from unknown sources (e.g. apps). This will get
    // prefixed with the string 'android-' before being sent on the wire.
    const val GOOGLE_SEARCH_SOURCE_UNKNOWN = "unknown"

    /** Construct the language code (hl= parameter) for the given locale. */
    fun getLanguage(locale: Locale): String {
      val language: String = locale.getLanguage()
      val hl: StringBuilder = StringBuilder(language)
      val country: String = locale.getCountry()
      if (!TextUtils.isEmpty(country) && useLangCountryHl(language, country)) {
        hl.append('-')
        hl.append(country)
      }
      if (DBG) Log.d(TAG, "language $language, country $country -> hl=$hl")
      return hl.toString()
    }

    // TODO: This is a workaround for bug 3232296. When that is fixed, this method can be removed.
    private fun useLangCountryHl(language: String, country: String): Boolean {
      // lang-country is currently only supported for a small number of locales
      return if ("en".equals(language)) {
        "GB".equals(country)
      } else if ("zh".equals(language)) {
        "CN".equals(country) || "TW".equals(country)
      } else if ("pt".equals(language)) {
        "BR".equals(country) || "PT".equals(country)
      } else {
        false
      }
    }
  }
}
