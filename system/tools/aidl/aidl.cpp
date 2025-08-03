/*
 * Copyright (C) 2015, The Android Open Source Project
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

#include "aidl.h"

#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/param.h>
#include <sys/stat.h>
#include <unistd.h>
#include <algorithm>
#include <iostream>
#include <map>
#include <memory>

#ifdef _WIN32
#include <io.h>
#include <direct.h>
#include <sys/stat.h>
#endif

#include <android-base/strings.h>

#include "aidl_checkapi.h"
#include "aidl_dumpapi.h"
#include "aidl_language.h"
#include "aidl_typenames.h"
#include "check_valid.h"
#include "generate_aidl_mappings.h"
#include "generate_cpp.h"
#include "generate_cpp_analyzer.h"
#include "generate_java.h"
#include "generate_ndk.h"
#include "generate_rust.h"
#include "import_resolver.h"
#include "logging.h"
#include "options.h"
#include "os.h"
#include "parser.h"
#include "preprocess.h"

#ifndef O_BINARY
#  define O_BINARY  0
#endif

using android::base::Error;
using android::base::Join;
using android::base::Result;
using android::base::Split;
using std::set;
using std::string;
using std::unique_ptr;
using std::unordered_set;
using std::vector;

namespace android {
namespace aidl {
namespace {

// Copied from android.is.IBinder.[FIRST|LAST]_CALL_TRANSACTION
const int kFirstCallTransaction = 1;
const int kLastCallTransaction = 0x00ffffff;

// Following IDs are all offsets from  kFirstCallTransaction

// IDs for meta transactions. Most of the meta transactions are implemented in
// the framework side (Binder.java or Binder.cpp). But these are the ones that
// are auto-implemented by the AIDL compiler.
const int kFirstMetaMethodId = kLastCallTransaction - kFirstCallTransaction;
const int kGetInterfaceVersionId = kFirstMetaMethodId;
const int kGetInterfaceHashId = kFirstMetaMethodId - 1;
// Additional meta transactions implemented by AIDL should use
// kFirstMetaMethodId -1, -2, ...and so on.

// Reserve 100 IDs for meta methods, which is more than enough. If we don't reserve,
// in the future, a newly added meta transaction ID will have a chance to
// collide with the user-defined methods that were added in the past. So,
// let's prevent users from using IDs in this range from the beginning.
const int kLastMetaMethodId = kFirstMetaMethodId - 99;

// Range of IDs that is allowed for user-defined methods.
const int kMinUserSetMethodId = 0;
const int kMaxUserSetMethodId = kLastMetaMethodId - 1;

bool check_filename(const std::string& filename, const Options& options, const AidlDefinedType& defined_type) {
    const char* p;
    string expected;
    string fn;
    size_t len;
    bool valid = false;

    if (!IoDelegate::GetAbsolutePath(filename, &fn)) {
      return false;
    }

    const std::string package = defined_type.GetPackage();
    if (!package.empty()) {
        expected = package;
        expected += '.';
    }

    len = expected.length();
    for (size_t i=0; i<len; i++) {
        if (expected[i] == '.') {
            expected[i] = OS_PATH_SEPARATOR;
        }
    }

    const std::string name = defined_type.GetName();
    expected.append(name, 0, name.find('.'));

    expected += ".aidl";

    len = fn.length();
    valid = (len >= expected.length());

    if (valid) {
        p = fn.c_str() + (len - expected.length());

#ifdef _WIN32
        if (OS_PATH_SEPARATOR != '/') {
            // Input filename under cygwin most likely has / separators
            // whereas the expected string uses \\ separators. Adjust
            // them accordingly.
          for (char *c = const_cast<char *>(p); *c; ++c) {
                if (*c == '/') *c = OS_PATH_SEPARATOR;
            }
        }
#endif

        // aidl assumes case-insensitivity on Mac Os and Windows.
#if defined(__linux__)
        valid = (expected == p);
#else
        valid = !strcasecmp(expected.c_str(), p);
#endif
    }

    if (!valid) {
      AIDL_ERROR(defined_type) << name << " should be declared in a file called " << expected;
      return false;
    }

    // Make sure that base directory of this AIDL file is one of the import directories. Base
    // directory of an AIDL file `some/dir/package/name/Iface.aidl` is defined as `some/dir` if the
    // package name is `package.name` and the type name is `Iface`. This check is needed because the
    // build system that invokes this aidl compiler doesn't have knowledge about what the package
    // name is for a given aidl file; because the build system doesn't parse the file. The only hint
    // that user can give to the build system is the import path. In the above case, by specifying
    // the import path to be `some/dir/`, the build system can know that the package name is what
    // follows the import path: `package.name`.
    // This is not used when the user has specified the exact output file path though.
    if (options.OutputFile().empty()) {
      std::string_view basedir(filename);
      basedir.remove_suffix(expected.length());
      if (basedir.empty()) {
        basedir = "./";
      }
      const std::set<std::string>& i = options.ImportDirs();
      if (std::find_if(i.begin(), i.end(), [&basedir](const std::string& i) {
            return basedir == i;
      }) == i.end()) {
        AIDL_ERROR(defined_type) << "directory " << basedir << " is not found in any of the import paths:\n - " <<
          base::Join(options.ImportDirs(), "\n - ");
        return false;
      }
    }

    // All checks passed
    return true;
}

bool write_dep_file(const Options& options, const AidlDefinedType& defined_type,
                    const vector<string>& imports, const IoDelegate& io_delegate,
                    const string& input_file, const string& output_file) {
  string dep_file_name = options.DependencyFile();
  if (dep_file_name.empty() && options.AutoDepFile()) {
    dep_file_name = output_file + ".d";
  }

  if (dep_file_name.empty()) {
    return true;  // nothing to do
  }

  CodeWriterPtr writer = io_delegate.GetCodeWriter(dep_file_name);
  if (!writer) {
    AIDL_ERROR(dep_file_name) << "Could not open dependency file.";
    return false;
  }

  vector<string> source_aidl = {input_file};
  for (const auto& import : imports) {
    source_aidl.push_back(import);
  }

  // Encode that the output file depends on aidl input files.
  if (defined_type.AsUnstructuredParcelable() != nullptr &&
      options.TargetLanguage() == Options::Language::JAVA) {
    // Legacy behavior. For parcelable declarations in Java, don't emit output file as
    // the dependency target. b/141372861
    writer->Write(" : \\\n");
  } else {
    writer->Write("%s : \\\n", output_file.c_str());
  }
  writer->Write("  %s", Join(source_aidl, " \\\n  ").c_str());
  writer->Write("\n");

  if (!options.DependencyFileNinja()) {
    writer->Write("\n");
    // Output "<input_aidl_file>: " so make won't fail if the input .aidl file
    // has been deleted, moved or renamed in incremental build.
    for (const auto& src : source_aidl) {
      writer->Write("%s :\n", src.c_str());
    }
  }

  if (options.IsCppOutput()) {
    if (!options.DependencyFileNinja()) {
      using ::android::aidl::cpp::ClassNames;
      using ::android::aidl::cpp::HeaderFile;
      vector<string> headers;
      for (ClassNames c : {ClassNames::CLIENT, ClassNames::SERVER, ClassNames::RAW}) {
        headers.push_back(options.OutputHeaderDir() +
                          HeaderFile(defined_type, c, false /* use_os_sep */));
      }

      writer->Write("\n");

      // Generated headers also depend on the source aidl files.
      writer->Write("%s : \\\n    %s\n", Join(headers, " \\\n    ").c_str(),
                    Join(source_aidl, " \\\n    ").c_str());
    }
  }

  return true;
}

