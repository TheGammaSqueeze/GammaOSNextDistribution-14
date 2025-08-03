/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include "common_compiler_test.h"

#include <android-base/unique_fd.h>
#include <type_traits>

#include "arch/instruction_set_features.h"
#include "art_field-inl.h"
#include "art_method-inl.h"
#include "base/callee_save_type.h"
#include "base/casts.h"
#include "base/enums.h"
#include "base/memfd.h"
#include "base/utils.h"
#include "class_linker.h"
#include "dex/descriptors_names.h"
#include "driver/compiled_code_storage.h"
#include "driver/compiler_options.h"
#include "jni/java_vm_ext.h"
#include "interpreter/interpreter.h"
#include "mirror/class-inl.h"
#include "mirror/class_loader.h"
#include "mirror/dex_cache.h"
#include "mirror/object-inl.h"
#include "oat/oat_quick_method_header.h"
#include "scoped_thread_state_change-inl.h"
#include "thread-current-inl.h"
#include "utils/atomic_dex_ref_map-inl.h"

namespace art HIDDEN {

class CommonCompilerTestImpl::CodeAndMetadata {
 public:
  CodeAndMetadata(CodeAndMetadata&& other) = default;

  CodeAndMetadata(ArrayRef<const uint8_t> code,
                  ArrayRef<const uint8_t> vmap_table,
                  InstructionSet instruction_set) {
    const size_t page_size = MemMap::GetPageSize();
    const uint32_t code_size = code.size();
    CHECK_NE(code_size, 0u);
    const uint32_t vmap_table_offset = vmap_table.empty() ? 0u
        : sizeof(OatQuickMethodHeader) + vmap_table.size();
    OatQuickMethodHeader method_header(vmap_table_offset);
    const size_t code_alignment = GetInstructionSetCodeAlignment(instruction_set);
    DCHECK_ALIGNED_PARAM(page_size, code_alignment);
    const uint32_t code_offset = RoundUp(vmap_table.size() + sizeof(method_header), code_alignment);
    const uint32_t capacity = RoundUp(code_offset + code_size, page_size);

    // Create a memfd handle with sufficient capacity.
    android::base::unique_fd mem_fd(art::memfd_create_compat("test code", /*flags=*/ 0));
    CHECK_GE(mem_fd.get(), 0);
    int err = ftruncate(mem_fd, capacity);
    CHECK_EQ(err, 0);

    // Map the memfd contents for read/write.
    std::string error_msg;
    rw_map_ = MemMap::MapFile(capacity,
                              PROT_READ | PROT_WRITE,
                              MAP_SHARED,
                              mem_fd,
                              /*start=*/ 0,
                              /*low_4gb=*/ false,
                              /*filename=*/ "test code",
                              &error_msg);
    CHECK(rw_map_.IsValid()) << error_msg;

    // Store data.
    uint8_t* code_addr = rw_map_.Begin() + code_offset;
    CHECK_ALIGNED_PARAM(code_addr, code_alignment);
    CHECK_LE(vmap_table_offset, code_offset);
    memcpy(code_addr - vmap_table_offset, vmap_table.data(), vmap_table.size());
    static_assert(std::is_trivially_copyable<OatQuickMethodHeader>::value, "Cannot use memcpy");
    CHECK_LE(sizeof(method_header), code_offset);
    memcpy(code_addr - sizeof(method_header), &method_header, sizeof(method_header));
    CHECK_LE(code_size, static_cast<size_t>(rw_map_.End() - code_addr));
    memcpy(code_addr, code.data(), code_size);

    // Sync data.
    bool success = rw_map_.Sync();
    CHECK(success);
    success = FlushCpuCaches(rw_map_.Begin(), rw_map_.End());
    CHECK(success);

    // Map the data as read/executable.
    rx_map_ = MemMap::MapFile(capacity,
                              PROT_READ | PROT_EXEC,
                              MAP_SHARED,
                              mem_fd,
                              /*start=*/ 0,
                              /*low_4gb=*/ false,
                              /*filename=*/ "test code",
                              &error_msg);
    CHECK(rx_map_.IsValid()) << error_msg;

    DCHECK_LT(code_offset, rx_map_.Size());
    size_t adjustment = GetInstructionSetEntryPointAdjustment(instruction_set);
    entry_point_ = rx_map_.Begin() + code_offset + adjustment;
  }

