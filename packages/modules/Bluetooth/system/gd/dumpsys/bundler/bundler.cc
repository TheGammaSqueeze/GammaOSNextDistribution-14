/*
 * Copyright 2020 The Android Open Source Project
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
#include "bundler.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <cassert>
#include <list>
#include <map>
#include <vector>

#include "bundler_schema_generated.h"
#include "flatbuffers/idl.h"
#include "flatbuffers/util.h"

using namespace bluetooth;
using namespace dumpsys;

struct Opts opts;

/**
 * Load a binary schema from persistent store using flatbuffer API.
 *
 * @param filename; Name of file to open and read.
 * @param binary_schema: Backing store for flatbuffer binary schema data.
 *
 * @return: True if operation successful, false otherwise.
 */
bool LoadBinarySchema(const char* filename, std::string* binary_schema) {
  assert(filename != nullptr);
  assert(binary_schema != nullptr);
  if (!flatbuffers::LoadFile(filename, helper::AsBinaryFile, binary_schema)) {
    fprintf(stderr, "Unable to open binary flatbuffer schema file:%s\n", filename);
    return false;
  };
  return true;
}

/**
 * Verify a binary schema using flatbuffer API.
 *
 * @param schema: Raw binary schema to verify
 *
 * @return: True if operation successful, false otherwise.
 */
bool VerifyBinarySchema(const std::vector<uint8_t>& raw_schema) {
  flatbuffers::Verifier verifier(raw_schema.data(), raw_schema.size());
  if (!reflection::VerifySchemaBuffer(verifier)) {
    return false;
  }
  return true;
}

/**
 * Bundle a set of binary flatbuffer schema into the bundler database.
 *
 * @param builder; Flatbuffer builder
 * @param filenames: Set of filenames to include in bundle
 * @param vector_map: Filename to filedata mapping
 *
 * @return: True if operation successful, false otherwise.
 */
bool CreateBinarySchemaBundle(
    flatbuffers::FlatBufferBuilder* builder,
    const std::vector<std::string>& filenames,
    std::vector<flatbuffers::Offset<BundledSchemaMap>>* vector_map,
    std::list<std::string>* bundled_names) {
  assert(builder != nullptr);
  assert(vector_map != nullptr);
  assert(bundled_names != nullptr);

  for (auto filename : filenames) {
    std::string string_schema;
    if (!LoadBinarySchema(filename.c_str(), &string_schema)) {
      fprintf(stderr, "Unable to load binary schema from filename:%s\n", filename.c_str());
      return false;
    }
    std::vector<uint8_t> raw_schema(string_schema.begin(), string_schema.end());
    if (!VerifyBinarySchema(raw_schema)) {
      fprintf(stderr, "Failed verification on binary schema filename:%s\n", filename.c_str());
      return false;
    }

    const reflection::Schema* schema = reflection::GetSchema(raw_schema.data());
    if (schema->root_table() == nullptr) {
      fprintf(stderr, "Unable to find root table for binary flatbuffer schema:%s\n", filename.c_str());
      return false;
    }

    bundled_names->push_back(schema->root_table()->name()->str());
    auto name = builder->CreateString(schema->root_table()->name()->str());
    auto data = builder->CreateVector<uint8_t>(raw_schema.data(), raw_schema.size());
    vector_map->push_back(CreateBundledSchemaMap(*builder, name, data));

    if (opts.verbose) {
      fprintf(stdout, "Bundled binary schema file:%s\n", schema->root_table()->name()->c_str());
    }
  }
  return true;
}

/**
 * Write generated header file containing the bundled binary schema
 * data and meta data
 *
 * @param data: Source file data.
 * @param data_len: length of data
 */
void WriteHeaderFile(FILE* fp, const uint8_t* data, size_t data_len) {
  assert(fp != nullptr);
  std::string delim(kDefaultNamespaceDelim);
  std::string ns_string(opts.ns_name);
  std::vector<std::string> namespaces;

  size_t start = 0;
  size_t end = ns_string.find(delim);
  while (end != std::string::npos) {
    namespaces.push_back(ns_string.substr(start, end - start));
    start = end + delim.size();
    end = ns_string.find(delim, start);
  }
  if (start != 0 && start != std::string::npos) {
    namespaces.push_back(ns_string.substr(start));
  } else if (!ns_string.empty()) {
    namespaces.push_back(ns_string);
  }

  std::string namespace_prefix;
  for (const auto& name : namespaces) namespace_prefix += (name + '_');

  fprintf(
      fp,
      "// Generated file by bluetooth_flatbuffer bundler\n"
      "#include <string>\n");
  for_each(
      namespaces.begin(), namespaces.end(), [fp](const std::string& s) { fprintf(fp, "namespace %s {\n", s.c_str()); });
  fprintf(fp, "extern const std::string& GetBundledSchemaData();\n");
  fprintf(fp, "const unsigned char %sdata_[%zu] = {\n", namespace_prefix.c_str(), data_len);

  for (size_t i = 0; i < data_len; i++) {
    fprintf(fp, " 0x%02x", data[i]);
    if (i != data_len - 1) {
      fprintf(fp, ",");
    }
    if ((i + 1) % 16 == 0) {
      fprintf(fp, "\n");
    }
  }
  fprintf(fp, " };\n");
  fprintf(
      fp,
      "const std::string %sstring_data_(%sdata_, %sdata_ + sizeof(%sdata_));\n",
      namespace_prefix.c_str(),
      namespace_prefix.c_str(),
      namespace_prefix.c_str(),
      namespace_prefix.c_str());
  fprintf(fp, "const std::string& GetBundledSchemaData() { return %sstring_data_; }\n", namespace_prefix.c_str());

  for_each(namespaces.crbegin(), namespaces.crend(), [fp](const std::string& s) {
    fprintf(fp, "}  // namespace %s\n", s.c_str());
  });
}

