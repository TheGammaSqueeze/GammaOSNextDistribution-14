/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <dlfcn.h>
#include <stdint.h>
#include <string.h>

#include <string>
#include <string_view>
#include <vector>

#include <android-base/endian.h>
#include <android-base/logging.h>
#include <android-base/strings.h>
#include <misc_writer/misc_writer.h>
#include <recovery_ui/device.h>
#include <recovery_ui/wear_ui.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

namespace {

// Provision Silent OTA(SOTA) flag while reason is "enable-sota"
bool ProvisionSilentOtaFlag(const std::string& reason) {
    if (android::base::StartsWith(reason, MiscWriter::kSotaFlag)) {
        MiscWriter misc_writer(MiscWriterActions::kSetSotaFlag);
        if (!misc_writer.PerformAction()) {
            LOG(ERROR) << "Failed to set the silent ota flag";
            return false;
        }
        LOG(INFO) << "Silent ota flag set successful";
    }
    return true;
}

}  // namespace

class PixelWatchDevice : public ::Device {
  public:
    explicit PixelWatchDevice(::WearRecoveryUI* const ui) : ::Device(ui) {}

    /** Hook to wipe user data not stored in /data */
    bool PostWipeData() override {
        // Try to do everything but report a failure if anything wasn't successful
        bool totalSuccess = false;

        // Additional behavior along with wiping data
        auto reason = GetReason();
        CHECK(reason.has_value());
        if (!ProvisionSilentOtaFlag(reason.value())) {
            totalSuccess = false;
        }

        return totalSuccess;
    }
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

Device *make_device() {
    return new ::android::hardware::google::pixel::PixelWatchDevice(new ::WearRecoveryUI);
}
