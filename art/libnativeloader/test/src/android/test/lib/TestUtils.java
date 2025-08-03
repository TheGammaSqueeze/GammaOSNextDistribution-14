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

package android.test.lib;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.os.SystemProperties;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.modules.utils.build.SdkLevel;

import org.junit.function.ThrowingRunnable;

import java.io.File;

public final class TestUtils {
    public static void assertLibraryInaccessible(ThrowingRunnable loadLibrary) {
        Throwable t = assertThrows(UnsatisfiedLinkError.class, loadLibrary);
        assertThat(t.getMessage())
                .containsMatch("dlopen failed: .* (not found|not accessible for the namespace)");
    }

    public static String libPath(String dir, String libName) {
        String libDirName = InstrumentationRegistry.getArguments().getString("libDirName");
        return dir + "/" + libDirName + "/lib" + libName + ".so";
    }

    // True if we have to skip testing public libraries in the product
    // partition, which got supported in T.
    public static boolean skipPublicProductLibTests() {
        return !SdkLevel.isAtLeastT();
    }

    // True if apps in product partitions get shared library namespaces, so we
    // cannot test that libs in system and system_ext get blocked.
    public static boolean productAppsAreShared() {
        return !SdkLevel.isAtLeastU() && SystemProperties.get("ro.product.vndk.version").isEmpty();
    }

    // Test that private libs are present, as a safeguard so that the dlopen
    // failures we expect in other tests aren't due to them not being there.
    public static void testPrivateLibsExist(String libDir, String libStem) {
        // Remember to update pushPrivateLibs in LibnativeloaderTest.java when
        // the number of libraries changes.
        for (int i = 1; i <= 10; ++i) {
            String libPath = libPath(libDir, libStem + i);
            assertWithMessage(libPath + " does not exist")
                    .that(new File(libPath).exists())
                    .isTrue();
        }
    }
}
