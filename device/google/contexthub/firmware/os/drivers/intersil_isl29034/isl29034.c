/*
 * Copyright (C) 2017 STMicroelectronics
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

#include <stdlib.h>
#include <string.h>
#include <float.h>

#include <seos.h>
#include <i2c.h>
#include <timer.h>
#include <sensors.h>
#include <heap.h>
#include <hostIntf.h>
#include <nanohubPacket.h>
#include <eventnums.h>
#include <util.h>
#include <variant/variant.h>

#ifdef DEBUG
#undef ISL29034_DBG_ENABLED
#define ISL29034_DBG_ENABLED                    1
#endif

#ifndef ISL29034_DBG_ENABLED
#define ISL29034_DBG_ENABLED                    0
#endif /* ISL29034_DBG_ENABLED */

#define ISL29034_APP_VERSION                    1

#define ISL29034_REG_CMD_1                      0x00
#define ISL29034_REG_CMD_2                      0x01
#define ISL29034_REG_DATA_L                     0x02
#define ISL29034_REG_DATA_H                     0x03
#define ISL29034_REG_ID                         0x0f

#define ISL29034_CMD1_POWERDOWN                 (0 << 5)
#define ISL29034_CMD1_MEASURE                   (5 << 5)

#define ISL29034_CMD2_RANGE_1KLUX               0
#define ISL29034_CMD2_RANGE_4KLUX               1
#define ISL29034_CMD2_RANGE_16KLUX              2
#define ISL29034_CMD2_RANGE_64KLUX              3
#define ISL29034_CMD2_RES_16BIT                 (0 << 2)
#define ISL29034_CMD2_RES_12BIT                 (1 << 2)
#define ISL29034_CMD2_RES_8BIT                  (2 << 2)
#define ISL29034_CMD2_RES_4BIT                  (3 << 2)

#define ISL29034_ID                             (5 << 3)
#define ISL29034_ID_MASK                        (7 << 3)
#define ISL29034_ID_BOUT                        (1 << 7)

#define ISL29034_ALS_INVALID                    UINT32_MAX
#define ISL29034_MAX_PENDING_I2C_REQUESTS       4
#define ISL29034_MAX_I2C_TRANSFER_SIZE          3

/* Used when SENSOR_RATE_ONCHANGE is requested */
#define ISL29034_DEFAULT_RATE                   SENSOR_HZ(10)

#ifndef ISL29034_I2C_BUS_ID
#error "ISL29034_I2C_BUS_ID is not defined; please define in variant.h"
#endif

#ifndef ISL29034_I2C_SPEED
#error "ISL29034_I2C_SPEED is not defined; please define in variant.h"
#endif

#ifndef ISL29034_I2C_ADDR
#define ISL29034_I2C_ADDR                       0x44
#endif

