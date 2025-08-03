/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <stdio.h>
#include <xmmintrin.h>

#include <algorithm>
#include <iterator>
#include <memory>
#include <optional>
#include <string>
#include <tuple>
#include <type_traits>
#include <vector>

#include "berberis/base/checks.h"
#include "berberis/base/config.h"
#include "berberis/intrinsics/common_to_x86/intrinsics_bindings.h"
#include "berberis/intrinsics/intrinsics_args.h"
#include "berberis/intrinsics/intrinsics_float.h"
#include "berberis/intrinsics/macro_assembler.h"
#include "berberis/intrinsics/simd_register.h"
#include "berberis/intrinsics/type_traits.h"

#include "text_assembler.h"

namespace berberis {

namespace constants_pool {

// Note: kBerberisMacroAssemblerConstantsRelocated is the same as original,
// unrelocated version in 32-bit world.  But in 64-bit world it's copy on the first 2GiB.
//
// Our builder could be built as 64-bit binary thus we must not mix them.
//
// Note: we have CHECK_*_LAYOUT tests in macro_assembler_common_x86.cc to make sure
// offsets produced by 64-bit builder are usable in 32-bit libberberis.so

extern const int32_t kBerberisMacroAssemblerConstantsRelocated;

int32_t GetOffset(int32_t address) {
  return address - constants_pool::kBerberisMacroAssemblerConstantsRelocated;
}

}  // namespace constants_pool

template <typename AsmCallInfo>
void GenerateOutputVariables(FILE* out, int indent);
template <typename AsmCallInfo>
void GenerateTemporaries(FILE* out, int indent);
template <typename AsmCallInfo>
void GenerateInShadows(FILE* out, int indent);
template <typename AsmCallInfo>
void AssignRegisterNumbers(int* register_numbers);
template <typename AsmCallInfo>
auto CallTextAssembler(FILE* out, int indent, int* register_numbers);
template <typename AsmCallInfo>
void GenerateAssemblerOuts(FILE* out, int indent);
template <typename AsmCallInfo>
void GenerateAssemblerIns(FILE* out,
                          int indent,
                          int* register_numbers,
                          bool need_gpr_macroassembler_scratch,
                          bool need_gpr_macroassembler_constants);
template <typename AsmCallInfo>
void GenerateOutShadows(FILE* out, int indent);
template <typename AsmCallInfo>
void GenerateElementsList(FILE* out,
                          int indent,
                          const std::string& prefix,
                          const std::string& suffix,
                          const std::vector<std::string>& elements);
template <typename AsmCallInfo, typename Arg>
constexpr bool NeedInputShadow(Arg arg);
template <typename AsmCallInfo, typename Arg>
constexpr bool NeedOutputShadow(Arg arg);

template <typename AsmCallInfo>
void GenerateFunctionHeader(FILE* out, int indent) {
  if (strchr(AsmCallInfo::kIntrinsic, '<')) {
    fprintf(out, "template <>\n");
  }
  std::string prefix;
  if constexpr (std::tuple_size_v<typename AsmCallInfo::OutputArguments> == 0) {
    prefix = "inline void " + std::string(AsmCallInfo::kIntrinsic) + "(";
  } else {
    const char* prefix_of_prefix = "inline std::tuple<";
    for (const char* type_name : AsmCallInfo::OutputArgumentsTypeNames) {
      prefix += prefix_of_prefix + std::string(type_name);
      prefix_of_prefix = ", ";
    }
    prefix += "> " + std::string(AsmCallInfo::kIntrinsic) + "(";
  }
  std::vector<std::string> ins;
  for (const char* type_name : AsmCallInfo::InputArgumentsTypeNames) {
    ins.push_back(std::string(type_name) + " in" + std::to_string(ins.size()));
  }
  GenerateElementsList<AsmCallInfo>(out, indent, prefix, ") {", ins);
  fprintf(out,
          "  [[maybe_unused]]  alignas(berberis::config::kScratchAreaAlign)"
          " uint8_t scratch[berberis::config::kScratchAreaSize];\n");
  fprintf(out,
          "  [[maybe_unused]] auto& scratch2 ="
          " scratch[berberis::config::kScratchAreaSlotSize];\n");
}

template <typename AsmCallInfo>
void GenerateFunctionBody(FILE* out, int indent) {
  // Declare out variables.
  GenerateOutputVariables<AsmCallInfo>(out, indent);
  // Declare temporary variables.
  GenerateTemporaries<AsmCallInfo>(out, indent);
  // We need "shadow variables" for ins of types: Float32, Float64 and SIMD128Register.
  // This is because assembler does not accept these arguments for XMMRegisters and
  // we couldn't use "float"/"double" function arguments because if ABI issues.
  GenerateInShadows<AsmCallInfo>(out, indent);
  // Even if we don't pass any registers we need to allocate at least one element.
  int register_numbers[std::tuple_size_v<typename AsmCallInfo::Bindings> == 0
                           ? 1
                           : std::tuple_size_v<typename AsmCallInfo::Bindings>];
  // Assign numbers to registers - we need to pass them to assembler and then, later,
  // to Generator of Input Variable line.
  AssignRegisterNumbers<AsmCallInfo>(register_numbers);
  // Print opening line for asm call.
  if constexpr (AsmCallInfo::kSideEffects) {
    fprintf(out, "%*s__asm__ __volatile__(\n", indent, "");
  } else {
    fprintf(out, "%*s__asm__(\n", indent, "");
  }
  // Call text assembler to produce the body of an asm call.
  auto [need_gpr_macroassembler_scratch, need_gpr_macroassembler_constants] =
      CallTextAssembler<AsmCallInfo>(out, indent, register_numbers);
  // Assembler instruction outs.
  GenerateAssemblerOuts<AsmCallInfo>(out, indent);
  // Assembler instruction ins.
  GenerateAssemblerIns<AsmCallInfo>(out,
                                    indent,
                                    register_numbers,
                                    need_gpr_macroassembler_scratch,
                                    need_gpr_macroassembler_constants);
  // Close asm call.
  fprintf(out, "%*s);\n", indent, "");
  // Generate copies from shadows to outputs.
  GenerateOutShadows<AsmCallInfo>(out, indent);
  // Return value from function.
  if constexpr (std::tuple_size_v<typename AsmCallInfo::OutputArguments> > 0) {
    std::vector<std::string> outs;
    for (std::size_t id = 0; id < std::tuple_size_v<typename AsmCallInfo::OutputArguments>; ++id) {
      outs.push_back("out" + std::to_string(id));
    }
    GenerateElementsList<AsmCallInfo>(out, indent, "return {", "};", outs);
  }
}

template <typename AsmCallInfo>
void GenerateOutputVariables(FILE* out, int indent) {
  std::size_t id = 0;
  for (const char* type_name : AsmCallInfo::OutputArgumentsTypeNames) {
    fprintf(out, "%*s%s out%zd;\n", indent, "", type_name, id++);
  }
}

template <typename AsmCallInfo>
void GenerateTemporaries(FILE* out, int indent) {
  std::size_t id = 0;
  AsmCallInfo::ProcessBindings([out, &id, indent](auto arg) {
    using RegisterClass = typename decltype(arg)::RegisterClass;
    if constexpr (!std::is_same_v<RegisterClass, intrinsics::bindings::FLAGS>) {
      if constexpr (!HaveInput(arg.arg_info) && !HaveOutput(arg.arg_info)) {
        static_assert(
            std::is_same_v<typename decltype(arg)::Usage, intrinsics::bindings::Def> ||
            std::is_same_v<typename decltype(arg)::Usage, intrinsics::bindings::DefEarlyClobber>);
        fprintf(out,
                "%*s%s tmp%zd;\n",
                indent,
                "",
                TypeTraits<typename RegisterClass::Type>::kName,
                id++);
      }
    }
  });
}

template <typename AsmCallInfo>
void GenerateInShadows(FILE* out, int indent) {
  AsmCallInfo::ProcessBindings([out, indent](auto arg) {
    using RegisterClass = typename decltype(arg)::RegisterClass;
    if constexpr (RegisterClass::kAsRegister == 'm') {
      // Only temporary memory scratch area is supported.
      static_assert(!HaveInput(arg.arg_info) && !HaveOutput(arg.arg_info));
    } else if constexpr (RegisterClass::kAsRegister == 'r') {
      // TODO(b/138439904): remove when clang handling of 'r' constraint would be fixed.
      if constexpr (NeedInputShadow<AsmCallInfo>(arg)) {
        fprintf(out, "%2$*1$suint32_t in%3$d_shadow = in%3$d;\n", indent, "", arg.arg_info.from);
      }
      if constexpr (NeedOutputShadow<AsmCallInfo>(arg)) {
        fprintf(out, "%*suint32_t out%d_shadow;\n", indent, "", arg.arg_info.to);
      }
    } else if constexpr (RegisterClass::kAsRegister == 'x') {
      if constexpr (HaveInput(arg.arg_info)) {
        using Type = std::tuple_element_t<arg.arg_info.from, typename AsmCallInfo::InputArguments>;
        const char* type_name = TypeTraits<Type>::kName;
        const char* xmm_type_name;
        const char* expanded = "";
        // Types allowed for 'x' restriction are float, double and __m128/__m128i/__m128d
        // First two work for {,u}int32_t and {,u}int64_t, but small integer types must be expanded.
        if constexpr (std::is_integral_v<Type> && sizeof(Type) < sizeof(int32_t)) {
          fprintf(
              out, "%2$*1$suint32_t in%3$d_expanded = in%3$d;\n", indent, "", arg.arg_info.from);
          type_name = TypeTraits<uint32_t>::kName;
          xmm_type_name =
              TypeTraits<typename TypeTraits<typename TypeTraits<uint32_t>::Float>::Raw>::kName;
          expanded = "_expanded";
        } else if constexpr (std::is_integral_v<Type>) {
          // {,u}int32_t and {,u}int64_t have to be converted to float/double.
          xmm_type_name =
              TypeTraits<typename TypeTraits<typename TypeTraits<Type>::Float>::Raw>::kName;
        } else {
          // Float32/Float64 can not be used, we need to use raw float/double.
          xmm_type_name = TypeTraits<typename TypeTraits<Type>::Raw>::kName;
        }
        fprintf(out, "%*s%s in%d_shadow;\n", indent, "", xmm_type_name, arg.arg_info.from);
        fprintf(out,
                "%*sstatic_assert(sizeof(%s) == sizeof(%s));\n",
                indent,
                "",
                type_name,
                xmm_type_name);
        // Note: it's not safe to use bit_cast here till we have std::bit_cast from C++20.
        // If optimizer wouldn't be enabled (e.g. if code is compiled with -O0) then bit_cast
        // would use %st on 32-bit platform which destroys NaNs.
        fprintf(out,
                "%2$*1$smemcpy(&in%3$d_shadow, &in%3$d%4$s, sizeof(%5$s));\n",
                indent,
                "",
                arg.arg_info.from,
                expanded,
                xmm_type_name);
      }
      if constexpr (HaveOutput(arg.arg_info)) {
        using Type = std::tuple_element_t<arg.arg_info.to, typename AsmCallInfo::OutputArguments>;
        const char* xmm_type_name;
        // {,u}int32_t and {,u}int64_t have to be converted to float/double.
        if constexpr (std::is_integral_v<Type>) {
          xmm_type_name =
              TypeTraits<typename TypeTraits<typename TypeTraits<Type>::Float>::Raw>::kName;
        } else {
          // Float32/Float64 can not be used, we need to use raw float/double.
          xmm_type_name = TypeTraits<typename TypeTraits<Type>::Raw>::kName;
        }
        fprintf(out, "%*s%s out%d_shadow;\n", indent, "", xmm_type_name, arg.arg_info.to);
      }
    }
  });
}

template <typename AsmCallInfo>
void AssignRegisterNumbers(int* register_numbers) {
  // Assign number for output (and temporary) arguments.
  std::size_t id = 0;
  int arg_counter = 0;
  AsmCallInfo::ProcessBindings([&id, &arg_counter, &register_numbers](auto arg) {
    using RegisterClass = typename decltype(arg)::RegisterClass;
    if constexpr (!std::is_same_v<RegisterClass, intrinsics::bindings::FLAGS>) {
      if constexpr (!std::is_same_v<typename decltype(arg)::Usage, intrinsics::bindings::Use>) {
        register_numbers[arg_counter] = id++;
      }
      ++arg_counter;
    }
  });
  // Assign numbers for input arguments.
  arg_counter = 0;
  AsmCallInfo::ProcessBindings([&id, &arg_counter, &register_numbers](auto arg) {
    using RegisterClass = typename decltype(arg)::RegisterClass;
    if constexpr (!std::is_same_v<RegisterClass, intrinsics::bindings::FLAGS>) {
      if constexpr (std::is_same_v<typename decltype(arg)::Usage, intrinsics::bindings::Use>) {
        register_numbers[arg_counter] = id++;
      }
      ++arg_counter;
    }
  });
}

template <typename AsmCallInfo>
auto CallTextAssembler(FILE* out, int indent, int* register_numbers) {
  MacroAssembler<TextAssembler> as(indent, out);
  int arg_counter = 0;
  AsmCallInfo::ProcessBindings([&arg_counter, &as, register_numbers](auto arg) {
    using RegisterClass = typename decltype(arg)::RegisterClass;
    if constexpr (!std::is_same_v<RegisterClass, intrinsics::bindings::FLAGS>) {
      if constexpr (RegisterClass::kAsRegister != 'm') {
        if constexpr (RegisterClass::kIsImplicitReg) {
          if constexpr (RegisterClass::kAsRegister == 'a') {
            as.gpr_a = TextAssembler::Register(register_numbers[arg_counter]);
          } else if constexpr (RegisterClass::kAsRegister == 'c') {
            as.gpr_c = TextAssembler::Register(register_numbers[arg_counter]);
          } else {
            static_assert(RegisterClass::kAsRegister == 'd');
            as.gpr_d = TextAssembler::Register(register_numbers[arg_counter]);
          }
        }
      }
      ++arg_counter;
    }
  });
  as.gpr_macroassembler_constants = TextAssembler::Register(arg_counter);
  arg_counter = 0;
  int scratch_counter = 0;
  std::apply(AsmCallInfo::kMacroInstruction,
             std::tuple_cat(
                 std::tuple<MacroAssembler<TextAssembler>&>{as},
                 AsmCallInfo::MakeTuplefromBindings(
                     [&as, &arg_counter, &scratch_counter, register_numbers](auto arg) {
                       using RegisterClass = typename decltype(arg)::RegisterClass;
                       if constexpr (!std::is_same_v<RegisterClass, intrinsics::bindings::FLAGS>) {
                         if constexpr (RegisterClass::kAsRegister == 'm') {
                           if (scratch_counter == 0) {
                             as.gpr_macroassembler_scratch = TextAssembler::Register(arg_counter++);
                           } else if (scratch_counter == 1) {
                             as.gpr_macroassembler_scratch2 =
                                 TextAssembler::Register(arg_counter++);
                           } else {
                             FATAL("Only two scratch registers are supported for now");
                           }
                           // Note: as.gpr_scratch in combination with offset is treated by text
                           // assembler specially.  We rely on offset set here to be the same as
                           // scratch2 address in scratch buffer.
                           return std::tuple{TextAssembler::Operand{
                               .base = as.gpr_scratch,
                               .disp = static_cast<int32_t>(config::kScratchAreaSlotSize *
                                                            scratch_counter++)}};
                         } else if constexpr (RegisterClass::kIsImplicitReg) {
                           ++arg_counter;
                           return std::tuple{};
                         } else {
                           return std::tuple{register_numbers[arg_counter++]};
                         }
                       } else {
                         return std::tuple{};
                       }
                     })));
  // Verify CPU vendor and SSE restrictions.
  bool expect_avx = false;
  bool expect_bmi = false;
  bool expect_fma = false;
  bool expect_fma4 = false;
  bool expect_lzcnt = false;
  bool expect_popcnt = false;
  bool expect_sse3 = false;
  bool expect_ssse3 = false;
  bool expect_sse4_1 = false;
  bool expect_sse4_2 = false;
  switch (AsmCallInfo::kCPUIDRestriction) {
    case intrinsics::bindings::kHasBMI:
      expect_bmi = true;
      break;
    case intrinsics::bindings::kHasLZCNT:
      expect_lzcnt = true;
      break;
    case intrinsics::bindings::kHasPOPCNT:
      expect_popcnt = true;
      break;
    case intrinsics::bindings::kHasFMA:
    case intrinsics::bindings::kHasFMA4:
      if (AsmCallInfo::kCPUIDRestriction == intrinsics::bindings::kHasFMA) {
        expect_fma = true;
      } else {
        expect_fma4 = true;
      }
      [[fallthrough]];
    case intrinsics::bindings::kHasAVX:
      expect_avx = true;
      [[fallthrough]];
    case intrinsics::bindings::kHasSSE4_2:
      expect_sse4_2 = true;
      [[fallthrough]];
    case intrinsics::bindings::kHasSSE4_1:
      expect_sse4_1 = true;
      [[fallthrough]];
    case intrinsics::bindings::kHasSSSE3:
      expect_ssse3 = true;
      [[fallthrough]];
    case intrinsics::bindings::kHasSSE3:
      expect_sse3 = true;
      [[fallthrough]];
    case intrinsics::bindings::kIsAuthenticAMD:
    case intrinsics::bindings::kNoCPUIDRestriction:;  // Do nothing - make compiler happy.
  }
  CHECK_EQ(expect_avx, as.need_avx);
  CHECK_EQ(expect_bmi, as.need_bmi);
  CHECK_EQ(expect_fma, as.need_fma);
  CHECK_EQ(expect_fma4, as.need_fma4);
  CHECK_EQ(expect_lzcnt, as.need_lzcnt);
  CHECK_EQ(expect_popcnt, as.need_popcnt);
  CHECK_EQ(expect_sse3, as.need_sse3);
  CHECK_EQ(expect_ssse3, as.need_ssse3);
  CHECK_EQ(expect_sse4_1, as.need_sse4_1);
  CHECK_EQ(expect_sse4_2, as.need_sse4_2);
  return std::tuple{as.need_gpr_macroassembler_scratch(), as.need_gpr_macroassembler_constants()};
}

template <typename AsmCallInfo>
void GenerateAssemblerOuts(FILE* out, int indent) {
  std::vector<std::string> outs;
  int tmp_id = 0;
  AsmCallInfo::ProcessBindings([&outs, &tmp_id](auto arg) {
    using RegisterClass = typename decltype(arg)::RegisterClass;
    if constexpr (!std::is_same_v<RegisterClass, intrinsics::bindings::FLAGS> &&
                  !std::is_same_v<typename decltype(arg)::Usage, intrinsics::bindings::Use>) {
      std::string out = "\"=";
      if constexpr (std::is_same_v<typename decltype(arg)::Usage,
                                   intrinsics::bindings::DefEarlyClobber>) {
        out += "&";
      }
      out += RegisterClass::kAsRegister;
      if constexpr (HaveOutput(arg.arg_info)) {
        bool need_shadow = NeedOutputShadow<AsmCallInfo>(arg);
        out += "\"(out" + std::to_string(arg.arg_info.to) + (need_shadow ? "_shadow)" : ")");
      } else if constexpr (HaveInput(arg.arg_info)) {
        bool need_shadow = NeedInputShadow<AsmCallInfo>(arg);
        out += "\"(in" + std::to_string(arg.arg_info.from) + (need_shadow ? "_shadow)" : ")");
      } else {
        out += "\"(tmp" + std::to_string(tmp_id++) + ")";
      }
      outs.push_back(out);
    }
  });
  GenerateElementsList<AsmCallInfo>(out, indent, "  : ", "", outs);
}

template <typename AsmCallInfo>
void GenerateAssemblerIns(FILE* out,
                          int indent,
                          int* register_numbers,
                          bool need_gpr_macroassembler_scratch,
                          bool need_gpr_macroassembler_constants) {
  std::vector<std::string> ins;
  AsmCallInfo::ProcessBindings([&ins](auto arg) {
    using RegisterClass = typename decltype(arg)::RegisterClass;
    if constexpr (!std::is_same_v<RegisterClass, intrinsics::bindings::FLAGS> &&
                  std::is_same_v<typename decltype(arg)::Usage, intrinsics::bindings::Use>) {
      ins.push_back("\"" + std::string(1, RegisterClass::kAsRegister) + "\"(in" +
                    std::to_string(arg.arg_info.from) +
                    (NeedInputShadow<AsmCallInfo>(arg) ? "_shadow)" : ")"));
    }
  });
  if (need_gpr_macroassembler_scratch) {
    ins.push_back("\"m\"(scratch), \"m\"(scratch2)");
  }
  if (need_gpr_macroassembler_constants) {
    ins.push_back(
        "\"m\"(*reinterpret_cast<const char*>(&constants_pool::kBerberisMacroAssemblerConstants))");
  }
  int arg_counter = 0;
  AsmCallInfo::ProcessBindings([&ins, &arg_counter, register_numbers](auto arg) {
    using RegisterClass = typename decltype(arg)::RegisterClass;
    if constexpr (!std::is_same_v<RegisterClass, intrinsics::bindings::FLAGS>) {
      if constexpr (HaveInput(arg.arg_info) &&
                    !std::is_same_v<typename decltype(arg)::Usage, intrinsics::bindings::Use>) {
        ins.push_back("\"" + std::to_string(register_numbers[arg_counter]) + "\"(in" +
                      std::to_string(arg.arg_info.from) +
                      (NeedInputShadow<AsmCallInfo>(arg) ? "_shadow)" : ")"));
      }
      ++arg_counter;
    }
  });
  GenerateElementsList<AsmCallInfo>(out, indent, "  : ", "", ins);
}

template <typename AsmCallInfo>
void GenerateOutShadows(FILE* out, int indent) {
  AsmCallInfo::ProcessBindings([out, indent](auto arg) {
    using RegisterClass = typename decltype(arg)::RegisterClass;
    if constexpr (RegisterClass::kAsRegister == 'r') {
      // TODO(b/138439904): remove when clang handling of 'r' constraint would be fixed.
      if constexpr (HaveOutput(arg.arg_info)) {
        using Type = std::tuple_element_t<arg.arg_info.to, typename AsmCallInfo::OutputArguments>;
        if constexpr (sizeof(Type) == sizeof(uint8_t)) {
          fprintf(out, "%2$*1$sout%3$d = out%3$d_shadow;\n", indent, "", arg.arg_info.to);
        }
      }
    } else if constexpr (RegisterClass::kAsRegister == 'x') {
      if constexpr (HaveOutput(arg.arg_info)) {
        using Type = std::tuple_element_t<arg.arg_info.to, typename AsmCallInfo::OutputArguments>;
        const char* type_name = TypeTraits<Type>::kName;
        const char* xmm_type_name;
        // {,u}int32_t and {,u}int64_t have to be converted to float/double.
        if constexpr (std::is_integral_v<Type>) {
          xmm_type_name =
              TypeTraits<typename TypeTraits<typename TypeTraits<Type>::Float>::Raw>::kName;
        } else {
          // Float32/Float64 can not be used, we need to use raw float/double.
          xmm_type_name = TypeTraits<typename TypeTraits<Type>::Raw>::kName;
        }
        fprintf(out,
                "%*sstatic_assert(sizeof(%s) == sizeof(%s));\n",
                indent,
                "",
                type_name,
                xmm_type_name);
        // Note: it's not safe to use bit_cast here till we have std::bit_cast from C++20.
        // If optimizer wouldn't be enabled (e.g. if code is compiled with -O0) then bit_cast
        // would use %st on 32-bit platform which destroys NaNs.
        fprintf(out,
                "%2$*1$smemcpy(&out%3$d, &out%3$d_shadow, sizeof(%4$s));\n",
                indent,
                "",
                arg.arg_info.to,
                xmm_type_name);
      }
    }
  });
}

template <typename AsmCallInfo>
void GenerateElementsList(FILE* out,
                          int indent,
                          const std::string& prefix,
                          const std::string& suffix,
                          const std::vector<std::string>& elements) {
  std::size_t length = prefix.length() + suffix.length();
  if (elements.size() == 0) {
    fprintf(out, "%*s%s%s\n", indent, "", prefix.c_str(), suffix.c_str());
    return;
  }
  for (const auto& element : elements) {
    length += element.length() + 2;
  }
  for (const auto& element : elements) {
    if (&element == &elements[0]) {
      fprintf(out, "%*s%s%s", indent, "", prefix.c_str(), element.c_str());
    } else {
      if (length <= 102) {
        fprintf(out, ", %s", element.c_str());
      } else {
        fprintf(out, ",\n%*s%s", static_cast<int>(prefix.length()) + indent, "", element.c_str());
      }
    }
  }
  fprintf(out, "%s\n", suffix.c_str());
}

template <typename AsmCallInfo, typename Arg>
constexpr bool NeedInputShadow(Arg arg) {
  using RegisterClass = typename Arg::RegisterClass;
  // Without shadow clang silently converts 'r' restriction into 'q' restriction which
  // is wrong: if %ah or %bh is picked we would produce incorrect result here.
  // TODO(b/138439904): remove when clang handling of 'r' constraint would be fixed.
  if constexpr (RegisterClass::kAsRegister == 'r' && HaveInput(arg.arg_info)) {
    // Only 8-bit registers are special because each 16-bit registers include two of them
    // (%al/%ah, %cl/%ch, %dl/%dh, %bl/%bh).
    // Mix of 16-bit and 64-bit registers doesn't trigger bug in Clang.
    if constexpr (sizeof(std::tuple_element_t<arg.arg_info.from,
                                              typename AsmCallInfo::InputArguments>) ==
                  sizeof(uint8_t)) {
      return true;
    }
  } else if constexpr (RegisterClass::kAsRegister == 'x') {
    return true;
  }
  return false;
}

template <typename AsmCallInfo, typename Arg>
constexpr bool NeedOutputShadow(Arg arg) {
  using RegisterClass = typename Arg::RegisterClass;
  // Without shadow clang silently converts 'r' restriction into 'q' restriction which
  // is wrong: if %ah or %bh is picked we would produce incorrect result here.
  // TODO(b/138439904): remove when clang handling of 'r' constraint would be fixed.
  if constexpr (RegisterClass::kAsRegister == 'r' && HaveOutput(arg.arg_info)) {
    // Only 8-bit registers are special because each some 16-bit registers include two of
    // them (%al/%ah, %cl/%ch, %dl/%dh, %bl/%bh).
    // Mix of 16-bit and 64-bit registers don't trigger bug in Clang.
    if constexpr (sizeof(std::tuple_element_t<arg.arg_info.to,
                                              typename AsmCallInfo::OutputArguments>) ==
                  sizeof(uint8_t)) {
      return true;
    }
  } else if constexpr (RegisterClass::kAsRegister == 'x') {
    return true;
  }
  return false;
}

#include "text_asm_intrinsics_process_bindings-inl.h"

void GenerateTextAsmIntrinsics(FILE* out) {
  intrinsics::bindings::CPUIDRestriction cpuid_restriction =
      intrinsics::bindings::kNoCPUIDRestriction;
  bool if_opened = false;
  std::string running_name;
  ProcessAllBindings<TextAssemblerX86<TextAssembler>,
                     TextAssembler,
                     MacroAssembler<TextAssembler>::MacroAssemblers>(
      [&running_name, &if_opened, &cpuid_restriction, out](auto&& asm_call_generator) {
        using AsmCallInfo = std::decay_t<decltype(asm_call_generator)>;
        std::string full_name = std::string(asm_call_generator.kIntrinsic,
                                            std::strlen(asm_call_generator.kIntrinsic) - 1) +
                                ", kUseCppImplementation>";
        if (size_t arguments_count = std::tuple_size_v<typename AsmCallInfo::InputArguments>) {
          full_name += "(in0";
          for (size_t i = 1; i < arguments_count; ++i) {
            full_name += ", in" + std::to_string(i);
          }
          full_name += ")";
        } else {
          full_name += "()";
        }
        if (full_name != running_name) {
          if (if_opened) {
            if (cpuid_restriction != intrinsics::bindings::kNoCPUIDRestriction) {
              fprintf(out, "  } else {\n    return %s;\n", running_name.c_str());
              cpuid_restriction = intrinsics::bindings::kNoCPUIDRestriction;
            }
            if_opened = false;
            fprintf(out, "  }\n");
          }
          // Final line of function.
          if (!running_name.empty()) {
            fprintf(out, "};\n\n");
          }
          GenerateFunctionHeader<AsmCallInfo>(out, 0);
          running_name = full_name;
        }
        if (asm_call_generator.kCPUIDRestriction != cpuid_restriction) {
          if (asm_call_generator.kCPUIDRestriction == intrinsics::bindings::kNoCPUIDRestriction) {
            fprintf(out, "  } else {\n");
          } else {
            if (if_opened) {
              fprintf(out, "  } else if (");
            } else {
              fprintf(out, "  if (");
              if_opened = true;
            }
            switch (asm_call_generator.kCPUIDRestriction) {
              default:
                // Unsupported CPUID value.
                CHECK(false);
              case intrinsics::bindings::kIsAuthenticAMD:
                fprintf(out, "host_platform::kIsAuthenticAMD");
                break;
              case intrinsics::bindings::kHasAVX:
                fprintf(out, "host_platform::kHasAVX");
                break;
              case intrinsics::bindings::kHasBMI:
                fprintf(out, "host_platform::kHasBMI");
                break;
              case intrinsics::bindings::kHasFMA:
                fprintf(out, "host_platform::kHasFMA");
                break;
              case intrinsics::bindings::kHasFMA4:
                fprintf(out, "host_platform::kHasFMA4");
                break;
              case intrinsics::bindings::kHasLZCNT:
                fprintf(out, "host_platform::kHasLZCNT");
                break;
              case intrinsics::bindings::kHasPOPCNT:
                fprintf(out, "host_platform::kHasPOPCNT");
                break;
              case intrinsics::bindings::kHasSSE3:
                fprintf(out, "host_platform::kHasSSE3");
                break;
              case intrinsics::bindings::kHasSSSE3:
                fprintf(out, "host_platform::kHasSSSE3");
                break;
              case intrinsics::bindings::kHasSSE4_1:
                fprintf(out, "host_platform::kHasSSE4_1");
                break;
              case intrinsics::bindings::kHasSSE4_2:
                fprintf(out, "host_platform::kHasSSE4_2");
                break;
              case intrinsics::bindings::kNoCPUIDRestriction:;  // Do nothing - make compiler happy.
            }
            fprintf(out, ") {\n");
          }
          cpuid_restriction = asm_call_generator.kCPUIDRestriction;
        }
        GenerateFunctionBody<AsmCallInfo>(out, 2 + 2 * if_opened);
      });
  if (if_opened) {
    fprintf(out, "  }\n");
  }
  // Final line of function.
  fprintf(out, "};\n\n");
}

}  // namespace berberis

