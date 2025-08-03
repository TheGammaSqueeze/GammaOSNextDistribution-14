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

package android.compat.sjp.app;

import static org.junit.Assert.assertNotNull;

import android.content.pm.PackageManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Device-side helper app for obtaining Apex info.
 *
 * <p>It is not technically a test as it simply collects information, but it simplifies the usage
 * and communication with host-side tests.
 */
@RunWith(AndroidJUnit4.class)
public class ApexDeviceTest {

    @Before
    public void before() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();
    }

    @After
    public void after() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /**
     * Collects all apk-in-apex apps on the device and writes them to disk.
     */
    @Test
    public void testCollectApkInApexPaths() throws Exception {
        Path detailsFilepath = new File("/sdcard/apk-in-apex-paths.txt").toPath();
        final PackageManager pm = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getPackageManager();
        assertNotNull("No package manager instance!", pm);
        final Set<String> lines = pm.getInstalledPackages(0).stream()
                .map(pkg -> pkg.applicationInfo.sourceDir)
                .filter(sourceDir -> sourceDir != null && sourceDir.contains("/apex/"))
                .collect(Collectors.toSet());
        Files.write(detailsFilepath, lines);
    }
}
