/*
 * GammaOS DS screen sync and input latency test ROM, ARM9 side.
 *
 * Both screens show the same large vblank counter, drawn in the same frame and blitted to both
 * displays by DMA inside the same vblank, so on a correctly synced pair of panels the two numbers
 * always match. Any button press turns the background red, any touch turns it green (both:
 * amber), and the background returns to normal after 5 seconds without that input. The frame
 * number at which each event was first seen is shown on both screens, together with the number
 * of frames that have passed since.
 *
 * Bare metal, no libnds. Both engines run mode 5 with BG2 as a 256x256 direct colour bitmap.
 * Everything is drawn straight into VRAM, never into main RAM, which is far too slow on hardware
 * for a full redraw every frame: engine A (top) flips between banks A and B, the bottom picture
 * is built in bank D (LCDC) and copied to bank C (engine B) by DMA inside the vblank.
 * Missed vblanks are counted with a free-running timer so a slow frame cannot hide.
 */
#include "synctest.h"
#include "font8x8.h"

#define REG8(a)  (*(volatile u8*)(a))
#define REG16(a) (*(volatile u16*)(a))
#define REG32(a) (*(volatile u32*)(a))

#define DISPCNT_A  REG32(0x04000000)
#define VCOUNT     REG16(0x04000006)
#define KEYINPUT   REG16(0x04000130)
#define TM0D       REG16(0x04000100)
#define TM0CNT     REG16(0x04000102)
#define TM1D       REG16(0x04000104)
#define TM1CNT     REG16(0x04000106)
#define DMA3SAD    REG32(0x040000D4)
#define DMA3DAD    REG32(0x040000D8)
#define DMA3CNT    REG32(0x040000DC)
#define VRAMCNT_A  REG8(0x04000240)
#define VRAMCNT_B  REG8(0x04000241)
#define VRAMCNT_C  REG8(0x04000242)
#define VRAMCNT_D  REG8(0x04000243)
#define WRAMCNT    REG8(0x04000247)
#define POWCNT1    REG16(0x04000304)
#define BG2CNT_A   REG16(0x0400000C)
#define BG2PA_A    REG16(0x04000020)
#define BG2PB_A    REG16(0x04000022)
#define BG2PC_A    REG16(0x04000024)
#define BG2PD_A    REG16(0x04000026)
#define BG2X_A     REG32(0x04000028)
#define BG2Y_A     REG32(0x0400002C)
#define MBRIGHT_A  REG16(0x0400006C)
#define DISPCNT_B  REG32(0x04001000)
#define BG2CNT_B   REG16(0x0400100C)
#define MBRIGHT_B  REG16(0x0400106C)
#define BG2PA_B    REG16(0x04001020)
#define BG2PB_B    REG16(0x04001022)
#define BG2PC_B    REG16(0x04001024)
#define BG2PD_B    REG16(0x04001026)
#define BG2X_B     REG32(0x04001028)
#define BG2Y_B     REG32(0x0400102C)

#define FB_TOP_A   ((u16*)0x06000000)   /* VRAM A: engine A BG, bitmap base 0 */
#define FB_TOP_B   ((u16*)0x06020000)   /* VRAM B: engine A BG, bitmap base 128 KB */
#define FB_BOTTOM  ((u16*)0x06200000)   /* VRAM C: engine B BG */
#define FB_BUILD   ((u16*)0x06860000)   /* VRAM D in LCDC mode: the bottom picture being built */
#define FB_BYTES   (256 * 192 * 2)
/* BG2CNT: 256x256 direct colour bitmap, bitmap base in 16 KB units (0 = bank A, 8 = bank B). */
#define BG2CNT_BITMAP(base) ((u16)(0x4084 | ((base) << 8)))
#define PAL_BACKDROP_A REG16(0x05000000)   /* BG palette entry 0: what transparent pixels show */
#define PAL_BACKDROP_B REG16(0x05000400)

