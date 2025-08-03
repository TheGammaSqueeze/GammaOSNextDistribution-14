%def invoke(helper="NterpUnimplemented"):
    call    SYMBOL($helper)

%def op_invoke_custom():
   EXPORT_PC
   movzwl 2(rPC), %eax // call_site index, first argument of runtime call.
   jmp NterpCommonInvokeCustom

%def op_invoke_custom_range():
   EXPORT_PC
   movzwl 2(rPC), %eax // call_site index, first argument of runtime call.
   jmp NterpCommonInvokeCustomRange

%def invoke_direct_or_super(helper="", range="", is_super=""):
   EXPORT_PC
   // Fast-path which gets the method from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label="2f")
1:
   // Load the first argument (the 'this' pointer).
   movzwl 4(rPC), %ecx // arguments
   .if !$range
   andl $$0xf, %ecx
   .endif
   movl (rFP, %ecx, 4), %ecx
   // NullPointerException check.
   testl %ecx, %ecx
   je common_errNullObject
   jmp $helper
2:
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   call nterp_get_method
   .if $is_super
   jmp 1b
   .else
   testl MACRO_LITERAL(1), %eax
   je 1b
   andl $$-2, %eax  // Remove the extra bit that marks it's a String.<init> method.
   .if $range
   jmp NterpHandleStringInitRange
   .else
   jmp NterpHandleStringInit
   .endif
   .endif

%def op_invoke_direct():
%  invoke_direct_or_super(helper="NterpCommonInvokeInstance", range="0", is_super="0")

%def op_invoke_direct_range():
%  invoke_direct_or_super(helper="NterpCommonInvokeInstanceRange", range="1", is_super="0")

%def op_invoke_polymorphic():
   EXPORT_PC
   // No need to fetch the target method.
   // Load the first argument (the 'this' pointer).
   movzwl 4(rPC), %ecx // arguments
   andl $$0xf, %ecx
   movl (rFP, %ecx, 4), %ecx
   // NullPointerException check.
   testl %ecx, %ecx
   je common_errNullObject
   jmp NterpCommonInvokePolymorphic

%def op_invoke_polymorphic_range():
   EXPORT_PC
   // No need to fetch the target method.
   // Load the first argument (the 'this' pointer).
   movzwl 4(rPC), %ecx // arguments
   movl (rFP, %ecx, 4), %ecx
   // NullPointerException check.
   testl %ecx, %ecx
   je common_errNullObject
   jmp NterpCommonInvokePolymorphicRange

%def invoke_interface(helper="", range=""):
%  slow_path = add_slow_path(op_invoke_interface_slow_path)
   EXPORT_PC
   // Fast-path which gets the interface method from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label=slow_path)
.L${opcode}_resume:
   // First argument is the 'this' pointer.
   movzwl 4(rPC), %ecx // arguments
   .if !$range
   andl $$0xf, %ecx
   .endif
   movl (rFP, %ecx, 4), %ecx
   movl MIRROR_OBJECT_CLASS_OFFSET(%ecx), %edx
   UNPOISON_HEAP_REF edx
   // Test the first two bits of the fetched ArtMethod:
   // - If the first bit is set, this is a method on j.l.Object
   // - If the second bit is set, this is a default method.
   testl $$3, %eax
   jne 2f
   // Save interface method as hidden argument.
   movd %eax, %xmm7
   movzw ART_METHOD_IMT_INDEX_OFFSET(%eax), %eax
1:
   movl MIRROR_CLASS_IMT_PTR_OFFSET_32(%edx), %edx
   movl (%edx, %eax, 4), %eax
   jmp $helper
2:
   testl $$1, %eax
   .if $range
   jne NterpHandleInvokeInterfaceOnObjectMethodRange
   .else
   jne NterpHandleInvokeInterfaceOnObjectMethod
   .endif
   // Default method
   andl $$-4, %eax
   // Save interface method as hidden argument.
   movd %eax, %xmm7
   movzw ART_METHOD_METHOD_INDEX_OFFSET(%eax), %eax
   andl $$ART_METHOD_IMT_MASK, %eax
   jmp 1b

%def op_invoke_interface_slow_path():
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   call nterp_get_method
   jmp .L${opcode}_resume

%def op_invoke_interface():
%  invoke_interface(helper="NterpCommonInvokeInterface", range="0")

%def op_invoke_interface_range():
%  invoke_interface(helper="NterpCommonInvokeInterfaceRange", range="1")

%def invoke_static(helper=""):
   EXPORT_PC
   // Fast-path which gets the method from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label="1f")
   jmp $helper
1:
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   call nterp_get_method
   jmp $helper

%def op_invoke_static():
%  invoke_static(helper="NterpCommonInvokeStatic")

%def op_invoke_static_range():
%  invoke_static(helper="NterpCommonInvokeStaticRange")

%def op_invoke_super():
%  invoke_direct_or_super(helper="NterpCommonInvokeInstance", range="0", is_super="1")

%def op_invoke_super_range():
%  invoke_direct_or_super(helper="NterpCommonInvokeInstanceRange", range="1", is_super="1")

%def invoke_virtual(helper="", range=""):
   EXPORT_PC
   // Fast-path which gets the method from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label="2f")
1:
   // First argument is the 'this' pointer.
   movzwl 4(rPC), %ecx // arguments
   .if !$range
   andl $$0xf, %ecx
   .endif
   movl (rFP, %ecx, 4), %ecx
   // Note: if ecx is null, this will be handled by our SIGSEGV handler.
   movl MIRROR_OBJECT_CLASS_OFFSET(%ecx), %edx
   UNPOISON_HEAP_REF edx
   movl MIRROR_CLASS_VTABLE_OFFSET_32(%edx, %eax, 4), %eax
   jmp $helper
2:
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   call nterp_get_method
   jmp 1b

%def op_invoke_virtual():
%  invoke_virtual(helper="NterpCommonInvokeInstance", range="0")

%def op_invoke_virtual_range():
%  invoke_virtual(helper="NterpCommonInvokeInstanceRange", range="1")
