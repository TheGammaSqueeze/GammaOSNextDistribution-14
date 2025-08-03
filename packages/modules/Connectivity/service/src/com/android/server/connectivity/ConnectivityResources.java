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

package com.android.server.connectivity;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.DeviceConfigUtils;

/**
 * Utility to obtain the {@link com.android.server.ConnectivityService} {@link Resources}, in the
 * ServiceConnectivityResources APK.
 * @hide
 */
public class ConnectivityResources {
    @NonNull
    private final Context mContext;

    @Nullable
    private Context mResourcesContext = null;

    @Nullable
    private static Context sTestResourcesContext = null;

    public ConnectivityResources(Context context) {
        mContext = context;
    }

    /**
     * Convenience method to mock all resources for the duration of a test.
     *
     * Call with a null context to reset after the test.
     */
    @VisibleForTesting
    public static void setResourcesContextForTest(@Nullable Context testContext) {
        sTestResourcesContext = testContext;
    }

    /**
     * Get the {@link Context} of the resources package.
     */
    public synchronized Context getResourcesContext() {
        if (sTestResourcesContext != null) {
            return sTestResourcesContext;
        }

        if (mResourcesContext != null) {
            return mResourcesContext;
        }

        final String resPkg = DeviceConfigUtils.getConnectivityResourcesPackageName(mContext);
        final Context pkgContext;
        try {
            pkgContext = mContext.createPackageContext(resPkg, 0 /* flags */);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException("Resolved package not found", e);
        }

        mResourcesContext = pkgContext;
        return pkgContext;
    }

    /**
     * Get the {@link Resources} of the ServiceConnectivityResources APK.
     */
    public Resources get() {
        return getResourcesContext().getResources();
    }
}