#define KEY_A      (1 << 0)
#define KEY_B      (1 << 1)
#define KEY_SELECT (1 << 2)
#define KEY_START  (1 << 3)
#define KEY_RIGHT  (1 << 4)
#define KEY_LEFT   (1 << 5)
#define KEY_UP     (1 << 6)
#define KEY_DOWN   (1 << 7)
#define KEY_R      (1 << 8)
#define KEY_L      (1 << 9)
#define KEY_X      (1 << 10)
#define KEY_Y      (1 << 11)

#define HOLD_FRAMES 300          /* 5 seconds at 60 Hz */
/* Timer 0 at F/1024 = 32728 Hz, one frame = 560190 cycles / 1024 = 547 ticks. */
#define TICKS_PER_FRAME 547

/* Bit 15 is the alpha bit of a direct colour bitmap pixel: clear means transparent. */
#define RGB(r, g, b) ((u16)(0x8000 | (r) | ((g) << 5) | ((b) << 10)))

void* memcpy(void* d, const void* s, __SIZE_TYPE__ n) {
    u8* dd = (u8*)d; const u8* ss = (const u8*)s;
    while (n--) *dd++ = *ss++;
    return d;
}
void* memset(void* d, int c, __SIZE_TYPE__ n) {
    u8* dd = (u8*)d;
    while (n--) *dd++ = (u8)c;
    return d;
}

static u16* gBuf = FB_TOP_A;

/* VRAM writes go through the write buffer (enableCaches): drain it before a DMA reads them. */
static void drainWriteBuffer(void) {
    __asm__ volatile("mcr p15, 0, %0, c7, c10, 4" : : "r"(0) : "memory");
}

static void dmaCopy(const void* src, volatile void* dst, u32 bytes) {
    drainWriteBuffer();
    DMA3SAD = (u32)src;
    DMA3DAD = (u32)dst;
    DMA3CNT = (1u << 31) | (1u << 26) | (bytes / 4);
    while (DMA3CNT & (1u << 31)) {}
}

/* Zero (transparent) rows y0..y1-1 of the current buffer with 32-byte block stores. The
 * background is the backdrop colour showing through, so a colour change costs nothing and a
 * clear never has to read anything (a DMA fill re-reads its source in slow main RAM for
 * every word it writes). */
static void clearRows(int y0, int y1) {
    u32* p = (u32*)(gBuf + y0 * 256);
    u32 blocks = (u32)(y1 - y0) * 256 * 2 / 32;
    __asm__ volatile(
        "mov r2, #0\n mov r3, #0\n mov r4, #0\n mov r5, #0\n"
        "mov r6, #0\n mov r7, #0\n mov r8, #0\n mov r9, #0\n"
        "1: stmia %0!, {r2-r9}\n"
        "subs %1, %1, #1\n"
        "bne 1b\n"
        : "+r"(p), "+r"(blocks)
        :
        : "r2", "r3", "r4", "r5", "r6", "r7", "r8", "r9", "cc", "memory");
}
static void rect(int x, int y, int w, int h, u16 c) {
    if (x < 0) { w += x; x = 0; }
    if (y < 0) { h += y; y = 0; }
    if (x + w > 256) w = 256 - x;
    if (y + h > 192) h = 192 - y;
    if (w <= 0 || h <= 0) return;
    for (int j = 0; j < h; j++) {
        u16* row = gBuf + (y + j) * 256 + x;
        for (int i = 0; i < w; i++) row[i] = c;
    }
}

/* Character at pixel (x, y), each font pixel drawn as a scale x scale block; each run of set
 * pixels in a glyph row is one rectangle. */
