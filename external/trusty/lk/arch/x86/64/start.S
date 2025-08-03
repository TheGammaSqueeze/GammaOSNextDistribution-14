/*
 * Copyright (c) 2009 Corey Tabaka
 * Copyright (c) 2015 Intel Corporation
 * Copyright (c) 2016 Travis Geiselbrecht
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
#include <asm.h>
#include <arch/x86/descriptor.h>
#include <arch/x86/mmu.h>

/* The magic number for the Multiboot header. */
#define MULTIBOOT_HEADER_MAGIC 0x1BADB002

/* The flags for the Multiboot header. */
#if defined(__ELF__) && 0
#define MULTIBOOT_HEADER_FLAGS 0x00000002
#else
#define MULTIBOOT_HEADER_FLAGS 0x00010002
#endif

/* The magic number passed by a Multiboot-compliant boot loader. */
#define MULTIBOOT_BOOTLOADER_MAGIC 0x2BADB002

#define MSR_EFER    0xc0000080
#define EFER_NXE    0x00000800
#define MSR_GS_BASE 0xc0000101

#define PHYS(x) ((x) - KERNEL_BASE + MEMBASE)

#define PAGE_MASK               0xfff
#define ONE_GB                  0x40000000
#define ADDR_OFFSET_MASK        ((1 << ADDR_OFFSET) - 1)
#define MAX_MEM_SIZE            (0xFFFFFFFFFFFFFFFF - KERNEL_BASE)
#define X86_PAGE_ALIGN          (0xFFFFFFFFFFFFF000)


.section ".text.boot"
.code64

/*
 * Macro used to fill in an entry in the page table for the region of memory
 * beginning at virtual address KERNEL_BASE. This macro can be used to fill
 * in PML4E and PDPTE.
 *
 * Input:
 *  table: PML4 or PDPT
 *  entry: base address of PDPT or PD to be filled in PML4E or PDPTE
 *  shift: Since we need right shift address for 9 bits to calculate
 *         index in PML4 (512GB for each entry), this argument should be 9
 *         when fill in PML4 table.
 *         This argument should be 0 when fill in PDPT table.
 */
.macro map_kernel_base table, entry, shift
    movq $KERNEL_BASE, %rcx
    shrq $30, %rcx
    shrq \shift, %rcx
    andq $ADDR_OFFSET_MASK, %rcx
    shlq $3, %rcx
    movq $PHYS(\entry), %rax
    orq  $X86_KERNEL_PD_FLAGS, %rax
    movq $PHYS(\table), %rdi
    addq %rcx, %rdi
    movq %rax, (%rdi)
.endm

/*
 * Macro used to fill in an entry in PD or PT in page table.
 * Before invoking this macro, please fill in:
 *   RDI:  address of first entry to be filled
 *   RCX:  numbers of entry to be filled
 * Input:
 *   src_addr: base address of PT if fill in PDE
 *             base address of physical address if fill in PTE
 *   attr:     X86_KERNEL_PD_FLAGS if fill in PDE
 *             X86_KERNEL_PD_LP_FLAGS if fill in PTE
 */
.macro fill_page_table_entry src_addr, attr
    movq \src_addr, %rsi
    orq  \attr, %rsi
    xorq %rax, %rax
.Lfill_entry_\@:
    movq %rax, %rdx
    shlq $PAGE_DIV_SHIFT, %rdx
    addq %rsi, %rdx
    movq %rdx, (%rdi)
    incq %rax
    addq $8, %rdi
    loop .Lfill_entry_\@
.endm

