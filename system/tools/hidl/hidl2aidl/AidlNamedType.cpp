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

#include "AidlHelper.h"
#include "CompoundType.h"
#include "Coordinator.h"
#include "EnumType.h"
#include "Interface.h"
#include "NamedType.h"
#include "TypeDef.h"

namespace android {

static void emitConversionNotes(Formatter& out, const NamedType& namedType) {
    out << "// This is the HIDL definition of " << namedType.fqName().string() << "\n";
    out.pushLinePrefix("// ");
    namedType.emitHidlDefinition(out);
    out.popLinePrefix();
    out << "\n";
}

static void emitTypeDefAidlDefinition(Formatter& out, const TypeDef& typeDef) {
    out << "// Cannot convert typedef " << typeDef.referencedType()->definedName() << " "
        << typeDef.fqName().string() << " since AIDL does not support typedefs.\n";
    emitConversionNotes(out, typeDef);
}

static void emitEnumAidlDefinition(Formatter& out, const EnumType& enumType) {
    const ScalarType* scalar = enumType.storageType()->resolveToScalarType();
    CHECK(scalar != nullptr) << enumType.typeName();

    enumType.emitDocComment(out);
    out << "@VintfStability\n";
    out << "@Backing(type=\"" << AidlHelper::getAidlType(*scalar, enumType.fqName()) << "\")\n";
    out << "enum " << AidlHelper::getAidlType(enumType, enumType.fqName()) << " ";

    std::vector<const EnumValue*> values;
    const EnumType* skippedType = nullptr;
    for (const EnumType* type : enumType.typeChain()) {
        if (!AidlHelper::shouldBeExpanded(enumType.fqName(), type->fqName())) {
            skippedType = type;
            break;
        }
        values.insert(values.end(), type->values().rbegin(), type->values().rend());
    }
    out.block([&] {
        if (skippedType != nullptr) {
            out << "// Not expanding values from " << skippedType->fqName().string()
                << ". See \'-e\' argument.\n";
        }
        for (auto it = values.rbegin(); it != values.rend(); ++it) {
            (*it)->emitDocComment(out);
            out << (*it)->name();
            if (!(*it)->isAutoFill()) {
                out << " = " << (*it)->constExpr()->expression();
            }
            out << ",\n";
        };
    });
    out << "\n";
}

static void emitCompoundTypeAidlDefinition(
        Formatter& out, const CompoundType& compoundType,
        const std::map<const NamedType*, const ProcessedCompoundType>& processedTypes) {
    // Get all of the subtypes and fields from this type and any older versions
    // that it references.
    const auto& it = processedTypes.find(&compoundType);
    CHECK(it != processedTypes.end()) << "Failed to find " << compoundType.fullName();
    const ProcessedCompoundType& processedType = it->second;

    compoundType.emitDocComment(out);
    out << "@VintfStability\n";
    if (compoundType.style() == CompoundType::STYLE_STRUCT) {
        out << "parcelable " << AidlHelper::getAidlName(compoundType.fqName()) << " ";
    } else {
        if (compoundType.style() == CompoundType::STYLE_UNION) {
            out << "// FIXME Any discriminators should be removed since they are automatically "
                   "added.\n";
        }
        out << "union " << AidlHelper::getAidlName(compoundType.fqName()) << " ";
    }
    out.block([&] {
        // Emit all nested type definitions
        for (auto const& type : processedType.subTypes) {
            AidlHelper::emitAidl(*type, out, processedTypes);
        }
        // Emit all of the fields from the processed type
        for (auto const& fieldWithVersion : processedType.fields) {
            fieldWithVersion.field->emitDocComment(out);
            std::string aidlType =
                    AidlHelper::getAidlType(*fieldWithVersion.field->get(), compoundType.fqName());
            out << aidlType << " " << fieldWithVersion.field->name() << ";\n";
        }
    });
    out << "\n";
}

void AidlHelper::emitAidl(
        const NamedType& namedType, Formatter& out,
        const std::map<const NamedType*, const ProcessedCompoundType>& processedTypes) {
    if (namedType.isTypeDef()) {
        const TypeDef& typeDef = static_cast<const TypeDef&>(namedType);
        emitTypeDefAidlDefinition(out, typeDef);
    } else if (namedType.isCompoundType()) {
        const CompoundType& compoundType = static_cast<const CompoundType&>(namedType);
        emitCompoundTypeAidlDefinition(out, compoundType, processedTypes);
    } else if (namedType.isEnum()) {
        const EnumType& enumType = static_cast<const EnumType&>(namedType);
        emitEnumAidlDefinition(out, enumType);
    } else if (namedType.isInterface()) {
        const Interface& iface = static_cast<const Interface&>(namedType);
        emitAidl(iface, out, processedTypes);
    } else {
        out << "// TODO: Fix this " << namedType.definedName() << "\n";
    }
}

}  // namespace android
