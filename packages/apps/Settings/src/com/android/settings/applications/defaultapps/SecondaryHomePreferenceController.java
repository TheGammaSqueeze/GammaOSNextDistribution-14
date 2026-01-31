/*
 * Copyright (C) 2026 The GammaOS Project
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

package com.android.settings.applications.defaultapps;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemProperties;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;

/**
 * Displays and summarizes the currently configured GammaOS secondary home activity.
 *
 * The value is stored in {@code persist.gammaos.secondary_home} as either:
 *  - component name string: "package/class"
 *  - package name (legacy): "package"
 */
public class SecondaryHomePreferenceController extends BasePreferenceController {

    private static final String PROP_SECONDARY_HOME = "persist.gammaos.secondary_home";

    /**
     * GammaOS default secondary home activity. This is intentionally an explicit component because
     * the Launcher3 secondary display activity typically does not advertise CATEGORY_HOME.
     */
    static final String DEFAULT_SECONDARY_HOME =
            "com.android.launcher3/com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher";

    private final PackageManager mPm;

    public SecondaryHomePreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mPm = context.getPackageManager();
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        ensureDefaultValue();
    }

    @Override
    public @Nullable CharSequence getSummary() {
        final String key = getCurrentKey();
        if (TextUtils.isEmpty(key)) {
            return null;
        }

        final ComponentName cn = ComponentName.unflattenFromString(key);
        try {
            if (cn != null) {
                final ActivityInfo ai = mPm.getActivityInfo(cn, 0 /* flags */);
                return ai.loadLabel(mPm);
            } else {
                // Legacy: property contains a package name.
                final ApplicationInfo appInfo = mPm.getApplicationInfo(key, 0 /* flags */);
                return appInfo.loadLabel(mPm);
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Fall back to raw string.
        }
        return key;
    }

    static void ensureDefaultValue() {
        final String cur = SystemProperties.get(PROP_SECONDARY_HOME, "").trim();
        if (cur.isEmpty()) {
            SystemProperties.set(PROP_SECONDARY_HOME, DEFAULT_SECONDARY_HOME);
        }
    }

    static String getCurrentKey() {
        final String cur = SystemProperties.get(PROP_SECONDARY_HOME, "").trim();
        return cur.isEmpty() ? DEFAULT_SECONDARY_HOME : cur;
    }

    static void setCurrentKey(String key) {
        if (key == null) {
            return;
        }
        SystemProperties.set(PROP_SECONDARY_HOME, key.trim());
    }
}