/*
 * Copyright (C) 2019, The Android Open Source Project
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

#include "java_writer.h"

#include <stdio.h>

#include "Collation.h"
#include "java_writer_q.h"
#include "utils.h"

namespace android {
namespace stats_log_api_gen {

static int write_java_q_logger_class(FILE* out, const SignatureInfoMap& signatureInfoMap,
                                     const AtomDecl& attributionDecl) {
    fprintf(out, "\n");
    fprintf(out, "    // Write logging helper methods for statsd in Q and earlier.\n");
    fprintf(out, "    private static class QLogger {\n");

    write_java_q_logging_constants(out, "        ");

    // Print Q write methods.
    fprintf(out, "\n");
    fprintf(out, "        // Write methods.\n");
    write_java_methods_q_schema(out, signatureInfoMap, attributionDecl, "        ");

    fprintf(out, "    }\n");
    return 0;
}

static void write_java_annotation_constants(FILE* out, const int minApiLevel) {
    fprintf(out, "    // Annotation constants.\n");

    const map<AnnotationId, AnnotationStruct>& ANNOTATION_ID_CONSTANTS =
            get_annotation_id_constants(ANNOTATION_CONSTANT_NAME_PREFIX);
    for (const auto& [id, annotation] : ANNOTATION_ID_CONSTANTS) {
        if (annotation.minApiLevel >= API_U) {  // we don't generate annotation constants for U+
            continue;
        }
        if (minApiLevel <= API_R) {
            fprintf(out, "    public static final byte %s =\n", annotation.name.c_str());
            fprintf(out, "            Build.VERSION.SDK_INT <= %s ?\n",
                    get_java_build_version_code(API_R).c_str());
            fprintf(out, "            %hhu : StatsLog.%s;\n", id, annotation.name.c_str());
            fprintf(out, "\n");
        } else {
            fprintf(out, "    public static final byte %s = StatsLog.%s;\n",
                    annotation.name.c_str(), annotation.name.c_str());
        }
    }

    fprintf(out, "\n");
}

static void write_annotations(FILE* out, int argIndex,
                              const FieldNumberToAtomDeclSet& fieldNumberToAtomDeclSet) {
    const FieldNumberToAtomDeclSet::const_iterator fieldNumberToAtomDeclSetIt =
            fieldNumberToAtomDeclSet.find(argIndex);
    if (fieldNumberToAtomDeclSet.end() == fieldNumberToAtomDeclSetIt) {
        return;
    }
    const AtomDeclSet& atomDeclSet = fieldNumberToAtomDeclSetIt->second;
    const map<AnnotationId, AnnotationStruct>& ANNOTATION_ID_CONSTANTS =
            get_annotation_id_constants(ANNOTATION_CONSTANT_NAME_PREFIX);
    for (const shared_ptr<AtomDecl>& atomDecl : atomDeclSet) {
        const string atomConstant = make_constant_name(atomDecl->name);
        fprintf(out, "        if (%s == code) {\n", atomConstant.c_str());
        const AnnotationSet& annotations = atomDecl->fieldNumberToAnnotations.at(argIndex);
        int resetState = -1;
        int defaultState = -1;
        for (const shared_ptr<Annotation>& annotation : annotations) {
            const AnnotationStruct& annotationConstant =
                    ANNOTATION_ID_CONSTANTS.at(annotation->annotationId);
            const char *prefix = annotationConstant.minApiLevel >= API_U ? "StatsLog." : "";
            switch (annotation->type) {
                case ANNOTATION_TYPE_INT:
                    if (ANNOTATION_ID_TRIGGER_STATE_RESET == annotation->annotationId) {
                        resetState = annotation->value.intValue;
                    } else if (ANNOTATION_ID_DEFAULT_STATE == annotation->annotationId) {
                        defaultState = annotation->value.intValue;
                    } else if (ANNOTATION_ID_RESTRICTION_CATEGORY == annotation->annotationId) {
                        fprintf(out, "            builder.addIntAnnotation(%s%s,\n",
                                prefix, annotationConstant.name.c_str());
                        fprintf(out, "                                     %s%s);\n",
                                prefix, get_restriction_category_str(annotation->value.intValue)
                                .c_str());
                    } else {
                        fprintf(out, "            builder.addIntAnnotation(%s%s, %d);\n",
                                prefix, annotationConstant.name.c_str(),
                                annotation->value.intValue);
                    }
                    break;
                case ANNOTATION_TYPE_BOOL:
                    fprintf(out, "            builder.addBooleanAnnotation(%s%s, %s);\n",
                            prefix, annotationConstant.name.c_str(),
                            annotation->value.boolValue ? "true" : "false");
                    break;
                default:
                    break;
            }
        }
        if (defaultState != -1 && resetState != -1) {
            const AnnotationStruct& annotationConstant =
                    ANNOTATION_ID_CONSTANTS.at(ANNOTATION_ID_TRIGGER_STATE_RESET);
            fprintf(out, "            if (arg%d == %d) {\n", argIndex, resetState);
            fprintf(out, "                builder.addIntAnnotation(%s, %d);\n",
                    annotationConstant.name.c_str(), defaultState);
            fprintf(out, "            }\n");
        }
        fprintf(out, "        }\n");
    }
}

static int write_method_body(FILE* out, const vector<java_type_t>& signature,
                             const FieldNumberToAtomDeclSet& fieldNumberToAtomDeclSet,
                             const AtomDecl& attributionDecl, const string& indent,
                             const int minApiLevel) {
    // Start StatsEvent.Builder.
    fprintf(out,
            "%s        final StatsEvent.Builder builder = "
            "StatsEvent.newBuilder();\n",
            indent.c_str());

    // Write atom code.
    fprintf(out, "%s        builder.setAtomId(code);\n", indent.c_str());
    write_annotations(out, ATOM_ID_FIELD_NUMBER, fieldNumberToAtomDeclSet);

    // Write the args.
    int argIndex = 1;
    for (vector<java_type_t>::const_iterator arg = signature.begin(); arg != signature.end();
         arg++) {
        if (minApiLevel < API_T && is_repeated_field(*arg)) {
            fprintf(stderr, "Found repeated field type with min api level < T.");
            return 1;
        }
        switch (*arg) {
            case JAVA_TYPE_BOOLEAN:
                fprintf(out, "%s        builder.writeBoolean(arg%d);\n", indent.c_str(), argIndex);
                break;
            case JAVA_TYPE_INT:
            case JAVA_TYPE_ENUM:
                fprintf(out, "%s        builder.writeInt(arg%d);\n", indent.c_str(), argIndex);
                break;
            case JAVA_TYPE_FLOAT:
                fprintf(out, "%s        builder.writeFloat(arg%d);\n", indent.c_str(), argIndex);
                break;
            case JAVA_TYPE_LONG:
                fprintf(out, "%s        builder.writeLong(arg%d);\n", indent.c_str(), argIndex);
                break;
            case JAVA_TYPE_STRING:
                fprintf(out, "%s        builder.writeString(arg%d);\n", indent.c_str(), argIndex);
                break;
            case JAVA_TYPE_BYTE_ARRAY:
                fprintf(out,
                        "%s        builder.writeByteArray(null == arg%d ? new byte[0] : "
                        "arg%d);\n",
                        indent.c_str(), argIndex, argIndex);
                break;
            case JAVA_TYPE_BOOLEAN_ARRAY:
                fprintf(out,
                        "%s        builder.writeBooleanArray(null == arg%d ? new boolean[0] : "
                        "arg%d);\n",
                        indent.c_str(), argIndex, argIndex);
                break;
            case JAVA_TYPE_INT_ARRAY:
                fprintf(out,
                        "%s        builder.writeIntArray(null == arg%d ? new int[0] : arg%d);\n",
                        indent.c_str(), argIndex, argIndex);
                break;
            case JAVA_TYPE_FLOAT_ARRAY:
                fprintf(out,
                        "%s        builder.writeFloatArray(null == arg%d ? new float[0] : "
                        "arg%d);\n",
                        indent.c_str(), argIndex, argIndex);
                break;
            case JAVA_TYPE_LONG_ARRAY:
                fprintf(out,
                        "%s        builder.writeLongArray(null == arg%d ? new long[0] : arg%d);\n",
                        indent.c_str(), argIndex, argIndex);
                break;
            case JAVA_TYPE_STRING_ARRAY:
                fprintf(out,
                        "%s        builder.writeStringArray(null == arg%d ? new String[0] : "
                        "arg%d);\n",
                        indent.c_str(), argIndex, argIndex);
                break;
            case JAVA_TYPE_ATTRIBUTION_CHAIN: {
                const char* uidName = attributionDecl.fields.front().name.c_str();
                const char* tagName = attributionDecl.fields.back().name.c_str();

                fprintf(out, "%s        builder.writeAttributionChain(\n", indent.c_str());
                fprintf(out, "%s                null == %s ? new int[0] : %s,\n", indent.c_str(),
                        uidName, uidName);
                fprintf(out, "%s                null == %s ? new String[0] : %s);\n",
                        indent.c_str(), tagName, tagName);
                break;
            }
            default:
                // Unsupported types: OBJECT, DOUBLE.
                fprintf(stderr, "Encountered unsupported type.");
                return 1;
        }
        write_annotations(out, argIndex, fieldNumberToAtomDeclSet);
        argIndex++;
    }
    return 0;
}

static int write_java_pushed_methods(FILE* out, const SignatureInfoMap& signatureInfoMap,
                                     const AtomDecl& attributionDecl, const int minApiLevel) {
    for (auto signatureInfoMapIt = signatureInfoMap.begin();
         signatureInfoMapIt != signatureInfoMap.end(); signatureInfoMapIt++) {
        const FieldNumberToAtomDeclSet& fieldNumberToAtomDeclSet = signatureInfoMapIt->second;
        const FieldNumberToAtomDeclSet::const_iterator fieldNumberToAtomDeclSetIt =
                fieldNumberToAtomDeclSet.find(ATOM_ID_FIELD_NUMBER);
        const AtomDeclSet* atomDeclSet =
                fieldNumberToAtomDeclSetIt == fieldNumberToAtomDeclSet.end()
                        ? nullptr
                        : &fieldNumberToAtomDeclSetIt->second;
        const int requiresApiLevel = get_requires_api_level(minApiLevel, atomDeclSet);
        if (requiresApiLevel != API_LEVEL_CURRENT) {
            fprintf(out, "    @RequiresApi(%s)\n",
                    get_java_build_version_code(requiresApiLevel).c_str());
        }
        // Print method signature.
        fprintf(out, "    public static void write(int code");
        const vector<java_type_t>& signature = signatureInfoMapIt->first;
        write_java_method_signature(out, signature, attributionDecl);
        fprintf(out, ") {\n");

        // Print method body.
        string indent("");
        if (minApiLevel == API_Q) {
            fprintf(out, "        if (Build.VERSION.SDK_INT > %s) {\n",
                    get_java_build_version_code(API_Q).c_str());
            indent = "    ";
        }

        const int ret = write_method_body(out, signature, fieldNumberToAtomDeclSet, attributionDecl,
                                          indent, minApiLevel);
        if (ret != 0) {
            return ret;
        }
        fprintf(out, "\n");

        fprintf(out, "%s        builder.usePooledBuffer();\n", indent.c_str());
        fprintf(out, "%s        StatsLog.write(builder.build());\n", indent.c_str());

        // Add support for writing using Q schema if this is not the default module.
        if (minApiLevel == API_Q) {
            fprintf(out, "        } else {\n");
            fprintf(out, "            QLogger.write(code");
            int argIndex = 1;
            for (vector<java_type_t>::const_iterator arg = signature.begin();
                 arg != signature.end(); arg++) {
                if (*arg == JAVA_TYPE_ATTRIBUTION_CHAIN) {
                    const char* uidName = attributionDecl.fields.front().name.c_str();
                    const char* tagName = attributionDecl.fields.back().name.c_str();
                    fprintf(out, ", %s, %s", uidName, tagName);
                } else if (is_repeated_field(*arg)) {
                    // Module logging does not support repeated fields.
                    fprintf(stderr, "Module logging does not support repeated fields.\n");
                    return 1;
                } else {
                    fprintf(out, ", arg%d", argIndex);
                }
                argIndex++;
            }
            fprintf(out, ");\n");
            fprintf(out, "        }\n");  // if
        }

        fprintf(out, "    }\n");  // method
        fprintf(out, "\n");
    }
    return 0;
}

static int write_java_pulled_methods(FILE* out, const SignatureInfoMap& signatureInfoMap,
                                     const AtomDecl& attributionDecl, const int minApiLevel) {
    for (auto signatureInfoMapIt = signatureInfoMap.begin();
         signatureInfoMapIt != signatureInfoMap.end(); signatureInfoMapIt++) {
        // Print method signature.
        fprintf(out, "    public static StatsEvent buildStatsEvent(int code");
        const vector<java_type_t>& signature = signatureInfoMapIt->first;
        const FieldNumberToAtomDeclSet& fieldNumberToAtomDeclSet = signatureInfoMapIt->second;
        int ret = write_java_method_signature(out, signature, attributionDecl);
        if (ret != 0) {
            return ret;
        }
        fprintf(out, ") {\n");

        // Print method body.
        const string indent("");
        ret = write_method_body(out, signature, fieldNumberToAtomDeclSet, attributionDecl,
                                    indent, minApiLevel);
        if (ret != 0) {
            return ret;
        }
        fprintf(out, "\n");

        fprintf(out, "%s        return builder.build();\n", indent.c_str());

        fprintf(out, "    }\n");  // method
        fprintf(out, "\n");
    }
    return 0;
}

int write_stats_log_java(FILE* out, const Atoms& atoms, const AtomDecl& attributionDecl,
                         const string& javaClass, const string& javaPackage, const int minApiLevel,
                         const bool supportWorkSource) {
    // Print prelude
    fprintf(out, "// This file is autogenerated\n");
    fprintf(out, "\n");
    fprintf(out, "package %s;\n", javaPackage.c_str());
    fprintf(out, "\n");
    fprintf(out, "\n");
    fprintf(out, "import android.os.Build;\n");
    if (minApiLevel <= API_Q) {
        fprintf(out, "import android.os.SystemClock;\n");
    }

    fprintf(out, "import android.util.StatsEvent;\n");
    fprintf(out, "import android.util.StatsLog;\n");
    if (get_requires_api_level(minApiLevel, &atoms.decls) != API_LEVEL_CURRENT) {
        fprintf(out, "import androidx.annotation.RequiresApi;\n");
    }

    fprintf(out, "\n");
    fprintf(out, "\n");
    fprintf(out, "/**\n");
    fprintf(out, " * Utility class for logging statistics events.\n");
    fprintf(out, " */\n");
    fprintf(out, "public final class %s {\n", javaClass.c_str());

    write_java_atom_codes(out, atoms);
    write_java_enum_values(out, atoms);
    write_java_annotation_constants(out, minApiLevel);

    int errors = 0;

    // Print write methods.
    fprintf(out, "    // Write methods\n");
    errors += write_java_pushed_methods(out, atoms.signatureInfoMap, attributionDecl, minApiLevel);
    errors += write_java_non_chained_methods(out, atoms.nonChainedSignatureInfoMap);
    errors += write_java_pulled_methods(out, atoms.pulledAtomsSignatureInfoMap, attributionDecl,
                                        minApiLevel);
    if (supportWorkSource) {
        errors += write_java_work_source_methods(out, atoms.signatureInfoMap);
    }

    if (minApiLevel == API_Q) {
        errors += write_java_q_logger_class(out, atoms.signatureInfoMap, attributionDecl);
    }

    fprintf(out, "}\n");

    return errors;
}

}  // namespace stats_log_api_gen
}  // namespace android
