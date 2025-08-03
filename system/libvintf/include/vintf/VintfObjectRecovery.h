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

#pragma once

#if defined(__ANDROID__) && !defined(__ANDROID_RECOVERY__)
#error VintfObjectRecovery is only supported in recovery and host.
#endif

#include <vintf/VintfObject.h>

namespace android::vintf {

/**
 * A special variant of VintfObject for the recovery ramdisk.
 *
 * In recovery ramdisk, there is no Treble split. All VINTF data is stored in /system/etc/vintf.
 *
 * All getDevice* / getFramework* functions return nullptr. Instead, getRecovery* should be
 * used instead.
 */
class VintfObjectRecovery : public VintfObject {
   public:
    /*
     * Get global instance. Results are cached.
     */
    static std::shared_ptr<VintfObjectRecovery> GetInstance();

    /*
     * Return the API that access the HAL manifests built from component pieces on the
     * recovery partition.
     *
     * Returned manifest has SchemaType::DEVICE.
     *
     * No SKU manifest support.
     */
    std::shared_ptr<const HalManifest> getRecoveryHalManifest();

    // Not supported. Call getRecoveryHalManifest instead.
    std::shared_ptr<const HalManifest> getDeviceHalManifest() override { return nullptr; }
    std::shared_ptr<const HalManifest> getFrameworkHalManifest() override { return nullptr; }

    // Not supported. No compatibility check in recovery because there is no Treble split.
    std::shared_ptr<const CompatibilityMatrix> getDeviceCompatibilityMatrix() override {
        return nullptr;
    }
    std::shared_ptr<const CompatibilityMatrix> getFrameworkCompatibilityMatrix() override {
        return nullptr;
    }

    /** Builder of VintfObjectRecovery. See VintfObjectBuilder for details. */
    class Builder : public details::VintfObjectBuilder {
       public:
        Builder();
    };

   private:
    VintfObjectRecovery() = default;
    status_t fetchRecoveryHalManifest(HalManifest* out, std::string* error);
    details::LockedSharedPtr<HalManifest> mRecoveryManifest;
};

}  // namespace android::vintf
