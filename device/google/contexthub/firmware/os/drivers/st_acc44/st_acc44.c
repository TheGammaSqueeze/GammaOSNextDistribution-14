/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <atomic.h>
#include <gpio.h>
#include <isr.h>
#include <nanohubPacket.h>
#include <plat/exti.h>
#include <plat/gpio.h>
#include <platform.h>
#include <plat/syscfg.h>
#include <plat/rtc.h>
#include <sensors.h>
#include <seos.h>
#include <slab.h>
#include <heap.h>
#include <i2c.h>
#include <timer.h>
#include <variant/sensType.h>
#include <cpu/cpuMath.h>
#include <floatRt.h>

#include <stdlib.h>
#include <string.h>
#include <variant/variant.h>

#define ST_ACC44_APP_ID            APP_ID_MAKE(NANOHUB_VENDOR_STMICRO, 7)

/* Sensor registers */
#define ST_ACC44_WAI_REG_ADDR      0x0F
#define ST_ACC44_WAI_REG_VAL       0x44

/*
 * CTRL1 Register
 *
 * CTRL1[7:4] := ODR
 * CTRL1[3:2] := MODE
 * CTRL1[1:0] := LP_MODE
 */
#define ST_ACC44_CTRL1_REG         0x20
#define ST_ACC44_ODR_POWER_DOWN    0x00
#define ST_ACC44_ODR_12_5_HZ       0x20
#define ST_ACC44_ODR_25_HZ         0x30
#define ST_ACC44_ODR_50_HZ         0x40
#define ST_ACC44_ODR_100_HZ        0x50
#define ST_ACC44_ODR_200_HZ        0x60
#define ST_ACC44_ODR_400_HZ        0x70
#define ST_ACC44_ODR_800_HZ        0x80
#define ST_ACC44_ODR_1600_HZ       0x90
#define ST_ACC44_HIPERF_MODE       0x04
#define ST_ACC44_CTRL1_DEFVAL      (ST_ACC44_HIPERF_MODE)

/*
 * CTRL2 Register
 *
 * CTRL2[7]   := BOOT
 * CTRL2[6]   := SOFT_RESET
 * CTRL2[3]   := BDU
 */
#define ST_ACC44_CTRL2_REG         0x21
#define ST_ACC44_CTRL2_BOOT        0x80
#define ST_ACC44_CTRL2_SW_RST      0x40
#define ST_ACC44_CTRL2_BDU         0x08
#define ST_ACC44_CTRL2_IF_ADD_INC  0x04
#define ST_ACC44_CTRL2_DEFVAL      (ST_ACC44_CTRL2_BDU | ST_ACC44_CTRL2_IF_ADD_INC)

/*
 * CTRL3 Register
 */
#define ST_ACC44_CTRL3_REG         0x22
#define ST_ACC44_CTRL3_LIR         0x10

/*
 * CTRL4 Register
 *
 * CTRL4[7]   := INT1_6D
 * CTRL4[6]   := INT1_SINGLE_TAP
 * CTRL4[5]   := INT1_WU
 * CTRL4[4]   := INT1_FF
 * CTRL4[3]   := INT1_TAP
 * CTRL4[2]   := INT1_DIFF5
 * CTRL4[1]   := INT1_FTH
 * CTRL4[0]   := INT1_DRDY
 */
#define ST_ACC44_CTRL4_REG         0x23
#define ST_ACC44_CTRL4_INT1_6D     0x80
#define ST_ACC44_CTRL4_INT1_STAP   0x40
#define ST_ACC44_CTRL4_INT1_WU     0x20
#define ST_ACC44_CTRL4_INT1_FF     0x10
#define ST_ACC44_CTRL4_INT1_DTAP   0x08
#define ST_ACC44_CTRL4_INT1_DIFF5  0x04
#define ST_ACC44_CTRL4_INT1_FTH    0x02
#define ST_ACC44_CTRL4_INT1_DRDY   0x01

/*
 * CTRL5 Register
 */
#define ST_ACC44_CTRL5_REG         0x24

/*
 * CTRL6 Register
 *
 * CTRL6[5:4] := FS
 */
#define ST_ACC44_CTRL6_REG         0x25
#define ST_ACC44_CTRL6_FS_2G       0x00
#define ST_ACC44_CTRL6_FS_4G       0x10
#define ST_ACC44_CTRL6_FS_8G       0x20
#define ST_ACC44_CTRL6_FS_16G      0x30