.macro map_trampoline
    movq $PHYS(pdpt_trampoline), %rsi
    orq  $X86_KERNEL_PD_FLAGS, %rsi
    movq %rsi, PHYS(pml4)

    /* update corresponding trampoline PDPTE, it covers 1GB */
    movq $MEMBASE, %rax
    shrq $30, %rax
    andq $0x1ff, %rax
    shlq $3, %rax
    movq $PHYS(pdpt_trampoline), %rdi
    addq %rax, %rdi
    movq $PHYS(pd_trampoline), %rsi
    orq  $X86_KERNEL_PD_FLAGS, %rsi
    movq %rsi, (%rdi)

    /* update corresponding trampoline PDE for bootstrap, it covers 2MB */
    movq $MEMBASE, %rax
    addq $KERNEL_LOAD_OFFSET, %rax
    shrq $21, %rax
    andq $0x1ff, %rax
    shlq $3, %rax
    movq $PHYS(pd_trampoline), %rdi
    addq %rax, %rdi
    movq $PHYS(pt_trampoline), %rsi
    orq $X86_KERNEL_PD_FLAGS, %rsi
    movq %rsi, (%rdi)

    /* update corresponding trampoline PTE for bootstrap, it covers 4KB */
    movq $KERNEL_LOAD_OFFSET, %rax
    shrq $PAGE_DIV_SHIFT, %rax
    andq $0x1ff, %rax
    shlq $3, %rax
    movq $PHYS(pt_trampoline), %rdi
    addq %rax, %rdi
    movq $MEMBASE, %rsi
    addq $KERNEL_LOAD_OFFSET, %rsi
    andq $X86_PAGE_ALIGN, %rsi
    orq  $X86_KERNEL_PD_LP_FLAGS, %rsi
    movq %rsi, (%rdi)

    /* Check whether gdtr and bootstrap code share same PDE */
    movq $MEMBASE, %rax
    addq $KERNEL_LOAD_OFFSET, %rax
    movq $PHYS(_gdtr_phys), %rcx
    /* RCX indicates (_gdtr_phys - MEMBASE - KERNEL_LOAD_OFFSET) */
    subq %rax, %rcx
    /* RCX = RCX / 2MB */
    shrq $21, %rcx
    /* _gdtr_phys and MEMBASE use different PDEs */
    jnz .Luse_different_pde
    /* _gdtr_phys and MEMBASE share same PDE */
    movq $PHYS(pt_trampoline), %rdi
    jmp .Lshare_same_pde

.Luse_different_pde:
    /* update corresponding trampoline PDE for GDT, it covers 2MB */
    movq $PHYS(_gdtr_phys), %rax
    shrq $21, %rax
    andq $0x1ff, %rax
    shlq $3, %rax
    movq $PHYS(pd_trampoline), %rdi
    addq %rax, %rdi
    movq $PHYS(pt_trampoline_gdt), %rsi
    orq $X86_KERNEL_PD_FLAGS, %rsi
    movq %rsi, (%rdi)

    movq $PHYS(pt_trampoline_gdt), %rdi
.Lshare_same_pde:
    /* update corresponding trampoline PTE for GDT, it covers 4KB */
    movq $PHYS(_gdtr_phys), %rax
    shrq $PAGE_DIV_SHIFT, %rax
    andq $0x1ff, %rax
    shlq $3, %rax
    addq %rax, %rdi
    movq $PHYS(_gdtr_phys), %rsi
    orq  $X86_KERNEL_PD_LP_FLAGS, %rsi
    movq %rsi, (%rdi)
.endm

/* Macro used to update mapping attribute for each section */
.macro update_mapping_attribute_of_each_section
    leaq PHYS(pt), %rdi
    movq $KERNEL_LOAD_OFFSET, %rcx
    shrq $PAGE_DIV_SHIFT, %rcx
    shlq $3, %rcx
    addq %rcx, %rdi

    movq $__code_start, %rax
    movq $__code_end, %rcx
    addq $PAGE_MASK, %rcx
    subq %rax, %rcx
    shrq $PAGE_DIV_SHIFT, %rcx

    /* clear R/W bit in pte */
