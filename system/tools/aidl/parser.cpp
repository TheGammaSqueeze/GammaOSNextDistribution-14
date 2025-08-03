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

#include "parser.h"

#include <queue>

#include "aidl_language_y.h"
#include "logging.h"

void yylex_init(void**);
void yylex_destroy(void*);
void yyset_in(FILE* f, void*);
int yyparse(Parser*);
YY_BUFFER_STATE yy_scan_buffer(char*, size_t, void*);
void yy_delete_buffer(YY_BUFFER_STATE, void*);

// For each union, generate nested "Tag" enum type so that "Tag" can be used as a valid type.
//    union Foo { int a; int b; } => union Foo { ... enum Tag { a, b }}
struct UnionTagGenerater : AidlVisitor {
  void Visit(const AidlUnionDecl& decl) override {
    std::vector<std::unique_ptr<AidlEnumerator>> enumerators;
    for (const auto& field : decl.GetFields()) {
      enumerators.push_back(std::make_unique<AidlEnumerator>(AIDL_LOCATION_HERE, field->GetName(),
                                                             nullptr, field->GetComments()));
    }
    auto tag_enum = std::make_unique<AidlEnumDeclaration>(AIDL_LOCATION_HERE, "Tag", &enumerators,
                                                          decl.GetPackage(), Comments{});
    // Tag for @FixedSize union is limited to "byte" type so that it can be passed via FMQ with
    // with lower overhead.
    std::shared_ptr<AidlConstantValue> backing_type{
        AidlConstantValue::String(AIDL_LOCATION_HERE, decl.IsFixedSize() ? "\"byte\"" : "\"int\"")};
    std::vector<std::unique_ptr<AidlAnnotation>> annotations;
    annotations.push_back(
        AidlAnnotation::Parse(AIDL_LOCATION_HERE, "Backing", {{"type", backing_type}}, Comments{}));
    tag_enum->Annotate(std::move(annotations));
    const_cast<AidlUnionDecl&>(decl).AddType(std::move(tag_enum));
  }
};

const AidlDocument* Parser::Parse(const std::string& filename,
                                  const android::aidl::IoDelegate& io_delegate,
                                  AidlTypenames& typenames, bool is_preprocessed) {
  auto clean_path = android::aidl::IoDelegate::CleanPath(filename);
  // reuse pre-parsed document from typenames
  for (auto& doc : typenames.AllDocuments()) {
    if (doc->GetLocation().GetFile() == clean_path) {
      return doc.get();
    }
  }
  // Make sure we can read the file first, before trashing previous state.
  unique_ptr<string> raw_buffer = io_delegate.GetFileContents(clean_path);
  if (raw_buffer == nullptr) {
    AIDL_ERROR(clean_path) << "Error while opening file for parsing";
    return nullptr;
  }

  // We're going to scan this buffer in place, and yacc demands we put two
  // nulls at the end.
  raw_buffer->append(2u, '\0');

  Parser parser(clean_path, *raw_buffer, is_preprocessed);

  if (yy::parser(&parser).parse() != 0 || parser.HasError()) {
    return nullptr;
  }

  // Preprocess parsed document before adding to typenames.
  UnionTagGenerater v;
  VisitTopDown(v, *parser.document_);

  // transfer ownership to AidlTypenames and return the raw pointer
  const AidlDocument* result = parser.document_.get();
  if (!typenames.AddDocument(std::move(parser.document_))) {
    return nullptr;
  }
  return result;
}

void Parser::SetTypeParameters(AidlTypeSpecifier* type,
                               std::vector<std::unique_ptr<AidlTypeSpecifier>>* type_args) {
  if (type->IsArray()) {
    AIDL_ERROR(type) << "Must specify type parameters (<>) before array ([]).";
    AddError();
  }
  if (!type->SetTypeParameters(type_args)) {
    AIDL_ERROR(type) << "Can only specify one set of type parameters.";
    AddError();
    delete type_args;
  }
}

