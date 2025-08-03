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
// Author: Giuliano Procida

#include "filter.h"

#include <fnmatch.h>

#include <array>
#include <cctype>
#include <cstddef>
#include <cstring>
#include <fstream>
#include <memory>
#include <ostream>
#include <queue>
#include <sstream>
#include <string>
#include <string_view>
#include <unordered_set>
#include <utility>

#include "error.h"

namespace stg {
namespace {

using Items = std::unordered_set<std::string>;

Items ReadAbigail(const std::string& filename) {
  static constexpr std::string_view kSectionSuffix = "list";
  Items items;
  std::ifstream file(filename);
  Check(file.good()) << "error opening filter file '" << filename << ": "
                     << Error(errno);
  bool in_filter_section = false;
  std::string line;
  while (std::getline(file, line)) {
    size_t start = 0;
    size_t limit = line.size();
    // Strip leading whitespace.
    while (start < limit && std::isspace(line[start])) {
      ++start;
    }
    // Skip comment lines.
    if (start < limit && line[start] == '#') {
      continue;
    }
    // Strip trailing whitespace.
    while (start < limit && std::isspace(line[limit - 1])) {
      --limit;
    }
    // Skip empty lines.
    if (start == limit) {
      continue;
    }
    // See if we are entering a filter list section.
    if (line[start] == '[' && line[limit - 1] == ']') {
      std::string_view section(&line[start + 1], limit - start - 2);
      // TODO: use std::string_view::ends_with
      const auto section_size = section.size();
      const auto suffix_size = kSectionSuffix.size();
      in_filter_section = section_size >= suffix_size &&
          section.substr(section_size - suffix_size) == kSectionSuffix;
      continue;
    }
    // Add item.
    if (in_filter_section) {
      items.insert(std::string(&line[start], limit - start));
    }
  }
  Check(file.eof()) << "error reading filter file '" << filename << ": "
                    << Error(errno);
  return items;
}

// Inverted filter.
class NotFilter : public Filter {
 public:
  explicit NotFilter(std::unique_ptr<Filter> filter)
      : filter_(std::move(filter)) {}
  bool operator()(const std::string& item) const final {
    return !(*filter_)(item);
  };

 private:
  const std::unique_ptr<Filter> filter_;
};

// Conjunction of filters.
class AndFilter : public Filter {
 public:
  AndFilter(std::unique_ptr<Filter> filter1,
            std::unique_ptr<Filter> filter2)
      : filter1_(std::move(filter1)), filter2_(std::move(filter2)) {}
  bool operator()(const std::string& item) const final {
    return (*filter1_)(item) && (*filter2_)(item);
  };

 private:
  const std::unique_ptr<Filter> filter1_;
  const std::unique_ptr<Filter> filter2_;
};

// Disjunction of filters.
class OrFilter : public Filter {
 public:
  OrFilter(std::unique_ptr<Filter> filter1,
           std::unique_ptr<Filter> filter2)
      : filter1_(std::move(filter1)), filter2_(std::move(filter2)) {}
  bool operator()(const std::string& item) const final {
    return (*filter1_)(item) || (*filter2_)(item);
  };

 private:
  const std::unique_ptr<Filter> filter1_;
  const std::unique_ptr<Filter> filter2_;
};

// Glob filter.
class GlobFilter : public Filter {
 public:
  explicit GlobFilter(const std::string& pattern) : pattern_(pattern) {}
  bool operator()(const std::string& item) const final {
    return fnmatch(pattern_.c_str(), item.c_str(), 0) == 0;
  }

 private:
  const std::string pattern_;
};

// Literal list filter.
class SetFilter : public Filter {
 public:
  explicit SetFilter(Items&& items)
      : items_(std::move(items)) {}
  bool operator()(const std::string& item) const final {
    return items_.count(item) > 0;
  };

