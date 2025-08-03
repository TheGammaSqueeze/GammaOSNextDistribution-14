// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <fstream>
#include <iostream>
#include <map>
#include <string>
#include <vector>

#ifdef __ANDROID__
#include <benchmark.pb.h>
#else
#include "schema/benchmark.pb.h"
#endif

#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <google/protobuf/text_format.h>

struct Syscall {
  std::string name;
  std::vector<std::string> arguments;
  std::string return_value;
};

// Reads lines from the provided file with strace output. Returns a list of lines
std::vector<std::string> ReadLines(const std::string& file_path) {
  std::vector<std::string> lines;

  std::string line;
  std::ifstream input(file_path);
  while (std::getline(input, line)) {
    lines.push_back(line);
  }
  input.close();

  return lines;
}

// Processes the given line into syscall name, arguments and return value
Syscall ProcessLine(const std::string& line) {
  Syscall syscall;

  syscall.name = line.substr(0, line.find('('));
  std::string raw_arguments = line.substr(line.find('(') + 1, line.find(')') - line.find('(') - 1);
  syscall.return_value = line.substr(line.find(')'));
  syscall.return_value = syscall.return_value.substr(syscall.return_value.find("= ") + 2);

  size_t next = 0;
  size_t last = 0;
  while ((next = raw_arguments.find(", ", last)) != std::string::npos) {
    std::string part = raw_arguments.substr(last, next - last);
    last = next + 2;
    if (part.size() != 0) syscall.arguments.push_back(part);
  }
  std::string part = raw_arguments.substr(last);
  if (part.size() != 0) syscall.arguments.push_back(part);

  return syscall;
}

// Splits lines by pid. Returns a map where pid maps to a list of lines
std::map<int, std::vector<std::string>> SplitByPid(const std::vector<std::string>& lines) {
  std::map<int, std::vector<std::string>> lines_by_pid;

  for (const auto& line : lines) {
    int pid = strtoll(line.substr(0, line.find(' ')).c_str(), nullptr, 10);
    lines_by_pid[pid].push_back(line);
  }

  return lines_by_pid;
}

// Goes through all the lines for each pid, merges lines with unfinished and resumed tags, then
// calls ProcessLine on each of those merged lines. Returns a map where pid maps to a list of
// processed lines/syscalls
std::map<int, std::vector<Syscall>> ProcessLines(
    const std::map<int, std::vector<std::string>>& lines_by_pid) {
  std::map<int, std::vector<Syscall>> processed_syscalls_by_pid;

  for (const auto& [pid, lines] : lines_by_pid) {
    for (std::size_t i = 0; i < lines.size(); ++i) {
      auto line = lines[i];

      // If only the resumed part of the syscall was found, ignore it
      if (line.find("resumed>") != std::string::npos) continue;

      // If the syscall is detached, ignore it
      if (line.find("<detached ...>") != std::string::npos) continue;

      // If the line contains "unfinished", concatenate it with the next line, which should contain
      // "resumed"
      if (line.find("<unfinished ...>") != std::string::npos) {
        // Remove the "unfinished" tag
        line = line.substr(0, line.find("<unfinished ...>"));

        // If the next line does not exist, ignore the syscall altogether
        if (i + 1 >= lines.size()) continue;

        auto second_line = lines[++i];

        // Remove the "resumed" tag
        second_line = second_line.substr(second_line.find("resumed>") + std::strlen("resumed>"));
        // Concatenate both lines
        line += second_line;
      }

      // Remove the pid
      line = line.substr(line.find("  ") + 2);

      // If the line starts with "---" or "+++", ignore it
      if (line.length() >= 3 && (line.substr(0, 3) == "---" || line.substr(0, 3) == "+++"))
        continue;

      auto processed_syscall = ProcessLine(line);
      processed_syscalls_by_pid[pid].push_back(processed_syscall);
    }
  }

  return processed_syscalls_by_pid;
}