.Lupdate_code_section:
    movq (%rdi), %rdx
    andq $~X86_MMU_PG_RW, %rdx
    movq %rdx, (%rdi)
    addq $8, %rdi
    loop .Lupdate_code_section

    /* from now, RDI indicates virtual address of __rodata_start */
    movq $__rodata_start, %rax
    movq $__rodata_end, %rcx
    addq $PAGE_MASK, %rcx
    subq %rax, %rcx
    shrq $PAGE_DIV_SHIFT, %rcx

    /* clear R/W bit and set XD bit in pte */
.Lupdate_rodata_section:
    movq (%rdi), %rdx
    andq $~X86_MMU_PG_RW, %rdx
    btsq $63, %rdx
    movq %rdx, (%rdi)
    addq $8, %rdi
    loop .Lupdate_rodata_section

    /* from now, RDI indicates virtual address of __data_start */
    movq $__data_start, %rax
    movq $__data_end, %rcx
    addq $PAGE_MASK, %rcx
    subq %rax, %rcx
    shrq $PAGE_DIV_SHIFT, %rcx

    /* set XD bit in pte */
.Lupdate_data_section:
    movq (%rdi), %rdx
    btsq $63, %rdx
    movq %rdx, (%rdi)
    addq $8, %rdi
    loop .Lupdate_data_section

    /* from now, RDI indicates virtual address of __bss_start */
    movq $__bss_start, %rax
    movq $__bss_end, %rcx
    addq $PAGE_MASK, %rcx
    subq %rax, %rcx
    shrq $PAGE_DIV_SHIFT, %rcx

    /* set XD bit in pte */
.Lupdate_bss_section:
    movq (%rdi), %rdx
    btsq $63, %rdx
    movq %rdx, (%rdi)
    addq $8, %rdi
    loop .Lupdate_bss_section
.endm

/* add mapping up to upper_mem, upper_mem suppports up to 512GB */
.macro map_up_to_uppper_memory
    leaq mmu_initial_mappings, %rdi
    movq 0x10(%edi), %r9

    subq $ONE_GB, %r9
    /* RBX stores how many pages(4KB) still unmapped */
    shrq $PAGE_DIV_SHIFT, %r9

    movq %r9, %rcx
    /* up round memory size to GB */
    addq $0x3ffff, %rcx
    /* RCX indicates how many GB should be mapped */
    shrq $18, %rcx

    /* start to allocate boot memory for PDs */
    movq $PHYS(boot_alloc_end), %rdi
    movq (%rdi), %rdx
    /* up round to page size */
    addq $PAGE_MASK, %rdx
    /* RDX indicates new PD base address (page aligned address of _end) */
    andq $X86_PAGE_ALIGN, %rdx

    /* store first new PD base address in RSI */
    movq %rdx, %rsi
    orq  $X86_KERNEL_PD_FLAGS, %rdx

    movq $PHYS(pdpt), %rdi
    movq $KERNEL_BASE, %rax
    /* RAX indicates PDPTE we have already mapped */
    andq $ADDR_OFFSET_MASK, %rax
    /* RAX indicates PDPTE we should start to fill in now */
    incq %rax
    shlq $3, %rax
    addq %rax, %rdi
.Lfill_upper_mem_pdpte:
    movq %rdx, (%rdi)
    addq $0x4, %rdi
    movq $0, (%rdi)
    addq $PAGE_SIZE, %rdx
    addq $0x4, %rdi
    loop .Lfill_upper_mem_pdpte

    /* RDI indicates PDE we should start to fill in */
    movq %rsi, %rdi
    /* RDX indicates new PT base address with page directory attribute */
    movq %rdx, %rsi
    /* RSI indicates base address of first PTE now */
    andq $X86_PAGE_ALIGN, %rsi

    movq %r9, %rcx
    /* upround to 2MB */
    addq $ADDR_OFFSET_MASK, %rcx
    /* RCX indicates how many 2MB should be mapped */
    shrq $9, %rcx

