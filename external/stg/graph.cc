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
// Author: Maria Teguiani
// Author: Giuliano Procida
// Author: Ignes Simeonova

#include "graph.h"

#include <ios>
#include <limits>
#include <ostream>
#include <string>
#include <string_view>

namespace stg {

const Id Id::kInvalid(std::numeric_limits<decltype(Id::ix_)>::max());

std::ostream& operator<<(std::ostream& os, Id id) {
  return os << '<' << id.ix_ << '>';
}

std::ostream& operator<<(std::ostream& os, BaseClass::Inheritance inheritance) {
  switch (inheritance) {
    case BaseClass::Inheritance::NON_VIRTUAL:
      return os << "non-virtual";
    case BaseClass::Inheritance::VIRTUAL:
      return os << "virtual";
  }
}

namespace {

std::string_view ToString(StructUnion::Kind kind) {
  switch (kind) {
    case StructUnion::Kind::STRUCT:
      return "struct";
    case StructUnion::Kind::UNION:
      return "union";
  }
}

}  // namespace

std::ostream& operator<<(std::ostream& os, StructUnion::Kind kind) {
  return os << ToString(kind);
}

std::string& operator+=(std::string& os, StructUnion::Kind kind) {
  return os += ToString(kind);
}

std::ostream& operator<<(std::ostream& os, Qualifier qualifier) {
  switch (qualifier) {
    case Qualifier::CONST:
      return os << "const";
    case Qualifier::VOLATILE:
      return os << "volatile";
    case Qualifier::RESTRICT:
      return os << "restrict";
    case Qualifier::ATOMIC:
      return os << "atomic";
  }
}

std::ostream& operator<<(std::ostream& os, ElfSymbol::SymbolType type) {
  switch (type) {
    case ElfSymbol::SymbolType::OBJECT:
      return os << "variable";
    case ElfSymbol::SymbolType::FUNCTION:
      return os << "function";
    case ElfSymbol::SymbolType::COMMON:
      return os << "common";
    case ElfSymbol::SymbolType::TLS:
      return os << "TLS";
    case ElfSymbol::SymbolType::GNU_IFUNC:
      return os << "indirect (ifunc) function";
  }
}

std::ostream& operator<<(std::ostream& os, ElfSymbol::Binding binding) {
  switch (binding) {
    case ElfSymbol::Binding::GLOBAL:
      return os << "global";
    case ElfSymbol::Binding::LOCAL:
      return os << "local";
    case ElfSymbol::Binding::WEAK:
      return os << "weak";
    case ElfSymbol::Binding::GNU_UNIQUE:
      return os << "GNU unique";
  }
}

std::ostream& operator<<(std::ostream& os, ElfSymbol::Visibility visibility) {
  switch (visibility) {
    case ElfSymbol::Visibility::DEFAULT:
      return os << "default";
    case ElfSymbol::Visibility::PROTECTED:
      return os << "protected";
    case ElfSymbol::Visibility::HIDDEN:
      return os << "hidden";
    case ElfSymbol::Visibility::INTERNAL:
      return os << "internal";
  }
}

std::string VersionInfoToString(const ElfSymbol::VersionInfo& version_info) {
  return '@' + std::string(version_info.is_default ? "@" : "") +
         version_info.name;
}

std::string VersionedSymbolName(const ElfSymbol& symbol) {
  return symbol.symbol_name + (symbol.version_info
                                   ? VersionInfoToString(*symbol.version_info)
                                   : std::string());
}

std::ostream& operator<<(std::ostream& os, ElfSymbol::CRC crc) {
  return os << Hex(crc.number);
}

std::ostream& operator<<(std::ostream& os, Primitive::Encoding encoding) {
  switch (encoding) {
    case Primitive::Encoding::BOOLEAN:
      return os << "boolean";
    case Primitive::Encoding::SIGNED_INTEGER:
      return os << "signed integer";
    case Primitive::Encoding::UNSIGNED_INTEGER:
      return os << "unsigned integer";
    case Primitive::Encoding::SIGNED_CHARACTER:
      return os << "signed character";
    case Primitive::Encoding::UNSIGNED_CHARACTER:
      return os << "unsigned character";
    case Primitive::Encoding::REAL_NUMBER:
      return os << "real number";
    case Primitive::Encoding::COMPLEX_NUMBER:
      return os << "complex number";
    case Primitive::Encoding::UTF:
      return os << "UTF";
  }
}

}  // namespace stg
