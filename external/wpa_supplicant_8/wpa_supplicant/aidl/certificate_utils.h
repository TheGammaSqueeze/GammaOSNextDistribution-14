/*
 * WPA Supplicant - Certificate utils
 * Copyright (c) 2022, Google Inc. All rights reserved.
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#pragma once

#include <aidl/android/hardware/wifi/supplicant/INonStandardCertCallback.h>
#include <aidl/android/system/keystore2/IKeystoreService.h>
#include <aidl/android/system/keystore2/ResponseCode.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <openssl/base.h>
#include <openssl/bio.h>
#include <openssl/pem.h>
#include <openssl/x509.h>
#include <vector>

extern "C"
{
#include "utils/common.h"
}

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace certificate_utils {
	std::optional<std::vector<uint8_t>> getCertificate(const std::string& alias,
		const std::shared_ptr<INonStandardCertCallback> &non_standard_callback);
	std::optional<std::vector<std::string>> listAliases(const std::string& prefix,
		const std::shared_ptr<INonStandardCertCallback> &non_standard_callback);
}  // namespace certificate_utils
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
