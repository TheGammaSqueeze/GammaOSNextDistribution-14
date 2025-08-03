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
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.util.Log
import com.android.common.SharedPreferencesCompat

/** Manages user settings. */
class SearchSettingsImpl(context: Context?, config: Config?) : SearchSettings {
  private val mContext: Context?
  protected val config: Config?
  protected val context: Context?
    get() = mContext

  @Override override fun upgradeSettingsIfNeeded() {}

  val searchPreferences: SharedPreferences
    get() = context!!.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  protected fun storeBoolean(name: String?, value: Boolean) {
    SharedPreferencesCompat.apply(searchPreferences.edit().putBoolean(name, value))
  }

  protected fun storeInt(name: String?, value: Int) {
    SharedPreferencesCompat.apply(searchPreferences.edit().putInt(name, value))
  }

  protected fun storeLong(name: String?, value: Long) {
    SharedPreferencesCompat.apply(searchPreferences.edit().putLong(name, value))
  }

  protected fun storeString(name: String?, value: String?) {
    SharedPreferencesCompat.apply(searchPreferences.edit().putString(name, value))
  }

  protected fun removePref(name: String?) {
    SharedPreferencesCompat.apply(searchPreferences.edit().remove(name))
  }

  /** Informs our listeners about the updated settings data. */
  @Override
  override fun broadcastSettingsChanged() {
    // We use a message broadcast since the listeners could be in multiple processes.
    val intent = Intent(SearchManager.INTENT_ACTION_SEARCH_SETTINGS_CHANGED)
    Log.i(TAG, "Broadcasting: $intent")
    context?.sendBroadcast(intent)
  }

  @Override
  override fun getNextVoiceSearchHintIndex(size: Int): Int {
    val i = getAndIncrementIntPreference(searchPreferences, NEXT_VOICE_SEARCH_HINT_INDEX_PREF)
    return i % size
  }

  // TODO: Could this be made atomic to avoid races?
  private fun getAndIncrementIntPreference(prefs: SharedPreferences, name: String): Int {
    val i: Int = prefs.getInt(name, 0)
    storeInt(name, i + 1)
    return i
  }

  @Override
  override fun resetVoiceSearchHintFirstSeenTime() {
    storeLong(FIRST_VOICE_HINT_DISPLAY_TIME, System.currentTimeMillis())
  }

  @Override
  override fun haveVoiceSearchHintsExpired(currentVoiceSearchVersion: Int): Boolean {
    val prefs: SharedPreferences = searchPreferences
    return if (currentVoiceSearchVersion != 0) {
      val currentTime: Long = System.currentTimeMillis()
      val lastVoiceSearchVersion: Int = prefs.getInt(LAST_SEEN_VOICE_SEARCH_VERSION, 0)
      var firstHintTime: Long = prefs.getLong(FIRST_VOICE_HINT_DISPLAY_TIME, 0)
      if (firstHintTime == 0L || currentVoiceSearchVersion != lastVoiceSearchVersion) {
        SharedPreferencesCompat.apply(
          prefs
            .edit()
            .putInt(LAST_SEEN_VOICE_SEARCH_VERSION, currentVoiceSearchVersion)
            .putLong(FIRST_VOICE_HINT_DISPLAY_TIME, currentTime)
        )
        firstHintTime = currentTime
      }
      if (currentTime - firstHintTime > config!!.voiceSearchHintActivePeriod) {
        if (DBG) Log.d(TAG, "Voice search hint period expired; not showing hints.")
        return true
      } else {
        false
      }
    } else {
      if (DBG) Log.d(TAG, "Could not determine voice search version; not showing hints.")
      true
    }
  }

  /** @return true if user searches should always be based at google.com, false otherwise. */
  @Override
  override fun shouldUseGoogleCom(): Boolean {
    // Note that this preserves the old behaviour of using google.com
    // for searches, with the gl= parameter set.
    return searchPreferences.getBoolean(USE_GOOGLE_COM_PREF, true)
  }

  @Override
  override fun setUseGoogleCom(useGoogleCom: Boolean) {
    storeBoolean(USE_GOOGLE_COM_PREF, useGoogleCom)
  }

  @get:Override
  override val searchBaseDomainApplyTime: Long
    get() = searchPreferences.getLong(SEARCH_BASE_DOMAIN_APPLY_TIME, -1)

  // Note that the only time this will return null is on the first run
  // of the app, or when settings have been cleared. Callers should
  // ideally check that getSearchBaseDomainApplyTime() is not -1 before
  // calling this function.
  @get:Override
  @set:Override
  override var searchBaseDomain: String?
    get() = searchPreferences.getString(SEARCH_BASE_DOMAIN_PREF, null)
    set(searchBaseUrl) {
      val sharedPrefEditor: Editor = searchPreferences.edit()
      sharedPrefEditor.putString(SEARCH_BASE_DOMAIN_PREF, searchBaseUrl)
      sharedPrefEditor.putLong(SEARCH_BASE_DOMAIN_APPLY_TIME, System.currentTimeMillis())
      SharedPreferencesCompat.apply(sharedPrefEditor)
    }

  companion object {
    private const val DBG = false
    private const val TAG = "QSB.SearchSettingsImpl"

    // Name of the preferences file used to store search preference
    const val PREFERENCES_NAME = "SearchSettings"

    /** Preference key used for storing the index of the next voice search hint to show. */
    private const val NEXT_VOICE_SEARCH_HINT_INDEX_PREF = "next_voice_search_hint"

    /** Preference key used to store the time at which the first voice search hint was displayed. */
    private const val FIRST_VOICE_HINT_DISPLAY_TIME = "first_voice_search_hint_time"

    /** Preference key for the version of voice search we last got hints from. */
    private const val LAST_SEEN_VOICE_SEARCH_VERSION = "voice_search_version"

    /**
     * Preference key for storing whether searches always go to google.com. Public so that it can be
     * used by PreferenceControllers.
     */
    const val USE_GOOGLE_COM_PREF = "use_google_com"

    /**
     * Preference key for the base search URL. This value is normally set by a SearchBaseUrlHelper
     * instance. Public so classes can listen to changes on this key.
     */
    const val SEARCH_BASE_DOMAIN_PREF = "search_base_domain"

    /**
     * This is the time at which the base URL was stored, and is set using
     * @link{System.currentTimeMillis()}.
     */
    private const val SEARCH_BASE_DOMAIN_APPLY_TIME = "search_base_domain_apply_time"
  }

  init {
    mContext = context
    this.config = config
  }
}
