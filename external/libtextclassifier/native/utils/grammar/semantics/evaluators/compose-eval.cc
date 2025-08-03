/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "utils/grammar/semantics/evaluators/compose-eval.h"

#include "utils/base/status_macros.h"
#include "utils/strings/stringpiece.h"

namespace libtextclassifier3::grammar {
namespace {

// Tries setting a singular field.
template <typename T>
Status TrySetField(const reflection::Field* field, const SemanticValue* value,
                   MutableFlatbuffer* result) {
  if (!result->Set<T>(field, value->Value<T>())) {
    return Status(StatusCode::INVALID_ARGUMENT, "Could not set field.");
  }
  return Status::OK;
}

template <>
Status TrySetField<flatbuffers::Table>(const reflection::Field* field,
                                       const SemanticValue* value,
                                       MutableFlatbuffer* result) {
  auto* flatbuffer = result->Mutable(field);
  if (flatbuffer == nullptr || !flatbuffer->MergeFrom(value->Table())) {
    return Status(StatusCode::INVALID_ARGUMENT,
                  "Could not set sub-field in result.");
  }
  return Status::OK;
}

// Tries adding a value to a repeated field.
template <typename T>
Status TryAddField(const reflection::Field* field, const SemanticValue* value,
                   MutableFlatbuffer* result) {
  auto* flatbuffer = result->Repeated(field);
  if (flatbuffer == nullptr || !flatbuffer->Add(value->Value<T>())) {
    return Status(StatusCode::INVALID_ARGUMENT, "Could not add field.");
  }
  return Status::OK;
}

template <>
Status TryAddField<flatbuffers::Table>(const reflection::Field* field,
                                       const SemanticValue* value,
                                       MutableFlatbuffer* result) {
  auto* flatbuffer = result->Repeated(field);
  auto* added = flatbuffer == nullptr ? nullptr : flatbuffer->Add();
  if (added == nullptr || !added->MergeFrom(value->Table())) {
    return Status(StatusCode::INVALID_ARGUMENT,
                  "Could not add message to repeated field.");
  }
  return Status::OK;
}

// Tries adding or setting a value for a field.
template <typename T>
Status TrySetOrAddValue(const FlatbufferFieldPath* field_path,
                        const SemanticValue* value, MutableFlatbuffer* result) {
  MutableFlatbuffer* parent;
  const reflection::Field* field;
  if (!result->GetFieldWithParent(field_path, &parent, &field)) {
    return Status(StatusCode::INVALID_ARGUMENT, "Could not get field.");
  }
  if (field->type()->base_type() == reflection::Vector) {
    return TryAddField<T>(field, value, parent);
  } else {
    return TrySetField<T>(field, value, parent);
  }
}

}  // namespace

StatusOr<const SemanticValue*> ComposeEvaluator::Apply(
    const EvalContext& context, const SemanticExpression* expression,
    UnsafeArena* arena) const {
  const ComposeExpression* compose_expression =
      expression->expression_as_ComposeExpression();
  std::unique_ptr<MutableFlatbuffer> result =
      semantic_value_builder_.NewTable(compose_expression->type());

  if (result == nullptr) {
    return Status(StatusCode::INVALID_ARGUMENT, "Invalid result type.");
  }

  // Evaluate and set fields.
  if (compose_expression->fields() != nullptr) {
    for (const ComposeExpression_::Field* field :
         *compose_expression->fields()) {
      // Evaluate argument.
      TC3_ASSIGN_OR_RETURN(const SemanticValue* value,
                           composer_->Apply(context, field->value(), arena));
      if (value == nullptr) {
        continue;
      }

      switch (value->base_type()) {
        case reflection::BaseType::Bool: {
          TC3_RETURN_IF_ERROR(
              TrySetOrAddValue<bool>(field->path(), value, result.get()));
          break;
        }
        case reflection::BaseType::Byte: {
          TC3_RETURN_IF_ERROR(
              TrySetOrAddValue<int8>(field->path(), value, result.get()));
          break;
        }
        case reflection::BaseType::UByte: {
          TC3_RETURN_IF_ERROR(
              TrySetOrAddValue<uint8>(field->path(), value, result.get()));
          break;
        }
        case reflection::BaseType::Short: {
          TC3_RETURN_IF_ERROR(
              TrySetOrAddValue<int16>(field->path(), value, result.get()));
          break;
        }
        case reflection::BaseType::UShort: {
          TC3_RETURN_IF_ERROR(
              TrySetOrAddValue<uint16>(field->path(), value, result.get()));
          break;
        }
        case reflection::BaseType::Int: {
          TC3_RETURN_IF_ERROR(
              TrySetOrAddValue<int32>(field->path(), value, result.get()));
          break;
        }
        case reflection::BaseType::UInt: {
          TC3_RETURN_IF_ERROR(
              TrySetOrAddValue<uint32>(field->path(), value, result.get()));
          break;
        }
        case reflection::BaseType::Long: {
          TC3_RETURN_IF_ERROR(
              TrySetOrAddValue<int64>(field->path(), value, result.get()));
          break;
        }
        case reflection::BaseType::ULong: {
          TC3_RETURN_IF_ERROR(
              TrySetOrAddValue<uint64>(field->path(), value, result.get()));
          break;
        }
        case reflection::BaseType::Float: {
          TC3_RETURN_IF_ERROR(
              TrySetOrAddValue<float>(field->path(), value, result.get()));
          break;
        }
        case reflection::BaseType::Double: {
          TC3_RETURN_IF_ERROR(
              TrySetOrAddValue<double>(field->path(), value, result.get()));
          break;
        }
        case reflection::BaseType::String: {
          TC3_RETURN_IF_ERROR(TrySetOrAddValue<StringPiece>(
              field->path(), value, result.get()));
          break;
        }
        case reflection::BaseType::Obj: {
          TC3_RETURN_IF_ERROR(TrySetOrAddValue<flatbuffers::Table>(
              field->path(), value, result.get()));
          break;
        }
        default:
          return Status(StatusCode::INVALID_ARGUMENT, "Unhandled type.");
      }
    }
  }

  return SemanticValue::Create<const MutableFlatbuffer*>(result.get(), arena);
}

}  // namespace libtextclassifier3::grammar
