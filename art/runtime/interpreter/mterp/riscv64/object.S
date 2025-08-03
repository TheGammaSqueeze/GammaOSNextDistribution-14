// check-cast vAA, type@BBBB
// Format 21c: AA|1f BBBB
// Throw a ClassCastException if the reference in the given register cannot be cast to the indicated
// type.
%def op_check_cast():
   FETCH_FROM_THREAD_CACHE /*expected klass*/a1, .L${opcode}_miss, t0, t1
.L${opcode}_miss_resume:

   srliw t0, xINST, 8         // t0 := AA
%  get_vreg("a0", "t0", is_unsigned=True)  # a0 := fp[AA], zext
   beqz a0, .L${opcode}_next  // null
   lwu a2, MIRROR_OBJECT_CLASS_OFFSET(a0)  // a2 := actual klass
   UNPOISON_HEAP_REF a2
   // Fast path: compare without read barrier.
   bne a1, a2, .L${opcode}_slow

.L${opcode}_next:
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_miss:
   EXPORT_PC
   mv a0, xSELF
   ld a1, (sp)  // caller ArtMethod*
   mv a2, xPC
   call nterp_get_class
   mv a1, a0
   j .L${opcode}_miss_resume

.L${opcode}_slow:
   // A0 and A1 in position for quick call.
%  slow_path = add_slow_path(op_check_cast_slow_path, "t0", "t1", "t2")
   tail $slow_path  // slow offset exceeds branch imm
                    // args a0, a1, a2


// Checks cases for (1) interface, (2) array, and (3) super classes.
// Hardcoded: a0 (obj), a1 (expected klass), a2 (actual klass)
//
// Note. We don't do read barriers for simplicity. However, this means that fetched objects may be a
// from-space reference. That's OK as we only fetch constant information from the references. This
// also means that some of the comparisons below may lead to false negative due to stale data, so
// all negative cases must pass through the runtime, via potential read barrier.
%def op_check_cast_slow_path(z0, z1, z2):
   // Interface check: cut to runtime.
   lwu $z0, MIRROR_CLASS_ACCESS_FLAGS_OFFSET(a1)
   BRANCH_IF_BIT_SET $z0, $z0, MIRROR_CLASS_IS_INTERFACE_FLAG_BIT, .L${opcode}_runtime

   // Array check handled below.
   lwu $z0, MIRROR_CLASS_COMPONENT_TYPE_OFFSET(a1)
   // Defer z0 unpoison to array path.
   bnez $z0, .L${opcode}_array

   // Super check: find expected class, else cut to runtime.
.L${opcode}_super:
   lwu a2, MIRROR_CLASS_SUPER_CLASS_OFFSET(a2)
   UNPOISON_HEAP_REF a2
   beq a2, a1, .L${opcode}_slow_next
   bnez a2, .L${opcode}_super

.L${opcode}_runtime:
   TEST_IF_MARKING $z0, .L${opcode}_mark
.L${opcode}_mark_resume:
   EXPORT_PC
   call art_quick_check_instance_of  // args a0 (obj), a1 (expected klass)

   // Advancement logic replicated here for branch distance.
.L${opcode}_slow_next:
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE $z0
   GOTO_OPCODE $z0

.L${opcode}_mark:
   call art_quick_read_barrier_mark_reg11  // a1, expected klass
   j .L${opcode}_mark_resume

.L${opcode}_array:
   UNPOISON_HEAP_REF $z0          // z0 = expected.component
   lwu $z1, MIRROR_CLASS_COMPONENT_TYPE_OFFSET(a2)  // z1 := actual.component
   beqz $z1, .L${opcode}_runtime  // null: actual not an array
   UNPOISON_HEAP_REF $z1
   lwu $z2, MIRROR_CLASS_SUPER_CLASS_OFFSET($z0)  // z2 := expected.component.super
   // z2 can skip unpoison for null check
   bnez $z2, .L${opcode}_runtime  // super type exists
   // expected.component.super is null: expected is either Object[] or primitive array.
   lhu $z2, MIRROR_CLASS_OBJECT_PRIMITIVE_TYPE_OFFSET($z0)  // z2 := expected.component.primitive
   bnez $z2, .L${opcode}_runtime  // expected's component is primitive
   lwu $z2, MIRROR_CLASS_OBJECT_PRIMITIVE_TYPE_OFFSET($z1)  // z2 := actual.component.primitive
   bnez $z2, .L${opcode}_runtime  // actual's component is primitive
   // Here, z0 is Object, and z1 is a subclass of Object.
   j .L${opcode}_slow_next


