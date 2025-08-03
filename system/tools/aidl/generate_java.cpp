/*
 * Copyright (C) 2016, The Android Open Source Project
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

#include "generate_java.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <algorithm>
#include <map>
#include <memory>
#include <optional>
#include <sstream>

#include <android-base/format.h>
#include <android-base/stringprintf.h>

#include "aidl_to_common.h"
#include "aidl_to_java.h"
#include "code_writer.h"
#include "logging.h"

using ::android::base::EndsWith;
using ::android::base::Join;
using ::android::base::StartsWith;
using std::string;
using std::unique_ptr;
using std::vector;

namespace {
using android::aidl::java::CodeGeneratorContext;
using android::aidl::java::ConstantValueDecorator;

inline string GetterName(const AidlVariableDeclaration& variable) {
  return "get" + variable.GetCapitalizedName();
}
inline string SetterName(const AidlVariableDeclaration& variable) {
  return "set" + variable.GetCapitalizedName();
}

// clang-format off
const map<string, string> kContentsDescribers {
  {"FileDescriptor", R"(if (_v instanceof java.io.FileDescriptor) {
  return android.os.Parcelable.CONTENTS_FILE_DESCRIPTOR;
})"},
  {"Parcelable", R"(if (_v instanceof android.os.Parcelable) {
  return ((android.os.Parcelable) _v).describeContents();
})"},
  {"Map", R"(if (_v instanceof java.util.Map) {
  return describeContents(((java.util.Map) _v).values());
})"},
  {"List", R"(if (_v instanceof java.util.Collection) {
  int _mask = 0;
  for (Object o : (java.util.Collection) _v) {
    _mask |= describeContents(o);
  }
  return _mask;
})"},
  {"Array", R"(if (_v instanceof Object[]) {
  int _mask = 0;
  for (Object o : (Object[]) _v) {
    _mask |= describeContents(o);
  }
  return _mask;
})"},
};
// clang-format on

void GenerateDescribeContentsHelper(CodeWriter& out, const set<string>& describers) {
  out << "private int describeContents(Object _v) {\n";
  out.Indent();
  out << "if (_v == null) return 0;\n";
  for (const auto& d : describers) {
    out << kContentsDescribers.at(d) << "\n";
  }
  out << "return 0;\n";
  out.Dedent();
  out << "}\n";
}

// Some types contribute to Parcelable.describeContents().
// e.g. FileDescriptor, Parcelables, List<Parcelables> ...
bool CanDescribeContents(const AidlTypeSpecifier& type, const AidlTypenames& types,
                         set<string>* describers) {
  if (type.IsArray()) {
    bool canDescribe = false;
    type.ViewAsArrayBase([&](const AidlTypeSpecifier& base) {
      canDescribe = CanDescribeContents(base, types, describers);
    });

    if (canDescribe) {
      describers->insert("Array");
      return true;
    }
    return false;
  }

  if (type.GetName() == "List") {
    if (CanDescribeContents(*type.GetTypeParameters()[0], types, describers)) {
      describers->insert("List");
      return true;
    }
    return false;
  }

  if (type.GetName() == "Map") {
    if (CanDescribeContents(*type.GetTypeParameters()[1], types, describers)) {
      describers->insert("Map");  // Map describer uses List describer
      describers->insert("List");
      return true;
    }
    return false;
  }

  if (type.GetName() == "FileDescriptor") {
    describers->insert("FileDescriptor");
    return true;
  }

  if (type.GetName() == "ParcelFileDescriptor" || type.GetName() == "ParcelableHolder" ||
      types.GetParcelable(type) != nullptr) {
    describers->insert("Parcelable");
    return true;
  }

  return false;
}
void GenerateParcelableDescribeContents(CodeWriter& out, const AidlStructuredParcelable& decl,
                                        const AidlTypenames& types) {
  set<string> describers;

  out << "@Override\n";
  out << "public int describeContents() {\n";
  out.Indent();
  out << "int _mask = 0;\n";
  for (const auto& f : decl.GetFields()) {
    if (CanDescribeContents(f->GetType(), types, &describers)) {
      out << "_mask |= describeContents(" << f->GetName() << ");\n";
    }
  }
  out << "return _mask;\n";
  out.Dedent();
  out << "}\n";
  if (!describers.empty()) {
    GenerateDescribeContentsHelper(out, describers);
  }
}

void GenerateParcelableDescribeContents(CodeWriter& out, const AidlUnionDecl& decl,
                                        const AidlTypenames& types) {
  set<string> describers;

  out << "@Override\n";
  out << "public int describeContents() {\n";
  out.Indent();
  out << "int _mask = 0;\n";
  out << "switch (getTag()) {\n";
  for (const auto& f : decl.GetFields()) {
    if (CanDescribeContents(f->GetType(), types, &describers)) {
      out << "case " << f->GetName() << ":\n";
      out.Indent();
      out << "_mask |= describeContents(" << GetterName(*f) << "());\n";
      out << "break;\n";
      out.Dedent();
    }
  }
  out << "}\n";
  out << "return _mask;\n";
  out.Dedent();
  out << "}\n";
  if (!describers.empty()) {
    GenerateDescribeContentsHelper(out, describers);
  }
}

void GenerateToString(CodeWriter& out, const AidlStructuredParcelable& parcel,
                      const AidlTypenames& typenames, const Options& options) {
  out << "@Override\n";
  out << "public String toString() {\n";
  out.Indent();
  out << "java.util.StringJoiner _aidl_sj = new java.util.StringJoiner(";
  out << "\", \", \"{\", \"}\");\n";
  for (const auto& field : parcel.GetFields()) {
    CodeGeneratorContext ctx{
        .writer = out,
        .typenames = typenames,
        .type = field->GetType(),
        .var = field->GetName(),
        .min_sdk_version = options.GetMinSdkVersion(),
    };
    out << "_aidl_sj.add(\"" << field->GetName() << ": \" + (";
    ToStringFor(ctx);
    out << "));\n";
  }
  out << "return \"" << parcel.GetName() << "\" + _aidl_sj.toString()  ;\n";
  out.Dedent();
  out << "}\n";
}

void GenerateToString(CodeWriter& out, const AidlUnionDecl& parcel, const AidlTypenames& typenames,
                      const Options& options) {
  out << "@Override\n";
  out << "public String toString() {\n";
  out.Indent();
  out << "switch (_tag) {\n";
  for (const auto& field : parcel.GetFields()) {
    CodeGeneratorContext ctx{
        .writer = out,
        .typenames = typenames,
        .type = field->GetType(),
        .var = GetterName(*field) + "()",
        .min_sdk_version = options.GetMinSdkVersion(),
    };
    out << "case " << field->GetName() << ": return \"" << parcel.GetName() << "."
        << field->GetName() << "(\" + (";
    ToStringFor(ctx);
    out << ") + \")\";\n";
  }
  out << "}\n";
  out << "throw new IllegalStateException(\"unknown field: \" + _tag);\n";
  out.Dedent();
  out << "}\n";
}

void GenerateEqualsAndHashCode(CodeWriter& out, const AidlStructuredParcelable& parcel,
                      const AidlTypenames&) {
  out << "@Override\n";
  out << "public boolean equals(Object other) {\n";
  out.Indent();
  out << "if (this == other) return true;\n";
  out << "if (other == null) return false;\n";
  out << "if (!(other instanceof " << parcel.GetName() << ")) return false;\n";
  out << parcel.GetName() << " that = (" << parcel.GetName() << ")other;\n";
  for (const auto& field : parcel.GetFields()) {
    out << "if (!java.util.Objects.deepEquals(" << field->GetName() << ", that." << field->GetName()
        << ")) return false;\n";
  }
  out << "return true;\n";
  out.Dedent();
  out << "}\n";
  out << "\n";
  out << "@Override\n";
  out << "public int hashCode() {\n";
  out.Indent();
  out << "return java.util.Arrays.deepHashCode(java.util.Arrays.asList(";
  std::vector<std::string> names;
  for (const auto& field : parcel.GetFields()) {
    names.push_back(field->GetName());
  }
  out << android::base::Join(names, ", ") << ").toArray());\n";
  out.Dedent();
  out << "}\n";
}

void GenerateEqualsAndHashCode(CodeWriter& out, const AidlUnionDecl& decl,
                                 const AidlTypenames&) {
  out << "@Override\n";
  out << "public boolean equals(Object other) {\n";
  out.Indent();
  out << "if (this == other) return true;\n";
  out << "if (other == null) return false;\n";
  out << "if (!(other instanceof " << decl.GetName() << ")) return false;\n";
  out << decl.GetName() << " that = (" << decl.GetName() << ")other;\n";
  out << "if (_tag != that._tag) return false;\n";
  out << "if (!java.util.Objects.deepEquals(_value, that._value)) return false;\n";
  out << "return true;\n";
  out.Dedent();
  out << "}\n";
  out << "\n";
  out << "@Override\n";
  out << "public int hashCode() {\n";
  out.Indent();
  out << "return java.util.Arrays.deepHashCode(java.util.Arrays.asList(_tag, _value).toArray());\n";
  out.Dedent();
  out << "}\n";
  out << "\n";
}

}  // namespace

namespace android {
namespace aidl {
namespace java {

std::string GenerateComments(const AidlCommentable& node) {
  return FormatCommentsForJava(node.GetComments());
}

std::string GenerateAnnotations(const AidlNode& node) {
  std::string result;
  for (const auto& a : JavaAnnotationsFor(node)) {
    result += a + "\n";
  }
  return result;
}

std::unique_ptr<android::aidl::java::Class> GenerateParcelableClass(
    const AidlStructuredParcelable* parcel, const AidlTypenames& typenames,
    const Options& options) {
  auto parcel_class = std::make_unique<Class>();
  parcel_class->comment = GenerateComments(*parcel);
  parcel_class->modifiers = PUBLIC;
  parcel_class->what = Class::CLASS;
  parcel_class->type = parcel->GetCanonicalName();
  parcel_class->interfaces.push_back("android.os.Parcelable");
  parcel_class->annotations = JavaAnnotationsFor(*parcel);
  if (parcel->GetParentType()) {
    parcel_class->modifiers |= STATIC;
  }

  if (parcel->IsGeneric()) {
    parcel_class->type += "<" + base::Join(parcel->GetTypeParameters(), ",") + ">";
  }

  for (const auto& variable : parcel->GetFields()) {
    std::ostringstream out;
    out << GenerateComments(*variable);
    out << GenerateAnnotations(*variable);
    out << "public ";

    if (variable->GetType().GetName() == "ParcelableHolder" || parcel->IsJavaOnlyImmutable()) {
      out << "final ";
    }
    out << JavaSignatureOf(variable->GetType()) << " " << variable->GetName();
    if (!parcel->IsJavaOnlyImmutable() && variable->GetDefaultValue()) {
      out << " = " << variable->ValueString(ConstantValueDecorator);
    } else if (variable->GetType().GetName() == "ParcelableHolder") {
      out << std::boolalpha;
      out << " = new " << JavaSignatureOf(variable->GetType()) << "(";
      if (parcel->IsVintfStability()) {
        out << "android.os.Parcelable.PARCELABLE_STABILITY_VINTF";
      } else {
        out << "android.os.Parcelable.PARCELABLE_STABILITY_LOCAL";
      }
      out << ")";
      out << std::noboolalpha;
    }
    out << ";\n";
    parcel_class->elements.push_back(std::make_shared<LiteralClassElement>(out.str()));
  }

  std::ostringstream out;
  if (parcel->IsJavaOnlyImmutable()) {
    auto builder_class = std::make_shared<Class>();
    builder_class->modifiers = PUBLIC | FINAL | STATIC;
    builder_class->what = Class::CLASS;
    builder_class->type = "Builder";

    out.str("");
    for (const auto& variable : parcel->GetFields()) {
      out << "private " << JavaSignatureOf(variable->GetType()) << " " << variable->GetName();
      if (variable->GetDefaultValue()) {
        out << " = " << variable->ValueString(ConstantValueDecorator);
      }
      out << ";\n";
      out << "public Builder " << SetterName(*variable) << "("
          << JavaSignatureOf(variable->GetType()) << " " << variable->GetName() << ") {\n"
          << "  "
          << "this." << variable->GetName() << " = " << variable->GetName() << ";\n"
          << "  return this;\n"
          << "}\n";
    }
    out << "public " << parcel->GetCanonicalName() << " build() {\n"
        << "  return new " << parcel->GetCanonicalName() << "(";
    std::vector<std::string> variables;
    std::transform(parcel->GetFields().begin(), parcel->GetFields().end(),
                   std::back_inserter(variables), [](const auto& f) { return f->GetName(); });
    out << base::Join(variables, ", ") << ");\n"
        << "}\n";
    builder_class->elements.push_back(std::make_shared<LiteralClassElement>(out.str()));
    parcel_class->elements.push_back(builder_class);
  }
  if (parcel->IsVintfStability()) {
    parcel_class->elements.push_back(std::make_shared<LiteralClassElement>(
        "@Override\n public final int getStability() { return "
        "android.os.Parcelable.PARCELABLE_STABILITY_VINTF; }\n"));
  }

  out.str("");
  out << "public static final android.os.Parcelable.Creator<" << parcel->GetName() << "> CREATOR = "
      << "new android.os.Parcelable.Creator<" << parcel->GetName() << ">() {\n";
  out << "  @Override\n";
  out << "  public " << parcel->GetName()
      << " createFromParcel(android.os.Parcel _aidl_source) {\n";
  if (parcel->IsJavaOnlyImmutable()) {
    out << "    return internalCreateFromParcel(_aidl_source);\n";
  } else {
    out << "    " << parcel->GetName() << " _aidl_out = new " << parcel->GetName() << "();\n";
    out << "    _aidl_out.readFromParcel(_aidl_source);\n";
    out << "    return _aidl_out;\n";
  }
  out << "  }\n";
  out << "  @Override\n";
  out << "  public " << parcel->GetName() << "[] newArray(int _aidl_size) {\n";
  out << "    return new " << parcel->GetName() << "[_aidl_size];\n";
  out << "  }\n";
  out << "};\n";
  parcel_class->elements.push_back(std::make_shared<LiteralClassElement>(out.str()));

  auto flag_variable = std::make_shared<Variable>("int", "_aidl_flag");
  auto parcel_variable = std::make_shared<Variable>("android.os.Parcel", "_aidl_parcel");

  auto write_method = std::make_shared<Method>();
  write_method->modifiers = PUBLIC | OVERRIDE | FINAL;
  write_method->returnType = "void";
  write_method->name = "writeToParcel";
  write_method->parameters.push_back(parcel_variable);
  write_method->parameters.push_back(flag_variable);
  write_method->statements = std::make_shared<StatementBlock>();

  out.str("");
  out << "int _aidl_start_pos = _aidl_parcel.dataPosition();\n"
      << "_aidl_parcel.writeInt(0);\n";
  write_method->statements->Add(std::make_shared<LiteralStatement>(out.str()));

  for (const auto& field : parcel->GetFields()) {
    string code;
    CodeWriterPtr writer = CodeWriter::ForString(&code);
    CodeGeneratorContext context{
        .writer = *(writer.get()),
        .typenames = typenames,
        .type = field->GetType(),
        .parcel = parcel_variable->name,
        .var = field->GetName(),
        .min_sdk_version = options.GetMinSdkVersion(),
        .write_to_parcel_flag = "_aidl_flag",
    };
    if (field->IsNew() && ShouldForceDowngradeFor(CommunicationSide::WRITE)) {
      context.writer.Write("if (false) {;\n");
      context.writer.Indent();
    }
    WriteToParcelFor(context);
    if (field->IsNew() && ShouldForceDowngradeFor(CommunicationSide::WRITE)) {
      context.writer.Dedent();
      context.writer.Write("};\n");
    }
    writer->Close();
    write_method->statements->Add(std::make_shared<LiteralStatement>(code));
  }

  out.str("");
  out << "int _aidl_end_pos = _aidl_parcel.dataPosition();\n"
      << "_aidl_parcel.setDataPosition(_aidl_start_pos);\n"
      << "_aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);\n"
      << "_aidl_parcel.setDataPosition(_aidl_end_pos);\n";

  write_method->statements->Add(std::make_shared<LiteralStatement>(out.str()));

  parcel_class->elements.push_back(write_method);

  if (parcel->IsJavaOnlyImmutable()) {
    auto constructor = std::make_shared<Method>();
    constructor->modifiers = PUBLIC;
    constructor->name = parcel->GetName();
    constructor->statements = std::make_shared<StatementBlock>();
    for (const auto& field : parcel->GetFields()) {
      constructor->parameters.push_back(
          std::make_shared<Variable>(JavaSignatureOf(field->GetType()), field->GetName()));
      out.str("");

      out << "this." << field->GetName() << " = ";
      if (field->GetType().GetName() == "List") {
        out << field->GetName() << " == null ? null : java.util.Collections.unmodifiableList("
            << field->GetName() << ");\n";
      } else if (field->GetType().GetName() == "Map") {
        out << field->GetName() << " == null ? null : java.util.Collections.unmodifiableMap("
            << field->GetName() << ");\n";
      } else {
        out << field->GetName() << ";\n";
      }
      constructor->statements->Add(std::make_shared<LiteralStatement>(out.str()));
    }
    parcel_class->elements.push_back(constructor);
  }

  // For an immutable parcelable, generate internalCreateFromParcel method.
  // Otherwise, generate readFromParcel method.
  auto read_or_create_method = std::make_shared<Method>();
  if (parcel->IsJavaOnlyImmutable()) {
    auto constructor = std::make_shared<Method>();
    read_or_create_method->modifiers = PRIVATE | STATIC;
    read_or_create_method->returnType = parcel->GetName();
    read_or_create_method->name = "internalCreateFromParcel";
    read_or_create_method->parameters.push_back(parcel_variable);
    read_or_create_method->statements = std::make_shared<StatementBlock>();
  } else {
    read_or_create_method->modifiers = PUBLIC | FINAL;
    read_or_create_method->returnType = "void";
    read_or_create_method->name = "readFromParcel";
    read_or_create_method->parameters.push_back(parcel_variable);
    read_or_create_method->statements = std::make_shared<StatementBlock>();
  }
  out.str("");
  const string builder_variable = "_aidl_parcelable_builder";
  if (parcel->IsJavaOnlyImmutable()) {
    out << "Builder " << builder_variable << " = new Builder();\n";
  }
  out << "int _aidl_start_pos = _aidl_parcel.dataPosition();\n"
      << "int _aidl_parcelable_size = _aidl_parcel.readInt();\n"
      << "try {\n"
      << "  if (_aidl_parcelable_size < 4) throw new "
         "android.os.BadParcelableException(\"Parcelable too small\");";
  if (parcel->IsJavaOnlyImmutable()) {
    out << " " << builder_variable << ".build()";
  }
  out << ";\n";

  read_or_create_method->statements->Add(std::make_shared<LiteralStatement>(out.str()));

  out.str("");
  out << "  if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return";
  if (parcel->IsJavaOnlyImmutable()) {
    out << " " << builder_variable << ".build()";
  }
  out << ";\n";

  std::shared_ptr<LiteralStatement> sizeCheck = std::make_shared<LiteralStatement>(out.str());
  // keep this across different fields in order to create the classloader
  // at most once.
  bool is_classloader_created = false;
  for (const auto& field : parcel->GetFields()) {
    read_or_create_method->statements->Add(sizeCheck);
    const auto field_variable_name =
        (parcel->IsJavaOnlyImmutable() ? "_aidl_temp_" : "") + field->GetName();
    string code;
    CodeWriterPtr writer = CodeWriter::ForString(&code);
    CodeGeneratorContext context{
        .writer = *(writer.get()),
        .typenames = typenames,
        .type = field->GetType(),
        .parcel = parcel_variable->name,
        .var = field_variable_name,
        .min_sdk_version = options.GetMinSdkVersion(),
        .is_classloader_created = &is_classloader_created,
    };
    context.writer.Indent();
    if (field->IsNew() && ShouldForceDowngradeFor(CommunicationSide::READ)) {
      context.writer.Write("if (false) {;\n");
      context.writer.Indent();
    }
    if (parcel->IsJavaOnlyImmutable()) {
      context.writer.Write("%s %s;\n", JavaSignatureOf(field->GetType()).c_str(),
                           field_variable_name.c_str());
    }
    CreateFromParcelFor(context);
    if (parcel->IsJavaOnlyImmutable()) {
      context.writer.Write("%s.%s(%s);\n", builder_variable.c_str(), SetterName(*field).c_str(),
                           field_variable_name.c_str());
    }
    if (field->IsNew() && ShouldForceDowngradeFor(CommunicationSide::READ)) {
      context.writer.Dedent();
      context.writer.Write("};\n");
    }
    writer->Close();
    read_or_create_method->statements->Add(std::make_shared<LiteralStatement>(code));
  }

  out.str("");
  out << "} finally {\n"
      << "  if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {\n"
      << "    throw new android.os.BadParcelableException(\"Overflow in the size of "
         "parcelable\");\n"
      << "  }\n"
      << "  _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);\n";
  if (parcel->IsJavaOnlyImmutable()) {
    out << "  return " << builder_variable << ".build();\n";
  }
  out << "}\n";

  read_or_create_method->statements->Add(std::make_shared<LiteralStatement>(out.str()));

  parcel_class->elements.push_back(read_or_create_method);

  string constants;
  GenerateConstantDeclarations(*CodeWriter::ForString(&constants), *parcel);
  parcel_class->elements.push_back(std::make_shared<LiteralClassElement>(constants));

  if (parcel->JavaDerive("toString")) {
    string to_string;
    GenerateToString(*CodeWriter::ForString(&to_string), *parcel, typenames, options);
    parcel_class->elements.push_back(std::make_shared<LiteralClassElement>(to_string));
  }

  if (parcel->JavaDerive("equals")) {
    string to_string;
    GenerateEqualsAndHashCode(*CodeWriter::ForString(&to_string), *parcel, typenames);
    parcel_class->elements.push_back(std::make_shared<LiteralClassElement>(to_string));
  }

  string describe_contents;
  GenerateParcelableDescribeContents(*CodeWriter::ForString(&describe_contents), *parcel,
                                     typenames);
  parcel_class->elements.push_back(std::make_shared<LiteralClassElement>(describe_contents));

  // all the nested types
  string code;
  auto writer = CodeWriter::ForString(&code);
  for (const auto& nested : parcel->GetNestedTypes()) {
    GenerateClass(*writer, *nested, typenames, options);
  }
  GenerateParcelHelpers(*writer, *parcel, typenames, options);
  writer->Close();
  parcel_class->elements.push_back(std::make_shared<LiteralClassElement>(code));

  return parcel_class;
}

void GenerateEnumClass(CodeWriter& out, const AidlEnumDeclaration& enum_decl) {
  const AidlTypeSpecifier& backing_type = enum_decl.GetBackingType();
  std::string raw_type = JavaSignatureOf(backing_type);
  std::string boxing_type = JavaBoxingTypeOf(backing_type);
  out << GenerateComments(enum_decl);
  out << GenerateAnnotations(enum_decl);
  out << "public ";
  if (enum_decl.GetParentType()) {
    out << "static ";
  }
  out << "@interface " << enum_decl.GetName() << " {\n";
  out.Indent();
  for (const auto& enumerator : enum_decl.GetEnumerators()) {
    out << GenerateComments(*enumerator);
    out << GenerateAnnotations(*enumerator);
    out << fmt::format("public static final {} {} = {};\n", raw_type, enumerator->GetName(),
                       enumerator->ValueString(backing_type, ConstantValueDecorator));
  }
  if (enum_decl.JavaDerive("toString")) {
    out << "interface $ {\n";
    out.Indent();
    out << "static String toString(" << raw_type << " _aidl_v) {\n";
    out.Indent();
    for (const auto& enumerator : enum_decl.GetEnumerators()) {
      out << "if (_aidl_v == " << enumerator->GetName() << ") return \"" << enumerator->GetName()
          << "\";\n";
    }
    out << "return " << boxing_type << ".toString(_aidl_v);\n";
    out.Dedent();
    out << "}\n";
    out << fmt::format(R"(static String arrayToString(Object _aidl_v) {{
  if (_aidl_v == null) return "null";
  Class<?> _aidl_cls = _aidl_v.getClass();
  if (!_aidl_cls.isArray()) throw new IllegalArgumentException("not an array: " + _aidl_v);
  Class<?> comp = _aidl_cls.getComponentType();
  java.util.StringJoiner _aidl_sj = new java.util.StringJoiner(", ", "[", "]");
  if (comp.isArray()) {{
    for (int _aidl_i = 0; _aidl_i < java.lang.reflect.Array.getLength(_aidl_v); _aidl_i++) {{
      _aidl_sj.add(arrayToString(java.lang.reflect.Array.get(_aidl_v, _aidl_i)));
    }}
  }} else {{
    if (_aidl_cls != {raw_type}[].class) throw new IllegalArgumentException("wrong type: " + _aidl_cls);
    for ({raw_type} e : ({raw_type}[]) _aidl_v) {{
      _aidl_sj.add(toString(e));
    }}
  }}
  return _aidl_sj.toString();
}}
)",
                       fmt::arg("raw_type", raw_type));
    out.Dedent();
    out << "}\n";
  }
  out.Dedent();
  out << "}\n";
}

void GenerateUnionClass(CodeWriter& out, const AidlUnionDecl* decl, const AidlTypenames& typenames,
                        const Options& options) {
  const string tag_type = "int";
  auto tag_type_specifier =
      typenames.MakeResolvedType(AIDL_LOCATION_HERE, tag_type, /* isArray */ false);
  const string clazz = decl->GetName();

  out << GenerateComments(*decl);
  out << GenerateAnnotations(*decl);

  out << "public ";
  if (decl->GetParentType()) {
    out << "static ";
  }
  out << "final class " + clazz + " implements android.os.Parcelable {\n";
  out.Indent();

  size_t tag_index = 0;
  out << "// tags for union fields\n";
  for (const auto& variable : decl->GetFields()) {
    auto signature = variable->Signature() + ";";
    out << "public final static " + tag_type + " " + variable->GetName() + " = " +
               std::to_string(tag_index++) + ";  // " + signature + "\n";
  }
  out << "\n";

  const auto final_opt = decl->IsJavaOnlyImmutable() ? "final " : "";
  out << "private " << final_opt << tag_type + " _tag;\n";
  out << "private " << final_opt << "Object _value;\n";
  out << "\n";

  AIDL_FATAL_IF(decl->GetFields().empty(), *decl) << "Union '" << clazz << "' is empty.";
  const auto& first_field = decl->GetFields()[0];
  const auto& first_type = JavaSignatureOf(first_field->GetType());
  const auto& first_value = first_field->ValueString(ConstantValueDecorator);

  // default ctor() inits with first member's default value
  out << "public " + clazz + "() {\n";
  out.Indent();
  out << first_type + " _value = "
      << (first_value.empty() ? DefaultJavaValueOf(first_field->GetType()) : first_value) << ";\n";
  out << "this._tag = " << first_field->GetName() << ";\n";
  out << "this._value = _value;\n";
  out.Dedent();
  out << "}\n\n";

  if (!decl->IsJavaOnlyImmutable()) {
    // private ctor(Parcel)
    out << "private " + clazz + "(android.os.Parcel _aidl_parcel) {\n";
    out << "  readFromParcel(_aidl_parcel);\n";
    out << "}\n\n";
  }

  // private ctor(tag, value)
  out << "private " + clazz + "(" + tag_type + " _tag, Object _value) {\n";
  out.Indent();
  out << "this._tag = _tag;\n";
  out << "this._value = _value;\n";
  out.Dedent();
  out << "}\n\n";

  // getTag()
  out << "public " + tag_type + " " + "getTag() {\n";
  out.Indent();
  out << "return _tag;\n";
  out.Dedent();
  out << "}\n\n";

  // value ctor, getter, setter(for mutable) for each field
  for (const auto& variable : decl->GetFields()) {
    out << "// " + variable->Signature() + ";\n\n";

    auto var_name = variable->GetName();
    auto var_type = JavaSignatureOf(variable->GetType());

    // value ctor
    out << GenerateComments(*variable);
    out << GenerateAnnotations(*variable);
    out << "public static " + clazz + " " + var_name + "(" + var_type + " _value) {\n";
    out.Indent();
    out << "return new " + clazz + "(" + var_name + ", _value);\n";
    out.Dedent();
    out << "}\n\n";

    // getter
    if (variable->GetType().IsGeneric()) {
      out << "@SuppressWarnings(\"unchecked\")\n";
    }
    out << "public " + var_type + " " + GetterName(*variable) + "() {\n";
    out.Indent();
    out << "_assertTag(" + var_name + ");\n";
    out << "return (" + var_type + ") _value;\n";
    out.Dedent();
    out << "}\n\n";

    // setter
    if (!decl->IsJavaOnlyImmutable()) {
      out << "public void " + SetterName(*variable) + "(" + var_type + " _value) {\n";
      out.Indent();
      out << "_set(" + var_name + ", _value);\n";
      out.Dedent();
      out << "}\n\n";
    }
  }

  if (decl->IsVintfStability()) {
    out << "@Override\n";
    out << "public final int getStability() {\n";
    out << "  return android.os.Parcelable.PARCELABLE_STABILITY_VINTF;\n";
    out << "}\n\n";
  }

  out << "public static final android.os.Parcelable.Creator<" << clazz << "> CREATOR = "
      << "new android.os.Parcelable.Creator<" << clazz << ">() {\n";
  out << "  @Override\n";
  out << "  public " << clazz << " createFromParcel(android.os.Parcel _aidl_source) {\n";
  if (decl->IsJavaOnlyImmutable()) {
    out << "    return internalCreateFromParcel(_aidl_source);\n";
  } else {
    out << "    return new " + clazz + "(_aidl_source);\n";
  }
  out << "  }\n";
  out << "  @Override\n";
  out << "  public " << clazz << "[] newArray(int _aidl_size) {\n";
  out << "    return new " << clazz << "[_aidl_size];\n";
  out << "  }\n";
  out << "};\n\n";

  auto write_to_parcel = [&](const AidlTypeSpecifier& type, std::string name, std::string parcel) {
    string code;
    CodeWriterPtr writer = CodeWriter::ForString(&code);
    CodeGeneratorContext context{
        .writer = *(writer.get()),
        .typenames = typenames,
        .type = type,
        .parcel = parcel,
        .var = name,
        .min_sdk_version = options.GetMinSdkVersion(),
        .write_to_parcel_flag = "_aidl_flag",
    };
    WriteToParcelFor(context);
    writer->Close();
    return code;
  };

  out << "@Override\n";
  out << "public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag) {\n";
  out.Indent();
  out << write_to_parcel(*tag_type_specifier, "_tag", "_aidl_parcel");
  out << "switch (_tag) {\n";
  for (const auto& variable : decl->GetFields()) {
    out << "case " + variable->GetName() + ":\n";
    out.Indent();
    if (variable->IsNew() && ShouldForceDowngradeFor(CommunicationSide::WRITE)) {
      out << "if (true) throw new IllegalArgumentException(\"union: unknown tag: \" + _tag);\n";
    }

    out << write_to_parcel(variable->GetType(), GetterName(*variable) + "()", "_aidl_parcel");
    out << "break;\n";
    out.Dedent();
  }
  out << "}\n";
  out.Dedent();
  out << "}\n\n";

  // keep this across different fields in order to create the classloader
  // at most once.
  bool is_classloader_created = false;
  auto read_from_parcel = [&](const AidlTypeSpecifier& type, std::string name, std::string parcel) {
    string code;
    CodeWriterPtr writer = CodeWriter::ForString(&code);
    CodeGeneratorContext context{
        .writer = *(writer.get()),
        .typenames = typenames,
        .type = type,
        .parcel = parcel,
        .var = name,
        .min_sdk_version = options.GetMinSdkVersion(),
        .is_classloader_created = &is_classloader_created,
    };
    CreateFromParcelFor(context);
    writer->Close();
    return code;
  };

  if (decl->IsJavaOnlyImmutable()) {
    // When it's immutable we don't need readFromParcel, but we can use it from createFromParcel
    out << "private static " + clazz +
               " internalCreateFromParcel(android.os.Parcel _aidl_parcel) {\n";
  } else {
    // Not override, but as a user-defined parcelable, this method should be public
    out << "public void readFromParcel(android.os.Parcel _aidl_parcel) {\n";
  }
  out.Indent();
  out << tag_type + " _aidl_tag;\n";
  out << read_from_parcel(*tag_type_specifier, "_aidl_tag", "_aidl_parcel");
  out << "switch (_aidl_tag) {\n";
  for (const auto& variable : decl->GetFields()) {
    auto var_name = variable->GetName();
    auto var_type = JavaSignatureOf(variable->GetType());
    out << "case " + var_name + ": {\n";
    out.Indent();
    if (variable->IsNew() && ShouldForceDowngradeFor(CommunicationSide::READ)) {
      out << "if (true) throw new IllegalArgumentException(\"union: unknown tag: \" + _tag);\n";
    }
    out << var_type + " _aidl_value;\n";
    out << read_from_parcel(variable->GetType(), "_aidl_value", "_aidl_parcel");
    if (decl->IsJavaOnlyImmutable()) {
      out << "return new " << clazz << "(_aidl_tag, _aidl_value); }\n";
    } else {
      out << "_set(_aidl_tag, _aidl_value);\n";
      out << "return; }\n";
    }
    out.Dedent();
  }
  out << "}\n";
  out << "throw new IllegalArgumentException(\"union: unknown tag: \" + _aidl_tag);\n";
  out.Dedent();
  out << "}\n\n";

  GenerateConstantDeclarations(out, *decl);

  GenerateParcelableDescribeContents(out, *decl, typenames);
  out << "\n";
  if (decl->JavaDerive("toString")) {
    GenerateToString(out, *decl, typenames, options);
  }

  if (decl->JavaDerive("equals")) {
    GenerateEqualsAndHashCode(out, *decl, typenames);
  }

  // helper: _assertTag
  out << "private void _assertTag(" + tag_type + " tag) {\n";
  out << "  if (getTag() != tag) {\n";
  out << "    throw new IllegalStateException(\"bad access: \" + _tagString(tag) + \", \" + "
         "_tagString(getTag()) + \" is available.\");\n";
  out << "  }\n";
  out << "}\n\n";

  // helper: _tagString
  out << "private String _tagString(" + tag_type + " _tag) {\n";
  out << "  switch (_tag) {\n";
  for (const auto& variable : decl->GetFields()) {
    auto var_name = variable->GetName();
    out << "  case " + var_name + ": return \"" + var_name + "\";\n";
  }
  out << "  }\n";
  out << "  throw new IllegalStateException(\"unknown field: \" + _tag);\n";
  out << "}\n";

  if (!decl->IsJavaOnlyImmutable()) {
    out << "\n";
    out << "private void _set(int _tag, Object _value) {\n";
    out.Indent();
    out << "this._tag = _tag;\n";
    out << "this._value = _value;\n";
    out.Dedent();
    out << "}\n";
  }

  // all the nested types
  for (const auto& nested : decl->GetNestedTypes()) {
    GenerateClass(out, *nested, typenames, options);
  }
  GenerateParcelHelpers(out, *decl, typenames, options);

  out.Dedent();
  out << "}\n";
}

