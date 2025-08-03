/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tv.settings.device.displaysound;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Keep;

import com.android.tv.twopanelsettings.R;
import com.android.tv.twopanelsettings.slices.InfoFragment;

/**
 * The preview Fragment for "Text scaling" screen in TV Settings showing sample text.
 */
@Keep
public class FontScalePreviewFragment extends InfoFragment implements OnLayoutChangeListener {

    private static final String ELLIPSIS = "...";

    /** Argument key containing the current font scale value. */
    public static final String CURRENT_FONT_SCALE_VALUE = "current_font_scale_value";

    /** Argument key containing the font scale value this fragment will preview. */
    public static final String PREVIEW_FONT_SCALE_VALUE = "preview_font_scale_value";

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.font_scale_preview, container, false);

        // Use the current font scale and the font scale assigned to this preview to calculate
        // the adjusted text size for each TextView. Uses px because sp would be scaled up
        // according to the system FONT_SCALE value.
        float preview_font_scale = Float.parseFloat(
                getArguments().getString(PREVIEW_FONT_SCALE_VALUE));
        float current_font_scale = getArguments().getFloat(CURRENT_FONT_SCALE_VALUE);

        TextView title = ((TextView) view.findViewById(R.id.preview_title));
        float adjustedTextSizeTitle =
                (title.getTextSize() / current_font_scale) * preview_font_scale;
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, adjustedTextSizeTitle);

        TextView subtitle = ((TextView) view.findViewById(R.id.preview_subtitle));
        float adjustedTextSizeSubtitle =
                (subtitle.getTextSize() / current_font_scale) * preview_font_scale;
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, adjustedTextSizeSubtitle);

        TextView sampleContent = ((TextView) view.findViewById(R.id.preview_content));
        float adjustedTextSizeContent =
                (sampleContent.getTextSize() / current_font_scale) * preview_font_scale;
        sampleContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, adjustedTextSizeContent);
        sampleContent.addOnLayoutChangeListener(this);

        return view;
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {

        TextView sampleText = (TextView) v;
        int sampleTextHeight = sampleText.getHeight();
        if (sampleTextHeight > 0) {
            // Set the max amount of lines to prevent horizontal cut on the text
            int lines = sampleTextHeight / sampleText.getLineHeight();
            sampleText.setMaxLines(lines);

            // Ellipsize after the max line change
            int lineEndIndex = sampleText.getLayout().getLineEnd(lines - 1);
            if (lineEndIndex >= ELLIPSIS.length()) {
                String ellipsizedText =
                        sampleText.getText().subSequence(0, lineEndIndex - ELLIPSIS.length())
                        + ELLIPSIS;
                sampleText.setText(ellipsizedText);
            }
        }
    }
}