/*
 * STATUS Register
 */
#define ST_ACC44_STATUS_REG_ADDR   0x27
#define ST_ACC44_STATUS_REG_FTH    0x80
#define ST_ACC44_STATUS_REG_DRDY   0x01

/*
 * OUTXL Register
 */
#define ST_ACC44_OUTXL_REG_ADDR    0x28

/*
 * value in m/s2 per LSB (in high-resolution mode @8g)
 * Since samples are 14-bit left aligned, the value
 * must also be right-shifted by 2.
 *
 * (9.80665 * 0.976) / (4 * 1000)
 */
#define ST_ACC44_KSCALE            0.0023928226


/* Enable auto-increment of the I2C subaddress (to allow I2C multiple ops) */
#define ST_ACC44_I2C_AUTO_INCR     0x80

#define INFO_PRINT(fmt, ...) \
    do { \
        osLog(LOG_INFO, "%s " fmt, "[ST_ACC44]", ##__VA_ARGS__); \
    } while (0);

#define DEBUG_PRINT(fmt, ...) \
    do { \
        if (ST_ACC44_DBG_ENABLED) { \
            osLog(LOG_DEBUG, "%s " fmt, "[ST_ACC44]", ##__VA_ARGS__); \
        } \
    } while (0);

#define ERROR_PRINT(fmt, ...) \
    do { \
        osLog(LOG_ERROR, "%s " fmt, "[ST_ACC44]", ##__VA_ARGS__); \
    } while (0);

/* DO NOT MODIFY, just to avoid compiler error if not defined using FLAGS */
#ifndef ST_ACC44_DBG_ENABLED
#define ST_ACC44_DBG_ENABLED                           0
#endif /* ST_ACC44_DBG_ENABLED */

enum st_acc44_SensorEvents
{
    EVT_COMM_DONE = EVT_APP_START + 1,
    EVT_SENSOR_INTERRUPT,
};

enum st_acc44_SensorState {
    SENSOR_BOOT,
    SENSOR_VERIFY_ID,
    SENSOR_INIT,
    SENSOR_IDLE,
    SENSOR_ACCEL_POWER_UP,
    SENSOR_ACCEL_POWER_DOWN,
    SENSOR_CHANGE_RATE,
    SENSOR_READ_SAMPLES,
};

#ifndef ST_ACC44_I2C_BUS_ID
#error "ST_ACC44_I2C_BUS_ID is not defined; please define in variant.h"
#endif

#ifndef ST_ACC44_I2C_SPEED
#error "ST_ACC44_I2C_SPEED is not defined; please define in variant.h"
#endif

#ifndef ST_ACC44_I2C_ADDR
#error "ST_ACC44_I2C_ADDR is not defined; please define in variant.h"
#endif

#ifndef ST_ACC44_INT_PIN
#error "ST_ACC44_INT_PIN is not defined; please define in variant.h"
#endif

#ifndef ST_ACC44_INT_IRQ
#error "ST_ACC44_INT_IRQ is not defined; please define in variant.h"
#endif

#ifndef ST_ACC44_TO_ANDROID_COORDINATE
#error "ST_ACC44_TO_ANDROID_COORDINATE is not defined; please define in variant.h"
#endif

#define RAW_TO_MS2(raw_axis) ((float)raw_axis * ST_ACC44_KSCALE)

#define ST_ACC44_MAX_PENDING_I2C_REQUESTS   10
#define ST_ACC44_MAX_I2C_TRANSFER_SIZE      6
#define ST_ACC44_MAX_ACC_EVENTS             50

struct I2cTransfer
{
    size_t tx;
    size_t rx;
    int err;
    uint8_t txrxBuf[ST_ACC44_MAX_I2C_TRANSFER_SIZE];
    bool last;
    bool inUse;
    uint32_t delay;
};

/* Task structure */
struct st_acc44_Task {
    uint32_t tid;

    struct SlabAllocator *accDataSlab;

    volatile uint8_t state; //task state, type enum st_mag40_SensorState, do NOT change this directly
    bool accOn;
    uint32_t sample_rate_ns;
    uint32_t irq_rate_ns;
    uint32_t rate;
    uint32_t latency;
    uint8_t currentODR;
    uint8_t samplesToDiscard;
    uint64_t Timestamp;
    uint64_t lastTime;

    bool pendingInt;
    bool pendingSetPower;
    bool pendingSetRate;
    uint32_t pendingRate;
    uint32_t pendingLatency;
    bool pendingPower;

    struct I2cTransfer transfers[ST_ACC44_MAX_PENDING_I2C_REQUESTS];

    /* Communication functions */
    bool (*comm_tx)(uint8_t addr, uint8_t data, uint32_t delay, bool last);
    bool (*comm_rx)(uint8_t addr, uint16_t len, uint32_t delay, bool last);

    /* irq */
    struct Gpio *Int1;
    struct ChainedIsr Isr1;
    uint32_t int_num;

    /* sensors */
    uint32_t accHandle;
};

static struct st_acc44_Task mTask;

#if DBG_STATE
#define PRI_STATE PRIi32
static int32_t getStateName(int32_t s) {
    return s;
}
#endif

// Atomic get state
#define GET_STATE() (atomicReadByte(&mTask.state))

// Atomic set state, this set the state to arbitrary value, use with caution
#define SET_STATE(s) do{\
        atomicWriteByte(&mTask.state, (s));\
    }while(0)

// Atomic switch state from IDLE to desired state.
static bool trySwitchState(enum st_acc44_SensorState newState) {
#if DBG_STATE
    bool ret = atomicCmpXchgByte(&mTask.state, SENSOR_IDLE, newState);
    uint8_t prevState = ret ? SENSOR_IDLE : GET_STATE();
    DEBUG_PRINT("switch state %" PRI_STATE "->%" PRI_STATE ", %s\n",
            getStateName(prevState), getStateName(newState), ret ? "ok" : "failed");
    return ret;
#else
    return atomicCmpXchgByte(&mTask.state, SENSOR_IDLE, newState);
#endif
}

#define DEC_INFO(name, type, axis, inter, samples, rates, raw, scale) \
    .sensorName = name, \
    .sensorType = type, \
    .numAxis = axis, \
    .interrupt = inter, \
    .minSamples = samples, \
    .supportedRates = rates, \
    .rawType = raw, \
    .rawScale = scale,

static uint32_t st_acc44_Rates[] = {
    SENSOR_HZ(25.0f/2.0f),
    SENSOR_HZ(25.0f),
    SENSOR_HZ(50.0f),
    SENSOR_HZ(100.0f),
    SENSOR_HZ(200.0f),
    SENSOR_HZ(400.0f),
    SENSOR_HZ(800.0f),
    0
};

static uint32_t st_acc44_Rates_in_ns[] = {
    80000000,         /*  12.5 Hz */
    40000000,         /*  25 Hz */
    20000000,         /*  50 Hz */
    10000000,         /* 100 Hz */
    5000000,          /* 200 Hz */
    2500000,          /* 400 Hz */
    1250000,          /* 800 Hz */
    0
};

static uint32_t st_acc44_regVal[] = {
    ST_ACC44_ODR_12_5_HZ,
    ST_ACC44_ODR_25_HZ,
    ST_ACC44_ODR_50_HZ,
    ST_ACC44_ODR_100_HZ,
    ST_ACC44_ODR_200_HZ,
    ST_ACC44_ODR_400_HZ,
    ST_ACC44_ODR_800_HZ,
    ST_ACC44_ODR_1600_HZ,
};

static uint8_t st_acc44_computeOdr(uint32_t rate)
{
    int i;

    for (i = 0; i < (ARRAY_SIZE(st_acc44_Rates) - 1); i++) {
        if (st_acc44_Rates[i] == rate)
            break;
    }
    if (i == (ARRAY_SIZE(st_acc44_Rates) -1 )) {
        ERROR_PRINT("ODR not valid! Choosed smallest ODR available\n");
        i = 0;
    }

    return i;
}

static uint32_t st_acc44_Rate_hz_to_ns(uint32_t rate)
{
    int i;

    if ((i = st_acc44_computeOdr(rate)) >= 0)
        return st_acc44_Rates_in_ns[i];

    return 0;
}

static const struct SensorInfo st_acc44_SensorInfo =
{
    DEC_INFO("Accelerometer", SENS_TYPE_ACCEL, NUM_AXIS_THREE, NANOHUB_INT_NONWAKEUP,
        600, st_acc44_Rates, SENS_TYPE_ACCEL_RAW, 1.0f / ST_ACC44_KSCALE)
};

static bool st_acc44_Power(bool on, void *cookie)
{
    bool oldMode = mTask.accOn;
    bool newMode = on;
    uint32_t state = on ? SENSOR_ACCEL_POWER_UP : SENSOR_ACCEL_POWER_DOWN;
    bool ret = true;

    INFO_PRINT("Power %s\n", on ? "on" : "off");

    if (trySwitchState(state)) {
        if (oldMode != newMode) {
            if (on) {
                ret = mTask.comm_tx(ST_ACC44_CTRL1_REG, ST_ACC44_ODR_12_5_HZ |
                                                    ST_ACC44_CTRL1_DEFVAL, 0, true);
            } else {
                ret = mTask.comm_tx(ST_ACC44_CTRL1_REG, ST_ACC44_ODR_POWER_DOWN |
                                                    ST_ACC44_CTRL1_DEFVAL, 0, true);
            }
        } else
            sensorSignalInternalEvt(mTask.accHandle,
                    SENSOR_INTERNAL_EVT_POWER_STATE_CHG, on, 0);
    } else {
        mTask.pendingSetPower = true;
        mTask.pendingPower = on;
    }

    return ret;
}

static bool st_acc44_FwUpload(void *cookie)
{
    INFO_PRINT("FwUpload\n");
    return sensorSignalInternalEvt(mTask.accHandle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
}

static bool st_acc44_SetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    uint8_t num = 0;

    INFO_PRINT("SetRate %lu Hz - %llu ns\n", rate, latency);

    if (trySwitchState(SENSOR_CHANGE_RATE)) {
        num = st_acc44_computeOdr(rate);
        mTask.currentODR = st_acc44_regVal[num];
        mTask.latency = latency;
        mTask.rate = rate;
        mTask.sample_rate_ns = st_acc44_Rate_hz_to_ns(rate);
        mTask.samplesToDiscard = 2;
        mTask.lastTime = 0;

        /* one interrupt every sample */
        mTask.irq_rate_ns = mTask.sample_rate_ns;

        mTask.comm_rx(ST_ACC44_OUTXL_REG_ADDR, 6, 0, false);
        mTask.comm_tx(ST_ACC44_CTRL4_REG, ST_ACC44_CTRL4_INT1_DRDY, 0, false);
        mTask.comm_tx(ST_ACC44_CTRL1_REG, mTask.currentODR | ST_ACC44_CTRL1_DEFVAL, 0, true);
    } else {
        mTask.pendingSetRate = true;
        mTask.pendingRate = rate;
        mTask.pendingLatency = latency;
    }

    return true;
}

static bool st_acc44_Flush(void *cookie)
{
    INFO_PRINT("Flush\n");
    return osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_ACCEL), SENSOR_DATA_EVENT_FLUSH, NULL);
}

