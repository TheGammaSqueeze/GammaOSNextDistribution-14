/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "ManifestHal.h"

#include <unordered_set>

#include "MapValueIterator.h"
#include "constants-private.h"
#include "parse_string.h"
#include "utils.h"

namespace android {
namespace vintf {

bool ManifestHal::isValid(std::string* error) const {
    if (error) {
        error->clear();
    }

    bool success = true;
    std::map<size_t, Version> existing;
    for (const auto &v : versions) {
        auto&& [it, inserted] = existing.emplace(v.majorVer, v);
        if (inserted) {
            continue;
        }
        success = false;
        if (error) {
            *error += "Duplicated major version: " + to_string(v) + " vs. " +
                      to_string(it->second) + ".\n";
        }
    }

    std::string transportArchError;
    if (!transportArch.isValid(&transportArchError)) {
        success = false;
        if (error) *error += transportArchError + "\n";
    }
    return success;
}

bool ManifestHal::operator==(const ManifestHal &other) const {
    // ignore fileName().
    if (format != other.format)
        return false;
    if (name != other.name)
        return false;
    if (versions != other.versions)
        return false;
    if (!(transportArch == other.transportArch)) return false;
    if (isOverride() != other.isOverride()) return false;
    if (updatableViaApex() != other.updatableViaApex()) return false;
    if (mManifestInstances != other.mManifestInstances) return false;
    return true;
}

bool ManifestHal::forEachInstance(const std::function<bool(const ManifestInstance&)>& func) const {
    for (const auto& manifestInstance : mManifestInstances) {
        // For AIDL HALs, <version> tag is mangled with <fqname>. Note that if there's no
        // <version> tag, libvintf will create one by default, so each <fqname> is executed
        // at least once.
        if (format == HalFormat::AIDL) {
            for (const auto& v : versions) {
                if (!func(manifestInstance.withVersion(v))) {
                    return false;
                }
            }
        } else {
            if (!func(manifestInstance)) {
                return false;
            }
        }
    }

    return true;
}

bool ManifestHal::isDisabledHal() const {
    if (!isOverride()) return false;
    bool hasInstance = false;
    forEachInstance([&hasInstance](const auto&) {
        hasInstance = true;
        return false;  // has at least one instance, stop here.
    });
    return !hasInstance;
}

void ManifestHal::appendAllVersions(std::set<Version>* ret) const {
    ret->insert(versions.begin(), versions.end());
    forEachInstance([&](const auto& e) {
        ret->insert(e.version());
        return true;
    });
}

bool ManifestHal::verifyInstance(const FqInstance& fqInstance, std::string* error) const {
    if (fqInstance.hasPackage() && fqInstance.getPackage() != this->getName()) {
        if (error) {
            *error = "Should not add \"" + fqInstance.string() + "\" to a HAL with name " +
                     this->getName();
        }
        return false;
    }
    if (!fqInstance.hasVersion()) {
        if (error) *error = "Should specify version: \"" + fqInstance.string() + "\"";
        return false;
    }
    if (!fqInstance.hasInterface() && format != HalFormat::NATIVE) {
        if (error) *error = "Should specify interface: \"" + fqInstance.string() + "\"";
        return false;
    }
    if (!fqInstance.hasInstance()) {
        if (error) *error = "Should specify instance: \"" + fqInstance.string() + "\"";
        return false;
    }
    return true;
}

bool ManifestHal::insertInstances(const std::set<FqInstance>& fqInstances,
                                  bool allowDupMajorVersion, std::string* error) {
    for (const FqInstance& e : fqInstances) {
        if (!insertInstance(e, allowDupMajorVersion, error)) {
            return false;
        }
    }
    return true;
}

bool ManifestHal::insertInstance(const FqInstance& e, bool allowDupMajorVersion,
                                 std::string* error) {
    if (!verifyInstance(e, error)) {
        return false;
    }

    size_t minorVer = e.getMinorVersion();
    for (auto it = mManifestInstances.begin(); it != mManifestInstances.end();) {
        if (it->version().majorVer == e.getMajorVersion() && it->interface() == e.getInterface() &&
            it->instance() == e.getInstance()) {
            if (!allowDupMajorVersion) {
                if (format == HalFormat::AIDL) {
                    *error = "Duplicated HAL version: " + to_string(it->version().minorVer) +
                             " vs " + to_string(e.getMinorVersion());
                } else {
                    *error = "Duplicated major version: " + to_string(it->version()) + " vs " +
                             to_string(Version(e.getMajorVersion(), e.getMinorVersion()));
                }
                return false;
            }
            minorVer = std::max(minorVer, it->version().minorVer);
            it = mManifestInstances.erase(it);
        } else {
            ++it;
        }
    }

    FqInstance toAdd;
    if (!toAdd.setTo(this->getName(), e.getMajorVersion(), minorVer, e.getInterface(),
                     e.getInstance())) {
        if (error) {
            *error = "Cannot create FqInstance with package='" + this->getName() + "', version='" +
                     to_string(Version(e.getMajorVersion(), minorVer)) + "', interface='" +
                     e.getInterface() + "', instance='" + e.getInstance() + "'";
        }
        return false;
    }

    mManifestInstances.emplace(std::move(toAdd), this->transportArch, this->format,
                               this->updatableViaApex());
    return true;
}

} // namespace vintf
} // namespace android
