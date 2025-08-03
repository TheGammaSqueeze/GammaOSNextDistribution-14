/*
 * Copyright (c) 2009 Corey Tabaka
 * Copyright (c) 2012-2019 LK Trusty Authors. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#include <arch/x86.h>
#include <bits.h>
#include <debug.h>
#include <dev/interrupt/x86_interrupts.h>
#include <dev/timer/x86_pit.h>
#include <err.h>
#include <lib/fixed_point.h>
#include <platform/interrupts.h>
#include <platform/timer.h>

static platform_timer_callback t_callback;

static struct fp_32_64 ms_per_tsc;
static struct fp_32_64 ns_per_tsc;
static struct fp_32_64 ticks_per_ms;

/* The oscillator used by the PIT chip runs at 1.193182MHz */
#define INTERNAL_FREQ   1193182UL

/* Maximum amount of time that can be program, in milliseconds */
#define MAX_TIMER_INTERVAL  55

/*
 * Freqency in HZ of the timer.
 * TODO: Select the frequency in platform_set_oneshot_timer based on the time
 * we actually need to sleep so we don't wake up more often than needed when
 * sleeping more than 55ms and so we get better precision when sleeping
 * significantly less than 55ms.
 */
#define PIT_FREQUENCY 1000

/* Mode/Command register */
#define I8253_CONTROL_REG   0x43
/* Channel 0 data port */
#define I8253_DATA_REG      0x40

static uint64_t lk_time_to_ticks(lk_time_t lk_time)
{
    return u64_mul_u32_fp32_64(lk_time, ticks_per_ms);
}

static lk_time_t tsc_cnt_to_lk_time(uint64_t tsc_cnt)
{
    return u32_mul_u64_fp32_64(tsc_cnt, ms_per_tsc);
}

static lk_time_ns_t tsc_cnt_to_lk_time_ns(uint64_t tsc_cnt)
{
    return u64_mul_u64_fp32_64(tsc_cnt, ns_per_tsc);
}

static inline void serializing_instruction(void)
{
    uint32_t unused = 0;

    cpuid(unused, &unused, &unused, &unused, &unused);
}

static bool is_constant_tsc_avail(void)
{
    uint32_t version_info;
    uint32_t unused;
    uint8_t  family;
    uint8_t  model;

    cpuid(X86_CPUID_VERSION_INFO, &version_info, &unused, &unused, &unused);
    model  = (version_info >> 4) & 0x0F;
    family = (version_info >> 8) & 0x0F;

    /*
     * According to description in IDSM vol3 chapter 17.17, the time stamp
     * counter of following processors increments at a constant rate.
     * TSC would be stopped at deep ACPI C-states.
     */
    return !!(((family == 0x0F) && (model > 0x03)) ||
            ((family == 0x06) && (model == 0x0E)) ||
            ((family == 0x06) && (model == 0x0F)) ||
            ((family == 0x06) && (model == 0x17)) ||
            ((family == 0x06) && (model == 0x1C)));
}

static bool is_invariant_tsc_avail(void)
{
    uint32_t invariant_tsc;
    uint32_t unused;

    cpuid(0x80000007, &unused, &unused, &unused, &invariant_tsc);

    /*
     * According to description in IDSM vol3 chapter 17.17, the invariant
     * TSC will run at a constant rate in all ACPI P-, C- and T-states.
     */
    return !!BIT(invariant_tsc, 8);
}

lk_time_ns_t current_time_ns(void)
{
    return tsc_cnt_to_lk_time_ns(__rdtsc());
}

lk_time_t current_time(void)
{
    return tsc_cnt_to_lk_time(__rdtsc());
}

static enum handler_return os_timer_tick(void* arg)
{
    if (t_callback) {
        return t_callback(arg, current_time_ns());
    } else {
        return INT_NO_RESCHEDULE;
    }
}

