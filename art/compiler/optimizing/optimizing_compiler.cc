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

#include "optimizing_compiler.h"

#include <fstream>
#include <memory>
#include <sstream>

#include <stdint.h>

#include "art_method-inl.h"
#include "base/arena_allocator.h"
#include "base/arena_containers.h"
#include "base/dumpable.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/mutex.h"
#include "base/scoped_arena_allocator.h"
#include "base/timing_logger.h"
#include "builder.h"
#include "code_generator.h"
#include "compiler.h"
#include "debug/elf_debug_writer.h"
#include "debug/method_debug_info.h"
#include "dex/dex_file_types.h"
#include "driver/compiled_code_storage.h"
#include "driver/compiler_options.h"
#include "driver/dex_compilation_unit.h"
#include "graph_checker.h"
#include "graph_visualizer.h"
#include "inliner.h"
#include "jit/debugger_interface.h"
#include "jit/jit.h"
#include "jit/jit_code_cache.h"
#include "jit/jit_logger.h"
#include "jni/quick/jni_compiler.h"
#include "linker/linker_patch.h"
#include "nodes.h"
#include "oat/oat_quick_method_header.h"
#include "optimizing/write_barrier_elimination.h"
#include "prepare_for_register_allocation.h"
#include "profiling_info_builder.h"
#include "reference_type_propagation.h"
#include "register_allocator_linear_scan.h"
#include "select_generator.h"
#include "ssa_builder.h"
#include "ssa_liveness_analysis.h"
#include "ssa_phi_elimination.h"
#include "stack_map_stream.h"
#include "utils/assembler.h"

namespace art HIDDEN {

static constexpr size_t kArenaAllocatorMemoryReportThreshold = 8 * MB;

static constexpr const char* kPassNameSeparator = "$";

/**
 * Filter to apply to the visualizer. Methods whose name contain that filter will
 * be dumped.
 */
static constexpr const char kStringFilter[] = "";

class PassScope;

class PassObserver : public ValueObject {
 public:
  PassObserver(HGraph* graph,
               CodeGenerator* codegen,
               std::ostream* visualizer_output,
               const CompilerOptions& compiler_options)
      : graph_(graph),
        last_seen_graph_size_(0),
        cached_method_name_(),
        timing_logger_enabled_(compiler_options.GetDumpPassTimings()),
        timing_logger_(timing_logger_enabled_ ? GetMethodName() : "", true, true),
        disasm_info_(graph->GetAllocator()),
        visualizer_oss_(),
        visualizer_output_(visualizer_output),
        visualizer_enabled_(!compiler_options.GetDumpCfgFileName().empty()),
        visualizer_(&visualizer_oss_, graph, codegen),
        codegen_(codegen),
        graph_in_bad_state_(false) {
    if (timing_logger_enabled_ || visualizer_enabled_) {
      if (!IsVerboseMethod(compiler_options, GetMethodName())) {
        timing_logger_enabled_ = visualizer_enabled_ = false;
      }
      if (visualizer_enabled_) {
        visualizer_.PrintHeader(GetMethodName());
        codegen->SetDisassemblyInformation(&disasm_info_);
      }
    }
  }

  ~PassObserver() {
    if (timing_logger_enabled_) {
      LOG(INFO) << "TIMINGS " << GetMethodName();
      LOG(INFO) << Dumpable<TimingLogger>(timing_logger_);
    }
    if (visualizer_enabled_) {
      FlushVisualizer();
    }
    DCHECK(visualizer_oss_.str().empty());
  }

  void DumpDisassembly() {
    if (visualizer_enabled_) {
      visualizer_.DumpGraphWithDisassembly();
      FlushVisualizer();
    }
  }

  void SetGraphInBadState() { graph_in_bad_state_ = true; }

  const char* GetMethodName() {
    // PrettyMethod() is expensive, so we delay calling it until we actually have to.
    if (cached_method_name_.empty()) {
      cached_method_name_ = graph_->GetDexFile().PrettyMethod(graph_->GetMethodIdx());
    }
    return cached_method_name_.c_str();
  }

 private:
  void StartPass(const char* pass_name) {
    VLOG(compiler) << "Starting pass: " << pass_name;
    // Dump graph first, then start timer.
    if (visualizer_enabled_) {
      visualizer_.DumpGraph(pass_name, /* is_after_pass= */ false, graph_in_bad_state_);
      FlushVisualizer();
    }
    if (timing_logger_enabled_) {
      timing_logger_.StartTiming(pass_name);
    }
  }

  void FlushVisualizer() {
    *visualizer_output_ << visualizer_oss_.str();
    visualizer_output_->flush();
    visualizer_oss_.str("");
    visualizer_oss_.clear();
  }

  void EndPass(const char* pass_name, bool pass_change) {
    // Pause timer first, then dump graph.
    if (timing_logger_enabled_) {
      timing_logger_.EndTiming();
    }
    if (visualizer_enabled_) {
      visualizer_.DumpGraph(pass_name, /* is_after_pass= */ true, graph_in_bad_state_);
      FlushVisualizer();
    }

    // Validate the HGraph if running in debug mode.
    if (kIsDebugBuild) {
      if (!graph_in_bad_state_) {
        GraphChecker checker(graph_, codegen_);
        last_seen_graph_size_ = checker.Run(pass_change, last_seen_graph_size_);
        if (!checker.IsValid()) {
          std::ostringstream stream;
          graph_->Dump(stream, codegen_);
          LOG(FATAL_WITHOUT_ABORT) << "Error after " << pass_name << "(" << graph_->PrettyMethod()
                                   << "): " << stream.str();
          LOG(FATAL) << "(" << pass_name <<  "): " << Dumpable<GraphChecker>(checker);
        }
      }
    }
  }

  static bool IsVerboseMethod(const CompilerOptions& compiler_options, const char* method_name) {
    // Test an exact match to --verbose-methods. If verbose-methods is set, this overrides an
    // empty kStringFilter matching all methods.
    if (compiler_options.HasVerboseMethods()) {
      return compiler_options.IsVerboseMethod(method_name);
    }

    // Test the kStringFilter sub-string. constexpr helper variable to silence unreachable-code
    // warning when the string is empty.
    constexpr bool kStringFilterEmpty = arraysize(kStringFilter) <= 1;
    if (kStringFilterEmpty || strstr(method_name, kStringFilter) != nullptr) {
      return true;
    }

    return false;
  }

  HGraph* const graph_;
  size_t last_seen_graph_size_;

  std::string cached_method_name_;

  bool timing_logger_enabled_;
  TimingLogger timing_logger_;

  DisassemblyInformation disasm_info_;

  std::ostringstream visualizer_oss_;
  std::ostream* visualizer_output_;
  bool visualizer_enabled_;
  HGraphVisualizer visualizer_;
  CodeGenerator* codegen_;

  // Flag to be set by the compiler if the pass failed and the graph is not
  // expected to validate.
  bool graph_in_bad_state_;

  friend PassScope;

  DISALLOW_COPY_AND_ASSIGN(PassObserver);
};

class PassScope : public ValueObject {
 public:
  PassScope(const char *pass_name, PassObserver* pass_observer)
      : pass_name_(pass_name),
        pass_change_(true),  // assume change
        pass_observer_(pass_observer) {
    pass_observer_->StartPass(pass_name_);
  }

  void SetPassNotChanged() {
    pass_change_ = false;
  }

  ~PassScope() {
    pass_observer_->EndPass(pass_name_, pass_change_);
  }

 private:
  const char* const pass_name_;
  bool pass_change_;
  PassObserver* const pass_observer_;
};

class OptimizingCompiler final : public Compiler {
 public:
  explicit OptimizingCompiler(const CompilerOptions& compiler_options,
                              CompiledCodeStorage* storage);
  ~OptimizingCompiler() override;

