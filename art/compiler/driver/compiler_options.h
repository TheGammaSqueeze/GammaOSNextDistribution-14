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

#ifndef ART_COMPILER_DRIVER_COMPILER_OPTIONS_H_
#define ART_COMPILER_DRIVER_COMPILER_OPTIONS_H_

#include <memory>
#include <ostream>
#include <string>
#include <vector>

#include "base/compiler_filter.h"
#include "base/globals.h"
#include "base/hash_set.h"
#include "base/macros.h"
#include "base/stl_util.h"
#include "base/utils.h"
#include "optimizing/register_allocator.h"

namespace art HIDDEN {

namespace jit {
class JitCompiler;
}  // namespace jit

namespace verifier {
class VerifierDepsTest;
}  // namespace verifier

namespace linker {
class Arm64RelativePatcherTest;
class Thumb2RelativePatcherTest;
}  // namespace linker

class ArtMethod;
class DexFile;
enum class InstructionSet;
class InstructionSetFeatures;
class ProfileCompilationInfo;

// Enum for CheckProfileMethodsCompiled. Outside CompilerOptions so it can be forward-declared.
enum class ProfileMethodsCheck : uint8_t {
  kNone,
  kLog,
  kAbort,
};

class CompilerOptions final {
 public:
  // Default values for parameters set via flags.
  static constexpr bool kDefaultGenerateDebugInfo = false;
  static constexpr bool kDefaultGenerateMiniDebugInfo = true;
  static constexpr size_t kDefaultHugeMethodThreshold = 10000;
  static constexpr size_t kDefaultInlineMaxCodeUnits = 32;
  // Token to represent no value set for `inline_max_code_units_`.
  static constexpr size_t kUnsetInlineMaxCodeUnits = -1;
  // We set a lower inlining threshold for baseline to reduce code size and compilation time. This
  // cannot be changed via flags.
  static constexpr size_t kBaselineInlineMaxCodeUnits = 14;

  enum class CompilerType : uint8_t {
    kAotCompiler,             // AOT compiler.
    kJitCompiler,             // Normal JIT compiler.
    kSharedCodeJitCompiler,   // Zygote JIT producing code in the shared region area, putting
                              // restrictions on, for example, how literals are being generated.
  };

  enum class ImageType : uint8_t {
    kNone,                    // JIT or AOT app compilation producing only an oat file but no image.
    kBootImage,               // Creating boot image.
    kBootImageExtension,      // Creating boot image extension.
    kAppImage,                // Creating app image.
  };

  EXPORT CompilerOptions();
  EXPORT ~CompilerOptions();

  CompilerFilter::Filter GetCompilerFilter() const {
    return compiler_filter_;
  }

  void SetCompilerFilter(CompilerFilter::Filter compiler_filter) {
    compiler_filter_ = compiler_filter;
  }

  bool IsAotCompilationEnabled() const {
    return CompilerFilter::IsAotCompilationEnabled(compiler_filter_);
  }

  bool IsJniCompilationEnabled() const {
    return CompilerFilter::IsJniCompilationEnabled(compiler_filter_);
  }

  bool IsVerificationEnabled() const {
    return CompilerFilter::IsVerificationEnabled(compiler_filter_);
  }

  bool AssumeDexFilesAreVerified() const {
    return compiler_filter_ == CompilerFilter::kAssumeVerified;
  }

  bool AssumeClassesAreVerified() const {
    return compiler_filter_ == CompilerFilter::kAssumeVerified;
  }

  bool IsAnyCompilationEnabled() const {
    return CompilerFilter::IsAnyCompilationEnabled(compiler_filter_);
  }

  size_t GetHugeMethodThreshold() const {
    return huge_method_threshold_;
  }

  bool IsHugeMethod(size_t num_dalvik_instructions) const {
    return num_dalvik_instructions > huge_method_threshold_;
  }

  size_t GetInlineMaxCodeUnits() const {
    return inline_max_code_units_;
  }
  void SetInlineMaxCodeUnits(size_t units) {
    inline_max_code_units_ = units;
  }

  bool EmitReadBarrier() const {
    return emit_read_barrier_;
  }

  bool GetDebuggable() const {
    return debuggable_;
  }

  void SetDebuggable(bool value) {
    debuggable_ = value;
  }

  bool GetNativeDebuggable() const {
    return GetDebuggable() && GetGenerateDebugInfo();
  }

  // This flag controls whether the compiler collects debugging information.
  // The other flags control how the information is written to disk.
  bool GenerateAnyDebugInfo() const {
    return GetGenerateDebugInfo() || GetGenerateMiniDebugInfo();
  }

  bool GetGenerateDebugInfo() const {
    return generate_debug_info_;
  }

  bool GetGenerateMiniDebugInfo() const {
    return generate_mini_debug_info_;
  }

  // Should run-time checks be emitted in debug mode?
  bool EmitRunTimeChecksInDebugMode() const;

  bool GetGenerateBuildId() const {
    return generate_build_id_;
  }

  bool GetImplicitNullChecks() const {
    return implicit_null_checks_;
  }

