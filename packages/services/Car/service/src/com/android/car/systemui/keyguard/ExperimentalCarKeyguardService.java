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

package com.android.car.systemui.keyguard;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_INVISIBLE;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_VISIBLE;

import static com.android.car.PermissionHelper.checkHasDumpPermissionGranted;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.car.CarOccupantZoneManager;
import android.car.ICarOccupantZoneCallback;
import android.car.IExperimentalCarKeyguardLockedStateListener;
import android.car.IExperimentalCarKeyguardService;
import android.car.builtin.keyguard.KeyguardServiceDelegate;
import android.car.builtin.util.Slogf;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;
import android.view.Display;

import com.android.car.CarOccupantZoneService;
import com.android.car.CarServiceBase;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Iterator;

/**
 * Experimental Service for controlling keyguard for non-foreground users (passengers) for Car.
 */
public final class ExperimentalCarKeyguardService extends IExperimentalCarKeyguardService.Stub
        implements CarServiceBase {
    @VisibleForTesting
    static final String TAG = "CarKeyguardService";
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private final Context mContext;
    private final UserManager mUserManager;
    private final CarUserService mCarUserService;
    private final CarOccupantZoneService mCarOccupantZoneService;
    private final Object mLock = new Object();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    // Set of users awaiting display assignment (so keyguard can then bind for them)
    @GuardedBy("mUsersNeedDisplays")
    private final ArraySet<UserHandle> mUsersNeedDisplays = new ArraySet<>();
    // Stores the currently active keyguard states with the mapping userId -> state
    @GuardedBy("mLock")
    private final SparseArray<KeyguardState> mKeyguardState = new SparseArray<>();
    private DisplayManager mDisplayManager;

    @VisibleForTesting
    final UserLifecycleListener mUserLifecycleListener = new UserLifecycleListener() {
        @Override
        public void onEvent(CarUserManager.UserLifecycleEvent event) {
            if (DBG) {
                Slogf.d(TAG, "UserLifecycleListener onEvent(" + event + "");
            }
            UserHandle user = event.getUserHandle();
            if (isPassengerUser(user.getIdentifier())) {
                // Keyguard should not be bound until its user is both started and visible. Because
                // the order of the start and visible events may not always be consistent, it is
                // necessary to listen to both to ensure the service binds at the correct time.
                if (event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_STARTING) {
                    if (mCarUserService.isUserVisible(user.getIdentifier())) {
                        handleUserStart(user);
                    }
                } else if (event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_VISIBLE) {
                    if (mUserManager.isUserRunning(user)) {
                        handleUserStart(user);
                    }
                } else if (event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_INVISIBLE) {
                    handleUserStop(user);
                }
            }
        }
    };

    @VisibleForTesting
    final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    handleDisplayChanged(displayId);
                }
            };

    private final ICarOccupantZoneCallback mOccupantZoneCallback =
            new ICarOccupantZoneCallback.Stub() {
                @Override
                public void onOccupantZoneConfigChanged(int flags) {
                    if (DBG) {
                        Slogf.v(TAG, "onOccupantZoneConfigChanged flags=%d", flags);
                    }
                    if ((flags & (CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY
                            | CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER)) != 0) {
                        synchronized (mUsersNeedDisplays) {
                            for (int i = mUsersNeedDisplays.size() - 1; i >= 0; i--) {
                                UserHandle userHandle = mUsersNeedDisplays.valueAt(i);
                                Pair<Display, int[]> displays = getDisplaysForUser(userHandle);
                                if (displays != null) {
                                    initKeyguard(userHandle, displays.first, displays.second);
                                    mUsersNeedDisplays.remove(userHandle);
                                }
                            }
                        }
                    }
                }
            };

    public ExperimentalCarKeyguardService(Context context, CarUserService carUserService,
            CarOccupantZoneService carOccupantZoneService) {
        mContext = context;
        mUserManager = context.getSystemService(UserManager.class);
        mCarUserService = carUserService;
        mCarOccupantZoneService = carOccupantZoneService;
    }

    @Override
    public void init() {
        if (DBG) {
            Slogf.d(TAG, "init()");
        }

        UserLifecycleEventFilter userEventFilter = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_VISIBLE)
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_INVISIBLE)
                .build();
        mCarUserService.addUserLifecycleListener(userEventFilter, mUserLifecycleListener);

        mCarOccupantZoneService.registerCallback(mOccupantZoneCallback);

        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mDisplayManager.registerDisplayListener(mDisplayListener, mMainHandler);

        initializeVisibleUsers();
    }

    @Override
    public void release() {
        if (DBG) {
            Slogf.v(TAG, "release()");
        }
    }

    @Override
    public boolean isKeyguardLocked(int userId) {
        if (!isPassengerUser(userId)) {
            throw new IllegalArgumentException(
                    "Attempted to get the keyguard state of a non-passenger user");
        }
        KeyguardState state = findKeyguardStateForUserId(userId);
        if (state == null) {
            // state not defined yet - assume locked until told otherwise
            if (DBG) {
                Slogf.d(TAG,
                        "KeyguardState not defined for user %d - assuming default locked state",
                        userId);
            }
            return true;
        }
        if (state.mKeyguardDelegate != null) {
            state.mShowing = state.mKeyguardDelegate.isShowing();
        }
        return state.mShowing;
    }

    @Override
    public boolean addKeyguardLockedStateListener(int userId,
            IExperimentalCarKeyguardLockedStateListener listener) {
        if (!isPassengerUser(userId)) {
            throw new IllegalArgumentException(
                    "Attempted to register a locked state listener for a non-passenger user");
        }
        KeyguardState state = findKeyguardStateForUserId(userId);
        if (state == null) {
            if (DBG) {
                Slogf.d(TAG, "Unable to add keyguard locked state listener - "
                        + "KeyguardState not defined for user %d", userId);
            }
            return false;
        }
        state.addKeyguardLockedStateListener(listener);
        return true;
    }

    @Override
    public void removeKeyguardLockedStateListener(int userId,
            IExperimentalCarKeyguardLockedStateListener listener) {
        KeyguardState state = findKeyguardStateForUserId(userId);
        if (state == null) {
            if (DBG) {
                Slogf.d(TAG, "Unable to remove keyguard locked state listener - "
                        + "KeyguardState not defined for user %d", userId);
            }
            return;
        }
        state.removeKeyguardLockedStateListener(listener);
    }

    /**
     * Function to initialize keyguard for the currently visible users when the service starts.
     */
    private void initializeVisibleUsers() {
        for (Iterator<UserHandle> iterator = mUserManager.getVisibleUsers().iterator();
                iterator.hasNext();) {
            UserHandle userHandle = iterator.next();
            if (isPassengerUser(userHandle.getIdentifier())) {
                handleUserStart(userHandle);
            }
        }
    }

    private void handleUserStart(UserHandle userHandle) {
        Pair<Display, int[]> displays = getDisplaysForUser(userHandle);
        if (displays != null) {
            initKeyguard(userHandle, displays.first, displays.second);
        } else {
            // Mark user as needing display assignment
            Slogf.e(TAG, "Can't obtain a display for the user " + userHandle
                    + ". Will wait for occupant zone update.");
            synchronized (mUsersNeedDisplays) {
                mUsersNeedDisplays.add(userHandle);
            }
        }
    }

    private void handleUserStop(UserHandle userHandle) {
        stopKeyguard(userHandle);
    }

    private void initKeyguard(UserHandle userHandle, Display userMainDisplay, int[] allDisplays) {
        int userId = userHandle.getIdentifier();
        synchronized (mLock) {
            if (mKeyguardState.get(userId) == null) {
                KeyguardServiceDelegate keyguardDelegate = createKeyguardServiceDelegate();
                if (DBG) {
                    Slogf.d(TAG,
                            "initKeyguard for user " + userHandle.getIdentifier()
                                    + " on main display " + userMainDisplay.getDisplayId());
                }
                KeyguardState newState = new KeyguardState(keyguardDelegate, userId,
                        userMainDisplay);
                mKeyguardState.put(userId, newState);
            }
            if (!mKeyguardState.get(userId).mKeyguardBound) {
                mKeyguardState.get(userId).mKeyguardBound = true;
                mKeyguardState.get(userId).mKeyguardDelegate.bindService(mContext,
                        userHandle, allDisplays);
            }
        }
    }

    private void stopKeyguard(UserHandle userHandle) {
        synchronized (mUsersNeedDisplays) {
            mUsersNeedDisplays.remove(userHandle);
        }
        synchronized (mLock) {
            int userId = userHandle.getIdentifier();
            if (mKeyguardState.get(userId) != null) {
                mKeyguardState.get(userId).mKeyguardDelegate.stop(mContext);
                mKeyguardState.delete(userId);
            }
        }
    }

    private void handleDisplayChanged(int displayId) {
        if (DBG) {
            Slogf.d(TAG, "handleDisplayChanged: " + displayId);
        }
        KeyguardState keyguardState = findKeyguardStateForDisplayId(displayId);
        if (keyguardState == null) {
            Slogf.w(TAG, "handleDisplayChanged, no state for display" + displayId);
            return;
        }
        int oldState = keyguardState.mDisplayState;
        int newState = mDisplayManager.getDisplay(displayId).getState();
        if (DBG) {
            Slogf.d(TAG, "handleDisplayChanged: " + displayId + " old displayState = "
                    + oldState + " new displayState = " + newState);
        }
        keyguardState.mDisplayState = newState;
        if (oldState == Display.STATE_OFF && newState != Display.STATE_OFF) {
            keyguardState.mKeyguardDelegate.notifyDisplayOn();

        } else if (oldState != Display.STATE_OFF && newState == Display.STATE_OFF) {
            keyguardState.mKeyguardDelegate.notifyDisplayOff();
        }
    }

    @Nullable
    private KeyguardState findKeyguardStateForDisplayId(int displayId) {
        synchronized (mLock) {
            for (int i = 0; i < mKeyguardState.size(); i++) {
                KeyguardState state = mKeyguardState.valueAt(i);
                if (state.mMainDisplayId == displayId) {
                    return state;
                }
            }
        }
        return null;
    }

    @Nullable
    private KeyguardState findKeyguardStateForUserId(int userId) {
        synchronized (mLock) {
            return mKeyguardState.get(userId);
        }
    }

    /** Returns the displays for a given user as a pair [mainDisplay, allDisplays] */
    @Nullable
    private Pair<Display, int[]> getDisplaysForUser(UserHandle userHandle) {
        CarOccupantZoneManager.OccupantZoneInfo info =
                mCarOccupantZoneService.getOccupantZoneForUser(userHandle);
        if (info == null) {
            return null;
        }
        int mainDisplayId = mCarOccupantZoneService.getDisplayForOccupant(info.zoneId,
                DISPLAY_TYPE_MAIN);
        if (mainDisplayId == Display.INVALID_DISPLAY) {
            return null;
        }
        return Pair.create(mDisplayManager.getDisplay(mainDisplayId),
                mCarOccupantZoneService.getAllDisplaysForOccupantZone(info.zoneId));
    }

    private boolean isPassengerUser(int userId) {
        return userId != UserHandle.SYSTEM.getIdentifier()
                && userId != ActivityManager.getCurrentUser();
    }

    @VisibleForTesting
    KeyguardServiceDelegate createKeyguardServiceDelegate() {
        return new KeyguardServiceDelegate();
    }

    @VisibleForTesting
    SparseArray<KeyguardState> getKeyguardState() {
        synchronized (mLock) {
            return mKeyguardState;
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        checkHasDumpPermissionGranted(mContext, "dump()");
        writer.println("*ExperimentalCarKeyguardService*");
        synchronized (mLock) {
            for (int i = 0; i < mKeyguardState.size(); i++) {
                KeyguardState state = mKeyguardState.valueAt(i);
                writer.println("*KeyguardState*");
                writer.println("mUserId=" + state.mUserId);
                writer.println("mKeyguardBound=" + state.mKeyguardBound);
                writer.println("mMainDisplayId=" + state.mMainDisplayId);
                writer.println("mDisplayState=" + state.mDisplayState);
                // MORE DUMP HERE
                if (state.mKeyguardDelegate != null) {
                    state.mKeyguardDelegate.dump(writer);
                }
            }
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    @VisibleForTesting
    static class KeyguardState {
        @VisibleForTesting
        final KeyguardServiceDelegate mKeyguardDelegate;
        private final KeyguardServiceDelegate.KeyguardLockedStateCallback mLockedStateCallback;
        private final ArraySet<IExperimentalCarKeyguardLockedStateListener> mLockedStateListeners =
                new ArraySet<>();
        private final int mMainDisplayId;
        private final int mUserId;
        private int mDisplayState;
        private boolean mKeyguardBound = false;
        private boolean mShowing = true;
        private KeyguardState(KeyguardServiceDelegate keyguardDelegate, int userId,
                Display mainDisplay) {
            mKeyguardDelegate = keyguardDelegate;
            mUserId = userId;
            mMainDisplayId = mainDisplay.getDisplayId();
            mDisplayState = mainDisplay.getState();
            mLockedStateCallback = isKeyguardLocked -> {
                synchronized (mLockedStateListeners) {
                    for (int i = 0; i < mLockedStateListeners.size(); i++) {
                        IExperimentalCarKeyguardLockedStateListener listener =
                                mLockedStateListeners.valueAt(i);
                        try {
                            listener.onKeyguardLockedStateChanged(isKeyguardLocked);
                        } catch (RemoteException e) {
                            Slogf.e(TAG, "Could not update listener", e);
                        }
                    }
                }
            };
        }

        void addKeyguardLockedStateListener(IExperimentalCarKeyguardLockedStateListener listener) {
            synchronized (mLockedStateListeners) {
                mLockedStateListeners.add(listener);
                if (mLockedStateListeners.size() > 1) {
                    return;
                }
                mKeyguardDelegate.registerKeyguardLockedStateCallback(mLockedStateCallback);
            }
        }

        void removeKeyguardLockedStateListener(
                IExperimentalCarKeyguardLockedStateListener listener) {
            synchronized (mLockedStateListeners) {
                mLockedStateListeners.remove(listener);
                if (mLockedStateListeners.size() != 0) {
                    return;
                }
                mKeyguardDelegate.unregisterKeyguardLockedStateCallback();
            }
        }
    }
}
