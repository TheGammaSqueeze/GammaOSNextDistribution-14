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
package com.android.quicksearchbox

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log

/** Voice Search integration. */
class VoiceSearch(context: Context?) {

  private val mContext: Context?

  protected val context: Context?
    get() = mContext

  fun shouldShowVoiceSearch(): Boolean {
    return isVoiceSearchAvailable
  }

  protected fun createVoiceSearchIntent(): Intent {
    return Intent(RecognizerIntent.ACTION_WEB_SEARCH)
  }

  private val resolveInfo: ResolveInfo?
    @Suppress("DEPRECATION")
    get() {
      val intent: Intent = createVoiceSearchIntent()
      return mContext
        ?.getPackageManager()
        ?.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }
  val isVoiceSearchAvailable: Boolean
    get() = resolveInfo != null

  fun createVoiceWebSearchIntent(appData: Bundle?): Intent? {
    if (!isVoiceSearchAvailable) return null
    val intent: Intent = createVoiceSearchIntent()
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.putExtra(
      RecognizerIntent.EXTRA_LANGUAGE_MODEL,
      RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH
    )
    if (appData != null) {
      intent.putExtra(SearchManager.APP_DATA, appData)
    }
    return intent
  }

  /**
   * Create an intent to launch the voice search help screen, if any exists.
   * @return The intent, or null.
   */
  fun createVoiceSearchHelpIntent(): Intent? {
    return null
  }

  /**
   * Gets the `versionCode` of the currently installed voice search package.
   *
   * @return The `versionCode` of voiceSearch, or 0 if none is installed.
   */
  val version: Long
    @Suppress("DEPRECATION")
    get() {
      val ri: ResolveInfo = resolveInfo ?: return 0
      val ci: ComponentInfo = if (ri.activityInfo != null) ri.activityInfo else ri.serviceInfo
      return try {
        context!!.getPackageManager().getPackageInfo(ci.packageName, 0).getLongVersionCode()
      } catch (e: NameNotFoundException) {
        Log.e(TAG, "Cannot find voice search package " + ci.packageName, e)
        0
      }
    }
  val component: ComponentName
    get() = createVoiceSearchIntent().resolveActivity(context!!.getPackageManager())

  companion object {
    private const val TAG = "QSB.VoiceSearch"
  }

  init {
    mContext = context
  }
}
