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

#include "aidl.h"
#include "aidl_language.h"
#include "aidl_to_common.h"
#include "aidl_to_java.h"
#include "aidl_typenames.h"
#include "ast_java.h"
#include "code_writer.h"
#include "generate_java.h"
#include "logging.h"
#include "options.h"
#include "parser.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <algorithm>
#include <unordered_set>
#include <utility>
#include <vector>

#include <android-base/stringprintf.h>

using android::base::Join;
using android::base::StringPrintf;

using std::string;
using std::unique_ptr;
using std::vector;

namespace android {
namespace aidl {
namespace java {

// =================================================
class VariableFactory {
 public:
  using Variable = ::android::aidl::java::Variable;

  explicit VariableFactory(const std::string& base) : base_(base), index_(0) {}
  std::shared_ptr<Variable> Get(const AidlTypeSpecifier& type) {
    auto v = std::make_shared<Variable>(JavaSignatureOf(type),
                                        StringPrintf("%s%d", base_.c_str(), index_));
    vars_.push_back(v);
    index_++;
    return v;
  }

  std::shared_ptr<Variable> Get(int index) { return vars_[index]; }

 private:
  std::vector<std::shared_ptr<Variable>> vars_;
  std::string base_;
  int index_;
};

// =================================================
class StubClass : public Class {
 public:
  StubClass(const AidlInterface* interfaceType, const Options& options);
  ~StubClass() override = default;

  // non-copyable, non-movable
  StubClass(const StubClass&) = delete;
  StubClass(StubClass&&) = delete;
  StubClass& operator=(const StubClass&) = delete;
  StubClass& operator=(StubClass&&) = delete;

  std::shared_ptr<Variable> transact_code;
  std::shared_ptr<Variable> transact_data;
  std::shared_ptr<Variable> transact_reply;
  std::shared_ptr<Variable> transact_flags;
  std::vector<std::shared_ptr<IfStatement>> transact_if_statements_meta;
  std::shared_ptr<SwitchStatement> transact_switch_user;
  std::shared_ptr<StatementBlock> transact_statements;
  std::shared_ptr<SwitchStatement> code_to_method_name_switch;

  // Where onTransact cases should be generated as separate methods.
  bool transact_outline;
  // Specific methods that should be outlined when transact_outline is true.
  std::unordered_set<const AidlMethod*> outline_methods;
  // Number of all methods.
  size_t all_method_count;

  // Finish generation. This will add a default case to the switch.
  void Finish();

  std::shared_ptr<Expression> GetTransactDescriptor(const AidlMethod* method);

 private:
  void MakeConstructors(const AidlInterface* interfaceType);
  void MakeAsInterface(const AidlInterface* interfaceType);

