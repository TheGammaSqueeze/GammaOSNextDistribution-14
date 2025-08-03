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

package com.android.compatibility.common.util;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;


import java.util.Objects;
import java.util.function.Function;

/**
 * Helper class providing methods to interact with the user under test.
 *
 * <p>For example, it knows if the user was {@link
 * android.app.ActivityManager#startUserInBackgroundVisibleOnDisplay(int, int) started visible in a
 * display} and provide methods (like {@link #injectDisplayIdIfNeeded(ActivityOptions)}) to help
 * tests support such behavior.
 */
// TODO(b/271153404): move logic to bedstead and/or rename it to UserVisibilityHelper
public final class UserHelper {

    private static final String TAG = "CtsUserHelper";

    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private final boolean mVisibleBackgroundUsersSupported;
    private final UserHandle mUser;
    private final boolean mIsVisibleBackgroundUser;
    private final int mDisplayId;

    /**
     * Creates a helper using {@link InstrumentationRegistry#getTargetContext()}.
     */
    public UserHelper() {
        this(InstrumentationRegistry.getTargetContext());
    }

    /**
     * Creates a helper for the given context.
     */
    public UserHelper(Context context) {
        mUser = Objects.requireNonNull(context).getUser();
        UserManager userManager = context.getSystemService(UserManager.class);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mVisibleBackgroundUsersSupported = false;
            if (DEBUG) {
                Log.d(TAG, "Pre-UDC constructor (mUser=" + mUser + "): setting "
                        + "mVisibleBackgroundUsersSupported as false");
            }
        } else {
            mVisibleBackgroundUsersSupported = userManager.isVisibleBackgroundUsersSupported();
        }
        if (!mVisibleBackgroundUsersSupported) {
            if (DEBUG) {
                Log.d(TAG, "Device doesn't support visible background users; setting mDisplayId as"
                        + " DEFAULT_DISPLAY and mIsVisibleBackgroundUser as false");
            }
            mIsVisibleBackgroundUser = false;
            mDisplayId = DEFAULT_DISPLAY;
            return;
        }

