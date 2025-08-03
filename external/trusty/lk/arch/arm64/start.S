#include <asm.h>
#include <arch/arm64/mmu.h>
#include <arch/asm_macros.h>
#include <kernel/vm.h>

/*
 * Register use:
 *  x0-x3   Arguments
 *  x9-x15  Scratch
 *  x18     Shadow stack pointer (if enabled)
 *  x19-x28 Globals
 */
tmp                     .req x9
tmp2                    .req x10
wtmp2                   .req w10
index                   .req x11
canary                  .req x12

ssp                     .req x18
cpuid                   .req x19
page_table0             .req x20
page_table1             .req x21
size                    .req x22
supports_mte            .req x23

.section .text.boot
.globl arm_reset
arm_reset:
.globl _start
.type _start,STT_OBJECT
_start:
    /* This instruction is read by the bootloader to determine image type */
    bl      arm64_elX_to_el1

    /* Initialize VBAR to the temporary exception vector table */
    adrl    tmp, .Learly_exception_base
    msr     vbar_el1, tmp
    isb

    mrs     tmp2, id_aa64pfr1_el1
    tst     tmp2, #0xe00
    cset    supports_mte, ne

#if WITH_KERNEL_VM
    /* enable caches so atomics and spinlocks work */
    mrs     tmp, sctlr_el1
    orr     tmp, tmp, #(1<<12) /* Enable icache */
    orr     tmp, tmp, #(1<<2)  /* Enable dcache/ucache */
    orr     tmp, tmp, #(1<<3)  /* Enable Stack Alignment Check EL1 */
    orr     tmp, tmp, #(1<<4)  /* Enable Stack Alignment Check EL0 */
    cbz     supports_mte, .Ldont_set_mte_flags
    orr     tmp, tmp, #(1<<43) /* Allocation Tag Access in EL1 */
    orr     tmp, tmp, #(1<<42) /* Allocation Tag Access in EL0 */
    bic     tmp, tmp, #(1<<40) /* No tag check faults in EL1 */
    orr     tmp, tmp, #(1<<38) /* Tag check faults in EL0 are synchronous */
.Ldont_set_mte_flags:
    bic     tmp, tmp, #(1<<1)  /* Disable Alignment Checking for EL1 EL0 */
    msr     sctlr_el1, tmp

    /* set up the mmu according to mmu_initial_mappings */

    /* load the base of the translation table and clear the table */
    adrl    page_table1, arm64_kernel_translation_table

    /* Prepare tt_trampoline page table */
    /* Calculate pagetable physical addresses */
    adrl    page_table0, tt_trampoline

#if WITH_SMP
    /*
     * Stash x0 as it will be clobbered
     * We place it in size as x0 contains the size passed to the entry point.
     */
    mov     size, x0
    /* Get the CPU number */
    bl      arm64_curr_cpu_num
    mov     cpuid, x0
    /* Restore registers */
    mov     x0, size
    cbnz    cpuid, .Ltt_trampoline_check_secondary
#endif

    /* Zero the top level kernel page table */
    mov     tmp, #0

    /* walk through all the entries in the translation table, setting them up */