#define INFO_PRINT(fmt, ...) \
    do { \
        osLog(LOG_INFO, "%s " fmt, "[ISL29034]", ##__VA_ARGS__); \
    } while (0);

#define DEBUG_PRINT(fmt, ...) \
    do { \
        if (ISL29034_DBG_ENABLED) { \
            osLog(LOG_DEBUG, "%s " fmt, "[ISL29034]", ##__VA_ARGS__); \
        } \
    } while (0);

#define ERROR_PRINT(fmt, ...) \
    do { \
        osLog(LOG_ERROR, "%s " fmt, "[ISL29034]", ##__VA_ARGS__); \
    } while (0);


/* Private driver events */
enum SensorEvents
{
    EVT_SENSOR_I2C = EVT_APP_START + 1,
    EVT_SENSOR_ALS_TIMER,
};

/* I2C state machine */
enum SensorState
{
    SENSOR_STATE_VERIFY_ID,
    SENSOR_STATE_CLEAR_BOUT,

    SENSOR_STATE_ENABLING_ALS,
    SENSOR_STATE_DISABLING_ALS,

    SENSOR_STATE_IDLE,
    SENSOR_STATE_SAMPLING,
};

struct I2cTransfer
{
    size_t tx;
    size_t rx;
    int err;

    uint8_t txrxBuf[ISL29034_MAX_I2C_TRANSFER_SIZE];

    uint8_t state;
    bool inUse;
};

struct SensorData
{
    uint32_t tid;

    uint32_t alsHandle;
    uint32_t alsTimerHandle;

    struct I2cTransfer transfers[ISL29034_MAX_PENDING_I2C_REQUESTS];

    union EmbeddedDataPoint lastAlsSample;

    bool alsOn;
    bool alsReading;
};

static struct SensorData mData;

static const uint32_t supportedRates[] =
{
    SENSOR_HZ(10),
    SENSOR_RATE_ONCHANGE,
    0
};

// should match "supported rates in length" and be the timer length for that rate in nanosecs
static const uint64_t rateTimerVals[] =
{
    1000000000ULL / 10,
};

/*
 * Helper functions
 */

static void i2cCallback(void *cookie, size_t tx, size_t rx, int err)
{
    struct I2cTransfer *xfer = cookie;

    xfer->tx = tx;
    xfer->rx = rx;
    xfer->err = err;

    osEnqueuePrivateEvt(EVT_SENSOR_I2C, cookie, NULL, mData.tid);
    if (err != 0)
        INFO_PRINT("i2c error (tx: %d, rx: %d, err: %d)\n", tx, rx, err);
}

// Allocate a buffer and mark it as in use with the given state, or return NULL
// if no buffers available. Must *not* be called from interrupt context.
static struct I2cTransfer *allocXfer(uint8_t state)
{
    size_t i;

    for (i = 0; i < ARRAY_SIZE(mData.transfers); i++) {
        if (!mData.transfers[i].inUse) {
            mData.transfers[i].inUse = true;
            mData.transfers[i].state = state;
            return &mData.transfers[i];
        }
    }

    ERROR_PRINT("Ran out of i2c buffers!");
    return NULL;
}

// Helper function to write a one byte register. Returns true if we got a
// successful return value from i2cMasterTx().
static bool writeRegister(uint8_t reg, uint8_t value, uint8_t state)
{
    struct I2cTransfer *xfer = allocXfer(state);
    int ret = -1;

    if (xfer != NULL) {
        xfer->txrxBuf[0] = reg;
        xfer->txrxBuf[1] = value;
        ret = i2cMasterTx(ISL29034_I2C_BUS_ID, ISL29034_I2C_ADDR, xfer->txrxBuf, 2, i2cCallback, xfer);
    }

    return (ret == 0);
}

static void alsTimerCallback(uint32_t timerId, void *cookie)
{
    osEnqueuePrivateEvt(EVT_SENSOR_ALS_TIMER, cookie, NULL, mData.tid);
}

static inline float getLuxFromAlsData(uint16_t als)
{
    float range = 64000.0;
    float resolution = 65536.0;

    return range * als / resolution;
}

static bool sensorPowerAls(bool on, void *cookie)
{
    INFO_PRINT("sensorPowerAls: %d\n", on);

    if (mData.alsTimerHandle) {
        timTimerCancel(mData.alsTimerHandle);
        mData.alsTimerHandle = 0;
        mData.alsReading = false;
    }

    mData.lastAlsSample.idata = ISL29034_ALS_INVALID;
    mData.alsOn = on;

    writeRegister(ISL29034_REG_CMD_1,
                  on ? ISL29034_CMD1_MEASURE : ISL29034_CMD1_POWERDOWN,
                  on ? SENSOR_STATE_ENABLING_ALS : SENSOR_STATE_DISABLING_ALS);

    return true;
}

static bool sensorFirmwareAls(void *cookie)
{
    sensorSignalInternalEvt(mData.alsHandle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
    return true;
}

static bool sensorRateAls(uint32_t rate, uint64_t latency, void *cookie)
{
    if (rate == SENSOR_RATE_ONCHANGE)
        rate = ISL29034_DEFAULT_RATE;

    INFO_PRINT("sensorRateAls: %ld/%lld\n", rate, latency);

    if (mData.alsTimerHandle)
        timTimerCancel(mData.alsTimerHandle);
    mData.alsTimerHandle = timTimerSet(sensorTimerLookupCommon(supportedRates, rateTimerVals, rate), 0, 50, alsTimerCallback, NULL, false);
    osEnqueuePrivateEvt(EVT_SENSOR_ALS_TIMER, NULL, NULL, mData.tid);
    sensorSignalInternalEvt(mData.alsHandle, SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);

    return true;
}

static bool sensorFlushAls(void *cookie)
{
    return osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_ALS), SENSOR_DATA_EVENT_FLUSH, NULL);
}

static bool sendLastSampleAls(void *cookie, uint32_t tid)
{
    bool result = true;

    // If we don't end up doing anything here, the expectation is that we are powering up/haven't got the
    // first sample yet, so a broadcast event will go out soon with the first sample
    if (mData.lastAlsSample.idata != ISL29034_ALS_INVALID)
        result = osEnqueuePrivateEvt(sensorGetMyEventType(SENS_TYPE_ALS), mData.lastAlsSample.vptr, NULL, tid);

    return result;
}

static const struct SensorInfo sensorInfoAls =
{
    .sensorName = "ALS",
    .supportedRates = supportedRates,
    .sensorType = SENS_TYPE_ALS,
    .numAxis = NUM_AXIS_EMBEDDED,
    .interrupt = NANOHUB_INT_NONWAKEUP,
    .minSamples = 20
};

static const struct SensorOps sensorOpsAls =
{
    .sensorPower = sensorPowerAls,
    .sensorFirmwareUpload = sensorFirmwareAls,
    .sensorSetRate = sensorRateAls,
    .sensorFlush = sensorFlushAls,
    .sensorTriggerOndemand = NULL,
    .sensorCalibrate = NULL,
    .sensorSendOneDirectEvt = sendLastSampleAls
};

/*
 * Sensor i2c state machine
 */

static void handle_i2c_event(struct I2cTransfer *xfer)
{
    union EmbeddedDataPoint sample;
    struct I2cTransfer *nextXfer;

    switch (xfer->state) {
    case SENSOR_STATE_VERIFY_ID:
        /* Check the sensor ID */
        if (xfer->err != 0 ||
            (xfer->txrxBuf[0] & ISL29034_ID_MASK) != ISL29034_ID) {
            INFO_PRINT("not detected\n");
            sensorUnregister(mData.alsHandle);
            break;
        }
        INFO_PRINT("detected\n")

        if (xfer->txrxBuf[0] & ISL29034_ID_BOUT) {
            INFO_PRINT("Brownout Condition\n");
            writeRegister(ISL29034_REG_ID,
                          xfer->txrxBuf[0] ^ ISL29034_ID_BOUT,
                          SENSOR_STATE_CLEAR_BOUT);
            break;
        }
        /* fallthrough */

    case SENSOR_STATE_CLEAR_BOUT:
        nextXfer = allocXfer(SENSOR_STATE_IDLE);
        if (nextXfer != NULL) {
            nextXfer->txrxBuf[0] = ISL29034_REG_CMD_1;
            nextXfer->txrxBuf[1] = ISL29034_CMD1_POWERDOWN;
            nextXfer->txrxBuf[2] = ISL29034_CMD2_RANGE_64KLUX | ISL29034_CMD2_RES_16BIT;
            i2cMasterTx(ISL29034_I2C_BUS_ID, ISL29034_I2C_ADDR, nextXfer->txrxBuf, 3, i2cCallback, nextXfer);
        }
        break;

    case SENSOR_STATE_IDLE:
        sensorRegisterInitComplete(mData.alsHandle);
        break;

    case SENSOR_STATE_ENABLING_ALS:
        sensorSignalInternalEvt(mData.alsHandle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, true, 0);
        break;

    case SENSOR_STATE_DISABLING_ALS:
        sensorSignalInternalEvt(mData.alsHandle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, false, 0);
        break;

    case SENSOR_STATE_SAMPLING:
        DEBUG_PRINT("sample ready: als0=%u als1=%u\n", xfer->txrxBuf[0], xfer->txrxBuf[1]);

        if (mData.alsOn && mData.alsReading) {
            /* Create event */
            sample.fdata = getLuxFromAlsData(xfer->txrxBuf[0] | (xfer->txrxBuf[1] << 8));
            if (mData.lastAlsSample.idata != sample.idata) {
                osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_ALS), sample.vptr, NULL);
                mData.lastAlsSample.fdata = sample.fdata;
            }
        }

        mData.alsReading = false;
        break;
    }

    xfer->inUse = false;
}

