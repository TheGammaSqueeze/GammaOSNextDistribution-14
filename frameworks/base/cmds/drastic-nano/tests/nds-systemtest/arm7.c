/*
 * GammaOS DS system test ROM, ARM7 side: reads the RTC (Seiko serial RTC at
 * 0x04000138, the protocol libnds uses: command byte MSB first, data LSB first)
 * once per frame and publishes it at SYSTEST7 (see systest.h).
 */
#include "systest.h"

#define REG8(a)  (*(volatile u8*)(a))
#define REG16(a) (*(volatile u16*)(a))
#define RTC_CR8  REG8(0x04000138)
#define VCOUNT   REG16(0x04000006)

#define CS_0    (1 << 6)
#define CS_1    ((1 << 6) | (1 << 2))
#define SCK_0   (1 << 5)
#define SCK_1   ((1 << 5) | (1 << 1))
#define SIO_1   ((1 << 4) | (1 << 0))
#define SIO_OUT (1 << 4)
#define SIO_IN  (1 << 0)

#define READ_STATUS1   0x61
#define READ_DATETIME  0x65

static void delay(int n) { for (volatile int i = 0; i < n; i++) {} }

static void rtcTransaction(u8 command, u8* result, int resultLen) {
    RTC_CR8 = CS_0 | SCK_1 | SIO_1; delay(48);
    RTC_CR8 = CS_1 | SCK_1 | SIO_1; delay(48);
    u8 data = command;
    for (int bit = 0; bit < 8; bit++) {
        RTC_CR8 = (u8)(CS_1 | SCK_0 | SIO_OUT | (data >> 7)); delay(48);
        RTC_CR8 = (u8)(CS_1 | SCK_1 | SIO_OUT | (data >> 7)); delay(48);
        data = (u8)(data << 1);
    }
    for (int n = 0; n < resultLen; n++) {
        u8 v = 0;
        for (int bit = 0; bit < 8; bit++) {
            RTC_CR8 = CS_1 | SCK_0; delay(48);
            RTC_CR8 = CS_1 | SCK_1; delay(48);
            if (RTC_CR8 & SIO_IN) v |= (u8)(1 << bit);
        }
        result[n] = v;
    }
    delay(48);
    RTC_CR8 = CS_0 | SCK_1; delay(48);
}

void main7(void) {
    volatile Systest7* r = SYSTEST7;
    r->reads = 0;
    for (u32 n = 0;; n++) {
        while (VCOUNT >= 192) {}
        while (VCOUNT < 192) {}
        u8 dt[7], st;
        rtcTransaction(READ_DATETIME, dt, 7);
        rtcTransaction(READ_STATUS1, &st, 1);
        for (int i = 0; i < 7; i++) r->dateTime[i] = dt[i];
        r->status1 = st;
        r->reads = r->reads + 1;
        r->alive = n;
        r->magic = SYSTEST_MAGIC;
    }
}
