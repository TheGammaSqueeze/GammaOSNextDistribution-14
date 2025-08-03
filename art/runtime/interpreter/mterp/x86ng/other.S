%def unused():
    int3

%def op_const():
    /* const vAA, #+BBBBbbbb */
    movl    2(rPC), %eax                    # grab all 32 bits at once
    SET_VREG %eax, rINST                    # vAA<- eax
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 3

%def op_const_16():
    /* const/16 vAA, #+BBBB */
    movswl  2(rPC), %ecx                    # ecx <- ssssBBBB
    SET_VREG %ecx, rINST                    # vAA <- ssssBBBB
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 2

%def op_const_4():
    /* const/4 vA, #+B */
    movsbl  rINSTbl, %eax                   # eax <-ssssssBx
    andl    MACRO_LITERAL(0xf), rINST       # rINST <- A
    sarl    MACRO_LITERAL(4), %eax
    SET_VREG %eax, rINST
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 1

%def op_const_high16():
    /* const/high16 vAA, #+BBBB0000 */
    movzwl  2(rPC), %eax                    # eax <- 0000BBBB
    sall    MACRO_LITERAL(16), %eax         # eax <- BBBB0000
    SET_VREG %eax, rINST                    # vAA <- eax
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 2

%def op_const_object(jumbo="0", helper="nterp_load_object"):
   // Fast-path which gets the object from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label="2f")
   cmpl MACRO_LITERAL(0), rSELF:THREAD_READ_BARRIER_MARK_REG00_OFFSET
   jne 3f
1:
   SET_VREG_OBJECT %eax, rINST             # vAA <- value
   .if $jumbo
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 3
   .else
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
   .endif
2:
   EXPORT_PC
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   call SYMBOL($helper)
   jmp 1b
3:
   // 00 is %eax
   call art_quick_read_barrier_mark_reg00
   jmp 1b

%def op_const_class():
%  op_const_object(jumbo="0", helper="nterp_get_class")

%def op_const_method_handle():
%  op_const_object(jumbo="0")

%def op_const_method_type():
%  op_const_object(jumbo="0")

%def op_const_string():
   /* const/string vAA, String@BBBB */
%  op_const_object(jumbo="0")

%def op_const_string_jumbo():
   /* const/string vAA, String@BBBBBBBB */
%  op_const_object(jumbo="1")

%def op_const_wide():
    /* const-wide vAA, #+HHHHhhhhBBBBbbbb */
    movl    2(rPC), %eax                    # eax <- lsw
    movzbl  rINSTbl, %ecx                   # ecx <- AA
    movl    6(rPC), rINST                   # rINST <- msw
    SET_VREG %eax, %ecx
    SET_VREG_HIGH  rINST, %ecx
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 5

%def op_const_wide_16():
    /* const-wide/16 vAA, #+BBBB */
    movswl  2(rPC), %eax                    # eax <- ssssBBBB
    movl    rIBASE, %ecx                    # preserve rIBASE (cdq trashes it)
    cdq                                     # rIBASE:eax <- ssssssssssssBBBB
    SET_VREG_HIGH rIBASE, rINST             # store msw
    SET_VREG %eax, rINST                    # store lsw
    movl    %ecx, rIBASE                    # restore rIBASE
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 2

%def op_const_wide_32():
    /* const-wide/32 vAA, #+BBBBbbbb */
    movl    2(rPC), %eax                    # eax <- BBBBbbbb
    movl    rIBASE, %ecx                    # preserve rIBASE (cdq trashes it)
    cdq                                     # rIBASE:eax <- ssssssssssssBBBB
    SET_VREG_HIGH rIBASE, rINST             # store msw
    SET_VREG %eax, rINST                    # store lsw
    movl    %ecx, rIBASE                    # restore rIBASE
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 3

%def op_const_wide_high16():
    /* const-wide/high16 vAA, #+BBBB000000000000 */
    movzwl  2(rPC), %eax                    # eax <- 0000BBBB
    sall    $$16, %eax                      # eax <- BBBB0000
    SET_VREG_HIGH %eax, rINST               # v[AA+1] <- eax
    xorl    %eax, %eax
    SET_VREG %eax, rINST                    # v[AA+0] <- eax
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 2

%def op_monitor_enter():
/*
 * Synchronize on an object.
 */
    /* monitor-enter vAA */
    EXPORT_PC
    GET_VREG ARG0, rINST
    call art_quick_lock_object
    RESTORE_IBASE
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 1

%def op_monitor_exit():
/*
 * Unlock an object.
 *
 * Exceptions that occur when unlocking a monitor need to appear as
 * if they happened at the following instruction.  See the Dalvik
 * instruction spec.
 */
    /* monitor-exit vAA */
    EXPORT_PC
    GET_VREG ARG0, rINST
    call art_quick_unlock_object
    RESTORE_IBASE
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 1

%def op_move(is_object="0"):
    /* for move, move-object, long-to-int */
    /* op vA, vB */
    movl    rINST, %eax                     # eax <- BA
    andb    $$0xf, %al                      # eax <- A
    shrl    $$4, rINST                      # rINST <- B
    GET_VREG %ecx, rINST
    .if $is_object
    SET_VREG_OBJECT %ecx, %eax              # fp[A] <- fp[B]
    .else
    SET_VREG %ecx, %eax                     # fp[A] <- fp[B]
    .endif
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 1