 private:
  const Items items_;
};

const char* kTokenCharacters = ":!()&|";

// Split a filter expression into tokens.
//
// All tokens are just strings, but single characters from kTokenCharacters are
// recognised as special syntax. Whitespace can be used between tokens and will
// be ignored.
std::queue<std::string> Tokenise(const std::string& filter) {
  std::queue<std::string> result;
  auto it = filter.begin();
  const auto end = filter.end();
  while (it != end) {
    if (std::isspace(*it)) {
      ++it;
    } else if (std::strchr(kTokenCharacters, *it)) {
      result.emplace(&*it, 1);
      ++it;
    } else if (std::isgraph(*it)) {
      auto name = it;
      ++it;
      while (it != end && std::isgraph(*it)
             && !std::strchr(kTokenCharacters, *it)) {
        ++it;
      }
      result.emplace(&*name, it - name);
    } else {
      Die() << "unexpected character in filter: '" << *it;
    }
  }

  return result;
}

// The failing parser.
std::unique_ptr<Filter> Fail(
    const std::string& message, std::queue<std::string>& tokens) {
  std::ostringstream os;
  os << "syntax error in filter expression: '" << message << "'; context:";
  for (size_t i = 0; i < 3; ++i) {
    os << ' ';
    if (tokens.empty()) {
      os << "<end>";
      break;
    }
    os << '"' << tokens.front() << '"';
    tokens.pop();
  }
  Die() << os.str();
}

std::unique_ptr<Filter> Expression(std::queue<std::string>& tokens);

// Parse a filter atom.
std::unique_ptr<Filter> Atom(std::queue<std::string>& tokens) {
  if (tokens.empty()) {
    return Fail("expected a filter expression", tokens);
  }
  auto token = tokens.front();
  tokens.pop();
  if (token == "(") {
    auto expression = Expression(tokens);
    if (tokens.empty() || tokens.front() != ")") {
      return Fail("expected a ')'", tokens);
    }
    tokens.pop();
    return expression;
  } else if (token == ":") {
    if (tokens.empty()) {
      return Fail("expected a file name", tokens);
    }
    token = tokens.front();
    tokens.pop();
    return std::make_unique<SetFilter>(ReadAbigail(token));
  } else {
    if (std::strchr(kTokenCharacters, token[0])) {
      return Fail("expected a glob token", tokens);
    }
    return std::make_unique<GlobFilter>(token);
  }
}

// Parse a filter factor.
std::unique_ptr<Filter> Factor(std::queue<std::string>& tokens) {
  bool invert = false;
  while (!tokens.empty() && tokens.front() == "!") {
    tokens.pop();
    invert = !invert;
  }
  auto atom = Atom(tokens);
  if (invert) {
    atom = std::make_unique<NotFilter>(std::move(atom));
  }
  return atom;
}

// Parse a filter term.
std::unique_ptr<Filter> Term(std::queue<std::string>& tokens) {
  auto factor = Factor(tokens);
  while (!tokens.empty() && tokens.front() == "&") {
    tokens.pop();
    factor = std::make_unique<AndFilter>(std::move(factor), Factor(tokens));
  }
  return factor;
}

// Parse a filter expression.
std::unique_ptr<Filter> Expression(std::queue<std::string>& tokens) {
  auto term = Term(tokens);
  while (!tokens.empty() && tokens.front() == "|") {
    tokens.pop();
    term = std::make_unique<OrFilter>(std::move(term), Term(tokens));
  }
  return term;
}

}  // namespace

// Tokenise and parse a filter expression.
std::unique_ptr<Filter> MakeFilter(const std::string& filter)
{
  auto tokens = Tokenise(filter);
  auto result = Expression(tokens);
  if (!tokens.empty()) {
    return Fail("unexpected junk at end of filter", tokens);
  }
  return result;
}

void FilterUsage(std::ostream& os) {
  os << "filter syntax:\n"
     << "  <filter>   ::= <term>          |  <expression> '|' <term>\n"
     << "  <term>     ::= <factor>        |  <term> '&' <factor>\n"
     << "  <factor>   ::= <atom>          |  '!' <factor>\n"
     << "  <atom>     ::= ':' <filename>  |  <glob>  |  '(' <expression> ')'\n"
     << "  <filename> ::= <string>\n"
     << "  <glob>     ::= <string>\n";
}

}  // namespace stg