  bool CanCompileMethod(uint32_t method_idx, const DexFile& dex_file) const override;

  CompiledMethod* Compile(const dex::CodeItem* code_item,
                          uint32_t access_flags,
                          InvokeType invoke_type,
                          uint16_t class_def_idx,
                          uint32_t method_idx,
                          Handle<mirror::ClassLoader> class_loader,
                          const DexFile& dex_file,
                          Handle<mirror::DexCache> dex_cache) const override;

  CompiledMethod* JniCompile(uint32_t access_flags,
                             uint32_t method_idx,
                             const DexFile& dex_file,
                             Handle<mirror::DexCache> dex_cache) const override;

  uintptr_t GetEntryPointOf(ArtMethod* method) const override
      REQUIRES_SHARED(Locks::mutator_lock_) {
    return reinterpret_cast<uintptr_t>(method->GetEntryPointFromQuickCompiledCodePtrSize(
        InstructionSetPointerSize(GetCompilerOptions().GetInstructionSet())));
  }

  bool JitCompile(Thread* self,
                  jit::JitCodeCache* code_cache,
                  jit::JitMemoryRegion* region,
                  ArtMethod* method,
                  CompilationKind compilation_kind,
                  jit::JitLogger* jit_logger)
      override
      REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  bool RunOptimizations(HGraph* graph,
                        CodeGenerator* codegen,
                        const DexCompilationUnit& dex_compilation_unit,
                        PassObserver* pass_observer,
                        const OptimizationDef definitions[],
                        size_t length) const {
    // Convert definitions to optimization passes.
    ArenaVector<HOptimization*> optimizations = ConstructOptimizations(
        definitions,
        length,
        graph->GetAllocator(),
        graph,
        compilation_stats_.get(),
        codegen,
        dex_compilation_unit);
    DCHECK_EQ(length, optimizations.size());
    // Run the optimization passes one by one. Any "depends_on" pass refers back to
    // the most recent occurrence of that pass, skipped or executed.
    std::bitset<static_cast<size_t>(OptimizationPass::kLast) + 1u> pass_changes;
    pass_changes[static_cast<size_t>(OptimizationPass::kNone)] = true;
    bool change = false;
    for (size_t i = 0; i < length; ++i) {
      if (pass_changes[static_cast<size_t>(definitions[i].depends_on)]) {
        // Execute the pass and record whether it changed anything.
        PassScope scope(optimizations[i]->GetPassName(), pass_observer);
        bool pass_change = optimizations[i]->Run();
        pass_changes[static_cast<size_t>(definitions[i].pass)] = pass_change;
        if (pass_change) {
          change = true;
        } else {
          scope.SetPassNotChanged();
        }
      } else {
        // Skip the pass and record that nothing changed.
        pass_changes[static_cast<size_t>(definitions[i].pass)] = false;
      }
    }
    return change;
  }

  template <size_t length> bool RunOptimizations(
      HGraph* graph,
      CodeGenerator* codegen,
      const DexCompilationUnit& dex_compilation_unit,
      PassObserver* pass_observer,
      const OptimizationDef (&definitions)[length]) const {
    return RunOptimizations(
        graph, codegen, dex_compilation_unit, pass_observer, definitions, length);
  }

  void RunOptimizations(HGraph* graph,
                        CodeGenerator* codegen,
                        const DexCompilationUnit& dex_compilation_unit,
                        PassObserver* pass_observer) const;

  // Create a 'CompiledMethod' for an optimized graph.
  CompiledMethod* Emit(ArenaAllocator* allocator,
                       CodeGenerator* codegen,
                       bool is_intrinsic,
                       const dex::CodeItem* item) const;

  // Try compiling a method and return the code generator used for
  // compiling it.
  // This method:
  // 1) Builds the graph. Returns null if it failed to build it.
  // 2) Transforms the graph to SSA. Returns null if it failed.
  // 3) Runs optimizations on the graph, including register allocator.
  CodeGenerator* TryCompile(ArenaAllocator* allocator,
                            ArenaStack* arena_stack,
                            const DexCompilationUnit& dex_compilation_unit,
                            ArtMethod* method,
                            CompilationKind compilation_kind,
                            VariableSizedHandleScope* handles) const;

  CodeGenerator* TryCompileIntrinsic(ArenaAllocator* allocator,
                                     ArenaStack* arena_stack,
                                     const DexCompilationUnit& dex_compilation_unit,
                                     ArtMethod* method,
                                     VariableSizedHandleScope* handles) const;

  bool RunArchOptimizations(HGraph* graph,
                            CodeGenerator* codegen,
                            const DexCompilationUnit& dex_compilation_unit,
                            PassObserver* pass_observer) const;

  bool RunRequiredPasses(HGraph* graph,
                         CodeGenerator* codegen,
                         const DexCompilationUnit& dex_compilation_unit,
                         PassObserver* pass_observer) const;

  std::vector<uint8_t> GenerateJitDebugInfo(const debug::MethodDebugInfo& method_debug_info);

  // This must be called before any other function that dumps data to the cfg
  void DumpInstructionSetFeaturesToCfg() const;

  std::unique_ptr<OptimizingCompilerStats> compilation_stats_;

  std::unique_ptr<std::ostream> visualizer_output_;

