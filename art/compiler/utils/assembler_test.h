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

#ifndef ART_COMPILER_UTILS_ASSEMBLER_TEST_H_
#define ART_COMPILER_UTILS_ASSEMBLER_TEST_H_

#include "assembler.h"

#include <sys/stat.h>

#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iterator>

#include "base/array_ref.h"
#include "base/macros.h"
#include "base/malloc_arena_pool.h"
#include "assembler_test_base.h"
#include "common_runtime_test.h"  // For ScratchFile

namespace art HIDDEN {

// Helper for a constexpr string length.
constexpr size_t ConstexprStrLen(char const* str, size_t count = 0) {
  return ('\0' == str[0]) ? count : ConstexprStrLen(str+1, count+1);
}

enum class RegisterView {  // private
  kUsePrimaryName,
  kUseSecondaryName,
  kUseTertiaryName,
  kUseQuaternaryName,
};

// For use in the template as the default type to get a nonvector registers version.
struct NoVectorRegs {};

template<typename Ass,
         typename Addr,
         typename Reg,
         typename FPReg,
         typename Imm,
         typename VecReg = NoVectorRegs>
class AssemblerTest : public AssemblerTestBase {
 public:
  Ass* GetAssembler() {
    return assembler_.get();
  }

  using TestFn = std::string (*)(AssemblerTest *, Ass *);

  void DriverFn(TestFn f, const std::string& test_name) {
    DriverWrapper(f(this, assembler_.get()), test_name);
  }

  // This driver assumes the assembler has already been called.
  void DriverStr(const std::string& assembly_string, const std::string& test_name) {
    DriverWrapper(assembly_string, test_name);
  }

  //
  // Register repeats.
  //

  std::string RepeatR(void (Ass::*f)(Reg), const std::string& fmt) {
    return RepeatTemplatedRegister<Reg>(f,
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt);
  }

  std::string Repeatr(void (Ass::*f)(Reg), const std::string& fmt) {
    return RepeatTemplatedRegister<Reg>(f,
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUseSecondaryName>,
        fmt);
  }

  std::string RepeatRR(void (Ass::*f)(Reg, Reg),
                       const std::string& fmt,
                       const std::vector<std::pair<Reg, Reg>>* except = nullptr) {
    return RepeatTemplatedRegisters<Reg, Reg>(f,
        GetRegisters(),
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt,
        except);
  }

  std::string RepeatRRNoDupes(void (Ass::*f)(Reg, Reg), const std::string& fmt) {
    return RepeatTemplatedRegistersNoDupes<Reg, Reg>(f,
        GetRegisters(),
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt);
  }

  std::string Repeatrr(void (Ass::*f)(Reg, Reg),
                       const std::string& fmt,
                       const std::vector<std::pair<Reg, Reg>>* except = nullptr) {
    return RepeatTemplatedRegisters<Reg, Reg>(f,
        GetRegisters(),
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUseSecondaryName>,
        &AssemblerTest::GetRegName<RegisterView::kUseSecondaryName>,
        fmt,
        except);
  }

  std::string Repeatww(void (Ass::*f)(Reg, Reg),
                       const std::string& fmt,
                       const std::vector<std::pair<Reg, Reg>>* except = nullptr) {
    return RepeatTemplatedRegisters<Reg, Reg>(f,
        GetRegisters(),
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUseTertiaryName>,
        &AssemblerTest::GetRegName<RegisterView::kUseTertiaryName>,
        fmt,
        except);
  }

  std::string Repeatbb(void (Ass::*f)(Reg, Reg),
                       const std::string& fmt,
                       const std::vector<std::pair<Reg, Reg>>* except = nullptr) {
    return RepeatTemplatedRegisters<Reg, Reg>(f,
        GetRegisters(),
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUseQuaternaryName>,
        &AssemblerTest::GetRegName<RegisterView::kUseQuaternaryName>,
        fmt,
        except);
  }

  std::string RepeatRRR(void (Ass::*f)(Reg, Reg, Reg), const std::string& fmt) {
    return RepeatTemplatedRegisters<Reg, Reg, Reg>(f,
        GetRegisters(),
        GetRegisters(),
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt);
  }

  std::string Repeatrb(void (Ass::*f)(Reg, Reg),
                       const std::string& fmt,
                       const std::vector<std::pair<Reg, Reg>>* except = nullptr) {
    return RepeatTemplatedRegisters<Reg, Reg>(f,
        GetRegisters(),
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUseSecondaryName>,
        &AssemblerTest::GetRegName<RegisterView::kUseQuaternaryName>,
        fmt,
        except);
  }

  std::string RepeatRr(void (Ass::*f)(Reg, Reg),
                       const std::string& fmt,
                       const std::vector<std::pair<Reg, Reg>>* except = nullptr) {
    return RepeatTemplatedRegisters<Reg, Reg>(f,
        GetRegisters(),
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetRegName<RegisterView::kUseSecondaryName>,
        fmt,
        except);
  }

  std::string RepeatRI(void (Ass::*f)(Reg, const Imm&), size_t imm_bytes, const std::string& fmt) {
    return RepeatRegisterImm<RegisterView::kUsePrimaryName>(f, imm_bytes, fmt);
  }

  std::string RepeatrI(void (Ass::*f)(Reg, const Imm&), size_t imm_bytes, const std::string& fmt) {
    return RepeatRegisterImm<RegisterView::kUseSecondaryName>(f, imm_bytes, fmt);
  }

  std::string RepeatwI(void (Ass::*f)(Reg, const Imm&), size_t imm_bytes, const std::string& fmt) {
    return RepeatRegisterImm<RegisterView::kUseTertiaryName>(f, imm_bytes, fmt);
  }

  std::string RepeatbI(void (Ass::*f)(Reg, const Imm&), size_t imm_bytes, const std::string& fmt) {
    return RepeatRegisterImm<RegisterView::kUseQuaternaryName>(f, imm_bytes, fmt);
  }

  template <typename Reg1, typename Reg2, typename ImmType>
  std::string RepeatTemplatedRegistersImmBits(void (Ass::*f)(Reg1, Reg2, ImmType),
                                              int imm_bits,
                                              ArrayRef<const Reg1> reg1_registers,
                                              ArrayRef<const Reg2> reg2_registers,
                                              std::string (AssemblerTest::*GetName1)(const Reg1&),
                                              std::string (AssemblerTest::*GetName2)(const Reg2&),
                                              const std::string& fmt,
                                              int bias = 0,
                                              int multiplier = 1) {
    std::string str;
    std::vector<int64_t> imms = CreateImmediateValuesBits(abs(imm_bits), (imm_bits > 0));

    for (auto reg1 : reg1_registers) {
      for (auto reg2 : reg2_registers) {
        for (int64_t imm : imms) {
          ImmType new_imm = CreateImmediate(imm);
          if (f != nullptr) {
            (assembler_.get()->*f)(reg1, reg2, new_imm * multiplier + bias);
          }
          std::string base = fmt;

          ReplaceReg(REG1_TOKEN, (this->*GetName1)(reg1), &base);
          ReplaceReg(REG2_TOKEN, (this->*GetName2)(reg2), &base);
          ReplaceImm(imm, bias, multiplier, &base);

          str += base;
          str += "\n";
        }
      }
    }
    return str;
  }

