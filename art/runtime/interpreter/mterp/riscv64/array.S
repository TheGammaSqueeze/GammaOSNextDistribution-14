// array-length vA, vB
// Format 12x: B|A|21
// Store in the given destination register the length of the indicated array, in entries
%def op_array_length():
   srliw t0, xINST, 12     // t0 := B
   GET_VREG_OBJECT t0, t0  // t0 := refs[B]
   beqz t0, 1f
   srliw t1, xINST, 8      // t1 := B|A
   FETCH_ADVANCE_INST 1
   andi t1, t1, 0xF        // t1 := A
   GET_INST_OPCODE t3
   lw t2, MIRROR_ARRAY_LENGTH_OFFSET(t0)
%  set_vreg("t2", "t1", z0="t0")
   GOTO_OPCODE t3
1:
   tail common_errNullObject


// new-array vA, vB, type@CCCC
// Format 22c: B|A|23 CCCC
// Construct a new array of the indicated type and size. The type must be an array type.
%def op_new_array():
   EXPORT_PC
   srliw s8, xINST, 8   // s8 := B|A
   srliw s7, xINST, 12  // s7 := B
   andi s8, s8, 0xF     // s8 := A
   FETCH_FROM_THREAD_CACHE /*resolved klass*/a0, .L${opcode}_miss, t0, t1
   TEST_IF_MARKING t0, .L${opcode}_mark
.L${opcode}_resume:

%  get_vreg("a1", "s7")  # a1 := fp[B] length
   ld t0, THREAD_ALLOC_ARRAY_ENTRYPOINT_OFFSET(xSELF)
   jalr t0     // args a0 (klass), a1 (length)
               // return a0 := new-array
   fence w, w  // constructor fence; main.S has details

   SET_VREG_OBJECT a0, s8, z0=t0  // refs[A] := new-array
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_mark:
   call art_quick_read_barrier_mark_reg10  // a0, klass
   j .L${opcode}_resume

.L${opcode}_miss:
   EXPORT_PC
   mv a0, xSELF
   ld a1, (sp)  // caller ArtMethod*
   mv a2, xPC
   call nterp_get_class
   j .L${opcode}_resume




// filled-new-array {vC, vD, vE, vF, vG}, type@BBBB
// Format 35c: A|G|24 BBBB F|E|D|C
// Construct an array of the given type and size, filling it with the supplied contents. The type
// must be an array type. The array's contents must be single-word (that is, no arrays of long or
// double, but reference types are acceptable). The constructed instance is stored as a "result" in
// the same way that the method invocation instructions store their results, so the constructed
// instance must be moved to a register with an immediately subsequent move-result-object
// instruction (if it is to be used).
%def op_filled_new_array(is_range=False):
   EXPORT_PC
   mv a0, xSELF
   ld a1, (sp)  // a1 := caller ArtMethod*
   mv a2, xFP   // a2 := vreg array
   mv a3, xPC
%  if is_range:
     call nterp_filled_new_array_range  // args a0, a1, a2, a3
%  else:
     call nterp_filled_new_array  // args a0, a1, a2, a3
%#:
   FETCH_ADVANCE_INST 3
   GET_INST_OPCODE t0
   GOTO_OPCODE t0


// filled-new-array/range {vCCCC .. vNNNN}, type@BBBB
// where NNNN = CCCC + AA - 1
// Format 3rc: AA|25 BBBB CCCC
// Construct an array of the given type and size, filling it with the supplied contents.
// Clarifications and restrictions are the same as filled-new-array, described above.
%def op_filled_new_array_range():
%  op_filled_new_array(is_range=True)


