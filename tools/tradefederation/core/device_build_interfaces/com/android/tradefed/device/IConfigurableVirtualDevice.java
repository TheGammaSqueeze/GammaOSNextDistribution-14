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

package com.android.tradefed.device;

/**
 * An interface to provide information about a possibly preconfigured virtual device info (host ip,
 * host user, ports offset and etc.).
 */
public interface IConfigurableVirtualDevice {

    /** Returns the known associated IP if available, returns null if no known ip. */
    default String getKnownDeviceIp() {
        return null;
    }

    /** Returns the known user if available, returns null if no known user. */
    default String getKnownUser() {
        return null;
    }

    /** Returns the known device num offset if available, returns null if device num offset not set. */
    default Integer getDeviceNumOffset() {
        return null;
    }
}
