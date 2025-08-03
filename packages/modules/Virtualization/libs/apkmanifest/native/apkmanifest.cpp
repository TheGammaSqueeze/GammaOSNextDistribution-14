/*
 * Copyright 2023 The Android Open Source Project
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

#include "apkmanifest.hpp"

#include <android-base/logging.h>
#include <android-base/result.h>
#include <androidfw/AssetsProvider.h>
#include <androidfw/ResourceTypes.h>
#include <androidfw/StringPiece.h>
#include <androidfw/Util.h>
#include <stddef.h>
#include <stdint.h>
#include <utils/Errors.h>

#include <cstdlib>
#include <limits>
#include <string>
#include <string_view>

using android::Asset;
using android::AssetsProvider;
using android::OK;
using android::Res_value;
using android::ResXMLParser;
using android::ResXMLTree;
using android::statusToString;
using android::StringPiece16;
using android::base::Error;
using android::base::Result;
using android::util::Utf16ToUtf8;
using std::u16string_view;
using std::unique_ptr;

struct ApkManifestInfo {
    std::string package;
    uint32_t version_code;
    uint32_t version_code_major;
};

namespace {
// See https://developer.android.com/guide/topics/manifest/manifest-element
constexpr u16string_view MANIFEST_TAG_NAME{u"manifest"};
constexpr u16string_view ANDROID_NAMESPACE_URL{u"http://schemas.android.com/apk/res/android"};
constexpr u16string_view PACKAGE_ATTRIBUTE_NAME{u"package"};
constexpr u16string_view VERSION_CODE_ATTRIBUTE_NAME{u"versionCode"};
constexpr u16string_view VERSION_CODE_MAJOR_ATTRIBUTE_NAME{u"versionCodeMajor"};

// Read through the XML parse tree up to the <manifest> element.
Result<void> findManifestElement(ResXMLTree& tree) {
    for (;;) {
        ResXMLParser::event_code_t event = tree.next();
        switch (event) {
            case ResXMLParser::END_DOCUMENT:
            case ResXMLParser::END_TAG:
            case ResXMLParser::TEXT:
            default:
                return Error() << "Unexpected XML parsing event: " << event;
            case ResXMLParser::BAD_DOCUMENT:
                return Error() << "Failed to parse XML: " << statusToString(tree.getError());
            case ResXMLParser::START_NAMESPACE:
            case ResXMLParser::END_NAMESPACE:
                // Not of interest, keep going.
                break;
            case ResXMLParser::START_TAG:
                // The first tag in an AndroidManifest.xml should be <manifest> (no namespace).
                // And that's actually the only tag we care about.
                if (tree.getElementNamespaceID() >= 0) {
                    return Error() << "Root element has unexpected namespace.";
                }
                size_t nameLength = 0;
                const char16_t* nameChars = tree.getElementName(&nameLength);
                if (!nameChars) {
                    return Error() << "Missing tag name";
                }
                if (u16string_view(nameChars, nameLength) != MANIFEST_TAG_NAME) {
                    return Error() << "Expected <manifest> as root element";
                }
                return {};
        }
    }
}

// Return an attribute encoded as a string, converted to UTF-8. Note that all
// attributes are strings in the original XML, but the binary format encodes
// some as binary numbers etc. This function does not handle converting those
// encodings back to strings, so should only be used when it is known that a
// numeric value is not allowed.
Result<std::string> getStringOnlyAttribute(const ResXMLTree& tree, size_t index) {
    size_t len;
    const char16_t* value = tree.getAttributeStringValue(index, &len);
    if (!value) {
        return Error() << "Expected attribute to have string value";
    }
    return Utf16ToUtf8(StringPiece16(value, len));
}

// Return the u32 value of an attribute.
Result<uint32_t> getU32Attribute(const ResXMLTree& tree, size_t index) {
    auto type = tree.getAttributeDataType(index);
    switch (type) {
        case Res_value::TYPE_INT_DEC:
        case Res_value::TYPE_INT_HEX:
            // This is how we'd expect the version to be encoded - and we don't
            // care what base it was originally in.
            return tree.getAttributeData(index);
        case Res_value::TYPE_STRING: {
            // If the original string is encoded, then we need to convert it.
            auto str = OR_RETURN(getStringOnlyAttribute(tree, index));
            char* str_end = nullptr;
            // Note that by specifying base 0 we allow for octal, hex, or
            // decimal representations here.
            unsigned long value = std::strtoul(str.c_str(), &str_end, 0);
            if (str_end != str.c_str() + str.size() ||
                value > std::numeric_limits<uint32_t>::max()) {
                return Error() << "Invalid numeric value";
            }
            return static_cast<uint32_t>(value);
        }
        default:
            return Error() << "Expected numeric value, got type " << type;
    }
}

// Parse the binary manifest and extract the information we care about.
// Everything we're interested in should be an attribute on the <manifest> tag.
// We don't care what order they come in, absent attributes will be treated as
// the default value, and any unknown attributes (including ones not in the
// expected namespace) will be ignored.
Result<unique_ptr<ApkManifestInfo>> parseManifest(const void* manifest, size_t size) {
    ResXMLTree tree;
    auto status = tree.setTo(manifest, size);
    if (status != OK) {
        return Error() << "Failed to create XML Tree: " << statusToString(status);
    }

    OR_RETURN(findManifestElement(tree));

    unique_ptr<ApkManifestInfo> info{new ApkManifestInfo{}};

    size_t count = tree.getAttributeCount();
    for (size_t i = 0; i < count; ++i) {
        size_t len;
        const char16_t* chars;

        chars = tree.getAttributeNamespace(i, &len);
        auto namespaceUrl = chars ? u16string_view(chars, len) : u16string_view();

        chars = tree.getAttributeName(i, &len);
        auto attributeName = chars ? u16string_view(chars, len) : u16string_view();

        if (namespaceUrl.empty()) {
            if (attributeName == PACKAGE_ATTRIBUTE_NAME) {
                auto result = getStringOnlyAttribute(tree, i);
                if (!result.ok()) return Error() << "Package name: " << result.error();
                info->package = *result;
            }
        } else if (namespaceUrl == ANDROID_NAMESPACE_URL) {
            if (attributeName == VERSION_CODE_ATTRIBUTE_NAME) {
                auto result = getU32Attribute(tree, i);
                if (!result.ok()) return Error() << "Version code: " << result.error();
                info->version_code = *result;
            } else if (attributeName == VERSION_CODE_MAJOR_ATTRIBUTE_NAME) {
                auto result = getU32Attribute(tree, i);
                if (!result.ok()) return Error() << "Version code major: " << result.error();
                info->version_code_major = *result;
            }
        }
    }

    return info;
}
} // namespace

const ApkManifestInfo* extractManifestInfo(const void* manifest, size_t size) {
    auto result = parseManifest(manifest, size);
    if (!result.ok()) {
        LOG(ERROR) << "Failed to parse APK manifest:" << result.error().message();
        return nullptr;
    }
    return result->release();
}

void freeManifestInfo(const ApkManifestInfo* info) {
    delete info;
}

const char* getPackageName(const ApkManifestInfo* info) {
    return info->package.c_str();
}

uint64_t getVersionCode(const ApkManifestInfo* info) {
    return info->version_code | (static_cast<uint64_t>(info->version_code_major) << 32);
}