// fill-array-data vAA, +BBBBBBBB
// Format 31t: AA|26 BBBB(lo) BBBB(hi)
// Fill the given array with the indicated data. The reference must be to an array of primitives,
// and the data table must match it in type and must contain no more elements than will fit in the
// array. That is, the array may be larger than the table, and if so, only the initial elements of
// the array are set, leaving the remainder alone.
%def op_fill_array_data():
   EXPORT_PC
   srliw t0, xINST, 8      // t0 := AA
   FETCH t1, count=1, signed=1, width=32
                           // t1 := ssssssssBBBBBBBB
   GET_VREG_OBJECT a1, t0  // a1 := refs[AA] (array ref)
   // +BBBBBBBB offset is in code units. Multiply by 2 for byte offset against dex PC.
   sh1add a0, t1, xPC      // a0 := payload address
   call art_quick_handle_fill_data  // args a0, a1
   FETCH_ADVANCE_INST 3
   GET_INST_OPCODE t0
   GOTO_OPCODE t0


// Common setup across APUT and AGET variants.
// Sets \array, \index, and \length registers.
// Branches to null handler and out-of-bounds handler.
%def array_prelude(array, index, length, null_label, oob_label):
   FETCH $index, count=1           // index := CC|BB
   andi $array, $index, 0xFF       // array := BB
   GET_VREG_OBJECT $array, $array  // array := refs[BB], array obj
   beqz $array, $null_label

   srliw $index, $index, 8         // index := CC
%  get_vreg(index, index)          # index := fp[CC]
   lw $length, MIRROR_ARRAY_LENGTH_OFFSET($array)  // length (signed 32b)
   bgeu $index, $length, $oob_label  // Unsigned comparison also catches negative index.


// aget vAA, vBB, vCC
// Format 23x: AA|44 CC|BB
// vAA := vBB[vCC]
%def op_aget(width=32, zext=False):
%  array_prelude(array="t0", index="a0", length="a1", null_label=f".L{opcode}_null", oob_label=f".L{opcode}_oob")
       // t0 := vBB array object, a0 := vCC index, a1 := array length

%  if width == 8 and zext:
     add t0, a0, t0
     lbu t0, MIRROR_BOOLEAN_ARRAY_DATA_OFFSET(t0)
%  elif width == 8:
     add t0, a0, t0
     lb t0, MIRROR_BYTE_ARRAY_DATA_OFFSET(t0)
%  elif width == 16 and zext:
     sh1add t0, a0, t0
     lhu t0, MIRROR_CHAR_ARRAY_DATA_OFFSET(t0)
%  elif width == 16:
     sh1add t0, a0, t0
     lh t0, MIRROR_SHORT_ARRAY_DATA_OFFSET(t0)
%  elif width == 32:
     sh2add t0, a0, t0
     lw t0, MIRROR_INT_ARRAY_DATA_OFFSET(t0)
%  elif width == 64:
     sh3add t0, a0, t0
     ld t0, MIRROR_WIDE_ARRAY_DATA_OFFSET(t0)
%  else:
%    assert False, width
%#:
                       // t0 := *(array obj + data offset + idx * elem_size)
   srliw t1, xINST, 8  // t1 := AA
%  set_vreg("t0", "t1", z0="t2", width=width)

   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_null:
   tail common_errNullObject
.L${opcode}_oob:
   tail common_errArrayIndex  // args a0 (index), a1 (length)


// aget-wide vAA, vBB, vCC
// Format 23x: AA|45 CC|BB
%def op_aget_wide():
%  op_aget(width=64)


// aget-object vAA, vBB, vCC
// Format 23x: AA|46 CC|BB
%def op_aget_object():
%  array_prelude(array="t0", index="a0", length="a1", null_label=f".L{opcode}_null", oob_label=f".L{opcode}_oob")
       // t0 := vBB array object, a0 := vCC index, a1 := array length

   sh2add t0, a0, t0
   lwu a0, MIRROR_OBJECT_ARRAY_DATA_OFFSET(t0)
       // a0 := *(array obj + data offset + idx * elem_size)
   UNPOISON_HEAP_REF a0
   TEST_IF_MARKING t1, .L${opcode}_mark
.L${opcode}_mark_resume:
   srliw t1, xINST, 8  // t1 := AA
   SET_VREG_OBJECT a0, t1, z0=t2

   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_mark:
   call art_quick_read_barrier_mark_reg10  // a0, object elem
   j .L${opcode}_mark_resume
