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

#pragma once

#include <optional>
#include <string>
#include <utility>

#include <vintf/FQName.h>

namespace android::vintf {

// A wrapper around FQName to include instance name as well.
// FqInstance::setTo also recognizes all FQName formats
// Typical usage:
// FqInstance fqInstance;
// if (!fqInstance.setTo("...")) {
//    // error handling
// }
// LOG(WARNING) << fqInstance.string();
class FqInstance {
   public:
    const std::string& getPackage() const;
    size_t getMajorVersion() const;
    size_t getMinorVersion() const;
    std::pair<size_t, size_t> getVersion() const;
    std::string getInterface() const;
    const std::string& getInstance() const;
    std::string getFqNameString() const;

    bool hasPackage() const;
    bool hasVersion() const;
    bool hasInterface() const;
    bool hasInstance() const;

    // If this is android.hardware@1.0::IFoo
    // package = "and" -> false
    // package = "android" -> true
    // package = "android.hardware@1.0" -> false
    bool inPackage(const std::string& package) const;

    // Return true if valid:
    // android.hardware.foo@1.0/instance
    // android.hardware.foo@1.0::IFoo/instance
    // @1.0::IFoo/instance
    // @1.0/instance
    // IFoo/instance
    __attribute__((warn_unused_result)) bool setTo(const std::string& s);

    // Convenience method for the following formats:
    // android.hardware.foo@1.0::IFoo/default
    __attribute__((warn_unused_result)) bool setTo(const std::string& package, size_t majorVer,
                                                   size_t minorVer, const std::string& interface,
                                                   const std::string& instance);
    // Convenience method for the following formats:
    // @1.0::IFoo/default
    __attribute__((warn_unused_result)) bool setTo(size_t majorVer, size_t minorVer,
                                                   const std::string& interface,
                                                   const std::string& instance);

    // Convenience method for the following formats:
    // IFoo/default
    __attribute__((warn_unused_result)) bool setTo(const std::string& interface,
                                                   const std::string& instance);

    // Same as creating an FqInstance then call setTo. See setTo for all valid signatures.
    // If setTo returns false, this function returns nullopt.
    template <typename... Args>
    static std::optional<FqInstance> from(Args&&... args) {
        FqInstance fqInstance;
        if (fqInstance.setTo(std::forward<Args>(args)...)) return fqInstance;
        return std::nullopt;
    }

    // undefined behavior if:
    // - Default constructor is called without setTo();
    // - setTo is called but returned false.
    // Should only be called after setTo() returns true.
    std::string string() const;
    bool operator<(const FqInstance& other) const;
    bool operator==(const FqInstance& other) const;
    bool operator!=(const FqInstance& other) const;

   private:
    details::FQName mFqName;
    std::string mInstance;

    // helper to setTo() to determine that the FqInstance is actually valid.
    bool isValid() const;
};

}  // namespace android::vintf