std::string dump_location(const AidlNode& method) {
  return method.PrintLocation();
}

std::string GenerateJavaUnsupportedAppUsageParameters(const AidlAnnotation& a) {
  const std::map<std::string, std::string> params = a.AnnotationParams(ConstantValueDecorator);
  std::vector<string> parameters_decl;
  for (const auto& name_and_param : params) {
    const std::string& param_name = name_and_param.first;
    const std::string& param_value = name_and_param.second;
    parameters_decl.push_back(param_name + " = " + param_value);
  }
  parameters_decl.push_back("overrideSourcePosition=\"" + dump_location(a) + "\"");
  return "(" + Join(parameters_decl, ", ") + ")";
}

std::vector<std::string> GenerateJavaAnnotations(const AidlAnnotatable& a) {
  std::vector<std::string> result;

  const AidlAnnotation* unsupported_app_usage = a.UnsupportedAppUsage();
  if (unsupported_app_usage != nullptr) {
    result.emplace_back("@android.compat.annotation.UnsupportedAppUsage" +
                        GenerateJavaUnsupportedAppUsageParameters(*unsupported_app_usage));
  }

  for (const auto& annotation : a.GetAnnotations()) {
    if (annotation->GetType() == AidlAnnotation::Type::JAVA_PASSTHROUGH) {
      result.emplace_back(annotation->ParamValue<std::string>("annotation").value());
    }
    if (annotation->GetType() == AidlAnnotation::Type::JAVA_SUPPRESS_LINT) {
      std::vector<std::string> values;
      for (const auto& [name, value] : annotation->AnnotationParams(ConstantValueDecorator)) {
        values.emplace_back(name + " = " + value);
      }
      result.emplace_back("@android.annotation.SuppressLint(" + Join(values, ", ") + ")");
    }
  }

  return result;
}