static bool st_acc44_SelfTest(void *cookie)
{
    INFO_PRINT("SelfTest\n");
    return true;
}

#define DEC_OPS(power, firmware, rate, flush, test, cal, cfg) \
    .sensorPower = power, \
    .sensorFirmwareUpload = firmware, \
    .sensorSetRate = rate, \
    .sensorFlush = flush, \
    .sensorCalibrate = cal, \
    .sensorSelfTest = test, \
    .sensorCfgData = cfg

static const struct SensorOps st_acc44_SensorOps =
{
    DEC_OPS(st_acc44_Power, st_acc44_FwUpload, st_acc44_SetRate, st_acc44_Flush, st_acc44_SelfTest, NULL, NULL),
};

static void inline enableInterrupt(struct Gpio *pin, struct ChainedIsr *isr)
{
    gpioConfigInput(pin, GPIO_SPEED_LOW, GPIO_PULL_NONE);
    syscfgSetExtiPort(pin);
    extiEnableIntGpio(pin, EXTI_TRIGGER_RISING);
    extiChainIsr(ST_ACC44_INT_IRQ, isr);
}

static void inline disableInterrupt(struct Gpio *pin, struct ChainedIsr *isr)
{
    extiUnchainIsr(ST_ACC44_INT_IRQ, isr);
    extiDisableIntGpio(pin);
}

