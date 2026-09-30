/*
 * GammaOS DS system test ROM: shared result layout.
 *
 * The ROM reads what a DS game sees of the System settings and publishes it in
 * main RAM at fixed addresses, so a harness can read it from the emulator's
 * memory as well as from the screen:
 *   - firmware user settings: the 0x70-byte copy at 0x027FFC80 (colour, birthday,
 *     nickname, language), exactly what games read;
 *   - the RTC, read by the ARM7 through the serial RTC port (0x04000138);
 *   - the Slot-2 bus as the ARM9 sees it (GBA ROM space, open-bus pattern, SRAM).
 */
#pragma once

typedef unsigned char u8;
typedef unsigned short u16;
typedef unsigned int u32;

#define SYSTEST_MAGIC   0x54595347u   /* "GSYT" */
#define SYSTEST_VERSION 1u

/* ARM9 results, main RAM. */
typedef struct {
    u32 magic;            /* SYSTEST_MAGIC once the first pass is published */
    u32 version;
    u32 frames;           /* ARM9 frame counter */
    u8  fwRaw[0x70];      /* copy of 0x027FFC80 */
    /* decoded */
    u32 fwVersion;
    u32 color;
    u32 month;
    u32 day;
    u32 nickLen;
    u16 nick[10];
    u32 language;         /* 0x027FFCE4 bits 0-2 */
    /* Slot-2 */
    u32 slot2Probed;      /* 1 after the probe */
    u16 gbaRom[16];       /* first halfwords at 0x08000000 */
    u32 gbaIs96;          /* byte at 0x080000B2 (0x96 on a GBA cartridge) */
    u32 openBusMiss;      /* halfwords 0..99 that differ from the open-bus value (addr/2) */
    u32 rumbleMiss;       /* halfwords 0..99 that differ from (i & 0xFFFD) */
    u32 sramBefore;       /* byte at 0x0A000000 */
    u32 sramReadback;     /* after writing (sramBefore ^ 0xA5) */
    u32 sramWritable;     /* readback matched */
} Systest9;

/* ARM7 results, main RAM. */
typedef struct {
    u32 magic;            /* SYSTEST_MAGIC */
    u32 reads;            /* RTC reads done */
    u8  dateTime[7];      /* BCD: year, month, day, weekday, hour (bit 6 = PM), minute, second */
    u8  status1;
    u32 alive;            /* ARM7 loop counter */
} Systest7;

#define SYSTEST9 ((volatile Systest9*)0x02300000)
#define SYSTEST7 ((volatile Systest7*)0x02300200)