  const void* GetEntryPoint() const {
    DCHECK(rx_map_.IsValid());
    return entry_point_;
  }

 private:
  MemMap rw_map_;
  MemMap rx_map_;
  const void* entry_point_;

  DISALLOW_COPY_AND_ASSIGN(CodeAndMetadata);
};

class CommonCompilerTestImpl::OneCompiledMethodStorage final : public CompiledCodeStorage {
 public:
  OneCompiledMethodStorage() {}
  ~OneCompiledMethodStorage() {}

  CompiledMethod* CreateCompiledMethod(InstructionSet instruction_set,
                                       ArrayRef<const uint8_t> code,
                                       ArrayRef<const uint8_t> stack_map,
                                       [[maybe_unused]] ArrayRef<const uint8_t> cfi,
                                       ArrayRef<const linker::LinkerPatch> patches,
                                       [[maybe_unused]] bool is_intrinsic) override {
    // Supports only one method at a time.
    CHECK_EQ(instruction_set_, InstructionSet::kNone);
    CHECK_NE(instruction_set, InstructionSet::kNone);
    instruction_set_ = instruction_set;
    CHECK(code_.empty());
    CHECK(!code.empty());
    code_.assign(code.begin(), code.end());
    CHECK(stack_map_.empty());
    CHECK(!stack_map.empty());
    stack_map_.assign(stack_map.begin(), stack_map.end());
    CHECK(patches.empty()) << "Linker patches are unsupported for compiler gtests.";
    return reinterpret_cast<CompiledMethod*>(this);
  }

  ArrayRef<const uint8_t> GetThunkCode([[maybe_unused]] const linker::LinkerPatch& patch,
                                       [[maybe_unused]] /*out*/ std::string* debug_name) override {
    LOG(FATAL) << "Unsupported.";
    UNREACHABLE();
  }

  void SetThunkCode([[maybe_unused]] const linker::LinkerPatch& patch,
                    [[maybe_unused]] ArrayRef<const uint8_t> code,
                    [[maybe_unused]] const std::string& debug_name) override {
    LOG(FATAL) << "Unsupported.";
    UNREACHABLE();
  }

  InstructionSet GetInstructionSet() const {
    CHECK_NE(instruction_set_, InstructionSet::kNone);
    return instruction_set_;
  }

  ArrayRef<const uint8_t> GetCode() const {
    CHECK(!code_.empty());
    return ArrayRef<const uint8_t>(code_);
  }

  ArrayRef<const uint8_t> GetStackMap() const {
    CHECK(!stack_map_.empty());
    return ArrayRef<const uint8_t>(stack_map_);
  }

