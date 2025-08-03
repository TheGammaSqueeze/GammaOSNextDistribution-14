// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2022-2023 Google LLC
//
// Licensed under the Apache License v2.0 with LLVM Exceptions (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     https://llvm.org/LICENSE.txt
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Author: Siddharth Nayyar

#include "post_processing.h"

#include <algorithm>
#include <cstddef>
#include <iostream>
#include <map>
#include <ostream>
#include <regex>
#include <sstream>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

namespace stg {

std::vector<std::string> SummariseCRCChanges(
    const std::vector<std::string>& report, size_t limit) {
  const std::regex symbol_changed_re("^.* symbol .* changed$");
  const std::regex crc_re("^  CRC changed from [^ ]* to [^ ]*$");
  const std::regex empty_re("^$");
  const std::regex section_re("^[^ \\n].*$");
  const std::regex symbol_re("^.* symbol .*$");

  std::vector<std::string> new_report;
  std::vector<std::pair<std::string, std::string>> pending;

  auto emit_pending = [&]() {
    const size_t crc_only_changes = pending.size();
    for (size_t ix = 0; ix < std::min(crc_only_changes, limit); ++ix) {
      new_report.push_back(pending[ix].first);
      new_report.push_back(pending[ix].second);
      new_report.emplace_back();
    }
    if (crc_only_changes > limit) {
      std::ostringstream os;
      os << "... " << crc_only_changes - limit << " omitted; "
         << crc_only_changes << " symbols have only CRC changes";
      new_report.push_back(os.str());
      new_report.emplace_back();
    }
    pending.clear();
  };

  for (size_t ix = 0; ix < report.size(); ++ix) {
    if (std::regex_match(report[ix], section_re) &&
        !std::regex_match(report[ix], symbol_re)) {
      emit_pending();
      new_report.push_back(report[ix]);
    } else if (ix + 2 < report.size() &&
               std::regex_match(report[ix], symbol_changed_re) &&
               std::regex_match(report[ix + 1], crc_re) &&
               std::regex_match(report[ix + 2], empty_re)) {
      pending.emplace_back(report[ix], report[ix + 1]);
      // consumed 3 lines in total => 2 extra lines
      ix += 2;
    } else {
      new_report.push_back(report[ix]);
    }
  }

  emit_pending();
  return new_report;
}

std::vector<std::string> SummariseOffsetChanges(
    const std::vector<std::string>& report) {
  const std::regex re1("^( *)member ('.*') changed$");
  const std::regex re2("^( *)offset changed from (\\d+) to (\\d+)$");
  const std::regex re3("^( *).*$");

  std::smatch match1;
  std::smatch match2;
  std::smatch match3;
  size_t indent = 0;
  int64_t offset = 0;
  std::vector<std::string> vars;
  std::vector<std::string> new_report;

  auto emit_pending = [&]() {
    if (vars.empty()) {
      return;
    }
    std::ostringstream line1;
    line1 << std::string(indent, ' ');
    if (vars.size() == 1) {
      line1 << "member " << vars.front() << " changed";
    } else {
      line1 << vars.size() << " members (" << vars.front() << " .. "
            << vars.back() << ") changed";
    }
    new_report.push_back(line1.str());
    std::ostringstream line2;
    line2 << std::string(indent, ' ') << "  offset changed by " << offset;
    new_report.push_back(line2.str());
    vars.clear();
  };

  for (size_t ix = 0; ix < report.size(); ++ix) {
    if (ix + 2 < report.size() && std::regex_match(report[ix], match1, re1) &&
        std::regex_match(report[ix + 1], match2, re2) &&
        std::regex_match(report[ix + 2], match3, re3)) {
      const size_t indent1 = match1[1].length();
      const size_t indent2 = match2[1].length();
      const size_t indent3 = match3[1].length();
      if (indent1 + 2 == indent2 && indent1 >= indent3) {
        const auto new_indent = indent1;
        int64_t new_offset =
            std::stoll(match2[3].str()) - std::stoll(match2[2].str());
        if (new_indent != indent || new_offset != offset) {
          emit_pending();
          indent = new_indent;
          offset = new_offset;
        }
        vars.push_back(match1[2]);
        // consumed 2 lines in total => 1 extra line
        ++ix;
        continue;
      }
    }
    emit_pending();
    new_report.push_back(report[ix]);
  }

  emit_pending();
  return new_report;
}

std::vector<std::string> GroupRemovedAddedSymbols(
    const std::vector<std::string>& report) {
  const std::regex symbol_re("^(.*) symbol (.*) was (added|removed)$");
  const std::regex empty_re("^$");

  std::vector<std::string> new_report;
  std::unordered_map<std::string,
      std::map<std::string, std::vector<std::string>>> pending;

  auto emit_pending = [&]() {
    for (const auto& which : {"removed", "added"}) {
      auto& pending_kinds = pending[which];
      for (auto& [kind, pending_symbols] : pending_kinds) {
        if (!pending_symbols.empty()) {
          std::ostringstream os;
          os << pending_symbols.size() << ' ' << kind << " symbol(s) " << which;
          new_report.push_back(os.str());
          for (const auto& symbol : std::exchange(pending_symbols, {})) {
            new_report.push_back("  " + symbol);
          }
          new_report.emplace_back();
        }
      }
    }
  };

  for (size_t ix = 0; ix < report.size(); ++ix) {
    std::smatch match;
    if (ix + 1 < report.size() &&
        std::regex_match(report[ix], match, symbol_re) &&
        std::regex_match(report[ix + 1], empty_re)) {
      pending[match[3].str()][match[1].str()].push_back(match[2].str());
      // consumed 2 lines in total => 1 extra line (there is always an empty
      // line after symbol added/removed line)
      ++ix;
    } else {
      emit_pending();
      new_report.push_back(report[ix]);
    }
  }

  emit_pending();
  return new_report;
}

std::vector<std::string> PostProcess(const std::vector<std::string>& report,
                                     size_t max_crc_only_changes) {
  std::vector<std::string> new_report;
  new_report = SummariseCRCChanges(report, max_crc_only_changes);
  new_report = GroupRemovedAddedSymbols(new_report);
  new_report = SummariseOffsetChanges(new_report);
  return new_report;
}

}  // namespace stg
