//
// unop vA, vB
// Format 12x: B|A|op
// (see floating_point.S for float/double ops)
//

// neg-int vA, vB
// Format 12x: B|A|7b
%def op_neg_int():
%  generic_unop(instr="negw t1, t1")

// not-int vA, vB
// Format 12x: B|A|7c
%def op_not_int():
%  generic_unop(instr="not t1, t1")

// neg-long vA, vB
// Format 12x: B|A|7d
%def op_neg_long():
%  generic_unop(instr="neg t1, t1", is_wide=True)

// not-long vA, vB
// Format 12x: B|A|7e
%def op_not_long():
%  generic_unop(instr="not t1, t1", is_wide=True)

// int-to-long vA, vB
// Format 12x: B|A|81
// Note: Sign extension of int32 into int64.
// Read from 32-bit vreg and write to 64-bit vreg, hence a custom impl.
%def op_int_to_long():
   srliw t1, xINST, 12   // t1 := B
   srliw t2, xINST, 8    // t2 := B|A
%  get_vreg("t1", "t1")  #  t1 := fp[B] with sign extension to 64 bits
   FETCH_ADVANCE_INST 1  // advance xPC, load xINST
   and t2, t2, 0xF       // t2 := A
   GET_INST_OPCODE t3    // t3 holds next opcode
   SET_VREG_WIDE t1, t2, z0=t0
                         // fp[A:A+1] := t1
   GOTO_OPCODE t3        // continue to next

// long-to-int vA, vB
// Format 12x: B|A|84
// Note: Truncation of int64 into int32.
// Implemented as a read from the low bits from vB, write them to vA.
// Note: instr is intentionally empty.
%def op_long_to_int():
%  generic_unop(instr="")

// int-to-byte vA, vB
// Format 12x: B|A|8d
// Note: Truncation of int32 to int8, sign extending the result.
%def op_int_to_byte():
%  generic_unop(instr="sext.b t1, t1")

// int-to-byte vA, vB
// Format 12x: B|A|8e
// Note: Truncation of int32 to uint16, without sign extension.
%def op_int_to_char():
%  generic_unop(instr="zext.h t1, t1")

// int-to-byte vA, vB
// Format 12x: B|A|8f
// Note: Truncation of int32 to int16, sign extending the result.
%def op_int_to_short():
%  generic_unop(instr="sext.h t1, t1")

// unop boilerplate
// instr: operand held in t1, result written to t1.
// instr must not clobber t2.
// Clobbers: t0, t1, t2
%def generic_unop(instr, is_wide=False):
   srliw t1, xINST, 12   // t1 := B
   srliw t2, xINST, 8    // t2 := B|A
%  get_vreg("t1", "t1", is_wide=is_wide)
                         // t1 := fp[B]
   and t2, t2, 0xF       // t2 := A
   FETCH_ADVANCE_INST 1  // advance xPC, load xINST
   $instr                // read t1, write result to t1.
                         // do not clobber t2!
%  set_vreg("t1", "t2", z0="t0", is_wide=is_wide)
                         // fp[A] := t1
   GET_INST_OPCODE t0    // t0 holds next opcode
   GOTO_OPCODE t0        // continue to next

//
// binop vAA, vBB, vCC
// Format 23x: AA|op CC|BB
// (see floating_point.S for float/double ops)
//

// add-int vAA, vBB, vCC
// Format 23x: AA|90 CC|BB
%def op_add_int():
%  generic_binop(instr="addw t1, t1, t2")

// sub-int vAA, vBB, vCC
// Format 23x: AA|91 CC|BB
%def op_sub_int():
%  generic_binop(instr="subw t1, t1, t2")

// mul-int vAA, vBB, vCC
// Format 23x: AA|92 CC|BB
%def op_mul_int():
%  generic_binop(instr="mulw t1, t1, t2")

// div-int vAA, vBB, vCC
// Format 23x: AA|93 CC|BB
// Note: Twos-complement division, rounded towards zero (that is, truncated to integer). This throws
// ArithmeticException if b == 0.
%def op_div_int():
%  generic_binop(instr="divw t1, t1, t2", divz_throw=True)

// rem-int vAA, vBB, vCC
// Format 23x: AA|94 CC|BB
// Note: Twos-complement remainder after division. The sign of the result is the same as that of a,
// and it is more precisely defined as result == a - (a / b) * b. This throws ArithmeticException if
// b == 0.
%def op_rem_int():
%  generic_binop(instr="remw t1, t1, t2", divz_throw=True)

