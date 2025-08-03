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

import android.util.Log;

import com.android.ondevicepersonalization.libraries.plugin.Plugin;
import com.android.ondevicepersonalization.libraries.plugin.internal.util.ApkReader;

import dalvik.system.InMemoryDexClassLoader;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;

/** Implementation of PluginLoader */
public final class PluginLoaderImpl implements PluginLoader {
    private static final String TAG = "PluginLoaderImpl";

    @Override public @Nullable Plugin loadPlugin(
            String className,
            ImmutableList<PluginCode> pluginCode,
            ClassLoader classLoader,
            ImmutableSet<String> containerClassesAllowlist,
            ImmutableSet<String> containerPackagesAllowlist) {

        try (CloseableList<FileInputStream> archiveList =
                PluginCode.createFileInputStreamListFromNonNativeFds(pluginCode)) {
            ByteBuffer[] dexes = ApkReader.loadPluginCode(archiveList.closeables());

            // Instantiating a ClassLoader and loading classes on Android would trigger dex-to-vdex
            // generation to speed up class verification process at subsequent calls by verifying
            // dex
            // upfront and storing verified dex (vdex) to app's storage.
            // On an isolated process, dex-to-vdex process would always fail due to denials of
            // file/dir creation. It potentially hurts class loading performance but not impacts
            // functionalities, worth further systracing/profiling to observe VerifyClass overhead.
            IsolationClassLoader isolatedContainerClassLoader =
                    new IsolationClassLoader(
                            classLoader, containerClassesAllowlist, containerPackagesAllowlist);
            ClassLoader pluginClassLoader;
            if (dexes.length > 0) {
                pluginClassLoader = new InMemoryDexClassLoader(dexes, isolatedContainerClassLoader);
            } else {
                // TODO(b/249345663): Remove this after we add tests for loading APKs.
                // InMemoryDexClassLoader crashes if there are no dexes.
                pluginClassLoader = isolatedContainerClassLoader;
            }

            Class<?> clazz = pluginClassLoader.loadClass(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();

            if (!(instance instanceof Plugin)) {
                Log.e(TAG, "Instance not a Plugin");
                return null;
            }
            Plugin pluginInstance = (Plugin) instance;
            pluginInstance.setClassLoader(pluginClassLoader);
            return pluginInstance;
        } catch (IOException e) {
            Log.e(TAG, "Error loading dex files from archive", e);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, String.format("Class %s not found", className), e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            Log.e(TAG, String.format("Error instantiating %s", className), e);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Plugin's declared constructor not found", e);
        }

        return null;
    }
}