// instance-of vA, vB, type@CCCC
// Format 22c: B|A|20 CCCC
// vA := 1 if vB instance-of CCCC, else 0
// Store in the given destination register 1 if the indicated reference is an instance of the given
// type, or 0 if not.
%def op_instance_of():
   srliw s7, xINST, 8    // s7 := B|A
   srliw s8, xINST, 12   // s8 := B
   andi s7, s7, 0xF      // s7 := A, used in slow path
   FETCH_FROM_THREAD_CACHE /*expected klass*/ a1, .L${opcode}_miss, t0, t1
.L${opcode}_miss_resume:

%  get_vreg("a0", "s8", is_unsigned=True)  # a0 := fp[B], zext
   beqz a0, .L${opcode}_next  // a0 = null = dst value "false"
   lwu a2, MIRROR_OBJECT_CLASS_OFFSET(a0)  // a2 := actual klass
   UNPOISON_HEAP_REF a2
   // Fast path: compare without read barrier.
   bne a1, a2, .L${opcode}_slow

   li a0, 1  // dst value "true"

.L${opcode}_next:
%  set_vreg("a0", "s7", z0="t1")  # fp[A] := a0
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_miss:
   EXPORT_PC
   mv a0, xSELF
   ld a1, (sp)  // caller ArtMethod*
   mv a2, xPC
   call nterp_get_class
   mv a1, a0
   j .L${opcode}_miss_resume

.L${opcode}_slow:
   // A0 and A1 in position for quick call.
%  slow_path = add_slow_path(op_instance_of_slow_path, "s7", "t0", "t1", "t2")
   tail $slow_path  // slow offset exceeds branch imm
                    // args a0, a1, a2


// Checks cases for (1) interface, (2) array, and (3) super classes.
// Hardcoded: a0 (obj), a1 (expected klass), a2 (actual klass)
//
// Npte. If marking, don't bother with read barrier calls - cut to runtime. This arrangement allows
// the (non marking) super class fast path's negative case to skip the read barrier and runtime
// call, and correctly diagnose the situation with fp[A] := 0.
%def op_instance_of_slow_path(vA, z0, z1, z2):
   TEST_IF_MARKING $z0, .L${opcode}_runtime_with_read_barrier

   // Interface check: cut to runtime.
   lwu $z0, MIRROR_CLASS_ACCESS_FLAGS_OFFSET(a1)
   BRANCH_IF_BIT_SET $z0, $z0, MIRROR_CLASS_IS_INTERFACE_FLAG_BIT, .L${opcode}_runtime

   // Array check handled below.
   lwu $z0, MIRROR_CLASS_COMPONENT_TYPE_OFFSET(a1)
   // Defer z0 unpoison to array path.
   bnez $z0, .L${opcode}_array

   // Super check: find klass up the hierarchy.
.L${opcode}_super:
   lwu a2, MIRROR_CLASS_SUPER_CLASS_OFFSET(a2)
   UNPOISON_HEAP_REF a2
   beq a2, a1, .L${opcode}_super_exit
   bnez a2, .L${opcode}_super
.L${opcode}_super_exit:
   snez a0, a2  // a0 := 1 if (a1 = a2 != null), else 0 (because a2 = null)

.L${opcode}_slow_next:
%  set_vreg("a0", vA, z0=z0)  # fp[A] := a0
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE $z0
   GOTO_OPCODE $z0

.L${opcode}_runtime_with_read_barrier:
   call art_quick_read_barrier_mark_reg11  // a1, expected klass
.L${opcode}_runtime:
   EXPORT_PC
   call artInstanceOfFromCode  // args a0 (obj), a1 (expected klass)
       // return a0: 1 if true, else 0
   j .L${opcode}_slow_next

.L${opcode}_array:
   UNPOISON_HEAP_REF $z0           // z0 = expected.component
   lwu $z1, MIRROR_CLASS_SUPER_CLASS_OFFSET($z0)  // z1 := expected.component.super
   // z1 can skip unpoison for null check
   bnez $z1, .L${opcode}_runtime   // super type exists
   // Here, expected.component.super is null: expected is either Object[] or primitive array.
   lwu a0, MIRROR_CLASS_COMPONENT_TYPE_OFFSET(a2)  // a0 := actual.component
   beqz a0, .L${opcode}_slow_next  // actual not an array, a0 = null = dst value "false"
   UNPOISON_HEAP_REF a0
   lhu $z1, MIRROR_CLASS_OBJECT_PRIMITIVE_TYPE_OFFSET($z0)  // z1 := expected.component.primitive
   lhu $z2, MIRROR_CLASS_OBJECT_PRIMITIVE_TYPE_OFFSET(a0)   // z2 := actual.component.primitive
   or a0, $z1, $z2  // a0 := 0 if z1 = z2 = 0, else non-zero (Primitive::Type enum)
   seqz a0, a0      // a0 := 1 if both are class types, else 0
   // Here, when a0 = 1, expected.component is Object, and actual.component is a subclass of Object.
   j .L${opcode}_slow_next