int main(int argc, char** argv) {
  if (argc != 3) {
    std::cerr << "Invalid number of arguments.\n";
    exit(EXIT_FAILURE);
  }

  auto raw_lines = ReadLines(argv[1]);
  auto raw_lines_by_pid = SplitByPid(raw_lines);
  auto processed_syscalls_by_pid = ProcessLines(raw_lines_by_pid);

  std::string absolute_path = argv[2];

  // Initialize .ditto file
  auto benchmark = std::make_unique<dittosuiteproto::Benchmark>();
  auto main_instruction_set = benchmark->mutable_main()->mutable_instruction_set();
  benchmark->mutable_global()->set_absolute_path(absolute_path);

  // Iterate over each pid and its processed lines. Start creating instructions after first openat()
  // syscall, whose file name includes the provided absolute path, is found
  for (const auto& [pid, syscalls] : processed_syscalls_by_pid) {
    std::map<int, std::unique_ptr<dittosuiteproto::InstructionSet>> instruction_set_by_fd;
    for (const auto& syscall : syscalls) {
      if (syscall.name == "openat" &&
          syscall.arguments[1].find(absolute_path) != std::string::npos) {
        // Remove absolute_path
        std::string path_name = syscall.arguments[1].substr(absolute_path.size() + 2);
        // Remove quotes at the end
        path_name.pop_back();

        // If the return value is -1, ignore it
        if (syscall.return_value.find("-1") != std::string::npos) continue;

        int fd = strtoll(syscall.return_value.c_str(), nullptr, 10);

        // Create .ditto instruction set for this fd with open file instruction
        instruction_set_by_fd[fd] = std::make_unique<dittosuiteproto::InstructionSet>();
        auto instruction = instruction_set_by_fd[fd]->add_instructions()->mutable_open_file();
        instruction->set_path_name(path_name);
        instruction->set_output_fd("fd");
      } else if (syscall.name == "pread64") {
        int fd = strtoll(syscall.arguments[0].c_str(), nullptr, 10);

        if (syscall.arguments.size() != 4) continue;
        if (instruction_set_by_fd.find(fd) == instruction_set_by_fd.end()) continue;

        int64_t size = strtoll(syscall.arguments[2].c_str(), nullptr, 10);
        int64_t offset = strtoll(syscall.arguments[3].c_str(), nullptr, 10);

        // Create .ditto read file instruction
        auto instruction = instruction_set_by_fd[fd]->add_instructions()->mutable_read_file();
        instruction->set_input_fd("fd");
        instruction->set_size(size);
        instruction->set_block_size(size);
        instruction->set_starting_offset(offset);
      } else if (syscall.name == "pwrite64") {
        int fd = strtoll(syscall.arguments[0].c_str(), nullptr, 10);

        if (syscall.arguments.size() != 4) continue;
        if (instruction_set_by_fd.find(fd) == instruction_set_by_fd.end()) continue;

        int64_t size = strtoll(syscall.arguments[2].c_str(), nullptr, 10);
        int64_t offset = strtoll(syscall.arguments[3].c_str(), nullptr, 10);

        // Create .ditto write file instruction
        auto instruction = instruction_set_by_fd[fd]->add_instructions()->mutable_write_file();
        instruction->set_input_fd("fd");
        instruction->set_size(size);
        instruction->set_block_size(size);
        instruction->set_starting_offset(offset);
      } else if (syscall.name == "close") {
        int fd = strtoll(syscall.arguments[0].c_str(), nullptr, 10);

        if (instruction_set_by_fd.find(fd) == instruction_set_by_fd.end()) continue;

        // Create .ditto close file instruction
        auto instruction = instruction_set_by_fd[fd]->add_instructions()->mutable_close_file();
        instruction->set_input_fd("fd");

        // Add the instruction set for this fd to the main instruction set
        main_instruction_set->add_instructions()->set_allocated_instruction_set(
            instruction_set_by_fd[fd].release());
        instruction_set_by_fd.erase(instruction_set_by_fd.find(fd));
      }
    }
  }

  auto output = std::make_unique<google::protobuf::io::OstreamOutputStream>(&std::cout);
  google::protobuf::TextFormat::Print(*benchmark, output.get());
  return 0;
}
