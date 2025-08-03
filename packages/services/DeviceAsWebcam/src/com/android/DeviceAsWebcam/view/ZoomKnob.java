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

package com.android.DeviceAsWebcam.view;

import static com.android.DeviceAsWebcam.view.ZoomController.convertZoomRatioToString;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.SeekBar;

import androidx.appcompat.widget.AppCompatTextView;

import com.android.DeviceAsWebcam.R;

/** A knob that aims to provide the sliding gesture control on the SeekBar. */
public class ZoomKnob extends AppCompatTextView {
    private final Resources mResources;
    /**
     * The max zoom progress value.
     */
    private int mMaxZoomProgress;
    /**
     * The SeekBar this Knob is attached to.
     */
    private SeekBar mSeekBar;

    public ZoomKnob(Context context, AttributeSet attrs) {
        super(context, attrs);
        mResources = context.getResources();
    }

    void initialize(SeekBar zoomSeekBar, int maxZoomProgress) {
        mSeekBar = zoomSeekBar;
        mMaxZoomProgress = maxZoomProgress;
        float textSizePx = mResources.getDimensionPixelSize(R.dimen.zoom_knob_text_size);
        float textSizeSp = textSizePx / mResources.getDisplayMetrics().scaledDensity;

        setElevation(mResources.getDimensionPixelSize(R.dimen.zoom_thumb_elevation));
        setGravity(Gravity.CENTER);
        setTextAlignment(TEXT_ALIGNMENT_CENTER);
        setTextSize(textSizeSp);

        mSeekBar.setSplitTrack(false);
    }

    /**
     * Update the zoom ratio text which shows on the knob.
     *
     * @param zoomProgress the progress on the SeekBar.
     * @param zoomRatio    the zoom ratio.
     */
    public void updateZoomProgress(int zoomProgress, float zoomRatio) {
        int maxMargin = mSeekBar.getWidth() - getWidth();
        FrameLayout.LayoutParams lp = (LayoutParams) getLayoutParams();
        int margin = (int) (((float) zoomProgress / mMaxZoomProgress) * maxMargin);
        lp.leftMargin = margin;
        lp.rightMargin = 0;
        setLayoutParams(lp);
        setText(convertZoomRatioToString(zoomRatio));
    }

    /** Elevate the knob above SeekBar. */
    public void setElevated(boolean elevated) {
        FrameLayout.LayoutParams lp = (LayoutParams) getLayoutParams();
        if (elevated) {
            lp.bottomMargin = mResources.getDimensionPixelSize(R.dimen.zoom_knob_lift)
                    + (mResources.getDimensionPixelSize(R.dimen.zoom_icon_size) / 2);
        } else {
            lp.bottomMargin = 0;
        }
        setLayoutParams(lp);
    }
}

