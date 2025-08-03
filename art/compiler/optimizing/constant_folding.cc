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

#include "constant_folding.h"

#include <algorithm>

#include "base/bit_utils.h"
#include "base/casts.h"
#include "base/logging.h"
#include "dex/dex_file-inl.h"
#include "intrinsics_enum.h"
#include "optimizing/data_type.h"
#include "optimizing/nodes.h"

namespace art HIDDEN {

// This visitor tries to simplify instructions that can be evaluated
// as constants.
class HConstantFoldingVisitor final : public HGraphDelegateVisitor {
 public:
  explicit HConstantFoldingVisitor(HGraph* graph, OptimizingCompilerStats* stats)
      : HGraphDelegateVisitor(graph, stats) {}

 private:
  void VisitBasicBlock(HBasicBlock* block) override;

  void VisitUnaryOperation(HUnaryOperation* inst) override;
  void VisitBinaryOperation(HBinaryOperation* inst) override;

  // Tries to replace constants in binary operations like:
  // * BinaryOp(Select(false_constant, true_constant, condition), other_constant), or
  // * BinaryOp(other_constant, Select(false_constant, true_constant, condition))
  // with consolidated constants. For example, Add(Select(10, 20, condition), 5) can be replaced
  // with Select(15, 25, condition).
  bool TryRemoveBinaryOperationViaSelect(HBinaryOperation* inst);

  void VisitArrayLength(HArrayLength* inst) override;
  void VisitDivZeroCheck(HDivZeroCheck* inst) override;
  void VisitIf(HIf* inst) override;
  void VisitInvoke(HInvoke* inst) override;
  void VisitTypeConversion(HTypeConversion* inst) override;

  void PropagateValue(HBasicBlock* starting_block, HInstruction* variable, HConstant* constant);

  // Intrinsics foldings
  void FoldReverseIntrinsic(HInvoke* invoke);
  void FoldReverseBytesIntrinsic(HInvoke* invoke);
  void FoldBitCountIntrinsic(HInvoke* invoke);
  void FoldDivideUnsignedIntrinsic(HInvoke* invoke);
  void FoldHighestOneBitIntrinsic(HInvoke* invoke);
  void FoldLowestOneBitIntrinsic(HInvoke* invoke);
  void FoldNumberOfLeadingZerosIntrinsic(HInvoke* invoke);
  void FoldNumberOfTrailingZerosIntrinsic(HInvoke* invoke);

  DISALLOW_COPY_AND_ASSIGN(HConstantFoldingVisitor);
};

// This visitor tries to simplify operations with an absorbing input,
// yielding a constant. For example `input * 0` is replaced by a
// null constant.
class InstructionWithAbsorbingInputSimplifier : public HGraphVisitor {
 public:
  explicit InstructionWithAbsorbingInputSimplifier(HGraph* graph) : HGraphVisitor(graph) {}

 private:
  void VisitShift(HBinaryOperation* shift);

  void VisitEqual(HEqual* instruction) override;
  void VisitNotEqual(HNotEqual* instruction) override;

  void VisitAbove(HAbove* instruction) override;
  void VisitAboveOrEqual(HAboveOrEqual* instruction) override;
  void VisitBelow(HBelow* instruction) override;
  void VisitBelowOrEqual(HBelowOrEqual* instruction) override;

  void VisitGreaterThan(HGreaterThan* instruction) override;
  void VisitGreaterThanOrEqual(HGreaterThanOrEqual* instruction) override;
  void VisitLessThan(HLessThan* instruction) override;
  void VisitLessThanOrEqual(HLessThanOrEqual* instruction) override;