        boolean isForeground = userManager.isUserForeground();
        boolean isProfile = userManager.isProfile();
        int displayId = DEFAULT_DISPLAY;
        try {
            // NOTE: getMainDisplayIdAssignedToUser() was added on UDC, but it's a @TestApi, so it
            // will throw a NoSuchMethodError if the test is not configured to allow it
            displayId = userManager.getMainDisplayIdAssignedToUser();
        } catch (NoSuchMethodError e) {
            Log.wtf(TAG, "test is not configured to access @TestApi; setting mDisplayId as"
                    + " DEFAULT_DISPLAY", e);
        }
        mDisplayId = displayId;
        boolean isVisible = userManager.isUserVisible();
        if (DEBUG) {
            Log.d(TAG, "Constructor: mUser=" + mUser + ", visible=" + isVisible
                    + ", isForeground=" + isForeground + ", isProfile=" + isProfile
                    + ", mDisplayId=" + mDisplayId + ", mVisibleBackgroundUsersSupported="
                    + mVisibleBackgroundUsersSupported);
        }
        // TODO(b/271153404): use TestApis.users().instrument() to set mIsVisibleBackgroundUser
        if (isVisible && !isForeground && !isProfile) {
            if (mDisplayId == INVALID_DISPLAY) {
                throw new IllegalStateException("UserManager returned INVALID_DISPLAY for "
                        + "visible background user " + mUser);
            }
            mIsVisibleBackgroundUser = true;
            Log.i(TAG, "Test user " + mUser + " is running on background, visible on display "
                    + mDisplayId);
        } else {
            mIsVisibleBackgroundUser = false;
            if (DEBUG) {
                Log.d(TAG, "Test user " + mUser + " is not running visible on background");
            }
        }
    }

    /**
     * Checks if the user is a full user (i.e, not a {@link UserManager#isProfile() profile}) and
     * is {@link UserManager#isVisibleBackgroundUsersEnabled() running in the background but
     * visible in a display}; if it's not, then it's either the
     * {@link android.app.ActivityManager#getCurrentUser() current foreground user}, a profile, or a
     * full user running in background but not {@link UserManager#isUserVisible() visible}.
     */
    public boolean isVisibleBackgroundUser() {
        return mIsVisibleBackgroundUser;
    }

    /**
     * Convenience method to return {@link UserManager#isVisibleBackgroundUsersSupported()}.
     */
    public boolean isVisibleBackgroundUserSupported() {
        return mVisibleBackgroundUsersSupported;
    }

    /**
     * Convenience method to get the user running this test.
     */
    public UserHandle getUser() {
        return mUser;
    }

    /**
     * Convenience method to get the id of the {@link #getUser() user running this test}.
     */
    public int getUserId() {
        return mUser.getIdentifier();
    }

    /**
     * Gets the display id the {@link #getUser() user} {@link #isVisibleBackgroundUser() is
     * running visible on}.
     *
     * <p>Notice that this id is not necessarily the same as the id returned by
     * {@link Context#getDisplayId()}, as that method returns {@link INVALID_DISPLAY} on contexts
     * that are not associated with a {@link Context#isUiContext() UI}.
     */
    public int getMainDisplayId() {
        return mDisplayId;
    }

    /**
     * Gets an {@link ActivityOptions} that can be used to launch an activity in the display under
     * test.
     */
    public ActivityOptions getActivityOptions() {
        return injectDisplayIdIfNeeded((ActivityOptions) null);
    }

    /**
     * Get the proper {@code cmd appops} with the user id set, including the trailing space.
     */
    public String getAppopsCmd(String command) {
        return "cmd appops " + command + " --user " + getUserId() + " ";
    }

    /**
     * Get a {@code cmd input} for the given {@code source}, setting the proper display (if needed).
     */
    public String getInputCmd(String source) {
        StringBuilder cmd = new StringBuilder("cmd input ").append(source);
        if (mIsVisibleBackgroundUser) {
            cmd.append(" -d ").append(mDisplayId);
        }

        return cmd.toString();
    }

    /**
     * Augments a existing {@link ActivityOptions} (or create a new one), injecting the
     * {{@link #getMainDisplayId()} if needed.
     */
    public ActivityOptions injectDisplayIdIfNeeded(@Nullable ActivityOptions options) {
        ActivityOptions augmentedOptions = options != null ? options : ActivityOptions.makeBasic();
        if (mIsVisibleBackgroundUser) {
            augmentedOptions.setLaunchDisplayId(mDisplayId);
        }
        Log.v(TAG, "injectDisplayIdIfNeeded(): returning " + augmentedOptions);
        return augmentedOptions;
    }

    /**
     * Sets the display id of the event if the test is running in a visible background user.
     */
    public MotionEvent injectDisplayIdIfNeeded(MotionEvent event) {
        return injectDisplayIdIfNeeded(event, MotionEvent.class,
                (e) -> MotionEvent.actionToString(event.getAction()));
    }

    /**
     * Sets the display id of the event if the test is running in a visible background user.
     */
    public KeyEvent injectDisplayIdIfNeeded(KeyEvent event) {
        return injectDisplayIdIfNeeded(event, KeyEvent.class,
                (e) -> KeyEvent.actionToString(event.getAction()));
    }

    private <T extends InputEvent> T injectDisplayIdIfNeeded(T event,  Class<T> clazz,
            Function<T, String> liteStringGenerator) {
        if (!isVisibleBackgroundUserSupported()) {
            return event;
        }
        int eventDisplayId = event.getDisplayId();
        if (!mIsVisibleBackgroundUser) {
            if (DEBUG) {
                Log.d(TAG, "Not replacing display id (" + eventDisplayId + "->" + mDisplayId
                        + ") as user is not running visible on background");
            }
            return event;
        }
        event.setDisplayId(mDisplayId);
        if (VERBOSE) {
            Log.v(TAG, "Replaced displayId (" + eventDisplayId + "->" + mDisplayId + ") on "
                    + event);
        } else if (DEBUG) {
            Log.d(TAG, "Replaced displayId (" + eventDisplayId + "->" + mDisplayId + ") on "
                    + liteStringGenerator.apply(event));
        }
        return event;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[user=" + mUser + ", displayId=" + mDisplayId
                + ", isVisibleBackgroundUser=" + mIsVisibleBackgroundUser
                + ", isVisibleBackgroundUsersSupported" + mVisibleBackgroundUsersSupported
                + "]";
    }
}