std::optional<std::string> JavaPermissionAnnotation(const AidlAnnotatable& a) {
  if (auto enforce_expr = a.EnforceExpression(); enforce_expr) {
    return "@android.annotation.EnforcePermission(" +
           android::aidl::perm::AsJavaAnnotation(*enforce_expr.get()) + ")";
  } else if (a.IsPermissionNone()) {
    return "@android.annotation.RequiresNoPermission";
  }  // TODO: Add annotation for @PermissionManuallyEnforced

  return {};
}

struct JavaAnnotationsVisitor : AidlVisitor {
  JavaAnnotationsVisitor(std::vector<std::string>& result) : result(result) {}
  void Visit(const AidlTypeSpecifier& t) override { result = GenerateJavaAnnotations(t); }
  void Visit(const AidlInterface& t) override { ForDefinedType(t); }
  void Visit(const AidlParcelable& t) override { ForDefinedType(t); }
  void Visit(const AidlStructuredParcelable& t) override { ForDefinedType(t); }
  void Visit(const AidlUnionDecl& t) override { ForDefinedType(t); }
  void Visit(const AidlEnumDeclaration& t) override { ForDefinedType(t); }
  void Visit(const AidlEnumerator& e) override {
    if (e.IsDeprecated()) {
      result.push_back("@Deprecated");
    }
  }
  void Visit(const AidlMethod& m) override { ForMember(m); }
  void Visit(const AidlConstantDeclaration& c) override { ForMember(c); }
  void Visit(const AidlVariableDeclaration& v) override { ForMember(v); }
  std::vector<std::string>& result;

