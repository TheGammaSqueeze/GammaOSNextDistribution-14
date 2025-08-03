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

#include "gtest/gtest.h"

#include <sys/mman.h>

#include <iterator>
#include <string>

#include "berberis/assembler/machine_code.h"
#include "berberis/assembler/x86_32.h"
#include "berberis/assembler/x86_64.h"
#include "berberis/base/bit_util.h"
#include "berberis/base/logging.h"
#include "berberis/test_utils/scoped_exec_region.h"

#if defined(__i386__)
using CodeEmitter = berberis::x86_32::Assembler;
#elif defined(__amd64__)
using CodeEmitter = berberis::x86_64::Assembler;
#else
#error "Unsupported platform"
#endif

namespace berberis {

int Callee() {
  return 239;
}

float FloatFunc(float f1, float f2) {
  return f1 - f2;
}

inline bool CompareCode(const uint8_t* code_template_begin,
                        const uint8_t* code_template_end,
                        const MachineCode& code) {
  if ((code_template_end - code_template_begin) != static_cast<intptr_t>(code.install_size())) {
    ALOGE("Code size mismatch: %zd != %u",
          code_template_end - code_template_begin,
          code.install_size());
    return false;
  }

  if (memcmp(code_template_begin, code.AddrAs<uint8_t>(0), code.install_size()) != 0) {
    ALOGE("Code mismatch");
    MachineCode code2;
    code2.AddSequence(code_template_begin, code_template_end - code_template_begin);
    std::string code_str1, code_str2;
    code.AsString(&code_str1);
    code2.AsString(&code_str2);
    ALOGE("assembler generated\n%s\nshall be\n%s", code_str1.c_str(), code_str2.c_str());
    return false;
  }
  return true;
}

#if defined(__i386__)

namespace x86_32 {

bool AssemblerTest() {
  MachineCode code;
  CodeEmitter assembler(&code);
  assembler.Movl(Assembler::eax, {.base = Assembler::esp, .disp = 4});
  assembler.CmpXchgl({.base = Assembler::esp, .disp = 4}, Assembler::eax);
  assembler.Subl(Assembler::esp, 16);
  assembler.Movl({.base = Assembler::esp}, Assembler::eax);
  assembler.Push(Assembler::esp);
  assembler.Push(0xcccccccc);
  assembler.Pushl({.base = Assembler::esp, .disp = 0x428});
  assembler.Popl({.base = Assembler::esp, .disp = 0x428});
  assembler.Movl(Assembler::ecx, 0xcccccccc);
  assembler.Call(Assembler::ecx);
  assembler.Movl(Assembler::eax, {.base = Assembler::esp, .disp = 8});
  assembler.Addl(Assembler::esp, 24);
  assembler.Ret();
  assembler.Finalize();

  // clang-format off
  static const uint8_t code_template[] = {
    0x8b, 0x44, 0x24, 0x04,                    // mov     0x4(%esp),%eax
    0x0f, 0xb1, 0x44, 0x24, 0x04,              // cmpxchg 0x4(%esp),%eax
    0x83, 0xec, 0x10,                          // sub     $16, %esp
    0x89, 0x04, 0x24,                          // mov     %eax,(%esp)
    0x54,                                      // push    %esp
    0x68, 0xcc, 0xcc, 0xcc, 0xcc,              // push    $cccccccc
    0xff, 0xb4, 0x24, 0x28, 0x04, 0x00, 0x00,  // pushl   0x428(%esp)
    0x8f, 0x84, 0x24, 0x28, 0x04, 0x00, 0x00,  // popl    0x428(%esp)
    0xb9, 0xcc, 0xcc, 0xcc, 0xcc,              // mov     $cccccccc, %ecx
    0xff, 0xd1,                                // call    *%ecx
    0x8b, 0x44, 0x24, 0x08,                    // mov     0x8(%esp),%eax
    0x83, 0xc4, 0x18,                          // add     $24, %esp
    0xc3                                       //  ret
  };
  // clang-format on

  if (sizeof(code_template) != code.install_size()) {
    ALOGE("Code size mismatch: %zu != %u", sizeof(code_template), code.install_size());
    return false;
  }

  if (memcmp(code_template, code.AddrAs<uint8_t>(0), code.install_size()) != 0) {
    ALOGE("Code mismatch");
    MachineCode code2;
    code2.Add(code_template);
    std::string code_str1, code_str2;
    code.AsString(&code_str1);
    code2.AsString(&code_str2);
    ALOGE("assembler generated\n%s\nshall be\n%s", code_str1.c_str(), code_str2.c_str());
    return false;
  }

  return true;
}

bool LabelTest() {
  MachineCode code;
  CodeEmitter as(&code);
  Assembler::Label skip, skip2, back, end;
  as.Call(bit_cast<const void*>(&Callee));
  as.Jmp(skip);
  as.Movl(Assembler::eax, 2);
  as.Bind(&skip);
  as.Addl(Assembler::eax, 8);
  as.Jmp(skip2);
  as.Bind(&back);
  as.Addl(Assembler::eax, 12);
  as.Jmp(end);
  as.Bind(&skip2);
  as.Jmp(back);
  as.Bind(&end);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  int result = exec.get<int()>()();
  return result == 239 + 8 + 12;
}

bool CondTest1() {
  MachineCode code;
  CodeEmitter as(&code);
  as.Movl(Assembler::eax, 0xcccccccc);
  as.Movl(Assembler::edx, {.base = Assembler::esp, .disp = 4});  // arg1.
  as.Movl(Assembler::ecx, {.base = Assembler::esp, .disp = 8});  // arg2.
  as.Cmpl(Assembler::edx, Assembler::ecx);
  as.Setcc(Assembler::Condition::kEqual, Assembler::eax);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t(int, int);
  auto target_func = exec.get<TestFunc>();
  uint32_t result = target_func(1, 2);
  if (result != 0xcccccc00) {
    ALOGE("Bug in seteq(not equal): %x", result);
    return false;
  }
  result = target_func(-1, -1);
  if (result != 0xcccccc01) {
    ALOGE("Bug in seteq(equal): %x", result);
    return false;
  }
  return true;
}

bool CondTest2() {
  MachineCode code;
  CodeEmitter as(&code);
  as.Movl(Assembler::edx, {.base = Assembler::esp, .disp = 4});  // arg1.
  as.Movl(Assembler::ecx, {.base = Assembler::esp, .disp = 8});  // arg2.
  as.Xorl(Assembler::eax, Assembler::eax);
  as.Testb(Assembler::edx, Assembler::ecx);
  as.Setcc(Assembler::Condition::kNotZero, Assembler::eax);
  as.Xchgl(Assembler::eax, Assembler::ecx);
  as.Xchgl(Assembler::ecx, Assembler::eax);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t(int, int);
  auto target_func = exec.get<TestFunc>();
  uint32_t result = target_func(0x11, 1);
  if (result != 0x1) {
    ALOGE("Bug in testb(not zero): %x", result);
    return false;
  }
  result = target_func(0x11, 0x8);
  if (result != 0x0) {
    ALOGE("Bug in testb(zero): %x", result);
    return false;
  }
  return true;
}

bool JccTest() {
  MachineCode code;
  CodeEmitter as(&code);
  Assembler::Label equal, above, below, done;
  as.Movl(Assembler::edx, {.base = Assembler::esp, .disp = 4});  // arg1.
  as.Movl(Assembler::ecx, {.base = Assembler::esp, .disp = 8});  // arg2.
  as.Cmpl(Assembler::edx, Assembler::ecx);
  as.Jcc(Assembler::Condition::kEqual, equal);
  as.Jcc(Assembler::Condition::kBelow, below);
  as.Jcc(Assembler::Condition::kAbove, above);

  as.Movl(Assembler::eax, 13);
  as.Jmp(done);

  as.Bind(&equal);
  as.Movl(Assembler::eax, 0u);
  as.Jmp(done);

  as.Bind(&below);
  as.Movl(Assembler::eax, -1);
  as.Jmp(done);

  as.Bind(&above);
  as.Movl(Assembler::eax, 1);
  as.Jmp(done);

  as.Bind(&done);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = int(int, int);
  auto target_func = exec.get<TestFunc>();
  int result = target_func(1, 1);
  if (result != 0) {
    ALOGE("Bug in jcc(equal): %x", result);
    return false;
  }
  result = target_func(1, 0);
  if (result != 1) {
    ALOGE("Bug in jcc(above): %x", result);
    return false;
  }
  result = target_func(0, 1);
  if (result != -1) {
    ALOGE("Bug in jcc(below): %x", result);
    return false;
  }
  return true;
}

bool ShiftTest() {
  MachineCode code;
  CodeEmitter as(&code);
  as.Movl(Assembler::eax, {.base = Assembler::esp, .disp = 4});
  as.Shll(Assembler::eax, int8_t{2});
  as.Shrl(Assembler::eax, int8_t{1});
  as.Movl(Assembler::ecx, 3);
  as.ShllByCl(Assembler::eax);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t(uint32_t);
  uint32_t result = exec.get<TestFunc>()(22);
  return result == (22 << 4);
}

bool LogicTest() {
  MachineCode code;
  CodeEmitter as(&code);
  as.Movl(Assembler::eax, {.base = Assembler::esp, .disp = 4});
  as.Movl(Assembler::ecx, 0x1);
  as.Xorl(Assembler::eax, Assembler::ecx);
  as.Movl(Assembler::ecx, 0xf);
  as.Andl(Assembler::eax, Assembler::ecx);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t(uint32_t);
  uint32_t result = exec.get<TestFunc>()(239);
  return result == ((239 ^ 1) & 0xf);
}

bool BsrTest() {
  MachineCode code;
  CodeEmitter as(&code);

  as.Movl(Assembler::ecx, {.base = Assembler::esp, .disp = 4});
  as.Movl(Assembler::edx, 239);
  as.Bsrl(Assembler::eax, Assembler::ecx);
  as.Cmovl(Assembler::Condition::kZero, Assembler::eax, Assembler::edx);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t(uint32_t arg);
  auto func = exec.get<TestFunc>();
  return func(0) == 239 && func(1 << 15) == 15;
}

bool CallFPTest() {
  MachineCode code;
  CodeEmitter as(&code);
  as.Push(0x3f800000);
  as.Push(0x40000000);
  as.Call(bit_cast<const void*>(&FloatFunc));
  as.Fstps({.base = Assembler::esp});
  as.Pop(Assembler::eax);
  as.Addl(Assembler::esp, 4);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t();
  uint32_t result = exec.get<TestFunc>()();
  return result == 0x3f800000;
}

bool XmmTest() {
  MachineCode code;
  CodeEmitter as(&code);
  as.Movl(Assembler::eax, 0x3f800000);
  as.Movd(Assembler::xmm0, Assembler::eax);
  as.Movl(Assembler::eax, 0x40000000);
  as.Movd(Assembler::xmm5, Assembler::eax);
  as.Addss(Assembler::xmm0, Assembler::xmm5);
  as.Movd(Assembler::eax, Assembler::xmm0);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t();
  uint32_t result = exec.get<TestFunc>()();
  return result == 0x40400000;
}

bool ReadGlobalTest() {
  MachineCode code;
  CodeEmitter as(&code);
  static const uint32_t kData[] __attribute__((aligned(16))) =  // NOLINT
      {0x00112233, 0x44556677, 0x8899aabb, 0xccddeeff};
  as.Movsd(Assembler::xmm0, {.disp = bit_cast<int32_t>(&kData)});
  as.Movdqa(Assembler::xmm1, {.disp = bit_cast<int32_t>(&kData)});
  as.Movl(Assembler::eax, {.base = Assembler::esp, .disp = 4});
  as.Movl(Assembler::ecx, {.base = Assembler::esp, .disp = 8});
  as.Movsd({.base = Assembler::eax}, Assembler::xmm0);
  as.Movdqu({.base = Assembler::ecx}, Assembler::xmm1);

  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = void(void*, void*);
  uint8_t res1[8];
  uint8_t res2[16];
  exec.get<TestFunc>()(res1, res2);

  return (memcmp(res1, kData, 8) == 0) && (memcmp(res2, kData, 16) == 0);
}

}  // namespace x86_32

#elif defined(__amd64__)

namespace x86_64 {

bool AssemblerTest() {
  MachineCode code;
  CodeEmitter assembler(&code);
  assembler.Movq(Assembler::rax, Assembler::rdi);
  assembler.Subq(Assembler::rsp, 16);
  assembler.Movq({.base = Assembler::rsp}, Assembler::rax);
  assembler.Movq({.base = Assembler::rsp, .disp = 8}, Assembler::rax);
  assembler.Movl({.base = Assembler::rax, .disp = 16}, 239);
  assembler.Movq(Assembler::r11, {.base = Assembler::rsp});
  assembler.Addq(Assembler::rsp, 16);
  assembler.Ret();
  assembler.Finalize();

  // clang-format off
  static const uint8_t code_template[] = {
    0x48, 0x89, 0xf8,               // mov %rdi, %rax
    0x48, 0x83, 0xec, 0x10,         // sub $0x10, %rsp
    0x48, 0x89, 0x04, 0x24,         // mov rax, (%rsp)
    0x48, 0x89, 0x44, 0x24, 0x08,   // mov rax, 8(%rsp)
    0xc7, 0x40, 0x10, 0xef, 0x00,   // movl $239, 0x10(%rax)
    0x00, 0x00,
    0x4c, 0x8b, 0x1c, 0x24,         // mov (%rsp), r11
    0x48, 0x83, 0xc4, 0x10,         // add $0x10, %rsp
    0xc3                            // ret
  };
  // clang-format on

  if (sizeof(code_template) != code.install_size()) {
    ALOGE("Code size mismatch: %zu != %u", sizeof(code_template), code.install_size());
    return false;
  }

  if (memcmp(code_template, code.AddrAs<uint8_t>(0), code.install_size()) != 0) {
    ALOGE("Code mismatch");
    MachineCode code2;
    code2.Add(code_template);
    std::string code_str1, code_str2;
    code.AsString(&code_str1);
    code2.AsString(&code_str2);
    ALOGE("assembler generated\n%s\nshall be\n%s", code_str1.c_str(), code_str2.c_str());
    return false;
  }
  return true;
}

bool LabelTest() {
  MachineCode code;
  CodeEmitter as(&code);
  Assembler::Label skip, skip2, back, end;
  as.Call(bit_cast<const void*>(&Callee));
  as.Jmp(skip);
  as.Movl(Assembler::rax, 2);
  as.Bind(&skip);
  as.Addb(Assembler::rax, {end});
  as.Jmp(skip2);
  as.Bind(&back);
  as.Addl(Assembler::rax, 12);
  as.Jmp(end);
  as.Bind(&skip2);
  as.Jmp(back);
  as.Bind(&end);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = int();
  int result = exec.get<TestFunc>()();
  return result == uint8_t(239 + 0xc3) + 12;
}

bool CondTest1() {
  MachineCode code;
  CodeEmitter as(&code);
  as.Movl(Assembler::rax, 0xcccccccc);
  as.Cmpl(Assembler::rdi, Assembler::rsi);
  as.Setcc(Assembler::Condition::kEqual, Assembler::rax);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  std::string code_str;
  code.AsString(&code_str);
  using TestFunc = uint32_t(int, int);
  auto target_func = exec.get<TestFunc>();
  uint32_t result;
  result = target_func(1, 2);
  if (result != 0xcccccc00) {
    ALOGE("Bug in seteq(not equal): %x", result);
    return false;
  }
  result = target_func(-1, -1);
  if (result != 0xcccccc01) {
    ALOGE("Bug in seteq(equal): %x", result);
    return false;
  }
  return true;
}

bool CondTest2() {
  MachineCode code;
  CodeEmitter as(&code);
  as.Movl(Assembler::rdx, Assembler::rdi);  // arg1.
  as.Movl(Assembler::rcx, Assembler::rsi);  // arg2.
  as.Xorl(Assembler::rax, Assembler::rax);
  as.Testb(Assembler::rdx, Assembler::rcx);
  as.Setcc(Assembler::Condition::kNotZero, Assembler::rax);
  as.Xchgq(Assembler::rax, Assembler::rcx);
  as.Xchgq(Assembler::rcx, Assembler::rax);
  as.Xchgq(Assembler::rcx, Assembler::r11);
  as.Xchgq(Assembler::r11, Assembler::rcx);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t(int, int);
  auto target_func = exec.get<TestFunc>();
  uint32_t result = target_func(0x11, 1);
  if (result != 0x1) {
    printf("Bug in testb(not zero): %x", result);
    return false;
  }
  result = target_func(0x11, 0x8);
  if (result != 0x0) {
    printf("Bug in testb(zero): %x", result);
    return false;
  }
  return true;
}

bool JccTest() {
  MachineCode code;
  CodeEmitter as(&code);
  Assembler::Label equal, above, below, done;
  as.Cmpl(Assembler::rdi, Assembler::rsi);
  as.Jcc(Assembler::Condition::kEqual, equal);
  as.Jcc(Assembler::Condition::kBelow, below);
  as.Jcc(Assembler::Condition::kAbove, above);

  as.Movl(Assembler::rax, 13);
  as.Jmp(done);

  as.Bind(&equal);
  as.Movq(Assembler::rax, 0);
  as.Jmp(done);

  as.Bind(&below);
  as.Movl(Assembler::rax, -1);
  as.Jmp(done);

  as.Bind(&above);
  as.Movl(Assembler::rax, 1);
  as.Jmp(done);

  as.Bind(&done);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = int(int, int);
  auto target_func = exec.get<TestFunc>();
  int result;
  result = target_func(1, 1);
  if (result != 0) {
    ALOGE("Bug in jcc(equal): %x", result);
    return false;
  }
  result = target_func(1, 0);
  if (result != 1) {
    ALOGE("Bug in jcc(above): %x", result);
    return false;
  }
  result = target_func(0, 1);
  if (result != -1) {
    ALOGE("Bug in jcc(below): %x", result);
    return false;
  }
  return true;
}

bool ReadWriteTest() {
  MachineCode code;
  CodeEmitter as(&code);

  as.Movq(Assembler::rax, 0);
  as.Movb(Assembler::rax, {.base = Assembler::rdi});
  as.Movl(Assembler::rcx, {.base = Assembler::rsi});
  as.Addl(Assembler::rax, Assembler::rcx);
  as.Movl({.base = Assembler::rsi}, Assembler::rax);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t(uint8_t*, uint32_t*);
  uint8_t p1[4] = {0x12, 0x34, 0x56, 0x78};
  uint32_t p2 = 0x239;
  uint32_t result = exec.get<TestFunc>()(p1, &p2);
  return (result == 0x239 + 0x12) && (p2 == result);
}

bool CallFPTest() {
  MachineCode code;
  CodeEmitter as(&code);
  as.Movl(Assembler::rax, 0x40000000);
  as.Movd(Assembler::xmm0, Assembler::rax);
  as.Movl(Assembler::rax, 0x3f800000);
  as.Movd(Assembler::xmm1, Assembler::rax);
  as.Call(bit_cast<const void*>(&FloatFunc));
  as.Movd(Assembler::rax, Assembler::xmm0);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t();
  uint32_t result = exec.get<TestFunc>()();
  return result == 0x3f800000;
}

bool XmmTest() {
  MachineCode code;
  CodeEmitter as(&code);
  as.Movl(Assembler::rax, 0x40000000);
  as.Movd(Assembler::xmm0, Assembler::rax);
  as.Movl(Assembler::rax, 0x3f800000);
  as.Movd(Assembler::xmm11, Assembler::rax);
  as.Addss(Assembler::xmm0, Assembler::xmm11);
  as.Movaps(Assembler::xmm12, Assembler::xmm0);
  as.Addss(Assembler::xmm0, Assembler::xmm12);
  as.Movapd(Assembler::xmm14, Assembler::xmm1);
  as.Movd(Assembler::rax, Assembler::xmm0);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t();
  uint32_t result = exec.get<TestFunc>()();
  return result == 0x40c00000;
}

bool XmmMemTest() {
  MachineCode code;
  CodeEmitter as(&code);

  as.Movsd(Assembler::xmm0, {.base = Assembler::rdi});
  as.Movaps(Assembler::xmm12, Assembler::xmm0);
  as.Addsd(Assembler::xmm12, Assembler::xmm12);
  as.Movsd({.base = Assembler::rdi}, Assembler::xmm12);
  as.Movq(Assembler::rax, Assembler::xmm0);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  double d = 239.0;
  char bits[16], *p = bits + 5;
  memcpy(p, &d, sizeof(d));

  using TestFunc = uint64_t(char* p);
  uint64_t result = exec.get<TestFunc>()(p);
  uint64_t doubled = *reinterpret_cast<uint64_t*>(p);
  return result == 0x406de00000000000ULL && doubled == 0x407de00000000000ULL;
}

bool MovsxblRexTest() {
  MachineCode code;
  CodeEmitter as(&code);

  as.Xorl(Assembler::rdx, Assembler::rdx);
  as.Movl(Assembler::rsi, 0xdeadff);
  // CodeEmitter should use REX prefix to encode SIL.
  // Without REX DH is used.
  as.Movsxbl(Assembler::rax, Assembler::rsi);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t();
  uint32_t result = exec.get<TestFunc>()();

  return result == 0xffffffff;
}

bool MovzxblRexTest() {
  MachineCode code;
  CodeEmitter as(&code);

  as.Xorl(Assembler::rdx, Assembler::rdx);
  as.Movl(Assembler::rsi, 0xdeadff);
  // CodeEmitter should use REX prefix to encode SIL.
  // Without REX DH is used.
  as.Movzxbl(Assembler::rax, Assembler::rsi);
  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t();
  uint32_t result = exec.get<TestFunc>()();

  return result == 0x000000ff;
}

bool ShldlRexTest() {
  MachineCode code;
  CodeEmitter as(&code);

  as.Movl(Assembler::rdx, 0x12345678);
  // If most-significant bit is not encoded correctly with REX
  // RAX can be used instead R8 and R10 can be used instead RDX.
  // Init them all:
  as.Xorl(Assembler::r8, Assembler::r8);
  as.Movl(Assembler::rax, 0xdeadbeef);
  as.Movl(Assembler::r10, 0xdeadbeef);

  as.Shldl(Assembler::r8, Assembler::rdx, int8_t{8});
  as.Movl(Assembler::rcx, 8);
  as.ShldlByCl(Assembler::r8, Assembler::rdx);

  as.Movl(Assembler::rax, Assembler::r8);

  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t();
  uint32_t result = exec.get<TestFunc>()();

  return result == 0x1212;
}

bool ShrdlRexTest() {
  MachineCode code;
  CodeEmitter as(&code);

  as.Movl(Assembler::rdx, 0x12345678);
  // If most-significant bit is not encoded correctly with REX
  // RAX can be used instead R8 and R10 can be used instead RDX.
  // Init them all:
  as.Xorl(Assembler::r8, Assembler::r8);
  as.Movl(Assembler::rax, 0xdeadbeef);
  as.Movl(Assembler::r10, 0xdeadbeef);

  as.Shrdl(Assembler::r8, Assembler::rdx, int8_t{8});
  as.Movl(Assembler::rcx, 8);
  as.ShrdlByCl(Assembler::r8, Assembler::rdx);

  as.Movl(Assembler::rax, Assembler::r8);

  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = uint32_t();
  uint32_t result = exec.get<TestFunc>()();

  return result == 0x78780000;
}

bool ReadGlobalTest() {
  MachineCode code;
  CodeEmitter as(&code);
  static const uint32_t kData[] __attribute__((aligned(16))) =  // NOLINT
      {0x00112233, 0x44556677, 0x8899aabb, 0xccddeeff};
  // We couldn't read data from arbitrary address on x86_64, need address in first 2GiB.
  void* data =
      mmap(nullptr, 4096, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_32BIT, -1, 0);
  // Copy our global there.
  memcpy(data, kData, 16);
  int32_t data_offset = static_cast<int32_t>(bit_cast<intptr_t>(data));
  as.Movsd(Assembler::xmm0, {.disp = data_offset});
  as.Movdqa(Assembler::xmm1, {.disp = data_offset});
  as.Movsd({.base = Assembler::rdi}, Assembler::xmm0);
  as.Movdqu({.base = Assembler::rsi}, Assembler::xmm1);

  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = void(void*, void*);
  uint8_t res1[8];
  uint8_t res2[16];
  exec.get<TestFunc>()(res1, res2);

  munmap(data, 4096);

  return (memcmp(res1, kData, 8) == 0) && (memcmp(res2, kData, 16) == 0);
}

bool MemShiftTest() {
  MachineCode code;
  CodeEmitter as(&code);

  as.Push(Assembler::rdi);
  as.Movl(Assembler::rcx, 1);
  as.ShrlByCl({.base = Assembler::rsp});
  as.Addl(Assembler::rcx, 1);
  as.Movq(Assembler::rdi, Assembler::rsp);
  as.ShllByCl({.base = Assembler::rdi});
  as.Pop(Assembler::rax);

  as.Ret();
  as.Finalize();

  ScopedExecRegion exec(&code);

  using TestFunc = int(int x);
  int result = exec.get<TestFunc>()(0x10);

  return result == 0x20;
}

}  // namespace x86_64

#endif

#if defined(__i386__) || defined(__amd64__)

#if defined(__i386__)

extern "C" const uint8_t berberis_gnu_as_output_start[] asm(
    "berberis_gnu_as_output_start_x86_32");
extern "C" const uint8_t berberis_gnu_as_output_end[] asm(
    "berberis_gnu_as_output_end_x86_32");

namespace x86_32 {
void GenInsnsCommon(CodeEmitter* as);
void GenInsnsArch(CodeEmitter* as);
}  // namespace x86_32

#else

extern "C" const uint8_t berberis_gnu_as_output_start[] asm(
    "berberis_gnu_as_output_start_x86_64");
extern "C" const uint8_t berberis_gnu_as_output_end[] asm(
    "berberis_gnu_as_output_end_x86_64");

namespace x86_64 {
void GenInsnsCommon(CodeEmitter* as);
void GenInsnsArch(CodeEmitter* as);
}  // namespace x86_64

#endif

bool ExhaustiveTest() {
  MachineCode code;
  CodeEmitter as(&code);

#if defined(__i386__)
  berberis::x86_32::GenInsnsCommon(&as);
  berberis::x86_32::GenInsnsArch(&as);
#else
  berberis::x86_64::GenInsnsCommon(&as);
  berberis::x86_64::GenInsnsArch(&as);
#endif
  as.Finalize();

  return CompareCode(berberis_gnu_as_output_start, berberis_gnu_as_output_end, code);
}

bool MixedAssembler() {
  MachineCode code;
  x86_32::Assembler as32(&code);
  x86_64::Assembler as64(&code);
  x86_32::Assembler::Label lbl32;
  x86_64::Assembler::Label lbl64;

  as32.Jmp(lbl32);
  as32.Xchgl(x86_32::Assembler::eax, x86_32::Assembler::eax);
  as64.Jmp(lbl64);
  as64.Xchgl(x86_64::Assembler::rax, x86_64::Assembler::rax);
  as32.Bind(&lbl32);
  as32.Movl(x86_32::Assembler::eax, {.disp = 0});
  as64.Bind(&lbl64);
  as32.Finalize();
  as64.Finalize();

  // clang-format off
  static const uint8_t code_template[] = {
    0xe9, 0x08, 0x00, 0x00, 0x00,              // jmp lbl32
    0x90,                                      // xchg %eax, %eax == nop
    0xe9, 0x07, 0x00, 0x00, 0x00,              // jmp lbl64
    0x87, 0xc0,                                // xchg %eax, %eax != nop
                                               // lbl32:
    0xa1, 0x00, 0x00, 0x00, 0x00               // movabs %eax, 0x0
                                               // lbl64:
  };
  // clang-format on

  return CompareCode(std::begin(code_template), std::end(code_template), code);
}
#endif

}  // namespace berberis