static void st_acc44_calc_timestamp(void)
{
    if (mTask.lastTime == 0) {
        mTask.Timestamp = sensorGetTime();
    } else {
        uint64_t currTime = sensorGetTime();
        uint64_t deltaTime = currTime - mTask.lastTime;

        deltaTime = (deltaTime + 7*mTask.irq_rate_ns)/8;
        mTask.Timestamp = mTask.lastTime + deltaTime;
    }
    mTask.lastTime = mTask.Timestamp;
}

static bool st_acc44_int1_isr(struct ChainedIsr *isr)
{
    if (!extiIsPendingGpio(mTask.Int1))
        return false;

    /* Start sampling for a value */
    if (!osEnqueuePrivateEvt(EVT_SENSOR_INTERRUPT, NULL, NULL, mTask.tid))
        ERROR_PRINT("st_acc44_int1_isr: osEnqueuePrivateEvt() failed\n");

    mTask.int_num++;
    extiClearPendingGpio(mTask.Int1);
    return true;
}

static void int2Evt(void)
{
    if (trySwitchState(SENSOR_READ_SAMPLES)) {
        mTask.comm_rx(ST_ACC44_OUTXL_REG_ADDR, 6, 0, true);
    } else {
        mTask.pendingInt = true;
    }
}

static void processPendingEvt(void)
{
    if (mTask.pendingInt) {
        mTask.pendingInt = false;
        int2Evt();
        return;
    }

    if (mTask.pendingSetPower) {
        mTask.pendingSetPower = false;
        st_acc44_Power(mTask.pendingPower, NULL);
    }

    if (mTask.pendingSetRate) {
        mTask.pendingSetRate = false;
        st_acc44_SetRate(mTask.pendingRate, mTask.pendingLatency, NULL);
    }
}

