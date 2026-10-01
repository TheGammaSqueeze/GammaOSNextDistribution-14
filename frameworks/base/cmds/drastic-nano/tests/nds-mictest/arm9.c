/*
 * GammaOS DS microphone test ROM, ARM9 side: the screen, the controls, and the analysis.
 *
 * Records up to 30 seconds of 12-bit microphone input (sampled by the ARM7), then plays it back
 * at the same rate. After each recording the raw samples are checked for the faults that make a
 * microphone sound distorted or crackly - clipping, held (repeated) values, sudden jumps, missing
 * low bits, late samples - and the recording is converted in place to signed 16-bit with its DC
 * offset removed for playback. Bare metal, no libnds: the top screen is engine A in VRAM display
 * mode (a 256x192 15-bit framebuffer in VRAM bank A).
 *
 *   A       record (A or B stops early)      START   play the recording
 *   B       stop                             SELECT  play a 1 kHz test tone
 *   LEFT/RIGHT  sample rate                  UP/DOWN microphone gain
 */
#include "mictest.h"
#include "font8x8.h"

#define REG8(a)  (*(volatile u8*)(a))
#define REG16(a) (*(volatile u16*)(a))
#define REG32(a) (*(volatile u32*)(a))

#define DISPCNT   REG32(0x04000000)
#define VCOUNT    REG16(0x04000006)
#define KEYINPUT  REG16(0x04000130)
#define VRAMCNT_A REG8(0x04000240)
#define WRAMCNT   REG8(0x04000247)
#define POWCNT1   REG16(0x04000304)
#define FB        ((volatile u16*)0x06800000)

#define KEY_A      (1 << 0)
#define KEY_B      (1 << 1)
#define KEY_SELECT (1 << 2)
#define KEY_START  (1 << 3)
#define KEY_RIGHT  (1 << 4)
#define KEY_LEFT   (1 << 5)
#define KEY_UP     (1 << 6)
#define KEY_DOWN   (1 << 7)

/* 3 seconds of test tone at the fastest rate, clear of the recording and the shared block. */
#define TONE_BUF     ((volatile s16*)0x02310000)
#define TONE_SECONDS 3

#define RGB(r, g, b) ((u16)((r) | ((g) << 5) | ((b) << 10)))

/* The compiler may call these for struct copies and clears. */
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

static u16 gBack[256 * 192];   /* drawn here, then copied during vblank */

static void clear(u16 c) { for (int i = 0; i < 256 * 192; i++) gBack[i] = c; }

static void pix(int x, int y, u16 c) {
    if ((unsigned)x < 256 && (unsigned)y < 192) gBack[y * 256 + x] = c;
}
static void rect(int x, int y, int w, int h, u16 c) {
    for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) pix(x + i, y + j, c);
}
static void vline(int x, int y0, int y1, u16 c) {
    if (y0 > y1) { int t = y0; y0 = y1; y1 = t; }
    for (int y = y0; y <= y1; y++) pix(x, y, c);
}

static void putc8(int col, int row, char ch, u16 fg) {
    if (col < 0 || col >= 32 || row < 0 || row >= 24) return;
    if (ch < 32 || ch > 126) ch = '?';
    const unsigned char* g = kFont8x8[ch - 32];
    for (int y = 0; y < 8; y++)
        for (int x = 0; x < 8; x++)
            if (g[y] & (0x80 >> x)) gBack[(row * 8 + y) * 256 + col * 8 + x] = fg;
}
static int puts8(int col, int row, const char* s, u16 fg) {
    while (*s) putc8(col++, row, *s++, fg);
    return col;
}
static int dec(int col, int row, u32 v, int minDigits, u16 fg) {
    char buf[12]; int n = 0;
    do { buf[n++] = (char)('0' + v % 10); v /= 10; } while (v && n < 11);
    while (n < minDigits && n < 11) buf[n++] = '0';
    while (n) putc8(col++, row, buf[--n], fg);
    return col;
}
/* tenths: 123 -> "12.3" */
static int dec1(int col, int row, u32 tenths, u16 fg) {
    col = dec(col, row, tenths / 10, 1, fg);
    putc8(col++, row, '.', fg);
    return dec(col, row, tenths % 10, 1, fg);
}

