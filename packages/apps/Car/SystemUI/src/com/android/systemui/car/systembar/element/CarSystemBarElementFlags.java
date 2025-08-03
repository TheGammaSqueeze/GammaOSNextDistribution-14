/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.car.systembar.element;

import android.annotation.IntDef;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Flag definitions for applicable flag attributes for CarSystemBarElement views and helper
 * functions for working with these attributes.
 */
public class CarSystemBarElementFlags {
    static final int DISABLE_FLAG_NONE = 0;
    static final int DISABLE_FLAG_EXPAND = 1;
    static final int DISABLE_FLAG_NOTIFICATION_ICONS = 1 << 1;
    static final int DISABLE_FLAG_NOTIFICATION_ALERTS = 1 << 2;
    static final int DISABLE_FLAG_SYSTEM_INFO = 1 << 3;
    static final int DISABLE_FLAG_HOME = 1 << 4;
    static final int DISABLE_FLAG_RECENT = 1 << 5;
    static final int DISABLE_FLAG_BACK = 1 << 6;
    static final int DISABLE_FLAG_CLOCK = 1 << 7;
    static final int DISABLE_FLAG_SEARCH = 1 << 8;
    static final int DISABLE_FLAG_ONGOING_CALL_CHIP = 1 << 9;
    @IntDef(flag = true, prefix = {"DISABLE_FLAG_"}, value = {
            DISABLE_FLAG_NONE,
            DISABLE_FLAG_EXPAND,
            DISABLE_FLAG_NOTIFICATION_ICONS,
            DISABLE_FLAG_NOTIFICATION_ALERTS,
            DISABLE_FLAG_SYSTEM_INFO,
            DISABLE_FLAG_HOME,
            DISABLE_FLAG_RECENT,
            DISABLE_FLAG_BACK,
            DISABLE_FLAG_CLOCK,
            DISABLE_FLAG_SEARCH,
            DISABLE_FLAG_ONGOING_CALL_CHIP
    })
    @Retention(RetentionPolicy.CLASS)
    @interface SystemBarDisableFlags {}

    static final int DISABLE2_FLAG_NONE = 0;
    static final int DISABLE2_FLAG_QUICK_SETTINGS = 1;
    static final int DISABLE2_FLAG_SYSTEM_ICONS = 1 << 1;
    static final int DISABLE2_FLAG_NOTIFICATION_SHADE = 1 << 2;
    static final int DISABLE2_FLAG_GLOBAL_ACTIONS = 1 << 3;
    static final int DISABLE2_FLAG_ROTATE_SUGGESTIONS = 1 << 4;
    @IntDef(flag = true, prefix = {"DISABLE2_FLAG_"}, value = {
            DISABLE2_FLAG_NONE,
            DISABLE2_FLAG_QUICK_SETTINGS,
            DISABLE2_FLAG_SYSTEM_ICONS,
            DISABLE2_FLAG_NOTIFICATION_SHADE,
            DISABLE2_FLAG_GLOBAL_ACTIONS,
            DISABLE2_FLAG_ROTATE_SUGGESTIONS
    })
    @Retention(RetentionPolicy.CLASS)
    @interface SystemBarDisable2Flags {}

