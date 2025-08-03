%def op_aget(load="movl", multiplier="4", data_offset="MIRROR_INT_ARRAY_DATA_OFFSET", wide="0", is_object="0"):
/*
 * Array get.  vAA <- vBB[vCC].
 *
 * for: aget, aget-boolean, aget-byte, aget-char, aget-short, aget-wide, aget-object
 *
 */
    /* op vAA, vBB, vCC */
    movzbl  2(rPC), %eax                    # eax <- BB
    movzbl  3(rPC), %ecx                    # ecx <- CC
    GET_VREG %eax, %eax                     # eax <- vBB (array object)
    GET_VREG %ecx, %ecx                     # ecx <- vCC (requested index)
    testl   %eax, %eax                      # null array object?
    je      common_errNullObject            # bail if so
    cmpl    MIRROR_ARRAY_LENGTH_OFFSET(%eax), %ecx
    jae     common_errArrayIndex            # index >= length, bail.
    .if $wide
    movq $data_offset(%eax,%ecx,8), %xmm0
    SET_WIDE_FP_VREG %xmm0, rINST           # vAA <- xmm0
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
    .elseif $is_object
    testb $$READ_BARRIER_TEST_VALUE, GRAY_BYTE_OFFSET(%eax)
    $load   $data_offset(%eax,%ecx,$multiplier), %eax
    jnz 2f
    UNPOISON_HEAP_REF eax  // Affects flags, so we cannot unpoison before the jnz.
1:
    SET_VREG_OBJECT %eax, rINST
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
2:
    UNPOISON_HEAP_REF eax
    // reg00 is eax
    call art_quick_read_barrier_mark_reg00
    jmp 1b
    .else
    $load   $data_offset(%eax,%ecx,$multiplier), %eax
    SET_VREG %eax, rINST
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
    .endif

%def op_aget_boolean():
%  op_aget(load="movzbl", multiplier="1", data_offset="MIRROR_BOOLEAN_ARRAY_DATA_OFFSET")

%def op_aget_byte():
%  op_aget(load="movsbl", multiplier="1", data_offset="MIRROR_BYTE_ARRAY_DATA_OFFSET")

%def op_aget_char():
%  op_aget(load="movzwl", multiplier="2", data_offset="MIRROR_CHAR_ARRAY_DATA_OFFSET")

%def op_aget_object():
%  op_aget(load="movl", multiplier="4", data_offset="MIRROR_OBJECT_ARRAY_DATA_OFFSET", is_object="1")

%def op_aget_short():
%  op_aget(load="movswl", multiplier="2", data_offset="MIRROR_SHORT_ARRAY_DATA_OFFSET")

%def op_aget_wide():
%  op_aget(load="", multiplier="8", data_offset="MIRROR_WIDE_ARRAY_DATA_OFFSET", wide="1")

%def op_aput(rINST_reg="rINST", store="movl", multiplier="4", data_offset="MIRROR_INT_ARRAY_DATA_OFFSET", wide="0"):
/*
 * Array put.  vBB[vCC] <- vAA.
 *
 * for: aput, aput-boolean, aput-byte, aput-char, aput-short, aput-wide
 *
 */
    /* op vAA, vBB, vCC */
    movzbl  2(rPC), %eax                     # eax <- BB
    movzbl  3(rPC), %ecx                     # ecx <- CC
    GET_VREG %eax, %eax                      # eax <- vBB (array object)
    GET_VREG %ecx, %ecx                      # ecx <- vCC (requested index)
    testl   %eax, %eax                       # null array object?
    je      common_errNullObject             # bail if so
    cmpl    MIRROR_ARRAY_LENGTH_OFFSET(%eax), %ecx
    jae     common_errArrayIndex             # index >= length, bail.
    .if $wide
    GET_WIDE_FP_VREG %xmm0, rINST            # xmm0 <- vAA
    movq    %xmm0, $data_offset(%eax,%ecx,8) # vBB[vCC] <- xmm0
    .else
    GET_VREG rINST, rINST
    $store    $rINST_reg, $data_offset(%eax,%ecx,$multiplier)
    .endif
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 2

%def op_aput_boolean():
%  op_aput(rINST_reg="rINSTbl", store="movb", multiplier="1", data_offset="MIRROR_BOOLEAN_ARRAY_DATA_OFFSET")

%def op_aput_byte():
%  op_aput(rINST_reg="rINSTbl", store="movb", multiplier="1", data_offset="MIRROR_BYTE_ARRAY_DATA_OFFSET")

%def op_aput_char():
%  op_aput(rINST_reg="rINSTw", store="movw", multiplier="2", data_offset="MIRROR_CHAR_ARRAY_DATA_OFFSET")

%def op_aput_short():
%  op_aput(rINST_reg="rINSTw", store="movw", multiplier="2", data_offset="MIRROR_SHORT_ARRAY_DATA_OFFSET")

%def op_aput_wide():
%  op_aput(rINST_reg="", store="", multiplier="8", data_offset="MIRROR_WIDE_ARRAY_DATA_OFFSET", wide="1")

%def op_aput_object():
    EXPORT_PC                               # for the art_quick_aput_obj call
    movzbl  2(rPC), %eax                    # eax <- BB
    movzbl  3(rPC), %ecx                    # ecx <- CC
    GET_VREG %eax, %eax                     # eax <- vBB (array object)
    GET_VREG %ecx, %ecx                     # ecx <- vCC (requested index)
    testl   %eax, %eax                      # null array object?
    je      common_errNullObject            # bail if so
    cmpl    MIRROR_ARRAY_LENGTH_OFFSET(%eax), %ecx
    jae     common_errArrayIndex            # index >= length, bail.
    GET_VREG %edx, rINST
    call art_quick_aput_obj
    RESTORE_IBASE                           # edx got overwritten, restore it
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 2

%def op_array_length():
/*
 * Return the length of an array.
 */
    movl    rINST, %eax                     # eax <- BA
    sarl    $$4, rINST                      # rINST <- B
    GET_VREG %ecx, rINST                    # ecx <- vB (object ref)
    testl   %ecx, %ecx                      # is null?
    je      common_errNullObject
    andb    $$0xf, %al                      # eax <- A
    movl    MIRROR_ARRAY_LENGTH_OFFSET(%ecx), rINST
    SET_VREG rINST, %eax
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 1

%def op_fill_array_data():
    /* fill-array-data vAA, +BBBBBBBB */
    EXPORT_PC
    movl    2(rPC), %ecx                    # ecx <- BBBBbbbb
    leal    (rPC,%ecx,2), ARG0              # ARG0 <- PC + BBBBbbbb*2
    GET_VREG ARG1, rINST                    # ARG1 <- vAA (array object)
    call    art_quick_handle_fill_data
    RESTORE_IBASE
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 3

%def op_filled_new_array(helper="nterp_filled_new_array"):
/*
 * Create a new array with elements filled from registers.
 *
 * for: filled-new-array, filled-new-array/range
 */
    /* op vB, {vD, vE, vF, vG, vA}, class@CCCC */
    /* op {vCCCC..v(CCCC+AA-1)}, type@BBBB */
    EXPORT_PC
    movl    rSELF:THREAD_SELF_OFFSET, ARG0
    movl    (%esp), ARG1
    movl    rFP, ARG2
    movl    rPC, ARG3
    call    SYMBOL($helper)
    ADVANCE_PC_FETCH_AND_GOTO_NEXT 3

%def op_filled_new_array_range():
%  op_filled_new_array(helper="nterp_filled_new_array_range")

%def op_new_array():
  jmp NterpNewArray
