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

package com.android.layoutlib.bridge.test.widgets;

import android.content.Context;
import android.text.Html;
import android.util.AttributeSet;
import android.widget.TextView;

public class HtmlTextView extends TextView {
    public HtmlTextView(Context context) {
        super(context);
        init(context);
    }

    public HtmlTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public HtmlTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public HtmlTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        setText(Html.fromHtml("This is <b>bold</>!", Html.FROM_HTML_MODE_COMPACT));
    }
}