int main(int argc, char* argv[]) {
  FILE* out = argc > 1 ? fopen(argv[1], "w") : stdout;
  fprintf(out,
          R"STRING(
// This file automatically generated by make_intrinsics.cc
// DO NOT EDIT!

#ifndef %2$s_%3$s_INTRINSICS_INTRINSICS_H_
#define %2$s_%3$s_INTRINSICS_INTRINSICS_H_

#include <xmmintrin.h>

#include "berberis/base/config.h"
#include "berberis/runtime_primitives/platform.h"
#include "%3$s/intrinsics/%1$s/intrinsics.h"
#include "%3$s/intrinsics/vector_intrinsics.h"

namespace berberis::constants_pool {

struct MacroAssemblerConstants;

extern const MacroAssemblerConstants kBerberisMacroAssemblerConstants
    __attribute__((visibility("hidden")));

}  // namespace berberis::constants_pool

namespace %3$s {

namespace constants_pool {

%4$s

}  // namespace constants_pool

namespace intrinsics {
)STRING",
          berberis::TextAssembler::kArchName,
          berberis::TextAssembler::kArchGuard,
          berberis::TextAssembler::kNamespaceName,
          strcmp(berberis::TextAssembler::kNamespaceName, "berberis")
              ? "using berberis::constants_pool::kBerberisMacroAssemblerConstants;"
              : "");

  berberis::GenerateTextAsmIntrinsics(out);
  berberis::MakeExtraGuestFunctions(out);

  fprintf(out,
          R"STRING(
}  // namespace intrinsics

}  // namespace %2$s

#endif /* %1$s_%2$s_INTRINSICS_INTRINSICS_H_ */
)STRING",
          berberis::TextAssembler::kArchGuard,
          berberis::TextAssembler::kNamespaceName);

  fclose(out);
  return 0;
}