// Returns the path to the destination file of `defined_type`.
string GetOutputFilePath(const Options& options, const AidlDefinedType& defined_type) {
  string result = options.OutputDir();

  // add the package
  string package = defined_type.GetPackage();
  if (!package.empty()) {
    for (auto& c : package) {
      if (c == '.') {
        c = OS_PATH_SEPARATOR;
      }
    }
    result += package;
    result += OS_PATH_SEPARATOR;
  }

  // add the filename
  result += defined_type.GetName();
  if (options.TargetLanguage() == Options::Language::JAVA) {
    result += ".java";
  } else if (options.IsCppOutput()) {
    result += ".cpp";
  } else if (options.TargetLanguage() == Options::Language::RUST) {
    result += ".rs";
  } else {
    AIDL_FATAL("Unknown target language");
    return "";
  }

  return result;
}

bool CheckAndAssignMethodIDs(const std::vector<std::unique_ptr<AidlMethod>>& items) {
  // Check whether there are any methods with manually assigned id's and any
  // that are not. Either all method id's must be manually assigned or all of
  // them must not. Also, check for uplicates of user set ID's and that the
  // ID's are within the proper bounds.
  set<int> usedIds;
  bool hasUnassignedIds = false;
  bool hasAssignedIds = false;
  int newId = kMinUserSetMethodId;
  for (const auto& item : items) {
    // However, meta transactions that are added by the AIDL compiler are
    // exceptions. They have fixed IDs but allowed to be with user-defined
    // methods having auto-assigned IDs. This is because the Ids of the meta
    // transactions must be stable during the entire lifetime of an interface.
    // In other words, their IDs must be the same even when new user-defined
    // methods are added.
    if (!item->IsUserDefined()) {
      continue;
    }
    if (item->HasId()) {
      hasAssignedIds = true;
    } else {
      item->SetId(newId++);
      hasUnassignedIds = true;
    }

    if (hasAssignedIds && hasUnassignedIds) {
      AIDL_ERROR(item) << "You must either assign id's to all methods or to none of them.";
      return false;
    }

    // Ensure that the user set id is not duplicated.
    if (usedIds.find(item->GetId()) != usedIds.end()) {
      // We found a duplicate id, so throw an error.
      AIDL_ERROR(item) << "Found duplicate method id (" << item->GetId() << ") for method "
                       << item->GetName();
      return false;
    }
    usedIds.insert(item->GetId());

    // Ensure that the user set id is within the appropriate limits
    if (item->GetId() < kMinUserSetMethodId || item->GetId() > kMaxUserSetMethodId) {
      AIDL_ERROR(item) << "Found out of bounds id (" << item->GetId() << ") for method "
                       << item->GetName() << ". Value for id must be between "
                       << kMinUserSetMethodId << " and " << kMaxUserSetMethodId << " inclusive.";
      return false;
    }
  }

  return true;
}

