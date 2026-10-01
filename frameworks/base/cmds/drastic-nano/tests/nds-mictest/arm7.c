/*
 * GammaOS DS microphone test ROM, ARM7 side.
 *
 * Samples the microphone through the touchscreen controller's AUX input in 12-bit mode (the
 * sequence libnds' micReadData12 uses), paced by timer 0 so the period is exact, and plays the
 * recording back on sound channel 0 as 16-bit PCM at the same rate. No interrupts: the timer's
 * overflow flag in IF is polled, which keeps the sample timing free of handler overhead.
 */
#include "mictest.h"

#define REG8(a)  (*(volatile u8*)(a))
#define REG16(a) (*(volatile u16*)(a))
#define REG32(a) (*(volatile u32*)(a))

#define SPICNT     REG16(0x040001C0)
#define SPIDATA    REG16(0x040001C2)
#define REG_IF     REG32(0x04000214)
#define REG_IME    REG16(0x04000208)
#define POWCNT2    REG16(0x04000304)
#define TM0D       REG16(0x04000100)
#define TM0CNT     REG16(0x04000102)
#define SOUNDCNT   REG16(0x04000500)
#define SOUNDBIAS  REG16(0x04000504)
#define SND0CNT    REG32(0x04000400)
#define SND0SAD    REG32(0x04000404)
#define SND0TMR    REG16(0x04000408)
#define SND0PNT    REG16(0x0400040A)
#define SND0LEN    REG32(0x0400040C)

#define SPI_ENABLE     (1 << 15)
#define SPI_CONTINUOUS (1 << 11)
#define SPI_BUSY       (1 << 7)
#define SPI_DEV_POWER  (0 << 8)
#define SPI_DEV_TOUCH  (2 << 8)
#define SPI_1MHZ       2
#define SPI_2MHZ       1

#define IF_TIMER0      (1 << 3)

static void spiWait(void) { while (SPICNT & SPI_BUSY) {} }

/* Power management device: reg | 0x80 reads, otherwise writes. */
static u8 pmTransfer(u8 reg, u8 data) {
    spiWait();
    SPICNT = SPI_ENABLE | SPI_DEV_POWER | SPI_1MHZ | SPI_CONTINUOUS;
    SPIDATA = reg;
    spiWait();
    SPICNT = SPI_ENABLE | SPI_DEV_POWER | SPI_1MHZ;
    SPIDATA = data;
    spiWait();
    return (u8)(SPIDATA & 0xFF);
}
static u8 pmRead(u8 reg) { return pmTransfer((u8)(reg | 0x80), 0); }
static void pmWrite(u8 reg, u8 v) { pmTransfer(reg, v); }

/* Register 0 bit 0 = sound amplifier on, bit 1 = mute. Register 2 bit 0 = microphone
 * amplifier on, register 3 = its gain (0..3). The backlight bits in register 0 are kept. */
static void micPower(u32 gain) {
    u8 c = pmRead(0);
    c = (u8)((c | 0x01) & ~0x02);
    pmWrite(0, c);
    pmWrite(2, 1);
    pmWrite(3, (u8)(gain & 3));
}

/* One 12-bit conversion of the AUX channel (the microphone). Command 0xE4: start bit, channel
 * 6, 12-bit mode, differential reference off, power down between conversions. */
