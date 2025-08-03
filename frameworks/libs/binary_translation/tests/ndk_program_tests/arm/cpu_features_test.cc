/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "gtest/gtest.h"

#include <asm/hwcap.h>
#include <cpu-features.h>
#include <sys/auxv.h>
#include <sys/system_properties.h>

#include <fstream>
#include <string>

namespace {

// Test CPU features interfaces from
// https://developer.android.com/ndk/guides/cpu-features
//
// Required features for armeabi-v7a from
// https://developer.android.com/ndk/guides/abis
// are only:
// - armeabi
// - thumb-2
// - vfpv3-d16
//
// For ndk-translation we check that we get all the values as expected.

class ProcCpuinfoFeatures {
 public:
  ProcCpuinfoFeatures() {
    std::ifstream cpuinfo("/proc/cpuinfo");
    std::string line;
    while (std::getline(cpuinfo, line)) {
      // Warning: line caption for features is architecture dependent!
      if (line.find("Features") != std::string::npos) {
        features_ = line;
        features_ += " ";  // add feature delimiter at the end.
      } else if (line.find("Hardware") != std::string::npos) {
        hardware_ = line;
      }
    }
  }

  bool Empty() const { return features_.empty(); }

  bool Get(const char* name) const {
    // Search for name surrounded by delimiters.
    std::string value = " ";
    value += name;
    value += " ";
    return features_.find(name) != std::string::npos;
  }

  bool IsNdkTranslation() const { return hardware_.find("ndk_translation") != std::string::npos; }

 private:
  std::string features_;
  std::string hardware_;
};

class BitFeatures {
 public:
  explicit BitFeatures(uint64_t features) : features_(features) {}

  bool Empty() const { return features_ == 0; }

  bool Get(uint64_t bits) const { return (features_ & bits) != 0; }