bool ValidateAnnotationContext(const AidlDocument& doc) {
  struct AnnotationValidator : AidlVisitor {
    bool success = true;

    void Check(const AidlAnnotatable& annotatable, AidlAnnotation::TargetContext context) {
      for (const auto& annot : annotatable.GetAnnotations()) {
        if (!annot->CheckContext(context)) {
          success = false;
        }
      }
    }
    void Visit(const AidlInterface& m) override {
      Check(m, AidlAnnotation::CONTEXT_TYPE_INTERFACE);
    }
    void Visit(const AidlParcelable& m) override {
      Check(m, AidlAnnotation::CONTEXT_TYPE_UNSTRUCTURED_PARCELABLE);
    }
    void Visit(const AidlStructuredParcelable& m) override {
      Check(m, AidlAnnotation::CONTEXT_TYPE_STRUCTURED_PARCELABLE);
    }
    void Visit(const AidlEnumDeclaration& m) override {
      Check(m, AidlAnnotation::CONTEXT_TYPE_ENUM);
    }
    void Visit(const AidlUnionDecl& m) override { Check(m, AidlAnnotation::CONTEXT_TYPE_UNION); }
    void Visit(const AidlMethod& m) override {
      Check(m.GetType(), AidlAnnotation::CONTEXT_TYPE_SPECIFIER | AidlAnnotation::CONTEXT_METHOD);
      for (const auto& arg : m.GetArguments()) {
        Check(arg->GetType(), AidlAnnotation::CONTEXT_TYPE_SPECIFIER);
      }
    }
    void Visit(const AidlConstantDeclaration& m) override {
      Check(m.GetType(), AidlAnnotation::CONTEXT_TYPE_SPECIFIER | AidlAnnotation::CONTEXT_CONST);
    }
    void Visit(const AidlVariableDeclaration& m) override {
      Check(m.GetType(), AidlAnnotation::CONTEXT_TYPE_SPECIFIER | AidlAnnotation::CONTEXT_FIELD);
    }
    void Visit(const AidlTypeSpecifier& m) override {
      // nested generic type parameters are checked as well
      if (m.IsGeneric()) {
        for (const auto& tp : m.GetTypeParameters()) {
          Check(*tp, AidlAnnotation::CONTEXT_TYPE_SPECIFIER);
        }
      }
    }
  };

  AnnotationValidator validator;
  VisitTopDown(validator, doc);
  return validator.success;
}