static u16 micRead12(void) {
    spiWait();
    SPICNT = SPI_ENABLE | SPI_DEV_TOUCH | SPI_2MHZ | SPI_CONTINUOUS;
    SPIDATA = 0xE4;
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

static void timerStart(u32 period) {
    TM0CNT = 0;
    TM0D = (u16)(0x10000 - period);
    REG_IF = IF_TIMER0;
    TM0CNT = (1 << 7) | (1 << 6);         /* run at the bus clock, flag overflows in IF */
}

/* Wait for the next sample deadline. Returns 1 when it had already passed again by the time
 * the previous one was handled (the sample is then taken late). */
static int timerWait(void) {
    int late = (REG_IF & IF_TIMER0) ? 1 : 0;
    while (!(REG_IF & IF_TIMER0)) {}
    REG_IF = IF_TIMER0;
    return late;
}

static void soundStop(void) {
    SND0CNT = 0;
}

static void soundStart(u32 addr, u32 samples, u32 period) {
    soundStop();
    SND0SAD = addr;
    SND0TMR = (u16)(0x10000 - period / 2);  /* the sound timer runs at half the bus clock */
    SND0PNT = 0;
    SND0LEN = (samples * 2 + 3) / 4;         /* words */
    /* volume 127, pan centre, 16-bit PCM, one shot, start */
    SND0CNT = 127u | (64u << 16) | (1u << 29) | (2u << 27) | (1u << 31);
}

void main7(void) {
    volatile MicCtrl* c = MIC_CTRL;
    REG_IME = 0;
    POWCNT2 = (u16)(POWCNT2 | 1);           /* sound circuits on */
    SOUNDBIAS = 0x200;
    SOUNDCNT = (1 << 15) | 127;             /* master enable, full master volume */

    u32 seen = c->cmdSeq;
    u32 period = c->period ? c->period : kMicPeriod[1];
    u32 gain = c->gain & 3;
    micPower(gain);
    timerStart(period);
    c->state = ST_MONITOR;
    c->magic = MIC_MAGIC;

    u32 alive = 0;
    for (;;) {
        c->alive = ++alive;
        if (c->cmdSeq != seen) {
            seen = c->cmdSeq;
            const u32 cmd = c->cmd;
            c->ackSeq = seen;
            if (cmd == CMD_SETUP || cmd == CMD_RECORD) {
                if (c->period != period || (c->gain & 3) != gain) {
                    period = c->period ? c->period : kMicPeriod[1];
                    gain = c->gain & 3;
                    micPower(gain);
                }
            }
            if (cmd == CMD_RECORD) {
                soundStop();
                const u32 max = MIC_MAX_SECONDS * (MIC_CLOCK / period);
                volatile u16* buf = MIC_BUF;
                c->samples = 0;
                c->late = 0;
                c->recPeriod = period;
                c->state = ST_RECORDING;
                timerStart(period);
                u32 n = 0, late = 0, sp = 0;
                while (n < max && c->cmdSeq == seen) {
                    late += (u32)timerWait();
                    const u16 v = micRead12();
                    buf[n++] = v;
                    c->scope[sp] = v;
                    sp = (sp + 1) & 255;
                    if ((n & 63) == 0) { c->samples = n; c->scopePos = sp; c->late = late; }
                }
                c->samples = n; c->scopePos = sp; c->late = late;
                /* A stop that ended the recording early belongs to the recording: take it here.
                 * Left for the command handler, it switched straight back to monitoring, the
                 * ARM9 never saw ST_RECORDED and the recording could not be played. */
                if (c->cmdSeq != seen && c->cmd == CMD_STOP) {
                    seen = c->cmdSeq;
                    c->ackSeq = seen;
                }
                c->state = ST_RECORDED;      /* the ARM9 converts it, then asks for playback */
                continue;
            }
            if (cmd == CMD_PLAY || cmd == CMD_TONE) {
                const u32 n = c->playSamples, p = c->recPeriod ? c->recPeriod : period;
                c->playPos = 0;
                c->state = ST_PLAYING;
                timerStart(p);
                soundStart(c->playAddr, n, p);
                u32 pos = 0;
                while (pos < n && c->cmdSeq == seen) {
                    timerWait();
                    c->playPos = ++pos;
                }
                soundStop();
                c->state = ST_MONITOR;
                timerStart(period);
                continue;
            }
            if (cmd == CMD_STOP || cmd == CMD_SETUP) {
                soundStop();
                c->state = ST_MONITOR;
            }
            timerStart(period);
        }
        /* Monitor: keep sampling at the chosen rate so the level meter shows what a recording
         * would get, and so the gain can be set before recording. */
        if (c->state == ST_MONITOR) {
            u32 sp = c->scopePos & 255;
            for (int i = 0; i < 64 && c->cmdSeq == seen; i++) {
                timerWait();
                c->scope[sp] = micRead12();
                sp = (sp + 1) & 255;
            }
            c->scopePos = sp;
        }
    }
}
