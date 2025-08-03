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

package com.android.quicksearchbox.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.TextAppearanceSpan
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.android.quicksearchbox.R
import com.android.quicksearchbox.Source
import com.android.quicksearchbox.Suggestion
import com.android.quicksearchbox.util.Consumer
import com.android.quicksearchbox.util.NowOrLater

/**
 * View for the items in the suggestions list. This includes promoted suggestions, sources, and
 * suggestions under each source.
 */
class DefaultSuggestionView : BaseSuggestionView {
  private val TAG = "QSB.DefaultSuggestionView"
  private var mAsyncIcon1: DefaultSuggestionView.AsyncIcon? = null
  private var mAsyncIcon2: DefaultSuggestionView.AsyncIcon? = null

  constructor(
    context: Context?,
    attrs: AttributeSet?,
    defStyle: Int
  ) : super(context, attrs, defStyle)

  constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context?) : super(context)

  @Override
  override fun onFinishInflate() {
    super.onFinishInflate()
    mText1 = findViewById(R.id.text1) as TextView
    mText2 = findViewById(R.id.text2) as TextView
    mAsyncIcon1 =
      object : AsyncIcon(mIcon1) {
        // override default icon (when no other available) with default source icon
        @Override
        override fun getFallbackIconId(source: Source?): String {
          return source?.sourceIconUri.toString()
        }

        @Override
        override fun getFallbackIcon(source: Source?): Drawable? {
          return source?.sourceIcon
        }
      }
    mAsyncIcon2 = AsyncIcon(mIcon2)
  }

  @Override
  override fun bindAsSuggestion(suggestion: Suggestion?, userQuery: String?) {
    super.bindAsSuggestion(suggestion, userQuery)
    val text1 = formatText(suggestion?.suggestionText1, suggestion)
    var text2: CharSequence = suggestion?.suggestionText2Url as CharSequence
    text2 = formatUrl(text2)
    // If there is no text for the second line, allow the first line to be up to two lines
    if (TextUtils.isEmpty(text2)) {
      mText1?.setSingleLine(false)
      mText1?.setMaxLines(2)
      mText1?.setEllipsize(TextUtils.TruncateAt.START)
    } else {
      mText1?.setSingleLine(true)
      mText1?.setMaxLines(1)
      mText1?.setEllipsize(TextUtils.TruncateAt.MIDDLE)
    }
    setText1(text1)
    setText2(text2)
    mAsyncIcon1?.set(suggestion.suggestionSource, suggestion.suggestionIcon1)
    mAsyncIcon2?.set(suggestion.suggestionSource, suggestion.suggestionIcon2)
    if (DBG) {
      Log.d(
        TAG,
        "bindAsSuggestion(), text1=" +
          text1 +
          ",text2=" +
          text2 +
          ",q='" +
          userQuery +
          ",fromHistory=" +
          isFromHistory(suggestion)
      )
    }
  }

  private fun formatUrl(url: CharSequence): CharSequence {
    val text = SpannableString(url)
    val colors: ColorStateList = getResources().getColorStateList(R.color.url_text, null)
    text.setSpan(
      TextAppearanceSpan(null, 0, 0, colors, null),
      0,
      url.length,
      Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    return text
  }

  private fun formatText(str: String?, suggestion: Suggestion?): CharSequence {
    val isHtml = "html" == suggestion?.suggestionFormat
    return if (isHtml && looksLikeHtml(str)) {
      Html.fromHtml(str, Html.FROM_HTML_MODE_LEGACY)
    } else {
      str as CharSequence
    }
  }

  private fun looksLikeHtml(str: String?): Boolean {
    if (TextUtils.isEmpty(str)) return false
    for (i in str!!.length - 1 downTo 0) {
      val c: Char = str[i]
      if (c == '>' || c == '&') return true
    }
    return false
  }

  private open inner class AsyncIcon(view: ImageView?) {
    private val mView: ImageView?
    private var mCurrentId: String? = null
    private var mWantedId: String? = null

    operator fun set(source: Source?, sourceIconId: String?) {
      if (sourceIconId != null) {
        // The iconId can just be a package-relative resource ID, which may overlap with
        // other packages. Make sure it's globally unique.
        val iconUri: Uri? = source?.getIconUri(sourceIconId)
        val uniqueIconId: String? = if (iconUri == null) null else iconUri.toString()
        mWantedId = uniqueIconId
        if (!TextUtils.equals(mWantedId, mCurrentId)) {
          if (DBG) Log.d(TAG, "getting icon Id=$uniqueIconId")
          val icon: NowOrLater<Drawable?>? = source?.getIcon(sourceIconId)
          if (icon!!.haveNow()) {
            if (DBG) Log.d(TAG, "getIcon ready now")
            handleNewDrawable(icon.now, uniqueIconId, source)
          } else {
            // make sure old icon is not visible while new one is loaded
            if (DBG) Log.d(TAG, "getIcon getting later")
            clearDrawable()
            icon.getLater(
              object : Consumer<Drawable?> {
                @Override
                override fun consume(value: Drawable?): Boolean {
                  if (DBG) {
                    Log.d(TAG, "IconConsumer.consume got id $uniqueIconId want id $mWantedId")
                  }
                  // ensure we have not been re-bound since the request was made.
                  if (TextUtils.equals(uniqueIconId, mWantedId)) {
                    handleNewDrawable(value, uniqueIconId, source)
                    return true
                  }
                  return false
                }
              }
            )
          }
        }
      } else {
        mWantedId = null
        handleNewDrawable(null, null, source)
      }
    }

    private fun handleNewDrawable(icon: Drawable?, id: String?, source: Source?) {
      var mIcon: Drawable? = icon
      if (mIcon == null) {
        mWantedId = getFallbackIconId(source)
        if (TextUtils.equals(mWantedId, mCurrentId)) {
          return
        }
        mIcon = getFallbackIcon(source)
      }
      setDrawable(mIcon, id)
    }

    private fun setDrawable(icon: Drawable?, id: String?) {
      mCurrentId = id
      setViewDrawable(mView, icon)
    }

    private fun clearDrawable() {
      mCurrentId = null
      mView?.setImageDrawable(null)
    }

    protected open fun getFallbackIconId(source: Source?): String? {
      return null
    }

    protected open fun getFallbackIcon(source: Source?): Drawable? {
      return null
    }

    init {
      mView = view
    }
  }

  class Factory(context: Context?) :
    SuggestionViewInflater(
      VIEW_ID,
      DefaultSuggestionView::class.java,
      R.layout.suggestion,
      context
    )

  companion object {
    private const val DBG = false
    private const val VIEW_ID = "default"

    /**
     * Sets the drawable in an image view, makes sure the view is only visible if there is a
     * drawable.
     */
    private fun setViewDrawable(v: ImageView?, drawable: Drawable?) {
      // Set the icon even if the drawable is null, since we need to clear any
      // previous icon.
      v?.setImageDrawable(drawable)
      if (drawable == null) {
        v?.setVisibility(View.GONE)
      } else {
        v?.setVisibility(View.VISIBLE)

        // This is a hack to get any animated drawables (like a 'working' spinner)
        // to animate. You have to setVisible true on an AnimationDrawable to get
        // it to start animating, but it must first have been false or else the
        // call to setVisible will be ineffective. We need to clear up the story
        // about animated drawables in the future, see http://b/1878430.
        drawable.setVisible(false, false)
        drawable.setVisible(true, false)
      }
    }
  }
}
