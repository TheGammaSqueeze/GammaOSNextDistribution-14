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

package com.android.car.qc.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.SeekBar;

import androidx.annotation.Nullable;

import com.android.car.ui.uxr.DrawableStateSeekBar;

import java.util.function.Consumer;

/**
 * A {@link SeekBar} specifically for Quick Controls that allows for a disabled click action
 * to execute on {@link MotionEvent.ACTION_UP}.
 */
public class QCSeekBarView extends DrawableStateSeekBar {
    private boolean mClickableWhileDisabled;
    private Consumer<SeekBar> mDisabledClickListener;

    public QCSeekBarView(Context context) {
        super(context);
    }

    public QCSeekBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public QCSeekBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public QCSeekBarView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // AbsSeekBar will ignore all touch events if not enabled. If this SeekBar should be
        // clickable while disabled, the touch event will be handled here.
        if (!isEnabled() && mClickableWhileDisabled) {
            if (event.getAction() == MotionEvent.ACTION_UP && mDisabledClickListener != null) {
                mDisabledClickListener.accept(this);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    public void setClickableWhileDisabled(boolean clickable) {
        mClickableWhileDisabled = clickable;
    }

    public void setDisabledClickListener(@Nullable Consumer<SeekBar> disabledClickListener) {
        mDisabledClickListener = disabledClickListener;
    }

    public boolean isClickableWhileDisabled() {
        return mClickableWhileDisabled;
    }
}