static void putc(int x, int y, char ch, int scale, u16 fg) {
    if (ch < 32 || ch > 126) ch = '?';
    const unsigned char* g = kFont8x8[ch - 32];
    for (int gy = 0; gy < 8; gy++) {
        const unsigned bits = g[gy];
        for (int gx = 0; gx < 8;) {
            if (!(bits & (0x80u >> gx))) { gx++; continue; }
            int run = 1;
            while (gx + run < 8 && (bits & (0x80u >> (gx + run)))) run++;
            rect(x + gx * scale, y + gy * scale, run * scale, scale, fg);
            gx += run;
        }
    }
}
static int puts(int x, int y, const char* s, int scale, u16 fg) {
    while (*s) { putc(x, y, *s++, scale, fg); x += 8 * scale; }
    return x;
}
static int dec(int x, int y, u32 v, int minDigits, int scale, u16 fg) {
    char buf[12]; int n = 0;
    do { buf[n++] = (char)('0' + v % 10); v /= 10; } while (v && n < 11);
    while (n < minDigits && n < 11) buf[n++] = '0';
    while (n) { putc(x, y, buf[--n], scale, fg); x += 8 * scale; }
    return x;
}

static void waitVblank(void) {
    while (VCOUNT >= 192) {}
    while (VCOUNT < 192) {}
}

static u32 timerNow(void) {
    u16 hi = TM1D, lo = TM0D;
    while (hi != TM1D) { hi = TM1D; lo = TM0D; }
    return ((u32)hi << 16) | lo;
}

static const char* keyName(u16 keys) {
    static const char* const names[12] = { "A", "B", "SELECT", "START", "RIGHT", "LEFT",
                                           "UP", "DOWN", "R", "L", "X", "Y" };
    for (int i = 0; i < 12; i++) if (keys & (1 << i)) return names[i];
    return "-";
}

typedef struct {
    u32 frame;           /* vblank number the latest press/touch was first seen in */
    u32 count;           /* presses / touches so far */
    u32 until;           /* background stays coloured while frame < until */
    u32 missedBefore;    /* missed vblanks at the time of the event */
} Event;

/* Timer ticks (F/1024) to tenths of a millisecond. */
static u32 ticksToTenthsMs(u32 t) { return t * 3055u / 10000u; }

static int tenths(int x, int y, u32 v, u16 fg) {
    x = dec(x, y, v / 10, 1, 1, fg);
    putc(x, y, '.', 1, fg);
    return dec(x + 8, y, v % 10, 1, 1, fg);
}

