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

#ifndef BERBERIS_INTRINSICS_COMMON_TO_X86_TEXT_ASSEMBLER_COMMON_H_
#define BERBERIS_INTRINSICS_COMMON_TO_X86_TEXT_ASSEMBLER_COMMON_H_

#include <array>
#include <cstdint>
#include <cstdio>
#include <deque>
#include <string>

#include "berberis/base/checks.h"
#include "berberis/base/config.h"
#include "berberis/base/macros.h"  // DISALLOW_IMPLICIT_CONSTRUCTORS

namespace berberis {

namespace constants_pool {

int32_t GetOffset(int32_t address);

}

template <typename Assembler>
class TextAssemblerX86 {
 public:
  // Condition class - 16 x86 conditions.
  enum class Condition {
    kOverflow = 0,
    kNoOverflow = 1,
    kBelow = 2,
    kAboveEqual = 3,
    kEqual = 4,
    kNotEqual = 5,
    kBelowEqual = 6,
    kAbove = 7,
    kNegative = 8,
    kPositive = 9,
    kParityEven = 10,
    kParityOdd = 11,
    kLess = 12,
    kGreaterEqual = 13,
    kLessEqual = 14,
    kGreater = 15,

    // aka...
    kCarry = kBelow,
    kNotCarry = kAboveEqual,
    kZero = kEqual,
    kNotZero = kNotEqual,
    kSign = kNegative,
    kNotSign = kPositive
  };

  enum ScaleFactor {
    kTimesOne = 0,
    kTimesTwo = 1,
    kTimesFour = 2,
    kTimesEight = 3,
    // All our target systems use 32-bit pointers.
    kTimesPointerSize = kTimesFour
  };

  struct Label {
    size_t id;
    bool bound = false;

    template <typename MacroAssembler>
    friend std::string ToGasArgument(const Label& label, MacroAssembler*) {
      return std::to_string(label.id) + (label.bound ? "b" : "f");
    }
  };

  struct Operand;

  class Register {
   public:
    constexpr Register() : arg_no_(kNoRegister) {}
    constexpr Register(int arg_no) : arg_no_(arg_no) {}
    int arg_no() const {
      CHECK_NE(arg_no_, kNoRegister);
      return arg_no_;
    }

    constexpr bool operator==(const Register& other) const { return arg_no() == other.arg_no(); }
    constexpr bool operator!=(const Register& other) const { return arg_no() != other.arg_no(); }

    static constexpr int kNoRegister = -1;
    static constexpr int kStackPointer = -2;
    // Used in Operand to deal with references to scratch area.
    static constexpr int kScratchPointer = -3;

   private:
    friend struct Operand;

    // Register number created during creation of assembler call.
    // See arg['arm_register'] in _gen_c_intrinsic_body in gen_intrinsics.py
    //
    // Default value (-1) means it's not assigned yet (thus couldn't be used).
    int arg_no_;
  };

  class X87Register {
   public:
    constexpr X87Register() : arg_no_(kNoRegister) {}
    constexpr X87Register(int arg_no) : arg_no_(arg_no) {}
    int arg_no() const {
      CHECK_NE(arg_no_, kNoRegister);
      return arg_no_;
    }

    constexpr bool operator==(const X87Register& other) const { return arg_no_ == other.arg_no_; }
    constexpr bool operator!=(const X87Register& other) const { return arg_no_ != other.arg_no_; }

    template <typename MacroAssembler>
    friend const std::string ToGasArgument(const X87Register& reg, MacroAssembler*) {
      return '%' + std::to_string(reg.arg_no());
    }

   private:
    // Register number created during creation of assembler call.
    // See arg['arm_register'] in _gen_c_intrinsic_body in gen_intrinsics.py
    //
    // Default value (-1) means it's not assigned yet (thus couldn't be used).
    static constexpr int kNoRegister = -1;
    int arg_no_;
  };

  class XMMRegister {
   public:
    constexpr XMMRegister() : arg_no_(kNoRegister) {}
    constexpr XMMRegister(int arg_no) : arg_no_(arg_no) {}
    int arg_no() const {
      CHECK_NE(arg_no_, kNoRegister);
      return arg_no_;
    }

