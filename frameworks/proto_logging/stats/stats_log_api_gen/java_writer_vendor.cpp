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

#include "java_writer_vendor.h"

#include <stdio.h>

#include <algorithm>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include "Collation.h"
#include "utils.h"

namespace android {
namespace stats_log_api_gen {

static void write_vendor_annotation_int(FILE* out, const string& annotationName, int value,
                                        const char* indent) {
    fprintf(out, "%s{\n", indent);
    fprintf(out, "%s    Annotation annotation = new Annotation();\n", indent);
    fprintf(out, "%s    annotation.annotationId = %s;\n", indent, annotationName.c_str());
    fprintf(out, "%s    annotation.value = AnnotationValue.intValue(%d);\n", indent, value);
    fprintf(out, "%s    annotations[annotationIdx] = annotation;\n", indent);
    fprintf(out, "%s    annotationIdx++;\n", indent);
    fprintf(out, "%s}\n", indent);
}

static void write_vendor_annotation_int_constant(FILE* out, const string& annotationName,
                                                 const string& constantName, const char* indent) {
    fprintf(out, "%s{\n", indent);
    fprintf(out, "%s    Annotation annotation = new Annotation();\n", indent);
    fprintf(out, "%s    annotation.annotationId = %s;\n", indent, annotationName.c_str());
    fprintf(out, "%s    annotation.value = AnnotationValue.intValue(%s);\n", indent,
            constantName.c_str());
    fprintf(out, "%s    annotations[annotationIdx] = annotation;\n", indent);
    fprintf(out, "%s    annotationIdx++;\n", indent);
    fprintf(out, "%s}\n", indent);
}

static void write_vendor_annotation_bool(FILE* out, const string& annotationName, bool value,
                                         const char* indent) {
    fprintf(out, "%s{\n", indent);
    fprintf(out, "%s    Annotation annotation = new Annotation();\n", indent);
    fprintf(out, "%s    annotation.annotationId = %s;\n", indent, annotationName.c_str());
    fprintf(out, "%s    annotation.value = AnnotationValue.boolValue(%s);\n", indent,
            value ? "true" : "false");
    fprintf(out, "%s    annotations[annotationIdx] = annotation;\n", indent);
    fprintf(out, "%s    annotationIdx++;\n", indent);
    fprintf(out, "%s}\n", indent);
}

static bool write_value_annotations_array_init(FILE* out, const AtomDeclSet& atomDeclSet,
                                               set<string>& processedAtomNames) {
    const char* indent = "        ";

    for (const shared_ptr<AtomDecl>& atomDecl : atomDeclSet) {
        const string atomConstant = make_constant_name(atomDecl->name);
        if (processedAtomNames.find(atomConstant) != processedAtomNames.end()) {
            continue;
        }
        processedAtomNames.insert(atomConstant);
        fprintf(out, "%sif (%s == atomId) {\n", indent, atomConstant.c_str());
        fprintf(out, "%s    fieldsAnnotations = new ArrayList<AnnotationSet>();\n", indent);
        fprintf(out, "%s}\n", indent);
    }
    return true;
}

static bool write_annotations_vendor_for_field(FILE* out, int argIndex,
                                               const AtomDeclSet& atomDeclSet) {
    const char* indent = "        ";
    const char* indent2 = "            ";
    const char* indent3 = "               ";

    const map<AnnotationId, AnnotationStruct>& annotationIdConstants =
            get_annotation_id_constants(ANNOTATION_CONSTANT_NAME_VENDOR_PREFIX);

    for (const shared_ptr<AtomDecl>& atomDecl : atomDeclSet) {
        const string atomConstant = make_constant_name(atomDecl->name);
        fprintf(out, "%sif (%s == atomId) {\n", indent, atomConstant.c_str());

        const AnnotationSet& annotations = atomDecl->fieldNumberToAnnotations.at(argIndex);
        // calculate annotations amount upfront
        // search for a trigger reset state & default state annotation
        int resetState = -1;
        int defaultState = -1;
        for (const shared_ptr<Annotation>& annotation : annotations) {
            switch (annotation->type) {
                case ANNOTATION_TYPE_INT:
                    if (ANNOTATION_ID_TRIGGER_STATE_RESET == annotation->annotationId) {
                        resetState = annotation->value.intValue;
                    } else if (ANNOTATION_ID_DEFAULT_STATE == annotation->annotationId) {
                        defaultState = annotation->value.intValue;
                    }
                    break;
                default:
                    break;
            }
        }

        const int annotationsCount = std::count_if(
                annotations.begin(), annotations.end(),
                [](const shared_ptr<Annotation>& annotation) {
                    return (annotation->type == ANNOTATION_TYPE_INT &&
                            (ANNOTATION_ID_TRIGGER_STATE_RESET != annotation->annotationId &&
                             ANNOTATION_ID_DEFAULT_STATE != annotation->annotationId)) ||
                           annotation->type == ANNOTATION_TYPE_BOOL;
                });

        fprintf(out, "%sint annotationsCount = %d;\n", indent2, annotationsCount);
        if (defaultState != -1 && resetState != -1) {
            fprintf(out, "%sif (arg%d == %d) {\n", indent2, argIndex, resetState);
            fprintf(out, "%s    annotationsCount++;\n", indent2);
            fprintf(out, "%s}\n", indent2);
        }
        fprintf(out, "%sAnnotation[] annotations = new Annotation[annotationsCount];\n", indent2);
        fprintf(out, "%sint annotationIdx = 0;\n", indent2);

        for (const shared_ptr<Annotation>& annotation : annotations) {
            const AnnotationStruct& annotationConstant =
                    annotationIdConstants.at(annotation->annotationId);
            switch (annotation->type) {
                case ANNOTATION_TYPE_INT:
                    if (ANNOTATION_ID_RESTRICTION_CATEGORY == annotation->annotationId) {
                        write_vendor_annotation_int_constant(
                                out, annotationConstant.name,
                                get_restriction_category_str(annotation->value.intValue), indent2);
                    } else if (ANNOTATION_ID_TRIGGER_STATE_RESET != annotation->annotationId &&
                               ANNOTATION_ID_DEFAULT_STATE != annotation->annotationId) {
                        write_vendor_annotation_int(out, annotationConstant.name,
                                                    annotation->value.intValue, indent2);
                    }
                    break;
                case ANNOTATION_TYPE_BOOL:
                    write_vendor_annotation_bool(out, annotationConstant.name,
                                                 annotation->value.boolValue, indent2);
                    break;
                default:
                    break;
            }
        }
        if (defaultState != -1 && resetState != -1) {
            const AnnotationStruct& annotationConstant =
                    annotationIdConstants.at(ANNOTATION_ID_TRIGGER_STATE_RESET);
            fprintf(out, "%sif (arg%d == %d)", indent2, argIndex, resetState);
            write_vendor_annotation_int(out, annotationConstant.name, defaultState, indent2);
        }

        if (argIndex == ATOM_ID_FIELD_NUMBER) {
            fprintf(out, "%satomAnnotations = annotations;\n", indent2);
        } else {
            const int valueIndex = argIndex - 2;
            fprintf(out, "%sif (annotationsCount > 0) {\n", indent2);
            fprintf(out, "%sAnnotationSet field%dAnnotations = new AnnotationSet();\n", indent3,
                    valueIndex);
            fprintf(out, "%sfield%dAnnotations.valueIndex = %d;\n", indent3, valueIndex,
                    valueIndex);
            fprintf(out, "%sfield%dAnnotations.annotations = annotations;\n", indent3, valueIndex);
            fprintf(out, "%sfieldsAnnotations.add(field%dAnnotations);\n", indent3, valueIndex);
            fprintf(out, "%s}\n", indent2);
        }
        fprintf(out, "%s}\n", indent);
    }

    return true;
}

static int write_method_body_vendor(FILE* out, const vector<java_type_t>& signature,
                                    const FieldNumberToAtomDeclSet& fieldNumberToAtomDeclSet) {
    const char* indent = "        ";

    // Write atom code.
    fprintf(out, "%sVendorAtom atom = new VendorAtom();\n", indent);
    fprintf(out, "%satom.reverseDomainName = arg1;\n", indent);
    fprintf(out, "%satom.atomId = atomId;\n", indent);
    fprintf(out, "%satom.values = new VendorAtomValue[%ld];\n", indent, signature.size() - 1);

    for (int argIndex = 2; argIndex <= signature.size(); argIndex++) {
        const java_type_t& argType = signature[argIndex - 1];
        const int atomValueIndex = argIndex - 2;
        switch (argType) {
            case JAVA_TYPE_ATTRIBUTION_CHAIN: {
                fprintf(stderr, "Found attribution chain - not supported.\n");
                return 1;
            }
            case JAVA_TYPE_BYTE_ARRAY:
                fprintf(out, "%satom.values[%d] = VendorAtomValue.byteArrayValue(arg%d);\n", indent,
                        atomValueIndex, argIndex);
                break;
            case JAVA_TYPE_BOOLEAN:
                fprintf(out, "%satom.values[%d] = VendorAtomValue.boolValue(arg%d);\n", indent,
                        atomValueIndex, argIndex);
                break;
            case JAVA_TYPE_INT:
                [[fallthrough]];
            case JAVA_TYPE_ENUM:
                fprintf(out, "%satom.values[%d] = VendorAtomValue.intValue(arg%d);\n", indent,
                        atomValueIndex, argIndex);
                break;
            case JAVA_TYPE_FLOAT:
                fprintf(out, "%satom.values[%d] = VendorAtomValue.floatValue(arg%d);\n", indent,
                        atomValueIndex, argIndex);
                break;
            case JAVA_TYPE_LONG:
                fprintf(out, "%satom.values[%d] = VendorAtomValue.longValue(arg%d);\n", indent,
                        atomValueIndex, argIndex);
                break;
            case JAVA_TYPE_STRING:
                fprintf(out, "%satom.values[%d] = VendorAtomValue.stringValue(arg%d);\n", indent,
                        atomValueIndex, argIndex);
                break;
            case JAVA_TYPE_BOOLEAN_ARRAY:
                fprintf(out, "%satom.values[%d] = VendorAtomValue.repeatedBoolValue(arg%d);\n",
                        indent, atomValueIndex, argIndex);
                break;
            case JAVA_TYPE_INT_ARRAY:
                [[fallthrough]];
            case JAVA_TYPE_ENUM_ARRAY:
                fprintf(out, "%satom.values[%d] = VendorAtomValue.repeatedIntValue(arg%d);\n",
                        indent, atomValueIndex, argIndex);
                break;
            case JAVA_TYPE_FLOAT_ARRAY:
                fprintf(out, "%satom.values[%d] = VendorAtomValue.repeatedFloatValue(arg%d);\n",
                        indent, atomValueIndex, argIndex);
                break;
            case JAVA_TYPE_LONG_ARRAY:
                fprintf(out, "%satom.values[%d] = VendorAtomValue.repeatedLongValue(arg%d);\n",
                        indent, atomValueIndex, argIndex);
                break;
            case JAVA_TYPE_STRING_ARRAY:
                fprintf(out, "%satom.values[%d] = VendorAtomValue.repeatedStringValue(arg%d);\n",
                        indent, atomValueIndex, argIndex);
                break;
            default:
                // Unsupported types: OBJECT, DOUBLE
                fprintf(stderr, "Encountered unsupported type.\n");
                return 1;
        }
    }

    // check will be there an atom for this signature with atom level annotations
    const AtomDeclSet atomAnnotations =
            get_annotations(ATOM_ID_FIELD_NUMBER, fieldNumberToAtomDeclSet);
    if (atomAnnotations.size()) {
        fprintf(out, "%sAnnotation[] atomAnnotations = null;\n", indent);
        write_annotations_vendor_for_field(out, ATOM_ID_FIELD_NUMBER, atomAnnotations);
        fprintf(out, "%sif (atomAnnotations != null && atomAnnotations.length > 0) {\n", indent);
        fprintf(out, "%s    atom.atomAnnotations = atomAnnotations;\n", indent);
        fprintf(out, "%s}\n", indent);
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
        fprintf(out, "%sArrayList<AnnotationSet> fieldsAnnotations = null;\n", indent);
        set<string> processedAtomNames;
        for (int argIndex = 2; argIndex <= signature.size(); argIndex++) {
            const AtomDeclSet fieldAnnotations =
                    get_annotations(argIndex, fieldNumberToAtomDeclSet);
            write_value_annotations_array_init(out, fieldAnnotations, processedAtomNames);
        }

        for (int argIndex = 2; argIndex <= signature.size(); argIndex++) {
            const AtomDeclSet fieldAnnotations =
                    get_annotations(argIndex, fieldNumberToAtomDeclSet);
            write_annotations_vendor_for_field(out, argIndex, fieldAnnotations);
        }
        fprintf(out, "%sif (fieldsAnnotations != null && fieldsAnnotations.size() > 0) {\n",
                indent);
        // Converting ArrayList<AnnotationSet> to annotationSet[]
        fprintf(out,
                "%s    atom.valuesAnnotations = new AnnotationSet[fieldsAnnotations.size()];\n",
                indent);
        fprintf(out,
                "%s    atom.valuesAnnotations = "
                "fieldsAnnotations.toArray(atom.valuesAnnotations);\n",
                indent);
        fprintf(out, "%s}\n", indent);
    }

    fprintf(out, "%sreturn atom;\n", indent);

    return 0;
}

static int write_java_pushed_methods_vendor(FILE* out, const SignatureInfoMap& signatureInfoMap) {
    for (auto signatureInfoMapIt = signatureInfoMap.begin();
         signatureInfoMapIt != signatureInfoMap.end(); signatureInfoMapIt++) {
        // Print method signature.
        fprintf(out, "    public static VendorAtom createVendorAtom(int atomId");
        const vector<java_type_t>& signature = signatureInfoMapIt->first;
        const AtomDecl emptyAttributionDecl;
        int ret = write_java_method_signature(out, signature, emptyAttributionDecl);
        if (ret != 0) {
            return ret;
        }
        fprintf(out, ") {\n");

        // Print method body.
        const FieldNumberToAtomDeclSet& fieldNumberToAtomDeclSet = signatureInfoMapIt->second;
        ret = write_method_body_vendor(out, signature, fieldNumberToAtomDeclSet);
        if (ret != 0) {
            return ret;
        }

        fprintf(out, "    }\n");  // method
        fprintf(out, "\n");
    }
    return 0;
}

static void write_java_enum_values_vendor(FILE* out, const Atoms& atoms) {
    set<string> processedEnums;

    fprintf(out, "    // Constants for enum values.\n\n");
    for (AtomDeclSet::const_iterator atomIt = atoms.decls.begin(); atomIt != atoms.decls.end();
         atomIt++) {
        for (vector<AtomField>::const_iterator field = (*atomIt)->fields.begin();
             field != (*atomIt)->fields.end(); field++) {
            if (field->javaType == JAVA_TYPE_ENUM || field->javaType == JAVA_TYPE_ENUM_ARRAY) {
                // There might be N fields with the same enum type
                // Avoiding duplication definitions
                // enum type name == [atom_message_type_name]__[enum_type_name]
                const string full_enum_type_name = (*atomIt)->message + "__" + field->enumTypeName;

                if (processedEnums.find(full_enum_type_name) != processedEnums.end()) {
                    continue;
                }
                processedEnums.insert(full_enum_type_name);

                fprintf(out, "    // Values for %s.%s\n", (*atomIt)->message.c_str(),
                        field->name.c_str());
                for (map<int, string>::const_iterator value = field->enumValues.begin();
                     value != field->enumValues.end(); value++) {
                    fprintf(out, "    public static final int %s__%s = %d;\n",
                            make_constant_name(full_enum_type_name).c_str(),
                            make_constant_name(value->second).c_str(), value->first);
                }
                fprintf(out, "\n");
            }
        }
    }
}

int write_stats_log_java_vendor(FILE* out, const Atoms& atoms, const string& javaClass,
                                const string& javaPackage) {
    // Print prelude
    fprintf(out, "// This file is autogenerated\n");
    fprintf(out, "\n");
    fprintf(out, "package %s;\n", javaPackage.c_str());
    fprintf(out, "\n");

    fprintf(out, "import android.frameworks.stats.VendorAtom;\n");
    fprintf(out, "import android.frameworks.stats.VendorAtomValue;\n");
    fprintf(out, "import android.frameworks.stats.AnnotationValue;\n");
    fprintf(out, "import android.frameworks.stats.Annotation;\n");
    fprintf(out, "import android.frameworks.stats.AnnotationId;\n");
    fprintf(out, "import android.frameworks.stats.AnnotationSet;\n");

    fprintf(out, "import java.util.ArrayList;\n");

    fprintf(out, "\n");
    fprintf(out, "/**\n");
    fprintf(out, " * Utility class for logging statistics events.\n");
    fprintf(out, " */\n");
    fprintf(out, "public final class %s {\n", javaClass.c_str());

    write_java_atom_codes(out, atoms);
    write_java_enum_values_vendor(out, atoms);

    // Print write methods.
    fprintf(out, "    // Write methods\n");
    const int errors = write_java_pushed_methods_vendor(out, atoms.signatureInfoMap);

    fprintf(out, "}\n");

    return errors;
}

}  // namespace stats_log_api_gen
}  // namespace android
