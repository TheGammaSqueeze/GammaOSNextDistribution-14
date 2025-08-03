/*
 * Copyright (C) 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "native_writer_vendor.h"

#include <stdio.h>

#include <set>
#include <string>
#include <vector>

#include "Collation.h"
#include "utils.h"

namespace android {
namespace stats_log_api_gen {

using std::string;

static void write_native_vendor_annotation_header(FILE* out, const string& annotationName,
                                                  const char* indent) {
    fprintf(out, "%s{\n", indent);
    fprintf(out, "%s    Annotation annotation;\n", indent);
    fprintf(out, "%s    annotation.annotationId = %s;\n", indent, annotationName.c_str());
}

static void write_native_vendor_annotation_footer(FILE* out, const char* indent) {
    fprintf(out, "%s    annotations.push_back(std::move(annotation));\n", indent);
    fprintf(out, "%s}\n", indent);
}

static void write_native_vendor_annotation_int(FILE* out, const string& annotationName, int value,
                                               const char* indent) {
    write_native_vendor_annotation_header(out, annotationName, indent);
    fprintf(out, "%sannotation.value.set<AnnotationValue::intValue>(%d);\n", indent, value);
    write_native_vendor_annotation_footer(out, indent);
}

static void write_native_vendor_annotation_int_constant(FILE* out, const string& annotationName,
                                                        const string& constantName,
                                                        const char* indent) {
    write_native_vendor_annotation_header(out, annotationName, indent);
    fprintf(out, "%sannotation.value.set<AnnotationValue::intValue>(%s);\n", indent,
            constantName.c_str());
    write_native_vendor_annotation_footer(out, indent);
}

static void write_native_vendor_annotation_bool(FILE* out, const string& annotationName, bool value,
                                                const char* indent) {
    write_native_vendor_annotation_header(out, annotationName, indent);
    fprintf(out, "%sannotation.value.set<AnnotationValue::boolValue>(%s);\n", indent,
            value ? "true" : "false");
    write_native_vendor_annotation_footer(out, indent);
}

static void write_native_annotations_vendor_for_field(FILE* out, int argIndex,
                                                      const AtomDeclSet& atomDeclSet) {
    if (atomDeclSet.empty()) {
        return;
    }

    const char* indent = "    ";
    const char* indent2 = "        ";
    const char* indent3 = "            ";

    const int valueIndex = argIndex - 2;

    const map<AnnotationId, AnnotationStruct>& ANNOTATION_ID_CONSTANTS =
            get_annotation_id_constants(ANNOTATION_CONSTANT_NAME_VENDOR_NATIVE_PREFIX);

    for (const shared_ptr<AtomDecl>& atomDecl : atomDeclSet) {
        const string atomConstant = make_constant_name(atomDecl->name);
        fprintf(out, "%sif (%s == code) {\n", indent, atomConstant.c_str());

        if (argIndex == ATOM_ID_FIELD_NUMBER) {
            fprintf(out, "%sstd::vector<std::optional<Annotation>> annotations;\n", indent2);
        } else {
            fprintf(out, "%sstd::vector<Annotation> annotations;\n", indent2);
        }

        const AnnotationSet& annotations = atomDecl->fieldNumberToAnnotations.at(argIndex);
        int resetState = -1;
        int defaultState = -1;
        for (const shared_ptr<Annotation>& annotation : annotations) {
            const AnnotationStruct& annotationConstant =
                    ANNOTATION_ID_CONSTANTS.at(annotation->annotationId);
            switch (annotation->type) {
                case ANNOTATION_TYPE_INT:
                    if (ANNOTATION_ID_TRIGGER_STATE_RESET == annotation->annotationId) {
                        resetState = annotation->value.intValue;
                    } else if (ANNOTATION_ID_DEFAULT_STATE == annotation->annotationId) {
                        defaultState = annotation->value.intValue;
                    } else if (ANNOTATION_ID_RESTRICTION_CATEGORY == annotation->annotationId) {
                        fprintf(out, "%s{\n", indent2);
                        write_native_vendor_annotation_int_constant(
                                out, annotationConstant.name,
                                get_restriction_category_str(annotation->value.intValue), indent3);
                        fprintf(out, "%s}\n", indent2);
                    } else {
                        fprintf(out, "%s{\n", indent2);
                        write_native_vendor_annotation_int(out, annotationConstant.name,
                                                           annotation->value.intValue, indent3);
                        fprintf(out, "%s}\n", indent2);
                    }
                    break;
                case ANNOTATION_TYPE_BOOL:
                    fprintf(out, "%s{\n", indent2);
                    write_native_vendor_annotation_bool(out, annotationConstant.name,
                                                        annotation->value.boolValue, indent3);
                    fprintf(out, "%s}\n", indent2);
                    break;
                default:
                    break;
            }
        }
        if (defaultState != -1 && resetState != -1) {
            const AnnotationStruct& annotationConstant =
                    ANNOTATION_ID_CONSTANTS.at(ANNOTATION_ID_TRIGGER_STATE_RESET);
            fprintf(out, "%sif (arg%d == %d) {\n", indent2, argIndex, resetState);
            write_native_vendor_annotation_int(out, annotationConstant.name, defaultState, indent3);
            fprintf(out, "%s}\n", indent2);
        }

        if (argIndex == ATOM_ID_FIELD_NUMBER) {
            fprintf(out, "%satomAnnotations = std::move(annotations);\n", indent2);
        } else {
            fprintf(out, "%sif (annotations.size() > 0) {\n", indent2);
            fprintf(out, "%sAnnotationSet field%dAnnotations;\n", indent3, valueIndex);
            fprintf(out, "%sfield%dAnnotations.valueIndex = %d;\n", indent3, valueIndex,
                    valueIndex);
            fprintf(out, "%sfield%dAnnotations.annotations = std::move(annotations);\n", indent3,
                    valueIndex);
            fprintf(out, "%sfieldsAnnotations.push_back(std::move(field%dAnnotations));\n", indent3,
                    valueIndex);
            fprintf(out, "%s}\n", indent2);
        }
        fprintf(out, "%s}\n", indent);
    }
}

static int write_native_create_vendor_atom_methods(FILE* out,
                                                   const SignatureInfoMap& signatureInfoMap,
                                                   const AtomDecl& attributionDecl) {
    fprintf(out, "\n");
    for (const auto& [signature, fieldNumberToAtomDeclSet] : signatureInfoMap) {
        // TODO (b/264922532): provide vendor implementation to skip arg1 for reverseDomainName
        write_native_method_signature(out, "VendorAtom createVendorAtom(", signature,
                                      attributionDecl, " {", /*isVendorAtomLogging=*/true);

        fprintf(out, "    VendorAtom atom;\n");

        // Write method body.
        fprintf(out, "    atom.atomId = code;\n");
        fprintf(out, "    atom.reverseDomainName = arg1;\n");

        // Exclude first field - which is reverseDomainName
        const int vendorAtomValuesCount = signature.size() - 1;
        fprintf(out, "    vector<VendorAtomValue> values(%d);\n", vendorAtomValuesCount);

        // Use 1-based index to access signature arguments
        for (int argIndex = 2; argIndex <= signature.size(); argIndex++) {
            const java_type_t& argType = signature[argIndex - 1];

            const int atomValueIndex = argIndex - 2;

            switch (argType) {
                case JAVA_TYPE_ATTRIBUTION_CHAIN: {
                    fprintf(stderr, "Found attribution chain - not supported.\n");
                    return 1;
                }
                case JAVA_TYPE_BYTE_ARRAY:
                    fprintf(out,
                            "    "
                            "values[%d].set<VendorAtomValue::byteArrayValue>(arg%d);\n",
                            atomValueIndex, argIndex);
                    break;
                case JAVA_TYPE_BOOLEAN:
                    fprintf(out, "    values[%d].set<VendorAtomValue::boolValue>(arg%d);\n",
                            atomValueIndex, argIndex);
                    break;
                case JAVA_TYPE_INT:
                    [[fallthrough]];
                case JAVA_TYPE_ENUM:
                    fprintf(out, "    values[%d].set<VendorAtomValue::intValue>(arg%d);\n",
                            atomValueIndex, argIndex);
                    break;
                case JAVA_TYPE_FLOAT:
                    fprintf(out, "    values[%d].set<VendorAtomValue::floatValue>(arg%d);\n",
                            atomValueIndex, argIndex);
                    break;
                case JAVA_TYPE_LONG:
                    fprintf(out, "    values[%d].set<VendorAtomValue::longValue>(arg%d);\n",
                            atomValueIndex, argIndex);
                    break;
                case JAVA_TYPE_STRING:
                    fprintf(out, "    values[%d].set<VendorAtomValue::stringValue>(arg%d);\n",
                            atomValueIndex, argIndex);
                    break;
                case JAVA_TYPE_BOOLEAN_ARRAY:
                    fprintf(out, "    values[%d].set<VendorAtomValue::repeatedBoolValue>(arg%d);\n",
                            atomValueIndex, argIndex);
                    break;
                case JAVA_TYPE_INT_ARRAY:
                    [[fallthrough]];
                case JAVA_TYPE_ENUM_ARRAY:
                    fprintf(out, "    values[%d].set<VendorAtomValue::repeatedIntValue>(arg%d);\n",
                            atomValueIndex, argIndex);
                    break;
                case JAVA_TYPE_FLOAT_ARRAY:
                    fprintf(out,
                            "    values[%d].set<VendorAtomValue::repeatedFloatValue>(arg%d);\n",
                            atomValueIndex, argIndex);
                    break;
                case JAVA_TYPE_LONG_ARRAY:
                    fprintf(out, "    values[%d].set<VendorAtomValue::repeatedLongValue>(arg%d);\n",
                            atomValueIndex, argIndex);
                    break;
                case JAVA_TYPE_STRING_ARRAY:
                    fprintf(out, "    {\n");
                    fprintf(out, "    vector<optional<string>> arrayValue(\n");
                    fprintf(out, "        arg%d.begin(), arg%d.end());\n", argIndex, argIndex);
                    fprintf(out,
                            "    "
                            "values[%d].set<VendorAtomValue::repeatedStringValue>(std::move("
                            "arrayValue));\n",
                            atomValueIndex);
                    fprintf(out, "    }\n");
                    break;
                default:
                    // Unsupported types: OBJECT, DOUBLE
                    fprintf(stderr, "Encountered unsupported type.\n");
                    return 1;
            }
        }
        fprintf(out, "    atom.values = std::move(values);\n");  // end method body.

        // check will be there an atom for this signature with atom level annotations
        const AtomDeclSet atomAnnotations =
                get_annotations(ATOM_ID_FIELD_NUMBER, fieldNumberToAtomDeclSet);
        if (atomAnnotations.size()) {
            fprintf(out, "    std::vector<std::optional<Annotation>> atomAnnotations;\n");
            write_native_annotations_vendor_for_field(out, ATOM_ID_FIELD_NUMBER, atomAnnotations);
            fprintf(out, "    if (atomAnnotations.size() > 0) {\n");
            fprintf(out, "        atom.atomAnnotations = std::move(atomAnnotations);\n");
            fprintf(out, "    }\n\n");
        }

        // Create fieldsAnnotations instance only in case if there is an atom fields with annotation
        // for this signature
        bool atomWithFieldsAnnotation = false;
        for (int argIndex = 2; argIndex <= signature.size(); argIndex++) {
            if (get_annotations(argIndex, fieldNumberToAtomDeclSet).size() > 0) {
                atomWithFieldsAnnotation = true;
                break;
            }
        }

        if (atomWithFieldsAnnotation) {
            fprintf(out, "    std::vector<std::optional<AnnotationSet>> fieldsAnnotations;\n");
            for (int argIndex = 2; argIndex <= signature.size(); argIndex++) {
                const AtomDeclSet fieldAnnotations =
                        get_annotations(argIndex, fieldNumberToAtomDeclSet);
                write_native_annotations_vendor_for_field(out, argIndex, fieldAnnotations);
            }
            fprintf(out, "    if (fieldsAnnotations.size() > 0) {\n");
            fprintf(out, "        atom.valuesAnnotations = std::move(fieldsAnnotations);\n");
            fprintf(out, "    }\n\n");
        }

        fprintf(out, "    // elision of copy operations is permitted on return\n");
        fprintf(out, "    return atom;\n");
        fprintf(out, "}\n\n");  // end method.
    }
    return 0;
}