  template <typename Reg1, typename Reg2, typename Reg3, typename ImmType>
  std::string RepeatTemplatedRegistersImmBits(void (Ass::*f)(Reg1, Reg2, Reg3, ImmType),
                                              int imm_bits,
                                              ArrayRef<const Reg1> reg1_registers,
                                              ArrayRef<const Reg2> reg2_registers,
                                              ArrayRef<const Reg3> reg3_registers,
                                              std::string (AssemblerTest::*GetName1)(const Reg1&),
                                              std::string (AssemblerTest::*GetName2)(const Reg2&),
                                              std::string (AssemblerTest::*GetName3)(const Reg3&),
                                              const std::string& fmt,
                                              int bias) {
    std::string str;
    std::vector<int64_t> imms = CreateImmediateValuesBits(abs(imm_bits), (imm_bits > 0));

    for (auto reg1 : reg1_registers) {
      for (auto reg2 : reg2_registers) {
        for (auto reg3 : reg3_registers) {
          for (int64_t imm : imms) {
            ImmType new_imm = CreateImmediate(imm);
            if (f != nullptr) {
              (assembler_.get()->*f)(reg1, reg2, reg3, new_imm + bias);
            }
            std::string base = fmt;

            ReplaceReg(REG1_TOKEN, (this->*GetName1)(reg1), &base);
            ReplaceReg(REG2_TOKEN, (this->*GetName2)(reg2), &base);
            ReplaceReg(REG3_TOKEN, (this->*GetName3)(reg3), &base);
            ReplaceImm(imm, bias, /*multiplier=*/ 1, &base);

            str += base;
            str += "\n";
          }
        }
      }
    }
    return str;
  }

  template <typename ImmType, typename Reg1, typename Reg2>
  std::string RepeatTemplatedImmBitsRegisters(void (Ass::*f)(ImmType, Reg1, Reg2),
                                              ArrayRef<const Reg1> reg1_registers,
                                              ArrayRef<const Reg2> reg2_registers,
                                              std::string (AssemblerTest::*GetName1)(const Reg1&),
                                              std::string (AssemblerTest::*GetName2)(const Reg2&),
                                              int imm_bits,
                                              const std::string& fmt) {
    std::vector<int64_t> imms = CreateImmediateValuesBits(abs(imm_bits), (imm_bits > 0));

    WarnOnCombinations(reg1_registers.size() * reg2_registers.size() * imms.size());

    std::string str;
    for (auto reg1 : reg1_registers) {
      for (auto reg2 : reg2_registers) {
        for (int64_t imm : imms) {
          ImmType new_imm = CreateImmediate(imm);
          if (f != nullptr) {
            (assembler_.get()->*f)(new_imm, reg1, reg2);
          }
          std::string base = fmt;

          ReplaceReg(REG1_TOKEN, (this->*GetName1)(reg1), &base);
          ReplaceReg(REG2_TOKEN, (this->*GetName2)(reg2), &base);
          ReplaceImm(imm, /*bias=*/ 0, /*multiplier=*/ 1, &base);

          str += base;
          str += "\n";
        }
      }
    }
    return str;
  }

  template <typename RegType, typename ImmType>
  std::string RepeatTemplatedRegisterImmBits(void (Ass::*f)(RegType, ImmType),
                                             int imm_bits,
                                             ArrayRef<const RegType> registers,
                                             std::string (AssemblerTest::*GetName)(const RegType&),
                                             const std::string& fmt,
                                             int bias) {
    std::string str;
    std::vector<int64_t> imms = CreateImmediateValuesBits(abs(imm_bits), (imm_bits > 0));

    for (auto reg : registers) {
      for (int64_t imm : imms) {
        ImmType new_imm = CreateImmediate(imm);
        if (f != nullptr) {
          (assembler_.get()->*f)(reg, new_imm + bias);
        }
        std::string base = fmt;

        ReplaceReg(REG_TOKEN, (this->*GetName)(reg), &base);
        ReplaceImm(imm, bias, /*multiplier=*/ 1, &base);

        str += base;
        str += "\n";
      }
    }
    return str;
  }

  template <typename RegType, typename ImmType>
  std::string RepeatTemplatedRegisterImmBitsShift(
      void (Ass::*f)(RegType, ImmType),
      int imm_bits,
      int shift,
      ArrayRef<const RegType> registers,
      std::string (AssemblerTest::*GetName)(const RegType&),
      const std::string& fmt,
      int bias) {
    std::string str;
    std::vector<int64_t> imms = CreateImmediateValuesBits(abs(imm_bits), (imm_bits > 0), shift);

    for (auto reg : registers) {
      for (int64_t imm : imms) {
        ImmType new_imm = CreateImmediate(imm);
        if (f != nullptr) {
          (assembler_.get()->*f)(reg, new_imm + bias);
        }
        std::string base = fmt;

        ReplaceReg(REG_TOKEN, (this->*GetName)(reg), &base);
        ReplaceImm(imm, bias, /*multiplier=*/ 1, &base);

        str += base;
        str += "\n";
      }
    }
    return str;
  }

  template <typename ImmType>
  std::string RepeatTemplatedImmBitsShift(
      void (Ass::*f)(ImmType), int imm_bits, int shift, const std::string& fmt, int bias = 0) {
    std::vector<int64_t> imms = CreateImmediateValuesBits(abs(imm_bits), (imm_bits > 0), shift);

    WarnOnCombinations(imms.size());

    std::string str;

    for (int64_t imm : imms) {
      ImmType new_imm = CreateImmediate(imm);
      if (f != nullptr) {
        (assembler_.get()->*f)(new_imm + bias);
      }
      std::string base = fmt;

      ReplaceImm(imm, bias, /*multiplier=*/ 1, &base);

      str += base;
      str += "\n";
    }
    return str;
  }

  template <typename Reg1, typename Reg2, typename ImmType>
  std::string RepeatTemplatedRegistersImmBitsShift(
      void (Ass::*f)(Reg1, Reg2, ImmType),
      int imm_bits,
      int shift,
      ArrayRef<const Reg1> reg1_registers,
      ArrayRef<const Reg2> reg2_registers,
      std::string (AssemblerTest::*GetName1)(const Reg1&),
      std::string (AssemblerTest::*GetName2)(const Reg2&),
      const std::string& fmt,
      int bias = 0,
      int multiplier = 1) {
    std::string str;
    std::vector<int64_t> imms = CreateImmediateValuesBits(abs(imm_bits), (imm_bits > 0), shift);

    for (auto reg1 : reg1_registers) {
      for (auto reg2 : reg2_registers) {
        for (int64_t imm : imms) {
          ImmType new_imm = CreateImmediate(imm);
          if (f != nullptr) {
            (assembler_.get()->*f)(reg1, reg2, new_imm * multiplier + bias);
          }
          std::string base = fmt;

          ReplaceReg(REG1_TOKEN, (this->*GetName1)(reg1), &base);
          ReplaceReg(REG2_TOKEN, (this->*GetName2)(reg2), &base);
          ReplaceImm(imm, bias, multiplier, &base);

          str += base;
          str += "\n";
        }
      }
    }
    return str;
  }

  template <typename ImmType>
  std::string RepeatIbS(
      void (Ass::*f)(ImmType), int imm_bits, int shift, const std::string& fmt, int bias = 0) {
    return RepeatTemplatedImmBitsShift<ImmType>(f, imm_bits, shift, fmt, bias);
  }

