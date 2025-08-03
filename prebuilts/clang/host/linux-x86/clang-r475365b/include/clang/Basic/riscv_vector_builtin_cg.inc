case RISCVVector::BI__builtin_rvv_vget_v:
  DefaultPolicy = 1;

      {
        ID = Intrinsic::vector_extract;
        auto *VecTy = cast<ScalableVectorType>(ResultType);
        auto *OpVecTy = cast<ScalableVectorType>(Ops[0]->getType());
        // Mask to only valid indices.
        unsigned MaxIndex = OpVecTy->getMinNumElements() / VecTy->getMinNumElements();
        assert(isPowerOf2_32(MaxIndex));
        Ops[1] = Builder.CreateZExt(Ops[1], Builder.getInt64Ty());
        Ops[1] = Builder.CreateAnd(Ops[1], MaxIndex - 1);
        Ops[1] = Builder.CreateMul(Ops[1],
                                   ConstantInt::get(Ops[1]->getType(),
                                                    VecTy->getMinNumElements()));
        IntrinsicTypes = {ResultType, Ops[0]->getType()};
        return Builder.CreateCall(CGM.getIntrinsic(ID, IntrinsicTypes), Ops, "");
      }
      break;
case RISCVVector::BI__builtin_rvv_vset_v:
  DefaultPolicy = 1;

      {
        ID = Intrinsic::vector_insert;
        IntrinsicTypes = {ResultType, Ops[2]->getType()};
        auto *ResVecTy = cast<ScalableVectorType>(ResultType);
        auto *VecTy = cast<ScalableVectorType>(Ops[2]->getType());
        // Mask to only valid indices.
        unsigned MaxIndex = ResVecTy->getMinNumElements() / VecTy->getMinNumElements();
        assert(isPowerOf2_32(MaxIndex));
        Ops[1] = Builder.CreateZExt(Ops[1], Builder.getInt64Ty());
        Ops[1] = Builder.CreateAnd(Ops[1], MaxIndex - 1);
        Ops[1] = Builder.CreateMul(Ops[1],
                                   ConstantInt::get(Ops[1]->getType(),
                                                    VecTy->getMinNumElements()));
        std::swap(Ops[1], Ops[2]);
        return Builder.CreateCall(CGM.getIntrinsic(ID, IntrinsicTypes), Ops, "");
      }
      break;
case RISCVVector::BI__builtin_rvv_vlmul_ext_v:
  DefaultPolicy = 1;

        ID = Intrinsic::vector_insert;
        IntrinsicTypes = {ResultType, Ops[0]->getType()};
        Ops.push_back(llvm::PoisonValue::get(ResultType));
        std::swap(Ops[0], Ops[1]);
        Ops.push_back(ConstantInt::get(Int64Ty, 0));
        return Builder.CreateCall(CGM.getIntrinsic(ID, IntrinsicTypes), Ops, "");
      break;
case RISCVVector::BI__builtin_rvv_vlmul_trunc_v:
  DefaultPolicy = 1;
 {
        ID = Intrinsic::vector_extract;
        IntrinsicTypes = {ResultType, Ops[0]->getType()};
        Ops.push_back(ConstantInt::get(Int64Ty, 0));
        return Builder.CreateCall(CGM.getIntrinsic(ID, IntrinsicTypes), Ops, "");
      } break;
case RISCVVector::BI__builtin_rvv_vreinterpret_v:
  DefaultPolicy = 1;

        return Builder.CreateBitCast(Ops[0], ResultType);
      break;
case RISCVVector::BI__builtin_rvv_vundefined:
  DefaultPolicy = 1;

        return llvm::PoisonValue::get(ResultType);
      break;
case RISCVVector::BI__builtin_rvv_vaadd_vv_tu:
case RISCVVector::BI__builtin_rvv_vaadd_vx_tu:
  ID = Intrinsic::riscv_vaadd;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vaadd_vv_ta:
case RISCVVector::BI__builtin_rvv_vaadd_vx_ta:
  ID = Intrinsic::riscv_vaadd;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vaadd_vv_tuma:
case RISCVVector::BI__builtin_rvv_vaadd_vx_tuma:
  ID = Intrinsic::riscv_vaadd_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vaadd_vv_tama:
case RISCVVector::BI__builtin_rvv_vaadd_vx_tama:
  ID = Intrinsic::riscv_vaadd_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vaadd_vv_tumu:
case RISCVVector::BI__builtin_rvv_vaadd_vx_tumu:
  ID = Intrinsic::riscv_vaadd_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vaadd_vv_tamu:
case RISCVVector::BI__builtin_rvv_vaadd_vx_tamu:
  ID = Intrinsic::riscv_vaadd_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vaaddu_vv_tu:
case RISCVVector::BI__builtin_rvv_vaaddu_vx_tu:
  ID = Intrinsic::riscv_vaaddu;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vaaddu_vv_ta:
case RISCVVector::BI__builtin_rvv_vaaddu_vx_ta:
  ID = Intrinsic::riscv_vaaddu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vaaddu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vaaddu_vx_tuma:
  ID = Intrinsic::riscv_vaaddu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vaaddu_vv_tama:
case RISCVVector::BI__builtin_rvv_vaaddu_vx_tama:
  ID = Intrinsic::riscv_vaaddu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vaaddu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vaaddu_vx_tumu:
  ID = Intrinsic::riscv_vaaddu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vaaddu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vaaddu_vx_tamu:
  ID = Intrinsic::riscv_vaaddu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vadc_vvm_tu:
case RISCVVector::BI__builtin_rvv_vadc_vxm_tu:
  ID = Intrinsic::riscv_vadc;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vadc_vvm_ta:
case RISCVVector::BI__builtin_rvv_vadc_vxm_ta:
  ID = Intrinsic::riscv_vadc;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vadd_vv_tu:
case RISCVVector::BI__builtin_rvv_vadd_vx_tu:
  ID = Intrinsic::riscv_vadd;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vadd_vv_ta:
case RISCVVector::BI__builtin_rvv_vadd_vx_ta:
  ID = Intrinsic::riscv_vadd;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vadd_vv_tuma:
case RISCVVector::BI__builtin_rvv_vadd_vx_tuma:
  ID = Intrinsic::riscv_vadd_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vadd_vv_tama:
case RISCVVector::BI__builtin_rvv_vadd_vx_tama:
  ID = Intrinsic::riscv_vadd_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vadd_vv_tumu:
case RISCVVector::BI__builtin_rvv_vadd_vx_tumu:
  ID = Intrinsic::riscv_vadd_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vadd_vv_tamu:
case RISCVVector::BI__builtin_rvv_vadd_vx_tamu:
  ID = Intrinsic::riscv_vadd_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vand_vv_tu:
case RISCVVector::BI__builtin_rvv_vand_vx_tu:
  ID = Intrinsic::riscv_vand;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vand_vv_ta:
case RISCVVector::BI__builtin_rvv_vand_vx_ta:
  ID = Intrinsic::riscv_vand;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vand_vv_tuma:
case RISCVVector::BI__builtin_rvv_vand_vx_tuma:
  ID = Intrinsic::riscv_vand_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vand_vv_tama:
case RISCVVector::BI__builtin_rvv_vand_vx_tama:
  ID = Intrinsic::riscv_vand_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vand_vv_tumu:
case RISCVVector::BI__builtin_rvv_vand_vx_tumu:
  ID = Intrinsic::riscv_vand_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vand_vv_tamu:
case RISCVVector::BI__builtin_rvv_vand_vx_tamu:
  ID = Intrinsic::riscv_vand_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vasub_vv_tu:
case RISCVVector::BI__builtin_rvv_vasub_vx_tu:
  ID = Intrinsic::riscv_vasub;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vasub_vv_ta:
case RISCVVector::BI__builtin_rvv_vasub_vx_ta:
  ID = Intrinsic::riscv_vasub;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vasub_vv_tuma:
case RISCVVector::BI__builtin_rvv_vasub_vx_tuma:
  ID = Intrinsic::riscv_vasub_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vasub_vv_tama:
case RISCVVector::BI__builtin_rvv_vasub_vx_tama:
  ID = Intrinsic::riscv_vasub_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vasub_vv_tumu:
case RISCVVector::BI__builtin_rvv_vasub_vx_tumu:
  ID = Intrinsic::riscv_vasub_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vasub_vv_tamu:
case RISCVVector::BI__builtin_rvv_vasub_vx_tamu:
  ID = Intrinsic::riscv_vasub_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vasubu_vv_tu:
case RISCVVector::BI__builtin_rvv_vasubu_vx_tu:
  ID = Intrinsic::riscv_vasubu;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vasubu_vv_ta:
case RISCVVector::BI__builtin_rvv_vasubu_vx_ta:
  ID = Intrinsic::riscv_vasubu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vasubu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vasubu_vx_tuma:
  ID = Intrinsic::riscv_vasubu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vasubu_vv_tama:
case RISCVVector::BI__builtin_rvv_vasubu_vx_tama:
  ID = Intrinsic::riscv_vasubu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vasubu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vasubu_vx_tumu:
  ID = Intrinsic::riscv_vasubu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vasubu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vasubu_vx_tamu:
  ID = Intrinsic::riscv_vasubu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vcompress_vm_tu:
  ID = Intrinsic::riscv_vcompress;
  DefaultPolicy = 0;

      std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
      // insert poison passthru
      if (DefaultPolicy == TAIL_AGNOSTIC)
        Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
    break;
case RISCVVector::BI__builtin_rvv_vcompress_vm_ta:
  ID = Intrinsic::riscv_vcompress;
  DefaultPolicy = 1;

      std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
      // insert poison passthru
      if (DefaultPolicy == TAIL_AGNOSTIC)
        Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
    break;
case RISCVVector::BI__builtin_rvv_vcpop_m:
  ID = Intrinsic::riscv_vcpop;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vcpop_m_m:
  ID = Intrinsic::riscv_vcpop_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[0]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vdiv_vv_tu:
case RISCVVector::BI__builtin_rvv_vdiv_vx_tu:
  ID = Intrinsic::riscv_vdiv;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vdiv_vv_ta:
case RISCVVector::BI__builtin_rvv_vdiv_vx_ta:
  ID = Intrinsic::riscv_vdiv;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vdiv_vv_tuma:
case RISCVVector::BI__builtin_rvv_vdiv_vx_tuma:
  ID = Intrinsic::riscv_vdiv_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vdiv_vv_tama:
case RISCVVector::BI__builtin_rvv_vdiv_vx_tama:
  ID = Intrinsic::riscv_vdiv_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vdiv_vv_tumu:
case RISCVVector::BI__builtin_rvv_vdiv_vx_tumu:
  ID = Intrinsic::riscv_vdiv_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vdiv_vv_tamu:
case RISCVVector::BI__builtin_rvv_vdiv_vx_tamu:
  ID = Intrinsic::riscv_vdiv_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vdivu_vv_tu:
case RISCVVector::BI__builtin_rvv_vdivu_vx_tu:
  ID = Intrinsic::riscv_vdivu;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vdivu_vv_ta:
case RISCVVector::BI__builtin_rvv_vdivu_vx_ta:
  ID = Intrinsic::riscv_vdivu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vdivu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vdivu_vx_tuma:
  ID = Intrinsic::riscv_vdivu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vdivu_vv_tama:
case RISCVVector::BI__builtin_rvv_vdivu_vx_tama:
  ID = Intrinsic::riscv_vdivu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vdivu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vdivu_vx_tumu:
  ID = Intrinsic::riscv_vdivu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vdivu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vdivu_vx_tamu:
  ID = Intrinsic::riscv_vdivu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfadd_vv_tu:
case RISCVVector::BI__builtin_rvv_vfadd_vf_tu:
  ID = Intrinsic::riscv_vfadd;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfadd_vv_ta:
case RISCVVector::BI__builtin_rvv_vfadd_vf_ta:
  ID = Intrinsic::riscv_vfadd;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfadd_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfadd_vf_tuma:
  ID = Intrinsic::riscv_vfadd_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfadd_vv_tama:
case RISCVVector::BI__builtin_rvv_vfadd_vf_tama:
  ID = Intrinsic::riscv_vfadd_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfadd_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfadd_vf_tumu:
  ID = Intrinsic::riscv_vfadd_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfadd_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfadd_vf_tamu:
  ID = Intrinsic::riscv_vfadd_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfclass_v_tu:
  ID = Intrinsic::riscv_vfclass;
  IntrinsicTypes = {Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfclass_v_ta:
  ID = Intrinsic::riscv_vfclass;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfclass_v_tuma:
  ID = Intrinsic::riscv_vfclass_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfclass_v_tama:
  ID = Intrinsic::riscv_vfclass_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfclass_v_tumu:
  ID = Intrinsic::riscv_vfclass_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfclass_v_tamu:
  ID = Intrinsic::riscv_vfclass_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_f_x_v_tu:
  ID = Intrinsic::riscv_vfcvt_f_x_v;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_f_x_v_ta:
  ID = Intrinsic::riscv_vfcvt_f_x_v;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_f_x_v_tuma:
  ID = Intrinsic::riscv_vfcvt_f_x_v_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_f_x_v_tama:
  ID = Intrinsic::riscv_vfcvt_f_x_v_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_f_x_v_tumu:
  ID = Intrinsic::riscv_vfcvt_f_x_v_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_f_x_v_tamu:
  ID = Intrinsic::riscv_vfcvt_f_x_v_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_f_xu_v_tu:
  ID = Intrinsic::riscv_vfcvt_f_xu_v;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_f_xu_v_ta:
  ID = Intrinsic::riscv_vfcvt_f_xu_v;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_f_xu_v_tuma:
  ID = Intrinsic::riscv_vfcvt_f_xu_v_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_f_xu_v_tama:
  ID = Intrinsic::riscv_vfcvt_f_xu_v_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_f_xu_v_tumu:
  ID = Intrinsic::riscv_vfcvt_f_xu_v_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_f_xu_v_tamu:
  ID = Intrinsic::riscv_vfcvt_f_xu_v_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_rtz_x_f_v_tu:
  ID = Intrinsic::riscv_vfcvt_rtz_x_f_v;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_rtz_x_f_v_ta:
  ID = Intrinsic::riscv_vfcvt_rtz_x_f_v;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_rtz_x_f_v_tuma:
  ID = Intrinsic::riscv_vfcvt_rtz_x_f_v_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_rtz_x_f_v_tama:
  ID = Intrinsic::riscv_vfcvt_rtz_x_f_v_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_rtz_x_f_v_tumu:
  ID = Intrinsic::riscv_vfcvt_rtz_x_f_v_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_rtz_x_f_v_tamu:
  ID = Intrinsic::riscv_vfcvt_rtz_x_f_v_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_rtz_xu_f_v_tu:
  ID = Intrinsic::riscv_vfcvt_rtz_xu_f_v;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_rtz_xu_f_v_ta:
  ID = Intrinsic::riscv_vfcvt_rtz_xu_f_v;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_rtz_xu_f_v_tuma:
  ID = Intrinsic::riscv_vfcvt_rtz_xu_f_v_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_rtz_xu_f_v_tama:
  ID = Intrinsic::riscv_vfcvt_rtz_xu_f_v_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_rtz_xu_f_v_tumu:
  ID = Intrinsic::riscv_vfcvt_rtz_xu_f_v_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_rtz_xu_f_v_tamu:
  ID = Intrinsic::riscv_vfcvt_rtz_xu_f_v_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_x_f_v_tu:
  ID = Intrinsic::riscv_vfcvt_x_f_v;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_x_f_v_ta:
  ID = Intrinsic::riscv_vfcvt_x_f_v;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_x_f_v_tuma:
  ID = Intrinsic::riscv_vfcvt_x_f_v_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_x_f_v_tama:
  ID = Intrinsic::riscv_vfcvt_x_f_v_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_x_f_v_tumu:
  ID = Intrinsic::riscv_vfcvt_x_f_v_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_x_f_v_tamu:
  ID = Intrinsic::riscv_vfcvt_x_f_v_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_xu_f_v_tu:
  ID = Intrinsic::riscv_vfcvt_xu_f_v;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_xu_f_v_ta:
  ID = Intrinsic::riscv_vfcvt_xu_f_v;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_xu_f_v_tuma:
  ID = Intrinsic::riscv_vfcvt_xu_f_v_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_xu_f_v_tama:
  ID = Intrinsic::riscv_vfcvt_xu_f_v_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_xu_f_v_tumu:
  ID = Intrinsic::riscv_vfcvt_xu_f_v_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfcvt_xu_f_v_tamu:
  ID = Intrinsic::riscv_vfcvt_xu_f_v_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfdiv_vv_tu:
case RISCVVector::BI__builtin_rvv_vfdiv_vf_tu:
  ID = Intrinsic::riscv_vfdiv;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfdiv_vv_ta:
case RISCVVector::BI__builtin_rvv_vfdiv_vf_ta:
  ID = Intrinsic::riscv_vfdiv;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfdiv_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfdiv_vf_tuma:
  ID = Intrinsic::riscv_vfdiv_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfdiv_vv_tama:
case RISCVVector::BI__builtin_rvv_vfdiv_vf_tama:
  ID = Intrinsic::riscv_vfdiv_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfdiv_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfdiv_vf_tumu:
  ID = Intrinsic::riscv_vfdiv_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfdiv_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfdiv_vf_tamu:
  ID = Intrinsic::riscv_vfdiv_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfirst_m:
  ID = Intrinsic::riscv_vfirst;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfirst_m_m:
  ID = Intrinsic::riscv_vfirst_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[0]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmacc_vv_tu:
case RISCVVector::BI__builtin_rvv_vfmacc_vf_tu:
  ID = Intrinsic::riscv_vfmacc;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmacc_vv_ta:
case RISCVVector::BI__builtin_rvv_vfmacc_vf_ta:
  ID = Intrinsic::riscv_vfmacc;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmacc_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfmacc_vf_tuma:
  ID = Intrinsic::riscv_vfmacc_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmacc_vv_tama:
case RISCVVector::BI__builtin_rvv_vfmacc_vf_tama:
  ID = Intrinsic::riscv_vfmacc_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmacc_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfmacc_vf_tumu:
  ID = Intrinsic::riscv_vfmacc_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmacc_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfmacc_vf_tamu:
  ID = Intrinsic::riscv_vfmacc_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmadd_vv_tu:
case RISCVVector::BI__builtin_rvv_vfmadd_vf_tu:
  ID = Intrinsic::riscv_vfmadd;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmadd_vv_ta:
case RISCVVector::BI__builtin_rvv_vfmadd_vf_ta:
  ID = Intrinsic::riscv_vfmadd;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmadd_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfmadd_vf_tuma:
  ID = Intrinsic::riscv_vfmadd_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmadd_vv_tama:
case RISCVVector::BI__builtin_rvv_vfmadd_vf_tama:
  ID = Intrinsic::riscv_vfmadd_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmadd_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfmadd_vf_tumu:
  ID = Intrinsic::riscv_vfmadd_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmadd_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfmadd_vf_tamu:
  ID = Intrinsic::riscv_vfmadd_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmax_vv_tu:
case RISCVVector::BI__builtin_rvv_vfmax_vf_tu:
  ID = Intrinsic::riscv_vfmax;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmax_vv_ta:
case RISCVVector::BI__builtin_rvv_vfmax_vf_ta:
  ID = Intrinsic::riscv_vfmax;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmax_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfmax_vf_tuma:
  ID = Intrinsic::riscv_vfmax_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmax_vv_tama:
case RISCVVector::BI__builtin_rvv_vfmax_vf_tama:
  ID = Intrinsic::riscv_vfmax_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmax_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfmax_vf_tumu:
  ID = Intrinsic::riscv_vfmax_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmax_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfmax_vf_tamu:
  ID = Intrinsic::riscv_vfmax_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmerge_vfm_tu:
  ID = Intrinsic::riscv_vfmerge;
  DefaultPolicy = 0;

      std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
      // insert poison passthru
      if (DefaultPolicy == TAIL_AGNOSTIC)
        Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
      IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
    break;
case RISCVVector::BI__builtin_rvv_vfmerge_vfm_ta:
  ID = Intrinsic::riscv_vfmerge;
  DefaultPolicy = 1;

      std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
      // insert poison passthru
      if (DefaultPolicy == TAIL_AGNOSTIC)
        Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
      IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
    break;
case RISCVVector::BI__builtin_rvv_vfmin_vv_tu:
case RISCVVector::BI__builtin_rvv_vfmin_vf_tu:
  ID = Intrinsic::riscv_vfmin;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmin_vv_ta:
case RISCVVector::BI__builtin_rvv_vfmin_vf_ta:
  ID = Intrinsic::riscv_vfmin;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmin_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfmin_vf_tuma:
  ID = Intrinsic::riscv_vfmin_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmin_vv_tama:
case RISCVVector::BI__builtin_rvv_vfmin_vf_tama:
  ID = Intrinsic::riscv_vfmin_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmin_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfmin_vf_tumu:
  ID = Intrinsic::riscv_vfmin_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmin_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfmin_vf_tamu:
  ID = Intrinsic::riscv_vfmin_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmsac_vv_tu:
case RISCVVector::BI__builtin_rvv_vfmsac_vf_tu:
  ID = Intrinsic::riscv_vfmsac;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmsac_vv_ta:
case RISCVVector::BI__builtin_rvv_vfmsac_vf_ta:
  ID = Intrinsic::riscv_vfmsac;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmsac_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfmsac_vf_tuma:
  ID = Intrinsic::riscv_vfmsac_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmsac_vv_tama:
case RISCVVector::BI__builtin_rvv_vfmsac_vf_tama:
  ID = Intrinsic::riscv_vfmsac_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmsac_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfmsac_vf_tumu:
  ID = Intrinsic::riscv_vfmsac_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmsac_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfmsac_vf_tamu:
  ID = Intrinsic::riscv_vfmsac_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmsub_vv_tu:
case RISCVVector::BI__builtin_rvv_vfmsub_vf_tu:
  ID = Intrinsic::riscv_vfmsub;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmsub_vv_ta:
case RISCVVector::BI__builtin_rvv_vfmsub_vf_ta:
  ID = Intrinsic::riscv_vfmsub;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmsub_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfmsub_vf_tuma:
  ID = Intrinsic::riscv_vfmsub_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmsub_vv_tama:
case RISCVVector::BI__builtin_rvv_vfmsub_vf_tama:
  ID = Intrinsic::riscv_vfmsub_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmsub_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfmsub_vf_tumu:
  ID = Intrinsic::riscv_vfmsub_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmsub_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfmsub_vf_tamu:
  ID = Intrinsic::riscv_vfmsub_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmul_vv_tu:
case RISCVVector::BI__builtin_rvv_vfmul_vf_tu:
  ID = Intrinsic::riscv_vfmul;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmul_vv_ta:
case RISCVVector::BI__builtin_rvv_vfmul_vf_ta:
  ID = Intrinsic::riscv_vfmul;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmul_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfmul_vf_tuma:
  ID = Intrinsic::riscv_vfmul_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmul_vv_tama:
case RISCVVector::BI__builtin_rvv_vfmul_vf_tama:
  ID = Intrinsic::riscv_vfmul_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmul_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfmul_vf_tumu:
  ID = Intrinsic::riscv_vfmul_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmul_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfmul_vf_tamu:
  ID = Intrinsic::riscv_vfmul_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmv_f_s:
  ID = Intrinsic::riscv_vfmv_f_s;
  IntrinsicTypes = {Ops[0]->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmv_s_f_tu:
case RISCVVector::BI__builtin_rvv_vfmv_s_x_tu:
  ID = Intrinsic::riscv_vfmv_s_f;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmv_s_f_ta:
case RISCVVector::BI__builtin_rvv_vfmv_s_x_ta:
  ID = Intrinsic::riscv_vfmv_s_f;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmv_v_f_tu:
  ID = Intrinsic::riscv_vfmv_v_f;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfmv_v_f_ta:
  ID = Intrinsic::riscv_vfmv_v_f;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_f_w_tu:
  ID = Intrinsic::riscv_vfncvt_f_f_w;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_f_w_ta:
  ID = Intrinsic::riscv_vfncvt_f_f_w;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_f_w_tuma:
  ID = Intrinsic::riscv_vfncvt_f_f_w_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_f_w_tama:
  ID = Intrinsic::riscv_vfncvt_f_f_w_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_f_w_tumu:
  ID = Intrinsic::riscv_vfncvt_f_f_w_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_f_w_tamu:
  ID = Intrinsic::riscv_vfncvt_f_f_w_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_x_w_tu:
  ID = Intrinsic::riscv_vfncvt_f_x_w;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_x_w_ta:
  ID = Intrinsic::riscv_vfncvt_f_x_w;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_x_w_tuma:
  ID = Intrinsic::riscv_vfncvt_f_x_w_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_x_w_tama:
  ID = Intrinsic::riscv_vfncvt_f_x_w_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_x_w_tumu:
  ID = Intrinsic::riscv_vfncvt_f_x_w_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_x_w_tamu:
  ID = Intrinsic::riscv_vfncvt_f_x_w_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_xu_w_tu:
  ID = Intrinsic::riscv_vfncvt_f_xu_w;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_xu_w_ta:
  ID = Intrinsic::riscv_vfncvt_f_xu_w;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_xu_w_tuma:
  ID = Intrinsic::riscv_vfncvt_f_xu_w_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_xu_w_tama:
  ID = Intrinsic::riscv_vfncvt_f_xu_w_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_xu_w_tumu:
  ID = Intrinsic::riscv_vfncvt_f_xu_w_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_f_xu_w_tamu:
  ID = Intrinsic::riscv_vfncvt_f_xu_w_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rod_f_f_w_tu:
  ID = Intrinsic::riscv_vfncvt_rod_f_f_w;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rod_f_f_w_ta:
  ID = Intrinsic::riscv_vfncvt_rod_f_f_w;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rod_f_f_w_tuma:
  ID = Intrinsic::riscv_vfncvt_rod_f_f_w_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rod_f_f_w_tama:
  ID = Intrinsic::riscv_vfncvt_rod_f_f_w_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rod_f_f_w_tumu:
  ID = Intrinsic::riscv_vfncvt_rod_f_f_w_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rod_f_f_w_tamu:
  ID = Intrinsic::riscv_vfncvt_rod_f_f_w_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rtz_x_f_w_tu:
  ID = Intrinsic::riscv_vfncvt_rtz_x_f_w;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rtz_x_f_w_ta:
  ID = Intrinsic::riscv_vfncvt_rtz_x_f_w;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rtz_x_f_w_tuma:
  ID = Intrinsic::riscv_vfncvt_rtz_x_f_w_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rtz_x_f_w_tama:
  ID = Intrinsic::riscv_vfncvt_rtz_x_f_w_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rtz_x_f_w_tumu:
  ID = Intrinsic::riscv_vfncvt_rtz_x_f_w_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rtz_x_f_w_tamu:
  ID = Intrinsic::riscv_vfncvt_rtz_x_f_w_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rtz_xu_f_w_tu:
  ID = Intrinsic::riscv_vfncvt_rtz_xu_f_w;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rtz_xu_f_w_ta:
  ID = Intrinsic::riscv_vfncvt_rtz_xu_f_w;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rtz_xu_f_w_tuma:
  ID = Intrinsic::riscv_vfncvt_rtz_xu_f_w_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rtz_xu_f_w_tama:
  ID = Intrinsic::riscv_vfncvt_rtz_xu_f_w_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rtz_xu_f_w_tumu:
  ID = Intrinsic::riscv_vfncvt_rtz_xu_f_w_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_rtz_xu_f_w_tamu:
  ID = Intrinsic::riscv_vfncvt_rtz_xu_f_w_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_x_f_w_tu:
  ID = Intrinsic::riscv_vfncvt_x_f_w;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_x_f_w_ta:
  ID = Intrinsic::riscv_vfncvt_x_f_w;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_x_f_w_tuma:
  ID = Intrinsic::riscv_vfncvt_x_f_w_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_x_f_w_tama:
  ID = Intrinsic::riscv_vfncvt_x_f_w_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_x_f_w_tumu:
  ID = Intrinsic::riscv_vfncvt_x_f_w_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_x_f_w_tamu:
  ID = Intrinsic::riscv_vfncvt_x_f_w_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_xu_f_w_tu:
  ID = Intrinsic::riscv_vfncvt_xu_f_w;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_xu_f_w_ta:
  ID = Intrinsic::riscv_vfncvt_xu_f_w;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_xu_f_w_tuma:
  ID = Intrinsic::riscv_vfncvt_xu_f_w_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_xu_f_w_tama:
  ID = Intrinsic::riscv_vfncvt_xu_f_w_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_xu_f_w_tumu:
  ID = Intrinsic::riscv_vfncvt_xu_f_w_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfncvt_xu_f_w_tamu:
  ID = Intrinsic::riscv_vfncvt_xu_f_w_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmacc_vv_tu:
case RISCVVector::BI__builtin_rvv_vfnmacc_vf_tu:
  ID = Intrinsic::riscv_vfnmacc;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmacc_vv_ta:
case RISCVVector::BI__builtin_rvv_vfnmacc_vf_ta:
  ID = Intrinsic::riscv_vfnmacc;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmacc_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfnmacc_vf_tuma:
  ID = Intrinsic::riscv_vfnmacc_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmacc_vv_tama:
case RISCVVector::BI__builtin_rvv_vfnmacc_vf_tama:
  ID = Intrinsic::riscv_vfnmacc_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmacc_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfnmacc_vf_tumu:
  ID = Intrinsic::riscv_vfnmacc_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmacc_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfnmacc_vf_tamu:
  ID = Intrinsic::riscv_vfnmacc_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmadd_vv_tu:
case RISCVVector::BI__builtin_rvv_vfnmadd_vf_tu:
  ID = Intrinsic::riscv_vfnmadd;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmadd_vv_ta:
case RISCVVector::BI__builtin_rvv_vfnmadd_vf_ta:
  ID = Intrinsic::riscv_vfnmadd;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmadd_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfnmadd_vf_tuma:
  ID = Intrinsic::riscv_vfnmadd_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmadd_vv_tama:
case RISCVVector::BI__builtin_rvv_vfnmadd_vf_tama:
  ID = Intrinsic::riscv_vfnmadd_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmadd_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfnmadd_vf_tumu:
  ID = Intrinsic::riscv_vfnmadd_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmadd_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfnmadd_vf_tamu:
  ID = Intrinsic::riscv_vfnmadd_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmsac_vv_tu:
case RISCVVector::BI__builtin_rvv_vfnmsac_vf_tu:
  ID = Intrinsic::riscv_vfnmsac;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmsac_vv_ta:
case RISCVVector::BI__builtin_rvv_vfnmsac_vf_ta:
  ID = Intrinsic::riscv_vfnmsac;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmsac_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfnmsac_vf_tuma:
  ID = Intrinsic::riscv_vfnmsac_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmsac_vv_tama:
case RISCVVector::BI__builtin_rvv_vfnmsac_vf_tama:
  ID = Intrinsic::riscv_vfnmsac_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmsac_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfnmsac_vf_tumu:
  ID = Intrinsic::riscv_vfnmsac_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmsac_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfnmsac_vf_tamu:
  ID = Intrinsic::riscv_vfnmsac_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmsub_vv_tu:
case RISCVVector::BI__builtin_rvv_vfnmsub_vf_tu:
  ID = Intrinsic::riscv_vfnmsub;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmsub_vv_ta:
case RISCVVector::BI__builtin_rvv_vfnmsub_vf_ta:
  ID = Intrinsic::riscv_vfnmsub;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmsub_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfnmsub_vf_tuma:
  ID = Intrinsic::riscv_vfnmsub_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmsub_vv_tama:
case RISCVVector::BI__builtin_rvv_vfnmsub_vf_tama:
  ID = Intrinsic::riscv_vfnmsub_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmsub_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfnmsub_vf_tumu:
  ID = Intrinsic::riscv_vfnmsub_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfnmsub_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfnmsub_vf_tamu:
  ID = Intrinsic::riscv_vfnmsub_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrdiv_vf_tu:
  ID = Intrinsic::riscv_vfrdiv;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrdiv_vf_ta:
  ID = Intrinsic::riscv_vfrdiv;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrdiv_vf_tuma:
  ID = Intrinsic::riscv_vfrdiv_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrdiv_vf_tama:
  ID = Intrinsic::riscv_vfrdiv_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrdiv_vf_tumu:
  ID = Intrinsic::riscv_vfrdiv_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrdiv_vf_tamu:
  ID = Intrinsic::riscv_vfrdiv_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrec7_v_tu:
  ID = Intrinsic::riscv_vfrec7;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrec7_v_ta:
  ID = Intrinsic::riscv_vfrec7;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrec7_v_tuma:
  ID = Intrinsic::riscv_vfrec7_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrec7_v_tama:
  ID = Intrinsic::riscv_vfrec7_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrec7_v_tumu:
  ID = Intrinsic::riscv_vfrec7_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrec7_v_tamu:
  ID = Intrinsic::riscv_vfrec7_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredmax_vs_tu:
  ID = Intrinsic::riscv_vfredmax;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredmax_vs_ta:
  ID = Intrinsic::riscv_vfredmax;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredmax_vs_tum:
  ID = Intrinsic::riscv_vfredmax_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredmax_vs_tam:
  ID = Intrinsic::riscv_vfredmax_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredmax_vs_tumu:
  ID = Intrinsic::riscv_vfredmax_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredmin_vs_tu:
  ID = Intrinsic::riscv_vfredmin;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredmin_vs_ta:
  ID = Intrinsic::riscv_vfredmin;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredmin_vs_tum:
  ID = Intrinsic::riscv_vfredmin_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredmin_vs_tam:
  ID = Intrinsic::riscv_vfredmin_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredmin_vs_tumu:
  ID = Intrinsic::riscv_vfredmin_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredosum_vs_tu:
  ID = Intrinsic::riscv_vfredosum;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredosum_vs_ta:
  ID = Intrinsic::riscv_vfredosum;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredosum_vs_tum:
  ID = Intrinsic::riscv_vfredosum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredosum_vs_tam:
  ID = Intrinsic::riscv_vfredosum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredosum_vs_tumu:
  ID = Intrinsic::riscv_vfredosum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredusum_vs_tu:
  ID = Intrinsic::riscv_vfredusum;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredusum_vs_ta:
  ID = Intrinsic::riscv_vfredusum;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredusum_vs_tum:
  ID = Intrinsic::riscv_vfredusum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredusum_vs_tam:
  ID = Intrinsic::riscv_vfredusum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfredusum_vs_tumu:
  ID = Intrinsic::riscv_vfredusum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrsqrt7_v_tu:
  ID = Intrinsic::riscv_vfrsqrt7;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrsqrt7_v_ta:
  ID = Intrinsic::riscv_vfrsqrt7;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrsqrt7_v_tuma:
  ID = Intrinsic::riscv_vfrsqrt7_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrsqrt7_v_tama:
  ID = Intrinsic::riscv_vfrsqrt7_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrsqrt7_v_tumu:
  ID = Intrinsic::riscv_vfrsqrt7_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrsqrt7_v_tamu:
  ID = Intrinsic::riscv_vfrsqrt7_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrsub_vf_tu:
  ID = Intrinsic::riscv_vfrsub;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrsub_vf_ta:
  ID = Intrinsic::riscv_vfrsub;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrsub_vf_tuma:
  ID = Intrinsic::riscv_vfrsub_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrsub_vf_tama:
  ID = Intrinsic::riscv_vfrsub_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrsub_vf_tumu:
  ID = Intrinsic::riscv_vfrsub_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfrsub_vf_tamu:
  ID = Intrinsic::riscv_vfrsub_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsgnj_vv_tu:
case RISCVVector::BI__builtin_rvv_vfsgnj_vf_tu:
  ID = Intrinsic::riscv_vfsgnj;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsgnj_vv_ta:
case RISCVVector::BI__builtin_rvv_vfsgnj_vf_ta:
  ID = Intrinsic::riscv_vfsgnj;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsgnj_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfsgnj_vf_tuma:
  ID = Intrinsic::riscv_vfsgnj_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsgnj_vv_tama:
case RISCVVector::BI__builtin_rvv_vfsgnj_vf_tama:
  ID = Intrinsic::riscv_vfsgnj_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsgnj_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfsgnj_vf_tumu:
  ID = Intrinsic::riscv_vfsgnj_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsgnj_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfsgnj_vf_tamu:
  ID = Intrinsic::riscv_vfsgnj_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsgnjn_vv_tu:
case RISCVVector::BI__builtin_rvv_vfsgnjn_vf_tu:
  ID = Intrinsic::riscv_vfsgnjn;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfneg_v_tu:
  ID = Intrinsic::riscv_vfsgnjn;
  DefaultPolicy = 0;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        // op1, po2, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(), Ops[2]->getType()};
        Ops.insert(Ops.begin() + 2, Ops[1]);
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vfsgnjn_vv_ta:
case RISCVVector::BI__builtin_rvv_vfsgnjn_vf_ta:
  ID = Intrinsic::riscv_vfsgnjn;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfneg_v_ta:
  ID = Intrinsic::riscv_vfsgnjn;
  DefaultPolicy = 1;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        // op1, po2, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(), Ops[2]->getType()};
        Ops.insert(Ops.begin() + 2, Ops[1]);
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vfsgnjn_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfsgnjn_vf_tuma:
  ID = Intrinsic::riscv_vfsgnjn_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfneg_v_tuma:
  ID = Intrinsic::riscv_vfsgnjn_mask;
  DefaultPolicy = 2;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.insert(Ops.begin() + 2, Ops[1]);
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl
        IntrinsicTypes = {ResultType,
                          Ops[2]->getType(),
                          Ops.back()->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vfsgnjn_vv_tama:
case RISCVVector::BI__builtin_rvv_vfsgnjn_vf_tama:
  ID = Intrinsic::riscv_vfsgnjn_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfneg_v_tama:
  ID = Intrinsic::riscv_vfsgnjn_mask;
  DefaultPolicy = 3;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.insert(Ops.begin() + 2, Ops[1]);
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl
        IntrinsicTypes = {ResultType,
                          Ops[2]->getType(),
                          Ops.back()->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vfsgnjn_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfsgnjn_vf_tumu:
  ID = Intrinsic::riscv_vfsgnjn_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfneg_v_tumu:
  ID = Intrinsic::riscv_vfsgnjn_mask;
  DefaultPolicy = 0;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.insert(Ops.begin() + 2, Ops[1]);
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl
        IntrinsicTypes = {ResultType,
                          Ops[2]->getType(),
                          Ops.back()->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vfsgnjn_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfsgnjn_vf_tamu:
  ID = Intrinsic::riscv_vfsgnjn_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfneg_v_tamu:
  ID = Intrinsic::riscv_vfsgnjn_mask;
  DefaultPolicy = 1;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.insert(Ops.begin() + 2, Ops[1]);
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl
        IntrinsicTypes = {ResultType,
                          Ops[2]->getType(),
                          Ops.back()->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vfsgnjx_vv_tu:
case RISCVVector::BI__builtin_rvv_vfsgnjx_vf_tu:
  ID = Intrinsic::riscv_vfsgnjx;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfabs_v_tu:
  ID = Intrinsic::riscv_vfsgnjx;
  DefaultPolicy = 0;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        // op1, po2, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(), Ops[2]->getType()};
        Ops.insert(Ops.begin() + 2, Ops[1]);
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vfsgnjx_vv_ta:
case RISCVVector::BI__builtin_rvv_vfsgnjx_vf_ta:
  ID = Intrinsic::riscv_vfsgnjx;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfabs_v_ta:
  ID = Intrinsic::riscv_vfsgnjx;
  DefaultPolicy = 1;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        // op1, po2, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(), Ops[2]->getType()};
        Ops.insert(Ops.begin() + 2, Ops[1]);
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vfsgnjx_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfsgnjx_vf_tuma:
  ID = Intrinsic::riscv_vfsgnjx_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfabs_v_tuma:
  ID = Intrinsic::riscv_vfsgnjx_mask;
  DefaultPolicy = 2;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.insert(Ops.begin() + 2, Ops[1]);
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl
        IntrinsicTypes = {ResultType,
                          Ops[2]->getType(),
                          Ops.back()->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vfsgnjx_vv_tama:
case RISCVVector::BI__builtin_rvv_vfsgnjx_vf_tama:
  ID = Intrinsic::riscv_vfsgnjx_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfabs_v_tama:
  ID = Intrinsic::riscv_vfsgnjx_mask;
  DefaultPolicy = 3;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.insert(Ops.begin() + 2, Ops[1]);
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl
        IntrinsicTypes = {ResultType,
                          Ops[2]->getType(),
                          Ops.back()->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vfsgnjx_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfsgnjx_vf_tumu:
  ID = Intrinsic::riscv_vfsgnjx_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfabs_v_tumu:
  ID = Intrinsic::riscv_vfsgnjx_mask;
  DefaultPolicy = 0;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.insert(Ops.begin() + 2, Ops[1]);
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl
        IntrinsicTypes = {ResultType,
                          Ops[2]->getType(),
                          Ops.back()->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vfsgnjx_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfsgnjx_vf_tamu:
  ID = Intrinsic::riscv_vfsgnjx_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfabs_v_tamu:
  ID = Intrinsic::riscv_vfsgnjx_mask;
  DefaultPolicy = 1;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.insert(Ops.begin() + 2, Ops[1]);
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl
        IntrinsicTypes = {ResultType,
                          Ops[2]->getType(),
                          Ops.back()->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vfslide1down_vf_tu:
  ID = Intrinsic::riscv_vfslide1down;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfslide1down_vf_ta:
  ID = Intrinsic::riscv_vfslide1down;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfslide1down_vf_tuma:
  ID = Intrinsic::riscv_vfslide1down_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfslide1down_vf_tama:
  ID = Intrinsic::riscv_vfslide1down_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfslide1down_vf_tumu:
  ID = Intrinsic::riscv_vfslide1down_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfslide1down_vf_tamu:
  ID = Intrinsic::riscv_vfslide1down_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfslide1up_vf_tu:
  ID = Intrinsic::riscv_vfslide1up;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfslide1up_vf_ta:
  ID = Intrinsic::riscv_vfslide1up;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfslide1up_vf_tuma:
  ID = Intrinsic::riscv_vfslide1up_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfslide1up_vf_tama:
  ID = Intrinsic::riscv_vfslide1up_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfslide1up_vf_tumu:
  ID = Intrinsic::riscv_vfslide1up_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfslide1up_vf_tamu:
  ID = Intrinsic::riscv_vfslide1up_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsqrt_v_tu:
  ID = Intrinsic::riscv_vfsqrt;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsqrt_v_ta:
  ID = Intrinsic::riscv_vfsqrt;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsqrt_v_tuma:
  ID = Intrinsic::riscv_vfsqrt_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsqrt_v_tama:
  ID = Intrinsic::riscv_vfsqrt_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsqrt_v_tumu:
  ID = Intrinsic::riscv_vfsqrt_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsqrt_v_tamu:
  ID = Intrinsic::riscv_vfsqrt_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsub_vv_tu:
case RISCVVector::BI__builtin_rvv_vfsub_vf_tu:
  ID = Intrinsic::riscv_vfsub;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsub_vv_ta:
case RISCVVector::BI__builtin_rvv_vfsub_vf_ta:
  ID = Intrinsic::riscv_vfsub;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsub_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfsub_vf_tuma:
  ID = Intrinsic::riscv_vfsub_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsub_vv_tama:
case RISCVVector::BI__builtin_rvv_vfsub_vf_tama:
  ID = Intrinsic::riscv_vfsub_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsub_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfsub_vf_tumu:
  ID = Intrinsic::riscv_vfsub_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfsub_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfsub_vf_tamu:
  ID = Intrinsic::riscv_vfsub_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwadd_vv_tu:
case RISCVVector::BI__builtin_rvv_vfwadd_vf_tu:
  ID = Intrinsic::riscv_vfwadd;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwadd_vv_ta:
case RISCVVector::BI__builtin_rvv_vfwadd_vf_ta:
  ID = Intrinsic::riscv_vfwadd;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwadd_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfwadd_vf_tuma:
  ID = Intrinsic::riscv_vfwadd_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwadd_vv_tama:
case RISCVVector::BI__builtin_rvv_vfwadd_vf_tama:
  ID = Intrinsic::riscv_vfwadd_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwadd_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfwadd_vf_tumu:
  ID = Intrinsic::riscv_vfwadd_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwadd_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfwadd_vf_tamu:
  ID = Intrinsic::riscv_vfwadd_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwadd_wv_tu:
case RISCVVector::BI__builtin_rvv_vfwadd_wf_tu:
  ID = Intrinsic::riscv_vfwadd_w;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwadd_wv_ta:
case RISCVVector::BI__builtin_rvv_vfwadd_wf_ta:
  ID = Intrinsic::riscv_vfwadd_w;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwadd_wv_tuma:
case RISCVVector::BI__builtin_rvv_vfwadd_wf_tuma:
  ID = Intrinsic::riscv_vfwadd_w_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwadd_wv_tama:
case RISCVVector::BI__builtin_rvv_vfwadd_wf_tama:
  ID = Intrinsic::riscv_vfwadd_w_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwadd_wv_tumu:
case RISCVVector::BI__builtin_rvv_vfwadd_wf_tumu:
  ID = Intrinsic::riscv_vfwadd_w_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwadd_wv_tamu:
case RISCVVector::BI__builtin_rvv_vfwadd_wf_tamu:
  ID = Intrinsic::riscv_vfwadd_w_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_f_v_tu:
  ID = Intrinsic::riscv_vfwcvt_f_f_v;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_f_v_ta:
  ID = Intrinsic::riscv_vfwcvt_f_f_v;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_f_v_tuma:
  ID = Intrinsic::riscv_vfwcvt_f_f_v_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_f_v_tama:
  ID = Intrinsic::riscv_vfwcvt_f_f_v_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_f_v_tumu:
  ID = Intrinsic::riscv_vfwcvt_f_f_v_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_f_v_tamu:
  ID = Intrinsic::riscv_vfwcvt_f_f_v_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_x_v_tu:
  ID = Intrinsic::riscv_vfwcvt_f_x_v;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_x_v_ta:
  ID = Intrinsic::riscv_vfwcvt_f_x_v;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_x_v_tuma:
  ID = Intrinsic::riscv_vfwcvt_f_x_v_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_x_v_tama:
  ID = Intrinsic::riscv_vfwcvt_f_x_v_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_x_v_tumu:
  ID = Intrinsic::riscv_vfwcvt_f_x_v_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_x_v_tamu:
  ID = Intrinsic::riscv_vfwcvt_f_x_v_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_xu_v_tu:
  ID = Intrinsic::riscv_vfwcvt_f_xu_v;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_xu_v_ta:
  ID = Intrinsic::riscv_vfwcvt_f_xu_v;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_xu_v_tuma:
  ID = Intrinsic::riscv_vfwcvt_f_xu_v_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_xu_v_tama:
  ID = Intrinsic::riscv_vfwcvt_f_xu_v_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_xu_v_tumu:
  ID = Intrinsic::riscv_vfwcvt_f_xu_v_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_f_xu_v_tamu:
  ID = Intrinsic::riscv_vfwcvt_f_xu_v_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_rtz_x_f_v_tu:
  ID = Intrinsic::riscv_vfwcvt_rtz_x_f_v;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_rtz_x_f_v_ta:
  ID = Intrinsic::riscv_vfwcvt_rtz_x_f_v;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_rtz_x_f_v_tuma:
  ID = Intrinsic::riscv_vfwcvt_rtz_x_f_v_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_rtz_x_f_v_tama:
  ID = Intrinsic::riscv_vfwcvt_rtz_x_f_v_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_rtz_x_f_v_tumu:
  ID = Intrinsic::riscv_vfwcvt_rtz_x_f_v_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_rtz_x_f_v_tamu:
  ID = Intrinsic::riscv_vfwcvt_rtz_x_f_v_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_rtz_xu_f_v_tu:
  ID = Intrinsic::riscv_vfwcvt_rtz_xu_f_v;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_rtz_xu_f_v_ta:
  ID = Intrinsic::riscv_vfwcvt_rtz_xu_f_v;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_rtz_xu_f_v_tuma:
  ID = Intrinsic::riscv_vfwcvt_rtz_xu_f_v_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_rtz_xu_f_v_tama:
  ID = Intrinsic::riscv_vfwcvt_rtz_xu_f_v_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_rtz_xu_f_v_tumu:
  ID = Intrinsic::riscv_vfwcvt_rtz_xu_f_v_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_rtz_xu_f_v_tamu:
  ID = Intrinsic::riscv_vfwcvt_rtz_xu_f_v_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_x_f_v_tu:
  ID = Intrinsic::riscv_vfwcvt_x_f_v;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_x_f_v_ta:
  ID = Intrinsic::riscv_vfwcvt_x_f_v;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_x_f_v_tuma:
  ID = Intrinsic::riscv_vfwcvt_x_f_v_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_x_f_v_tama:
  ID = Intrinsic::riscv_vfwcvt_x_f_v_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_x_f_v_tumu:
  ID = Intrinsic::riscv_vfwcvt_x_f_v_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_x_f_v_tamu:
  ID = Intrinsic::riscv_vfwcvt_x_f_v_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_xu_f_v_tu:
  ID = Intrinsic::riscv_vfwcvt_xu_f_v;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_xu_f_v_ta:
  ID = Intrinsic::riscv_vfwcvt_xu_f_v;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_xu_f_v_tuma:
  ID = Intrinsic::riscv_vfwcvt_xu_f_v_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_xu_f_v_tama:
  ID = Intrinsic::riscv_vfwcvt_xu_f_v_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_xu_f_v_tumu:
  ID = Intrinsic::riscv_vfwcvt_xu_f_v_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwcvt_xu_f_v_tamu:
  ID = Intrinsic::riscv_vfwcvt_xu_f_v_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmacc_vv_tu:
case RISCVVector::BI__builtin_rvv_vfwmacc_vf_tu:
  ID = Intrinsic::riscv_vfwmacc;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmacc_vv_ta:
case RISCVVector::BI__builtin_rvv_vfwmacc_vf_ta:
  ID = Intrinsic::riscv_vfwmacc;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmacc_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfwmacc_vf_tuma:
  ID = Intrinsic::riscv_vfwmacc_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmacc_vv_tama:
case RISCVVector::BI__builtin_rvv_vfwmacc_vf_tama:
  ID = Intrinsic::riscv_vfwmacc_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmacc_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfwmacc_vf_tumu:
  ID = Intrinsic::riscv_vfwmacc_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmacc_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfwmacc_vf_tamu:
  ID = Intrinsic::riscv_vfwmacc_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmsac_vv_tu:
case RISCVVector::BI__builtin_rvv_vfwmsac_vf_tu:
  ID = Intrinsic::riscv_vfwmsac;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmsac_vv_ta:
case RISCVVector::BI__builtin_rvv_vfwmsac_vf_ta:
  ID = Intrinsic::riscv_vfwmsac;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmsac_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfwmsac_vf_tuma:
  ID = Intrinsic::riscv_vfwmsac_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmsac_vv_tama:
case RISCVVector::BI__builtin_rvv_vfwmsac_vf_tama:
  ID = Intrinsic::riscv_vfwmsac_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmsac_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfwmsac_vf_tumu:
  ID = Intrinsic::riscv_vfwmsac_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmsac_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfwmsac_vf_tamu:
  ID = Intrinsic::riscv_vfwmsac_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmul_vv_tu:
case RISCVVector::BI__builtin_rvv_vfwmul_vf_tu:
  ID = Intrinsic::riscv_vfwmul;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmul_vv_ta:
case RISCVVector::BI__builtin_rvv_vfwmul_vf_ta:
  ID = Intrinsic::riscv_vfwmul;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmul_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfwmul_vf_tuma:
  ID = Intrinsic::riscv_vfwmul_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmul_vv_tama:
case RISCVVector::BI__builtin_rvv_vfwmul_vf_tama:
  ID = Intrinsic::riscv_vfwmul_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmul_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfwmul_vf_tumu:
  ID = Intrinsic::riscv_vfwmul_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwmul_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfwmul_vf_tamu:
  ID = Intrinsic::riscv_vfwmul_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwnmacc_vv_tu:
case RISCVVector::BI__builtin_rvv_vfwnmacc_vf_tu:
  ID = Intrinsic::riscv_vfwnmacc;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwnmacc_vv_ta:
case RISCVVector::BI__builtin_rvv_vfwnmacc_vf_ta:
  ID = Intrinsic::riscv_vfwnmacc;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwnmacc_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfwnmacc_vf_tuma:
  ID = Intrinsic::riscv_vfwnmacc_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwnmacc_vv_tama:
case RISCVVector::BI__builtin_rvv_vfwnmacc_vf_tama:
  ID = Intrinsic::riscv_vfwnmacc_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwnmacc_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfwnmacc_vf_tumu:
  ID = Intrinsic::riscv_vfwnmacc_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwnmacc_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfwnmacc_vf_tamu:
  ID = Intrinsic::riscv_vfwnmacc_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwnmsac_vv_tu:
case RISCVVector::BI__builtin_rvv_vfwnmsac_vf_tu:
  ID = Intrinsic::riscv_vfwnmsac;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwnmsac_vv_ta:
case RISCVVector::BI__builtin_rvv_vfwnmsac_vf_ta:
  ID = Intrinsic::riscv_vfwnmsac;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwnmsac_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfwnmsac_vf_tuma:
  ID = Intrinsic::riscv_vfwnmsac_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwnmsac_vv_tama:
case RISCVVector::BI__builtin_rvv_vfwnmsac_vf_tama:
  ID = Intrinsic::riscv_vfwnmsac_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwnmsac_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfwnmsac_vf_tumu:
  ID = Intrinsic::riscv_vfwnmsac_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwnmsac_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfwnmsac_vf_tamu:
  ID = Intrinsic::riscv_vfwnmsac_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwredosum_vs_tu:
  ID = Intrinsic::riscv_vfwredosum;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwredosum_vs_ta:
  ID = Intrinsic::riscv_vfwredosum;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwredosum_vs_tum:
  ID = Intrinsic::riscv_vfwredosum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwredosum_vs_tam:
  ID = Intrinsic::riscv_vfwredosum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwredosum_vs_tumu:
  ID = Intrinsic::riscv_vfwredosum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwredusum_vs_tu:
  ID = Intrinsic::riscv_vfwredusum;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwredusum_vs_ta:
  ID = Intrinsic::riscv_vfwredusum;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwredusum_vs_tum:
  ID = Intrinsic::riscv_vfwredusum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwredusum_vs_tam:
  ID = Intrinsic::riscv_vfwredusum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwredusum_vs_tumu:
  ID = Intrinsic::riscv_vfwredusum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwsub_vv_tu:
case RISCVVector::BI__builtin_rvv_vfwsub_vf_tu:
  ID = Intrinsic::riscv_vfwsub;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwsub_vv_ta:
case RISCVVector::BI__builtin_rvv_vfwsub_vf_ta:
  ID = Intrinsic::riscv_vfwsub;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwsub_vv_tuma:
case RISCVVector::BI__builtin_rvv_vfwsub_vf_tuma:
  ID = Intrinsic::riscv_vfwsub_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwsub_vv_tama:
case RISCVVector::BI__builtin_rvv_vfwsub_vf_tama:
  ID = Intrinsic::riscv_vfwsub_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwsub_vv_tumu:
case RISCVVector::BI__builtin_rvv_vfwsub_vf_tumu:
  ID = Intrinsic::riscv_vfwsub_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwsub_vv_tamu:
case RISCVVector::BI__builtin_rvv_vfwsub_vf_tamu:
  ID = Intrinsic::riscv_vfwsub_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwsub_wv_tu:
case RISCVVector::BI__builtin_rvv_vfwsub_wf_tu:
  ID = Intrinsic::riscv_vfwsub_w;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwsub_wv_ta:
case RISCVVector::BI__builtin_rvv_vfwsub_wf_ta:
  ID = Intrinsic::riscv_vfwsub_w;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwsub_wv_tuma:
case RISCVVector::BI__builtin_rvv_vfwsub_wf_tuma:
  ID = Intrinsic::riscv_vfwsub_w_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwsub_wv_tama:
case RISCVVector::BI__builtin_rvv_vfwsub_wf_tama:
  ID = Intrinsic::riscv_vfwsub_w_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwsub_wv_tumu:
case RISCVVector::BI__builtin_rvv_vfwsub_wf_tumu:
  ID = Intrinsic::riscv_vfwsub_w_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vfwsub_wv_tamu:
case RISCVVector::BI__builtin_rvv_vfwsub_wf_tamu:
  ID = Intrinsic::riscv_vfwsub_w_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vid_v_tu:
  ID = Intrinsic::riscv_vid;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vid_v_ta:
  ID = Intrinsic::riscv_vid;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vid_v_tuma:
  ID = Intrinsic::riscv_vid_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vid_v_tama:
  ID = Intrinsic::riscv_vid_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vid_v_tumu:
  ID = Intrinsic::riscv_vid_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vid_v_tamu:
  ID = Intrinsic::riscv_vid_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_viota_m_tu:
  ID = Intrinsic::riscv_viota;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_viota_m_ta:
  ID = Intrinsic::riscv_viota;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_viota_m_tuma:
  ID = Intrinsic::riscv_viota_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_viota_m_tama:
  ID = Intrinsic::riscv_viota_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_viota_m_tumu:
  ID = Intrinsic::riscv_viota_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_viota_m_tamu:
  ID = Intrinsic::riscv_viota_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vle8_v_tu:
case RISCVVector::BI__builtin_rvv_vle16_v_tu:
case RISCVVector::BI__builtin_rvv_vle32_v_tu:
case RISCVVector::BI__builtin_rvv_vle64_v_tu:
  ID = Intrinsic::riscv_vle;
  Ops[1] = Builder.CreateBitCast(Ops[1], ResultType->getPointerTo());
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vle8_v_ta:
case RISCVVector::BI__builtin_rvv_vle16_v_ta:
case RISCVVector::BI__builtin_rvv_vle32_v_ta:
case RISCVVector::BI__builtin_rvv_vle64_v_ta:
  ID = Intrinsic::riscv_vle;
  Ops[0] = Builder.CreateBitCast(Ops[0], ResultType->getPointerTo());
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vle8_v_tuma:
case RISCVVector::BI__builtin_rvv_vle16_v_tuma:
case RISCVVector::BI__builtin_rvv_vle32_v_tuma:
case RISCVVector::BI__builtin_rvv_vle64_v_tuma:
  ID = Intrinsic::riscv_vle_mask;
  DefaultPolicy = 2;
  Ops[2] = Builder.CreateBitCast(Ops[2], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vle8_v_tama:
case RISCVVector::BI__builtin_rvv_vle16_v_tama:
case RISCVVector::BI__builtin_rvv_vle32_v_tama:
case RISCVVector::BI__builtin_rvv_vle64_v_tama:
  ID = Intrinsic::riscv_vle_mask;
  DefaultPolicy = 3;
  Ops[1] = Builder.CreateBitCast(Ops[1], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vle8_v_tumu:
case RISCVVector::BI__builtin_rvv_vle16_v_tumu:
case RISCVVector::BI__builtin_rvv_vle32_v_tumu:
case RISCVVector::BI__builtin_rvv_vle64_v_tumu:
  ID = Intrinsic::riscv_vle_mask;
  Ops[2] = Builder.CreateBitCast(Ops[2], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vle8_v_tamu:
case RISCVVector::BI__builtin_rvv_vle16_v_tamu:
case RISCVVector::BI__builtin_rvv_vle32_v_tamu:
case RISCVVector::BI__builtin_rvv_vle64_v_tamu:
  ID = Intrinsic::riscv_vle_mask;
  DefaultPolicy = 1;
  Ops[2] = Builder.CreateBitCast(Ops[2], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vle16ff_v_tu:
case RISCVVector::BI__builtin_rvv_vle32ff_v_tu:
case RISCVVector::BI__builtin_rvv_vle64ff_v_tu:
case RISCVVector::BI__builtin_rvv_vle8ff_v_tu:
  ID = Intrinsic::riscv_vleff;
  DefaultPolicy = 0;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        IntrinsicTypes = {ResultType, Ops[3]->getType()};
        Ops[1] = Builder.CreateBitCast(Ops[1], ResultType->getPointerTo());
        Value *NewVL = Ops[2];
        Ops.erase(Ops.begin() + 2);
        llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
        llvm::Value *LoadValue = Builder.CreateCall(F, Ops, "");
        llvm::Value *V = Builder.CreateExtractValue(LoadValue, {0});
        // Store new_vl.
        clang::CharUnits Align =
            CGM.getNaturalPointeeTypeAlignment(E->getArg(1)->getType());
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {1});
        Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
        return V;
      }
      break;
case RISCVVector::BI__builtin_rvv_vle16ff_v_ta:
case RISCVVector::BI__builtin_rvv_vle32ff_v_ta:
case RISCVVector::BI__builtin_rvv_vle64ff_v_ta:
case RISCVVector::BI__builtin_rvv_vle8ff_v_ta:
  ID = Intrinsic::riscv_vleff;
  DefaultPolicy = 1;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        IntrinsicTypes = {ResultType, Ops[3]->getType()};
        Ops[1] = Builder.CreateBitCast(Ops[1], ResultType->getPointerTo());
        Value *NewVL = Ops[2];
        Ops.erase(Ops.begin() + 2);
        llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
        llvm::Value *LoadValue = Builder.CreateCall(F, Ops, "");
        llvm::Value *V = Builder.CreateExtractValue(LoadValue, {0});
        // Store new_vl.
        clang::CharUnits Align =
            CGM.getNaturalPointeeTypeAlignment(E->getArg(1)->getType());
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {1});
        Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
        return V;
      }
      break;
case RISCVVector::BI__builtin_rvv_vle16ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vle32ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vle64ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vle8ff_v_tuma:
  ID = Intrinsic::riscv_vleff_mask;
  DefaultPolicy = 2;

      {
        // Move mask to right before vl.
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        IntrinsicTypes = {ResultType, Ops[4]->getType()};
        Ops[1] = Builder.CreateBitCast(Ops[1], ResultType->getPointerTo());
        Value *NewVL = Ops[2];
        Ops.erase(Ops.begin() + 2);
        llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
        llvm::Value *LoadValue = Builder.CreateCall(F, Ops, "");
        llvm::Value *V = Builder.CreateExtractValue(LoadValue, {0});
        // Store new_vl.
        clang::CharUnits Align = CGM.getNaturalPointeeTypeAlignment(
            E->getArg(E->getNumArgs()-2)->getType());
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {1});
        Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
        return V;
      }
      break;
case RISCVVector::BI__builtin_rvv_vle16ff_v_tama:
case RISCVVector::BI__builtin_rvv_vle32ff_v_tama:
case RISCVVector::BI__builtin_rvv_vle64ff_v_tama:
case RISCVVector::BI__builtin_rvv_vle8ff_v_tama:
  ID = Intrinsic::riscv_vleff_mask;
  DefaultPolicy = 3;

      {
        // Move mask to right before vl.
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        IntrinsicTypes = {ResultType, Ops[4]->getType()};
        Ops[1] = Builder.CreateBitCast(Ops[1], ResultType->getPointerTo());
        Value *NewVL = Ops[2];
        Ops.erase(Ops.begin() + 2);
        llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
        llvm::Value *LoadValue = Builder.CreateCall(F, Ops, "");
        llvm::Value *V = Builder.CreateExtractValue(LoadValue, {0});
        // Store new_vl.
        clang::CharUnits Align = CGM.getNaturalPointeeTypeAlignment(
            E->getArg(E->getNumArgs()-2)->getType());
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {1});
        Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
        return V;
      }
      break;
case RISCVVector::BI__builtin_rvv_vle16ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vle32ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vle64ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vle8ff_v_tumu:
  ID = Intrinsic::riscv_vleff_mask;
  DefaultPolicy = 0;

      {
        // Move mask to right before vl.
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        IntrinsicTypes = {ResultType, Ops[4]->getType()};
        Ops[1] = Builder.CreateBitCast(Ops[1], ResultType->getPointerTo());
        Value *NewVL = Ops[2];
        Ops.erase(Ops.begin() + 2);
        llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
        llvm::Value *LoadValue = Builder.CreateCall(F, Ops, "");
        llvm::Value *V = Builder.CreateExtractValue(LoadValue, {0});
        // Store new_vl.
        clang::CharUnits Align = CGM.getNaturalPointeeTypeAlignment(
            E->getArg(E->getNumArgs()-2)->getType());
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {1});
        Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
        return V;
      }
      break;
case RISCVVector::BI__builtin_rvv_vle16ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vle32ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vle64ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vle8ff_v_tamu:
  ID = Intrinsic::riscv_vleff_mask;
  DefaultPolicy = 1;

      {
        // Move mask to right before vl.
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        IntrinsicTypes = {ResultType, Ops[4]->getType()};
        Ops[1] = Builder.CreateBitCast(Ops[1], ResultType->getPointerTo());
        Value *NewVL = Ops[2];
        Ops.erase(Ops.begin() + 2);
        llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
        llvm::Value *LoadValue = Builder.CreateCall(F, Ops, "");
        llvm::Value *V = Builder.CreateExtractValue(LoadValue, {0});
        // Store new_vl.
        clang::CharUnits Align = CGM.getNaturalPointeeTypeAlignment(
            E->getArg(E->getNumArgs()-2)->getType());
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {1});
        Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
        return V;
      }
      break;
case RISCVVector::BI__builtin_rvv_vlm_v:
  ID = Intrinsic::riscv_vlm;
  DefaultPolicy = 1;
  Ops[0] = Builder.CreateBitCast(Ops[0], ResultType->getPointerTo());
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vloxei8_v_tu:
case RISCVVector::BI__builtin_rvv_vloxei16_v_tu:
case RISCVVector::BI__builtin_rvv_vloxei32_v_tu:
case RISCVVector::BI__builtin_rvv_vloxei64_v_tu:
  ID = Intrinsic::riscv_vloxei;
  Ops[1] = Builder.CreateBitCast(Ops[1], ResultType->getPointerTo());
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vloxei8_v_ta:
case RISCVVector::BI__builtin_rvv_vloxei16_v_ta:
case RISCVVector::BI__builtin_rvv_vloxei32_v_ta:
case RISCVVector::BI__builtin_rvv_vloxei64_v_ta:
  ID = Intrinsic::riscv_vloxei;
  Ops[0] = Builder.CreateBitCast(Ops[0], ResultType->getPointerTo());
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vloxei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxei64_v_tuma:
  ID = Intrinsic::riscv_vloxei_mask;
  DefaultPolicy = 2;
  Ops[2] = Builder.CreateBitCast(Ops[2], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vloxei8_v_tama:
case RISCVVector::BI__builtin_rvv_vloxei16_v_tama:
case RISCVVector::BI__builtin_rvv_vloxei32_v_tama:
case RISCVVector::BI__builtin_rvv_vloxei64_v_tama:
  ID = Intrinsic::riscv_vloxei_mask;
  DefaultPolicy = 3;
  Ops[1] = Builder.CreateBitCast(Ops[1], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vloxei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxei64_v_tumu:
  ID = Intrinsic::riscv_vloxei_mask;
  Ops[2] = Builder.CreateBitCast(Ops[2], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vloxei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxei64_v_tamu:
  ID = Intrinsic::riscv_vloxei_mask;
  DefaultPolicy = 1;
  Ops[2] = Builder.CreateBitCast(Ops[2], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vloxseg2ei16_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg2ei32_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg2ei64_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg2ei8_v_tu:
  ID = Intrinsic::riscv_vloxseg2;
  NF = 2;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg2ei16_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg2ei32_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg2ei64_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg2ei8_v_ta:
  ID = Intrinsic::riscv_vloxseg2;
  NF = 2;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg2ei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg2ei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg2ei64_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg2ei8_v_tuma:
  ID = Intrinsic::riscv_vloxseg2_mask;
  NF = 2;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg2ei16_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg2ei32_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg2ei64_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg2ei8_v_tama:
  ID = Intrinsic::riscv_vloxseg2_mask;
  NF = 2;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg2ei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg2ei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg2ei64_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg2ei8_v_tumu:
  ID = Intrinsic::riscv_vloxseg2_mask;
  NF = 2;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg2ei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg2ei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg2ei64_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg2ei8_v_tamu:
  ID = Intrinsic::riscv_vloxseg2_mask;
  NF = 2;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg3ei8_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg3ei16_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg3ei32_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg3ei64_v_tu:
  ID = Intrinsic::riscv_vloxseg3;
  NF = 3;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg3ei8_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg3ei16_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg3ei32_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg3ei64_v_ta:
  ID = Intrinsic::riscv_vloxseg3;
  NF = 3;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg3ei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg3ei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg3ei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg3ei64_v_tuma:
  ID = Intrinsic::riscv_vloxseg3_mask;
  NF = 3;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg3ei8_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg3ei16_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg3ei32_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg3ei64_v_tama:
  ID = Intrinsic::riscv_vloxseg3_mask;
  NF = 3;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg3ei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg3ei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg3ei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg3ei64_v_tumu:
  ID = Intrinsic::riscv_vloxseg3_mask;
  NF = 3;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg3ei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg3ei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg3ei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg3ei64_v_tamu:
  ID = Intrinsic::riscv_vloxseg3_mask;
  NF = 3;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg4ei8_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg4ei16_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg4ei32_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg4ei64_v_tu:
  ID = Intrinsic::riscv_vloxseg4;
  NF = 4;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg4ei8_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg4ei16_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg4ei32_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg4ei64_v_ta:
  ID = Intrinsic::riscv_vloxseg4;
  NF = 4;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg4ei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg4ei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg4ei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg4ei64_v_tuma:
  ID = Intrinsic::riscv_vloxseg4_mask;
  NF = 4;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg4ei8_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg4ei16_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg4ei32_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg4ei64_v_tama:
  ID = Intrinsic::riscv_vloxseg4_mask;
  NF = 4;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg4ei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg4ei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg4ei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg4ei64_v_tumu:
  ID = Intrinsic::riscv_vloxseg4_mask;
  NF = 4;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg4ei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg4ei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg4ei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg4ei64_v_tamu:
  ID = Intrinsic::riscv_vloxseg4_mask;
  NF = 4;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg5ei8_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg5ei16_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg5ei32_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg5ei64_v_tu:
  ID = Intrinsic::riscv_vloxseg5;
  NF = 5;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg5ei8_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg5ei16_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg5ei32_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg5ei64_v_ta:
  ID = Intrinsic::riscv_vloxseg5;
  NF = 5;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg5ei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg5ei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg5ei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg5ei64_v_tuma:
  ID = Intrinsic::riscv_vloxseg5_mask;
  NF = 5;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg5ei8_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg5ei16_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg5ei32_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg5ei64_v_tama:
  ID = Intrinsic::riscv_vloxseg5_mask;
  NF = 5;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg5ei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg5ei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg5ei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg5ei64_v_tumu:
  ID = Intrinsic::riscv_vloxseg5_mask;
  NF = 5;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg5ei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg5ei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg5ei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg5ei64_v_tamu:
  ID = Intrinsic::riscv_vloxseg5_mask;
  NF = 5;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg6ei8_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg6ei16_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg6ei32_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg6ei64_v_tu:
  ID = Intrinsic::riscv_vloxseg6;
  NF = 6;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg6ei8_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg6ei16_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg6ei32_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg6ei64_v_ta:
  ID = Intrinsic::riscv_vloxseg6;
  NF = 6;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg6ei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg6ei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg6ei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg6ei64_v_tuma:
  ID = Intrinsic::riscv_vloxseg6_mask;
  NF = 6;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg6ei8_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg6ei16_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg6ei32_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg6ei64_v_tama:
  ID = Intrinsic::riscv_vloxseg6_mask;
  NF = 6;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg6ei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg6ei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg6ei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg6ei64_v_tumu:
  ID = Intrinsic::riscv_vloxseg6_mask;
  NF = 6;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg6ei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg6ei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg6ei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg6ei64_v_tamu:
  ID = Intrinsic::riscv_vloxseg6_mask;
  NF = 6;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg7ei8_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg7ei16_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg7ei32_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg7ei64_v_tu:
  ID = Intrinsic::riscv_vloxseg7;
  NF = 7;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg7ei8_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg7ei16_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg7ei32_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg7ei64_v_ta:
  ID = Intrinsic::riscv_vloxseg7;
  NF = 7;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg7ei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg7ei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg7ei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg7ei64_v_tuma:
  ID = Intrinsic::riscv_vloxseg7_mask;
  NF = 7;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg7ei8_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg7ei16_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg7ei32_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg7ei64_v_tama:
  ID = Intrinsic::riscv_vloxseg7_mask;
  NF = 7;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg7ei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg7ei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg7ei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg7ei64_v_tumu:
  ID = Intrinsic::riscv_vloxseg7_mask;
  NF = 7;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg7ei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg7ei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg7ei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg7ei64_v_tamu:
  ID = Intrinsic::riscv_vloxseg7_mask;
  NF = 7;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg8ei8_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg8ei16_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg8ei32_v_tu:
case RISCVVector::BI__builtin_rvv_vloxseg8ei64_v_tu:
  ID = Intrinsic::riscv_vloxseg8;
  NF = 8;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg8ei8_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg8ei16_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg8ei32_v_ta:
case RISCVVector::BI__builtin_rvv_vloxseg8ei64_v_ta:
  ID = Intrinsic::riscv_vloxseg8;
  NF = 8;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg8ei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg8ei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg8ei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vloxseg8ei64_v_tuma:
  ID = Intrinsic::riscv_vloxseg8_mask;
  NF = 8;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg8ei8_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg8ei16_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg8ei32_v_tama:
case RISCVVector::BI__builtin_rvv_vloxseg8ei64_v_tama:
  ID = Intrinsic::riscv_vloxseg8_mask;
  NF = 8;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg8ei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg8ei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg8ei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vloxseg8ei64_v_tumu:
  ID = Intrinsic::riscv_vloxseg8_mask;
  NF = 8;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vloxseg8ei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg8ei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg8ei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vloxseg8ei64_v_tamu:
  ID = Intrinsic::riscv_vloxseg8_mask;
  NF = 8;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlse32_v_tu:
case RISCVVector::BI__builtin_rvv_vlse64_v_tu:
case RISCVVector::BI__builtin_rvv_vlse8_v_tu:
case RISCVVector::BI__builtin_rvv_vlse16_v_tu:
  ID = Intrinsic::riscv_vlse;
  Ops[1] = Builder.CreateBitCast(Ops[1], ResultType->getPointerTo());
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vlse32_v_ta:
case RISCVVector::BI__builtin_rvv_vlse64_v_ta:
case RISCVVector::BI__builtin_rvv_vlse8_v_ta:
case RISCVVector::BI__builtin_rvv_vlse16_v_ta:
  ID = Intrinsic::riscv_vlse;
  Ops[0] = Builder.CreateBitCast(Ops[0], ResultType->getPointerTo());
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vlse32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlse64_v_tuma:
case RISCVVector::BI__builtin_rvv_vlse8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlse16_v_tuma:
  ID = Intrinsic::riscv_vlse_mask;
  DefaultPolicy = 2;
  Ops[2] = Builder.CreateBitCast(Ops[2], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vlse32_v_tama:
case RISCVVector::BI__builtin_rvv_vlse64_v_tama:
case RISCVVector::BI__builtin_rvv_vlse8_v_tama:
case RISCVVector::BI__builtin_rvv_vlse16_v_tama:
  ID = Intrinsic::riscv_vlse_mask;
  DefaultPolicy = 3;
  Ops[1] = Builder.CreateBitCast(Ops[1], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vlse32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlse64_v_tumu:
case RISCVVector::BI__builtin_rvv_vlse8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlse16_v_tumu:
  ID = Intrinsic::riscv_vlse_mask;
  Ops[2] = Builder.CreateBitCast(Ops[2], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vlse32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlse64_v_tamu:
case RISCVVector::BI__builtin_rvv_vlse8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlse16_v_tamu:
  ID = Intrinsic::riscv_vlse_mask;
  DefaultPolicy = 1;
  Ops[2] = Builder.CreateBitCast(Ops[2], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vlseg2e8_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg2e16_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg2e32_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg2e64_v_tu:
  ID = Intrinsic::riscv_vlseg2;
  NF = 2;
  DefaultPolicy = 0;

    {
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // TA builtin: (val0 address, val1 address, ..., ptr, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, vl)
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 10> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg2e8_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg2e16_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg2e32_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg2e64_v_ta:
  ID = Intrinsic::riscv_vlseg2;
  NF = 2;
  DefaultPolicy = 1;

    {
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // TA builtin: (val0 address, val1 address, ..., ptr, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, vl)
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 10> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg2e8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg2e16_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg2e32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg2e64_v_tuma:
  ID = Intrinsic::riscv_vlseg2_mask;
  NF = 2;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg2e8_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg2e16_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg2e32_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg2e64_v_tama:
  ID = Intrinsic::riscv_vlseg2_mask;
  NF = 2;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg2e8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg2e16_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg2e32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg2e64_v_tumu:
  ID = Intrinsic::riscv_vlseg2_mask;
  NF = 2;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg2e8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg2e16_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg2e32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg2e64_v_tamu:
  ID = Intrinsic::riscv_vlseg2_mask;
  NF = 2;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg2e16ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg2e32ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg2e64ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg2e8ff_v_tu:
  ID = Intrinsic::riscv_vlseg2ff;
  NF = 2;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, new_vl, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, new_vl, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
        NewVL = Ops[NF + 1];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 2]);
        NewVL = Ops[2 * NF + 1];
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg2e16ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg2e32ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg2e64ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg2e8ff_v_ta:
  ID = Intrinsic::riscv_vlseg2ff;
  NF = 2;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, new_vl, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, new_vl, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
        NewVL = Ops[NF + 1];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 2]);
        NewVL = Ops[2 * NF + 1];
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg2e16ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg2e32ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg2e64ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg2e8ff_v_tuma:
  ID = Intrinsic::riscv_vlseg2ff_mask;
  NF = 2;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg2e16ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg2e32ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg2e64ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg2e8ff_v_tama:
  ID = Intrinsic::riscv_vlseg2ff_mask;
  NF = 2;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg2e16ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg2e32ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg2e64ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg2e8ff_v_tumu:
  ID = Intrinsic::riscv_vlseg2ff_mask;
  NF = 2;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg2e16ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg2e32ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg2e64ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg2e8ff_v_tamu:
  ID = Intrinsic::riscv_vlseg2ff_mask;
  NF = 2;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg3e8_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg3e16_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg3e32_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg3e64_v_tu:
  ID = Intrinsic::riscv_vlseg3;
  NF = 3;
  DefaultPolicy = 0;

    {
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // TA builtin: (val0 address, val1 address, ..., ptr, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, vl)
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 10> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg3e8_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg3e16_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg3e32_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg3e64_v_ta:
  ID = Intrinsic::riscv_vlseg3;
  NF = 3;
  DefaultPolicy = 1;

    {
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // TA builtin: (val0 address, val1 address, ..., ptr, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, vl)
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 10> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg3e8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg3e16_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg3e32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg3e64_v_tuma:
  ID = Intrinsic::riscv_vlseg3_mask;
  NF = 3;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg3e8_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg3e16_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg3e32_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg3e64_v_tama:
  ID = Intrinsic::riscv_vlseg3_mask;
  NF = 3;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg3e8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg3e16_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg3e32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg3e64_v_tumu:
  ID = Intrinsic::riscv_vlseg3_mask;
  NF = 3;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg3e8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg3e16_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg3e32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg3e64_v_tamu:
  ID = Intrinsic::riscv_vlseg3_mask;
  NF = 3;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg3e8ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg3e16ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg3e32ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg3e64ff_v_tu:
  ID = Intrinsic::riscv_vlseg3ff;
  NF = 3;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, new_vl, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, new_vl, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
        NewVL = Ops[NF + 1];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 2]);
        NewVL = Ops[2 * NF + 1];
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg3e8ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg3e16ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg3e32ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg3e64ff_v_ta:
  ID = Intrinsic::riscv_vlseg3ff;
  NF = 3;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, new_vl, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, new_vl, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
        NewVL = Ops[NF + 1];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 2]);
        NewVL = Ops[2 * NF + 1];
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg3e8ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg3e16ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg3e32ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg3e64ff_v_tuma:
  ID = Intrinsic::riscv_vlseg3ff_mask;
  NF = 3;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg3e8ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg3e16ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg3e32ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg3e64ff_v_tama:
  ID = Intrinsic::riscv_vlseg3ff_mask;
  NF = 3;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg3e8ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg3e16ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg3e32ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg3e64ff_v_tumu:
  ID = Intrinsic::riscv_vlseg3ff_mask;
  NF = 3;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg3e8ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg3e16ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg3e32ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg3e64ff_v_tamu:
  ID = Intrinsic::riscv_vlseg3ff_mask;
  NF = 3;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg4e8_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg4e16_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg4e32_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg4e64_v_tu:
  ID = Intrinsic::riscv_vlseg4;
  NF = 4;
  DefaultPolicy = 0;

    {
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // TA builtin: (val0 address, val1 address, ..., ptr, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, vl)
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 10> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg4e8_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg4e16_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg4e32_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg4e64_v_ta:
  ID = Intrinsic::riscv_vlseg4;
  NF = 4;
  DefaultPolicy = 1;

    {
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // TA builtin: (val0 address, val1 address, ..., ptr, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, vl)
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 10> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg4e8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg4e16_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg4e32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg4e64_v_tuma:
  ID = Intrinsic::riscv_vlseg4_mask;
  NF = 4;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg4e8_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg4e16_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg4e32_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg4e64_v_tama:
  ID = Intrinsic::riscv_vlseg4_mask;
  NF = 4;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg4e8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg4e16_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg4e32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg4e64_v_tumu:
  ID = Intrinsic::riscv_vlseg4_mask;
  NF = 4;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg4e8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg4e16_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg4e32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg4e64_v_tamu:
  ID = Intrinsic::riscv_vlseg4_mask;
  NF = 4;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg4e8ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg4e16ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg4e32ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg4e64ff_v_tu:
  ID = Intrinsic::riscv_vlseg4ff;
  NF = 4;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, new_vl, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, new_vl, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
        NewVL = Ops[NF + 1];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 2]);
        NewVL = Ops[2 * NF + 1];
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg4e8ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg4e16ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg4e32ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg4e64ff_v_ta:
  ID = Intrinsic::riscv_vlseg4ff;
  NF = 4;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, new_vl, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, new_vl, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
        NewVL = Ops[NF + 1];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 2]);
        NewVL = Ops[2 * NF + 1];
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg4e8ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg4e16ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg4e32ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg4e64ff_v_tuma:
  ID = Intrinsic::riscv_vlseg4ff_mask;
  NF = 4;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg4e8ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg4e16ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg4e32ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg4e64ff_v_tama:
  ID = Intrinsic::riscv_vlseg4ff_mask;
  NF = 4;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg4e8ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg4e16ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg4e32ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg4e64ff_v_tumu:
  ID = Intrinsic::riscv_vlseg4ff_mask;
  NF = 4;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg4e8ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg4e16ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg4e32ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg4e64ff_v_tamu:
  ID = Intrinsic::riscv_vlseg4ff_mask;
  NF = 4;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg5e8_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg5e16_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg5e32_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg5e64_v_tu:
  ID = Intrinsic::riscv_vlseg5;
  NF = 5;
  DefaultPolicy = 0;

    {
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // TA builtin: (val0 address, val1 address, ..., ptr, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, vl)
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 10> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg5e8_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg5e16_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg5e32_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg5e64_v_ta:
  ID = Intrinsic::riscv_vlseg5;
  NF = 5;
  DefaultPolicy = 1;

    {
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // TA builtin: (val0 address, val1 address, ..., ptr, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, vl)
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 10> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg5e8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg5e16_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg5e32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg5e64_v_tuma:
  ID = Intrinsic::riscv_vlseg5_mask;
  NF = 5;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg5e8_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg5e16_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg5e32_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg5e64_v_tama:
  ID = Intrinsic::riscv_vlseg5_mask;
  NF = 5;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg5e8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg5e16_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg5e32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg5e64_v_tumu:
  ID = Intrinsic::riscv_vlseg5_mask;
  NF = 5;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg5e8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg5e16_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg5e32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg5e64_v_tamu:
  ID = Intrinsic::riscv_vlseg5_mask;
  NF = 5;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg5e8ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg5e16ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg5e32ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg5e64ff_v_tu:
  ID = Intrinsic::riscv_vlseg5ff;
  NF = 5;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, new_vl, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, new_vl, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
        NewVL = Ops[NF + 1];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 2]);
        NewVL = Ops[2 * NF + 1];
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg5e8ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg5e16ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg5e32ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg5e64ff_v_ta:
  ID = Intrinsic::riscv_vlseg5ff;
  NF = 5;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, new_vl, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, new_vl, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
        NewVL = Ops[NF + 1];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 2]);
        NewVL = Ops[2 * NF + 1];
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg5e8ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg5e16ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg5e32ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg5e64ff_v_tuma:
  ID = Intrinsic::riscv_vlseg5ff_mask;
  NF = 5;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg5e8ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg5e16ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg5e32ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg5e64ff_v_tama:
  ID = Intrinsic::riscv_vlseg5ff_mask;
  NF = 5;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg5e8ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg5e16ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg5e32ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg5e64ff_v_tumu:
  ID = Intrinsic::riscv_vlseg5ff_mask;
  NF = 5;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg5e8ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg5e16ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg5e32ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg5e64ff_v_tamu:
  ID = Intrinsic::riscv_vlseg5ff_mask;
  NF = 5;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg6e8_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg6e16_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg6e32_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg6e64_v_tu:
  ID = Intrinsic::riscv_vlseg6;
  NF = 6;
  DefaultPolicy = 0;

    {
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // TA builtin: (val0 address, val1 address, ..., ptr, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, vl)
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 10> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg6e8_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg6e16_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg6e32_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg6e64_v_ta:
  ID = Intrinsic::riscv_vlseg6;
  NF = 6;
  DefaultPolicy = 1;

    {
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // TA builtin: (val0 address, val1 address, ..., ptr, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, vl)
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 10> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg6e8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg6e16_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg6e32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg6e64_v_tuma:
  ID = Intrinsic::riscv_vlseg6_mask;
  NF = 6;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg6e8_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg6e16_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg6e32_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg6e64_v_tama:
  ID = Intrinsic::riscv_vlseg6_mask;
  NF = 6;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg6e8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg6e16_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg6e32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg6e64_v_tumu:
  ID = Intrinsic::riscv_vlseg6_mask;
  NF = 6;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg6e8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg6e16_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg6e32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg6e64_v_tamu:
  ID = Intrinsic::riscv_vlseg6_mask;
  NF = 6;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg6e8ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg6e16ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg6e32ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg6e64ff_v_tu:
  ID = Intrinsic::riscv_vlseg6ff;
  NF = 6;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, new_vl, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, new_vl, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
        NewVL = Ops[NF + 1];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 2]);
        NewVL = Ops[2 * NF + 1];
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg6e8ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg6e16ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg6e32ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg6e64ff_v_ta:
  ID = Intrinsic::riscv_vlseg6ff;
  NF = 6;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, new_vl, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, new_vl, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
        NewVL = Ops[NF + 1];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 2]);
        NewVL = Ops[2 * NF + 1];
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg6e8ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg6e16ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg6e32ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg6e64ff_v_tuma:
  ID = Intrinsic::riscv_vlseg6ff_mask;
  NF = 6;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg6e8ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg6e16ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg6e32ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg6e64ff_v_tama:
  ID = Intrinsic::riscv_vlseg6ff_mask;
  NF = 6;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg6e8ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg6e16ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg6e32ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg6e64ff_v_tumu:
  ID = Intrinsic::riscv_vlseg6ff_mask;
  NF = 6;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg6e8ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg6e16ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg6e32ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg6e64ff_v_tamu:
  ID = Intrinsic::riscv_vlseg6ff_mask;
  NF = 6;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg7e8_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg7e16_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg7e32_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg7e64_v_tu:
  ID = Intrinsic::riscv_vlseg7;
  NF = 7;
  DefaultPolicy = 0;

    {
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // TA builtin: (val0 address, val1 address, ..., ptr, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, vl)
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 10> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg7e8_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg7e16_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg7e32_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg7e64_v_ta:
  ID = Intrinsic::riscv_vlseg7;
  NF = 7;
  DefaultPolicy = 1;

    {
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // TA builtin: (val0 address, val1 address, ..., ptr, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, vl)
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 10> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg7e8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg7e16_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg7e32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg7e64_v_tuma:
  ID = Intrinsic::riscv_vlseg7_mask;
  NF = 7;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg7e8_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg7e16_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg7e32_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg7e64_v_tama:
  ID = Intrinsic::riscv_vlseg7_mask;
  NF = 7;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg7e8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg7e16_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg7e32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg7e64_v_tumu:
  ID = Intrinsic::riscv_vlseg7_mask;
  NF = 7;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg7e8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg7e16_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg7e32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg7e64_v_tamu:
  ID = Intrinsic::riscv_vlseg7_mask;
  NF = 7;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg7e8ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg7e16ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg7e32ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg7e64ff_v_tu:
  ID = Intrinsic::riscv_vlseg7ff;
  NF = 7;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, new_vl, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, new_vl, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
        NewVL = Ops[NF + 1];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 2]);
        NewVL = Ops[2 * NF + 1];
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg7e8ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg7e16ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg7e32ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg7e64ff_v_ta:
  ID = Intrinsic::riscv_vlseg7ff;
  NF = 7;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, new_vl, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, new_vl, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
        NewVL = Ops[NF + 1];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 2]);
        NewVL = Ops[2 * NF + 1];
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg7e8ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg7e16ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg7e32ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg7e64ff_v_tuma:
  ID = Intrinsic::riscv_vlseg7ff_mask;
  NF = 7;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg7e8ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg7e16ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg7e32ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg7e64ff_v_tama:
  ID = Intrinsic::riscv_vlseg7ff_mask;
  NF = 7;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg7e8ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg7e16ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg7e32ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg7e64ff_v_tumu:
  ID = Intrinsic::riscv_vlseg7ff_mask;
  NF = 7;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg7e8ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg7e16ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg7e32ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg7e64ff_v_tamu:
  ID = Intrinsic::riscv_vlseg7ff_mask;
  NF = 7;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg8e8_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg8e16_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg8e32_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg8e64_v_tu:
  ID = Intrinsic::riscv_vlseg8;
  NF = 8;
  DefaultPolicy = 0;

    {
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // TA builtin: (val0 address, val1 address, ..., ptr, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, vl)
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 10> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg8e8_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg8e16_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg8e32_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg8e64_v_ta:
  ID = Intrinsic::riscv_vlseg8;
  NF = 8;
  DefaultPolicy = 1;

    {
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // TA builtin: (val0 address, val1 address, ..., ptr, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, vl)
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 10> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg8e8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg8e16_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg8e32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg8e64_v_tuma:
  ID = Intrinsic::riscv_vlseg8_mask;
  NF = 8;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg8e8_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg8e16_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg8e32_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg8e64_v_tama:
  ID = Intrinsic::riscv_vlseg8_mask;
  NF = 8;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg8e8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg8e16_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg8e32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg8e64_v_tumu:
  ID = Intrinsic::riscv_vlseg8_mask;
  NF = 8;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg8e8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg8e16_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg8e32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg8e64_v_tamu:
  ID = Intrinsic::riscv_vlseg8_mask;
  NF = 8;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg8e8ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg8e16ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg8e32ff_v_tu:
case RISCVVector::BI__builtin_rvv_vlseg8e64ff_v_tu:
  ID = Intrinsic::riscv_vlseg8ff;
  NF = 8;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, new_vl, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, new_vl, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
        NewVL = Ops[NF + 1];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 2]);
        NewVL = Ops[2 * NF + 1];
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg8e8ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg8e16ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg8e32ff_v_ta:
case RISCVVector::BI__builtin_rvv_vlseg8e64ff_v_ta:
  ID = Intrinsic::riscv_vlseg8ff;
  NF = 8;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, new_vl, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, new_vl, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, vl)
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 2]);
        NewVL = Ops[NF + 1];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 2]);
        NewVL = Ops[2 * NF + 1];
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg8e8ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg8e16ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg8e32ff_v_tuma:
case RISCVVector::BI__builtin_rvv_vlseg8e64ff_v_tuma:
  ID = Intrinsic::riscv_vlseg8ff_mask;
  NF = 8;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg8e8ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg8e16ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg8e32ff_v_tama:
case RISCVVector::BI__builtin_rvv_vlseg8e64ff_v_tama:
  ID = Intrinsic::riscv_vlseg8ff_mask;
  NF = 8;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg8e8ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg8e16ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg8e32ff_v_tumu:
case RISCVVector::BI__builtin_rvv_vlseg8e64ff_v_tumu:
  ID = Intrinsic::riscv_vlseg8ff_mask;
  NF = 8;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlseg8e8ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg8e16ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg8e32ff_v_tamu:
case RISCVVector::BI__builtin_rvv_vlseg8e64ff_v_tamu:
  ID = Intrinsic::riscv_vlseg8ff_mask;
  NF = 8;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, new_vl, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, new_vl, vl)
      // intrinsic: (maskedoff0, ..., ptr, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      Value *NewVL;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        NewVL = Ops[NF + 2];
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        NewVL = Ops[2 * NF + 2];
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 4);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      // Store new_vl.
      llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {NF});
      return Builder.CreateStore(Val, Address(NewVL, Val->getType(), Align));
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg2e16_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg2e8_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg2e32_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg2e64_v_tu:
  ID = Intrinsic::riscv_vlsseg2;
  NF = 2;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, stride, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, stride, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, stride, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg2e16_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg2e8_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg2e32_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg2e64_v_ta:
  ID = Intrinsic::riscv_vlsseg2;
  NF = 2;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, stride, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, stride, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, stride, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg2e16_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg2e8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg2e32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg2e64_v_tuma:
  ID = Intrinsic::riscv_vlsseg2_mask;
  NF = 2;
  DefaultPolicy = 2;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg2e16_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg2e8_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg2e32_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg2e64_v_tama:
  ID = Intrinsic::riscv_vlsseg2_mask;
  NF = 2;
  DefaultPolicy = 3;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg2e16_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg2e8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg2e32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg2e64_v_tumu:
  ID = Intrinsic::riscv_vlsseg2_mask;
  NF = 2;
  DefaultPolicy = 0;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg2e16_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg2e8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg2e32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg2e64_v_tamu:
  ID = Intrinsic::riscv_vlsseg2_mask;
  NF = 2;
  DefaultPolicy = 1;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg3e8_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg3e16_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg3e32_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg3e64_v_tu:
  ID = Intrinsic::riscv_vlsseg3;
  NF = 3;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, stride, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, stride, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, stride, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg3e8_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg3e16_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg3e32_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg3e64_v_ta:
  ID = Intrinsic::riscv_vlsseg3;
  NF = 3;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, stride, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, stride, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, stride, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg3e8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg3e16_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg3e32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg3e64_v_tuma:
  ID = Intrinsic::riscv_vlsseg3_mask;
  NF = 3;
  DefaultPolicy = 2;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg3e8_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg3e16_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg3e32_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg3e64_v_tama:
  ID = Intrinsic::riscv_vlsseg3_mask;
  NF = 3;
  DefaultPolicy = 3;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg3e8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg3e16_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg3e32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg3e64_v_tumu:
  ID = Intrinsic::riscv_vlsseg3_mask;
  NF = 3;
  DefaultPolicy = 0;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg3e8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg3e16_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg3e32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg3e64_v_tamu:
  ID = Intrinsic::riscv_vlsseg3_mask;
  NF = 3;
  DefaultPolicy = 1;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg4e8_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg4e16_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg4e32_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg4e64_v_tu:
  ID = Intrinsic::riscv_vlsseg4;
  NF = 4;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, stride, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, stride, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, stride, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg4e8_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg4e16_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg4e32_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg4e64_v_ta:
  ID = Intrinsic::riscv_vlsseg4;
  NF = 4;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, stride, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, stride, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, stride, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg4e8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg4e16_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg4e32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg4e64_v_tuma:
  ID = Intrinsic::riscv_vlsseg4_mask;
  NF = 4;
  DefaultPolicy = 2;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg4e8_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg4e16_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg4e32_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg4e64_v_tama:
  ID = Intrinsic::riscv_vlsseg4_mask;
  NF = 4;
  DefaultPolicy = 3;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg4e8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg4e16_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg4e32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg4e64_v_tumu:
  ID = Intrinsic::riscv_vlsseg4_mask;
  NF = 4;
  DefaultPolicy = 0;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg4e8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg4e16_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg4e32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg4e64_v_tamu:
  ID = Intrinsic::riscv_vlsseg4_mask;
  NF = 4;
  DefaultPolicy = 1;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg5e8_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg5e16_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg5e32_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg5e64_v_tu:
  ID = Intrinsic::riscv_vlsseg5;
  NF = 5;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, stride, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, stride, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, stride, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg5e8_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg5e16_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg5e32_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg5e64_v_ta:
  ID = Intrinsic::riscv_vlsseg5;
  NF = 5;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, stride, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, stride, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, stride, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg5e8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg5e16_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg5e32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg5e64_v_tuma:
  ID = Intrinsic::riscv_vlsseg5_mask;
  NF = 5;
  DefaultPolicy = 2;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg5e8_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg5e16_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg5e32_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg5e64_v_tama:
  ID = Intrinsic::riscv_vlsseg5_mask;
  NF = 5;
  DefaultPolicy = 3;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg5e8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg5e16_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg5e32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg5e64_v_tumu:
  ID = Intrinsic::riscv_vlsseg5_mask;
  NF = 5;
  DefaultPolicy = 0;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg5e8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg5e16_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg5e32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg5e64_v_tamu:
  ID = Intrinsic::riscv_vlsseg5_mask;
  NF = 5;
  DefaultPolicy = 1;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg6e8_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg6e16_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg6e32_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg6e64_v_tu:
  ID = Intrinsic::riscv_vlsseg6;
  NF = 6;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, stride, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, stride, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, stride, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg6e8_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg6e16_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg6e32_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg6e64_v_ta:
  ID = Intrinsic::riscv_vlsseg6;
  NF = 6;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, stride, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, stride, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, stride, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg6e8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg6e16_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg6e32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg6e64_v_tuma:
  ID = Intrinsic::riscv_vlsseg6_mask;
  NF = 6;
  DefaultPolicy = 2;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg6e8_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg6e16_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg6e32_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg6e64_v_tama:
  ID = Intrinsic::riscv_vlsseg6_mask;
  NF = 6;
  DefaultPolicy = 3;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg6e8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg6e16_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg6e32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg6e64_v_tumu:
  ID = Intrinsic::riscv_vlsseg6_mask;
  NF = 6;
  DefaultPolicy = 0;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg6e8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg6e16_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg6e32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg6e64_v_tamu:
  ID = Intrinsic::riscv_vlsseg6_mask;
  NF = 6;
  DefaultPolicy = 1;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg7e8_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg7e16_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg7e32_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg7e64_v_tu:
  ID = Intrinsic::riscv_vlsseg7;
  NF = 7;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, stride, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, stride, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, stride, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg7e8_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg7e16_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg7e32_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg7e64_v_ta:
  ID = Intrinsic::riscv_vlsseg7;
  NF = 7;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, stride, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, stride, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, stride, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg7e8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg7e16_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg7e32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg7e64_v_tuma:
  ID = Intrinsic::riscv_vlsseg7_mask;
  NF = 7;
  DefaultPolicy = 2;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg7e8_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg7e16_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg7e32_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg7e64_v_tama:
  ID = Intrinsic::riscv_vlsseg7_mask;
  NF = 7;
  DefaultPolicy = 3;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg7e8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg7e16_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg7e32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg7e64_v_tumu:
  ID = Intrinsic::riscv_vlsseg7_mask;
  NF = 7;
  DefaultPolicy = 0;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg7e8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg7e16_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg7e32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg7e64_v_tamu:
  ID = Intrinsic::riscv_vlsseg7_mask;
  NF = 7;
  DefaultPolicy = 1;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg8e8_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg8e16_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg8e32_v_tu:
case RISCVVector::BI__builtin_rvv_vlsseg8e64_v_tu:
  ID = Intrinsic::riscv_vlsseg8;
  NF = 8;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, stride, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, stride, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, stride, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg8e8_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg8e16_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg8e32_v_ta:
case RISCVVector::BI__builtin_rvv_vlsseg8e64_v_ta:
  ID = Intrinsic::riscv_vlsseg8;
  NF = 8;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, stride, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, stride, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      // intrinsic: (passthru0, passthru1, ..., ptr, stride, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg8e8_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg8e16_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg8e32_v_tuma:
case RISCVVector::BI__builtin_rvv_vlsseg8e64_v_tuma:
  ID = Intrinsic::riscv_vlsseg8_mask;
  NF = 8;
  DefaultPolicy = 2;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg8e8_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg8e16_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg8e32_v_tama:
case RISCVVector::BI__builtin_rvv_vlsseg8e64_v_tama:
  ID = Intrinsic::riscv_vlsseg8_mask;
  NF = 8;
  DefaultPolicy = 3;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg8e8_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg8e16_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg8e32_v_tumu:
case RISCVVector::BI__builtin_rvv_vlsseg8e64_v_tumu:
  ID = Intrinsic::riscv_vlsseg8_mask;
  NF = 8;
  DefaultPolicy = 0;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vlsseg8e8_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg8e16_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg8e32_v_tamu:
case RISCVVector::BI__builtin_rvv_vlsseg8e64_v_tamu:
  ID = Intrinsic::riscv_vlsseg8_mask;
  NF = 8;
  DefaultPolicy = 1;

    {
      //TAMA builtin: (val0 address, ..., mask, ptr, stride, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, stride, vl)
      // intrinsic: (maskedoff0, ..., ptr, stride, mask, vl)
      ResultType =  ConvertType(E->getArg(0)->getType()->getPointeeType());
      IntrinsicTypes = {ResultType, Ops.back()->getType()};
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxei8_v_tu:
case RISCVVector::BI__builtin_rvv_vluxei64_v_tu:
case RISCVVector::BI__builtin_rvv_vluxei16_v_tu:
case RISCVVector::BI__builtin_rvv_vluxei32_v_tu:
  ID = Intrinsic::riscv_vluxei;
  Ops[1] = Builder.CreateBitCast(Ops[1], ResultType->getPointerTo());
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vluxei8_v_ta:
case RISCVVector::BI__builtin_rvv_vluxei64_v_ta:
case RISCVVector::BI__builtin_rvv_vluxei16_v_ta:
case RISCVVector::BI__builtin_rvv_vluxei32_v_ta:
  ID = Intrinsic::riscv_vluxei;
  Ops[0] = Builder.CreateBitCast(Ops[0], ResultType->getPointerTo());
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vluxei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxei64_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxei32_v_tuma:
  ID = Intrinsic::riscv_vluxei_mask;
  DefaultPolicy = 2;
  Ops[2] = Builder.CreateBitCast(Ops[2], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vluxei8_v_tama:
case RISCVVector::BI__builtin_rvv_vluxei64_v_tama:
case RISCVVector::BI__builtin_rvv_vluxei16_v_tama:
case RISCVVector::BI__builtin_rvv_vluxei32_v_tama:
  ID = Intrinsic::riscv_vluxei_mask;
  DefaultPolicy = 3;
  Ops[1] = Builder.CreateBitCast(Ops[1], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vluxei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxei64_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxei32_v_tumu:
  ID = Intrinsic::riscv_vluxei_mask;
  Ops[2] = Builder.CreateBitCast(Ops[2], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vluxei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxei64_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxei32_v_tamu:
  ID = Intrinsic::riscv_vluxei_mask;
  DefaultPolicy = 1;
  Ops[2] = Builder.CreateBitCast(Ops[2], ResultType->getPointerTo());
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vluxseg2ei8_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg2ei16_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg2ei32_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg2ei64_v_tu:
  ID = Intrinsic::riscv_vluxseg2;
  NF = 2;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg2ei8_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg2ei16_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg2ei32_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg2ei64_v_ta:
  ID = Intrinsic::riscv_vluxseg2;
  NF = 2;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg2ei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg2ei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg2ei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg2ei64_v_tuma:
  ID = Intrinsic::riscv_vluxseg2_mask;
  NF = 2;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg2ei8_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg2ei16_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg2ei32_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg2ei64_v_tama:
  ID = Intrinsic::riscv_vluxseg2_mask;
  NF = 2;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg2ei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg2ei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg2ei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg2ei64_v_tumu:
  ID = Intrinsic::riscv_vluxseg2_mask;
  NF = 2;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg2ei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg2ei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg2ei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg2ei64_v_tamu:
  ID = Intrinsic::riscv_vluxseg2_mask;
  NF = 2;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg3ei8_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg3ei16_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg3ei32_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg3ei64_v_tu:
  ID = Intrinsic::riscv_vluxseg3;
  NF = 3;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg3ei8_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg3ei16_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg3ei32_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg3ei64_v_ta:
  ID = Intrinsic::riscv_vluxseg3;
  NF = 3;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg3ei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg3ei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg3ei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg3ei64_v_tuma:
  ID = Intrinsic::riscv_vluxseg3_mask;
  NF = 3;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg3ei8_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg3ei16_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg3ei32_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg3ei64_v_tama:
  ID = Intrinsic::riscv_vluxseg3_mask;
  NF = 3;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg3ei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg3ei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg3ei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg3ei64_v_tumu:
  ID = Intrinsic::riscv_vluxseg3_mask;
  NF = 3;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg3ei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg3ei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg3ei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg3ei64_v_tamu:
  ID = Intrinsic::riscv_vluxseg3_mask;
  NF = 3;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg4ei8_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg4ei16_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg4ei32_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg4ei64_v_tu:
  ID = Intrinsic::riscv_vluxseg4;
  NF = 4;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg4ei8_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg4ei16_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg4ei32_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg4ei64_v_ta:
  ID = Intrinsic::riscv_vluxseg4;
  NF = 4;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg4ei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg4ei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg4ei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg4ei64_v_tuma:
  ID = Intrinsic::riscv_vluxseg4_mask;
  NF = 4;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg4ei8_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg4ei16_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg4ei32_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg4ei64_v_tama:
  ID = Intrinsic::riscv_vluxseg4_mask;
  NF = 4;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg4ei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg4ei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg4ei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg4ei64_v_tumu:
  ID = Intrinsic::riscv_vluxseg4_mask;
  NF = 4;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg4ei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg4ei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg4ei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg4ei64_v_tamu:
  ID = Intrinsic::riscv_vluxseg4_mask;
  NF = 4;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg5ei8_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg5ei16_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg5ei32_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg5ei64_v_tu:
  ID = Intrinsic::riscv_vluxseg5;
  NF = 5;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg5ei8_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg5ei16_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg5ei32_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg5ei64_v_ta:
  ID = Intrinsic::riscv_vluxseg5;
  NF = 5;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg5ei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg5ei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg5ei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg5ei64_v_tuma:
  ID = Intrinsic::riscv_vluxseg5_mask;
  NF = 5;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg5ei8_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg5ei16_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg5ei32_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg5ei64_v_tama:
  ID = Intrinsic::riscv_vluxseg5_mask;
  NF = 5;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg5ei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg5ei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg5ei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg5ei64_v_tumu:
  ID = Intrinsic::riscv_vluxseg5_mask;
  NF = 5;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg5ei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg5ei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg5ei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg5ei64_v_tamu:
  ID = Intrinsic::riscv_vluxseg5_mask;
  NF = 5;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg6ei8_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg6ei16_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg6ei32_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg6ei64_v_tu:
  ID = Intrinsic::riscv_vluxseg6;
  NF = 6;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg6ei8_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg6ei16_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg6ei32_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg6ei64_v_ta:
  ID = Intrinsic::riscv_vluxseg6;
  NF = 6;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg6ei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg6ei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg6ei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg6ei64_v_tuma:
  ID = Intrinsic::riscv_vluxseg6_mask;
  NF = 6;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg6ei8_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg6ei16_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg6ei32_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg6ei64_v_tama:
  ID = Intrinsic::riscv_vluxseg6_mask;
  NF = 6;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg6ei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg6ei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg6ei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg6ei64_v_tumu:
  ID = Intrinsic::riscv_vluxseg6_mask;
  NF = 6;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg6ei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg6ei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg6ei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg6ei64_v_tamu:
  ID = Intrinsic::riscv_vluxseg6_mask;
  NF = 6;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg7ei8_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg7ei16_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg7ei32_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg7ei64_v_tu:
  ID = Intrinsic::riscv_vluxseg7;
  NF = 7;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg7ei8_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg7ei16_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg7ei32_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg7ei64_v_ta:
  ID = Intrinsic::riscv_vluxseg7;
  NF = 7;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg7ei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg7ei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg7ei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg7ei64_v_tuma:
  ID = Intrinsic::riscv_vluxseg7_mask;
  NF = 7;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg7ei8_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg7ei16_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg7ei32_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg7ei64_v_tama:
  ID = Intrinsic::riscv_vluxseg7_mask;
  NF = 7;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg7ei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg7ei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg7ei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg7ei64_v_tumu:
  ID = Intrinsic::riscv_vluxseg7_mask;
  NF = 7;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg7ei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg7ei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg7ei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg7ei64_v_tamu:
  ID = Intrinsic::riscv_vluxseg7_mask;
  NF = 7;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg8ei8_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg8ei16_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg8ei32_v_tu:
case RISCVVector::BI__builtin_rvv_vluxseg8ei64_v_tu:
  ID = Intrinsic::riscv_vluxseg8;
  NF = 8;
  DefaultPolicy = 0;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg8ei8_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg8ei16_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg8ei32_v_ta:
case RISCVVector::BI__builtin_rvv_vluxseg8ei64_v_ta:
  ID = Intrinsic::riscv_vluxseg8;
  NF = 8;
  DefaultPolicy = 1;

    {
      // TA builtin: (val0 address, val1 address, ..., ptr, index, vl)
      // TU builtin: (val0 address, ..., passthru0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (passthru0, passthru1, ..., ptr, index, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        IntrinsicTypes = {ResultType, Ops[NF + 1]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I]);
        Operands.push_back(Ops[2 * NF]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 1]->getType(), Ops.back()->getType()};
      }
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg8ei8_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg8ei16_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg8ei32_v_tuma:
case RISCVVector::BI__builtin_rvv_vluxseg8ei64_v_tuma:
  ID = Intrinsic::riscv_vluxseg8_mask;
  NF = 8;
  DefaultPolicy = 2;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg8ei8_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg8ei16_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg8ei32_v_tama:
case RISCVVector::BI__builtin_rvv_vluxseg8ei64_v_tama:
  ID = Intrinsic::riscv_vluxseg8_mask;
  NF = 8;
  DefaultPolicy = 3;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg8ei8_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg8ei16_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg8ei32_v_tumu:
case RISCVVector::BI__builtin_rvv_vluxseg8ei64_v_tumu:
  ID = Intrinsic::riscv_vluxseg8_mask;
  NF = 8;
  DefaultPolicy = 0;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vluxseg8ei8_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg8ei16_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg8ei32_v_tamu:
case RISCVVector::BI__builtin_rvv_vluxseg8ei64_v_tamu:
  ID = Intrinsic::riscv_vluxseg8_mask;
  NF = 8;
  DefaultPolicy = 1;

    {
      // TAMA builtin: (val0 address, ..., mask, ptr, index, vl)
      // builtin: (val0 address, ..., mask, maskedoff0, ..., ptr, index, vl)
      ResultType = ConvertType(E->getArg(0)->getType()->getPointeeType());
      // intrinsic: (maskedoff0, ..., ptr, index, mask, vl)
      SmallVector<llvm::Value*, 12> Operands;
      if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC) {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(llvm::PoisonValue::get(ResultType));
        Operands.push_back(Ops[NF + 1]);
        Operands.push_back(Ops[NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[NF + 3]);
        IntrinsicTypes = {ResultType, Ops[NF + 2]->getType(), Ops.back()->getType()};
      } else {
        for (unsigned I = 0; I < NF; ++I)
          Operands.push_back(Ops[NF + I + 1]);
        Operands.push_back(Ops[2 * NF + 1]);
        Operands.push_back(Ops[2 * NF + 2]);
        Operands.push_back(Ops[NF]);
        Operands.push_back(Ops[2 * NF + 3]);
        IntrinsicTypes = {ResultType, Ops[2 * NF + 2]->getType(), Ops.back()->getType()};
      }
      Operands.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
      assert(Operands.size() == NF + 5);
      llvm::Function *F = CGM.getIntrinsic(ID, IntrinsicTypes);
      llvm::Value *LoadValue = Builder.CreateCall(F, Operands, "");
      clang::CharUnits Align =
          CGM.getNaturalPointeeTypeAlignment(E->getArg(0)->getType());
      llvm::Value *V;
      for (unsigned I = 0; I < NF; ++I) {
        llvm::Value *Val = Builder.CreateExtractValue(LoadValue, {I});
        V = Builder.CreateStore(Val, Address(Ops[I], Val->getType(), Align));
      }
      return V;
    }
            break;
case RISCVVector::BI__builtin_rvv_vmacc_vv_tu:
case RISCVVector::BI__builtin_rvv_vmacc_vx_tu:
  ID = Intrinsic::riscv_vmacc;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmacc_vv_ta:
case RISCVVector::BI__builtin_rvv_vmacc_vx_ta:
  ID = Intrinsic::riscv_vmacc;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmacc_vv_tuma:
case RISCVVector::BI__builtin_rvv_vmacc_vx_tuma:
  ID = Intrinsic::riscv_vmacc_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmacc_vv_tama:
case RISCVVector::BI__builtin_rvv_vmacc_vx_tama:
  ID = Intrinsic::riscv_vmacc_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmacc_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmacc_vx_tumu:
  ID = Intrinsic::riscv_vmacc_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmacc_vv_tamu:
case RISCVVector::BI__builtin_rvv_vmacc_vx_tamu:
  ID = Intrinsic::riscv_vmacc_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmadc_vv:
case RISCVVector::BI__builtin_rvv_vmadc_vx:
  ID = Intrinsic::riscv_vmadc;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmadc_vvm:
case RISCVVector::BI__builtin_rvv_vmadc_vxm:
  ID = Intrinsic::riscv_vmadc_carry_in;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmadd_vv_tu:
case RISCVVector::BI__builtin_rvv_vmadd_vx_tu:
  ID = Intrinsic::riscv_vmadd;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmadd_vv_ta:
case RISCVVector::BI__builtin_rvv_vmadd_vx_ta:
  ID = Intrinsic::riscv_vmadd;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmadd_vv_tuma:
case RISCVVector::BI__builtin_rvv_vmadd_vx_tuma:
  ID = Intrinsic::riscv_vmadd_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmadd_vv_tama:
case RISCVVector::BI__builtin_rvv_vmadd_vx_tama:
  ID = Intrinsic::riscv_vmadd_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmadd_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmadd_vx_tumu:
  ID = Intrinsic::riscv_vmadd_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmadd_vv_tamu:
case RISCVVector::BI__builtin_rvv_vmadd_vx_tamu:
  ID = Intrinsic::riscv_vmadd_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmmv_m:
  ID = Intrinsic::riscv_vmand;
  DefaultPolicy = 1;

      {
        // op1, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType()};
        Ops.insert(Ops.begin() + 1, Ops[0]);
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vmand_mm:
  ID = Intrinsic::riscv_vmand;
  DefaultPolicy = 1;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmandn_mm:
  ID = Intrinsic::riscv_vmandn;
  DefaultPolicy = 1;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmax_vv_tu:
case RISCVVector::BI__builtin_rvv_vmax_vx_tu:
  ID = Intrinsic::riscv_vmax;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmax_vv_ta:
case RISCVVector::BI__builtin_rvv_vmax_vx_ta:
  ID = Intrinsic::riscv_vmax;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmax_vv_tuma:
case RISCVVector::BI__builtin_rvv_vmax_vx_tuma:
  ID = Intrinsic::riscv_vmax_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmax_vv_tama:
case RISCVVector::BI__builtin_rvv_vmax_vx_tama:
  ID = Intrinsic::riscv_vmax_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmax_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmax_vx_tumu:
  ID = Intrinsic::riscv_vmax_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmax_vv_tamu:
case RISCVVector::BI__builtin_rvv_vmax_vx_tamu:
  ID = Intrinsic::riscv_vmax_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmaxu_vv_tu:
case RISCVVector::BI__builtin_rvv_vmaxu_vx_tu:
  ID = Intrinsic::riscv_vmaxu;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmaxu_vv_ta:
case RISCVVector::BI__builtin_rvv_vmaxu_vx_ta:
  ID = Intrinsic::riscv_vmaxu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmaxu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vmaxu_vx_tuma:
  ID = Intrinsic::riscv_vmaxu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmaxu_vv_tama:
case RISCVVector::BI__builtin_rvv_vmaxu_vx_tama:
  ID = Intrinsic::riscv_vmaxu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmaxu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmaxu_vx_tumu:
  ID = Intrinsic::riscv_vmaxu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmaxu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vmaxu_vx_tamu:
  ID = Intrinsic::riscv_vmaxu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmclr_m:
  ID = Intrinsic::riscv_vmclr;
  DefaultPolicy = 1;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmerge_vvm_tu:
case RISCVVector::BI__builtin_rvv_vmerge_vxm_tu:
  ID = Intrinsic::riscv_vmerge;
  DefaultPolicy = 0;

      std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
      // insert poison passthru
      if (DefaultPolicy == TAIL_AGNOSTIC)
        Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
      IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
    break;
case RISCVVector::BI__builtin_rvv_vmerge_vvm_ta:
case RISCVVector::BI__builtin_rvv_vmerge_vxm_ta:
  ID = Intrinsic::riscv_vmerge;
  DefaultPolicy = 1;

      std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
      // insert poison passthru
      if (DefaultPolicy == TAIL_AGNOSTIC)
        Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
      IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
    break;
case RISCVVector::BI__builtin_rvv_vmfeq_vv:
case RISCVVector::BI__builtin_rvv_vmfeq_vf:
  ID = Intrinsic::riscv_vmfeq;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfeq_vv_ma:
case RISCVVector::BI__builtin_rvv_vmfeq_vf_ma:
  ID = Intrinsic::riscv_vmfeq_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfeq_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmfeq_vf_tumu:
  ID = Intrinsic::riscv_vmfeq_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfeq_vv_mu:
case RISCVVector::BI__builtin_rvv_vmfeq_vf_mu:
  ID = Intrinsic::riscv_vmfeq_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfge_vv:
case RISCVVector::BI__builtin_rvv_vmfge_vf:
  ID = Intrinsic::riscv_vmfge;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfge_vv_ma:
case RISCVVector::BI__builtin_rvv_vmfge_vf_ma:
  ID = Intrinsic::riscv_vmfge_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfge_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmfge_vf_tumu:
  ID = Intrinsic::riscv_vmfge_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfge_vv_mu:
case RISCVVector::BI__builtin_rvv_vmfge_vf_mu:
  ID = Intrinsic::riscv_vmfge_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfgt_vv:
case RISCVVector::BI__builtin_rvv_vmfgt_vf:
  ID = Intrinsic::riscv_vmfgt;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfgt_vv_ma:
case RISCVVector::BI__builtin_rvv_vmfgt_vf_ma:
  ID = Intrinsic::riscv_vmfgt_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfgt_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmfgt_vf_tumu:
  ID = Intrinsic::riscv_vmfgt_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfgt_vv_mu:
case RISCVVector::BI__builtin_rvv_vmfgt_vf_mu:
  ID = Intrinsic::riscv_vmfgt_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfle_vv:
case RISCVVector::BI__builtin_rvv_vmfle_vf:
  ID = Intrinsic::riscv_vmfle;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfle_vv_ma:
case RISCVVector::BI__builtin_rvv_vmfle_vf_ma:
  ID = Intrinsic::riscv_vmfle_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfle_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmfle_vf_tumu:
  ID = Intrinsic::riscv_vmfle_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfle_vv_mu:
case RISCVVector::BI__builtin_rvv_vmfle_vf_mu:
  ID = Intrinsic::riscv_vmfle_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmflt_vv:
case RISCVVector::BI__builtin_rvv_vmflt_vf:
  ID = Intrinsic::riscv_vmflt;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmflt_vv_ma:
case RISCVVector::BI__builtin_rvv_vmflt_vf_ma:
  ID = Intrinsic::riscv_vmflt_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmflt_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmflt_vf_tumu:
  ID = Intrinsic::riscv_vmflt_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmflt_vv_mu:
case RISCVVector::BI__builtin_rvv_vmflt_vf_mu:
  ID = Intrinsic::riscv_vmflt_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfne_vv:
case RISCVVector::BI__builtin_rvv_vmfne_vf:
  ID = Intrinsic::riscv_vmfne;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfne_vv_ma:
case RISCVVector::BI__builtin_rvv_vmfne_vf_ma:
  ID = Intrinsic::riscv_vmfne_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfne_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmfne_vf_tumu:
  ID = Intrinsic::riscv_vmfne_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmfne_vv_mu:
case RISCVVector::BI__builtin_rvv_vmfne_vf_mu:
  ID = Intrinsic::riscv_vmfne_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmin_vv_tu:
case RISCVVector::BI__builtin_rvv_vmin_vx_tu:
  ID = Intrinsic::riscv_vmin;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmin_vv_ta:
case RISCVVector::BI__builtin_rvv_vmin_vx_ta:
  ID = Intrinsic::riscv_vmin;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmin_vv_tuma:
case RISCVVector::BI__builtin_rvv_vmin_vx_tuma:
  ID = Intrinsic::riscv_vmin_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmin_vv_tama:
case RISCVVector::BI__builtin_rvv_vmin_vx_tama:
  ID = Intrinsic::riscv_vmin_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmin_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmin_vx_tumu:
  ID = Intrinsic::riscv_vmin_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmin_vv_tamu:
case RISCVVector::BI__builtin_rvv_vmin_vx_tamu:
  ID = Intrinsic::riscv_vmin_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vminu_vv_tu:
case RISCVVector::BI__builtin_rvv_vminu_vx_tu:
  ID = Intrinsic::riscv_vminu;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vminu_vv_ta:
case RISCVVector::BI__builtin_rvv_vminu_vx_ta:
  ID = Intrinsic::riscv_vminu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vminu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vminu_vx_tuma:
  ID = Intrinsic::riscv_vminu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vminu_vv_tama:
case RISCVVector::BI__builtin_rvv_vminu_vx_tama:
  ID = Intrinsic::riscv_vminu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vminu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vminu_vx_tumu:
  ID = Intrinsic::riscv_vminu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vminu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vminu_vx_tamu:
  ID = Intrinsic::riscv_vminu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmnot_m:
  ID = Intrinsic::riscv_vmnand;
  DefaultPolicy = 1;

      {
        // op1, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType()};
        Ops.insert(Ops.begin() + 1, Ops[0]);
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vmnand_mm:
  ID = Intrinsic::riscv_vmnand;
  DefaultPolicy = 1;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmnor_mm:
  ID = Intrinsic::riscv_vmnor;
  DefaultPolicy = 1;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmor_mm:
  ID = Intrinsic::riscv_vmor;
  DefaultPolicy = 1;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmorn_mm:
  ID = Intrinsic::riscv_vmorn;
  DefaultPolicy = 1;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsbc_vv:
case RISCVVector::BI__builtin_rvv_vmsbc_vx:
  ID = Intrinsic::riscv_vmsbc;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsbc_vvm:
case RISCVVector::BI__builtin_rvv_vmsbc_vxm:
  ID = Intrinsic::riscv_vmsbc_borrow_in;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsbf_m:
  ID = Intrinsic::riscv_vmsbf;
  DefaultPolicy = 1;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsbf_m_ma:
  ID = Intrinsic::riscv_vmsbf_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsbf_m_tumu:
  ID = Intrinsic::riscv_vmsbf_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsbf_m_mu:
  ID = Intrinsic::riscv_vmsbf_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmseq_vv:
case RISCVVector::BI__builtin_rvv_vmseq_vx:
  ID = Intrinsic::riscv_vmseq;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmseq_vv_ma:
case RISCVVector::BI__builtin_rvv_vmseq_vx_ma:
  ID = Intrinsic::riscv_vmseq_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmseq_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmseq_vx_tumu:
  ID = Intrinsic::riscv_vmseq_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmseq_vv_mu:
case RISCVVector::BI__builtin_rvv_vmseq_vx_mu:
  ID = Intrinsic::riscv_vmseq_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmset_m:
  ID = Intrinsic::riscv_vmset;
  DefaultPolicy = 1;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsge_vv:
case RISCVVector::BI__builtin_rvv_vmsge_vx:
  ID = Intrinsic::riscv_vmsge;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsge_vv_ma:
case RISCVVector::BI__builtin_rvv_vmsge_vx_ma:
  ID = Intrinsic::riscv_vmsge_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsge_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmsge_vx_tumu:
  ID = Intrinsic::riscv_vmsge_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsge_vv_mu:
case RISCVVector::BI__builtin_rvv_vmsge_vx_mu:
  ID = Intrinsic::riscv_vmsge_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsgeu_vv:
case RISCVVector::BI__builtin_rvv_vmsgeu_vx:
  ID = Intrinsic::riscv_vmsgeu;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsgeu_vv_ma:
case RISCVVector::BI__builtin_rvv_vmsgeu_vx_ma:
  ID = Intrinsic::riscv_vmsgeu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsgeu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmsgeu_vx_tumu:
  ID = Intrinsic::riscv_vmsgeu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsgeu_vv_mu:
case RISCVVector::BI__builtin_rvv_vmsgeu_vx_mu:
  ID = Intrinsic::riscv_vmsgeu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsgt_vv:
case RISCVVector::BI__builtin_rvv_vmsgt_vx:
  ID = Intrinsic::riscv_vmsgt;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsgt_vv_ma:
case RISCVVector::BI__builtin_rvv_vmsgt_vx_ma:
  ID = Intrinsic::riscv_vmsgt_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsgt_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmsgt_vx_tumu:
  ID = Intrinsic::riscv_vmsgt_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsgt_vv_mu:
case RISCVVector::BI__builtin_rvv_vmsgt_vx_mu:
  ID = Intrinsic::riscv_vmsgt_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsgtu_vv:
case RISCVVector::BI__builtin_rvv_vmsgtu_vx:
  ID = Intrinsic::riscv_vmsgtu;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsgtu_vv_ma:
case RISCVVector::BI__builtin_rvv_vmsgtu_vx_ma:
  ID = Intrinsic::riscv_vmsgtu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsgtu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmsgtu_vx_tumu:
  ID = Intrinsic::riscv_vmsgtu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsgtu_vv_mu:
case RISCVVector::BI__builtin_rvv_vmsgtu_vx_mu:
  ID = Intrinsic::riscv_vmsgtu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsif_m:
  ID = Intrinsic::riscv_vmsif;
  DefaultPolicy = 1;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsif_m_ma:
  ID = Intrinsic::riscv_vmsif_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsif_m_tumu:
  ID = Intrinsic::riscv_vmsif_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsif_m_mu:
  ID = Intrinsic::riscv_vmsif_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsle_vv:
case RISCVVector::BI__builtin_rvv_vmsle_vx:
  ID = Intrinsic::riscv_vmsle;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsle_vv_ma:
case RISCVVector::BI__builtin_rvv_vmsle_vx_ma:
  ID = Intrinsic::riscv_vmsle_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsle_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmsle_vx_tumu:
  ID = Intrinsic::riscv_vmsle_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsle_vv_mu:
case RISCVVector::BI__builtin_rvv_vmsle_vx_mu:
  ID = Intrinsic::riscv_vmsle_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsleu_vv:
case RISCVVector::BI__builtin_rvv_vmsleu_vx:
  ID = Intrinsic::riscv_vmsleu;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsleu_vv_ma:
case RISCVVector::BI__builtin_rvv_vmsleu_vx_ma:
  ID = Intrinsic::riscv_vmsleu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsleu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmsleu_vx_tumu:
  ID = Intrinsic::riscv_vmsleu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsleu_vv_mu:
case RISCVVector::BI__builtin_rvv_vmsleu_vx_mu:
  ID = Intrinsic::riscv_vmsleu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmslt_vv:
case RISCVVector::BI__builtin_rvv_vmslt_vx:
  ID = Intrinsic::riscv_vmslt;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmslt_vv_ma:
case RISCVVector::BI__builtin_rvv_vmslt_vx_ma:
  ID = Intrinsic::riscv_vmslt_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmslt_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmslt_vx_tumu:
  ID = Intrinsic::riscv_vmslt_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmslt_vv_mu:
case RISCVVector::BI__builtin_rvv_vmslt_vx_mu:
  ID = Intrinsic::riscv_vmslt_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsltu_vv:
case RISCVVector::BI__builtin_rvv_vmsltu_vx:
  ID = Intrinsic::riscv_vmsltu;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsltu_vv_ma:
case RISCVVector::BI__builtin_rvv_vmsltu_vx_ma:
  ID = Intrinsic::riscv_vmsltu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsltu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmsltu_vx_tumu:
  ID = Intrinsic::riscv_vmsltu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsltu_vv_mu:
case RISCVVector::BI__builtin_rvv_vmsltu_vx_mu:
  ID = Intrinsic::riscv_vmsltu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsne_vv:
case RISCVVector::BI__builtin_rvv_vmsne_vx:
  ID = Intrinsic::riscv_vmsne;
  DefaultPolicy = 1;
  IntrinsicTypes = {Ops[0]->getType(), Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsne_vv_ma:
case RISCVVector::BI__builtin_rvv_vmsne_vx_ma:
  ID = Intrinsic::riscv_vmsne_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsne_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmsne_vx_tumu:
  ID = Intrinsic::riscv_vmsne_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsne_vv_mu:
case RISCVVector::BI__builtin_rvv_vmsne_vx_mu:
  ID = Intrinsic::riscv_vmsne_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsof_m:
  ID = Intrinsic::riscv_vmsof;
  DefaultPolicy = 1;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsof_m_ma:
  ID = Intrinsic::riscv_vmsof_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsof_m_tumu:
  ID = Intrinsic::riscv_vmsof_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmsof_m_mu:
  ID = Intrinsic::riscv_vmsof_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmul_vv_tu:
case RISCVVector::BI__builtin_rvv_vmul_vx_tu:
  ID = Intrinsic::riscv_vmul;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmul_vv_ta:
case RISCVVector::BI__builtin_rvv_vmul_vx_ta:
  ID = Intrinsic::riscv_vmul;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmul_vv_tuma:
case RISCVVector::BI__builtin_rvv_vmul_vx_tuma:
  ID = Intrinsic::riscv_vmul_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmul_vv_tama:
case RISCVVector::BI__builtin_rvv_vmul_vx_tama:
  ID = Intrinsic::riscv_vmul_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmul_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmul_vx_tumu:
  ID = Intrinsic::riscv_vmul_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmul_vv_tamu:
case RISCVVector::BI__builtin_rvv_vmul_vx_tamu:
  ID = Intrinsic::riscv_vmul_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulh_vv_tu:
case RISCVVector::BI__builtin_rvv_vmulh_vx_tu:
  ID = Intrinsic::riscv_vmulh;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulh_vv_ta:
case RISCVVector::BI__builtin_rvv_vmulh_vx_ta:
  ID = Intrinsic::riscv_vmulh;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulh_vv_tuma:
case RISCVVector::BI__builtin_rvv_vmulh_vx_tuma:
  ID = Intrinsic::riscv_vmulh_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulh_vv_tama:
case RISCVVector::BI__builtin_rvv_vmulh_vx_tama:
  ID = Intrinsic::riscv_vmulh_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulh_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmulh_vx_tumu:
  ID = Intrinsic::riscv_vmulh_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulh_vv_tamu:
case RISCVVector::BI__builtin_rvv_vmulh_vx_tamu:
  ID = Intrinsic::riscv_vmulh_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulhsu_vv_tu:
case RISCVVector::BI__builtin_rvv_vmulhsu_vx_tu:
  ID = Intrinsic::riscv_vmulhsu;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulhsu_vv_ta:
case RISCVVector::BI__builtin_rvv_vmulhsu_vx_ta:
  ID = Intrinsic::riscv_vmulhsu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulhsu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vmulhsu_vx_tuma:
  ID = Intrinsic::riscv_vmulhsu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulhsu_vv_tama:
case RISCVVector::BI__builtin_rvv_vmulhsu_vx_tama:
  ID = Intrinsic::riscv_vmulhsu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulhsu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmulhsu_vx_tumu:
  ID = Intrinsic::riscv_vmulhsu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulhsu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vmulhsu_vx_tamu:
  ID = Intrinsic::riscv_vmulhsu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulhu_vv_tu:
case RISCVVector::BI__builtin_rvv_vmulhu_vx_tu:
  ID = Intrinsic::riscv_vmulhu;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulhu_vv_ta:
case RISCVVector::BI__builtin_rvv_vmulhu_vx_ta:
  ID = Intrinsic::riscv_vmulhu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulhu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vmulhu_vx_tuma:
  ID = Intrinsic::riscv_vmulhu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulhu_vv_tama:
case RISCVVector::BI__builtin_rvv_vmulhu_vx_tama:
  ID = Intrinsic::riscv_vmulhu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulhu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vmulhu_vx_tumu:
  ID = Intrinsic::riscv_vmulhu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmulhu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vmulhu_vx_tamu:
  ID = Intrinsic::riscv_vmulhu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmv_s_x_tu:
  ID = Intrinsic::riscv_vmv_s_x;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmv_s_x_ta:
  ID = Intrinsic::riscv_vmv_s_x;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmv_v_v_tu:
  ID = Intrinsic::riscv_vmv_v_v;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmv_v_v_ta:
  ID = Intrinsic::riscv_vmv_v_v;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmv_v_x_tu:
  ID = Intrinsic::riscv_vmv_v_x;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmv_v_x_ta:
  ID = Intrinsic::riscv_vmv_v_x;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmv_x_s:
  ID = Intrinsic::riscv_vmv_x_s;
  IntrinsicTypes = {Ops[0]->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmxnor_mm:
  ID = Intrinsic::riscv_vmxnor;
  DefaultPolicy = 1;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vmxor_mm:
  ID = Intrinsic::riscv_vmxor;
  DefaultPolicy = 1;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnclip_wv_tu:
case RISCVVector::BI__builtin_rvv_vnclip_wx_tu:
  ID = Intrinsic::riscv_vnclip;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnclip_wv_ta:
case RISCVVector::BI__builtin_rvv_vnclip_wx_ta:
  ID = Intrinsic::riscv_vnclip;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnclip_wv_tuma:
case RISCVVector::BI__builtin_rvv_vnclip_wx_tuma:
  ID = Intrinsic::riscv_vnclip_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnclip_wv_tama:
case RISCVVector::BI__builtin_rvv_vnclip_wx_tama:
  ID = Intrinsic::riscv_vnclip_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnclip_wv_tumu:
case RISCVVector::BI__builtin_rvv_vnclip_wx_tumu:
  ID = Intrinsic::riscv_vnclip_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnclip_wv_tamu:
case RISCVVector::BI__builtin_rvv_vnclip_wx_tamu:
  ID = Intrinsic::riscv_vnclip_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnclipu_wv_tu:
case RISCVVector::BI__builtin_rvv_vnclipu_wx_tu:
  ID = Intrinsic::riscv_vnclipu;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnclipu_wv_ta:
case RISCVVector::BI__builtin_rvv_vnclipu_wx_ta:
  ID = Intrinsic::riscv_vnclipu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnclipu_wv_tuma:
case RISCVVector::BI__builtin_rvv_vnclipu_wx_tuma:
  ID = Intrinsic::riscv_vnclipu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnclipu_wv_tama:
case RISCVVector::BI__builtin_rvv_vnclipu_wx_tama:
  ID = Intrinsic::riscv_vnclipu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnclipu_wv_tumu:
case RISCVVector::BI__builtin_rvv_vnclipu_wx_tumu:
  ID = Intrinsic::riscv_vnclipu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnclipu_wv_tamu:
case RISCVVector::BI__builtin_rvv_vnclipu_wx_tamu:
  ID = Intrinsic::riscv_vnclipu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnmsac_vv_tu:
case RISCVVector::BI__builtin_rvv_vnmsac_vx_tu:
  ID = Intrinsic::riscv_vnmsac;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnmsac_vv_ta:
case RISCVVector::BI__builtin_rvv_vnmsac_vx_ta:
  ID = Intrinsic::riscv_vnmsac;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnmsac_vv_tuma:
case RISCVVector::BI__builtin_rvv_vnmsac_vx_tuma:
  ID = Intrinsic::riscv_vnmsac_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnmsac_vv_tama:
case RISCVVector::BI__builtin_rvv_vnmsac_vx_tama:
  ID = Intrinsic::riscv_vnmsac_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnmsac_vv_tumu:
case RISCVVector::BI__builtin_rvv_vnmsac_vx_tumu:
  ID = Intrinsic::riscv_vnmsac_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnmsac_vv_tamu:
case RISCVVector::BI__builtin_rvv_vnmsac_vx_tamu:
  ID = Intrinsic::riscv_vnmsac_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnmsub_vv_tu:
case RISCVVector::BI__builtin_rvv_vnmsub_vx_tu:
  ID = Intrinsic::riscv_vnmsub;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnmsub_vv_ta:
case RISCVVector::BI__builtin_rvv_vnmsub_vx_ta:
  ID = Intrinsic::riscv_vnmsub;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnmsub_vv_tuma:
case RISCVVector::BI__builtin_rvv_vnmsub_vx_tuma:
  ID = Intrinsic::riscv_vnmsub_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnmsub_vv_tama:
case RISCVVector::BI__builtin_rvv_vnmsub_vx_tama:
  ID = Intrinsic::riscv_vnmsub_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnmsub_vv_tumu:
case RISCVVector::BI__builtin_rvv_vnmsub_vx_tumu:
  ID = Intrinsic::riscv_vnmsub_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnmsub_vv_tamu:
case RISCVVector::BI__builtin_rvv_vnmsub_vx_tamu:
  ID = Intrinsic::riscv_vnmsub_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnsra_wv_tu:
case RISCVVector::BI__builtin_rvv_vnsra_wx_tu:
  ID = Intrinsic::riscv_vnsra;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnsra_wv_ta:
case RISCVVector::BI__builtin_rvv_vnsra_wx_ta:
  ID = Intrinsic::riscv_vnsra;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnsra_wv_tuma:
case RISCVVector::BI__builtin_rvv_vnsra_wx_tuma:
  ID = Intrinsic::riscv_vnsra_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnsra_wv_tama:
case RISCVVector::BI__builtin_rvv_vnsra_wx_tama:
  ID = Intrinsic::riscv_vnsra_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnsra_wv_tumu:
case RISCVVector::BI__builtin_rvv_vnsra_wx_tumu:
  ID = Intrinsic::riscv_vnsra_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnsra_wv_tamu:
case RISCVVector::BI__builtin_rvv_vnsra_wx_tamu:
  ID = Intrinsic::riscv_vnsra_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnsrl_wv_tu:
case RISCVVector::BI__builtin_rvv_vnsrl_wx_tu:
  ID = Intrinsic::riscv_vnsrl;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vncvt_x_x_w_tu:
  ID = Intrinsic::riscv_vnsrl;
  DefaultPolicy = 0;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(Ops.back()->getType()));
        // passthru, op1, xlen, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          Ops[3]->getType(),
                          Ops[3]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vnsrl_wv_ta:
case RISCVVector::BI__builtin_rvv_vnsrl_wx_ta:
  ID = Intrinsic::riscv_vnsrl;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vncvt_x_x_w_ta:
  ID = Intrinsic::riscv_vnsrl;
  DefaultPolicy = 1;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(Ops.back()->getType()));
        // passthru, op1, xlen, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          Ops[3]->getType(),
                          Ops[3]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vnsrl_wv_tuma:
case RISCVVector::BI__builtin_rvv_vnsrl_wx_tuma:
  ID = Intrinsic::riscv_vnsrl_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vncvt_x_x_w_tuma:
  ID = Intrinsic::riscv_vnsrl_mask;
  DefaultPolicy = 2;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(Ops.back()->getType()));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, xlen, mask, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          Ops[4]->getType(),
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vnsrl_wv_tama:
case RISCVVector::BI__builtin_rvv_vnsrl_wx_tama:
  ID = Intrinsic::riscv_vnsrl_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vncvt_x_x_w_tama:
  ID = Intrinsic::riscv_vnsrl_mask;
  DefaultPolicy = 3;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(Ops.back()->getType()));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, xlen, mask, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          Ops[4]->getType(),
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vnsrl_wv_tumu:
case RISCVVector::BI__builtin_rvv_vnsrl_wx_tumu:
  ID = Intrinsic::riscv_vnsrl_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vncvt_x_x_w_tumu:
  ID = Intrinsic::riscv_vnsrl_mask;
  DefaultPolicy = 0;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(Ops.back()->getType()));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, xlen, mask, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          Ops[4]->getType(),
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vnsrl_wv_tamu:
case RISCVVector::BI__builtin_rvv_vnsrl_wx_tamu:
  ID = Intrinsic::riscv_vnsrl_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vncvt_x_x_w_tamu:
  ID = Intrinsic::riscv_vnsrl_mask;
  DefaultPolicy = 1;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(Ops.back()->getType()));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, xlen, mask, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          Ops[4]->getType(),
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vor_vv_tu:
case RISCVVector::BI__builtin_rvv_vor_vx_tu:
  ID = Intrinsic::riscv_vor;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vor_vv_ta:
case RISCVVector::BI__builtin_rvv_vor_vx_ta:
  ID = Intrinsic::riscv_vor;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vor_vv_tuma:
case RISCVVector::BI__builtin_rvv_vor_vx_tuma:
  ID = Intrinsic::riscv_vor_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vor_vv_tama:
case RISCVVector::BI__builtin_rvv_vor_vx_tama:
  ID = Intrinsic::riscv_vor_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vor_vv_tumu:
case RISCVVector::BI__builtin_rvv_vor_vx_tumu:
  ID = Intrinsic::riscv_vor_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vor_vv_tamu:
case RISCVVector::BI__builtin_rvv_vor_vx_tamu:
  ID = Intrinsic::riscv_vor_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredand_vs_tu:
  ID = Intrinsic::riscv_vredand;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredand_vs_ta:
  ID = Intrinsic::riscv_vredand;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredand_vs_tum:
  ID = Intrinsic::riscv_vredand_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredand_vs_tam:
  ID = Intrinsic::riscv_vredand_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredand_vs_tumu:
  ID = Intrinsic::riscv_vredand_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmax_vs_tu:
  ID = Intrinsic::riscv_vredmax;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmax_vs_ta:
  ID = Intrinsic::riscv_vredmax;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmax_vs_tum:
  ID = Intrinsic::riscv_vredmax_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmax_vs_tam:
  ID = Intrinsic::riscv_vredmax_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmax_vs_tumu:
  ID = Intrinsic::riscv_vredmax_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmaxu_vs_tu:
  ID = Intrinsic::riscv_vredmaxu;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmaxu_vs_ta:
  ID = Intrinsic::riscv_vredmaxu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmaxu_vs_tum:
  ID = Intrinsic::riscv_vredmaxu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmaxu_vs_tam:
  ID = Intrinsic::riscv_vredmaxu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmaxu_vs_tumu:
  ID = Intrinsic::riscv_vredmaxu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmin_vs_tu:
  ID = Intrinsic::riscv_vredmin;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmin_vs_ta:
  ID = Intrinsic::riscv_vredmin;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmin_vs_tum:
  ID = Intrinsic::riscv_vredmin_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmin_vs_tam:
  ID = Intrinsic::riscv_vredmin_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredmin_vs_tumu:
  ID = Intrinsic::riscv_vredmin_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredminu_vs_tu:
  ID = Intrinsic::riscv_vredminu;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredminu_vs_ta:
  ID = Intrinsic::riscv_vredminu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredminu_vs_tum:
  ID = Intrinsic::riscv_vredminu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredminu_vs_tam:
  ID = Intrinsic::riscv_vredminu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredminu_vs_tumu:
  ID = Intrinsic::riscv_vredminu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredor_vs_tu:
  ID = Intrinsic::riscv_vredor;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredor_vs_ta:
  ID = Intrinsic::riscv_vredor;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredor_vs_tum:
  ID = Intrinsic::riscv_vredor_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredor_vs_tam:
  ID = Intrinsic::riscv_vredor_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredor_vs_tumu:
  ID = Intrinsic::riscv_vredor_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredsum_vs_tu:
  ID = Intrinsic::riscv_vredsum;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredsum_vs_ta:
  ID = Intrinsic::riscv_vredsum;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredsum_vs_tum:
  ID = Intrinsic::riscv_vredsum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredsum_vs_tam:
  ID = Intrinsic::riscv_vredsum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredsum_vs_tumu:
  ID = Intrinsic::riscv_vredsum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredxor_vs_tu:
  ID = Intrinsic::riscv_vredxor;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredxor_vs_ta:
  ID = Intrinsic::riscv_vredxor;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredxor_vs_tum:
  ID = Intrinsic::riscv_vredxor_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredxor_vs_tam:
  ID = Intrinsic::riscv_vredxor_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vredxor_vs_tumu:
  ID = Intrinsic::riscv_vredxor_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrem_vv_tu:
case RISCVVector::BI__builtin_rvv_vrem_vx_tu:
  ID = Intrinsic::riscv_vrem;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrem_vv_ta:
case RISCVVector::BI__builtin_rvv_vrem_vx_ta:
  ID = Intrinsic::riscv_vrem;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrem_vv_tuma:
case RISCVVector::BI__builtin_rvv_vrem_vx_tuma:
  ID = Intrinsic::riscv_vrem_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrem_vv_tama:
case RISCVVector::BI__builtin_rvv_vrem_vx_tama:
  ID = Intrinsic::riscv_vrem_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrem_vv_tumu:
case RISCVVector::BI__builtin_rvv_vrem_vx_tumu:
  ID = Intrinsic::riscv_vrem_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrem_vv_tamu:
case RISCVVector::BI__builtin_rvv_vrem_vx_tamu:
  ID = Intrinsic::riscv_vrem_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vremu_vv_tu:
case RISCVVector::BI__builtin_rvv_vremu_vx_tu:
  ID = Intrinsic::riscv_vremu;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vremu_vv_ta:
case RISCVVector::BI__builtin_rvv_vremu_vx_ta:
  ID = Intrinsic::riscv_vremu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vremu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vremu_vx_tuma:
  ID = Intrinsic::riscv_vremu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vremu_vv_tama:
case RISCVVector::BI__builtin_rvv_vremu_vx_tama:
  ID = Intrinsic::riscv_vremu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vremu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vremu_vx_tumu:
  ID = Intrinsic::riscv_vremu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vremu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vremu_vx_tamu:
  ID = Intrinsic::riscv_vremu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgather_vv_tu:
  ID = Intrinsic::riscv_vrgather_vv;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgather_vv_ta:
  ID = Intrinsic::riscv_vrgather_vv;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgather_vv_tuma:
  ID = Intrinsic::riscv_vrgather_vv_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgather_vv_tama:
  ID = Intrinsic::riscv_vrgather_vv_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgather_vv_tumu:
  ID = Intrinsic::riscv_vrgather_vv_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgather_vv_tamu:
  ID = Intrinsic::riscv_vrgather_vv_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgather_vx_tu:
  ID = Intrinsic::riscv_vrgather_vx;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgather_vx_ta:
  ID = Intrinsic::riscv_vrgather_vx;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgather_vx_tuma:
  ID = Intrinsic::riscv_vrgather_vx_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgather_vx_tama:
  ID = Intrinsic::riscv_vrgather_vx_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgather_vx_tumu:
  ID = Intrinsic::riscv_vrgather_vx_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgather_vx_tamu:
  ID = Intrinsic::riscv_vrgather_vx_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgatherei16_vv_tu:
  ID = Intrinsic::riscv_vrgatherei16_vv;
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgatherei16_vv_ta:
  ID = Intrinsic::riscv_vrgatherei16_vv;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgatherei16_vv_tuma:
  ID = Intrinsic::riscv_vrgatherei16_vv_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgatherei16_vv_tama:
  ID = Intrinsic::riscv_vrgatherei16_vv_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgatherei16_vv_tumu:
  ID = Intrinsic::riscv_vrgatherei16_vv_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrgatherei16_vv_tamu:
  ID = Intrinsic::riscv_vrgatherei16_vv_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vrsub_vx_tu:
  ID = Intrinsic::riscv_vrsub;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vneg_v_tu:
  ID = Intrinsic::riscv_vrsub;
  DefaultPolicy = 0;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        // passthru, op1, op2, vl
        IntrinsicTypes = {ResultType, ElemTy, Ops[3]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vrsub_vx_ta:
  ID = Intrinsic::riscv_vrsub;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vneg_v_ta:
  ID = Intrinsic::riscv_vrsub;
  DefaultPolicy = 1;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        // passthru, op1, op2, vl
        IntrinsicTypes = {ResultType, ElemTy, Ops[3]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vrsub_vx_tuma:
  ID = Intrinsic::riscv_vrsub_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vneg_v_tuma:
  ID = Intrinsic::riscv_vrsub_mask;
  DefaultPolicy = 2;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl, policy
        IntrinsicTypes = {ResultType, ElemTy, Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vrsub_vx_tama:
  ID = Intrinsic::riscv_vrsub_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vneg_v_tama:
  ID = Intrinsic::riscv_vrsub_mask;
  DefaultPolicy = 3;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl, policy
        IntrinsicTypes = {ResultType, ElemTy, Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vrsub_vx_tumu:
  ID = Intrinsic::riscv_vrsub_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vneg_v_tumu:
  ID = Intrinsic::riscv_vrsub_mask;
  DefaultPolicy = 0;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl, policy
        IntrinsicTypes = {ResultType, ElemTy, Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vrsub_vx_tamu:
  ID = Intrinsic::riscv_vrsub_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vneg_v_tamu:
  ID = Intrinsic::riscv_vrsub_mask;
  DefaultPolicy = 1;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl, policy
        IntrinsicTypes = {ResultType, ElemTy, Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vsadd_vv_tu:
case RISCVVector::BI__builtin_rvv_vsadd_vx_tu:
  ID = Intrinsic::riscv_vsadd;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsadd_vv_ta:
case RISCVVector::BI__builtin_rvv_vsadd_vx_ta:
  ID = Intrinsic::riscv_vsadd;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsadd_vv_tuma:
case RISCVVector::BI__builtin_rvv_vsadd_vx_tuma:
  ID = Intrinsic::riscv_vsadd_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsadd_vv_tama:
case RISCVVector::BI__builtin_rvv_vsadd_vx_tama:
  ID = Intrinsic::riscv_vsadd_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsadd_vv_tumu:
case RISCVVector::BI__builtin_rvv_vsadd_vx_tumu:
  ID = Intrinsic::riscv_vsadd_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsadd_vv_tamu:
case RISCVVector::BI__builtin_rvv_vsadd_vx_tamu:
  ID = Intrinsic::riscv_vsadd_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsaddu_vv_tu:
case RISCVVector::BI__builtin_rvv_vsaddu_vx_tu:
  ID = Intrinsic::riscv_vsaddu;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsaddu_vv_ta:
case RISCVVector::BI__builtin_rvv_vsaddu_vx_ta:
  ID = Intrinsic::riscv_vsaddu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsaddu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vsaddu_vx_tuma:
  ID = Intrinsic::riscv_vsaddu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsaddu_vv_tama:
case RISCVVector::BI__builtin_rvv_vsaddu_vx_tama:
  ID = Intrinsic::riscv_vsaddu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsaddu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vsaddu_vx_tumu:
  ID = Intrinsic::riscv_vsaddu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsaddu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vsaddu_vx_tamu:
  ID = Intrinsic::riscv_vsaddu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsbc_vvm_tu:
case RISCVVector::BI__builtin_rvv_vsbc_vxm_tu:
  ID = Intrinsic::riscv_vsbc;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsbc_vvm_ta:
case RISCVVector::BI__builtin_rvv_vsbc_vxm_ta:
  ID = Intrinsic::riscv_vsbc;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vse8_v:
case RISCVVector::BI__builtin_rvv_vse16_v:
case RISCVVector::BI__builtin_rvv_vse32_v:
case RISCVVector::BI__builtin_rvv_vse64_v:
  ID = Intrinsic::riscv_vse;
  DefaultPolicy = 1;

      // Builtin: (ptr, value, vl). Intrinsic: (value, ptr, vl)
      std::swap(Ops[0], Ops[1]);
      Ops[1] = Builder.CreateBitCast(Ops[1], Ops[0]->getType()->getPointerTo());
      IntrinsicTypes = {Ops[0]->getType(), Ops[2]->getType()};
    break;
case RISCVVector::BI__builtin_rvv_vse8_v_m:
case RISCVVector::BI__builtin_rvv_vse16_v_m:
case RISCVVector::BI__builtin_rvv_vse32_v_m:
case RISCVVector::BI__builtin_rvv_vse64_v_m:
  ID = Intrinsic::riscv_vse_mask;
  DefaultPolicy = 0;

      // Builtin: (mask, ptr, value, vl). Intrinsic: (value, ptr, mask, vl)
      std::swap(Ops[0], Ops[2]);
      Ops[1] = Builder.CreateBitCast(Ops[1], Ops[0]->getType()->getPointerTo());
      IntrinsicTypes = {Ops[0]->getType(), Ops[3]->getType()};
    break;
case RISCVVector::BI__builtin_rvv_vsetvli:
  ID = Intrinsic::riscv_vsetvli;
  DefaultPolicy = 1;
IntrinsicTypes = {ResultType};break;
case RISCVVector::BI__builtin_rvv_vsetvlimax:
  ID = Intrinsic::riscv_vsetvlimax;
  DefaultPolicy = 1;
IntrinsicTypes = {ResultType};break;
case RISCVVector::BI__builtin_rvv_vsext_vf2_tu:
case RISCVVector::BI__builtin_rvv_vsext_vf4_tu:
case RISCVVector::BI__builtin_rvv_vsext_vf8_tu:
  ID = Intrinsic::riscv_vsext;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsext_vf2_ta:
case RISCVVector::BI__builtin_rvv_vsext_vf4_ta:
case RISCVVector::BI__builtin_rvv_vsext_vf8_ta:
  ID = Intrinsic::riscv_vsext;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsext_vf2_tuma:
case RISCVVector::BI__builtin_rvv_vsext_vf4_tuma:
case RISCVVector::BI__builtin_rvv_vsext_vf8_tuma:
  ID = Intrinsic::riscv_vsext_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsext_vf2_tama:
case RISCVVector::BI__builtin_rvv_vsext_vf4_tama:
case RISCVVector::BI__builtin_rvv_vsext_vf8_tama:
  ID = Intrinsic::riscv_vsext_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsext_vf2_tumu:
case RISCVVector::BI__builtin_rvv_vsext_vf4_tumu:
case RISCVVector::BI__builtin_rvv_vsext_vf8_tumu:
  ID = Intrinsic::riscv_vsext_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsext_vf2_tamu:
case RISCVVector::BI__builtin_rvv_vsext_vf4_tamu:
case RISCVVector::BI__builtin_rvv_vsext_vf8_tamu:
  ID = Intrinsic::riscv_vsext_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslide1down_vx_tu:
  ID = Intrinsic::riscv_vslide1down;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslide1down_vx_ta:
  ID = Intrinsic::riscv_vslide1down;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslide1down_vx_tuma:
  ID = Intrinsic::riscv_vslide1down_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslide1down_vx_tama:
  ID = Intrinsic::riscv_vslide1down_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslide1down_vx_tumu:
  ID = Intrinsic::riscv_vslide1down_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslide1down_vx_tamu:
  ID = Intrinsic::riscv_vslide1down_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslide1up_vx_tu:
  ID = Intrinsic::riscv_vslide1up;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslide1up_vx_ta:
  ID = Intrinsic::riscv_vslide1up;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslide1up_vx_tuma:
  ID = Intrinsic::riscv_vslide1up_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslide1up_vx_tama:
  ID = Intrinsic::riscv_vslide1up_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslide1up_vx_tumu:
  ID = Intrinsic::riscv_vslide1up_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslide1up_vx_tamu:
  ID = Intrinsic::riscv_vslide1up_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslidedown_vx_tu:
  ID = Intrinsic::riscv_vslidedown;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslidedown_vx_ta:
  ID = Intrinsic::riscv_vslidedown;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslidedown_vx_tuma:
  ID = Intrinsic::riscv_vslidedown_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslidedown_vx_tama:
  ID = Intrinsic::riscv_vslidedown_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslidedown_vx_tumu:
  ID = Intrinsic::riscv_vslidedown_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslidedown_vx_tamu:
  ID = Intrinsic::riscv_vslidedown_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslideup_vx_tu:
  ID = Intrinsic::riscv_vslideup;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslideup_vx_ta:
  ID = Intrinsic::riscv_vslideup;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslideup_vx_tuma:
  ID = Intrinsic::riscv_vslideup_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslideup_vx_tama:
  ID = Intrinsic::riscv_vslideup_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslideup_vx_tumu:
  ID = Intrinsic::riscv_vslideup_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vslideup_vx_tamu:
  ID = Intrinsic::riscv_vslideup_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsll_vv_tu:
case RISCVVector::BI__builtin_rvv_vsll_vx_tu:
  ID = Intrinsic::riscv_vsll;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsll_vv_ta:
case RISCVVector::BI__builtin_rvv_vsll_vx_ta:
  ID = Intrinsic::riscv_vsll;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsll_vv_tuma:
case RISCVVector::BI__builtin_rvv_vsll_vx_tuma:
  ID = Intrinsic::riscv_vsll_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsll_vv_tama:
case RISCVVector::BI__builtin_rvv_vsll_vx_tama:
  ID = Intrinsic::riscv_vsll_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsll_vv_tumu:
case RISCVVector::BI__builtin_rvv_vsll_vx_tumu:
  ID = Intrinsic::riscv_vsll_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsll_vv_tamu:
case RISCVVector::BI__builtin_rvv_vsll_vx_tamu:
  ID = Intrinsic::riscv_vsll_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsm_v:
  ID = Intrinsic::riscv_vsm;
  DefaultPolicy = 1;

      // Builtin: (ptr, value, vl). Intrinsic: (value, ptr, vl)
      std::swap(Ops[0], Ops[1]);
      Ops[1] = Builder.CreateBitCast(Ops[1], Ops[0]->getType()->getPointerTo());
      IntrinsicTypes = {Ops[0]->getType(), Ops[2]->getType()};
    break;
case RISCVVector::BI__builtin_rvv_vsmul_vv_tu:
case RISCVVector::BI__builtin_rvv_vsmul_vx_tu:
  ID = Intrinsic::riscv_vsmul;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsmul_vv_ta:
case RISCVVector::BI__builtin_rvv_vsmul_vx_ta:
  ID = Intrinsic::riscv_vsmul;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsmul_vv_tuma:
case RISCVVector::BI__builtin_rvv_vsmul_vx_tuma:
  ID = Intrinsic::riscv_vsmul_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsmul_vv_tama:
case RISCVVector::BI__builtin_rvv_vsmul_vx_tama:
  ID = Intrinsic::riscv_vsmul_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsmul_vv_tumu:
case RISCVVector::BI__builtin_rvv_vsmul_vx_tumu:
  ID = Intrinsic::riscv_vsmul_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsmul_vv_tamu:
case RISCVVector::BI__builtin_rvv_vsmul_vx_tamu:
  ID = Intrinsic::riscv_vsmul_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsoxei8_v:
case RISCVVector::BI__builtin_rvv_vsoxei16_v:
case RISCVVector::BI__builtin_rvv_vsoxei32_v:
case RISCVVector::BI__builtin_rvv_vsoxei64_v:
  ID = Intrinsic::riscv_vsoxei;
  DefaultPolicy = 1;

        // Builtin: (ptr, index, value, vl). Intrinsic: (value, ptr, index, vl)
        std::rotate(Ops.begin(), Ops.begin() + 2, Ops.begin() + 3);
        Ops[1] = Builder.CreateBitCast(Ops[1],Ops[0]->getType()->getPointerTo());
        IntrinsicTypes = {Ops[0]->getType(), Ops[2]->getType(), Ops[3]->getType()};
      break;
case RISCVVector::BI__builtin_rvv_vsoxei8_v_m:
case RISCVVector::BI__builtin_rvv_vsoxei16_v_m:
case RISCVVector::BI__builtin_rvv_vsoxei32_v_m:
case RISCVVector::BI__builtin_rvv_vsoxei64_v_m:
  ID = Intrinsic::riscv_vsoxei_mask;
  DefaultPolicy = 0;

        // Builtin: (mask, ptr, index, value, vl). Intrinsic: (value, ptr, index, mask, vl)
        std::swap(Ops[0], Ops[3]);
        Ops[1] = Builder.CreateBitCast(Ops[1], Ops[0]->getType()->getPointerTo());
        IntrinsicTypes = {Ops[0]->getType(), Ops[2]->getType(), Ops[4]->getType()};
      break;
case RISCVVector::BI__builtin_rvv_vsoxseg2ei8_v:
case RISCVVector::BI__builtin_rvv_vsoxseg2ei16_v:
case RISCVVector::BI__builtin_rvv_vsoxseg2ei32_v:
case RISCVVector::BI__builtin_rvv_vsoxseg2ei64_v:
  ID = Intrinsic::riscv_vsoxseg2;
  NF = 2;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsoxseg2ei8_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg2ei16_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg2ei32_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg2ei64_v_m:
  ID = Intrinsic::riscv_vsoxseg2_mask;
  NF = 2;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 3]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsoxseg3ei8_v:
case RISCVVector::BI__builtin_rvv_vsoxseg3ei16_v:
case RISCVVector::BI__builtin_rvv_vsoxseg3ei32_v:
case RISCVVector::BI__builtin_rvv_vsoxseg3ei64_v:
  ID = Intrinsic::riscv_vsoxseg3;
  NF = 3;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsoxseg3ei8_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg3ei16_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg3ei32_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg3ei64_v_m:
  ID = Intrinsic::riscv_vsoxseg3_mask;
  NF = 3;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 3]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsoxseg4ei8_v:
case RISCVVector::BI__builtin_rvv_vsoxseg4ei16_v:
case RISCVVector::BI__builtin_rvv_vsoxseg4ei32_v:
case RISCVVector::BI__builtin_rvv_vsoxseg4ei64_v:
  ID = Intrinsic::riscv_vsoxseg4;
  NF = 4;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsoxseg4ei8_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg4ei16_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg4ei32_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg4ei64_v_m:
  ID = Intrinsic::riscv_vsoxseg4_mask;
  NF = 4;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 3]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsoxseg5ei8_v:
case RISCVVector::BI__builtin_rvv_vsoxseg5ei16_v:
case RISCVVector::BI__builtin_rvv_vsoxseg5ei32_v:
case RISCVVector::BI__builtin_rvv_vsoxseg5ei64_v:
  ID = Intrinsic::riscv_vsoxseg5;
  NF = 5;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsoxseg5ei8_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg5ei16_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg5ei32_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg5ei64_v_m:
  ID = Intrinsic::riscv_vsoxseg5_mask;
  NF = 5;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 3]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsoxseg6ei8_v:
case RISCVVector::BI__builtin_rvv_vsoxseg6ei16_v:
case RISCVVector::BI__builtin_rvv_vsoxseg6ei32_v:
case RISCVVector::BI__builtin_rvv_vsoxseg6ei64_v:
  ID = Intrinsic::riscv_vsoxseg6;
  NF = 6;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsoxseg6ei8_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg6ei16_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg6ei32_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg6ei64_v_m:
  ID = Intrinsic::riscv_vsoxseg6_mask;
  NF = 6;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 3]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsoxseg7ei8_v:
case RISCVVector::BI__builtin_rvv_vsoxseg7ei16_v:
case RISCVVector::BI__builtin_rvv_vsoxseg7ei32_v:
case RISCVVector::BI__builtin_rvv_vsoxseg7ei64_v:
  ID = Intrinsic::riscv_vsoxseg7;
  NF = 7;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsoxseg7ei8_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg7ei16_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg7ei32_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg7ei64_v_m:
  ID = Intrinsic::riscv_vsoxseg7_mask;
  NF = 7;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 3]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsoxseg8ei8_v:
case RISCVVector::BI__builtin_rvv_vsoxseg8ei16_v:
case RISCVVector::BI__builtin_rvv_vsoxseg8ei32_v:
case RISCVVector::BI__builtin_rvv_vsoxseg8ei64_v:
  ID = Intrinsic::riscv_vsoxseg8;
  NF = 8;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsoxseg8ei8_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg8ei16_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg8ei32_v_m:
case RISCVVector::BI__builtin_rvv_vsoxseg8ei64_v_m:
  ID = Intrinsic::riscv_vsoxseg8_mask;
  NF = 8;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 3]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsra_vv_tu:
case RISCVVector::BI__builtin_rvv_vsra_vx_tu:
  ID = Intrinsic::riscv_vsra;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsra_vv_ta:
case RISCVVector::BI__builtin_rvv_vsra_vx_ta:
  ID = Intrinsic::riscv_vsra;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsra_vv_tuma:
case RISCVVector::BI__builtin_rvv_vsra_vx_tuma:
  ID = Intrinsic::riscv_vsra_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsra_vv_tama:
case RISCVVector::BI__builtin_rvv_vsra_vx_tama:
  ID = Intrinsic::riscv_vsra_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsra_vv_tumu:
case RISCVVector::BI__builtin_rvv_vsra_vx_tumu:
  ID = Intrinsic::riscv_vsra_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsra_vv_tamu:
case RISCVVector::BI__builtin_rvv_vsra_vx_tamu:
  ID = Intrinsic::riscv_vsra_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsrl_vv_tu:
case RISCVVector::BI__builtin_rvv_vsrl_vx_tu:
  ID = Intrinsic::riscv_vsrl;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsrl_vv_ta:
case RISCVVector::BI__builtin_rvv_vsrl_vx_ta:
  ID = Intrinsic::riscv_vsrl;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsrl_vv_tuma:
case RISCVVector::BI__builtin_rvv_vsrl_vx_tuma:
  ID = Intrinsic::riscv_vsrl_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsrl_vv_tama:
case RISCVVector::BI__builtin_rvv_vsrl_vx_tama:
  ID = Intrinsic::riscv_vsrl_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsrl_vv_tumu:
case RISCVVector::BI__builtin_rvv_vsrl_vx_tumu:
  ID = Intrinsic::riscv_vsrl_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsrl_vv_tamu:
case RISCVVector::BI__builtin_rvv_vsrl_vx_tamu:
  ID = Intrinsic::riscv_vsrl_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsse16_v:
case RISCVVector::BI__builtin_rvv_vsse32_v:
case RISCVVector::BI__builtin_rvv_vsse64_v:
case RISCVVector::BI__builtin_rvv_vsse8_v:
  ID = Intrinsic::riscv_vsse;
  DefaultPolicy = 1;

        // Builtin: (ptr, stride, value, vl). Intrinsic: (value, ptr, stride, vl)
        std::rotate(Ops.begin(), Ops.begin() + 2, Ops.begin() + 3);
        Ops[1] = Builder.CreateBitCast(Ops[1], Ops[0]->getType()->getPointerTo());
        IntrinsicTypes = {Ops[0]->getType(), Ops[3]->getType()};
      break;
case RISCVVector::BI__builtin_rvv_vsse16_v_m:
case RISCVVector::BI__builtin_rvv_vsse32_v_m:
case RISCVVector::BI__builtin_rvv_vsse64_v_m:
case RISCVVector::BI__builtin_rvv_vsse8_v_m:
  ID = Intrinsic::riscv_vsse_mask;
  DefaultPolicy = 0;

        // Builtin: (mask, ptr, stride, value, vl). Intrinsic: (value, ptr, stride, mask, vl)
        std::swap(Ops[0], Ops[3]);
        Ops[1] = Builder.CreateBitCast(Ops[1], Ops[0]->getType()->getPointerTo());
        IntrinsicTypes = {Ops[0]->getType(), Ops[4]->getType()};
      break;
case RISCVVector::BI__builtin_rvv_vsseg2e16_v:
case RISCVVector::BI__builtin_rvv_vsseg2e32_v:
case RISCVVector::BI__builtin_rvv_vsseg2e64_v:
case RISCVVector::BI__builtin_rvv_vsseg2e8_v:
  ID = Intrinsic::riscv_vsseg2;
  NF = 2;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, vl)
      std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 2);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsseg2e16_v_m:
case RISCVVector::BI__builtin_rvv_vsseg2e32_v_m:
case RISCVVector::BI__builtin_rvv_vsseg2e64_v_m:
case RISCVVector::BI__builtin_rvv_vsseg2e8_v_m:
  ID = Intrinsic::riscv_vsseg2_mask;
  NF = 2;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      std::swap(Ops[NF], Ops[NF + 1]);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsseg3e8_v:
case RISCVVector::BI__builtin_rvv_vsseg3e16_v:
case RISCVVector::BI__builtin_rvv_vsseg3e32_v:
case RISCVVector::BI__builtin_rvv_vsseg3e64_v:
  ID = Intrinsic::riscv_vsseg3;
  NF = 3;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, vl)
      std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 2);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsseg3e8_v_m:
case RISCVVector::BI__builtin_rvv_vsseg3e16_v_m:
case RISCVVector::BI__builtin_rvv_vsseg3e32_v_m:
case RISCVVector::BI__builtin_rvv_vsseg3e64_v_m:
  ID = Intrinsic::riscv_vsseg3_mask;
  NF = 3;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      std::swap(Ops[NF], Ops[NF + 1]);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsseg4e8_v:
case RISCVVector::BI__builtin_rvv_vsseg4e16_v:
case RISCVVector::BI__builtin_rvv_vsseg4e32_v:
case RISCVVector::BI__builtin_rvv_vsseg4e64_v:
  ID = Intrinsic::riscv_vsseg4;
  NF = 4;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, vl)
      std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 2);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsseg4e8_v_m:
case RISCVVector::BI__builtin_rvv_vsseg4e16_v_m:
case RISCVVector::BI__builtin_rvv_vsseg4e32_v_m:
case RISCVVector::BI__builtin_rvv_vsseg4e64_v_m:
  ID = Intrinsic::riscv_vsseg4_mask;
  NF = 4;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      std::swap(Ops[NF], Ops[NF + 1]);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsseg5e8_v:
case RISCVVector::BI__builtin_rvv_vsseg5e16_v:
case RISCVVector::BI__builtin_rvv_vsseg5e32_v:
case RISCVVector::BI__builtin_rvv_vsseg5e64_v:
  ID = Intrinsic::riscv_vsseg5;
  NF = 5;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, vl)
      std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 2);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsseg5e8_v_m:
case RISCVVector::BI__builtin_rvv_vsseg5e16_v_m:
case RISCVVector::BI__builtin_rvv_vsseg5e32_v_m:
case RISCVVector::BI__builtin_rvv_vsseg5e64_v_m:
  ID = Intrinsic::riscv_vsseg5_mask;
  NF = 5;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      std::swap(Ops[NF], Ops[NF + 1]);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsseg6e8_v:
case RISCVVector::BI__builtin_rvv_vsseg6e16_v:
case RISCVVector::BI__builtin_rvv_vsseg6e32_v:
case RISCVVector::BI__builtin_rvv_vsseg6e64_v:
  ID = Intrinsic::riscv_vsseg6;
  NF = 6;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, vl)
      std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 2);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsseg6e8_v_m:
case RISCVVector::BI__builtin_rvv_vsseg6e16_v_m:
case RISCVVector::BI__builtin_rvv_vsseg6e32_v_m:
case RISCVVector::BI__builtin_rvv_vsseg6e64_v_m:
  ID = Intrinsic::riscv_vsseg6_mask;
  NF = 6;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      std::swap(Ops[NF], Ops[NF + 1]);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsseg7e8_v:
case RISCVVector::BI__builtin_rvv_vsseg7e16_v:
case RISCVVector::BI__builtin_rvv_vsseg7e32_v:
case RISCVVector::BI__builtin_rvv_vsseg7e64_v:
  ID = Intrinsic::riscv_vsseg7;
  NF = 7;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, vl)
      std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 2);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsseg7e8_v_m:
case RISCVVector::BI__builtin_rvv_vsseg7e16_v_m:
case RISCVVector::BI__builtin_rvv_vsseg7e32_v_m:
case RISCVVector::BI__builtin_rvv_vsseg7e64_v_m:
  ID = Intrinsic::riscv_vsseg7_mask;
  NF = 7;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      std::swap(Ops[NF], Ops[NF + 1]);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsseg8e8_v:
case RISCVVector::BI__builtin_rvv_vsseg8e16_v:
case RISCVVector::BI__builtin_rvv_vsseg8e32_v:
case RISCVVector::BI__builtin_rvv_vsseg8e64_v:
  ID = Intrinsic::riscv_vsseg8;
  NF = 8;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, vl)
      std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 2);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsseg8e8_v_m:
case RISCVVector::BI__builtin_rvv_vsseg8e16_v_m:
case RISCVVector::BI__builtin_rvv_vsseg8e32_v_m:
case RISCVVector::BI__builtin_rvv_vsseg8e64_v_m:
  ID = Intrinsic::riscv_vsseg8_mask;
  NF = 8;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      std::swap(Ops[NF], Ops[NF + 1]);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssra_vv_tu:
case RISCVVector::BI__builtin_rvv_vssra_vx_tu:
  ID = Intrinsic::riscv_vssra;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssra_vv_ta:
case RISCVVector::BI__builtin_rvv_vssra_vx_ta:
  ID = Intrinsic::riscv_vssra;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssra_vv_tuma:
case RISCVVector::BI__builtin_rvv_vssra_vx_tuma:
  ID = Intrinsic::riscv_vssra_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssra_vv_tama:
case RISCVVector::BI__builtin_rvv_vssra_vx_tama:
  ID = Intrinsic::riscv_vssra_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssra_vv_tumu:
case RISCVVector::BI__builtin_rvv_vssra_vx_tumu:
  ID = Intrinsic::riscv_vssra_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssra_vv_tamu:
case RISCVVector::BI__builtin_rvv_vssra_vx_tamu:
  ID = Intrinsic::riscv_vssra_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssrl_vv_tu:
case RISCVVector::BI__builtin_rvv_vssrl_vx_tu:
  ID = Intrinsic::riscv_vssrl;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssrl_vv_ta:
case RISCVVector::BI__builtin_rvv_vssrl_vx_ta:
  ID = Intrinsic::riscv_vssrl;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssrl_vv_tuma:
case RISCVVector::BI__builtin_rvv_vssrl_vx_tuma:
  ID = Intrinsic::riscv_vssrl_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssrl_vv_tama:
case RISCVVector::BI__builtin_rvv_vssrl_vx_tama:
  ID = Intrinsic::riscv_vssrl_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssrl_vv_tumu:
case RISCVVector::BI__builtin_rvv_vssrl_vx_tumu:
  ID = Intrinsic::riscv_vssrl_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssrl_vv_tamu:
case RISCVVector::BI__builtin_rvv_vssrl_vx_tamu:
  ID = Intrinsic::riscv_vssrl_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssseg2e16_v:
case RISCVVector::BI__builtin_rvv_vssseg2e32_v:
case RISCVVector::BI__builtin_rvv_vssseg2e64_v:
case RISCVVector::BI__builtin_rvv_vssseg2e8_v:
  ID = Intrinsic::riscv_vssseg2;
  NF = 2;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, stride, val0, val1, ..., vl).
      // Intrinsic: (val0, val1, ..., ptr, stride, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssseg2e16_v_m:
case RISCVVector::BI__builtin_rvv_vssseg2e32_v_m:
case RISCVVector::BI__builtin_rvv_vssseg2e64_v_m:
case RISCVVector::BI__builtin_rvv_vssseg2e8_v_m:
  ID = Intrinsic::riscv_vssseg2_mask;
  NF = 2;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, stride, val0, val1, ..., vl).
      // Intrinsic: (val0, val1, ..., ptr, stride, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssseg3e8_v:
case RISCVVector::BI__builtin_rvv_vssseg3e16_v:
case RISCVVector::BI__builtin_rvv_vssseg3e32_v:
case RISCVVector::BI__builtin_rvv_vssseg3e64_v:
  ID = Intrinsic::riscv_vssseg3;
  NF = 3;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, stride, val0, val1, ..., vl).
      // Intrinsic: (val0, val1, ..., ptr, stride, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssseg3e8_v_m:
case RISCVVector::BI__builtin_rvv_vssseg3e16_v_m:
case RISCVVector::BI__builtin_rvv_vssseg3e32_v_m:
case RISCVVector::BI__builtin_rvv_vssseg3e64_v_m:
  ID = Intrinsic::riscv_vssseg3_mask;
  NF = 3;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, stride, val0, val1, ..., vl).
      // Intrinsic: (val0, val1, ..., ptr, stride, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssseg4e8_v:
case RISCVVector::BI__builtin_rvv_vssseg4e16_v:
case RISCVVector::BI__builtin_rvv_vssseg4e32_v:
case RISCVVector::BI__builtin_rvv_vssseg4e64_v:
  ID = Intrinsic::riscv_vssseg4;
  NF = 4;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, stride, val0, val1, ..., vl).
      // Intrinsic: (val0, val1, ..., ptr, stride, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssseg4e8_v_m:
case RISCVVector::BI__builtin_rvv_vssseg4e16_v_m:
case RISCVVector::BI__builtin_rvv_vssseg4e32_v_m:
case RISCVVector::BI__builtin_rvv_vssseg4e64_v_m:
  ID = Intrinsic::riscv_vssseg4_mask;
  NF = 4;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, stride, val0, val1, ..., vl).
      // Intrinsic: (val0, val1, ..., ptr, stride, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssseg5e8_v:
case RISCVVector::BI__builtin_rvv_vssseg5e16_v:
case RISCVVector::BI__builtin_rvv_vssseg5e32_v:
case RISCVVector::BI__builtin_rvv_vssseg5e64_v:
  ID = Intrinsic::riscv_vssseg5;
  NF = 5;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, stride, val0, val1, ..., vl).
      // Intrinsic: (val0, val1, ..., ptr, stride, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssseg5e8_v_m:
case RISCVVector::BI__builtin_rvv_vssseg5e16_v_m:
case RISCVVector::BI__builtin_rvv_vssseg5e32_v_m:
case RISCVVector::BI__builtin_rvv_vssseg5e64_v_m:
  ID = Intrinsic::riscv_vssseg5_mask;
  NF = 5;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, stride, val0, val1, ..., vl).
      // Intrinsic: (val0, val1, ..., ptr, stride, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssseg6e8_v:
case RISCVVector::BI__builtin_rvv_vssseg6e16_v:
case RISCVVector::BI__builtin_rvv_vssseg6e32_v:
case RISCVVector::BI__builtin_rvv_vssseg6e64_v:
  ID = Intrinsic::riscv_vssseg6;
  NF = 6;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, stride, val0, val1, ..., vl).
      // Intrinsic: (val0, val1, ..., ptr, stride, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssseg6e8_v_m:
case RISCVVector::BI__builtin_rvv_vssseg6e16_v_m:
case RISCVVector::BI__builtin_rvv_vssseg6e32_v_m:
case RISCVVector::BI__builtin_rvv_vssseg6e64_v_m:
  ID = Intrinsic::riscv_vssseg6_mask;
  NF = 6;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, stride, val0, val1, ..., vl).
      // Intrinsic: (val0, val1, ..., ptr, stride, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssseg7e8_v:
case RISCVVector::BI__builtin_rvv_vssseg7e16_v:
case RISCVVector::BI__builtin_rvv_vssseg7e32_v:
case RISCVVector::BI__builtin_rvv_vssseg7e64_v:
  ID = Intrinsic::riscv_vssseg7;
  NF = 7;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, stride, val0, val1, ..., vl).
      // Intrinsic: (val0, val1, ..., ptr, stride, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssseg7e8_v_m:
case RISCVVector::BI__builtin_rvv_vssseg7e16_v_m:
case RISCVVector::BI__builtin_rvv_vssseg7e32_v_m:
case RISCVVector::BI__builtin_rvv_vssseg7e64_v_m:
  ID = Intrinsic::riscv_vssseg7_mask;
  NF = 7;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, stride, val0, val1, ..., vl).
      // Intrinsic: (val0, val1, ..., ptr, stride, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssseg8e8_v:
case RISCVVector::BI__builtin_rvv_vssseg8e16_v:
case RISCVVector::BI__builtin_rvv_vssseg8e32_v:
case RISCVVector::BI__builtin_rvv_vssseg8e64_v:
  ID = Intrinsic::riscv_vssseg8;
  NF = 8;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, stride, val0, val1, ..., vl).
      // Intrinsic: (val0, val1, ..., ptr, stride, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssseg8e8_v_m:
case RISCVVector::BI__builtin_rvv_vssseg8e16_v_m:
case RISCVVector::BI__builtin_rvv_vssseg8e32_v_m:
case RISCVVector::BI__builtin_rvv_vssseg8e64_v_m:
  ID = Intrinsic::riscv_vssseg8_mask;
  NF = 8;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, stride, val0, val1, ..., vl).
      // Intrinsic: (val0, val1, ..., ptr, stride, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(), Ops[NF + 1]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vssub_vv_tu:
case RISCVVector::BI__builtin_rvv_vssub_vx_tu:
  ID = Intrinsic::riscv_vssub;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssub_vv_ta:
case RISCVVector::BI__builtin_rvv_vssub_vx_ta:
  ID = Intrinsic::riscv_vssub;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssub_vv_tuma:
case RISCVVector::BI__builtin_rvv_vssub_vx_tuma:
  ID = Intrinsic::riscv_vssub_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssub_vv_tama:
case RISCVVector::BI__builtin_rvv_vssub_vx_tama:
  ID = Intrinsic::riscv_vssub_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssub_vv_tumu:
case RISCVVector::BI__builtin_rvv_vssub_vx_tumu:
  ID = Intrinsic::riscv_vssub_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssub_vv_tamu:
case RISCVVector::BI__builtin_rvv_vssub_vx_tamu:
  ID = Intrinsic::riscv_vssub_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssubu_vv_tu:
case RISCVVector::BI__builtin_rvv_vssubu_vx_tu:
  ID = Intrinsic::riscv_vssubu;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssubu_vv_ta:
case RISCVVector::BI__builtin_rvv_vssubu_vx_ta:
  ID = Intrinsic::riscv_vssubu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssubu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vssubu_vx_tuma:
  ID = Intrinsic::riscv_vssubu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssubu_vv_tama:
case RISCVVector::BI__builtin_rvv_vssubu_vx_tama:
  ID = Intrinsic::riscv_vssubu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssubu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vssubu_vx_tumu:
  ID = Intrinsic::riscv_vssubu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vssubu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vssubu_vx_tamu:
  ID = Intrinsic::riscv_vssubu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsub_vv_tu:
case RISCVVector::BI__builtin_rvv_vsub_vx_tu:
  ID = Intrinsic::riscv_vsub;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsub_vv_ta:
case RISCVVector::BI__builtin_rvv_vsub_vx_ta:
  ID = Intrinsic::riscv_vsub;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsub_vv_tuma:
case RISCVVector::BI__builtin_rvv_vsub_vx_tuma:
  ID = Intrinsic::riscv_vsub_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsub_vv_tama:
case RISCVVector::BI__builtin_rvv_vsub_vx_tama:
  ID = Intrinsic::riscv_vsub_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsub_vv_tumu:
case RISCVVector::BI__builtin_rvv_vsub_vx_tumu:
  ID = Intrinsic::riscv_vsub_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsub_vv_tamu:
case RISCVVector::BI__builtin_rvv_vsub_vx_tamu:
  ID = Intrinsic::riscv_vsub_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vsuxei16_v:
case RISCVVector::BI__builtin_rvv_vsuxei32_v:
case RISCVVector::BI__builtin_rvv_vsuxei8_v:
case RISCVVector::BI__builtin_rvv_vsuxei64_v:
  ID = Intrinsic::riscv_vsuxei;
  DefaultPolicy = 1;

        // Builtin: (ptr, index, value, vl). Intrinsic: (value, ptr, index, vl)
        std::rotate(Ops.begin(), Ops.begin() + 2, Ops.begin() + 3);
        Ops[1] = Builder.CreateBitCast(Ops[1],Ops[0]->getType()->getPointerTo());
        IntrinsicTypes = {Ops[0]->getType(), Ops[2]->getType(), Ops[3]->getType()};
      break;