static void set_pit_frequency(uint32_t frequency)
{
    struct fp_32_64 result;
    uint16_t count;

    /* Figure out the correct divisor for the desired frequency */
    if (frequency < 1) {
        dprintf(SPEW, "invalid frequency, %u, use max count\n", frequency);
        count = 0xffff;
    } else if (frequency >= INTERNAL_FREQ) {
        dprintf(SPEW, "invalid frequency, %u, use min count\n", frequency);
        count = 1;
    } else {
        fp_32_64_div_32_32(&result, INTERNAL_FREQ, frequency);
        if (result.l0 > 0xffff) {
            dprintf(SPEW, "invalid frequency, %u, use max count\n", frequency);
            count = 0xffff;
        } else {
            count = result.l0 & 0xffff;
        }
    }

    /*
     * Setup the Programmable Interval Timer
     * Counter 0, mode 2, binary counter, LSB followed by MSB
     */
    outp(I8253_CONTROL_REG, 0x34);
    outp(I8253_DATA_REG, count & 0xff);
    outp(I8253_DATA_REG, count >> 8);
}

static void x86_pit_init_conversion_factors(void)
{
    uint64_t begin, end;
    uint8_t status = 0;

    fp_32_64_div_32_32(&ticks_per_ms, INTERNAL_FREQ, 1000);

    /* Set PIT mode to count down and set OUT pin high when count reaches 0 */
    outp(I8253_CONTROL_REG, 0x30);

    /*
     * According to ISDM vol3 chapter 17.17:
     *  The RDTSC instruction is not serializing or ordered with other
     *  instructions. Subsequent instructions may begin execution before
     *  the RDTSC instruction operation is performed.
     *
     * Insert serializing instruction before and after RDTSC to make
     * calibration more accurate.
     */
    serializing_instruction();
    begin = __rdtsc();
    serializing_instruction();

    /* Write LSB in counter 0 */
    outp(I8253_DATA_REG, ticks_per_ms.l0 & 0xff);
    /* Write MSB in counter 0 */
    outp(I8253_DATA_REG, ticks_per_ms.l0 >> 8);

    do {
        /* Read-back command, count MSB, counter 0 */
        outp(I8253_CONTROL_REG, 0xE2);
        /* Wait till OUT pin goes high and null count goes low */
        status = inp(I8253_DATA_REG);
    } while ((status & 0xC0) != 0x80);

    /* Make sure all instructions above executed and submitted */
    serializing_instruction();
    end = __rdtsc();
    serializing_instruction();

    /* Enable interrupt mode that will stop the decreasing counter of the PIT */
    outp(I8253_CONTROL_REG, 0x30);

    fp_32_64_div_32_32(&ms_per_tsc, 1, (end - begin));
    fp_32_64_div_32_32(&ns_per_tsc, 1000 * 1000, (end - begin));
}

void x86_init_pit(void)
{
    if(!is_invariant_tsc_avail() && !is_constant_tsc_avail()) {
        dprintf(INFO, "CAUTION: Current time is inaccurate!\n");
    }

    x86_pit_init_conversion_factors();

    /* 1ms granularity */
    set_pit_frequency(PIT_FREQUENCY);

    register_int_handler(INT_PIT, &os_timer_tick, NULL);
    unmask_interrupt(INT_PIT);
}

status_t platform_set_oneshot_timer(platform_timer_callback callback,
                                    lk_time_ns_t time_ns)
{
    uint32_t count;
    uint16_t divisor;
    uint32_t time_ms;

    t_callback = callback;
    /* Set millisecond timer with interval */
    time_ms = (time_ns - current_time_ns()) / (1000UL * 1000);

    if (time_ms > MAX_TIMER_INTERVAL) {
        time_ms = MAX_TIMER_INTERVAL;
    } else if (time_ms < 1) {
        time_ms = 1;
    }

    count = lk_time_to_ticks(time_ms);

    divisor = count & 0xffff;
    /*
     * Program PIT in the software strobe configuration, to send one pulse
     * after the count reach 0
     */
    outp(I8253_CONTROL_REG, 0x38);
    outp(I8253_DATA_REG, divisor & 0xff);
    outp(I8253_DATA_REG, divisor >> 8);

    return NO_ERROR;
}

void platform_stop_timer(void)
{
    /* Enable interrupt mode that will stop the decreasing counter of the PIT */
    outp(I8253_CONTROL_REG, 0x30);
}
