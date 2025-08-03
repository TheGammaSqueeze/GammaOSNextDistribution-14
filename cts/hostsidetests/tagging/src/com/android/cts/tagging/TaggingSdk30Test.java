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

public class TaggingSdk30Test extends TaggingBaseTest {
    protected static final String TEST_APK = "CtsHostsideTaggingSdk30App.apk";
    protected static final String TEST_PKG = "android.cts.tagging.sdk30";
    protected static final String TEST_APK2 = "CtsHostsideTaggingSdk30MemtagApp.apk";
    protected static final String TEST_PKG2 = "android.cts.tagging.sdk30memtag";
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

    public void testHeapTaggingCompatFeatureDefault() throws Exception {
        runDeviceCompatTestReported(TEST_PKG, DEVICE_TEST_CLASS_NAME,
                testForWhenSoftwareWantsTagging,
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of(),
                /*reportedEnabledChanges*/ reportedChangeSet,
                /*reportedDisabledChanges*/ ImmutableSet.of());
    }

    public void testHeapTaggingCompatFeatureEnabled() throws Exception {
        runDeviceCompatTestReported(TEST_PKG, DEVICE_TEST_CLASS_NAME,
                testForWhenSoftwareWantsTagging,
                /*enabledChanges*/ ImmutableSet.of(NATIVE_HEAP_POINTER_TAGGING_CHANGE_ID),
                /*disabledChanges*/ ImmutableSet.of(),
                /*reportedEnabledChanges*/ reportedChangeSet,
                /*reportedDisabledChanges*/ ImmutableSet.of());
    }

    public void testHeapTaggingCompatFeatureDisabled() throws Exception {
        // We're not allowed to disable compat features (see
        // force_non_debuggable_final_build_for_compat in TaggingBaseTest for more info). Check to
        // ensure that even if we try that we still get pointer tagging.
        runDeviceCompatTestReported(TEST_PKG, DEVICE_TEST_CLASS_NAME,
                testForWhenSoftwareWantsTagging,
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of(NATIVE_HEAP_POINTER_TAGGING_CHANGE_ID),
                /*reportedEnabledChanges*/ ImmutableSet.of(),
                /*reportedDisabledChanges*/ ImmutableSet.of());
    }

    public void testMemoryTagChecksSyncCompatFeatureEnabled() throws Exception {
        if (!deviceSupportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testMemoryTagSyncChecksEnabled",
                /*enabledChanges*/ ImmutableSet.of(NATIVE_MEMTAG_SYNC_CHANGE_ID),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testMemoryTagChecksAsyncCompatFeatureEnabled() throws Exception {
        if (!deviceSupportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testMemoryTagAsyncChecksEnabled",
                /*enabledChanges*/ ImmutableSet.of(NATIVE_MEMTAG_ASYNC_CHANGE_ID),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testMemoryTagChecksCompatFeatureDisabled() throws Exception {
        if (!deviceSupportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testMemoryTagChecksDisabled",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/
                ImmutableSet.of(NATIVE_MEMTAG_SYNC_CHANGE_ID, NATIVE_MEMTAG_ASYNC_CHANGE_ID));
    }

    // Ensure that enabling MTE on non-MTE hardware is a no-op. Note - No statsd report for
    // NATIVE_HEAP_POINTER_TAGGING_CHANGE_ID. The fallback for an app that requests MTE on non-MTE
    // hardware is an implicit TBI. Compat is never probed for the status of the heap pointer
    // tagging feature in this instance.
    public void testMemoryTagChecksCompatFeatureEnabledNonMTE() throws Exception {
        if (deviceSupportsMemoryTagging) {
            return;
        }
        // Tagged Pointers should still be used if the kernel/HW supports it.
        runDeviceCompatTestReported(TEST_PKG, ".TaggingTest", testForWhenSoftwareWantsTagging,
                /*enabledChanges*/ ImmutableSet.of(NATIVE_MEMTAG_ASYNC_CHANGE_ID),
                /*disabledChanges*/ ImmutableSet.of(),
                // Don't check statsd report for NATIVE_MEMTAG_ASYNC_CHANGE_ID, as on non-aarch64
                // we never probed compat for this feature.
                /*reportedEnabledChanges*/ ImmutableSet.of(),
                /*reportedDisabledChanges*/ ImmutableSet.of());
    }

    // Ensure that disabling MTE on non-MTE hardware is a no-op.
    public void testMemoryTagChecksCompatFeatureDisabledNonMTE() throws Exception {
        if (deviceSupportsMemoryTagging) {
            return;
        }
        // Tagged Pointers should still be used if the kernel/HW supports it.
        runDeviceCompatTestReported(TEST_PKG, ".TaggingTest", testForWhenSoftwareWantsTagging,
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of(NATIVE_MEMTAG_ASYNC_CHANGE_ID),
                /*reportedEnabledChanges*/ reportedChangeSet,
                /*reportedDisabledChanges*/ ImmutableSet.of());
    }

    public void testMemoryTagChecksSyncActivity() throws Exception {
        if (!deviceSupportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testMemoryTagSyncActivityChecksEnabled",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testMemoryTagChecksAsyncActivity() throws Exception {
        if (!deviceSupportsMemoryTagging) {
            return;
        }
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testMemoryTagAsyncActivityChecksEnabled",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testHeapZeroInitActivity() throws Exception {
        // Note, only Scudo supports heap zero initialization. This test is
        // effectively a no-op on jemalloc devices.
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testHeapZeroInitActivity",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testHeapZeroInitMemtagAsyncActivity() throws Exception {
        // Note, only Scudo supports heap zero initialization. This test is
        // effectively a no-op on jemalloc devices.
        runDeviceCompatTest(TEST_PKG, ".TaggingTest", "testHeapZeroInitMemtagAsyncActivity",
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
}
