/*
 * Copyright (C) 2026 GammaOS
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

// Self-contained GammaOS OTA online update-check and download for gammaos-nano.
//
// Ports the check/compare logic from the in-tree LineageOS-derived Updater app
// (packages/apps/Updater: misc/Utils.java getServerURL()/isCompatible()/canInstall()
// and misc/Constants.java). The OTA server speaks the LineageOS Updater JSON API:
//
//   GET https://ota.gammaos.sh/api/v1/{ro.gammaos.device}/{ro.gammaos.variant}
//   -> {"response":[{"datetime":<utc seconds>,"filename":"...","id":"...",
//                    "romtype":"unofficial","size":<bytes>,"url":"...","version":"1.3.2"}]}
//
// Dependency-light by design: fetches with a fork+exec of the on-device curl and
// hand-rolls a tiny JSON scan (no libcurl / no JSON library), mirroring how nano
// already shells out to curl elsewhere.

#pragma once

#include <string>
#include <cstdint>
#include <functional>

namespace nano {

struct OtaUpdateInfo {
    bool        available = false;      // a build newer than current exists for this device/variant
    std::string version;                // e.g. "1.3.2"
    std::string filename;
    std::string url;                    // download URL
    uint64_t    size = 0;               // bytes
    std::string currentVersion;         // human-readable current OS version
    std::string error;                  // non-empty on query failure
};

// Query the OTA server for the latest build for this device+variant.
// Returns true if the query SUCCEEDED (info.available says whether an update exists).
// Returns false if the query failed (info.error set).
bool otaCheckForUpdate(OtaUpdateInfo& info);

// Download info.url into /data/gammaos_ota/ and extract into /data/gammaos_ota/package/
// (so that /data/gammaos_ota/package/manifest.json exists). Calls progress(percent 0..100,
// phaseText) periodically. Returns true on success (package staged), false on failure.
bool otaDownloadAndStage(const OtaUpdateInfo& info,
                         const std::function<void(int, const char*)>& progress);

}  // namespace nano
