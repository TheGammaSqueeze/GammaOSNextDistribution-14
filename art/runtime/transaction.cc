/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "transaction.h"

#include <android-base/logging.h>

#include "base/mutex-inl.h"
#include "base/stl_util.h"
#include "dex/descriptors_names.h"
#include "gc/accounting/card_table-inl.h"
#include "gc/heap.h"
#include "gc_root-inl.h"
#include "intern_table.h"
#include "mirror/class-inl.h"
#include "mirror/dex_cache-inl.h"
#include "mirror/object-inl.h"
#include "mirror/object_array-inl.h"
#include "oat/aot_class_linker.h"
#include "obj_ptr-inl.h"
#include "runtime.h"

#include <list>

namespace art HIDDEN {

// TODO: remove (only used for debugging purpose).
static constexpr bool kEnableTransactionStats = false;

Transaction::Transaction(bool strict,
                         mirror::Class* root,
                         ArenaStack* arena_stack,
                         ArenaPool* arena_pool)
    : arena_stack_(std::nullopt),
      allocator_(arena_stack != nullptr ? arena_stack : &arena_stack_.emplace(arena_pool)),
      object_logs_(std::less<mirror::Object*>(), allocator_.Adapter(kArenaAllocTransaction)),
      array_logs_(std::less<mirror::Array*>(), allocator_.Adapter(kArenaAllocTransaction)),
      intern_string_logs_(allocator_.Adapter(kArenaAllocTransaction)),
      resolve_string_logs_(allocator_.Adapter(kArenaAllocTransaction)),
      resolve_method_type_logs_(allocator_.Adapter(kArenaAllocTransaction)),
      aborted_(false),
      rolling_back_(false),
      heap_(Runtime::Current()->GetHeap()),
      strict_(strict),
      root_(root),
      assert_no_new_records_reason_(nullptr) {
  DCHECK(Runtime::Current()->IsAotCompiler());
  DCHECK_NE(arena_stack != nullptr, arena_pool != nullptr);
}

Transaction::~Transaction() {
  if (kEnableTransactionStats) {
    size_t objects_count = object_logs_.size();
    size_t field_values_count = 0;
    for (const auto& it : object_logs_) {
      field_values_count += it.second.Size();
    }
    size_t array_count = array_logs_.size();
    size_t array_values_count = 0;
    for (const auto& it : array_logs_) {
      array_values_count += it.second.Size();
    }
    size_t intern_string_count =
        std::distance(intern_string_logs_.begin(), intern_string_logs_.end());
    size_t resolve_string_count =
        std::distance(resolve_string_logs_.begin(), resolve_string_logs_.end());
    size_t resolve_method_type_count =
        std::distance(resolve_method_type_logs_.begin(), resolve_method_type_logs_.end());
    LOG(INFO) << "Transaction::~Transaction"
              << ": objects_count=" << objects_count
              << ", field_values_count=" << field_values_count
              << ", array_count=" << array_count
              << ", array_values_count=" << array_values_count
              << ", intern_string_count=" << intern_string_count
              << ", resolve_string_count=" << resolve_string_count
              << ", resolve_method_type_count=" << resolve_method_type_count;
  }
}

void Transaction::Abort(const std::string& abort_message) {
  // We may abort more than once if the exception thrown at the time of the
  // previous abort has been caught during execution of a class initializer.
  // We just keep the message of the first abort because it will cause the
  // transaction to be rolled back anyway.
  if (!aborted_) {
    aborted_ = true;
    abort_message_ = abort_message;
  }
}

void Transaction::ThrowAbortError(Thread* self, const std::string* abort_message) {
  const bool rethrow = (abort_message == nullptr);
  if (kIsDebugBuild && rethrow) {
    CHECK(IsAborted()) << "Rethrow " << DescriptorToDot(Transaction::kAbortExceptionDescriptor)
                       << " while transaction is not aborted";
  }
  if (rethrow) {
    // Rethrow an exception with the earlier abort message stored in the transaction.
    self->ThrowNewWrappedException(Transaction::kAbortExceptionDescriptor,
                                   GetAbortMessage().c_str());
  } else {
    // Throw an exception with the given abort message.
    self->ThrowNewWrappedException(Transaction::kAbortExceptionDescriptor,
                                   abort_message->c_str());
  }
}

const std::string& Transaction::GetAbortMessage() const {
  return abort_message_;
}

bool Transaction::WriteConstraint(ObjPtr<mirror::Object> obj) const {
  DCHECK(obj != nullptr);

  // Prevent changes in boot image spaces for app or boot image extension.
  // For boot image there are no boot image spaces and this condition evaluates to false.
  if (heap_->ObjectIsInBootImageSpace(obj)) {
    return true;
  }

  // For apps, also prevent writing to other classes.
  return IsStrict() &&
         obj->IsClass() &&  // no constraint updating instances or arrays
         obj != root_;  // modifying other classes' static field, fail
}

bool Transaction::WriteValueConstraint(ObjPtr<mirror::Object> value) const {
  if (value == nullptr) {
    return false;  // We can always store null values.
  }
  gc::Heap* heap = Runtime::Current()->GetHeap();
  if (IsStrict()) {
    // TODO: Should we restrict writes the same way as for boot image extension?
    return false;
  } else if (heap->GetBootImageSpaces().empty()) {
    return false;  // No constraints for boot image.
  } else {
    // Boot image extension.
    ObjPtr<mirror::Class> klass = value->IsClass() ? value->AsClass() : value->GetClass();
    return !AotClassLinker::CanReferenceInBootImageExtension(klass, heap);
  }
}

bool Transaction::ReadConstraint(ObjPtr<mirror::Object> obj) const {
  // Read constraints are checked only for static field reads as there are
  // no constraints on reading instance fields and array elements.
  DCHECK(obj->IsClass());
  if (IsStrict()) {
    return obj != root_;  // fail if not self-updating
  } else {
    // For boot image and boot image extension, allow reading any field.
    return false;
  }
}

inline Transaction::ObjectLog& Transaction::GetOrCreateObjectLog(mirror::Object* obj) {
  return object_logs_.GetOrCreate(obj, [&]() { return ObjectLog(&allocator_); });
}

void Transaction::RecordWriteFieldBoolean(mirror::Object* obj,
                                          MemberOffset field_offset,
                                          uint8_t value,
                                          bool is_volatile) {
  DCHECK(obj != nullptr);
  DCHECK(assert_no_new_records_reason_ == nullptr) << assert_no_new_records_reason_;
  ObjectLog& object_log = GetOrCreateObjectLog(obj);
  object_log.LogBooleanValue(field_offset, value, is_volatile);
}

void Transaction::RecordWriteFieldByte(mirror::Object* obj,
                                       MemberOffset field_offset,
                                       int8_t value,
                                       bool is_volatile) {
  DCHECK(obj != nullptr);
  DCHECK(assert_no_new_records_reason_ == nullptr) << assert_no_new_records_reason_;
  ObjectLog& object_log = GetOrCreateObjectLog(obj);
  object_log.LogByteValue(field_offset, value, is_volatile);
}

void Transaction::RecordWriteFieldChar(mirror::Object* obj,
                                       MemberOffset field_offset,
                                       uint16_t value,
                                       bool is_volatile) {
  DCHECK(obj != nullptr);
  DCHECK(assert_no_new_records_reason_ == nullptr) << assert_no_new_records_reason_;
  ObjectLog& object_log = GetOrCreateObjectLog(obj);
  object_log.LogCharValue(field_offset, value, is_volatile);
}


void Transaction::RecordWriteFieldShort(mirror::Object* obj,
                                        MemberOffset field_offset,
                                        int16_t value,
                                        bool is_volatile) {
  DCHECK(obj != nullptr);
  DCHECK(assert_no_new_records_reason_ == nullptr) << assert_no_new_records_reason_;
  ObjectLog& object_log = GetOrCreateObjectLog(obj);
  object_log.LogShortValue(field_offset, value, is_volatile);
}


void Transaction::RecordWriteField32(mirror::Object* obj,
                                     MemberOffset field_offset,
                                     uint32_t value,
                                     bool is_volatile) {
  DCHECK(obj != nullptr);
  DCHECK(assert_no_new_records_reason_ == nullptr) << assert_no_new_records_reason_;
  ObjectLog& object_log = GetOrCreateObjectLog(obj);
  object_log.Log32BitsValue(field_offset, value, is_volatile);
}

void Transaction::RecordWriteField64(mirror::Object* obj,
                                     MemberOffset field_offset,
                                     uint64_t value,
                                     bool is_volatile) {
  DCHECK(obj != nullptr);
  DCHECK(assert_no_new_records_reason_ == nullptr) << assert_no_new_records_reason_;
  ObjectLog& object_log = GetOrCreateObjectLog(obj);
  object_log.Log64BitsValue(field_offset, value, is_volatile);
}

void Transaction::RecordWriteFieldReference(mirror::Object* obj,
                                            MemberOffset field_offset,
                                            mirror::Object* value,
                                            bool is_volatile) {
  DCHECK(obj != nullptr);
  DCHECK(assert_no_new_records_reason_ == nullptr) << assert_no_new_records_reason_;
  ObjectLog& object_log = GetOrCreateObjectLog(obj);
  object_log.LogReferenceValue(field_offset, value, is_volatile);
}

void Transaction::RecordWriteArray(mirror::Array* array, size_t index, uint64_t value) {
  DCHECK(array != nullptr);
  DCHECK(array->IsArrayInstance());
  DCHECK(!array->IsObjectArray());
  DCHECK(assert_no_new_records_reason_ == nullptr) << assert_no_new_records_reason_;
  ArrayLog& array_log = array_logs_.GetOrCreate(array, [&]() { return ArrayLog(&allocator_); });
  array_log.LogValue(index, value);
}

void Transaction::RecordResolveString(ObjPtr<mirror::DexCache> dex_cache,
                                      dex::StringIndex string_idx) {
  DCHECK(dex_cache != nullptr);
  DCHECK_LT(string_idx.index_, dex_cache->GetDexFile()->NumStringIds());
  DCHECK(assert_no_new_records_reason_ == nullptr) << assert_no_new_records_reason_;
  resolve_string_logs_.emplace_front(dex_cache, string_idx);
}

void Transaction::RecordResolveMethodType(ObjPtr<mirror::DexCache> dex_cache,
                                          dex::ProtoIndex proto_idx) {
  DCHECK(dex_cache != nullptr);
  DCHECK_LT(proto_idx.index_, dex_cache->GetDexFile()->NumProtoIds());
  DCHECK(assert_no_new_records_reason_ == nullptr) << assert_no_new_records_reason_;
  resolve_method_type_logs_.emplace_front(dex_cache, proto_idx);
}

void Transaction::RecordStrongStringInsertion(ObjPtr<mirror::String> s) {
  InternStringLog log(s, InternStringLog::kStrongString, InternStringLog::kInsert);
  LogInternedString(std::move(log));
}

void Transaction::RecordWeakStringInsertion(ObjPtr<mirror::String> s) {
  InternStringLog log(s, InternStringLog::kWeakString, InternStringLog::kInsert);
  LogInternedString(std::move(log));
}

void Transaction::RecordStrongStringRemoval(ObjPtr<mirror::String> s) {
  InternStringLog log(s, InternStringLog::kStrongString, InternStringLog::kRemove);
  LogInternedString(std::move(log));
}

void Transaction::RecordWeakStringRemoval(ObjPtr<mirror::String> s) {
  InternStringLog log(s, InternStringLog::kWeakString, InternStringLog::kRemove);
  LogInternedString(std::move(log));
}

void Transaction::LogInternedString(InternStringLog&& log) {
  Locks::intern_table_lock_->AssertExclusiveHeld(Thread::Current());
  DCHECK(assert_no_new_records_reason_ == nullptr) << assert_no_new_records_reason_;
  intern_string_logs_.push_front(std::move(log));
}

void Transaction::Rollback() {
  Thread* self = Thread::Current();
  self->AssertNoPendingException();
  MutexLock mu(self, *Locks::intern_table_lock_);
  rolling_back_ = true;
  CHECK(!Runtime::Current()->IsActiveTransaction());
  UndoObjectModifications();
  UndoArrayModifications();
  UndoInternStringTableModifications();
  UndoResolveStringModifications();
  UndoResolveMethodTypeModifications();
  rolling_back_ = false;
}

void Transaction::UndoObjectModifications() {
  // TODO we may not need to restore objects allocated during this transaction. Or we could directly
  // remove them from the heap.
  for (const auto& it : object_logs_) {
    it.second.Undo(it.first);
  }
  object_logs_.clear();
}

void Transaction::UndoArrayModifications() {
  // TODO we may not need to restore array allocated during this transaction. Or we could directly
  // remove them from the heap.
  for (const auto& it : array_logs_) {
    it.second.Undo(it.first);
  }
  array_logs_.clear();
}

void Transaction::UndoInternStringTableModifications() {
  InternTable* const intern_table = Runtime::Current()->GetInternTable();
  // We want to undo each operation from the most recent to the oldest. List has been filled so the
  // most recent operation is at list begin so just have to iterate over it.
  for (const InternStringLog& string_log : intern_string_logs_) {
    string_log.Undo(intern_table);
  }
  intern_string_logs_.clear();
}

void Transaction::UndoResolveStringModifications() {
  for (ResolveStringLog& string_log : resolve_string_logs_) {
    string_log.Undo();
  }
  resolve_string_logs_.clear();
}

void Transaction::UndoResolveMethodTypeModifications() {
  for (ResolveMethodTypeLog& method_type_log : resolve_method_type_logs_) {
    method_type_log.Undo();
  }
  resolve_method_type_logs_.clear();
}

void Transaction::VisitRoots(RootVisitor* visitor) {
  // Transactions are used for single-threaded initialization.
  // This is the only function that should be called from a different thread,
  // namely the GC thread, and it is called with the mutator lock held exclusively,
  // so the data structures in the `Transaction` are protected from concurrent use.
  DCHECK(Locks::mutator_lock_->IsExclusiveHeld(Thread::Current()));

  visitor->VisitRoot(reinterpret_cast<mirror::Object**>(&root_), RootInfo(kRootUnknown));
  {
    // Create a separate `ArenaStack` for this thread.
    ArenaStack arena_stack(Runtime::Current()->GetArenaPool());
    VisitObjectLogs(visitor, &arena_stack);
    VisitArrayLogs(visitor, &arena_stack);
  }
  VisitInternStringLogs(visitor);
  VisitResolveStringLogs(visitor);
  VisitResolveMethodTypeLogs(visitor);
}

template <typename MovingRoots, typename Container>
void UpdateKeys(const MovingRoots& moving_roots, Container& container) {
  for (const auto& pair : moving_roots) {
    auto* old_root = pair.first;
    auto* new_root = pair.second;
    auto node = container.extract(old_root);
    CHECK(!node.empty());
    node.key() = new_root;
    bool inserted = container.insert(std::move(node)).inserted;
    CHECK(inserted);
  }
}

void Transaction::VisitObjectLogs(RootVisitor* visitor, ArenaStack* arena_stack) {
  // List of moving roots.
  ScopedArenaAllocator allocator(arena_stack);
  using ObjectPair = std::pair<mirror::Object*, mirror::Object*>;
  ScopedArenaForwardList<ObjectPair> moving_roots(allocator.Adapter(kArenaAllocTransaction));

  // Visit roots.
  for (auto& it : object_logs_) {
    it.second.VisitRoots(visitor);
    mirror::Object* old_root = it.first;
    mirror::Object* new_root = old_root;
    visitor->VisitRoot(&new_root, RootInfo(kRootUnknown));
    if (new_root != old_root) {
      moving_roots.push_front(std::make_pair(old_root, new_root));
    }
  }

  // Update object logs with moving roots.
  UpdateKeys(moving_roots, object_logs_);
}

void Transaction::VisitArrayLogs(RootVisitor* visitor, ArenaStack* arena_stack) {
  // List of moving roots.
  ScopedArenaAllocator allocator(arena_stack);
  using ArrayPair = std::pair<mirror::Array*, mirror::Array*>;
  ScopedArenaForwardList<ArrayPair> moving_roots(allocator.Adapter(kArenaAllocTransaction));

  for (auto& it : array_logs_) {
    mirror::Array* old_root = it.first;
    mirror::Array* new_root = old_root;
    visitor->VisitRoot(reinterpret_cast<mirror::Object**>(&new_root), RootInfo(kRootUnknown));
    if (new_root != old_root) {
      moving_roots.push_front(std::make_pair(old_root, new_root));
    }
  }

  // Update array logs with moving roots.
  UpdateKeys(moving_roots, array_logs_);
}

void Transaction::VisitInternStringLogs(RootVisitor* visitor) {
  for (InternStringLog& log : intern_string_logs_) {
    log.VisitRoots(visitor);
  }
}

void Transaction::VisitResolveStringLogs(RootVisitor* visitor) {
  for (ResolveStringLog& log : resolve_string_logs_) {
    log.VisitRoots(visitor);
  }
}

void Transaction::VisitResolveMethodTypeLogs(RootVisitor* visitor) {
  for (ResolveMethodTypeLog& log : resolve_method_type_logs_) {
    log.VisitRoots(visitor);
  }
}

void Transaction::ObjectLog::LogBooleanValue(MemberOffset offset, uint8_t value, bool is_volatile) {
  LogValue(ObjectLog::kBoolean, offset, value, is_volatile);
}

void Transaction::ObjectLog::LogByteValue(MemberOffset offset, int8_t value, bool is_volatile) {
  LogValue(ObjectLog::kByte, offset, value, is_volatile);
}

void Transaction::ObjectLog::LogCharValue(MemberOffset offset, uint16_t value, bool is_volatile) {
  LogValue(ObjectLog::kChar, offset, value, is_volatile);
}

void Transaction::ObjectLog::LogShortValue(MemberOffset offset, int16_t value, bool is_volatile) {
  LogValue(ObjectLog::kShort, offset, value, is_volatile);
}

void Transaction::ObjectLog::Log32BitsValue(MemberOffset offset, uint32_t value, bool is_volatile) {
  LogValue(ObjectLog::k32Bits, offset, value, is_volatile);
}

void Transaction::ObjectLog::Log64BitsValue(MemberOffset offset, uint64_t value, bool is_volatile) {
  LogValue(ObjectLog::k64Bits, offset, value, is_volatile);
}

void Transaction::ObjectLog::LogReferenceValue(MemberOffset offset,
                                               mirror::Object* obj,
                                               bool is_volatile) {
  LogValue(ObjectLog::kReference, offset, reinterpret_cast<uintptr_t>(obj), is_volatile);
}

void Transaction::ObjectLog::LogValue(ObjectLog::FieldValueKind kind,
                                      MemberOffset offset,
                                      uint64_t value,
                                      bool is_volatile) {
  auto it = field_values_.find(offset.Uint32Value());
  if (it == field_values_.end()) {
    ObjectLog::FieldValue field_value;
    field_value.value = value;
    field_value.is_volatile = is_volatile;
    field_value.kind = kind;
    field_values_.emplace(offset.Uint32Value(), std::move(field_value));
  }
}

void Transaction::ObjectLog::Undo(mirror::Object* obj) const {
  for (auto& it : field_values_) {
    // Garbage collector needs to access object's class and array's length. So we don't rollback
    // these values.
    MemberOffset field_offset(it.first);
    if (field_offset.Uint32Value() == mirror::Class::ClassOffset().Uint32Value()) {
      // Skip Object::class field.
      continue;
    }
    if (obj->IsArrayInstance() &&
        field_offset.Uint32Value() == mirror::Array::LengthOffset().Uint32Value()) {
      // Skip Array::length field.
      continue;
    }
    const FieldValue& field_value = it.second;
    UndoFieldWrite(obj, field_offset, field_value);
  }
}

void Transaction::ObjectLog::UndoFieldWrite(mirror::Object* obj,
                                            MemberOffset field_offset,
                                            const FieldValue& field_value) const {
  // TODO We may want to abort a transaction while still being in transaction mode. In this case,
  // we'd need to disable the check.
  constexpr bool kCheckTransaction = false;
  switch (field_value.kind) {
    case kBoolean:
      if (UNLIKELY(field_value.is_volatile)) {
        obj->SetFieldBooleanVolatile<false, kCheckTransaction>(
            field_offset,
            field_value.value);
      } else {
        obj->SetFieldBoolean<false, kCheckTransaction>(
            field_offset,
            field_value.value);
      }
      break;
    case kByte:
      if (UNLIKELY(field_value.is_volatile)) {
        obj->SetFieldByteVolatile<false, kCheckTransaction>(
            field_offset,
            static_cast<int8_t>(field_value.value));
      } else {
        obj->SetFieldByte<false, kCheckTransaction>(
            field_offset,
            static_cast<int8_t>(field_value.value));
      }
      break;
    case kChar:
      if (UNLIKELY(field_value.is_volatile)) {
        obj->SetFieldCharVolatile<false, kCheckTransaction>(
            field_offset,
            static_cast<uint16_t>(field_value.value));
      } else {
        obj->SetFieldChar<false, kCheckTransaction>(
            field_offset,
            static_cast<uint16_t>(field_value.value));
      }
      break;
    case kShort:
      if (UNLIKELY(field_value.is_volatile)) {
        obj->SetFieldShortVolatile<false, kCheckTransaction>(
            field_offset,
            static_cast<int16_t>(field_value.value));
      } else {
        obj->SetFieldShort<false, kCheckTransaction>(
            field_offset,
            static_cast<int16_t>(field_value.value));
      }
      break;
    case k32Bits:
      if (UNLIKELY(field_value.is_volatile)) {
        obj->SetField32Volatile<false, kCheckTransaction>(
            field_offset,
            static_cast<uint32_t>(field_value.value));
      } else {
        obj->SetField32<false, kCheckTransaction>(
            field_offset,
            static_cast<uint32_t>(field_value.value));
      }
      break;
    case k64Bits:
      if (UNLIKELY(field_value.is_volatile)) {
        obj->SetField64Volatile<false, kCheckTransaction>(field_offset, field_value.value);
      } else {
        obj->SetField64<false, kCheckTransaction>(field_offset, field_value.value);
      }
      break;
    case kReference:
      if (UNLIKELY(field_value.is_volatile)) {
        obj->SetFieldObjectVolatile<false, kCheckTransaction>(
            field_offset,
            reinterpret_cast<mirror::Object*>(field_value.value));
      } else {
        obj->SetFieldObject<false, kCheckTransaction>(
            field_offset,
            reinterpret_cast<mirror::Object*>(field_value.value));
      }
      break;
    default:
      LOG(FATAL) << "Unknown value kind " << static_cast<int>(field_value.kind);
      UNREACHABLE();
  }
}

void Transaction::ObjectLog::VisitRoots(RootVisitor* visitor) {
  for (auto& it : field_values_) {
    FieldValue& field_value = it.second;
    if (field_value.kind == ObjectLog::kReference) {
      visitor->VisitRootIfNonNull(reinterpret_cast<mirror::Object**>(&field_value.value),
                                  RootInfo(kRootUnknown));
    }
  }
}

void Transaction::InternStringLog::Undo(InternTable* intern_table) const {
  DCHECK(!Runtime::Current()->IsActiveTransaction());
  DCHECK(intern_table != nullptr);
  ObjPtr<mirror::String> s = str_.Read();
  uint32_t hash = static_cast<uint32_t>(s->GetStoredHashCode());
  switch (string_op_) {
    case InternStringLog::kInsert: {
      switch (string_kind_) {
        case InternStringLog::kStrongString:
          intern_table->RemoveStrong(s, hash);
          break;
        case InternStringLog::kWeakString:
          intern_table->RemoveWeak(s, hash);
          break;
        default:
          LOG(FATAL) << "Unknown interned string kind";
          UNREACHABLE();
      }
      break;
    }
    case InternStringLog::kRemove: {
      switch (string_kind_) {
        case InternStringLog::kStrongString:
          intern_table->InsertStrong(s, hash);
          break;
        case InternStringLog::kWeakString:
          intern_table->InsertWeak(s, hash);
          break;
        default:
          LOG(FATAL) << "Unknown interned string kind";
          UNREACHABLE();
      }
      break;
    }
    default:
      LOG(FATAL) << "Unknown interned string op";
      UNREACHABLE();
  }
}

void Transaction::InternStringLog::VisitRoots(RootVisitor* visitor) {
  str_.VisitRoot(visitor, RootInfo(kRootInternedString));
}

void Transaction::ResolveStringLog::Undo() const {
  dex_cache_.Read()->ClearString(string_idx_);
}

Transaction::ResolveStringLog::ResolveStringLog(ObjPtr<mirror::DexCache> dex_cache,
                                                dex::StringIndex string_idx)
    : dex_cache_(dex_cache),
      string_idx_(string_idx) {
  DCHECK(dex_cache != nullptr);
  DCHECK_LT(string_idx_.index_, dex_cache->GetDexFile()->NumStringIds());
}

void Transaction::ResolveStringLog::VisitRoots(RootVisitor* visitor) {
  dex_cache_.VisitRoot(visitor, RootInfo(kRootVMInternal));
}

void Transaction::ResolveMethodTypeLog::Undo() const {
  dex_cache_.Read()->ClearMethodType(proto_idx_);
}

Transaction::ResolveMethodTypeLog::ResolveMethodTypeLog(ObjPtr<mirror::DexCache> dex_cache,
                                                        dex::ProtoIndex proto_idx)
    : dex_cache_(dex_cache),
      proto_idx_(proto_idx) {
  DCHECK(dex_cache != nullptr);
  DCHECK_LT(proto_idx_.index_, dex_cache->GetDexFile()->NumProtoIds());
}

void Transaction::ResolveMethodTypeLog::VisitRoots(RootVisitor* visitor) {
  dex_cache_.VisitRoot(visitor, RootInfo(kRootVMInternal));
}

Transaction::InternStringLog::InternStringLog(ObjPtr<mirror::String> s,
                                              StringKind kind,
                                              StringOp op)
    : str_(s),
      string_kind_(kind),
      string_op_(op) {
  DCHECK(s != nullptr);
}

void Transaction::ArrayLog::LogValue(size_t index, uint64_t value) {
  // Add a mapping if there is none yet.
  array_values_.FindOrAdd(index, value);
}

void Transaction::ArrayLog::Undo(mirror::Array* array) const {
  DCHECK(array != nullptr);
  DCHECK(array->IsArrayInstance());
  Primitive::Type type = array->GetClass()->GetComponentType()->GetPrimitiveType();
  for (auto it : array_values_) {
    UndoArrayWrite(array, type, it.first, it.second);
  }
}

void Transaction::ArrayLog::UndoArrayWrite(mirror::Array* array,
                                           Primitive::Type array_type,
                                           size_t index,
                                           uint64_t value) const {
  // TODO We may want to abort a transaction while still being in transaction mode. In this case,
  // we'd need to disable the check.
  constexpr bool kCheckTransaction = false;
  switch (array_type) {
    case Primitive::kPrimBoolean:
      array->AsBooleanArray()->SetWithoutChecks<false, kCheckTransaction>(
          index, static_cast<uint8_t>(value));
      break;
    case Primitive::kPrimByte:
      array->AsByteArray()->SetWithoutChecks<false, kCheckTransaction>(
          index, static_cast<int8_t>(value));
      break;
    case Primitive::kPrimChar:
      array->AsCharArray()->SetWithoutChecks<false, kCheckTransaction>(
          index, static_cast<uint16_t>(value));
      break;
    case Primitive::kPrimShort:
      array->AsShortArray()->SetWithoutChecks<false, kCheckTransaction>(
          index, static_cast<int16_t>(value));
      break;
    case Primitive::kPrimInt:
      array->AsIntArray()->SetWithoutChecks<false, kCheckTransaction>(
          index, static_cast<int32_t>(value));
      break;
    case Primitive::kPrimFloat:
      array->AsFloatArray()->SetWithoutChecks<false, kCheckTransaction>(
          index, static_cast<float>(value));
      break;
    case Primitive::kPrimLong:
      array->AsLongArray()->SetWithoutChecks<false, kCheckTransaction>(
          index, static_cast<int64_t>(value));
      break;
    case Primitive::kPrimDouble:
      array->AsDoubleArray()->SetWithoutChecks<false, kCheckTransaction>(
          index, static_cast<double>(value));
      break;
    case Primitive::kPrimNot:
      LOG(FATAL) << "ObjectArray should be treated as Object";
      UNREACHABLE();
    default:
      LOG(FATAL) << "Unsupported type " << array_type;
      UNREACHABLE();
  }
}

Transaction* ScopedAssertNoNewTransactionRecords::InstallAssertion(const char* reason) {
  Transaction* transaction = nullptr;
  if (kIsDebugBuild && Runtime::Current()->IsActiveTransaction()) {
    transaction = Runtime::Current()->GetTransaction();
    if (transaction != nullptr) {
      CHECK(transaction->assert_no_new_records_reason_ == nullptr)
          << "old: " << transaction->assert_no_new_records_reason_ << " new: " << reason;
      transaction->assert_no_new_records_reason_ = reason;
    }
  }
  return transaction;
}

void ScopedAssertNoNewTransactionRecords::RemoveAssertion(Transaction* transaction) {
  if (kIsDebugBuild) {
    CHECK(Runtime::Current()->GetTransaction() == transaction);
    CHECK(transaction->assert_no_new_records_reason_ != nullptr);
    transaction->assert_no_new_records_reason_ = nullptr;
  }
}

}  // namespace art
