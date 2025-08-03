%def unused():
   ebreak

// nop
// Format 10x: 00|00
%def op_nop():
   FETCH_ADVANCE_INST 1  // advance xPC, load xINST
   GET_INST_OPCODE t0    // t0 holds next opcode
   GOTO_OPCODE t0        // continue to next

// move vA, vB
// Format 12x: B|A|01
%def op_move(is_object=False, is_wide=False):
   srliw t1, xINST, 12   // t1 := B
   srliw t2, xINST, 8    // t2 := B|A
%  if is_object:
     // Note: leaves a useful breadcrumb if the reference is corrupted, unlike GET_VREG_OBJECT.
%    get_vreg("t1", "t1", is_unsigned=True)  # t1 = fp[B], zext
%  else:
%    get_vreg("t1", "t1", is_wide=is_wide)  # t1 := fp[B]
%#:
   and t2, t2, 0xF       // t2 := A
   FETCH_ADVANCE_INST 1  // advance xPC, load xINST
   GET_INST_OPCODE t3    // t3 holds next opcode
%  if is_object:
     SET_VREG_OBJECT t1, t2, z0=t0  // refs[A] := fp[B]
%  else:
%    set_vreg("t1", "t2", z0="t0", is_wide=is_wide)  # fp[A] := fp[B]
%#:
   GOTO_OPCODE t3       // continue to next

// move/from16 vAA, vBBBB
// Format 22x: AA|16 BBBB
%def op_move_from16(is_object=False, is_wide=False):
   FETCH t1, count=1     // t1 := BBBB
   srliw t2, xINST, 8    // t2 := AA
%  if is_object:
     // Note: leaves a useful breadcrumb if the reference is corrupted, unlike GET_VREG_OBJECT.
%    get_vreg("t1", "t1", is_unsigned=True)  # t1 = fp[BBBB], zext
%  else:
%    get_vreg("t1", "t1", is_wide=is_wide)  # t1 := fp[BBBB]
%#:
   FETCH_ADVANCE_INST 2  // advance xPC, load xINST
   GET_INST_OPCODE t3    // t3 := next opcode
%  if is_object:
     SET_VREG_OBJECT t1, t2, z0=t0  // refs[AA] := fp[BBBB]
%  else:
%    set_vreg("t1", "t2", z0="t0", is_wide=is_wide)  # fp[AA] := fp[BBBB]
%#:
   GOTO_OPCODE t3        // continue to next

// move/16 vAAAA, vBBBB
// Format 32x: 00|03 AAAA BBBB
%def op_move_16(is_object=False, is_wide=False):
   FETCH t1, count=2     // t1 := BBBB
   FETCH t2, count=1     // t2 := AAAA
%  if is_object:
     // Note: leaves a useful breadcrumb if the reference is corrupted, unlike GET_VREG_OBJECT.
%    get_vreg("t1", "t1", is_unsigned=True)  # t1 = fp[BBBB], zext
%  else:
%    get_vreg("t1", "t1", is_wide=is_wide)  # t1 := fp[BBBB]
%#:
   FETCH_ADVANCE_INST 3  // advance xPC, load xINST
   GET_INST_OPCODE t3    // t3 := next opcode
%  if is_object:
     SET_VREG_OBJECT t1, t2, z0=t0  // refs[AAAA] := fp[BBBB]
%  else:
%    set_vreg("t1", "t2", z0="t0", is_wide=is_wide)  # fp[AAAA] := fp[BBBB]
%#:
   GOTO_OPCODE t3        // continue to next

// move-wide vA, vB
// Format 12x: B|A|04
// NOTE: vregs can overlap, e.g. "move-wide v6,v7" or "move-wide v7,v6"
%def op_move_wide():
%  op_move(is_wide=True)

// move-wide/from16 vAA, vBBBB
// Format 22x: AA|05 BBBB
// NOTE: vregs can overlap, e.g. "move-wide v6,v7" or "move-wide v7,v6"
%def op_move_wide_from16():
%  op_move_from16(is_wide=True)

// move-wide/16, vAAAA, vBBBB
// Format 32x: 00|06 AAAA BBBB
// NOTE: vregs can overlap, e.g. "move-wide v6,v7" or "move-wide v7,v6"
%def op_move_wide_16():
%  op_move_16(is_wide=True)

// move-object vA, vB
// Format 12x: B|A|07
%def op_move_object():
%  op_move(is_object=True)

// move-object/from16 vAA, vBBBB
// Format 22x: AA|08 BBBB
%def op_move_object_from16():
%  op_move_from16(is_object=True)

// move-object/16 vAAAA, vBBBB
// Format 32x: 00|09 AAAA BBBB
%def op_move_object_16():
%  op_move_16(is_object=True)