 private:
  InstructionSet instruction_set_ = InstructionSet::kNone;
  std::vector<uint8_t> code_;
  std::vector<uint8_t> stack_map_;
};

std::unique_ptr<CompilerOptions> CommonCompilerTestImpl::CreateCompilerOptions(
    InstructionSet instruction_set, const std::string& variant) {
  std::unique_ptr<CompilerOptions> compiler_options = std::make_unique<CompilerOptions>();
  compiler_options->emit_read_barrier_ = gUseReadBarrier;
  compiler_options->instruction_set_ = instruction_set;
  std::string error_msg;
  compiler_options->instruction_set_features_ =
      InstructionSetFeatures::FromVariant(instruction_set, variant, &error_msg);
  CHECK(compiler_options->instruction_set_features_ != nullptr) << error_msg;
  return compiler_options;
}

CommonCompilerTestImpl::CommonCompilerTestImpl() {}
CommonCompilerTestImpl::~CommonCompilerTestImpl() {}

const void* CommonCompilerTestImpl::MakeExecutable(ArrayRef<const uint8_t> code,
                                                   ArrayRef<const uint8_t> vmap_table,
                                                   InstructionSet instruction_set) {
  CHECK_NE(code.size(), 0u);
  code_and_metadata_.emplace_back(code, vmap_table, instruction_set);
  return code_and_metadata_.back().GetEntryPoint();
}

void CommonCompilerTestImpl::SetUp() {
  {
    ScopedObjectAccess soa(Thread::Current());

    Runtime* runtime = GetRuntime();
    runtime->SetInstructionSet(instruction_set_);
    for (uint32_t i = 0; i < static_cast<uint32_t>(CalleeSaveType::kLastCalleeSaveType); ++i) {
      CalleeSaveType type = CalleeSaveType(i);
      if (!runtime->HasCalleeSaveMethod(type)) {
        runtime->SetCalleeSaveMethod(runtime->CreateCalleeSaveMethod(), type);
      }
    }
  }
}

void CommonCompilerTestImpl::ApplyInstructionSet() {
  // Copy local instruction_set_ and instruction_set_features_ to *compiler_options_;
  CHECK(instruction_set_features_ != nullptr);
  if (instruction_set_ == InstructionSet::kThumb2) {
    CHECK_EQ(InstructionSet::kArm, instruction_set_features_->GetInstructionSet());
  } else {
    CHECK_EQ(instruction_set_, instruction_set_features_->GetInstructionSet());
  }
  compiler_options_->instruction_set_ = instruction_set_;
  compiler_options_->instruction_set_features_ =
      InstructionSetFeatures::FromBitmap(instruction_set_, instruction_set_features_->AsBitmap());
  CHECK(compiler_options_->instruction_set_features_->Equals(instruction_set_features_.get()));
}

void CommonCompilerTestImpl::OverrideInstructionSetFeatures(InstructionSet instruction_set,
                                                            const std::string& variant) {
  instruction_set_ = instruction_set;
  std::string error_msg;
  instruction_set_features_ =
      InstructionSetFeatures::FromVariant(instruction_set, variant, &error_msg);
  CHECK(instruction_set_features_ != nullptr) << error_msg;

  if (compiler_options_ != nullptr) {
    ApplyInstructionSet();
  }
}

void CommonCompilerTestImpl::SetUpRuntimeOptionsImpl() {
  compiler_options_ = CreateCompilerOptions(instruction_set_, "default");
  ApplyInstructionSet();
}

Compiler::Kind CommonCompilerTestImpl::GetCompilerKind() const {
  return compiler_kind_;
}

void CommonCompilerTestImpl::SetCompilerKind(Compiler::Kind compiler_kind) {
  compiler_kind_ = compiler_kind;
}

void CommonCompilerTestImpl::TearDown() {
  code_and_metadata_.clear();
  compiler_options_.reset();
}

void CommonCompilerTestImpl::CompileMethod(ArtMethod* method) {
  CHECK(method != nullptr);
  TimingLogger timings("CommonCompilerTestImpl::CompileMethod", false, false);
  TimingLogger::ScopedTiming t(__FUNCTION__, &timings);
  OneCompiledMethodStorage storage;
  CompiledMethod* compiled_method = nullptr;
  {
    DCHECK(!Runtime::Current()->IsStarted());
    Thread* self = Thread::Current();
    StackHandleScope<2> hs(self);
    std::unique_ptr<Compiler> compiler(
        Compiler::Create(*compiler_options_, &storage, compiler_kind_));
    const DexFile& dex_file = *method->GetDexFile();
    Handle<mirror::DexCache> dex_cache =
        hs.NewHandle(GetClassLinker()->FindDexCache(self, dex_file));
    Handle<mirror::ClassLoader> class_loader = hs.NewHandle(method->GetClassLoader());
    if (method->IsNative()) {
      compiled_method = compiler->JniCompile(method->GetAccessFlags(),
                                             method->GetDexMethodIndex(),
                                             dex_file,
                                             dex_cache);
    } else {
      compiled_method = compiler->Compile(method->GetCodeItem(),
                                          method->GetAccessFlags(),
                                          method->GetInvokeType(),
                                          method->GetClassDefIndex(),
                                          method->GetDexMethodIndex(),
                                          class_loader,
                                          dex_file,
                                          dex_cache);
    }
    CHECK(compiled_method != nullptr) << "Failed to compile " << method->PrettyMethod();
    CHECK_EQ(reinterpret_cast<OneCompiledMethodStorage*>(compiled_method), &storage);
  }
  {
    TimingLogger::ScopedTiming t2("MakeExecutable", &timings);
    const void* method_code = MakeExecutable(storage.GetCode(),
                                             storage.GetStackMap(),
                                             storage.GetInstructionSet());
    LOG(INFO) << "MakeExecutable " << method->PrettyMethod() << " code=" << method_code;
    GetRuntime()->GetInstrumentation()->InitializeMethodsCode(method, /*aot_code=*/ method_code);
  }
}

void CommonCompilerTestImpl::ClearBootImageOption() {
  compiler_options_->image_type_ = CompilerOptions::ImageType::kNone;
}

}  // namespace art