 private:
  uint64_t features_;
};

bool IsNdkTranslation() {
  char value[PROP_VALUE_MAX];
  __system_property_get("ro.dalvik.vm.native.bridge", value);
  return std::string(value) == "libndk_translation.so";
}

TEST(CpuFeatures, proc_cpuinfo) {
  ProcCpuinfoFeatures cpuinfo;
  // Attention: ART mounts guest cpuinfo in case of native bridge. No one does
  // that in case of standalone executable, so we observe the host one.
  if (cpuinfo.Empty()) {
    GTEST_LOG_(INFO) << "skipping test, /proc/cpuinfo features are empty.\n";
    return;
  }

  EXPECT_TRUE(cpuinfo.Get("vfpv3"));
  EXPECT_TRUE(cpuinfo.Get("thumb"));

  if (!IsNdkTranslation()) {
    GTEST_LOG_(INFO) << "skipping test, not under ndk_translation.\n";
    return;
  }

  EXPECT_TRUE(cpuinfo.Get("neon"));
  EXPECT_TRUE(cpuinfo.Get("vfp"));
  EXPECT_TRUE(cpuinfo.Get("swp"));
  EXPECT_TRUE(cpuinfo.Get("half"));
  EXPECT_TRUE(cpuinfo.Get("thumb"));
  EXPECT_TRUE(cpuinfo.Get("fastmult"));
  EXPECT_TRUE(cpuinfo.Get("edsp"));
  EXPECT_TRUE(cpuinfo.Get("vfpv3"));
  EXPECT_TRUE(cpuinfo.Get("vfpv4"));
  EXPECT_TRUE(cpuinfo.Get("idiva"));
  EXPECT_TRUE(cpuinfo.Get("idivt"));
}

TEST(CpuFeatures, getauxval_AT_HWCAP) {
  BitFeatures hwcap(getauxval(AT_HWCAP));

  EXPECT_TRUE(hwcap.Get(HWCAP_THUMB));
  EXPECT_TRUE(hwcap.Get(HWCAP_VFPv3 | HWCAP_VFPv3D16));

  if (!IsNdkTranslation()) {
    GTEST_LOG_(INFO) << "skipping test, not under ndk_translation.\n";
    return;
  }

  EXPECT_TRUE(hwcap.Get(HWCAP_SWP));
  EXPECT_TRUE(hwcap.Get(HWCAP_HALF));
  EXPECT_TRUE(hwcap.Get(HWCAP_FAST_MULT));
  EXPECT_TRUE(hwcap.Get(HWCAP_VFP));
  EXPECT_TRUE(hwcap.Get(HWCAP_EDSP));
  EXPECT_TRUE(hwcap.Get(HWCAP_NEON));
  EXPECT_TRUE(hwcap.Get(HWCAP_VFPv3));
  EXPECT_TRUE(hwcap.Get(HWCAP_VFPv4));
  EXPECT_TRUE(hwcap.Get(HWCAP_IDIVA));
  EXPECT_TRUE(hwcap.Get(HWCAP_IDIVT));
  EXPECT_TRUE(hwcap.Get(HWCAP_IDIV));

  EXPECT_FALSE(hwcap.Get(HWCAP_26BIT));
  EXPECT_FALSE(hwcap.Get(HWCAP_FPA));
  EXPECT_FALSE(hwcap.Get(HWCAP_JAVA));
  EXPECT_FALSE(hwcap.Get(HWCAP_IWMMXT));
  EXPECT_FALSE(hwcap.Get(HWCAP_CRUNCH));
  EXPECT_FALSE(hwcap.Get(HWCAP_THUMBEE));
  EXPECT_FALSE(hwcap.Get(HWCAP_VFPv3D16));
  EXPECT_FALSE(hwcap.Get(HWCAP_TLS));
  EXPECT_FALSE(hwcap.Get(HWCAP_VFPD32));
  EXPECT_FALSE(hwcap.Get(HWCAP_LPAE));
  EXPECT_FALSE(hwcap.Get(HWCAP_EVTSTRM));
}

TEST(CpuFeatures, getauxval_AT_HWCAP2) {
  BitFeatures hwcap2(getauxval(AT_HWCAP2));

  if (!IsNdkTranslation()) {
    GTEST_LOG_(INFO) << "skipping test, not under ndk_translation.\n";
    return;
  }

  EXPECT_FALSE(hwcap2.Get(HWCAP2_AES));
  EXPECT_FALSE(hwcap2.Get(HWCAP2_PMULL));
  EXPECT_FALSE(hwcap2.Get(HWCAP2_SHA1));
  EXPECT_FALSE(hwcap2.Get(HWCAP2_SHA2));
  EXPECT_FALSE(hwcap2.Get(HWCAP2_CRC32));
}

TEST(CpuFeatures, android_getCpuFeatures) {
  AndroidCpuFamily cpu_family = android_getCpuFamily();
  ASSERT_EQ(cpu_family, ANDROID_CPU_FAMILY_ARM);

  BitFeatures android_cpu_features(android_getCpuFeatures());

  EXPECT_TRUE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_ARMv7));

  // VFPv3 here means at least 16 FP registers.
  EXPECT_TRUE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_VFPv3));

  if (!IsNdkTranslation()) {
    GTEST_LOG_(INFO) << "skipping test, not under ndk_translation.\n";
    return;
  }

  EXPECT_TRUE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_NEON));

  EXPECT_TRUE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_VFPv2));
  EXPECT_TRUE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_VFP_D32));
  EXPECT_TRUE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_VFP_FP16));
  EXPECT_TRUE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_VFP_FMA));

  // TODO(b/118179742): We claim it but don't actually implement.
  EXPECT_TRUE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_NEON_FMA));

  EXPECT_TRUE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_IDIV_ARM));
  EXPECT_TRUE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_IDIV_THUMB2));

  EXPECT_FALSE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_iWMMXt));
  EXPECT_FALSE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_AES));
  EXPECT_FALSE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_PMULL));
  EXPECT_FALSE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_SHA1));
  EXPECT_FALSE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_SHA2));
  EXPECT_FALSE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_CRC32));

  ProcCpuinfoFeatures cpuinfo;
  if (!cpuinfo.IsNdkTranslation()) {
    GTEST_LOG_(INFO) << "skipping test, ndk_translation /proc/cpuinfo is not mounted.\n";
    return;
  }

  // android_getCpuFeatures enables ANDROID_CPU_ARM_FEATURE_LDREX_STREX by 'CPU architecture' field
  // from /proc/cpuinfo, so the check fails when we don't have it mounted.
  EXPECT_TRUE(android_cpu_features.Get(ANDROID_CPU_ARM_FEATURE_LDREX_STREX));
}

}  // namespace
