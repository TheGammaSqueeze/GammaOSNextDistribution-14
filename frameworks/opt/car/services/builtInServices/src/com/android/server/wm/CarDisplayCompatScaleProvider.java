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
package com.android.server.wm;

import static android.content.pm.ApplicationInfo.PRIVATE_FLAG_PRIVILEGED;

import static com.android.server.wm.CompatScaleProvider.COMPAT_SCALE_MODE_PRODUCT;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityTaskManager;
import android.car.builtin.util.Slogf;
import android.car.feature.Flags;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.CompatScaleWrapper;
import android.content.res.CompatibilityInfo.CompatScale;
import android.os.UserHandle;

import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;

/**
 * Automotive implementation of {@link CompatScaleProvider}
 * This class is responsible for providing different scaling factor for some automotive specific
 * packages.
 *
 * @hide
 */
public final class CarDisplayCompatScaleProvider implements CompatScaleProvider {
    private static final String TAG = CarDisplayCompatScaleProvider.class.getSimpleName();
    public static final String DISPLAYCOMPAT_SYSTEM_FEATURE = "android.car.displaycompatibility";

    private CarDisplayCompatScaleProviderUpdatable mCarCompatScaleProviderUpdatable;
    private ActivityTaskManagerService mAtms;

    /**
     * Registers {@link CarDisplayCompatScaleProvider} with {@link ActivityTaskManagerService}
     */
    public void init(Context context) {
        if (!Flags.displayCompatibility()) {
            Slogf.i(TAG, Flags.FLAG_DISPLAY_COMPATIBILITY + " is not enabled");
            return;
        }
        PackageManager packageManager = context.getPackageManager();
        if (packageManager.hasSystemFeature(DISPLAYCOMPAT_SYSTEM_FEATURE)) {
            mAtms = (ActivityTaskManagerService) ActivityTaskManager.getService();
            mAtms.registerCompatScaleProvider(COMPAT_SCALE_MODE_PRODUCT, this);
            Slogf.i(TAG, "registered Car service as a CompatScaleProvider.");
        }
    }

    /**
     * Sets the given {@link CarActivityInterceptorUpdatable} which this internal class will
     * communicate with.
     */
    public void setUpdatable(
            CarDisplayCompatScaleProviderUpdatable carCompatScaleProviderUpdatable) {
        mCarCompatScaleProviderUpdatable = carCompatScaleProviderUpdatable;
    }

    @Nullable
    @Override
    public CompatScale getCompatScale(@NonNull String packageName, int uid) {
        if (mCarCompatScaleProviderUpdatable == null) {
            Slogf.w(TAG, "mCarCompatScaleProviderUpdatable not set");
            return null;
        }
        CompatScaleWrapper wrapper = mCarCompatScaleProviderUpdatable
                .getCompatScale(packageName, UserHandle.getUserId(uid));
        return wrapper == null ? null : new CompatScale(wrapper.getScaleFactor(),
                wrapper.getDensityScaleFactor());
    }

    /**
     * @return an interface that exposes mainly APIs that are not available on client side.
     */
    public CarDisplayCompatScaleProviderInterface getBuiltinInterface() {
        return new CarDisplayCompatScaleProviderInterface() {
            @Override
            public int getMainDisplayAssignedToUser(int userId) {
                return LocalServices.getService(UserManagerInternal.class)
                        .getMainDisplayAssignedToUser(userId);
            }

            @Override
            public boolean isPrivileged(ApplicationInfo applicationInfo) {
                return (applicationInfo.privateFlags & PRIVATE_FLAG_PRIVILEGED) != 0;
            }
        };
    }
}