  template <typename ImmType>
  std::string RepeatRIbS(
      void (Ass::*f)(Reg, ImmType), int imm_bits, int shift, const std::string& fmt, int bias = 0) {
    return RepeatTemplatedRegisterImmBitsShift<Reg, ImmType>(
        f,
        imm_bits,
        shift,
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt,
        bias);
  }

  template <typename ImmType>
  std::string RepeatRRIbS(void (Ass::*f)(Reg, Reg, ImmType),
                          int imm_bits,
                          int shift,
                          const std::string& fmt,
                          int bias = 0) {
    return RepeatTemplatedRegistersImmBitsShift<Reg, Reg, ImmType>(
        f,
        imm_bits,
        shift,
        GetRegisters(),
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt,
        bias);
  }

  template <typename ImmType>
  std::string RepeatRRIb(void (Ass::*f)(Reg, Reg, ImmType),
                         int imm_bits,
                         const std::string& fmt,
                         int bias = 0) {
    return RepeatTemplatedRegistersImmBits<Reg, Reg, ImmType>(f,
        imm_bits,
        GetRegisters(),
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt,
        bias);
  }

  template <typename ImmType>
  std::string RepeatRRRIb(void (Ass::*f)(Reg, Reg, Reg, ImmType),
                          int imm_bits,
                          const std::string& fmt,
                          int bias = 0) {
    return RepeatTemplatedRegistersImmBits<Reg, Reg, Reg, ImmType>(f,
        imm_bits,
        GetRegisters(),
        GetRegisters(),
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt,
        bias);
  }

  template <typename ImmType>
  std::string RepeatRIb(void (Ass::*f)(Reg, ImmType), int imm_bits, std::string fmt, int bias = 0) {
    return RepeatTemplatedRegisterImmBits<Reg, ImmType>(f,
        imm_bits,
        GetRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt,
        bias);
  }

  template <typename ImmType>
  std::string RepeatFRIb(void (Ass::*f)(FPReg, Reg, ImmType),
                         int imm_bits,
                         const std::string& fmt,
                         int bias = 0) {
    return RepeatTemplatedRegistersImmBits<FPReg, Reg, ImmType>(f,
        imm_bits,
        GetFPRegisters(),
        GetRegisters(),
        &AssemblerTest::GetFPRegName,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt,
        bias);
  }

  std::string RepeatFF(void (Ass::*f)(FPReg, FPReg), const std::string& fmt) {
    return RepeatTemplatedRegisters<FPReg, FPReg>(f,
                                                  GetFPRegisters(),
                                                  GetFPRegisters(),
                                                  &AssemblerTest::GetFPRegName,
                                                  &AssemblerTest::GetFPRegName,
                                                  fmt);
  }

  std::string RepeatFFF(void (Ass::*f)(FPReg, FPReg, FPReg), const std::string& fmt) {
    return RepeatTemplatedRegisters<FPReg, FPReg, FPReg>(f,
                                                         GetFPRegisters(),
                                                         GetFPRegisters(),
                                                         GetFPRegisters(),
                                                         &AssemblerTest::GetFPRegName,
                                                         &AssemblerTest::GetFPRegName,
                                                         &AssemblerTest::GetFPRegName,
                                                         fmt);
  }

  std::string RepeatFFFF(void (Ass::*f)(FPReg, FPReg, FPReg, FPReg), const std::string& fmt) {
    return RepeatTemplatedRegisters<FPReg, FPReg, FPReg, FPReg>(f,
                                                                GetFPRegisters(),
                                                                GetFPRegisters(),
                                                                GetFPRegisters(),
                                                                GetFPRegisters(),
                                                                &AssemblerTest::GetFPRegName,
                                                                &AssemblerTest::GetFPRegName,
                                                                &AssemblerTest::GetFPRegName,
                                                                &AssemblerTest::GetFPRegName,
                                                                fmt);
  }

  std::string RepeatFFR(void (Ass::*f)(FPReg, FPReg, Reg), const std::string& fmt) {
    return RepeatTemplatedRegisters<FPReg, FPReg, Reg>(
        f,
        GetFPRegisters(),
        GetFPRegisters(),
        GetRegisters(),
        &AssemblerTest::GetFPRegName,
        &AssemblerTest::GetFPRegName,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt);
  }

  std::string RepeatFFI(void (Ass::*f)(FPReg, FPReg, const Imm&),
                        size_t imm_bytes,
                        const std::string& fmt) {
    return RepeatTemplatedRegistersImm<FPReg, FPReg>(f,
                                                     GetFPRegisters(),
                                                     GetFPRegisters(),
                                                     &AssemblerTest::GetFPRegName,
                                                     &AssemblerTest::GetFPRegName,
                                                     imm_bytes,
                                                     fmt);
  }

  template <typename ImmType>
  std::string RepeatFFIb(void (Ass::*f)(FPReg, FPReg, ImmType),
                         int imm_bits,
                         const std::string& fmt) {
    return RepeatTemplatedRegistersImmBits<FPReg, FPReg, ImmType>(f,
                                                                  imm_bits,
                                                                  GetFPRegisters(),
                                                                  GetFPRegisters(),
                                                                  &AssemblerTest::GetFPRegName,
                                                                  &AssemblerTest::GetFPRegName,
                                                                  fmt);
  }

  template <typename ImmType>
  std::string RepeatIbFF(void (Ass::*f)(ImmType, FPReg, FPReg),
                         int imm_bits,
                         const std::string& fmt) {
    return RepeatTemplatedImmBitsRegisters<ImmType, FPReg, FPReg>(f,
                                                                  GetFPRegisters(),
                                                                  GetFPRegisters(),
                                                                  &AssemblerTest::GetFPRegName,
                                                                  &AssemblerTest::GetFPRegName,
                                                                  imm_bits,
                                                                  fmt);
  }

  std::string RepeatRFF(void (Ass::*f)(Reg, FPReg, FPReg), const std::string& fmt) {
    return RepeatTemplatedRegisters<Reg, FPReg, FPReg>(
        f,
        GetRegisters(),
        GetFPRegisters(),
        GetFPRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetFPRegName,
        &AssemblerTest::GetFPRegName,
        fmt);
  }

  template <typename ImmType>
  std::string RepeatRFIb(void (Ass::*f)(Reg, FPReg, ImmType),
                         int imm_bits,
                         const std::string& fmt) {
    return RepeatTemplatedRegistersImmBits<Reg, FPReg, ImmType>(
        f,
        imm_bits,
        GetRegisters(),
        GetFPRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetFPRegName,
        fmt);
  }

  std::string RepeatFR(void (Ass::*f)(FPReg, Reg), const std::string& fmt) {
    return RepeatTemplatedRegisters<FPReg, Reg>(f,
        GetFPRegisters(),
        GetRegisters(),
        &AssemblerTest::GetFPRegName,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt);
  }

  std::string RepeatFr(void (Ass::*f)(FPReg, Reg), const std::string& fmt) {
    return RepeatTemplatedRegisters<FPReg, Reg>(f,
        GetFPRegisters(),
        GetRegisters(),
        &AssemblerTest::GetFPRegName,
        &AssemblerTest::GetRegName<RegisterView::kUseSecondaryName>,
        fmt);
  }

