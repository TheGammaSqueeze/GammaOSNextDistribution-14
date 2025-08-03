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

#include <string>
#include <vector>

namespace android::vintf::details {

struct FQName {
    __attribute__((warn_unused_result)) static bool parse(const std::string& s, FQName* into);

    explicit FQName();

    FQName(const std::string& package, const std::string& version, const std::string& name = "");

    // Returns false if string isn't a valid FQName object.
    __attribute__((warn_unused_result)) bool setTo(const std::string& s);
    __attribute__((warn_unused_result)) bool setTo(const std::string& package, size_t majorVer,
                                                   size_t minorVer, const std::string& name = "");

    const std::string& package() const;
    // Return version in the form "1.0" if it is present, otherwise empty string.
    std::string version() const;
    // Return true only if version is present.
    bool hasVersion() const;
    // Return pair of (major, minor) version. Defaults to 0, 0.
    std::pair<size_t, size_t> getVersion() const;

    // The next method return the name part of the FQName, that is, the
    // part after the version field.  For example:
    //
    // package android.hardware.tests.foo@1.0;
    // interface IFoo {
    //    struct bar {
    //        struct baz {
    //            ...
    //        };
    //    };
    // };
    //
    // package android.hardware.tests.bar@1.0;
    // import android.hardware.tests.foo@1.0;
    // interface {
    //    struct boo {
    //        IFoo.bar.baz base;
    //    };
    // }
    //
    // The FQName for base is android.hardware.tests.foo@1.0::IFoo.bar.baz; so
    // FQName::name() will return "IFoo.bar.baz".
    const std::string& name() const;

    // Interface names start with 'I'
    bool isInterfaceName() const;

    std::string string() const;

    bool operator<(const FQName& other) const;
    bool operator==(const FQName& other) const;
    bool operator!=(const FQName& other) const;

    // Must be called on an interface
    // android.hardware.foo@1.0::IBar
    // -> IBar
    const std::string& getInterfaceName() const;

    // android.hardware.foo@1.0::Abc.Type:VALUE
    // -> android.hardware.foo@1.0
    FQName getPackageAndVersion() const;

    // If this is android.hardware@1.0::IFoo
    // package = "and" -> false
    // package = "android" -> true
    // package = "android.hardware@1.0" -> false
    bool inPackage(const std::string& package) const;

    // return major and minor version if they exist, else abort program.
    // Existence of version can be checked via hasVersion().
    size_t getPackageMajorVersion() const;
    size_t getPackageMinorVersion() const;

   private:
    bool mIsIdentifier;
    std::string mPackage;
    // mMajor == 0 means empty.
    size_t mMajor = 0;
    size_t mMinor = 0;
    std::string mName;

    void clear();

    __attribute__((warn_unused_result)) bool setVersion(const std::string& v);
    __attribute__((warn_unused_result)) bool parseVersion(const std::string& majorStr,
                                                          const std::string& minorStr);
    __attribute__((warn_unused_result)) static bool parseVersion(const std::string& majorStr,
                                                                 const std::string& minorStr,
                                                                 size_t* majorVer,
                                                                 size_t* minorVer);
    __attribute__((warn_unused_result)) static bool parseVersion(const std::string& v,
                                                                 size_t* majorVer,
                                                                 size_t* minorVer);
    static void clearVersion(size_t* majorVer, size_t* minorVer);

    void clearVersion();

    bool isIdentifier() const;
    // Return version in the form "@1.0" if it is present, otherwise empty string.
    std::string atVersion() const;

    std::vector<std::string> getPackageComponents() const;
};

}  // namespace android::vintf::details