static void waitVblank(void) {
    while (VCOUNT >= 192) {}
    while (VCOUNT < 192) {}
}

/* ---- analysis -------------------------------------------------------------------------- */

typedef struct {
    u32 valid;
    u32 n, period;
    u32 mean, minV, maxV;
    u32 clipLo, clipHi;      /* samples at 0 / 4095 */
    u32 rms;                 /* AC RMS in raw units */
    u32 longestRun;          /* longest run of identical consecutive samples */
    u32 heldRuns;            /* runs of 16 or more identical samples */
    u32 jumps;               /* steps larger than JUMP_LIMIT between neighbours */
    u32 lowBitsUsed;         /* OR of the low 4 bits over the recording (0 = 8-bit data) */
    u32 levels;              /* distinct 12-bit values seen */
    u32 late;                /* ARM7 deadlines met late */
} Stats;

#define JUMP_LIMIT 1024      /* a quarter of full scale in one sample */
#define HELD_RUN   16

static Stats gStats;
static u8 gSeen[4096 / 8];
static s16 gEnvMin[256], gEnvMax[256];

static u32 isqrt64(u64 v) {
    u64 r = 0, b = (u64)1 << 62;
    while (b > v) b >>= 2;
    while (b) {
        if (v >= r + b) { v -= r + b; r = (r >> 1) + b; } else r >>= 1;
        b >>= 2;
    }
    return (u32)r;
}

/* Analyse the raw 12-bit recording, then convert it in place to signed 16-bit with the DC
 * offset removed, and build the 256-column envelope for the screen. */
static void analyseAndConvert(u32 n, u32 period, u32 late) {
    volatile u16* raw = MIC_BUF;
    Stats s; memset(&s, 0, sizeof(s));
    memset(gSeen, 0, sizeof(gSeen));
    s.n = n; s.period = period; s.late = late; s.minV = 4095; s.maxV = 0;
    u64 sum = 0;
    u32 run = 1, prev = n ? raw[0] : 0;
    for (u32 i = 0; i < n; i++) {
        const u32 v = raw[i] & 0x0FFF;
        sum += v;
        if (v < s.minV) s.minV = v;
        if (v > s.maxV) s.maxV = v;
        if (v == 0) s.clipLo++;
        if (v == 4095) s.clipHi++;
        s.lowBitsUsed |= v & 15;
        gSeen[v >> 3] |= (u8)(1 << (v & 7));
        if (i) {
            const s32 d = (s32)v - (s32)prev;
            if (d > JUMP_LIMIT || d < -JUMP_LIMIT) s.jumps++;
            if (v == prev) {
                run++;
            } else {
                if (run >= HELD_RUN) s.heldRuns++;
                if (run > s.longestRun) s.longestRun = run;
                run = 1;
            }
        }
        prev = v;
    }
    if (n) {
        if (run >= HELD_RUN) s.heldRuns++;
        if (run > s.longestRun) s.longestRun = run;
    }
    s.mean = n ? (u32)(sum / n) : 2048;
    for (int i = 0; i < 4096 / 8; i++)
        for (int b = 0; b < 8; b++) s.levels += (gSeen[i] >> b) & 1;

    u64 sq = 0;
    for (int c = 0; c < 256; c++) { gEnvMin[c] = 32767; gEnvMax[c] = -32768; }
    volatile s16* out = (volatile s16*)MIC_BUF;
    for (u32 i = 0; i < n; i++) {
        const s32 d = (s32)(raw[i] & 0x0FFF) - (s32)s.mean;
        sq += (u64)(u32)(d * d);
        s32 v = d * 16;
        if (v > 32767) v = 32767;
        if (v < -32768) v = -32768;
        out[i] = (s16)v;
        const int col = (int)((u64)i * 256 / n);
        if (v < gEnvMin[col]) gEnvMin[col] = (s16)v;
        if (v > gEnvMax[col]) gEnvMax[col] = (s16)v;
    }
    s.rms = n ? isqrt64(sq / n) : 0;
    s.valid = 1;
    gStats = s;
}

