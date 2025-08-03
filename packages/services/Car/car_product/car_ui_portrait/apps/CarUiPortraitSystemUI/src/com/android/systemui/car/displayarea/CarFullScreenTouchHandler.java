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
package com.android.systemui.car.displayarea;

import static android.view.Display.DEFAULT_DISPLAY;

import android.hardware.input.InputManagerGlobal;
import android.os.Looper;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.MotionEvent;

import com.android.wm.shell.common.ShellExecutor;

/**
 * Handles the motion event on screen, mainly used in full immersive mode.
 */
public class CarFullScreenTouchHandler {
    private static final String TAG = CarFullScreenTouchHandler.class.getSimpleName();
    private final ShellExecutor mMainExecutor;
    private InputMonitor mInputMonitor;
    private InputEventReceiver mInputEventReceiver;
    private boolean mIsEnabled;
    private OnTouchTaskViewListener mOnTouchTaskViewListener;

    public CarFullScreenTouchHandler(ShellExecutor mainExecutor) {
        mMainExecutor = mainExecutor;
    }

    /**
     * Notified by {@link CarDisplayAreaController}, to update enable or disable this handler
     */
    public void enable(boolean isEnabled) {
        mIsEnabled = isEnabled;
        updateInputChannel();
    }

    private void onInputEvent(InputEvent ev) {
        if (ev instanceof MotionEvent) {
            MotionEvent me = (MotionEvent) ev;
            if (me.getActionMasked() == MotionEvent.ACTION_UP) {
                mOnTouchTaskViewListener.onTouchEvent();
            }
        }
    }

    private void updateInputChannel() {
        disposeInputChannel();
        if (mIsEnabled) {
            createInputChannel();
        }
    }

    private void disposeInputChannel() {
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
        }
        if (mInputMonitor != null) {
            mInputMonitor.dispose();
            mInputMonitor = null;
        }
    }

    private void createInputChannel() {
        mInputMonitor = InputManagerGlobal.getInstance().monitorGestureInput(
                "car-display-area-touch", DEFAULT_DISPLAY);
        try {
            mMainExecutor.executeBlocking(() -> {
                mInputEventReceiver = new EventReceiver(
                        mInputMonitor.getInputChannel(), Looper.myLooper());
            });
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to create input event receiver", e);
        }
    }

    /**
     * Register touch event listener
     */
    public void registerTouchEventListener(OnTouchTaskViewListener onTouchTaskViewListener) {
        mOnTouchTaskViewListener = onTouchTaskViewListener;
    }

    private class EventReceiver extends InputEventReceiver {
        EventReceiver(InputChannel channel, Looper looper) {
            super(channel, looper);
        }

        public void onInputEvent(InputEvent event) {
            CarFullScreenTouchHandler.this.onInputEvent(event);
            finishInputEvent(event, /* handled */ false);
        }
    }

    /**
     * Callback invoked when a user touches anywhere on the display area.
     */
    interface OnTouchTaskViewListener {
        /**
         * Implement this method to handle touch screen motion events.
         */
        void onTouchEvent();
    }
}