// new-instance vAA, type@BBBB
// Format 21c: AA|22 BBBB
// Construct a new instance of the indicated type, storing a reference to it in the destination. The
// type must refer to a non-array class.
%def op_new_instance():
   EXPORT_PC
   srliw s7, xINST, 8  // s7 := AA
   FETCH_FROM_THREAD_CACHE /*resolved klass*/a0, .L${opcode}_miss, t0, t1
   TEST_IF_MARKING t0, .L${opcode}_mark
.L${opcode}_mark_resume:

   ld t0, THREAD_ALLOC_OBJECT_ENTRYPOINT_OFFSET(xSELF)
   jalr t0     // arg a0 (klass)
               // return a0 := new-obj
   fence w, w  // constructor fence; main.S has details

.L${opcode}_miss_resume:
   SET_VREG_OBJECT a0, s7, z0=t0  // refs[AA] := new-obj
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_mark:
   call art_quick_read_barrier_mark_reg10  // a0, klass
   j .L${opcode}_mark_resume

.L${opcode}_miss:
   EXPORT_PC
   mv a0, xSELF
   ld a1, (sp)  // caller ArtMethod*
   mv a2, xPC
   call nterp_allocate_object
       // return a0 := new-obj, plus cache entry is updated with the resolved klass
   j .L${opcode}_miss_resume


// *** iget ***

%def load(dst, src, width, zext):
%  if width == 8 and zext:
     lbu $dst, ($src)
%  elif width == 8:
     lb $dst, ($src)
%  elif width == 16 and zext:
     lhu $dst, ($src)
%  elif width == 16:
     lh $dst, ($src)
%  elif width == 32:
     lw $dst, ($src)
%  elif width == 64:
     ld $dst, ($src)
%  else:
%    assert False, width
%#:


%def store(src, dst, width):
%  if width == 8:
     sb $src, ($dst)
%  elif width == 16:
     sh $src, ($dst)
%  elif width == 32:
     sw $src, ($dst)
%  elif width == 64:
     sd $src, ($dst)
%  else:
%    assert False, width
%#:


// iget vA, vB, field@CCCC
// Format 22c: B|A|52 CCCC
// vA := vB.field
%def op_iget(width=32, zext=False):
   srliw s8, xINST, 8
   srliw s7, xINST, 12     // s7 := B
   andi s8, s8, 0xF        // s8 := A

   // Fast path: NterpGetInstanceFieldOffset's byte offset from thread-local cache.
   FETCH_FROM_THREAD_CACHE /*field_offset*/a0, .L${opcode}_slow, t1, t2
.L${opcode}_slow_resume:

%  get_vreg("t0", "s7", is_unsigned=True)  # t0 := holder
   beqz t0, .L${opcode}_null
   add t0, a0, t0          // t0 := field addr
%  load(dst="t1", src="t0", width=width, zext=zext)
                           // t1 := value
   FETCH_ADVANCE_INST 2
%  set_vreg("t1", "s8", z0="t0", width=width)
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_slow:
   mv a0, xSELF
   ld a1, (sp)  // a1 := caller ArtMethod*
   mv a2, xPC
   mv a3, zero
   EXPORT_PC
   call nterp_get_instance_field_offset  // result a0 := field_offset

   // Test for volatile (negative value).
   bgez a0, .L${opcode}_slow_resume
%  volatile_path = add_slow_path(op_iget_volatile, width, zext, "s7", "s8", "t0", "t1")
   tail $volatile_path

.L${opcode}_null:
   tail common_errNullObject


%def op_iget_volatile(width, zext, holder, dst, z0, z1):
%  get_vreg(z0, holder, is_unsigned=True)  # z0 := holder
   beqz $z0, .L${opcode}_volatile_null
   sub $z0, $z0, a0           // z0 := field addr (holder - (-offset))
   // Atomic load: "fence rw,rw ; LOAD ; fence r,rw"
   fence rw, rw
%  load(dst=z1, src=z0, width=width, zext=zext)
   fence r, rw
                              // t1 := value
   FETCH_ADVANCE_INST 2
%  set_vreg(z1, dst, z0=z0, width=width)
   GET_INST_OPCODE $z0
   GOTO_OPCODE $z0

.L${opcode}_volatile_null:
   tail common_errNullObject


// iget-wide vA, vB, field@CCCC
// Format 22c: B|A|53 CCCC
%def op_iget_wide():
%  op_iget(width=64)


// iget-object vA, vB, field@CCCC
// Format 22c: B|A|54 CCCC
%def op_iget_object():
   srliw s8, xINST, 8
   srliw s7, xINST, 12     // s7 := B
   andi s8, s8, 0xF        // s8 := A

   // Fast path: NterpGetInstanceFieldOffset's byte offset from thread-local cache.
   FETCH_FROM_THREAD_CACHE /*field_offset*/a0, .L${opcode}_slow, t1, t2