.Lfill_upper_mem_pde:
    movq %rdx, (%rdi)
    addq $0x4, %rdi
    movq $0, (%rdi)
    addq $PAGE_SIZE, %rdx
    addq $0x4, %rdi
    loop .Lfill_upper_mem_pde

    movq $MEMBASE, %rax
    addq $ONE_GB, %rax
    /* we have already mapped up to PA MEMBASE+KERNEL_LOAD_OFFSET+1GB */
    addq $KERNEL_LOAD_OFFSET, %rax
    /* RAX indicates first index of 4KB physical address to fill in PTE */
    shrq $PAGE_DIV_SHIFT, %rax
    /* ECX indicates how many PTEs should be filled */
    movq %r9, %rcx
    movq %rdx, %r9
    /* RBX incates page aligned address behind last PT */
    andq $X86_PAGE_ALIGN, %r9
.Lfill_upper_mem_pte:
    movq %rax, %rdx
    shlq $PAGE_DIV_SHIFT, %rdx
    addq $X86_KERNEL_PD_LP_FLAGS, %rdx
    movq %rdx, (%rsi)
    incq %rax
    addq $8, %rsi
    loop .Lfill_upper_mem_pte

    movq $PHYS(boot_alloc_end), %rdi
    movq %r9, (%edi)
.endm

.global _start
_start:
    jmp real_start

.align 8

.type multiboot_header,STT_OBJECT
multiboot_header:
    /* magic */
    .int MULTIBOOT_HEADER_MAGIC
    /* flags */
    .int MULTIBOOT_HEADER_FLAGS
    /* checksum */
    .int -(MULTIBOOT_HEADER_MAGIC + MULTIBOOT_HEADER_FLAGS)

#if !defined(__ELF__) || 1
    /* header_addr */
    .int PHYS(multiboot_header)
    /* load_addr */
    .int PHYS(_start)
    /* load_end_addr */
    .int PHYS(__data_end)
    /* bss_end_addr */
    .int PHYS(__bss_end)
    /* entry_addr */
    .int PHYS(real_start)
#endif

real_start:
    cmpq $MULTIBOOT_BOOTLOADER_MAGIC, %rax

    jne .Lno_multiboot_info
    movq %rbx, PHYS(_multiboot_info)

    /* RSI stores multiboot info address */
    leaq (%rbx), %rsi

    /*
     * first member in multiboot info structure is flag. If bit 0 in the flag
     * word is set, then mem_lower and mem_high indicate the amount of lower
     * and upper memory in kilobytes. Currently, we support this mode only.
     */
    movq 0(%rsi), %rcx
    andq $0x1, %rcx
    jz  .Lno_multiboot_info

    /* ECX stores high memory (KB) provided by multiboot info */
    movl 0x8(%rsi), %ecx

    /* halt machine if memory size to small */
    movq $__bss_end, %rdx
    movq $__code_start, %rax
    subq %rax, %rdx
    addq $MEMBASE, %rdx
    shrq $10, %rdx
    cmpq %rdx, %rcx
    jc .Lhalt

    movq %rcx, %rdx
    /* RDX indicates high memory in bytes */
    shlq $10, %rdx
    movq $MEMBASE, %rax

    /* RDX indicates memory size in bytes */
    subq %rax, %rdx

    /* halt machine if memory size to large */
    cmpq $MAX_MEM_SIZE, %rdx
    jnc .Lhalt

    leaq PHYS(mmu_initial_mappings), %rsi
    /*
     * RDI points to size of first entry in mmu_initial_mappings.
     * Contents of this entry will be used to init pmm arena,
     * mmu_initial_mappings is defined in platform.c.
     */
    leaq 0x10(%rsi), %rdi
    movq %rdx, (%rdi)

.Lno_multiboot_info:

    /* zero the bss section */
bss_setup:
    movq $PHYS(__bss_start), %rdi /* starting address of the bss */
    movq $PHYS(__bss_end), %rcx   /* find the length of the bss in bytes */
    subq %rdi, %rcx
    shrq $2, %rcx       /* convert to 32 bit words, since the bss is aligned anyway */
