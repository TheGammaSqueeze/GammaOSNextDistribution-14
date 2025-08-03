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

package com.android.systemui.wm;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.IDisplayWindowInsetsController;
import android.view.IWindowManager;
import android.view.InsetsSource;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.WindowInsets;
import android.view.inputmethod.ImeTracker;

import androidx.annotation.BinderThread;
import androidx.annotation.MainThread;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.car.systembar.CarSystemBar;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.statusbar.CommandQueue;

import java.util.HashSet;
import java.util.Set;

/**
 * b/259604616, This controller is created as a workaround for NavBar issues in concurrent
 * {@link CarSystemBar}/SystemUI.
 * Problem: CarSystemBar relies on {@link IStatusBarService},
 * which can register only one process to listen for the {@link CommandQueue} events.
 * Solution: {@link MDSystemBarsController} intercepts Insets change event by registering the
 * {@link BinderThread} with
 * {@link IWindowManager#setDisplayWindowInsetsController(int, IDisplayWindowInsetsController)} and
 * notifies its listener for both Primary and Secondary SystemUI
 * process.
 */
public class MDSystemBarsController {

    private static final String TAG = MDSystemBarsController.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private Set<Listener> mListeners;
    private int mDisplayId = Display.INVALID_DISPLAY;
    private InsetsState mCurrentInsetsState;
    private final IWindowManager mIWindowManager;
    private final Handler mMainHandler;
    private final Context mContext;

    public MDSystemBarsController(
            IWindowManager wmService,
            @Main Handler mainHandler,
            Context context) {
        mIWindowManager = wmService;
        mMainHandler = mainHandler;
        mContext = context;
    }

    /**
     * Adds a listener for the display.
     * Adding a listener to a Display, replaces previous binder callback to this
     * displayId
     * {@link IWindowManager#setDisplayWindowInsetsController(int, IDisplayWindowInsetsController)}
     * A SystemUI process should only register to a single display with displayId
     * {@link Context#getDisplayId()}
     *
     * Note: {@link  Context#getDisplayId()} will return the {@link Context#DEVICE_ID_DEFAULT}, if
     * called in the constructor. As this component's constructor is called before the DisplayId
     * gets assigned to the context.
     *
     * @param listener SystemBar Inset events
     */
    @MainThread
    public void addListener(Listener listener) {
        if (mDisplayId != Display.INVALID_DISPLAY && mDisplayId != mContext.getDisplayId()) {
            Log.e(TAG, "Unexpected Display Id change");
            mListeners = null;
            mCurrentInsetsState = null;
            unregisterWindowInsetController(mDisplayId);
        }
        if (mListeners != null) {
            mListeners.add(listener);
            return;
        }
        mDisplayId = mContext.getDisplayId();
        mListeners = new HashSet<>();
        mListeners.add(listener);
        registerWindowInsetController(mDisplayId);
    }

    private void registerWindowInsetController(int displayId) {
        if (DEBUG) {
            Log.d(TAG, "Registering a WindowInsetController with Display: " + displayId);
        }
        try {
            mIWindowManager.setDisplayWindowInsetsController(displayId,
                    new DisplayWindowInsetsControllerImpl());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to set insets controller on display " + displayId);
        }
    }

    private void unregisterWindowInsetController(int displayId) {
        if (DEBUG) {
            Log.d(TAG, "Unregistering a WindowInsetController with Display: " + displayId);
        }
        try {
            mIWindowManager.setDisplayWindowInsetsController(displayId, null);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to remove insets controller on display " + displayId);
        }
    }

    @BinderThread
    private class DisplayWindowInsetsControllerImpl
            extends IDisplayWindowInsetsController.Stub {
        @Override
        public void topFocusedWindowChanged(ComponentName component,
                @WindowInsets.Type.InsetsType int requestedVisibleTypes) {
            //no-op
        }

        @Override
        public void insetsChanged(InsetsState insetsState) {
            if (insetsState == null || insetsState.equals(mCurrentInsetsState)) {
                return;
            }
            mCurrentInsetsState = insetsState;
            if (mListeners == null) {
                return;
            }
            boolean show = insetsState.isSourceOrDefaultVisible(InsetsSource.ID_IME,
                    WindowInsets.Type.ime());
            mMainHandler.post(() -> {
                for (Listener l : mListeners) {
                    l.onKeyboardVisibilityChanged(show);
                }
            });
        }

        @Override
        public void insetsControlChanged(InsetsState insetsState,
                InsetsSourceControl[] activeControls) {
            //no-op
        }

        @Override
        public void showInsets(@WindowInsets.Type.InsetsType int types, boolean fromIme,
                @Nullable ImeTracker.Token statsToken) {
            //no-op
        }

        @Override
        public void hideInsets(@WindowInsets.Type.InsetsType int types, boolean fromIme,
                @Nullable ImeTracker.Token statsToken) {
            //no-op
        }
    }

    /**
     * Remove a listener for a display
     *
     * @param listener SystemBar Inset events Listener
     * @return if set contains such a listener, returns {@code true} otherwise false
     */
    public boolean removeListener(Listener listener) {
        if (mListeners == null) {
            return false;
        }
        return mListeners.remove(listener);
    }

    /**
     * Listener for SystemBar insets events
     */
    public interface Listener {
        /**
         * show/hide keyboard
         */
        void onKeyboardVisibilityChanged(boolean showing);
    }
}
