/*
 * Copyright (C) 2021, The Android Open Source Project
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

#include "preprocess.h"

#include <android-base/strings.h>

#include "aidl.h"

using android::base::Join;

namespace android {
namespace aidl {

namespace {
// PreprocessVisitor emits
// - types including comments(hide/deprecated) and annotations
// - constant delcarations for interface/parcelable/unions
// - enumerators for enums
struct PreprocessVisitor : AidlVisitor {
  CodeWriter& out;
  PreprocessVisitor(CodeWriter& out) : out(out) {}

  void DumpType(const AidlDefinedType& dt, const string& type) {
    DumpComments(dt);
    DumpAnnotations(dt);
    // Top-level definition emits canonical name while nested type emits "name" only.
    if (dt.GetParentType()) {
      out << type << " " << dt.GetName();
    } else {
      out << type << " " << dt.GetCanonicalName();
    }
    if (auto generic_type = dt.AsParameterizable(); generic_type && generic_type->IsGeneric()) {
      out << "<" << Join(generic_type->GetTypeParameters(), ", ") << ">";
    }
  }
  void DumpMembers(const AidlDefinedType& dt) {
    out << " {\n";
    out.Indent();
    for (const auto& constdecl : dt.GetConstantDeclarations()) {
      constdecl->DispatchVisit(*this);
    }
    for (const auto& nested : dt.GetNestedTypes()) {
      nested->DispatchVisit(*this);
    }
    out.Dedent();
    out << "}\n";
  }
  void DumpComments(const AidlCommentable& c) {
    const auto hidden = c.IsHidden();
    const auto deprecated = FindDeprecated(c.GetComments());
    if (hidden || deprecated) {
      out << "/**\n";
      if (hidden) {
        out << " * @hide\n";
      }
      if (deprecated) {
        out << " * @deprecated " << deprecated->note << "\n";
      }
      out << " */\n";
    }
  }
  void DumpAnnotations(const AidlAnnotatable& a) {
    auto annotations = a.ToString();
    if (!annotations.empty()) {
      out << annotations << "\n";
    }
  }
  void DumpConstantValue(const AidlTypeSpecifier& type, const AidlConstantValue& c) {
    out << c.ValueString(type, AidlConstantValueDecorator);
  }
  void Visit(const AidlInterface& t) override {
    DumpType(t, "interface");
    DumpMembers(t);
  }
  void Visit(const AidlParcelable& t) override {
    DumpType(t, "parcelable");
    if (const auto& cpp_header = t.GetCppHeader(); !cpp_header.empty()) {
      out << " cpp_header " << cpp_header;
    }
    if (const auto& ndk_header = t.GetNdkHeader(); !ndk_header.empty()) {
      out << " ndk_header " << ndk_header;
    }
    if (const auto& rust_type = t.GetRustType(); !rust_type.empty()) {
      out << " rust_type " << rust_type;
    }
    out << ";\n";
  }
  void Visit(const AidlStructuredParcelable& t) override {
    DumpType(t, "parcelable");
    DumpMembers(t);
  }
  void Visit(const AidlUnionDecl& t) override {
    DumpType(t, "union");
    DumpMembers(t);
  }
  void Visit(const AidlEnumDeclaration& t) override {
    DumpType(t, "enum");
    out << " {\n";
    out.Indent();
    for (const auto& e : t.GetEnumerators()) {
      out << e->GetName() << " = ";
      DumpConstantValue(t.GetBackingType(), *e->GetValue());
      out << ",\n";
    }
    out.Dedent();
    out << "}\n";
  }
  void Visit(const AidlConstantDeclaration& c) override {
    DumpComments(c);
    out << "const ";
    Visit(c.GetType());
    out << " " << c.GetName() << " = ";
    DumpConstantValue(c.GetType(), c.GetValue());
    out << ";\n";
  }
  void Visit(const AidlTypeSpecifier& t) override { out << t.ToString(); }
};

}  // namespace

bool Preprocess(const Options& options, const IoDelegate& io_delegate) {
  unique_ptr<CodeWriter> writer = io_delegate.GetCodeWriter(options.OutputFile());
  PreprocessVisitor visitor(*writer);

  for (const auto& file : options.InputFiles()) {
    AidlTypenames typenames;
    auto result =
        internals::load_and_validate_aidl(file, options, io_delegate, &typenames, nullptr);
    if (result == AidlError::OK) {
      const auto& doc = typenames.MainDocument();
      for (const auto& t : doc.DefinedTypes()) {
        t->DispatchVisit(visitor);
      }
    } else {
      return false;
    }
  }

  return writer->Close();
}

}  // namespace aidl
}  // namespace android