bool ValidateHeaders(Options::Language language, const AidlDocument& doc) {
  typedef std::string (AidlParcelable::*GetHeader)() const;

  struct HeaderVisitor : AidlVisitor {
    bool success = true;
    const char* str = nullptr;
    GetHeader getHeader = nullptr;

    void check(const AidlParcelable& p) {
      if ((p.*getHeader)().empty()) {
        AIDL_ERROR(p) << "Unstructured parcelable \"" << p.GetName() << "\" must have " << str
                      << " defined.";
        success = false;
      }
    }

    void Visit(const AidlParcelable& p) override { check(p); }
    void Visit(const AidlTypeSpecifier& m) override {
      auto type = m.GetDefinedType();
      if (type) {
        auto unstructured = type->AsUnstructuredParcelable();
        if (unstructured) check(*unstructured);
      }
    }
  };

  if (language == Options::Language::CPP) {
    HeaderVisitor validator;
    validator.str = "cpp_header";
    validator.getHeader = &AidlParcelable::GetCppHeader;
    VisitTopDown(validator, doc);
    return validator.success;
  } else if (language == Options::Language::NDK) {
    HeaderVisitor validator;
    validator.str = "ndk_header";
    validator.getHeader = &AidlParcelable::GetNdkHeader;
    VisitTopDown(validator, doc);
    return validator.success;
  } else if (language == Options::Language::RUST) {
    HeaderVisitor validator;
    validator.str = "rust_type";
    validator.getHeader = &AidlParcelable::GetRustType;
    VisitTopDown(validator, doc);
    return validator.success;
  }
  return true;
}

}  // namespace

