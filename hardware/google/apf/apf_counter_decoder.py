#!/usr/bin/env python3
#
# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
import argparse

# The following list must be in sync with
# https://cs.android.com/android/platform/superproject/+/master:packages/modules/NetworkStack/src/android/net/apf/ApfFilter.java;l=139?q=ApfFilter.java
Counter = (
    "TOTAL_PACKETS",
    "PASSED_ARP",
    "PASSED_DHCP",
    "PASSED_IPV4",
    "PASSED_IPV6_NON_ICMP",
    "PASSED_IPV4_UNICAST",
    "PASSED_IPV6_ICMP",
    "PASSED_IPV6_UNICAST_NON_ICMP",
    "PASSED_ARP_NON_IPV4",
    "PASSED_ARP_UNKNOWN",
    "PASSED_ARP_UNICAST_REPLY",
    "PASSED_NON_IP_UNICAST",
    "PASSED_MDNS",
    "DROPPED_ETH_BROADCAST",
    "DROPPED_RA",
    "DROPPED_GARP_REPLY",
    "DROPPED_ARP_OTHER_HOST",
    "DROPPED_IPV4_L2_BROADCAST",
    "DROPPED_IPV4_BROADCAST_ADDR",
    "DROPPED_IPV4_BROADCAST_NET",
    "DROPPED_IPV4_MULTICAST",
    "DROPPED_IPV6_ROUTER_SOLICITATION",
    "DROPPED_IPV6_MULTICAST_NA",
    "DROPPED_IPV6_MULTICAST",
    "DROPPED_IPV6_MULTICAST_PING",
    "DROPPED_IPV6_NON_ICMP_MULTICAST",
    "DROPPED_802_3_FRAME",
    "DROPPED_ETHERTYPE_BLACKLISTED",
    "DROPPED_ARP_REPLY_SPA_NO_HOST",
    "DROPPED_IPV4_KEEPALIVE_ACK",
    "DROPPED_IPV6_KEEPALIVE_ACK",
    "DROPPED_IPV4_NATT_KEEPALIVE",
    "DROPPED_MDNS"
)

def main():
  parser = argparse.ArgumentParser(description='Parse APF counter HEX string.')
  parser.add_argument('hexstring')
  args = parser.parse_args()
  data_hexstring = args.hexstring
  data_bytes = bytes.fromhex(data_hexstring)
  data_bytes_len = len(data_bytes)
  for i in range(len(Counter)):
    cnt = int.from_bytes(data_bytes[data_bytes_len - 4 * (i + 1) :
                                    data_bytes_len - 4 * i], byteorder = "big")
    if cnt != 0:
      print("{} : {}".format(Counter[i], cnt))

if __name__ == "__main__":
  main()
