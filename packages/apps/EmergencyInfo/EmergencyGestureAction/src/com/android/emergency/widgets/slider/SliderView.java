/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.emergency.widgets.slider;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.emergency.action.R;

/**
 * A slider button. User can slide the thumb/scrim from one end to the other end to activate the
 * button.
 */
public final class SliderView extends FrameLayout
        implements SeekBar.OnSeekBarChangeListener {

    private SliderSeekBar mSliderSeekBar;
    private View mSliderTextContainer;
    private TextView mSliderTextView;

    public SliderView(Context context) {
        super(context);
        init(null, 0);
    }

    public SliderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public SliderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr);
    }

    public SliderView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs, defStyleAttr);
    }

    private void init(AttributeSet attrs, int defStyle) {
        inflate(getContext(), R.layout.slider_view, this);
        mSliderTextView = findViewById(android.R.id.title);
        mSliderSeekBar = findViewById(R.id.slider_seek_bar);
        mSliderTextContainer = findViewById(R.id.slider_text_container);
        mSliderSeekBar.setOnSeekBarChangeListener(this);

        String slideText;

        TypedArray a =
                getContext()
                        .getTheme()
                        .obtainStyledAttributes(attrs, R.styleable.SliderView, defStyle, defStyle);

        slideText = a.getString(R.styleable.SliderView_android_text);

        if (a.hasValue(R.styleable.SliderView_android_thumb)) {
            // Updating thumb drawable clears the offset. Cache it first.
            int thumbOffSet = mSliderSeekBar.getThumbOffset();
            mSliderSeekBar.setThumb(a.getDrawable(R.styleable.SliderView_android_thumb));
            mSliderSeekBar.setThumbOffset(thumbOffSet);
        }

        setText(slideText);
        a.recycle();
    }

    public void setText(CharSequence text) {
        mSliderTextView.setText(text);
    }

    public void setSlideCompleteListener(OnSlideCompleteListener listener) {
        mSliderSeekBar.setOnSlideCompleteListener(listener);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        // Dim text as slider makes progress
        mSliderTextContainer.setAlpha(1 - (progress / (float) SliderSeekBar.SLIDE_MAX_SLIDE_SCALE));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }
}