// and-int vAA, vBB, vCC
// Format 23x: AA|95 CC|BB
%def op_and_int():
%  generic_binop(instr="and t1, t1, t2")

// or-int vAA, vBB, vCC
// Format 23x: AA|96 CC|BB
%def op_or_int():
%  generic_binop(instr="or t1, t1, t2")

// xor-int vAA, vBB, vCC
// Format 23x: AA|97 CC|BB
%def op_xor_int():
%  generic_binop(instr="xor t1, t1, t2")

// shl-int vAA, vBB, vCC
// Format 23x: AA|98 CC|BB
// Note: SLLW uses t2[4:0] for the shift amount.
%def op_shl_int():
%  generic_binop(instr="sllw t1, t1, t2")

// shr-int vAA, vBB, vCC
// Format 23x: AA|99 CC|BB
// Note: SRAW uses t2[4:0] for the shift amount.
%def op_shr_int():
%  generic_binop(instr="sraw t1, t1, t2")

// ushr-int vAA, vBB, vCC
// Format 23x: AA|9a CC|BB
// Note: SRLW uses t2[4:0] for the shift amount.
%def op_ushr_int():
%  generic_binop(instr="srlw t1, t1, t2")

// add-long vAA, vBB, vCC
// Format 23x: AA|9b CC|BB
%def op_add_long():
%  generic_binop(instr="add t1, t1, t2", is_wide=True)

// sub-long vAA, vBB, vCC
// Format 23x: AA|9c CC|BB
%def op_sub_long():
%  generic_binop(instr="sub t1, t1, t2", is_wide=True)

// mul-long vAA, vBB, vCC
// Format 23x: AA|9d CC|BB
%def op_mul_long():
%  generic_binop(instr="mul t1, t1, t2", is_wide=True)

// div-long vAA, vBB, vCC
// Format 23x: AA|9e CC|BB
// Note: Twos-complement division, rounded towards zero (that is, truncated to integer). This throws
// ArithmeticException if b == 0.
%def op_div_long():
%  generic_binop(instr="div t1, t1, t2", divz_throw=True, is_wide=True)

// rem-long vAA, vBB, vCC
// Format 23x: AA|9f CC|BB
// Note: Twos-complement remainder after division. The sign of the result is the same as that of a,
// and it is more precisely defined as result == a - (a / b) * b. This throws ArithmeticException if
// b == 0.
%def op_rem_long():
%  generic_binop(instr="rem t1, t1, t2", divz_throw=True, is_wide=True)

// and-long vAA, vBB, vCC
// Format 23x: AA|a0 CC|BB
%def op_and_long():
%  generic_binop(instr="and t1, t1, t2", is_wide=True)

// or-long vAA, vBB, vCC
// Format 23x: AA|a1 CC|BB
%def op_or_long():
%  generic_binop(instr="or t1, t1, t2", is_wide=True)

// xor-long vAA, vBB, vCC
// Format 23x: AA|a2 CC|BB
%def op_xor_long():
%  generic_binop(instr="xor t1, t1, t2", is_wide=True)

// shl-long vAA, vBB, vCC
// Format 23x: AA|a3 CC|BB
// Note: SLL uses t2[5:0] for the shift amount.
%def op_shl_long():
%  generic_shift_wide(instr="sll t1, t1, t2")

// shr-long vAA, vBB, vCC
// Format 23x: AA|a4 CC|BB
// Note: SRA uses t2[5:0] for the shift amount.
%def op_shr_long():
%  generic_shift_wide(instr="sra t1, t1, t2")

// ushr-long vAA, vBB, vCC
// Format 23x: AA|a5 CC|BB
// Note: SRL uses t2[5:0] for the shift amount.
%def op_ushr_long():
%  generic_shift_wide(instr="srl t1, t1, t2")

// binop boilerplate
// instr: operands held in t1 and t2, result written to t1.
// instr must not throw. Exceptions to be thrown prior to instr.
// instr must not clobber t3.
//
// The divz_throw flag generates check-and-throw code for div/0.
// The is_wide flag ensures vregs are read and written in 64-bit widths.
// Clobbers: t0, t1, t2, t3
%def generic_binop(instr, divz_throw=False, is_wide=False):
   FETCH t1, count=1     // t1 := CC|BB
   srliw t3, xINST, 8    // t3 := AA
   srliw t2, t1, 8       // t2 := CC
   and t1, t1, 0xFF      // t1 := BB
