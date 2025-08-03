%def op_check_cast():
%  slow_path = add_slow_path(op_check_cast_slow_path)
   // Fast-path which gets the class from thread-local cache.
%  fetch_from_thread_cache("%rsi", miss_label="2f")
1:
   GET_VREG %edi, rINSTq
   testl %edi, %edi
   je .L${opcode}_resume
   // Fast path without read barriers.
   POISON_HEAP_REF esi  // Poison class reference for in-memory comparison.
   cmpl MIRROR_OBJECT_CLASS_OFFSET(%edi), %esi
   jne ${slow_path}
.L${opcode}_resume:
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
2:
   EXPORT_PC
   movq rSELF:THREAD_SELF_OFFSET, %rdi
   movq 0(%rsp), %rsi
   movq rPC, %rdx
   call nterp_get_class
   movq %rax, %rsi
   jmp 1b

%def op_check_cast_slow_path():
   UNPOISON_HEAP_REF esi  // Unpoison class reference poisoned in main path.
   testl $$MIRROR_CLASS_IS_INTERFACE_FLAG, MIRROR_CLASS_ACCESS_FLAGS_OFFSET(%rsi)
   jne 2f
   movl MIRROR_OBJECT_CLASS_OFFSET(%edi), %eax
   UNPOISON_HEAP_REF eax
   cmpl $$0, MIRROR_CLASS_COMPONENT_TYPE_OFFSET(%rsi)
   jne 2f
1:
   movl MIRROR_CLASS_SUPER_CLASS_OFFSET(%eax), %eax
   UNPOISON_HEAP_REF eax
   cmpl %eax, %esi
   je .L${opcode}_resume
   testl %eax, %eax
   jne 1b
2:
   cmpq $$0, rSELF:THREAD_READ_BARRIER_MARK_REG00_OFFSET
   jne 4f
3:
   EXPORT_PC
   call art_quick_check_instance_of
   jmp .L${opcode}_resume
4:
   // 06 is %rsi
   call art_quick_read_barrier_mark_reg06
   jmp 3b
5:
   movl MIRROR_CLASS_COMPONENT_TYPE_OFFSET(%eax), %eax
   UNPOISON_HEAP_REF eax
   // Check if object is an array.
   testl %eax, %eax
   je 2b
   movl MIRROR_CLASS_COMPONENT_TYPE_OFFSET(%esi), %ecx
   UNPOISON_HEAP_REF ecx
   cmpl $$0, MIRROR_CLASS_SUPER_CLASS_OFFSET(%ecx)
   jne 2b
   cmpw $$0, MIRROR_CLASS_OBJECT_PRIMITIVE_TYPE_OFFSET(%ecx)
   jne 2b
   // %ecx is Object[]
   // Check if the object is a primitive array.
   cmpw $$0, MIRROR_CLASS_OBJECT_PRIMITIVE_TYPE_OFFSET(%eax)
   je .L${opcode}_resume
   jmp 2b

%def op_iget_boolean():
   jmp NterpGetBooleanInstanceField

%def op_iget_byte():
   jmp NterpGetByteInstanceField

%def op_iget_char():
   jmp NterpGetCharInstanceField

%def op_iget_object():
    jmp NterpGetObjectInstanceField

%def op_iget_short():
   jmp NterpGetShortInstanceField

%def op_iget_wide():
   jmp NterpGetWideInstanceField

%def op_instance_of():
%  slow_path = add_slow_path(op_instance_of_slow_path)
    /* instance-of vA, vB, class@CCCC */
   // Fast-path which gets the class from thread-local cache.
%  fetch_from_thread_cache("%rsi", miss_label=".L"+opcode+"_init")
.L${opcode}_start:
   movzbl  rINSTbl,%edi
   sarl    $$4,%edi                          # edi<- B
   GET_VREG %edi %rdi                        # edi<- vB (object)
   andb    $$0xf,rINSTbl                     # rINST<- A
   testl %edi, %edi
   je .L${opcode}_set_vreg
   // Fast path without read barriers.
   POISON_HEAP_REF esi  // Poison class reference for in-memory comparison.
   cmpl MIRROR_OBJECT_CLASS_OFFSET(%edi), %esi
   jne ${slow_path}
.L${opcode}_set_one:
   movl $$1, %edi
.L${opcode}_set_vreg:
   SET_VREG %edi, rINSTq
