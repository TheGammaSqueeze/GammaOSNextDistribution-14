/*
 * Copyright (C) 2020, The Android Open Source Project
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
#include "diagnostics.h"

#include <functional>
#include <stack>

#include "aidl_language.h"
#include "logging.h"

using std::placeholders::_1;

namespace android {
namespace aidl {

const std::map<std::string, DiagnosticOption> kAllDiagnostics = {
#define DIAG(ENUM, NAME, ENABLED) {NAME, DiagnosticOption{DiagnosticID::ENUM, NAME, ENABLED}},
#include "diagnostics.inc"
#undef DIAG
};

static const std::map<DiagnosticID, std::string> kDiagnosticsNames = {
#define DIAG(ENUM, NAME, ENABLED) {DiagnosticID::ENUM, NAME},
#include "diagnostics.inc"
#undef DIAG
};

void DiagnosticMapping::Severity(DiagnosticID id, DiagnosticSeverity severity) {
  mapping_[id] = severity;
}

DiagnosticSeverity DiagnosticMapping::Severity(DiagnosticID id) const {
  return mapping_.at(id);
}

std::string to_string(DiagnosticID id) {
  return kDiagnosticsNames.at(id);
}

static std::string ToUpper(const std::string& name) {
  std::string nameCopy(name);
  for (auto& c : nameCopy) c = std::toupper(c);
  return nameCopy;
}

class DiagnosticsContext {
 public:
  DiagnosticsContext(DiagnosticMapping mapping) : mapping_({std::move(mapping)}) {}
  AidlErrorLog Report(const AidlLocation& loc, DiagnosticID id,
                      DiagnosticSeverity force_severity = DiagnosticSeverity::DISABLED) {
    if (loc.IsInternal()) {
      return AidlErrorLog(AidlErrorLog::NO_OP, loc);
    }
    const std::string suffix = " [-W" + to_string(id) + "]";
    auto severity = std::max(force_severity, mapping_.top().Severity(id));
    switch (severity) {
      case DiagnosticSeverity::DISABLED:
        return AidlErrorLog(AidlErrorLog::NO_OP, loc);
      case DiagnosticSeverity::WARNING:
        return AidlErrorLog(AidlErrorLog::WARNING, loc, suffix);
      case DiagnosticSeverity::ERROR:
        error_count_++;
        return AidlErrorLog(AidlErrorLog::ERROR, loc, suffix);
    }
  }
  size_t ErrorCount() const { return error_count_; }
  void Suppress(const AidlAnnotatable& a) {
    const auto& warnings = a.SuppressWarnings();
    DiagnosticMapping new_mapping = mapping_.top();
    for (const auto& w : warnings) {
      auto it = kAllDiagnostics.find(w);
      if (it == kAllDiagnostics.end()) {
        Report(a.GetLocation(), DiagnosticID::unknown_warning, DiagnosticSeverity::ERROR)
            << "unknown warning: " << w;
        continue;
      }
      new_mapping.Severity(it->second.id, DiagnosticSeverity::DISABLED);
    }
    mapping_.push(std::move(new_mapping));
  }
  void Restore(const AidlAnnotatable&) {
    mapping_.pop();
  }
 private:
  std::stack<DiagnosticMapping> mapping_;
  size_t error_count_ = {};
};

class DiagnosticsVisitor : public AidlVisitor {
 public:
  DiagnosticsVisitor(DiagnosticsContext& diag) : diag(diag) {}
  void Check(const AidlDocument& doc) {
    DiagnosticsVisitor* visitor = this;
    using Fun = std::function<void(const AidlAnnotatable&)>;
    struct Hook : public AidlVisitor {
      Fun fun;
      Hook(Fun fun) : fun(fun) {}
      void Visit(const AidlInterface& a) override { fun(a); }
      void Visit(const AidlEnumDeclaration& a) override { fun(a); }
      void Visit(const AidlStructuredParcelable& a) override { fun(a); }
      void Visit(const AidlUnionDecl& a) override { fun(a); }
      void Visit(const AidlParcelable& a) override { fun(a); }
      void Visit(const AidlMethod& a) override { fun(a.GetType()); }
    };
    Hook suppress{std::bind(&DiagnosticsContext::Suppress, &diag, _1)};
    Hook restore{std::bind(&DiagnosticsContext::Restore, &diag, _1)};
    std::function<void(const AidlNode&)> top_down = [&top_down, &suppress, &restore,
                                                     visitor](const AidlNode& a) {
      a.DispatchVisit(suppress);
      a.DispatchVisit(*visitor);
      a.TraverseChildren(top_down);
      a.DispatchVisit(restore);
    };
    top_down(doc);
  }
 protected:
  DiagnosticsContext& diag;
};

struct DiagnoseInterfaceName : DiagnosticsVisitor {
  DiagnoseInterfaceName(DiagnosticsContext& diag) : DiagnosticsVisitor(diag) {}
  void Visit(const AidlInterface& i) override {
    if (auto name = i.GetName(); name.size() < 1 || name[0] != 'I') {
      diag.Report(i.GetLocation(), DiagnosticID::interface_name)
          << "Interface names should start with I.";
    }
  }
};

struct DiagnoseInoutParameter : DiagnosticsVisitor {
  DiagnoseInoutParameter(DiagnosticsContext& diag) : DiagnosticsVisitor(diag) {}
  void Visit(const AidlArgument& a) override {
    if (a.GetDirection() == AidlArgument::INOUT_DIR) {
      diag.Report(a.GetLocation(), DiagnosticID::inout_parameter)
          << a.GetName()
          << " is 'inout'. Avoid inout parameters. This is somewhat confusing for clients "
             "because although the parameters are 'in', they look out 'out' parameters.";
    }
  }
};

struct DiagnoseConstName : DiagnosticsVisitor {
  DiagnoseConstName(DiagnosticsContext& diag) : DiagnosticsVisitor(diag) {}
  void Visit(const AidlEnumerator& e) override {
    if (ToUpper(e.GetName()) != e.GetName()) {
      diag.Report(e.GetLocation(), DiagnosticID::const_name)
          << "Enum values should be named in upper case: " << e.GetName();
    }
  }
  void Visit(const AidlConstantDeclaration& c) override {
    if (ToUpper(c.GetName()) != c.GetName()) {
      diag.Report(c.GetLocation(), DiagnosticID::const_name)
          << "Constants should be named in upper case: " << c.GetName();
    }
  }
};

struct DiagnoseExplicitDefault : DiagnosticsVisitor {
  DiagnoseExplicitDefault(DiagnosticsContext& diag) : DiagnosticsVisitor(diag) {}
  void Visit(const AidlStructuredParcelable& p) override {
    for (const auto& var : p.GetFields()) {
      CheckExplicitDefault(*var);
    }
  }
  void Visit(const AidlUnionDecl& u) override {
    AIDL_FATAL_IF(u.GetFields().empty(), u) << "The union '" << u.GetName() << "' has no fields.";
    const auto& first = u.GetFields()[0];
    CheckExplicitDefault(*first);
  }
  void CheckExplicitDefault(const AidlVariableDeclaration& v) {
    if (v.IsDefaultUserSpecified()) return;
    if (v.GetType().IsNullable()) return;
    if (v.GetType().IsArray()) return;
    const auto defined_type = v.GetType().GetDefinedType();
    if (defined_type && defined_type->AsEnumDeclaration()) {
      diag.Report(v.GetLocation(), DiagnosticID::enum_explicit_default)
          << "The enum field '" << v.GetName() << "' has no explicit value.";
      return;
    }
  }
};

struct DiagnoseMixedOneway : DiagnosticsVisitor {
  DiagnoseMixedOneway(DiagnosticsContext& diag) : DiagnosticsVisitor(diag) {}
  void Visit(const AidlInterface& i) override {
    bool has_oneway = false;
    bool has_twoway = false;
    for (const auto& m : i.GetMethods()) {
      if (!m->IsUserDefined()) continue;
      if (Suppressed(*m)) continue;
      if (m->IsOneway()) {
        has_oneway = true;
      } else {
        has_twoway = true;
      }
    }
    if (has_oneway && has_twoway) {
      diag.Report(i.GetLocation(), DiagnosticID::mixed_oneway)
          << "The interface '" << i.GetName()
          << "' has both one-way and two-way methods. This makes it hard to reason about threading "
             "of client code.";
    }
  }
  bool Suppressed(const AidlMethod& m) const {
    for (const auto& w : m.GetType().SuppressWarnings()) {
      if (w == to_string(DiagnosticID::mixed_oneway)) {
        return true;
      }
    }
    return false;
  }
};

struct DiagnoseRedundantOneway : DiagnosticsVisitor {
  DiagnoseRedundantOneway(DiagnosticsContext& diag) : DiagnosticsVisitor(diag) {}
  void Visit(const AidlInterface& i) override {
    if (i.HasOnewayAnnotation()) {
      for (const auto& m : i.GetMethods()) {
        if (!m->IsUserDefined()) continue;
        if (Suppressed(*m)) continue;
        if (m->HasOnewayAnnotation()) {
          diag.Report(i.GetLocation(), DiagnosticID::redundant_oneway)
              << "The interface '" << i.GetName()
              << "' is oneway. Redundant oneway annotation for method '" << m->GetName() << "'.";
        }
      }
    }
  }
  bool Suppressed(const AidlMethod& m) const {
    for (const auto& w : m.GetType().SuppressWarnings()) {
      if (w == to_string(DiagnosticID::redundant_oneway)) {
        return true;
      }
    }
    return false;
  }
};

struct DiagnoseOutArray : DiagnosticsVisitor {
  DiagnoseOutArray(DiagnosticsContext& diag) : DiagnosticsVisitor(diag) {}
  void Visit(const AidlMethod& m) override {
    for (const auto& a : m.GetArguments()) {
      if (a->GetType().IsArray() && a->IsOut()) {
        diag.Report(m.GetLocation(), DiagnosticID::out_array)
            << "The method '" << m.GetName() << "' an array output parameter '" << a->GetName()
            << "'. Instead prefer APIs like '" << a->GetType().Signature() << " " << m.GetName()
            << "(...).";
      }
    }
  }
};

struct DiagnoseFileDescriptor : DiagnosticsVisitor {
  DiagnoseFileDescriptor(DiagnosticsContext& diag) : DiagnosticsVisitor(diag) {}
  void Visit(const AidlTypeSpecifier& t) override {
    if (t.GetName() == "FileDescriptor") {
      diag.Report(t.GetLocation(), DiagnosticID::file_descriptor)
          << "Please use ParcelFileDescriptor instead of FileDescriptor.";
    }
  }
};

struct DiagnoseOutNullable : DiagnosticsVisitor {
  DiagnoseOutNullable(DiagnosticsContext& diag) : DiagnosticsVisitor(diag) {}
  void Visit(const AidlArgument& a) override {
    if (a.GetType().IsArray()) return;
    if (a.IsOut() && a.GetType().IsNullable()) {
      diag.Report(a.GetLocation(), DiagnosticID::out_nullable)
          << "'" << a.GetName() << "' is an " << a.GetDirectionSpecifier()
          << " parameter and also nullable. Some backends don't support setting null value to out "
             "parameters. Please use it as return value or drop @nullable to avoid potential "
             "errors.";
    }
  }
};

struct DiagnoseImports : DiagnosticsVisitor {
  DiagnoseImports(DiagnosticsContext& diag) : DiagnosticsVisitor(diag) {}
  void Visit(const AidlDocument& doc) override {
    auto collide_with_decls = [&](const auto& import) {
      for (const auto& type : doc.DefinedTypes()) {
        if (type->GetCanonicalName() != import && type->GetName() == SimpleName(import)) {
          return true;
        }
      }
      return false;
    };

    std::set<std::string> imported_names;
    for (const auto& import : doc.Imports()) {
      if (collide_with_decls(import)) {
        diag.Report(doc.GetLocation(), DiagnosticID::unique_import)
            << SimpleName(import) << " is already defined in this file.";
      }
      auto [_, inserted] = imported_names.insert(SimpleName(import));
      if (!inserted) {
        diag.Report(doc.GetLocation(), DiagnosticID::unique_import)
            << SimpleName(import) << " is already imported.";
      }
    }
  }
};

struct DiagnoseUntypedCollection : DiagnosticsVisitor {
  DiagnoseUntypedCollection(DiagnosticsContext& diag) : DiagnosticsVisitor(diag) {}
  void Visit(const AidlTypeSpecifier& t) override {
    if (t.GetName() == "List" || t.GetName() == "Map") {
      if (!t.IsGeneric()) {
        diag.Report(t.GetLocation(), DiagnosticID::untyped_collection)
            << "Use List<V> or Map<K,V> instead.";
      }
    }
  }
};

struct DiagnosePermissionAnnotations : DiagnosticsVisitor {
  DiagnosePermissionAnnotations(DiagnosticsContext& diag) : DiagnosticsVisitor(diag) {}
  void Visit(const AidlInterface& intf) override {
    const std::string diag_message =
        " is not annotated for permissions. Declare which permissions are "
        "required using @EnforcePermission. If permissions are manually "
        "verified within the implementation, use @PermissionManuallyEnforced. "
        "If no permissions are required, use @RequiresNoPermission.";
    if (intf.IsPermissionAnnotated()) {
      return;
    }
    const auto& methods = intf.GetMethods();
    std::vector<size_t> methods_without_annotations;
    size_t num_user_defined_methods = 0;
    for (size_t i = 0; i < methods.size(); ++i) {
      auto& m = methods[i];
      if (!m->IsUserDefined()) continue;
      num_user_defined_methods++;
      if (m->GetType().IsPermissionAnnotated()) {
        continue;
      }
      methods_without_annotations.push_back(i);
    }
    if (methods_without_annotations.size() == num_user_defined_methods) {
      diag.Report(intf.GetLocation(), DiagnosticID::missing_permission_annotation)
          << intf.GetName() << diag_message
          << " This can be done for the whole interface or for each method.";
    } else {
      for (size_t i : methods_without_annotations) {
        auto& m = methods[i];
        diag.Report(m->GetLocation(), DiagnosticID::missing_permission_annotation)
            << m->GetName() << diag_message;
      }
    }
  }
};

struct DiagnoseRedundantNames : DiagnosticsVisitor {
  DiagnoseRedundantNames(DiagnosticsContext& diag) : DiagnosticsVisitor(diag) {}

  // tokenize the name with either capital letters or '_' being the delimiters
  static std::vector<std::string> TokenizeName(const std::string& name) {
    // if a name is all capitals with no '_', then we don't want to tokenize it.
    if (std::all_of(name.begin(), name.end(), [](unsigned char c) { return isupper(c); })) {
      return {name};
    }
    // if a name has an `_` in it, it will be tokenized based on '_',
    // otherwise based on capital letters
    if (name.find('_') != std::string::npos) {
      return base::Tokenize(name, "_");
    }

    std::vector<std::string> tokens;
    size_t size = name.size();
    std::string tmp{name.front()};
    // Skip the first character to avoid an empty substring for common cases
    for (size_t i = 1; i < size; i++) {
      if (std::isupper(name[i])) {
        tokens.push_back(tmp);
        tmp.clear();
        // This uppercase letter belongs to the next token
        tmp += name[i];
      } else {
        tmp += name[i];
      }
    }

    if (!tmp.empty()) tokens.push_back(tmp);

    return tokens;
  }

  void Visit(const AidlEnumDeclaration& e) override {
    const std::vector<std::string> parent = TokenizeName(e.GetName());
    for (const auto& enumerator : e.GetEnumerators()) {
      const std::vector<std::string> child = TokenizeName(enumerator->GetName());
      for (const auto& parentSubStr : parent) {
        for (const auto& childSubStr : child) {
          if (ToUpper(parentSubStr) == ToUpper(childSubStr)) {
            diag.Report(e.GetLocation(), DiagnosticID::redundant_name)
                << "The enumerator '" << enumerator->GetName() << "' has a redundant substring '"
                << childSubStr << "' being defined in '" << e.GetName() << "'";
          }
        }
      }
    }
  }

  void CheckConstantDeclarations(
      const std::string& name,
      const std::vector<std::unique_ptr<AidlConstantDeclaration>>& consts) {
    const std::vector<std::string> parent = TokenizeName(name);
    for (const auto& member : consts) {
      const std::vector<std::string> child = TokenizeName(member->GetName());
      for (const auto& parentSubStr : parent) {
        for (const auto& childSubStr : child) {
          if (ToUpper(parentSubStr) == ToUpper(childSubStr)) {
            diag.Report(member->GetLocation(), DiagnosticID::redundant_name)
                << "The constant '" << member->GetName() << "' has a redundant substring '"
                << childSubStr << "' being defined in '" << name << "'";
          }
        }
      }
    }
  }

  void Visit(const AidlInterface& t) override {
    CheckConstantDeclarations(t.GetName(), t.GetConstantDeclarations());
  }

  void Visit(const AidlUnionDecl& t) override {
    CheckConstantDeclarations(t.GetName(), t.GetConstantDeclarations());
  }

  void Visit(const AidlStructuredParcelable& t) override {
    CheckConstantDeclarations(t.GetName(), t.GetConstantDeclarations());
  }
};

bool Diagnose(const AidlDocument& doc, const DiagnosticMapping& mapping) {
  DiagnosticsContext diag(mapping);

  DiagnoseInterfaceName{diag}.Check(doc);
  DiagnoseInoutParameter{diag}.Check(doc);
  DiagnoseConstName{diag}.Check(doc);
  DiagnoseExplicitDefault{diag}.Check(doc);
  DiagnoseMixedOneway{diag}.Check(doc);
  DiagnoseOutArray{diag}.Check(doc);
  DiagnoseFileDescriptor{diag}.Check(doc);
  DiagnoseOutNullable{diag}.Check(doc);
  DiagnoseImports{diag}.Check(doc);
  DiagnoseUntypedCollection{diag}.Check(doc);
  DiagnosePermissionAnnotations{diag}.Check(doc);
  DiagnoseRedundantNames{diag}.Check(doc);
  DiagnoseRedundantOneway{diag}.Check(doc);

  return diag.ErrorCount() == 0;
}
}  // namespace aidl
}  // namespace android
