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

#define LOG_TAG "GammaOSOta"

#include "OtaManifest.h"

#include <fstream>
#include <sstream>
#include <algorithm>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/strings.h>

// Minimal JSON parser — no external dependency
// Handles the flat manifest.json structure we define

namespace android {

static std::string extractString(const std::string& json, const std::string& key) {
    std::string search = "\"" + key + "\"";
    auto pos = json.find(search);
    if (pos == std::string::npos) return "";
    pos = json.find("\"", pos + search.length() + 1);
    if (pos == std::string::npos) return "";
    auto end = json.find("\"", pos + 1);
    if (end == std::string::npos) return "";
    return json.substr(pos + 1, end - pos - 1);
}

static int64_t extractInt(const std::string& json, const std::string& key) {
    std::string search = "\"" + key + "\"";
    auto pos = json.find(search);
    if (pos == std::string::npos) return 0;
    pos = json.find(":", pos);
    if (pos == std::string::npos) return 0;
    pos++;
    while (pos < json.size() && (json[pos] == ' ' || json[pos] == '\t')) pos++;
    return strtoll(json.c_str() + pos, nullptr, 10);
}

static std::vector<std::string> extractStringArray(const std::string& json, const std::string& key) {
    std::vector<std::string> result;
    std::string search = "\"" + key + "\"";
    auto pos = json.find(search);
    if (pos == std::string::npos) return result;
    auto arrStart = json.find("[", pos);
    auto arrEnd = json.find("]", arrStart);
    if (arrStart == std::string::npos || arrEnd == std::string::npos) return result;
    std::string arr = json.substr(arrStart + 1, arrEnd - arrStart - 1);
    size_t p = 0;
    while ((p = arr.find("\"", p)) != std::string::npos) {
        auto e = arr.find("\"", p + 1);
        if (e == std::string::npos) break;
        result.push_back(arr.substr(p + 1, e - p - 1));
        p = e + 1;
    }
    return result;
}

static std::vector<std::string> extractPartitionBlocks(const std::string& json) {
    std::vector<std::string> blocks;
    std::string key = "\"partitions\"";
    auto pos = json.find(key);
    if (pos == std::string::npos) return blocks;
    auto arrStart = json.find("[", pos);
    if (arrStart == std::string::npos) return blocks;

    int depth = 0;
    size_t blockStart = std::string::npos;
    for (size_t i = arrStart + 1; i < json.size(); i++) {
        if (json[i] == '{') {
            if (depth == 0) blockStart = i;
            depth++;
        } else if (json[i] == '}') {
            depth--;
            if (depth == 0 && blockStart != std::string::npos) {
                blocks.push_back(json.substr(blockStart, i - blockStart + 1));
                blockStart = std::string::npos;
            }
        } else if (json[i] == ']' && depth == 0) {
            break;
        }
    }
    return blocks;
}

bool OtaManifest::parse(const std::string& jsonPath) {
    std::string json;
    if (!android::base::ReadFileToString(jsonPath, &json)) {
        LOG(ERROR) << "Failed to read manifest: " << jsonPath;
        return false;
    }

    version = extractString(json, "version");
    versionCode = (int)extractInt(json, "version_code");
    datetime = extractInt(json, "datetime");
    minBattery = (int)extractInt(json, "min_battery");
    devices = extractStringArray(json, "device");

    if (version.empty()) {
        LOG(ERROR) << "Manifest missing version";
        return false;
    }

    auto partBlocks = extractPartitionBlocks(json);
    for (const auto& block : partBlocks) {
        OtaPartition p;
        p.name = extractString(block, "name");
        p.type = extractString(block, "type");
        p.size = (uint64_t)extractInt(block, "size");
        p.file = extractString(block, "file");
        p.sha256 = extractString(block, "sha256");
        p.sha256_uncompressed = extractString(block, "sha256_uncompressed");

        if (p.name.empty() || p.type.empty() || p.file.empty()) {
            LOG(WARNING) << "Skipping partition with missing fields";
            continue;
        }
        partitions.push_back(std::move(p));
    }

    LOG(INFO) << "Parsed manifest: version=" << version
              << " partitions=" << partitions.size();
    return true;
}

bool OtaManifest::isDeviceCompatible(const std::string& device) const {
    if (devices.empty()) return true; // no restriction
    return std::find(devices.begin(), devices.end(), device) != devices.end();
}

const OtaPartition* OtaManifest::findPartition(const std::string& name) const {
    for (const auto& p : partitions) {
        if (p.name == name) return &p;
    }
    return nullptr;
}

std::vector<const OtaPartition*> OtaManifest::physicalPartitions() const {
    std::vector<const OtaPartition*> result;
    for (const auto& p : partitions) {
        if (p.type == "physical") result.push_back(&p);
    }
    return result;
}

std::vector<const OtaPartition*> OtaManifest::logicalPartitions() const {
    std::vector<const OtaPartition*> result;
    for (const auto& p : partitions) {
        if (p.type == "logical") result.push_back(&p);
    }
    return result;
}

} // namespace android
