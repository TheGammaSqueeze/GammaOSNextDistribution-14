/*
 * GammaOS DS system test ROM, ARM9 side: shows the firmware user settings, the
 * RTC (read by the ARM7) and the Slot-2 bus on the top screen, and publishes them
 * at SYSTEST9 (see systest.h). Bare metal, no libnds: the top screen is engine A in
 * VRAM display mode (a 256x192 15-bit framebuffer in VRAM bank A).
 */
#include "systest.h"
#include "font8x8.h"

#define REG8(a)  (*(volatile u8*)(a))
#define REG16(a) (*(volatile u16*)(a))
#define REG32(a) (*(volatile u32*)(a))

#define DISPCNT   REG32(0x04000000)
#define VCOUNT    REG16(0x04000006)
#define EXMEMCNT  REG16(0x04000204)
#define VRAMCNT_A REG8(0x04000240)
#define POWCNT1   REG16(0x04000304)
#define FB        ((volatile u16*)0x06800000)

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

static void clear(u16 c) {
    for (int i = 0; i < 256 * 192; i++) gBack[i] = c;
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

static char hexd(u32 v) { v &= 15; return (char)(v < 10 ? '0' + v : 'A' + v - 10); }

static int hex(int col, int row, u32 v, int digits, u16 fg) {
    for (int i = digits - 1; i >= 0; i--) putc8(col++, row, hexd(v >> (i * 4)), fg);
    return col;
}

static int dec(int col, int row, u32 v, int minDigits, u16 fg) {
    char buf[12]; int n = 0;
    do { buf[n++] = (char)('0' + v % 10); v /= 10; } while (v && n < 11);
    while (n < minDigits && n < 11) buf[n++] = '0';
    while (n) putc8(col++, row, buf[--n], fg);
    return col;
}

static u32 bcd(u8 v) { return (u32)(v >> 4) * 10 + (v & 15); }

static void waitVblank(void) {
    while (VCOUNT >= 192) {}
    while (VCOUNT < 192) {}
}

static void probeSlot2(volatile Systest9* r) {
    /* bit 7 = 0: the ARM9 owns the Slot-2 bus */
    EXMEMCNT = (u16)(EXMEMCNT & ~0x80);
    volatile u16* rom = (volatile u16*)0x08000000;
    for (int i = 0; i < 16; i++) r->gbaRom[i] = rom[i];
    r->gbaIs96 = *(volatile u8*)0x080000B2;
    u32 ob = 0, rb = 0;
    for (u32 i = 0; i < 100; i++) {
        const u16 v = rom[i];
        if (v != (u16)(i & 0xFFFF)) ob++;
        if (v != (u16)(i & 0xFFFD)) rb++;
    }
    r->openBusMiss = ob;
    r->rumbleMiss = rb;
    volatile u8* sram = (volatile u8*)0x0A000000;
    const u8 before = sram[0];
    sram[0] = (u8)(before ^ 0xA5);
    const u8 after = sram[0];
    sram[0] = before;                  /* leave the cartridge's save as it was */
    r->sramBefore = before;
    r->sramReadback = after;
    r->sramWritable = (after == (u8)(before ^ 0xA5)) ? 1 : 0;
    r->slot2Probed = 1;
}

static const char* const kLang[8] = { "Japanese", "English", "French", "German",
                                      "Italian", "Spanish", "Chinese", "Korean" };
static const char* const kMonth[13] = { "?", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

void main9(void) {
    POWCNT1 = 0x8003;          /* LCDs + engine A on, engine A on the top screen */
    VRAMCNT_A = 0x80;          /* bank A enabled, LCDC */
    DISPCNT = 0x00020000;      /* display mode 2: framebuffer from VRAM A */

    volatile Systest9* r = SYSTEST9;
    volatile Systest7* r7 = SYSTEST7;
    r->version = SYSTEST_VERSION;
    probeSlot2(r);

    const u16 bg = RGB(2, 3, 6), white = RGB(31, 31, 31), grey = RGB(20, 20, 22),
              gold = RGB(31, 26, 8), green = RGB(10, 31, 12), red = RGB(31, 10, 10);
    for (u32 frame = 0;; frame++) {
        /* Firmware user settings, as games read them. */
        const volatile u8* fw = (const volatile u8*)0x027FFC80;
        for (int i = 0; i < 0x70; i++) r->fwRaw[i] = fw[i];
        r->fwVersion = fw[0] | (fw[1] << 8);
        r->color = fw[2];
        r->month = fw[3];
        r->day = fw[4];
        r->nickLen = fw[0x1A] | (fw[0x1B] << 8);
        for (int i = 0; i < 10; i++) r->nick[i] = (u16)(fw[6 + 2 * i] | (fw[7 + 2 * i] << 8));
        r->language = fw[0x64] & 7;
        r->frames = frame;
        r->magic = SYSTEST_MAGIC;

        clear(bg);
        puts8(0, 0, "GammaOS DS system test v1", gold);
        int c;
        c = puts8(0, 2, "Nickname: ", grey);
        for (u32 i = 0; i < r->nickLen && i < 10; i++) {
            const u16 u = r->nick[i];
            putc8(c++, 2, (u >= 32 && u < 127) ? (char)u : '*', white);
        }
        puts8(0, 3, "  UTF-16:", grey);
        for (u32 i = 0; i < r->nickLen && i < 10; i++)   /* five code units per line */
            hex(10 + (int)(i % 5) * 5, 3 + (int)(i / 5), r->nick[i], 4, white);
        c = puts8(0, 5, "  length ", grey); dec(c, 5, r->nickLen, 1, white);
        c = puts8(0, 6, "Birthday: ", grey);
        c = puts8(c, 6, kMonth[r->month <= 12 ? r->month : 0], white);
        c = dec(c + 1, 6, r->day, 1, white);
        c = puts8(0, 7, "Colour:   ", grey); dec(c, 7, r->color, 1, white);
        c = puts8(0, 8, "Language: ", grey); dec(c, 8, r->language, 1, white);
        puts8(c + 2, 8, kLang[r->language], white);
        c = puts8(0, 9, "FW ver:   ", grey); hex(c, 9, r->fwVersion, 4, white);

        if (r7->magic == SYSTEST_MAGIC) {
            const volatile u8* t = r7->dateTime;
            c = puts8(0, 11, "RTC: ", grey);
            c = dec(c, 11, 2000 + bcd(t[0]), 4, green); putc8(c++, 11, '-', green);
            c = dec(c, 11, bcd(t[1]), 2, green); putc8(c++, 11, '-', green);
            c = dec(c, 11, bcd(t[2]), 2, green); c++;
            c = dec(c, 11, bcd(t[4] & 0x3F), 2, green); putc8(c++, 11, ':', green);
            c = dec(c, 11, bcd(t[5]), 2, green); putc8(c++, 11, ':', green);
            dec(c, 11, bcd(t[6]), 2, green);
            c = puts8(0, 12, "  status1 ", grey); c = hex(c, 12, r7->status1, 2, white);
            c = puts8(c + 1, 12, "reads ", grey); dec(c, 12, r7->reads, 1, white);
        } else {
            puts8(0, 11, "RTC: waiting for the ARM7", red);
        }

        c = puts8(0, 14, "Slot-2 ROM: ", grey);
        for (int i = 0; i < 4; i++) c = hex(c, 14, r->gbaRom[i], 4, white) + 1;
        c = puts8(0, 15, "  is96 ", grey); c = hex(c, 15, r->gbaIs96, 2, white);
        c = puts8(c + 1, 15, "openbus miss ", grey); dec(c, 15, r->openBusMiss, 1, white);
        c = puts8(0, 16, "  rumble miss ", grey); dec(c, 16, r->rumbleMiss, 1, white);
        c = puts8(0, 17, "  SRAM ", grey); c = hex(c, 17, r->sramBefore, 2, white);
        c = puts8(c + 1, 17, "->", grey); c = hex(c + 1, 17, r->sramReadback, 2, white);
        puts8(c + 1, 17, r->sramWritable ? "writable" : "read-only", r->sramWritable ? green : grey);

        c = puts8(0, 19, "Frame ", grey); dec(c, 19, frame, 1, white);
        if (r7->magic == SYSTEST_MAGIC) { c = puts8(c + 2, 19, "ARM7 ", grey); dec(c, 19, r7->alive, 1, white); }

        waitVblank();
        for (int i = 0; i < 256 * 192; i++) FB[i] = gBack[i];
    }
}