namespace internals {

// WARNING: options are passed here and below, but only the file contents should determine
// what is generated for portability.
AidlError load_and_validate_aidl(const std::string& input_file_name, const Options& options,
                                 const IoDelegate& io_delegate, AidlTypenames* typenames,
                                 vector<string>* imported_files) {
  AidlError err = AidlError::OK;

  //////////////////////////////////////////////////////////////////////////
  // Loading phase
  //////////////////////////////////////////////////////////////////////////

  // Parse the main input file
  const AidlDocument* document = Parser::Parse(input_file_name, io_delegate, *typenames);
  if (document == nullptr) {
    return AidlError::PARSE_ERROR;
  }
  int num_top_level_decls = 0;
  for (const auto& type : document->DefinedTypes()) {
    if (type->AsUnstructuredParcelable() == nullptr) {
      num_top_level_decls++;
      if (num_top_level_decls > 1) {
        AIDL_ERROR(*type) << "You must declare only one type per file.";
        return AidlError::BAD_TYPE;
      }
    }
  }

  // Import the preprocessed file
  for (const string& filename : options.PreprocessedFiles()) {
    auto preprocessed = Parser::Parse(filename, io_delegate, *typenames, /*is_preprocessed=*/true);
    if (!preprocessed) {
      return AidlError::BAD_PRE_PROCESSED_FILE;
    }
  }

  // Find files to import and parse them
  vector<string> import_paths;
  ImportResolver import_resolver{io_delegate, input_file_name, options.ImportDirs()};
  for (const auto& import : document->Imports()) {
    if (typenames->IsIgnorableImport(import)) {
      // There are places in the Android tree where an import doesn't resolve,
      // but we'll pick the type up through the preprocessed types.
      // This seems like an error, but legacy support demands we support it...
      continue;
    }
    string import_path = import_resolver.FindImportFile(import);
    if (import_path.empty()) {
      err = AidlError::BAD_IMPORT;
      continue;
    }

    import_paths.emplace_back(import_path);

    auto imported_doc = Parser::Parse(import_path, io_delegate, *typenames);
    if (imported_doc == nullptr) {
      AIDL_ERROR(import_path) << "error while importing " << import_path << " for " << import;
      err = AidlError::BAD_IMPORT;
      continue;
    }
  }
  if (err != AidlError::OK) {
    return err;
  }

  TypeResolver resolver = [&](const AidlDefinedType* scope, AidlTypeSpecifier* type) {
    // resolve with already loaded types
    if (type->Resolve(*typenames, scope)) {
      return true;
    }
    const string import_path = import_resolver.FindImportFile(scope->ResolveName(type->GetName()));
    if (import_path.empty()) {
      return false;
    }
    import_paths.push_back(import_path);
    auto imported_doc = Parser::Parse(import_path, io_delegate, *typenames);
    if (imported_doc == nullptr) {
      AIDL_ERROR(import_path) << "error while importing " << import_path << " for " << import_path;
      return false;
    }

    // now, try to resolve it again
    if (!type->Resolve(*typenames, scope)) {
      AIDL_ERROR(type) << "Can't resolve " << type->GetName();
      return false;
    }
    return true;
  };

  // Resolve the unresolved references
  if (!ResolveReferences(*document, resolver)) {
    return AidlError::BAD_TYPE;
  }

  if (!typenames->Autofill()) {
    return AidlError::BAD_TYPE;
  }

  //////////////////////////////////////////////////////////////////////////
  // Validation phase
  //////////////////////////////////////////////////////////////////////////

  const auto& types = document->DefinedTypes();
  const int num_defined_types = types.size();
  for (const auto& defined_type : types) {
    AIDL_FATAL_IF(defined_type == nullptr, document);

    // Ensure type is exactly one of the following:
    AidlInterface* interface = defined_type->AsInterface();
    AidlStructuredParcelable* parcelable = defined_type->AsStructuredParcelable();
    AidlParcelable* unstructured_parcelable = defined_type->AsUnstructuredParcelable();
    AidlEnumDeclaration* enum_decl = defined_type->AsEnumDeclaration();
    AidlUnionDecl* union_decl = defined_type->AsUnionDeclaration();
    AIDL_FATAL_IF(
        !!interface + !!parcelable + !!unstructured_parcelable + !!enum_decl + !!union_decl != 1,
        defined_type);

    // Ensure that foo.bar.IFoo is defined in <some_path>/foo/bar/IFoo.aidl
    if (num_defined_types == 1 && !check_filename(input_file_name, options, *defined_type)) {
      return AidlError::BAD_PACKAGE;
    }

    {
      bool valid_type = true;

      if (!defined_type->CheckValid(*typenames)) {
        valid_type = false;
      }

      if (!defined_type->LanguageSpecificCheckValid(options.TargetLanguage())) {
        valid_type = false;
      }

      if (!valid_type) {
        return AidlError::BAD_TYPE;
      }
    }

    if (unstructured_parcelable != nullptr) {
      auto lang = options.TargetLanguage();
      bool isStable = unstructured_parcelable->IsStableApiParcelable(lang);
      if (options.IsStructured() && !isStable) {
        AIDL_ERROR(unstructured_parcelable)
            << "Cannot declare unstructured parcelable in a --structured interface. Parcelable "
               "must be defined in AIDL directly.";
        return AidlError::NOT_STRUCTURED;
      }
      if (options.FailOnParcelable() || lang == Options::Language::NDK ||
          lang == Options::Language::RUST) {
        AIDL_ERROR(unstructured_parcelable)
            << "Refusing to generate code with unstructured parcelables. Declared parcelables "
               "should be in their own file and/or cannot be used with --structured interfaces.";
        return AidlError::FOUND_PARCELABLE;
      }
    }

    if (defined_type->IsVintfStability()) {
      bool success = true;
      if (options.GetStability() != Options::Stability::VINTF) {
        AIDL_ERROR(defined_type)
            << "Must compile @VintfStability type w/ aidl_interface 'stability: \"vintf\"'";
        success = false;
      }
      if (!options.IsStructured()) {
        AIDL_ERROR(defined_type)
            << "Must compile @VintfStability type w/ aidl_interface --structured";
        success = false;
      }
      if (!success) return AidlError::NOT_STRUCTURED;
    }
  }

  // We only want to mutate the types defined in this AIDL file or subtypes. We can't
  // use IterateTypes, as this will re-mutate types that have already been loaded
  // when AidlTypenames is re-used (such as in dump API).
  class MetaMethodVisitor : public AidlVisitor {
   public:
    MetaMethodVisitor(const Options* options, const AidlTypenames* typenames)
        : mOptions(options), mTypenames(typenames) {}
    virtual void Visit(const AidlInterface& const_interface) {
      // TODO: we do not have mutable visitor infrastructure.
      AidlInterface* interface = const_cast<AidlInterface*>(&const_interface);
      if (mOptions->Version() > 0) {
        auto ret = mTypenames->MakeResolvedType(AIDL_LOCATION_HERE, "int", false);
        vector<unique_ptr<AidlArgument>>* args = new vector<unique_ptr<AidlArgument>>();
        auto method = std::make_unique<AidlMethod>(AIDL_LOCATION_HERE, false, ret.release(),
                                                   "getInterfaceVersion", args, Comments{},
                                                   kGetInterfaceVersionId);
        interface->AddMethod(std::move(method));
      }
      // add the meta-method 'string getInterfaceHash()' if hash is specified.
      if (!mOptions->Hash().empty()) {
        auto ret = mTypenames->MakeResolvedType(AIDL_LOCATION_HERE, "String", false);
        vector<unique_ptr<AidlArgument>>* args = new vector<unique_ptr<AidlArgument>>();
        auto method =
            std::make_unique<AidlMethod>(AIDL_LOCATION_HERE, false, ret.release(),
                                         kGetInterfaceHash, args, Comments{}, kGetInterfaceHashId);
        interface->AddMethod(std::move(method));
      }
    }

   private:
    const Options* mOptions;
    const AidlTypenames* mTypenames;
  };
  MetaMethodVisitor meta_method_visitor(&options, typenames);
  for (const auto& defined_type : types) {
    VisitTopDown(meta_method_visitor, *defined_type);
  }

  typenames->IterateTypes([&](const AidlDefinedType& type) {
    const AidlInterface* interface = type.AsInterface();
    if (interface == nullptr) return;
    if (!CheckAndAssignMethodIDs(interface->GetMethods())) {
      err = AidlError::BAD_METHOD_ID;
    }
  });
  if (err != AidlError::OK) {
    return err;
  }

  for (const auto& doc : typenames->AllDocuments()) {
    VisitTopDown([](const AidlNode& n) { n.MarkVisited(); }, *doc);
  }

  if (!CheckValid(*document, options)) {
    return AidlError::BAD_TYPE;
  }

  if (!ValidateAnnotationContext(*document)) {
    return AidlError::BAD_TYPE;
  }

  if (!ValidateHeaders(options.TargetLanguage(), *document)) {
    return AidlError::BAD_TYPE;
  }

  if (!Diagnose(*document, options.GetDiagnosticMapping())) {
    return AidlError::BAD_TYPE;
  }

  typenames->IterateTypes([&](const AidlDefinedType& type) {
    if (!type.LanguageSpecificCheckValid(options.TargetLanguage())) {
      err = AidlError::BAD_TYPE;
    }

    bool isStable = type.IsStableApiParcelable(options.TargetLanguage());

    if (options.IsStructured() && type.AsUnstructuredParcelable() != nullptr && !isStable) {
      err = AidlError::NOT_STRUCTURED;
      AIDL_ERROR(type) << type.GetCanonicalName()
                       << " is not structured, but this is a structured interface in "
                       << to_string(options.TargetLanguage());
    }
    if (options.GetStability() == Options::Stability::VINTF && !type.IsVintfStability() &&
        !isStable) {
      err = AidlError::NOT_STRUCTURED;
      AIDL_ERROR(type) << type.GetCanonicalName()
                       << " does not have VINTF level stability (marked @VintfStability), but this "
                          "interface requires it in "
                       << to_string(options.TargetLanguage());
    }

    // Ensure that untyped List/Map is not used in a parcelable, a union and a stable interface.

    std::function<void(const AidlTypeSpecifier&, const AidlNode*)> check_untyped_container =
        [&err, &check_untyped_container](const AidlTypeSpecifier& type, const AidlNode* node) {
          if (type.IsGeneric()) {
            std::for_each(type.GetTypeParameters().begin(), type.GetTypeParameters().end(),
                          [&node, &check_untyped_container](auto& nested) {
                            check_untyped_container(*nested, node);
                          });
            return;
          }
          if (type.GetName() == "List" || type.GetName() == "Map") {
            err = AidlError::BAD_TYPE;
            AIDL_ERROR(node)
                << "Encountered an untyped List or Map. The use of untyped List/Map is prohibited "
                << "because it is not guaranteed that the objects in the list are recognizable in "
                << "the receiving side. Consider switching to an array or a generic List/Map.";
          }
        };

    if (type.AsInterface() && options.IsStructured()) {
      for (const auto& method : type.GetMethods()) {
        check_untyped_container(method->GetType(), method.get());
        for (const auto& arg : method->GetArguments()) {
          check_untyped_container(arg->GetType(), method.get());
        }
      }
    }
    for (const auto& field : type.GetFields()) {
      check_untyped_container(field->GetType(), field.get());
    }
  });

  if (err != AidlError::OK) {
    return err;
  }

  if (imported_files != nullptr) {
    *imported_files = import_paths;
  }

  return AidlError::OK;
}

void markNewAdditions(AidlTypenames& typenames, const AidlTypenames& previous_typenames) {
  for (const AidlDefinedType* type : typenames.AllDefinedTypes()) {
    const AidlDefinedType* previous_type = nullptr;
    for (const AidlDefinedType* previous : previous_typenames.AllDefinedTypes()) {
      if (type->GetCanonicalName() == previous->GetCanonicalName()) {
        previous_type = previous;
      }
    }
    if (previous_type == nullptr) {
      // This is a new type for this version.
      continue;
    }
    if (type->AsInterface()) {
      for (const std::unique_ptr<AidlMethod>& member : type->AsInterface()->GetMethods()) {
        if (!member->IsUserDefined()) continue;
        bool found = false;
        for (const std::unique_ptr<AidlMethod>& previous_member : previous_type->GetMethods()) {
          if (previous_member->GetName() == member->GetName()) {
            found = true;
          }
        }
        if (!found) member->MarkNew();
      }
    } else if (type->AsStructuredParcelable() || type->AsUnionDeclaration()) {
      for (const std::unique_ptr<AidlVariableDeclaration>& member : type->GetFields()) {
        if (!member->IsUserDefined()) continue;
        bool found = false;
        for (const std::unique_ptr<AidlVariableDeclaration>& previous_member :
             previous_type->GetFields()) {
          if (previous_member->GetName() == member->GetName()) {
            found = true;
          }
        }
        if (!found) member->MarkNew();
      }
    } else if (type->AsEnumDeclaration() || type->AsUnstructuredParcelable()) {
      // We have nothing to do for these types
    } else {
      AIDL_FATAL(type) << "Unexpected type when looking for new members";
    }
  }
}

} // namespace internals

