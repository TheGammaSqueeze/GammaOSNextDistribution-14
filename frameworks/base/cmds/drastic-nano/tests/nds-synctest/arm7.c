/*
 * GammaOS DS screen sync and input latency test ROM, ARM7 side.
 *
 * Polls the pen-down line (KEYXY bit 6) and the X/Y buttons as fast as it can, reads the
 * touchscreen position through the TSC2046 while the pen is down, and publishes everything in
 * the shared block for the ARM9. No interrupts.
 */
#include "synctest.h"

#define REG16(a) (*(volatile u16*)(a))
#define REG32(a) (*(volatile u32*)(a))

#define SPICNT   REG16(0x040001C0)
#define SPIDATA  REG16(0x040001C2)
#define REG_IME  REG16(0x04000208)
#define KEYXY    REG16(0x04000136)

#define SPI_ENABLE     (1 << 15)
#define SPI_CONTINUOUS (1 << 11)
#define SPI_BUSY       (1 << 7)
#define SPI_DEV_TOUCH  (2 << 8)
#define SPI_2MHZ       1

#define TSC_MEASURE_Y  0x90
#define TSC_MEASURE_X  0xD0

static void spiWait(void) { while (SPICNT & SPI_BUSY) {} }

/* One 12-bit conversion of the given TSC2046 channel. */
static u16 tscRead12(u8 cmd) {
    spiWait();
    SPICNT = SPI_ENABLE | SPI_DEV_TOUCH | SPI_2MHZ | SPI_CONTINUOUS;
    SPIDATA = cmd;
    spiWait();
    SPIDATA = 0x00;
    spiWait();
    const u16 hi = SPIDATA;
    SPICNT = SPI_ENABLE | SPI_DEV_TOUCH | SPI_2MHZ;
    SPIDATA = 0x00;
    spiWait();
    const u16 lo = SPIDATA;
    return (u16)(((hi & 0x7F) << 5) | ((lo >> 3) & 0x1F));
}

void main7(void) {
    REG_IME = 0;
    volatile SyncCtrl* c = SYNC_CTRL;
    c->alive = 0; c->keysXY = 0; c->penDown = 0; c->rawX = 0; c->rawY = 0; c->touchSeq = 0; c->frame9 = 0;
    c->magic = SYNC_MAGIC;
    u32 wasDown = 0;
    for (;;) {
        const u16 xy = KEYXY;
        const u32 down = (xy & (1 << 6)) ? 0 : 1;
        c->keysXY = (u32)(~xy & 3);
        if (down) {
            c->rawX = tscRead12(TSC_MEASURE_X);
            c->rawY = tscRead12(TSC_MEASURE_Y);
            if (!wasDown) c->touchSeq = c->touchSeq + 1;
        }
        c->penDown = down;
        wasDown = down;
        c->alive = c->alive + 1;
    }
}
