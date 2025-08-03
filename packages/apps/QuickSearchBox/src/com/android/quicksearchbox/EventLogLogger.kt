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

import android.content.Context
import android.util.EventLog
import java.util.Random
import kotlin.text.StringBuilder

/** Logs events to [EventLog]. */
class EventLogLogger(context: Context?, config: Config) : Logger {
  private val mContext: Context?
  protected val config: Config
  private val mPackageName: String
  private val mRandom: Random
  protected val context: Context?
    get() = mContext
  protected val versionCode: Long
    get() = QsbApplication.get(context).versionCode

  @Override
  override fun logStart(onCreateLatency: Int, latency: Int, intentSource: String?) {
    // TODO: Add more info to startMethod
    EventLogTags.writeQsbStart(
      mPackageName,
      versionCode.toInt(),
      intentSource,
      latency,
      null,
      null,
      onCreateLatency
    )
  }

  @Override
  override fun logSuggestionClick(
    suggestionId: Long,
    suggestionCursor: SuggestionCursor?,
    clickType: Int
  ) {
    val suggestions = getSuggestions(suggestionCursor)
    val numChars: Int = suggestionCursor!!.userQuery!!.length
    EventLogTags.writeQsbClick(suggestionId, suggestions, null, numChars, clickType)
  }

  @Override
  override fun logSearch(startMethod: Int, numChars: Int) {
    EventLogTags.writeQsbSearch(null, startMethod, numChars)
  }

  @Override
  override fun logVoiceSearch() {
    EventLogTags.writeQsbVoiceSearch(null)
  }

  @Override
  override fun logExit(suggestionCursor: SuggestionCursor?, numChars: Int) {
    val suggestions = getSuggestions(suggestionCursor)
    EventLogTags.writeQsbExit(suggestions, numChars)
  }

  @Override override fun logLatency(result: SourceResult?) {}

  private fun getSuggestions(cursor: SuggestionCursor?): String {
    val sb = StringBuilder()
    val count = cursor?.count ?: 0
    for (i in 0 until count) {
      if (i > 0) sb.append(LIST_SEPARATOR)
      cursor!!.moveTo(i)
      val source: String? = cursor.suggestionSource?.name
      var type: String? = cursor.suggestionLogType
      if (type == null) type = ""
      val shortcut = if (cursor.isSuggestionShortcut) "shortcut" else ""
      sb.append(source).append(":").append(type).append(":").append(shortcut)
    }
    return sb.toString()
  }

  companion object {
    private const val LIST_SEPARATOR = '|'
  }

  init {
    mContext = context
    this.config = config
    mPackageName = mContext!!.getPackageName()
    mRandom = Random()
  }
}
