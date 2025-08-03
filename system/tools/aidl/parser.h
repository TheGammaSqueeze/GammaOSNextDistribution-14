/*
 * Copyright (C) 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include "aidl_language.h"
#include "aidl_typenames.h"
#include "comments.h"
#include "io_delegate.h"
#include "logging.h"
#include "options.h"

#include <memory>
#include <string>
#include <vector>

struct yy_buffer_state;
typedef yy_buffer_state* YY_BUFFER_STATE;

class AidlToken {
 public:
  AidlToken(const std::string& text, android::aidl::Comments comments)
      : text_(text), comments_(std::move(comments)) {}
  ~AidlToken() = default;

  AidlToken(const AidlToken&) = delete;
  AidlToken(AidlToken&&) = delete;
  AidlToken& operator=(const AidlToken&) = delete;
  AidlToken& operator=(AidlToken&&) = delete;

  const std::string& GetText() const { return text_; }
  const android::aidl::Comments& GetComments() const { return comments_; }

  template <typename T>
  void Append(T&& text) {
    text_ += std::forward<T>(text);
  }

 private:
  std::string text_;
  android::aidl::Comments comments_;
};

using TypeResolver = std::function<bool(const AidlDefinedType*, AidlTypeSpecifier*)>;
bool ResolveReferences(const AidlDocument& document, TypeResolver& resolver);

class Parser {
 public:
  // non-copyable, non-assignable
  Parser(const Parser&) = delete;
  Parser& operator=(const Parser&) = delete;

  ~Parser();

  // Parse contents of file |filename|. Should only be called once.
  static const AidlDocument* Parse(const std::string& filename,
                                   const android::aidl::IoDelegate& io_delegate,
                                   AidlTypenames& typenames, bool is_preprocessed = false);

  void AddError() { error_++; }
  bool HasError() const { return error_ != 0; }

  const std::string& FileName() const { return filename_; }
  void* Scanner() const { return scanner_; }

  AidlDocument* GetDocument() { return document_.get(); }

  // This restricts the grammar to something more reasonable. One alternative
  // would be to support multiple sets of type specifiers in our AST, but then a
  // lot of later code would have to deal with this more complicated type. So,
  // in order to keep the AST simpler, restricting the grammar here.
  //
  // Takes ownership of type_args, modifies type.
  void SetTypeParameters(AidlTypeSpecifier* type,
                         std::vector<std::unique_ptr<AidlTypeSpecifier>>* type_args);

  // fully-qualified type names are allowed only in preprocessed files
  void CheckValidTypeName(const AidlToken& token, const AidlLocation& loc);

  void SetPackage(const std::string& package);
  const std::string& Package() const { return package_; }

  void MakeDocument(const AidlLocation& location, const Comments& comments,
                    std::vector<std::string> imports,
                    std::vector<std::unique_ptr<AidlDefinedType>> defined_types);

 private:
  explicit Parser(const std::string& filename, std::string& raw_buffer, bool is_preprocessed);

  std::string filename_;
  bool is_preprocessed_;
  std::string package_;
  void* scanner_ = nullptr;
  YY_BUFFER_STATE buffer_;
  int error_ = 0;

  std::unique_ptr<AidlDocument> document_;
};