    constexpr bool operator==(const XMMRegister& other) const { return arg_no() == other.arg_no(); }
    constexpr bool operator!=(const XMMRegister& other) const { return arg_no() != other.arg_no(); }

    template <typename MacroAssembler>
    friend const std::string ToGasArgument(const XMMRegister& reg, MacroAssembler*) {
      return '%' + std::to_string(reg.arg_no());
    }

   private:
    // Register number created during creation of assembler call.
    // See arg['arm_register'] in _gen_c_intrinsic_body in gen_intrinsics.py
    //
    // Default value (-1) means it's not assigned yet (thus couldn't be used).
    static constexpr int kNoRegister = -1;
    int arg_no_;
  };

  struct Operand {
    Register base = Register{};
    Register index = Register{};
    ScaleFactor scale = kTimesOne;
    int32_t disp = 0;

    template <typename MacroAssembler>
    friend const std::string ToGasArgument(const Operand& op, MacroAssembler* as) {
      std::string result{};
      if (op.base.arg_no_ == Register::kNoRegister and op.index.arg_no_ == Register::kNoRegister) {
        as->need_gpr_macroassembler_constants_ = true;
        result = std::to_string(constants_pool::GetOffset(op.disp)) + " + " +
                 ToGasArgument(
                     typename Assembler::RegisterDefaultBit(as->gpr_macroassembler_constants), as);
      } else if (op.base.arg_no_ == Register::kScratchPointer) {
        CHECK(op.index.arg_no_ == Register::kNoRegister);
        // Only support two pointers to scratch area for now.
        if (op.disp == 0) {
          result = '%' + std::to_string(as->gpr_macroassembler_scratch.arg_no());
        } else if (op.disp == config::kScratchAreaSlotSize) {
          result = '%' + std::to_string(as->gpr_macroassembler_scratch2.arg_no());
        } else {
          FATAL("Only two scratch registers are supported for now");
        }
      } else {
        if (op.base.arg_no_ != Register::kNoRegister) {
          result = ToGasArgument(typename Assembler::RegisterDefaultBit(op.base), as);
        }
        if (op.index.arg_no_ != Register::kNoRegister) {
          result += ',' + ToGasArgument(typename Assembler::RegisterDefaultBit(op.index), as) +
                    ',' + std::to_string(1 << op.scale);
        }
        result = '(' + result + ')';
        if (op.disp) {
          result = std::to_string(op.disp) + result;
        }
      }
      return result;
    }
  };

  TextAssemblerX86(int indent, FILE* out) : indent_(indent), out_(out) {}

  Register gpr_a{};
  Register gpr_c{};
  Register gpr_d{};
  // Note: stack pointer is not reflected in list of arguments, intrinsics use
  // it implicitly.
  Register gpr_s{Register::kStackPointer};
  // Used in Operand as pseudo-register to temporary operand.
  Register gpr_scratch{Register::kScratchPointer};

  // In x86-64 case we could refer to kBerberisMacroAssemblerConstants via %rip.
  // In x86-32 mode, on the other hand, we need complex dance to access it via GOT.
  // Intrinsics which use these constants receive it via additional parameter - and
  // we need to know if it's needed or not.
  Register gpr_macroassembler_constants{};
  bool need_gpr_macroassembler_constants() const { return need_gpr_macroassembler_constants_; }

  Register gpr_macroassembler_scratch{};
  bool need_gpr_macroassembler_scratch() const { return need_gpr_macroassembler_scratch_; }
  Register gpr_macroassembler_scratch2{};

  bool need_avx = false;
  bool need_bmi = false;
  bool need_bmi2 = false;
  bool need_fma = false;
  bool need_fma4 = false;
  bool need_lzcnt = false;
  bool need_popcnt = false;
  bool need_sse3 = false;
  bool need_ssse3 = false;
  bool need_sse4_1 = false;
  bool need_sse4_2 = false;

