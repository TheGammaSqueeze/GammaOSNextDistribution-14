// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2020-2022 Google LLC
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
// Author: Giuliano Procida
// Author: Siddharth Nayyar

#include "reporting.h"

#include <array>
#include <cstddef>
#include <deque>
#include <optional>
#include <ostream>
#include <sstream>
#include <string>
#include <string_view>
#include <type_traits>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#include "comparison.h"
#include "error.h"
#include "fidelity.h"
#include "graph.h"
#include "post_processing.h"

namespace stg {
namespace reporting {

struct FormatDescriptor {
  std::string_view name;
  OutputFormat value;
};

static constexpr std::array<FormatDescriptor, 5> kFormats{{
  {"plain", OutputFormat::PLAIN},
  {"flat",  OutputFormat::FLAT },
  {"small", OutputFormat::SMALL},
  {"short", OutputFormat::SHORT},
  {"viz",   OutputFormat::VIZ  },
}};

std::optional<OutputFormat> ParseOutputFormat(std::string_view format) {
  for (const auto& [name, value] : kFormats) {
    if (name == format) {
      return {value};
    }
  }
  return {};
}

std::ostream& operator<<(std::ostream& os, OutputFormatUsage) {
  os << "output formats:";
  for (const auto& [name, _] : kFormats) {
    os << ' ' << name;
  }
  return os << '\n';
}

namespace {

std::string GetResolvedDescription(
    const Graph& graph, NameCache& names, Id id) {
  std::ostringstream os;
  const auto [resolved, typedefs] = ResolveTypedefs(graph, id);
  for (const auto& td : typedefs) {
    os << '\'' << td << "' = ";
  }
  os << '\'' << Describe(graph, names)(resolved) << '\''
     << DescribeExtra(graph)(resolved);
  return os.str();
}

// Prints a comparison to the given output stream. The comparison is printed
// with the given indentation and prefixed with the given prefix if it is not
// empty.
//
// It returns true if the comparison denotes addition or removal of a node.
bool PrintComparison(const Reporting& reporting, const Comparison& comparison,
                     std::ostream& os, size_t indent,
                     const std::string& prefix) {
  os << std::string(indent, ' ');
  if (!prefix.empty()) {
    os << prefix << ' ';
  }
  const auto id1 = comparison.first;
  const auto id2 = comparison.second;

  Check(id1.has_value() || id2.has_value())
      << "internal error: Attempt to print comparison with nothing to compare.";

  if (!id2) {
    os << DescribeKind(reporting.graph)(*id1) << " '"
       << Describe(reporting.graph, reporting.names)(*id1)
       << "'"
       << DescribeExtra(reporting.graph)(*id1)
       << " was removed\n";
    return true;
  }
  if (!id1) {
    os << DescribeKind(reporting.graph)(*id2) << " '"
       << Describe(reporting.graph, reporting.names)(*id2)
       << "'"
       << DescribeExtra(reporting.graph)(*id2)
       << " was added\n";
    return true;
  }

  const auto description1 =
      GetResolvedDescription(reporting.graph, reporting.names, *id1);
  const auto description2 =
      GetResolvedDescription(reporting.graph, reporting.names, *id2);
  os << DescribeKind(reporting.graph)(*id1) << ' ';
  if (description1 == description2) {
    os << description1 << " changed\n";
  } else {
    os << "changed from " << description1 << " to " << description2 << '\n';
  }
  return false;
}

static constexpr size_t INDENT_INCREMENT = 2;

class Plain {
  // unvisited (absent) -> started (false) -> finished (true)
  using Seen = std::unordered_map<Comparison, bool, HashComparison>;

 public:
  Plain(const Reporting& reporting, std::ostream& output)
      : reporting_(reporting), output_(output) {}

  void Report(const Comparison&);

 private:
  const Reporting& reporting_;
  std::ostream& output_;
  Seen seen_;

  void Print(const Comparison&, size_t, const std::string&);
};

void Plain::Print(const Comparison& comparison, size_t indent,
           const std::string& prefix) {
  if (PrintComparison(reporting_, comparison, output_, indent, prefix)) {
    return;
  }

  indent += INDENT_INCREMENT;
  const auto it = reporting_.outcomes.find(comparison);
  Check(it != reporting_.outcomes.end())
      << "internal error: missing comparison";
  const auto& diff = it->second;

  const bool holds_changes = diff.holds_changes;
  std::pair<Seen::iterator, bool> insertion;

  if (holds_changes) {
    insertion = seen_.insert({comparison, false});
  }

  if (holds_changes && !insertion.second) {
    if (!insertion.first->second) {
      output_ << std::string(indent, ' ') << "(being reported)\n";
    } else if (!diff.details.empty()) {
      output_ << std::string(indent, ' ') << "(already reported)\n";
    }
    return;
  }

  for (const auto& detail : diff.details) {
    if (!detail.edge_) {
      output_ << std::string(indent, ' ') << detail.text_ << '\n';
    } else {
      Print(*detail.edge_, indent, detail.text_);
    }
  }

  if (holds_changes) {
    insertion.first->second = true;
  }
}

void Plain::Report(const Comparison& comparison) {
  // unpack then print - want symbol diff forest rather than symbols diff tree
  const auto& diff = reporting_.outcomes.at(comparison);
  for (const auto& detail : diff.details) {
    Print(*detail.edge_, 0, {});
    // paragraph spacing
    output_ << '\n';
  }
}

// Print the subtree of a diff graph starting at a given node and stopping at
// nodes that can themselves hold diffs, queuing such nodes for subsequent
// printing. Optionally, avoid printing "uninteresting" nodes - those that have
// no diff and no path to a diff that does not pass through a node that can hold
// diffs. Return whether the diff node's tree was intrinisically interesting.
class Flat {
 public:
  Flat(const Reporting& reporting, bool full, std::ostream& output)
      : reporting_(reporting), full_(full), output_(output) {}

