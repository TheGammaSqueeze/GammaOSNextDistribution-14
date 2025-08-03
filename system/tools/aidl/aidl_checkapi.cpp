/*
 * Copyright (C) 2018, The Android Open Source Project
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

#include <map>
#include <string>
#include <vector>

#include <android-base/result.h>
#include <android-base/strings.h>
#include <gtest/gtest.h>

#include "aidl_dumpapi.h"
#include "aidl_language.h"
#include "import_resolver.h"
#include "logging.h"
#include "options.h"

namespace android {
namespace aidl {

using android::base::Error;
using android::base::Result;
using android::base::StartsWith;
using std::map;
using std::set;
using std::string;
using std::vector;

static std::string Dump(const AidlDefinedType& type) {
  string code;
  CodeWriterPtr out = CodeWriter::ForString(&code);
  DumpVisitor visitor(*out, /*inline_constants=*/true);
  type.DispatchVisit(visitor);
  out->Close();
  return code;
}

// Uses each type's Dump() and GTest utility(EqHelper).
static bool CheckEquality(const AidlDefinedType& older, const AidlDefinedType& newer) {
  using testing::internal::EqHelper;
  auto older_file = older.GetLocation().GetFile();
  auto newer_file = newer.GetLocation().GetFile();
  auto result = EqHelper::Compare(older_file.data(), newer_file.data(), Dump(older), Dump(newer));
  if (!result) {
    AIDL_ERROR(newer) << result.failure_message();
  }
  return result;
}

static vector<string> get_strict_annotations(const AidlAnnotatable& node) {
  // This must be symmetrical (if you can add something, you must be able to
  // remove it). The reason is that we have no way of knowing which interface a
  // server serves and which interface a client serves (e.g. a callback
  // interface). Note that this is being overly lenient. It makes sense for
  // newer code to start accepting nullable things. However, here, we don't know
  // if the client of an interface or the server of an interface is newer.
  //
  // Here are two examples to demonstrate this:
  // - a new implementation might change so that it no longer returns null
  // values (remove @nullable)
  // - a new implementation might start accepting null values (add @nullable)
  //
  // AidlAnnotation::Type::SENSITIVE_DATA could be ignored for backwards
  // compatibility, but is not. It should retroactively be applied to the
  // older versions of the interface. When doing that, we need
  // to add the new hash to the older versions after the change using
  // tools/aidl/build/hash_gen.sh.
  static const set<AidlAnnotation::Type> kIgnoreAnnotations{
      AidlAnnotation::Type::NULLABLE,
      // @JavaDerive doesn't affect read/write
      AidlAnnotation::Type::JAVA_DERIVE,
      AidlAnnotation::Type::JAVA_DEFAULT,
      AidlAnnotation::Type::JAVA_DELEGATOR,
      AidlAnnotation::Type::JAVA_ONLY_IMMUTABLE,
      AidlAnnotation::Type::JAVA_PASSTHROUGH,
      AidlAnnotation::Type::JAVA_SUPPRESS_LINT,
      // @RustDerive doesn't affect read/write
      AidlAnnotation::Type::RUST_DERIVE,
      AidlAnnotation::Type::SUPPRESS_WARNINGS,
  };
  vector<string> annotations;
  for (const auto& annotation : node.GetAnnotations()) {
    if (kIgnoreAnnotations.find(annotation->GetType()) != kIgnoreAnnotations.end()) {
      continue;
    }
    auto annotation_string = annotation->ToString();
    // adding @Deprecated (with optional args) is okay
    if (StartsWith(annotation_string, "@JavaPassthrough(annotation=\"@Deprecated")) {
      continue;
    }
    annotations.push_back(annotation_string);
  }
  return annotations;
}

static bool have_compatible_annotations(const AidlAnnotatable& older,
                                        const AidlAnnotatable& newer) {
  vector<string> olderAnnotations = get_strict_annotations(older);
  vector<string> newerAnnotations = get_strict_annotations(newer);
  sort(olderAnnotations.begin(), olderAnnotations.end());
  sort(newerAnnotations.begin(), newerAnnotations.end());
  if (olderAnnotations != newerAnnotations) {
    const string from = older.ToString().empty() ? "(empty)" : older.ToString();
    const string to = newer.ToString().empty() ? "(empty)" : newer.ToString();
    AIDL_ERROR(newer) << "Changed annotations: " << from << " to " << to;
    return false;
  }
  return true;
}

