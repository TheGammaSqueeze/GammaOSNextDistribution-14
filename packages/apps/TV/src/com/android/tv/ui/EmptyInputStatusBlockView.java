/*
 * Copyright 2023 The Android Open Source Project
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.tv.R;

public class EmptyInputStatusBlockView extends FrameLayout {
    private ImageView mEmptyInputStatusIcon;
    private TextView mEmptyInputTitleTextView;

    public EmptyInputStatusBlockView(Context context) {
        this(context, null, 0);
    }

    public EmptyInputStatusBlockView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmptyInputStatusBlockView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        inflate(context, R.layout.empty_input_status_block, this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mEmptyInputStatusIcon = findViewById(R.id.empty_input_status_icon);
        mEmptyInputTitleTextView = findViewById(R.id.empty_input_status_title_text);
    }

    public void setIconAndLabelByInputInfo(TvInputInfo inputInfo) {
        CharSequence label = inputInfo.loadLabel(getContext());
        String title = getResources().getString(R.string.empty_input_status_title_format, label);
        mEmptyInputTitleTextView.setText(title);

        if (inputInfo.isPassthroughInput()) {
            mEmptyInputStatusIcon.setImageDrawable(
                    getResources().getDrawable(R.drawable.ic_empty_input_hdmi, null)
            );
        } else {
            mEmptyInputStatusIcon.setImageDrawable(
                    getResources().getDrawable(R.drawable.ic_empty_input_tuner, null)
            );
        }
    }
}