  bool GetImplicitStackOverflowChecks() const {
    return implicit_so_checks_;
  }

  bool IsAotCompiler() const {
    return compiler_type_ == CompilerType::kAotCompiler;
  }

  bool IsJitCompiler() const {
    return compiler_type_ == CompilerType::kJitCompiler ||
           compiler_type_ == CompilerType::kSharedCodeJitCompiler;
  }

  bool IsJitCompilerForSharedCode() const {
    return compiler_type_ == CompilerType::kSharedCodeJitCompiler;
  }

  bool GetImplicitSuspendChecks() const {
    return implicit_suspend_checks_;
  }

  bool IsGeneratingImage() const {
    return IsBootImage() || IsBootImageExtension() || IsAppImage();
  }

  // Are we compiling a boot image?
  bool IsBootImage() const {
    return image_type_ == ImageType::kBootImage;
  }

  // Are we compiling a boot image extension?
  bool IsBootImageExtension() const {
    return image_type_ == ImageType::kBootImageExtension;
  }

  bool IsBaseline() const {
    return baseline_;
  }

  bool ProfileBranches() const {
    return profile_branches_;
  }

  // Are we compiling an app image?
  bool IsAppImage() const {
    return image_type_ == ImageType::kAppImage;
  }

  bool IsMultiImage() const {
    return multi_image_;
  }

  // Returns whether we are running ART tests.
  // The compiler will use that information for checking invariants.
  bool CompileArtTest() const {
    return compile_art_test_;
  }

  // Should the code be compiled as position independent?
  bool GetCompilePic() const {
    return compile_pic_;
  }

  const ProfileCompilationInfo* GetProfileCompilationInfo() const {
    return profile_compilation_info_;
  }

  bool HasVerboseMethods() const {
    return !verbose_methods_.empty();
  }

  bool IsVerboseMethod(const std::string& pretty_method) const {
    for (const std::string& cur_method : verbose_methods_) {
      if (pretty_method.find(cur_method) != std::string::npos) {
        return true;
      }
    }
    return false;
  }

  std::ostream* GetInitFailureOutput() const {
    return init_failure_output_.get();
  }

  bool AbortOnHardVerifierFailure() const {
    return abort_on_hard_verifier_failure_;
  }
  bool AbortOnSoftVerifierFailure() const {
    return abort_on_soft_verifier_failure_;
  }

  InstructionSet GetInstructionSet() const {
    return instruction_set_;
  }

  const InstructionSetFeatures* GetInstructionSetFeatures() const {
    return instruction_set_features_.get();
  }


  const std::vector<const DexFile*>& GetNoInlineFromDexFile() const {
    return no_inline_from_;
  }

  const std::vector<const DexFile*>& GetDexFilesForOatFile() const {
    return dex_files_for_oat_file_;
  }

  const HashSet<std::string>& GetImageClasses() const {
    return image_classes_;
  }

  EXPORT bool IsImageClass(const char* descriptor) const;

  // Returns whether the given `pretty_descriptor` is in the list of preloaded
  // classes. `pretty_descriptor` should be the result of calling `PrettyDescriptor`.
  EXPORT bool IsPreloadedClass(const char* pretty_descriptor) const;

  bool ParseCompilerOptions(const std::vector<std::string>& options,
                            bool ignore_unrecognized,
                            std::string* error_msg);

  void SetNonPic() {
    compile_pic_ = false;
  }

  const std::string& GetDumpCfgFileName() const {
    return dump_cfg_file_name_;
  }

  bool GetDumpCfgAppend() const {
    return dump_cfg_append_;
  }

  bool IsForceDeterminism() const {
    return force_determinism_;
  }

  bool IsCheckLinkageConditions() const {
    return check_linkage_conditions_;
  }

  bool IsCrashOnLinkageViolation() const {
    return crash_on_linkage_violation_;
  }

  bool DeduplicateCode() const {
    return deduplicate_code_;
  }

  RegisterAllocator::Strategy GetRegisterAllocationStrategy() const {
    return register_allocation_strategy_;
  }

  const std::vector<std::string>* GetPassesToRun() const {
    return passes_to_run_;
  }

  bool GetDumpTimings() const {
    return dump_timings_;
  }

  bool GetDumpPassTimings() const {
    return dump_pass_timings_;
  }

  bool GetDumpStats() const {
    return dump_stats_;
  }

  bool CountHotnessInCompiledCode() const {
    return count_hotness_in_compiled_code_;
  }

  bool ResolveStartupConstStrings() const {
    return resolve_startup_const_strings_;
  }

  ProfileMethodsCheck CheckProfiledMethodsCompiled() const {
    return check_profiled_methods_;
  }

  uint32_t MaxImageBlockSize() const {
    return max_image_block_size_;
  }

  void SetMaxImageBlockSize(uint32_t size) {
    max_image_block_size_ = size;
  }

  bool InitializeAppImageClasses() const {
    return initialize_app_image_classes_;
  }