.L${opcode}_resume:
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2

%def op_instance_of_slow_path():
   UNPOISON_HEAP_REF esi  // Unpoison class reference poisoned in main path.
   // Go slow path if we are marking. Checking now allows
   // not going to slow path if the super class hierarchy check fails.
   cmpq $$0, rSELF:THREAD_READ_BARRIER_MARK_REG00_OFFSET
   jne 4f
   testl $$MIRROR_CLASS_IS_INTERFACE_FLAG, MIRROR_CLASS_ACCESS_FLAGS_OFFSET(%rsi)
   jne 5f
   movl MIRROR_OBJECT_CLASS_OFFSET(%edi), %eax
   UNPOISON_HEAP_REF eax
   cmpl $$0, MIRROR_CLASS_COMPONENT_TYPE_OFFSET(%rsi)
   jne 3f
1:
   movl MIRROR_CLASS_SUPER_CLASS_OFFSET(%eax), %eax
   UNPOISON_HEAP_REF eax
   cmpl %eax, %esi
   je .L${opcode}_set_one
   testl %eax, %eax
   jne 1b
2:
   SET_VREG $$0, rINSTq            # fp[A] <- value
   jmp       .L${opcode}_resume
3:
   movl MIRROR_CLASS_COMPONENT_TYPE_OFFSET(%eax), %eax
   UNPOISON_HEAP_REF eax
   // Check if object is an array.
   testl %eax, %eax
   je 2b
   movl MIRROR_CLASS_COMPONENT_TYPE_OFFSET(%esi), %ecx
   UNPOISON_HEAP_REF ecx
   cmpl $$0, MIRROR_CLASS_SUPER_CLASS_OFFSET(%ecx)
   jne 5f
   cmpw $$0, MIRROR_CLASS_OBJECT_PRIMITIVE_TYPE_OFFSET(%ecx)
   jne 2b
   // %ecx is Object[]
   // Check if the object is a primitive array.
   xorl %ecx, %ecx
   cmpw $$0, MIRROR_CLASS_OBJECT_PRIMITIVE_TYPE_OFFSET(%eax)
   sete %cl
   SET_VREG %ecx, rINSTq
   jmp .L${opcode}_resume
4:
   // 06 is %rsi
   call art_quick_read_barrier_mark_reg06
5:
   EXPORT_PC
   call artInstanceOfFromCode
   SET_VREG %eax, rINSTq            # fp[A] <- value
   jmp .L${opcode}_resume

.L${opcode}_init:
   EXPORT_PC
   movq rSELF:THREAD_SELF_OFFSET, %rdi
   movq 0(%rsp), %rsi
   movq rPC, %rdx
   call nterp_get_class
   movq %rax, %rsi
   jmp .L${opcode}_start

%def op_iget():
   jmp NterpGetInstanceField

%def op_iput():
   jmp NterpPutInstanceField

%def op_iput_boolean():
   jmp NterpPutBooleanInstanceField

%def op_iput_byte():
   jmp NterpPutByteInstanceField

%def op_iput_char():
   jmp NterpPutCharInstanceField

%def op_iput_object():
    jmp NterpPutObjectInstanceField

%def op_iput_short():
   jmp NterpPutShortInstanceField

%def op_iput_wide():
   jmp NterpPutWideInstanceField

%def op_sget(load="movl", wide="0"):
   jmp NterpGetIntStaticField

%def op_sget_boolean():
   jmp NterpGetBooleanStaticField

%def op_sget_byte():
   jmp NterpGetByteStaticField

%def op_sget_char():
   jmp NterpGetCharStaticField

%def op_sget_object():
   jmp NterpGetObjectStaticField

%def op_sget_short():
   jmp NterpGetShortStaticField

%def op_sget_wide():
   jmp NterpGetWideStaticField

%def op_sput():
   jmp NterpPutStaticField

%def op_sput_boolean():
   jmp NterpPutBooleanStaticField

%def op_sput_byte():
   jmp NterpPutByteStaticField

%def op_sput_char():
   jmp NterpPutCharStaticField

%def op_sput_object():
   jmp NterpPutObjectStaticField

%def op_sput_short():
   jmp NterpPutShortStaticField

%def op_sput_wide():
   jmp NterpPutWideStaticField

%def op_new_instance():
   // The routine is too big to fit in a handler, so jump to it.
   jmp NterpNewInstance