  void Bind(Label* label) {
    CHECK_EQ(label->bound, false);
    fprintf(out_, "%*s\"%zd:\\n\"\n", indent_ + 2, "", label->id);
    label->bound = true;
  }

  Label* MakeLabel() {
    labels_allocated_.push_back({labels_allocated_.size()});
    return &labels_allocated_.back();
  }

// Instructions.
#include "gen_text_assembler_common_x86-inl.h"  // NOLINT generated file

 protected:
  bool need_gpr_macroassembler_constants_ = false;
  bool need_gpr_macroassembler_scratch_ = false;

  template <const char* kSpPrefix, char kRegisterPrefix>
  class RegisterTemplate {
   public:
    explicit RegisterTemplate(Register reg) : reg_(reg) {}

    template <typename MacroAssembler>
    friend const std::string ToGasArgument(const RegisterTemplate& reg, MacroAssembler*) {
      if (reg.reg_.arg_no() == Register::kStackPointer) {
        return kSpPrefix;
      } else {
        if (kRegisterPrefix) {
          return std::string({'%', kRegisterPrefix}) + std::to_string(reg.reg_.arg_no());
        } else {
          return '%' + std::to_string(reg.reg_.arg_no());
        }
      }
    }

   private:
    Register reg_;
  };

  constexpr static char kSpl[] = "%%spl";
  using Register8Bit = RegisterTemplate<kSpl, 'b'>;
  constexpr static char kSp[] = "%%sp";
  using Register16Bit = RegisterTemplate<kSp, 'w'>;
  constexpr static char kEsp[] = "%%esp";
  using Register32Bit = RegisterTemplate<kEsp, 'k'>;
  constexpr static char kRsp[] = "%%rsp";
  using Register64Bit = RegisterTemplate<kRsp, 'q'>;

  void SetRequiredFeatureAVX() {
    need_avx = true;
    SetRequiredFeatureSSE4_2();
  }

  void SetRequiredFeatureBMI() {
    need_bmi = true;
  }

  void SetRequiredFeatureBMI2() {
    need_bmi2 = true;
  }

  void SetRequiredFeatureFMA() {
    need_fma = true;
    SetRequiredFeatureAVX();
  }

  void SetRequiredFeatureFMA4() {
    need_fma4 = true;
    SetRequiredFeatureAVX();
  }

  void SetRequiredFeatureLZCNT() {
    need_lzcnt = true;
  }

  void SetRequiredFeaturePOPCNT() {
    need_popcnt = true;
  }

  void SetRequiredFeatureSSE3() {
    need_sse3 = true;
    // Note: we assume that SSE2 is always available thus we don't have have_sse2 or have_sse1
    // variables.
  }

  void SetRequiredFeatureSSSE3() {
    need_ssse3 = true;
    SetRequiredFeatureSSE3();
  }

  void SetRequiredFeatureSSE4_1() {
    need_sse4_1 = true;
    SetRequiredFeatureSSSE3();
  }

  void SetRequiredFeatureSSE4_2() {
    need_sse4_2 = true;
    SetRequiredFeatureSSE4_1();
  }

  template <typename... Args>
  void Instruction(const char* name, Condition cond, const Args&... args);

  template <typename... Args>
  void Instruction(const char* name, const Args&... args);

  void EmitString() {}

  void EmitString(const std::string& s) { fprintf(out_, "%s", s.c_str()); }

  template <typename... Args>
  void EmitString(const std::string& s, const Args&... args) {
    EmitString(args...);
    fprintf(out_, ", %s", s.c_str());
  }

 protected:
  int indent_;
  FILE* out_;

 private:
  std::deque<Label> labels_allocated_;

