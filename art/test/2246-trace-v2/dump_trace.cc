/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <stdio.h>

#include <map>
#include <memory>

#include "base/leb128.h"
#include "base/os.h"
#include "base/unix_file/fd_file.h"
#include "jni.h"

namespace art {
namespace {

static const int kMagicValue = 0x574f4c53;
static const int kVersionDualClock = 0xf5;
static const int kThreadInfo = 0;
static const int kMethodInfo = 1;
static const int kTraceEntries = 2;
static const int kTraceActionBits = 2;
static const int kSummary = 3;

int ReadNumber(int num_bytes, uint8_t* header) {
  int number = 0;
  for (int i = 0; i < num_bytes; i++) {
    number += header[i] << (i * 8);
  }
  return number;
}

bool ProcessThreadOrMethodInfo(std::unique_ptr<File>& file, std::map<int, std::string>& name_map) {
  uint8_t header[6];
  if (!file->ReadFully(&header, sizeof(header))) {
    printf("Couldn't read header\n");
    return false;
  }
  int id = ReadNumber(4, header);
  int length = ReadNumber(2, header + 4);

  char* name = new char[length];
  if (!file->ReadFully(name, length)) {
    delete[] name;
    return false;
  }
  std::string str(name, length);
  std::replace(str.begin(), str.end(), '\t', ' ');
  name_map.emplace(id, str);
  delete[] name;
  return true;
}

void print_trace_entry(const std::string& thread_name,
                       const std::string& method_name,
                       int* current_depth,
                       int event_type) {
  std::string entry;
  for (int i = 0; i < *current_depth; i++) {
    entry.push_back('.');
  }
  if (event_type == 0) {
    *current_depth += 1;
    entry.append(".>> ");
  } else if (event_type == 1) {
    *current_depth -= 1;
    entry.append("<< ");
  } else if (event_type == 2) {
    *current_depth -= 1;
    entry.append("<<E ");
  } else {
    entry.append("?? ");
  }
  entry.append(thread_name);
  entry.append(" ");
  entry.append(method_name);
  printf("%s", entry.c_str());
}

bool ProcessTraceEntries(std::unique_ptr<File>& file,
                         std::map<int, int>& current_depth_map,
                         std::map<int, std::string>& thread_map,
                         std::map<int, std::string>& method_map,
                         bool is_dual_clock,
                         const char* thread_name_filter) {
  uint8_t header[20];
  int header_size = is_dual_clock ? 20 : 16;
  if (!file->ReadFully(header, header_size)) {
    return false;
  }

  uint32_t thread_id = ReadNumber(4, header);
  uint32_t method_value = ReadNumber(4, header + 4);
  int offset = 8;
  if (is_dual_clock) {
    // Read timestamp.
    ReadNumber(4, header + offset);
    offset += 4;
  }
  // Read timestamp.
  ReadNumber(4, header + offset);
  offset += 4;
  int num_records = ReadNumber(2, header + offset);
  offset += 2;
  int total_size = ReadNumber(2, header + offset);
  uint8_t* buffer = new uint8_t[total_size];
  if (!file->ReadFully(buffer, total_size)) {
    delete[] buffer;
    return false;
  }

  const uint8_t* current_buffer_ptr = buffer;
  int32_t method_id = method_value >> kTraceActionBits;
  uint8_t event_type = method_value & 0x3;
  int current_depth = 0;
  if (current_depth_map.find(thread_id) != current_depth_map.end()) {
    // Get the current call stack depth. If it is the first method we are seeing on this thread
    // then this map wouldn't haven an entry we start with the depth of 0.
    current_depth = current_depth_map[thread_id];
  }
  std::string thread_name = thread_map[thread_id];
  bool print_thread_events = (thread_name.compare(thread_name_filter) == 0);
  if (method_map.find(method_id) == method_map.end()) {
    LOG(FATAL) << "No entry for init method " << method_id;
  }
  if (print_thread_events) {
    print_trace_entry(thread_name, method_map[method_id], &current_depth, event_type);
  }
  for (int i = 0; i < num_records; i++) {
    int32_t diff = 0;
    bool success = DecodeSignedLeb128Checked(&current_buffer_ptr, buffer + total_size - 1, &diff);
    if (!success) {
      LOG(FATAL) << "Reading past the buffer???";
    }
    int32_t curr_method_value = method_value + diff;
    method_id = curr_method_value >> kTraceActionBits;
    event_type = curr_method_value & 0x3;
    if (print_thread_events) {
      print_trace_entry(thread_name, method_map[method_id], &current_depth, event_type);
    }
    // Read timestamps
    DecodeUnsignedLeb128(&current_buffer_ptr);
    if (is_dual_clock) {
      DecodeUnsignedLeb128(&current_buffer_ptr);
    }
  }
  current_depth_map[thread_id] = current_depth;
  return true;
}

extern "C" JNIEXPORT void JNICALL Java_Main_dumpTrace(JNIEnv* env,
                                                      jclass,
                                                      jstring fileName,
                                                      jstring threadName) {
  const char* file_name = env->GetStringUTFChars(fileName, nullptr);
  const char* thread_name = env->GetStringUTFChars(threadName, nullptr);
  std::map<int, std::string> thread_map;
  std::map<int, std::string> method_map;
  std::map<int, int> current_depth_map;

  std::unique_ptr<File> file(OS::OpenFileForReading(file_name));
  if (file == nullptr) {
    printf("Couldn't open file\n");
    return;
  }

  uint8_t header[32];
  const bool success = file->ReadFully(&header, sizeof(header));
  if (!success) {
    printf("Couldn't read header\n");
    return;
  }
  int magic_value = ReadNumber(4, header);
  if (magic_value != kMagicValue) {
    printf("Incorrect magic value\n");
    return;
  }
  int version = ReadNumber(2, header + 4);
  if (success) {
    printf("version=%0x\n", version);
  }

  bool is_dual_clock = (version == kVersionDualClock);
  bool has_entries = true;
  while (has_entries) {
    uint8_t entry_header;
    if (!file->ReadFully(&entry_header, sizeof(entry_header))) {
      break;
    }
    switch (entry_header) {
      case kThreadInfo:
        if (!ProcessThreadOrMethodInfo(file, thread_map)) {
          has_entries = false;
        }
        break;
      case kMethodInfo:
        if (!ProcessThreadOrMethodInfo(file, method_map)) {
          has_entries = false;
        }
        break;
      case kTraceEntries:
        ProcessTraceEntries(
            file, current_depth_map, thread_map, method_map, is_dual_clock, thread_name);
        break;
      case kSummary:
        has_entries = false;
        break;
      default:
        printf("Invalid Header %d\n", entry_header);
        has_entries = false;
        break;
    }
  }

  env->ReleaseStringUTFChars(fileName, file_name);
  env->ReleaseStringUTFChars(threadName, thread_name);
}

}  // namespace
}  // namespace art
