%def op_check_cast():
%  slow_path = add_slow_path(op_check_cast_slow_path)
   // Fast-path which gets the class from thread-local cache.
%  fetch_from_thread_cache("%ecx", miss_label="3f")
1:
   GET_VREG %eax, rINST
   testl %eax, %eax
   je .L${opcode}_resume
   // Fast path without read barriers.
   POISON_HEAP_REF ecx  // Poison class reference for in-memory comparison.
   cmpl MIRROR_OBJECT_CLASS_OFFSET(%eax), %ecx
   jne ${slow_path}
.L${opcode}_resume:
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
3:
   EXPORT_PC
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   call nterp_get_class
   movl %eax, %ecx
   jmp 1b

%def op_check_cast_slow_path():
   UNPOISON_HEAP_REF ecx  // Unpoison class reference poisoned in main path.
   cmpl $$0, rSELF:THREAD_READ_BARRIER_MARK_REG00_OFFSET
   jne 2f
1:
   EXPORT_PC
   call art_quick_check_instance_of
   RESTORE_IBASE
   jmp .L${opcode}_resume
2:
   // 01 is %ecx
   call art_quick_read_barrier_mark_reg01
   jmp 1b

%def op_instance_of():
%  slow_path = add_slow_path(op_instance_of_slow_path)
   /* instance-of vA, vB, class@CCCC */
   // Fast-path which gets the class from thread-local cache.
%  fetch_from_thread_cache("%ecx", miss_label="2f")
1:
   movzbl  rINSTbl, %eax
   sarl    $$4,%eax                          # eax<- B
   GET_VREG %eax %eax                        # eax<- vB (object)
   testl %eax, %eax
   je .L${opcode}_resume
   // Fast path without read barriers.
   POISON_HEAP_REF ecx  // Poison class reference for in-memory comparison.
   cmpl MIRROR_OBJECT_CLASS_OFFSET(%eax), %ecx
   jne ${slow_path}
.L${opcode}_set_one:
   movl $$1, %eax
.L${opcode}_resume:
   andb    $$0xf,rINSTbl                     # rINST<- A
   SET_VREG %eax, rINST                      # fp[A] <- value
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
2:
   EXPORT_PC
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   call nterp_get_class
   movl %eax, %ecx
   jmp 1b

%def op_instance_of_slow_path():
   UNPOISON_HEAP_REF ecx  // Unpoison class reference poisoned in main path.
   cmpl $$0, rSELF:THREAD_READ_BARRIER_MARK_REG00_OFFSET
   jne 2f
   testl $$MIRROR_CLASS_IS_INTERFACE_FLAG, MIRROR_CLASS_ACCESS_FLAGS_OFFSET(%ecx)
   jne 3f
   cmpl $$0, MIRROR_CLASS_COMPONENT_TYPE_OFFSET(%ecx)
   jne 3f
   movl MIRROR_OBJECT_CLASS_OFFSET(%eax), %eax
   UNPOISON_HEAP_REF eax
1:
   movl MIRROR_CLASS_SUPER_CLASS_OFFSET(%eax), %eax
   UNPOISON_HEAP_REF eax
   cmpl %eax, %ecx
   je .L${opcode}_set_one
   testl %eax, %eax
   jne 1b
   jmp .L${opcode}_resume
2:
   // 01 is %ecx
   call art_quick_read_barrier_mark_reg01
3:
   EXPORT_PC
   call art_quick_instance_of
   RESTORE_IBASE
   FETCH_INST_CLEAR_OPCODE
   jmp .L${opcode}_resume

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