.L${opcode}_slow_resume:

%  get_vreg("t0", "s7", is_unsigned=True)  # t0 := holder
   beqz t0, .L${opcode}_null
   add t0, a0, t0          // t0 := field addr
   lwu a0, (t0)            // a0 := object
   UNPOISON_HEAP_REF a0
   TEST_IF_MARKING t1, .L${opcode}_mark
.L${opcode}_mark_resume:

   FETCH_ADVANCE_INST 2
   SET_VREG_OBJECT a0, s8, z0=t0
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_mark:
   call art_quick_read_barrier_mark_reg10  // a0, object
   j .L${opcode}_mark_resume

.L${opcode}_slow:
%  slow_path = add_slow_path(op_iget_object_slow_path, "s7", "s8", "t0", "t1")
   tail $slow_path

.L${opcode}_null:
   tail common_errNullObject


%def op_iget_object_slow_path(holder, dst, z0, z1):
   mv a0, xSELF
   ld a1, (sp)  // a1 := caller ArtMethod*
   mv a2, xPC
   mv a3, zero
   EXPORT_PC
   call nterp_get_instance_field_offset  // result a0 := field_offset

   // Test for volatile (negative value).
   bltz a0, .L${opcode}_volatile
   tail .L${opcode}_slow_resume  // resume offset exceeds branch imm

.L${opcode}_volatile:
%  get_vreg(z0, holder, is_unsigned=True)  # z0 := holder
   beqz $z0, .L${opcode}_volatile_null
   sub $z0, $z0, a0           // z0 := field addr (holder - (-offset))
   // Atomic load: "fence rw,rw ; LOAD ; fence r,rw"
   fence rw, rw
   lwu a0, ($z0)              // a0 := object
   fence r, rw
   UNPOISON_HEAP_REF a0
   TEST_IF_MARKING t1, .L${opcode}_volatile_mark
.L${opcode}_volatile_mark_resume:

   FETCH_ADVANCE_INST 2
   SET_VREG_OBJECT a0, $dst, z0=$z0
   GET_INST_OPCODE $z0
   GOTO_OPCODE $z0

.L${opcode}_volatile_mark:
   call art_quick_read_barrier_mark_reg10  // a0, object
   j .L${opcode}_volatile_mark_resume

.L${opcode}_volatile_null:
   tail common_errNullObject


// iget-boolean vA, vB, field@CCCC
// Format 22c: B|A|55 CCCC
%def op_iget_boolean():
%  op_iget(width=8, zext=True)


// iget-byte vA, vB, field@CCCC
// Format 22c: B|A|56 CCCC
%def op_iget_byte():
%  op_iget(width=8)


// iget-char vA, vB, field@CCCC
// Format 22c: B|A|57 CCCC
%def op_iget_char():
%  op_iget(width=16, zext=True)


// iget-short vA, vB, field@CCCC
// Format 22c: B|A|58 CCCC
%def op_iget_short():
%  op_iget(width=16)


// *** iput ***

// iput vA, vB, field@CCCC
// Format 22c: B|A|59 CCCC
// vB.field := vA
%def op_iput(width=32):
   srliw s8, xINST, 8
   srliw s7, xINST, 12     // s7 := B
   andi s8, s8, 0xF        // s8 := A
%  get_vreg("s8", "s8", width=width)
                           // s8 := value, held across slow path call

   // Fast path: NterpGetInstanceFieldOffset's byte offset from thread-local cache.
   FETCH_FROM_THREAD_CACHE /*resolved_field*/a0, .L${opcode}_slow, t0, t1
.L${opcode}_slow_resume:

%  get_vreg("t0", "s7", is_unsigned=True)  # t0 := holder
   beqz t0, .L${opcode}_null
   add t0, a0, t0          // t0 := field addr
   FETCH_ADVANCE_INST 2
%  store(src="s8", dst="t0", width=width)
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_slow:
   mv a0, xSELF
   ld a1, (sp)  // a1 := caller ArtMethod*
   mv a2, xPC
   mv a3, zero
   EXPORT_PC
   call nterp_get_instance_field_offset  // result a0 := field_offset

   // Test for volatile (negative value).
   bgez a0, .L${opcode}_slow_resume
%  volatile_path = add_slow_path(op_iput_volatile, width, "s7", "s8", "t0", "t1")
   tail $volatile_path

.L${opcode}_null:
   tail common_errNullObject


%def op_iput_volatile(width, holder, value, z0, z1):
%  get_vreg(z0, holder, is_unsigned=True)  # z0 := holder
   beqz $z0, .L${opcode}_volatile_null
   sub $z0, $z0, a0              // z0 := field addr (holder - (-offset))
   // Ensure the volatile store is released.