void Parser::CheckValidTypeName(const AidlToken& token, const AidlLocation& loc) {
  if (!is_preprocessed_ && token.GetText().find('.') != std::string::npos) {
    AIDL_ERROR(loc) << "Type name can't be qualified. Use `package`.";
    AddError();
  }
}

void Parser::SetPackage(const std::string& package) {
  if (is_preprocessed_) {
    AIDL_ERROR(package) << "Preprocessed file can't declare package.";
    AddError();
  }
  package_ = package;
}

bool CheckNoRecursiveDefinition(const AidlNode& node) {
  struct Visitor : AidlVisitor {
    enum {
      NOT_STARTED = 0,
      STARTED = 1,
      FINISHED = 2,
    };
    std::map<const AidlParcelable*, int> visited;
    std::vector<std::string> path;
    bool found_cycle = false;

    void Visit(const AidlStructuredParcelable& t) override { FindCycle(&t); }
    void Visit(const AidlUnionDecl& t) override { FindCycle(&t); }
    void Visit(const AidlParcelable& t) override { FindCycle(&t); }

    bool FindCycle(const AidlParcelable* p) {
      // no need to search further
      if (found_cycle) {
        return true;
      }
      // we just found a cycle
      if (visited[p] == STARTED) {
        path.push_back(p->GetName());
        AIDL_ERROR(p) << p->GetName()
                      << " is a recursive parcelable: " << android::base::Join(path, "->");
        return (found_cycle = true);
      }
      // we arrived here with a different route.
      if (visited[p] == FINISHED) {
        return false;
      }
      // start DFS
      visited[p] = STARTED;
      path.push_back(p->GetName());
      for (const auto& f : p->GetFields()) {
        const auto& ref = f->GetType();
        if (!ref.IsArray() && !ref.IsHeapNullable()) {
          const auto& type = ref.GetDefinedType();
          if (type && type->AsParcelable()) {
            if (FindCycle(type->AsParcelable())) {
              return true;
            }
          }
        }
      }
      path.pop_back();
      visited[p] = FINISHED;
      return false;
    }
  } v;
  VisitTopDown(v, node);
  return !v.found_cycle;
}

// Each Visit*() method can use Scope() to get its scope(AidlDefinedType)
class ScopedVisitor : public AidlVisitor {
 protected:
  const AidlDefinedType* Scope() const {
    AIDL_FATAL_IF(scope_.empty(), AIDL_LOCATION_HERE) << "Scope is empty";
    return scope_.back();
  }
  void PushScope(const AidlDefinedType* scope) { scope_.push_back(scope); }
  void PopScope() { scope_.pop_back(); }
  // Keep user defined type as a defining scope
  void VisitScopedTopDown(const AidlNode& node) {
    std::function<void(const AidlNode&)> top_down = [&](const AidlNode& a) {
      a.DispatchVisit(*this);
      auto defined_type = AidlCast<AidlDefinedType>(a);
      if (defined_type) PushScope(defined_type);
      a.TraverseChildren(top_down);
      if (defined_type) PopScope();
    };
    top_down(node);
  }

 private:
  std::vector<const AidlDefinedType*> scope_ = {};
};

class TypeReferenceResolver : public ScopedVisitor {
 public:
  TypeReferenceResolver(TypeResolver& resolver) : resolver_(resolver) {}

  void Visit(const AidlTypeSpecifier& t) override {
    // We're visiting the same node again. This can happen when two constant references
    // point to an ancestor of this node.
    if (t.IsResolved()) {
      return;
    }
    AidlTypeSpecifier& type = const_cast<AidlTypeSpecifier&>(t);
    if (!resolver_(Scope(), &type)) {
      AIDL_ERROR(type) << "Failed to resolve '" << type.GetUnresolvedName() << "'";
      success_ = false;
      return;
    }

    // In case a new document is imported for the type reference, enqueue it for type resolution.
    auto resolved = t.GetDefinedType();
    if (resolved) {
      queue_.push(&resolved->GetDocument());
    }
  }

