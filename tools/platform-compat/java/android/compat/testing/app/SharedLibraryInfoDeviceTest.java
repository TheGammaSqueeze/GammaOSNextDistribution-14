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

package android.compat.testing.app;

import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.SharedLibraryInfo;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Device-side helper app for obtaining shared libraries.
 *
 * <p>It is not technically a test as it simply collects information, but it simplifies the usage
 * and communication with host-side tests.
 */
@RunWith(AndroidJUnit4.class)
public class SharedLibraryInfoDeviceTest {

    private static final String TAG = "SharedLibraryInfoDeviceTest";
    private static final Set<String> T_PLUS_EXCLUDES = ImmutableSet.of(
            // This shared library's code is added to the bootclasspath in T+ by the
            // AdServices mainline module.
            "android.ext.adservices"
        );

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();

    @Before
    public void before() {
        assumeTrue(SdkLevel.isAtLeastS());
        instrumentation.getUiAutomation().adoptShellPermissionIdentity();
    }

    @After
    public void after() {
        instrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    /**
     * Collects details about all shared libraries on the device and writes them to disk.
     */
    @Test
    public void collectSharedLibraryPaths() throws Exception {
        List<SharedLibraryInfo> sharedLibraries =
                context.getPackageManager().getSharedLibraries(0);

        ImmutableList.Builder<String> content = ImmutableList.builder();
        for (SharedLibraryInfo sharedLibrary : sharedLibraries) {
            if (!canSafelyIgnoreSharedLibrary(sharedLibrary)) {
                content.add(String.format(Locale.US, "%s %d %d %s",
                        sharedLibrary.getName(),
                        sharedLibrary.getType(),
                        sharedLibrary.getLongVersion(),
                        String.join(" ", sharedLibrary.getAllCodePaths())));
            }
        }

        Path detailsFilepath = new File("/sdcard/shared-libs.txt").toPath();
        ImmutableList<String> lines = content.build();
        Log.i(TAG, String.format("Writing details about %d shared libraries to %s",
                lines.size(), detailsFilepath));
        Files.write(detailsFilepath, lines);
    }

    private boolean canSafelyIgnoreSharedLibrary(SharedLibraryInfo sharedLibrary) {
        if (sharedLibrary.isNative()) {
            return true;
        }
        if (SdkLevel.isAtLeastT()) {
            return T_PLUS_EXCLUDES.contains(sharedLibrary.getName());
        }
        return false;
    }

}