%  if width == 8:
     fence rw, w
     sb $value, ($z0)
     fence rw, rw
%  elif width == 16:
     fence rw, w
     sh $value, ($z0)
     fence rw, rw
%  elif width == 32:
     amoswap.w.rl zero, $value, ($z0)
%  elif width == 64:
     amoswap.d.rl zero, $value, ($z0)
%  else:
%    assert False, width
%#:

   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE $z0
   GOTO_OPCODE $z0

.L${opcode}_volatile_null:
   tail common_errNullObject


// iput-wide vA, vB, field@CCCC
// Format 22c: B|A|5a CCCC
%def op_iput_wide():
%  op_iput(width=64)


// iput-object vA, vB, field@CCCC
// Format 22c: B|A|5b CCCC
%def op_iput_object():
   srliw s8, xINST, 8
   srliw s7, xINST, 12     // s7 := B
   andi s8, s8, 0xF        // s8 := A
%  get_vreg("s9", "s8", is_unsigned=True)  # s9 := reference

   // Fast path: NterpGetInstanceFieldOffset's byte offset from thread-local cache.
   FETCH_FROM_THREAD_CACHE /*resolved_field*/a0, .L${opcode}_slow, t0, t1
.L${opcode}_slow_resume:   // s9 := reference (slow path only)

%  get_vreg("t0", "s7", is_unsigned=True)  # t0 := holder
   beqz t0, .L${opcode}_null
   add t1, a0, t0          // t1 := field addr
   POISON_HEAP_REF s9      // Poisoning maps null to null for the null check in write barrier.
   sw s9, (t1)
%  object_write_barrier(value="s9", holder="t0", z0="t1", z1="t2", uniq=f"{opcode}")

   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_slow:
%  slow_path = add_slow_path(op_iput_object_slow_path, "s7", "s8", "s9", "t0", "t1", "t2")
   tail $slow_path

.L${opcode}_null:
   tail common_errNullObject


%def op_iput_object_slow_path(holder, src, value, z0, z1, z2):
   mv a0, xSELF
   ld a1, (sp)  // a1 := caller ArtMethod*
   mv a2, xPC
   mv a3, $value
   EXPORT_PC
   call nterp_get_instance_field_offset  // result a0 := field_offset

   // Reload value, object may have moved.
%  get_vreg(value, src, is_unsigned=True)  # value := fp[A] zext

   // Test for volatile (negative value).
   bltz a0, .L${opcode}_volatile
   tail .L${opcode}_slow_resume  // resume offset exceeds branch imm

.L${opcode}_volatile:
%  get_vreg(z0, holder, is_unsigned=True)  # z0 := holder
   beqz $z0, .L${opcode}_volatile_null
   sub $z1, $z0, a0              // z1 := field addr (holder - (-offset))
   // Ensure the volatile store is released.
   POISON_HEAP_REF $value  // Poisoning maps null to null for the null check in write barrier.
   amoswap.w.rl zero, $value, ($z1)
%  object_write_barrier(value=value, holder=z0, z0=z1, z1=z2, uniq=f"slow_{opcode}")

   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE $z0
   GOTO_OPCODE $z0

.L${opcode}_volatile_null:
   tail common_errNullObject


// iput-boolean vA, vB, field@CCCC
// Format 22c: B|A|5c CCCC
%def op_iput_boolean():
%  op_iput(width=8)


// iput-byte vA, vB, field@CCCC
// Format 22c: B|A|5d CCCC
%def op_iput_byte():
%  op_iput(width=8)


// iput-char vA, vB, field@CCCC
// Format 22c: B|A|5e CCCC
%def op_iput_char():
%  op_iput(width=16)


// iput-short vA, vB, field@CCCC
// Format 22c: B|A|5f CCCC
%def op_iput_short():
%  op_iput(width=16)


// *** sget ***

.macro CLEAR_STATIC_VOLATILE_MARKER reg
    andi \reg, \reg, ~0x1
.endm

// sget vAA, field@BBBB
// Format 21c: AA|60 BBBB
// vAA := klass.field
%def op_sget(width=32, zext=False):
   srliw s7, xINST, 8  // s7 := AA (live through volatile path)
   // Fast path: NterpGetStaticField's resolved_field from thread-local cache.
   // Stores cache value in a0 to match slow path's return from NterpGetStaticField.
   FETCH_FROM_THREAD_CACHE /*resolved_field*/a0, .L${opcode}_slow, t0, t1
.L${opcode}_slow_resume:

   lwu t0, ART_FIELD_OFFSET_OFFSET(a0)           // t0 := field offset
   lwu a0, ART_FIELD_DECLARING_CLASS_OFFSET(a0)  // a0 := holder
   TEST_IF_MARKING t2, .L${opcode}_mark