/*
 * Main driver entry points
 */

static bool init_app(uint32_t myTid)
{
    INFO_PRINT("started\n");

    /* Set up driver private data */
    mData.tid = myTid;
    mData.alsOn = false;
    mData.alsReading = false;
    mData.lastAlsSample.idata = ISL29034_ALS_INVALID;

    i2cMasterRequest(ISL29034_I2C_BUS_ID, ISL29034_I2C_SPEED);

    /* Register sensors */
    mData.alsHandle = sensorRegister(&sensorInfoAls, &sensorOpsAls, NULL, false);

    osEventSubscribe(myTid, EVT_APP_START);

    return true;
}

static void end_app(void)
{
    INFO_PRINT("ended\n");

    sensorUnregister(mData.alsHandle);

    i2cMasterRelease(ISL29034_I2C_BUS_ID);
}

static void handle_event(uint32_t evtType, const void* evtData)
{
    struct I2cTransfer *xfer;

    switch (evtType) {
    case EVT_APP_START:
        osEventUnsubscribe(mData.tid, EVT_APP_START);

        xfer = allocXfer(SENSOR_STATE_VERIFY_ID);
        if (xfer != NULL) {
            xfer->txrxBuf[0] = ISL29034_REG_ID;
            i2cMasterTxRx(ISL29034_I2C_BUS_ID, ISL29034_I2C_ADDR, xfer->txrxBuf, 1, xfer->txrxBuf, 1, i2cCallback, xfer);
        }
        break;

    case EVT_SENSOR_I2C:
        handle_i2c_event((struct I2cTransfer *)evtData);
        break;

    case EVT_SENSOR_ALS_TIMER:
        /* Start sampling for a value */
        if (!mData.alsReading) {
            xfer = allocXfer(SENSOR_STATE_SAMPLING);
            if (xfer != NULL) {
                xfer->txrxBuf[0] = ISL29034_REG_DATA_L;
                i2cMasterTxRx(ISL29034_I2C_BUS_ID, ISL29034_I2C_ADDR, xfer->txrxBuf, 1, xfer->txrxBuf, 2, i2cCallback, xfer);
            }
        }

        mData.alsReading = true;
        break;
    }
}

INTERNAL_APP_INIT(APP_ID_MAKE(NANOHUB_VENDOR_STMICRO, 4), ISL29034_APP_VERSION, init_app, end_app, handle_event);
