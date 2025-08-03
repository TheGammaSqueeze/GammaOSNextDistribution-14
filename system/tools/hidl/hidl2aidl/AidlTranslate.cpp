/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <android-base/logging.h>
#include <android-base/strings.h>
#include <hidl-util/FQName.h>
#include <hidl-util/Formatter.h>
#include <hidl-util/StringHelper.h>
#include <limits>
#include <set>
#include <string>
#include <vector>

#include "AidlHelper.h"
#include "ArrayType.h"
#include "CompoundType.h"
#include "ConstantExpression.h"
#include "Coordinator.h"
#include "EnumType.h"
#include "Interface.h"
#include "NamedType.h"
#include "ScalarType.h"
#include "Scope.h"
#include "VectorType.h"

namespace android {

std::string AidlHelper::translateHeaderFile(const FQName& fqName, AidlBackend backend) {
    switch (backend) {
        case AidlBackend::NDK:
            return AidlHelper::getAidlPackagePath(fqName) + "/translate-ndk.h";
        case AidlBackend::CPP:
            return AidlHelper::getAidlPackagePath(fqName) + "/translate-cpp.h";
        default:
            LOG(FATAL) << "Unexpected AidlBackend value";
            return "";
    }
}

std::string AidlHelper::translateSourceFile(const FQName& fqName, AidlBackend backend) {
    switch (backend) {
        case AidlBackend::NDK:
            return "translate/" + AidlHelper::getAidlPackagePath(fqName) + "/translate-ndk.cpp";
        case AidlBackend::CPP:
            return "translate/" + AidlHelper::getAidlPackagePath(fqName) + "/translate-cpp.cpp";
        case AidlBackend::JAVA:
            return "translate/" + AidlHelper::getAidlPackagePath(fqName) + "/Translate.java";
        default:
            LOG(FATAL) << "Unexpected AidlBackend value";
            return "";
    }
}

static const std::string aidlTypePackage(const NamedType& type, AidlBackend backend) {
    const std::string prefix = (backend == AidlBackend::NDK) ? "aidl::" : std::string();
    const std::string separator = (backend == AidlBackend::JAVA) ? "." : "::";
    return prefix +
           base::Join(base::Split(AidlHelper::getAidlPackage(type.fqName()), "."), separator) +
           separator + AidlHelper::getAidlType(type, type.fqName(), backend);
}

static void emitEnumStaticAssert(Formatter& out, const NamedType& namedType, AidlBackend backend) {
    CHECK(namedType.isEnum());
    const auto& enumType = static_cast<const EnumType&>(namedType);

    std::vector<const EnumValue*> values;
    for (const EnumType* type : enumType.typeChain()) {
        if (!AidlHelper::shouldBeExpanded(enumType.fqName(), type->fqName())) {
            break;
        }
        values.insert(values.end(), type->values().rbegin(), type->values().rend());
    }

    for (auto it = values.rbegin(); it != values.rend(); ++it) {
        out << "static_assert(" << aidlTypePackage(namedType, backend) << "::" << (*it)->name()
            << " == static_cast<" << aidlTypePackage(namedType, backend) << ">("
            << namedType.fullName() << "::" << (*it)->name() << "));\n";
    };

    out << "\n";
}

static void emitStaticAsserts(Formatter& out, const std::set<const NamedType*>& namedTypes,
                              AidlBackend backend) {
    CHECK(backend != AidlBackend::JAVA);
    for (const auto& namedType : namedTypes) {
        if (namedType->isEnum()) {
            emitEnumStaticAssert(out, *namedType, backend);
        }
    }
}

static void namedTypeTranslation(Formatter& out, const std::set<const NamedType*>& namedTypes,
                                 const FieldWithVersion& field, const CompoundType* parent,
                                 AidlBackend backend) {
    const NamedType* type = static_cast<const NamedType*>(field.field->get());
    if (namedTypes.find(type) == namedTypes.end()) {
        std::optional<const ReplacedTypeInfo> replacedType =
                AidlHelper::getAidlReplacedType(type->fqName());
        if (replacedType) {
            std::optional<std::function<void(Formatter&)>> translateField =
                    replacedType.value().translateField;
            if (translateField) {
                translateField.value()(out);
            }
        } else {
            AidlHelper::notes() << "An unknown named type was found in translation: "
                                << type->fqName().string() + "\n";
            out << "// FIXME Unknown type: " << type->fqName().string() << "\n";
            out << "// That type's package needs to be converted separately and the corresponding "
                   "translate function should be added here.\n";
        }
    } else {
        if (parent->style() == CompoundType::STYLE_STRUCT) {
            if (backend == AidlBackend::JAVA) {
                out << "out." << field.field->name() << " = h2aTranslate(in." << field.fullName
                    << ");\n";
            } else {
                out << "if (!translate(in." << field.fullName << ", &out->" << field.field->name()
                    << ")) return false;\n";
            }
        } else {
            if (backend == AidlBackend::JAVA) {
                out << "out.set" << StringHelper::Capitalize(field.field->name())
                    << "(h2aTranslate(in." << field.fullName << "()));\n";
            } else {
                out << "{\n";
                out << aidlTypePackage(*type, backend) << " " << field.field->name() << ";\n";
                out << "if (!translate(in." << field.fullName + "(), &" << field.field->name()
                    << ")) return false;\n";
                out << "out->set<" << aidlTypePackage(*parent, backend) << "::" << field.fullName
                    << ">(" << field.field->name() << ");\n";
                out << "}\n";
            }
        }
    }
}

static void h2aScalarChecks(Formatter& out, const Type& type, const std::string& inputAccess,
                            AidlBackend backend) {
    static const std::map<ScalarType::Kind, std::pair<std::string, size_t>> kSignedMaxSize{
            {ScalarType::KIND_UINT8,
             {"std::numeric_limits<int8_t>::max()", std::numeric_limits<int8_t>::max()}},
            {ScalarType::KIND_UINT16, {"", 0}},
            {ScalarType::KIND_INT16, {"", 0}},
            {ScalarType::KIND_UINT32,
             {"std::numeric_limits<int32_t>::max()", std::numeric_limits<int32_t>::max()}},
            {ScalarType::KIND_UINT64,
             {"std::numeric_limits<int64_t>::max()", std::numeric_limits<int64_t>::max()}}};
    const ScalarType* scalarType = type.resolveToScalarType();
    if (scalarType != nullptr && !type.isEnum()) {
        const auto& it = kSignedMaxSize.find(scalarType->getKind());
        // *int16_t is a special case for both HIDL and AIDL. For uint16_t, checks are only
        // needed in the Java backend.
        if (backend != AidlBackend::JAVA && scalarType->getKind() == ScalarType::KIND_UINT16) {
            return;
        }
        if (it != kSignedMaxSize.end()) {
            out << "// FIXME This requires conversion between signed and unsigned. Change this if "
                   "it doesn't suit your needs.\n";
            if (scalarType->getKind() == ScalarType::KIND_UINT16 ||
                scalarType->getKind() == ScalarType::KIND_INT16) {
                // HIDL uses a signed 16-bit char in Java for uint16_t and int16_t
                // AIDL uses an unsigned 16-bit char/char16_t, so this is signed to unsigned.
                out << "if (" << inputAccess << " < 0) {\n";
            } else {
                std::string affix = (scalarType->getKind() == ScalarType::KIND_UINT64) ? "L" : "";
                std::string limit = (backend == AidlBackend::JAVA)
                                            ? std::to_string(it->second.second) + affix
                                            : it->second.first;
                out << "if (" << inputAccess << " > " << limit << " || " << inputAccess
                    << " < 0) {\n";
            }
            if (backend == AidlBackend::JAVA) {
                out.indent([&] {
                    out << "throw new RuntimeException(\"Unsafe conversion between signed and "
                           "unsigned scalars for field: "
                        << inputAccess << "\");\n";
                });
            } else {
                out.indent([&] { out << "return false;\n"; });
            }
            out << "}\n";
        }
    }
}

static std::string wrapToString16(const std::string& payload, AidlBackend backend) {
    if (backend == AidlBackend::CPP) {
        return "String16(" + payload + ".c_str())";
    } else {
        return payload;
    }
}

static std::string wrapStaticCast(const std::string& payload, const Type& type,
                                  const FQName& fqName, AidlBackend backend) {
    static const std::map<std::string, std::string> kAidlBackendScalarTypes{
            {"boolean", "bool"}, {"byte", "int8_t"}, {"char", "char16_t"}, {"int", "int32_t"},
            {"long", "int64_t"}, {"float", "float"}, {"double", "double"}};
    if (type.isEnum()) {
        return "static_cast<" +
               aidlTypePackage(static_cast<const android::NamedType&>(type), backend) + ">(" +
               payload + ")";
    }
    const auto& it = kAidlBackendScalarTypes.find(AidlHelper::getAidlType(type, fqName));
    if (it != kAidlBackendScalarTypes.end()) {
        return "static_cast<" + it->second + ">(" + payload + ")";
    } else {
        return payload;
    }
}

static std::string wrapCppSource(const std::string& payload, const Type& type, const FQName& fqName,
                                 AidlBackend backend) {
    if (type.isString()) {
        return wrapToString16(payload, backend);
    } else if (type.isBitField()) {
        return wrapStaticCast(payload, *static_cast<const BitFieldType&>(type).getElementEnumType(),
                              fqName, backend);
    } else {
        return wrapStaticCast(payload, type, fqName, backend);
    }
}

static void containerTranslation(Formatter& out, const FieldWithVersion& field,
                                 const CompoundType* parent, AidlBackend backend) {
    const Type* elementType;
    std::string javaSizeAccess;
    std::string javaElementAccess;
    std::string cppSize;
    const std::string inputAccess = "in." + field.fullName +
                                    (parent->style() == CompoundType::STYLE_SAFE_UNION ? "()" : "");
    if (field.field->type().isArray()) {
        auto fieldArray = static_cast<const ArrayType*>(field.field->get());
        if (fieldArray->getConstantExpressions()[0]->castSizeT() == 0) {
            // Nothing to translate for 0 sized arrays!
            return;
        }
        elementType = fieldArray->getElementType();
        javaSizeAccess = inputAccess + ".length";
        javaElementAccess = "[i]";
        cppSize = "sizeof(" + inputAccess + ")/sizeof(" + inputAccess + "[0])";
    } else if (field.field->type().isVector()) {
        elementType = static_cast<const VectorType*>(field.field->get())->getElementType();
        javaSizeAccess = inputAccess + ".size()";
        javaElementAccess = ".get(i)";
        cppSize = inputAccess + ".size()";
    } else {
        LOG(FATAL) << "Unexpected container type for field: " << field.field->name();
        return;
    }
    if (elementType->isArray() || elementType->isVector()) {
        out << "#error Nested arrays and vectors are currently not supported. Needs implementation "
               "for field: "
            << field.field->name() << "\n";
        return;
    }
    if (elementType->isNamedType() && !elementType->isEnum()) {
        out << "#error Arrays of NamedTypes are not currently not supported. Needs implementation "
               "for field: "
            << field.field->name() << "\n";
        return;
    }
    if (backend == AidlBackend::JAVA) {
        out << "if (" << inputAccess << " != null) {\n";
        out.indent([&] {
            if (parent->style() == CompoundType::STYLE_SAFE_UNION) {
                out << "out.set" << StringHelper::Capitalize(field.field->name()) << "(new "
                    << elementType->getJavaType(true) << "[" << javaSizeAccess << "]);\n";
            } else {
                out << "out." << field.field->name() << " = new " << elementType->getJavaType(true)
                    << "[" << javaSizeAccess << "];\n";
            }
            out << "for (int i = 0; i < " << javaSizeAccess << "; i++) {\n";
            out.indent([&] {
                h2aScalarChecks(out, *elementType, inputAccess + javaElementAccess, backend);
                if (parent->style() == CompoundType::STYLE_SAFE_UNION) {
                    out << "out.get" << StringHelper::Capitalize(field.field->name()) << "()";
                } else {
                    out << "out." << field.field->name();
                }
                out << "[i] = " << inputAccess << javaElementAccess << ";\n";
            });
            out << "}\n";
        });
        out << "}\n";
    } else {
        const std::string inputAccessElement = inputAccess + "[i]";
        out << "{\n";
        out.indent([&] {
            if (parent->style() == CompoundType::STYLE_SAFE_UNION) {
                out << "out->set<" << aidlTypePackage(*parent, backend)
                    << "::" << field.field->name() << ">();\n";
            }
            out << "size_t size = " << cppSize << ";\n";
            out << "for (size_t i = 0; i < size; i++) {\n";
            out.indent([&] {
                h2aScalarChecks(out, *elementType, inputAccessElement, backend);
                out << "out->";
                if (parent->style() == CompoundType::STYLE_SAFE_UNION) {
                    out << "get<" << aidlTypePackage(*parent, backend)
                        << "::" << field.field->name() << ">()";
                } else {
                    out << field.field->name();
                }
                // Arrays with explicit size use std::array instead of std::vector
                if (field.field->type().isArray() &&
                    static_cast<const ArrayType*>(field.field->get())
                                    ->getConstantExpressions()
                                    .size() > 0) {
                    out << "[i] = "
                        << wrapCppSource(inputAccessElement, *elementType, parent->fqName(),
                                         backend)
                        << ";\n";
                } else {
                    out << ".push_back("
                        << wrapCppSource(inputAccessElement, *elementType, parent->fqName(),
                                         backend)
                        << ");\n";
                }
            });
            out << "}\n";
        });
        out << "}\n";
    }
}

static void simpleTranslation(Formatter& out, const FieldWithVersion& field,
                              const CompoundType* parent, AidlBackend backend) {
    std::string inputAccess = "in." + field.fullName;
    if (backend == AidlBackend::JAVA) {
        // HIDL uses short(signed) in the Java backend for uint16_t and int16_t
        // AIDL uses char which is unsigned. This assignment needs a cast.
        std::string cast;
        if (AidlHelper::getAidlType(field.field->type(), parent->fqName()) == "char") {
            cast = "(char) ";
        }
        if (parent->style() == CompoundType::STYLE_STRUCT) {
            h2aScalarChecks(out, field.field->type(), inputAccess, backend);
            out << "out." << field.field->name() << " = " << cast << inputAccess << ";\n";
        } else {
            inputAccess += "()";
            h2aScalarChecks(out, field.field->type(), inputAccess, backend);
            out << "out.set" << StringHelper::Capitalize(field.fullName) << "(" << cast
                << inputAccess << ");\n";
        }
    } else {
        if (parent->style() == CompoundType::STYLE_STRUCT) {
            h2aScalarChecks(out, field.field->type(), inputAccess, backend);
            out << "out->" << field.field->name() << " = "
                << wrapCppSource("in." + field.fullName, field.field->type(), parent->fqName(),
                                 backend)
                << ";\n";
        } else {
            inputAccess += "()";
            h2aScalarChecks(out, field.field->type(), inputAccess, backend);
            out << "out->set<" << aidlTypePackage(*parent, backend) << "::" << field.fullName
                << ">("
                << wrapCppSource(inputAccess, field.field->type(), parent->fqName(), backend)
                << ");\n";
        }
    }
}

static void h2aFieldTranslation(Formatter& out, const std::set<const NamedType*>& namedTypes,
                                const CompoundType* parent, const FieldWithVersion& field,
                                AidlBackend backend) {
    if (field.field->type().isNamedType() && !field.field->type().isEnum()) {
        namedTypeTranslation(out, namedTypes, field, parent, backend);
    } else if (field.field->type().isArray() || field.field->type().isVector()) {
        containerTranslation(out, field, parent, backend);
    } else if (field.field->type().isEnum() || field.field->type().isScalar() ||
               field.field->type().isString() || field.field->type().isBitField()) {
        simpleTranslation(out, field, parent, backend);
    } else {
        AidlHelper::notes() << "An unhandled type was found in translation: "
                            << field.field->type().typeName() << "\n";
        out << "#error FIXME Unhandled type: " << field.field->type().typeName() << "\n";
    }
}

static const std::string declareAidlFunctionSignature(const NamedType* type, AidlBackend backend) {
    if (backend == AidlBackend::JAVA) {
        return "static public " + aidlTypePackage(*type, backend) + " h2aTranslate(" +
               type->fullJavaName() + " in)";
    } else {
        return "__attribute__((warn_unused_result)) bool translate(const " + type->fullName() +
               "& in, " + aidlTypePackage(*type, backend) + "* out)";
    }
}

static const std::string getHidlPackagePath(const NamedType* type) {
    return base::Join(base::Split(type->fqName().package(), "."), "/");
}

static std::optional<const Interface*> getParentInterface(const NamedType* type) {
    const Scope* parent = type->parent();
    while (parent != nullptr) {
        if (parent->definesInterfaces()) {
            return parent->getInterface();
        }
        parent = parent->parent();
    }
    return std::nullopt;
}

static const std::string hidlIncludeFile(const NamedType* type) {
    std::optional<const Interface*> parent = getParentInterface(type);
    if (parent) {
        return "#include \"" + getHidlPackagePath(type) + "/" + type->fqName().version() + "/" +
               parent.value()->fqName().getInterfaceName() + ".h\"\n";
    } else {
        return "#include \"" + getHidlPackagePath(type) + "/" + type->fqName().version() +
               "/types.h\"\n";
    }
}

static const std::string aidlIncludeFile(const NamedType* type, AidlBackend backend) {
    const std::string prefix = (backend == AidlBackend::NDK) ? "aidl/" : std::string();
    return "#include \"" + prefix + AidlHelper::getAidlPackagePath(type->fqName()) + "/" +
           AidlHelper::getAidlType(*type, type->fqName()) + ".h\"\n";
}

static void emitCppTranslateHeader(
        const Coordinator& coordinator, const FQName& fqName,
        const std::set<const NamedType*>& namedTypes,
        const std::map<const NamedType*, const ProcessedCompoundType>& processedTypes,
        AidlBackend backend) {
    CHECK(backend == AidlBackend::CPP || backend == AidlBackend::NDK);
    Formatter out =
            coordinator.getFormatter(fqName, Coordinator::Location::DIRECT,
                                     "include/" + AidlHelper::translateHeaderFile(fqName, backend));

    AidlHelper::emitFileHeader(out);
    out << "// FIXME Remove this file if you don't need to translate types in this backend.\n\n";
    out << "#pragma once\n\n";

    std::set<std::string> includes = {"#include <limits>"};
    for (const auto& type : namedTypes) {
        const auto& it = processedTypes.find(type);
        if (it == processedTypes.end() && !type->isEnum()) {
            continue;
        }
        includes.insert(aidlIncludeFile(AidlHelper::getTopLevelType(type), backend));
        includes.insert(hidlIncludeFile(type));
    }
    out << base::Join(includes, "") << "\n\n";

    out << "namespace android::h2a {\n\n";
    for (const auto& type : namedTypes) {
        const auto& it = processedTypes.find(type);
        if (it == processedTypes.end()) {
            continue;
        }
        out << declareAidlFunctionSignature(type, backend) << ";\n";
    }
    out << "\n}  // namespace android::h2a\n";
}

static void emitTranslateSource(
        const Coordinator& coordinator, const FQName& fqName,
        const std::set<const NamedType*>& namedTypes,
        const std::map<const NamedType*, const ProcessedCompoundType>& processedTypes,
        AidlBackend backend) {
    Formatter out = coordinator.getFormatter(fqName, Coordinator::Location::DIRECT,
                                             AidlHelper::translateSourceFile(fqName, backend));
    AidlHelper::emitFileHeader(out);
    out << "// FIXME Remove this file if you don't need to translate types in this backend.\n\n";
    if (backend == AidlBackend::JAVA) {
        out << "package " << AidlHelper::getAidlPackage(fqName) + ";\n\n";
        out << "public class Translate {\n";
    } else {
        out << "#include \""
            << AidlHelper::translateHeaderFile((*namedTypes.begin())->fqName(), backend) + "\"\n\n";
        out << "namespace android::h2a {\n\n";
        emitStaticAsserts(out, namedTypes, backend);
    }
    for (const auto& type : namedTypes) {
        const auto& it = processedTypes.find(type);
        if (it == processedTypes.end()) {
            continue;
        }
        CHECK(type->isCompoundType()) << "Unexpected type: " << type->fqName().string();
        const CompoundType* compound = static_cast<const CompoundType*>(type);

        if (compound->style() == CompoundType::STYLE_UNION) {
            // HIDL Java backend doesn't support union so don't add a comment.
            if (backend != AidlBackend::JAVA) {
                out << "// FIXME not enough information to safely convert. Remove this function or "
                       "fill it out using the custom discriminators.\n";
                out << "// " << declareAidlFunctionSignature(type, backend) << "\n\n";
            }
            continue;
        }

        out << declareAidlFunctionSignature(type, backend) << " {\n";
        if (compound->style() == CompoundType::STYLE_SAFE_UNION) {
            out.indent([&] {
                if (backend == AidlBackend::JAVA) {
                    out << aidlTypePackage(*type, backend) << " out = new "
                        << aidlTypePackage(*type, backend) << "();\n";
                }
                out << "switch (in.getDiscriminator()) {\n";
                out.indent([&] {
                    const ProcessedCompoundType& processedType = it->second;
                    for (const auto& field : processedType.fields) {
                        if (backend == AidlBackend::JAVA) {
                            out << "case " << compound->fullJavaName() << ".hidl_discriminator."
                                << field.field->name() << ":\n";
                        } else {
                            out << "case " << compound->fullName()
                                << "::hidl_discriminator::" << field.field->name() << ":\n";
                        }
                        out.indent([&] {
                            h2aFieldTranslation(out, namedTypes, compound, field, backend);
                            out << "break;\n";
                        });
                    }
                    out << "default:\n";
                    if (backend == AidlBackend::JAVA) {
                        out.indent([&] {
                            out << "throw new RuntimeException(\"Unknown discriminator value: \" + "
                                   "Integer.toString(in.getDiscriminator()));\n";
                        });
                    } else {
                        out.indent([&] { out << "return false;\n"; });
                    }
                });
                out << "}\n";
            });
        } else {
            out.indent([&] {
                if (backend == AidlBackend::JAVA) {
                    out << aidlTypePackage(*type, backend) << " out = new "
                        << aidlTypePackage(*type, backend) << "();\n";
                }
                const ProcessedCompoundType& processedType = it->second;
                for (const auto& field : processedType.fields) {
                    h2aFieldTranslation(out, namedTypes, compound, field, backend);
                }
            });
        }
        if (backend == AidlBackend::JAVA) {
            out.indent([&] { out << "return out;\n"; });
        } else {
            out.indent([&] { out << "return true;\n"; });
        }
        out << "}\n\n";
    }
    if (backend == AidlBackend::JAVA) {
        out << "}";
    } else {
        out << "}  // namespace android::h2a";
    }
}

void AidlHelper::emitTranslation(
        const Coordinator& coordinator, const FQName& fqName,
        const std::set<const NamedType*>& namedTypes,
        const std::map<const NamedType*, const ProcessedCompoundType>& processedTypes) {
    if (processedTypes.empty()) return;
    for (auto backend : {AidlBackend::NDK, AidlBackend::CPP, AidlBackend::JAVA}) {
        if (backend != AidlBackend::JAVA) {
            emitCppTranslateHeader(coordinator, fqName, namedTypes, processedTypes, backend);
        }
        emitTranslateSource(coordinator, fqName, namedTypes, processedTypes, backend);
    }
}

}  // namespace android