  void Report(const Comparison&);

 private:
  const Reporting& reporting_;
  const bool full_;
  std::ostream& output_;
  std::unordered_set<Comparison, HashComparison> seen_;
  std::deque<Comparison> todo_;

  bool Print(const Comparison&, bool, std::ostream&, size_t,
             const std::string&);
};

bool Flat::Print(const Comparison& comparison, bool stop, std::ostream& os,
                 size_t indent, const std::string& prefix) {
  // Nodes that represent additions or removal are always interesting and no
  // recursion is possible.
  if (PrintComparison(reporting_, comparison, os, indent, prefix)) {
    return true;
  }

  // Look up the diff (including node and edge changes).
  const auto it = reporting_.outcomes.find(comparison);
  Check(it != reporting_.outcomes.end())
      << "internal error: missing comparison";
  const auto& diff = it->second;

  // Check the stopping condition.
  if (diff.holds_changes && stop) {
    // If it's a new diff-holding node, queue it.
    if (seen_.insert(comparison).second) {
      todo_.push_back(comparison);
    }
    return false;
  }
  // The stop flag can only be false on a non-recursive call which should be for
  // a diff-holding node.
  if (!diff.holds_changes && !stop) {
    Die() << "internal error: FlatPrint called on inappropriate node";
  }

  // Indent before describing diff details.
  indent += INDENT_INCREMENT;
  bool interesting = diff.has_changes;
  for (const auto& detail : diff.details) {
    if (!detail.edge_) {
      os << std::string(indent, ' ') << detail.text_ << '\n';
      // Node changes may not be interesting, if we allow non-change diff
      // details at some point. Just trust the has_changes flag.
    } else {
      // Edge changes are interesting if the target diff node is.
      std::ostringstream sub_os;
      // Set the stop flag to prevent recursion past diff-holding nodes.
      bool sub_interesting =
          Print(*detail.edge_, true, sub_os, indent, detail.text_);
      // If the sub-tree was interesting, add it.
      if (sub_interesting || full_) {
        os << sub_os.str();
      }
      interesting |= sub_interesting;
    }
  }
  return interesting;
}

void Flat::Report(const Comparison& comparison) {
  // We want a symbol diff forest rather than a symbol table diff tree, so
  // unpack the symbol table and then print the symbols specially.
  const auto& diff = reporting_.outcomes.at(comparison);
  for (const auto& detail : diff.details) {
    std::ostringstream os;
    const bool interesting = Print(*detail.edge_, true, os, 0, {});
    if (interesting || full_) {
      output_ << os.str() << '\n';
    }
  }
  while (!todo_.empty()) {
    auto comp = todo_.front();
    todo_.pop_front();
    std::ostringstream os;
    const bool interesting = Print(comp, false, os, 0, {});
    if (interesting || full_) {
      output_ << os.str() << '\n';
    }
  }
}

size_t VizId(std::unordered_map<Comparison, size_t, HashComparison>& ids,
             const Comparison& comparison) {
  return ids.insert({comparison, ids.size()}).first->second;
}

void VizPrint(const Reporting& reporting, const Comparison& comparison,
              std::unordered_set<Comparison, HashComparison>& seen,
              std::unordered_map<Comparison, size_t, HashComparison>& ids,
              std::ostream& os) {
  if (!seen.insert(comparison).second) {
    return;
  }

  const auto node = VizId(ids, comparison);

  const auto id1 = comparison.first;
  const auto id2 = comparison.second;

  Check(id1.has_value() || id2.has_value())
      << "internal error: Attempt to print comparison with nothing to compare.";

  if (!id2) {
    os << "  \"" << node << "\" [color=red, label=\"" << "removed("
       << Describe(reporting.graph, reporting.names)(*id1)
       << DescribeExtra(reporting.graph)(*id1)
       << ")\"]\n";
    return;
  }
  if (!id1) {
    os << "  \"" << node << "\" [color=red, label=\"" << "added("
       << Describe(reporting.graph, reporting.names)(*id2)
       << DescribeExtra(reporting.graph)(*id2)
       << ")\"]\n";
    return;
  }

  const auto it = reporting.outcomes.find(comparison);
  Check(it != reporting.outcomes.end()) << "internal error: missing comparison";
  const auto& diff = it->second;
  const char* colour = diff.has_changes ? "color=red, " : "";
  const char* shape = diff.holds_changes ? "shape=rectangle, " : "";
  const auto description1 =
      GetResolvedDescription(reporting.graph, reporting.names, *id1);
  const auto description2 =
      GetResolvedDescription(reporting.graph, reporting.names, *id2);
  if (description1 == description2) {
    os << "  \"" << node << "\" [" << colour << shape << "label=\""
       << description1 << "\"]\n";
  } else {
    os << "  \"" << node << "\" [" << colour << shape << "label=\""
       << description1 << " -> " << description2 << "\"]\n";
  }

  size_t index = 0;
  for (const auto& detail : diff.details) {
    if (!detail.edge_) {
      // attribute change, create an implicit edge and node
      os << "  \"" << node << "\" -> \"" << node << ':' << index << "\"\n"
         << "  \"" << node << ':' << index << "\" [color=red, label=\""
         << detail.text_ << "\"]\n";
      ++index;
    } else {
      const auto& to = *detail.edge_;
      VizPrint(reporting, to, seen, ids, os);
      os << "  \"" << node << "\" -> \"" << VizId(ids, to) << "\" [label=\""
         << detail.text_ << "\"]\n";
    }
  }
}

void ReportViz(const Reporting& reporting, const Comparison& comparison,
               std::ostream& output) {
  output << "digraph \"ABI diff\" {\n";
  std::unordered_set<Comparison, HashComparison> seen;
  std::unordered_map<Comparison, size_t, HashComparison> ids;
  VizPrint(reporting, comparison, seen, ids, output);
  output << "}\n";
}

template <typename T>
void PrintFidelityReportBucket(T transition,
                               const std::vector<std::string>& symbols_or_types,
                               std::ostream& output) {
  output << symbols_or_types.size() << ' ' << transition << ":\n";
  for (const auto& symbol_or_type : symbols_or_types) {
    output << "  " << symbol_or_type << '\n';
  }
  output << '\n';
}

}  // namespace

void Report(const Reporting& reporting, const Comparison& comparison,
            std::ostream& output) {
  switch (reporting.options.format) {
    case OutputFormat::PLAIN: {
      Plain(reporting, output).Report(comparison);
      break;
    }
    case OutputFormat::FLAT:
    case OutputFormat::SMALL: {
      bool full = reporting.options.format == OutputFormat::FLAT;
      Flat(reporting, full, output).Report(comparison);
      break;
    }
    case OutputFormat::SHORT: {
      std::stringstream report;
      Flat(reporting, false, report).Report(comparison);
      std::vector<std::string> report_lines;
      std::string line;
      while (std::getline(report, line)) {
        report_lines.push_back(line);
      }
      report_lines = stg::PostProcess(report_lines,
                                      reporting.options.max_crc_only_changes);
      for (const auto& line : report_lines) {
        output << line << '\n';
      }
      break;
    }
    case OutputFormat::VIZ: {
      ReportViz(reporting, comparison, output);
      break;
    }
  }
}

bool FidelityDiff(const stg::FidelityDiff& diff, std::ostream& output) {
  bool diffs_reported = false;
  auto print_bucket = [&diff, &output, &diffs_reported](auto&& from,
                                                        auto&& to) {
    auto transition = std::make_pair(from, to);
    if constexpr (std::is_same_v<decltype(from), SymbolFidelity&&>) {
      auto it = diff.symbol_transitions.find(transition);
      if (it != diff.symbol_transitions.end()) {
        PrintFidelityReportBucket(transition, it->second, output);
        diffs_reported = true;
      }
    } else if constexpr (std::is_same_v<decltype(from), TypeFidelity&&>) {
      auto it = diff.type_transitions.find(transition);
      if (it != diff.type_transitions.end()) {
        PrintFidelityReportBucket(transition, it->second, output);
        diffs_reported = true;
      }
    }
  };

  print_bucket(TypeFidelity::FULLY_DEFINED, TypeFidelity::ABSENT);
  print_bucket(TypeFidelity::DECLARATION_ONLY, TypeFidelity::ABSENT);
  print_bucket(TypeFidelity::FULLY_DEFINED, TypeFidelity::DECLARATION_ONLY);
  print_bucket(SymbolFidelity::TYPED, SymbolFidelity::UNTYPED);
  print_bucket(TypeFidelity::ABSENT, TypeFidelity::DECLARATION_ONLY);
  print_bucket(TypeFidelity::DECLARATION_ONLY, TypeFidelity::FULLY_DEFINED);
  print_bucket(SymbolFidelity::UNTYPED, SymbolFidelity::TYPED);
  print_bucket(TypeFidelity::ABSENT, TypeFidelity::FULLY_DEFINED);
  return diffs_reported;
}

}  // namespace reporting
}  // namespace stg