int write_stats_log_cpp_vendor(FILE* out, const Atoms& atoms, const AtomDecl& attributionDecl,
                               const string& cppNamespace, const string& importHeader) {
    // Print prelude
    fprintf(out, "// This file is autogenerated\n");
    fprintf(out, "\n");

    fprintf(out, "#include <%s>\n", importHeader.c_str());
    fprintf(out, "#include <aidl/android/frameworks/stats/VendorAtom.h>\n");

    fprintf(out, "\n");
    write_namespace(out, cppNamespace);
    fprintf(out, "\n");
    fprintf(out, "using namespace aidl::android::frameworks::stats;\n");
    fprintf(out, "using std::make_optional;\n");
    fprintf(out, "using std::optional;\n");
    fprintf(out, "using std::vector;\n");
    fprintf(out, "using std::string;\n");

    const int ret =
            write_native_create_vendor_atom_methods(out, atoms.signatureInfoMap, attributionDecl);
    if (ret != 0) {
        return ret;
    }
    // Print footer
    fprintf(out, "\n");
    write_closing_namespace(out, cppNamespace);

    return 0;
}

int write_stats_log_header_vendor(FILE* out, const Atoms& atoms, const AtomDecl& attributionDecl,
                                  const string& cppNamespace) {
    write_native_header_preamble(out, cppNamespace, false, /*isVendorAtomLogging=*/true);
    write_native_atom_constants(out, atoms, attributionDecl, "createVendorAtom(",
                                /*isVendorAtomLogging=*/true);

    for (AtomDeclSet::const_iterator atomIt = atoms.decls.begin(); atomIt != atoms.decls.end();
         atomIt++) {
        set<string> processedEnums;

        for (vector<AtomField>::const_iterator field = (*atomIt)->fields.begin();
             field != (*atomIt)->fields.end(); field++) {
            if (field->javaType == JAVA_TYPE_ENUM || field->javaType == JAVA_TYPE_ENUM_ARRAY) {
                // There might be N fields with the same enum type
                // avoid duplication definitions
                if (processedEnums.find(field->enumTypeName) != processedEnums.end()) {
                    continue;
                }

                if (processedEnums.empty()) {
                    fprintf(out, "class %s final {\n", (*atomIt)->message.c_str());
                    fprintf(out, "public:\n\n");
                }

                processedEnums.insert(field->enumTypeName);

                fprintf(out, "enum %s {\n", field->enumTypeName.c_str());
                size_t i = 0;
                for (map<int, string>::const_iterator value = field->enumValues.begin();
                     value != field->enumValues.end(); value++) {
                    fprintf(out, "    %s = %d", make_constant_name(value->second).c_str(),
                            value->first);
                    char const* const comma = (i == field->enumValues.size() - 1) ? "" : ",";
                    fprintf(out, "%s\n", comma);
                    i++;
                }

                fprintf(out, "};\n");
            }
        }
        if (!processedEnums.empty()) {
            fprintf(out, "};\n\n");
        }
    }

    fprintf(out, "using ::aidl::android::frameworks::stats::VendorAtom;\n");

    // Print write methods
    fprintf(out, "//\n");
    fprintf(out, "// Write methods\n");
    fprintf(out, "//\n");
    write_native_method_header(out, "VendorAtom createVendorAtom(", atoms.signatureInfoMap,
                               attributionDecl,
                               /*isVendorAtomLogging=*/true);
    fprintf(out, "\n");

    write_native_header_epilogue(out, cppNamespace);

    return 0;
}

}  // namespace stats_log_api_gen
}  // namespace android