static void drawScreen(u32 frame, u32 missed, const Event* key, const Event* touch,
                       u16 keysHeld, const char* lastKey, const volatile SyncCtrl* c, u32 arm7ok,
                       u32 workTicks, u32 copyTicks) {
    const u16 white = RGB(31, 31, 31), grey = RGB(20, 20, 22), dim = RGB(12, 12, 14),
              red = RGB(31, 10, 10), green = RGB(12, 31, 12), gold = RGB(31, 26, 8);
    /* Only the rows that carry text; the rest stays transparent from the start. */
    clearRows(0, 58);
    clearRows(68, 108);
    clearRows(120, 168);
    clearRows(176, 192);
    puts(0, 0, "GammaOS DS sync test", 1, grey);

    /* The counter: six digits at 5x, 240 px wide. */
    dec(8, 16, frame % 1000000, 6, 5, white);

    int x;
    x = puts(0, 68, "KEY  ", 2, grey);
    x = dec(x, 68, key->count ? key->frame % 1000000 : 0, 6, 2, key->count ? red : dim);
    if (key->count) {
        x = puts(x + 8, 68, "+", 2, grey);
        dec(x, 68, frame - key->frame, 1, 2, white);
    }
    x = puts(0, 92, "TOUCH", 2, grey);
    x = dec(x + 16, 92, touch->count ? touch->frame % 1000000 : 0, 6, 2, touch->count ? green : dim);
    if (touch->count) {
        x = puts(x + 8, 92, "+", 2, grey);
        dec(x, 92, frame - touch->frame, 1, 2, white);
    }

    x = puts(0, 120, "presses ", 1, grey); x = dec(x, 120, key->count, 1, 1, white);
    x = puts(x + 8, 120, "last ", 1, grey); x = puts(x, 120, lastKey, 1, white);
    x = puts(x + 8, 120, "held ", 1, grey); puts(x, 120, keysHeld ? keyName(keysHeld) : "-", 1, white);
    x = puts(0, 130, "touches ", 1, grey); x = dec(x, 130, touch->count, 1, 1, white);
    x = puts(x + 8, 130, "pen ", 1, grey); x = puts(x, 130, c->penDown ? "DOWN" : "up", 1, c->penDown ? green : white);
    x = puts(x + 8, 130, "raw ", 1, grey); x = dec(x, 130, c->rawX, 1, 1, white);
    putc(x, 130, ',', 1, grey); dec(x + 8, 130, c->rawY, 1, 1, white);
    x = puts(0, 140, "missed vblanks ", 1, grey); dec(x, 140, missed, 1, 1, missed ? gold : white);
    x = puts(0, 150, "arm7 ", 1, grey); puts(x, 150, arm7ok ? "ok" : "NOT RUNNING", 1, arm7ok ? white : red);
    /* Time the previous frame's work took (drawing and copies) and its vblank copy, in ms. */
    x = puts(0, 160, "work ", 1, grey); x = tenths(x, 160, ticksToTenthsMs(workTicks), white);
    x = puts(x + 8, 160, "vblank copy ", 1, grey); tenths(x, 160, ticksToTenthsMs(copyTicks), white);

    puts(0, 176, "any button: red   touch: green", 1, dim);
    puts(0, 184, "back to normal after 5 s idle", 1, dim);

    /* Corner counters (6 digits at 2x, 96x16, on a dark box): filmed together they show each
     * panel's scan order and timing, and so where a frame store or a turned image sits. */
    {
        static const int cx[4] = { 0, 160, 0, 160 }, cy[4] = { 0, 0, 176, 176 };
        for (int i = 0; i < 4; i++) {
            rect(cx[i], cy[i], 96, 16, RGB(0, 0, 0));
            dec(cx[i], cy[i], frame % 1000000, 6, 2, white);
        }
    }

    /* Frame barcode for emulator-side probes: 24 cells of 8x4 px at x 32..223, rows 60..63
     * (a band nothing else uses), white = 1, MSB first. Bits 23..4 are the frame number's low
     * 20 bits, bits 3..0 the XOR of its five nibbles, so a reader can reject a torn picture. */
    {
        const u32 f = frame & 0xFFFFF;
        const u32 check = (f ^ (f >> 4) ^ (f >> 8) ^ (f >> 12) ^ (f >> 16)) & 0xF;
        const u32 code = (f << 4) | check;
        for (int i = 0; i < 24; i++)
            rect(32 + 8 * i, 60, 8, 4, (code >> (23 - i)) & 1 ? white : RGB(0, 0, 0));
    }
}

static void drawMarker(const volatile SyncCtrl* c) {
    const u16 green = RGB(12, 31, 12);
    if (c->penDown) {
        /* Crude raw-to-screen mapping of the touch position: a marker, not a calibration. */
        int px = ((int)c->rawX - 256) * 256 / 3584, py = ((int)c->rawY - 256) * 192 / 3584;
        if (px < 0) px = 0;
        if (px > 255) px = 255;
        if (py < 0) py = 0;
        if (py > 191) py = 191;
        rect(px - 3, py - 3, 7, 7, green);
        rect(px - 1, py - 12, 3, 25, green);
        rect(px - 12, py - 1, 25, 3, green);
    }
}

/* Protection unit, caches and write buffer. Region 0: everything, uncached. Region 1: main RAM
 * (code, font, globals), cached write-through. Region 2: the block the ARM7 writes, uncached
 * and unbuffered (a higher region wins). Region 3: VRAM, uncached but
 * buffered, so drawing does not stall on every store. */