  std::shared_ptr<Variable> transact_descriptor;
  const Options& options_;
};

StubClass::StubClass(const AidlInterface* interfaceType, const Options& options)
    : Class(), options_(options) {
  transact_descriptor = nullptr;
  transact_outline = false;
  all_method_count = 0;  // Will be set when outlining may be enabled.

  this->comment = "/** Local-side IPC implementation stub class. */";
  this->modifiers = PUBLIC | ABSTRACT | STATIC;
  this->what = Class::CLASS;
  this->type = interfaceType->GetCanonicalName() + ".Stub";
  this->extends = "android.os.Binder";
  this->interfaces.push_back(interfaceType->GetCanonicalName());

  MakeConstructors(interfaceType);
  MakeAsInterface(interfaceType);

  // asBinder
  auto asBinder = std::make_shared<Method>();
  asBinder->modifiers = PUBLIC | OVERRIDE;
  asBinder->returnType = "android.os.IBinder";
  asBinder->name = "asBinder";
  asBinder->statements = std::make_shared<StatementBlock>();
  asBinder->statements->Add(std::make_shared<ReturnStatement>(THIS_VALUE));
  this->elements.push_back(asBinder);

  if (options_.GenTransactionNames() || options_.GenTraces()) {
    // getDefaultTransactionName
    auto getDefaultTransactionName = std::make_shared<Method>();
    getDefaultTransactionName->comment = "/** @hide */";
    getDefaultTransactionName->modifiers = PUBLIC | STATIC;
    getDefaultTransactionName->returnType = "java.lang.String";
    getDefaultTransactionName->name = "getDefaultTransactionName";
    auto code = std::make_shared<Variable>("int", "transactionCode");
    getDefaultTransactionName->parameters.push_back(code);
    getDefaultTransactionName->statements = std::make_shared<StatementBlock>();
    this->code_to_method_name_switch = std::make_shared<SwitchStatement>(code);
    getDefaultTransactionName->statements->Add(this->code_to_method_name_switch);
    this->elements.push_back(getDefaultTransactionName);

    // getTransactionName
    auto getTransactionName = std::make_shared<Method>();
    getTransactionName->comment = "/** @hide */";
    getTransactionName->modifiers = PUBLIC;
    getTransactionName->returnType = "java.lang.String";
    getTransactionName->name = "getTransactionName";
    auto code2 = std::make_shared<Variable>("int", "transactionCode");
    getTransactionName->parameters.push_back(code2);
    getTransactionName->statements = std::make_shared<StatementBlock>();
    getTransactionName->statements->Add(std::make_shared<ReturnStatement>(
        std::make_shared<MethodCall>(THIS_VALUE, "getDefaultTransactionName",
                                     std::vector<std::shared_ptr<Expression>>{code2})));
    this->elements.push_back(getTransactionName);
  }

  // onTransact
  this->transact_code = std::make_shared<Variable>("int", "code");
  this->transact_data = std::make_shared<Variable>("android.os.Parcel", "data");
  this->transact_reply = std::make_shared<Variable>("android.os.Parcel", "reply");
  this->transact_flags = std::make_shared<Variable>("int", "flags");
  auto onTransact = std::make_shared<Method>();
  onTransact->modifiers = PUBLIC | OVERRIDE;
  onTransact->returnType = "boolean";
  onTransact->name = "onTransact";
  onTransact->parameters.push_back(this->transact_code);
  onTransact->parameters.push_back(this->transact_data);
  onTransact->parameters.push_back(this->transact_reply);
  onTransact->parameters.push_back(this->transact_flags);
  onTransact->statements = std::make_shared<StatementBlock>();
  transact_statements = onTransact->statements;
  onTransact->exceptions.push_back("android.os.RemoteException");
  this->elements.push_back(onTransact);
  this->transact_switch_user = std::make_shared<SwitchStatement>(this->transact_code);
}

void StubClass::Finish() {
  auto default_case = std::make_shared<Case>();

  auto superCall = std::make_shared<MethodCall>(
      SUPER_VALUE, "onTransact",
      std::vector<std::shared_ptr<Expression>>{this->transact_code, this->transact_data,
                                               this->transact_reply, this->transact_flags});
  default_case->statements->Add(std::make_shared<ReturnStatement>(superCall));

  auto case_count = transact_switch_user->cases.size();
  transact_switch_user->cases.push_back(default_case);

  // Interface token validation is done for user-defined transactions.
  if (case_count > 0) {
    auto ifStatement = std::make_shared<IfStatement>();
    ifStatement->expression = std::make_shared<LiteralExpression>(
        "code >= android.os.IBinder.FIRST_CALL_TRANSACTION && "
        "code <= android.os.IBinder.LAST_CALL_TRANSACTION");
    ifStatement->statements = std::make_shared<StatementBlock>();
    ifStatement->statements->Add(std::make_shared<MethodCall>(
        this->transact_data, "enforceInterface",
        std::vector<std::shared_ptr<Expression>>{this->GetTransactDescriptor(nullptr)}));
    transact_statements->Add(ifStatement);
  }

  // Build the if/else chain for the meta methods. There at most 3 different
  // statements so if/else is more efficient than a switch statement.
  // Meta transactions are looked up prior to user-defined transactions.
  AIDL_FATAL_IF(this->transact_if_statements_meta.size() == 0, AIDL_LOCATION_HERE)
      << "Expecting to have meta methods and found none.";
  AIDL_FATAL_IF(this->transact_if_statements_meta.size() > 3, AIDL_LOCATION_HERE)
      << "Expecting to have at most 3 meta methods and found "
      << this->transact_if_statements_meta.size();
  auto ifStatement = this->transact_if_statements_meta[0];
  std::shared_ptr<IfStatement> currentIfStatement = ifStatement;
  for (size_t i = 1; i < transact_if_statements_meta.size(); i++) {
    currentIfStatement->elseif = this->transact_if_statements_meta[i];
    currentIfStatement = currentIfStatement->elseif;
  }

  transact_statements->Add(ifStatement);
  transact_statements->Add(this->transact_switch_user);

  // getTransactionName
  if (options_.GenTransactionNames() || options_.GenTraces()) {
    // Some transaction codes are common, e.g. INTERFACE_TRANSACTION or DUMP_TRANSACTION.
    // Common transaction codes will not be resolved to a string by getTransactionName. The method
    // will return NULL in this case.
    auto code_switch_default_case = std::make_shared<Case>();
    code_switch_default_case->statements->Add(std::make_shared<ReturnStatement>(NULL_VALUE));
    this->code_to_method_name_switch->cases.push_back(code_switch_default_case);
  }

  // There will be at least one statement for the default, but if we emit a
  // return true after that default, it will be unreachable.
  if (case_count > 0) {
    transact_statements->Add(std::make_shared<ReturnStatement>(TRUE_VALUE));
  }
}

// The the expression for the interface's descriptor to be used when
// generating code for the given method. Null is acceptable for method
// and stands for synthetic cases.
std::shared_ptr<Expression> StubClass::GetTransactDescriptor(const AidlMethod* method) {
  if (transact_outline) {
    if (method != nullptr) {
      // When outlining, each outlined method needs its own literal.
      if (outline_methods.count(method) != 0) {
        return std::make_shared<LiteralExpression>("DESCRIPTOR");
      }
    } else {
      // Synthetic case. A small number is assumed. Use its own descriptor
      // if there are only synthetic cases.
      if (outline_methods.size() == all_method_count) {
        return std::make_shared<LiteralExpression>("DESCRIPTOR");
      }
    }
  }

  // When not outlining, store the descriptor literal into a local variable, in
  // an effort to save const-string instructions in each switch case.
  if (transact_descriptor == nullptr) {
    transact_descriptor = std::make_shared<Variable>("java.lang.String", "descriptor");
    transact_statements->Add(std::make_shared<VariableDeclaration>(
        transact_descriptor, std::make_shared<LiteralExpression>("DESCRIPTOR")));
  }
  return transact_descriptor;
}

void StubClass::MakeConstructors(const AidlInterface* interfaceType) {
  string ctors_code;
  CodeWriterPtr writer = CodeWriter::ForString(&ctors_code);
  CodeWriter& code = *writer;

  if (interfaceType->UsesPermissions()) {
    code << "private final android.os.PermissionEnforcer mEnforcer;\n";
    code << "/** Construct the stub using the Enforcer provided. */\n";
    code << "public Stub(android.os.PermissionEnforcer enforcer)\n";
  } else {
    code << "/** Construct the stub at attach it to the interface. */\n";
    code << "@SuppressWarnings(\"this-escape\")\n";
    code << "public Stub()\n";
  }
  code << "{\n";
  code.Indent();
  if (interfaceType->IsVintfStability()) {
    code << "this.markVintfStability();\n";
  }
  code << "this.attachInterface(this, DESCRIPTOR);\n";
  if (interfaceType->UsesPermissions()) {
    code << "if (enforcer == null) {\n";
    code.Indent();
    code << "throw new IllegalArgumentException(\"enforcer cannot be null\");\n";
    code.Dedent();
    code << "}\n";
    code << "mEnforcer = enforcer;\n";
  }
  code.Dedent();
  code << "}\n";

  // Setup a default constructor for permissions interfaces.
  if (interfaceType->UsesPermissions()) {
    code << "@Deprecated\n";
    code << "/** Default constructor. */\n";
    code << "public Stub() {\n";
    code.Indent();
    code << "this(android.os.PermissionEnforcer.fromContext(\n";
    code << "   android.app.ActivityThread.currentActivityThread().getSystemContext()));\n";
    code.Dedent();
    code << "}\n";
  }

  code.Close();
  this->elements.push_back(std::make_shared<LiteralClassElement>(ctors_code));
}

void StubClass::MakeAsInterface(const AidlInterface* interfaceType) {
  auto obj = std::make_shared<Variable>("android.os.IBinder", "obj");

  auto m = std::make_shared<Method>();
  m->comment = "/**\n * Cast an IBinder object into an ";
  m->comment += interfaceType->GetCanonicalName();
  m->comment += " interface,\n";
  m->comment += " * generating a proxy if needed.\n */";
  m->modifiers = PUBLIC | STATIC;
  m->returnType = interfaceType->GetCanonicalName();
  m->name = "asInterface";
  m->parameters.push_back(obj);
  m->statements = std::make_shared<StatementBlock>();

  auto ifstatement = std::make_shared<IfStatement>();
  ifstatement->expression = std::make_shared<Comparison>(obj, "==", NULL_VALUE);
  ifstatement->statements = std::make_shared<StatementBlock>();
  ifstatement->statements->Add(std::make_shared<ReturnStatement>(NULL_VALUE));
  m->statements->Add(ifstatement);

  // IInterface iin = obj.queryLocalInterface(DESCRIPTOR)
  auto queryLocalInterface = std::make_shared<MethodCall>(obj, "queryLocalInterface");
  queryLocalInterface->arguments.push_back(std::make_shared<LiteralExpression>("DESCRIPTOR"));
  auto iin = std::make_shared<Variable>("android.os.IInterface", "iin");
  auto iinVd = std::make_shared<VariableDeclaration>(iin, queryLocalInterface);
  m->statements->Add(iinVd);

  // Ensure the instance type of the local object is as expected.
  // One scenario where this is needed is if another package (with a
  // different class loader) runs in the same process as the service.

  // if (iin != null && iin instanceof <interfaceType>) return (<interfaceType>)
  // iin;
  auto iinNotNull = std::make_shared<Comparison>(iin, "!=", NULL_VALUE);
  auto instOfCheck = std::make_shared<Comparison>(
      iin, " instanceof ", std::make_shared<LiteralExpression>(interfaceType->GetCanonicalName()));
  auto instOfStatement = std::make_shared<IfStatement>();
  instOfStatement->expression = std::make_shared<Comparison>(iinNotNull, "&&", instOfCheck);
  instOfStatement->statements = std::make_shared<StatementBlock>();
  instOfStatement->statements->Add(std::make_shared<ReturnStatement>(
      std::make_shared<Cast>(interfaceType->GetCanonicalName(), iin)));
  m->statements->Add(instOfStatement);

  auto ne = std::make_shared<NewExpression>(interfaceType->GetCanonicalName() + ".Stub.Proxy");
  ne->arguments.push_back(obj);
  m->statements->Add(std::make_shared<ReturnStatement>(ne));

  this->elements.push_back(m);
}

// =================================================
class ProxyClass : public Class {
 public:
  ProxyClass(const AidlInterface* interfaceType, const Options& options);
  ~ProxyClass() override;

