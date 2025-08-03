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

package com.android.cts.verifier.controls;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.style.ImageSpan;

import com.android.cts.verifier.R;

class ControlsPanelTestUtils {

    private ControlsPanelTestUtils() {}

    private static CharSequence addImage(Context context, CharSequence text, float height) {
        String replace = "[ICON]";
        Spannable spannable = Spannable.Factory.getInstance().newSpannable(text);
        int position = text.toString().indexOf(replace);
        Drawable drawable = context.getDrawable(android.R.drawable.ic_dialog_alert);
        drawable.setBounds(0, 0, (int) height, (int) height);
        spannable.setSpan(
                new ImageSpan(drawable),
                position,
                position + replace.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return spannable;
    }

    static CharSequence createOpenPanelInstructions(Context context, float height) {
        CharSequence text = context.getString(R.string.controls_panel_general_test_instructions);
        return addImage(context, text, height);
    }
}