static void enableCaches(void) {
    const u32 r0 = 0x00000000u | (31u << 1) | 1u;   /* 4 GB */
    const u32 r1 = 0x02000000u | (21u << 1) | 1u;   /* 4 MB main RAM */
    const u32 r2 = 0x02300000u | (11u << 1) | 1u;   /* 4 KB shared block */
    const u32 r3 = 0x06000000u | (23u << 1) | 1u;   /* 16 MB VRAM */
    __asm__ volatile(
        "mcr p15, 0, %0, c6, c0, 0\n"
        "mcr p15, 0, %1, c6, c1, 0\n"
        "mcr p15, 0, %2, c6, c2, 0\n"
        "mcr p15, 0, %6, c6, c3, 0\n"
        /* A loader can leave regions 4-7 enabled. They outrank 0-3 and the access word below
         * gives them no access, so any of them still covering VRAM, I/O or main RAM aborts the
         * first touch on real hardware (emulators do not enforce the MPU). Switch them off. */
        "mov r0, #0\n"
        "mcr p15, 0, r0, c6, c4, 0\n"
        "mcr p15, 0, r0, c6, c5, 0\n"
        "mcr p15, 0, r0, c6, c6, 0\n"
        "mcr p15, 0, r0, c6, c7, 0\n"
        "mcr p15, 0, %3, c2, c0, 0\n"   /* data cacheable: region 1 */
        "mcr p15, 0, %3, c2, c0, 1\n"   /* instruction cacheable: region 1 */
        "mcr p15, 0, %4, c3, c0, 0\n"   /* write buffer: region 3 (region 1 stays write-through) */
        "mcr p15, 0, %5, c5, c0, 2\n"   /* data access: RW for regions 0-3 */
        "mcr p15, 0, %5, c5, c0, 3\n"   /* instruction access: RW for regions 0-3 */
        "mov r0, #0\n"
        "mcr p15, 0, r0, c7, c5, 0\n"   /* invalidate instruction cache */
        "mcr p15, 0, r0, c7, c6, 0\n"   /* invalidate data cache */
        "mrc p15, 0, r0, c1, c0, 0\n"
        "orr r0, r0, #0x1000\n"         /* instruction cache */
        "orr r0, r0, #0x0005\n"         /* data cache + protection unit */
        "mcr p15, 0, r0, c1, c0, 0\n"
        :
        : "r"(r0), "r"(r1), "r"(r2), "r"(0x02u), "r"(0x08u), "r"(0x3333u), "r"(r3)
        : "r0", "memory");
}