.Lclear_top_page_table_loop:
    str     xzr, [page_table1, tmp, lsl #3]
    add     tmp, tmp, #1
    cmp     tmp, #MMU_KERNEL_PAGE_TABLE_ENTRIES_TOP
    bne     .Lclear_top_page_table_loop

    /* Prepare tt_trampoline page table */

    /* Zero tt_trampoline translation tables */
    mov     tmp, #0
.Lclear_tt_trampoline:
    str     xzr, [page_table0, tmp, lsl#3]
    add     tmp, tmp, #1
    cmp     tmp, #MMU_PAGE_TABLE_ENTRIES_IDENT
    blt     .Lclear_tt_trampoline

    /* Setup mapping at phys -> phys */
    /*
     * Map from the start of the kernel to the end of RAM
     * so we have enough pages for boot_alloc memory.
     */
    adr     index, _start
    lsr     tmp, index, #MMU_IDENT_TOP_SHIFT    /* tmp = paddr index */

    /* Check that the start index falls inside the table */
    cmp     tmp, #MMU_PAGE_TABLE_ENTRIES_IDENT
    b.hs    platform_early_halt

    /*
     * The physical address of last byte of the kernel image is (_start + x0 - 1),
     * and we round that down to a multiple of 1<<MMU_IDENT_TOP_SHIFT
     * to get the inclusive upper bound of the tt_trampoline range.
     */
    add     index, index, x0
    sub     index, index, #1
    lsr     index, index, #MMU_IDENT_TOP_SHIFT

    /* Clamp the end index to the last possible entry */
    cmp     index, #MMU_PAGE_TABLE_ENTRIES_IDENT
    b.lo    .Lset_tt_trampoline_loop
    mov     index, #(MMU_PAGE_TABLE_ENTRIES_IDENT - 1)

.Lset_tt_trampoline_loop:
    cmp     tmp, index
    b.hi   .Lset_tt_trampoline_done

    ldr     tmp2, =MMU_PTE_IDENT_FLAGS
    cbz     supports_mte, .Luse_untagged_mapping
    ldr     tmp2, =MMU_PTE_IDENT_FLAGS_TAGGED
.Luse_untagged_mapping:
    add     tmp2, tmp2, tmp, lsl #MMU_IDENT_TOP_SHIFT  /* tmp2 = pt entry */
    str     tmp2, [page_table0, tmp, lsl #3]     /* tt_trampoline[paddr index] = pt entry */
    add     tmp, tmp, #1
    b       .Lset_tt_trampoline_loop

.Lset_tt_trampoline_done:

#if WITH_SMP
    /* Release the first lock on the secondary CPUs */
    adrl    tmp, tt_trampoline_not_ready
    str     wzr, [tmp]
    b       .Ltt_trampoline_ready

.Ltt_trampoline_check_secondary:
    adrl    tmp, tt_trampoline_not_ready
.Ltt_trampoline_not_ready:
    ldr     wtmp2, [tmp]
    cbnz    wtmp2, .Ltt_trampoline_not_ready
.Ltt_trampoline_ready:
#endif

    /* set up the mmu */

    /* Invalidate TLB */
    tlbi    vmalle1is
    isb
    dsb     sy

    /* Initialize Memory Attribute Indirection Register */
    ldr     tmp, =MMU_MAIR_VAL
    msr     mair_el1, tmp

    /* Initialize TCR_EL1 */
    /* set cacheable attributes on translation walk */
    /* (SMP extensions) non-shareable, inner write-back write-allocate */
    ldr     tmp, =MMU_TCR_FLAGS_IDENT
    msr     tcr_el1, tmp

    isb

    /* Write ttbr with phys addr of the translation table */
    msr     ttbr0_el1, page_table0
    msr     ttbr1_el1, page_table1
    isb

    /* Read SCTLR */
    mrs     tmp, sctlr_el1

    /* Turn on the MMU */
    orr     tmp, tmp, #0x1

    /* Write back SCTLR */
    msr     sctlr_el1, tmp
    isb

#if WITH_SMP
    cbnz    cpuid, .Lpage_tables_check_secondary
#endif
#endif /* WITH_KERNEL_VM */

    /* clear bss */
.L__do_bss:
    /* clear out the bss excluding the stack and kernel translation table  */
    /* NOTE: relies on __post_prebss_bss_start and __bss_end being 8 byte aligned */
    adrl    tmp, __post_prebss_bss_start
    adrl    tmp2, __bss_end
    sub     tmp2, tmp2, tmp
    cbz     tmp2, .L__bss_loop_done
.L__bss_loop:
    sub     tmp2, tmp2, #8
    str     xzr, [tmp], #8
    cbnz    tmp2, .L__bss_loop
.L__bss_loop_done:

#if WITH_KERNEL_VM
    /* Set up the stack */
    adrl    tmp, __stack_end
    mov     sp, tmp

    /* Add the stack canary region at the low end of the stack */
    ldr     tmp2, =ARCH_DEFAULT_STACK_SIZE
    sub     tmp, tmp, tmp2
    ldr     index, =ARM64_PHYSICAL_STACK_CANARY_WORDS
    ldr     canary, =ARM64_PHYSICAL_STACK_CANARY

.Lphysical_stack_canary_setup_loop:
    cbz     index, .Lphysical_stack_canary_setup_end

    /* Store the canary at the current stack location */
    str     canary, [tmp], #8

    sub     index, index, #1
    /* Rotate the canary so every value is different */
    ror     canary, canary, #ARM64_PHYSICAL_STACK_CANARY_ROTATE
    b       .Lphysical_stack_canary_setup_loop

.Lphysical_stack_canary_setup_end:

#if KERNEL_SCS_ENABLED
    adrl    ssp, __shadow_stack
#endif

    /* Try to write and read tags to test if EL3 configured tag space access */
    cbz     supports_mte, .Lno_tagging_feature

    /*
     * The stack is set up but unused at this point, so we can use
     * it as a convenient address for tagging.
     */
    mov     tmp, sp
    sub     tmp, tmp, #16
    mov     tmp2, #3
    bfi     tmp, tmp2, #56, #4
    mov     tmp2, tmp
.arch_extension memtag
    stg     tmp, [tmp]
    ldg     tmp, [tmp]
    cmp     tmp, tmp2
    cset    tmp2, eq
    adrl    tmp, arm64_mte_enabled
    strb    wtmp2, [tmp]

.Lno_tagging_feature:

    /* Save the arguments */
    push    x2, x3
    push    x0, x1

    /* x0 already contains ram_size */
    adrl    x1, __relr_start
    adrl    x2, __relr_end
    adrl    x3, _start
    bl      arm64_early_mmu_init

    /* Check the stack canaries */
    adrl    tmp, __stack_end
    ldr     tmp2, =ARCH_DEFAULT_STACK_SIZE
    sub     tmp, tmp, tmp2
    ldr     index, =ARM64_PHYSICAL_STACK_CANARY_WORDS
    ldr     canary, =ARM64_PHYSICAL_STACK_CANARY

.Lphysical_stack_canary_check_loop:
    cbz     index, .Lphysical_stack_canary_check_end

    ldr     tmp2, [tmp], #8
    cmp     tmp2, canary
    b.ne    platform_early_halt /* Error: canary got overwritten, stack overflow */

    sub     index, index, #1
    /* Rotate the canary so every value is different */
    ror     canary, canary, #ARM64_PHYSICAL_STACK_CANARY_ROTATE
    b       .Lphysical_stack_canary_check_loop

.Lphysical_stack_canary_check_end:

    /* Restore the arguments */
    pop     x0, x1
    pop     x2, x3

    /* Check the stack offset */
    adrl    tmp, __stack_end
    mov     tmp2, sp
    cmp     tmp, tmp2
    b.ne    platform_early_halt /* Error: invalid SP on return from C */

#if WITH_SMP
    /* Release the second lock on the secondary CPUs */
    adrl    tmp, page_tables_not_ready
    stlr    wzr, [tmp]
    b       .Lpage_tables_ready

.Lpage_tables_check_secondary:
    adrl    tmp, page_tables_not_ready
.Lpage_tables_not_ready:
    ldar    wtmp2, [tmp]
    cbnz    wtmp2, .Lpage_tables_not_ready
.Lpage_tables_ready:
#endif
    isb

    /* Jump to virtual code address */
    adrl    tmp, mmu_on_vaddr_ptr
    ldr     tmp, [tmp]
    br      tmp

.Lmmu_on_vaddr:
    /* Allow br from above if BTI is enabled, otherwise this is a NOP */
    bti     j

    /* Update VBAR to its virtual address */
    adrl    tmp, .Learly_exception_base
    msr     vbar_el1, tmp
    isb

    /* Disable trampoline page-table in ttbr0 */
    ldr     tmp, =MMU_TCR_FLAGS_KERNEL
    msr     tcr_el1, tmp
    isb

    /* Invalidate TLB */
    tlbi    vmalle1
    isb

    /* We're no longer using the tagged identity map at this point, so
     * enable synchronous tag check faults in EL1 to catch any code
     * improperly using a tagged mapping
     */
    cbz     supports_mte, .Ldont_set_mte_flags_2
    mrs     tmp, sctlr_el1
    orr     tmp, tmp, #(1<<40)
    msr     sctlr_el1, tmp
.Ldont_set_mte_flags_2:

#if WITH_SMP
    cbnz    cpuid, .Lsecondary_boot
#endif
#endif /* WITH_KERNEL_VM */

    adrl    tmp, sp_el1_bufs
    mov     sp, tmp

    msr     spsel, #0 /* Use SP0 for kernel stacks */
    adrl    tmp, __stack_end
    mov sp, tmp

#if KERNEL_SCS_ENABLED
    adrl    ssp, __shadow_stack
#endif

    bl  lk_main
    b   .

#if WITH_SMP
/*
 *  Decodes the CPU number out of MPIDR.
 *  This can be overridden by the platform.
 *  If it is, it must:
 *  - Not assume a stack
 *  - Only clobber x0, x9, x10
 *  - Return the CPU number in x0
 *  - If the CPU number would be invalid, return SMP_MAX_CPUS
 */
WEAK_FUNCTION(arm64_curr_cpu_num)
    mrs     x0, mpidr_el1
    ubfx    x0, x0, #0, #SMP_CPU_ID_BITS
    and     tmp, x0, #0xff
    cmp     tmp, #(1 << SMP_CPU_CLUSTER_SHIFT)
    bge     .Lunsupported_cpu_num
    bic     x0, x0, #0xff
    orr     x0, tmp, x0, LSR #(8 - SMP_CPU_CLUSTER_SHIFT)
    ret

.Lunsupported_cpu_num:
    mov     x0, #SMP_MAX_CPUS
    ret

.Lsecondary_boot:
    cmp     cpuid, #SMP_MAX_CPUS
    bge     .Lunsupported_cpu_trap

    /* Set up the stack pointers */
    adrl    tmp, sp_el1_bufs
    mov     tmp2, #ARM64_EXC_SP_EL1_BUF_SIZE
    mul     tmp2, tmp2, cpuid
    add     sp, tmp, tmp2

    msr     spsel, #0 /* Use SP0 for kernel stacks */

    adrl    tmp, __stack_end
    mov     tmp2, #ARCH_DEFAULT_STACK_SIZE
    mul     tmp2, tmp2, cpuid
    sub     sp, tmp, tmp2

#if KERNEL_SCS_ENABLED
    /* Set up the shadow stack pointer */
    adrl    ssp, __shadow_stack
    mov     tmp, #ARCH_DEFAULT_SHADOW_STACK_SIZE
    mul     tmp, tmp, cpuid
    add     ssp, ssp, tmp
#endif

    mov     x0, cpuid
    bl      arm64_secondary_entry

.Lunsupported_cpu_trap:
    wfe
    b       .Lunsupported_cpu_trap
#endif

.ltorg

.section .text.boot.early.vectab
.balign 0x800 /* This is the required alignment for the table */
.Learly_exception_base:
.org 0x000
    b platform_early_halt
.org 0x080
    b platform_early_halt
.org 0x100
    b platform_early_halt
.org 0x180
    b platform_early_halt
.org 0x200
    b platform_early_halt
.org 0x280
    b platform_early_halt
.org 0x300
    b platform_early_halt
.org 0x380
    b platform_early_halt
.org 0x400
    b platform_early_halt
.org 0x480
    b platform_early_halt
.org 0x500
    b platform_early_halt
.org 0x580
    b platform_early_halt
.org 0x600
    b platform_early_halt
.org 0x680
    b platform_early_halt
.org 0x700
    b platform_early_halt
.org 0x780
    b platform_early_halt

#if WITH_KERNEL_VM
.data
DATA(mmu_on_vaddr_ptr)
.hidden mmu_on_vaddr_ptr
    /*
     * Store a pointer to the virtual address of .Lmmu_on_vaddr inside a
     * pointer quad that ASLR can relocate.
     */
    .align 3
    .quad .Lmmu_on_vaddr
#endif

#if WITH_SMP
.data
DATA(tt_trampoline_not_ready)
    /*
     * The primary processor clears this when the ttbr0 page tables
     * are ready and all processors can enable their MMUs. Before
     * passing this semaphore, all CPUs should have MMUs off, and
     * turn them on immediately after.
     */
    .long       1
DATA(page_tables_not_ready)
    /*
     * The primary processor clears this when the final (ttbr1)
     * page tables are ready.
     */
    .long       1
#endif

.section .bss.prebss.stack
    .align 4
DATA(__stack)
    .skip ARCH_DEFAULT_STACK_SIZE * SMP_MAX_CPUS
DATA(__stack_end)

#if KERNEL_SCS_ENABLED
.section .bss.prebss.shadow_stack
    /*
     * Request 2^3 = 8-byte alignment. For aarch64, the stack pointer
     * alignment must be two times the pointer size (2^4) but the same is not
     * required for the shadow stack. Protecting the shadow stack with Memory
     * Tagging Extensions may require matching MTE's 16-byte tag granularity.
     */
    .align 3
DATA(__shadow_stack)
    .skip ARCH_DEFAULT_SHADOW_STACK_SIZE * SMP_MAX_CPUS
#endif

.section .bss.prebss.sp_el1_bufs
    .align 4
DATA(sp_el1_bufs)
    .skip ARM64_EXC_SP_EL1_BUF_SIZE * SMP_MAX_CPUS

#if WITH_KERNEL_VM
.section ".bss.prebss.translation_table"
.align 3 + MMU_PAGE_TABLE_ENTRIES_IDENT_SHIFT
DATA(tt_trampoline)
    .skip 8 * MMU_PAGE_TABLE_ENTRIES_IDENT
#endif