/* 1 kHz sine at half full scale, TONE_SECONDS long, at the given sample period. */
static u32 makeTone(u32 period) {
    const u32 rate = MIC_CLOCK / period;
    const u32 n = TONE_SECONDS * rate;
    /* Oscillator y[k] = 2cos(w) y[k-1] - y[k-2] in Q30: exact enough for a test tone. */
    const double w = 2.0 * 3.14159265358979323846 * 1000.0 / (double)rate;
    double c = 1.0, t = 1.0;                    /* cos(w) by its series */
    for (int k = 1; k < 12; k++) { t *= -w * w / (double)((2 * k - 1) * (2 * k)); c += t; }
    double sn = w, ts = w;                      /* sin(w) by its series */
    for (int k = 1; k < 12; k++) { ts *= -w * w / (double)((2 * k) * (2 * k + 1)); sn += ts; }
    double y1 = 0.0, y0 = sn;                   /* sin(0), sin(w) */
    volatile s16* out = TONE_BUF;
    out[0] = 0;
    for (u32 i = 1; i < n; i++) {
        out[i] = (s16)(y0 * 16384.0);
        const double y = 2.0 * c * y0 - y1;
        y1 = y0; y0 = y;
    }
    return n;
}

/* ---- main ------------------------------------------------------------------------------ */

static void sendCmd(volatile MicCtrl* c, u32 cmd) {
    c->cmd = cmd;
    c->cmdSeq = c->cmdSeq + 1;
}

static const char* const kGain[4] = { "20x", "40x", "80x", "160x" };

