ENTRY(_start)
SECTIONS {
    . = 0x02000000;
    .text : { *(.text.start) *(.text*) }
    .rodata : { *(.rodata*) }
    .data : { *(.data*) }
    . = ALIGN(4);
    __bss_start = .;
    .bss : { *(.bss*) *(COMMON) }
    . = ALIGN(4);
    __bss_end = .;
    /DISCARD/ : { *(.ARM.exidx*) *(.comment) }
}