%  get_vreg("t2", "t2", is_wide=is_wide)  # t2 := fp[CC]
%  get_vreg("t1", "t1", is_wide=is_wide)  # t1 := fp[BB]
%  if divz_throw:
     beqz t2, 1f         // Must throw before FETCH_ADVANCE_INST.
%#:
   FETCH_ADVANCE_INST 2  // advance xPC, load xINST
   $instr                // read t1 and t2, write result to t1.
                         // do not clobber t3!
   GET_INST_OPCODE t2    // t2 holds next opcode
%  set_vreg("t1", "t3", z0="t0", is_wide=is_wide)
                         // fp[AA] := t1
   GOTO_OPCODE t2        // continue to next
1:
%  if divz_throw:
     tail common_errDivideByZero
%#:

// binop wide shift boilerplate
// instr: operands held in t1 (64-bit) and t2 (32-bit), result written to t1.
// instr must not clobber t3.
// Clobbers: t0, t1, t2, t3
//
// Note: Contrary to other -long mathematical operations (which take register pairs for both their
// first and their second source), shl-long, shr-long, and ushr-long take a register pair for their
// first source (the value to be shifted), but a single register for their second source (the
// shifting distance).
%def generic_shift_wide(instr):
   FETCH t1, count=1     // t1 := CC|BB
   srliw t3, xINST, 8    // t3 := AA
   srliw t2, t1, 8       // t2 := CC
   and t1, t1, 0xFF      // t1 := BB
%  get_vreg("t2", "t2")  #  t2 := fp[CC]
   GET_VREG_WIDE t1, t1  // t1 := fp[BB]
   FETCH_ADVANCE_INST 2  // advance xPC, load xINST
   $instr                // read t1 and t2, write result to t1.
                         // do not clobber t3!
   GET_INST_OPCODE t2    // t2 holds next opcode
   SET_VREG_WIDE t1, t3, z0=t0
                         // fp[AA] := t1
   GOTO_OPCODE t2        // continue to next

//
// binop/2addr vA, vB
// Format 12x: B|A|op
// (see floating_point.S for float/double ops)
//

// add-int/2addr vA, vB
// Format 12x: B|A|b0
%def op_add_int_2addr():
%  generic_binop_2addr(instr="addw t1, t1, t2")

// sub-int/2addr vA, vB
// Format 12x: B|A|b1
%def op_sub_int_2addr():
%  generic_binop_2addr(instr="subw t1, t1, t2")

// mul-int/2addr vA, vB
// Format 12x: B|A|b2
%def op_mul_int_2addr():
%  generic_binop_2addr(instr="mulw t1, t1, t2")

// div-int/2addr vA, vB
// Format 12x: B|A|b3
// Note: Twos-complement division, rounded towards zero (that is, truncated to integer). This throws
// ArithmeticException if b == 0.
%def op_div_int_2addr():
%  generic_binop_2addr(instr="divw t1, t1, t2", divz_throw=True)

// rem-int/2addr vA, vB
// Format 12x: B|A|b4
// Note: Twos-complement remainder after division. The sign of the result is the same as that of a,
// and it is more precisely defined as result == a - (a / b) * b. This throws ArithmeticException if
// b == 0.
%def op_rem_int_2addr():
%  generic_binop_2addr(instr="remw t1, t1, t2", divz_throw=True)

// and-int/2addr vA, vB
// Format 12x: B|A|b5
%def op_and_int_2addr():
%  generic_binop_2addr(instr="and t1, t1, t2")

// or-int/2addr vA, vB
// Format 12x: B|A|b6
%def op_or_int_2addr():
%  generic_binop_2addr(instr="or t1, t1, t2")

// xor-int/2addr vA, vB
// Format 12x: B|A|b7
%def op_xor_int_2addr():
%  generic_binop_2addr(instr="xor t1, t1, t2")

// shl-int/2addr vA, vB
// Format 12x: B|A|b8
%def op_shl_int_2addr():
%  generic_binop_2addr(instr="sllw t1, t1, t2")

