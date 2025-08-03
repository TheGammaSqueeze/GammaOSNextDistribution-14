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

package com.android.interactive;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.MANAGE_EXTERNAL_STORAGE;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;

import static com.android.bedstead.nene.appops.CommonAppOps.OPSTR_MANAGE_EXTERNAL_STORAGE;

import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.appops.AppOpsMode;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.interactive.annotations.AutomationFor;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Logic for loading an APK containing step automations and running those automations.
 */
public final class Automator {

    private static final String LOG_TAG = "Interaction.Automator";

    private final Context mContext = TestApis.context().instrumentedContext();
    private final String mAutomationFile;
    private boolean mHasInitialised = false;
    private Map<String, Automation<?>> mAutomationClasses = new HashMap<>();

    public static final String AUTOMATION_FILE = "/sdcard/InteractiveAutomation.apk";

    /**
     * Create an {@link Automator} for the given automation APK.
     */
    public Automator(String automationFile) {
        mAutomationFile = automationFile;
    }

    private void copy(InputStream source, OutputStream target) throws IOException {
        byte[] buf = new byte[1024];
        int length;
        while ((length = source.read(buf)) != -1) {
            target.write(buf, 0, length);
        }
    }

    /**
     * If we're not on the system user, we try to fetch the automation file from the system user.
     */
    private File copyAutomationFileToInternalStorage() {
        try (PermissionContext p = TestApis.permissions()
                .withPermission(READ_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE,
                        INTERACT_ACROSS_USERS_FULL)) {
            UserReference systemUser = TestApis.users().system();
            File apk = new File(mContext.getCacheDir(), "InteractiveAutomation.apk");

            if (apk.exists()) {
                // We want to avoid caching the automations - TODO: This should probably be
                //  controlled by a flag and when the flag is off we don't attempt to copy over
                // again at all
                apk.delete();
            }

            if (TestApis.users().instrumented().equals(systemUser)) {
                // Just need to copy to internal

                if (!new File(mAutomationFile).exists()) {
                    return new File(mAutomationFile); // Doesn't exist
                }

                try (FileInputStream is = new FileInputStream(mAutomationFile);
                     FileOutputStream os = new FileOutputStream(apk)) {
                    apk.setReadOnly();
                    copy(is, os);
                } catch (IOException e) {
                    // Handle error
                    Log.e("Interactive.Automator", "Error reading automation file", e);
                }

                return apk;
            }

            Package pkg = TestApis.packages().instrumented();
            // Otherwise, let's try to fetch the file from the system user
            boolean mustBeUninstalled = false;
            try {
                mustBeUninstalled = !pkg.installedOnUser(systemUser);

                if (mustBeUninstalled) {
                    pkg.installExisting(systemUser);
                }

                pkg.appOps(systemUser).set(OPSTR_MANAGE_EXTERNAL_STORAGE, AppOpsMode.ALLOWED);
                try (ParcelFileDescriptor remoteFile = mContext.createContextAsUser(
                        systemUser.userHandle(), /* flags= */0)
                        .getContentResolver()
                        .openFile(Uri.parse("content://"
                                        + mContext.getPackageName()
                                        + ".interactive.automation"), "r",
                        new CancellationSignal());
                     InputStream fileStream = new FileInputStream(remoteFile.getFileDescriptor());
                     OutputStream outputStream = new FileOutputStream(apk)) {

                    apk.setReadOnly();
                    copy(fileStream, outputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return apk;
            } finally {
                if (mustBeUninstalled) {
                    pkg.uninstall(systemUser);
                }
            }
        }
    }

    private void init() {
        if (mHasInitialised) {
            return;
        }

        mHasInitialised = true;

        try (PermissionContext p = TestApis.permissions()
                .withPermission(READ_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE)) {

            File automation = copyAutomationFileToInternalStorage();

            if (!automation.exists()) {
                Log.e(LOG_TAG, "Automation file does not exist");
                return;
            }

            final File optimizedDexOutputPath = mContext.getDir("outdex", 0);
            DexClassLoader dLoader =
                    new DexClassLoader(automation.getAbsolutePath(), optimizedDexOutputPath.getAbsolutePath(),
                    null, ClassLoader.getSystemClassLoader());

            try {
                Class<?> instrumentationRegistryClass =
                        dLoader.loadClass("androidx.test.platform.app.InstrumentationRegistry");
                instrumentationRegistryClass
                        .getMethod("registerInstance", android.app.Instrumentation.class,
                                android.os.Bundle.class).invoke(null,
                                InstrumentationRegistry.getInstrumentation(),
                                InstrumentationRegistry.getArguments());
            } catch (InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
                throw new RuntimeException("Error sharing instrumentation with automation", e);
            }

            DexFile dx = DexFile
                    .loadDex(automation.getAbsolutePath(), File.createTempFile("opt", "dex",
                            mContext.getCacheDir()).getPath(), 0);
            for (Enumeration<String> classNames = dx.entries(); classNames.hasMoreElements();) {
                String className = classNames.nextElement();
                try {
                    Class<?> cls = dLoader.loadClass(className);
                    String automationFor = getAutomationFor(cls);
                    if (automationFor != null) {
                        // TODO: We need to check that the data type of the automation matches the
                        //  data type of the step
                        mAutomationClasses.put(automationFor,
                                new AutomationExecutor(cls.newInstance()));
                    }
                } catch (ClassNotFoundException e) {
                    // If we can't load the class we just assume it's not an automation
                    Log.i(LOG_TAG, "Error loading potential automation class", e);
                }
            }
        } catch (IOException | InstantiationException | IllegalAccessException e) {
            Log.e(LOG_TAG, "Error loading automations", e);
        }
    }

    private String getAutomationFor(Class<?> cls) {
        for (Annotation annotation : cls.getAnnotations()) {
            if (annotation.annotationType().getCanonicalName().equals(
                    AutomationFor.class.getCanonicalName())) {
                try {
                    return (String) annotation.annotationType().getMethod("value")
                            .invoke(annotation);
                } catch (IllegalAccessException | InvocationTargetException
                        | NoSuchMethodException e) {
                    throw new RuntimeException(
                            "Error getting automation details for " + annotation);
                }
            }
        }
        return null;
    }

    /**
     * Returns true if there is a valid automation for the given step.
     */
    public boolean canAutomate(Step step) {
        init();

        return (mAutomationClasses.containsKey(step.getClass().getCanonicalName()));
    }

    /**
     * Run automation for a given step.
     *
     * <p>{@link #canAutomate(Step)} should be returning true before calling this.
     */
    public <E> E automate(Step<E> step) throws Exception {
        // Unchecked cast is okay as we've verified the types when inserting into the map
        return ((Automation<E>)mAutomationClasses.get(step.getClass().getCanonicalName()))
                .automate();
    }
}