bool compile_aidl(const Options& options, const IoDelegate& io_delegate) {
  const Options::Language lang = options.TargetLanguage();

  // load the previously frozen version if it exists
  Result<AidlTypenames> previous_typenames_result;
  if (options.IsLatestUnfrozenVersion()) {
    // TODO(b/292005937) Once LoadApiDump can handle the OS_PATH_SEPARATOR at
    // the end of PreviousApiDir, we can stop passing in a substr without it.
    AIDL_FATAL_IF(options.PreviousApiDir().back() != OS_PATH_SEPARATOR, "Expecting a separator");
    previous_typenames_result =
        LoadApiDump(options.WithNoWarnings().WithoutVersion().AsPreviousVersion(), io_delegate,
                    options.PreviousApiDir().substr(0, options.PreviousApiDir().size() - 1));
    if (!previous_typenames_result.ok()) {
      AIDL_ERROR(options.PreviousApiDir())
          << "Failed to load api dump for '" << options.PreviousApiDir()
          << "'. Error: " << previous_typenames_result.error().message();
      return false;
    }
  }

  for (const string& input_file : options.InputFiles()) {
    AidlTypenames typenames;

    vector<string> imported_files;

    AidlError aidl_err = internals::load_and_validate_aidl(input_file, options, io_delegate,
                                                           &typenames, &imported_files);
    if (aidl_err != AidlError::OK) {
      return false;
    }

    if (options.IsLatestUnfrozenVersion()) {
      internals::markNewAdditions(typenames, previous_typenames_result.value());
    }

    for (const auto& defined_type : typenames.MainDocument().DefinedTypes()) {
      AIDL_FATAL_IF(defined_type == nullptr, input_file);

      string output_file_name = options.OutputFile();
      // if needed, generate the output file name from the base folder
      if (output_file_name.empty() && !options.OutputDir().empty()) {
        output_file_name = GetOutputFilePath(options, *defined_type);
        if (output_file_name.empty()) {
          return false;
        }
      }

      if (!write_dep_file(options, *defined_type, imported_files, io_delegate, input_file,
                          output_file_name)) {
        return false;
      }

      bool success = false;
      if (lang == Options::Language::CPP) {
        success =
            cpp::GenerateCpp(output_file_name, options, typenames, *defined_type, io_delegate);
      } else if (lang == Options::Language::NDK) {
        ndk::GenerateNdk(output_file_name, options, typenames, *defined_type, io_delegate);
        success = true;
      } else if (lang == Options::Language::JAVA) {
        if (defined_type->AsUnstructuredParcelable() != nullptr) {
          // Legacy behavior. For parcelable declarations in Java, don't generate code.
          success = true;
          // If the output directory is set, we're not going to be dropping a file right
          // next to the .aidl code, so we shouldn't be clobbering an existing
          // implementation unless someone has set their output dir to be their source
          // dir explicitly.
          // The build system expects us to produce an output file, so produce an empty one.
          if (!options.OutputDir().empty()) {
            io_delegate.GetCodeWriter(output_file_name)->Close();
          }
        } else {
          java::GenerateJava(output_file_name, options, typenames, *defined_type, io_delegate);
          success = true;
        }
      } else if (lang == Options::Language::RUST) {
        rust::GenerateRust(output_file_name, options, typenames, *defined_type, io_delegate);
        success = true;
      } else if (lang == Options::Language::CPP_ANALYZER) {
        success = cpp::GenerateCppAnalyzer(output_file_name, options, typenames, *defined_type,
                                           io_delegate);
      } else {
        AIDL_FATAL(input_file) << "Should not reach here.";
      }
      if (!success) {
        return false;
      }
    }
  }
  return true;
}