// shr-int/2addr vA, vB
// Format 12x: B|A|b9
%def op_shr_int_2addr():
%  generic_binop_2addr(instr="sraw t1, t1, t2")

// ushr-int/2addr vA, vB
// Format 12x: B|A|ba
%def op_ushr_int_2addr():
%  generic_binop_2addr(instr="srlw t1, t1, t2")

// add-long/2addr vA, vB
// Format 12x: B|A|bb
%def op_add_long_2addr():
%  generic_binop_2addr(instr="add t1, t1, t2", is_wide=True)

// sub-long/2addr vA, vB
// Format 12x: B|A|bc
%def op_sub_long_2addr():
%  generic_binop_2addr(instr="sub t1, t1, t2", is_wide=True)

// mul-long/2addr vA, vB
// Format 12x: B|A|bd
%def op_mul_long_2addr():
%  generic_binop_2addr(instr="mul t1, t1, t2", is_wide=True)

// div-long/2addr vA, vB
// Format 12x: B|A|be
%def op_div_long_2addr():
%  generic_binop_2addr(instr="div t1, t1, t2", divz_throw=True, is_wide=True)

// rem-long/2addr vA, vB
// Format 12x: B|A|bf
%def op_rem_long_2addr():
%  generic_binop_2addr(instr="rem t1, t1, t2", divz_throw=True, is_wide=True)

// and-long/2addr vA, vB
// Format 12x: B|A|c0
%def op_and_long_2addr():
%  generic_binop_2addr(instr="and t1, t1, t2", is_wide=True)

// or-long/2addr vA, vB
// Format 12x: B|A|c1
%def op_or_long_2addr():
%  generic_binop_2addr(instr="or t1, t1, t2", is_wide=True)

// xor-long/2addr vA, vB
// Format 12x: B|A|c2
%def op_xor_long_2addr():
%  generic_binop_2addr(instr="xor t1, t1, t2", is_wide=True)

// shl-long/2addr vA, vB
// Format 12x: B|A|c3
// Note: SLL uses t2[5:0] for the shift amount.
%def op_shl_long_2addr():
%  generic_shift_wide_2addr(instr="sll t1, t1, t2")

// shr-long/2addr vA, vB
// Format 12x: B|A|c4
// Note: SRA uses t2[5:0] for the shift amount.
%def op_shr_long_2addr():
%  generic_shift_wide_2addr(instr="sra t1, t1, t2")

// ushr-long/2addr vA, vB
// Format 12x: B|A|c5
// Note: SRL uses t2[5:0] for the shift amount.
%def op_ushr_long_2addr():
%  generic_shift_wide_2addr(instr="srl t1, t1, t2")

// binop 2addr boilerplate
// instr: operands held in t1 and t2, result written to t1.
// instr must not throw. Exceptions to be thrown prior to instr.
// instr must not clobber t3.
//
// The divz_throw flag generates check-and-throw code for div/0.
// The is_wide flag ensures vregs are read and written in 64-bit widths.
// Clobbers: t0, t1, t2, t3, t4
%def generic_binop_2addr(instr, divz_throw=False, is_wide=False):
   srliw t2, xINST, 12   // t2 := B
   srliw t3, xINST, 8    // t3 := B|A
%  get_vreg("t2", "t2", is_wide=is_wide)
                         // t2 := fp[B]
   and t3, t3, 0xF       // t3 := A (cached for SET_VREG)
   mv t4, t3             // t4 := A
%  get_vreg("t1", "t4", is_wide=is_wide)
                         // t1 := fp[A]
%  if divz_throw:
     beqz t2, 1f         // Must throw before FETCH_ADVANCE_INST.
%#:
   FETCH_ADVANCE_INST 1  // advance xPC, load xINST
   $instr                // read t1 and t2, write result to t1.
                         // do not clobber t3!
   GET_INST_OPCODE t2    // t2 holds next opcode
%  set_vreg("t1", "t3", z0="t0", is_wide=is_wide)
                         // fp[A] := t1
   GOTO_OPCODE t2        // continue to next
1:
%  if divz_throw:
     tail common_errDivideByZero
%#:

// binop wide shift 2addr boilerplate
// instr: operands held in t1 (64-bit) and t2 (32-bit), result written to t1.
// instr must not clobber t3.
// Clobbers: t0, t1, t2, t3, t4
//
// Note: Contrary to other -long/2addr mathematical operations (which take register pairs for both
// their destination/first source and their second source), shl-long/2addr, shr-long/2addr, and
// ushr-long/2addr take a register pair for their destination/first source (the value to be
// shifted), but a single register for their second source (the shifting distance).
%def generic_shift_wide_2addr(instr):
   srliw t2, xINST, 12   // t2 := B
   srliw t3, xINST, 8    // t3 := B|A
%  get_vreg("t2", "t2")  #  t2 := fp[B]
   and t3, t3, 0xF       // t3 := A (cached for SET_VREG_WIDE)
   mv t4, t3             // t4 := A
   FETCH_ADVANCE_INST 1  // advance xPC, load xINST
   GET_VREG_WIDE t1, t4  // t1 := fp[A]
   $instr                // read t1 and t2, write result to t1.
                         // do not clobber t3!
   GET_INST_OPCODE t2    // t2 holds next opcode
   SET_VREG_WIDE t1, t3, z0=t0
                         // fp[A] := t1
   GOTO_OPCODE t2        // continue to next

//
// binop/lit16 vA, vB, #+CCCC
// Format 22s: B|A|op CCCC
//

// add-int/lit16 vA, vB, #+CCCC
// Format 22s: B|A|d0 CCCC
%def op_add_int_lit16():
%  generic_binop_lit16(instr="addw t1, t1, t2")

// rsub-int vA, vB, #+CCCC
// Format 22s: B|A|d1 CCCC
// Note: rsub-int does not have a suffix since this version is the main opcode of its family.
// Note: Twos-complement reverse subtraction.
%def op_rsub_int():
%  generic_binop_lit16(instr="subw t1, t2, t1")

// mul-int/lit16 vA, vB, #+CCCC
// Format 22s: B|A|d2 CCCC
%def op_mul_int_lit16():
%  generic_binop_lit16(instr="mulw t1, t1, t2")

// div-int/lit16 vA, vB, #+CCCC
// Format 22s: B|A|d3 CCCC
// Note: Twos-complement division, rounded towards zero (that is, truncated to integer). This throws
// ArithmeticException if b == 0.
%def op_div_int_lit16():
%  generic_binop_lit16(instr="divw t1, t1, t2", divz_throw=True)

// rem-int/lit16 vA, vB, #+CCCC
// Format 22s: B|A|d4 CCCC
// Note: Twos-complement remainder after division. The sign of the result is the same as that of a,
// and it is more precisely defined as result == a - (a / b) * b. This throws ArithmeticException if
// b == 0.
%def op_rem_int_lit16():
%  generic_binop_lit16(instr="remw t1, t1, t2", divz_throw=True)

// and-int/lit16 vA, vB, #+CCCC
// Format 22s: B|A|d5 CCCC
%def op_and_int_lit16():
%  generic_binop_lit16(instr="and t1, t1, t2")

// or-int/lit16 vA, vB, #+CCCC
// Format 22s: B|A|d6 CCCC
%def op_or_int_lit16():
%  generic_binop_lit16(instr="or t1, t1, t2")

// xor-int/lit16 vA, vB, #+CCCC
// Format 22s: B|A|d7 CCCC
%def op_xor_int_lit16():
%  generic_binop_lit16(instr="xor t1, t1, t2")

// binop lit16 boilerplate
// instr: operands held in t1 and t2, result written to t1.
// instr must not throw. Exceptions to be thrown prior to instr.
// instr must not clobber t3.
//
// The divz_throw flag generates check-and-throw code for div/0.
// Clobbers: t0, t1, t2, t3
%def generic_binop_lit16(instr, divz_throw=False):
   FETCH t2, count=1, signed=1  // t2 := ssssCCCC
   srliw t1, xINST, 12          // t1 := B
   srliw t3, xINST, 8           // t3 := B|A
%  if divz_throw:
     beqz t2, 1f                // Must throw before FETCH_ADVANCE_INST.
%#:
%  get_vreg("t1", "t1")         #  t1 := fp[B]
   and t3, t3, 0xF              // t3 := A
   FETCH_ADVANCE_INST 2         // advance xPC, load xINST
   $instr                       // read t1 and t2, write result to t1.
                                // do not clobber t3!
   GET_INST_OPCODE t2           // t2 holds next opcode
%  set_vreg("t1", "t3", z0="t0")  # fp[A] := t1
   GOTO_OPCODE t2               // continue to next
