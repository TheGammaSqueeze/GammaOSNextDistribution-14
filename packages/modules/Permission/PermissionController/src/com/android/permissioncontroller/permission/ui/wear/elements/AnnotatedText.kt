/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.wear.elements

import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.wear.compose.material.MaterialTheme

const val CLICKABLE_SPAN_TAG = "CLICKABLE_SPAN_TAG"

@Composable
fun AnnotatedText(text: CharSequence, style: TextStyle, modifier: Modifier = Modifier) {
    val onClickCallbacks = mutableMapOf<String, (View) -> Unit>()
    val annotatedString = spannableStringToAnnotatedString(text, onClickCallbacks)
    val context = LocalContext.current
    ClickableText(text = annotatedString, style = style, modifier = modifier) { offset ->
        // Fires the onClickCallback at the clicked position.
        // It's tricky to send an empty view over the parameter, but no way to get the proper one.
        // Need to improve to use it in common.
        annotatedString
            .getStringAnnotations(CLICKABLE_SPAN_TAG, offset, offset)
            .firstOrNull()
            ?.let { onClickCallbacks.get(it.item)?.invoke(View(context)) }
    }
}

@Composable
private fun spannableStringToAnnotatedString(
    text: CharSequence,
    onClickCallbacks: MutableMap<String, (View) -> Unit>,
    spanColor: Color = MaterialTheme.colors.primary
) =
    if (text is Spanned) {
        buildAnnotatedString {
            append((text.toString()))
            for (span in text.getSpans(0, text.length, Any::class.java)) {
                val start = text.getSpanStart(span)
                val end = text.getSpanEnd(span)
                when (span) {
                    is ClickableSpan ->
                        addClickableSpan(span, spanColor, start, end, onClickCallbacks)
                    else -> addStyle(SpanStyle(), start, end)
                }
            }
        }
    } else {
        AnnotatedString(text.toString())
    }

private fun AnnotatedString.Builder.addClickableSpan(
    span: ClickableSpan,
    spanColor: Color,
    start: Int,
    end: Int,
    onClickCallbacks: MutableMap<String, (View) -> Unit>
) {
    addStyle(
        SpanStyle(color = spanColor, textDecoration = TextDecoration.Underline),
        start,
        end,
    )
    val key = "${CLICKABLE_SPAN_TAG}:$start:$end"
    onClickCallbacks[key] = span::onClick
    addStringAnnotation(CLICKABLE_SPAN_TAG, key, start, end)
}
