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

package com.android.devicelockcontroller.activities.util;

import android.content.Context;
import android.content.Intent;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.devicelockcontroller.activities.HelpActivity;

/**
 * A utility class which handles URL.
 */
public final class UrlUtils {

    private UrlUtils() {}

    /**
     * Sets a text on the given {@link TextView}. If the text contains URLs, the text will be
     * formatted.
     */
    public static void setUrlText(TextView textView, String text) {
        SpannableString spannableString =
                new SpannableString(Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT));
        if (handleUrlSpan(spannableString)) {
            textView.setText(spannableString);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            textView.setText(text);
        }
    }

    private static boolean handleUrlSpan(SpannableString text) {
        boolean hasUrl = false;
        URLSpan[] spans = text.getSpans(0, text.length(), URLSpan.class);
        for (URLSpan span : spans) {
            int start = text.getSpanStart(span);
            int end = text.getSpanEnd(span);
            ClickableSpan clickableSpan = new CustomClickableSpan(span.getURL());
            text.removeSpan(span);
            text.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            hasUrl = true;
        }
        return hasUrl;
    }

    /**
     * A {@link ClickableSpan} which will open the URL the text contains in the
     * {@link HelpActivity}.
     */
    private static final class CustomClickableSpan extends ClickableSpan {
        final String mUrl;

        CustomClickableSpan(String url) {
            mUrl = url;
        }

        @Override
        public void onClick(@NonNull View view) {
            Context context = view.getContext();
            Intent webIntent = new Intent(context, HelpActivity.class);
            webIntent.putExtra(HelpActivity.EXTRA_URL_PARAM, mUrl);
            context.startActivity(webIntent);
        }
    }
}