1:
%  if divz_throw:
     tail common_errDivideByZero
%#:

//
// binop/lit8 vAA, vBB, #+CC
// Format 22b: AA|op CC|BB
//

// add-int/lit8, vAA, vBB, #+CC
// Format 22b: AA|d8, CC|BB
%def op_add_int_lit8():
%  generic_binop_lit8(instr="addw t1, t1, t2")

// rsub-int/lit8, vAA, vBB, #+CC
// Format 22b: AA|d9, CC|BB
// Note: Twos-complement reverse subtraction.
%def op_rsub_int_lit8():
%  generic_binop_lit8(instr="subw t1, t2, t1")

// mul-int/lit8, vAA, vBB, #+CC
// Format 22b: AA|da, CC|BB
%def op_mul_int_lit8():
%  generic_binop_lit8(instr="mulw t1, t1, t2")

// div-int/lit8, vAA, vBB, #+CC
// Format 22b: AA|db, CC|BB
// Note: Twos-complement division, rounded towards zero (that is, truncated to integer). This throws
// ArithmeticException if b == 0.
%def op_div_int_lit8():
%  generic_binop_lit8(instr="divw t1, t1, t2", divz_throw=True)

// rem-int/lit8, vAA, vBB, #+CC
// Format 22b: AA|dc, CC|BB
// Note: Twos-complement remainder after division. The sign of the result is the same as that of a,
// and it is more precisely defined as result == a - (a / b) * b. This throws ArithmeticException if
// b == 0.
%def op_rem_int_lit8():
%  generic_binop_lit8(instr="remw t1, t1, t2", divz_throw=True)

// and-int/lit8, vAA, vBB, #+CC
// Format 22b: AA|dd, CC|BB
%def op_and_int_lit8():
%  generic_binop_lit8(instr="and t1, t1, t2")

// or-int/lit8, vAA, vBB, #+CC
// Format 22b: AA|de, CC|BB
%def op_or_int_lit8():
%  generic_binop_lit8(instr="or t1, t1, t2")

// xor-int/lit8, vAA, vBB, #+CC
// Format 22b: AA|df, CC|BB
%def op_xor_int_lit8():
%  generic_binop_lit8(instr="xor t1, t1, t2")

// shl-int/lit8, vAA, vBB, #+CC
// Format 22b: AA|e0, CC|BB
// Note: SLLW uses t2[4:0] for the shift amount.
%def op_shl_int_lit8():
%  generic_binop_lit8(instr="sllw t1, t1, t2")

// shr-int/lit8, vAA, vBB, #+CC
// Format 22b: AA|e1, CC|BB
// Note: SRAW uses t2[4:0] for the shift amount.
%def op_shr_int_lit8():
%  generic_binop_lit8(instr="sraw t1, t1, t2")

// ushr-int/lit8, vAA, vBB, #+CC
// Format 22b: AA|e2, CC|BB
// Note: SRLW uses t2[4:0] for the shift amount.
%def op_ushr_int_lit8():
%  generic_binop_lit8(instr="srlw t1, t1, t2")

// binop lit8 boilerplate
// instr: operands held in t1 and t2, result written to t1.
// instr must not throw. Exceptions to be thrown prior to instr.
// instr must not clobber t3.
//
// The divz_throw flag generates check-and-throw code for div/0.
// Clobbers: t0, t1, t2, t3
%def generic_binop_lit8(instr, divz_throw=False):
   FETCH t1, count=1, signed=1  // t1 := ssssCC|BB
   srliw t3, xINST, 8           // t3 := AA
   sraiw t2, t1, 8              // t2 := ssssssCC
   andi t1, t1, 0xFF            // t1 := BB
%  if divz_throw:
     beqz t2, 1f                // Must throw before FETCH_ADVANCE_INST.
%#:
%  get_vreg("t1", "t1")         #  t1 := fp[BB]
   FETCH_ADVANCE_INST 2         // advance xPC, load xINST
   $instr                       // read t1 and t2, write result to t1.
                                // do not clobber t3!
   GET_INST_OPCODE t2           // t2 holds next opcode
%  set_vreg("t1", "t3", z0="t0")  # fp[AA] := t1
   GOTO_OPCODE t2               // continue to next
1:
%  if divz_throw:
     tail common_errDivideByZero
%#:
