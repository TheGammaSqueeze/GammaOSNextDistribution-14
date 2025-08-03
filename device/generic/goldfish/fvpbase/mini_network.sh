#!/system/bin/sh
#
# Copyright 2020 Arm Ltd. All rights reserved.
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

# The Android network stack is not included in Nano, so we need to
# configure the network manually. The configuration below is the same as
# would be obtained from the fast model's emulated DHCP.

ip address add 172.20.51.1/24 broadcast 172.20.51.255 dev eth0
ip link set eth0 up
ip route add default via 172.20.51.254
