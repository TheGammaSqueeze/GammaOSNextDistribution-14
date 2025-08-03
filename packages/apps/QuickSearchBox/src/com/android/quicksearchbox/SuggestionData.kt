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

import android.content.ComponentName
import android.content.Intent
import com.google.common.annotations.VisibleForTesting

/**
 * Holds data for each suggest item including the display data and how to launch the result. Used
 * for passing from the provider to the suggest cursor.
 */
class SuggestionData(override val suggestionSource: Source?) : Suggestion {
  private var mFormat: String? = null
  private var mText1: String? = null
  private var mText2: String? = null
  private var mText2Url: String? = null
  private var mIcon1: String? = null
  private var mIcon2: String? = null
  private var mShortcutId: String? = null
  override var isSpinnerWhileRefreshing = false
    private set
  private var mIntentAction: String? = null
  private var mIntentData: String? = null
  var intentExtraData: String? = null
    private set
  private var mSuggestionQuery: String? = null
  private var mLogType: String? = null
  override var isSuggestionShortcut = false
    private set
  override var isHistorySuggestion = false
    private set
  private var mExtras: SuggestionExtras? = null
  override val suggestionFormat: String
    get() = mFormat!!
  override val suggestionText1: String
    get() = mText1!!
  override val suggestionText2: String
    get() = mText2!!
  override val suggestionText2Url: String
    get() = mText2Url!!
  override val suggestionIcon1: String
    get() = mIcon1!!
  override val suggestionIcon2: String
    get() = mIcon2!!
  override val shortcutId: String
    get() = mShortcutId!!
  override val suggestionIntentAction: String?
    get() = mIntentAction ?: suggestionSource?.defaultIntentAction
  override val suggestionIntentComponent: ComponentName?
    get() = suggestionSource?.intentComponent
  override val suggestionIntentDataString: String
    get() = mIntentData!!
  override val suggestionIntentExtraData: String
    get() = intentExtraData!!
  override val suggestionQuery: String
    get() = mSuggestionQuery!!
  override val suggestionLogType: String
    get() = mLogType!!
  override val isWebSearchSuggestion: Boolean
    get() = Intent.ACTION_WEB_SEARCH.equals(suggestionIntentAction)

  @VisibleForTesting
  fun setFormat(format: String?): SuggestionData {
    mFormat = format
    return this
  }

  @VisibleForTesting
  fun setText1(text1: String?): SuggestionData {
    mText1 = text1
    return this
  }

  @VisibleForTesting
  fun setText2(text2: String?): SuggestionData {
    mText2 = text2
    return this
  }

  @VisibleForTesting
  fun setText2Url(text2Url: String?): SuggestionData {
    mText2Url = text2Url
    return this
  }

  @VisibleForTesting
  fun setIcon1(icon1: String?): SuggestionData {
    mIcon1 = icon1
    return this
  }

  @VisibleForTesting
  fun setIcon2(icon2: String?): SuggestionData {
    mIcon2 = icon2
    return this
  }

  @VisibleForTesting
  fun setIntentAction(intentAction: String?): SuggestionData {
    mIntentAction = intentAction
    return this
  }

  @VisibleForTesting
  fun setIntentData(intentData: String?): SuggestionData {
    mIntentData = intentData
    return this
  }

  @VisibleForTesting
  fun setIntentExtraData(intentExtraData: String?): SuggestionData {
    this.intentExtraData = intentExtraData
    return this
  }

  @VisibleForTesting
  fun setSuggestionQuery(suggestionQuery: String?): SuggestionData {
    mSuggestionQuery = suggestionQuery
    return this
  }

  @VisibleForTesting
  fun setShortcutId(shortcutId: String?): SuggestionData {
    mShortcutId = shortcutId
    return this
  }

  @VisibleForTesting
  fun setSpinnerWhileRefreshing(spinnerWhileRefreshing: Boolean): SuggestionData {
    isSpinnerWhileRefreshing = spinnerWhileRefreshing
    return this
  }

  @VisibleForTesting
  fun setSuggestionLogType(logType: String?): SuggestionData {
    mLogType = logType
    return this
  }

  @VisibleForTesting
  fun setIsShortcut(isShortcut: Boolean): SuggestionData {
    isSuggestionShortcut = isShortcut
    return this
  }

  @VisibleForTesting
  fun setIsHistory(isHistory: Boolean): SuggestionData {
    isHistorySuggestion = isHistory
    return this
  }

