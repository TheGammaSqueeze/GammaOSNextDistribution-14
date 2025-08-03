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

#include <vintf/FQName.h>

#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>
#include <iostream>
#include <sstream>

namespace android::vintf::details {

FQName::FQName() : mIsIdentifier(false) {}

bool FQName::parse(const std::string& s, FQName* into) {
    return into->setTo(s);
}

FQName::FQName(const std::string& package, const std::string& version, const std::string& name) {
    size_t majorVer, minorVer;
    CHECK(parseVersion(version, &majorVer, &minorVer));
    CHECK(setTo(package, majorVer, minorVer, name)) << string();
}

bool FQName::setTo(const std::string& package, size_t majorVer, size_t minorVer,
                   const std::string& name) {
    mPackage = package;
    mMajor = majorVer;
    mMinor = minorVer;
    mName = name;

    FQName other;
    if (!parse(string(), &other)) return false;
    if ((*this) != other) return false;
    mIsIdentifier = other.isIdentifier();
    return true;
}

bool FQName::isIdentifier() const {
    return mIsIdentifier;
}

bool FQName::isInterfaceName() const {
    return !mName.empty() && mName[0] == 'I' && mName.find('.') == std::string::npos;
}

static inline bool isIdentStart(char a) {
    return ('a' <= a && a <= 'z') || ('A' <= a && a <= 'Z') || a == '_';
}
static inline bool isLeadingDigit(char a) {
    return '1' <= a && a <= '9';
}
static inline bool isDigit(char a) {
    return '0' <= a && a <= '9';
}
static inline bool isIdentBody(char a) {
    return isIdentStart(a) || isDigit(a);
}

// returns pointer to end of [a-zA-Z_][a-zA-Z0-9_]*
static const char* eatIdent(const char* l, const char* end) {
    if (!(l < end && isIdentStart(*l++))) return nullptr;
    while (l < end && isIdentBody(*l)) l++;
    return l;
}

// returns pointer to end of <ident>(\.<ident>)*
static const char* eatPackage(const char* l, const char* end) {
    if ((l = eatIdent(l, end)) == nullptr) return nullptr;

    while (l < end && *l == '.') {
        l++;
        if ((l = eatIdent(l, end)) == nullptr) return nullptr;
    }
    return l;
}

// returns pointer to end of [1-9][0-9]*|0
static const char* eatNumber(const char* l, const char* end) {
    if (!(l < end)) return nullptr;
    if (*l == '0') return l + 1;
    if (!isLeadingDigit(*l++)) return nullptr;
    while (l < end && isDigit(*l)) l++;
    return l;
}

bool FQName::setTo(const std::string& s) {
    clear();

    if (s.empty()) return false;

    const char* l = s.c_str();
    const char* end = l + s.size();
    // android.hardware.foo@10.12::IFoo.Type
    // S                   ES ES E S        E
    //
    // S - start pointer
    // E - end pointer

    struct StartEnd {
        const char* start = nullptr;
        const char* end = nullptr;

        std::string string() {
            if (start == nullptr) return std::string();
            return std::string(start, end - start);
        }
    };
    StartEnd package, major, minor, name;

    if (l < end && isIdentStart(*l)) {
        package.start = l;
        if ((package.end = l = eatPackage(l, end)) == nullptr) return false;
    }
    if (l < end && *l == '@') {
        l++;

        major.start = l;
        if ((major.end = l = eatNumber(l, end)) == nullptr) return false;

        if (!(l < end && *l++ == '.')) return false;

        minor.start = l;
        if ((minor.end = l = eatNumber(l, end)) == nullptr) return false;
    }
    if (l < end && *l == ':') {
        l++;
        if (l < end && *l == ':') {
            l++;
            name.start = l;
            if ((name.end = l = eatPackage(l, end)) == nullptr) return false;
        } else {
            return false;
        }
    }

    if (l < end) return false;

    CHECK((major.start == nullptr) == (minor.start == nullptr));

    // if we only parse a package, consider this to be a name
    if (name.start == nullptr && major.start == nullptr) {
        name.start = package.start;
        name.end = package.end;
        package.start = package.end = nullptr;
    }

    // failures after this goto fail to clear
    mName = name.string();
    mPackage = package.string();

    if (major.start != nullptr) {
        if (!parseVersion(major.string(), minor.string(), &mMajor, &mMinor)) goto fail;
    } else if (mPackage.empty() && name.end == eatIdent(name.start, name.end)) {
        // major.start == nullptr
        mIsIdentifier = true;
    }

    if (!mPackage.empty() && version().empty()) goto fail;

    return true;
fail:
    clear();
    return false;
}

const std::string& FQName::package() const {
    return mPackage;
}

std::string FQName::version() const {
    if (!hasVersion()) {
        return "";
    }
    return std::to_string(mMajor) + "." + std::to_string(mMinor);
}

std::string FQName::atVersion() const {
    std::string v = version();
    return v.empty() ? "" : ("@" + v);
}

void FQName::clear() {
    mIsIdentifier = false;
    mPackage.clear();
    clearVersion();
    mName.clear();
}

void FQName::clearVersion(size_t* majorVer, size_t* minorVer) {
    *majorVer = *minorVer = 0;
}

bool FQName::parseVersion(const std::string& majorStr, const std::string& minorStr,
                          size_t* majorVer, size_t* minorVer) {
    bool versionParseSuccess = ::android::base::ParseUint(majorStr, majorVer) &&
                               ::android::base::ParseUint(minorStr, minorVer);
    if (!versionParseSuccess) {
        LOG(ERROR) << "numbers in " << majorStr << "." << minorStr << " are out of range.";
    }
    return versionParseSuccess;
}

bool FQName::parseVersion(const std::string& v, size_t* majorVer, size_t* minorVer) {
    if (v.empty()) {
        clearVersion(majorVer, minorVer);
        return true;
    }

    std::vector<std::string> vs = base::Split(v, ".");
    if (vs.size() != 2) return false;
    return parseVersion(vs[0], vs[1], majorVer, minorVer);
}

bool FQName::setVersion(const std::string& v) {
    return parseVersion(v, &mMajor, &mMinor);
}

void FQName::clearVersion() {
    clearVersion(&mMajor, &mMinor);
}

bool FQName::parseVersion(const std::string& majorStr, const std::string& minorStr) {
    return parseVersion(majorStr, minorStr, &mMajor, &mMinor);
}

const std::string& FQName::name() const {
    return mName;
}

std::string FQName::string() const {
    std::string out;
    out.append(mPackage);
    out.append(atVersion());
    if (!mName.empty()) {
        if (!mPackage.empty() || !version().empty()) {
            out.append("::");
        }
        out.append(mName);
    }

    return out;
}

bool FQName::operator<(const FQName& other) const {
    return string() < other.string();
}

bool FQName::operator==(const FQName& other) const {
    return string() == other.string();
}

bool FQName::operator!=(const FQName& other) const {
    return !(*this == other);
}

const std::string& FQName::getInterfaceName() const {
    CHECK(isInterfaceName()) << mName;

    return mName;
}

FQName FQName::getPackageAndVersion() const {
    return FQName(package(), version(), "");
}

std::vector<std::string> FQName::getPackageComponents() const {
    return base::Split(package(), ".");
}

bool FQName::hasVersion() const {
    return mMajor > 0;
}

std::pair<size_t, size_t> FQName::getVersion() const {
    return {mMajor, mMinor};
}

size_t FQName::getPackageMajorVersion() const {
    CHECK(hasVersion()) << "FQName: No version exists at getPackageMajorVersion(). "
                        << "Did you check hasVersion()?";
    return mMajor;
}

size_t FQName::getPackageMinorVersion() const {
    CHECK(hasVersion()) << "FQName: No version exists at getPackageMinorVersion(). "
                        << "Did you check hasVersion()?";
    return mMinor;
}

bool FQName::inPackage(const std::string& package) const {
    std::vector<std::string> components = getPackageComponents();
    std::vector<std::string> inComponents = base::Split(package, ".");

    if (inComponents.size() > components.size()) {
        return false;
    }

    for (size_t i = 0; i < inComponents.size(); i++) {
        if (inComponents[i] != components[i]) {
            return false;
        }
    }

    return true;
}

}  // namespace android::vintf::details
