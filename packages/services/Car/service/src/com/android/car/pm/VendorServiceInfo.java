/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.pm;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Intent;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Describes configuration of the vendor service that needs to be started by Car Service. This is
 * immutable object to store only service configuration.
 */
final class VendorServiceInfo {

    @VisibleForTesting
    static final int DEFAULT_MAX_RETRIES = 6;

    private static final String KEY_BIND = "bind";
    private static final String KEY_USER_SCOPE = "user";
    private static final String KEY_TRIGGER = "trigger";
    private static final String KEY_MAX_RETRIES = "maxRetries";

    private static final int USER_SCOPE_ALL = 0;
    private static final int USER_SCOPE_SYSTEM = 1;
    private static final int USER_SCOPE_FOREGROUND = 2;
    private static final int USER_SCOPE_VISIBLE = 3;
    private static final int USER_SCOPE_BACKGROUND_VISIBLE = 4;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            USER_SCOPE_ALL,
            USER_SCOPE_FOREGROUND,
            USER_SCOPE_SYSTEM,
            USER_SCOPE_VISIBLE,
            USER_SCOPE_BACKGROUND_VISIBLE,
    })
    @interface UserScope {}

    private static final int TRIGGER_ASAP = 0;
    private static final int TRIGGER_UNLOCKED = 1;
    private static final int TRIGGER_POST_UNLOCKED = 2;
    private static final int TRIGGER_RESUME = 3;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            TRIGGER_ASAP,
            TRIGGER_UNLOCKED,
            TRIGGER_POST_UNLOCKED,
            TRIGGER_RESUME,
    })
    @interface Trigger {}

    private static final int BIND = 0;
    private static final int START = 1;
    private static final int START_FOREGROUND = 2;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            BIND,
            START,
            START_FOREGROUND,
    })
    @interface Bind {}

    private final @Bind int mBind;
    private final @UserScope int mUserScope;
    private final @Trigger int mTrigger;
    private final int mMaxRetries;
    @Nullable
    private final ComponentName mComponentName;

    private VendorServiceInfo(@Nullable ComponentName componentName, @Bind int bind,
            @UserScope int userScope, @Trigger int trigger, int maxRetries) {
        mComponentName = componentName;
        mUserScope = userScope;
        mTrigger = trigger;
        mBind = bind;
        mMaxRetries = maxRetries;
    }

    boolean isAllUserService() {
        return mUserScope == USER_SCOPE_ALL;
    }

    boolean isSystemUserService() {
        return mUserScope == USER_SCOPE_ALL || mUserScope == USER_SCOPE_SYSTEM;
    }

    boolean isForegroundUserService() {
        return mUserScope == USER_SCOPE_ALL || mUserScope == USER_SCOPE_FOREGROUND;
    }

    boolean isVisibleUserService() {
        return mUserScope == USER_SCOPE_ALL || mUserScope == USER_SCOPE_VISIBLE;
    }

    boolean isBackgroundVisibleUserService() {
        return mUserScope == USER_SCOPE_ALL || mUserScope == USER_SCOPE_BACKGROUND_VISIBLE;
    }

    boolean shouldStartOnUnlock() {
        return mTrigger == TRIGGER_UNLOCKED;
    }

    boolean shouldStartOnPostUnlock() {
        return mTrigger == TRIGGER_POST_UNLOCKED;
    }

    boolean shouldStartOnResume() {
        return mTrigger == TRIGGER_RESUME;
    }

    boolean shouldStartAsap() {
        return mTrigger == TRIGGER_ASAP;
    }

    boolean shouldBeBound() {
        return mBind == BIND;
    }

    boolean shouldBeStartedInForeground() {
        return mBind == START_FOREGROUND;
    }

    int getMaxRetries() {
        return mMaxRetries;
    }

    Intent getIntent() {
        Intent intent = new Intent();
        intent.setComponent(mComponentName);
        return intent;
    }

    static VendorServiceInfo parse(String rawServiceInfo) {
        String[] serviceParamTokens = rawServiceInfo.split("#");
        if (serviceParamTokens.length < 1 || serviceParamTokens.length > 2) {
            throw new IllegalArgumentException("Failed to parse service info: "
                    + rawServiceInfo + ", expected a single '#' symbol");

        }

        final ComponentName cn = ComponentName.unflattenFromString(serviceParamTokens[0]);
        if (cn == null) {
            throw new IllegalArgumentException("Failed to unflatten component name from: "
                    + rawServiceInfo);
        }

        int bind = START;
        int userScope = USER_SCOPE_ALL;
        int trigger = TRIGGER_UNLOCKED;
        int maxRetries = DEFAULT_MAX_RETRIES;

        if (serviceParamTokens.length == 2) {
            for (String keyValueStr : serviceParamTokens[1].split(",")) {
                String[] pair = keyValueStr.split("=");
                String key = pair[0];
                String val = pair[1];
                if (TextUtils.isEmpty(key)) {
                    continue;
                }

                switch (key) {
                    case KEY_BIND:
                        switch (val) {
                            case "bind":
                                bind = BIND;
                                break;
                            case "start":
                                bind = START;
                                break;
                            case "startForeground":
                                bind = START_FOREGROUND;
                                break;
                            default:
                                throw new IllegalArgumentException("Unexpected bind option: "
                                        + val);
                        }
                        break;
                    case KEY_USER_SCOPE:
                        switch (val) {
                            case "all":
                                userScope = USER_SCOPE_ALL;
                                break;
                            case "system":
                                userScope = USER_SCOPE_SYSTEM;
                                break;
                            case "foreground":
                                userScope = USER_SCOPE_FOREGROUND;
                                break;
                            case "visible":
                                userScope = USER_SCOPE_VISIBLE;
                                break;
                            case "backgroundVisible":
                                userScope = USER_SCOPE_BACKGROUND_VISIBLE;
                                break;
                            default:
                                throw new IllegalArgumentException("Unexpected user scope: " + val);
                        }
                        break;
                    case KEY_TRIGGER:
                        switch (val) {
                            case "asap":
                                trigger = TRIGGER_ASAP;
                                break;
                            case "userUnlocked":
                                trigger = TRIGGER_UNLOCKED;
                                break;
                            case "userPostUnlocked":
                                trigger = TRIGGER_POST_UNLOCKED;
                                break;
                            case "resume":
                                trigger = TRIGGER_RESUME;
                                break;
                            default:
                                throw new IllegalArgumentException("Unexpected trigger: " + val);
                        }
                        break;
                    case KEY_MAX_RETRIES:
                        try {
                            maxRetries = Integer.parseInt(val);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException(
                                    "Cannot parse the specified `maxRetries` into an integer: "
                                            + val);
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected token: " + key);
                }
            }
        }

        return new VendorServiceInfo(cn, bind, userScope, trigger, maxRetries);
    }

    @Override
    public String toString() {
        return "VendorService{"
                + "component=" + toShortString()
                + ", bind=" + bindToString(mBind)
                + ", trigger=" + triggerToString(mTrigger)
                + ", userScope=" + userScopeToString(mUserScope)
                + (mMaxRetries == DEFAULT_MAX_RETRIES ? "" : ", maxRetries=" + mMaxRetries)
                + '}';
    }

    String toShortString() {
        return mComponentName != null ? mComponentName.flattenToShortString() : "N/A";
    }

    // NOTE: cannot use DebugUtils below because constants are private

    private static String bindToString(@Bind int bind) {
        switch (bind) {
            case BIND:
                return "BIND";
            case START:
                return "START";
            case START_FOREGROUND:
                return "START_FOREGROUND";
            default:
                return "INVALID-" + bind;
        }
    }

    private static String triggerToString(@Trigger int trigger) {
        switch (trigger) {
            case TRIGGER_ASAP:
                return "ASAP";
            case TRIGGER_UNLOCKED:
                return "UNLOCKED";
            case TRIGGER_POST_UNLOCKED:
                return "POST_UNLOCKED";
            case TRIGGER_RESUME:
                return "RESUME";
            default:
                return "INVALID-" + trigger;
        }
    }

    private static String userScopeToString(@UserScope int userScope) {
        switch (userScope) {
            case USER_SCOPE_ALL:
                return "ALL";
            case USER_SCOPE_FOREGROUND:
                return "FOREGROUND";
            case USER_SCOPE_SYSTEM:
                return "SYSTEM";
            case USER_SCOPE_VISIBLE:
                return "VISIBLE";
            case USER_SCOPE_BACKGROUND_VISIBLE:
                return "BACKGROUND_VISIBLE";
            default:
                return "INVALID-" + userScope;
        }
    }
}