.L${opcode}_mark_resume:

   add t0, t0, a0      // t0 := field addr, after possible a0 update
%  load(dst="t1", src="t0", width=width, zext=zext)
                       // t1 := value
   FETCH_ADVANCE_INST 2
%  set_vreg("t1", "s7", z0="t0", width=width)
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_mark:
   call art_quick_read_barrier_mark_reg10  // a0, holder
   j .L${opcode}_mark_resume

.L${opcode}_slow:
   mv a0, xSELF
   ld a1, (sp)  // a1 := caller ArtMethod*
   mv a2, xPC
   mv a3, zero
   EXPORT_PC
   call nterp_get_static_field  // result a0 := resolved_field

   // Test for volatile bit
   slli t0, a0, 63
   bgez t0, .L${opcode}_slow_resume
%  volatile_path = add_slow_path(op_sget_volatile, width, zext, "s7", "t0", "t1")
   tail $volatile_path


// Volatile static load.
// Temporaries: z0, z1, z2
%def op_sget_volatile(width, zext, dst_vreg, z0, z1):
   CLEAR_STATIC_VOLATILE_MARKER a0
   lwu $z0, ART_FIELD_OFFSET_OFFSET(a0)          // z0 := field offset
   lwu a0, ART_FIELD_DECLARING_CLASS_OFFSET(a0)  // a0 := holder
   TEST_IF_MARKING $z1, .L${opcode}_volatile_mark
.L${opcode}_volatile_mark_resume:

   add $z0, $z0, a0  // z0 := field addr, after possible a0 update
   // Atomic load: "fence rw,rw ; LOAD ; fence r,rw"
   fence rw, rw
%  load(dst=z1, src=z0, width=width, zext=zext)
   fence r, rw
       // z1 := value
   FETCH_ADVANCE_INST 2
%  set_vreg(z1, dst_vreg, z0=z0, width=width)
   GET_INST_OPCODE $z0
   GOTO_OPCODE $z0

.L${opcode}_volatile_mark:
   call art_quick_read_barrier_mark_reg10  // a0, holder
   j .L${opcode}_volatile_mark_resume


// sget-wide vAA, field@BBBB
// Format 21c: AA|61 BBBB
%def op_sget_wide():
%  op_sget(width=64)


// sget-object vAA, field@BBBB
// Format 21c: AA|62 BBBB
// Variant for object load contains extra logic for GC mark.
%def op_sget_object():
   srliw s7, xINST, 8  // s7 := AA (live through volatile path)
   // Fast path: NterpGetStaticField's resolved_field from thread-local cache.
   // Stores cache value in a0 to match slow path's return from NterpGetStaticField.
   FETCH_FROM_THREAD_CACHE /*resolved_field*/a0, .L${opcode}_slow, t0, t1
.L${opcode}_slow_resume:

   lwu t0, ART_FIELD_OFFSET_OFFSET(a0)           // t0 := field offset
   lwu a0, ART_FIELD_DECLARING_CLASS_OFFSET(a0)  // a0 := holder
   TEST_IF_MARKING t1, .L${opcode}_mark

   add t0, t0, a0      // t0 := field addr
   lwu a0, (t0)        // a0 := value (ref)
   UNPOISON_HEAP_REF a0

.L${opcode}_mark_resume:
   FETCH_ADVANCE_INST 2
   SET_VREG_OBJECT a0, s7, z0=t0
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_mark:
   call art_quick_read_barrier_mark_reg10  // a0, holder
   add t0, t0, a0      // t0 := field addr, after a0 update
   lwu a0, (t0)        // a0 := value (ref)
   UNPOISON_HEAP_REF a0
   call art_quick_read_barrier_mark_reg10  // a0, object
   j .L${opcode}_mark_resume

.L${opcode}_slow:
%  slow_path = add_slow_path(op_sget_object_slow_path, "s7", "t0", "t1")
   tail $slow_path


// Static load, object variant. Contains both slow path and volatile path
// due to handler size limitation in op_sget_object.
// Hardcoded: a0, a1, a2, a3, xSELF, xPC, xINST, xFP, xREFS
// Temporaries: z0, z1
%def op_sget_object_slow_path(dst_vreg, z0, z1):
   mv a0, xSELF
   ld a1, (sp)  // a1 := caller ArtMethod*
   mv a2, xPC
   mv a3, zero
   EXPORT_PC
   call nterp_get_static_field  // result a0 := resolved_field

   // Test for volatile bit
   slli $z0, a0, 63
   bltz $z0, .L${opcode}_volatile
   tail .L${opcode}_slow_resume  // resume offset exceeds branch imm

