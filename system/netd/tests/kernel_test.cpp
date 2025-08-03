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
 *
 */

#include <gtest/gtest.h>
#include <vintf/VintfObject.h>

#include <fstream>
#include <string>

#include "bpf/KernelUtils.h"

namespace android {
namespace net {

namespace {

using ::android::vintf::RuntimeInfo;
using ::android::vintf::VintfObject;

class KernelConfigVerifier final {
  public:
    KernelConfigVerifier() : mRuntimeInfo(VintfObject::GetRuntimeInfo()) {}

    bool hasOption(const std::string& option) const {
        const auto& configMap = mRuntimeInfo->kernelConfigs();
        auto it = configMap.find(option);
        if (it != configMap.cend()) {
            return it->second == "y";
        }
        return false;
    }

    bool hasModule(const std::string& option) const {
        const auto& configMap = mRuntimeInfo->kernelConfigs();
        auto it = configMap.find(option);
        if (it != configMap.cend()) {
            return (it->second == "y") || (it->second == "m");
        }
        return false;
    }

  private:
    std::shared_ptr<const RuntimeInfo> mRuntimeInfo;
};

}  // namespace

/**
 * If this test fails, enable the following kernel modules in your kernel config:
 * CONFIG_NET_CLS_MATCHALL=y
 * CONFIG_NET_ACT_POLICE=y
 * CONFIG_NET_ACT_BPF=y
 * CONFIG_BPF_JIT=y
 */
TEST(KernelTest, TestRateLimitingSupport) {
    KernelConfigVerifier configVerifier;
    EXPECT_TRUE(configVerifier.hasOption("CONFIG_NET_CLS_MATCHALL"));
    EXPECT_TRUE(configVerifier.hasOption("CONFIG_NET_ACT_POLICE"));
    EXPECT_TRUE(configVerifier.hasOption("CONFIG_NET_ACT_BPF"));
    EXPECT_TRUE(configVerifier.hasOption("CONFIG_BPF_JIT"));
}

TEST(KernelTest, TestBpfJitAlwaysOn) {
    // 32-bit arm & x86 kernels aren't capable of JIT-ing all of our BPF code,
    if (bpf::isKernel32Bit()) GTEST_SKIP() << "Exempt on 32-bit kernel.";
    KernelConfigVerifier configVerifier;
    ASSERT_TRUE(configVerifier.hasOption("CONFIG_BPF_JIT_ALWAYS_ON"));
}

/* Android 14/U should only launch on 64-bit kernels
 *   T launches on 5.10/5.15
 *   U launches on 5.15/6.1
 * So >=5.16 implies isKernel64Bit()
 */
TEST(KernelTest, TestKernel64Bit) {
    if (!bpf::isAtLeastKernelVersion(5, 16, 0)) GTEST_SKIP() << "Exempt on < 5.16 kernel.";
    ASSERT_TRUE(bpf::isKernel64Bit());
}

// Android V requires x86 kernels to be 64-bit, as among other things
// 32-bit x86 kernels have subtly different structure layouts for XFRM
TEST(KernelTest, TestX86Kernel64Bit) {
    if (!bpf::isX86()) GTEST_SKIP() << "Exempt on non-x86 architecture.";
    ASSERT_TRUE(bpf::isKernel64Bit());
}

// Android V requires 4.19+
TEST(KernelTest, TestKernel419) {
    ASSERT_TRUE(bpf::isAtLeastKernelVersion(4, 19, 0));
}

static bool isKernel(unsigned major, unsigned minor) {
    return bpf::isAtLeastKernelVersion(major, minor, 0)
        && !bpf::isAtLeastKernelVersion(major, minor + 1, 0);
}

TEST(KernelTest, TestIsLTS) {
    ASSERT_TRUE(
        isKernel(4, 19) ||
        isKernel(5, 4) ||
        isKernel(5, 10) ||
        isKernel(5, 15) ||
        isKernel(6, 1) ||
        isKernel(6, 6));
}

#define ifIsKernelThenMinLTS(major, minor, sub) do { \
  if (!isKernel((major), (minor))) GTEST_SKIP() << "Not for this kernel major/minor version."; \
  ASSERT_TRUE(bpf::isAtLeastKernelVersion((major), (minor), (sub))); \
} while (0)

TEST(KernelTest, TestMinRequiredLTS_4_19) { ifIsKernelThenMinLTS(4, 19, 236); }
TEST(KernelTest, TestMinRequiredLTS_5_4)  { ifIsKernelThenMinLTS(5, 4, 186); }
TEST(KernelTest, TestMinRequiredLTS_5_10) { ifIsKernelThenMinLTS(5, 10, 199); }
TEST(KernelTest, TestMinRequiredLTS_5_15) { ifIsKernelThenMinLTS(5, 15, 136); }
TEST(KernelTest, TestMinRequiredLTS_6_1)  { ifIsKernelThenMinLTS(6, 1, 57); }
TEST(KernelTest, TestMinRequiredLTS_6_6)  { ifIsKernelThenMinLTS(6, 6, 0); }

TEST(KernelTest, TestSupportsCommonUsbEthernetDongles) {
    KernelConfigVerifier configVerifier;
    if (!configVerifier.hasModule("CONFIG_USB")) GTEST_SKIP() << "Exempt without USB support.";
    EXPECT_TRUE(configVerifier.hasModule("CONFIG_USB_NET_AX8817X"));
    EXPECT_TRUE(configVerifier.hasModule("CONFIG_USB_NET_AX88179_178A"));
    EXPECT_TRUE(configVerifier.hasModule("CONFIG_USB_NET_CDCETHER"));
    EXPECT_TRUE(configVerifier.hasModule("CONFIG_USB_NET_CDC_EEM"));
    EXPECT_TRUE(configVerifier.hasModule("CONFIG_USB_NET_CDC_NCM"));
    if (bpf::isAtLeastKernelVersion(5, 4, 0))
        EXPECT_TRUE(configVerifier.hasModule("CONFIG_USB_NET_AQC111"));

    EXPECT_TRUE(configVerifier.hasModule("CONFIG_USB_RTL8152"));
    EXPECT_TRUE(configVerifier.hasModule("CONFIG_USB_RTL8150"));
    if (bpf::isAtLeastKernelVersion(5, 15, 0)) {
        EXPECT_TRUE(configVerifier.hasModule("CONFIG_USB_RTL8153_ECM"));
        EXPECT_TRUE(configVerifier.hasModule("CONFIG_AX88796B_PHY"));
    }
}

}  // namespace net
}  // namespace android
