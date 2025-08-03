/*
 * Copyright (C) 2023 The Android Open Source Project
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

// Machine IR public interface.

#ifndef BERBERIS_BACKEND_COMMON_MACHINE_IR_H_
#define BERBERIS_BACKEND_COMMON_MACHINE_IR_H_

#include <climits>  // CHAR_BIT
#include <cstddef>
#include <cstdint>
#include <limits>
#include <string>

#include "berberis/backend/code_emitter.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/base/arena_list.h"
#include "berberis/base/arena_vector.h"
#include "berberis/base/checks.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

// MachineReg is a machine instruction argument meaningful for optimizations and
// register allocation. It can be:
// - virtual register:  [1024, +inf)
// - hard register:     [1, 1024)
// - invalid/undefined: 0
// - (reserved):        (-1024, -1]
// - spilled register:  (-inf, -1024]
class MachineReg {
 public:
  // Creates an invalid machine register.
  constexpr MachineReg() : reg_{kInvalidMachineVRegNumber} {}
  constexpr explicit MachineReg(int reg) : reg_{reg} {}
  constexpr MachineReg(const MachineReg&) = default;
  constexpr MachineReg& operator=(const MachineReg&) = default;

  constexpr MachineReg(MachineReg&&) = default;
  constexpr MachineReg& operator=(MachineReg&&) = default;

  [[nodiscard]] constexpr int reg() const { return reg_; }

  [[nodiscard]] constexpr bool IsSpilledReg() const { return reg_ <= kLastSpilledRegNumber; }

  [[nodiscard]] constexpr bool IsHardReg() const {
    return reg_ > kInvalidMachineVRegNumber && reg_ < kFirstVRegNumber;
  }

  [[nodiscard]] constexpr bool IsInvalidReg() const { return reg_ == kInvalidMachineVRegNumber; }

  [[nodiscard]] constexpr bool IsVReg() const { return reg_ >= kFirstVRegNumber; }

  [[nodiscard]] constexpr uint32_t GetVRegIndex() const {
    CHECK_GE(reg_, kFirstVRegNumber);
    return reg_ - kFirstVRegNumber;
  }

  [[nodiscard]] constexpr uint32_t GetSpilledRegIndex() const {
    CHECK_LE(reg_, kLastSpilledRegNumber);
    return kLastSpilledRegNumber - reg_;
  }

  constexpr friend bool operator==(MachineReg left, MachineReg right) {
    return left.reg_ == right.reg_;
  }

  constexpr friend bool operator!=(MachineReg left, MachineReg right) { return !(left == right); }

  [[nodiscard]] static constexpr MachineReg CreateVRegFromIndex(uint32_t index) {
    CHECK_LE(index, std::numeric_limits<int>::max() - kFirstVRegNumber);
    return MachineReg{kFirstVRegNumber + static_cast<int>(index)};
  }

  [[nodiscard]] static constexpr MachineReg CreateSpilledRegFromIndex(uint32_t index) {
    CHECK_LE(index, -(std::numeric_limits<int>::min() - kLastSpilledRegNumber));
    return MachineReg{kLastSpilledRegNumber - static_cast<int>(index)};
  }

  [[nodiscard]] static constexpr int GetFirstVRegNumberForTesting() { return kFirstVRegNumber; }

  [[nodiscard]] static constexpr int GetLastSpilledRegNumberForTesting() {
    return kLastSpilledRegNumber;
  }

 private:
  static constexpr int kFirstVRegNumber = 1024;
  static constexpr int kInvalidMachineVRegNumber = 0;
  static constexpr int kLastSpilledRegNumber = -1024;

  int reg_;
};

constexpr MachineReg kInvalidMachineReg{0};

[[nodiscard]] const char* GetMachineHardRegDebugName(MachineReg r);
[[nodiscard]] std::string GetMachineRegDebugString(MachineReg r);

using MachineRegVector = ArenaVector<MachineReg>;

// Set of registers, ordered by allocation preference.
// This is a struct to avoid static initializers.
// TODO(b/232598137) See if there's a way to use a class here. const array init
// (regs member) in constexpr context is the main challenge.
struct MachineRegClass {
  const char* debug_name;
  int reg_size;
  uint64_t reg_mask;
  int num_regs;
  const MachineReg regs[sizeof(reg_mask) * CHAR_BIT];

  [[nodiscard]] constexpr int RegSize() const { return reg_size; }

  [[nodiscard]] bool HasReg(MachineReg r) const { return reg_mask & (uint64_t{1} << r.reg()); }

  [[nodiscard]] bool IsSubsetOf(const MachineRegClass* other) const {
    return (reg_mask & other->reg_mask) == reg_mask;
  }

  [[nodiscard]] const MachineRegClass* GetIntersection(const MachineRegClass* other) const {
    // At the moment, only handle the case when one class is a subset of other.
    // In most real-life cases reg classes form a tree, so this is good enough.
    auto mask = reg_mask & other->reg_mask;
    if (mask == reg_mask) {
      return this;
    }
    if (mask == other->reg_mask) {
      return other;
    }
    return nullptr;
  }

  [[nodiscard]] constexpr int NumRegs() const { return num_regs; }

  [[nodiscard]] MachineReg RegAt(int i) const { return regs[i]; }

  [[nodiscard]] const MachineReg* begin() const { return &regs[0]; }
  [[nodiscard]] const MachineReg* end() const { return &regs[num_regs]; }

  [[nodiscard]] const char* GetDebugName() const { return debug_name; }
};

class MachineRegKind {
 private:
  enum { kRegisterIsUsed = 0x01, kRegisterIsDefined = 0x02, kRegisterIsInput = 0x04 };

 public:
  enum StandardAccess {
    kUse = kRegisterIsUsed | kRegisterIsInput,
    kDef = kRegisterIsDefined,
    kUseDef = kUse | kDef,
    // Note: in kDefEarlyClobber, register is Used and Defined, but it's not an input!
    kDefEarlyClobber = kRegisterIsUsed | kRegisterIsDefined
  };

  // We need default constructor to initialize arrays
  constexpr MachineRegKind() : reg_class_(nullptr), access_(StandardAccess(0)) {}
  constexpr MachineRegKind(const MachineRegClass* reg_class, StandardAccess access)
      : reg_class_(reg_class), access_(access) {}

  [[nodiscard]] constexpr const MachineRegClass* RegClass() const { return reg_class_; }

  [[nodiscard]] constexpr bool IsUse() const { return access_ & kRegisterIsUsed; }

  [[nodiscard]] constexpr bool IsDef() const { return access_ & kRegisterIsDefined; }

  // IsInput means that register must contain some kind of valid value and is not just used early.
  // This allows us to distinguish between UseDef and DefEarlyClobber.
  [[nodiscard]] constexpr bool IsInput() const { return access_ & kRegisterIsInput; }

 private:
  const MachineRegClass* reg_class_;
  enum StandardAccess access_;
};

class MachineBasicBlock;

// Machine insn kind meaningful for optimizations and register allocation.
enum MachineInsnKind {
  kMachineInsnDefault = 0,
  kMachineInsnSideEffects,  // never dead
  kMachineInsnCopy,         // can be deleted if dst == src
};

enum MachineOpcode : int;

class MachineInsn {
 public:
  virtual ~MachineInsn() {
    // No code here - will never be called!
  }

  [[nodiscard]] virtual std::string GetDebugString() const = 0;
  virtual void Emit(CodeEmitter* as) const = 0;

  [[nodiscard]] MachineOpcode opcode() const { return opcode_; };

  [[nodiscard]] int NumRegOperands() const { return num_reg_operands_; }

  [[nodiscard]] const MachineRegKind& RegKindAt(int i) const { return reg_kinds_[i]; }

  [[nodiscard]] MachineReg RegAt(int i) const {
    CHECK_LT(i, num_reg_operands_);
    return regs_[i];
  }

  void SetRegAt(int i, MachineReg reg) {
    CHECK_LT(i, num_reg_operands_);
    regs_[i] = reg;
  }

  [[nodiscard]] bool has_side_effects() const {
    return (kind_ == kMachineInsnSideEffects) || recovery_info_.bb ||
           (recovery_info_.pc != kNullGuestAddr);
  }

  [[nodiscard]] bool is_copy() const { return kind_ == kMachineInsnCopy; }

  [[nodiscard]] const MachineBasicBlock* recovery_bb() const { return recovery_info_.bb; }

  void set_recovery_bb(const MachineBasicBlock* bb) { recovery_info_.bb = bb; }

  [[nodiscard]] GuestAddr recovery_pc() const { return recovery_info_.pc; }

  void set_recovery_pc(GuestAddr pc) { recovery_info_.pc = pc; }

 protected:
  MachineInsn(MachineOpcode opcode,
              int num_reg_operands,
              const MachineRegKind* reg_kinds,
              MachineReg* regs,
              MachineInsnKind kind)
      : opcode_(opcode),
        num_reg_operands_(num_reg_operands),
        reg_kinds_(reg_kinds),
        regs_(regs),
        kind_(kind),
        recovery_info_{nullptr, kNullGuestAddr} {}

 private:
  // We either recover by building explicit recovery blocks or by storing recovery pc.
  // TODO(b/200327919): Convert this to union? We'll need to know which one is used during
  // initialization and in has_side_effects.
  struct RecoveryInfo {
    const MachineBasicBlock* bb;
    GuestAddr pc;
  };
  const MachineOpcode opcode_;
  const int num_reg_operands_;
  const MachineRegKind* reg_kinds_;
  MachineReg* regs_;
  MachineInsnKind kind_;
  RecoveryInfo recovery_info_;
};

std::string GetRegOperandDebugString(const MachineInsn* insn, int i);

using MachineInsnList = ArenaList<MachineInsn*>;

class MachineInsnListPosition {
 public:
  MachineInsnListPosition(MachineInsnList* list, MachineInsnList::iterator iterator)
      : list_(list), iterator_(iterator) {}

  [[nodiscard]] MachineInsn* insn() const { return *iterator_; }

  void InsertBefore(MachineInsn* insn) const { list_->insert(iterator_, insn); }

  void InsertAfter(MachineInsn* insn) const {
    MachineInsnList::iterator next_iterator = iterator_;
    list_->insert(++next_iterator, insn);
  }

 private:
  MachineInsnList* list_;
  const MachineInsnList::iterator iterator_;
};

class MachineEdge {
 public:
  MachineEdge(Arena* arena, MachineBasicBlock* src, MachineBasicBlock* dst)
      : src_(src), dst_(dst), insn_list_(arena) {}

  void set_src(MachineBasicBlock* bb) { src_ = bb; }
  void set_dst(MachineBasicBlock* bb) { dst_ = bb; }

  [[nodiscard]] MachineBasicBlock* src() const { return src_; }
  [[nodiscard]] MachineBasicBlock* dst() const { return dst_; }

  [[nodiscard]] const MachineInsnList& insn_list() const { return insn_list_; }
  [[nodiscard]] MachineInsnList& insn_list() { return insn_list_; }

 private:
  MachineBasicBlock* src_;
  MachineBasicBlock* dst_;
  MachineInsnList insn_list_;
};

using MachineEdgeVector = ArenaVector<MachineEdge*>;

class MachineBasicBlock {
 public:
  MachineBasicBlock(Arena* arena, uint32_t id)
      : id_(id),
        insn_list_(arena),
        in_edges_(arena),
        out_edges_(arena),
        live_in_(arena),
        live_out_(arena),
        is_recovery_(false) {}

  [[nodiscard]] uint32_t id() const { return id_; }

  [[nodiscard]] const MachineInsnList& insn_list() const { return insn_list_; }
  [[nodiscard]] MachineInsnList& insn_list() { return insn_list_; }

  [[nodiscard]] const MachineEdgeVector& in_edges() const { return in_edges_; }
  [[nodiscard]] MachineEdgeVector& in_edges() { return in_edges_; }

  [[nodiscard]] const MachineEdgeVector& out_edges() const { return out_edges_; }
  [[nodiscard]] MachineEdgeVector& out_edges() { return out_edges_; }

  [[nodiscard]] const MachineRegVector& live_in() const { return live_in_; }
  [[nodiscard]] MachineRegVector& live_in() { return live_in_; }

  [[nodiscard]] const MachineRegVector& live_out() const { return live_out_; }
  [[nodiscard]] MachineRegVector& live_out() { return live_out_; }

  void MarkAsRecovery() { is_recovery_ = true; }

  [[nodiscard]] bool is_recovery() const { return is_recovery_; }

  [[nodiscard]] std::string GetDebugString() const;

 private:
  const uint32_t id_;
  MachineInsnList insn_list_;
  MachineEdgeVector in_edges_;
  MachineEdgeVector out_edges_;
  MachineRegVector live_in_;
  MachineRegVector live_out_;
  bool is_recovery_;
};

using MachineBasicBlockList = ArenaList<MachineBasicBlock*>;

class MachineIR {
 public:
  // First num_vreg virtual register numbers are reserved for custom use
  // in the derived class, numbers above that can be used for scratches.
  MachineIR(Arena* arena, int num_vreg, uint32_t num_bb)
      : num_bb_(num_bb),
        arena_(arena),
        num_vreg_(num_vreg),
        num_arg_slots_(0),
        num_spill_slots_(0),
        bb_list_(arena) {}

  [[nodiscard]] int NumVReg() const { return num_vreg_; }

  [[nodiscard]] MachineReg AllocVReg() { return MachineReg::CreateVRegFromIndex(num_vreg_++); }

  [[nodiscard]] uint32_t ReserveBasicBlockId() { return num_bb_++; }

  // Stack frame layout is:
  //     [arg slots][spill slots]
  //     ^--- stack pointer
  //
  // Arg slots are for stack frame part that require a fixed offset from the
  // stack pointer, in particular for call arguments passed on the stack.
  // Spill slots are for spilled registers.
  // Each slot is 16-bytes, and the stack pointer is always 16-bytes aligned.
  //
  // TODO(b/232598137): If we need a custom stack layout for an architecture,
  // implement the following functions specifically for each architecture.

  void ReserveArgs(uint32_t size) {
    uint32_t slots = (size + 15) / 16;
    if (num_arg_slots_ < slots) {
      num_arg_slots_ = slots;
    }
  }

  [[nodiscard]] uint32_t AllocSpill() { return num_spill_slots_++; }

  [[nodiscard]] uint32_t SpillSlotOffset(uint32_t slot) const {
    return 16 * (num_arg_slots_ + slot);
  }

  [[nodiscard]] uint32_t FrameSize() const { return 16 * (num_arg_slots_ + num_spill_slots_); }

  [[nodiscard]] size_t NumBasicBlocks() const { return num_bb_; }

  [[nodiscard]] const MachineBasicBlockList& bb_list() const { return bb_list_; }

  [[nodiscard]] MachineBasicBlockList& bb_list() { return bb_list_; }

  [[nodiscard]] std::string GetDebugString() const;

  [[nodiscard]] std::string GetDebugStringForDot() const;

  void Emit(CodeEmitter* as) const;

  [[nodiscard]] Arena* arena() const { return arena_; }

  template <typename T, typename... Args>
  [[nodiscard]] T* NewInsn(Args... args) {
    return NewInArena<T>(arena(), args...);
  }

 private:
  // Basic block number is useful when allocating analytical data
  // structures indexed by IDs. Note that the return value of this function is
  // not necessarily equal to bb_list().size() since some basic blocks may not
  // be enrolled in this list.
  // This can be set in ctor or managed in the derived class. It's the derived
  // class's responsibility to guarantee that max basic block ID is less than
  // this number.
  uint32_t num_bb_;

 private:
  Arena* const arena_;
  int num_vreg_;
  uint32_t num_arg_slots_;    // 16-byte slots for call args/results
  uint32_t num_spill_slots_;  // 16-byte slots for spilled registers
  MachineBasicBlockList bb_list_;
};

class PseudoBranch : public MachineInsn {
 public:
  static const MachineOpcode kOpcode;

  explicit PseudoBranch(const MachineBasicBlock* then_bb);

  std::string GetDebugString() const override;
  void Emit(CodeEmitter* as) const override;

  const MachineBasicBlock* then_bb() const { return then_bb_; }
  void set_then_bb(const MachineBasicBlock* then_bb) { then_bb_ = then_bb; }

 private:
  const MachineBasicBlock* then_bb_;
};

class PseudoCondBranch : public MachineInsn {
 public:
  static const MachineOpcode kOpcode;

  PseudoCondBranch(CodeEmitter::Condition cond,
                   const MachineBasicBlock* then_bb,
                   const MachineBasicBlock* else_bb,
                   MachineReg eflags);

  std::string GetDebugString() const override;
  void Emit(CodeEmitter* as) const override;

  CodeEmitter::Condition cond() const { return cond_; }
  void set_cond(CodeEmitter::Condition cond) { cond_ = cond; }
  const MachineBasicBlock* then_bb() const { return then_bb_; }
  const MachineBasicBlock* else_bb() const { return else_bb_; }
  void set_then_bb(const MachineBasicBlock* then_bb) { then_bb_ = then_bb; }
  void set_else_bb(const MachineBasicBlock* else_bb) { else_bb_ = else_bb; }
  MachineReg eflags() const { return eflags_; }

 private:
  CodeEmitter::Condition cond_;
  const MachineBasicBlock* then_bb_;
  const MachineBasicBlock* else_bb_;
  MachineReg eflags_;
};

class PseudoJump : public MachineInsn {
 public:
  enum class Kind {
    kJumpWithPendingSignalsCheck,
    kJumpWithoutPendingSignalsCheck,
    kExitGeneratedCode,
    kSyscall,
  };

  PseudoJump(GuestAddr target, Kind kind = Kind::kJumpWithPendingSignalsCheck);

  std::string GetDebugString() const override;
  void Emit(CodeEmitter* as) const override;

  GuestAddr target() const { return target_; }
  Kind kind() const { return kind_; }

 private:
  GuestAddr target_;
  Kind kind_;
};

class PseudoIndirectJump : public MachineInsn {
 public:
  explicit PseudoIndirectJump(MachineReg src);

  [[nodiscard]] std::string GetDebugString() const override;
  void Emit(CodeEmitter* as) const override;

 private:
  MachineReg src_;
};

// Copy the value of given size between registers/memory.
// Register class of operands is anything capable of keeping values of this
// size.
// ATTENTION: this insn has operands with variable register class!
class PseudoCopy : public MachineInsn {
 public:
  static const MachineOpcode kOpcode;

  PseudoCopy(MachineReg dst, MachineReg src, int size);

  std::string GetDebugString() const override;
  void Emit(CodeEmitter* as) const override;

 private:
  MachineReg regs_[2];
};

// Some instructions have use-def operands, but for the semantics of our IR are really def-only,
// so we use this auxiliary instruction to ensure data-flow is integral (required by some phases
// including register allocation), but we do not emit it.
//
// Example: PmovsxwdXRegXReg followed by MovlhpsXRegXReg
// Example: xor rax, rax
class PseudoDefXReg : public MachineInsn {
 public:
  explicit PseudoDefXReg(MachineReg reg);

  [[nodiscard]] std::string GetDebugString() const override;
  void Emit(CodeEmitter* /*as*/) const override {
    // It's an auxiliary instruction. Does not emit.
  }

 private:
  MachineReg reg_;
};