.L${opcode}_null:
   tail common_errNullObject
.L${opcode}_oob:
   tail common_errArrayIndex  // args a0 (index), a1 (length)


// aget-boolean vAA, vBB, vCC
// Format 23x: AA|47 CC|BB
%def op_aget_boolean():
%  op_aget(width=8, zext=True)


// aget-byte vAA, vBB, vCC
// Format 23x: AA|48 CC|BB
%def op_aget_byte():
%  op_aget(width=8)


// aget_char vAA, vBB, vCC
// Format 23x: AA|49 CC|BB
%def op_aget_char():
%  op_aget(width=16, zext=True)


// aget-short vAA, vBB, vCC
// Format 23x: AA|4a CC|BB
%def op_aget_short():
%  op_aget(width=16)


// aput vAA, vBB, vCC
// Format 23x: AA|4b CC|BB
// vBB[vCC] := vAA
%def op_aput(width=32, zext=False):
%  array_prelude(array="t0", index="t1", length="t2", null_label=f".L{opcode}_null", oob_label=f".L{opcode}_oob")
       // t0 := vBB array object, t1 := vCC zext index, t2 := array length

   srliw t2, xINST, 8      // t2 := AA
%  get_vreg("t2", "t2", width=width)  # t2 := fp[AA]
%  if width == 8 and zext:
     add t0, t1, t0
     sb t2, MIRROR_BOOLEAN_ARRAY_DATA_OFFSET(t0)
%  elif width == 8:
     add t0, t1, t0
     sb t2, MIRROR_BYTE_ARRAY_DATA_OFFSET(t0)
%  elif width == 16 and zext:
     sh1add t0, t1, t0
     sh t2, MIRROR_CHAR_ARRAY_DATA_OFFSET(t0)
%  elif width == 16:
     sh1add t0, t1, t0
     sh t2, MIRROR_SHORT_ARRAY_DATA_OFFSET(t0)
%  elif width == 32:
     sh2add t0, t1, t0
     sw t2, MIRROR_INT_ARRAY_DATA_OFFSET(t0)
%  elif width == 64:
     sh3add t0, t1, t0
     sd t2, MIRROR_WIDE_ARRAY_DATA_OFFSET(t0)
%  else:
%    assert False, width
%#:
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_null:
   tail common_errNullObject
.L${opcode}_oob:
   sext.w a0, t1
   mv a1, t2
   tail common_errArrayIndex  // args a0 (index), a1 (length)


// aput-wide vAA, vBB, vCC
// Format 23x: AA|4c CC|BB
%def op_aput_wide():
%  op_aput(width=64)


// aput-object vAA, vBB, vCC
// Format 23x: AA|4d CC|BB
%def op_aput_object():
%  array_prelude(array="a0", index="a1", length="a2", null_label=f".L{opcode}_null", oob_label=f".L{opcode}_oob")
       // a0 := vBB array object, a1 := vCC zext index, a2 := array length

   EXPORT_PC
   srliw a2, xINST, 8       // a2 := AA
   GET_VREG_OBJECT a2, a2   // a2 := fp[AA]
   sext.w a1, a1            // a1 := sext index
   call art_quick_aput_obj  // args a0 (array obj), a1 (index), a2 (obj)

   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_null:
   tail common_errNullObject
.L${opcode}_oob:
   sext.w a0, a1
   mv a1, a2
   tail common_errArrayIndex  // args a0 (index), a1 (length)

// aput-boolean vAA, vBB, vCC
// Format 23x: AA|4e CC|BB
%def op_aput_boolean():
%  op_aput(width=8, zext=True)


// aput-byte vAA, vBB, vCC
// Format 23x: AA|4f CC|BB
%def op_aput_byte():
%  op_aput(width=8)


// aput-char vAA, vBB, vCC
// Format 23x: AA|50 CC|BB
%def op_aput_char():
%  op_aput(width=16, zext=True)


// aput-short vAA, vBB, vCC
// Format 23x: AA|51 CC|BB
%def op_aput_short():
%  op_aput(width=16)
