/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "scheduler_arm64.h"

#include "code_generator_utils.h"
#include "mirror/array-inl.h"
#include "mirror/string.h"

namespace art HIDDEN {
namespace arm64 {

static constexpr uint32_t kArm64MemoryLoadLatency = 5;
static constexpr uint32_t kArm64MemoryStoreLatency = 3;

static constexpr uint32_t kArm64CallInternalLatency = 10;
static constexpr uint32_t kArm64CallLatency = 5;

// AArch64 instruction latency.
// We currently assume that all arm64 CPUs share the same instruction latency list.
static constexpr uint32_t kArm64IntegerOpLatency = 2;
static constexpr uint32_t kArm64FloatingPointOpLatency = 5;

static constexpr uint32_t kArm64DataProcWithShifterOpLatency = 3;
static constexpr uint32_t kArm64DivDoubleLatency = 30;
static constexpr uint32_t kArm64DivFloatLatency = 15;
static constexpr uint32_t kArm64DivIntegerLatency = 5;
static constexpr uint32_t kArm64LoadStringInternalLatency = 7;
static constexpr uint32_t kArm64MulFloatingPointLatency = 6;
static constexpr uint32_t kArm64MulIntegerLatency = 6;
static constexpr uint32_t kArm64TypeConversionFloatingPointIntegerLatency = 5;
static constexpr uint32_t kArm64BranchLatency = kArm64IntegerOpLatency;

static constexpr uint32_t kArm64SIMDFloatingPointOpLatency = 10;
static constexpr uint32_t kArm64SIMDIntegerOpLatency = 6;
static constexpr uint32_t kArm64SIMDMemoryLoadLatency = 10;
static constexpr uint32_t kArm64SIMDMemoryStoreLatency = 6;
static constexpr uint32_t kArm64SIMDMulFloatingPointLatency = 12;
static constexpr uint32_t kArm64SIMDMulIntegerLatency = 12;
static constexpr uint32_t kArm64SIMDReplicateOpLatency = 16;
static constexpr uint32_t kArm64SIMDDivDoubleLatency = 60;
static constexpr uint32_t kArm64SIMDDivFloatLatency = 30;
static constexpr uint32_t kArm64SIMDTypeConversionInt2FPLatency = 10;

class SchedulingLatencyVisitorARM64 final : public SchedulingLatencyVisitor {
 public:
  // Default visitor for instructions not handled specifically below.
  void VisitInstruction([[maybe_unused]] HInstruction*) override {
    last_visited_latency_ = kArm64IntegerOpLatency;
  }

// We add a second unused parameter to be able to use this macro like the others
// defined in `nodes.h`.
#define FOR_EACH_SCHEDULED_COMMON_INSTRUCTION(M)     \
  M(ArrayGet             , unused)                   \
  M(ArrayLength          , unused)                   \
  M(ArraySet             , unused)                   \
  M(BoundsCheck          , unused)                   \
  M(Div                  , unused)                   \
  M(InstanceFieldGet     , unused)                   \
  M(InstanceOf           , unused)                   \
  M(LoadString           , unused)                   \
  M(Mul                  , unused)                   \
  M(NewArray             , unused)                   \
  M(NewInstance          , unused)                   \
  M(Rem                  , unused)                   \
  M(StaticFieldGet       , unused)                   \
  M(SuspendCheck         , unused)                   \
  M(TypeConversion       , unused)                   \
  M(VecReplicateScalar   , unused)                   \
  M(VecExtractScalar     , unused)                   \
  M(VecReduce            , unused)                   \
  M(VecCnv               , unused)                   \
  M(VecNeg               , unused)                   \
  M(VecAbs               , unused)                   \
  M(VecNot               , unused)                   \
  M(VecAdd               , unused)                   \
  M(VecHalvingAdd        , unused)                   \
  M(VecSub               , unused)                   \
  M(VecMul               , unused)                   \
  M(VecDiv               , unused)                   \
  M(VecMin               , unused)                   \
  M(VecMax               , unused)                   \
  M(VecAnd               , unused)                   \
  M(VecAndNot            , unused)                   \
  M(VecOr                , unused)                   \
  M(VecXor               , unused)                   \
  M(VecShl               , unused)                   \
  M(VecShr               , unused)                   \
  M(VecUShr              , unused)                   \
  M(VecSetScalars        , unused)                   \
  M(VecMultiplyAccumulate, unused)                   \
  M(VecLoad              , unused)                   \
  M(VecStore             , unused)

#define FOR_EACH_SCHEDULED_ABSTRACT_INSTRUCTION(M)   \
  M(BinaryOperation      , unused)                   \
  M(Invoke               , unused)

#define FOR_EACH_SCHEDULED_SHARED_INSTRUCTION(M) \
  M(BitwiseNegatedRight, unused)                 \
  M(MultiplyAccumulate, unused)                  \
  M(IntermediateAddress, unused)                 \
  M(IntermediateAddressIndex, unused)            \
  M(DataProcWithShifterOp, unused)

#define DECLARE_VISIT_INSTRUCTION(type, unused)  \
  void Visit##type(H##type* instruction) override;

