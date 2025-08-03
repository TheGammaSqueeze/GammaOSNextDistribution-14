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

package com.android.tv.ui;

import android.content.Context;
import android.media.tv.TvInputInfo;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.data.StreamInfo;
import com.android.tv.data.api.Channel;

public class InputBannerViewV2 extends InputBannerViewBase
        implements TvTransitionManager.TransitionLayout {
    private static final String TAG = "InputBannerViewV2";

    private TextView mInputLabelTextView;
    public InputBannerViewV2(Context context) {
        super(context);
    }

    public InputBannerViewV2(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public InputBannerViewV2(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mInputLabelTextView = findViewById(R.id.input_label);
    }

    @Override
    public void updateLabel() {
        MainActivity mainActivity = (MainActivity) getContext();
        Channel channel = mainActivity.getCurrentChannel();
        if (channel == null || !channel.isPassthrough()) {
            return;
        }

        TvInputInfo input =
                mainActivity.getTvInputManagerHelper().getTvInputInfo(channel.getInputId());
        if (input == null) {
            Log.e(TAG, "unable to get TvInputInfo of id " + channel.getInputId());
            return;
        }

        updateInputLabel(input);
    }

    private void updateInputLabel(TvInputInfo input) {
        CharSequence customLabel = input.loadCustomLabel(getContext());
        CharSequence label = input.loadLabel(getContext());

        if (customLabel == null) {
            mInputLabelTextView.setText(label);
        } else {
            String inputLabel = getResources().getString(
                    R.string.input_banner_v2_input_label_format, label, customLabel);
            mInputLabelTextView.setText(inputLabel);
        }

    }
}
