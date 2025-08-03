%def op_check_cast():
%  slow_path = add_slow_path(op_check_cast_slow_path)
   // Fast-path which gets the class from thread-local cache.
%  fetch_from_thread_cache("x1", miss_label="2f")
1:
   lsr     w2, wINST, #8               // w2<- A
   GET_VREG w0, w2                     // w0<- vA (object)
   cbz     w0, .L${opcode}_resume
   ldr     w2, [x0, #MIRROR_OBJECT_CLASS_OFFSET]
   UNPOISON_HEAP_REF w2
   // Fast path: do a comparison without read barrier.
   cmp     w1, w2
   bne     ${slow_path}
.L${opcode}_resume:
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE ip
   GOTO_OPCODE ip
2:
   EXPORT_PC
   mov     x0, xSELF
   ldr     x1, [sp]
   mov     x2, xPC
   bl      nterp_get_class
   mov     x1, x0
   b       1b

%def op_check_cast_slow_path():
   // We don't do read barriers for simplicity. However, this means that x1
   // (and all other fetched objects) may be a from-space reference. Tthat's OK as
   // we only fetch constant information from the references.
   // This also means that some of the comparisons below may lead to false negative,
   // but it will eventually be handled in the runtime.
   ldr     w3, [x1, #MIRROR_CLASS_ACCESS_FLAGS_OFFSET]
   tbnz    w3, #MIRROR_CLASS_IS_INTERFACE_FLAG_BIT, 2f
   ldr     w3, [x1, #MIRROR_CLASS_COMPONENT_TYPE_OFFSET]
   UNPOISON_HEAP_REF w3
   cbnz    w3, 5f
1:
   ldr     w2, [x2, #MIRROR_CLASS_SUPER_CLASS_OFFSET]
   UNPOISON_HEAP_REF w2
   cmp     w1, w2
   beq     .L${opcode}_resume
   cbnz    w2, 1b
2:
   TEST_IF_MARKING 4f
3:
   EXPORT_PC
   bl      art_quick_check_instance_of
   b       .L${opcode}_resume
4:
   bl      art_quick_read_barrier_mark_reg01
   b       3b
5:
   // Class in w1 is an array, w3 is the component type.
   ldr     w2, [x2, #MIRROR_CLASS_COMPONENT_TYPE_OFFSET]
   UNPOISON_HEAP_REF w2
   // Check if object is an array.
   cbz     w2, 2b
   ldr     w4, [x3, #MIRROR_CLASS_SUPER_CLASS_OFFSET]
   UNPOISON_HEAP_REF w4
   // If the super class of the component type is not null, go slow path.
   cbnz    w4, 2b
   ldrh    w3, [x3, #MIRROR_CLASS_OBJECT_PRIMITIVE_TYPE_OFFSET]
   // If the component type is primitive, go slow path.
   cbnz    w3, 2b
   // Check if the object is a primitive array.
   ldrh    w2, [x2, #MIRROR_CLASS_OBJECT_PRIMITIVE_TYPE_OFFSET]
   cbz     w2, .L${opcode}_resume
   // Go slow path for throwing the exception.
   b 2b

%def op_instance_of():
%  slow_path = add_slow_path(op_instance_of_slow_path)
   /* instance-of vA, vB, class@CCCC */
   // Fast-path which gets the class from thread-local cache.
%  fetch_from_thread_cache("x1", miss_label="2f")
1:
   lsr     w2, wINST, #12              // w2<- B
   GET_VREG w0, w2                     // w0<- vB (object)
   cbz     w0, .L${opcode}_resume
   ldr     w2, [x0, #MIRROR_OBJECT_CLASS_OFFSET]
   UNPOISON_HEAP_REF w2
   // Fast path: do a comparison without read barrier.
   cmp     w1, w2
   bne     ${slow_path}
.L${opcode}_set_one:
   mov     w0, #1
.L${opcode}_resume:
   ubfx    w1, wINST, #8, #4           // w1<- A
   SET_VREG w0, w1
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE ip
   GOTO_OPCODE ip
2:
   EXPORT_PC
   mov     x0, xSELF
   ldr     x1, [sp]
   mov     x2, xPC
   bl      nterp_get_class
   mov     x1, x0
   b       1b

%def op_instance_of_slow_path():
   // Go slow path if we are marking. Checking now allows
   // not going to slow path if the super class hierarchy check fails.
   TEST_IF_MARKING 4f
   ldr     w3, [x1, #MIRROR_CLASS_ACCESS_FLAGS_OFFSET]
   tbnz    w3, #MIRROR_CLASS_IS_INTERFACE_FLAG_BIT, 5f
   ldr     w3, [x1, #MIRROR_CLASS_COMPONENT_TYPE_OFFSET]
   UNPOISON_HEAP_REF w3
   cbnz    w3, 3f
1:
   ldr     w2, [x2, #MIRROR_CLASS_SUPER_CLASS_OFFSET]
   UNPOISON_HEAP_REF w2
   cmp     w1, w2
   beq     .L${opcode}_set_one
   cbnz    w2, 1b
2:
   mov     w0, #0
   b       .L${opcode}_resume
3:
   // Class in x1 is an array, x3 is the component type of x1, and x2 is the class of the object.
   ldr     w2, [x2, #MIRROR_CLASS_COMPONENT_TYPE_OFFSET]
   UNPOISON_HEAP_REF w2
   // Check if object is an array.
   cbz     w2, 2b
   // Check of x1 is Object[]
   ldr     w4, [x3, #MIRROR_CLASS_SUPER_CLASS_OFFSET]
   UNPOISON_HEAP_REF w4
   // If the super class is not Object, go to slow path.
   cbnz    w4, 5f
   // Super class is null, this could either be a primitive array or Object[].
   ldrh    w3, [x3, #MIRROR_CLASS_OBJECT_PRIMITIVE_TYPE_OFFSET]
   // If x1 is a primitive array class, we know the check is false.
   cbnz    w3, 2b
   // Check if x2 is a primitive array class.
   ldrh    w2, [x2, #MIRROR_CLASS_OBJECT_PRIMITIVE_TYPE_OFFSET]
   cmp     w2, #0
   cset    w0, eq
   b       .L${opcode}_resume
4:
   bl      art_quick_read_barrier_mark_reg01
5:
   EXPORT_PC
   bl      artInstanceOfFromCode
   b       .L${opcode}_resume

%def op_iget_boolean():
%  op_iget(load="ldrb", volatile_load="ldarb", maybe_extend="", wide="0", is_object="0")

%def op_iget_byte():
%  op_iget(load="ldrsb", volatile_load="ldarb", maybe_extend="sxtb w0, w0", wide="0", is_object="0")

%def op_iget_char():
%  op_iget(load="ldrh", volatile_load="ldarh", maybe_extend="", wide="0", is_object="0")

%def op_iget_short():
%  op_iget(load="ldrsh", volatile_load="ldarh", maybe_extend="sxth w0, w0", wide="0", is_object="0")

%def op_iget(load="ldr", volatile_load="ldar", maybe_extend="", wide="0", is_object="0"):
%  slow_path = add_slow_path(op_iget_slow_path, volatile_load, maybe_extend, wide, is_object)
   // Fast-path which gets the field from thread-local cache.
%  fetch_from_thread_cache("x0", miss_label=slow_path)
.L${opcode}_resume:
   lsr     w2, wINST, #12              // w2<- B
   GET_VREG w3, w2                     // w3<- object we're operating on
   ubfx    w2, wINST, #8, #4           // w2<- A
   cbz     w3, common_errNullObject    // object was null
   .if $wide
   $load   x0, [x3, x0]
   SET_VREG_WIDE x0, w2                // fp[A] <- value
   .elseif $is_object
   $load   w0, [x3, x0]
   UNPOISON_HEAP_REF w0
   TEST_IF_MARKING .L${opcode}_read_barrier
.L${opcode}_resume_after_read_barrier:
   SET_VREG_OBJECT w0, w2              // fp[A] <- value
   .else
   $load   w0, [x3, x0]
   SET_VREG w0, w2                     // fp[A] <- value
   .endif
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE ip
   GOTO_OPCODE ip
   .if $is_object
.L${opcode}_read_barrier:
   bl      art_quick_read_barrier_mark_reg00
   b       .L${opcode}_resume_after_read_barrier
   .endif

%def op_iget_slow_path(volatile_load, maybe_extend, wide, is_object):
   mov     x0, xSELF
   ldr     x1, [sp]
   mov     x2, xPC
   mov     x3, #0
   EXPORT_PC
   bl      nterp_get_instance_field_offset
   tbz     w0, #31, .L${opcode}_resume
   CLEAR_INSTANCE_VOLATILE_MARKER w0
   lsr     w2, wINST, #12              // w2<- B
   GET_VREG w3, w2                     // w3<- object we're operating on
   ubfx    w2, wINST, #8, #4           // w2<- A
   cbz     w3, common_errNullObject    // object was null
   add     x3, x3, x0
   .if $wide
   $volatile_load x0, [x3]
   SET_VREG_WIDE x0, w2                // fp[A] <- value
   .elseif $is_object
   $volatile_load w0, [x3]
   UNPOISON_HEAP_REF w0
   TEST_IF_MARKING .L${opcode}_read_barrier
   SET_VREG_OBJECT w0, w2              // fp[A] <- value
   .else
   $volatile_load w0, [x3]
   $maybe_extend
   SET_VREG w0, w2                     // fp[A] <- value
   .endif
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE ip
   GOTO_OPCODE ip

%def op_iget_wide():
%  op_iget(load="ldr", volatile_load="ldar", maybe_extend="", wide="1", is_object="0")

%def op_iget_object():
%  op_iget(load="ldr", volatile_load="ldar", maybe_extend="", wide="0", is_object="1")

%def op_iput_boolean():
%  op_iput(store="strb", volatile_store="stlrb", wide="0", is_object="0")

%def op_iput_byte():
%  op_iput(store="strb", volatile_store="stlrb", wide="0", is_object="0")

%def op_iput_char():
%  op_iput(store="strh", volatile_store="stlrh", wide="0", is_object="0")

%def op_iput_short():
%  op_iput(store="strh", volatile_store="stlrh", wide="0", is_object="0")

%def op_iput(store="str", volatile_store="stlr", wide="0", is_object="0"):
%  slow_path = add_slow_path(op_iput_slow_path, volatile_store, wide, is_object)
   ubfx    w1, wINST, #8, #4           // w1<- A
   .if $wide
   GET_VREG_WIDE x26, w1               // x26<- fp[A]/fp[A+1]
   .else
   GET_VREG w26, w1                    // w26 <- v[A]
   .endif
   // Fast-path which gets the field from thread-local cache.
%  fetch_from_thread_cache("x0", miss_label=slow_path)
.L${opcode}_resume:
   lsr     w2, wINST, #12              // w2<- B
   GET_VREG w2, w2                     // vB (object we're operating on)
   cbz w2, common_errNullObject
   .if $wide
   $store  x26, [x2, x0]
   .else
   POISON_HEAP_REF_IF_OBJECT $is_object, w26
   $store  w26, [x2, x0]
   WRITE_BARRIER_IF_OBJECT $is_object, w26, w2, .L${opcode}_skip_write_barrier
   .endif
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE ip
   GOTO_OPCODE ip

%def op_iput_slow_path(volatile_store, wide, is_object):
   mov     x0, xSELF
   ldr     x1, [sp]
   mov     x2, xPC
   .if $is_object
   mov     x3, x26
   .else
   mov     x3, #0
   .endif
   EXPORT_PC
   bl      nterp_get_instance_field_offset
   .if $is_object
   // Reload the value as it may have moved.
   ubfx    w1, wINST, #8, #4           // w1<- A
   GET_VREG w26, w1                    // w26 <- v[A]
   .endif
   tbz     w0, #31, .L${opcode}_resume
   CLEAR_INSTANCE_VOLATILE_MARKER w0
   lsr     w2, wINST, #12              // w2<- B
   GET_VREG w2, w2                     // vB (object we're operating on)
   cbz     w2, common_errNullObject
   add     x3, x2, x0
   .if $wide
   $volatile_store x26, [x3]
   .else
   POISON_HEAP_REF_IF_OBJECT $is_object, w26
   $volatile_store w26, [x3]
   WRITE_BARRIER_IF_OBJECT $is_object, w26, w2, .L${opcode}_slow_path_skip_write_barrier
   .endif
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE ip
   GOTO_OPCODE ip

%def op_iput_wide():
%  op_iput(store="str", volatile_store="stlr", wide="1", is_object="0")

%def op_iput_object():
%  op_iput(store="str", volatile_store="stlr", wide="0", is_object="1")

%def op_sget_boolean():
%  op_sget(load="ldrb", volatile_load="ldarb", maybe_extend="", wide="0", is_object="0")

%def op_sget_byte():
%  op_sget(load="ldrsb", volatile_load="ldarb", maybe_extend="sxtb w0, w0", wide="0", is_object="0")

%def op_sget_char():
%  op_sget(load="ldrh", volatile_load="ldarh", maybe_extend="", wide="0", is_object="0")

%def op_sget_short():
%  op_sget(load="ldrsh", volatile_load="ldarh", maybe_extend="sxth w0, w0", wide="0", is_object="0")

%def op_sget(load="ldr", volatile_load="ldar", maybe_extend="", wide="0", is_object="0"):
%  slow_path = add_slow_path(op_sget_slow_path, volatile_load, maybe_extend, wide, is_object)
   // Fast-path which gets the field from thread-local cache.
%  fetch_from_thread_cache("x0", miss_label=slow_path)
.L${opcode}_resume:
   ldr     w1, [x0, #ART_FIELD_OFFSET_OFFSET]
   lsr     w2, wINST, #8               // w2 <- A
   ldr     w0, [x0, #ART_FIELD_DECLARING_CLASS_OFFSET]
   TEST_IF_MARKING .L${opcode}_read_barrier
.L${opcode}_resume_after_read_barrier:
   .if $wide
   ldr     x0, [x0, x1]
   SET_VREG_WIDE x0, w2                // fp[A] <- value
   .elseif $is_object
   $load   w0, [x0, x1]
   UNPOISON_HEAP_REF w0
   // No need to check the marking register, we know it's not set here.
.L${opcode}_after_reference_load:
   SET_VREG_OBJECT w0, w2              // fp[A] <- value
   .else
   $load   w0, [x0, x1]
   SET_VREG w0, w2                     // fp[A] <- value
   .endif
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE ip
   GOTO_OPCODE ip
.L${opcode}_read_barrier:
   bl      art_quick_read_barrier_mark_reg00
   .if $is_object
   $load   w0, [x0, x1]
   UNPOISON_HEAP_REF w0
.L${opcode}_mark_after_load:
   // Here, we know the marking register is set.
   bl      art_quick_read_barrier_mark_reg00
   b       .L${opcode}_after_reference_load
   .else
   b       .L${opcode}_resume_after_read_barrier
   .endif

%def op_sget_slow_path(volatile_load, maybe_extend, wide, is_object):
   mov     x0, xSELF
   ldr     x1, [sp]
   mov     x2, xPC
   mov     x3, #0
   EXPORT_PC
   bl      nterp_get_static_field
   tbz     x0, #0, .L${opcode}_resume
   CLEAR_STATIC_VOLATILE_MARKER x0
   ldr     w1, [x0, #ART_FIELD_OFFSET_OFFSET]
   lsr     w2, wINST, #8               // w2 <- A
   ldr     w0, [x0, #ART_FIELD_DECLARING_CLASS_OFFSET]
   TEST_IF_MARKING .L${opcode}_slow_path_read_barrier
.L${opcode}_slow_path_resume_after_read_barrier:
   add     x0, x0, x1
   .if $wide
   ldar    x0, [x0]
   SET_VREG_WIDE x0, w2                // fp[A] <- value
   .elseif $is_object
   $volatile_load w0, [x0]
   UNPOISON_HEAP_REF w0
   TEST_IF_MARKING .L${opcode}_mark_after_load
   SET_VREG_OBJECT w0, w2              // fp[A] <- value
   .else
   $volatile_load w0, [x0]
   $maybe_extend
   SET_VREG w0, w2                     // fp[A] <- value
   .endif
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE ip
   GOTO_OPCODE ip
.L${opcode}_slow_path_read_barrier:
   bl      art_quick_read_barrier_mark_reg00
   b       .L${opcode}_slow_path_resume_after_read_barrier

%def op_sget_wide():
%  op_sget(load="ldr", volatile_load="ldar", maybe_extend="", wide="1", is_object="0")

%def op_sget_object():
%  op_sget(load="ldr", volatile_load="ldar", maybe_extend="", wide="0", is_object="1")

%def op_sput_boolean():
%  op_sput(store="strb", volatile_store="stlrb", wide="0", is_object="0")

%def op_sput_byte():
%  op_sput(store="strb", volatile_store="stlrb", wide="0", is_object="0")

%def op_sput_char():
%  op_sput(store="strh", volatile_store="stlrh", wide="0", is_object="0")

%def op_sput_short():
%  op_sput(store="strh", volatile_store="stlrh", wide="0", is_object="0")

%def op_sput(store="str", volatile_store="stlr", wide="0", is_object="0"):
%  slow_path = add_slow_path(op_sput_slow_path, volatile_store, wide, is_object)
   lsr     w2, wINST, #8               // w2 <- A
   .if $wide
   GET_VREG_WIDE x26, w2               // x26 <- v[A]
   .else
   GET_VREG w26, w2                    // w26 <- v[A]
   .endif
   // Fast-path which gets the field from thread-local cache.
%  fetch_from_thread_cache("x0", miss_label=slow_path)
.L${opcode}_resume:
   ldr     w1, [x0, #ART_FIELD_OFFSET_OFFSET]
   ldr     w0, [x0, #ART_FIELD_DECLARING_CLASS_OFFSET]
   TEST_IF_MARKING .L${opcode}_read_barrier
.L${opcode}_resume_after_read_barrier:
   .if $wide
   $store  x26, [x0, x1]
   .else
   POISON_HEAP_REF_IF_OBJECT $is_object, w26
   $store  w26, [x0, x1]
   WRITE_BARRIER_IF_OBJECT $is_object, w26, w0, .L${opcode}_skip_write_barrier
   .endif
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE ip
   GOTO_OPCODE ip
.L${opcode}_read_barrier:
   bl      art_quick_read_barrier_mark_reg00
   b       .L${opcode}_resume_after_read_barrier

%def op_sput_slow_path(volatile_store, wide, is_object):
   mov     x0, xSELF
   ldr     x1, [sp]
   mov     x2, xPC
   .if $is_object
   mov     x3, x26
   .else
   mov     x3, #0
   .endif
   EXPORT_PC
   bl      nterp_get_static_field
   .if $is_object
   // Reload the value as it may have moved.
   lsr     w2, wINST, #8               // w2 <- A
   GET_VREG w26, w2                    // w26 <- v[A]
   .endif
   tbz     x0, #0, .L${opcode}_resume
   CLEAR_STATIC_VOLATILE_MARKER x0
   ldr     w1, [x0, #ART_FIELD_OFFSET_OFFSET]
   ldr     w0, [x0, #ART_FIELD_DECLARING_CLASS_OFFSET]
   TEST_IF_MARKING .L${opcode}_slow_path_read_barrier
.L${opcode}_slow_path_resume_after_read_barrier:
   add     x1, x0, x1
   .if $wide
   $volatile_store    x26, [x1]
   .else
   POISON_HEAP_REF_IF_OBJECT $is_object, w26
   $volatile_store    w26, [x1]
   WRITE_BARRIER_IF_OBJECT $is_object, w26, w0, .L${opcode}_slow_path_skip_write_barrier
   .endif
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE ip
   GOTO_OPCODE ip
.L${opcode}_slow_path_read_barrier:
   bl      art_quick_read_barrier_mark_reg00
   b       .L${opcode}_slow_path_resume_after_read_barrier

%def op_sput_wide():
%  op_sput(store="str", volatile_store="stlr", wide="1", is_object="0")

%def op_sput_object():
%  op_sput(store="str", volatile_store="stlr", wide="0", is_object="1")

%def op_new_instance():
   EXPORT_PC
   // Fast-path which gets the class from thread-local cache.
%  fetch_from_thread_cache("x0", miss_label="2f")
   TEST_IF_MARKING 3f
4:
   ldr     lr, [xSELF, #THREAD_ALLOC_OBJECT_ENTRYPOINT_OFFSET]
   blr     lr
   dmb     ishst                       // need fence for making object's class visible
1:
   lsr     w1, wINST, #8               // w1 <- A
   SET_VREG_OBJECT w0, w1              // fp[A] <- value
   FETCH_ADVANCE_INST 2
   GET_INST_OPCODE ip
   GOTO_OPCODE ip
2:
   mov     x0, xSELF
   ldr     x1, [sp]
   mov     x2, xPC
   bl      nterp_allocate_object
   b       1b
3:
   bl      art_quick_read_barrier_mark_reg00
   b       4b
