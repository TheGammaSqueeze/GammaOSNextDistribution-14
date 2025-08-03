/**
 * Copyright (c) 2022, The Android Open Source Project
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

package android.net;

@JavaOnlyImmutable
parcelable IpSecMigrateInfoParcel {
  /** The unique identifier for allocated resources. */
  int requestId;
  /**
   * The address family identifier for the new selector. Can be AF_INET
   * or AF_INET6.
   */
  int selAddrFamily;
  /** IPSEC_DIRECTION_IN or IPSEC_DIRECTION_OUT. */
  int direction;
  /**
   * The IP address for the current sending endpoint.
   *
   * The local address for an outbound SA and the remote address for an
   * inbound SA.
   */
  @utf8InCpp String oldSourceAddress;
  /**
   * The IP address for the current receiving endpoint.
   *
   * The remote address for an outbound SA and the local address for an
   * inbound SA.
   */
  @utf8InCpp String oldDestinationAddress;
  /** The IP address for the new sending endpoint. */
  @utf8InCpp String newSourceAddress;
  /** The IP address for the new receiving endpoint. */
  @utf8InCpp String newDestinationAddress;
  /** The identifier for the XFRM interface. */
  int interfaceId;
}
