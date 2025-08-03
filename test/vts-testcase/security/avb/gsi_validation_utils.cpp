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

#include <memory>

#include <android-base/file.h>
#include <android-base/properties.h>
#include <android/api-level.h>
#include <androidfw/AssetManager.h>
#include <androidfw/ResourceTypes.h>
#include <gtest/gtest.h>
#include <openssl/sha.h>
#include <stdio.h>
#include <utils/String8.h>
#include <utils/Vector.h>

#include "gsi_validation_utils.h"

uint8_t HexDigitToByte(char c) {
  if (c >= '0' && c <= '9') {
    return c - '0';
  }
  if (c >= 'a' && c <= 'f') {
    return c - 'a' + 10;
  }
  if (c >= 'A' && c <= 'Z') {
    return c - 'A' + 10;
  }
  return 0xff;
}

bool HexToBytes(const std::string &hex, std::vector<uint8_t> *bytes) {
  if (hex.size() % 2 != 0) {
    return false;
  }
  bytes->resize(hex.size() / 2);
  for (unsigned i = 0; i < bytes->size(); i++) {
    uint8_t hi = HexDigitToByte(hex[i * 2]);
    uint8_t lo = HexDigitToByte(hex[i * 2 + 1]);
    if (lo > 0xf || hi > 0xf) {
      return false;
    }
    bytes->at(i) = (hi << 4) | lo;
  }
  return true;
}