int ReadBundledSchema() {
  const char* filename = opts.filename;
  assert(filename != nullptr);

  std::string flatfile_data;
  if (!flatbuffers::LoadFile(filename, helper::AsBinaryFile, &flatfile_data)) {
    fprintf(stderr, "Unable to load schema data file:%s\n", filename);
    return -5;
  }

  auto bundle_schema = flatbuffers::GetRoot<BundledSchema>(flatfile_data.c_str());
  const flatbuffers::Vector<flatbuffers::Offset<BundledSchemaMap>>* map = bundle_schema->map();

  fprintf(stdout, "Bundle schema title:%s\n", bundle_schema->title()->c_str());
  fprintf(stdout, "Bundle schema root_name:%s\n", bundle_schema->root_name()->c_str());
  int cnt = 0;
  for (auto it = map->cbegin(); it != map->cend(); ++it, cnt++) {
    fprintf(stdout, "   %d name:%s schema:%s\n", cnt, it->name()->c_str(), "schema");
  }
  return EXIT_SUCCESS;
}

int WriteBundledSchema() {
  const char* filename = opts.filename;
  assert(filename != nullptr);

  const char* main_root_name = opts.main_root_name;
  if (main_root_name == nullptr) {
    fprintf(stderr, "Must specify the name of the main root name for this bundle\n");
    return EXIT_FAILURE;
  }

  std::vector<std::string> bfbs_filenames;
  for (int i = 0; i < opts.arg.c; i++) {
    bfbs_filenames.push_back(std::string(opts.arg.v[i]));
  }
  if (bfbs_filenames.empty()) {
    fprintf(stderr, "No bfbs files are specified to bundle\n");
    return EXIT_FAILURE;
  }

  flatbuffers::FlatBufferBuilder builder(1024);

  std::list<std::string> bundled_names;
  std::vector<flatbuffers::Offset<BundledSchemaMap>> vector_map;
  if (!CreateBinarySchemaBundle(&builder, bfbs_filenames, &vector_map, &bundled_names)) {
    fprintf(stderr, "Unable to bundle schema bfbs files\n");
    return EXIT_FAILURE;
  }

  if (std::find(bundled_names.begin(), bundled_names.end(), main_root_name) == bundled_names.end()) {
    fprintf(stderr, "The main root name must match one of the bundled schema names\n");
    fprintf(stderr, "  main root name:%s\n", main_root_name);
    for (auto name : bundled_names) {
      fprintf(stderr, "  bundled schema name:%s\n", name.c_str());
    }
    return EXIT_FAILURE;
  }

  const char* title = opts.title;
  auto schema_offset = CreateBundledSchemaDirect(builder, title, main_root_name, &vector_map);
  builder.Finish(schema_offset);

  std::string final_filename(opts.gen);
  final_filename.append("/");
  final_filename.append(filename);
  if (!flatbuffers::SaveFile(
          final_filename.c_str(), (const char*)builder.GetBufferPointer(), builder.GetSize(), helper::AsBinaryFile)) {
    fprintf(stderr, "Unable to save file:%s\n", final_filename.c_str());
    return EXIT_FAILURE;
  }

  std::string header(opts.gen);
  header += ("/" + std::string(opts.filename) + ".cc");
  FILE* fp = fopen(header.c_str(), "w+");
  if (fp == nullptr) {
    fprintf(stdout, "Unable to open for writing header file:%s\n", header.c_str());
    return EXIT_FAILURE;
  }
  WriteHeaderFile(fp, builder.GetBufferPointer(), builder.GetSize());
  fclose(fp);
  return EXIT_SUCCESS;
}

int Usage(int argc, char** argv) {
  fprintf(
      stderr,
      "Usage: %s [-r | -w] [-f <filename>] [-g <gen_out_path>] [-n <namespace> ] [-v] -m <main_root_name> <file.bfbs "
      "...>\n",
      argv[0]);
  fprintf(stderr, " -r|-w : Read or write a dumpsys file\n");
  fprintf(stderr, " -f : Filename bundled schema to read or write (default:%s)\n", kDefaultBundleDataFile);
  fprintf(stderr, " -g : Generated file output path\n");
  fprintf(stderr, " -n : Namespace to embed binary output bundle data source\n");
  fprintf(stderr, " -m : Name of the main root of this bundle\n");
  fprintf(stderr, " -v : Verbose printing mode\n");
  return EXIT_FAILURE;
}

void ParseArgs(int argc, char** argv) {
  int opt;
  int parsed_cnt = 1;
  while ((opt = getopt(argc, argv, "f:g:m:n:rt:vw")) != -1) {
    parsed_cnt++;
    switch (opt) {
      case 'f':
        opts.filename = optarg;
        parsed_cnt++;
        break;
      case 'g':
        opts.gen = optarg;
        parsed_cnt++;
        break;
      case 'm':
        opts.main_root_name = optarg;
        parsed_cnt++;
        break;
      case 'n':
        opts.ns_name = optarg;
        parsed_cnt++;
        break;
      case 'r':
        opts.read = true;
        break;
      case 'w':
        opts.write = true;
        break;
      case 't':
        opts.title = optarg;
        parsed_cnt++;
        break;
      case 'v':
        opts.verbose = true;
        break;
      default:
        exit(Usage(argc, argv));
        break;
    }
  }
  opts.arg.c = argc - parsed_cnt;
  opts.arg.v = &argv[parsed_cnt];
}
