/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <hidl-util/FQName.h>
#include <string>

#include "AidlHelper.h"
#include "ArrayType.h"
#include "EnumType.h"
#include "FmqType.h"
#include "NamedType.h"
#include "Type.h"
#include "VectorType.h"

namespace android {

static std::string getPlaceholderType(const std::string& type) {
    return "IBinder /* FIXME: " + type + " */";
}

static const std::map<std::string, ReplacedTypeInfo> kReplacedTypes{
        {"android.hidl.safe_union@1.0::Monostate",
         ReplacedTypeInfo{
                 "boolean", std::nullopt,
                 [](Formatter& out) { out << "// Nothing to translate for Monostate.\n"; }}},
};

std::optional<const ReplacedTypeInfo> AidlHelper::getAidlReplacedType(const FQName& fqName) {
    const auto& it = kReplacedTypes.find(fqName.string());
    if (it != kReplacedTypes.end()) {
        return it->second;
    }
    return std::nullopt;
}

std::string AidlHelper::getAidlType(const Type& type, const FQName& relativeTo,
                                    AidlBackend backend) {
    if (type.isVector()) {
        const VectorType& vec = static_cast<const VectorType&>(type);
        return getAidlType(*vec.getElementType(), relativeTo) + "[]";
    } else if (type.isArray()) {
        const ArrayType& arr = static_cast<const ArrayType&>(type);
        auto sizes = arr.getConstantExpressions();
        CHECK(sizes.size() > 0) << "Failed to get array dimensions for " << arr.definedName();
        std::string typeStr = getAidlType(*arr.getElementType(), relativeTo);
        for (const auto& size : sizes) {
            typeStr += "[" + size->value() + "]";
        }
        return typeStr;
    } else if (type.isNamedType()) {
        const NamedType& namedType = static_cast<const NamedType&>(type);
        if (getAidlPackage(relativeTo) == getAidlPackage(namedType.fqName())) {
            return getAidlName(namedType.fqName(), backend);
        } else {
            std::optional<const ReplacedTypeInfo> type = getAidlReplacedType(namedType.fqName());
            if (type) {
                notes() << "Replacing type " << namedType.fqName().string() << " with "
                        << type.value().aidlReplacedType << ".\n";
                return type.value().aidlReplacedType;
            }
            std::optional<std::string> name = getAidlFQName(namedType.fqName()).value();
            if (name) {
                return name.value();
            } else {
                LOG(FATAL) << "Failed to resolve Aidl FQName: " << namedType.fqName().string();
                return "";
            }
        }
    } else if (type.isMemory()) {
        return getPlaceholderType("memory");
    } else if (type.isFmq()) {
        const FmqType& fmq = static_cast<const FmqType&>(type);
        return getPlaceholderType(fmq.templatedTypeName() + "<" +
                                  getAidlType(*fmq.getElementType(), relativeTo) + ">");
    } else if (type.isPointer()) {
        return getPlaceholderType("pointer");
    } else if (type.isEnum()) {
        // enum type goes to the primitive java type in HIDL, but AIDL should use
        // the enum type name itself
        return type.definedName();
    } else if (type.isBitField()) {
        const BitFieldType& bitfield = static_cast<const BitFieldType&>(type);
        return getAidlType(*bitfield.getElementType(), relativeTo);
    } else if (type.getJavaType() == "short") {
        notes() << relativeTo.name()
                << ": Consider replacing char with int if signed integer is desired\n";
        return "char";
    } else {
        return type.getJavaType();
    }
}

}  // namespace android
