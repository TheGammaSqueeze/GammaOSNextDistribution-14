// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2021-2022 Google LLC
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

#ifndef STG_ERROR_H_
#define STG_ERROR_H_

#include <exception>
#include <ios>
#include <iostream>
#include <optional>
#include <ostream>
#include <sstream>
#include <string>

namespace stg {

class Exception : public std::exception {
 public:
  explicit Exception(const std::string& message) {
    Add(message);
  }

  const char* what() const noexcept(true) final {
    return message_.c_str();
  }

  void Add(const std::string& message) {
    (message_ += message) += '\n';
  }

 private:
  std::string message_;
};

class Check {
 public:
  explicit Check(bool ok)
      : os_(ok ? std::optional<std::ostringstream>()
               : std::make_optional<std::ostringstream>()) {}
  ~Check() noexcept(false) {
    if (os_) {
      throw Exception(os_->str());
    }
  }

  template <typename T>
  Check& operator<<(const T& t) {
    if (os_) {
      *os_ << t;
    }
    return *this;
  }

 private:
  std::optional<std::ostringstream> os_;
};

class Die {
 public:
  [[noreturn]] ~Die() noexcept(false) {
    throw Exception(os_.str());
  }

  template <typename T>
  Die& operator<<(const T& t) {
    os_ << t;
    return *this;
  }

 private:
  std::ostringstream os_;
};

class Warn {
 public:
  ~Warn() {
    std::cerr << "warning: " << os_.str() << '\n';
  }

  template <typename T>
  Warn& operator<<(const T& t) {
    os_ << t;
    return *this;
  }

 private:
  std::ostringstream os_;
};

struct Error {
  explicit Error(int number) : number(number) {}
  int number;
};

inline std::ostream& operator<<(std::ostream& os, Error error) {
  return os << std::system_error(error.number, std::generic_category()).what();
}

template <typename T>
struct Hex {
  explicit Hex(const T& value) : value(value) {}
  const T& value;
};

template <typename T> Hex(const T&) -> Hex<T>;

template <typename T>
std::ostream& operator<<(std::ostream& os, const Hex<T>& hex_value) {
  // not quite right if an exception is thrown
  const auto flags = os.flags();
  os << "0x" << std::hex << hex_value.value;
  os.flags(flags);
  return os;
}

}  // namespace stg

#endif  // STG_ERROR_H_