.Lzero_bss_sec:
    movq $0, (%rdi)
    addq $4, %rdi
    loop .Lzero_bss_sec

paging_setup:

    movl $MSR_EFER ,%ecx
    rdmsr
    /* NXE bit should be set, since we update XD bit in page table */
    orl $EFER_NXE,%eax
    wrmsr

    /* setting the corresponding PML4E to map from KERNEL_BASE */
    map_kernel_base pml4, pdpt, $9

    /* setting the corresponding PDPTE to map from KERNEL_BASE */
    map_kernel_base pdpt, pd, $0

    /*
     * calculate memory size to be mapped
     * CAUTION: we have only reserved page tables to cover 1GB mapping
     */
    leaq PHYS(mmu_initial_mappings), %rdx
    /* RAX indicates mmu_initial_mappings[0].size */
    movq 0x10(%rdx), %rax
    /* RAX indicates memsize in GB */
    shrq $30, %rax

    /* reload memsize in case memsize + MEMBASE < 1GB */
    movl 0x10(%rdx), %ecx
    /*
     * RAX indicates memsize + MEMBASE in GB
     * RCX = (RAX > 1GB) ? (1GB - MEMBASE) : memsize
     */
    test %rax, %rax
    jz  .Lsize_smaller_than_one_gb
    map_up_to_uppper_memory
    movq $ONE_GB, %rcx
.Lsize_smaller_than_one_gb:
    movq %rcx, %r9
    /* calculate offset of PD to map from KERNEL_BASE */
    movq $KERNEL_BASE, %rdx
    shrq $PD_SHIFT, %rdx
    andq $ADDR_OFFSET_MASK, %rdx
    shlq $3, %rdx
    movq $PHYS(pd), %rdi
    addq %rdx, %rdi

    /* calculate how many 2MB PDE should be used */
    shrq $PD_SHIFT, %rcx

    /* fill in PDEs */
    fill_page_table_entry $PHYS(pt), $X86_KERNEL_PD_FLAGS

    movq %r9, %rcx
    /* calculate how many 4KB PTE should be used */
    shrq $PAGE_DIV_SHIFT, %rcx
    movq $PHYS(pt), %rdi

    /* fill in PTEs */
    fill_page_table_entry $MEMBASE, $X86_KERNEL_PD_LP_FLAGS

    update_mapping_attribute_of_each_section

    map_trampoline

    /* switch page table */
    movq $PHYS(pml4), %rax
    movq %rax, %cr3

    lgdt PHYS(_gdtr_phys)

    movq $.Lfarjump64, %rax
    jmp  *%rax

.Lfarjump64:
    /* load the high kernel stack */
    movq  $(_kstack + 4096), %rsp
    pushq $CODE_64_SELECTOR
    pushq $.Lhighaddr
    lretq

.Lhighaddr:

    /* reload the gdtr */
    lgdt _gdtr

    /* set up all segments except Stack Selector to NULL selector */
    xorq %rax, %rax
    movw %ax, %ds
    movw %ax, %es
    movw %ax, %fs
    movw %ax, %gs

    /* set up Stack Segment */
    movq $STACK_64_SELECTOR, %rax
    movw %ax, %ss

    /* unmap low virtual address */
    movq $0, (pml4)

    /* set up gs base */
    leaq per_cpu_states(%rip), %rax

    movq %rax, %rdx
    shrq $32, %rdx
    movq $MSR_GS_BASE, %rcx
    /*
     * RCX - MSR index (MSR_GS_BASE)
     * EDX - high 32 bits value to write
     * EAX - low 32 bits value to write
     * MSR(RCX)(MSR_GS_BSSE) = EDX:EAX
     */
    wrmsr

    /* set up the idt */
    call setup_idt

    /* call the main module */
    call lk_main

.Lhalt:                     /* just sit around waiting for interrupts */
    hlt                     /* interrupts will unhalt the processor */
    pause
    jmp .Lhalt              /* so jump back to halt to conserve power */