.L${opcode}_volatile:
   CLEAR_STATIC_VOLATILE_MARKER a0
   lwu $z0, ART_FIELD_OFFSET_OFFSET(a0)          // z0 := field offset
   lwu a0, ART_FIELD_DECLARING_CLASS_OFFSET(a0)  // a0 := holder
   TEST_IF_MARKING $z1, .L${opcode}_volatile_mark

   add $z0, $z0, a0  // z0 := field addr
   fence rw, rw
   lwu a0, ($z0)  // Atomic ref load: "fence rw,rw, ; LOAD ; fence r,rw"
   fence r, rw
   UNPOISON_HEAP_REF a0

.L${opcode}_volatile_mark_resume:
   FETCH_ADVANCE_INST 2
   SET_VREG_OBJECT a0, $dst_vreg, z0=$z0
   GET_INST_OPCODE $z0
   GOTO_OPCODE $z0

.L${opcode}_volatile_mark:
   call art_quick_read_barrier_mark_reg10  // a0, holder
   add $z0, $z0, a0  // z0 := field addr, after a0 update
   fence rw, rw
   lwu a0, ($z0)  // Atomic ref load: "fence rw,rw, ; LOAD ; fence r,rw"
   fence r, rw
   UNPOISON_HEAP_REF a0
   call art_quick_read_barrier_mark_reg10  // a0, object
   j .L${opcode}_volatile_mark_resume


// sget-boolean vAA, field@BBBB
// Format 21c: AA|63 BBBB
%def op_sget_boolean():
%  op_sget(width=8, zext=True)


// sget-byte vAA, field@BBBB
// Format 21c: AA|64 BBBB
%def op_sget_byte():
%  op_sget(width=8)


// sget-char vAA, field@BBBB
// Format 21c: AA|65 BBBB
%def op_sget_char():
%  op_sget(width=16, zext=True)


// sget-short vAA, field@BBBB
// Format 21c: AA|66 BBBB
%def op_sget_short():
%  op_sget(width=16)


// *** sput ***

// sput vAA, field@BBBB
// Format 21c: AA|67 BBBB
// klass.field := vAA
%def op_sput(width=32):
   srliw t2, xINST, 8  // t2 := AA
%  get_vreg("s7", "t2", width=width)
                       // s7 := value, held across slow path call

   // Fast path: NterpGetStaticField's resolved_field from thread-local cache.
   // Stores cache value in a0 to match slow path's return from NterpGetStaticField.
   FETCH_FROM_THREAD_CACHE /*resolved_field*/a0, .L${opcode}_slow, t0, t1
.L${opcode}_slow_resume:

   lwu t0, ART_FIELD_OFFSET_OFFSET(a0)
   lwu a0, ART_FIELD_DECLARING_CLASS_OFFSET(a0)  // a0 := holder
   TEST_IF_MARKING t1, .L${opcode}_mark
.L${opcode}_mark_resume:

   add t0, t0, a0  // t0 := field addr, after possible a0 update
   FETCH_ADVANCE_INST 2
%  store(src="s7", dst="t0", width=width)
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_mark:
   call art_quick_read_barrier_mark_reg10  // a0, holder
   j .L${opcode}_mark_resume

.L${opcode}_slow:
   mv a0, xSELF
   ld a1, (sp)
   mv a2, xPC
   mv a3, zero
   EXPORT_PC
   call nterp_get_static_field  // result a0 := resolved_field

   // Test for volatile bit
   slli t0, a0, 63
   bgez t0, .L${opcode}_slow_resume
%  volatile_path = add_slow_path(op_sput_volatile, width, "s7", "t0", "t1")
   tail $volatile_path


// Volatile static store.
// Temporaries: z0, z1
%def op_sput_volatile(width, value, z0, z1):
   CLEAR_STATIC_VOLATILE_MARKER a0
   lwu $z0, ART_FIELD_OFFSET_OFFSET(a0)          // z0 := field offset
   lwu a0, ART_FIELD_DECLARING_CLASS_OFFSET(a0)  // a0 := holder
   TEST_IF_MARKING $z1, .L${opcode}_volatile_mark
.L${opcode}_volatile_mark_resume:

   add $z0, $z0, a0                              // z0 := field addr, after possible a0 update
   // Ensure the volatile store is released.
%  if width == 8:
     fence rw, w
     sb $value, ($z0)
     fence rw, rw
%  elif width == 16:
     fence rw, w
     sh $value, ($z0)
     fence rw, rw
%  elif width == 32:
     amoswap.w.rl zero, $value, ($z0)
%  elif width == 64:
     amoswap.d.rl zero, $value, ($z0)
%  else:
%    assert False, width
%#:

   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE $z0
   GOTO_OPCODE $z0

.L${opcode}_volatile_mark:
   call art_quick_read_barrier_mark_reg10  // a0, holder
   j .L${opcode}_volatile_mark_resume


