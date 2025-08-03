/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.media.audio.cts;

import android.compat.cts.CompatChangeGatingTestCase;

import com.google.common.collect.ImmutableSet;

/**
 * Tests app compatibility changes for audio device volume behavior APIs.
 */
public final class AudioVolumeBehaviorCompatHostTest extends CompatChangeGatingTestCase {

    private static final String TEST_APK = "CtsAudioHostTestApp.apk";
    private static final String TEST_PKG = "android.media.audio.app";

    // AudioManager#RETURN_DEVICE_VOLUME_BEHAVIOR_ABSOLUTE_ADJUST_ONLY
    private static final long CHANGE_ID = 240663182L;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        installPackage(TEST_APK, true);
    }

    public void testChangeEnabled() throws Exception {
        runDeviceCompatTest(TEST_PKG, ".AudioVolumeBehaviorCompatTest",
                "changeEnabled_getDeviceVolumeBehaviorReturnsAbsoluteVolumeAdjustOnlyBehavior",
                /*enabledChanges*/ ImmutableSet.of(CHANGE_ID),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testChangeDisabled() throws Exception {
        runDeviceCompatTest(TEST_PKG, ".AudioVolumeBehaviorCompatTest",
                "changeDisabled_getDeviceVolumeBehaviorReturnsFullVolumeBehavior",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of(CHANGE_ID));
    }
}
