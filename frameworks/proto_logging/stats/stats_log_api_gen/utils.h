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

#ifndef ANDROID_STATS_LOG_API_GEN_UTILS_H
#define ANDROID_STATS_LOG_API_GEN_UTILS_H

#include <google/protobuf/compiler/importer.h>
#include <stdio.h>
#include <string.h>

#include <map>
#include <set>
#include <vector>

#include "Collation.h"

namespace android {
namespace stats_log_api_gen {

const char DEFAULT_CPP_NAMESPACE[] = "android,util";
const char DEFAULT_CPP_HEADER_IMPORT[] = "statslog.h";

const int API_LEVEL_CURRENT = 10000;
const int API_Q = 29;
const int API_R = 30;
const int API_S = 31;
const int API_S_V2 = 32;
const int API_T = 33;
const int API_U = 34;

const int JAVA_MODULE_REQUIRES_FLOAT = 0x01;
const int JAVA_MODULE_REQUIRES_ATTRIBUTION = 0x02;

const char ANNOTATION_CONSTANT_NAME_PREFIX[] = "ANNOTATION_ID_";
const char ANNOTATION_CONSTANT_NAME_VENDOR_PREFIX[] = "AnnotationId.";
const char ANNOTATION_CONSTANT_NAME_VENDOR_NATIVE_PREFIX[] = "AnnotationId::";

struct AnnotationStruct {
    string name;
    int minApiLevel;
    AnnotationStruct(string name, int minApiLevel)
        : name(std::move(name)), minApiLevel(minApiLevel){};
};

void build_non_chained_decl_map(const Atoms& atoms,
                                std::map<int, AtomDeclSet::const_iterator>* decl_map);

const map<AnnotationId, AnnotationStruct>& get_annotation_id_constants(const string& prefix);

string get_java_build_version_code(int apiLevel);

string get_restriction_category_str(int annotationValue);

string make_constant_name(const string& str);

const char* cpp_type_name(java_type_t type, bool isVendorAtomLogging = false);

const char* java_type_name(java_type_t type);

bool is_repeated_field(java_type_t type);

bool is_primitive_field(java_type_t type);

AtomDeclSet get_annotations(int argIndex, const FieldNumberToAtomDeclSet& fieldNumberToAtomDeclSet);

// Common Native helpers
void write_namespace(FILE* out, const string& cppNamespaces);

void write_closing_namespace(FILE* out, const string& cppNamespaces);

void write_native_atom_constants(FILE* out, const Atoms& atoms, const AtomDecl& attributionDecl,
                                 const string& methodName = "stats_write",
                                 bool isVendorAtomLogging = false);

void write_native_atom_enums(FILE* out, const Atoms& atoms);

void write_native_method_signature(FILE* out, const string& signaturePrefix,
                                   const vector<java_type_t>& signature,
                                   const AtomDecl& attributionDecl, const string& closer,
                                   bool isVendorAtomLogging = false);

void write_native_method_header(FILE* out, const string& methodName,
                                const SignatureInfoMap& signatureInfoMap,
                                const AtomDecl& attributionDecl, bool isVendorAtomLogging = false);

void write_native_header_preamble(FILE* out, const string& cppNamespace, bool includePull,
                                  bool isVendorAtomLogging = false);

void write_native_header_epilogue(FILE* out, const string& cppNamespace);

// Common Java helpers.
void write_java_atom_codes(FILE* out, const Atoms& atoms);

void write_java_enum_values(FILE* out, const Atoms& atoms);

int write_java_method_signature(FILE* out, const vector<java_type_t>& signature,
                                const AtomDecl& attributionDecl);

void write_java_usage(FILE* out, const string& method_name, const string& atom_code_name,
                      const AtomDecl& atom);

int write_java_non_chained_methods(FILE* out, const SignatureInfoMap& signatureInfoMap);

int write_java_work_source_methods(FILE* out, const SignatureInfoMap& signatureInfoMap);

class MFErrorCollector : public google::protobuf::compiler::MultiFileErrorCollector {
public:
    void AddError(const std::string& filename, int line, int column,
                  const std::string& message) override {
        fprintf(stderr, "[Error] %s:%d:%d - %s\n", filename.c_str(), line, column, message.c_str());
    }
};

int get_requires_api_level(int minApiLevel, const AtomDeclSet* atomDeclSet);

}  // namespace stats_log_api_gen
}  // namespace android

#endif  // ANDROID_STATS_LOG_API_GEN_UTILS_H
