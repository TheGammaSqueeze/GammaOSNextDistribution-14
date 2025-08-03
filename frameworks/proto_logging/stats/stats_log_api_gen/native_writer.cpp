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

#include "native_writer.h"

#include <stdio.h>

#include "Collation.h"
#include "utils.h"

namespace android {
namespace stats_log_api_gen {

static void write_native_annotation_constants(FILE* out) {
    fprintf(out, "// Annotation constants.\n");

    const map<AnnotationId, AnnotationStruct>& ANNOTATION_ID_CONSTANTS =
            get_annotation_id_constants(ANNOTATION_CONSTANT_NAME_PREFIX);
    for (const auto& [id, annotation] : ANNOTATION_ID_CONSTANTS) {
        fprintf(out, "const uint8_t %s = %hhu;\n", annotation.name.c_str(), id);
    }
    fprintf(out, "\n");
}

static void write_annotations(FILE* out, int argIndex,
                              const FieldNumberToAtomDeclSet& fieldNumberToAtomDeclSet,
                              const string& methodPrefix, const string& methodSuffix,
                              const int minApiLevel) {
    const FieldNumberToAtomDeclSet::const_iterator fieldNumberToAtomDeclSetIt =
            fieldNumberToAtomDeclSet.find(argIndex);
    if (fieldNumberToAtomDeclSet.end() == fieldNumberToAtomDeclSetIt) {
        return;
    }
    const AtomDeclSet& atomDeclSet = fieldNumberToAtomDeclSetIt->second;
    const map<AnnotationId, AnnotationStruct>& ANNOTATION_ID_CONSTANTS =
            get_annotation_id_constants(ANNOTATION_CONSTANT_NAME_PREFIX);
    const string constantPrefix = minApiLevel > API_R ? "ASTATSLOG_" : "";
    for (const shared_ptr<AtomDecl>& atomDecl : atomDeclSet) {
        const string atomConstant = make_constant_name(atomDecl->name);
        fprintf(out, "    if (%s == code) {\n", atomConstant.c_str());
        const AnnotationSet& annotations = atomDecl->fieldNumberToAnnotations.at(argIndex);
        int resetState = -1;
        int defaultState = -1;
        for (const shared_ptr<Annotation>& annotation : annotations) {
            const string& annotationConstant =
                    ANNOTATION_ID_CONSTANTS.at(annotation->annotationId).name;
            switch (annotation->type) {
                case ANNOTATION_TYPE_INT:
                    if (ANNOTATION_ID_TRIGGER_STATE_RESET == annotation->annotationId) {
                        resetState = annotation->value.intValue;
                    } else if (ANNOTATION_ID_DEFAULT_STATE == annotation->annotationId) {
                        defaultState = annotation->value.intValue;
                    } else if (ANNOTATION_ID_RESTRICTION_CATEGORY == annotation->annotationId) {
                        fprintf(out, "        %saddInt32Annotation(%s%s%s,\n",
                                methodPrefix.c_str(), methodSuffix.c_str(), constantPrefix.c_str(),
                                annotationConstant.c_str());
                        fprintf(out, "                                       %s%s);\n",
                                constantPrefix.c_str(),
                                get_restriction_category_str(annotation->value.intValue).c_str());
                    } else {
                        fprintf(out, "        %saddInt32Annotation(%s%s%s, %d);\n",
                                methodPrefix.c_str(), methodSuffix.c_str(), constantPrefix.c_str(),
                                annotationConstant.c_str(), annotation->value.intValue);
                    }
                    break;
                case ANNOTATION_TYPE_BOOL:
                    fprintf(out, "        %saddBoolAnnotation(%s%s%s, %s);\n", methodPrefix.c_str(),
                            methodSuffix.c_str(), constantPrefix.c_str(),
                            annotationConstant.c_str(),
                            annotation->value.boolValue ? "true" : "false");
                    break;
                default:
                    break;
            }
        }
        if (defaultState != -1 && resetState != -1) {
            const string& annotationConstant =
                    ANNOTATION_ID_CONSTANTS.at(ANNOTATION_ID_TRIGGER_STATE_RESET).name;
            fprintf(out, "        if (arg%d == %d) {\n", argIndex, resetState);
            fprintf(out, "            %saddInt32Annotation(%s%s%s, %d);\n", methodPrefix.c_str(),
                    methodSuffix.c_str(), constantPrefix.c_str(), annotationConstant.c_str(),
                    defaultState);
            fprintf(out, "        }\n");
        }
        fprintf(out, "    }\n");
    }
}

static int write_native_method_body(FILE* out, const vector<java_type_t>& signature,
                                    const FieldNumberToAtomDeclSet& fieldNumberToAtomDeclSet,
                                    const AtomDecl& attributionDecl, const int minApiLevel) {
    int argIndex = 1;
    fprintf(out, "    AStatsEvent_setAtomId(event, code);\n");
    write_annotations(out, ATOM_ID_FIELD_NUMBER, fieldNumberToAtomDeclSet, "AStatsEvent_",
                      "event, ", minApiLevel);
    for (vector<java_type_t>::const_iterator arg = signature.begin(); arg != signature.end();
         arg++) {
        if (minApiLevel < API_T && is_repeated_field(*arg)) {
            fprintf(stderr, "Found repeated field type with min api level < T.");
            return 1;
        }
        switch (*arg) {
            case JAVA_TYPE_ATTRIBUTION_CHAIN: {
                const char* uidName = attributionDecl.fields.front().name.c_str();
                const char* tagName = attributionDecl.fields.back().name.c_str();
                fprintf(out,
                        "    AStatsEvent_writeAttributionChain(event, "
                        "reinterpret_cast<const uint32_t*>(%s), %s.data(), "
                        "static_cast<uint8_t>(%s_length));\n",
                        uidName, tagName, uidName);
                break;
            }
            case JAVA_TYPE_BYTE_ARRAY:
                fprintf(out,
                        "    AStatsEvent_writeByteArray(event, "
                        "reinterpret_cast<const uint8_t*>(arg%d.arg), "
                        "arg%d.arg_length);\n",
                        argIndex, argIndex);
                break;
            case JAVA_TYPE_BOOLEAN:
                fprintf(out, "    AStatsEvent_writeBool(event, arg%d);\n", argIndex);
                break;
            case JAVA_TYPE_INT:
                [[fallthrough]];
            case JAVA_TYPE_ENUM:
                fprintf(out, "    AStatsEvent_writeInt32(event, arg%d);\n", argIndex);
                break;
            case JAVA_TYPE_FLOAT:
                fprintf(out, "    AStatsEvent_writeFloat(event, arg%d);\n", argIndex);
                break;
            case JAVA_TYPE_LONG:
                fprintf(out, "    AStatsEvent_writeInt64(event, arg%d);\n", argIndex);
                break;
            case JAVA_TYPE_STRING:
                fprintf(out, "    AStatsEvent_writeString(event, arg%d);\n", argIndex);
                break;
            case JAVA_TYPE_BOOLEAN_ARRAY:
                fprintf(out, "    AStatsEvent_writeBoolArray(event, arg%d, arg%d_length);\n",
                        argIndex, argIndex);
                break;
            case JAVA_TYPE_INT_ARRAY:
                [[fallthrough]];
            case JAVA_TYPE_ENUM_ARRAY:
                fprintf(out,
                        "    AStatsEvent_writeInt32Array(event, arg%d.data(), arg%d.size());\n",
                        argIndex, argIndex);
                break;
            case JAVA_TYPE_FLOAT_ARRAY:
                fprintf(out,
                        "    AStatsEvent_writeFloatArray(event, arg%d.data(), arg%d.size());\n",
                        argIndex, argIndex);
                break;
            case JAVA_TYPE_LONG_ARRAY:
                fprintf(out,
                        "    AStatsEvent_writeInt64Array(event, arg%d.data(), arg%d.size());\n",
                        argIndex, argIndex);
                break;
            case JAVA_TYPE_STRING_ARRAY:
                fprintf(out,
                        "    AStatsEvent_writeStringArray(event, arg%d.data(), arg%d.size());\n",
                        argIndex, argIndex);
                break;

            default:
                // Unsupported types: OBJECT, DOUBLE
                fprintf(stderr, "Encountered unsupported type.\n");
                return 1;
        }
        write_annotations(out, argIndex, fieldNumberToAtomDeclSet, "AStatsEvent_", "event, ",
                          minApiLevel);
        argIndex++;
    }
    return 0;
}

static void write_native_method_call(FILE* out, const string& methodName,
                                     const vector<java_type_t>& signature,
                                     const AtomDecl& attributionDecl, int argIndex) {
    fprintf(out, "%s(code", methodName.c_str());
    for (vector<java_type_t>::const_iterator arg = signature.begin(); arg != signature.end();
         arg++) {
        if (*arg == JAVA_TYPE_ATTRIBUTION_CHAIN) {
            for (const auto& chainField : attributionDecl.fields) {
                if (chainField.javaType == JAVA_TYPE_STRING) {
                    fprintf(out, ", %s", chainField.name.c_str());
                } else {
                    fprintf(out, ",  %s,  %s_length", chainField.name.c_str(),
                            chainField.name.c_str());
                }
            }
        } else {
            fprintf(out, ", arg%d", argIndex);

            if (*arg == JAVA_TYPE_BOOLEAN_ARRAY) {
                fprintf(out, ", arg%d_length", argIndex);
            }
        }
        argIndex++;
    }
    fprintf(out, ");\n");
}

static int write_native_stats_write_methods(FILE* out, const SignatureInfoMap& signatureInfoMap,
                                            const AtomDecl& attributionDecl, const int minApiLevel,
                                            bool bootstrap) {
    fprintf(out, "\n");
    for (const auto& [signature, fieldNumberToAtomDeclSet] : signatureInfoMap) {
        write_native_method_signature(out, "int stats_write(", signature, attributionDecl, " {");

        // Write method body.
        if (bootstrap) {
            fprintf(out, "    ::android::os::StatsBootstrapAtom atom;\n");
            fprintf(out, "    atom.atomId = code;\n");
            const FieldNumberToAtomDeclSet::const_iterator fieldNumberToAtomDeclSetIt =
                    fieldNumberToAtomDeclSet.find(ATOM_ID_FIELD_NUMBER);
            if (fieldNumberToAtomDeclSet.end() != fieldNumberToAtomDeclSetIt) {
                fprintf(stderr, "Bootstrap atoms do not support annotations\n");
                return 1;
            }
            int argIndex = 1;
            const char* atomVal = "::android::os::StatsBootstrapAtomValue::";
            for (vector<java_type_t>::const_iterator arg = signature.begin();
                 arg != signature.end(); arg++) {
                switch (*arg) {
                    case JAVA_TYPE_BYTE_ARRAY:
                        fprintf(out,
                                "    const uint8_t* arg%dbyte = reinterpret_cast<const "
                                "uint8_t*>(arg%d.arg);\n",
                                argIndex, argIndex);
                        fprintf(out,
                                "    "
                                "atom.values.push_back(%smake<%sbytesValue>(std::vector(arg%dbyte, "
                                "arg%dbyte + arg%d.arg_length)));\n",
                                atomVal, atomVal, argIndex, argIndex, argIndex);
                        break;
                    case JAVA_TYPE_BOOLEAN:
                        fprintf(out, "    atom.values.push_back(%smake<%sboolValue>(arg%d));\n",
                                atomVal, atomVal, argIndex);
                        break;
                    case JAVA_TYPE_INT:  // Fall through.
                    case JAVA_TYPE_ENUM:
                        fprintf(out, "    atom.values.push_back(%smake<%sintValue>(arg%d));\n",
                                atomVal, atomVal, argIndex);
                        break;
                    case JAVA_TYPE_FLOAT:
                        fprintf(out, "    atom.values.push_back(%smake<%sfloatValue>(arg%d));\n",
                                atomVal, atomVal, argIndex);
                        break;
                    case JAVA_TYPE_LONG:
                        fprintf(out, "    atom.values.push_back(%smake<%slongValue>(arg%d));\n",
                                atomVal, atomVal, argIndex);
                        break;
                    case JAVA_TYPE_STRING:
                        fprintf(out,
                                "    atom.values.push_back(%smake<%sstringValue>("
                                "::android::String16(arg%d)));\n",
                                atomVal, atomVal, argIndex);
                        break;
                    default:
                        // Unsupported types: OBJECT, DOUBLE, ATTRIBUTION_CHAIN,
                        // and all repeated fields
                        fprintf(stderr, "Encountered unsupported type.\n");
                        return 1;
                }
                const FieldNumberToAtomDeclSet::const_iterator fieldNumberToAtomDeclSetIt =
                        fieldNumberToAtomDeclSet.find(argIndex);
                if (fieldNumberToAtomDeclSet.end() != fieldNumberToAtomDeclSetIt) {
                    fprintf(stderr, "Bootstrap atoms do not support annotations\n");
                    return 1;
                }
                argIndex++;
            }
            fprintf(out,
                    "    bool success = "
                    "::android::os::stats::StatsBootstrapAtomClient::reportBootstrapAtom(atom);\n");
            fprintf(out, "    return success? 0 : -1;\n");

        } else if (minApiLevel == API_Q) {
            int argIndex = 1;
            fprintf(out, "    StatsEventCompat event;\n");
            fprintf(out, "    event.setAtomId(code);\n");
            write_annotations(out, ATOM_ID_FIELD_NUMBER, fieldNumberToAtomDeclSet, "event.", "",
                              minApiLevel);
            for (vector<java_type_t>::const_iterator arg = signature.begin();
                 arg != signature.end(); arg++) {
                switch (*arg) {
                    case JAVA_TYPE_ATTRIBUTION_CHAIN: {
                        const char* uidName = attributionDecl.fields.front().name.c_str();
                        const char* tagName = attributionDecl.fields.back().name.c_str();
                        fprintf(out, "    event.writeAttributionChain(%s, %s_length, %s);\n",
                                uidName, uidName, tagName);
                        break;
                    }
                    case JAVA_TYPE_BYTE_ARRAY:
                        fprintf(out, "    event.writeByteArray(arg%d.arg, arg%d.arg_length);\n",
                                argIndex, argIndex);
                        break;
                    case JAVA_TYPE_BOOLEAN:
                        fprintf(out, "    event.writeBool(arg%d);\n", argIndex);
                        break;
                    case JAVA_TYPE_INT:  // Fall through.
                    case JAVA_TYPE_ENUM:
                        fprintf(out, "    event.writeInt32(arg%d);\n", argIndex);
                        break;
                    case JAVA_TYPE_FLOAT:
                        fprintf(out, "    event.writeFloat(arg%d);\n", argIndex);
                        break;
                    case JAVA_TYPE_LONG:
                        fprintf(out, "    event.writeInt64(arg%d);\n", argIndex);
                        break;
                    case JAVA_TYPE_STRING:
                        fprintf(out, "    event.writeString(arg%d);\n", argIndex);
                        break;
                    default:
                        // Unsupported types: OBJECT, DOUBLE, and all repeated
                        // fields.
                        fprintf(stderr, "Encountered unsupported type.\n");
                        return 1;
                }
                write_annotations(out, argIndex, fieldNumberToAtomDeclSet, "event.", "",
                                  minApiLevel);
                argIndex++;
            }
            fprintf(out, "    return event.writeToSocket();\n");  // end method body.
        } else {
            fprintf(out, "    AStatsEvent* event = AStatsEvent_obtain();\n");
            const int ret = write_native_method_body(out, signature, fieldNumberToAtomDeclSet,
                                                     attributionDecl, minApiLevel);
            if (ret != 0) {
                return ret;
            }
            fprintf(out, "    const int ret = AStatsEvent_write(event);\n");
            fprintf(out, "    AStatsEvent_release(event);\n");
            fprintf(out, "    return ret;\n");  // end method body.
        }
        fprintf(out, "}\n\n");  // end method.
    }
    return 0;
}

static void write_native_stats_write_non_chained_methods(FILE* out,
                                                         const SignatureInfoMap& signatureInfoMap,
                                                         const AtomDecl& attributionDecl) {
    fprintf(out, "\n");
    for (const auto& [signature, _] : signatureInfoMap) {
        write_native_method_signature(out, "int stats_write_non_chained(", signature,
                                      attributionDecl, " {");

        vector<java_type_t> newSignature;

        // First two args form the attribution node so size goes down by 1.
        newSignature.reserve(signature.size() - 1);

        // First arg is Attribution Chain.
        newSignature.push_back(JAVA_TYPE_ATTRIBUTION_CHAIN);

        // Followed by the originial signature except the first 2 args.
        newSignature.insert(newSignature.end(), signature.begin() + 2, signature.end());

        const char* uidName = attributionDecl.fields.front().name.c_str();
        const char* tagName = attributionDecl.fields.back().name.c_str();
        fprintf(out, "    const int32_t* %s = &arg1;\n", uidName);
        fprintf(out, "    const size_t %s_length = 1;\n", uidName);
        fprintf(out, "    const std::vector<char const*> %s(1, arg2);\n", tagName);
        fprintf(out, "    return ");
        write_native_method_call(out, "stats_write", newSignature, attributionDecl, 2);

        fprintf(out, "}\n\n");
    }
}

static int write_native_build_stats_event_methods(FILE* out,
                                                  const SignatureInfoMap& signatureInfoMap,
                                                  const AtomDecl& attributionDecl,
                                                  const int minApiLevel) {
    fprintf(out, "\n");
    for (const auto& [signature, fieldNumberToAtomDeclSet] : signatureInfoMap) {
        write_native_method_signature(out, "void addAStatsEvent(AStatsEventList* pulled_data, ",
                                      signature, attributionDecl, " {");

        fprintf(out, "    AStatsEvent* event = AStatsEventList_addStatsEvent(pulled_data);\n");
        const int ret = write_native_method_body(out, signature, fieldNumberToAtomDeclSet,
                                                 attributionDecl, minApiLevel);
        if (ret != 0) {
            return ret;
        }
        fprintf(out, "    AStatsEvent_build(event);\n");  // end method body.

        fprintf(out, "}\n\n");  // end method.
    }
    return 0;
}

int write_stats_log_cpp(FILE* out, const Atoms& atoms, const AtomDecl& attributionDecl,
                        const string& cppNamespace, const string& importHeader,
                        const int minApiLevel, bool bootstrap) {
    // Print prelude
    fprintf(out, "// This file is autogenerated\n");
    fprintf(out, "\n");

    fprintf(out, "#include <%s>\n", importHeader.c_str());
    if (!bootstrap) {
        if (minApiLevel == API_Q) {
            fprintf(out, "#include <StatsEventCompat.h>\n");
        } else {
            fprintf(out, "#include <stats_event.h>\n");
        }

        if (minApiLevel > API_R) {
            fprintf(out, "#include <stats_annotations.h>\n");
        }

        if (minApiLevel > API_Q && !atoms.pulledAtomsSignatureInfoMap.empty()) {
            fprintf(out, "#include <stats_pull_atom_callback.h>\n");
        }
    } else {
        fprintf(out, "#include <StatsBootstrapAtomClient.h>\n");
        fprintf(out, "#include <android/os/StatsBootstrapAtom.h>\n");
        fprintf(out, "#include <utils/String16.h>\n");
    }

    fprintf(out, "\n");
    write_namespace(out, cppNamespace);

    int ret = write_native_stats_write_methods(out, atoms.signatureInfoMap, attributionDecl,
                                               minApiLevel, bootstrap);
    if (ret != 0) {
        return ret;
    }
    if (!bootstrap) {
        write_native_stats_write_non_chained_methods(out, atoms.nonChainedSignatureInfoMap,
                                                     attributionDecl);
        ret = write_native_build_stats_event_methods(out, atoms.pulledAtomsSignatureInfoMap,
                                                     attributionDecl, minApiLevel);
        if (ret != 0) {
            return ret;
        }
    }

    // Print footer
    fprintf(out, "\n");
    write_closing_namespace(out, cppNamespace);

    return 0;
}

int write_stats_log_header(FILE* out, const Atoms& atoms, const AtomDecl& attributionDecl,
                           const string& cppNamespace, const int minApiLevel, bool bootstrap) {
    const bool includePull = !atoms.pulledAtomsSignatureInfoMap.empty() && !bootstrap;
    write_native_header_preamble(out, cppNamespace, includePull);
    write_native_atom_constants(out, atoms, attributionDecl);
    write_native_atom_enums(out, atoms);

    if (minApiLevel <= API_R) {
        write_native_annotation_constants(out);
    }

    fprintf(out, "struct BytesField {\n");
    fprintf(out,
            "  BytesField(char const* array, size_t len) : arg(array), "
            "arg_length(len) {}\n");
    fprintf(out, "  char const* arg;\n");
    fprintf(out, "  size_t arg_length;\n");
    fprintf(out, "};\n");
    fprintf(out, "\n");

    // Print write methods
    fprintf(out, "//\n");
    fprintf(out, "// Write methods\n");
    fprintf(out, "//\n");
    write_native_method_header(out, "int stats_write(", atoms.signatureInfoMap, attributionDecl);
    fprintf(out, "\n");

    // Attribution chains and pulled atoms are not supported for bootstrap processes.
    if (!bootstrap) {
        fprintf(out, "//\n");
        fprintf(out, "// Write flattened methods\n");
        fprintf(out, "//\n");
        write_native_method_header(out, "int stats_write_non_chained(",
                                   atoms.nonChainedSignatureInfoMap, attributionDecl);
        fprintf(out, "\n");

        // Print pulled atoms methods.
        fprintf(out, "//\n");
        fprintf(out, "// Add AStatsEvent methods\n");
        fprintf(out, "//\n");
        write_native_method_header(out, "void addAStatsEvent(AStatsEventList* pulled_data, ",
                                   atoms.pulledAtomsSignatureInfoMap, attributionDecl);
        fprintf(out, "\n");
    }

    write_native_header_epilogue(out, cppNamespace);

    return 0;
}

}  // namespace stats_log_api_gen
}  // namespace android