static bool accAllocateEvt(struct TripleAxisDataEvent **evPtr)
{
    struct TripleAxisDataEvent *ev;

    ev = *evPtr = slabAllocatorAlloc(mTask.accDataSlab);
    if (!ev) {
        ERROR_PRINT("Failed to allocate acc event memory");
        return false;
    }

    memset(&ev->samples[0].firstSample, 0x00, sizeof(struct SensorFirstSample));
    return true;
}

static void accFreeEvt(void *ptr)
{
    slabAllocatorFree(mTask.accDataSlab, ptr);
}

// Allocate a buffer and mark it as in use with the given state, or return NULL
// if no buffers available. Must *not* be called from interrupt context.
static struct I2cTransfer *allocXfer(void)
{
    size_t i;

    for (i = 0; i < ARRAY_SIZE(mTask.transfers); i++) {
        if (!mTask.transfers[i].inUse) {
            mTask.transfers[i].inUse = true;
            return &mTask.transfers[i];
        }
    }

    ERROR_PRINT("Ran out of i2c buffers!");
    return NULL;
}

static inline void releaseXfer(struct I2cTransfer *xfer)
{
    xfer->inUse = false;
}

static void st_acc44_i2cCallback(void *cookie, size_t tx, size_t rx, int err)
{
    struct I2cTransfer *xfer = cookie;

    /* Do not run callback if not the last one in a set of i2c transfers */
    if (xfer && !xfer->last) {
        releaseXfer(xfer);
        return;
    }

    xfer->tx = tx;
    xfer->rx = rx;
    xfer->err = err;

    osEnqueuePrivateEvt(EVT_COMM_DONE, cookie, NULL, mTask.tid);
    if (err != 0)
        ERROR_PRINT("i2c error (tx: %d, rx: %d, err: %d)\n", tx, rx, err);
}

static bool st_acc44_i2c_read(uint8_t addr, uint16_t len, uint32_t delay, bool last)
{
    struct I2cTransfer *xfer = allocXfer();
    int ret = -1;

    if (xfer != NULL) {
        xfer->delay = delay;
        xfer->last = last;
        xfer->txrxBuf[0] = ST_ACC44_I2C_AUTO_INCR | addr;
        if ((ret = i2cMasterTxRx(ST_ACC44_I2C_BUS_ID, ST_ACC44_I2C_ADDR, xfer->txrxBuf, 1, xfer->txrxBuf, len, st_acc44_i2cCallback, xfer)) < 0) {
            releaseXfer(xfer);
            DEBUG_PRINT("st_acc44_i2c_read: i2cMasterTxRx operation failed (ret: %d)\n", ret);
            return false;
        }
    }

    return (ret == -1) ? false : true;
}