static bool are_compatible_types(const AidlTypeSpecifier& older, const AidlTypeSpecifier& newer) {
  bool compatible = true;
  if (older.Signature() != newer.Signature()) {
    AIDL_ERROR(newer) << "Type changed: " << older.Signature() << " to " << newer.Signature()
                      << ".";
    compatible = false;
  }
  compatible &= have_compatible_annotations(older, newer);
  return compatible;
}

static bool are_compatible_constants(const AidlDefinedType& older, const AidlDefinedType& newer) {
  bool compatible = true;

  map<string, AidlConstantDeclaration*> new_constdecls;
  for (const auto& c : newer.GetConstantDeclarations()) {
    new_constdecls[c->GetName()] = &*c;
  }

  for (const auto& old_c : older.GetConstantDeclarations()) {
    const auto found = new_constdecls.find(old_c->GetName());
    if (found == new_constdecls.end()) {
      AIDL_ERROR(old_c) << "Removed constant declaration: " << older.GetCanonicalName() << "."
                        << old_c->GetName();
      compatible = false;
      continue;
    }

    const auto new_c = found->second;
    compatible &= are_compatible_types(old_c->GetType(), new_c->GetType());

    const string old_value = old_c->ValueString(AidlConstantValueDecorator);
    const string new_value = new_c->ValueString(AidlConstantValueDecorator);
    if (old_value != new_value) {
      AIDL_ERROR(newer) << "Changed constant value: " << older.GetCanonicalName() << "."
                        << old_c->GetName() << " from " << old_value << " to " << new_value << ".";
      compatible = false;
    }
  }
  return compatible;
}

static bool are_compatible_interfaces(const AidlInterface& older, const AidlInterface& newer) {
  bool compatible = true;

  map<string, AidlMethod*> new_methods;
  for (const auto& m : newer.AsInterface()->GetMethods()) {
    new_methods.emplace(m->Signature(), m.get());
  }

  for (const auto& old_m : older.AsInterface()->GetMethods()) {
    const auto found = new_methods.find(old_m->Signature());
    if (found == new_methods.end()) {
      AIDL_ERROR(old_m) << "Removed or changed method: " << older.GetCanonicalName() << "."
                        << old_m->Signature();
      compatible = false;
      continue;
    }

    // Compare IDs to detect method reordering. IDs are assigned by their
    // textual order, so if there is an ID mismatch, that means reordering
    // has happened.
    const auto new_m = found->second;

    if (old_m->IsOneway() != new_m->IsOneway()) {
      AIDL_ERROR(new_m) << "Oneway attribute " << (old_m->IsOneway() ? "removed" : "added") << ": "
                        << older.GetCanonicalName() << "." << old_m->Signature();
      compatible = false;
    }

    if (old_m->GetId() != new_m->GetId()) {
      AIDL_ERROR(new_m) << "Transaction ID changed: " << older.GetCanonicalName() << "."
                        << old_m->Signature() << " is changed from " << old_m->GetId() << " to "
                        << new_m->GetId() << ".";
      compatible = false;
    }

    compatible &= are_compatible_types(old_m->GetType(), new_m->GetType());

    const auto& old_args = old_m->GetArguments();
    const auto& new_args = new_m->GetArguments();
    // this is guaranteed because arguments are part of AidlMethod::Signature()
    AIDL_FATAL_IF(old_args.size() != new_args.size(), old_m);
    for (size_t i = 0; i < old_args.size(); i++) {
      const AidlArgument& old_a = *(old_args.at(i));
      const AidlArgument& new_a = *(new_args.at(i));
      compatible &= are_compatible_types(old_a.GetType(), new_a.GetType());

      if (old_a.GetDirection() != new_a.GetDirection()) {
        AIDL_ERROR(new_m) << "Direction changed: " << old_a.GetDirectionSpecifier() << " to "
                          << new_a.GetDirectionSpecifier() << ".";
        compatible = false;
      }
    }
  }

  compatible = are_compatible_constants(older, newer) && compatible;

  return compatible;
}

static bool HasZeroEnumerator(const AidlEnumDeclaration& enum_decl) {
  return std::any_of(enum_decl.GetEnumerators().begin(), enum_decl.GetEnumerators().end(),
                     [&](const unique_ptr<AidlEnumerator>& enumerator) {
                       return enumerator->GetValue()->ValueString(
                                  enum_decl.GetBackingType(), AidlConstantValueDecorator) == "0";
                     });
}