// move-result vAA
// Format 11x: AA|0a
%def op_move_result(is_object=False, is_wide=False):
   srliw t1, xINST, 8    // t1 := AA
   FETCH_ADVANCE_INST 1  // advance xPC, load xINST
   GET_INST_OPCODE t2    // t2 := next opcode
%  if is_object:
     SET_VREG_OBJECT a0, t1, z0=t0  // refs[AA] := a0
%  else:
%    set_vreg("a0", "t1", z0="t0", is_wide=is_wide)  # fp[AA] := a0
%#:
   GOTO_OPCODE t2        // continue to next

// move-result-wide vAA
// Format 11x: AA|0b
%def op_move_result_wide():
%  op_move_result(is_wide=True)

// move-result-object vAA
// Format 11x: AA|0c
%def op_move_result_object():
%  op_move_result(is_object=True)

// move-exception vAA
// Format 11x: AA|0d
%def op_move_exception():
   ld t1, THREAD_EXCEPTION_OFFSET(xSELF)    // t1 := exception object
   srliw t2, xINST, 8                       // t2 := AA
   FETCH_ADVANCE_INST 1                     // advance xPC, load xINST
   SET_VREG_OBJECT t1, t2, z0=t0            // refs[AA] := exception object
   GET_INST_OPCODE t3                       // t3 := next opcode
   sd zero, THREAD_EXCEPTION_OFFSET(xSELF)  // clear exception
   GOTO_OPCODE t3                           // continue to next

// const/4 vA, #+B
// Format 11n: B|A|12
// Clobbers: t0, t1, t2, t3
%def op_const_4():
   slliw t1, xINST, 16     // B as MSB of word
   sraiw t1, t1, 28        // t1 := sssssssB
   slliw t2, xINST, 20     // A as MSB of word
   srliw t2, t2, 28        // t2 := A
   FETCH_ADVANCE_INST 1    // advance xPC, load xINST
   GET_INST_OPCODE t3      // t3 holds next opcode
%  set_vreg("t1", "t2", z0="t0")  # fp[A] := sssssssB
   GOTO_OPCODE t3          // continue to next

// const/16 vAA, #+BBBB
// Format 21s: AA|13 BBBB
// Clobbers: t0, t1, t2, t3
%def op_const_16(is_wide=False):
   FETCH t1, count=1, signed=1
                         // t1 := ssssssssssssBBBB
   srliw t2, xINST, 8    // t2 := AA
   FETCH_ADVANCE_INST 2  // advance xPC, load xINST
   GET_INST_OPCODE t3    // t3 := next opcode
%  set_vreg("t1", "t2", z0="t0", is_wide=is_wide)
                         // fp[AA] := +BBBB
   GOTO_OPCODE t3        // continue to next

// const vAA, #+BBBBBBBB
// Format 31i: AA|14 BBBB(lo) BBBB(hi)
// Clobbers: t0, t1, t2, t3
%def op_const(is_wide=False):
   FETCH t1, count=1, signed=1, width=32
                         // t1 := ssssssssBBBBBBBB
   srliw t2, xINST, 8    // t2 := AA
   FETCH_ADVANCE_INST 3  // advance xPC, load xINST
   GET_INST_OPCODE t3    // t3 := next opcode
%  set_vreg("t1", "t2", z0="t0", is_wide=is_wide)
                         // fp[AA] := +BBBBBBBB
   GOTO_OPCODE t3        // continue to next

// const/high16 vAA, #+BBBB0000
// Format 21h: AA|15 BBBB
// Clobbers: t0, t1, t2, t3
%def op_const_high16():
   FETCH t1, count=1       // t1 := BBBB
   srliw t2, xINST, 8      // t2 := AA
   slliw t1, t1, 16        // t1 := BBBB0000
   FETCH_ADVANCE_INST 2    // advance xPC, load xINST
   GET_INST_OPCODE t3      // t3 := next opcode
%  set_vreg("t1", "t2", z0="t0")  # fp[AA] := BBBB0000
   GOTO_OPCODE t3          // continue to next

// const-wide/16 vAA, #+BBBB
// Format 21s: AA|16 BBBB
%def op_const_wide_16():
%   op_const_16(is_wide=True)

// const-wide/32 vAA, #+BBBBBBBB
// Format 31i: AA|17 BBBB(lo) BBBB(hi)
%def op_const_wide_32():
%   op_const(is_wide=True)

// const-wide vAA, #+BBBBBBBBBBBBBBBB
// Format 51l: AA|18 BBBB(lo) BBBB BBBB BBBB(hi)
%def op_const_wide():
   FETCH t1, count=1, width=64
                         // t1 := BBBBBBBBBBBBBBBB
   srliw t2, xINST, 8    // t2 := AA
   FETCH_ADVANCE_INST 5  // advance xPC, load xINST
   GET_INST_OPCODE t3    // t3 := next opcode
   SET_VREG_WIDE t1, t2, z0=t0
                         // fp[AA] := BBBBBBBBBBBBBBBB
   GOTO_OPCODE t3        // continue to next