static bool st_acc44_i2c_write(uint8_t addr, uint8_t data, uint32_t delay, bool last)
{
    struct I2cTransfer *xfer = allocXfer();
    int ret = -1;

    if (xfer != NULL) {
        xfer->delay = delay;
        xfer->last = last;
        xfer->txrxBuf[0] = addr;
        xfer->txrxBuf[1] = data;
        if ((ret = i2cMasterTx(ST_ACC44_I2C_BUS_ID, ST_ACC44_I2C_ADDR, xfer->txrxBuf, 2, st_acc44_i2cCallback, xfer)) < 0) {
            releaseXfer(xfer);
            DEBUG_PRINT("st_acc44_i2c_write: i2cMasterTx operation failed (ret: %d)\n", ret);
            return false;
        }
    }

    return (ret == -1) ? false : true;
}

static void parseRawData(uint8_t *raw, uint8_t num_of_smpl, uint64_t sensor_time)
{
    uint8_t i;
    struct TripleAxisDataEvent *accSample;
    float x, y, z;
    int32_t raw_x;
    int32_t raw_y;
    int32_t raw_z;

    /* Discard samples generated during sensor turn-on time */
    if (mTask.samplesToDiscard > 0) {
        if (num_of_smpl > mTask.samplesToDiscard) {
           num_of_smpl -= mTask.samplesToDiscard;
           mTask.samplesToDiscard = 0;
        } else{
            mTask.samplesToDiscard -= num_of_smpl;
            return;
        }
    }

    if (accAllocateEvt(&accSample) == false)
        return;

    accSample->referenceTime = sensor_time;

    accSample->samples[0].deltaTime = 0;
    accSample->samples[0].firstSample.numSamples = num_of_smpl;

    for (i = 0; i < num_of_smpl; i++) {
        raw_x = (*(int16_t *)&raw[6*i + 0]);
        raw_y = (*(int16_t *)&raw[6*i + 2]);
        raw_z = (*(int16_t *)&raw[6*i + 4]);

        /* convert raw data in m/s2 */
        x = RAW_TO_MS2(raw_x);
        y = RAW_TO_MS2(raw_y);
        z = RAW_TO_MS2(raw_z);

        /* rotate axis */
        ST_ACC44_TO_ANDROID_COORDINATE(x, y, z);

        accSample->samples[i].x = x;
        accSample->samples[i].y = y;
        accSample->samples[i].z = z;

        if (i > 0)
            accSample->samples[i].deltaTime = mTask.sample_rate_ns;
    }

    osEnqueueEvtOrFree(sensorGetMyEventType(SENS_TYPE_ACCEL), accSample, accFreeEvt);
}

static int st_acc44_handleCommDoneEvt(const void* evtData)
{
    bool returnIdle = false;
    struct I2cTransfer *xfer = (struct I2cTransfer *)evtData;

    switch (GET_STATE()) {
    case SENSOR_BOOT:
        SET_STATE(SENSOR_VERIFY_ID);
        if (!mTask.comm_rx(ST_ACC44_WAI_REG_ADDR, 1, 0, true)) {
            DEBUG_PRINT("Not able to read WAI\n");
            return -1;
        }
        break;

    case SENSOR_VERIFY_ID:
        /* Check the sensor ID */
        if (xfer->err != 0 || xfer->txrxBuf[0] != ST_ACC44_WAI_REG_VAL) {
            DEBUG_PRINT("WAI returned is: %02x\n", xfer->txrxBuf[0]);
            break;
        }

        INFO_PRINT("Device ID is correct! (%02x)\n", xfer->txrxBuf[0]);

        SET_STATE(SENSOR_INIT);
        mTask.comm_tx(ST_ACC44_CTRL1_REG, ST_ACC44_ODR_POWER_DOWN | ST_ACC44_CTRL1_DEFVAL, 0, false);
        mTask.comm_tx(ST_ACC44_CTRL2_REG, ST_ACC44_CTRL2_DEFVAL, 0, false);
        mTask.comm_tx(ST_ACC44_CTRL3_REG, ST_ACC44_CTRL3_LIR, 0, false);
        mTask.comm_tx(ST_ACC44_CTRL6_REG, ST_ACC44_CTRL6_FS_8G, 0, false);
        mTask.comm_tx(ST_ACC44_CTRL4_REG, 0, 0, true);
        break;

    case SENSOR_INIT:
        DEBUG_PRINT("SENSOR INIT\n");
        returnIdle = true;
        sensorRegisterInitComplete(mTask.accHandle);
        break;

    case SENSOR_ACCEL_POWER_UP:
        DEBUG_PRINT("POWER UP\n");
        returnIdle = true;
        mTask.accOn = true;
        sensorSignalInternalEvt(mTask.accHandle,
                    SENSOR_INTERNAL_EVT_POWER_STATE_CHG, true, 0);
        break;

    case SENSOR_ACCEL_POWER_DOWN:
        DEBUG_PRINT("POWER DWN\n");
        returnIdle = true;
        mTask.accOn = false;
        sensorSignalInternalEvt(mTask.accHandle,
                    SENSOR_INTERNAL_EVT_POWER_STATE_CHG, false, 0);
        break;

    case SENSOR_CHANGE_RATE:
        DEBUG_PRINT("CHANGE RATE\n");
        returnIdle = true;
        DEBUG_PRINT("int_num %ld\n", mTask.int_num);
        mTask.int_num = 0;
        sensorSignalInternalEvt(mTask.accHandle,
                SENSOR_INTERNAL_EVT_RATE_CHG, mTask.rate, mTask.latency);
        break;

    case SENSOR_READ_SAMPLES:
        returnIdle = true;

        parseRawData(&xfer->txrxBuf[0], 1, mTask.Timestamp);
        break;

    case SENSOR_IDLE:
    default:
        break;
    }

    releaseXfer(xfer);

    if (returnIdle) {
        SET_STATE(SENSOR_IDLE);
        processPendingEvt();
    }

    return (0);
}

