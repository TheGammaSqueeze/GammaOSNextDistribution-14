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

package com.android.systemui.car.hvac;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

/** A view that contains interfaces for the HVAC Panel application and intercepts the key events. */
public class HvacPanelView extends ConstraintLayout {
    private KeyEventHandler mKeyEventHandler;
    private MotionEventHandler mMotionEventHandler;

    public HvacPanelView(Context context) {
        super(context);
    }

    public HvacPanelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public HvacPanelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public HvacPanelView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean result = super.onInterceptTouchEvent(ev);

        if (mMotionEventHandler != null) {
            mMotionEventHandler.onInterceptTouchEvent(ev);
        }

        return result;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (super.dispatchKeyEvent(event)) {
            return true;
        }

        if (mKeyEventHandler != null) {
            return mKeyEventHandler.dispatchKeyEvent(event);
        }

        return false;
    }

    /** Sets a {@link KeyEventHandler} to help interact with the HVAC panel. */
    public void setKeyEventHandler(KeyEventHandler keyEventHandler) {
        mKeyEventHandler = keyEventHandler;
    }

    /** Gets the {@link KeyEventHandler} of the HVAC panel. */
    public KeyEventHandler getKeyEventHandler() {
        return mKeyEventHandler;
    }

    /** An interface to help interact with the HVAC panel. */
    public interface KeyEventHandler {
        /**
         * Allows handling of a {@link KeyEvent} if it wasn't already handled by the superclass.
         * Returns {@code true} if the event was handled and false otherwise.
         */
        boolean dispatchKeyEvent(KeyEvent event);
    }

    /** Sets a {@link MotionEventHandler} to help process motion events in the HVAC panel. */
    public void setMotionEventHandler(MotionEventHandler motionEventHandler) {
        mMotionEventHandler = motionEventHandler;
    }

    /** An interface to help process motion events in the HVAC panel. */
    public interface MotionEventHandler {
        /**
         * Intercepts incoming events of type {@link MotionEvent}. This can be used to know when the
         * last interaction with the HVAC panel might have happened.
         */
        void onInterceptTouchEvent(MotionEvent event);
    }
}
