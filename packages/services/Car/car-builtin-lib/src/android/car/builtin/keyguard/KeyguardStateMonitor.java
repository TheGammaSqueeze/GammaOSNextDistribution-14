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

package android.car.builtin.keyguard;

import android.car.builtin.util.Slogf;
import android.os.RemoteException;

import com.android.internal.policy.IKeyguardService;
import com.android.internal.policy.IKeyguardStateCallback;

import java.io.PrintWriter;

/**
 * Maintains a cached copy of Keyguard's state.
 */
final class KeyguardStateMonitor {
    private static final String TAG = "KeyguardStateMonitor";
    private final int mCurrentUserId;
    private final StateCallback mCallback;

    // These cache the current state of Keyguard to improve performance and avoid deadlock. After
    // Keyguard changes its state, it always triggers a layout in window manager. Because
    // IKeyguardStateCallback is synchronous and because these states are declared volatile, it's
    // guaranteed that the new state is always picked up in the layout caused by the state change
    // of Keyguard. To be extra safe, assume most restrictive values until Keyguard tells us the
    // actual value.
    private volatile boolean mIsShowing = true;

    private final IKeyguardStateCallback.Stub mKeyguardStateCallback =
            new IKeyguardStateCallback.Stub() {
                @Override
                public void onShowingStateChanged(boolean showing, int userId) {
                    if (userId != mCurrentUserId) return;
                    mIsShowing = showing;

                    mCallback.onShowingChanged(showing);
                }

                @Override
                public void onSimSecureStateChanged(boolean simSecure) {
                }

                @Override
                public void onInputRestrictedStateChanged(boolean inputRestricted) {
                }

                @Override
                public void onTrustedChanged(boolean trusted) {
                }
            };

    KeyguardStateMonitor(IKeyguardService service, int userId, StateCallback callback) {
        mCurrentUserId = userId;
        mCallback = callback;

        try {
            service.addStateMonitorCallback(mKeyguardStateCallback);
        } catch (RemoteException e) {
            Slogf.w(TAG, "Remote Exception", e);
        }
    }

    boolean isShowing() {
        return mIsShowing;
    }

    /**
     * Dump KeyguardStateMonitor.
     */
    void dump(PrintWriter writer) {
        writer.println("*KeyguardStateMonitor*");
        writer.println("mIsShowing=" + mIsShowing);
    }

    interface StateCallback {
        void onShowingChanged(boolean showing);
    }
}
