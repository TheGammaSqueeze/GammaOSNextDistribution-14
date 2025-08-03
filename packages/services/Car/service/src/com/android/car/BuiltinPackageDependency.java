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

package com.android.car;

import android.content.Context;
import android.content.Intent;

import com.android.car.internal.NotificationHelperBase;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.reflect.Constructor;

/**
 * Declared all dependencies into builtin package, mostly for Activity / class / method names.
 *
 * <p> This is for tracking all dependencies done through java reflection.
 */
public final class BuiltinPackageDependency {
    private BuiltinPackageDependency() {};

    /** Package name of builtin, Will be necessary to send Intent. */
    public static final String BUILTIN_CAR_SERVICE_PACKAGE_NAME = "com.android.car";

    /** {@code com.android.car.am.ContinuousBlankActivity} */
    public static final String BLANK_ACTIVITY_CLASS = "com.android.car.am.ContinuousBlankActivity";

    /** {@code com.android.car.pm.CarSafetyAccessibilityService} */
    public static final String CAR_ACCESSIBILITY_SERVICE_CLASS =
            "com.android.car.pm.CarSafetyAccessibilityService";

    /** {@code com.android.car.CarPerUserService} */
    public static final String CAR_USER_PER_SERVICE_CLASS = "com.android.car.CarPerUserService";

    public static final String EVS_HAL_WRAPPER_CLASS = "com.android.car.evs.EvsHalWrapperImpl";

    /** {@code com.android.car.admin.NotificationHelper} class. */
    @VisibleForTesting
    public static final String NOTIFICATION_HELPER_CLASS =
            "com.android.car.admin.NotificationHelper";

    /** Returns {@code ComponentName} string for builtin package component */
    public static String getComponentName(String className) {
        return new StringBuilder()
                .append(BUILTIN_CAR_SERVICE_PACKAGE_NAME)
                .append('/')
                .append(className)
                .toString();
    }

    /** Sets builtin package's class to the passed Intent and returns the Intent. */
    public static Intent addClassNameToIntent(Context context, Intent intent, String className) {
        intent.setClassName(context.getPackageName(), className);
        return intent;
    }

    /**
     * Creates {@link NotificationHelperBase} implemented from Builtin Car service.
     *
     * @param builtinContext {@code Context} of the builtin CarService where the
     *                       NotificationHelper's code is loaded.
     */
    public static NotificationHelperBase createNotificationHelper(Context builtinContext) {
        try {
            Class helperClass = builtinContext.getClassLoader().loadClass(
                    NOTIFICATION_HELPER_CLASS);
            // Use default constructor always
            Constructor constructor = helperClass.getConstructor(new Class[]{Context.class});
            return (NotificationHelperBase) constructor.newInstance(builtinContext);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load class:" + NOTIFICATION_HELPER_CLASS, e);
        }
    }
}
