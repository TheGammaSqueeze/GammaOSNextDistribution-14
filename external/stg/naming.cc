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
// Author: Ignes Simeonova

#include "naming.h"

#include <ostream>
#include <sstream>
#include <string>

#include "graph.h"

namespace stg {

Name Name::Add(Side side, Precedence precedence,
               const std::string& text) const {
  bool bracket = precedence < precedence_;
  std::ostringstream left;
  std::ostringstream right;

  // Bits on the left require whitespace separation when an identifier is being
  // added. While it would be simpler to unconditionally add a space, we choose
  // to only do this for identifiers and not for pointer and reference tokens,
  // except for the longer pointer-to-member syntax.
  //
  // For illegal types containing && & or & && this could result in &&&.
  left << left_;
  if (bracket) {
    left << '(';
  } else if (side == Side::LEFT
             && (precedence == Precedence::ATOMIC || text.size() > 2)) {
    left << ' ';
  }

  (side == Side::LEFT ? left : right) << text;

  // Bits on the right are arrays [] and functions () and need no whitespace.
  if (bracket) {
    right << ')';
  }
  right << right_;

  return Name{left.str(), precedence, right.str()};
}

Name Name::Qualify(Qualifier qualifier) const {
  std::ostringstream os;
  // Qualifiers attach without affecting precedence but the precedence
  // determines the relative position of the qualifier.
  switch (precedence_) {
    case Precedence::NIL: {
      // Add qualifier to the left of the type stem.
      //
      // This gives the more popular format (const int rather than int const)
      // and is safe because NIL precedence types are always leaf syntax.
      os << qualifier << ' ' << left_;
      return Name{os.str(), precedence_, right_};
    }
    case Precedence::POINTER: {
      // Add qualifier to the right of the sigil.
      //
      // TODO: consider dropping ' ' here.
      os << left_ << ' ' << qualifier;
      return Name{os.str(), precedence_, right_};
    }
    case Precedence::ARRAY_FUNCTION: {
      // Qualifiers should not normally apply to arrays or functions.
      os << '{' << qualifier << ">}" << right_;
      return Name{left_, precedence_, os.str()};
    }
    case Precedence::ATOMIC: {
      // Qualifiers should not normally apply to names.
      os << left_ << "{<" << qualifier << '}';
      return Name{os.str(), precedence_, right_};
    }
  }
}

std::ostream& Name::Print(std::ostream& os) const {
  return os << left_ << right_;
}

std::string Name::ToString() const {
  return left_ + right_;
}

std::ostream& operator<<(std::ostream& os, const Name& name) {
  return name.Print(os);
}

Name Describe::operator()(Id id) {
  // infinite recursion prevention - insert at most once
  static const Name black_hole{"#"};
  auto insertion = names.insert({id, black_hole});
  Name& cached = insertion.first->second;
  if (insertion.second) {
    cached = graph.Apply<Name>(*this, id);
  }
  return cached;
}

Name Describe::operator()(const Special& x) {
  switch (x.kind) {
    case Special::Kind::VOID:
      return Name{"void"};
    case Special::Kind::VARIADIC:
      return Name{"..."};
    case Special::Kind::NULLPTR:
      return Name{"decltype(nullptr)"};
  }
}

Name Describe::operator()(const PointerReference& x) {
  std::string sign;
  switch (x.kind) {
    case PointerReference::Kind::POINTER:
      sign = "*";
      break;
    case PointerReference::Kind::LVALUE_REFERENCE:
      sign = "&";
      break;
    case PointerReference::Kind::RVALUE_REFERENCE:
      sign = "&&";
      break;
  }
  return (*this)(x.pointee_type_id)
          .Add(Side::LEFT, Precedence::POINTER, sign);
}

Name Describe::operator()(const PointerToMember& x) {
  std::ostringstream os;
  os << (*this)(x.containing_type_id) << "::*";
  return (*this)(x.pointee_type_id).Add(Side::LEFT, Precedence::POINTER,
                                        os.str());
}

Name Describe::operator()(const Typedef& x) {
  return Name{x.name};
}

Name Describe::operator()(const Qualified& x) {
  return (*this)(x.qualified_type_id).Qualify(x.qualifier);
}

Name Describe::operator()(const Primitive& x) {
  return Name{x.name};
}

Name Describe::operator()(const Array& x) {
  std::ostringstream os;
  os << '[' << x.number_of_elements << ']';
  return (*this)(x.element_type_id)
          .Add(Side::RIGHT, Precedence::ARRAY_FUNCTION, os.str());
}

Name Describe::operator()(const BaseClass& x) {
  return (*this)(x.type_id);
}

Name Describe::operator()(const Method& x) {
  if (x.mangled_name == x.name) {
    return Name{x.name};
  }
  return Name{x.name + " {" + x.mangled_name + "}"};
}

Name Describe::operator()(const Member& x) {
  auto description = (*this)(x.type_id);
  if (!x.name.empty()) {
    description = description.Add(Side::LEFT, Precedence::ATOMIC, x.name);
  }
  if (x.bitsize) {
    description = description.Add(
        Side::RIGHT, Precedence::ATOMIC, ':' + std::to_string(x.bitsize));
  }
  return description;
}

Name Describe::operator()(const StructUnion& x) {
  std::ostringstream os;
  os << x.kind << ' ';
  if (!x.name.empty()) {
    os << x.name;
  } else if (x.definition) {
    os << "{ ";
    for (const auto& member : x.definition->members) {
      os << (*this)(member) << "; ";
    }
    os << '}';
  }
  return Name{os.str()};
}

Name Describe::operator()(const Enumeration& x) {
  std::ostringstream os;
  os << "enum ";
  if (!x.name.empty()) {
    os << x.name;
  } else if (x.definition) {
    os << "{ ";
    for (const auto& e : x.definition->enumerators) {
      os << e.first << " = " << e.second << ", ";
    }
    os << '}';
  }
  return Name{os.str()};
}

Name Describe::operator()(const Function& x) {
  std::ostringstream os;
  os << '(';
  bool sep = false;
  for (const Id p : x.parameters) {
    if (sep) {
      os << ", ";
    } else {
      sep = true;
    }
    os << (*this)(p);
  }
  os << ')';
  return (*this)(x.return_type_id)
          .Add(Side::RIGHT, Precedence::ARRAY_FUNCTION, os.str());
}

Name Describe::operator()(const ElfSymbol& x) {
  const auto& name = x.full_name ? *x.full_name : x.symbol_name;
  return x.type_id
      ? (*this)(*x.type_id).Add(Side::LEFT, Precedence::ATOMIC, name)
      : Name{name};
}

Name Describe::operator()(const Interface&) {
  return Name{"interface"};
}

std::string DescribeKind::operator()(Id id) {
  return graph.Apply<std::string>(*this, id);
}

std::string DescribeKind::operator()(const BaseClass&) {
  return "base class";
}

std::string DescribeKind::operator()(const Method&) {
  return "method";
}

std::string DescribeKind::operator()(const Member&) {
  return "member";
}

std::string DescribeKind::operator()(const ElfSymbol& x) {
  std::ostringstream os;
  os << x.symbol_type << " symbol";
  return os.str();
}

std::string DescribeKind::operator()(const Interface&) {
  return "interface";
}

template <typename Node>
std::string DescribeKind::operator()(const Node&) {
  return "type";
}

std::string DescribeExtra::operator()(Id id) {
  return graph.Apply<std::string>(*this, id);
}

std::string DescribeExtra::operator()(const ElfSymbol& x) {
  const auto& name = x.full_name ? *x.full_name : x.symbol_name;
  auto versioned = VersionedSymbolName(x);
  return name == versioned ? std::string() : " {" + versioned + '}';
}

template <typename Node>
std::string DescribeExtra::operator()(const Node&) {
  return {};
}

}  // namespace stg