%def op_move_16(is_object="0"):
    /* for: move/16, move-object/16 */
    /* op vAAAA, vBBBB */
    movzwl  4(rPC), %ecx                    # ecx <- BBBB
    movzwl  2(rPC), %eax                    # eax <- AAAA
    GET_VREG %ecx, %ecx
    .if $is_object
    SET_VREG_OBJECT %ecx, %eax              # fp[A] <- fp[B]
    .else
    SET_VREG %ecx, %eax                     # fp[A] <- fp[B]
    .endif
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 3

%def op_move_exception():
    /* move-exception vAA */
    movl    rSELF:THREAD_EXCEPTION_OFFSET, %eax
    SET_VREG_OBJECT %eax, rINST            # fp[AA] <- exception object
    movl    $$0, rSELF:THREAD_EXCEPTION_OFFSET
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 1

%def op_move_from16(is_object="0"):
    /* for: move/from16, move-object/from16 */
    /* op vAA, vBBBB */
    movzwl  2(rPC), %eax                    # eax <- BBBB
    GET_VREG %ecx, %eax                     # ecx <- fp[BBBB]
    .if $is_object
    SET_VREG_OBJECT %ecx, rINST             # fp[A] <- fp[B]
    .else
    SET_VREG %ecx, rINST                    # fp[A] <- fp[B]
    .endif
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 2

%def op_move_object():
%  op_move(is_object="1")

%def op_move_object_16():
%  op_move_16(is_object="1")

%def op_move_object_from16():
%  op_move_from16(is_object="1")

%def op_move_result(is_object="0"):
    /* for: move-result, move-result-object */
    /* op vAA */
    .if $is_object
    SET_VREG_OBJECT %eax, rINST             # fp[A] <- fp[B]
    .else
    SET_VREG %eax, rINST                    # fp[A] <- fp[B]
    .endif
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 1

%def op_move_result_object():
%  op_move_result(is_object="1")

%def op_move_result_wide():
    /* move-result-wide vAA */
    SET_VREG %eax, rINST
    LOAD_WIDE_RETURN %eax
    SET_VREG_HIGH %eax, rINST
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 1

%def op_move_wide():
    /* move-wide vA, vB */
    /* NOTE: regs can overlap, e.g. "move v6,v7" or "move v7,v6" */
    movzbl  rINSTbl, %ecx                   # ecx <- BA
    sarl    $$4, rINST                      # rINST <- B
    andb    $$0xf, %cl                      # ecx <- A
    GET_WIDE_FP_VREG %xmm0, rINST           # xmm0 <- v[B]
    SET_WIDE_FP_VREG %xmm0, %ecx            # v[A] <- xmm0
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 1

%def op_move_wide_16():
    /* move-wide/16 vAAAA, vBBBB */
    /* NOTE: regs can overlap, e.g. "move v6,v7" or "move v7,v6" */
    movzwl  4(rPC), %ecx                    # ecx<- BBBB
    movzwl  2(rPC), %eax                    # eax<- AAAA
    GET_WIDE_FP_VREG %xmm0, %ecx            # xmm0 <- v[B]
    SET_WIDE_FP_VREG %xmm0, %eax            # v[A] <- xmm0
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 3

%def op_move_wide_from16():
    /* move-wide/from16 vAA, vBBBB */
    /* NOTE: regs can overlap, e.g. "move v6,v7" or "move v7,v6" */
    movzwl  2(rPC), %ecx                    # ecx <- BBBB
    movzbl  rINSTbl, %eax                   # eax <- AAAA
    GET_WIDE_FP_VREG %xmm0, %ecx            # xmm0 <- v[B]
    SET_WIDE_FP_VREG %xmm0, %eax            # v[A] <- xmm0
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 2

%def op_nop():
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 1

%def op_unused_3e():
%  unused()

%def op_unused_3f():
%  unused()

%def op_unused_40():
%  unused()

%def op_unused_41():
%  unused()

%def op_unused_42():
%  unused()

%def op_unused_43():
%  unused()

%def op_unused_73():
%  unused()

%def op_unused_79():
%  unused()

%def op_unused_7a():
%  unused()

%def op_unused_e3():
%  unused()

%def op_unused_e4():
%  unused()

%def op_unused_e5():
%  unused()

%def op_unused_e6():
%  unused()

%def op_unused_e7():
%  unused()

%def op_unused_e8():
%  unused()

%def op_unused_e9():
%  unused()

%def op_unused_ea():
%  unused()

%def op_unused_eb():
%  unused()

%def op_unused_ec():
%  unused()

%def op_unused_ed():
%  unused()

%def op_unused_ee():
%  unused()

%def op_unused_ef():
%  unused()

%def op_unused_f0():
%  unused()

%def op_unused_f1():
%  unused()

%def op_unused_f2():
%  unused()

%def op_unused_f3():
%  unused()

%def op_unused_f4():
%  unused()

%def op_unused_f5():
%  unused()

%def op_unused_f6():
%  unused()

%def op_unused_f7():
%  unused()

%def op_unused_f8():
%  unused()

%def op_unused_f9():
%  unused()

%def op_unused_fc():
%  unused()

%def op_unused_fd():
%  unused()
