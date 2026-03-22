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

#ifndef GAMMAOS_OTA_MANIFEST_H
#define GAMMAOS_OTA_MANIFEST_H

#include <string>
#include <vector>

namespace android {

struct OtaPartition {
    std::string name;       // e.g. "system", "vendor", "boot"
    std::string type;       // "logical" or "physical"
    uint64_t size;          // target size in bytes (logical only)
    std::string file;       // filename inside zip, e.g. "system.img.xz"
    std::string sha256;     // checksum of compressed file
    std::string sha256_uncompressed; // checksum of raw image (for post-flash verify)
};

struct OtaManifest {
    std::string version;
    int versionCode;
    int64_t datetime;
    std::vector<std::string> devices;
    int minBattery;
    std::vector<OtaPartition> partitions;

    bool parse(const std::string& jsonPath);
    bool isDeviceCompatible(const std::string& device) const;
    const OtaPartition* findPartition(const std::string& name) const;

    // Partition helpers
    std::vector<const OtaPartition*> physicalPartitions() const;
    std::vector<const OtaPartition*> logicalPartitions() const;
};

} // namespace android

#endif // GAMMAOS_OTA_MANIFEST_H
