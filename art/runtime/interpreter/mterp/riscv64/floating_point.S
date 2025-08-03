// Note: Floating point operations must follow IEEE 754 rules, using round-to-nearest and gradual
// underflow, except where stated otherwise.

//
// floating-point comparators vAA, vBB, vCC
// Note: Perform the indicated floating point comparison, setting a to 0 if b == c, 1 if b > c, or
// -1 if b < c. The "bias" listed indicates how NaN comparisons are treated: "gt bias" instructions
// return 1 for NaN comparisons, and "lt bias" instructions return -1.
//

// cmpl-float vAA, vBB, vCC
// Format 23x: AA|2d CC|BB
// LT bias, if NaN then vAA := -1
%def op_cmpl_float(is_double=False):
   FETCH t1, count=1     // t1 := CC|BB
   srliw t0, xINST, 8    // t0 := AA
   srliw t2, t1, 8       // t2 := CC
   andi t1, t1, 0xFF     // t1 := BB
%  get_vreg_float("ft1", "t1", is_double=is_double)  # ft1 := fp[BB]
%  get_vreg_float("ft2", "t2", is_double=is_double)  # ft2 := fp[CC]
   // Note: Formula "((FLE r,l) - 1) + (FLT r,l)" lifted from compiler.
%  precision = "d" if is_double else "s"
   fle.${precision} t1, ft2, ft1
   flt.${precision} t2, ft2, ft1
   addi t1, t1, -1
   add t2, t2, t1
   FETCH_ADVANCE_INST 2
%  set_vreg("t2", "t0", z0="t1")  # fp[AA] := result
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

// cmpg-float vvAA, vBB, vCC
// Format 23x: AA|2e CC|BB
// GT bias, if NaN then vAA := 1
%def op_cmpg_float(is_double=False):
   FETCH t1, count=1     // t1 := CC|BB
   srliw t0, xINST, 8    // t0 := AA
   srliw t2, t1, 8       // t2 := CC
   andi t1, t1, 0xFF     // t1 := BB
%  get_vreg_float("ft1", "t1", is_double=is_double)  # ft1 := fp[BB]
%  get_vreg_float("ft2", "t2", is_double=is_double)  # ft2 := fp[CC]
   // Note: Formula "((FLE l,r) ^ 1) - (FLT l,r)" lifted from compiler.
%  precision = "d" if is_double else "s"
   fle.${precision} t1, ft1, ft2
   flt.${precision} t2, ft1, ft2
   xori t1, t1, 1
   sub t2, t1, t2
   FETCH_ADVANCE_INST 2
%  set_vreg("t2", "t0", z0="t1")  # fp[AA] := result
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

// cmpl-double vAA, vBB, vCC
// Format 23x: AA|2f CC|BB
// LT bias, if NaN then vAA := -1
%def op_cmpl_double():
%  op_cmpl_float(is_double=True)

// cmpg-double vAA, vBB, vCC
// Format 23x: AA|30 CC|BB
// Note: Formula "((FLE l,r) ^ 1) - (FLT l,r)" lifted from compiler.
// GT bias, if NaN then vAA := 1
%def op_cmpg_double():
%  op_cmpg_float(is_double=True)

//
// funop vA, vB
// Format 12x: B|A|op
//

// neg-float vA, vB
// Format 12x: B|A|7f
%def op_neg_float():
%  generic_funop(instr="fneg.s ft0, ft0", dst="s", src="s")

// neg-double vA, vB
// Format 12x: B|A|80
%def op_neg_double():
%  generic_funop(instr="fneg.d ft0, ft0", dst="d", src="d")

// int-to-float vA, vB
// Format 12x: B|A|82
// Note: Conversion of int32 to float, using round-to-nearest. This loses precision for some values.
// Note: For ties, the IEEE 754-2008 standard defaults to "roundTiesToEven" for binary floats.
%def op_int_to_float():
%  generic_funop(instr="fcvt.s.w ft0, t1, rne", dst="s", src="w")

// int-to-double vA, vB
// Format 12x: B|A|83
// Note: Conversion of int32 to double.
%def op_int_to_double():
%  generic_funop(instr="fcvt.d.w ft0, t1", dst="d", src="w")