static bool EvaluatesToZero(const AidlEnumDeclaration& enum_decl, const AidlConstantValue* value) {
  if (value == nullptr) return true;
  return value->ValueString(enum_decl.GetBackingType(), AidlConstantValueDecorator) == "0";
}

static bool are_compatible_parcelables(const AidlDefinedType& older, const AidlTypenames&,
                                       const AidlDefinedType& newer,
                                       const AidlTypenames& new_types) {
  const auto& old_fields = older.GetFields();
  const auto& new_fields = newer.GetFields();
  if (old_fields.size() > new_fields.size()) {
    // you can add new fields only at the end
    AIDL_ERROR(newer) << "Number of fields in " << older.GetCanonicalName() << " is reduced from "
                      << old_fields.size() << " to " << new_fields.size() << ".";
    return false;
  }
  if (newer.IsFixedSize() && old_fields.size() != new_fields.size()) {
    AIDL_ERROR(newer) << "Number of fields in " << older.GetCanonicalName() << " is changed from "
                      << old_fields.size() << " to " << new_fields.size()
                      << ". This is an incompatible change for FixedSize types.";
    return false;
  }

  // android.net.UidRangeParcel should be frozen to prevent breakage in legacy (b/186720556)
  if (older.GetCanonicalName() == "android.net.UidRangeParcel" &&
      old_fields.size() != new_fields.size()) {
    AIDL_ERROR(newer) << "Number of fields in " << older.GetCanonicalName() << " is changed from "
                      << old_fields.size() << " to " << new_fields.size()
                      << ". But it is forbidden because of legacy support.";
    return false;
  }

  bool compatible = true;
  for (size_t i = 0; i < old_fields.size(); i++) {
    const auto& old_field = old_fields.at(i);
    const auto& new_field = new_fields.at(i);
    compatible &= are_compatible_types(old_field->GetType(), new_field->GetType());

    const string old_value = old_field->ValueString(AidlConstantValueDecorator);
    const string new_value = new_field->ValueString(AidlConstantValueDecorator);
    if (old_value == new_value) {
      continue;
    }
    // For enum type fields, we accept setting explicit default value which is "zero"
    auto enum_decl = new_types.GetEnumDeclaration(new_field->GetType());
    if (old_value == "" && enum_decl && EvaluatesToZero(*enum_decl, new_field->GetDefaultValue())) {
      continue;
    }

    AIDL_ERROR(new_field) << "Changed default value: " << old_value << " to " << new_value << ".";
    compatible = false;
  }

  // Reordering of fields is an incompatible change.
  for (size_t i = 0; i < new_fields.size(); i++) {
    const auto& new_field = new_fields.at(i);
    auto found = std::find_if(old_fields.begin(), old_fields.end(), [&new_field](const auto& f) {
      return new_field->GetName() == f->GetName();
    });
    if (found != old_fields.end()) {
      size_t old_index = std::distance(old_fields.begin(), found);
      if (old_index != i) {
        AIDL_ERROR(new_field) << "Reordered " << new_field->GetName() << " from " << old_index
                              << " to " << i << ".";
        compatible = false;
      }
    }
  }

  // New fields must have default values.
  if (older.AsUnionDeclaration() == nullptr) {
    for (size_t i = old_fields.size(); i < new_fields.size(); i++) {
      const auto& new_field = new_fields.at(i);
      if (new_field->HasUsefulDefaultValue()) {
        continue;
      }

      // enum can't be nullable, but it's okay if it has 0 as a valid enumerator.
      if (const auto& enum_decl = new_types.GetEnumDeclaration(new_field->GetType());
          enum_decl != nullptr) {
        if (HasZeroEnumerator(*enum_decl)) {
          continue;
        }

        // TODO(b/142893595): Rephrase the message: "provide a default value or make sure ..."
        AIDL_ERROR(new_field) << "Field '" << new_field->GetName() << "' of enum '"
                              << enum_decl->GetName()
                              << "' can't be initialized as '0'. Please make sure '"
                              << enum_decl->GetName() << "' has '0' as a valid value.";
        compatible = false;
        continue;
      }

      // Old API versions may suffer from the issue presented here. There is
      // only a finite number in Android, which we must allow indefinitely.
      struct HistoricalException {
        std::string canonical;
        std::string field;
      };
      static std::vector<HistoricalException> exceptions = {
          {"android.net.DhcpResultsParcelable", "serverHostName"},
          {"android.net.ResolverParamsParcel", "resolverOptions"},
      };
      bool excepted = false;
      for (const HistoricalException& exception : exceptions) {
        if (older.GetCanonicalName() == exception.canonical &&
            new_field->GetName() == exception.field) {
          excepted = true;
          break;
        }
      }
      if (excepted) continue;

      AIDL_ERROR(new_field)
          << "Field '" << new_field->GetName()
          << "' does not have a useful default in some backends. Please either provide a default "
             "value for this field or mark the field as @nullable. This value or a null value will "
             "be used automatically when an old version of this parcelable is sent to a process "
             "which understands a new version of this parcelable. In order to make sure your code "
             "continues to be backwards compatible, make sure the default or null value does not "
             "cause a semantic change to this parcelable.";
      compatible = false;
    }
  }

  compatible = are_compatible_constants(older, newer) && compatible;

  return compatible;
}