  std::shared_ptr<Variable> mRemote;
};

ProxyClass::ProxyClass(const AidlInterface* interfaceType, const Options& options) : Class() {
  this->modifiers = PRIVATE | STATIC;
  this->what = Class::CLASS;
  this->type = interfaceType->GetCanonicalName() + ".Stub.Proxy";
  this->interfaces.push_back(interfaceType->GetCanonicalName());

  // IBinder mRemote
  mRemote = std::make_shared<Variable>("android.os.IBinder", "mRemote");
  this->elements.push_back(std::make_shared<Field>(PRIVATE, mRemote));

  // Proxy()
  auto remote = std::make_shared<Variable>("android.os.IBinder", "remote");
  auto ctor = std::make_shared<Method>();
  ctor->name = "Proxy";
  ctor->statements = std::make_shared<StatementBlock>();
  ctor->parameters.push_back(remote);
  ctor->statements->Add(std::make_shared<Assignment>(mRemote, remote));
  this->elements.push_back(ctor);

  if (options.Version() > 0) {
    std::ostringstream code;
    code << "private int mCachedVersion = -1;\n";
    this->elements.emplace_back(std::make_shared<LiteralClassElement>(code.str()));
  }
  if (!options.Hash().empty()) {
    std::ostringstream code;
    code << "private String mCachedHash = \"-1\";\n";
    this->elements.emplace_back(std::make_shared<LiteralClassElement>(code.str()));
  }

  // IBinder asBinder()
  auto asBinder = std::make_shared<Method>();
  asBinder->modifiers = PUBLIC | OVERRIDE;
  asBinder->returnType = "android.os.IBinder";
  asBinder->name = "asBinder";
  asBinder->statements = std::make_shared<StatementBlock>();
  asBinder->statements->Add(std::make_shared<ReturnStatement>(mRemote));
  this->elements.push_back(asBinder);
}

ProxyClass::~ProxyClass() {}

// =================================================

static void GenerateWriteToParcel(CodeWriter& out, const AidlTypenames& typenames,
                                  const AidlTypeSpecifier& type, const std::string& parcel,
                                  const std::string& var, uint32_t min_sdk_version,
                                  bool is_return_value) {
  WriteToParcelFor(CodeGeneratorContext{
      .writer = out,
      .typenames = typenames,
      .type = type,
      .parcel = parcel,
      .var = var,
      .min_sdk_version = min_sdk_version,
      .write_to_parcel_flag =
          is_return_value ? "android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE" : "0",
  });
}

static void GenerateWriteToParcel(std::shared_ptr<StatementBlock> addTo,
                                  const AidlTypenames& typenames, const AidlTypeSpecifier& type,
                                  const std::string& parcel, const std::string& var,
                                  uint32_t min_sdk_version, bool is_return_value) {
  string code;
  GenerateWriteToParcel(*CodeWriter::ForString(&code), typenames, type, parcel, var,
                        min_sdk_version, is_return_value);
  addTo->Add(std::make_shared<LiteralStatement>(code));
}

void GenerateConstantDeclarations(CodeWriter& out, const AidlDefinedType& type) {
  for (const auto& constant : type.GetConstantDeclarations()) {
    const AidlTypeSpecifier& type = constant->GetType();
    out << GenerateComments(*constant);
    out << GenerateAnnotations(*constant);
    out << "public static final " << type.Signature() << " " << constant->GetName() << " = "
        << constant->ValueString(ConstantValueDecorator) << ";\n";
  }
}

static std::shared_ptr<Method> GenerateInterfaceMethod(const AidlInterface& iface,
                                                       const AidlMethod& method) {
  auto decl = std::make_shared<Method>();
  decl->comment = GenerateComments(method);
  decl->modifiers = PUBLIC;
  decl->returnType = JavaSignatureOf(method.GetType());
  decl->name = method.GetName();
  decl->annotations = JavaAnnotationsFor(method);
  // If the interface has some permission annotation, add it to the method.
  if (auto iface_annotation = JavaPermissionAnnotation(iface); iface_annotation) {
    decl->annotations.push_back(*iface_annotation);
  }

  for (const std::unique_ptr<AidlArgument>& arg : method.GetArguments()) {
    auto var = std::make_shared<Variable>(JavaSignatureOf(arg->GetType()), arg->GetName());
    var->annotations = JavaAnnotationsFor(arg->GetType());
    decl->parameters.push_back(var);
  }

  decl->exceptions.push_back("android.os.RemoteException");

  return decl;
}

// Visitor for the permission declared in the @EnforcePermission annotation.
class PermissionVisitor {
 public:
  PermissionVisitor(CodeWriter* code, const AidlMethod& method) : code_(code), method_(method) {}

  ~PermissionVisitor() {
    code_->Dedent();
    *code_ << "}\n";
  }

  string Credentials() const { return "getCallingPid(), getCallingUid()"; }

  void Prologue() {
    *code_ << "/** Helper method to enforce permissions for " << method_.GetName() << " */\n";
    *code_ << "protected void " << method_.GetName() << "_enforcePermission() "
           << "throws SecurityException {\n";
    code_->Indent();
  }

  void AddStaticArrayPermissions(const std::vector<std::string>& permissions) {
    *code_ << "static final String[] PERMISSIONS_" << method_.GetName() << " = {"
           << Join(permissions, ", ") << "};\n";
  }

  void operator()(const perm::AllOf& quantifier) {
    std::vector<std::string> permissions;
    permissions.reserve(quantifier.operands.size());
    for (auto const& permission : quantifier.operands) {
      permissions.push_back(android::aidl::perm::JavaFullName(permission));
    }
    AddStaticArrayPermissions(permissions);
    Prologue();
    *code_ << "mEnforcer.enforcePermissionAllOf(PERMISSIONS_" << method_.GetName() << ", "
           << Credentials() << ");\n";
  }

  void operator()(const perm::AnyOf& quantifier) {
    std::vector<std::string> permissions;
    permissions.reserve(quantifier.operands.size());
    for (auto const& permission : quantifier.operands) {
      permissions.push_back(android::aidl::perm::JavaFullName(permission));
    }
    AddStaticArrayPermissions(permissions);
    Prologue();
    *code_ << "mEnforcer.enforcePermissionAnyOf(PERMISSIONS_" << method_.GetName() << ", "
           << Credentials() << ");\n";
  }

  void operator()(const std::string& permission) {
    auto permissionName = android::aidl::perm::JavaFullName(permission);
    Prologue();
    *code_ << "mEnforcer.enforcePermission(" << permissionName << ", " << Credentials() << ");\n";
  }