// long-to-float vA, vB
// Format 12x: B|A|85
// Note: Conversion of int64 to float, using round-to-nearest. This loses precision for some values.
// Note: For ties, the IEEE 754-2008 standard defaults to "roundTiesToEven" for binary floats.
%def op_long_to_float():
%  generic_funop(instr="fcvt.s.l ft0, t1, rne", dst="s", src="l")

// long-to-double vA, vB
// Format 12x: B|A|86
// Note: Conversion of int64 to double, using round-to-nearest. This loses precision for some values.
// Note: For ties, the IEEE 754-2008 standard defaults to "roundTiesToEven" for binary floats.
%def op_long_to_double():
%  generic_funop(instr="fcvt.d.l ft0, t1, rne", dst="d", src="l")

// float-to-int vA, vB
// Format 12x: B|A|87
// Note: Conversion of float to int32, using round-toward-zero. NaN and -0.0 (negative zero)
// convert to the integer 0. Infinities and values with too large a magnitude to be represented
// get converted to either 0x7fffffff or -0x80000000 depending on sign.
//
// FCVT.W.S RTZ has the following behavior:
// - NaN rounds to 0x7ffffff - requires check and set to zero.
// - negative zero rounds to zero - matches dex spec.
// - pos inf rounds to 0x7fffffff - matches dex spec.
// - neg inf rounds to 0x80000000 - matches dex spec.
%def op_float_to_int():
%  generic_funop(instr="fcvt.w.s t1, ft0, rtz", dst="w", src="s", nan_zeroed=True)

// float-to-long vA, vB
// Format 12x: B|A|88
// Note: Conversion of float to int64, using round-toward-zero. The same special case rules as for
// float-to-int apply here, except that out-of-range values get converted to either
// 0x7fffffffffffffff or -0x8000000000000000 depending on sign.
//
// FCVT.L.S RTZ has the following behavior:
// - NaN rounds to 0x7fffffffffffffff - requires check and set to zero.
// - negative zero rounds to zero - matches dex spec.
// - pos inf rounds to 0x7fffffffffffffff - matches dex spec.
// - neg inf rounds to 0x8000000000000000 - matches dex spec.
%def op_float_to_long():
%  generic_funop(instr="fcvt.l.s t1, ft0, rtz", dst="l", src="s", nan_zeroed=True)

// float-to-double vA, vB
// Format 12x: B|A|89
// Note: Conversion of float to double, preserving the value exactly.
%def op_float_to_double():
%  generic_funop(instr="fcvt.d.s ft0, ft0", dst="d", src="s")

// double-to-int vA, vB
// Format 12x: B|A|8a
// Note: Conversion of double to int32, using round-toward-zero. The same special case rules as for
// float-to-int apply here.
%def op_double_to_int():
%  generic_funop(instr="fcvt.w.d t1, ft0, rtz", dst="w", src="d", nan_zeroed=True)

// double-to-long vA, vB
// Format 12x: B|A|8b
// Note: Conversion of double to int64, using round-toward-zero. The same special case rules as for
// float-to-long apply here.
%def op_double_to_long():
%  generic_funop(instr="fcvt.l.d t1, ft0, rtz", dst="l", src="d", nan_zeroed=True)

// double-to-float vA, vB
// Format 12x: B|A|8c
// Note: Conversion of double to float, using round-to-nearest. This loses precision for some values.
// Note: For ties, the IEEE 754-2008 standard defaults to "roundTiesToEven" for binary floats.
%def op_double_to_float():
%  generic_funop(instr="fcvt.s.d ft0, ft0, rne", dst="s", src="d")

// unop boilerplate
// instr: operand held in t1 or ft0, result written to t1 or ft0.
// instr must not clobber t2.
// dst: one of w (int32), l (int64), s (float), d (double)
// src: one of w (int32), l (int64), s (float), d (double)
// Clobbers: ft0, t0, t1, t2
%def generic_funop(instr, dst, src, nan_zeroed=False):
    srliw t0, xINST, 12        // t0 := B
    srliw t2, xINST, 8         // t2 := B|A

%  if src == "w":
%    get_vreg("t1", "t0")     #  t1 := fp[B]
%  elif src == "l":
     GET_VREG_WIDE t1, t0     // t1 := fp[B]
%  elif src == "s":
%    get_vreg_float("ft0", "t0")  #  ft0 := fp[B]
%  elif src == "d":
     GET_VREG_DOUBLE ft0, t0  // ft0 := fp[B]
