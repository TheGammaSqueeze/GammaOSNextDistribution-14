/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tv.settings.library.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

public final class ResourcesUtil {
    private static final String SETTINGS_PACKAGE_NAME = "com.android.tv.settings";

    public static String getString(Context context, String name) {
        try {
            Resources resources = context.getPackageManager()
                    .getResourcesForApplication(SETTINGS_PACKAGE_NAME);
            int id = resources.getIdentifier(name, "string", SETTINGS_PACKAGE_NAME);
            if (id != 0) {
                return resources.getString(id);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean getBoolean(Context context, String name) {
        try {
            Resources resources = context.getPackageManager()
                    .getResourcesForApplication(SETTINGS_PACKAGE_NAME);
            int id = resources.getIdentifier(name, "string", SETTINGS_PACKAGE_NAME);
            if (id != 0) {
                return resources.getBoolean(id);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getQuantityString(Context context, String name, int count,
            Object... formatArgs) {

        try {
            Resources resources = context.getPackageManager()
                    .getResourcesForApplication(SETTINGS_PACKAGE_NAME);
            int id = resources.getIdentifier(name, "string", SETTINGS_PACKAGE_NAME);
            if (id != 0) {
                return resources.getQuantityString(id, count, formatArgs);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getString(Context context, String name, Object... formatArgs) {
        try {
            Resources resources = context.getPackageManager()
                    .getResourcesForApplication(SETTINGS_PACKAGE_NAME);
            int id = resources.getIdentifier(name, "string", SETTINGS_PACKAGE_NAME);
            if (id != 0) {
                return resources.getString(id, formatArgs);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }


    public static Drawable getDrawable(Context context, String name) {
        try {
            Resources resources = context.getPackageManager()
                    .getResourcesForApplication(SETTINGS_PACKAGE_NAME);
            int id = resources.getIdentifier(name, "drawable", SETTINGS_PACKAGE_NAME);
            if (id != 0) {
                return resources.getDrawable(id);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String[] getStringArray(Context context, String name) {
        try {
            Resources resources = context.getPackageManager()
                    .getResourcesForApplication(SETTINGS_PACKAGE_NAME);
            int id = resources.getIdentifier(name, "array", SETTINGS_PACKAGE_NAME);
            if (id != 0) {
                return resources.getStringArray(id);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static int[] getIntArray(Context context, String name) {
        try {
            Resources resources = context.getPackageManager()
                    .getResourcesForApplication(SETTINGS_PACKAGE_NAME);
            int id = resources.getIdentifier(name, "array", SETTINGS_PACKAGE_NAME);
            if (id != 0) {
                return resources.getIntArray(id);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
