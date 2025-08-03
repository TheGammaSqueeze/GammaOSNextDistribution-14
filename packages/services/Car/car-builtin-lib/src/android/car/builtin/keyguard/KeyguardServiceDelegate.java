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

import static android.os.PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON;
import static android.os.PowerManager.WAKE_REASON_POWER_BUTTON;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.car.builtin.util.Slogf;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.policy.IKeyguardDrawnCallback;
import com.android.internal.policy.IKeyguardService;
import com.android.internal.util.Preconditions;

import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Wrapper for KeyguardService
 *
 * Simplified version of com.android.server.policy.keyguard.KeyguardServiceDelegate. If something
 * is not working, check for oversimplification from that class.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class KeyguardServiceDelegate {
    private static final String TAG = KeyguardServiceDelegate.class.getSimpleName();
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private final KeyguardStateMonitor.StateCallback mStateCallback;
    private final Object mLock = new Object();
    private IKeyguardService mKeyguardService;
    private KeyguardStateMonitor mKeyguardStateMonitor;
    @GuardedBy("mLock")
    private KeyguardLockedStateCallback mLockedStateCallback;
    private Handler mHandler;
    private int mUserId;
    private int[] mDisplays;

    private final IKeyguardDrawnCallback.Stub mKeyguardShowDelegate =
            new IKeyguardDrawnCallback.Stub() {
                @Override
                public void onDrawn() {
                    if (DBG) {
                        Slogf.v(TAG, "KeyguardShowDelegate.onDrawn userId=%d", mUserId);
                    }
                }
            };

    private final ServiceConnection mKeyguardConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DBG) {
                Slogf.d(TAG, "Keyguard connected for user %d", mUserId);
            }
            try {
                // replicated PhoneWindowManager order
                mKeyguardService = IKeyguardService.Stub.asInterface(service);
                mKeyguardService.onSystemReady();
                mKeyguardService.setCurrentUser(mUserId);
                mKeyguardStateMonitor = new KeyguardStateMonitor(mKeyguardService, mUserId,
                        mStateCallback);

                mKeyguardService.setSwitchingUser(false);
                mKeyguardService.onBootCompleted();
                mKeyguardService.onStartedWakingUp(PowerManager.WAKE_REASON_UNKNOWN, false);
                mKeyguardService.onFinishedWakingUp();
                mKeyguardService.onScreenTurningOn(mKeyguardShowDelegate);
                mKeyguardService.onScreenTurnedOn();
            } catch (Exception e) {
                Slogf.e(TAG, e, "Can not start the keyguard");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DBG) {
                Slogf.d(TAG, "Keyguard disconnected for user %d", mUserId);
            }
            mKeyguardService = null;
            mKeyguardStateMonitor = null;
            // TODO(b/258238612): Enable once ActivityTaskManagerService APIs are ready
            // mHandler.post(() -> {
            //     try {
            //         ActivityTaskManager.getService().setLockScreenShownForDisplays(
            //                 /* showingKeyguard= */ true, /* showingAod= */ false, mDisplays);
            //     } catch (RemoteException e) {
            //     }
            // });
        }
    };

    public KeyguardServiceDelegate() {
        mStateCallback = showing -> {
            if (mHandler != null) {
                mHandler.post(() -> {
                    synchronized (mLock) {
                        if (mLockedStateCallback != null) {
                            mLockedStateCallback.onKeyguardLockedStateChanged(showing);
                        }
                    }
                });
            }
        };
    }

    /**
     * Binds to the KeyguardService for a particular user and display(s).
     */
    public void bindService(Context context, UserHandle userHandle, int[] displays) {
        if (DBG) {
            Slogf.v(TAG, "bindService for user=%d, displays=%s", userHandle.getIdentifier(),
                    Arrays.toString(displays));
        }
        mHandler = new Handler(context.getMainLooper());
        mUserId = userHandle.getIdentifier();
        mDisplays = displays;

        Intent intent = new Intent();
        Resources resources = context.getApplicationContext().getResources();

        ComponentName keyguardComponent = ComponentName.unflattenFromString(
                resources.getString(com.android.internal.R.string.config_keyguardComponent));
        intent.setComponent(keyguardComponent);

        if (!context.bindServiceAsUser(intent, mKeyguardConnection,
                Context.BIND_AUTO_CREATE, mHandler, userHandle)) {
            Slogf.e(TAG, "Keyguard: can't bind to " + keyguardComponent);
        } else {
            if (DBG) {
                Slogf.v(TAG, "Keyguard started for user=%d", mUserId);
            }
        }
    }

    /**
     * Unbinds the currently bound KeyguardService.
     */
    public void stop(Context context) {
        try {
            context.unbindService(mKeyguardConnection);
        } catch (Exception e) {
            Slogf.e(TAG, "Can't unbind the KeyguardService", e);
        }
    }

    /**
     * Returns whether Keyguard is showing for this delegate. If Keyguard is not bound, return true
     * to assume the most secure state.
     */
    public boolean isShowing() {
        if (mKeyguardStateMonitor == null) {
            if (DBG) {
                Slogf.d(TAG, "mKeyguardStateMonitor null for user=%d - returning default", mUserId);
            }
            return true;
        }
        boolean showing = mKeyguardStateMonitor.isShowing();
        if (DBG) {
            Slogf.d(TAG, "isShowing=%b for user=%d", showing, mUserId);
        }
        return showing;
    }

    /**
     * Register a KeyguardLockedStateCallback for this delegate.
     */
    public void registerKeyguardLockedStateCallback(
            @NonNull KeyguardLockedStateCallback callback) {
        synchronized (mLock) {
            Preconditions.checkState(mLockedStateCallback == null,
                    "Keyguard locked state callback already registered");
            mLockedStateCallback = callback;
        }
    }

    /**
     * Unregister a KeyguardLockedStateCallback from this delegate.
     */
    public void unregisterKeyguardLockedStateCallback() {
        synchronized (mLock) {
            Preconditions.checkNotNull(mLockedStateCallback,
                    "Keyguard locked state callback never registered");
            mLockedStateCallback = null;
        }
    }

    /**
     * Notify Keyguard of a display on event.
     */
    public void notifyDisplayOn() {
        if (mKeyguardService == null) {
            Slogf.w(TAG, "onDisplayOn - null KeyguardService");
            return;
        }
        try {
            if (DBG) {
                Slogf.v(TAG, "onDisplayOn");
            }
            mKeyguardService.onStartedWakingUp(
                    WAKE_REASON_POWER_BUTTON, /* cameraGestureTriggered= */ false);
            mKeyguardService.onScreenTurningOn(mKeyguardShowDelegate);
            mKeyguardService.onScreenTurnedOn();
            mKeyguardService.onFinishedWakingUp();
        } catch (RemoteException e) {
            Slogf.e(TAG, "RemoteException", e);
        }
    }

    /**
     * Notify Keyguard of a display off event.
     */
    public void notifyDisplayOff() {
        if (mKeyguardService == null) {
            Slogf.w(TAG, "onDisplayOff - null KeyguardService");
            return;
        }
        try {
            if (DBG) {
                Slogf.v(TAG, "onDisplayOff");
            }
            // TODO(b/258238612): check that nothing is missed comparing to foreground user behavior
            mKeyguardService.onStartedGoingToSleep(GO_TO_SLEEP_REASON_POWER_BUTTON);
            mKeyguardService.onScreenTurningOff();
            mKeyguardService.onScreenTurnedOff();
            mKeyguardService.onFinishedGoingToSleep(GO_TO_SLEEP_REASON_POWER_BUTTON, false);
        } catch (RemoteException e) {
            Slogf.e(TAG, "RemoteException", e);
        }
    }

    /**
     * Dump the KeyguardServiceDelegate state
     */
    public void dump(PrintWriter writer) {
        writer.println("*KeyguardServiceDelegate*");
        writer.println("Keyguard service connected = " + (mKeyguardService != null));
        if (mKeyguardStateMonitor != null) {
            mKeyguardStateMonitor.dump(writer);
        }
    }

    /**
     * Callback interface that executes when the keyguard locked state changes.
     */
    public interface KeyguardLockedStateCallback {
        /**
         * Callback function that executes when the keyguard locked state changes.
         */
        void onKeyguardLockedStateChanged(boolean isKeyguardLocked);
    }
}