    /** Get the {@link StatusBarManager.DisableFlags} from the CarSystemBarElement attributes **/
    @StatusBarManager.DisableFlags
    public static int getStatusBarManagerDisableFlagsFromAttributes(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        if (attrs == null) {
            return getStatusBarManagerDisableFlagsFromElementFlags(DISABLE_FLAG_NONE);
        }
        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.CarSystemBarElement);
        int flags = typedArray.getInt(R.styleable.CarSystemBarElement_systemBarDisableFlags,
                DISABLE_FLAG_NONE);
        typedArray.recycle();
        return getStatusBarManagerDisableFlagsFromElementFlags(flags);
    }

    /**
     * Get the {@link StatusBarManager.DisableFlags} from the CarSystemBarElement
     * {@link SystemBarDisableFlags}
     */
    @StatusBarManager.DisableFlags
    public static int getStatusBarManagerDisableFlagsFromElementFlags(int flags) {
        int newFlag = StatusBarManager.DISABLE_NONE;
        if ((DISABLE_FLAG_EXPAND & flags) != 0) newFlag |= StatusBarManager.DISABLE_EXPAND;
        if ((DISABLE_FLAG_NOTIFICATION_ICONS & flags) != 0) {
            newFlag |= StatusBarManager.DISABLE_NOTIFICATION_ICONS;
        }
        if ((DISABLE_FLAG_NOTIFICATION_ALERTS & flags) != 0) {
            newFlag |= StatusBarManager.DISABLE_NOTIFICATION_ALERTS;
        }
        if ((DISABLE_FLAG_SYSTEM_INFO & flags) != 0) {
            newFlag |= StatusBarManager.DISABLE_SYSTEM_INFO;
        }
        if ((DISABLE_FLAG_HOME & flags) != 0) newFlag |= StatusBarManager.DISABLE_HOME;
        if ((DISABLE_FLAG_RECENT & flags) != 0) newFlag |= StatusBarManager.DISABLE_RECENT;
        if ((DISABLE_FLAG_BACK & flags) != 0) newFlag |= StatusBarManager.DISABLE_BACK;
        if ((DISABLE_FLAG_CLOCK & flags) != 0) newFlag |= StatusBarManager.DISABLE_CLOCK;
        if ((DISABLE_FLAG_SEARCH & flags) != 0) newFlag |= StatusBarManager.DISABLE_SEARCH;
        if ((DISABLE_FLAG_ONGOING_CALL_CHIP & flags) != 0) {
            newFlag |= StatusBarManager.DISABLE_ONGOING_CALL_CHIP;
        }
        return newFlag;
    }

    /** Get the {@link StatusBarManager.Disable2Flags} from the CarSystemBarElement attributes **/
    @StatusBarManager.Disable2Flags
    public static int getStatusBarManagerDisable2FlagsFromAttributes(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        if (attrs == null) {
            return getStatusBarManagerDisable2FlagsFromElementFlags(DISABLE2_FLAG_NONE);
        }
        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.CarSystemBarElement);
        int flags = typedArray.getInt(R.styleable.CarSystemBarElement_systemBarDisable2Flags,
                DISABLE2_FLAG_NONE);
        typedArray.recycle();
        return getStatusBarManagerDisable2FlagsFromElementFlags(flags);
    }

    /**
     * Get the {@link StatusBarManager.Disable2Flags} from the CarSystemBarElement
     * {@link SystemBar2DisableFlags}
     */
    @StatusBarManager.Disable2Flags
    public static int getStatusBarManagerDisable2FlagsFromElementFlags(int flags) {
        int newFlag = StatusBarManager.DISABLE2_NONE;
        if ((DISABLE2_FLAG_QUICK_SETTINGS & flags) != 0) {
            newFlag |= StatusBarManager.DISABLE2_QUICK_SETTINGS;
        }
        if ((DISABLE2_FLAG_SYSTEM_ICONS & flags) != 0) {
            newFlag |= StatusBarManager.DISABLE2_SYSTEM_ICONS;
        }
        if ((DISABLE2_FLAG_NOTIFICATION_SHADE & flags) != 0) {
            newFlag |= StatusBarManager.DISABLE2_NOTIFICATION_SHADE;
        }
        if ((DISABLE2_FLAG_GLOBAL_ACTIONS & flags) != 0) {
            newFlag |= StatusBarManager.DISABLE2_GLOBAL_ACTIONS;
        }
        if ((DISABLE2_FLAG_ROTATE_SUGGESTIONS & flags) != 0) {
            newFlag |= StatusBarManager.DISABLE2_ROTATE_SUGGESTIONS;
        }
        return newFlag;
    }

    /** Get the disable for locked task mode state from the CarSystemBarElement attributes **/
    public static boolean getDisableForLockTaskModeLockedFromAttributes(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        if (attrs == null) {
            return false;
        }
        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.CarSystemBarElement);
        boolean disable = typedArray.getBoolean(
                R.styleable.CarSystemBarElement_disableForLockTaskModeLocked, false);
        typedArray.recycle();
        return disable;
    }
}
