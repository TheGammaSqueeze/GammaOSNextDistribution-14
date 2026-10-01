/*
 * GammaOS DS microphone test ROM: the block the two CPUs share, in main RAM.
 *
 * The ARM9 writes a command (cmd + cmdSeq), the ARM7 carries it out and reports its state.
 * The recording itself is kept at MIC_BUF as raw 12-bit samples while recording, and is
 * converted in place to signed 16-bit (DC removed) for playback. The block and the buffer are
 * at fixed addresses so a harness can read them out of an emulator's memory.
 */
#pragma once

typedef unsigned char u8;
typedef unsigned short u16;
typedef unsigned int u32;
typedef signed short s16;
typedef signed int s32;
typedef unsigned long long u64;

#define MIC_MAGIC        0x43494d47u     /* "GMIC" */
#define MIC_MAX_SECONDS  30

/* Sample periods in ARM7 bus cycles (33513982 Hz). Even, so the sound channel, whose timer
 * runs at half that clock, plays back at exactly the rate the recording was made at. */
#define MIC_CLOCK        33513982u
#define MIC_RATES        5
static const u32 kMicPeriod[MIC_RATES] = { 704, 1024, 1536, 2048, 4096 };
/* 47605, 32728, 21819, 16364, 8182 Hz */

/* The buffer holds the longest recording at the fastest rate: 30 s * 47605 Hz. */
#define MIC_BUF          ((volatile u16*)0x02040000)
#define MIC_BUF_SAMPLES  (MIC_MAX_SECONDS * (MIC_CLOCK / 704 + 1))

enum { CMD_NONE = 0, CMD_RECORD, CMD_STOP, CMD_PLAY, CMD_TONE, CMD_SETUP };
enum { ST_BOOT = 0, ST_MONITOR, ST_RECORDING, ST_RECORDED, ST_PLAYING };

typedef struct {
    u32 magic;
    /* ARM9 -> ARM7 */
    u32 cmd;
    u32 cmdSeq;          /* bumped by the ARM9 after cmd and the arguments are written */
    u32 period;          /* sample period, from kMicPeriod */
    u32 gain;            /* mic amplifier gain 0..3 = 20x, 40x, 80x, 160x */
    u32 playSamples;     /* CMD_PLAY / CMD_TONE: how many 16-bit samples at playAddr */
    u32 playAddr;
    /* ARM7 -> ARM9 */
    u32 ackSeq;          /* the last cmdSeq the ARM7 has taken */
    u32 state;
    u32 samples;         /* recorded so far / recorded in total */
    u32 playPos;         /* samples played so far */
    u32 late;            /* sample deadlines the ARM7 met late (the next one was already due) */
    u32 alive;
    u16 scope[256];      /* the most recent raw samples, ring buffer */
    u32 scopePos;
    u32 recPeriod;       /* the period the current recording was made at */
} MicCtrl;

#define MIC_CTRL ((volatile MicCtrl*)0x02300000)
