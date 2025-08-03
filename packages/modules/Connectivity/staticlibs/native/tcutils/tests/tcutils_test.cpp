/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * TcUtilsTest.cpp - unit tests for TcUtils.cpp
 */

#include <gtest/gtest.h>

#include "bpf/KernelUtils.h"
#include <tcutils/tcutils.h>

#include <BpfSyscallWrappers.h>
#include <errno.h>
#include <linux/if_ether.h>

namespace android {

TEST(LibTcUtilsTest, IsEthernetOfNonExistingIf) {
  bool result = false;
  int error = isEthernet("not_existing_if", result);
  ASSERT_FALSE(result);
  ASSERT_EQ(-ENODEV, error);
}

TEST(LibTcUtilsTest, IsEthernetOfLoopback) {
  bool result = false;
  int error = isEthernet("lo", result);
  ASSERT_FALSE(result);
  ASSERT_EQ(-EAFNOSUPPORT, error);
}

// If wireless 'wlan0' interface exists it should be Ethernet.
// See also HardwareAddressTypeOfWireless.
TEST(LibTcUtilsTest, IsEthernetOfWireless) {
  bool result = false;
  int error = isEthernet("wlan0", result);
  if (!result && error == -ENODEV)
    return;

  ASSERT_EQ(0, error);
  ASSERT_TRUE(result);
}

// If cellular 'rmnet_data0' interface exists it should
// *probably* not be Ethernet and instead be RawIp.
// See also HardwareAddressTypeOfCellular.
TEST(LibTcUtilsTest, IsEthernetOfCellular) {
  bool result = false;
  int error = isEthernet("rmnet_data0", result);
  if (!result && error == -ENODEV)
    return;

  ASSERT_EQ(0, error);
  ASSERT_FALSE(result);
}

// See Linux kernel source in include/net/flow.h
static constexpr int LOOPBACK_IFINDEX = 1;

TEST(LibTcUtilsTest, AttachReplaceDetachClsactLo) {
  // This attaches and detaches a configuration-less and thus no-op clsact
  // qdisc to loopback interface (and it takes fractions of a second)
  EXPECT_EQ(0, tcAddQdiscClsact(LOOPBACK_IFINDEX));
  EXPECT_EQ(0, tcReplaceQdiscClsact(LOOPBACK_IFINDEX));
  EXPECT_EQ(0, tcDeleteQdiscClsact(LOOPBACK_IFINDEX));
  EXPECT_EQ(-EINVAL, tcDeleteQdiscClsact(LOOPBACK_IFINDEX));
}

TEST(LibTcUtilsTest, AddAndDeleteBpfFilter) {
  // TODO: this should likely be in the tethering module, where using netd.h would be ok
  static constexpr char bpfProgPath[] =
      "/sys/fs/bpf/tethering/prog_offload_schedcls_tether_downstream6_ether";
  const int errNOENT = bpf::isAtLeastKernelVersion(4, 19, 0) ? ENOENT : EINVAL;

  // static test values
  static constexpr bool ingress = true;
  static constexpr uint16_t prio = 17;
  static constexpr uint16_t proto = ETH_P_ALL;

  // try to delete missing filter from missing qdisc
  EXPECT_EQ(-EINVAL, tcDeleteFilter(LOOPBACK_IFINDEX, ingress, prio, proto));
  // try to attach bpf filter to missing qdisc
  EXPECT_EQ(-EINVAL, tcAddBpfFilter(LOOPBACK_IFINDEX, ingress, prio, proto,
                                    bpfProgPath));
  // add the clsact qdisc
  EXPECT_EQ(0, tcAddQdiscClsact(LOOPBACK_IFINDEX));
  // try to delete missing filter when there is a qdisc attached
  EXPECT_EQ(-errNOENT, tcDeleteFilter(LOOPBACK_IFINDEX, ingress, prio, proto));
  // add and delete a bpf filter
  EXPECT_EQ(
      0, tcAddBpfFilter(LOOPBACK_IFINDEX, ingress, prio, proto, bpfProgPath));
  EXPECT_EQ(0, tcDeleteFilter(LOOPBACK_IFINDEX, ingress, prio, proto));
  // try to remove the same filter a second time
  EXPECT_EQ(-errNOENT, tcDeleteFilter(LOOPBACK_IFINDEX, ingress, prio, proto));
  // remove the clsact qdisc
  EXPECT_EQ(0, tcDeleteQdiscClsact(LOOPBACK_IFINDEX));
  // once again, try to delete missing filter from missing qdisc
  EXPECT_EQ(-EINVAL, tcDeleteFilter(LOOPBACK_IFINDEX, ingress, prio, proto));
}

TEST(LibTcUtilsTest, AddAndDeleteIngressPoliceFilter) {
  // TODO: this should likely be in the tethering module, where using netd.h would be ok
  static constexpr char bpfProgPath[] =
      "/sys/fs/bpf/netd_shared/prog_netd_schedact_ingress_account";
  int fd = bpf::retrieveProgram(bpfProgPath);
  ASSERT_LE(3, fd);
  close(fd);

  const int errNOENT = bpf::isAtLeastKernelVersion(4, 19, 0) ? ENOENT : EINVAL;

  // static test values
  static constexpr unsigned rateInBytesPerSec =
      1024 * 1024; // 8mbit/s => 1mbyte/s => 1024*1024 bytes/s.
  static constexpr uint16_t prio = 17;
  static constexpr uint16_t proto = ETH_P_ALL;

  // try to delete missing filter from missing qdisc
  EXPECT_EQ(-EINVAL,
            tcDeleteFilter(LOOPBACK_IFINDEX, true /*ingress*/, prio, proto));
  // try to attach bpf filter to missing qdisc
  EXPECT_EQ(-EINVAL, tcAddIngressPoliceFilter(LOOPBACK_IFINDEX, prio, proto,
                                              rateInBytesPerSec, bpfProgPath));
  // add the clsact qdisc
  EXPECT_EQ(0, tcAddQdiscClsact(LOOPBACK_IFINDEX));
  // try to delete missing filter when there is a qdisc attached
  EXPECT_EQ(-errNOENT,
            tcDeleteFilter(LOOPBACK_IFINDEX, true /*ingress*/, prio, proto));
  // add and delete a bpf filter
  EXPECT_EQ(0, tcAddIngressPoliceFilter(LOOPBACK_IFINDEX, prio, proto,
                                        rateInBytesPerSec, bpfProgPath));
  EXPECT_EQ(0, tcDeleteFilter(LOOPBACK_IFINDEX, true /*ingress*/, prio, proto));
  // try to remove the same filter a second time
  EXPECT_EQ(-errNOENT,
            tcDeleteFilter(LOOPBACK_IFINDEX, true /*ingress*/, prio, proto));
  // remove the clsact qdisc
  EXPECT_EQ(0, tcDeleteQdiscClsact(LOOPBACK_IFINDEX));
  // once again, try to delete missing filter from missing qdisc
  EXPECT_EQ(-EINVAL,
            tcDeleteFilter(LOOPBACK_IFINDEX, true /*ingress*/, prio, proto));
}

} // namespace android