void main9(void) {
    WRAMCNT = 3;               /* shared WRAM to the ARM7, where its code lives */
    POWCNT1 = 0x8003;          /* LCDs + engine A on, engine A on the top screen */
    VRAMCNT_A = 0x80;          /* bank A enabled, LCDC */
    DISPCNT = 0x00020000;      /* display mode 2: framebuffer from VRAM A */

    volatile MicCtrl* c = MIC_CTRL;
    int rateIdx = 1, gain = 2;   /* 32.7 kHz, 80x: a safe start; both change with the d-pad */
    c->period = kMicPeriod[rateIdx];
    c->gain = (u32)gain;

    const u16 bg = RGB(2, 3, 6), white = RGB(31, 31, 31), grey = RGB(18, 18, 20),
              gold = RGB(31, 26, 8), green = RGB(10, 31, 12), red = RGB(31, 8, 8),
              blue = RGB(10, 18, 31), dim = RGB(6, 7, 10), amber = RGB(31, 20, 4);

    u16 prevKeys = 0;
    int converted = 1;             /* no recording yet: nothing to convert */
    int haveRec = 0, playingTone = 0;
    u32 recN = 0, recPeriod = 0;

    for (u32 frame = 0;; frame++) {
        const u16 keys = (u16)(~KEYINPUT & 0x3FF);
        const u16 down = (u16)(keys & ~prevKeys);
        prevKeys = keys;
        const u32 st = (c->magic == MIC_MAGIC) ? c->state : ST_BOOT;
        const int idle = (st == ST_MONITOR);

        if (st == ST_RECORDED && !converted) {
            recN = c->samples; recPeriod = c->recPeriod;
            analyseAndConvert(recN, recPeriod, c->late);
            converted = 1; haveRec = recN > 0;
            sendCmd(c, CMD_SETUP);   /* back to monitoring */
        }

        if (idle) {
            int changed = 0;
            if ((down & KEY_RIGHT) && rateIdx > 0)            { rateIdx--; changed = 1; }
            if ((down & KEY_LEFT) && rateIdx < MIC_RATES - 1) { rateIdx++; changed = 1; }
            if ((down & KEY_UP) && gain < 3)                  { gain++; changed = 1; }
            if ((down & KEY_DOWN) && gain > 0)                { gain--; changed = 1; }
            if (changed) {
                c->period = kMicPeriod[rateIdx]; c->gain = (u32)gain;
                sendCmd(c, CMD_SETUP);
            } else if (down & KEY_A) {
                converted = 0; haveRec = 0; gStats.valid = 0;
                c->period = kMicPeriod[rateIdx]; c->gain = (u32)gain;
                sendCmd(c, CMD_RECORD);
            } else if ((down & KEY_START) && haveRec) {
                c->playAddr = (u32)MIC_BUF; c->playSamples = recN;
                playingTone = 0;
                sendCmd(c, CMD_PLAY);
            } else if (down & KEY_SELECT) {
                /* The tone plays at the selected rate; a recording keeps its own. */
                const u32 n = makeTone(kMicPeriod[rateIdx]);
                c->recPeriod = kMicPeriod[rateIdx];
                c->playAddr = (u32)TONE_BUF; c->playSamples = n;
                playingTone = 1;
                sendCmd(c, CMD_TONE);
            }
        } else if (st == ST_RECORDING && (down & (KEY_A | KEY_B))) {
            sendCmd(c, CMD_STOP);
        } else if (st == ST_PLAYING && (down & KEY_B)) {
            sendCmd(c, CMD_STOP);
        }
        /* The tone borrowed recPeriod: put the recording's back once it has finished. */
        if (playingTone && st == ST_MONITOR && haveRec) c->recPeriod = recPeriod;

        /* ---- draw ---- */
        clear(bg);
        puts8(0, 0, "GammaOS DS mic test v1", gold);
        int col;
        col = puts8(0, 2, "State: ", grey);
        if (st == ST_BOOT) puts8(col, 2, "waiting for ARM7", red);
        else if (st == ST_MONITOR) puts8(col, 2, haveRec ? "ready (monitoring)" : "monitoring", green);
        else if (st == ST_RECORDING) {
            col = puts8(col, 2, "RECORDING ", red);
            const u32 rate = MIC_CLOCK / (c->recPeriod ? c->recPeriod : 1);
            col = dec1(col, 2, rate ? c->samples * 10 / rate : 0, white);
            puts8(col, 2, " s", white);
        } else if (st == ST_RECORDED) puts8(col, 2, "analysing", amber);
        else if (st == ST_PLAYING) puts8(col, 2, playingTone ? "playing 1 kHz tone" : "playing", blue);

        col = puts8(0, 3, "Rate:  ", grey);
        col = dec(col, 3, MIC_CLOCK / kMicPeriod[rateIdx], 1, white);
        puts8(col, 3, " Hz 12-bit  <>", white);
        col = puts8(0, 4, "Gain:  ", grey);
        col = puts8(col, 4, kGain[gain], white);
        puts8(col + 1, 4, "(up/down)", dim);

        /* Level: peak-to-peak of the last 256 samples, as a bar of the full 12-bit range. */
        u32 lo = 4095, hi = 0;
        for (int i = 0; i < 256; i++) { const u32 v = c->scope[i] & 0x0FFF; if (v < lo) lo = v; if (v > hi) hi = v; }
        const u32 pp = hi >= lo ? hi - lo : 0;
        puts8(0, 5, "Level", grey);
        rect(48, 41, 160, 6, dim);
        rect(48, 41, (int)(pp * 160 / 4095), 6, (lo == 0 || hi == 4095) ? red : green);
        if (lo == 0 || hi == 4095) puts8(27, 5, "CLIP", red);

        /* Scope (live) or the recording's envelope with the play position. */
        const int sy = 52, sh = 56, mid = sy + sh / 2;
        rect(0, sy, 256, sh, RGB(1, 2, 4));
        for (int x = 0; x < 256; x += 2) pix(x, mid, dim);
        if (haveRec && st != ST_RECORDING && !(st == ST_PLAYING && playingTone)) {
            for (int x = 0; x < 256; x++) {
                const int y0 = mid - gEnvMax[x] * (sh / 2) / 32768;
                const int y1 = mid - gEnvMin[x] * (sh / 2) / 32768;
                vline(x, y0, y1, blue);
            }
            if (st == ST_PLAYING && recN) vline((int)((u64)c->playPos * 256 / recN), sy, sy + sh - 1, gold);
        } else {
            const u32 p = c->scopePos & 255;
            int py = mid;
            for (int x = 0; x < 256; x++) {
                const s32 v = (s32)(c->scope[(p + (u32)x) & 255] & 0x0FFF) - 2048;
                const int y = mid - v * (sh / 2) / 2048;
                vline(x, x ? py : y, y, green);
                py = y;
            }
        }

        /* Recording length bar. */
        if (st == ST_RECORDING) {
            const u32 rate = MIC_CLOCK / (c->recPeriod ? c->recPeriod : 1);
            const u32 max = MIC_MAX_SECONDS * rate;
            rect(0, 110, 256, 3, dim);
            rect(0, 110, (int)((u64)c->samples * 256 / (max ? max : 1)), 3, red);
        }

        /* Analysis of the last recording. */
        if (gStats.valid) {
            const Stats* s = &gStats;
            const u32 rate = MIC_CLOCK / s->period;
            col = puts8(0, 14, "Rec ", grey);
            col = dec1(col, 14, s->n * 10 / rate, white);
            col = puts8(col, 14, "s ", white);
            col = dec(col, 14, s->n, 1, white);
            puts8(col, 14, " smp", white);
            col = puts8(0, 15, "DC ", grey); col = dec(col, 15, s->mean, 1, white);
            col = puts8(col + 1, 15, "range ", grey); col = dec(col, 15, s->minV, 1, white);
            putc8(col++, 15, '-', white); dec(col, 15, s->maxV, 1, white);
            col = puts8(0, 16, "RMS ", grey); col = dec(col, 16, s->rms, 1, white);
            col = puts8(col + 1, 16, "levels ", grey); dec(col, 16, s->levels, 1, white);
            const u32 clips = s->clipLo + s->clipHi;
            col = puts8(0, 17, "Clipped ", grey);
            col = dec(col, 17, clips, 1, clips ? red : green);
            puts8(col + 1, 17, "smp", grey);
            col = puts8(0, 18, "Held runs ", grey);
            col = dec(col, 18, s->heldRuns, 1, s->heldRuns ? red : green);
            col = puts8(col + 1, 18, "longest ", grey);
            dec(col, 18, s->longestRun, 1, s->longestRun >= HELD_RUN ? red : white);
            col = puts8(0, 19, "Jumps ", grey);
            col = dec(col, 19, s->jumps, 1, s->jumps ? red : green);
            col = puts8(col + 1, 19, "late ", grey);
            dec(col, 19, s->late, 1, s->late ? amber : green);
            col = puts8(0, 20, "Low 4 bits ", grey);
            puts8(col, 20, s->lowBitsUsed ? "used (12-bit)" : "ALL ZERO (8-bit)",
                  s->lowBitsUsed ? green : red);
        }

        puts8(0, 22, "A rec  B stop  START play", dim);
        puts8(0, 23, "SELECT 1kHz tone", dim);

        waitVblank();
        for (int i = 0; i < 256 * 192; i++) FB[i] = gBack[i];
        (void)frame;
    }
}
