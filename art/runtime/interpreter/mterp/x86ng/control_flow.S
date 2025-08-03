%def bincmp(revcmp=""):
/*
 * Generic two-operand compare-and-branch operation.  Provide a "revcmp"
 * fragment that specifies the *reverse* comparison to perform, e.g.
 * for "if-le" you would use "gt".
 *
 * For: if-eq, if-ne, if-lt, if-ge, if-gt, if-le
 */
    /* if-cmp vA, vB, +CCCC */
    movl    rINST, %ecx                     # rcx <- A+
    sarl    $$4, rINST                      # rINST <- B
    andb    $$0xf, %cl                      # rcx <- A
    GET_VREG %eax, %ecx                     # eax <- vA
    cmpl    VREG_ADDRESS(rINST), %eax       # compare (vA, vB)
    j${revcmp} 1f
    movswl  2(rPC), rINST                   # Get signed branch offset
    BRANCH
1:
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 2

%def zcmp(revcmp=""):
/*
 * Generic one-operand compare-and-branch operation.  Provide a "revcmp"
 * fragment that specifies the *reverse* comparison to perform, e.g.
 * for "if-le" you would use "gt".
 *
 * for: if-eqz, if-nez, if-ltz, if-gez, if-gtz, if-lez
 */
    /* if-cmp vAA, +BBBB */
    cmpl    $$0, VREG_ADDRESS(rINST)        # compare (vA, 0)
    j${revcmp} 1f
    movswl  2(rPC), rINST                   # fetch signed displacement
    BRANCH
1:
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 2

%def op_goto():
/*
 * Unconditional branch, 8-bit offset.
 *
 * The branch distance is a signed code-unit offset, which we need to
 * double to get a byte offset.
 */
    /* goto +AA */
    movsbl  rINSTbl, rINST                  # rINST <- ssssssAA
    BRANCH

%def op_goto_16():
/*
 * Unconditional branch, 16-bit offset.
 *
 * The branch distance is a signed code-unit offset, which we need to
 * double to get a byte offset.
 */
    /* goto/16 +AAAA */
    movswl  2(rPC), rINST                   # rINST <- ssssAAAA
    BRANCH

%def op_goto_32():
/*
 * Unconditional branch, 32-bit offset.
 *
 * The branch distance is a signed code-unit offset, which we need to
 * double to get a byte offset.
 */
    /* goto/32 +AAAAAAAA */
    movl  2(rPC), rINST                   # rINST <- AAAAAAAA
    BRANCH

%def op_if_eq():
%  bincmp(revcmp="ne")

%def op_if_eqz():
%  zcmp(revcmp="ne")

%def op_if_ge():
%  bincmp(revcmp="l")

%def op_if_gez():
%  zcmp(revcmp="l")

%def op_if_gt():
%  bincmp(revcmp="le")

%def op_if_gtz():
%  zcmp(revcmp="le")

%def op_if_le():
%  bincmp(revcmp="g")

%def op_if_lez():
%  zcmp(revcmp="g")

%def op_if_lt():
%  bincmp(revcmp="ge")

%def op_if_ltz():
%  zcmp(revcmp="ge")

%def op_if_ne():
%  bincmp(revcmp="e")

%def op_if_nez():
%  zcmp(revcmp="e")

%def op_packed_switch(func="NterpDoPackedSwitch"):
/*
 * Handle a packed-switch or sparse-switch instruction.  In both cases
 * we decode it and hand it off to a helper function.
 *
 * We don't really expect backward branches in a switch statement, but
 * they're perfectly legal, so we check for them here.
 *
 * for: packed-switch, sparse-switch
 */
    /* op vAA, +BBBB */
    movl    2(rPC), ARG0                # eax <- BBBBbbbb
    leal    (rPC,ARG0,2), ARG0          # eax <- PC + BBBBbbbb*2
    GET_VREG ARG1, rINST                # ecx <- vAA
    pushl   ARG1
    pushl   ARG0
    call    SYMBOL($func)
    addl MACRO_LITERAL(8), %esp
    RESTORE_IBASE
    FETCH_INST_CLEAR_OPCODE
    movl  %eax, rINST
    BRANCH

/*
 * Return a 32-bit value.
 */
%def op_return(is_object="0"):
    GET_VREG %eax, rINST                    # eax <- vAA
    .if !$is_object
    // In case we're going back to compiled code, put the
    // result also in a xmm register.
    movd %eax, %xmm0
    .endif
    CFI_REMEMBER_STATE
    movl -4(rREFS), %esp
    DROP_PARAMETERS_SAVES
    CFI_DEF_CFA(esp, CALLEE_SAVES_SIZE)
    RESTORE_ALL_CALLEE_SAVES
    ret
    CFI_RESTORE_STATE
    NTERP_DEF_CFA CFI_REFS

%def op_return_object():
%  op_return(is_object="1")

%def op_return_void():
    // Thread fence for constructor is a no-op on x86_64.
    CFI_REMEMBER_STATE
    movl -4(rREFS), %esp
    DROP_PARAMETERS_SAVES
    CFI_DEF_CFA(esp, CALLEE_SAVES_SIZE)
    RESTORE_ALL_CALLEE_SAVES
    ret
    CFI_RESTORE_STATE
    NTERP_DEF_CFA CFI_REFS

%def op_return_wide():
    // In case we're going back to compiled code, put the
    // result also in a xmm register.
    GET_WIDE_FP_VREG %xmm0, rINST
    GET_VREG %eax, rINST        # eax <- vAA
    GET_VREG_HIGH %edx, rINST   # edx <- vAA
    CFI_REMEMBER_STATE
    movl    -4(rREFS), %esp
    DROP_PARAMETERS_SAVES
    CFI_DEF_CFA(esp, CALLEE_SAVES_SIZE)
    RESTORE_ALL_CALLEE_SAVES
    ret
    CFI_RESTORE_STATE
    NTERP_DEF_CFA CFI_REFS

%def op_sparse_switch():
%  op_packed_switch(func="NterpDoSparseSwitch")

%def op_throw():
  EXPORT_PC
  GET_VREG ARG0, rINST                   # eax <- vAA (exception object)
  movl rSELF:THREAD_SELF_OFFSET, ARG1
  call SYMBOL(art_quick_deliver_exception)
  int3
