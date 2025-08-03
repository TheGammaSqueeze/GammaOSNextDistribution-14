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

#ifndef ANDROID_VINTF_HAL_GROUP_H
#define ANDROID_VINTF_HAL_GROUP_H

#include <map>
#include <set>

#include "HalFormat.h"
#include "MapValueIterator.h"
#include "Version.h"

namespace android {
namespace vintf {

// A HalGroup is a wrapped multimap from name to Hal.
// Hal.getName() must return a string indicating the name.
template <typename Hal>
struct HalGroup {
    using InstanceType = typename Hal::InstanceType;

   public:
    virtual ~HalGroup() {}

   protected:
    // Get all hals with the given name (e.g "android.hardware.camera").
    // There could be multiple hals that matches the same given name.
    std::vector<const Hal*> getHals(const std::string& name) const {
        std::vector<const Hal*> ret;
        auto range = mHals.equal_range(name);
        for (auto it = range.first; it != range.second; ++it) {
            ret.push_back(&it->second);
        }
        return ret;
    }

    // Get all hals with the given name (e.g "android.hardware.camera").
    // There could be multiple hals that matches the same given name.
    // Non-const version of the above getHals() method.
    std::vector<Hal*> getHals(const std::string& name) {
        std::vector<Hal*> ret;
        auto range = mHals.equal_range(name);
        for (auto it = range.first; it != range.second; ++it) {
            ret.push_back(&it->second);
        }
        return ret;
    }

   public:
    // Apply func to all instances.
    bool forEachInstance(const std::function<bool(const InstanceType&)>& func) const {
        for (const auto& hal : getHals()) {
            bool cont = hal.forEachInstance(func);
            if (!cont) return false;
        }
        return true;
    }

    bool forEachHidlInstance(const std::function<bool(const InstanceType&)>& func) const {
        return forEachInstance(HalFormat::HIDL, func);
    }

   private:
    bool forEachInstance(HalFormat format,
                         const std::function<bool(const InstanceType&)>& func) const {
        return forEachInstance([&](const InstanceType& e) {
            if (e.format() == format) {
                return func(e);
            }
            return true;  // continue
        });
    }

    bool forEachInstanceOfPackage(HalFormat format, const std::string& package,
                                  const std::function<bool(const InstanceType&)>& func) const {
        for (const auto* hal : getHals(package)) {
            if (hal->format != format) {
                continue;
            }
            if (!hal->forEachInstance(func)) {
                return false;
            }
        }
        return true;
    }
    bool forEachHidlInstanceOfPackage(const std::string& package,
                                      const std::function<bool(const InstanceType&)>& func) const {
        return forEachInstanceOfPackage(HalFormat::HIDL, package, func);
    }

   protected:
    // Apply func to all instances of package@expectVersion::*/*.
    // For example, if a.h.foo@1.1::IFoo/default is in "this" and getHidlFqInstances
    // is called with a.h.foo@1.0, then a.h.foo@1.1::IFoo/default is returned.
    // If format is AIDL, expectVersion should be the fake AIDL version.
    virtual bool forEachInstanceOfVersion(
        HalFormat format, const std::string& package, const Version& expectVersion,
        const std::function<bool(const InstanceType&)>& func) const = 0;

    // Apply func to instances of package@expectVersion::interface/*.
    // For example, if a.h.foo@1.1::IFoo/default is in "this" and getHidlFqInstances
    // is called with a.h.foo@1.0::IFoo, then a.h.foo@1.1::IFoo/default is returned.
    // If format is AIDL, expectVersion should be the fake AIDL version.
    bool forEachInstanceOfInterface(HalFormat format, const std::string& package,
                                    const Version& expectVersion, const std::string& interface,
                                    const std::function<bool(const InstanceType&)>& func) const {
        return forEachInstanceOfVersion(format, package, expectVersion,
                                        [&func, &interface](const InstanceType& e) {
                                            if (e.interface() == interface) {
                                                return func(e);
                                            }
                                            return true;
                                        });
    }

   public:
    // Apply func to all instances of package@expectVersion::*/*.
    // For example, if a.h.foo@1.1::IFoo/default is in "this" and getHidlFqInstances
    // is called with a.h.foo@1.0, then a.h.foo@1.1::IFoo/default is returned.
    virtual bool forEachHidlInstanceOfVersion(
        const std::string& package, const Version& expectVersion,
        const std::function<bool(const InstanceType&)>& func) const {
        return forEachInstanceOfVersion(HalFormat::HIDL, package, expectVersion, func);
    }

    // Apply func to instances of package@expectVersion::interface/*.
    // For example, if a.h.foo@1.1::IFoo/default is in "this" and getHidlFqInstances
    // is called with a.h.foo@1.0::IFoo, then a.h.foo@1.1::IFoo/default is returned.
    bool forEachHidlInstanceOfInterface(
        const std::string& package, const Version& expectVersion, const std::string& interface,
        const std::function<bool(const InstanceType&)>& func) const {
        return forEachInstanceOfInterface(HalFormat::HIDL, package, expectVersion, interface, func);
    }

    // Alternative to forEachHidlInstanceOfInterface if you need a vector instead.
    // If interface is empty, returns all instances of package@version;
    // else return all instances of package@version::interface.
    std::vector<InstanceType> getHidlFqInstances(const std::string& package,
                                                 const Version& expectVersion,
                                                 const std::string& interface = "") const {
        std::vector<InstanceType> v;
        auto mapToVector = [&v](const auto& e) {
            v.push_back(e);
            return true;
        };
        if (interface.empty()) {
            (void)forEachHidlInstanceOfVersion(package, expectVersion, mapToVector);
        } else {
            (void)forEachHidlInstanceOfInterface(package, expectVersion, interface, mapToVector);
        }
        return v;
    }

   protected:
    // sorted map from component name to the component.
    // The component name looks like: android.hardware.foo
    std::multimap<std::string, Hal> mHals;

    // Return an iterable to all Hal objects. Call it as follows:
    // for (const auto& e : vm.getHals()) { }
    ConstMultiMapValueIterable<std::string, Hal> getHals() const { return iterateValues(mHals); }

    // Return an iterable to all Hal objects. Call it as follows:
    // for (const auto& e : vm.getHals()) { }
    MultiMapValueIterable<std::string, Hal> getHals() { return iterateValues(mHals); }

    // Get any HAL component based on the component name. Return any one
    // if multiple. Return nullptr if the component does not exist. This is only
    // for creating objects programatically.
    // The component name looks like:
    // android.hardware.foo
    Hal* getAnyHal(const std::string& name) {
        auto it = mHals.find(name);
        if (it == mHals.end()) {
            return nullptr;
        }
        return &(it->second);
    }

    // Helper for "add(Hal)". Returns pointer to inserted object. Never null.
    Hal* addInternal(Hal&& hal) {
        std::string name = hal.getName();
        auto it = mHals.emplace(std::move(name), std::move(hal));  // always succeeds
        return &it->second;
    }

    // Remove if shouldRemove(hal).
    void removeHalsIf(const std::function<bool(const Hal&)>& shouldRemove) {
        for (auto it = mHals.begin(); it != mHals.end();) {
            const Hal& value = it->second;
            if (shouldRemove(value)) {
                it = mHals.erase(it);
            } else {
                ++it;
            }
        }
    }

   private:
    friend class AnalyzeMatrix;
    friend class VintfObject;
};

}  // namespace vintf
}  // namespace android

#endif  // ANDROID_VINTF_HAL_GROUP_H
