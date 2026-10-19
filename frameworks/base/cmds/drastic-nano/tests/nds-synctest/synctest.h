/*
 * GammaOS DS screen sync and input latency test ROM: the block the two CPUs share.
 *
 * The ARM7 owns the touchscreen controller and the X/Y buttons, so it polls them continuously
 * and publishes the state here; the ARM9 samples the block once per frame.
 */
#pragma once

typedef unsigned char u8;
typedef unsigned short u16;
typedef unsigned int u32;
typedef signed short s16;
typedef signed int s32;
typedef unsigned long long u64;

#define SYNC_MAGIC 0x4e595347u   /* "GSYN" */

typedef struct {
    u32 magic;
    u32 alive;       /* bumped on every ARM7 poll */
    u32 keysXY;      /* KEYXY, active high: bit 0 X, bit 1 Y */
    u32 penDown;     /* 1 while the pen is down */
    u32 rawX, rawY;  /* 12-bit touchscreen readings while the pen is down */
    u32 touchSeq;    /* bumped on each pen-down edge the ARM7 sees */
    /* ARM9 -> host: the counter drawn in the frame being prepared, for emulator-side probes */
    u32 frame9;
} SyncCtrl;

#define SYNC_CTRL ((volatile SyncCtrl*)0x02300000)