  bool Resolve(const AidlDocument& document) {
    queue_.push(&document);
    while (!queue_.empty()) {
      auto doc = queue_.front();
      queue_.pop();
      // Skip the doc if it's visited already.
      if (!visited_.insert(doc).second) {
        continue;
      }
      VisitScopedTopDown(*doc);
    }
    return success_;
  }

 private:
  TypeResolver& resolver_;
  bool success_ = true;
  std::queue<const AidlDocument*> queue_ = {};
  std::set<const AidlDocument*> visited_ = {};
};

class ConstantReferenceResolver : public ScopedVisitor {
 public:
  ConstantReferenceResolver() = default;

  void Visit(const AidlConstantReference& v) override {
    if (IsCircularReference(&v)) {
      success_ = false;
      return;
    }

    const AidlConstantValue* resolved = v.Resolve(Scope());
    if (!resolved) {
      AIDL_ERROR(v) << "Unknown reference '" << v.Literal() << "'";
      success_ = false;
      return;
    }

    // On error, skip recursive visiting to avoid redundant messages
    if (!success_) {
      return;
    }
    // resolve recursive references
    PushConstRef(&v);
    VisitScopedTopDown(*resolved);
    PopConstRef();
  }

  bool Resolve(const AidlDocument& document) {
    VisitScopedTopDown(document);
    return success_;
  }

 private:
  void PushConstRef(const AidlConstantReference* ref) {
    stack_.push_back(ref);
    if (ref->GetRefType()) {
      PushScope(ref->GetRefType()->GetDefinedType());
    }
  }

  void PopConstRef() {
    if (stack_.back()->GetRefType()) {
      PopScope();
    }
    stack_.pop_back();
  }

  bool IsCircularReference(const AidlConstantReference* ref) {
    auto it =
        std::find_if(stack_.begin(), stack_.end(), [&](const auto& elem) { return elem == ref; });
    if (it == stack_.end()) {
      return false;
    }
    std::vector<std::string> path;
    while (it != stack_.end()) {
      path.push_back((*it)->Literal());
      ++it;
    }
    path.push_back(ref->Literal());
    AIDL_ERROR(ref) << "Found a circular reference: " << android::base::Join(path, " -> ");
    return true;
  }

  bool success_ = true;
  std::vector<const AidlConstantReference*> stack_ = {};
};

// Resolve references(types/constants) in the "main" document.
bool ResolveReferences(const AidlDocument& document, TypeResolver& type_resolver) {
  // Types are resolved first before resolving constant references so that every referenced document
  // gets imported.
  if (!TypeReferenceResolver(type_resolver).Resolve(document)) {
    return false;
  }
  if (!ConstantReferenceResolver().Resolve(document)) {
    return false;
  }
  if (!CheckNoRecursiveDefinition(document)) {
    return false;
  }
  return true;
}

Parser::Parser(const std::string& filename, std::string& raw_buffer, bool is_preprocessed)
    : filename_(filename), is_preprocessed_(is_preprocessed) {
  yylex_init(&scanner_);
  buffer_ = yy_scan_buffer(&raw_buffer[0], raw_buffer.length(), scanner_);
}

Parser::~Parser() {
  yy_delete_buffer(buffer_, scanner_);
  yylex_destroy(scanner_);
}

void Parser::MakeDocument(const AidlLocation& location, const Comments& comments,
                          std::vector<std::string> imports,
                          std::vector<std::unique_ptr<AidlDefinedType>> defined_types) {
  AIDL_FATAL_IF(document_.get(), location);
  document_ = std::make_unique<AidlDocument>(location, comments, std::move(imports),
                                             std::move(defined_types), is_preprocessed_);
}
