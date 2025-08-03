/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ondevicepersonalization.libraries.plugin.internal;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableSet;

/**
 * An isolation {@link ClassLoader} layer between plugin container's class loader and plugin's class
 * loader. The layer mediates class loading requests and responses. By default it only allows
 * classes at Android framework as well as explicitly specified at the {@link
 * IsolationClassLoader#containerClassesAllowlist} to be loaded outside plugin's class loader to
 * secure class loading boundary.
 */
public final class IsolationClassLoader extends ClassLoader {
    private final ImmutableSet<String> mContainerClassesAllowlist;
    private final ImmutableSet<String> mContainerPackagesAllowlist;
    private ImmutableSet<String> mRejectedClassesList = ImmutableSet.of();

    /**
     * Create an isolation layer to manage class loading requests/responses to/from plugin
     * container's class loader.
     *
     * <p>Callers can optionally supply a classes allow list to specify additional classes the layer
     * allows for passing its isolation boundary.
     *
     * @param containerClassLoader The plugin container's class loader to be isolated and managed.
     * @param containerClassesAllowlist The classes list to allow pass-through of class loading
     *     requests/responses.
     */
    public IsolationClassLoader(
            @NonNull ClassLoader containerClassLoader,
            @NonNull ImmutableSet<String> containerClassesAllowlist,
            @NonNull ImmutableSet<String> containerPackagesAllowlist) {
        super(containerClassLoader);
        this.mContainerClassesAllowlist = containerClassesAllowlist;
        this.mContainerPackagesAllowlist = containerPackagesAllowlist;
    }

    /**
     * The same as {@link IsolationClassLoader#IsolationClassLoader(ClassLoader, String...)} with an
     * additional rejected-classes-list to specify what classes should be forbidden for use.
     *
     * <p>This is useful for callers to implement their own runtime polices checking if there are
     * any disallowed java classes being used unexpectedly in code. E.g., a policy does not allow
     * thread creation nor tasks scheduled in different thread context, simply specify {@link
     * java.util.concurrent.ExecutorService} and {@link Thread} in rejectedClassesList to fulfill
     * the policy requirement.
     *
     * @param containerClassLoader The plugin container's class loader to be isolated and managed.
     * @param rejectedClassesList The classes list to be rejected if being used.
     * @param containerClassesAllowlist The extra classes list to allow pass-through of * class
     *     loading requests/responses.
     */
    public IsolationClassLoader(
            @NonNull ClassLoader containerClassLoader,
            @NonNull ImmutableSet<String> containerClassesAllowlist,
            @NonNull ImmutableSet<String> containerPackagesAllowlist,
            @NonNull ImmutableSet<String> rejectedClassesList) {
        this(containerClassLoader, containerClassesAllowlist, containerPackagesAllowlist);
        this.mRejectedClassesList = rejectedClassesList;
    }

    private static boolean startsWithAnyPrefixFromSet(String name, ImmutableSet<String> prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> clazz = null;

        // Reject forbidden classes to be loaded unexpectedly.
        if (mRejectedClassesList.contains(name)) {
            throw new AssertionError("class " + name + " is forbidden!");
        }

        // Try loading classes from Android boot classes (Android framework).
        try {
            clazz = getSystemClassLoader().loadClass(name);
        } catch (ClassNotFoundException ignored) {
            // Non-Android-framework classes would go through the following process, so continue.
        }

        // Allow allow-listed container classes to be loaded outside plugin's class loader.
        if (clazz == null
                && (mContainerClassesAllowlist.contains(name)
                        || startsWithAnyPrefixFromSet(name, mContainerPackagesAllowlist))) {
            clazz = super.loadClass(name, resolve);
        }

        // Push back to use plugin's class loader.
        if (clazz == null) {
            throw new ClassNotFoundException("Isolation: deferring to plugin's class loader");
        }

        return clazz;
    }
}