static void st_acc44_handleEvent(uint32_t evtType, const void* evtData)
{
    switch (evtType) {
    case EVT_APP_START:
        INFO_PRINT("EVT_APP_START\n");
        osEventUnsubscribe(mTask.tid, EVT_APP_START);

        SET_STATE(SENSOR_BOOT);
        mTask.comm_tx(ST_ACC44_CTRL2_REG, ST_ACC44_CTRL2_SW_RST, 0, true);
        break;

    case EVT_COMM_DONE:
        st_acc44_handleCommDoneEvt(evtData);
        break;

    case EVT_SENSOR_INTERRUPT:
        st_acc44_calc_timestamp();
        int2Evt();
        break;

    default:
        break;
    }
}

static bool st_acc44_startTask(uint32_t task_id)
{
    size_t slabSize;

    mTask.tid = task_id;

    INFO_PRINT("start driver\n");

    mTask.accOn = false;
    mTask.pendingInt = false;
    mTask.pendingSetPower = false;
    mTask.pendingSetRate = false;

    mTask.currentODR = ST_ACC44_ODR_POWER_DOWN;

    slabSize = sizeof(struct TripleAxisDataEvent) + sizeof(struct TripleAxisDataPoint);

    mTask.accDataSlab = slabAllocatorNew(slabSize, 4, ST_ACC44_MAX_ACC_EVENTS);
    if (!mTask.accDataSlab) {
        ERROR_PRINT("Failed to allocate accDataSlab memory\n");
        return false;
    }

    /* Init the communication part */
    i2cMasterRequest(ST_ACC44_I2C_BUS_ID, ST_ACC44_I2C_SPEED);

    mTask.comm_tx = st_acc44_i2c_write;
    mTask.comm_rx = st_acc44_i2c_read;

    /* irq */
    mTask.int_num = 0;
    mTask.Int1 = gpioRequest(ST_ACC44_INT_PIN);
    gpioConfigInput(mTask.Int1, GPIO_SPEED_LOW, GPIO_PULL_NONE);
    mTask.Isr1.func = st_acc44_int1_isr;
    enableInterrupt(mTask.Int1, &mTask.Isr1);

    mTask.accHandle = sensorRegister(&st_acc44_SensorInfo, &st_acc44_SensorOps, NULL, false);

    osEventSubscribe(mTask.tid, EVT_APP_START);

    return true;
}

static void st_acc44_endTask(void)
{
    INFO_PRINT("ended\n");
    slabAllocatorDestroy(mTask.accDataSlab);
    disableInterrupt(mTask.Int1, &mTask.Isr1);
}

INTERNAL_APP_INIT(ST_ACC44_APP_ID, 0, st_acc44_startTask, st_acc44_endTask, st_acc44_handleEvent);