  void ForDefinedType(const AidlDefinedType& t) {
    result = GenerateJavaAnnotations(t);
    if (t.IsDeprecated()) {
      result.push_back("@Deprecated");
    }
  }
  template <typename Member>
  void ForMember(const Member& t) {
    result = GenerateJavaAnnotations(t.GetType());
    if (t.IsDeprecated()) {
      result.push_back("@Deprecated");
    }
    if (auto permission_annotation = JavaPermissionAnnotation(t.GetType()); permission_annotation) {
      result.push_back(*permission_annotation);
    }
  }
};

std::vector<std::string> JavaAnnotationsFor(const AidlNode& a) {
  std::vector<std::string> result;
  JavaAnnotationsVisitor visitor{result};
  a.DispatchVisit(visitor);
  return result;
}

void GenerateClass(CodeWriter& out, const AidlDefinedType& defined_type, const AidlTypenames& types,
                   const Options& options) {
  if (const AidlStructuredParcelable* parcelable = defined_type.AsStructuredParcelable();
      parcelable != nullptr) {
    GenerateParcelableClass(parcelable, types, options)->Write(&out);
  } else if (const AidlEnumDeclaration* enum_decl = defined_type.AsEnumDeclaration();
             enum_decl != nullptr) {
    GenerateEnumClass(out, *enum_decl);
  } else if (const AidlInterface* interface = defined_type.AsInterface(); interface != nullptr) {
    GenerateInterfaceClass(interface, types, options)->Write(&out);
  } else if (const AidlUnionDecl* union_decl = defined_type.AsUnionDeclaration();
             union_decl != nullptr) {
    GenerateUnionClass(out, union_decl, types, options);
  } else {
    AIDL_FATAL(defined_type) << "Unrecognized type sent for Java generation.";
  }
}

// In Java, there's 1:1 mapping between AIDL type and Java type. So we generate a single file for
// the type.
void GenerateJava(const std::string& filename, const Options& options, const AidlTypenames& types,
                  const AidlDefinedType& defined_type, const IoDelegate& io_delegate) {
  CodeWriterPtr code_writer = io_delegate.GetCodeWriter(filename);

  /* write header */ {
    auto& out = *code_writer;

    GenerateAutoGenHeader(out, options);

    if (const auto pkg = defined_type.GetPackage(); !pkg.empty()) {
      out << "package " << pkg << ";\n";
    }
  }

  GenerateClass(*code_writer, defined_type, types, options);
  AIDL_FATAL_IF(!code_writer->Close(), defined_type) << "I/O Error!";
}

}  // namespace java
}  // namespace aidl
}  // namespace android