  std::string RepeatRF(void (Ass::*f)(Reg, FPReg), const std::string& fmt) {
    return RepeatTemplatedRegisters<Reg, FPReg>(f,
        GetRegisters(),
        GetFPRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetFPRegName,
        fmt);
  }

  std::string RepeatrF(void (Ass::*f)(Reg, FPReg), const std::string& fmt) {
    return RepeatTemplatedRegisters<Reg, FPReg>(f,
        GetRegisters(),
        GetFPRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUseSecondaryName>,
        &AssemblerTest::GetFPRegName,
        fmt);
  }

  std::string RepeatI(void (Ass::*f)(const Imm&),
                      size_t imm_bytes,
                      const std::string& fmt,
                      bool as_uint = false) {
    std::string str;
    std::vector<int64_t> imms = CreateImmediateValues(imm_bytes, as_uint);

    WarnOnCombinations(imms.size());

    for (int64_t imm : imms) {
      Imm new_imm = CreateImmediate(imm);
      if (f != nullptr) {
        (assembler_.get()->*f)(new_imm);
      }
      std::string base = fmt;

      ReplaceImm(imm, /*bias=*/ 0, /*multiplier=*/ 1, &base);

      str += base;
      str += "\n";
    }
    return str;
  }

  std::string RepeatV(void (Ass::*f)(VecReg), const std::string& fmt) {
    return RepeatTemplatedRegister<VecReg>(
        f, GetVectorRegisters(), &AssemblerTest::GetVecRegName, fmt);
  }

  std::string RepeatVV(void (Ass::*f)(VecReg, VecReg), const std::string& fmt) {
    return RepeatTemplatedRegisters<VecReg, VecReg>(f,
                                                    GetVectorRegisters(),
                                                    GetVectorRegisters(),
                                                    &AssemblerTest::GetVecRegName,
                                                    &AssemblerTest::GetVecRegName,
                                                    fmt);
  }

  std::string RepeatVVV(void (Ass::*f)(VecReg, VecReg, VecReg), const std::string& fmt) {
    return RepeatTemplatedRegisters<VecReg, VecReg, VecReg>(f,
                                                            GetVectorRegisters(),
                                                            GetVectorRegisters(),
                                                            GetVectorRegisters(),
                                                            &AssemblerTest::GetVecRegName,
                                                            &AssemblerTest::GetVecRegName,
                                                            &AssemblerTest::GetVecRegName,
                                                            fmt);
  }

  std::string RepeatVVR(void (Ass::*f)(VecReg, VecReg, Reg), const std::string& fmt) {
    return RepeatTemplatedRegisters<VecReg, VecReg, Reg>(
        f,
        GetVectorRegisters(),
        GetVectorRegisters(),
        GetRegisters(),
        &AssemblerTest::GetVecRegName,
        &AssemblerTest::GetVecRegName,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt);
  }

  std::string RepeatVR(void (Ass::*f)(VecReg, Reg), const std::string& fmt) {
    return RepeatTemplatedRegisters<VecReg, Reg>(
        f,
        GetVectorRegisters(),
        GetRegisters(),
        &AssemblerTest::GetVecRegName,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt);
  }

  std::string RepeatVF(void (Ass::*f)(VecReg, FPReg), const std::string& fmt) {
    return RepeatTemplatedRegisters<VecReg, FPReg>(f,
                                                   GetVectorRegisters(),
                                                   GetFPRegisters(),
                                                   &AssemblerTest::GetVecRegName,
                                                   &AssemblerTest::GetFPRegName,
                                                   fmt);
  }

  std::string RepeatFV(void (Ass::*f)(FPReg, VecReg), const std::string& fmt) {
    return RepeatTemplatedRegisters<FPReg, VecReg>(f,
                                                   GetFPRegisters(),
                                                   GetVectorRegisters(),
                                                   &AssemblerTest::GetFPRegName,
                                                   &AssemblerTest::GetVecRegName,
                                                   fmt);
  }

  std::string RepeatRV(void (Ass::*f)(Reg, VecReg), const std::string& fmt) {
    return RepeatTemplatedRegisters<Reg, VecReg>(
        f,
        GetRegisters(),
        GetVectorRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetVecRegName,
        fmt);
  }

  template <typename ImmType>
  std::string RepeatVIb(void (Ass::*f)(VecReg, ImmType),
                        int imm_bits,
                        std::string fmt,
                        int bias = 0) {
    return RepeatTemplatedRegisterImmBits<VecReg, ImmType>(f,
                                                           imm_bits,
                                                           GetVectorRegisters(),
                                                           &AssemblerTest::GetVecRegName,
                                                           fmt,
                                                           bias);
  }

  template <typename ImmType>
  std::string RepeatVRIb(void (Ass::*f)(VecReg, Reg, ImmType),
                         int imm_bits,
                         const std::string& fmt,
                         int bias = 0,
                         int multiplier = 1) {
    return RepeatTemplatedRegistersImmBits<VecReg, Reg, ImmType>(
        f,
        imm_bits,
        GetVectorRegisters(),
        GetRegisters(),
        &AssemblerTest::GetVecRegName,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt,
        bias,
        multiplier);
  }

  template <typename ImmType>
  std::string RepeatRVIb(void (Ass::*f)(Reg, VecReg, ImmType),
                         int imm_bits,
                         const std::string& fmt,
                         int bias = 0,
                         int multiplier = 1) {
    return RepeatTemplatedRegistersImmBits<Reg, VecReg, ImmType>(
        f,
        imm_bits,
        GetRegisters(),
        GetVectorRegisters(),
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetVecRegName,
        fmt,
        bias,
        multiplier);
  }

  template <typename ImmType>
  std::string RepeatVVIb(void (Ass::*f)(VecReg, VecReg, ImmType),
                         int imm_bits,
                         const std::string& fmt,
                         int bias = 0) {
    return RepeatTemplatedRegistersImmBits<VecReg, VecReg, ImmType>(f,
                                                                    imm_bits,
                                                                    GetVectorRegisters(),
                                                                    GetVectorRegisters(),
                                                                    &AssemblerTest::GetVecRegName,
                                                                    &AssemblerTest::GetVecRegName,
                                                                    fmt,
                                                                    bias);
  }

  // The following functions are public so that TestFn can use them...

  // Returns a vector of address used by any of the repeat methods
  // involving an "A" (e.g. RepeatA).
  virtual std::vector<Addr> GetAddresses() = 0;

  // Returns a vector of registers used by any of the repeat methods
  // involving an "R" (e.g. RepeatR).
  virtual ArrayRef<const Reg> GetRegisters() = 0;

  // Returns a vector of fp-registers used by any of the repeat methods
  // involving an "F" (e.g. RepeatFF).
  virtual ArrayRef<const FPReg> GetFPRegisters() {
    UNIMPLEMENTED(FATAL) << "Architecture does not support floating-point registers";
    UNREACHABLE();
  }

  // Returns a vector of dedicated simd-registers used by any of the repeat
  // methods involving an "V" (e.g. RepeatVV).
  virtual ArrayRef<const VecReg> GetVectorRegisters() {
    UNIMPLEMENTED(FATAL) << "Architecture does not support vector registers";
    UNREACHABLE();
  }

  // Secondary register names are the secondary view on registers, e.g., 32b on 64b systems.
  virtual std::string GetSecondaryRegisterName([[maybe_unused]] const Reg& reg) {
    UNIMPLEMENTED(FATAL) << "Architecture does not support secondary registers";
    UNREACHABLE();
  }