static bool are_compatible_enums(const AidlEnumDeclaration& older,
                                 const AidlEnumDeclaration& newer) {
  std::map<std::string, const AidlConstantValue*> old_enum_map;
  for (const auto& enumerator : older.GetEnumerators()) {
    old_enum_map[enumerator->GetName()] = enumerator->GetValue();
  }
  std::map<std::string, const AidlConstantValue*> new_enum_map;
  for (const auto& enumerator : newer.GetEnumerators()) {
    new_enum_map[enumerator->GetName()] = enumerator->GetValue();
  }

  bool compatible = true;
  for (const auto& [name, value] : old_enum_map) {
    if (new_enum_map.find(name) == new_enum_map.end()) {
      AIDL_ERROR(newer) << "Removed enumerator from " << older.GetCanonicalName() << ": " << name;
      compatible = false;
      continue;
    }
    const string old_value =
        old_enum_map[name]->ValueString(older.GetBackingType(), AidlConstantValueDecorator);
    const string new_value =
        new_enum_map[name]->ValueString(newer.GetBackingType(), AidlConstantValueDecorator);
    if (old_value != new_value) {
      AIDL_ERROR(newer) << "Changed enumerator value: " << older.GetCanonicalName() << "::" << name
                        << " from " << old_value << " to " << new_value << ".";
      compatible = false;
    }
  }
  return compatible;
}

Result<AidlTypenames> LoadApiDump(const Options& options, const IoDelegate& io_delegate,
                                  const std::string& dir) {
  Result<std::vector<std::string>> dir_files = io_delegate.ListFiles(dir);
  if (!dir_files.ok()) {
    AIDL_ERROR(dir) << dir_files.error();
    return Error();
  }

  AidlTypenames typenames;
  for (const auto& file : *dir_files) {
    if (!android::base::EndsWith(file, ".aidl")) continue;
    // current "dir" is added to "imports" so that referenced.aidl files in the current
    // module are available when resolving references.
    if (internals::load_and_validate_aidl(file, options.PlusImportDir(dir), io_delegate, &typenames,
                                          nullptr /* imported_files */) != AidlError::OK) {
      AIDL_ERROR(file) << "Failed to read.";
      return Error();
    }
  }

  return typenames;
}

