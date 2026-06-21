/*
 * Copyright (C) 2026 GammaOS
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

package com.gammaos.browser;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebView;

/**
 * A WebView that never raises the framework (leanback) soft keyboard.
 *
 * GammaOS Nano hosts its own controller-first OSK for web text fields: a page
 * focusin event fires {@code Android.onEditableFocus} which raises nano's OSK, and
 * the typed text is injected back via JavaScript (see MainActivity.requestNanoOsk /
 * injectNanoOskResult). The framework IME is therefore redundant - and worse, it was
 * appearing on top of nano's OSK because Chromium calls showSoftInput on the WebView
 * whenever a web input is focused.
 *
 * Returning a null InputConnection makes the platform treat this view as not
 * accepting IME text, so that showSoftInput is a no-op and the leanback keyboard
 * never appears. Page focus events (which drive the nano OSK) and d-pad / cursor
 * navigation are unaffected, since those do not go through the input connection.
 */
public class NoImeWebView extends WebView {
    public NoImeWebView(Context context) {
        super(context);
    }

    public NoImeWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NoImeWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return null;
    }
}
