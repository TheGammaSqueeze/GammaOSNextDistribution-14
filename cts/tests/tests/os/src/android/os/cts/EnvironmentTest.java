/*
 * Copyright (C) 2009 The Android Open Source Project
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
package android.os.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Environment;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.system.Os;
import android.system.StructStatVfs;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

public class EnvironmentTest extends TestCase {

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    public void testEnvironment() {
        new Environment();
        assertNotNull(Environment.getExternalStorageState());
        assertTrue(Environment.getRootDirectory().isDirectory());
        assertTrue(Environment.getDownloadCacheDirectory().isDirectory());
        assertTrue(Environment.getDataDirectory().isDirectory());
    }

    @AppModeFull(reason = "External directory not accessible by instant apps")
    public void testEnvironmentExternal() {
        assertTrue(Environment.getStorageDirectory().isDirectory());
        assertTrue(Environment.getExternalStorageDirectory().isDirectory());
    }

    /**
     * If TMPDIR points to a global shared directory,
     * this could compromise the security of the files.
     */
    public void testNoTmpDir() {
        assertTrue(System.getenv("TMPDIR").endsWith("android.os.cts/cache"));
    }

    /**
     * Verify that all writable block filesystems are mounted "noatime" to avoid
     * unnecessary flash churn.
     */
    public void testNoAtime() throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = br.readLine()) != null) {
                final String[] fields = line.split(" ");
                final String source = fields[0];
                final String options = fields[3];

                if (source.startsWith("/dev/block/") && !options.startsWith("ro,")
                        && !options.contains("noatime")) {
                    fail("Found device mounted at " + source + " without 'noatime' option, "
                            + "which can cause unnecessary flash churn; please update your fstab.");
                }
            }
        }
    }

    /**
     * verify hidepid=2 on /proc
     */
    public void testHidePid2() throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = br.readLine()) != null) {
                final String[] fields = line.split(" ");
                final String source = fields[0];
                final String options = fields[3];

                if (source.equals("proc") && !options.contains("hidepid=2")
                        && !options.contains("hidepid=invisible")) {
                    fail("proc filesystem mounted without hidepid=2 or hidepid=invisible");
                }
            }
        }
    }

    public void testHidePid2_direct() throws Exception {
        assertFalse(new File("/proc/1").exists());
    }

    /**
     * Verify that all writable block filesystems are mounted with "resgid" to
     * mitigate disk-full trouble.
     */
    public void testSaneInodes() throws Exception {
        final File file = Environment.getDataDirectory();
        final StructStatVfs stat = Os.statvfs(file.getAbsolutePath());

        // By default ext4 creates one inode per 16KiB; we're okay with a much
        // wider range, but we want to make sure the device isn't going totally
        // crazy; too few inodes can result in system instability, and too many
        // inodes can result in wasted space.
        final long maxsize = stat.f_blocks * stat.f_frsize;
        final long maxInodes = maxsize / 4096;
        // Assuming the smallest storage would be 4GB, min # of free inodes
        // in EXT4/F2FS must be larger than 128k for Android to work properly.
        long minInodes = 128 * 1024;
        final long size4GB = 4294967296l;
        //If the storage size is smaller than 4GB, let's consider 32k for 1GB.
        if (maxsize < size4GB) {
            minInodes = 32 * 1024;
        }

        if (stat.f_ffree >= minInodes && stat.f_ffree <= maxInodes
            && stat.f_favail <= stat.f_ffree) {
            // Sweet, sounds great!
        } else {
            fail("Number of inodes " + stat.f_ffree + "/" + stat.f_favail
              + " not within sane range for partition of " + maxsize + " bytes; expected ["
              + minInodes + "," + maxInodes + "]");
        }
    }

    @ApiTest(apis = "android.os.Environment#getDataCePackageDirectoryForUser")
    public void testDataCePackageDirectoryForUser() {
        testDataPackageDirectoryForUser(
                (uuid, userHandle) -> Environment.getDataCePackageDirectoryForUser(
                        uuid, userHandle, sContext.getPackageName()),
                (appInfo) -> appInfo.credentialProtectedDataDir
        );
    }

    @ApiTest(apis = "android.os.Environment#getDataDePackageDirectoryForUser")
    public void testDataDePackageDirectoryForUser() {
        testDataPackageDirectoryForUser(
                (uuid, userHandle) -> Environment.getDataDePackageDirectoryForUser(
                        uuid, userHandle, sContext.getPackageName()),
                (appInfo) -> appInfo.deviceProtectedDataDir
        );
    }

    private void testDataPackageDirectoryForUser(
            BiFunction<UUID, UserHandle, File> publicApi,
            Function<ApplicationInfo, String> publicProperty) {
        var appInfo = sContext.getApplicationInfo();
        // Check that public API is consistent with the public property
        assertThat(publicApi.apply(appInfo.storageUuid, sContext.getUser()).getAbsolutePath())
                .isEqualTo(publicProperty.apply(appInfo));
    }
}