bool dump_mappings(const Options& options, const IoDelegate& io_delegate) {
  android::aidl::mappings::SignatureMap all_mappings;
  for (const string& input_file : options.InputFiles()) {
    AidlTypenames typenames;
    vector<string> imported_files;

    AidlError aidl_err = internals::load_and_validate_aidl(input_file, options, io_delegate,
                                                           &typenames, &imported_files);
    if (aidl_err != AidlError::OK) {
      return false;
    }
    for (const auto& defined_type : typenames.MainDocument().DefinedTypes()) {
      auto mappings = mappings::generate_mappings(defined_type.get());
      all_mappings.insert(mappings.begin(), mappings.end());
    }
  }
  std::stringstream mappings_str;
  for (const auto& mapping : all_mappings) {
    mappings_str << mapping.first << "\n" << mapping.second << "\n";
  }
  auto code_writer = io_delegate.GetCodeWriter(options.OutputFile());
  code_writer->Write("%s", mappings_str.str().c_str());
  return true;
}

int aidl_entry(const Options& options, const IoDelegate& io_delegate) {
  AidlErrorLog::clearError();
  AidlNode::ClearUnvisitedNodes();

  bool success = false;
  if (options.Ok()) {
    switch (options.GetTask()) {
      case Options::Task::HELP:
        success = true;
        break;
      case Options::Task::COMPILE:
        success = android::aidl::compile_aidl(options, io_delegate);
        break;
      case Options::Task::PREPROCESS:
        success = android::aidl::Preprocess(options, io_delegate);
        break;
      case Options::Task::DUMP_API:
        success = android::aidl::dump_api(options, io_delegate);
        break;
      case Options::Task::CHECK_API:
        success = android::aidl::check_api(options, io_delegate);
        break;
      case Options::Task::DUMP_MAPPINGS:
        success = android::aidl::dump_mappings(options, io_delegate);
        break;
      default:
        AIDL_FATAL(AIDL_LOCATION_HERE)
            << "Unrecognized task: " << static_cast<size_t>(options.GetTask());
    }
  } else {
    AIDL_ERROR(options.GetErrorMessage()) << options.GetUsage();
  }

  const bool reportedError = AidlErrorLog::hadError();
  AIDL_FATAL_IF(success == reportedError, AIDL_LOCATION_HERE)
      << "Compiler returned success " << success << " but did" << (reportedError ? "" : " not")
      << " emit error logs";

  if (success) {
    auto locations = AidlNode::GetLocationsOfUnvisitedNodes();
    if (!locations.empty()) {
      for (const auto& location : locations) {
        AIDL_ERROR(location) << "AidlNode at location was not visited!";
      }
      AIDL_FATAL(AIDL_LOCATION_HERE)
          << "The AIDL AST was not processed fully. Please report an issue.";
    }
  }

  return success ? 0 : 1;
}

}  // namespace aidl
}  // namespace android
