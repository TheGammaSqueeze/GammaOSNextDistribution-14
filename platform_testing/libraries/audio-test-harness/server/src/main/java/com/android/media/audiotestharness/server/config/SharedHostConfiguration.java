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

package com.android.media.audiotestharness.server.config;

import com.android.media.audiotestharness.common.Defaults;
import com.android.media.audiotestharness.proto.AudioDeviceOuterClass;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/**
 * Container class that encapsulates configuration or other metadata from the test-host that needs
 * to be accessible by the Audio Test Harness system.
 */
@AutoValue
public abstract class SharedHostConfiguration {

    public static SharedHostConfiguration create(
            ImmutableList<AudioDeviceOuterClass.AudioDevice> captureDevices) {
        return new AutoValue_SharedHostConfiguration(captureDevices);
    }

    public static SharedHostConfiguration getDefault() {
        return new AutoValue_SharedHostConfiguration(ImmutableList.of(Defaults.AUDIO_DEVICE));
    }

    /**
     * The list of capture devices that should be allocated by the system for capture.
     *
     * <p>At the moment, the system only supports a single capture device, so only the first device
     * is actually used.
     */
    public abstract ImmutableList<AudioDeviceOuterClass.AudioDevice> captureDevices();
}