  DISALLOW_IMPLICIT_CONSTRUCTORS(TextAssemblerX86);
};

template <typename Arg, typename MacroAssembler>
inline std::string ToGasArgument(const Arg& arg, MacroAssembler*) {
  return "$" + std::to_string(arg);
}

template <typename Assembler>
template <typename... Args>
inline void TextAssemblerX86<Assembler>::Instruction(const char* name, Condition cond, const Args&... args) {
  char name_with_condition[8] = {};
  if (strcmp(name, "Cmovl") == 0 || strcmp(name, "Cmovq") == 0) {
    strcpy(name_with_condition, "Cmov");
  } else if (strcmp(name, "Jcc") == 0) {
    strcpy(name_with_condition, "J");
  } else {
    CHECK(strcmp(name, "Setcc") == 0);
    strcpy(name_with_condition, "Set");
  }
  switch (cond) {
    case Condition::kOverflow:
      strcat(name_with_condition, "o");
      break;
    case Condition::kNoOverflow:
      strcat(name_with_condition, "no");
      break;
    case Condition::kBelow:
      strcat(name_with_condition, "b");
      break;
    case Condition::kAboveEqual:
      strcat(name_with_condition, "ae");
      break;
    case Condition::kEqual:
      strcat(name_with_condition, "e");
      break;
    case Condition::kNotEqual:
      strcat(name_with_condition, "ne");
      break;
    case Condition::kBelowEqual:
      strcat(name_with_condition, "be");
      break;
    case Condition::kAbove:
      strcat(name_with_condition, "a");
      break;
    case Condition::kNegative:
      strcat(name_with_condition, "s");
      break;
    case Condition::kPositive:
      strcat(name_with_condition, "ns");
      break;
    case Condition::kParityEven:
      strcat(name_with_condition, "p");
      break;
    case Condition::kParityOdd:
      strcat(name_with_condition, "np");
      break;
    case Condition::kLess:
      strcat(name_with_condition, "l");
      break;
    case Condition::kGreaterEqual:
      strcat(name_with_condition, "ge");
      break;
    case Condition::kLessEqual:
      strcat(name_with_condition, "le");
      break;
    case Condition::kGreater:
      strcat(name_with_condition, "g");
      break;
  }
  Instruction(name_with_condition, args...);
}

template <typename Assembler>
template <typename... Args>
inline void TextAssemblerX86<Assembler>::Instruction(const char* name, const Args&... args) {
  for (auto it : std::array<std::tuple<const char*, const char*>, 18>{
           {// Note: SSE doesn't include simple register-to-register move instruction.
            // You are supposed to use one of half-dozen variants depending on what you
            // are doing.
            //
            // Pseudoinstructions with embedded "lock" prefix.
            {"LockCmpXchg8b", "Lock; CmppXchg8b"},
            {"LockCmpXchg16b", "Lock; CmppXchg16b"},
            {"LockCmpXchgb", "Lock; CmppXchgb"},
            {"LockCmpXchgl", "Lock; CmppXchgl"},
            {"LockCmpXchgq", "Lock; CmppXchgq"},
            {"LockCmpXchgw", "Lock; CmppXchgq"},
            // Our assembler has Pmov instruction which is supposed to pick the best
            // option - but currently we just map Pmov to Movaps.
            {"Pmov", "Movaps"},
            // These instructions use different names in our assembler than in GNU AS.
            {"Movdq", "Movaps"},
            {"Movsxbl", "Movsbl"},
            {"Movsxbq", "Movsbq"},
            {"Movsxwl", "Movswl"},
            {"Movsxwq", "Movswq"},
            {"Movsxlq", "Movslq"},
            {"Movzxbl", "Movzbl"},
            {"Movzxbq", "Movzbq"},
            {"Movzxwl", "Movzwl"},
            {"Movzxwq", "Movzwq"},
            {"Movzxlq", "Movzlq"}}}) {
    if (strcmp(name, std::get<0>(it)) == 0) {
      name = std::get<1>(it);
      break;
    }
  }

  int name_length = strlen(name);
  auto cl_register = "";
  if (name_length > 4 && strcmp(name + (name_length - 4), "ByCl") == 0) {
    name_length -= 4;
    cl_register = " %%cl,";
  }

  fprintf(out_, "%*s\"%.*s%s ", indent_ + 2, "", name_length, name, cl_register);
  EmitString(ToGasArgument(args, this)...);
  fprintf(out_, "\\n\"\n");
}

}  // namespace berberis

#endif  // BERBERIS_INTRINSICS_COMMON_TO_X86_TEXT_ASSEMBLER_COMMON_H_
