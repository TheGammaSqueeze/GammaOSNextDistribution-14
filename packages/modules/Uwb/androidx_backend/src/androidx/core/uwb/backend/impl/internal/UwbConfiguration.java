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

package androidx.core.uwb.backend.impl.internal;

import com.google.uwb.support.fira.FiraParams;

/** UWB configuration supported by UWB API. */
public interface UwbConfiguration {

    /** Gets the ID of given configuration. */
    @Utils.UwbConfigId
    int getConfigId();

    /** Gets the multi-node mode of given configuration. */
    @FiraParams.MultiNodeMode
    int getMultiNodeMode();

    /** Gets the STS config of this configuration. */
    @FiraParams.StsConfig
    int getStsConfig();

    /** Gets the AoA result request mode of this configuration. */
    @FiraParams.AoaResultRequestMode
    int getAoaResultRequestMode();

    /** Indicates if controller is the initiator. */
    boolean isControllerTheInitiator();

    /** Gets the Ranging round usage of this configuration. */
    @FiraParams.RangingRoundUsage
    int getRangingRoundUsage();
}