  DISALLOW_COPY_AND_ASSIGN(OptimizingCompiler);
};

static const int kMaximumCompilationTimeBeforeWarning = 100; /* ms */

OptimizingCompiler::OptimizingCompiler(const CompilerOptions& compiler_options,
                                       CompiledCodeStorage* storage)
    : Compiler(compiler_options, storage, kMaximumCompilationTimeBeforeWarning) {
  // Enable C1visualizer output.
  const std::string& cfg_file_name = compiler_options.GetDumpCfgFileName();
  if (!cfg_file_name.empty()) {
    std::ios_base::openmode cfg_file_mode =
        compiler_options.GetDumpCfgAppend() ? std::ofstream::app : std::ofstream::out;
    visualizer_output_.reset(new std::ofstream(cfg_file_name, cfg_file_mode));
    DumpInstructionSetFeaturesToCfg();
  }
  if (compiler_options.GetDumpStats()) {
    compilation_stats_.reset(new OptimizingCompilerStats());
  }
}

OptimizingCompiler::~OptimizingCompiler() {
  if (compilation_stats_.get() != nullptr) {
    compilation_stats_->Log();
  }
}

void OptimizingCompiler::DumpInstructionSetFeaturesToCfg() const {
  const CompilerOptions& compiler_options = GetCompilerOptions();
  const InstructionSetFeatures* features = compiler_options.GetInstructionSetFeatures();
  std::string isa_string =
      std::string("isa:") + GetInstructionSetString(features->GetInstructionSet());
  std::string features_string = "isa_features:" + features->GetFeatureString();
  std::string read_barrier_type = "none";
  if (compiler_options.EmitReadBarrier()) {
    if (art::kUseBakerReadBarrier)
      read_barrier_type = "baker";
    else if (art::kUseTableLookupReadBarrier)
      read_barrier_type = "tablelookup";
  }
  std::string read_barrier_string = ART_FORMAT("read_barrier_type:{}", read_barrier_type);
  // It is assumed that visualizer_output_ is empty when calling this function, hence the fake
  // compilation block containing the ISA features will be printed at the beginning of the .cfg
  // file.
  *visualizer_output_ << HGraphVisualizer::InsertMetaDataAsCompilationBlock(
      isa_string + ' ' + features_string + ' ' + read_barrier_string);
}

bool OptimizingCompiler::CanCompileMethod([[maybe_unused]] uint32_t method_idx,
                                          [[maybe_unused]] const DexFile& dex_file) const {
  return true;
}

static bool IsInstructionSetSupported(InstructionSet instruction_set) {
  return instruction_set == InstructionSet::kArm ||
         instruction_set == InstructionSet::kArm64 ||
         instruction_set == InstructionSet::kThumb2 ||
         instruction_set == InstructionSet::kRiscv64 ||
         instruction_set == InstructionSet::kX86 ||
         instruction_set == InstructionSet::kX86_64;
}

bool OptimizingCompiler::RunRequiredPasses(HGraph* graph,
                                           CodeGenerator* codegen,
                                           const DexCompilationUnit& dex_compilation_unit,
                                           PassObserver* pass_observer) const {
  switch (codegen->GetCompilerOptions().GetInstructionSet()) {
#if defined(ART_ENABLE_CODEGEN_arm)
    case InstructionSet::kThumb2:
    case InstructionSet::kArm: {
      OptimizationDef arm_optimizations[] = {
          OptDef(OptimizationPass::kCriticalNativeAbiFixupArm),
      };
      return RunOptimizations(graph,
                              codegen,
                              dex_compilation_unit,
                              pass_observer,
                              arm_optimizations);
    }
#endif
#if defined(ART_ENABLE_CODEGEN_riscv64)
    case InstructionSet::kRiscv64: {
      OptimizationDef riscv64_optimizations[] = {
          OptDef(OptimizationPass::kCriticalNativeAbiFixupRiscv64),
      };
      return RunOptimizations(graph,
                              codegen,
                              dex_compilation_unit,
                              pass_observer,
                              riscv64_optimizations);
    }
#endif
#ifdef ART_ENABLE_CODEGEN_x86
    case InstructionSet::kX86: {
      OptimizationDef x86_optimizations[] = {
          OptDef(OptimizationPass::kPcRelativeFixupsX86),
      };
      return RunOptimizations(graph,
                              codegen,
                              dex_compilation_unit,
                              pass_observer,
                              x86_optimizations);
    }
#endif
    default:
      UNUSED(graph);
      UNUSED(codegen);
      UNUSED(dex_compilation_unit);
      UNUSED(pass_observer);
      return false;
  }
}

bool OptimizingCompiler::RunArchOptimizations(HGraph* graph,
                                              CodeGenerator* codegen,
                                              const DexCompilationUnit& dex_compilation_unit,
                                              PassObserver* pass_observer) const {
  switch (codegen->GetCompilerOptions().GetInstructionSet()) {
#if defined(ART_ENABLE_CODEGEN_arm)
    case InstructionSet::kThumb2:
    case InstructionSet::kArm: {
      OptimizationDef arm_optimizations[] = {
          OptDef(OptimizationPass::kInstructionSimplifierArm),
          OptDef(OptimizationPass::kSideEffectsAnalysis),
          OptDef(OptimizationPass::kGlobalValueNumbering, "GVN$after_arch"),
          OptDef(OptimizationPass::kCriticalNativeAbiFixupArm),
          OptDef(OptimizationPass::kScheduling)
      };
      return RunOptimizations(graph,
                              codegen,
                              dex_compilation_unit,
                              pass_observer,
                              arm_optimizations);
    }
#endif
#ifdef ART_ENABLE_CODEGEN_arm64
    case InstructionSet::kArm64: {
      OptimizationDef arm64_optimizations[] = {
          OptDef(OptimizationPass::kInstructionSimplifierArm64),
          OptDef(OptimizationPass::kSideEffectsAnalysis),
          OptDef(OptimizationPass::kGlobalValueNumbering, "GVN$after_arch"),
          OptDef(OptimizationPass::kScheduling)
      };
      return RunOptimizations(graph,
                              codegen,
                              dex_compilation_unit,
                              pass_observer,
                              arm64_optimizations);
    }
#endif
#if defined(ART_ENABLE_CODEGEN_riscv64)
    case InstructionSet::kRiscv64: {
      OptimizationDef riscv64_optimizations[] = {
          OptDef(OptimizationPass::kSideEffectsAnalysis),
          OptDef(OptimizationPass::kGlobalValueNumbering, "GVN$after_arch"),
          OptDef(OptimizationPass::kCriticalNativeAbiFixupRiscv64)
      };
      return RunOptimizations(graph,
                              codegen,
                              dex_compilation_unit,
                              pass_observer,
                              riscv64_optimizations);
    }
#endif
#ifdef ART_ENABLE_CODEGEN_x86
    case InstructionSet::kX86: {
      OptimizationDef x86_optimizations[] = {
          OptDef(OptimizationPass::kInstructionSimplifierX86),
          OptDef(OptimizationPass::kSideEffectsAnalysis),
          OptDef(OptimizationPass::kGlobalValueNumbering, "GVN$after_arch"),
          OptDef(OptimizationPass::kPcRelativeFixupsX86),
          OptDef(OptimizationPass::kX86MemoryOperandGeneration)
      };
      return RunOptimizations(graph,
                              codegen,
                              dex_compilation_unit,
                              pass_observer,
                              x86_optimizations);
    }
#endif
#ifdef ART_ENABLE_CODEGEN_x86_64
    case InstructionSet::kX86_64: {
      OptimizationDef x86_64_optimizations[] = {
          OptDef(OptimizationPass::kInstructionSimplifierX86_64),
          OptDef(OptimizationPass::kSideEffectsAnalysis),
          OptDef(OptimizationPass::kGlobalValueNumbering, "GVN$after_arch"),
          OptDef(OptimizationPass::kX86MemoryOperandGeneration)
      };
      return RunOptimizations(graph,
                              codegen,
                              dex_compilation_unit,
                              pass_observer,
                              x86_64_optimizations);
    }
#endif
    default:
      UNUSED(graph);
      UNUSED(dex_compilation_unit);
      UNUSED(pass_observer);
      return false;
  }
}

NO_INLINE  // Avoid increasing caller's frame size by large stack-allocated objects.
static void AllocateRegisters(HGraph* graph,
                              CodeGenerator* codegen,
                              PassObserver* pass_observer,
                              RegisterAllocator::Strategy strategy,
                              OptimizingCompilerStats* stats) {
  {
    PassScope scope(PrepareForRegisterAllocation::kPrepareForRegisterAllocationPassName,
                    pass_observer);
    PrepareForRegisterAllocation(graph, codegen->GetCompilerOptions(), stats).Run();
  }
  // Use local allocator shared by SSA liveness analysis and register allocator.
  // (Register allocator creates new objects in the liveness data.)
  ScopedArenaAllocator local_allocator(graph->GetArenaStack());
  SsaLivenessAnalysis liveness(graph, codegen, &local_allocator);
  {
    PassScope scope(SsaLivenessAnalysis::kLivenessPassName, pass_observer);
    liveness.Analyze();
  }
  {
    PassScope scope(RegisterAllocator::kRegisterAllocatorPassName, pass_observer);
    std::unique_ptr<RegisterAllocator> register_allocator =
        RegisterAllocator::Create(&local_allocator, codegen, liveness, strategy);
    register_allocator->AllocateRegisters();
  }
}

// Strip pass name suffix to get optimization name.
static std::string ConvertPassNameToOptimizationName(const std::string& pass_name) {
  size_t pos = pass_name.find(kPassNameSeparator);
  return pos == std::string::npos ? pass_name : pass_name.substr(0, pos);
}

void OptimizingCompiler::RunOptimizations(HGraph* graph,
                                          CodeGenerator* codegen,
                                          const DexCompilationUnit& dex_compilation_unit,
                                          PassObserver* pass_observer) const {
  const std::vector<std::string>* pass_names = GetCompilerOptions().GetPassesToRun();
  if (pass_names != nullptr) {
    // If passes were defined on command-line, build the optimization
    // passes and run these instead of the built-in optimizations.
    // TODO: a way to define depends_on via command-line?
    const size_t length = pass_names->size();
    std::vector<OptimizationDef> optimizations;
    for (const std::string& pass_name : *pass_names) {
      std::string opt_name = ConvertPassNameToOptimizationName(pass_name);
      optimizations.push_back(OptDef(OptimizationPassByName(opt_name), pass_name.c_str()));
    }
    RunOptimizations(graph,
                     codegen,
                     dex_compilation_unit,
                     pass_observer,
                     optimizations.data(),
                     length);
    return;
  }

  OptimizationDef optimizations[] = {
      // Initial optimizations.
      OptDef(OptimizationPass::kConstantFolding),
      OptDef(OptimizationPass::kInstructionSimplifier),
      OptDef(OptimizationPass::kDeadCodeElimination,
             "dead_code_elimination$initial"),
      // Inlining.
      OptDef(OptimizationPass::kInliner),
      // Simplification (if inlining occurred, or if we analyzed the invoke as "always throwing").
      OptDef(OptimizationPass::kConstantFolding,
             "constant_folding$after_inlining",
             OptimizationPass::kInliner),
      OptDef(OptimizationPass::kInstructionSimplifier,
             "instruction_simplifier$after_inlining",
             OptimizationPass::kInliner),
      OptDef(OptimizationPass::kDeadCodeElimination,
             "dead_code_elimination$after_inlining",
             OptimizationPass::kInliner),
      // GVN.
      OptDef(OptimizationPass::kSideEffectsAnalysis,
             "side_effects$before_gvn"),
      OptDef(OptimizationPass::kGlobalValueNumbering),
      // Simplification (TODO: only if GVN occurred).
      OptDef(OptimizationPass::kSelectGenerator),
      OptDef(OptimizationPass::kConstantFolding,
             "constant_folding$after_gvn"),
      OptDef(OptimizationPass::kInstructionSimplifier,
             "instruction_simplifier$after_gvn"),
      OptDef(OptimizationPass::kDeadCodeElimination,
             "dead_code_elimination$after_gvn"),
      // High-level optimizations.
      OptDef(OptimizationPass::kSideEffectsAnalysis,
             "side_effects$before_licm"),
      OptDef(OptimizationPass::kInvariantCodeMotion),
      OptDef(OptimizationPass::kInductionVarAnalysis),
      OptDef(OptimizationPass::kBoundsCheckElimination),
      OptDef(OptimizationPass::kLoopOptimization),
      // Simplification.
      OptDef(OptimizationPass::kConstantFolding,
             "constant_folding$after_loop_opt"),
      OptDef(OptimizationPass::kAggressiveInstructionSimplifier,
             "instruction_simplifier$after_loop_opt"),
      OptDef(OptimizationPass::kDeadCodeElimination,
             "dead_code_elimination$after_loop_opt"),
      // Other high-level optimizations.
      OptDef(OptimizationPass::kLoadStoreElimination),
      OptDef(OptimizationPass::kCHAGuardOptimization),
      OptDef(OptimizationPass::kCodeSinking),
      // Simplification.
      OptDef(OptimizationPass::kConstantFolding,
             "constant_folding$before_codegen"),
      // The codegen has a few assumptions that only the instruction simplifier
      // can satisfy. For example, the code generator does not expect to see a
      // HTypeConversion from a type to the same type.
      OptDef(OptimizationPass::kAggressiveInstructionSimplifier,
             "instruction_simplifier$before_codegen"),
      // Simplification may result in dead code that should be removed prior to
      // code generation.
      OptDef(OptimizationPass::kDeadCodeElimination,
             "dead_code_elimination$before_codegen"),
      // Eliminate constructor fences after code sinking to avoid
      // complicated sinking logic to split a fence with many inputs.
      OptDef(OptimizationPass::kConstructorFenceRedundancyElimination)
  };
  RunOptimizations(graph,
                   codegen,
                   dex_compilation_unit,
                   pass_observer,
                   optimizations);

  RunArchOptimizations(graph, codegen, dex_compilation_unit, pass_observer);
}

static ArenaVector<linker::LinkerPatch> EmitAndSortLinkerPatches(CodeGenerator* codegen) {
  ArenaVector<linker::LinkerPatch> linker_patches(codegen->GetGraph()->GetAllocator()->Adapter());
  codegen->EmitLinkerPatches(&linker_patches);

  // Sort patches by literal offset. Required for .oat_patches encoding.
  std::sort(linker_patches.begin(), linker_patches.end(),
            [](const linker::LinkerPatch& lhs, const linker::LinkerPatch& rhs) {
    return lhs.LiteralOffset() < rhs.LiteralOffset();
  });

  return linker_patches;
}

CompiledMethod* OptimizingCompiler::Emit(ArenaAllocator* allocator,
                                         CodeGenerator* codegen,
                                         bool is_intrinsic,
                                         const dex::CodeItem* code_item_for_osr_check) const {
  ArenaVector<linker::LinkerPatch> linker_patches = EmitAndSortLinkerPatches(codegen);
  ScopedArenaVector<uint8_t> stack_map = codegen->BuildStackMaps(code_item_for_osr_check);

  CompiledCodeStorage* storage = GetCompiledCodeStorage();
  CompiledMethod* compiled_method = storage->CreateCompiledMethod(
      codegen->GetInstructionSet(),
      codegen->GetCode(),
      ArrayRef<const uint8_t>(stack_map),
      ArrayRef<const uint8_t>(*codegen->GetAssembler()->cfi().data()),
      ArrayRef<const linker::LinkerPatch>(linker_patches),
      is_intrinsic);

  for (const linker::LinkerPatch& patch : linker_patches) {
    if (codegen->NeedsThunkCode(patch) && storage->GetThunkCode(patch).empty()) {
      ArenaVector<uint8_t> code(allocator->Adapter());
      std::string debug_name;
      codegen->EmitThunkCode(patch, &code, &debug_name);
      storage->SetThunkCode(patch, ArrayRef<const uint8_t>(code), debug_name);
    }
  }

  return compiled_method;
}

CodeGenerator* OptimizingCompiler::TryCompile(ArenaAllocator* allocator,
                                              ArenaStack* arena_stack,
                                              const DexCompilationUnit& dex_compilation_unit,
                                              ArtMethod* method,
                                              CompilationKind compilation_kind,
                                              VariableSizedHandleScope* handles) const {
  MaybeRecordStat(compilation_stats_.get(), MethodCompilationStat::kAttemptBytecodeCompilation);
  const CompilerOptions& compiler_options = GetCompilerOptions();
  InstructionSet instruction_set = compiler_options.GetInstructionSet();
  const DexFile& dex_file = *dex_compilation_unit.GetDexFile();
  uint32_t method_idx = dex_compilation_unit.GetDexMethodIndex();
  const dex::CodeItem* code_item = dex_compilation_unit.GetCodeItem();

  // Always use the Thumb-2 assembler: some runtime functionality
  // (like implicit stack overflow checks) assume Thumb-2.
  DCHECK_NE(instruction_set, InstructionSet::kArm);

  // Do not attempt to compile on architectures we do not support.
  if (!IsInstructionSetSupported(instruction_set)) {
    MaybeRecordStat(compilation_stats_.get(),
                    MethodCompilationStat::kNotCompiledUnsupportedIsa);
    return nullptr;
  }

  if (Compiler::IsPathologicalCase(*code_item, method_idx, dex_file)) {
    MaybeRecordStat(compilation_stats_.get(), MethodCompilationStat::kNotCompiledPathological);
    return nullptr;
  }

  // Implementation of the space filter: do not compile a code item whose size in
  // code units is bigger than 128.
  static constexpr size_t kSpaceFilterOptimizingThreshold = 128;
  if ((compiler_options.GetCompilerFilter() == CompilerFilter::kSpace)
      && (CodeItemInstructionAccessor(dex_file, code_item).InsnsSizeInCodeUnits() >
          kSpaceFilterOptimizingThreshold)) {
    MaybeRecordStat(compilation_stats_.get(), MethodCompilationStat::kNotCompiledSpaceFilter);
    return nullptr;
  }

  CodeItemDebugInfoAccessor code_item_accessor(dex_file, code_item, method_idx);

  bool dead_reference_safe;
  // For AOT compilation, we may not get a method, for example if its class is erroneous,
  // possibly due to an unavailable superclass.  JIT should always have a method.
  DCHECK(Runtime::Current()->IsAotCompiler() || method != nullptr);
  if (method != nullptr) {
    const dex::ClassDef* containing_class;
    {
      ScopedObjectAccess soa(Thread::Current());
      containing_class = &method->GetClassDef();
    }
    // MethodContainsRSensitiveAccess is currently slow, but HasDeadReferenceSafeAnnotation()
    // is currently rarely true.
    dead_reference_safe =
        annotations::HasDeadReferenceSafeAnnotation(dex_file, *containing_class)
        && !annotations::MethodContainsRSensitiveAccess(dex_file, *containing_class, method_idx);
  } else {
    // If we could not resolve the class, conservatively assume it's dead-reference unsafe.
    dead_reference_safe = false;
  }

  HGraph* graph = new (allocator) HGraph(
      allocator,
      arena_stack,
      handles,
      dex_file,
      method_idx,
      compiler_options.GetInstructionSet(),
      kInvalidInvokeType,
      dead_reference_safe,
      compiler_options.GetDebuggable(),
      compilation_kind);

  if (method != nullptr) {
    graph->SetArtMethod(method);
  }

  jit::Jit* jit = Runtime::Current()->GetJit();
  if (jit != nullptr) {
    ProfilingInfo* info = jit->GetCodeCache()->GetProfilingInfo(method, Thread::Current());
    graph->SetProfilingInfo(info);
  }

  std::unique_ptr<CodeGenerator> codegen(
      CodeGenerator::Create(graph,
                            compiler_options,
                            compilation_stats_.get()));
  if (codegen.get() == nullptr) {
    MaybeRecordStat(compilation_stats_.get(), MethodCompilationStat::kNotCompiledNoCodegen);
    return nullptr;
  }
  codegen->GetAssembler()->cfi().SetEnabled(compiler_options.GenerateAnyDebugInfo());

  PassObserver pass_observer(graph,
                             codegen.get(),
                             visualizer_output_.get(),
                             compiler_options);

  {
    VLOG(compiler) << "Building " << pass_observer.GetMethodName();
    PassScope scope(HGraphBuilder::kBuilderPassName, &pass_observer);
    HGraphBuilder builder(graph,
                          code_item_accessor,
                          &dex_compilation_unit,
                          &dex_compilation_unit,
                          codegen.get(),
                          compilation_stats_.get());
    GraphAnalysisResult result = builder.BuildGraph();
    if (result != kAnalysisSuccess) {
      switch (result) {
        case kAnalysisSkipped: {
          MaybeRecordStat(compilation_stats_.get(),
                          MethodCompilationStat::kNotCompiledSkipped);
          break;
        }
        case kAnalysisInvalidBytecode: {
          MaybeRecordStat(compilation_stats_.get(),
                          MethodCompilationStat::kNotCompiledInvalidBytecode);
          break;
        }
        case kAnalysisFailThrowCatchLoop: {
          MaybeRecordStat(compilation_stats_.get(),
                          MethodCompilationStat::kNotCompiledThrowCatchLoop);
          break;
        }
        case kAnalysisFailAmbiguousArrayOp: {
          MaybeRecordStat(compilation_stats_.get(),
                          MethodCompilationStat::kNotCompiledAmbiguousArrayOp);
          break;
        }
        case kAnalysisFailIrreducibleLoopAndStringInit: {
          MaybeRecordStat(compilation_stats_.get(),
                          MethodCompilationStat::kNotCompiledIrreducibleLoopAndStringInit);
          break;
        }
        case kAnalysisFailPhiEquivalentInOsr: {
          MaybeRecordStat(compilation_stats_.get(),
                          MethodCompilationStat::kNotCompiledPhiEquivalentInOsr);
          break;
        }
        case kAnalysisSuccess:
          UNREACHABLE();
      }
      pass_observer.SetGraphInBadState();
      return nullptr;
    }
  }

  if (compilation_kind == CompilationKind::kBaseline && compiler_options.ProfileBranches()) {
    graph->SetUsefulOptimizing();
    // Branch profiling currently doesn't support running optimizations.
    RunRequiredPasses(graph, codegen.get(), dex_compilation_unit, &pass_observer);
  } else {
    RunOptimizations(graph, codegen.get(), dex_compilation_unit, &pass_observer);
    PassScope scope(WriteBarrierElimination::kWBEPassName, &pass_observer);
    WriteBarrierElimination(graph, compilation_stats_.get()).Run();
  }

  // If we are compiling baseline and we haven't created a profiling info for
  // this method already, do it now.
  if (jit != nullptr &&
      compilation_kind == CompilationKind::kBaseline &&
      graph->IsUsefulOptimizing() &&
      graph->GetProfilingInfo() == nullptr) {
    ProfilingInfoBuilder(
        graph, codegen->GetCompilerOptions(), codegen.get(), compilation_stats_.get()).Run();
    // We expect a profiling info to be created and attached to the graph.
    // However, we may have run out of memory trying to create it, so in this
    // case just abort the compilation.
    if (graph->GetProfilingInfo() == nullptr) {
      MaybeRecordStat(compilation_stats_.get(), MethodCompilationStat::kJitOutOfMemoryForCommit);
      return nullptr;
    }
  }

  RegisterAllocator::Strategy regalloc_strategy =
    compiler_options.GetRegisterAllocationStrategy();
  AllocateRegisters(graph,
                    codegen.get(),
                    &pass_observer,
                    regalloc_strategy,
                    compilation_stats_.get());

  if (UNLIKELY(codegen->GetFrameSize() > codegen->GetMaximumFrameSize())) {
    LOG(WARNING) << "Stack frame size is " << codegen->GetFrameSize()
                 << " which is larger than the maximum of " << codegen->GetMaximumFrameSize()
                 << " bytes. Method: " << graph->PrettyMethod();
    MaybeRecordStat(compilation_stats_.get(), MethodCompilationStat::kNotCompiledFrameTooBig);
    return nullptr;
  }

  codegen->Compile();
  pass_observer.DumpDisassembly();

  MaybeRecordStat(compilation_stats_.get(), MethodCompilationStat::kCompiledBytecode);
  return codegen.release();
}

CodeGenerator* OptimizingCompiler::TryCompileIntrinsic(
    ArenaAllocator* allocator,
    ArenaStack* arena_stack,
    const DexCompilationUnit& dex_compilation_unit,
    ArtMethod* method,
    VariableSizedHandleScope* handles) const {
  MaybeRecordStat(compilation_stats_.get(), MethodCompilationStat::kAttemptIntrinsicCompilation);
  const CompilerOptions& compiler_options = GetCompilerOptions();
  InstructionSet instruction_set = compiler_options.GetInstructionSet();
  const DexFile& dex_file = *dex_compilation_unit.GetDexFile();
  uint32_t method_idx = dex_compilation_unit.GetDexMethodIndex();

  // Always use the Thumb-2 assembler: some runtime functionality
  // (like implicit stack overflow checks) assume Thumb-2.
  DCHECK_NE(instruction_set, InstructionSet::kArm);

  // Do not attempt to compile on architectures we do not support.
  if (!IsInstructionSetSupported(instruction_set)) {
    return nullptr;
  }

  HGraph* graph = new (allocator) HGraph(
      allocator,
      arena_stack,
      handles,
      dex_file,
      method_idx,
      compiler_options.GetInstructionSet(),
      kInvalidInvokeType,
      /* dead_reference_safe= */ true,  // Intrinsics don't affect dead reference safety.
      compiler_options.GetDebuggable(),
      CompilationKind::kOptimized);

  DCHECK(Runtime::Current()->IsAotCompiler());
  DCHECK(method != nullptr);
  graph->SetArtMethod(method);

  std::unique_ptr<CodeGenerator> codegen(
      CodeGenerator::Create(graph,
                            compiler_options,
                            compilation_stats_.get()));
  if (codegen.get() == nullptr) {
    return nullptr;
  }
  codegen->GetAssembler()->cfi().SetEnabled(compiler_options.GenerateAnyDebugInfo());

  PassObserver pass_observer(graph,
                             codegen.get(),
                             visualizer_output_.get(),
                             compiler_options);

  {
    VLOG(compiler) << "Building intrinsic graph " << pass_observer.GetMethodName();
    PassScope scope(HGraphBuilder::kBuilderPassName, &pass_observer);
    HGraphBuilder builder(graph,
                          CodeItemDebugInfoAccessor(),  // Null code item.
                          &dex_compilation_unit,
                          &dex_compilation_unit,
                          codegen.get(),
                          compilation_stats_.get());
    builder.BuildIntrinsicGraph(method);
  }

  OptimizationDef optimizations[] = {
      // The codegen has a few assumptions that only the instruction simplifier
      // can satisfy.
      OptDef(OptimizationPass::kInstructionSimplifier),
  };
  RunOptimizations(graph,
                   codegen.get(),
                   dex_compilation_unit,
                   &pass_observer,
                   optimizations);

  RunArchOptimizations(graph, codegen.get(), dex_compilation_unit, &pass_observer);
  {
    PassScope scope(WriteBarrierElimination::kWBEPassName, &pass_observer);
    WriteBarrierElimination(graph, compilation_stats_.get()).Run();
  }

  AllocateRegisters(graph,
                    codegen.get(),
                    &pass_observer,
                    compiler_options.GetRegisterAllocationStrategy(),
                    compilation_stats_.get());
  if (!codegen->IsLeafMethod()) {
    VLOG(compiler) << "Intrinsic method is not leaf: " << method->GetIntrinsic()
        << " " << graph->PrettyMethod();
    return nullptr;
  }

  CHECK_LE(codegen->GetFrameSize(), codegen->GetMaximumFrameSize());
  codegen->Compile();
  pass_observer.DumpDisassembly();

  VLOG(compiler) << "Compiled intrinsic: " << method->GetIntrinsic()
      << " " << graph->PrettyMethod();
  MaybeRecordStat(compilation_stats_.get(), MethodCompilationStat::kCompiledIntrinsic);
  return codegen.release();
}

CompiledMethod* OptimizingCompiler::Compile(const dex::CodeItem* code_item,
                                            uint32_t access_flags,
                                            InvokeType invoke_type,
                                            uint16_t class_def_idx,
                                            uint32_t method_idx,
                                            Handle<mirror::ClassLoader> jclass_loader,
                                            const DexFile& dex_file,
                                            Handle<mirror::DexCache> dex_cache) const {
  const CompilerOptions& compiler_options = GetCompilerOptions();
  DCHECK(compiler_options.IsAotCompiler());
  CompiledMethod* compiled_method = nullptr;
  Runtime* runtime = Runtime::Current();
  DCHECK(runtime->IsAotCompiler());
  ArenaAllocator allocator(runtime->GetArenaPool());
  ArenaStack arena_stack(runtime->GetArenaPool());
  std::unique_ptr<CodeGenerator> codegen;
  bool compiled_intrinsic = false;
  {
    ScopedObjectAccess soa(Thread::Current());
    ArtMethod* method =
        runtime->GetClassLinker()->ResolveMethod<ClassLinker::ResolveMode::kCheckICCEAndIAE>(
            method_idx, dex_cache, jclass_loader, /*referrer=*/ nullptr, invoke_type);
    DCHECK_EQ(method == nullptr, soa.Self()->IsExceptionPending());
    soa.Self()->ClearException();  // Suppress exception if any.
    VariableSizedHandleScope handles(soa.Self());
    Handle<mirror::Class> compiling_class =
        handles.NewHandle(method != nullptr ? method->GetDeclaringClass() : nullptr);
    DexCompilationUnit dex_compilation_unit(
        jclass_loader,
        runtime->GetClassLinker(),
        dex_file,
        code_item,
        class_def_idx,
        method_idx,
        access_flags,
        /*verified_method=*/ nullptr,  // Not needed by the Optimizing compiler.
        dex_cache,
        compiling_class);
    // All signature polymorphic methods are native.
    DCHECK(method == nullptr || !method->IsSignaturePolymorphic());
    // Go to native so that we don't block GC during compilation.
    ScopedThreadSuspension sts(soa.Self(), ThreadState::kNative);
    // Try to compile a fully intrinsified implementation.
    if (method != nullptr && UNLIKELY(method->IsIntrinsic())) {
      DCHECK(compiler_options.IsBootImage());
      codegen.reset(
          TryCompileIntrinsic(&allocator,
                              &arena_stack,
                              dex_compilation_unit,
                              method,
                              &handles));
      if (codegen != nullptr) {
        compiled_intrinsic = true;
      }
    }
    if (codegen == nullptr) {
      codegen.reset(
          TryCompile(&allocator,
                     &arena_stack,
                     dex_compilation_unit,
                     method,
                     compiler_options.IsBaseline()
                        ? CompilationKind::kBaseline
                        : CompilationKind::kOptimized,
                     &handles));
    }
  }
  if (codegen.get() != nullptr) {
    compiled_method = Emit(&allocator,
                           codegen.get(),
                           compiled_intrinsic,
                           compiled_intrinsic ? nullptr : code_item);

    if (kArenaAllocatorCountAllocations) {
      codegen.reset();  // Release codegen's ScopedArenaAllocator for memory accounting.
      size_t total_allocated = allocator.BytesAllocated() + arena_stack.PeakBytesAllocated();
      if (total_allocated > kArenaAllocatorMemoryReportThreshold) {
        MemStats mem_stats(allocator.GetMemStats());
        MemStats peak_stats(arena_stack.GetPeakStats());
        LOG(INFO) << "Used " << total_allocated << " bytes of arena memory for compiling "
                  << dex_file.PrettyMethod(method_idx)
                  << "\n" << Dumpable<MemStats>(mem_stats)
                  << "\n" << Dumpable<MemStats>(peak_stats);
      }
    }
  }

  if (kIsDebugBuild &&
      compiler_options.CompileArtTest() &&
      IsInstructionSetSupported(compiler_options.GetInstructionSet())) {
    // For testing purposes, we put a special marker on method names
    // that should be compiled with this compiler (when the
    // instruction set is supported). This makes sure we're not
    // regressing.
    std::string method_name = dex_file.PrettyMethod(method_idx);
    bool shouldCompile = method_name.find("$opt$") != std::string::npos;
    DCHECK_IMPLIES(compiled_method == nullptr, !shouldCompile) << "Didn't compile " << method_name;
  }

  return compiled_method;
}

static ScopedArenaVector<uint8_t> CreateJniStackMap(ScopedArenaAllocator* allocator,
                                                    const JniCompiledMethod& jni_compiled_method,
                                                    size_t code_size,
                                                    bool debuggable) {
  // StackMapStream is quite large, so allocate it using the ScopedArenaAllocator
  // to stay clear of the frame size limit.
  std::unique_ptr<StackMapStream> stack_map_stream(
      new (allocator) StackMapStream(allocator, jni_compiled_method.GetInstructionSet()));
  stack_map_stream->BeginMethod(jni_compiled_method.GetFrameSize(),
                                jni_compiled_method.GetCoreSpillMask(),
                                jni_compiled_method.GetFpSpillMask(),
                                /* num_dex_registers= */ 0,
                                /* baseline= */ false,
                                debuggable);
  stack_map_stream->EndMethod(code_size);
  return stack_map_stream->Encode();
}

CompiledMethod* OptimizingCompiler::JniCompile(uint32_t access_flags,
                                               uint32_t method_idx,
                                               const DexFile& dex_file,
                                               Handle<mirror::DexCache> dex_cache) const {
  Runtime* runtime = Runtime::Current();
  ArenaAllocator allocator(runtime->GetArenaPool());
  ArenaStack arena_stack(runtime->GetArenaPool());

  const CompilerOptions& compiler_options = GetCompilerOptions();
  if (compiler_options.IsBootImage()) {
    ScopedObjectAccess soa(Thread::Current());
    ArtMethod* method = runtime->GetClassLinker()->LookupResolvedMethod(
        method_idx, dex_cache.Get(), /*class_loader=*/ nullptr);
    // Try to compile a fully intrinsified implementation. Do not try to do this for
    // signature polymorphic methods as the InstructionBuilder cannot handle them;
    // and it would be useless as they always have a slow path for type conversions.
    if (method != nullptr && UNLIKELY(method->IsIntrinsic()) && !method->IsSignaturePolymorphic()) {
      VariableSizedHandleScope handles(soa.Self());
      ScopedNullHandle<mirror::ClassLoader> class_loader;  // null means boot class path loader.
      Handle<mirror::Class> compiling_class = handles.NewHandle(method->GetDeclaringClass());
      DexCompilationUnit dex_compilation_unit(
          class_loader,
          runtime->GetClassLinker(),
          dex_file,
          /*code_item=*/ nullptr,
          /*class_def_idx=*/ DexFile::kDexNoIndex16,
          method_idx,
          access_flags,
          /*verified_method=*/ nullptr,
          dex_cache,
          compiling_class);
      // Go to native so that we don't block GC during compilation.
      ScopedThreadSuspension sts(soa.Self(), ThreadState::kNative);
      std::unique_ptr<CodeGenerator> codegen(
          TryCompileIntrinsic(&allocator,
                              &arena_stack,
                              dex_compilation_unit,
                              method,
                              &handles));
      if (codegen != nullptr) {
        return Emit(&allocator,
                    codegen.get(),
                    /*is_intrinsic=*/ true,
                    /*item=*/ nullptr);
      }
    }
  }

  JniCompiledMethod jni_compiled_method = ArtQuickJniCompileMethod(
      compiler_options, access_flags, method_idx, dex_file, &allocator);
  MaybeRecordStat(compilation_stats_.get(), MethodCompilationStat::kCompiledNativeStub);

  ScopedArenaAllocator stack_map_allocator(&arena_stack);  // Will hold the stack map.
  ScopedArenaVector<uint8_t> stack_map =
      CreateJniStackMap(&stack_map_allocator,
                        jni_compiled_method,
                        jni_compiled_method.GetCode().size(),
                        compiler_options.GetDebuggable() && compiler_options.IsJitCompiler());
  return GetCompiledCodeStorage()->CreateCompiledMethod(
      jni_compiled_method.GetInstructionSet(),
      jni_compiled_method.GetCode(),
      ArrayRef<const uint8_t>(stack_map),
      jni_compiled_method.GetCfi(),
      /*patches=*/ ArrayRef<const linker::LinkerPatch>(),
      /*is_intrinsic=*/ false);
}

Compiler* CreateOptimizingCompiler(const CompilerOptions& compiler_options,
                                   CompiledCodeStorage* storage) {
  return new OptimizingCompiler(compiler_options, storage);
}

bool EncodeArtMethodInInlineInfo([[maybe_unused]] ArtMethod* method) {
  // Note: the runtime is null only for unit testing.
  return Runtime::Current() == nullptr || !Runtime::Current()->IsAotCompiler();
}

bool OptimizingCompiler::JitCompile(Thread* self,
                                    jit::JitCodeCache* code_cache,
                                    jit::JitMemoryRegion* region,
                                    ArtMethod* method,
                                    CompilationKind compilation_kind,
                                    jit::JitLogger* jit_logger) {
  const CompilerOptions& compiler_options = GetCompilerOptions();
  DCHECK(compiler_options.IsJitCompiler());
  DCHECK_EQ(compiler_options.IsJitCompilerForSharedCode(), code_cache->IsSharedRegion(*region));
  StackHandleScope<3> hs(self);
  Handle<mirror::ClassLoader> class_loader(hs.NewHandle(
      method->GetDeclaringClass()->GetClassLoader()));
  Handle<mirror::DexCache> dex_cache(hs.NewHandle(method->GetDexCache()));
  DCHECK(method->IsCompilable());

  const DexFile* dex_file = method->GetDexFile();
  const uint16_t class_def_idx = method->GetClassDefIndex();
  const dex::CodeItem* code_item = method->GetCodeItem();
  const uint32_t method_idx = method->GetDexMethodIndex();
  const uint32_t access_flags = method->GetAccessFlags();

  Runtime* runtime = Runtime::Current();
  ArenaAllocator allocator(runtime->GetJitArenaPool());

  if (UNLIKELY(method->IsNative())) {
    // Use GenericJniTrampoline for critical native methods in debuggable runtimes. We don't
    // support calling method entry / exit hooks for critical native methods yet.
    // TODO(mythria): Add support for calling method entry / exit hooks in JITed stubs for critical
    // native methods too.
    if (compiler_options.GetDebuggable() && method->IsCriticalNative()) {
      DCHECK(compiler_options.IsJitCompiler());
      return false;
    }
    // Java debuggable runtimes should set compiler options to debuggable, so that we either
    // generate method entry / exit hooks or skip JITing. For critical native methods we don't
    // generate method entry / exit hooks so we shouldn't JIT them in debuggable runtimes.
    DCHECK_IMPLIES(method->IsCriticalNative(), !runtime->IsJavaDebuggable());

    JniCompiledMethod jni_compiled_method = ArtQuickJniCompileMethod(
        compiler_options, access_flags, method_idx, *dex_file, &allocator);
    std::vector<Handle<mirror::Object>> roots;
    ArenaSet<ArtMethod*, std::less<ArtMethod*>> cha_single_implementation_list(
        allocator.Adapter(kArenaAllocCHA));
    ArenaStack arena_stack(runtime->GetJitArenaPool());
    // StackMapStream is large and it does not fit into this frame, so we need helper method.
    ScopedArenaAllocator stack_map_allocator(&arena_stack);  // Will hold the stack map.
    ScopedArenaVector<uint8_t> stack_map =
        CreateJniStackMap(&stack_map_allocator,
                          jni_compiled_method,
                          jni_compiled_method.GetCode().size(),
                          compiler_options.GetDebuggable() && compiler_options.IsJitCompiler());

    ArrayRef<const uint8_t> reserved_code;
    ArrayRef<const uint8_t> reserved_data;
    if (!code_cache->Reserve(self,
                             region,
                             jni_compiled_method.GetCode().size(),
                             stack_map.size(),
                             /* number_of_roots= */ 0,
                             method,
                             /*out*/ &reserved_code,
                             /*out*/ &reserved_data)) {
      MaybeRecordStat(compilation_stats_.get(), MethodCompilationStat::kJitOutOfMemoryForCommit);
      return false;
    }
    const uint8_t* code = reserved_code.data() + OatQuickMethodHeader::InstructionAlignedSize();

    // Add debug info after we know the code location but before we update entry-point.
    std::vector<uint8_t> debug_info;
    if (compiler_options.GenerateAnyDebugInfo()) {
      debug::MethodDebugInfo info = {};
      // Simpleperf relies on art_jni_trampoline to detect jni methods.
      info.custom_name = "art_jni_trampoline";
      info.dex_file = dex_file;
      info.class_def_index = class_def_idx;
      info.dex_method_index = method_idx;
      info.access_flags = access_flags;
      info.code_item = code_item;
      info.isa = jni_compiled_method.GetInstructionSet();
      info.deduped = false;
      info.is_native_debuggable = compiler_options.GetNativeDebuggable();
      info.is_optimized = true;
      info.is_code_address_text_relative = false;
      info.code_address = reinterpret_cast<uintptr_t>(code);
      info.code_size = jni_compiled_method.GetCode().size();
      info.frame_size_in_bytes = jni_compiled_method.GetFrameSize();
      info.code_info = nullptr;
      info.cfi = jni_compiled_method.GetCfi();
      debug_info = GenerateJitDebugInfo(info);
    }

    if (!code_cache->Commit(self,
                            region,
                            method,
                            reserved_code,
                            jni_compiled_method.GetCode(),
                            reserved_data,
                            roots,
                            ArrayRef<const uint8_t>(stack_map),
                            debug_info,
                            /* is_full_debug_info= */ compiler_options.GetGenerateDebugInfo(),
                            compilation_kind,
                            cha_single_implementation_list)) {
      code_cache->Free(self, region, reserved_code.data(), reserved_data.data());
      return false;
    }

    Runtime::Current()->GetJit()->AddMemoryUsage(method, allocator.BytesUsed());
    if (jit_logger != nullptr) {
      jit_logger->WriteLog(code, jni_compiled_method.GetCode().size(), method);
    }
    return true;
  }

  ArenaStack arena_stack(runtime->GetJitArenaPool());
  VariableSizedHandleScope handles(self);

  std::unique_ptr<CodeGenerator> codegen;
  {
    Handle<mirror::Class> compiling_class = handles.NewHandle(method->GetDeclaringClass());
    DexCompilationUnit dex_compilation_unit(
        class_loader,
        runtime->GetClassLinker(),
        *dex_file,
        code_item,
        class_def_idx,
        method_idx,
        access_flags,
        /*verified_method=*/ nullptr,
        dex_cache,
        compiling_class);

    // Go to native so that we don't block GC during compilation.
    ScopedThreadSuspension sts(self, ThreadState::kNative);
    codegen.reset(
        TryCompile(&allocator,
                   &arena_stack,
                   dex_compilation_unit,
                   method,
                   compilation_kind,
                   &handles));
    if (codegen.get() == nullptr) {
      return false;
    }
  }

  ScopedArenaVector<uint8_t> stack_map = codegen->BuildStackMaps(code_item);

  ArrayRef<const uint8_t> reserved_code;
  ArrayRef<const uint8_t> reserved_data;
  if (!code_cache->Reserve(self,
                           region,
                           codegen->GetAssembler()->CodeSize(),
                           stack_map.size(),
                           /*number_of_roots=*/codegen->GetNumberOfJitRoots(),
                           method,
                           /*out*/ &reserved_code,
                           /*out*/ &reserved_data)) {
    MaybeRecordStat(compilation_stats_.get(), MethodCompilationStat::kJitOutOfMemoryForCommit);
    return false;
  }
  const uint8_t* code = reserved_code.data() + OatQuickMethodHeader::InstructionAlignedSize();
  const uint8_t* roots_data = reserved_data.data();

  std::vector<Handle<mirror::Object>> roots;
  codegen->EmitJitRoots(const_cast<uint8_t*>(codegen->GetAssembler()->CodeBufferBaseAddress()),
                        roots_data,
                        &roots);
  // The root Handle<>s filled by the codegen reference entries in the VariableSizedHandleScope.
  DCHECK(std::all_of(roots.begin(),
                     roots.end(),
                     [&handles](Handle<mirror::Object> root){
                       return handles.Contains(root.GetReference());
                     }));

  // Add debug info after we know the code location but before we update entry-point.
  std::vector<uint8_t> debug_info;
  if (compiler_options.GenerateAnyDebugInfo()) {
    debug::MethodDebugInfo info = {};
    DCHECK(info.custom_name.empty());
    info.dex_file = dex_file;
    info.class_def_index = class_def_idx;
    info.dex_method_index = method_idx;
    info.access_flags = access_flags;
    info.code_item = code_item;
    info.isa = codegen->GetInstructionSet();
    info.deduped = false;
    info.is_native_debuggable = compiler_options.GetNativeDebuggable();
    info.is_optimized = true;
    info.is_code_address_text_relative = false;
    info.code_address = reinterpret_cast<uintptr_t>(code);
    info.code_size = codegen->GetAssembler()->CodeSize(),
    info.frame_size_in_bytes = codegen->GetFrameSize();
    info.code_info = stack_map.size() == 0 ? nullptr : stack_map.data();
    info.cfi = ArrayRef<const uint8_t>(*codegen->GetAssembler()->cfi().data());
    debug_info = GenerateJitDebugInfo(info);
  }

  if (compilation_kind == CompilationKind::kBaseline &&
      !codegen->GetGraph()->IsUsefulOptimizing()) {
    compilation_kind = CompilationKind::kOptimized;
  }

  if (!code_cache->Commit(self,
                          region,
                          method,
                          reserved_code,
                          codegen->GetCode(),
                          reserved_data,
                          roots,
                          ArrayRef<const uint8_t>(stack_map),
                          debug_info,
                          /* is_full_debug_info= */ compiler_options.GetGenerateDebugInfo(),
                          compilation_kind,
                          codegen->GetGraph()->GetCHASingleImplementationList())) {
    CHECK_EQ(CodeInfo::HasShouldDeoptimizeFlag(stack_map.data()),
             codegen->GetGraph()->HasShouldDeoptimizeFlag());
    code_cache->Free(self, region, reserved_code.data(), reserved_data.data());
    return false;
  }

  Runtime::Current()->GetJit()->AddMemoryUsage(method, allocator.BytesUsed());
  if (jit_logger != nullptr) {
    jit_logger->WriteLog(code, codegen->GetAssembler()->CodeSize(), method);
  }

  if (kArenaAllocatorCountAllocations) {
    codegen.reset();  // Release codegen's ScopedArenaAllocator for memory accounting.
    size_t total_allocated = allocator.BytesAllocated() + arena_stack.PeakBytesAllocated();
    if (total_allocated > kArenaAllocatorMemoryReportThreshold) {
      MemStats mem_stats(allocator.GetMemStats());
      MemStats peak_stats(arena_stack.GetPeakStats());
      LOG(INFO) << "Used " << total_allocated << " bytes of arena memory for compiling "
                << dex_file->PrettyMethod(method_idx)
                << "\n" << Dumpable<MemStats>(mem_stats)
                << "\n" << Dumpable<MemStats>(peak_stats);
    }
  }

  return true;
}

std::vector<uint8_t> OptimizingCompiler::GenerateJitDebugInfo(const debug::MethodDebugInfo& info) {
  const CompilerOptions& compiler_options = GetCompilerOptions();
  if (compiler_options.GenerateAnyDebugInfo()) {
    // If both flags are passed, generate full debug info.
    const bool mini_debug_info = !compiler_options.GetGenerateDebugInfo();

    // Create entry for the single method that we just compiled.
    InstructionSet isa = compiler_options.GetInstructionSet();
    const InstructionSetFeatures* features = compiler_options.GetInstructionSetFeatures();
    return debug::MakeElfFileForJIT(isa, features, mini_debug_info, info);
  }
  return std::vector<uint8_t>();
}

}  // namespace art
