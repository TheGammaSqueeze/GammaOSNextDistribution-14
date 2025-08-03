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

package android.net;

@Backing(type="int")
enum NativeVpnType {
  /**
   * A VPN created by an app using the VpnService API.
   */
  SERVICE = 1,

  /**
   * A VPN created using a VpnManager API such as startProvisionedVpnProfile.
   */
  PLATFORM = 2,

  /**
   * An IPsec VPN created by the built-in LegacyVpnRunner.
   */
  LEGACY = 3,

  /**
   * An VPN created by OEM code through other means than VpnService or VpnManager.
   */
  OEM = 4,
}