case RISCVVector::BI__builtin_rvv_vsuxei16_v_m:
case RISCVVector::BI__builtin_rvv_vsuxei32_v_m:
case RISCVVector::BI__builtin_rvv_vsuxei8_v_m:
case RISCVVector::BI__builtin_rvv_vsuxei64_v_m:
  ID = Intrinsic::riscv_vsuxei_mask;
  DefaultPolicy = 0;

        // Builtin: (mask, ptr, index, value, vl). Intrinsic: (value, ptr, index, mask, vl)
        std::swap(Ops[0], Ops[3]);
        Ops[1] = Builder.CreateBitCast(Ops[1], Ops[0]->getType()->getPointerTo());
        IntrinsicTypes = {Ops[0]->getType(), Ops[2]->getType(), Ops[4]->getType()};
      break;
case RISCVVector::BI__builtin_rvv_vsuxseg2ei16_v:
case RISCVVector::BI__builtin_rvv_vsuxseg2ei32_v:
case RISCVVector::BI__builtin_rvv_vsuxseg2ei64_v:
case RISCVVector::BI__builtin_rvv_vsuxseg2ei8_v:
  ID = Intrinsic::riscv_vsuxseg2;
  NF = 2;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsuxseg2ei16_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg2ei32_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg2ei64_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg2ei8_v_m:
  ID = Intrinsic::riscv_vsuxseg2_mask;
  NF = 2;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 3]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsuxseg3ei8_v:
case RISCVVector::BI__builtin_rvv_vsuxseg3ei16_v:
case RISCVVector::BI__builtin_rvv_vsuxseg3ei32_v:
case RISCVVector::BI__builtin_rvv_vsuxseg3ei64_v:
  ID = Intrinsic::riscv_vsuxseg3;
  NF = 3;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsuxseg3ei8_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg3ei16_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg3ei32_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg3ei64_v_m:
  ID = Intrinsic::riscv_vsuxseg3_mask;
  NF = 3;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 3]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsuxseg4ei8_v:
case RISCVVector::BI__builtin_rvv_vsuxseg4ei16_v:
case RISCVVector::BI__builtin_rvv_vsuxseg4ei32_v:
case RISCVVector::BI__builtin_rvv_vsuxseg4ei64_v:
  ID = Intrinsic::riscv_vsuxseg4;
  NF = 4;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsuxseg4ei8_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg4ei16_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg4ei32_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg4ei64_v_m:
  ID = Intrinsic::riscv_vsuxseg4_mask;
  NF = 4;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 3]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsuxseg5ei8_v:
case RISCVVector::BI__builtin_rvv_vsuxseg5ei16_v:
case RISCVVector::BI__builtin_rvv_vsuxseg5ei32_v:
case RISCVVector::BI__builtin_rvv_vsuxseg5ei64_v:
  ID = Intrinsic::riscv_vsuxseg5;
  NF = 5;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsuxseg5ei8_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg5ei16_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg5ei32_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg5ei64_v_m:
  ID = Intrinsic::riscv_vsuxseg5_mask;
  NF = 5;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 3]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsuxseg6ei8_v:
case RISCVVector::BI__builtin_rvv_vsuxseg6ei16_v:
case RISCVVector::BI__builtin_rvv_vsuxseg6ei32_v:
case RISCVVector::BI__builtin_rvv_vsuxseg6ei64_v:
  ID = Intrinsic::riscv_vsuxseg6;
  NF = 6;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsuxseg6ei8_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg6ei16_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg6ei32_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg6ei64_v_m:
  ID = Intrinsic::riscv_vsuxseg6_mask;
  NF = 6;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 3]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsuxseg7ei8_v:
case RISCVVector::BI__builtin_rvv_vsuxseg7ei16_v:
case RISCVVector::BI__builtin_rvv_vsuxseg7ei32_v:
case RISCVVector::BI__builtin_rvv_vsuxseg7ei64_v:
  ID = Intrinsic::riscv_vsuxseg7;
  NF = 7;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsuxseg7ei8_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg7ei16_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg7ei32_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg7ei64_v_m:
  ID = Intrinsic::riscv_vsuxseg7_mask;
  NF = 7;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 3]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsuxseg8ei8_v:
case RISCVVector::BI__builtin_rvv_vsuxseg8ei16_v:
case RISCVVector::BI__builtin_rvv_vsuxseg8ei32_v:
case RISCVVector::BI__builtin_rvv_vsuxseg8ei64_v:
  ID = Intrinsic::riscv_vsuxseg8;
  NF = 8;
  DefaultPolicy = 1;

    {
      // Builtin: (ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, vl)
      std::rotate(Ops.begin(), Ops.begin() + 2, Ops.end() - 1);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 2]->getType()};
      assert(Ops.size() == NF + 3);
    }
            break;
case RISCVVector::BI__builtin_rvv_vsuxseg8ei8_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg8ei16_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg8ei32_v_m:
case RISCVVector::BI__builtin_rvv_vsuxseg8ei64_v_m:
  ID = Intrinsic::riscv_vsuxseg8_mask;
  NF = 8;
  DefaultPolicy = 0;

    {
      // Builtin: (mask, ptr, index, val0, val1, ..., vl)
      // Intrinsic: (val0, val1, ..., ptr, index, mask, vl)
      std::rotate(Ops.begin(), Ops.begin() + 3, Ops.end() - 1);
      std::rotate(Ops.begin() + NF, Ops.begin() + NF + 1, Ops.begin() + NF + 3);
      IntrinsicTypes = {Ops[0]->getType(),
                        Ops[NF + 1]->getType(), Ops[NF + 3]->getType()};
      assert(Ops.size() == NF + 4);
    }
            break;
case RISCVVector::BI__builtin_rvv_vwadd_vv_tu:
case RISCVVector::BI__builtin_rvv_vwadd_vx_tu:
  ID = Intrinsic::riscv_vwadd;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwcvt_x_x_v_tu:
  ID = Intrinsic::riscv_vwadd;
  DefaultPolicy = 0;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        // passtru, op1, op2, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          ElemTy,
                          Ops[3]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vwadd_vv_ta:
case RISCVVector::BI__builtin_rvv_vwadd_vx_ta:
  ID = Intrinsic::riscv_vwadd;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwcvt_x_x_v_ta:
  ID = Intrinsic::riscv_vwadd;
  DefaultPolicy = 1;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        // passtru, op1, op2, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          ElemTy,
                          Ops[3]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vwadd_vv_tuma:
case RISCVVector::BI__builtin_rvv_vwadd_vx_tuma:
  ID = Intrinsic::riscv_vwadd_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwcvt_x_x_v_tuma:
  ID = Intrinsic::riscv_vwadd_mask;
  DefaultPolicy = 2;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl, policy
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          ElemTy,
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vwadd_vv_tama:
case RISCVVector::BI__builtin_rvv_vwadd_vx_tama:
  ID = Intrinsic::riscv_vwadd_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwcvt_x_x_v_tama:
  ID = Intrinsic::riscv_vwadd_mask;
  DefaultPolicy = 3;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl, policy
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          ElemTy,
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vwadd_vv_tumu:
case RISCVVector::BI__builtin_rvv_vwadd_vx_tumu:
  ID = Intrinsic::riscv_vwadd_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwcvt_x_x_v_tumu:
  ID = Intrinsic::riscv_vwadd_mask;
  DefaultPolicy = 0;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl, policy
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          ElemTy,
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vwadd_vv_tamu:
case RISCVVector::BI__builtin_rvv_vwadd_vx_tamu:
  ID = Intrinsic::riscv_vwadd_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwcvt_x_x_v_tamu:
  ID = Intrinsic::riscv_vwadd_mask;
  DefaultPolicy = 1;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl, policy
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          ElemTy,
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vwadd_wv_tu:
case RISCVVector::BI__builtin_rvv_vwadd_wx_tu:
  ID = Intrinsic::riscv_vwadd_w;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwadd_wv_ta:
case RISCVVector::BI__builtin_rvv_vwadd_wx_ta:
  ID = Intrinsic::riscv_vwadd_w;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwadd_wv_tuma:
case RISCVVector::BI__builtin_rvv_vwadd_wx_tuma:
  ID = Intrinsic::riscv_vwadd_w_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwadd_wv_tama:
case RISCVVector::BI__builtin_rvv_vwadd_wx_tama:
  ID = Intrinsic::riscv_vwadd_w_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwadd_wv_tumu:
case RISCVVector::BI__builtin_rvv_vwadd_wx_tumu:
  ID = Intrinsic::riscv_vwadd_w_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwadd_wv_tamu:
case RISCVVector::BI__builtin_rvv_vwadd_wx_tamu:
  ID = Intrinsic::riscv_vwadd_w_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwaddu_vv_tu:
case RISCVVector::BI__builtin_rvv_vwaddu_vx_tu:
  ID = Intrinsic::riscv_vwaddu;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwcvtu_x_x_v_tu:
  ID = Intrinsic::riscv_vwaddu;
  DefaultPolicy = 0;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        // passtru, op1, op2, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          ElemTy,
                          Ops[3]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vwaddu_vv_ta:
case RISCVVector::BI__builtin_rvv_vwaddu_vx_ta:
  ID = Intrinsic::riscv_vwaddu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwcvtu_x_x_v_ta:
  ID = Intrinsic::riscv_vwaddu;
  DefaultPolicy = 1;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        // passtru, op1, op2, vl
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          ElemTy,
                          Ops[3]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vwaddu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vwaddu_vx_tuma:
  ID = Intrinsic::riscv_vwaddu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwcvtu_x_x_v_tuma:
  ID = Intrinsic::riscv_vwaddu_mask;
  DefaultPolicy = 2;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl, policy
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          ElemTy,
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vwaddu_vv_tama:
case RISCVVector::BI__builtin_rvv_vwaddu_vx_tama:
  ID = Intrinsic::riscv_vwaddu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwcvtu_x_x_v_tama:
  ID = Intrinsic::riscv_vwaddu_mask;
  DefaultPolicy = 3;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl, policy
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          ElemTy,
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vwaddu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vwaddu_vx_tumu:
  ID = Intrinsic::riscv_vwaddu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwcvtu_x_x_v_tumu:
  ID = Intrinsic::riscv_vwaddu_mask;
  DefaultPolicy = 0;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl, policy
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          ElemTy,
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vwaddu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vwaddu_vx_tamu:
  ID = Intrinsic::riscv_vwaddu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwcvtu_x_x_v_tamu:
  ID = Intrinsic::riscv_vwaddu_mask;
  DefaultPolicy = 1;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2, llvm::Constant::getNullValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, op2, mask, vl, policy
        IntrinsicTypes = {ResultType,
                          Ops[1]->getType(),
                          ElemTy,
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vwaddu_wx_tu:
case RISCVVector::BI__builtin_rvv_vwaddu_wv_tu:
  ID = Intrinsic::riscv_vwaddu_w;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwaddu_wx_ta:
case RISCVVector::BI__builtin_rvv_vwaddu_wv_ta:
  ID = Intrinsic::riscv_vwaddu_w;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwaddu_wx_tuma:
case RISCVVector::BI__builtin_rvv_vwaddu_wv_tuma:
  ID = Intrinsic::riscv_vwaddu_w_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwaddu_wx_tama:
case RISCVVector::BI__builtin_rvv_vwaddu_wv_tama:
  ID = Intrinsic::riscv_vwaddu_w_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwaddu_wx_tumu:
case RISCVVector::BI__builtin_rvv_vwaddu_wv_tumu:
  ID = Intrinsic::riscv_vwaddu_w_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwaddu_wx_tamu:
case RISCVVector::BI__builtin_rvv_vwaddu_wv_tamu:
  ID = Intrinsic::riscv_vwaddu_w_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmacc_vv_tu:
case RISCVVector::BI__builtin_rvv_vwmacc_vx_tu:
  ID = Intrinsic::riscv_vwmacc;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmacc_vv_ta:
case RISCVVector::BI__builtin_rvv_vwmacc_vx_ta:
  ID = Intrinsic::riscv_vwmacc;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmacc_vv_tuma:
case RISCVVector::BI__builtin_rvv_vwmacc_vx_tuma:
  ID = Intrinsic::riscv_vwmacc_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmacc_vv_tama:
case RISCVVector::BI__builtin_rvv_vwmacc_vx_tama:
  ID = Intrinsic::riscv_vwmacc_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmacc_vv_tumu:
case RISCVVector::BI__builtin_rvv_vwmacc_vx_tumu:
  ID = Intrinsic::riscv_vwmacc_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmacc_vv_tamu:
case RISCVVector::BI__builtin_rvv_vwmacc_vx_tamu:
  ID = Intrinsic::riscv_vwmacc_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccsu_vv_tu:
case RISCVVector::BI__builtin_rvv_vwmaccsu_vx_tu:
  ID = Intrinsic::riscv_vwmaccsu;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccsu_vv_ta:
case RISCVVector::BI__builtin_rvv_vwmaccsu_vx_ta:
  ID = Intrinsic::riscv_vwmaccsu;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccsu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vwmaccsu_vx_tuma:
  ID = Intrinsic::riscv_vwmaccsu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccsu_vv_tama:
case RISCVVector::BI__builtin_rvv_vwmaccsu_vx_tama:
  ID = Intrinsic::riscv_vwmaccsu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccsu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vwmaccsu_vx_tumu:
  ID = Intrinsic::riscv_vwmaccsu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccsu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vwmaccsu_vx_tamu:
  ID = Intrinsic::riscv_vwmaccsu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccu_vv_tu:
case RISCVVector::BI__builtin_rvv_vwmaccu_vx_tu:
  ID = Intrinsic::riscv_vwmaccu;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccu_vv_ta:
case RISCVVector::BI__builtin_rvv_vwmaccu_vx_ta:
  ID = Intrinsic::riscv_vwmaccu;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vwmaccu_vx_tuma:
  ID = Intrinsic::riscv_vwmaccu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccu_vv_tama:
case RISCVVector::BI__builtin_rvv_vwmaccu_vx_tama:
  ID = Intrinsic::riscv_vwmaccu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vwmaccu_vx_tumu:
  ID = Intrinsic::riscv_vwmaccu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vwmaccu_vx_tamu:
  ID = Intrinsic::riscv_vwmaccu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccus_vx_tu:
  ID = Intrinsic::riscv_vwmaccus;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccus_vx_ta:
  ID = Intrinsic::riscv_vwmaccus;
  DefaultPolicy = 1;
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccus_vx_tuma:
  ID = Intrinsic::riscv_vwmaccus_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccus_vx_tama:
  ID = Intrinsic::riscv_vwmaccus_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccus_vx_tumu:
  ID = Intrinsic::riscv_vwmaccus_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmaccus_vx_tamu:
  ID = Intrinsic::riscv_vwmaccus_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmul_vv_tu:
case RISCVVector::BI__builtin_rvv_vwmul_vx_tu:
  ID = Intrinsic::riscv_vwmul;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmul_vv_ta:
case RISCVVector::BI__builtin_rvv_vwmul_vx_ta:
  ID = Intrinsic::riscv_vwmul;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmul_vv_tuma:
case RISCVVector::BI__builtin_rvv_vwmul_vx_tuma:
  ID = Intrinsic::riscv_vwmul_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmul_vv_tama:
case RISCVVector::BI__builtin_rvv_vwmul_vx_tama:
  ID = Intrinsic::riscv_vwmul_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmul_vv_tumu:
case RISCVVector::BI__builtin_rvv_vwmul_vx_tumu:
  ID = Intrinsic::riscv_vwmul_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmul_vv_tamu:
case RISCVVector::BI__builtin_rvv_vwmul_vx_tamu:
  ID = Intrinsic::riscv_vwmul_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmulsu_vv_tu:
case RISCVVector::BI__builtin_rvv_vwmulsu_vx_tu:
  ID = Intrinsic::riscv_vwmulsu;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmulsu_vv_ta:
case RISCVVector::BI__builtin_rvv_vwmulsu_vx_ta:
  ID = Intrinsic::riscv_vwmulsu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmulsu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vwmulsu_vx_tuma:
  ID = Intrinsic::riscv_vwmulsu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmulsu_vv_tama:
case RISCVVector::BI__builtin_rvv_vwmulsu_vx_tama:
  ID = Intrinsic::riscv_vwmulsu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmulsu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vwmulsu_vx_tumu:
  ID = Intrinsic::riscv_vwmulsu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmulsu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vwmulsu_vx_tamu:
  ID = Intrinsic::riscv_vwmulsu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmulu_vv_tu:
case RISCVVector::BI__builtin_rvv_vwmulu_vx_tu:
  ID = Intrinsic::riscv_vwmulu;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmulu_vv_ta:
case RISCVVector::BI__builtin_rvv_vwmulu_vx_ta:
  ID = Intrinsic::riscv_vwmulu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmulu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vwmulu_vx_tuma:
  ID = Intrinsic::riscv_vwmulu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmulu_vv_tama:
case RISCVVector::BI__builtin_rvv_vwmulu_vx_tama:
  ID = Intrinsic::riscv_vwmulu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmulu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vwmulu_vx_tumu:
  ID = Intrinsic::riscv_vwmulu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwmulu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vwmulu_vx_tamu:
  ID = Intrinsic::riscv_vwmulu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwredsum_vs_tu:
  ID = Intrinsic::riscv_vwredsum;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwredsum_vs_ta:
  ID = Intrinsic::riscv_vwredsum;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwredsum_vs_tum:
  ID = Intrinsic::riscv_vwredsum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwredsum_vs_tam:
  ID = Intrinsic::riscv_vwredsum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwredsum_vs_tumu:
  ID = Intrinsic::riscv_vwredsum_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwredsumu_vs_tu:
  ID = Intrinsic::riscv_vwredsumu;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwredsumu_vs_ta:
  ID = Intrinsic::riscv_vwredsumu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwredsumu_vs_tum:
  ID = Intrinsic::riscv_vwredsumu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwredsumu_vs_tam:
  ID = Intrinsic::riscv_vwredsumu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwredsumu_vs_tumu:
  ID = Intrinsic::riscv_vwredsumu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsub_vv_tu:
case RISCVVector::BI__builtin_rvv_vwsub_vx_tu:
  ID = Intrinsic::riscv_vwsub;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsub_vv_ta:
case RISCVVector::BI__builtin_rvv_vwsub_vx_ta:
  ID = Intrinsic::riscv_vwsub;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsub_vv_tuma:
case RISCVVector::BI__builtin_rvv_vwsub_vx_tuma:
  ID = Intrinsic::riscv_vwsub_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsub_vv_tama:
case RISCVVector::BI__builtin_rvv_vwsub_vx_tama:
  ID = Intrinsic::riscv_vwsub_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsub_vv_tumu:
case RISCVVector::BI__builtin_rvv_vwsub_vx_tumu:
  ID = Intrinsic::riscv_vwsub_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsub_vv_tamu:
case RISCVVector::BI__builtin_rvv_vwsub_vx_tamu:
  ID = Intrinsic::riscv_vwsub_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsub_wv_tu:
case RISCVVector::BI__builtin_rvv_vwsub_wx_tu:
  ID = Intrinsic::riscv_vwsub_w;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsub_wv_ta:
case RISCVVector::BI__builtin_rvv_vwsub_wx_ta:
  ID = Intrinsic::riscv_vwsub_w;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsub_wv_tuma:
case RISCVVector::BI__builtin_rvv_vwsub_wx_tuma:
  ID = Intrinsic::riscv_vwsub_w_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsub_wv_tama:
case RISCVVector::BI__builtin_rvv_vwsub_wx_tama:
  ID = Intrinsic::riscv_vwsub_w_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsub_wv_tumu:
case RISCVVector::BI__builtin_rvv_vwsub_wx_tumu:
  ID = Intrinsic::riscv_vwsub_w_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsub_wv_tamu:
case RISCVVector::BI__builtin_rvv_vwsub_wx_tamu:
  ID = Intrinsic::riscv_vwsub_w_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsubu_vv_tu:
case RISCVVector::BI__builtin_rvv_vwsubu_vx_tu:
  ID = Intrinsic::riscv_vwsubu;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsubu_vv_ta:
case RISCVVector::BI__builtin_rvv_vwsubu_vx_ta:
  ID = Intrinsic::riscv_vwsubu;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsubu_vv_tuma:
case RISCVVector::BI__builtin_rvv_vwsubu_vx_tuma:
  ID = Intrinsic::riscv_vwsubu_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsubu_vv_tama:
case RISCVVector::BI__builtin_rvv_vwsubu_vx_tama:
  ID = Intrinsic::riscv_vwsubu_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsubu_vv_tumu:
case RISCVVector::BI__builtin_rvv_vwsubu_vx_tumu:
  ID = Intrinsic::riscv_vwsubu_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsubu_vv_tamu:
case RISCVVector::BI__builtin_rvv_vwsubu_vx_tamu:
  ID = Intrinsic::riscv_vwsubu_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsubu_wv_tu:
case RISCVVector::BI__builtin_rvv_vwsubu_wx_tu:
  ID = Intrinsic::riscv_vwsubu_w;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsubu_wv_ta:
case RISCVVector::BI__builtin_rvv_vwsubu_wx_ta:
  ID = Intrinsic::riscv_vwsubu_w;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsubu_wv_tuma:
case RISCVVector::BI__builtin_rvv_vwsubu_wx_tuma:
  ID = Intrinsic::riscv_vwsubu_w_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsubu_wv_tama:
case RISCVVector::BI__builtin_rvv_vwsubu_wx_tama:
  ID = Intrinsic::riscv_vwsubu_w_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsubu_wv_tumu:
case RISCVVector::BI__builtin_rvv_vwsubu_wx_tumu:
  ID = Intrinsic::riscv_vwsubu_w_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vwsubu_wv_tamu:
case RISCVVector::BI__builtin_rvv_vwsubu_wx_tamu:
  ID = Intrinsic::riscv_vwsubu_w_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vxor_vv_tu:
case RISCVVector::BI__builtin_rvv_vxor_vx_tu:
  ID = Intrinsic::riscv_vxor;
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnot_v_tu:
  ID = Intrinsic::riscv_vxor;
  DefaultPolicy = 0;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2,
                   llvm::Constant::getAllOnesValue(ElemTy));
        // passthru, op1, op2, vl
        IntrinsicTypes = {ResultType,
                          ElemTy,
                          Ops[3]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vxor_vv_ta:
case RISCVVector::BI__builtin_rvv_vxor_vx_ta:
  ID = Intrinsic::riscv_vxor;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnot_v_ta:
  ID = Intrinsic::riscv_vxor;
  DefaultPolicy = 1;

      {
        if (DefaultPolicy == TAIL_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2,
                   llvm::Constant::getAllOnesValue(ElemTy));
        // passthru, op1, op2, vl
        IntrinsicTypes = {ResultType,
                          ElemTy,
                          Ops[3]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vxor_vv_tuma:
case RISCVVector::BI__builtin_rvv_vxor_vx_tuma:
  ID = Intrinsic::riscv_vxor_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnot_v_tuma:
  ID = Intrinsic::riscv_vxor_mask;
  DefaultPolicy = 2;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2,
                   llvm::Constant::getAllOnesValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, po2, mask, vl, policy
        IntrinsicTypes = {ResultType,
                          ElemTy,
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vxor_vv_tama:
case RISCVVector::BI__builtin_rvv_vxor_vx_tama:
  ID = Intrinsic::riscv_vxor_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnot_v_tama:
  ID = Intrinsic::riscv_vxor_mask;
  DefaultPolicy = 3;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2,
                   llvm::Constant::getAllOnesValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, po2, mask, vl, policy
        IntrinsicTypes = {ResultType,
                          ElemTy,
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vxor_vv_tumu:
case RISCVVector::BI__builtin_rvv_vxor_vx_tumu:
  ID = Intrinsic::riscv_vxor_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnot_v_tumu:
  ID = Intrinsic::riscv_vxor_mask;
  DefaultPolicy = 0;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2,
                   llvm::Constant::getAllOnesValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, po2, mask, vl, policy
        IntrinsicTypes = {ResultType,
                          ElemTy,
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vxor_vv_tamu:
case RISCVVector::BI__builtin_rvv_vxor_vx_tamu:
  ID = Intrinsic::riscv_vxor_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[2]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vnot_v_tamu:
  ID = Intrinsic::riscv_vxor_mask;
  DefaultPolicy = 1;

      {
        std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
        if (DefaultPolicy == TAIL_AGNOSTIC_MASK_AGNOSTIC)
          Ops.insert(Ops.begin(), llvm::PoisonValue::get(ResultType));
        auto ElemTy = cast<llvm::VectorType>(ResultType)->getElementType();
        Ops.insert(Ops.begin() + 2,
                   llvm::Constant::getAllOnesValue(ElemTy));
        Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
        // maskedoff, op1, po2, mask, vl, policy
        IntrinsicTypes = {ResultType,
                          ElemTy,
                          Ops[4]->getType()};
        break;
      }
      break;
case RISCVVector::BI__builtin_rvv_vzext_vf2_tu:
case RISCVVector::BI__builtin_rvv_vzext_vf4_tu:
case RISCVVector::BI__builtin_rvv_vzext_vf8_tu:
  ID = Intrinsic::riscv_vzext;
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vzext_vf2_ta:
case RISCVVector::BI__builtin_rvv_vzext_vf4_ta:
case RISCVVector::BI__builtin_rvv_vzext_vf8_ta:
  ID = Intrinsic::riscv_vzext;
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vzext_vf2_tuma:
case RISCVVector::BI__builtin_rvv_vzext_vf4_tuma:
case RISCVVector::BI__builtin_rvv_vzext_vf8_tuma:
  ID = Intrinsic::riscv_vzext_mask;
  DefaultPolicy = 2;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vzext_vf2_tama:
case RISCVVector::BI__builtin_rvv_vzext_vf4_tama:
case RISCVVector::BI__builtin_rvv_vzext_vf8_tama:
  ID = Intrinsic::riscv_vzext_mask;
  DefaultPolicy = 3;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  Ops.insert(Ops.begin(), llvm::UndefValue::get(ResultType));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vzext_vf2_tumu:
case RISCVVector::BI__builtin_rvv_vzext_vf4_tumu:
case RISCVVector::BI__builtin_rvv_vzext_vf8_tumu:
  ID = Intrinsic::riscv_vzext_mask;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;
case RISCVVector::BI__builtin_rvv_vzext_vf2_tamu:
case RISCVVector::BI__builtin_rvv_vzext_vf4_tamu:
case RISCVVector::BI__builtin_rvv_vzext_vf8_tamu:
  ID = Intrinsic::riscv_vzext_mask;
  DefaultPolicy = 1;
  std::rotate(Ops.begin(), Ops.begin() + 1, Ops.end() - 1);
  Ops.push_back(ConstantInt::get(Ops.back()->getType(), DefaultPolicy));
  IntrinsicTypes = {ResultType, Ops[1]->getType(), Ops.back()->getType()};
  break;