  @Override
  override fun hashCode(): Int {
    val prime = 31
    var result = 1
    result = prime * result + if (mFormat == null) 0 else mFormat.hashCode()
    result = prime * result + if (mIcon1 == null) 0 else mIcon1.hashCode()
    result = prime * result + if (mIcon2 == null) 0 else mIcon2.hashCode()
    result = prime * result + if (mIntentAction == null) 0 else mIntentAction.hashCode()
    result = prime * result + if (mIntentData == null) 0 else mIntentData.hashCode()
    result = prime * result + if (intentExtraData == null) 0 else intentExtraData.hashCode()
    result = prime * result + if (mLogType == null) 0 else mLogType.hashCode()
    result = prime * result + if (mShortcutId == null) 0 else mShortcutId.hashCode()
    result = prime * result + if (suggestionSource == null) 0 else suggestionSource.hashCode()
    result = prime * result + if (isSpinnerWhileRefreshing) 1231 else 1237
    result = prime * result + if (mSuggestionQuery == null) 0 else mSuggestionQuery.hashCode()
    result = prime * result + if (mText1 == null) 0 else mText1.hashCode()
    result = prime * result + if (mText2 == null) 0 else mText2.hashCode()
    return result
  }

  @Override
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null) return false
    if (this::class !== other::class) return false
    val suggestionData = other as SuggestionData
    if (mFormat == null) {
      if (suggestionData.mFormat != null) return false
    } else if (!mFormat.equals(suggestionData.mFormat)) return false
    if (mIcon1 == null) {
      if (suggestionData.mIcon1 != null) return false
    } else if (!mIcon1.equals(suggestionData.mIcon1)) return false
    if (mIcon2 == null) {
      if (suggestionData.mIcon2 != null) return false
    } else if (!mIcon2.equals(suggestionData.mIcon2)) return false
    if (mIntentAction == null) {
      if (suggestionData.mIntentAction != null) return false
    } else if (!mIntentAction.equals(suggestionData.mIntentAction)) return false
    if (mIntentData == null) {
      if (suggestionData.mIntentData != null) return false
    } else if (!mIntentData.equals(suggestionData.mIntentData)) return false
    if (intentExtraData == null) {
      if (suggestionData.intentExtraData != null) return false
    } else if (!intentExtraData.equals(suggestionData.intentExtraData)) return false
    if (mLogType == null) {
      if (suggestionData.mLogType != null) return false
    } else if (!mLogType.equals(suggestionData.mLogType)) return false
    if (mShortcutId == null) {
      if (suggestionData.mShortcutId != null) return false
    } else if (!mShortcutId.equals(suggestionData.mShortcutId)) return false
    if (suggestionSource == null) {
      if (suggestionData.suggestionSource != null) return false
    } else if (!suggestionSource.equals(suggestionData.suggestionSource)) return false
    if (isSpinnerWhileRefreshing != suggestionData.isSpinnerWhileRefreshing) return false
    if (mSuggestionQuery == null) {
      if (suggestionData.mSuggestionQuery != null) return false
    } else if (!mSuggestionQuery.equals(suggestionData.mSuggestionQuery)) return false
    if (mText1 == null) {
      if (suggestionData.mText1 != null) return false
    } else if (!mText1.equals(suggestionData.mText1)) return false
    if (mText2 == null) {
      if (suggestionData.mText2 != null) return false
    } else if (!mText2.equals(suggestionData.mText2)) return false
    return true
  }

  /**
   * Returns a string representation of the contents of this SuggestionData, for debugging purposes.
   */
  @Override
  override fun toString(): String {
    val builder: StringBuilder = StringBuilder("SuggestionData(")
    appendField(builder, "source", suggestionSource!!.name)
    appendField(builder, "text1", mText1)
    appendField(builder, "intentAction", mIntentAction)
    appendField(builder, "intentData", mIntentData)
    appendField(builder, "query", mSuggestionQuery)
    appendField(builder, "shortcutid", mShortcutId)
    appendField(builder, "logtype", mLogType)
    return builder.toString()
  }

  private fun appendField(builder: StringBuilder, name: String, value: String?) {
    if (value != null) {
      builder.append(",").append(name).append("=").append(value)
    }
  }

  @set:VisibleForTesting
  override var extras: SuggestionExtras?
    get() = mExtras
    set(extras) {
      mExtras = extras
    }
}