  FOR_EACH_SCHEDULED_COMMON_INSTRUCTION(DECLARE_VISIT_INSTRUCTION)
  FOR_EACH_SCHEDULED_ABSTRACT_INSTRUCTION(DECLARE_VISIT_INSTRUCTION)
  FOR_EACH_SCHEDULED_SHARED_INSTRUCTION(DECLARE_VISIT_INSTRUCTION)
  FOR_EACH_CONCRETE_INSTRUCTION_ARM64(DECLARE_VISIT_INSTRUCTION)

#undef DECLARE_VISIT_INSTRUCTION

 private:
  void HandleSimpleArithmeticSIMD(HVecOperation *instr);
  void HandleVecAddress(HVecMemoryOperation* instruction, size_t size);
};

void SchedulingLatencyVisitorARM64::VisitBinaryOperation(HBinaryOperation* instr) {
  last_visited_latency_ = DataType::IsFloatingPointType(instr->GetResultType())
      ? kArm64FloatingPointOpLatency
      : kArm64IntegerOpLatency;
}

void SchedulingLatencyVisitorARM64::VisitBitwiseNegatedRight(
    [[maybe_unused]] HBitwiseNegatedRight*) {
  last_visited_latency_ = kArm64IntegerOpLatency;
}

void SchedulingLatencyVisitorARM64::VisitDataProcWithShifterOp(
    [[maybe_unused]] HDataProcWithShifterOp*) {
  last_visited_latency_ = kArm64DataProcWithShifterOpLatency;
}

void SchedulingLatencyVisitorARM64::VisitIntermediateAddress(
    [[maybe_unused]] HIntermediateAddress*) {
  // Although the code generated is a simple `add` instruction, we found through empirical results
  // that spacing it from its use in memory accesses was beneficial.
  last_visited_latency_ = kArm64IntegerOpLatency + 2;
}

void SchedulingLatencyVisitorARM64::VisitIntermediateAddressIndex(
    [[maybe_unused]] HIntermediateAddressIndex* instr) {
  // Although the code generated is a simple `add` instruction, we found through empirical results
  // that spacing it from its use in memory accesses was beneficial.
  last_visited_latency_ = kArm64DataProcWithShifterOpLatency + 2;
}

void SchedulingLatencyVisitorARM64::VisitMultiplyAccumulate([[maybe_unused]] HMultiplyAccumulate*) {
  last_visited_latency_ = kArm64MulIntegerLatency;
}

void SchedulingLatencyVisitorARM64::VisitArrayGet(HArrayGet* instruction) {
  if (!instruction->GetArray()->IsIntermediateAddress()) {
    // Take the intermediate address computation into account.
    last_visited_internal_latency_ = kArm64IntegerOpLatency;
  }
  last_visited_latency_ = kArm64MemoryLoadLatency;
}

void SchedulingLatencyVisitorARM64::VisitArrayLength([[maybe_unused]] HArrayLength*) {
  last_visited_latency_ = kArm64MemoryLoadLatency;
}

void SchedulingLatencyVisitorARM64::VisitArraySet([[maybe_unused]] HArraySet*) {
  last_visited_latency_ = kArm64MemoryStoreLatency;
}

void SchedulingLatencyVisitorARM64::VisitBoundsCheck([[maybe_unused]] HBoundsCheck*) {
  last_visited_internal_latency_ = kArm64IntegerOpLatency;
  // Users do not use any data results.
  last_visited_latency_ = 0;
}

void SchedulingLatencyVisitorARM64::VisitDiv(HDiv* instr) {
  DataType::Type type = instr->GetResultType();
  switch (type) {
    case DataType::Type::kFloat32:
      last_visited_latency_ = kArm64DivFloatLatency;
      break;
    case DataType::Type::kFloat64:
      last_visited_latency_ = kArm64DivDoubleLatency;
      break;
    default:
      // Follow the code path used by code generation.
      if (instr->GetRight()->IsConstant()) {
        int64_t imm = Int64FromConstant(instr->GetRight()->AsConstant());
        if (imm == 0) {
          last_visited_internal_latency_ = 0;
          last_visited_latency_ = 0;
        } else if (imm == 1 || imm == -1) {
          last_visited_internal_latency_ = 0;
          last_visited_latency_ = kArm64IntegerOpLatency;
        } else if (IsPowerOfTwo(AbsOrMin(imm))) {
          last_visited_internal_latency_ = 4 * kArm64IntegerOpLatency;
          last_visited_latency_ = kArm64IntegerOpLatency;
        } else {
          DCHECK(imm <= -2 || imm >= 2);
          last_visited_internal_latency_ = 4 * kArm64IntegerOpLatency;
          last_visited_latency_ = kArm64MulIntegerLatency;
        }
      } else {
        last_visited_latency_ = kArm64DivIntegerLatency;
      }
      break;
  }
}

void SchedulingLatencyVisitorARM64::VisitInstanceFieldGet([[maybe_unused]] HInstanceFieldGet*) {
  last_visited_latency_ = kArm64MemoryLoadLatency;
}

void SchedulingLatencyVisitorARM64::VisitInstanceOf([[maybe_unused]] HInstanceOf*) {
  last_visited_internal_latency_ = kArm64CallInternalLatency;
  last_visited_latency_ = kArm64IntegerOpLatency;
}

void SchedulingLatencyVisitorARM64::VisitInvoke([[maybe_unused]] HInvoke*) {
  last_visited_internal_latency_ = kArm64CallInternalLatency;
  last_visited_latency_ = kArm64CallLatency;
}

void SchedulingLatencyVisitorARM64::VisitLoadString([[maybe_unused]] HLoadString*) {
  last_visited_internal_latency_ = kArm64LoadStringInternalLatency;
  last_visited_latency_ = kArm64MemoryLoadLatency;
}

void SchedulingLatencyVisitorARM64::VisitMul(HMul* instr) {
  last_visited_latency_ = DataType::IsFloatingPointType(instr->GetResultType())
      ? kArm64MulFloatingPointLatency
      : kArm64MulIntegerLatency;
}

void SchedulingLatencyVisitorARM64::VisitNewArray([[maybe_unused]] HNewArray*) {
  last_visited_internal_latency_ = kArm64IntegerOpLatency + kArm64CallInternalLatency;
  last_visited_latency_ = kArm64CallLatency;
}

void SchedulingLatencyVisitorARM64::VisitNewInstance(HNewInstance* instruction) {
  if (instruction->IsStringAlloc()) {
    last_visited_internal_latency_ = 2 + kArm64MemoryLoadLatency + kArm64CallInternalLatency;
  } else {
    last_visited_internal_latency_ = kArm64CallInternalLatency;
  }
  last_visited_latency_ = kArm64CallLatency;
}

void SchedulingLatencyVisitorARM64::VisitRem(HRem* instruction) {
  if (DataType::IsFloatingPointType(instruction->GetResultType())) {
    last_visited_internal_latency_ = kArm64CallInternalLatency;
    last_visited_latency_ = kArm64CallLatency;
  } else {
    // Follow the code path used by code generation.
    if (instruction->GetRight()->IsConstant()) {
      int64_t imm = Int64FromConstant(instruction->GetRight()->AsConstant());
      if (imm == 0) {
        last_visited_internal_latency_ = 0;
        last_visited_latency_ = 0;
      } else if (imm == 1 || imm == -1) {
        last_visited_internal_latency_ = 0;
        last_visited_latency_ = kArm64IntegerOpLatency;
      } else if (IsPowerOfTwo(AbsOrMin(imm))) {
        last_visited_internal_latency_ = 4 * kArm64IntegerOpLatency;
        last_visited_latency_ = kArm64IntegerOpLatency;
      } else {
        DCHECK(imm <= -2 || imm >= 2);
        last_visited_internal_latency_ = 4 * kArm64IntegerOpLatency;
        last_visited_latency_ = kArm64MulIntegerLatency;
      }
    } else {
      last_visited_internal_latency_ = kArm64DivIntegerLatency;
      last_visited_latency_ = kArm64MulIntegerLatency;
    }
  }
}

void SchedulingLatencyVisitorARM64::VisitStaticFieldGet([[maybe_unused]] HStaticFieldGet*) {
  last_visited_latency_ = kArm64MemoryLoadLatency;
}

void SchedulingLatencyVisitorARM64::VisitSuspendCheck(HSuspendCheck* instruction) {
  HBasicBlock* block = instruction->GetBlock();
  DCHECK_IMPLIES(block->GetLoopInformation() == nullptr,
                 block->IsEntryBlock() && instruction->GetNext()->IsGoto());
  // Users do not use any data results.
  last_visited_latency_ = 0;
}

void SchedulingLatencyVisitorARM64::VisitTypeConversion(HTypeConversion* instr) {
  if (DataType::IsFloatingPointType(instr->GetResultType()) ||
      DataType::IsFloatingPointType(instr->GetInputType())) {
    last_visited_latency_ = kArm64TypeConversionFloatingPointIntegerLatency;
  } else {
    last_visited_latency_ = kArm64IntegerOpLatency;
  }
}

void SchedulingLatencyVisitorARM64::HandleSimpleArithmeticSIMD(HVecOperation *instr) {
  if (DataType::IsFloatingPointType(instr->GetPackedType())) {
    last_visited_latency_ = kArm64SIMDFloatingPointOpLatency;
  } else {
    last_visited_latency_ = kArm64SIMDIntegerOpLatency;
  }
}

void SchedulingLatencyVisitorARM64::VisitVecReplicateScalar(
    [[maybe_unused]] HVecReplicateScalar* instr) {
  last_visited_latency_ = kArm64SIMDReplicateOpLatency;
}

void SchedulingLatencyVisitorARM64::VisitVecExtractScalar(HVecExtractScalar* instr) {
  HandleSimpleArithmeticSIMD(instr);
}

void SchedulingLatencyVisitorARM64::VisitVecReduce(HVecReduce* instr) {
  HandleSimpleArithmeticSIMD(instr);
}

void SchedulingLatencyVisitorARM64::VisitVecCnv([[maybe_unused]] HVecCnv* instr) {
  last_visited_latency_ = kArm64SIMDTypeConversionInt2FPLatency;
}

void SchedulingLatencyVisitorARM64::VisitVecNeg(HVecNeg* instr) {
  HandleSimpleArithmeticSIMD(instr);
}

void SchedulingLatencyVisitorARM64::VisitVecAbs(HVecAbs* instr) {
  HandleSimpleArithmeticSIMD(instr);
}

void SchedulingLatencyVisitorARM64::VisitVecNot(HVecNot* instr) {
  if (instr->GetPackedType() == DataType::Type::kBool) {
    last_visited_internal_latency_ = kArm64SIMDIntegerOpLatency;
  }
  last_visited_latency_ = kArm64SIMDIntegerOpLatency;
}

void SchedulingLatencyVisitorARM64::VisitVecAdd(HVecAdd* instr) {
  HandleSimpleArithmeticSIMD(instr);
}

void SchedulingLatencyVisitorARM64::VisitVecHalvingAdd(HVecHalvingAdd* instr) {
  HandleSimpleArithmeticSIMD(instr);
}

void SchedulingLatencyVisitorARM64::VisitVecSub(HVecSub* instr) {
  HandleSimpleArithmeticSIMD(instr);
}

void SchedulingLatencyVisitorARM64::VisitVecMul(HVecMul* instr) {
  if (DataType::IsFloatingPointType(instr->GetPackedType())) {
    last_visited_latency_ = kArm64SIMDMulFloatingPointLatency;
  } else {
    last_visited_latency_ = kArm64SIMDMulIntegerLatency;
  }
}

void SchedulingLatencyVisitorARM64::VisitVecDiv(HVecDiv* instr) {
  if (instr->GetPackedType() == DataType::Type::kFloat32) {
    last_visited_latency_ = kArm64SIMDDivFloatLatency;
  } else {
    DCHECK(instr->GetPackedType() == DataType::Type::kFloat64);
    last_visited_latency_ = kArm64SIMDDivDoubleLatency;
  }
}

void SchedulingLatencyVisitorARM64::VisitVecMin(HVecMin* instr) {
  HandleSimpleArithmeticSIMD(instr);
}

void SchedulingLatencyVisitorARM64::VisitVecMax(HVecMax* instr) {
  HandleSimpleArithmeticSIMD(instr);
}

void SchedulingLatencyVisitorARM64::VisitVecAnd([[maybe_unused]] HVecAnd* instr) {
  last_visited_latency_ = kArm64SIMDIntegerOpLatency;
}

void SchedulingLatencyVisitorARM64::VisitVecAndNot([[maybe_unused]] HVecAndNot* instr) {
  last_visited_latency_ = kArm64SIMDIntegerOpLatency;
}

void SchedulingLatencyVisitorARM64::VisitVecOr([[maybe_unused]] HVecOr* instr) {
  last_visited_latency_ = kArm64SIMDIntegerOpLatency;
}

void SchedulingLatencyVisitorARM64::VisitVecXor([[maybe_unused]] HVecXor* instr) {
  last_visited_latency_ = kArm64SIMDIntegerOpLatency;
}

void SchedulingLatencyVisitorARM64::VisitVecShl(HVecShl* instr) {
  HandleSimpleArithmeticSIMD(instr);
}

void SchedulingLatencyVisitorARM64::VisitVecShr(HVecShr* instr) {
  HandleSimpleArithmeticSIMD(instr);
}

void SchedulingLatencyVisitorARM64::VisitVecUShr(HVecUShr* instr) {
  HandleSimpleArithmeticSIMD(instr);
}

void SchedulingLatencyVisitorARM64::VisitVecSetScalars(HVecSetScalars* instr) {
  HandleSimpleArithmeticSIMD(instr);
}

void SchedulingLatencyVisitorARM64::VisitVecMultiplyAccumulate(
    [[maybe_unused]] HVecMultiplyAccumulate* instr) {
  last_visited_latency_ = kArm64SIMDMulIntegerLatency;
}

void SchedulingLatencyVisitorARM64::HandleVecAddress(HVecMemoryOperation* instruction,
                                                     [[maybe_unused]] size_t size) {
  HInstruction* index = instruction->InputAt(1);
  if (!index->IsConstant()) {
    last_visited_internal_latency_ += kArm64DataProcWithShifterOpLatency;
  }
}

void SchedulingLatencyVisitorARM64::VisitVecLoad(HVecLoad* instr) {
  last_visited_internal_latency_ = 0;
  size_t size = DataType::Size(instr->GetPackedType());

  if (instr->GetPackedType() == DataType::Type::kUint16
      && mirror::kUseStringCompression
      && instr->IsStringCharAt()) {
    // Set latencies for the uncompressed case.
    last_visited_internal_latency_ += kArm64MemoryLoadLatency + kArm64BranchLatency;
    HandleVecAddress(instr, size);
    last_visited_latency_ = kArm64SIMDMemoryLoadLatency;
  } else {
    HandleVecAddress(instr, size);
    last_visited_latency_ = kArm64SIMDMemoryLoadLatency;
  }
}

void SchedulingLatencyVisitorARM64::VisitVecStore(HVecStore* instr) {
  last_visited_internal_latency_ = 0;
  size_t size = DataType::Size(instr->GetPackedType());
  HandleVecAddress(instr, size);
  last_visited_latency_ = kArm64SIMDMemoryStoreLatency;
}

bool HSchedulerARM64::IsSchedulable(const HInstruction* instruction) const {
  switch (instruction->GetKind()) {
#define SCHEDULABLE_CASE(type, unused)       \
    case HInstruction::InstructionKind::k##type:  \
      return true;
    FOR_EACH_SCHEDULED_SHARED_INSTRUCTION(SCHEDULABLE_CASE)
    FOR_EACH_CONCRETE_INSTRUCTION_ARM64(SCHEDULABLE_CASE)
    FOR_EACH_SCHEDULED_COMMON_INSTRUCTION(SCHEDULABLE_CASE)
#undef SCHEDULABLE_CASE

    default:
      return HScheduler::IsSchedulable(instruction);
  }
}

std::pair<SchedulingGraph, ScopedArenaVector<SchedulingNode*>>
HSchedulerARM64::BuildSchedulingGraph(
    HBasicBlock* block,
    ScopedArenaAllocator* allocator,
    const HeapLocationCollector* heap_location_collector) {
  SchedulingLatencyVisitorARM64 latency_visitor;
  return HScheduler::BuildSchedulingGraph(
      block, allocator, heap_location_collector, &latency_visitor);
}

}  // namespace arm64
}  // namespace art