%  else:
%    assert false, src
%#:
    and t2, t2, 0xF            // t2 := A
    FETCH_ADVANCE_INST 1       // advance xPC, load xINST
%  if nan_zeroed:
     // Okay to clobber T1. It is not read if nan_zeroed=True.
     fclass.${src} t1, ft0  // fclass.s or fclass.d on the source register ft0
     sltiu t1, t1, 0x100    // t1 := 0 if NaN, per dex spec. Skip the conversion.
     beqz t1, 1f
%#:
    $instr                     // read operand (from t1|ft0), write result (to t1|ft0)
                               // do not clobber t2!
1:

%  if dst == "w":
%    set_vreg("t1", "t2", z0="t0")   #  fp[A] := t1
%  elif dst == "l":
     SET_VREG_WIDE t1, t2, z0=t0     // fp[A] := t1
%  elif dst == "s":
%    set_vreg_float("ft0", "t2", z0="t0")  # fp[A] := ft0
%  elif dst == "d":
     SET_VREG_DOUBLE ft0, t2, z0=t0  // fp[B] := ft0
%  else:
%    assert false, dst
%#:

   GET_INST_OPCODE t0         // t0 holds next opcode
   GOTO_OPCODE t0             // continue to next

//
// fbinop vAA, vBB, vCC
// Format 23x: AA|op CC|BB
//

// add-float vAA, vBB, vCC
// Format 23x: AA|a6 CC|BB
%def op_add_float():
%  generic_fbinop(instr="fadd.s fa0, fa0, fa1, rne")

// sub-float vAA, vBB, vCC
// Format 23x: AA|a7 CC|BB
%def op_sub_float():
%  generic_fbinop(instr="fsub.s fa0, fa0, fa1, rne")

// mul-float vAA, vBB, vCC
// Format 23x: AA|a8 CC|BB
%def op_mul_float():
%  generic_fbinop(instr="fmul.s fa0, fa0, fa1, rne")

// div-float vAA, vBB, vCC
// Format 23x: AA|a9 CC|BB
%def op_div_float():
%  generic_fbinop(instr="fdiv.s fa0, fa0, fa1, rne")

// rem-float vAA, vBB, vCC
// Format 23x: AA|aa CC|BB
// Note: Floating point remainder after division. This function is different than IEEE 754 remainder
// and is defined as result == a - roundTowardZero(a / b) * b.
// Note: RISC-V does not offer floating point remainder; use fmodf in libm.
%def op_rem_float():
%  generic_fbinop(instr="call fmodf")

// add-double vAA, vBB, vCC
// Format 23x: AA|ab CC|BB
%def op_add_double():
%  generic_fbinop(instr="fadd.d fa0, fa0, fa1, rne", is_double=True)

// sub-double vAA, vBB, vCC
// Format 23x: AA|ac CC|BB
%def op_sub_double():
%  generic_fbinop(instr="fsub.d fa0, fa0, fa1, rne", is_double=True)

// mul-double vAA, vBB, vCC
// Format 23x: AA|ad CC|BB
%def op_mul_double():
%  generic_fbinop(instr="fmul.d fa0, fa0, fa1, rne", is_double=True)

// div-double vAA, vBB, vCC
// Format 23x: AA|ae CC|BB
%def op_div_double():
%  generic_fbinop(instr="fdiv.d fa0, fa0, fa1, rne", is_double=True)

// rem-double vAA, vBB, vCC
// Format 23x: AA|af CC|BB
// Note: Floating point remainder after division. This function is different than IEEE 754 remainder
// and is defined as result == a - roundTowardZero(a / b) * b.
// Note: RISC-V does not offer floating point remainder; use fmod in libm.
%def op_rem_double():
%  generic_fbinop(instr="call fmod", is_double=True)

// fbinop boilerplate
// instr: operands held in fa0 and fa1, result written to fa0
// instr may be a libm call, so:
//  - avoid caller-save state across instr; s11 is used instead.
//  - fa0 and fa1 are used instead of ft0 and ft1.
//
// The is_double flag ensures vregs are read and written in 64-bit widths.
// Clobbers: t0, t1, fa0, fa1, s11
%def generic_fbinop(instr, is_double=False):
   FETCH t0, count=1     // t0 := CC|BB
   srliw s11, xINST, 8   // s11 := AA
   srliw t1, t0, 8       // t1 := CC
   and t0, t0, 0xFF      // t0 := BB