// const-wide/high16 vAA, #+BBBB000000000000
// Format 21h: AA|19 BBBB
%def op_const_wide_high16():
   FETCH t1, count=1     // t1 := BBBB
   srliw t2, xINST, 8    // t2 := AA
   slli t1, t1, 48       // t1 := BBBB000000000000
   FETCH_ADVANCE_INST 2  // advance xPC, load xINST
   GET_INST_OPCODE t3    // t3 := next opcode
   SET_VREG_WIDE t1, t2, z0=t0
                         // fp[AA] := BBBB000000000000
   GOTO_OPCODE t3        // continue to next


// const-string vAA, string@BBBB
// Format 21c: AA|1a BBBB
%def op_const_string(jumbo=False):
   // Fast path: string from thread-local cache.
   FETCH_FROM_THREAD_CACHE /*object*/a0, .L${opcode}_slow, t0, t1
   TEST_IF_MARKING t2, .L${opcode}_mark

.L${opcode}_resume:
   srliw t0, xINST, 8  // t0 := AA
%  code_units = "3" if jumbo else "2"
   FETCH_ADVANCE_INST $code_units
   SET_VREG_OBJECT a0, t0, z0=t1
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_mark:
   call art_quick_read_barrier_mark_reg10  // a0, string
   j .L${opcode}_resume
.L${opcode}_slow:
   EXPORT_PC
   mv a0, xSELF
   ld a1, (sp)  // caller ArtMethod*
   mv a2, xPC
   call nterp_load_object  // return a0 := string
   j .L${opcode}_resume


// const-string/jumbo vAA, string@BBBBBBBB
// Format 31c: AA|1b BBBB(lo) BBBB(hi)
%def op_const_string_jumbo():
%  op_const_string(jumbo=True)

// const-class vAA, type@BBBB
// Format 21c: AA|1c BBBB
%def op_const_class():
   // Fast path: klass reference from thread-local cache.
   FETCH_FROM_THREAD_CACHE /*object*/a0, .L${opcode}_slow, t0, t1
   TEST_IF_MARKING t2, .L${opcode}_mark

.L${opcode}_resume:
   srliw t0, xINST, 8  // t0 := AA
   FETCH_ADVANCE_INST 2
   SET_VREG_OBJECT a0, t0, z0=t1
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_mark:
   call art_quick_read_barrier_mark_reg10  // a0, klass
   j .L${opcode}_resume
.L${opcode}_slow:
   EXPORT_PC
   mv a0, xSELF
   ld a1, (sp)  // caller ArtMethod*
   mv a2, xPC
   call nterp_get_class  // return a0 := klass
   j .L${opcode}_resume


// const-method-handle vAA, method_handle@BBBB
// Format 21c: AA|fe BBBB
%def op_const_method_handle():
   // Method handle and method type are not cached, just call helper directly.
   EXPORT_PC
   mv a0, xSELF
   ld a1, (sp)  // caller ArtMethod*
   mv a2, xPC
   call nterp_load_object  // return a0 := method handle or method type
   srliw t0, xINST, 8  // t0 := AA
   FETCH_ADVANCE_INST 2
   SET_VREG_OBJECT a0, t0, z0=t1
   GET_INST_OPCODE t0
   GOTO_OPCODE t0


// const-method-type vAA, proto@BBBB
// Format 21c: AA|ff BBBB
%def op_const_method_type():
%  op_const_method_handle()


// monitor-enter vAA
// Format 11x: AA|1d
// Acquire the monitor for the indicated object.
%def op_monitor_enter():
   EXPORT_PC
   srliw t0, xINST, 8  // t0 := AA
   GET_VREG_OBJECT a0, t0
   call art_quick_lock_object  // arg a0
   FETCH_ADVANCE_INST 1
   GET_INST_OPCODE t0
   GOTO_OPCODE t0


// monitor-exit vAA
// Format 11x: AA|1e
// Release the monitor for the indicated object.
// Note: If this instruction needs to throw an exception, it must do so as if the pc has
//       already advanced past the instruction. It may be useful to think of this as the instruction
//       successfully executing (in a sense), and the exception getting thrown after the instruction
//       but before the next one gets a chance to run. This definition makes it possible for a
//       method to use a monitor cleanup catch-all (e.g., finally) block as the monitor cleanup for
//       that block itself, as a way to handle the arbitrary exceptions that might get thrown due to
//       the historical implementation of Thread.stop(), while still managing to have proper monitor
//       hygiene.
%def op_monitor_exit():
   EXPORT_PC
   srliw t0, xINST, 8  // t0 := AA
   GET_VREG_OBJECT a0, t0
   call art_quick_unlock_object  // arg a0
   FETCH_ADVANCE_INST 1
   GET_INST_OPCODE t0
   GOTO_OPCODE t0


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

