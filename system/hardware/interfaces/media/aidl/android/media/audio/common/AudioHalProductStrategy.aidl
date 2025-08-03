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

package android.media.audio.common;

import android.media.audio.common.AudioHalAttributesGroup;
import android.media.audio.common.AudioProductStrategyType;

/**
 * AudioHalProductStrategy is a grouping of AudioHalAttributesGroups that will
 * share the same audio routing and volume policy.
 *
 * {@hide}
 */
@SuppressWarnings(value={"redundant-name"}) // for VENDOR_STRATEGY*
@JavaDerive(equals=true, toString=true)
@VintfStability
parcelable AudioHalProductStrategy {
    /**
     * Defines the start of the vendor-defined product strategies
     */
    const int VENDOR_STRATEGY_ID_START = 1000;
    /**
     * Identifies the product strategy with a predefined constant. Vendors
     * using the default audio policy engine must use AudioProductStrategyType.
     * Vendors using the Configurable Audio Policy (CAP) engine must number
     * their strategies starting at VENDOR_STRATEGY_ID_START.
     */
    int id = AudioProductStrategyType.SYS_RESERVED_NONE;
    /**
     * This is the list of use cases that follow the same routing strategy.
     */
    AudioHalAttributesGroup[] attributesGroups;
}