  // Tertiary register names are the tertiary view on registers, e.g., 16b on 64b systems.
  virtual std::string GetTertiaryRegisterName([[maybe_unused]] const Reg& reg) {
    UNIMPLEMENTED(FATAL) << "Architecture does not support tertiary registers";
    UNREACHABLE();
  }

  // Quaternary register names are the quaternary view on registers, e.g., 8b on 64b systems.
  virtual std::string GetQuaternaryRegisterName([[maybe_unused]] const Reg& reg) {
    UNIMPLEMENTED(FATAL) << "Architecture does not support quaternary registers";
    UNREACHABLE();
  }

  std::string GetRegisterName(const Reg& reg) {
    return GetRegName<RegisterView::kUsePrimaryName>(reg);
  }

 protected:
  AssemblerTest() {}

  void SetUp() override {
    AssemblerTestBase::SetUp();
    allocator_.reset(new ArenaAllocator(&pool_));
    assembler_.reset(CreateAssembler(allocator_.get()));
    SetUpHelpers();
  }

  void TearDown() override {
    AssemblerTestBase::TearDown();
    assembler_.reset();
    allocator_.reset();
  }

  // Override this to set up any architecture-specific things, e.g., CPU revision.
  virtual Ass* CreateAssembler(ArenaAllocator* allocator) {
    return new (allocator) Ass(allocator);
  }

  // Override this to set up any architecture-specific things, e.g., register vectors.
  virtual void SetUpHelpers() {}

  // Create a couple of immediate values up to the number of bytes given.
  virtual std::vector<int64_t> CreateImmediateValues(size_t imm_bytes, bool as_uint = false) {
    std::vector<int64_t> res;
    res.push_back(0);
    if (!as_uint) {
      res.push_back(-1);
    } else {
      res.push_back(0xFF);
    }
    res.push_back(0x12);
    if (imm_bytes >= 2) {
      res.push_back(0x1234);
      if (!as_uint) {
        res.push_back(-0x1234);
      } else {
        res.push_back(0xFFFF);
      }
      if (imm_bytes >= 4) {
        res.push_back(0x12345678);
        if (!as_uint) {
          res.push_back(-0x12345678);
        } else {
          res.push_back(0xFFFFFFFF);
        }
        if (imm_bytes >= 6) {
          res.push_back(0x123456789ABC);
          if (!as_uint) {
            res.push_back(-0x123456789ABC);
          }
          if (imm_bytes >= 8) {
            res.push_back(0x123456789ABCDEF0);
            if (!as_uint) {
              res.push_back(-0x123456789ABCDEF0);
            } else {
              res.push_back(0xFFFFFFFFFFFFFFFF);
            }
          }
        }
      }
    }
    return res;
  }

  const int kMaxBitsExhaustiveTest = 8;

  // Create a couple of immediate values up to the number of bits given.
  virtual std::vector<int64_t> CreateImmediateValuesBits(const int imm_bits,
                                                         bool as_uint = false,
                                                         int shift = 0) {
    CHECK_GT(imm_bits, 0);
    CHECK_LE(imm_bits, 64);
    std::vector<int64_t> res;

    if (imm_bits <= kMaxBitsExhaustiveTest) {
      if (as_uint) {
        for (uint64_t i = MinInt<uint64_t>(imm_bits); i <= MaxInt<uint64_t>(imm_bits); i++) {
          res.push_back(static_cast<int64_t>(i << shift));
        }
      } else {
        for (int64_t i = MinInt<int64_t>(imm_bits); i <= MaxInt<int64_t>(imm_bits); i++) {
          res.push_back(i << shift);
        }
      }
    } else {
      if (as_uint) {
        for (uint64_t i = MinInt<uint64_t>(kMaxBitsExhaustiveTest);
             i <= MaxInt<uint64_t>(kMaxBitsExhaustiveTest);
             i++) {
          res.push_back(static_cast<int64_t>(i << shift));
        }
        for (int i = 0; i <= imm_bits; i++) {
          uint64_t j = (MaxInt<uint64_t>(kMaxBitsExhaustiveTest) + 1) +
                       ((MaxInt<uint64_t>(imm_bits) -
                        (MaxInt<uint64_t>(kMaxBitsExhaustiveTest) + 1))
                        * i / imm_bits);
          res.push_back(static_cast<int64_t>(j << shift));
        }
      } else {
        for (int i = 0; i <= imm_bits; i++) {
          int64_t j = MinInt<int64_t>(imm_bits) +
                      ((((MinInt<int64_t>(kMaxBitsExhaustiveTest) - 1) -
                         MinInt<int64_t>(imm_bits))
                        * i) / imm_bits);
          res.push_back(static_cast<int64_t>(j << shift));
        }
        for (int64_t i = MinInt<int64_t>(kMaxBitsExhaustiveTest);
             i <= MaxInt<int64_t>(kMaxBitsExhaustiveTest);
             i++) {
          res.push_back(static_cast<int64_t>(i << shift));
        }
        for (int i = 0; i <= imm_bits; i++) {
          int64_t j = (MaxInt<int64_t>(kMaxBitsExhaustiveTest) + 1) +
                      ((MaxInt<int64_t>(imm_bits) - (MaxInt<int64_t>(kMaxBitsExhaustiveTest) + 1))
                       * i / imm_bits);
          res.push_back(static_cast<int64_t>(j << shift));
        }
      }
    }

    return res;
  }

  // Create an immediate from the specific value.
  virtual Imm CreateImmediate(int64_t imm_value) = 0;

  //
  // Addresses repeats.
  //

  // Repeats over addresses provided by fixture.
  std::string RepeatA(void (Ass::*f)(const Addr&), const std::string& fmt) {
    return RepeatA(f, GetAddresses(), fmt);
  }

  // Variant that takes explicit vector of addresss
  // (to test restricted addressing modes set).
  std::string RepeatA(void (Ass::*f)(const Addr&),
                      const std::vector<Addr>& a,
                      const std::string& fmt) {
    return RepeatTemplatedMem<Addr>(f, a, &AssemblerTest::GetAddrName, fmt);
  }

  // Repeats over addresses and immediates provided by fixture.
  std::string RepeatAI(void (Ass::*f)(const Addr&, const Imm&),
                       size_t imm_bytes,
                       const std::string& fmt) {
    return RepeatAI(f, imm_bytes, GetAddresses(), fmt);
  }

  // Variant that takes explicit vector of addresss
  // (to test restricted addressing modes set).
  std::string RepeatAI(void (Ass::*f)(const Addr&, const Imm&),
                       size_t imm_bytes,
                       const std::vector<Addr>& a,
                       const std::string& fmt) {
    return RepeatTemplatedMemImm<Addr>(f, imm_bytes, a, &AssemblerTest::GetAddrName, fmt);
  }

  // Repeats over registers and addresses provided by fixture.
  std::string RepeatRA(void (Ass::*f)(Reg, const Addr&), const std::string& fmt) {
    return RepeatRA(f, GetAddresses(), fmt);
  }

  // Variant that takes explicit vector of addresss
  // (to test restricted addressing modes set).
  std::string RepeatRA(void (Ass::*f)(Reg, const Addr&),
                       const std::vector<Addr>& a,
                       const std::string& fmt) {
    return RepeatTemplatedRegMem<Reg, Addr>(
        f,
        GetRegisters(),
        a,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        &AssemblerTest::GetAddrName,
        fmt);
  }

