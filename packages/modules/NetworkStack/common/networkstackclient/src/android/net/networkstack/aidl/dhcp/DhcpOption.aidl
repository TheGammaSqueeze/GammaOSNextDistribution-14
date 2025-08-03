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
 * See the License for the specific language governing perNmissions and
 * limitations under the License.
 */

package android.net.networkstack.aidl.dhcp;

@JavaDerive(toString=true)
parcelable DhcpOption {
    /** The type of the option. */
    byte type;

    /**
     * The raw bytes of the DHCP option. When requesting a DHCP option, a null value
     * indicates that the option should appear in the PRL and not in the options section.
     */
    @nullable byte[] value;
}