  // Returns true if `dex_file` is within an oat file we're producing right now.
  bool WithinOatFile(const DexFile* dex_file) const {
    return ContainsElement(GetDexFilesForOatFile(), dex_file);
  }

  // If this is a static non-constructor method in the boot classpath, and its class isn't
  // initialized at compile-time, or won't be initialized by the zygote, add
  // initialization checks at entry. This will avoid the need of trampolines
  // which at runtime we will need to dirty after initialization.
  EXPORT bool ShouldCompileWithClinitCheck(ArtMethod* method) const;

 private:
  EXPORT bool ParseDumpInitFailures(const std::string& option, std::string* error_msg);
  EXPORT bool ParseRegisterAllocationStrategy(const std::string& option, std::string* error_msg);

  CompilerFilter::Filter compiler_filter_;
  size_t huge_method_threshold_;
  size_t inline_max_code_units_;

  InstructionSet instruction_set_;
  std::unique_ptr<const InstructionSetFeatures> instruction_set_features_;

  // Dex files from which we should not inline code. Does not own the dex files.
  // This is usually a very short list (i.e. a single dex file), so we
  // prefer vector<> over a lookup-oriented container, such as set<>.
  std::vector<const DexFile*> no_inline_from_;

  // List of dex files associated with the oat file, empty for JIT.
  std::vector<const DexFile*> dex_files_for_oat_file_;

  // Image classes, specifies the classes that will be included in the image if creating an image.
  // Must not be empty for real boot image, only for tests pretending to compile boot image.
  HashSet<std::string> image_classes_;

  // Classes listed in the preloaded-classes file, used for boot image and
  // boot image extension compilation.
  HashSet<std::string> preloaded_classes_;

  CompilerType compiler_type_;
  ImageType image_type_;
  bool multi_image_;
  bool compile_art_test_;
  bool emit_read_barrier_;
  bool baseline_;
  bool debuggable_;
  bool generate_debug_info_;
  bool generate_mini_debug_info_;
  bool generate_build_id_;
  bool implicit_null_checks_;
  bool implicit_so_checks_;
  bool implicit_suspend_checks_;
  bool compile_pic_;
  bool dump_timings_;
  bool dump_pass_timings_;
  bool dump_stats_;
  bool profile_branches_;

  // Info for profile guided compilation.
  const ProfileCompilationInfo* profile_compilation_info_;

  // Vector of methods to have verbose output enabled for.
  std::vector<std::string> verbose_methods_;

  // Abort compilation with an error if we find a class that fails verification with a hard
  // failure.
  bool abort_on_hard_verifier_failure_;
  // Same for soft failures.
  bool abort_on_soft_verifier_failure_;

  // Log initialization of initialization failures to this stream if not null.
  std::unique_ptr<std::ostream> init_failure_output_;

  std::string dump_cfg_file_name_;
  bool dump_cfg_append_;

  // Whether the compiler should trade performance for determinism to guarantee exactly reproducible
  // outcomes.
  bool force_determinism_;

  // Whether the compiler should check for violation of the conditions required to perform AOT
  // "linkage".
  bool check_linkage_conditions_;
  // Whether the compiler should crash when encountering a violation of one of
  // the conditions required to perform AOT "linkage".
  bool crash_on_linkage_violation_;

  // Whether code should be deduplicated.
  bool deduplicate_code_;

  // Whether compiled code should increment the hotness count of ArtMethod. Note that the increments
  // won't be atomic for performance reasons, so we accept races, just like in interpreter.
  bool count_hotness_in_compiled_code_;

  // Whether we eagerly resolve all of the const strings that are loaded from startup methods in the
  // profile.
  bool resolve_startup_const_strings_;

  // Whether we attempt to run class initializers for app image classes.
  bool initialize_app_image_classes_;

  // When running profile-guided compilation, check that methods intended to be compiled end
  // up compiled and are not punted.
  ProfileMethodsCheck check_profiled_methods_;

  // Maximum solid block size in the generated image.
  uint32_t max_image_block_size_;

  RegisterAllocator::Strategy register_allocation_strategy_;

  // If not null, specifies optimization passes which will be run instead of defaults.
  // Note that passes_to_run_ is not checked for correctness and providing an incorrect
  // list of passes can lead to unexpected compiler behaviour. This is caused by dependencies
  // between passes. Failing to satisfy them can for example lead to compiler crashes.
  // Passing pass names which are not recognized by the compiler will result in
  // compiler-dependant behavior.
  const std::vector<std::string>* passes_to_run_;

  friend class Dex2Oat;
  friend class CommonCompilerDriverTest;
  friend class CommonCompilerTestImpl;
  friend class jit::JitCompiler;
  friend class verifier::VerifierDepsTest;
  friend class linker::Arm64RelativePatcherTest;
  friend class linker::Thumb2RelativePatcherTest;

  template <class Base>
  friend bool ReadCompilerOptions(Base& map, CompilerOptions* options, std::string* error_msg);

  DISALLOW_COPY_AND_ASSIGN(CompilerOptions);
};

}  // namespace art

#endif  // ART_COMPILER_DRIVER_COMPILER_OPTIONS_H_