  // Repeats over secondary registers and addresses provided by fixture.
  std::string RepeatrA(void (Ass::*f)(Reg, const Addr&), const std::string& fmt) {
    return RepeatrA(f, GetAddresses(), fmt);
  }

  // Variant that takes explicit vector of addresss
  // (to test restricted addressing modes set).
  std::string RepeatrA(void (Ass::*f)(Reg, const Addr&),
                       const std::vector<Addr>& a,
                       const std::string& fmt) {
    return RepeatTemplatedRegMem<Reg, Addr>(
        f,
        GetRegisters(),
        a,
        &AssemblerTest::GetRegName<RegisterView::kUseSecondaryName>,
        &AssemblerTest::GetAddrName,
        fmt);
  }

  // Repeats over tertiary registers and addresses provided by fixture.
  std::string RepeatwA(void (Ass::*f)(Reg, const Addr&), const std::string& fmt) {
    return RepeatwA(f, GetAddresses(), fmt);
  }

  // Variant that takes explicit vector of addresss
  // (to test restricted addressing modes set).
  std::string RepeatwA(void (Ass::*f)(Reg, const Addr&),
                       const std::vector<Addr>& a,
                       const std::string& fmt) {
    return RepeatTemplatedRegMem<Reg, Addr>(
        f,
        GetRegisters(),
        a,
        &AssemblerTest::GetRegName<RegisterView::kUseTertiaryName>,
        &AssemblerTest::GetAddrName,
        fmt);
  }

  // Repeats over quaternary registers and addresses provided by fixture.
  std::string RepeatbA(void (Ass::*f)(Reg, const Addr&), const std::string& fmt) {
    return RepeatbA(f, GetAddresses(), fmt);
  }

  // Variant that takes explicit vector of addresss
  // (to test restricted addressing modes set).
  std::string RepeatbA(void (Ass::*f)(Reg, const Addr&),
                       const std::vector<Addr>& a,
                       const std::string& fmt) {
    return RepeatTemplatedRegMem<Reg, Addr>(
        f,
        GetRegisters(),
        a,
        &AssemblerTest::GetRegName<RegisterView::kUseQuaternaryName>,
        &AssemblerTest::GetAddrName,
        fmt);
  }

  // Repeats over fp-registers and addresses provided by fixture.
  std::string RepeatFA(void (Ass::*f)(FPReg, const Addr&), const std::string& fmt) {
    return RepeatFA(f, GetAddresses(), fmt);
  }

  // Variant that takes explicit vector of addresss
  // (to test restricted addressing modes set).
  std::string RepeatFA(void (Ass::*f)(FPReg, const Addr&),
                       const std::vector<Addr>& a,
                       const std::string& fmt) {
    return RepeatTemplatedRegMem<FPReg, Addr>(
        f,
        GetFPRegisters(),
        a,
        &AssemblerTest::GetFPRegName,
        &AssemblerTest::GetAddrName,
        fmt);
  }

  // Repeats over addresses and registers provided by fixture.
  std::string RepeatAR(void (Ass::*f)(const Addr&, Reg), const std::string& fmt) {
    return RepeatAR(f, GetAddresses(), fmt);
  }

  // Variant that takes explicit vector of addresss
  // (to test restricted addressing modes set).
  std::string RepeatAR(void (Ass::*f)(const Addr&, Reg),
                       const std::vector<Addr>& a,
                       const std::string& fmt) {
    return RepeatTemplatedMemReg<Addr, Reg>(
        f,
        a,
        GetRegisters(),
        &AssemblerTest::GetAddrName,
        &AssemblerTest::GetRegName<RegisterView::kUsePrimaryName>,
        fmt);
  }

  // Repeats over addresses and secondary registers provided by fixture.
  std::string RepeatAr(void (Ass::*f)(const Addr&, Reg), const std::string& fmt) {
    return RepeatAr(f, GetAddresses(), fmt);
  }

  // Variant that takes explicit vector of addresss
  // (to test restricted addressing modes set).
  std::string RepeatAr(void (Ass::*f)(const Addr&, Reg),
                       const std::vector<Addr>& a,
                       const std::string& fmt) {
    return RepeatTemplatedMemReg<Addr, Reg>(
        f,
        a,
        GetRegisters(),
        &AssemblerTest::GetAddrName,
        &AssemblerTest::GetRegName<RegisterView::kUseSecondaryName>,
        fmt);
  }

  // Repeats over addresses and tertiary registers provided by fixture.
  std::string RepeatAw(void (Ass::*f)(const Addr&, Reg), const std::string& fmt) {
    return RepeatAw(f, GetAddresses(), fmt);
  }

  // Variant that takes explicit vector of addresss
  // (to test restricted addressing modes set).
  std::string RepeatAw(void (Ass::*f)(const Addr&, Reg),
                       const std::vector<Addr>& a,
                       const std::string& fmt) {
    return RepeatTemplatedMemReg<Addr, Reg>(
        f,
        a,
        GetRegisters(),
        &AssemblerTest::GetAddrName,
        &AssemblerTest::GetRegName<RegisterView::kUseTertiaryName>,
        fmt);
  }

  // Repeats over addresses and quaternary registers provided by fixture.
  std::string RepeatAb(void (Ass::*f)(const Addr&, Reg), const std::string& fmt) {
    return RepeatAb(f, GetAddresses(), fmt);
  }

  // Variant that takes explicit vector of addresss
  // (to test restricted addressing modes set).
  std::string RepeatAb(void (Ass::*f)(const Addr&, Reg),
                       const std::vector<Addr>& a,
                       const std::string& fmt) {
    return RepeatTemplatedMemReg<Addr, Reg>(
        f,
        a,
        GetRegisters(),
        &AssemblerTest::GetAddrName,
        &AssemblerTest::GetRegName<RegisterView::kUseQuaternaryName>,
        fmt);
  }

  // Repeats over addresses and fp-registers provided by fixture.
  std::string RepeatAF(void (Ass::*f)(const Addr&, FPReg), const std::string& fmt) {
    return RepeatAF(f, GetAddresses(), fmt);
  }

  // Variant that takes explicit vector of addresss
  // (to test restricted addressing modes set).
  std::string RepeatAF(void (Ass::*f)(const Addr&, FPReg),
                       const std::vector<Addr>& a,
                       const std::string& fmt) {
    return RepeatTemplatedMemReg<Addr, FPReg>(
        f,
        a,
        GetFPRegisters(),
        &AssemblerTest::GetAddrName,
        &AssemblerTest::GetFPRegName,
        fmt);
  }

  template <typename AddrType>
  std::string RepeatTemplatedMem(void (Ass::*f)(const AddrType&),
                                 const std::vector<AddrType> addresses,
                                 std::string (AssemblerTest::*GetAName)(const AddrType&),
                                 const std::string& fmt) {
    WarnOnCombinations(addresses.size());
    std::string str;
    for (auto addr : addresses) {
      if (f != nullptr) {
        (assembler_.get()->*f)(addr);
      }
      std::string base = fmt;

      ReplaceAddr((this->*GetAName)(addr), &base);

      str += base;
      str += "\n";
    }
    return str;
  }

