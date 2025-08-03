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

package com.android.tv.twopanelsettings;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Provides a FrameLayout for {@link TwoPanelSettingsFragment} with ability to intercept touch event
 * before sent to a corresponding child view.
 */
public class TwoPanelSettingsFrameLayout extends FrameLayout {
    /**
     * Interface definition for a callback to be invoked when a touch event is going to be
     * dispatched to this view. The callback will be invoked before the touch
     * event is given to the view.
     */
    public interface OnDispatchTouchListener {
        /**
         * Called when a touch event is going to be dispatched to a view. This allows listeners to
         * get a chance to respond before the target view.
         *
         * @param v     The view the touch event is going to be dispatched to.
         * @param event The MotionEvent object containing full information about
         *              the event.
         * @return True if the listener has consumed the event, false otherwise.
         */
        boolean onDispatchTouch(View v, MotionEvent event);
    }

    private OnDispatchTouchListener mOnDispatchTouchListener;

    public TwoPanelSettingsFrameLayout(@NonNull Context context) {
        super(context);
    }

    public TwoPanelSettingsFrameLayout(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TwoPanelSettingsFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TwoPanelSettingsFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setOnDispatchTouchListener(@Nullable OnDispatchTouchListener listener) {
        mOnDispatchTouchListener = listener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean handled = false;
        if (mOnDispatchTouchListener != null) {
            handled = mOnDispatchTouchListener.onDispatchTouch(this, ev);
        }
        return handled || super.dispatchTouchEvent(ev);
    }
}
