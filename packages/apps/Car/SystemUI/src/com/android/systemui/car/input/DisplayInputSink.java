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

package com.android.systemui.car.input;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_CONSUMER;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Looper;
import android.os.RemoteException;
import android.view.Display;
import android.view.IWindowSession;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.SurfaceControl;
import android.view.WindowManagerGlobal;
import android.window.InputTransferToken;

import androidx.annotation.VisibleForTesting;

import com.android.internal.view.BaseIWindow;

/**
 * Creates a {@link InputWindowHandle} that catches all input events. Shows
 * a Toast when input events are received while
 * {@link DisplayInputSink} is activated.
 */
public final class DisplayInputSink {
    private static final int INPUT_LOCK_LAYER = Integer.MAX_VALUE;

    private final IWindowSession mWindowSession = WindowManagerGlobal.getWindowSession();
    private final SurfaceControl mSurfaceControl;
    private final int mDisplayId;
    private final OnInputEventListener mCallback;

    private BaseIWindow mFakeWindow;
    private InputTransferToken mFocusGrantToken;
    private InputChannel mInputChannel;
    @VisibleForTesting
    InputEventReceiver mInputEventReceiver;

    /**
     * Construct a new {@link DisplayInputSink}.
     *
     * @param display The display to add the {@link DisplayInputSink} on, must be non-null.
     * @param callback The callback to invoke when an input event is received on
     * {@link DisplayInputSink}, null for no callback.
     */
    public DisplayInputSink(@NonNull Display display,
            @Nullable OnInputEventListener callback) {
        mDisplayId = display.getDisplayId();
        mCallback = callback;
        mSurfaceControl = createSurface(display.getLayerStack());
        if (mCallback != null) {
            createDisplayInputListener();
        }
    }

    private SurfaceControl createSurface(int layerStack) {
        SurfaceControl.Builder sb = new SurfaceControl.Builder();
        SurfaceControl surfaceControl =
                sb.setName("DisplayInputSink-" + mDisplayId)
                        .setHidden(false)
                        .setCallsite("DisplayInputSink.createSurface")
                        .build();
        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        tx.setLayer(surfaceControl, INPUT_LOCK_LAYER)
                .setLayerStack(surfaceControl, layerStack)
                .apply();
        return surfaceControl;
    }

    /**
     * Removes surface and display input listener for the display input sink.
     */
    public void release() {
        if (mCallback != null) {
            removeDisplayInputListener();
        }

        if (mSurfaceControl != null) {
            SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
            tx.remove(mSurfaceControl).apply();
        }
    }

    private void createDisplayInputListener() {
        // Use a fake window as the backing surface is a container layer and we don't want
        // to create a buffer layer for it so we can't use ViewRootImpl.
        mFakeWindow = new BaseIWindow();
        mFakeWindow.setSession(mWindowSession);
        mFocusGrantToken = new InputTransferToken();
        mInputChannel = new InputChannel();
        try {
            mWindowSession.grantInputChannel(
                    mDisplayId,
                    mSurfaceControl,
                    mFakeWindow,
                    /* hostInputToken= */ null,
                    FLAG_NOT_FOCUSABLE,
                    PRIVATE_FLAG_TRUSTED_OVERLAY,
                    /* inputFeatures= */ 0,
                    TYPE_INPUT_CONSUMER,
                    /* windowToken= */ null,
                    mFocusGrantToken,
                    "InputListener of " + mSurfaceControl.toString(),
                    mInputChannel);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }

        mInputEventReceiver = new InputEventReceiver(mInputChannel, Looper.getMainLooper()) {
            @Override
            public void onInputEvent(InputEvent event) {
                mCallback.onInputEvent(event);
                finishInputEvent(event, /* handled= */ true);
            }
        };
    }

    private void removeDisplayInputListener() {
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
        }
        if (mInputChannel != null) {
            mInputChannel.dispose();
            mInputChannel = null;
        }
        try {
            if (mFakeWindow != null) {
                mWindowSession.remove(mFakeWindow);
            }
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("name='DisplayInputSink-");
        sb.append(mDisplayId)
                .append("', inputChannelToken=")
                .append(mInputChannel != null ? mInputChannel.getToken() : "null");
        return sb.toString();
    }

    /**
     * Interface definition for a callback to be invoked when an input event is received
     * on {@link DisplayInputSink}.
     */
    public interface OnInputEventListener {
        /**
         * Called when an input event is received on {@link DisplayInputSink}. This can be
         * used to notify the user that display input lock is currently enabled when the user
         * touches the screen.
         */
        void onInputEvent(InputEvent event);
    }
}