  template <typename AddrType>
  std::string RepeatTemplatedMemImm(void (Ass::*f)(const AddrType&, const Imm&),
                                    size_t imm_bytes,
                                    const std::vector<AddrType> addresses,
                                    std::string (AssemblerTest::*GetAName)(const AddrType&),
                                    const std::string& fmt) {
    std::vector<int64_t> imms = CreateImmediateValues(imm_bytes);
    WarnOnCombinations(addresses.size() * imms.size());
    std::string str;
    for (auto addr : addresses) {
      for (int64_t imm : imms) {
        Imm new_imm = CreateImmediate(imm);
        if (f != nullptr) {
          (assembler_.get()->*f)(addr, new_imm);
        }
        std::string base = fmt;

        ReplaceAddr((this->*GetAName)(addr), &base);
        ReplaceImm(imm, /*bias=*/ 0, /*multiplier=*/ 1, &base);

        str += base;
        str += "\n";
      }
    }
    return str;
  }

  template <typename RegType, typename AddrType>
  std::string RepeatTemplatedRegMem(void (Ass::*f)(RegType, const AddrType&),
                                    ArrayRef<const RegType> registers,
                                    const std::vector<AddrType> addresses,
                                    std::string (AssemblerTest::*GetRName)(const RegType&),
                                    std::string (AssemblerTest::*GetAName)(const AddrType&),
                                    const std::string& fmt) {
    WarnOnCombinations(addresses.size() * registers.size());
    std::string str;
    for (auto reg : registers) {
      for (auto addr : addresses) {
        if (f != nullptr) {
          (assembler_.get()->*f)(reg, addr);
        }
        std::string base = fmt;

        ReplaceReg(REG_TOKEN, (this->*GetRName)(reg), &base);
        ReplaceAddr((this->*GetAName)(addr), &base);

        str += base;
        str += "\n";
      }
    }
    return str;
  }

  template <typename AddrType, typename RegType>
  std::string RepeatTemplatedMemReg(void (Ass::*f)(const AddrType&, RegType),
                                    const std::vector<AddrType> addresses,
                                    ArrayRef<const RegType> registers,
                                    std::string (AssemblerTest::*GetAName)(const AddrType&),
                                    std::string (AssemblerTest::*GetRName)(const RegType&),
                                    const std::string& fmt) {
    WarnOnCombinations(addresses.size() * registers.size());
    std::string str;
    for (auto addr : addresses) {
      for (auto reg : registers) {
        if (f != nullptr) {
          (assembler_.get()->*f)(addr, reg);
        }
        std::string base = fmt;

        ReplaceAddr((this->*GetAName)(addr), &base);
        ReplaceReg(REG_TOKEN, (this->*GetRName)(reg), &base);

        str += base;
        str += "\n";
      }
    }
    return str;
  }

  //
  // Register repeats.
  //

  template <typename RegType>
  std::string RepeatTemplatedRegister(void (Ass::*f)(RegType),
                                      ArrayRef<const RegType> registers,
                                      std::string (AssemblerTest::*GetName)(const RegType&),
                                      const std::string& fmt) {
    std::string str;
    for (auto reg : registers) {
      if (f != nullptr) {
        (assembler_.get()->*f)(reg);
      }
      std::string base = fmt;

      ReplaceReg(REG_TOKEN, (this->*GetName)(reg), &base);

      str += base;
      str += "\n";
    }
    return str;
  }

  template <typename Reg1, typename Reg2>
  std::string RepeatTemplatedRegisters(void (Ass::*f)(Reg1, Reg2),
                                       ArrayRef<const Reg1> reg1_registers,
                                       ArrayRef<const Reg2> reg2_registers,
                                       std::string (AssemblerTest::*GetName1)(const Reg1&),
                                       std::string (AssemblerTest::*GetName2)(const Reg2&),
                                       const std::string& fmt,
                                       const std::vector<std::pair<Reg1, Reg2>>* except = nullptr) {
    WarnOnCombinations(reg1_registers.size() * reg2_registers.size());

    std::string str;
    for (auto reg1 : reg1_registers) {
      for (auto reg2 : reg2_registers) {
        // Check if this register pair is on the exception list. If so, skip it.
        if (except != nullptr) {
          const auto& pair = std::make_pair(reg1, reg2);
          if (std::find(except->begin(), except->end(), pair) != except->end()) {
            continue;
          }
        }

        if (f != nullptr) {
          (assembler_.get()->*f)(reg1, reg2);
        }
        std::string base = fmt;

        ReplaceReg(REG1_TOKEN, (this->*GetName1)(reg1), &base);
        ReplaceReg(REG2_TOKEN, (this->*GetName2)(reg2), &base);

        str += base;
        str += "\n";
      }
    }
    return str;
  }

  template <typename Reg1, typename Reg2>
  std::string RepeatTemplatedRegistersNoDupes(void (Ass::*f)(Reg1, Reg2),
                                              ArrayRef<const Reg1> reg1_registers,
                                              ArrayRef<const Reg2> reg2_registers,
                                              std::string (AssemblerTest::*GetName1)(const Reg1&),
                                              std::string (AssemblerTest::*GetName2)(const Reg2&),
                                              const std::string& fmt) {
    WarnOnCombinations(reg1_registers.size() * reg2_registers.size());

    std::string str;
    for (auto reg1 : reg1_registers) {
      for (auto reg2 : reg2_registers) {
        if (reg1 == reg2) continue;
        if (f != nullptr) {
          (assembler_.get()->*f)(reg1, reg2);
        }
        std::string base = fmt;

        ReplaceReg(REG1_TOKEN, (this->*GetName1)(reg1), &base);
        ReplaceReg(REG2_TOKEN, (this->*GetName2)(reg2), &base);

        str += base;
        str += "\n";
      }
    }
    return str;
  }

  template <typename Reg1, typename Reg2, typename Reg3>
  std::string RepeatTemplatedRegisters(void (Ass::*f)(Reg1, Reg2, Reg3),
                                       ArrayRef<const Reg1> reg1_registers,
                                       ArrayRef<const Reg2> reg2_registers,
                                       ArrayRef<const Reg3> reg3_registers,
                                       std::string (AssemblerTest::*GetName1)(const Reg1&),
                                       std::string (AssemblerTest::*GetName2)(const Reg2&),
                                       std::string (AssemblerTest::*GetName3)(const Reg3&),
                                       const std::string& fmt) {
    std::string str;
    for (auto reg1 : reg1_registers) {
      for (auto reg2 : reg2_registers) {
        for (auto reg3 : reg3_registers) {
          if (f != nullptr) {
            (assembler_.get()->*f)(reg1, reg2, reg3);
          }
          std::string base = fmt;

          ReplaceReg(REG1_TOKEN, (this->*GetName1)(reg1), &base);
          ReplaceReg(REG2_TOKEN, (this->*GetName2)(reg2), &base);
          ReplaceReg(REG3_TOKEN, (this->*GetName3)(reg3), &base);

          str += base;
          str += "\n";
        }
      }
    }
    return str;
  }

