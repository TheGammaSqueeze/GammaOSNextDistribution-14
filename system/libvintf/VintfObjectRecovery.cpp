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

#include <vintf/VintfObjectRecovery.h>

#include "VintfObjectUtils.h"
#include "constants-private.h"

using std::placeholders::_1;
using std::placeholders::_2;

namespace android::vintf {

using details::Get;
using details::kSystemManifest;
using details::kSystemManifestFragmentDir;

std::shared_ptr<VintfObjectRecovery> VintfObjectRecovery::GetInstance() {
    static details::LockedSharedPtr<VintfObjectRecovery> sInstance{};
    std::unique_lock<std::mutex> lock(sInstance.mutex);
    if (sInstance.object == nullptr) {
        std::unique_ptr<VintfObjectRecovery> uptr =
            VintfObjectRecovery::Builder().build<VintfObjectRecovery>();
        sInstance.object = std::shared_ptr<VintfObjectRecovery>(uptr.release());
    }
    return sInstance.object;
}

std::shared_ptr<const HalManifest> VintfObjectRecovery::getRecoveryHalManifest() {
    return Get(__func__, &mRecoveryManifest,
               std::bind(&VintfObjectRecovery::fetchRecoveryHalManifest, this, _1, _2));
}

// All manifests are installed under /system/etc/vintf.
// There may be mixed framework and device manifests under that directory. Treat them all
// as device manifest fragments.
// Priority:
// 1. /system/etc/vintf/manifest.xml
//    + /system/etc/vintf/manifest/*.xml if they exist
status_t VintfObjectRecovery::fetchRecoveryHalManifest(HalManifest* out, std::string* error) {
    HalManifest manifest;
    status_t systemEtcStatus = fetchOneHalManifest(kSystemManifest, &manifest, error);
    if (systemEtcStatus != OK && systemEtcStatus != NAME_NOT_FOUND) {
        return systemEtcStatus;
    }
    // Merge |manifest| to |out| only if the main manifest is found.
    if (systemEtcStatus == OK) {
        *out = std::move(manifest);
    }
    out->setType(SchemaType::DEVICE);
    status_t fragmentStatus =
        addDirectoryManifests(kSystemManifestFragmentDir, out, true /* forceSchemaType */, error);
    if (fragmentStatus != OK) {
        return fragmentStatus;
    }
    return OK;
}

VintfObjectRecovery::Builder::Builder()
    : VintfObjectBuilder(std::unique_ptr<VintfObject>(new VintfObjectRecovery())) {}

}  // namespace android::vintf
