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

public class TaggingManifestEnabledSdk30Test extends TaggingBaseTest {
    protected static final String TEST_APK = "CtsHostsideTaggingManifestEnabledSdk30App.apk";
    protected static final String TEST_PKG = "android.cts.tagging.sdk30.manifest_enabled";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        installPackage(TEST_APK, true);
    }

    @Override
    protected void tearDown() throws Exception {
        uninstallPackage(TEST_PKG, true);
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
}
