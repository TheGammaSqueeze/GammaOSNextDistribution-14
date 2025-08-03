/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "gtest/gtest.h"

#include "berberis/tiny_loader/tiny_loader.h"

#include <string>

#include <sys/user.h>

#include "berberis/base/file.h"
#include "berberis/base/page_size.h"
#include "berberis/base/stringprintf.h"

namespace {

const constexpr char* kTestSymbolName = "tiny_symbol";
const constexpr char* kTestLibInvalidElfClassName = "libtinytest_invalid_elf_class.so";
const constexpr char* kTestLibGnuName = "libtinytest.so";
const constexpr char* kTestLibSysvName = "libtinytest_sysv.so";
const constexpr char* kTestExecutableName = "tiny_static_executable";

#if defined(__LP64__)
constexpr uintptr_t kStaticExecutableEntryPoint = 0x1ce00;
constexpr const char* kTestFilesDir = "/tiny_loader/tests/files/64/";
#else
constexpr uintptr_t kStaticExecutableEntryPoint = 0x410f30;
constexpr const char* kTestFilesDir = "/tiny_loader/tests/files/32/";
#endif

void AssertLoadedElfFilesEqual(const LoadedElfFile& actual, const LoadedElfFile& expected) {
  ASSERT_EQ(actual.e_type(), expected.e_type());
  ASSERT_EQ(actual.base_addr(), expected.base_addr());
  ASSERT_EQ(actual.load_bias(), expected.load_bias());
  ASSERT_EQ(actual.entry_point(), expected.entry_point());
  ASSERT_EQ(actual.phdr_table(), expected.phdr_table());
  ASSERT_EQ(actual.phdr_count(), expected.phdr_count());
}

bool GetTestElfFilepath(const char* name, std::string* real_path, std::string* error_msg) {
  std::string path = berberis::GetExecutableDirectory();
  path += kTestFilesDir;

  std::string out_path;
  if (!berberis::Realpath(path.c_str(), &out_path)) {
    *error_msg = berberis::StringPrintf("Failed to get realpath for \"%s\"", path.c_str());
    return false;
  }

  out_path += "/";
  out_path += name;

  if (!berberis::Realpath(out_path, real_path)) {
    *error_msg = berberis::StringPrintf("\"%s\": does not exist", out_path.c_str());
    return false;
  }

  return true;
}

void TestLoadLibrary(const char* test_library_name) {
  LoadedElfFile loaded_elf_file;
  std::string error_msg;
  std::string elf_filepath;
  ASSERT_TRUE(GetTestElfFilepath(test_library_name, &elf_filepath, &error_msg)) << error_msg;
  ASSERT_TRUE(TinyLoader::LoadFromFile(elf_filepath.c_str(), &loaded_elf_file, &error_msg))
      << error_msg;

  // Get AT_BASE -> note that even though linker does not use
  // AT_BASE this is needed for dynamic vdso and passed to the linker
  // as AT_SYSINFO_EHDR
  void* base_addr = loaded_elf_file.base_addr();
  ElfAddr load_bias = loaded_elf_file.load_bias();
  ASSERT_TRUE(base_addr != nullptr);
  ASSERT_TRUE(reinterpret_cast<void*>(load_bias) == base_addr);
  ASSERT_TRUE(loaded_elf_file.phdr_table() != nullptr);
  ASSERT_EQ(loaded_elf_file.phdr_count(), 9U);
  void* symbol_addr = loaded_elf_file.FindSymbol(kTestSymbolName);
  ASSERT_TRUE(symbol_addr != nullptr);
  ASSERT_TRUE(static_cast<uint8_t*>(symbol_addr) > static_cast<uint8_t*>(base_addr));

  std::vector<std::pair<std::string, void*>> symbols;
  loaded_elf_file.ForEachSymbol([&symbols](const char* name, void* address, const ElfSym* s) {
    if (s->st_size != 0) {
      symbols.emplace_back(std::string(name), address);
    }
  });

  ASSERT_EQ(1U, symbols.size());
  ASSERT_EQ(kTestSymbolName, symbols.begin()->first);
  ASSERT_EQ(symbol_addr, symbols.begin()->second);

  // AT_ENTRY for this file is 0
  ASSERT_TRUE(loaded_elf_file.entry_point() == nullptr);

  ASSERT_EQ(ET_DYN, loaded_elf_file.e_type());

  ASSERT_NE(nullptr, loaded_elf_file.dynamic());

  // The second part of the test - to Load this file from already mapped memory.
  // Check that resulted loaded_elf_file is effectively the same
  LoadedElfFile memory_elf_file;
  ASSERT_TRUE(TinyLoader::LoadFromMemory(
      elf_filepath.c_str(), base_addr, berberis::kPageSize, &memory_elf_file, &error_msg))
      << error_msg;
  AssertLoadedElfFilesEqual(memory_elf_file, loaded_elf_file);
  void* memory_symbol_addr = memory_elf_file.FindSymbol(kTestSymbolName);
  ASSERT_EQ(symbol_addr, memory_symbol_addr);
}

}  // namespace

TEST(tiny_loader, library_gnu_hash) {
  TestLoadLibrary(kTestLibGnuName);
}

TEST(tiny_loader, library_sysv_hash) {
  TestLoadLibrary(kTestLibSysvName);
}

TEST(tiny_loader, library_invalid_elf_class) {
  LoadedElfFile loaded_elf_file;
  std::string error_msg;
  std::string elf_filepath;
  ASSERT_TRUE(GetTestElfFilepath(kTestLibInvalidElfClassName, &elf_filepath, &error_msg))
      << error_msg;
  ASSERT_FALSE(TinyLoader::LoadFromFile(elf_filepath.c_str(), &loaded_elf_file, &error_msg));
#if defined(__LP64__)
  std::string expected_error_msg =
      "\"" + elf_filepath + "\" ELFCLASS32 is not supported, expected ELFCLASS64.";
#else
  std::string expected_error_msg =
      "\"" + elf_filepath + "\" ELFCLASS64 is not supported, expected ELFCLASS32.";
#endif
  ASSERT_EQ(expected_error_msg, error_msg);
}

TEST(tiny_loader, binary) {
  LoadedElfFile loaded_elf_file;
  std::string error_msg;

  std::string elf_filepath;
  ASSERT_TRUE(GetTestElfFilepath(kTestExecutableName, &elf_filepath, &error_msg)) << error_msg;
  ASSERT_TRUE(TinyLoader::LoadFromFile(elf_filepath.c_str(), &loaded_elf_file, &error_msg))
      << error_msg;

  ASSERT_EQ(reinterpret_cast<void*>(kStaticExecutableEntryPoint), loaded_elf_file.entry_point());
  ASSERT_EQ(ET_EXEC, loaded_elf_file.e_type());

  ASSERT_NE(nullptr, loaded_elf_file.phdr_table());

  ASSERT_EQ(nullptr, loaded_elf_file.dynamic());

  // The second part of the test - to Load this file from already mapped memory.
  // Check that resulted loaded_elf_file is effectively the same
  LoadedElfFile memory_elf_file;
  ASSERT_TRUE(TinyLoader::LoadFromMemory(elf_filepath.c_str(),
                                         loaded_elf_file.base_addr(),
                                         berberis::kPageSize,
                                         &memory_elf_file,
                                         &error_msg))
      << error_msg;
  AssertLoadedElfFilesEqual(memory_elf_file, loaded_elf_file);
}