// sput-wide vAA, field@BBBB
// Format 21c: AA|68 BBBB
%def op_sput_wide():
%  op_sput(width=64)


// sput-object vAA, field@BBBB
// Format 21c: AA|69 BBBB
%def op_sput_object():
   srliw s7, xINST, 8      // s7 := AA (live through slow path)
%  get_vreg("s8", "s7", is_unsigned=True)  # s8 := reference, replaced in slow path

   // Fast path: NterpGetStaticField's resolved_field from thread-local cache.
   // Stores cache value in a0 to match slow path's return from NterpGetStaticField.
   FETCH_FROM_THREAD_CACHE /*resolved_field*/a0, .L${opcode}_slow, t0, t1
.L${opcode}_slow_resume:   // s8 := reference (slow path only)

   lwu t0, ART_FIELD_OFFSET_OFFSET(a0)
   lwu a0, ART_FIELD_DECLARING_CLASS_OFFSET(a0)  // a0 := holder
   TEST_IF_MARKING t1, .L${opcode}_mark
.L${opcode}_mark_resume:

   add t0, t0, a0          // t0 := field addr, after possible a0 update
   POISON_HEAP_REF s8      // Poisoning maps null to null for the null check in write barrier.
   sw s8, (t0)             // store reference
%  object_write_barrier(value="s8", holder="a0", z0="t0", z1="t1", uniq=f"{opcode}")
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE t0
   GOTO_OPCODE t0

.L${opcode}_mark:
   call art_quick_read_barrier_mark_reg10  // a0, holder
   j .L${opcode}_mark_resume

.L${opcode}_slow:
%  slow_path = add_slow_path(op_sput_object_slow_path, "s7", "s8", "t0", "t1")
   tail $slow_path  // slow path offset exceeds regular branch imm in FETCH_FROM_THREAD_CACHE


// Static store, object variant. Contains both slow path and volatile path
// due to handler size limitation in op_sput_object.
// Hardcoded: a0, a1, a2, a3, xSELF, xPC, xINST, xFP, xREFS
// Temporaries z0, z1
%def op_sput_object_slow_path(src_vreg, value, z0, z1):
   // Args for nterp_get_static_field
   mv a0, xSELF
   ld a1, (sp)
   mv a2, xPC
   mv a3, $value
   EXPORT_PC
   call nterp_get_static_field  // result a0 := resolved_field

   // Reload value, it may have moved.
%  get_vreg(value, src_vreg, is_unsigned=True)  # value := fp[AA], zext

   // Test for volatile bit
   slli $z0, a0, 63
   bltz $z0, .L${opcode}_volatile
   tail .L${opcode}_slow_resume  // resume offset exceeds branch imm

.L${opcode}_volatile:
   CLEAR_STATIC_VOLATILE_MARKER a0
   lwu $z0, ART_FIELD_OFFSET_OFFSET(a0)          // z0 := field offset
   lwu a0, ART_FIELD_DECLARING_CLASS_OFFSET(a0)  // a0 := holder
   TEST_IF_MARKING $z1, .L${opcode}_volatile_mark
.L${opcode}_volatile_mark_resume:

   add $z0, $z0, a0  // z0 := field addr, after possible a0 update
   // Ensure the volatile store is released.
   // \value must NOT be the destination register, the destination gets clobbered!
   // \value's original value is used in the write barrier below.
   POISON_HEAP_REF $value  // Poisoning maps null to null for the null check in write barrier.
   amoswap.w.rl zero, $value, ($z0)
%  object_write_barrier(value=value, holder="a0", z0=z0, z1=z1, uniq=f"slow_{opcode}")

   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE $z0
   GOTO_OPCODE $z0

.L${opcode}_volatile_mark:
   call art_quick_read_barrier_mark_reg10  // a0, holder
   j .L${opcode}_volatile_mark_resume


%def object_write_barrier(value, holder, z0, z1, uniq):
   beqz $value, .L${uniq}_skip_write_barrier  // No object, skip out.
   ld $z0, THREAD_CARD_TABLE_OFFSET(xSELF)
   srli $z1, $holder, CARD_TABLE_CARD_SHIFT
   add $z1, $z0, $z1
   sb $z0, ($z1)
.L${uniq}_skip_write_barrier:


// sput-object vAA, field@BBBB
// Format 21c: AA|6a BBBB
%def op_sput_boolean():
%  op_sput(width=8)


// sput-object vAA, field@BBBB
// Format 21c: AA|6b BBBB
%def op_sput_byte():
%  op_sput(width=8)


// sput-object vAA, field@BBBB
// Format 21c: AA|6c BBBB
%def op_sput_char():
%  op_sput(width=16)


// sput-object vAA, field@BBBB
// Format 21c: AA|6d BBBB
%def op_sput_short():
%  op_sput(width=16)