%  get_vreg_float("fa1", "t1", is_double=is_double)
                         // fa1 := fp[CC]
%  get_vreg_float("fa0", "t0", is_double=is_double)
                         // fa0 := fp[BB]
   FETCH_ADVANCE_INST 2  // advance xPC, load xINST
   $instr                // read fa0 and fa1, write result to fa0.
                         // instr may be a function call.
%  set_vreg_float("fa0", "s11", z0="t0", is_double=is_double)
                         // fp[AA] := fa0
   GET_INST_OPCODE t0    // t0 holds next opcode
   GOTO_OPCODE t0        // continue to next

//
// fbinop/2addr vA, vB
// Format 12x: B|A|op
//

// add-float/2addr vA, vB
// Format 12x: B|A|c6
%def op_add_float_2addr():
%  generic_fbinop_2addr(instr="fadd.s fa0, fa0, fa1")

// sub-float/2addr vA, vB
// Format 12x: B|A|c7
%def op_sub_float_2addr():
%  generic_fbinop_2addr(instr="fsub.s fa0, fa0, fa1")

// mul-float/2addr vA, vB
// Format 12x: B|A|c8
%def op_mul_float_2addr():
%  generic_fbinop_2addr(instr="fmul.s fa0, fa0, fa1")

// div-float/2addr vA, vB
// Format 12x: B|A|c9
%def op_div_float_2addr():
%  generic_fbinop_2addr(instr="fdiv.s fa0, fa0, fa1")

// rem-float/2addr vA, vB
// Format 12x: B|A|ca
// Note: Floating point remainder after division. This function is different than IEEE 754 remainder
// and is defined as result == a - roundTowardZero(a / b) * b.
// Note: RISC-V does not offer floating point remainder; use fmodf in libm.
%def op_rem_float_2addr():
%  generic_fbinop_2addr(instr="call fmodf")

// add-double/2addr vA, vB
// Format 12x: B|A|cb
%def op_add_double_2addr():
%  generic_fbinop_2addr(instr="fadd.d fa0, fa0, fa1", is_double=True)

// sub-double/2addr vA, vB
// Format 12x: B|A|cc
%def op_sub_double_2addr():
%  generic_fbinop_2addr(instr="fsub.d fa0, fa0, fa1", is_double=True)

// mul-double/2addr vA, vB
// Format 12x: B|A|cd
%def op_mul_double_2addr():
%  generic_fbinop_2addr(instr="fmul.d fa0, fa0, fa1", is_double=True)

// div-double/2addr vA, vB
// Format 12x: B|A|ce
%def op_div_double_2addr():
%  generic_fbinop_2addr(instr="fdiv.d fa0, fa0, fa1", is_double=True)

// rem-double/2addr vA, vB
// Format 12x: B|A|cf
// Note: Floating point remainder after division. This function is different than IEEE 754 remainder
// and is defined as result == a - roundTowardZero(a / b) * b.
// Note: RISC-V does not offer floating point remainder; use fmod in libm.
%def op_rem_double_2addr():
%  generic_fbinop_2addr(instr="call fmod", is_double=True)

// fbinop/2addr boilerplate
// instr: operands held in fa0 and fa1, result written to fa0
// instr may be a libm call, so:
//  - avoid caller-save state across instr; s11 is used instead.
//  - use fa0 and fa1 instead of ft0 and ft1.
//
// The is_double flag ensures vregs are read and written in 64-bit widths.
// Clobbers: t0, t1, fa0, fa1, s11
%def generic_fbinop_2addr(instr, is_double=False):
   srliw t0, xINST, 8       // t0 := B|A
   srliw t1, xINST, 12      // t1 := B
   and t0, t0, 0xF          // t0 := A
%  get_vreg_float("fa1", "t1", is_double=is_double)
                             // fa1 := fp[B]
   mv s11, t0               // s11 := A
%  get_vreg_float("fa0", "t0", is_double=is_double)
                             // fa0 := fp[A]
   FETCH_ADVANCE_INST 1     // advance xPC, load xINST
   $instr                   // read fa0 and f1, write result to fa0.
                            // instr may be a function call.
   GET_INST_OPCODE t1       // t1 holds next opcode
%  set_vreg_float("fa0", "s11", z0="t0", is_double=is_double)
                            // fp[A] := fa0
   GOTO_OPCODE t1           // continue to next