 private:
  CodeWriter* code_;
  const AidlMethod& method_;
};

static void GeneratePermissionMethod(const AidlInterface& iface, const AidlMethod& method,
                                     const std::shared_ptr<Class>& addTo) {
  string code;
  CodeWriterPtr writer = CodeWriter::ForString(&code);

  if (auto ifacePermExpr = iface.EnforceExpression(); ifacePermExpr) {
    std::visit(PermissionVisitor(writer.get(), method), *ifacePermExpr.get());
  } else if (auto methodPermExpr = method.GetType().EnforceExpression(); methodPermExpr) {
    std::visit(PermissionVisitor(writer.get(), method), *methodPermExpr.get());
  }
  writer->Close();
  addTo->elements.push_back(std::make_shared<LiteralClassElement>(code));
}

static void GenerateStubCode(const AidlMethod& method, bool oneway,
                             std::shared_ptr<Variable> transact_data,
                             std::shared_ptr<Variable> transact_reply,
                             const AidlTypenames& typenames,
                             std::shared_ptr<StatementBlock> statement_block,
                             const Options& options) {
  // try and finally
  auto& statements = statement_block;

  auto realCall = std::make_shared<MethodCall>(THIS_VALUE, method.GetName());

  // args
  VariableFactory stubArgs("_arg");
  {
    // keep this across different args in order to create the classloader
    // at most once.
    bool is_classloader_created = false;

    if (method.IsNew() && ShouldForceDowngradeFor(CommunicationSide::READ)) {
      auto if_statement = std::make_shared<IfStatement>();
      if_statement->expression = std::make_shared<LiteralExpression>("true");
      if_statement->statements = std::make_shared<StatementBlock>();
      if_statement->statements->Add(
          std::make_shared<LiteralExpression>("throw new android.os.RemoteException(\"Method " +
                                              method.GetName() + " is unimplemented.\")"));
      statements->Add(if_statement);
    }
    for (const std::unique_ptr<AidlArgument>& arg : method.GetArguments()) {
      std::shared_ptr<Variable> v = stubArgs.Get(arg->GetType());

      statements->Add(std::make_shared<VariableDeclaration>(v));

      string code;
      CodeWriterPtr writer = CodeWriter::ForString(&code);
      if (arg->GetDirection() & AidlArgument::IN_DIR) {
        // "in/inout" parameter should be created from parcel.
        CodeGeneratorContext context{.writer = *(writer.get()),
                                     .typenames = typenames,
                                     .type = arg->GetType(),
                                     .parcel = transact_data->name,
                                     .var = v->name,
                                     .min_sdk_version = options.GetMinSdkVersion(),
                                     .is_classloader_created = &is_classloader_created};
        CreateFromParcelFor(context);
      } else {
        // "out" parameter should be instantiated before calling the real impl.
        string java_type = InstantiableJavaSignatureOf(arg->GetType());

        if (arg->GetType().IsDynamicArray()) {
          // dynamic array should be created with a passed length.
          string var_length = v->name + "_length";
          (*writer) << "int " << var_length << " = data.readInt();\n";
          (*writer) << "if (" << var_length << " < 0) {\n";
          (*writer) << "  " << v->name << " = null;\n";
          (*writer) << "} else {\n";
          (*writer) << "  " << v->name << " = new " << java_type << "[" << var_length << "];\n";
          (*writer) << "}\n";
        } else if (arg->GetType().IsFixedSizeArray()) {
          // fixed-size array can be created with a known size
          string dimensions;
          for (auto dim : arg->GetType().GetFixedSizeArrayDimensions()) {
            dimensions += "[" + std::to_string(dim) + "]";
          }
          (*writer) << v->name << " = new " << java_type << dimensions << ";\n";
        } else {
          // otherwise, create a new instance with a default constructor
          (*writer) << v->name << " = new " << java_type << "();\n";
        }
      }
      writer->Close();
      statements->Add(std::make_shared<LiteralStatement>(code));

      realCall->arguments.push_back(v);
    }
  }

  // EOF check
  if (!method.GetArguments().empty() && options.GetMinSdkVersion() > 32u) {
    statements->Add(std::make_shared<MethodCall>(transact_data, "enforceNoDataAvail"));
  }

  // the real call
  if (method.GetType().GetName() == "void") {
    statements->Add(realCall);

    if (!oneway) {
      // report that there were no exceptions
      auto ex = std::make_shared<MethodCall>(transact_reply, "writeNoException");
      statements->Add(ex);
    }
  } else {
    auto _result = std::make_shared<Variable>(JavaSignatureOf(method.GetType()), "_result");
    statements->Add(std::make_shared<VariableDeclaration>(_result, realCall));

    if (!oneway) {
      // report that there were no exceptions
      auto ex = std::make_shared<MethodCall>(transact_reply, "writeNoException");
      statements->Add(ex);
    }

    // marshall the return value
    GenerateWriteToParcel(statements, typenames, method.GetType(), transact_reply->name,
                          _result->name, options.GetMinSdkVersion(), /*is_return_value=*/true);
  }

  // out parameters
  int i = 0;
  for (const std::unique_ptr<AidlArgument>& arg : method.GetArguments()) {
    std::shared_ptr<Variable> v = stubArgs.Get(i++);
    if (arg->GetDirection() & AidlArgument::OUT_DIR) {
      GenerateWriteToParcel(statements, typenames, arg->GetType(), transact_reply->name, v->name,
                            options.GetMinSdkVersion(), /*is_return_value=*/true);
    }
  }
}

static void GenerateStubCase(const AidlMethod& method, const std::string& transactCodeName,
                             bool oneway, const std::shared_ptr<StubClass>& stubClass,
                             const AidlTypenames& typenames, const Options& options) {
  auto c = std::make_shared<Case>(transactCodeName);

  GenerateStubCode(method, oneway, stubClass->transact_data, stubClass->transact_reply, typenames,
                   c->statements, options);
  c->statements->Add(std::make_shared<BreakStatement>());

  stubClass->transact_switch_user->cases.push_back(c);
}

static void GenerateStubCaseOutline(const AidlMethod& method, const std::string& transactCodeName,
                                    bool oneway, const std::shared_ptr<StubClass>& stubClass,
                                    const AidlTypenames& typenames, const Options& options) {
  std::string outline_name = "onTransact$" + method.GetName() + "$";
  // Generate an "outlined" method with the actual code.
  {
    auto transact_data = std::make_shared<Variable>("android.os.Parcel", "data");
    auto transact_reply = std::make_shared<Variable>("android.os.Parcel", "reply");
    auto onTransact_case = std::make_shared<Method>();
    onTransact_case->modifiers = PRIVATE;
    onTransact_case->returnType = "boolean";
    onTransact_case->name = outline_name;
    onTransact_case->parameters.push_back(transact_data);
    onTransact_case->parameters.push_back(transact_reply);
    onTransact_case->statements = std::make_shared<StatementBlock>();
    onTransact_case->exceptions.push_back("android.os.RemoteException");
    stubClass->elements.push_back(onTransact_case);

    GenerateStubCode(method, oneway, transact_data, transact_reply, typenames,
                     onTransact_case->statements, options);
    onTransact_case->statements->Add(std::make_shared<ReturnStatement>(TRUE_VALUE));
  }

  // Generate the case dispatch.
  {
    auto c = std::make_shared<Case>(transactCodeName);

    auto helper_call =
        std::make_shared<MethodCall>(THIS_VALUE, outline_name,
                                     std::vector<std::shared_ptr<Expression>>{
                                         stubClass->transact_data, stubClass->transact_reply});
    c->statements->Add(std::make_shared<ReturnStatement>(helper_call));

    stubClass->transact_switch_user->cases.push_back(c);
  }
}

template <typename Formatter>
static std::string ArgList(const AidlMethod& method, Formatter formatter) {
  vector<string> args;
  for (const auto& arg : method.GetArguments()) {
    args.push_back(std::invoke(formatter, *arg));
  }
  return Join(args, ", ");
}

static std::string FormatArgForDecl(const AidlArgument& arg) {
  return JavaSignatureOf(arg.GetType()) + " " + arg.GetName();
}

static void GenerateProxyMethod(CodeWriter& out, const AidlInterface& iface,
                                const AidlMethod& method, const std::string& transactCodeName,
                                bool oneway, const AidlTypenames& typenames,
                                const Options& options) {
  bool is_void = method.GetType().GetName() == "void";

  out << GenerateComments(method);
  out << "@Override public " << JavaSignatureOf(method.GetType()) << " " << method.GetName() << "("
      << ArgList(method, FormatArgForDecl) << ") throws android.os.RemoteException\n{\n";
  out.Indent();

  if (method.IsNew() && ShouldForceDowngradeFor(CommunicationSide::WRITE)) {
    out << "if (true) {\n";
    out << "  throw new android.os.RemoteException(\"Method " + method.GetName() +
               " is unimplemented.\");\n";
    out << "}\n";
  }

  // the parcels
  if (options.GenRpc()) {
    out << "android.os.Parcel _data = android.os.Parcel.obtain(asBinder());\n";
  } else {
    out << "android.os.Parcel _data = android.os.Parcel.obtain();\n";
  }

  if (iface.IsSensitiveData()) {
    out << "_data.markSensitive();\n";
  }

  if (!oneway) {
    out << "android.os.Parcel _reply = android.os.Parcel.obtain();\n";
  }

  // the return value
  if (!is_void) {
    out << JavaSignatureOf(method.GetType()) << " _result;\n";
  }

  out << "try {\n";
  out.Indent();

  // the interface identifier token: the DESCRIPTOR constant, marshalled as a
  // string
  out << "_data.writeInterfaceToken(DESCRIPTOR);\n";

  // the parameters
  for (const std::unique_ptr<AidlArgument>& arg : method.GetArguments()) {
    AidlArgument::Direction dir = arg->GetDirection();
    if (dir == AidlArgument::OUT_DIR && arg->GetType().IsDynamicArray()) {
      // In Java we pass a pre-allocated array for an 'out' argument. For transaction,
      // we pass the size of the array so that the remote can allocate the array with the same size.
      out << "_data.writeInt(" << arg->GetName() << ".length);\n";
    } else if (dir & AidlArgument::IN_DIR) {
      GenerateWriteToParcel(out, typenames, arg->GetType(), "_data", arg->GetName(),
                            options.GetMinSdkVersion(), /*is_return_value=*/false);
    }
  }

  std::vector<std::string> flags;
  if (oneway) flags.push_back("android.os.IBinder.FLAG_ONEWAY");
  if (iface.IsSensitiveData()) flags.push_back("android.os.IBinder.FLAG_CLEAR_BUF");

  // the transact call
  out << "boolean _status = mRemote.transact(Stub." << transactCodeName << ", _data, "
      << (oneway ? "null" : "_reply") << ", " << (flags.empty() ? "0" : Join(flags, " | "))
      << ");\n";

  // TODO(b/151102494): annotation is applied on the return type
  if (method.GetType().IsPropagateAllowBlocking() && !oneway) {
    if (options.GetMinSdkVersion() < JAVA_PROPAGATE_VERSION) {
      out << "if (android.os.Build.VERSION.SDK_INT >= " + std::to_string(JAVA_PROPAGATE_VERSION) +
                 ") { _reply.setPropagateAllowBlocking(); }\n";
    } else {
      out << "_reply.setPropagateAllowBlocking();\n";
    }
  }

  // If the transaction returns false, which means UNKNOWN_TRANSACTION, fall back to the local
  // method in the default impl, if set before. Otherwise, throw a RuntimeException if the interface
  // is versioned. We can't throw the exception for unversioned interface because that would be an
  // app breaking change.
  if (iface.IsJavaDefault() || options.Version() > 0) {
    out << "if (!_status) {\n";
    out.Indent();

    if (iface.IsJavaDefault()) {
      out << "if (getDefaultImpl() != null) {\n";
      out.Indent();
      if (is_void) {
        out << "getDefaultImpl()." << method.GetName() << "("
            << ArgList(method, &AidlArgument::GetName) << ");\n";
        out << "return;\n";
      } else {
        out << "return getDefaultImpl()." << method.GetName() << "("
            << ArgList(method, &AidlArgument::GetName) << ");\n";
      }
      out.Dedent();
      out << "}\n";
    }

    // TODO(b/274144762): we shouldn't have different behavior for versioned interfaces
    // also this set to false for all exceptions, not just unimplemented methods.
    if (options.Version() > 0) {
      out << "throw new android.os.RemoteException(\"Method " << method.GetName()
          << " is unimplemented.\");\n";
    }

    out.Dedent();
    out << "}\n";
  }

  if (!oneway) {
    // keep this across return value and arguments in order to create the
    // classloader at most once.
    bool is_classloader_created = false;

    // throw back exceptions.
    out << "_reply.readException();\n";

    if (!is_void) {
      CreateFromParcelFor(CodeGeneratorContext{.writer = out,
                                               .typenames = typenames,
                                               .type = method.GetType(),
                                               .parcel = "_reply",
                                               .var = "_result",
                                               .min_sdk_version = options.GetMinSdkVersion(),
                                               .is_classloader_created = &is_classloader_created});
    }

    // the out/inout parameters
    for (const std::unique_ptr<AidlArgument>& arg : method.GetArguments()) {
      if (arg->GetDirection() & AidlArgument::OUT_DIR) {
        ReadFromParcelFor(CodeGeneratorContext{.writer = out,
                                               .typenames = typenames,
                                               .type = arg->GetType(),
                                               .parcel = "_reply",
                                               .var = arg->GetName(),
                                               .min_sdk_version = options.GetMinSdkVersion(),
                                               .is_classloader_created = &is_classloader_created});
      }
    }
  }

  out.Dedent();
  out << "}\nfinally {\n";
  out.Indent();

  // returning and cleanup
  if (!oneway) {
    out << "_reply.recycle();\n";
  }
  out << "_data.recycle();\n";

  out.Dedent();
  out << "}\n";  // finally

  if (!is_void) {
    out << "return _result;\n";
  }

  out.Dedent();
  out << "}\n";  // method body
}

static void GenerateMethods(const AidlInterface& iface, const AidlMethod& method, Class* interface,
                            std::shared_ptr<StubClass> stubClass,
                            std::shared_ptr<ProxyClass> proxyClass, int index,
                            const AidlTypenames& typenames, const Options& options) {
  const bool oneway = method.IsOneway();

  // == the TRANSACT_ constant =============================================
  string transactCodeName = "TRANSACTION_";
  transactCodeName += method.GetName();

  auto transactCode =
      std::make_shared<Field>(STATIC | FINAL, std::make_shared<Variable>("int", transactCodeName));
  transactCode->value =
      StringPrintf("(android.os.IBinder.FIRST_CALL_TRANSACTION + %d)", index);
  stubClass->elements.push_back(transactCode);

  // getTransactionName
  if (options.GenTransactionNames() || options.GenTraces()) {
    auto c = std::make_shared<Case>(transactCodeName);
    c->statements->Add(std::make_shared<ReturnStatement>(
        std::make_shared<StringLiteralExpression>(method.GetName())));
    stubClass->code_to_method_name_switch->cases.push_back(c);
  }

  // == the declaration in the interface ===================================
  std::shared_ptr<ClassElement> decl;
  if (method.IsUserDefined()) {
    decl = GenerateInterfaceMethod(iface, method);
  } else {
    if (method.GetName() == kGetInterfaceVersion && options.Version() > 0) {
      std::ostringstream code;
      code << "public int " << kGetInterfaceVersion << "() "
           << "throws android.os.RemoteException;\n";
      decl = std::make_shared<LiteralClassElement>(code.str());
    }
    if (method.GetName() == kGetInterfaceHash && !options.Hash().empty()) {
      std::ostringstream code;
      code << "public String " << kGetInterfaceHash << "() "
           << "throws android.os.RemoteException;\n";
      decl = std::make_shared<LiteralClassElement>(code.str());
    }
  }
  interface->elements.push_back(decl);

  // == the stub method ====================================================
  if (method.IsUserDefined()) {
    bool outline_stub =
        stubClass->transact_outline && stubClass->outline_methods.count(&method) != 0;
    if (outline_stub) {
      GenerateStubCaseOutline(method, transactCodeName, oneway, stubClass, typenames, options);
    } else {
      GenerateStubCase(method, transactCodeName, oneway, stubClass, typenames, options);
    }
    if (iface.EnforceExpression() || method.GetType().EnforceExpression()) {
      GeneratePermissionMethod(iface, method, stubClass);
    }
  } else {
    if (method.GetName() == kGetInterfaceVersion && options.Version() > 0) {
      auto ifStatement = std::make_shared<IfStatement>();
      ifStatement->expression = std::make_shared<LiteralExpression>("code == " + transactCodeName);
      std::ostringstream code;
      code << "reply.writeNoException();\n"
           << "reply.writeInt(" << kGetInterfaceVersion << "());\n"
           << "return true;\n";
      ifStatement->statements->Add(std::make_shared<LiteralStatement>(code.str()));
      stubClass->transact_if_statements_meta.push_back(ifStatement);
    }
    if (method.GetName() == kGetInterfaceHash && !options.Hash().empty()) {
      auto ifStatement = std::make_shared<IfStatement>();
      ifStatement->expression = std::make_shared<LiteralExpression>("code == " + transactCodeName);
      std::ostringstream code;
      code << "reply.writeNoException();\n"
           << "reply.writeString(" << kGetInterfaceHash << "());\n"
           << "return true;\n";
      ifStatement->statements->Add(std::make_shared<LiteralStatement>(code.str()));
      stubClass->transact_if_statements_meta.push_back(ifStatement);
    }
  }

  // == the proxy method ===================================================
  string proxy_code;
  CodeWriterPtr writer = CodeWriter::ForString(&proxy_code);
  CodeWriter& code = *writer;
  if (method.IsUserDefined()) {
    GenerateProxyMethod(code, iface, method, transactCodeName, oneway, typenames, options);
  } else {
    if (method.GetName() == kGetInterfaceVersion && options.Version() > 0) {
      code << "@Override\n"
           << "public int " << kGetInterfaceVersion << "()"
           << " throws "
           << "android.os.RemoteException {\n"
           << "  if (mCachedVersion == -1) {\n";
      if (options.GenRpc()) {
        code << "    android.os.Parcel data = android.os.Parcel.obtain(asBinder());\n";
      } else {
        code << "    android.os.Parcel data = android.os.Parcel.obtain();\n";
      }
      code << "    android.os.Parcel reply = android.os.Parcel.obtain();\n"
           << "    try {\n"
           << "      data.writeInterfaceToken(DESCRIPTOR);\n"
           << "      boolean _status = mRemote.transact(Stub." << transactCodeName << ", "
           << "data, reply, 0);\n";
      if (iface.IsJavaDefault()) {
        code << "      if (!_status) {\n"
             << "        if (getDefaultImpl() != null) {\n"
             << "          return getDefaultImpl().getInterfaceVersion();\n"
             << "        }\n"
             << "      }\n";
      }
      code << "      reply.readException();\n"
           << "      mCachedVersion = reply.readInt();\n"
           << "    } finally {\n"
           << "      reply.recycle();\n"
           << "      data.recycle();\n"
           << "    }\n"
           << "  }\n"
           << "  return mCachedVersion;\n"
           << "}\n";
    }
    if (method.GetName() == kGetInterfaceHash && !options.Hash().empty()) {
      code << "@Override\n"
           << "public synchronized String " << kGetInterfaceHash << "()"
           << " throws "
           << "android.os.RemoteException {\n"
           << "  if (\"-1\".equals(mCachedHash)) {\n";
      if (options.GenRpc()) {
        code << "    android.os.Parcel data = android.os.Parcel.obtain(asBinder());\n";
      } else {
        code << "    android.os.Parcel data = android.os.Parcel.obtain();\n";
      }
      code << "    android.os.Parcel reply = android.os.Parcel.obtain();\n"
           << "    try {\n"
           << "      data.writeInterfaceToken(DESCRIPTOR);\n"
           << "      boolean _status = mRemote.transact(Stub." << transactCodeName << ", "
           << "data, reply, 0);\n";
      if (iface.IsJavaDefault()) {
        code << "      if (!_status) {\n"
             << "        if (getDefaultImpl() != null) {\n"
             << "          return getDefaultImpl().getInterfaceHash();\n"
             << "        }\n"
             << "      }\n";
      }
      code << "      reply.readException();\n"
           << "      mCachedHash = reply.readString();\n"
           << "    } finally {\n"
           << "      reply.recycle();\n"
           << "      data.recycle();\n"
           << "    }\n"
           << "  }\n"
           << "  return mCachedHash;\n"
           << "}\n";
    }
  }
  code.Close();
  if (!proxy_code.empty()) {
    proxyClass->elements.push_back(std::make_shared<LiteralClassElement>(proxy_code));
  }
}

static void GenerateInterfaceDescriptors(const Options& options, const AidlInterface* iface,
                                         Class* interface, std::shared_ptr<StubClass> stub,
                                         std::shared_ptr<ProxyClass> proxy) {
  // the interface descriptor transaction handler
  auto ifStatement = std::make_shared<IfStatement>();
  ifStatement->expression = std::make_shared<LiteralExpression>("code == INTERFACE_TRANSACTION");
  ifStatement->statements->Add(std::make_shared<MethodCall>(
      stub->transact_reply, "writeString",
      std::vector<std::shared_ptr<Expression>>{stub->GetTransactDescriptor(nullptr)}));
  ifStatement->statements->Add(std::make_shared<ReturnStatement>(TRUE_VALUE));
  stub->transact_if_statements_meta.push_back(ifStatement);

  // and the proxy-side method returning the descriptor directly
  auto getDesc = std::make_shared<Method>();
  getDesc->modifiers = PUBLIC;
  getDesc->returnType = "java.lang.String";
  getDesc->name = "getInterfaceDescriptor";
  getDesc->statements = std::make_shared<StatementBlock>();
  getDesc->statements->Add(
      std::make_shared<ReturnStatement>(std::make_shared<LiteralExpression>("DESCRIPTOR")));
  proxy->elements.push_back(getDesc);

  // add the DESCRIPTOR field to the interface class
  Class* classToAddDescriptor = interface;
  static std::set<std::string> greylist = {
#include "hiddenapi-greylist"
  };
  if (greylist.find(iface->GetCanonicalName()) != greylist.end()) {
    // For app compatibility, we keep DESCRIPTOR to the stub class for
    // the interfaces that are in the greylist.
    classToAddDescriptor = stub.get();
  }
  auto descriptor = std::make_shared<Field>(
      STATIC | FINAL | PUBLIC, std::make_shared<Variable>("java.lang.String", "DESCRIPTOR"));
  descriptor->comment = "/** @hide */";
  std::string name = iface->GetDescriptor();

  // TODO(b/242862858): avoid differentiating behahavior. This is currently blocked
  // to fix because of a metalava lint which disallows running code to generate
  // static fields.
  if (options.IsStructured()) {
    // mangle the interface name at build time and demangle it at runtime, to avoid
    // being renamed by jarjar. See b/153843174
    std::replace(name.begin(), name.end(), '.', '$');
    descriptor->value = "\"" + name + "\".replace('$', '.')";
  } else {
    descriptor->value = "\"" + name + "\"";
  }
  classToAddDescriptor->elements.push_back(descriptor);
}

// Check whether (some) methods in this interface should be "outlined," that
// is, have specific onTransact methods for certain cases. Set up StubClass
// metadata accordingly.
//
// Outlining will be enabled if the interface has more than outline_threshold
// methods. In that case, the methods are sorted by number of arguments
// (so that more "complex" methods come later), and the first non_outline_count
// number of methods not outlined (are kept in the onTransact() method).
//
// Requirements: non_outline_count <= outline_threshold.
static void ComputeOutlineMethods(const AidlInterface* iface, const std::shared_ptr<StubClass> stub,
                                  size_t outline_threshold, size_t non_outline_count) {
  AIDL_FATAL_IF(non_outline_count > outline_threshold, iface);
  // We'll outline (create sub methods) if there are more than min_methods
  // cases.
  stub->transact_outline = iface->GetMethods().size() > outline_threshold;
  if (stub->transact_outline) {
    stub->all_method_count = iface->GetMethods().size();
    std::vector<const AidlMethod*> methods;
    methods.reserve(iface->GetMethods().size());
    for (const auto& ptr : iface->GetMethods()) {
      methods.push_back(ptr.get());
    }

    std::stable_sort(
        methods.begin(),
        methods.end(),
        [](const AidlMethod* m1, const AidlMethod* m2) {
          return m1->GetArguments().size() < m2->GetArguments().size();
        });

    stub->outline_methods.insert(methods.begin() + non_outline_count,
                                 methods.end());
  }
}

static shared_ptr<ClassElement> GenerateDefaultImplMethod(const AidlMethod& method) {
  auto default_method = std::make_shared<Method>();
  default_method->comment = GenerateComments(method);
  default_method->modifiers = PUBLIC | OVERRIDE;
  default_method->returnType = JavaSignatureOf(method.GetType());
  default_method->name = method.GetName();
  default_method->statements = std::make_shared<StatementBlock>();
  for (const auto& arg : method.GetArguments()) {
    default_method->parameters.push_back(
        std::make_shared<Variable>(JavaSignatureOf(arg->GetType()), arg->GetName()));
  }
  default_method->exceptions.push_back("android.os.RemoteException");

  if (method.GetType().GetName() != "void") {
    const string& defaultValue = DefaultJavaValueOf(method.GetType());
    default_method->statements->Add(
        std::make_shared<LiteralStatement>(StringPrintf("return %s;\n", defaultValue.c_str())));
  }
  return default_method;
}

static shared_ptr<Class> GenerateDefaultImplClass(const AidlInterface& iface,
                                                  const Options& options) {
  auto default_class = std::make_shared<Class>();
  default_class->comment = "/** Default implementation for " + iface.GetName() + ". */";
  default_class->modifiers = PUBLIC | STATIC;
  default_class->what = Class::CLASS;
  default_class->type = iface.GetCanonicalName() + ".Default";
  default_class->interfaces.emplace_back(iface.GetCanonicalName());

  for (const auto& m : iface.GetMethods()) {
    if (m->IsUserDefined()) {
      default_class->elements.emplace_back(GenerateDefaultImplMethod(*m));
    } else {
      // These are called only when the remote side does not implement these
      // methods, which is normally impossible, because these methods are
      // automatically declared in the interface class and not implementing
      // them on the remote side causes a compilation error. But if the remote
      // side somehow managed to not implement it, that's an error and we
      // report the case by returning an invalid value here.
      if (m->GetName() == kGetInterfaceVersion && options.Version() > 0) {
        std::ostringstream code;
        code << "@Override\n"
             << "public int " << kGetInterfaceVersion << "() {\n"
             << "  return 0;\n"
             << "}\n";
        default_class->elements.emplace_back(std::make_shared<LiteralClassElement>(code.str()));
      }
      if (m->GetName() == kGetInterfaceHash && !options.Hash().empty()) {
        std::ostringstream code;
        code << "@Override\n"
             << "public String " << kGetInterfaceHash << "() {\n"
             << "  return \"\";\n"
             << "}\n";
        default_class->elements.emplace_back(std::make_shared<LiteralClassElement>(code.str()));
      }
    }
  }

  default_class->elements.emplace_back(
      std::make_shared<LiteralClassElement>("@Override\n"
                                            "public android.os.IBinder asBinder() {\n"
                                            "  return null;\n"
                                            "}\n"));

  return default_class;
}

static shared_ptr<ClassElement> GenerateDelegatorMethod(const AidlMethod& method) {
  auto delegator_method = std::make_shared<Method>();
  delegator_method->comment = GenerateComments(method);
  delegator_method->modifiers = PUBLIC | OVERRIDE;
  delegator_method->returnType = JavaSignatureOf(method.GetType());
  delegator_method->name = method.GetName();
  delegator_method->statements = std::make_shared<StatementBlock>();
  std::vector<std::string> argNames;
  for (const auto& arg : method.GetArguments()) {
    delegator_method->parameters.push_back(
        std::make_shared<Variable>(JavaSignatureOf(arg->GetType()), arg->GetName()));
    argNames.push_back(arg->GetName());
  }
  delegator_method->exceptions.push_back("android.os.RemoteException");

  std::string return_str;
  if (method.GetType().GetName() != "void") {
    return_str = "return ";
  }
  delegator_method->statements->Add(
      std::make_shared<LiteralStatement>(return_str + "mImpl." + method.GetName() + "(" +
                                         android::base::Join(argNames, ",") + ");\n"));
  return delegator_method;
}

static shared_ptr<Class> GenerateDelegatorClass(const AidlInterface& iface,
                                                const Options& options) {
  auto delegator_class = std::make_shared<Class>();
  delegator_class->comment = "/** Delegator implementation for " + iface.GetName() + ". */";
  delegator_class->modifiers = PUBLIC | STATIC;
  delegator_class->what = Class::CLASS;
  delegator_class->type = iface.GetCanonicalName() + ".Delegator";
  delegator_class->extends = iface.GetCanonicalName() + ".Stub";

  // constructor
  delegator_class->elements.emplace_back(
      std::make_shared<LiteralClassElement>("public Delegator(" + iface.GetCanonicalName() +
                                            " impl) {\n"
                                            "  this.mImpl = impl;\n"
                                            "}\n"));
  // meta methods
  if (!options.Hash().empty()) {
    delegator_class->elements.emplace_back(
        std::make_shared<LiteralClassElement>("@Override\n"
                                              "public String " +
                                              kGetInterfaceHash +
                                              "() throws android.os.RemoteException {\n"
                                              "  return mImpl." +
                                              kGetInterfaceHash +
                                              "();\n"
                                              "}\n"));
  }
  if (options.Version() > 0) {
    delegator_class->elements.emplace_back(
        std::make_shared<LiteralClassElement>("@Override\n"
                                              "public int " +
                                              kGetInterfaceVersion +
                                              "() throws android.os.RemoteException {\n"
                                              "  int implVer = mImpl." +
                                              kGetInterfaceVersion +
                                              "();\n"
                                              "  return VERSION < implVer ? VERSION : implVer;\n"
                                              "}\n"));
  }

  // user defined methods
  for (const auto& m : iface.GetMethods()) {
    if (m->IsUserDefined()) {
      delegator_class->elements.emplace_back(GenerateDelegatorMethod(*m));
    }
  }

  delegator_class->elements.emplace_back(
      std::make_shared<LiteralClassElement>(iface.GetCanonicalName() + " mImpl;\n"));

  return delegator_class;
}

static shared_ptr<ClassElement> GenerateMaxTransactionId(int max_transaction_id) {
  auto getMaxTransactionId = std::make_shared<Method>();
  getMaxTransactionId->comment = "/** @hide */";
  getMaxTransactionId->modifiers = PUBLIC;
  getMaxTransactionId->returnType = "int";
  getMaxTransactionId->name = "getMaxTransactionId";
  getMaxTransactionId->statements = std::make_shared<StatementBlock>();
  getMaxTransactionId->statements->Add(std::make_shared<ReturnStatement>(
      std::make_shared<LiteralExpression>(std::to_string(max_transaction_id))));
  return getMaxTransactionId;
}

std::unique_ptr<Class> GenerateInterfaceClass(const AidlInterface* iface,
                                              const AidlTypenames& typenames,
                                              const Options& options) {
  // the interface class
  auto interface = std::make_unique<Class>();
  interface->comment = GenerateComments(*iface);
  interface->modifiers = PUBLIC;
  interface->what = Class::INTERFACE;
  interface->type = iface->GetCanonicalName();
  interface->interfaces.push_back("android.os.IInterface");
  interface->annotations = JavaAnnotationsFor(*iface);

  if (options.Version()) {
    std::ostringstream code;
    code << "/**\n"
         << " * The version of this interface that the caller is built against.\n"
         << " * This might be different from what {@link #getInterfaceVersion()\n"
         << " * getInterfaceVersion} returns as that is the version of the interface\n"
         << " * that the remote object is implementing.\n"
         << " */\n"
         << "public static final int VERSION = ";
    if (options.IsLatestUnfrozenVersion()) {
      code << "true ? " << options.PreviousVersion() << " : " << options.Version() << ";\n";
    } else {
      code << options.Version() << ";\n";
    }

    interface->elements.emplace_back(std::make_shared<LiteralClassElement>(code.str()));
  }
  if (!options.Hash().empty() || options.IsLatestUnfrozenVersion()) {
    std::ostringstream code;
    if (options.IsLatestUnfrozenVersion()) {
      code << "public static final String HASH = \"" << options.PreviousHash() << "\";\n";
    } else {
      code << "public static final String HASH = \"" << options.Hash() << "\";\n";
    }

    interface->elements.emplace_back(std::make_shared<LiteralClassElement>(code.str()));
  }

  // the default impl class
  auto default_impl = GenerateDefaultImplClass(*iface, options);
  interface->elements.emplace_back(default_impl);

  // the delegator class
  if (iface->IsJavaDelegator()) {
    auto delegator = GenerateDelegatorClass(*iface, options);
    interface->elements.emplace_back(delegator);
  }

  // the stub inner class
  auto stub = std::make_shared<StubClass>(iface, options);
  interface->elements.push_back(stub);

  ComputeOutlineMethods(iface, stub, options.onTransact_outline_threshold_,
                        options.onTransact_non_outline_count_);

  // the proxy inner class
  auto proxy = std::make_shared<ProxyClass>(iface, options);
  stub->elements.push_back(proxy);

  // stub and proxy support for getInterfaceDescriptor()
  GenerateInterfaceDescriptors(options, iface, interface.get(), stub, proxy);

  // all the declared constants of the interface
  string constants;
  GenerateConstantDeclarations(*CodeWriter::ForString(&constants), *iface);
  interface->elements.push_back(std::make_shared<LiteralClassElement>(constants));

  // all the declared methods of the interface
  int max_transaction_id = 0;
  for (const auto& item : iface->GetMethods()) {
    GenerateMethods(*iface, *item, interface.get(), stub, proxy, item->GetId(), typenames, options);
    max_transaction_id = std::max(max_transaction_id, item->GetId());
  }

  // getMaxTransactionId
  if (options.GenTransactionNames() || options.GenTraces()) {
    stub->elements.push_back(GenerateMaxTransactionId(max_transaction_id));
  }

  // all the nested types
  string code;
  auto writer = CodeWriter::ForString(&code);
  for (const auto& nested : iface->GetNestedTypes()) {
    GenerateClass(*writer, *nested, typenames, options);
  }
  GenerateParcelHelpers(*writer, *iface, typenames, options);
  writer->Close();
  interface->elements.push_back(std::make_shared<LiteralClassElement>(code));

  if (iface->IsJavaDefault()) {
    // additional static methods for the default impl set/get to the
    // stub class. Can't add them to the interface as the generated java files
    // may be compiled with Java < 1.7 where static interface method isn't
    // supported.
    // TODO(b/111417145) make this conditional depending on the Java language
    // version requested
    const string i_name = iface->GetCanonicalName();
    stub->elements.emplace_back(std::make_shared<LiteralClassElement>(
        StringPrintf("public static boolean setDefaultImpl(%s impl) {\n"
                     "  // Only one user of this interface can use this function\n"
                     "  // at a time. This is a heuristic to detect if two different\n"
                     "  // users in the same process use this function.\n"
                     "  if (Stub.Proxy.sDefaultImpl != null) {\n"
                     "    throw new IllegalStateException(\"setDefaultImpl() called twice\");\n"
                     "  }\n"
                     "  if (impl != null) {\n"
                     "    Stub.Proxy.sDefaultImpl = impl;\n"
                     "    return true;\n"
                     "  }\n"
                     "  return false;\n"
                     "}\n",
                     i_name.c_str())));
    stub->elements.emplace_back(
        std::make_shared<LiteralClassElement>(StringPrintf("public static %s getDefaultImpl() {\n"
                                                           "  return Stub.Proxy.sDefaultImpl;\n"
                                                           "}\n",
                                                           i_name.c_str())));

    // the static field is defined in the proxy class, not in the interface class
    // because all fields in an interface class are by default final.
    proxy->elements.emplace_back(std::make_shared<LiteralClassElement>(
        StringPrintf("public static %s sDefaultImpl;\n", i_name.c_str())));
  }

  stub->Finish();

  return interface;
}

}  // namespace java
}  // namespace aidl
}  // namespace android
