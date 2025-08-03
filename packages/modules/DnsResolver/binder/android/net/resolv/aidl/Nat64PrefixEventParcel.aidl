/**
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.resolv.aidl;

/**
 * Nat64 prefix operation event.
 *
 * {@hide}
 */
@JavaDerive(toString=true)
parcelable Nat64PrefixEventParcel {

    /** The ID of the network the prefix operation was performed on. */
    int netId;

    /**
     * The NAT64 prefix operation.
     * There is only one prefix at a time for each netId. If a prefix is added, it replaces the
     * previous-added prefix.
     */
    int prefixOperation;

    /** The detected NAT64 prefix address. */
    @utf8InCpp String prefixAddress;

    /** The prefix length associated with this NAT64 prefix. */
    int prefixLength;
}