void main9(void) {
    enableCaches();
    WRAMCNT = 3;                 /* shared WRAM to the ARM7 */
    POWCNT1 = 0x8203;            /* LCDs, engine A (top) and engine B (bottom) on */
    VRAMCNT_A = 0x81;            /* bank A: engine A BG VRAM at 0x06000000 */
    VRAMCNT_B = 0x89;            /* bank B: engine A BG VRAM at 0x06020000 */
    VRAMCNT_C = 0x84;            /* bank C: engine B BG VRAM at 0x06200000 */
    VRAMCNT_D = 0x80;            /* bank D: LCDC at 0x06860000, the bottom staging buffer */
    gBuf = FB_TOP_A; clearRows(0, 192);
    gBuf = FB_TOP_B; clearRows(0, 192);
    gBuf = FB_BOTTOM; clearRows(0, 192);
    gBuf = FB_BUILD; clearRows(0, 192);
    /* Both engines: mode 5, BG2 as a 256x256 direct colour bitmap at base 0, identity affine. */
    BG2CNT_A = BG2CNT_BITMAP(0);
    BG2PA_A = 0x100; BG2PB_A = 0; BG2PC_A = 0; BG2PD_A = 0x100;
    BG2X_A = 0; BG2Y_A = 0;
    BG2CNT_B = BG2CNT_BITMAP(0);
    BG2PA_B = 0x100; BG2PB_B = 0; BG2PC_B = 0; BG2PD_B = 0x100;
    BG2X_B = 0; BG2Y_B = 0;
    MBRIGHT_A = 0; MBRIGHT_B = 0;
    DISPCNT_A = 0x00010405;      /* graphics display, mode 5, BG2 on */
    DISPCNT_B = 0x00010405;

    TM0CNT = 0; TM1CNT = 0;
    TM0D = 0; TM1D = 0;
    TM1CNT = 0x80 | 4;           /* cascade */
    TM0CNT = 0x80 | 3;           /* F/1024 */

    const u16 bgNormal = RGB(2, 3, 6), bgKey = RGB(18, 3, 3), bgTouch = RGB(3, 16, 3), bgBoth = RGB(20, 16, 3);
    volatile SyncCtrl* c = SYNC_CTRL;

    Event key = { 0, 0, 0, 0 }, touch = { 0, 0, 0, 0 };
    u16 prevKeys = 0;
    u32 prevPen = 0, prevTouchSeq = 0, prevAlive = 0;
    const char* lastKey = "-";
    u32 missed = 0;
    u32 lastTick = timerNow();
    u32 workTicks = 0, copyTicks = 0;
    int shownTop = 0;            /* engine A shows bank A (0) or bank B (1) */

    for (u32 frame = 0;; frame++) {
        c->frame9 = frame;
        const u32 arm7ok = (c->magic == SYNC_MAGIC);
        const u32 xy = arm7ok ? c->keysXY : 0;
        const u16 keys = (u16)((~KEYINPUT & 0x3FF) | ((xy & 3) << 10));
        const u16 down = (u16)(keys & ~prevKeys);
        prevKeys = keys;
        if (down) { key.frame = frame; key.count++; key.missedBefore = missed; lastKey = keyName(down); }
        if (keys) key.until = frame + HOLD_FRAMES;

        /* A tap shorter than a frame still counts: the ARM7 bumps touchSeq on every pen-down edge. */
        const u32 pen = arm7ok ? c->penDown : 0;
        const u32 seq = arm7ok ? c->touchSeq : 0;
        if ((pen && !prevPen) || seq != prevTouchSeq) {
            touch.frame = frame; touch.count++; touch.missedBefore = missed;
        }
        if (pen) touch.until = frame + HOLD_FRAMES;
        prevPen = pen; prevTouchSeq = seq;
        (void)prevAlive;

        const int keyOn = frame < key.until, touchOn = frame < touch.until;
        const u16 bgc = keyOn && touchOn ? bgBoth : keyOn ? bgKey : touchOn ? bgTouch : bgNormal;

        /* Both screens show the same picture (apart from the touch marker): draw it once into
         * the hidden top bank, copy it to the bottom staging bank and add the marker there. At
         * the vblank the top flips banks and the staging bank is copied to the bottom (VRAM to
         * VRAM, about 1.5 ms, well inside the 4.5 ms vblank), so both change in the same vblank. */
        u16* const top = shownTop ? FB_TOP_A : FB_TOP_B;
        gBuf = top;
        drawScreen(frame, missed, &key, &touch, keys, lastKey, c, arm7ok, workTicks, copyTicks);
        dmaCopy(top, FB_BUILD, FB_BYTES);
        gBuf = FB_BUILD;
        drawMarker(c);
        drainWriteBuffer();
        workTicks = timerNow() - lastTick;

        waitVblank();
        shownTop ^= 1;
        BG2CNT_A = BG2CNT_BITMAP(shownTop ? 8 : 0);
        PAL_BACKDROP_A = bgc;
        PAL_BACKDROP_B = bgc;
        const u32 copyStart = timerNow();
        dmaCopy(FB_BUILD, FB_BOTTOM, FB_BYTES);

        const u32 now = timerNow();
        copyTicks = now - copyStart;
        const u32 dt = now - lastTick;
        lastTick = now;
        if (frame && dt > TICKS_PER_FRAME + TICKS_PER_FRAME / 2)
            missed += (dt + TICKS_PER_FRAME / 2) / TICKS_PER_FRAME - 1;
    }
}
