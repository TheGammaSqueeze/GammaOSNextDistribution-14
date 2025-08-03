/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.tagging;

import com.google.common.collect.ImmutableSet;

public class TaggingSdk30MemtagTest extends TaggingBaseTest {
    protected static final String TEST_APK = "CtsHostsideTaggingSdk30MemtagApp.apk";
    protected static final String TEST_PKG = "android.cts.tagging.sdk30memtag";
    protected static final String TEST_APK2 = "CtsHostsideTaggingSdk30App.apk";
    protected static final String TEST_PKG2 = "android.cts.tagging.sdk30";
    private static final String TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner";

    private static final long NATIVE_MEMTAG_ASYNC_CHANGE_ID = 135772972;
    private static final long NATIVE_MEMTAG_SYNC_CHANGE_ID = 177438394;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        installPackage(TEST_APK, true);
        installPackage(TEST_APK2, true);
    }

    @Override
    protected void tearDown() throws Exception {
        uninstallPackage(TEST_PKG, true);
        uninstallPackage(TEST_PKG2, true);
        super.tearDown();
    }

    public void testMemtagOffService() throws Exception {
        if (!deviceSupportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testMemtagOffService",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testMemtagOffIsolatedService() throws Exception {
        if (!deviceSupportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testMemtagOffIsolatedService",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testMemtagOffAppZygoteService() throws Exception {
        if (!deviceSupportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testMemtagOffAppZygoteService",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testExportedMemtagSyncService() throws Exception {
        if (!deviceSupportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testExportedMemtagSyncService",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testExportedMemtagOffService() throws Exception {
        if (!deviceSupportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testExportedMemtagOffService",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testExportedMemtagSyncAppZygoteService() throws Exception {
        if (!deviceSupportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testExportedMemtagSyncAppZygoteService",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testExportedMemtagOffAppZygoteService() throws Exception {
        if (!deviceSupportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testExportedMemtagOffAppZygoteService",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testExportedServiceCompatFeatureEnabled() throws Exception {
        if (!deviceSupportsMemoryTagging) {
            return;
        }
        // Exported service is unaffected by compat feature overrides for the calling app...
        runDeviceCompatTestReported(TEST_PKG, ".TaggingTest", "testExportedMemtagOffService",
            /*enabledChanges*/ ImmutableSet.of(NATIVE_MEMTAG_SYNC_CHANGE_ID),
            /*disabledChanges*/ ImmutableSet.of(),
            // NATIVE_MEMTAG_SYNC_CHANGE_ID will not be checked in the framework because the calling
            // app enables MTE Sync in the manifest.
            /*reportedEnabledChanges*/ ImmutableSet.of(),
            /*reportedDisabledChanges*/ ImmutableSet.of());

        setCompatConfig(/*enabledChanges*/ ImmutableSet.of(NATIVE_MEMTAG_SYNC_CHANGE_ID),
            /*disabledChanges*/ ImmutableSet.of(), TEST_PKG2);

        try {
          // ... but is affected by the overrides for the defining app.
          runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testExportedMemtagOffService_expectSync",
              /*enabledChanges*/ ImmutableSet.of(),
              /*disabledChanges*/ ImmutableSet.of());
        } finally {
          resetCompatConfig(TEST_PKG2,
              /*enabledChanges*/ ImmutableSet.of(NATIVE_MEMTAG_SYNC_CHANGE_ID),
              /*disabledChanges*/ ImmutableSet.of());
        }
    }
}
