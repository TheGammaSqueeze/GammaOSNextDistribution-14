/**
 * Copyright (c) 2016, The Android Open Source Project
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
///////////////////////////////////////////////////////////////////////////////
// THIS FILE IS IMMUTABLE. DO NOT EDIT IN ANY CASE.                          //
///////////////////////////////////////////////////////////////////////////////

// This file is a snapshot of an AIDL file. Do not edit it manually. There are
// two cases:
// 1). this is a frozen version file - do not edit this in any case.
// 2). this is a 'current' file. If you make a backwards compatible change to
//     the interface (from the latest frozen version), the build system will
//     prompt you to update this file with `m <name>-update-api`.
//
// You must not make a backward incompatible change to any AIDL file built
// with the aidl_interface module type with versions property set. The module
// type is used to build AIDL files in a way that they can be used across
// independently updatable components of the system. If a device is shipped
// with such a backward incompatible change, it has a high risk of breaking
// later when a module using the interface is updated, e.g., Mainline modules.

package android.net.metrics;
/* @hide */
interface INetdEventListener {
  oneway void onDnsEvent(int netId, int eventType, int returnCode, int latencyMs, @utf8InCpp String hostname, in @utf8InCpp String[] ipAddresses, int ipAddressesCount, int uid);
  oneway void onPrivateDnsValidationEvent(int netId, String ipAddress, String hostname, boolean validated);
  oneway void onConnectEvent(int netId, int error, int latencyMs, String ipAddr, int port, int uid);
  oneway void onWakeupEvent(String prefix, int uid, int ethertype, int ipNextHeader, in byte[] dstHw, String srcIp, String dstIp, int srcPort, int dstPort, long timestampNs);
  oneway void onTcpSocketStatsEvent(in int[] networkIds, in int[] sentPackets, in int[] lostPackets, in int[] rttUs, in int[] sentAckDiffMs);
  oneway void onNat64PrefixEvent(int netId, boolean added, @utf8InCpp String prefixString, int prefixLength);
  const int EVENT_GETADDRINFO = 1;
  const int EVENT_GETHOSTBYNAME = 2;
  const int EVENT_GETHOSTBYADDR = 3;
  const int EVENT_RES_NSEND = 4;
  const int REPORTING_LEVEL_NONE = 0;
  const int REPORTING_LEVEL_METRICS = 1;
  const int REPORTING_LEVEL_FULL = 2;
  const int DNS_REPORTED_IP_ADDRESSES_LIMIT = 10;
}