bool check_api(const Options& options, const IoDelegate& io_delegate) {
  AIDL_FATAL_IF(!options.IsStructured(), AIDL_LOCATION_HERE);
  AIDL_FATAL_IF(options.InputFiles().size() != 2, AIDL_LOCATION_HERE)
      << "--checkapi requires two inputs "
      << "but got " << options.InputFiles().size();
  auto old_tns = LoadApiDump(options, io_delegate, options.InputFiles().at(0));
  if (!old_tns.ok()) {
    return false;
  }
  auto new_tns = LoadApiDump(options, io_delegate, options.InputFiles().at(1));
  if (!new_tns.ok()) {
    return false;
  }

  const Options::CheckApiLevel level = options.GetCheckApiLevel();

  // We don't check impoted types.
  auto get_types_in = [](const AidlTypenames& tns, const std::string& location) {
    std::vector<const AidlDefinedType*> types;
    for (const auto& type : tns.AllDefinedTypes()) {
      if (StartsWith(type->GetLocation().GetFile(), location)) {
        types.push_back(type);
      }
    }
    return types;
  };
  std::vector<const AidlDefinedType*> old_types =
      get_types_in(*old_tns, options.InputFiles().at(0));
  std::vector<const AidlDefinedType*> new_types =
      get_types_in(*new_tns, options.InputFiles().at(1));

  bool compatible = true;

  if (level == Options::CheckApiLevel::EQUAL) {
    std::set<string> old_type_names;
    for (const auto t : old_types) {
      old_type_names.insert(t->GetCanonicalName());
    }
    for (const auto new_type : new_types) {
      const auto found = old_type_names.find(new_type->GetCanonicalName());
      if (found == old_type_names.end()) {
        AIDL_ERROR(new_type) << "Added type: " << new_type->GetCanonicalName();
        compatible = false;
        continue;
      }
    }
  }

  map<string, const AidlDefinedType*> new_map;
  for (const auto t : new_types) {
    new_map.emplace(t->GetCanonicalName(), t);
  }

  for (const auto old_type : old_types) {
    const auto found = new_map.find(old_type->GetCanonicalName());
    if (found == new_map.end()) {
      AIDL_ERROR(old_type) << "Removed type: " << old_type->GetCanonicalName();
      compatible = false;
      continue;
    }
    const auto new_type = found->second;

    if (level == Options::CheckApiLevel::EQUAL) {
      if (!CheckEquality(*old_type, *new_type)) {
        compatible = false;
      }
      continue;
    }

    if (!have_compatible_annotations(*old_type, *new_type)) {
      compatible = false;
    }
    if (old_type->AsInterface() != nullptr) {
      if (new_type->AsInterface() == nullptr) {
        AIDL_ERROR(new_type) << "Type mismatch: " << old_type->GetCanonicalName()
                             << " is changed from " << old_type->GetPreprocessDeclarationName()
                             << " to " << new_type->GetPreprocessDeclarationName();
        compatible = false;
        continue;
      }
      compatible &=
          are_compatible_interfaces(*(old_type->AsInterface()), *(new_type->AsInterface()));
    } else if (old_type->AsStructuredParcelable() != nullptr) {
      if (new_type->AsStructuredParcelable() == nullptr) {
        AIDL_ERROR(new_type) << "Parcelable" << new_type->GetCanonicalName()
                             << " is not structured. ";
        compatible = false;
        continue;
      }
      compatible &= are_compatible_parcelables(*(old_type->AsStructuredParcelable()), *old_tns,
                                               *(new_type->AsStructuredParcelable()), *new_tns);
    } else if (old_type->AsUnstructuredParcelable() != nullptr) {
      // We could compare annotations or cpp_header/ndk_header here, but all these changes
      // can be safe, and it's really up to the person making these changes to make sure
      // they are safe. This is originally added for Android Studio. In the platform build
      // system, this can never be reached because we build with '-b'

      // ignore, do nothing
    } else if (old_type->AsUnionDeclaration() != nullptr) {
      if (new_type->AsUnionDeclaration() == nullptr) {
        AIDL_ERROR(new_type) << "Type mismatch: " << old_type->GetCanonicalName()
                             << " is changed from " << old_type->GetPreprocessDeclarationName()
                             << " to " << new_type->GetPreprocessDeclarationName();
        compatible = false;
        continue;
      }
      compatible &= are_compatible_parcelables(*(old_type->AsUnionDeclaration()), *old_tns,
                                               *(new_type->AsUnionDeclaration()), *new_tns);
    } else if (old_type->AsEnumDeclaration() != nullptr) {
      if (new_type->AsEnumDeclaration() == nullptr) {
        AIDL_ERROR(new_type) << "Type mismatch: " << old_type->GetCanonicalName()
                             << " is changed from " << old_type->GetPreprocessDeclarationName()
                             << " to " << new_type->GetPreprocessDeclarationName();
        compatible = false;
        continue;
      }
      compatible &=
          are_compatible_enums(*(old_type->AsEnumDeclaration()), *(new_type->AsEnumDeclaration()));
    } else {
      AIDL_ERROR(old_type) << "Unsupported declaration type "
                           << old_type->GetPreprocessDeclarationName() << " for "
                           << old_type->GetCanonicalName() << " API dump comparison";
      compatible = false;
    }
  }

  return compatible;
}

}  // namespace aidl
}  // namespace android