  template <typename Reg1, typename Reg2, typename Reg3, typename Reg4>
  std::string RepeatTemplatedRegisters(void (Ass::*f)(Reg1, Reg2, Reg3, Reg4),
                                       ArrayRef<const Reg1> reg1_registers,
                                       ArrayRef<const Reg2> reg2_registers,
                                       ArrayRef<const Reg3> reg3_registers,
                                       ArrayRef<const Reg4> reg4_registers,
                                       std::string (AssemblerTest::*GetName1)(const Reg1&),
                                       std::string (AssemblerTest::*GetName2)(const Reg2&),
                                       std::string (AssemblerTest::*GetName3)(const Reg3&),
                                       std::string (AssemblerTest::*GetName4)(const Reg4&),
                                       const std::string& fmt) {
    std::string str;
    for (auto reg1 : reg1_registers) {
      for (auto reg2 : reg2_registers) {
        for (auto reg3 : reg3_registers) {
          for (auto reg4 : reg4_registers) {
            if (f != nullptr) {
              (assembler_.get()->*f)(reg1, reg2, reg3, reg4);
            }
            std::string base = fmt;

            ReplaceReg(REG1_TOKEN, (this->*GetName1)(reg1), &base);
            ReplaceReg(REG2_TOKEN, (this->*GetName2)(reg2), &base);
            ReplaceReg(REG3_TOKEN, (this->*GetName3)(reg3), &base);
            ReplaceReg(REG4_TOKEN, (this->*GetName4)(reg4), &base);

            str += base;
            str += "\n";
          }
        }
      }
    }
    return str;
  }

  template <typename Reg1, typename Reg2>
  std::string RepeatTemplatedRegistersImm(void (Ass::*f)(Reg1, Reg2, const Imm&),
                                          ArrayRef<const Reg1> reg1_registers,
                                          ArrayRef<const Reg2> reg2_registers,
                                          std::string (AssemblerTest::*GetName1)(const Reg1&),
                                          std::string (AssemblerTest::*GetName2)(const Reg2&),
                                          size_t imm_bytes,
                                          const std::string& fmt) {
    std::vector<int64_t> imms = CreateImmediateValues(imm_bytes);
    WarnOnCombinations(reg1_registers.size() * reg2_registers.size() * imms.size());

    std::string str;
    for (auto reg1 : reg1_registers) {
      for (auto reg2 : reg2_registers) {
        for (int64_t imm : imms) {
          Imm new_imm = CreateImmediate(imm);
          if (f != nullptr) {
            (assembler_.get()->*f)(reg1, reg2, new_imm);
          }
          std::string base = fmt;

          ReplaceReg(REG1_TOKEN, (this->*GetName1)(reg1), &base);
          ReplaceReg(REG2_TOKEN, (this->*GetName2)(reg2), &base);
          ReplaceImm(imm, /*bias=*/ 0, /*multiplier=*/ 1, &base);

          str += base;
          str += "\n";
        }
      }
    }
    return str;
  }

  std::string GetAddrName(const Addr& addr) {
    std::ostringstream saddr;
    saddr << addr;
    return saddr.str();
  }

  template <RegisterView kRegView>
  std::string GetRegName(const Reg& reg) {
    std::ostringstream sreg;
    switch (kRegView) {
      case RegisterView::kUsePrimaryName:
        sreg << reg;
        break;

      case RegisterView::kUseSecondaryName:
        sreg << GetSecondaryRegisterName(reg);
        break;

      case RegisterView::kUseTertiaryName:
        sreg << GetTertiaryRegisterName(reg);
        break;

      case RegisterView::kUseQuaternaryName:
        sreg << GetQuaternaryRegisterName(reg);
        break;
    }
    return sreg.str();
  }

  std::string GetFPRegName(const FPReg& reg) {
    std::ostringstream sreg;
    sreg << reg;
    return sreg.str();
  }

  std::string GetVecRegName(const VecReg& reg) {
    std::ostringstream sreg;
    sreg << reg;
    return sreg.str();
  }

  void WarnOnCombinations(size_t count) {
    if (count > kWarnManyCombinationsThreshold) {
      GTEST_LOG_(WARNING) << "Many combinations (" << count << "), test generation might be slow.";
    }
  }

  static void ReplaceReg(const std::string& reg_token,
                         const std::string& replacement,
                         /*inout*/ std::string* str) {
    size_t reg_index;
    while ((reg_index = str->find(reg_token)) != std::string::npos) {
      str->replace(reg_index, reg_token.length(), replacement);
    }
  }

  static void ReplaceImm(int64_t imm,
                         int64_t bias,
                         int64_t multiplier,
                         /*inout*/ std::string* str) {
    size_t imm_index = str->find(IMM_TOKEN);
    if (imm_index != std::string::npos) {
      std::ostringstream sreg;
      sreg << imm * multiplier + bias;
      std::string imm_string = sreg.str();
      str->replace(imm_index, ConstexprStrLen(IMM_TOKEN), imm_string);
    }
  }

  static void ReplaceAddr(const std::string& replacement, /*inout*/ std::string* str) {
    size_t addr_index;
    if ((addr_index = str->find(ADDRESS_TOKEN)) != std::string::npos) {
      str->replace(addr_index, ConstexprStrLen(ADDRESS_TOKEN), replacement);
    }
  }

  static constexpr const char* ADDRESS_TOKEN = "{mem}";
  static constexpr const char* REG_TOKEN = "{reg}";
  static constexpr const char* REG1_TOKEN = "{reg1}";
  static constexpr const char* REG2_TOKEN = "{reg2}";
  static constexpr const char* REG3_TOKEN = "{reg3}";
  static constexpr const char* REG4_TOKEN = "{reg4}";
  static constexpr const char* IMM_TOKEN = "{imm}";

 private:
  template <RegisterView kRegView>
  std::string RepeatRegisterImm(void (Ass::*f)(Reg, const Imm&),
                                size_t imm_bytes,
                                const std::string& fmt) {
    ArrayRef<const Reg> registers = GetRegisters();
    std::string str;
    std::vector<int64_t> imms = CreateImmediateValues(imm_bytes);

    WarnOnCombinations(registers.size() * imms.size());

    for (auto reg : registers) {
      for (int64_t imm : imms) {
        Imm new_imm = CreateImmediate(imm);
        if (f != nullptr) {
          (assembler_.get()->*f)(reg, new_imm);
        }
        std::string base = fmt;

        ReplaceReg(REG_TOKEN, GetRegName<kRegView>(reg), &base);
        ReplaceImm(imm, /*bias=*/ 0, /*multiplier=*/ 1, &base);

        str += base;
        str += "\n";
      }
    }
    return str;
  }

  // Override this to pad the code with NOPs to a certain size if needed.
  virtual void Pad([[maybe_unused]] std::vector<uint8_t>& data) {}

  void DriverWrapper(const std::string& assembly_text, const std::string& test_name) {
    assembler_->FinalizeCode();
    size_t cs = assembler_->CodeSize();
    std::unique_ptr<std::vector<uint8_t>> data(new std::vector<uint8_t>(cs));
    MemoryRegion code(&(*data)[0], data->size());
    assembler_->CopyInstructions(code);
    Pad(*data);
    Driver(*data, assembly_text, test_name);
  }

  static constexpr size_t kWarnManyCombinationsThreshold = 500;

  MallocArenaPool pool_;
  std::unique_ptr<ArenaAllocator> allocator_;
  std::unique_ptr<Ass> assembler_;

  DISALLOW_COPY_AND_ASSIGN(AssemblerTest);
};

}  // namespace art

#endif  // ART_COMPILER_UTILS_ASSEMBLER_TEST_H_
