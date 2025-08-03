/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.appsecurity.cts;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.internal.pm.pkg.component.flags.Flags;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public final class UseEmbeddedDexTest extends BaseAppSecurityTest {

    private static final String PACKAGE_NAME = "com.android.cts.useembeddeddex";
    private static final String APK_CANONICAL = "CtsUseEmbeddedDexApp_Canonical.apk";
    private static final String APK_CANONICAL_PER_PROCESS =
            "CtsUseEmbeddedDexApp_Canonical_PerProcess.apk";
    private static final String APK_DEX_COMPRESSED = "CtsUseEmbeddedDexApp_DexCompressed.apk";
    private static final String APK_DEX_COMPRESSED_PER_PROCESS =
            "CtsUseEmbeddedDexApp_DexCompressed_PerProcess.apk";
    private static final String APK_SPLIT_CANONICAL =
            "CtsUseEmbeddedDexAppSplit_Canonical.apk";
    private static final String APK_SPLIT_COMPRESSED_DEX =
            "CtsUseEmbeddedDexAppSplit_CompressedDex.apk";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(PACKAGE_NAME);
    }

    @Test
    public void testCanonicalInstall() throws Exception {
        new InstallMultiple().addFile(APK_CANONICAL).run();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PER_PROCESS_USE_EMBEDDED_DEX_ATTR)
    public void testCanonicalInstall_perProcess() throws Exception {
        new InstallMultiple().addFile(APK_CANONICAL_PER_PROCESS).run();
    }

    @Test
    public void testIsolatedService() throws Exception {
        new InstallMultiple().addFile(APK_CANONICAL).run();
        getDevice().executeShellCommand("cmd package compile -f -m speed " + PACKAGE_NAME);
        runDeviceTests(PACKAGE_NAME, PACKAGE_NAME + ".EmbeddedDexTest", "testIsolatedService");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PER_PROCESS_USE_EMBEDDED_DEX_ATTR)
    public void testIsolatedService_perProcess() throws Exception {
        new InstallMultiple().addFile(APK_CANONICAL_PER_PROCESS).run();
        getDevice().executeShellCommand("cmd package compile -f -m speed " + PACKAGE_NAME);
        runDeviceTests(PACKAGE_NAME, PACKAGE_NAME + ".EmbeddedDexTest", "testIsolatedService");
    }

    @Test
    public void testBadInstallWithCompressedDex() throws Exception {
        new InstallMultiple().addFile(APK_DEX_COMPRESSED).runExpectingFailure();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PER_PROCESS_USE_EMBEDDED_DEX_ATTR)
    public void testBadInstallWithCompressedDex_perProcess() throws Exception {
        new InstallMultiple().addFile(APK_DEX_COMPRESSED_PER_PROCESS).runExpectingFailure();
    }

    @Test
    public void testCanonicalInstallWithSplit() throws Exception {
        new InstallMultiple().addFile(APK_CANONICAL).addFile(APK_SPLIT_CANONICAL).run();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PER_PROCESS_USE_EMBEDDED_DEX_ATTR)
    public void testCanonicalInstallWithSplit_perProcess() throws Exception {
        new InstallMultiple().addFile(APK_CANONICAL_PER_PROCESS).addFile(APK_SPLIT_CANONICAL).run();
    }

    @Test
    public void testBadInstallWithDexCompressedSplit() throws Exception {
        new InstallMultiple().addFile(APK_CANONICAL).addFile(APK_SPLIT_COMPRESSED_DEX)
                .runExpectingFailure();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PER_PROCESS_USE_EMBEDDED_DEX_ATTR)
    public void testBadInstallWithDexCompressedSplit_perProcess() throws Exception {
        new InstallMultiple().addFile(APK_CANONICAL_PER_PROCESS).addFile(APK_SPLIT_COMPRESSED_DEX)
                .runExpectingFailure();
    }

    @Test
    public void testCanonicalInstallWithBaseThenSplit() throws Exception {
        new InstallMultiple().addFile(APK_CANONICAL).run();
        new InstallMultiple().inheritFrom(PACKAGE_NAME).addFile(APK_SPLIT_CANONICAL).run();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PER_PROCESS_USE_EMBEDDED_DEX_ATTR)
    public void testCanonicalInstallWithBaseThenSplit_perProcess() throws Exception {
        new InstallMultiple().addFile(APK_CANONICAL_PER_PROCESS).run();
        new InstallMultiple().inheritFrom(PACKAGE_NAME).addFile(APK_SPLIT_CANONICAL).run();
    }

    @Test
    public void testBadInstallWithBaseThenDexCompressedSplit() throws Exception {
        new InstallMultiple().addFile(APK_CANONICAL).run();
        new InstallMultiple().inheritFrom(PACKAGE_NAME).addFile(APK_SPLIT_COMPRESSED_DEX)
                .runExpectingFailure();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PER_PROCESS_USE_EMBEDDED_DEX_ATTR)
    public void testBadInstallWithBaseThenDexCompressedSplit_perProcess() throws Exception {
        new InstallMultiple().addFile(APK_CANONICAL_PER_PROCESS).run();
        new InstallMultiple().inheritFrom(PACKAGE_NAME).addFile(APK_SPLIT_COMPRESSED_DEX)
                .runExpectingFailure();
    }
}
