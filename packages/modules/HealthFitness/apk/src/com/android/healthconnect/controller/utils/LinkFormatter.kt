/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.utils

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat

/**
 * Underlines {@code textView} with {@code linkContent} content and attaches the {@code
 * onClickListener}.
 */
fun convertTextViewIntoLink(
    textView: TextView,
    string: String?,
    start: Int,
    end: Int,
    onClickListener: View.OnClickListener?
) {
    val clickableSpan: ClickableSpan =
        object : ClickableSpan() {
            override fun onClick(view: View) {
                onClickListener?.onClick(view)
            }

            override fun updateDrawState(textPaint: TextPaint) {
                super.updateDrawState(textPaint)
                textPaint.isUnderlineText = true
            }
        }
    val spannableString = SpannableString(string)
    spannableString.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
    textView.setText(spannableString)
    textView.movementMethod = LinkMovementMethod.getInstance()
    textView.isLongClickable = false
    ViewCompat.enableAccessibleClickableSpanSupport(textView)
}

/**
 * Returns a {@link SpannableString} with the {@code appName} bolded.
 */
fun boldAppName(appName: String?, fullText: String): SpannableString {
    val spannableString = SpannableString(fullText)
    appName?.let {
        val start = fullText.indexOf(appName)
        if (start != -1) {
            val end = fullText.indexOf(appName) + appName.length
            spannableString.setSpan(
                StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        }
    }
    return spannableString
}
