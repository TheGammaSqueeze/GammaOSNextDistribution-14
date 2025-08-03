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

package android.net.netd.aidl;

import android.net.UidRangeParcel;

/**
 * The configuration to add or remove UID ranges.
 *
 * {@hide}
 */
@JavaDerive(toString=true, equals=true)
@JavaOnlyImmutable
parcelable NativeUidRangeConfig {
    /** The network ID of the network to add/remove the ranges to/from. */
    int netId;

    /** A set of non-overlapping ranges of UIDs. */
    UidRangeParcel[] uidRanges;

    /**
     * The priority of this UID range config. 0 is the highest priority; 999 is the lowest priority.
     * The function of this parameter is to adjust the priority when the same UID is set to
     * different networks for different features.
     */
    int subPriority;
}