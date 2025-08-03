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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.SeekBar;

/** A custom {@link SeekBar} that has a threshold to send completion event to listeners. */
public final class SliderSeekBar extends SeekBar {

    static final int SLIDE_MAX_SLIDE_SCALE = 1000;
    private static final float SLIDE_COMPLETE_THRESHOLD = 0.8f;

    private Drawable mThumb;
    private OnSlideCompleteListener mListener;

    public SliderSeekBar(Context context) {
        super(context);
    }

    public SliderSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setMax(SLIDE_MAX_SLIDE_SCALE);
    }

    @Override
    public void setThumb(Drawable thumb) {
        this.mThumb = thumb;
        super.setThumb(thumb);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                if (!mThumb.getBounds().contains((int) event.getX(), (int) event.getY())) {
                    // Only handle when within bound
                    return false;
                }
                super.onTouchEvent(event);
            }
            break;
            case MotionEvent.ACTION_UP: {
                if (getProgress() > SLIDE_COMPLETE_THRESHOLD * SLIDE_MAX_SLIDE_SCALE) {
                    if (mListener != null) {
                        mListener.onSlideComplete();
                    }
                    // Event completed, snap to end
                    setProgress(SLIDE_MAX_SLIDE_SCALE);
                }else {
                    // Event not completed, snap back to beginning
                    setProgress(0);
                }
            }
            break;
            default:
                super.onTouchEvent(event);
        }
        return true;
    }

    void setOnSlideCompleteListener(OnSlideCompleteListener listener) {
        this.mListener = listener;
    }
}