const char kNibble2Hex[16] = {'0', '1', '2', '3', '4', '5', '6', '7',
                              '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

std::string BytesToHex(const std::vector<uint8_t> &bytes) {
  std::string retval;
  retval.reserve(bytes.size() * 2 + 1);
  for (uint8_t byte : bytes) {
    retval.push_back(kNibble2Hex[0x0F & (byte >> 4)]);
    retval.push_back(kNibble2Hex[0x0F & byte]);
  }
  return retval;
}

std::unique_ptr<ShaHasher> CreateShaHasher(const std::string &algorithm) {
  if (algorithm == "sha1") {
    return std::make_unique<ShaHasherImpl<SHA_CTX>>(
        SHA1_Init, SHA1_Update, SHA1_Final, SHA_DIGEST_LENGTH);
  }
  if (algorithm == "sha256") {
    return std::make_unique<ShaHasherImpl<SHA256_CTX>>(
        SHA256_Init, SHA256_Update, SHA256_Final, SHA256_DIGEST_LENGTH);
  }
  if (algorithm == "sha512") {
    return std::make_unique<ShaHasherImpl<SHA512_CTX>>(
        SHA512_Init, SHA512_Update, SHA512_Final, SHA512_DIGEST_LENGTH);
  }
  return nullptr;
}

static std::optional<std::string> ReadCommandToString(const std::string &command) {
  std::unique_ptr<FILE, decltype(&pclose)> cmd_out_stream(popen(command.c_str(), "re"),
                                                          pclose);

  if (!cmd_out_stream) {
    GTEST_LOG_(ERROR) << "Invocation of cmd: " << command << " failed";
    return std::nullopt;
  }

  int fd = fileno(cmd_out_stream.get());
  if (fd < 0) {
    GTEST_LOG_(ERROR) << "Unable to acquire file descriptor for cmd: " << command;
    return std::nullopt;
  }

  std::string output;
  if (!android::base::ReadFdToString(fd, &output)) {
    GTEST_LOG_(ERROR) << "Unable to read cmd: " << command << " output to string";
    return std::nullopt;
  }

  return output;
}

// Returns true iff the device has the specified feature.
static bool DeviceSupportsFeature(const std::string &feature) {
  std::optional<std::string> features = ReadCommandToString("pm list features");

  if (!features.has_value()) {
    return false;
  }

  return features.value().find(feature) != std::string::npos;
}

// Returns true iff the device has the specified package installed.
static bool DeviceHasPackage(const std::string &package_name) {
  std::optional<std::string> packages = ReadCommandToString("pm list packages");

  if (!packages.has_value()) {
    return false;
  }

  return packages.value().find(package_name) != std::string::npos;
}

static bool IsWatchDevice() {
  return DeviceSupportsFeature("android.hardware.type.watch");
}

bool IsTvDevice() {
  return DeviceSupportsFeature("android.hardware.type.television") ||
         DeviceSupportsFeature("android.software.leanback");
}

bool IsAutomotiveDevice() {
  return DeviceSupportsFeature("android.hardware.type.automotive");
}

static bool IsVrHeadsetDevice() {
  android::AssetManager assetManager;
  // This apk is always available on devices.
  constexpr const static char *path = "/system/framework/framework-res.apk";

  if (!assetManager.addAssetPath(android::String8(path), nullptr)) {
    GTEST_LOG_(ERROR) << "Failed to add asset path";
    return false;
  }

  const android::ResTable& res = assetManager.getResources(false);
  if (res.getError() != android::NO_ERROR) {
    GTEST_LOG_(ERROR) << "getResources() invocation failed. Cannot determine device configuration.";
    return false;
  }

  android::Vector<android::ResTable_config> configs;
  res.getConfigurations(&configs, true);
  // This loop iterates through various configs of the APK
  // and searches for the UI mode, which is set to "vrheadset"
  // for VR headsets.
  for (const auto &config : configs) {
    if (config.toString().find("vrheadset") != std::string::npos) {
      return true;
    }
  }

  return false;
}

static bool IsArcDevice() {
  return DeviceSupportsFeature("org.chromium.arc") ||
         DeviceSupportsFeature("org.chromium.arc.device_management");
}

static bool IsUserBuild() {
  return android::base::GetProperty("ro.build.type", "") == "user";
}

// Returns whether the Play Store is installed for this build
// For User builds, check the Play Store package is user cert signed
// For Userdebug just check if the Play Store package exists.
static bool DeviceHasPlayStore() {
  bool has_playstore = DeviceHasPackage("com.android.vending");

  if (!has_playstore) {
    return false;
  }

  if (IsUserBuild()) {
    std::optional<std::string> package_dump = ReadCommandToString("pm dump com.android.vending");

    if (!package_dump.has_value())
      return false;

    const std::string playstore_user_cert = "F0:FD:6C:5B:41:0F:25:CB:25:C3:B5:"
                                            "33:46:C8:97:2F:AE:30:F8:EE:74:11:"
                                            "DF:91:04:80:AD:6B:2D:60:DB:83";

    bool certified_playstore = package_dump.value().find(playstore_user_cert) != std::string::npos;

    if (!certified_playstore) {
      GTEST_LOG_(INFO) << "Device has a user build but the version of playstore is not certified";
      return false;
    }

    GTEST_LOG_(INFO) << "Device has a user build and a certified version of playstore";
    return true;
  }

  GTEST_LOG_(INFO) << "Device has playstore on a non-user build";
  return true;
}

static bool DeviceHasGmsCore() {
  return DeviceHasPackage("com.google.android.gms");
}

static bool IsLowRamDevice() {
  return (GetSdkLevel() >= __ANDROID_API_O_MR1__) &&
         DeviceSupportsFeature("android.hardware.ram.low");
}

// Implementation taken from GmsUtil::isGoDevice()
//
// Android Go is only for phones and tablets. However, there is
// no way to identify if a device is a phone or tablet, so we
// must ensure that the device is not any other form factor. New
// form factors should be tested against here.
bool IsGoDevice() {
  return IsLowRamDevice() && DeviceHasGmsCore() && DeviceHasPlayStore() &&
         !IsWatchDevice() && !IsTvDevice() && !IsAutomotiveDevice() &&
         !IsVrHeadsetDevice() && !IsArcDevice();
}

bool ValidatePublicKeyBlob(const std::string &key_blob_to_validate) {
  if (key_blob_to_validate.empty()) {
    GTEST_LOG_(ERROR) << "Failed to validate an empty key";
    return false;
  }

  const std::string exec_dir = android::base::GetExecutableDirectory();
  std::vector<std::string> allowed_key_names = {
      "q-gsi.avbpubkey", "r-gsi.avbpubkey",    "s-gsi.avbpubkey",
      "t-gsi.avbpubkey", "qcar-gsi.avbpubkey",
  };
  std::vector<std::string> allowed_oem_key_names = {
      "gki-oem-2024.avbpubkey",
  };
  if (!IsGoDevice()) {
    allowed_key_names.insert(allowed_key_names.end(),
                             allowed_oem_key_names.begin(),
                             allowed_oem_key_names.end());
  }
  for (const auto &key_name : allowed_key_names) {
    const auto key_path = exec_dir + "/" + key_name;
    std::string allowed_key_blob;
    if (android::base::ReadFileToString(key_path, &allowed_key_blob)) {
      if (key_blob_to_validate == allowed_key_blob) {
        GTEST_LOG_(INFO) << "Found matching GSI key: " << key_path;
        return true;
      }
    }
  }
  return false;
}

const uint32_t kCurrentApiLevel = 10000;

static uint32_t ReadApiLevelProps(
    const std::vector<std::string> &api_level_props) {
  uint32_t api_level = kCurrentApiLevel;
  for (const auto &api_level_prop : api_level_props) {
    api_level = android::base::GetUintProperty<uint32_t>(api_level_prop,
                                                         kCurrentApiLevel);
    if (api_level != kCurrentApiLevel) {
      break;
    }
  }
  return api_level;
}

uint32_t GetSdkLevel() {
  uint32_t sdk_level = ReadApiLevelProps({"ro.build.version.sdk"});
  if (sdk_level == kCurrentApiLevel) {
    ADD_FAILURE() << "Failed to determine SDK level";
    return 0;
  }
  return sdk_level;
}

uint32_t GetProductFirstApiLevel() {
  uint32_t product_api_level =
      ReadApiLevelProps({"ro.product.first_api_level", "ro.build.version.sdk"});
  if (product_api_level == kCurrentApiLevel) {
    ADD_FAILURE() << "Failed to determine product first API level";
    return 0;
  }
  return product_api_level;
}

uint32_t GetVendorApiLevel() {
  // "ro.vendor.api_level" is added in Android T.
  uint32_t vendor_api_level = ReadApiLevelProps({"ro.vendor.api_level"});
  if (vendor_api_level != kCurrentApiLevel) {
    return vendor_api_level;
  }
  // For pre-T devices, determine the board API level by ourselves.
  uint32_t product_api_level = GetProductFirstApiLevel();
  uint32_t board_api_level =
      ReadApiLevelProps({"ro.board.api_level", "ro.board.first_api_level"});
  uint32_t api_level = std::min(board_api_level, product_api_level);
  if (api_level == kCurrentApiLevel) {
    ADD_FAILURE() << "Failed to determine vendor API level";
    return 0;
  }
  return api_level;
}

std::optional<uint32_t> GetBoardApiLevel() {
  uint32_t board_api_level =
      ReadApiLevelProps({"ro.board.api_level", "ro.board.first_api_level"});
  if (board_api_level == kCurrentApiLevel) {
    return std::nullopt;
  }
  return board_api_level;
}

bool IsReleasedAndroidVersion() {
  return android::base::GetProperty("ro.build.version.codename", "") == "REL";
}