  void VisitAnd(HAnd* instruction) override;
  void VisitCompare(HCompare* instruction) override;
  void VisitMul(HMul* instruction) override;
  void VisitOr(HOr* instruction) override;
  void VisitRem(HRem* instruction) override;
  void VisitShl(HShl* instruction) override;
  void VisitShr(HShr* instruction) override;
  void VisitSub(HSub* instruction) override;
  void VisitUShr(HUShr* instruction) override;
  void VisitXor(HXor* instruction) override;
};


bool HConstantFolding::Run() {
  HConstantFoldingVisitor visitor(graph_, stats_);
  // Process basic blocks in reverse post-order in the dominator tree,
  // so that an instruction turned into a constant, used as input of
  // another instruction, may possibly be used to turn that second
  // instruction into a constant as well.
  visitor.VisitReversePostOrder();
  return true;
}


void HConstantFoldingVisitor::VisitBasicBlock(HBasicBlock* block) {
  // Traverse this block's instructions (phis don't need to be processed) in (forward) order
  // and replace the ones that can be statically evaluated by a compile-time counterpart.
  VisitNonPhiInstructions(block);
}

void HConstantFoldingVisitor::VisitUnaryOperation(HUnaryOperation* inst) {
  // Constant folding: replace `op(a)' with a constant at compile
  // time if `a' is a constant.
  HConstant* constant = inst->TryStaticEvaluation();
  if (constant != nullptr) {
    inst->ReplaceWith(constant);
    inst->GetBlock()->RemoveInstruction(inst);
  } else if (inst->InputAt(0)->IsSelect() && inst->InputAt(0)->HasOnlyOneNonEnvironmentUse()) {
    // Try to replace the select's inputs in Select+UnaryOperation cases. We can do this if both
    // inputs to the select are constants, and this is the only use of the select.
    HSelect* select = inst->InputAt(0)->AsSelect();
    HConstant* false_constant = inst->TryStaticEvaluation(select->GetFalseValue());
    if (false_constant == nullptr) {
      return;
    }
    HConstant* true_constant = inst->TryStaticEvaluation(select->GetTrueValue());
    if (true_constant == nullptr) {
      return;
    }
    DCHECK_EQ(select->InputAt(0), select->GetFalseValue());
    DCHECK_EQ(select->InputAt(1), select->GetTrueValue());
    select->ReplaceInput(false_constant, 0);
    select->ReplaceInput(true_constant, 1);
    select->UpdateType();
    inst->ReplaceWith(select);
    inst->GetBlock()->RemoveInstruction(inst);
  }
}

bool HConstantFoldingVisitor::TryRemoveBinaryOperationViaSelect(HBinaryOperation* inst) {
  if (inst->GetLeft()->IsSelect() == inst->GetRight()->IsSelect()) {
    // If both of them are constants, VisitBinaryOperation already tried the static evaluation. If
    // both of them are selects, then we can't simplify.
    // TODO(solanes): Technically, if both of them are selects we could simplify iff both select's
    // conditions are equal e.g. Add(Select(1, 2, cond), Select(3, 4, cond)) could be replaced with
    // Select(4, 6, cond). This seems very unlikely to happen so we don't implement it.
    return false;
  }

  const bool left_is_select = inst->GetLeft()->IsSelect();
  HSelect* select = left_is_select ? inst->GetLeft()->AsSelect() : inst->GetRight()->AsSelect();
  HInstruction* maybe_constant = left_is_select ? inst->GetRight() : inst->GetLeft();

  if (select->HasOnlyOneNonEnvironmentUse()) {
    // Try to replace the select's inputs in Select+BinaryOperation. We can do this if both
    // inputs to the select are constants, and this is the only use of the select.
    HConstant* false_constant =
        inst->TryStaticEvaluation(left_is_select ? select->GetFalseValue() : maybe_constant,
                                  left_is_select ? maybe_constant : select->GetFalseValue());
    if (false_constant == nullptr) {
      return false;
    }
    HConstant* true_constant =
        inst->TryStaticEvaluation(left_is_select ? select->GetTrueValue() : maybe_constant,
                                  left_is_select ? maybe_constant : select->GetTrueValue());
    if (true_constant == nullptr) {
      return false;
    }
    DCHECK_EQ(select->InputAt(0), select->GetFalseValue());
    DCHECK_EQ(select->InputAt(1), select->GetTrueValue());
    select->ReplaceInput(false_constant, 0);
    select->ReplaceInput(true_constant, 1);
    select->UpdateType();
    inst->ReplaceWith(select);
    inst->GetBlock()->RemoveInstruction(inst);
    return true;
  }
  return false;
}

void HConstantFoldingVisitor::VisitBinaryOperation(HBinaryOperation* inst) {
  // Constant folding: replace `op(a, b)' with a constant at
  // compile time if `a' and `b' are both constants.
  HConstant* constant = inst->TryStaticEvaluation();
  if (constant != nullptr) {
    inst->ReplaceWith(constant);
    inst->GetBlock()->RemoveInstruction(inst);
  } else if (TryRemoveBinaryOperationViaSelect(inst)) {
    // Already replaced inside TryRemoveBinaryOperationViaSelect.
  } else {
    InstructionWithAbsorbingInputSimplifier simplifier(GetGraph());
    inst->Accept(&simplifier);
  }
}

void HConstantFoldingVisitor::VisitDivZeroCheck(HDivZeroCheck* inst) {
  // We can safely remove the check if the input is a non-null constant.
  HInstruction* check_input = inst->InputAt(0);
  if (check_input->IsConstant() && !check_input->AsConstant()->IsArithmeticZero()) {
    inst->ReplaceWith(check_input);
    inst->GetBlock()->RemoveInstruction(inst);
  }
}

void HConstantFoldingVisitor::PropagateValue(HBasicBlock* starting_block,
                                             HInstruction* variable,
                                             HConstant* constant) {
  const bool recording_stats = stats_ != nullptr;
  size_t uses_before = 0;
  size_t uses_after = 0;
  if (recording_stats) {
    uses_before = variable->GetUses().SizeSlow();
  }

  if (!variable->GetUses().HasExactlyOneElement()) {
    variable->ReplaceUsesDominatedBy(
        starting_block->GetFirstInstruction(), constant, /* strictly_dominated= */ false);
  }

  if (recording_stats) {
    uses_after = variable->GetUses().SizeSlow();
    DCHECK_GE(uses_after, 1u) << "we must at least have the use in the if clause.";
    DCHECK_GE(uses_before, uses_after);
    MaybeRecordStat(stats_, MethodCompilationStat::kPropagatedIfValue, uses_before - uses_after);
  }
}

void HConstantFoldingVisitor::VisitIf(HIf* inst) {
  // Consistency check: the true and false successors do not dominate each other.
  DCHECK(!inst->IfTrueSuccessor()->Dominates(inst->IfFalseSuccessor()) &&
         !inst->IfFalseSuccessor()->Dominates(inst->IfTrueSuccessor()));

  HInstruction* if_input = inst->InputAt(0);

  // Already a constant.
  if (if_input->IsConstant()) {
    return;
  }

  // if (variable) {
  //   SSA `variable` guaranteed to be true
  // } else {
  //   and here false
  // }
  PropagateValue(inst->IfTrueSuccessor(), if_input, GetGraph()->GetIntConstant(1));
  PropagateValue(inst->IfFalseSuccessor(), if_input, GetGraph()->GetIntConstant(0));

  // If the input is a condition, we can propagate the information of the condition itself.
  if (!if_input->IsCondition()) {
    return;
  }
  HCondition* condition = if_input->AsCondition();

  // We want either `==` or `!=`, since we cannot make assumptions for other conditions e.g. `>`
  if (!condition->IsEqual() && !condition->IsNotEqual()) {
    return;
  }

  HInstruction* left = condition->GetLeft();
  HInstruction* right = condition->GetRight();

  // We want one of them to be a constant and not the other.
  if (left->IsConstant() == right->IsConstant()) {
    return;
  }

  // At this point we have something like:
  // if (variable == constant) {
  //   SSA `variable` guaranteed to be equal to constant here
  // } else {
  //   No guarantees can be made here (usually, see boolean case below).
  // }
  // Similarly with variable != constant, except that we can make guarantees in the else case.

  HConstant* constant = left->IsConstant() ? left->AsConstant() : right->AsConstant();
  HInstruction* variable = left->IsConstant() ? right : left;

  // Don't deal with floats/doubles since they bring a lot of edge cases e.g.
  // if (f == 0.0f) {
  //   // f is not really guaranteed to be 0.0f. It could be -0.0f, for example
  // }
  if (DataType::IsFloatingPointType(variable->GetType())) {
    return;
  }
  DCHECK(!DataType::IsFloatingPointType(constant->GetType()));

  // Sometimes we have an HCompare flowing into an Equals/NonEquals, which can act as a proxy. For
  // example: `Equals(Compare(var, constant), 0)`. This is common for long, float, and double.
  if (variable->IsCompare()) {
    // We only care about equality comparisons so we skip if it is a less or greater comparison.
    if (!constant->IsArithmeticZero()) {
      return;
    }

    // Update left and right to be the ones from the HCompare.
    left = variable->AsCompare()->GetLeft();
    right = variable->AsCompare()->GetRight();

    // Re-check that one of them to be a constant and not the other.
    if (left->IsConstant() == right->IsConstant()) {
      return;
    }

    constant = left->IsConstant() ? left->AsConstant() : right->AsConstant();
    variable = left->IsConstant() ? right : left;

    // Re-check floating point values.
    if (DataType::IsFloatingPointType(variable->GetType())) {
      return;
    }
    DCHECK(!DataType::IsFloatingPointType(constant->GetType()));
  }

  // From this block forward we want to replace the SSA value. We use `starting_block` and not the
  // `if` block as we want to update one of the branches but not the other.
  HBasicBlock* starting_block =
      condition->IsEqual() ? inst->IfTrueSuccessor() : inst->IfFalseSuccessor();

  PropagateValue(starting_block, variable, constant);

  // Special case for booleans since they have only two values so we know what to propagate in the
  // other branch. However, sometimes our boolean values are not compared to 0 or 1. In those cases
  // we cannot make an assumption for the `else` branch.
  if (variable->GetType() == DataType::Type::kBool &&
      constant->IsIntConstant() &&
      (constant->AsIntConstant()->IsTrue() || constant->AsIntConstant()->IsFalse())) {
    HBasicBlock* other_starting_block =
        condition->IsEqual() ? inst->IfFalseSuccessor() : inst->IfTrueSuccessor();
    DCHECK_NE(other_starting_block, starting_block);

    HConstant* other_constant = constant->AsIntConstant()->IsTrue() ?
                                    GetGraph()->GetIntConstant(0) :
                                    GetGraph()->GetIntConstant(1);
    DCHECK_NE(other_constant, constant);
    PropagateValue(other_starting_block, variable, other_constant);
  }
}

void HConstantFoldingVisitor::VisitInvoke(HInvoke* inst) {
  switch (inst->GetIntrinsic()) {
    case Intrinsics::kIntegerReverse:
    case Intrinsics::kLongReverse:
      FoldReverseIntrinsic(inst);
      break;
    case Intrinsics::kIntegerReverseBytes:
    case Intrinsics::kLongReverseBytes:
    case Intrinsics::kShortReverseBytes:
      FoldReverseBytesIntrinsic(inst);
      break;
    case Intrinsics::kIntegerBitCount:
    case Intrinsics::kLongBitCount:
      FoldBitCountIntrinsic(inst);
      break;
    case Intrinsics::kIntegerDivideUnsigned:
    case Intrinsics::kLongDivideUnsigned:
      FoldDivideUnsignedIntrinsic(inst);
      break;
    case Intrinsics::kIntegerHighestOneBit:
    case Intrinsics::kLongHighestOneBit:
      FoldHighestOneBitIntrinsic(inst);
      break;
    case Intrinsics::kIntegerLowestOneBit:
    case Intrinsics::kLongLowestOneBit:
      FoldLowestOneBitIntrinsic(inst);
      break;
    case Intrinsics::kIntegerNumberOfLeadingZeros:
    case Intrinsics::kLongNumberOfLeadingZeros:
      FoldNumberOfLeadingZerosIntrinsic(inst);
      break;
    case Intrinsics::kIntegerNumberOfTrailingZeros:
    case Intrinsics::kLongNumberOfTrailingZeros:
      FoldNumberOfTrailingZerosIntrinsic(inst);
      break;
    default:
      break;
  }
}

void HConstantFoldingVisitor::FoldReverseIntrinsic(HInvoke* inst) {
  DCHECK(inst->GetIntrinsic() == Intrinsics::kIntegerReverse ||
         inst->GetIntrinsic() == Intrinsics::kLongReverse);

  HInstruction* input = inst->InputAt(0);
  if (!input->IsConstant()) {
    return;
  }

  // Integer and Long intrinsics have different return types.
  if (inst->GetIntrinsic() == Intrinsics::kIntegerReverse) {
    DCHECK(input->IsIntConstant());
    inst->ReplaceWith(
        GetGraph()->GetIntConstant(ReverseBits32(input->AsIntConstant()->GetValue())));
  } else {
    DCHECK(input->IsLongConstant());
    inst->ReplaceWith(
        GetGraph()->GetLongConstant(ReverseBits64(input->AsLongConstant()->GetValue())));
  }
  inst->GetBlock()->RemoveInstruction(inst);
}

void HConstantFoldingVisitor::FoldReverseBytesIntrinsic(HInvoke* inst) {
  DCHECK(inst->GetIntrinsic() == Intrinsics::kIntegerReverseBytes ||
         inst->GetIntrinsic() == Intrinsics::kLongReverseBytes ||
         inst->GetIntrinsic() == Intrinsics::kShortReverseBytes);

  HInstruction* input = inst->InputAt(0);
  if (!input->IsConstant()) {
    return;
  }

  // Integer, Long, and Short intrinsics have different return types.
  if (inst->GetIntrinsic() == Intrinsics::kIntegerReverseBytes) {
    DCHECK(input->IsIntConstant());
    inst->ReplaceWith(GetGraph()->GetIntConstant(BSWAP(input->AsIntConstant()->GetValue())));
  } else if (inst->GetIntrinsic() == Intrinsics::kLongReverseBytes) {
    DCHECK(input->IsLongConstant());
    inst->ReplaceWith(GetGraph()->GetLongConstant(BSWAP(input->AsLongConstant()->GetValue())));
  } else {
    DCHECK(input->IsIntConstant());
    inst->ReplaceWith(GetGraph()->GetIntConstant(
        BSWAP(dchecked_integral_cast<int16_t>(input->AsIntConstant()->GetValue()))));
  }
  inst->GetBlock()->RemoveInstruction(inst);
}

void HConstantFoldingVisitor::FoldBitCountIntrinsic(HInvoke* inst) {
  DCHECK(inst->GetIntrinsic() == Intrinsics::kIntegerBitCount ||
         inst->GetIntrinsic() == Intrinsics::kLongBitCount);

  HInstruction* input = inst->InputAt(0);
  if (!input->IsConstant()) {
    return;
  }

  DCHECK_IMPLIES(inst->GetIntrinsic() == Intrinsics::kIntegerBitCount, input->IsIntConstant());
  DCHECK_IMPLIES(inst->GetIntrinsic() == Intrinsics::kLongBitCount, input->IsLongConstant());

  // Note that both the Integer and Long intrinsics return an int as a result.
  int result = inst->GetIntrinsic() == Intrinsics::kIntegerBitCount ?
                   POPCOUNT(input->AsIntConstant()->GetValue()) :
                   POPCOUNT(input->AsLongConstant()->GetValue());
  inst->ReplaceWith(GetGraph()->GetIntConstant(result));
  inst->GetBlock()->RemoveInstruction(inst);
}

void HConstantFoldingVisitor::FoldDivideUnsignedIntrinsic(HInvoke* inst) {
  DCHECK(inst->GetIntrinsic() == Intrinsics::kIntegerDivideUnsigned ||
         inst->GetIntrinsic() == Intrinsics::kLongDivideUnsigned);

  HInstruction* divisor = inst->InputAt(1);
  if (!divisor->IsConstant()) {
    return;
  }
  DCHECK_IMPLIES(inst->GetIntrinsic() == Intrinsics::kIntegerDivideUnsigned,
                 divisor->IsIntConstant());
  DCHECK_IMPLIES(inst->GetIntrinsic() == Intrinsics::kLongDivideUnsigned,
                 divisor->IsLongConstant());
  const bool is_int_intrinsic = inst->GetIntrinsic() == Intrinsics::kIntegerDivideUnsigned;
  if ((is_int_intrinsic && divisor->AsIntConstant()->IsArithmeticZero()) ||
      (!is_int_intrinsic && divisor->AsLongConstant()->IsArithmeticZero())) {
    // We will be throwing, don't constant fold.
    inst->SetAlwaysThrows(true);
    GetGraph()->SetHasAlwaysThrowingInvokes(true);
    return;
  }

  HInstruction* dividend = inst->InputAt(0);
  if (!dividend->IsConstant()) {
    return;
  }
  DCHECK_IMPLIES(inst->GetIntrinsic() == Intrinsics::kIntegerDivideUnsigned,
                 dividend->IsIntConstant());
  DCHECK_IMPLIES(inst->GetIntrinsic() == Intrinsics::kLongDivideUnsigned,
                 dividend->IsLongConstant());

  if (is_int_intrinsic) {
    uint32_t dividend_val =
        dchecked_integral_cast<uint32_t>(dividend->AsIntConstant()->GetValueAsUint64());
    uint32_t divisor_val =
        dchecked_integral_cast<uint32_t>(divisor->AsIntConstant()->GetValueAsUint64());
    inst->ReplaceWith(GetGraph()->GetIntConstant(static_cast<int32_t>(dividend_val / divisor_val)));
  } else {
    uint64_t dividend_val = dividend->AsLongConstant()->GetValueAsUint64();
    uint64_t divisor_val = divisor->AsLongConstant()->GetValueAsUint64();
    inst->ReplaceWith(
        GetGraph()->GetLongConstant(static_cast<int64_t>(dividend_val / divisor_val)));
  }

  inst->GetBlock()->RemoveInstruction(inst);
}

void HConstantFoldingVisitor::FoldHighestOneBitIntrinsic(HInvoke* inst) {
  DCHECK(inst->GetIntrinsic() == Intrinsics::kIntegerHighestOneBit ||
         inst->GetIntrinsic() == Intrinsics::kLongHighestOneBit);

  HInstruction* input = inst->InputAt(0);
  if (!input->IsConstant()) {
    return;
  }

  // Integer and Long intrinsics have different return types.
  if (inst->GetIntrinsic() == Intrinsics::kIntegerHighestOneBit) {
    DCHECK(input->IsIntConstant());
    inst->ReplaceWith(
        GetGraph()->GetIntConstant(HighestOneBitValue(input->AsIntConstant()->GetValue())));
  } else {
    DCHECK(input->IsLongConstant());
    inst->ReplaceWith(
        GetGraph()->GetLongConstant(HighestOneBitValue(input->AsLongConstant()->GetValue())));
  }
  inst->GetBlock()->RemoveInstruction(inst);
}

void HConstantFoldingVisitor::FoldLowestOneBitIntrinsic(HInvoke* inst) {
  DCHECK(inst->GetIntrinsic() == Intrinsics::kIntegerLowestOneBit ||
         inst->GetIntrinsic() == Intrinsics::kLongLowestOneBit);

  HInstruction* input = inst->InputAt(0);
  if (!input->IsConstant()) {
    return;
  }

  // Integer and Long intrinsics have different return types.
  if (inst->GetIntrinsic() == Intrinsics::kIntegerLowestOneBit) {
    DCHECK(input->IsIntConstant());
    inst->ReplaceWith(
        GetGraph()->GetIntConstant(LowestOneBitValue(input->AsIntConstant()->GetValue())));
  } else {
    DCHECK(input->IsLongConstant());
    inst->ReplaceWith(
        GetGraph()->GetLongConstant(LowestOneBitValue(input->AsLongConstant()->GetValue())));
  }
  inst->GetBlock()->RemoveInstruction(inst);
}

void HConstantFoldingVisitor::FoldNumberOfLeadingZerosIntrinsic(HInvoke* inst) {
  DCHECK(inst->GetIntrinsic() == Intrinsics::kIntegerNumberOfLeadingZeros ||
         inst->GetIntrinsic() == Intrinsics::kLongNumberOfLeadingZeros);

  HInstruction* input = inst->InputAt(0);
  if (!input->IsConstant()) {
    return;
  }

  DCHECK_IMPLIES(inst->GetIntrinsic() == Intrinsics::kIntegerNumberOfLeadingZeros,
                 input->IsIntConstant());
  DCHECK_IMPLIES(inst->GetIntrinsic() == Intrinsics::kLongNumberOfLeadingZeros,
                 input->IsLongConstant());

  // Note that both the Integer and Long intrinsics return an int as a result.
  int result = input->IsIntConstant() ? JAVASTYLE_CLZ(input->AsIntConstant()->GetValue()) :
                                        JAVASTYLE_CLZ(input->AsLongConstant()->GetValue());
  inst->ReplaceWith(GetGraph()->GetIntConstant(result));
  inst->GetBlock()->RemoveInstruction(inst);
}

void HConstantFoldingVisitor::FoldNumberOfTrailingZerosIntrinsic(HInvoke* inst) {
  DCHECK(inst->GetIntrinsic() == Intrinsics::kIntegerNumberOfTrailingZeros ||
         inst->GetIntrinsic() == Intrinsics::kLongNumberOfTrailingZeros);

  HInstruction* input = inst->InputAt(0);
  if (!input->IsConstant()) {
    return;
  }

  DCHECK_IMPLIES(inst->GetIntrinsic() == Intrinsics::kIntegerNumberOfTrailingZeros,
                 input->IsIntConstant());
  DCHECK_IMPLIES(inst->GetIntrinsic() == Intrinsics::kLongNumberOfTrailingZeros,
                 input->IsLongConstant());

  // Note that both the Integer and Long intrinsics return an int as a result.
  int result = input->IsIntConstant() ? JAVASTYLE_CTZ(input->AsIntConstant()->GetValue()) :
                                        JAVASTYLE_CTZ(input->AsLongConstant()->GetValue());
  inst->ReplaceWith(GetGraph()->GetIntConstant(result));
  inst->GetBlock()->RemoveInstruction(inst);
}

void HConstantFoldingVisitor::VisitArrayLength(HArrayLength* inst) {
  HInstruction* input = inst->InputAt(0);
  if (input->IsLoadString()) {
    DCHECK(inst->IsStringLength());
    HLoadString* load_string = input->AsLoadString();
    const DexFile& dex_file = load_string->GetDexFile();
    const dex::StringId& string_id = dex_file.GetStringId(load_string->GetStringIndex());
    inst->ReplaceWith(GetGraph()->GetIntConstant(dex_file.GetStringLength(string_id)));
  }
}

void HConstantFoldingVisitor::VisitTypeConversion(HTypeConversion* inst) {
  // Constant folding: replace `TypeConversion(a)' with a constant at
  // compile time if `a' is a constant.
  HConstant* constant = inst->TryStaticEvaluation();
  if (constant != nullptr) {
    inst->ReplaceWith(constant);
    inst->GetBlock()->RemoveInstruction(inst);
  } else if (inst->InputAt(0)->IsSelect() && inst->InputAt(0)->HasOnlyOneNonEnvironmentUse()) {
    // Try to replace the select's inputs in Select+TypeConversion. We can do this if both
    // inputs to the select are constants, and this is the only use of the select.
    HSelect* select = inst->InputAt(0)->AsSelect();
    HConstant* false_constant = inst->TryStaticEvaluation(select->GetFalseValue());
    if (false_constant == nullptr) {
      return;
    }
    HConstant* true_constant = inst->TryStaticEvaluation(select->GetTrueValue());
    if (true_constant == nullptr) {
      return;
    }
    DCHECK_EQ(select->InputAt(0), select->GetFalseValue());
    DCHECK_EQ(select->InputAt(1), select->GetTrueValue());
    select->ReplaceInput(false_constant, 0);
    select->ReplaceInput(true_constant, 1);
    select->UpdateType();
    inst->ReplaceWith(select);
    inst->GetBlock()->RemoveInstruction(inst);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitShift(HBinaryOperation* instruction) {
  DCHECK(instruction->IsShl() || instruction->IsShr() || instruction->IsUShr());
  HInstruction* left = instruction->GetLeft();
  if (left->IsConstant() && left->AsConstant()->IsArithmeticZero()) {
    // Replace code looking like
    //    SHL dst, 0, shift_amount
    // with
    //    CONSTANT 0
    instruction->ReplaceWith(left);
    instruction->GetBlock()->RemoveInstruction(instruction);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitEqual(HEqual* instruction) {
  if (instruction->GetLeft() == instruction->GetRight() &&
      !DataType::IsFloatingPointType(instruction->GetLeft()->GetType())) {
    // Replace code looking like
    //    EQUAL lhs, lhs
    //    CONSTANT true
    // We don't perform this optimizations for FP types since Double.NaN != Double.NaN, which is the
    // opposite value.
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 1));
    instruction->GetBlock()->RemoveInstruction(instruction);
  } else if ((instruction->GetLeft()->IsNullConstant() && !instruction->GetRight()->CanBeNull()) ||
             (instruction->GetRight()->IsNullConstant() && !instruction->GetLeft()->CanBeNull())) {
    // Replace code looking like
    //    EQUAL lhs, null
    // where lhs cannot be null with
    //    CONSTANT false
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 0));
    instruction->GetBlock()->RemoveInstruction(instruction);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitNotEqual(HNotEqual* instruction) {
  if (instruction->GetLeft() == instruction->GetRight() &&
      !DataType::IsFloatingPointType(instruction->GetLeft()->GetType())) {
    // Replace code looking like
    //    NOT_EQUAL lhs, lhs
    //    CONSTANT false
    // We don't perform this optimizations for FP types since Double.NaN != Double.NaN, which is the
    // opposite value.
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 0));
    instruction->GetBlock()->RemoveInstruction(instruction);
  } else if ((instruction->GetLeft()->IsNullConstant() && !instruction->GetRight()->CanBeNull()) ||
             (instruction->GetRight()->IsNullConstant() && !instruction->GetLeft()->CanBeNull())) {
    // Replace code looking like
    //    NOT_EQUAL lhs, null
    // where lhs cannot be null with
    //    CONSTANT true
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 1));
    instruction->GetBlock()->RemoveInstruction(instruction);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitAbove(HAbove* instruction) {
  if (instruction->GetLeft() == instruction->GetRight()) {
    // Replace code looking like
    //    ABOVE lhs, lhs
    //    CONSTANT false
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 0));
    instruction->GetBlock()->RemoveInstruction(instruction);
  } else if (instruction->GetLeft()->IsConstant() &&
             instruction->GetLeft()->AsConstant()->IsArithmeticZero()) {
    // Replace code looking like
    //    ABOVE dst, 0, src  // unsigned 0 > src is always false
    // with
    //    CONSTANT false
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 0));
    instruction->GetBlock()->RemoveInstruction(instruction);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitAboveOrEqual(HAboveOrEqual* instruction) {
  if (instruction->GetLeft() == instruction->GetRight()) {
    // Replace code looking like
    //    ABOVE_OR_EQUAL lhs, lhs
    //    CONSTANT true
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 1));
    instruction->GetBlock()->RemoveInstruction(instruction);
  } else if (instruction->GetRight()->IsConstant() &&
             instruction->GetRight()->AsConstant()->IsArithmeticZero()) {
    // Replace code looking like
    //    ABOVE_OR_EQUAL dst, src, 0  // unsigned src >= 0 is always true
    // with
    //    CONSTANT true
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 1));
    instruction->GetBlock()->RemoveInstruction(instruction);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitBelow(HBelow* instruction) {
  if (instruction->GetLeft() == instruction->GetRight()) {
    // Replace code looking like
    //    BELOW lhs, lhs
    //    CONSTANT false
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 0));
    instruction->GetBlock()->RemoveInstruction(instruction);
  } else if (instruction->GetRight()->IsConstant() &&
             instruction->GetRight()->AsConstant()->IsArithmeticZero()) {
    // Replace code looking like
    //    BELOW dst, src, 0  // unsigned src < 0 is always false
    // with
    //    CONSTANT false
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 0));
    instruction->GetBlock()->RemoveInstruction(instruction);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitBelowOrEqual(HBelowOrEqual* instruction) {
  if (instruction->GetLeft() == instruction->GetRight()) {
    // Replace code looking like
    //    BELOW_OR_EQUAL lhs, lhs
    //    CONSTANT true
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 1));
    instruction->GetBlock()->RemoveInstruction(instruction);
  } else if (instruction->GetLeft()->IsConstant() &&
             instruction->GetLeft()->AsConstant()->IsArithmeticZero()) {
    // Replace code looking like
    //    BELOW_OR_EQUAL dst, 0, src  // unsigned 0 <= src is always true
    // with
    //    CONSTANT true
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 1));
    instruction->GetBlock()->RemoveInstruction(instruction);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitGreaterThan(HGreaterThan* instruction) {
  if (instruction->GetLeft() == instruction->GetRight() &&
      (!DataType::IsFloatingPointType(instruction->GetLeft()->GetType()) ||
       instruction->IsLtBias())) {
    // Replace code looking like
    //    GREATER_THAN lhs, lhs
    //    CONSTANT false
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 0));
    instruction->GetBlock()->RemoveInstruction(instruction);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitGreaterThanOrEqual(
    HGreaterThanOrEqual* instruction) {
  if (instruction->GetLeft() == instruction->GetRight() &&
      (!DataType::IsFloatingPointType(instruction->GetLeft()->GetType()) ||
       instruction->IsGtBias())) {
    // Replace code looking like
    //    GREATER_THAN_OR_EQUAL lhs, lhs
    //    CONSTANT true
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 1));
    instruction->GetBlock()->RemoveInstruction(instruction);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitLessThan(HLessThan* instruction) {
  if (instruction->GetLeft() == instruction->GetRight() &&
      (!DataType::IsFloatingPointType(instruction->GetLeft()->GetType()) ||
       instruction->IsGtBias())) {
    // Replace code looking like
    //    LESS_THAN lhs, lhs
    //    CONSTANT false
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 0));
    instruction->GetBlock()->RemoveInstruction(instruction);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitLessThanOrEqual(HLessThanOrEqual* instruction) {
  if (instruction->GetLeft() == instruction->GetRight() &&
      (!DataType::IsFloatingPointType(instruction->GetLeft()->GetType()) ||
       instruction->IsLtBias())) {
    // Replace code looking like
    //    LESS_THAN_OR_EQUAL lhs, lhs
    //    CONSTANT true
    instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kBool, 1));
    instruction->GetBlock()->RemoveInstruction(instruction);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitAnd(HAnd* instruction) {
  DataType::Type type = instruction->GetType();
  HConstant* input_cst = instruction->GetConstantRight();
  if ((input_cst != nullptr) && input_cst->IsZeroBitPattern()) {
    // Replace code looking like
    //    AND dst, src, 0
    // with
    //    CONSTANT 0
    instruction->ReplaceWith(input_cst);
    instruction->GetBlock()->RemoveInstruction(instruction);
  }

  HInstruction* left = instruction->GetLeft();
  HInstruction* right = instruction->GetRight();

  if (left->IsNot() ^ right->IsNot()) {
    // Replace code looking like
    //    NOT notsrc, src
    //    AND dst, notsrc, src
    // with
    //    CONSTANT 0
    HInstruction* hnot = (left->IsNot() ? left : right);
    HInstruction* hother = (left->IsNot() ? right : left);
    HInstruction* src = hnot->AsNot()->GetInput();

    if (src == hother) {
      instruction->ReplaceWith(GetGraph()->GetConstant(type, 0));
      instruction->GetBlock()->RemoveInstruction(instruction);
    }
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitCompare(HCompare* instruction) {
  HConstant* input_cst = instruction->GetConstantRight();
  if (input_cst != nullptr) {
    HInstruction* input_value = instruction->GetLeastConstantLeft();
    if (DataType::IsFloatingPointType(input_value->GetType()) &&
        ((input_cst->IsFloatConstant() && input_cst->AsFloatConstant()->IsNaN()) ||
         (input_cst->IsDoubleConstant() && input_cst->AsDoubleConstant()->IsNaN()))) {
      // Replace code looking like
      //    CMP{G,L}-{FLOAT,DOUBLE} dst, src, NaN
      // with
      //    CONSTANT +1 (gt bias)
      // or
      //    CONSTANT -1 (lt bias)
      instruction->ReplaceWith(GetGraph()->GetConstant(DataType::Type::kInt32,
                                                       (instruction->IsGtBias() ? 1 : -1)));
      instruction->GetBlock()->RemoveInstruction(instruction);
    }
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitMul(HMul* instruction) {
  HConstant* input_cst = instruction->GetConstantRight();
  DataType::Type type = instruction->GetType();
  if (DataType::IsIntOrLongType(type) &&
      (input_cst != nullptr) && input_cst->IsArithmeticZero()) {
    // Replace code looking like
    //    MUL dst, src, 0
    // with
    //    CONSTANT 0
    // Integral multiplication by zero always yields zero, but floating-point
    // multiplication by zero does not always do. For example `Infinity * 0.0`
    // should yield a NaN.
    instruction->ReplaceWith(input_cst);
    instruction->GetBlock()->RemoveInstruction(instruction);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitOr(HOr* instruction) {
  HConstant* input_cst = instruction->GetConstantRight();
  if (input_cst != nullptr && Int64FromConstant(input_cst) == -1) {
    // Replace code looking like
    //    OR dst, src, 0xFFF...FF
    // with
    //    CONSTANT 0xFFF...FF
    instruction->ReplaceWith(input_cst);
    instruction->GetBlock()->RemoveInstruction(instruction);
    return;
  }

  HInstruction* left = instruction->GetLeft();
  HInstruction* right = instruction->GetRight();
  if (left->IsNot() ^ right->IsNot()) {
    // Replace code looking like
    //    NOT notsrc, src
    //    OR  dst, notsrc, src
    // with
    //    CONSTANT 0xFFF...FF
    HInstruction* hnot = (left->IsNot() ? left : right);
    HInstruction* hother = (left->IsNot() ? right : left);
    HInstruction* src = hnot->AsNot()->GetInput();

    if (src == hother) {
      DCHECK(instruction->GetType() == DataType::Type::kInt32 ||
             instruction->GetType() == DataType::Type::kInt64);
      instruction->ReplaceWith(GetGraph()->GetConstant(instruction->GetType(), -1));
      instruction->GetBlock()->RemoveInstruction(instruction);
      return;
    }
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitRem(HRem* instruction) {
  DataType::Type type = instruction->GetType();

  if (!DataType::IsIntegralType(type)) {
    return;
  }

  HBasicBlock* block = instruction->GetBlock();

  if (instruction->GetLeft()->IsConstant() &&
      instruction->GetLeft()->AsConstant()->IsArithmeticZero()) {
    // Replace code looking like
    //    REM dst, 0, src
    // with
    //    CONSTANT 0
    instruction->ReplaceWith(instruction->GetLeft());
    block->RemoveInstruction(instruction);
  }

  HConstant* cst_right = instruction->GetRight()->AsConstantOrNull();
  if (((cst_right != nullptr) &&
       (cst_right->IsOne() || cst_right->IsMinusOne())) ||
      (instruction->GetLeft() == instruction->GetRight())) {
    // Replace code looking like
    //    REM dst, src, 1
    // or
    //    REM dst, src, -1
    // or
    //    REM dst, src, src
    // with
    //    CONSTANT 0
    instruction->ReplaceWith(GetGraph()->GetConstant(type, 0));
    block->RemoveInstruction(instruction);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitShl(HShl* instruction) {
  VisitShift(instruction);
}

void InstructionWithAbsorbingInputSimplifier::VisitShr(HShr* instruction) {
  VisitShift(instruction);
}

void InstructionWithAbsorbingInputSimplifier::VisitSub(HSub* instruction) {
  DataType::Type type = instruction->GetType();

  if (!DataType::IsIntegralType(type)) {
    return;
  }

  HBasicBlock* block = instruction->GetBlock();

  // We assume that GVN has run before, so we only perform a pointer
  // comparison.  If for some reason the values are equal but the pointers are
  // different, we are still correct and only miss an optimization
  // opportunity.
  if (instruction->GetLeft() == instruction->GetRight()) {
    // Replace code looking like
    //    SUB dst, src, src
    // with
    //    CONSTANT 0
    // Note that we cannot optimize `x - x` to `0` for floating-point. It does
    // not work when `x` is an infinity.
    instruction->ReplaceWith(GetGraph()->GetConstant(type, 0));
    block->RemoveInstruction(instruction);
  }
}

void InstructionWithAbsorbingInputSimplifier::VisitUShr(HUShr* instruction) {
  VisitShift(instruction);
}

void InstructionWithAbsorbingInputSimplifier::VisitXor(HXor* instruction) {
  if (instruction->GetLeft() == instruction->GetRight()) {
    // Replace code looking like
    //    XOR dst, src, src
    // with
    //    CONSTANT 0
    DataType::Type type = instruction->GetType();
    HBasicBlock* block = instruction->GetBlock();
    instruction->ReplaceWith(GetGraph()->GetConstant(type, 0));
    block->RemoveInstruction(instruction);
    return;
  }

  HInstruction* left = instruction->GetLeft();
  HInstruction* right = instruction->GetRight();
  if (left->IsNot() ^ right->IsNot()) {
    // Replace code looking like
    //    NOT notsrc, src
    //    XOR dst, notsrc, src
    // with
    //    CONSTANT 0xFFF...FF
    HInstruction* hnot = (left->IsNot() ? left : right);
    HInstruction* hother = (left->IsNot() ? right : left);
    HInstruction* src = hnot->AsNot()->GetInput();

    if (src == hother) {
      DCHECK(instruction->GetType() == DataType::Type::kInt32 ||
             instruction->GetType() == DataType::Type::kInt64);
      instruction->ReplaceWith(GetGraph()->GetConstant(instruction->GetType(), -1));
      instruction->GetBlock()->RemoveInstruction(instruction);
      return;
    }
  }
}

}  // namespace art