TEST(Assembler, AssemblerTest) {
#if defined(__i386__)
  EXPECT_TRUE(berberis::x86_32::AssemblerTest());
  EXPECT_TRUE(berberis::x86_32::LabelTest());
  EXPECT_TRUE(berberis::x86_32::CondTest1());
  EXPECT_TRUE(berberis::x86_32::CondTest2());
  EXPECT_TRUE(berberis::x86_32::JccTest());
  EXPECT_TRUE(berberis::x86_32::ShiftTest());
  EXPECT_TRUE(berberis::x86_32::LogicTest());
  EXPECT_TRUE(berberis::x86_32::CallFPTest());
  EXPECT_TRUE(berberis::x86_32::XmmTest());
  EXPECT_TRUE(berberis::x86_32::BsrTest());
  EXPECT_TRUE(berberis::x86_32::ReadGlobalTest());
  EXPECT_TRUE(berberis::ExhaustiveTest());
  EXPECT_TRUE(berberis::MixedAssembler());
#elif defined(__amd64__)
  EXPECT_TRUE(berberis::x86_64::AssemblerTest());
  EXPECT_TRUE(berberis::x86_64::LabelTest());
  EXPECT_TRUE(berberis::x86_64::CondTest1());
  EXPECT_TRUE(berberis::x86_64::CondTest2());
  EXPECT_TRUE(berberis::x86_64::JccTest());
  EXPECT_TRUE(berberis::x86_64::ReadWriteTest());
  EXPECT_TRUE(berberis::x86_64::CallFPTest());
  EXPECT_TRUE(berberis::x86_64::XmmTest());
  EXPECT_TRUE(berberis::x86_64::XmmMemTest());
  EXPECT_TRUE(berberis::x86_64::MovsxblRexTest());
  EXPECT_TRUE(berberis::x86_64::MovzxblRexTest());
  EXPECT_TRUE(berberis::x86_64::ShldlRexTest());
  EXPECT_TRUE(berberis::x86_64::ShrdlRexTest());
  EXPECT_TRUE(berberis::x86_64::ReadGlobalTest());
  EXPECT_TRUE(berberis::x86_64::MemShiftTest());
  EXPECT_TRUE(berberis::ExhaustiveTest());
  EXPECT_TRUE(berberis::MixedAssembler());
#endif
}
