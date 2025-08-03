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

package com.android.car.ui.plugin;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import com.android.car.ui.plugin.oemapis.PluginVersionProviderOEMV1;

import com.chassis.car.ui.plugin.PluginFactoryImplV2;
import com.chassis.car.ui.plugin.PluginFactoryImplV5;
import com.chassis.car.ui.plugin.PluginFactoryImplV6;
import com.chassis.car.ui.plugin.PluginFactoryImplV7;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a plugin that will delegate to the standard car-ui-lib implementation. The main benefit
 * of this is so that customizations can be applied to the car-ui-lib via a RRO without the need to
 * target each app specifically. Note: it only applies to the components that come through the
 * plugin system.
 */
public class PluginVersionProviderImpl implements PluginVersionProviderOEMV1 {
    public static final String TAG = "PluginVersionProvider";

    private static final List<String> DENIED_PACKAGES = new ArrayList<>(List.of(
            // TODO(b/260267959) remove.
            "com.android.vending"
    ));

    /**
     * This method returns different implementations of {@code PluginFactoryOEMV#} depending on the
     * max version supported by an app (i.e., if an app uses an older version of car-ui-lib).
     */
    @Override
    public Object getPluginFactory(
            int maxVersion, @NonNull Context context, @NonNull String packageName) {

        if (DENIED_PACKAGES.contains(packageName)) {
            return null;
        }

        SparseArray<String> r = getAssignedPackageIdentifiers(context.getAssets());
        for (int i = 0, n = r.size(); i < n; i++) {
            final int id = r.keyAt(i);
            // we can skip framework-res (0x01) and anything in the app space (0x7f)
            if (id == 0x01 || id == 0x7f) {
                continue;
            }
            if (context.getPackageName().equals(r.valueAt(i))) {
                Log.d(TAG, "PluginVersionProviderImpl : getPluginFactory: rewriting R prefix"
                        + " values for " + context.getPackageName() + " to: "
                        + Integer.toHexString(id));
                rewriteRValues(context.getClassLoader(), r.valueAt(i), id);
            }
        }

        Context pluginContext = new PluginContextWrapper(context, packageName);
        switch (maxVersion) {
            // There was a bug in car-ui-lib which only passed 1 as the max supported version of the
            // car-ui-lib plugin, even when there were more versions supported (e.g., V2, V3, V4).
            // This was eventually fixed when support for V5 was added. The guidance for oems is to
            // return a PluginFactoryV2 whenever the max version is less than 5 because apps only
            // supporting PluginFactoryV1 will never be deployed on system supporting car-ui-lib
            // plugin. Furthermore, system apps developed on sc-car or newer branches will support
            // PluginFactoryV2 or newer.
            case 1:
            case 2:
            case 3:
            case 4:
                return new PluginFactoryImplV2(pluginContext);
            case 5:
                return new PluginFactoryImplV5(pluginContext);
            case 6:
                return new PluginFactoryImplV6(pluginContext);
            // Keep the newest version as default case and add old versions above
            default:
                return new PluginFactoryImplV7(pluginContext);
        }
    }

    /**
     * Finds all the AssignedPackageIdentifiers where the result will have the keys being the
     * package id (first octet of the resources) and the value is the package name.
     * Details in comment on next function on why this is needed.
     */
    private static SparseArray<String> getAssignedPackageIdentifiers(AssetManager am) {
        final Class<? extends AssetManager> rClazz = am.getClass();
        Throwable cause;
        try {
            final Method callback = rClazz.getMethod("getAssignedPackageIdentifiers");
            Object invoke = callback.invoke(am);
            return (SparseArray<String>) invoke;
        } catch (NoSuchMethodException e) {
            // No rewriting to be done.
            Log.e(TAG, "getAssignedPackageIdentifiers method not found");
            return new SparseArray<>();
        } catch (IllegalAccessException e) {
            cause = e;
        } catch (InvocationTargetException e) {
            cause = e.getCause();
        }

        throw new RuntimeException("Failed to find R classes ",
                cause);
    }

    /**
     * The aapt compiler generates a "onResourcesLoaded" method for R classes when the
     * --shared-lib flag is set. It also sets the first octet of the resources to
     * 0x00 with the assumption that when the package is loaded into memory the onResourcesLoaded
     * method will be called to set the octet to the correct value. This normally happens in
     * android.app.LoadedApk but since this plugin gets loaded "manually" thus it must be set here.
     * Currently, shared libraries use 0x02. If this is not done the "constants" in the R files
     * will not match that of what's loaded through the xml parsers.
     */
    private static void rewriteRValues(ClassLoader cl, String packageName, int id) {
        Throwable cause;
        try {
            final Class<?> rClazz = cl.loadClass(packageName + ".R");
            final Method callback = rClazz.getMethod("onResourcesLoaded", int.class);
            callback.invoke(null, id);
            return;
        } catch (ClassNotFoundException e) {
            // This is not necessarily an error, as some packages do not ship with resources
            // (or they do not need rewriting).
            Log.e(TAG, "R class not found for package " + packageName);
            return;
        } catch (NoSuchMethodException e) {
            // No rewriting to be done.
            Log.e(TAG, "onResourcesLoaded method not found for package " + packageName);
            return;
        } catch (IllegalAccessException e) {
            cause = e;
        } catch (InvocationTargetException e) {
            cause = e.getCause();
        }

        throw new RuntimeException("Failed to rewrite resource references for " + packageName,
                cause);
    }
}