class PseudoDefReg : public MachineInsn {
 public:
  explicit PseudoDefReg(MachineReg reg);

  [[nodiscard]] std::string GetDebugString() const override;
  void Emit(CodeEmitter* /*as*/) const override {
    // It's an auxiliary instruction. Does not emit.
  }

 private:
  MachineReg reg_;
};

class PseudoReadFlags : public MachineInsn {
 public:
  static const MachineOpcode kOpcode;

  // Syntax sugar to avoid anonymous bool during construction on caller side.
  enum WithOverflowEnum { kWithOverflow, kWithoutOverflow };

  // Flags in LAHF-compatible format.
  enum Flags : uint16_t {
    kNegative = 1 << 15,
    kZero = 1 << 14,
    kCarry = 1 << 8,
    kOverflow = 1,
  };

  PseudoReadFlags(WithOverflowEnum with_overflow, MachineReg dst, MachineReg flags);

  std::string GetDebugString() const override;
  void Emit(CodeEmitter* as) const override;

  bool with_overflow() const { return with_overflow_; };

 private:
  MachineReg regs_[2];
  bool with_overflow_;
};

class PseudoWriteFlags : public MachineInsn {
 public:
  static const MachineOpcode kOpcode;

  using Flags = PseudoReadFlags::Flags;

  PseudoWriteFlags(MachineReg src, MachineReg flags);

  std::string GetDebugString() const override;
  void Emit(CodeEmitter* as) const override;

 private:
  MachineReg regs_[2];
};

}  // namespace berberis

#endif  // BERBERIS_BACKEND_COMMON_MACHINE_IR_H_
