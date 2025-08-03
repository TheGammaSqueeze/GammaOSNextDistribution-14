/*
 * Copyright (C) 2018 InvenSense Inc.
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

#include <algos/time_sync.h>
#include <stdlib.h>
#include <string.h>
#include <timer.h>
#include <sensors.h>
#include <heap.h>
#include <spi.h>
#include <slab.h>
#include <limits.h>
#include <atomic.h>
#include <plat/rtc.h>
#include <plat/gpio.h>
#include <plat/exti.h>
#include <plat/syscfg.h>
#include <seos.h>
#include <gpio.h>
#include <isr.h>
#include <halIntf.h>
#include <hostIntf.h>
#include <nanohubPacket.h>
#include <cpu/cpuMath.h>
#include <nanohub_math.h>
#include <variant/sensType.h>
#include <variant/variant.h>
#include <common/math/macros.h>

#ifdef ACCEL_CAL_ENABLED
#include <calibration/accelerometer/accel_cal.h>
#endif
#ifdef GYRO_CAL_ENABLED
#include <calibration/gyroscope/gyro_cal.h>
#endif

#define INFO_PRINT(fmt, ...) do { \
        osLog(LOG_INFO, "%s " fmt, "[ICM40600]", ##__VA_ARGS__); \
    } while (0);

#define ERROR_PRINT(fmt, ...) do { \
        osLog(LOG_ERROR, "%s " fmt, "[ICM40600] ERROR:", ##__VA_ARGS__); \
    } while (0);

#define DEBUG_PRINT(fmt, ...) do { \
        if (DBG_ENABLE) {  \
            INFO_PRINT(fmt,  ##__VA_ARGS__); \
        } \
    } while (0);

#define DEBUG_PRINT_IF(cond, fmt, ...) do { \
        if ((cond) && DBG_ENABLE) {  \
            INFO_PRINT(fmt,  ##__VA_ARGS__); \
        } \
    } while (0);

#define DBG_ENABLE                  0
#define DBG_STATE                   0
#define DBG_TIMESTAMP               0

#define ICM40600_APP_ID             APP_ID_MAKE(NANOHUB_VENDOR_INVENSENSE, 0x2)
#define ICM40600_APP_VERSION        1

#define ICM40600_SPI_WRITE          0x00
#define ICM40600_SPI_READ           0x80

// SPI speeds officialy supported: 5MHz (low speed), 17MHz (high speed)
#define ICM40600_SPI_LOW_SPEED_HZ       5000000
#define ICM40600_SPI_HIGH_SPEED_HZ      17000000
#define ICM40600_SPI_DEFAULT_SPEED_HZ   ICM40600_SPI_LOW_SPEED_HZ
#define ICM40600_SPI_MODE               3

#ifndef ICM40600_SPI_BUS_ID
#define ICM40600_SPI_BUS_ID         1
#endif
#ifndef ICM40600_INT1_PIN
#define ICM40600_INT1_PIN           GPIO_PB(6)
#endif
#ifndef ICM40600_INT1_IRQ
#define ICM40600_INT1_IRQ           EXTI9_5_IRQn
#endif
#define ICM40600_SPI_SPEED_HZ       ICM40600_SPI_HIGH_SPEED_HZ

// set SPI speed register value
#if ICM40600_SPI_SPEED_HZ == ICM40600_SPI_LOW_SPEED_HZ
#define ICM40600_SPI_SPEED_REG_VALUE    BIT_SPI_SPEED_5MHZ
#elif ICM40600_SPI_SPEED_HZ == ICM40600_SPI_HIGH_SPEED_HZ
#define ICM40600_SPI_SPEED_REG_VALUE    BIT_SPI_SPEED_17MHZ
#else
#error "Invalid ICM40600_SPI_SPEED_HZ setting: valid values are 5MHz or 17MHz"
#endif

/*
 **********************
 * Chip configuration
 **********************
 */
/* Full-scale range */
// Default accel range is 8g
#ifndef ICM40600_ACC_RANGE_G
#define ICM40600_ACC_RANGE_G        8
#endif
// Default gyro range is 2000dps
#ifndef ICM40600_GYR_RANGE_DPS
#define ICM40600_GYR_RANGE_DPS      2000
#endif
// 0 -> +-16g, 1 -> +-8g, 2 -> +-4g, 3 -> +-2g, 4 -> +-1g
#if ICM40600_ACC_RANGE_G == 16
#define ICM40600_ACC_FS     0
#elif ICM40600_ACC_RANGE_G == 8
#define ICM40600_ACC_FS     1
#elif ICM40600_ACC_RANGE_G == 4
#define ICM40600_ACC_FS     2
#else
#error "Invalid ICM40600_ACC_RANGE_G setting: valid values are 16, 8, 4 (no HiFi)"
#endif
// 0 -> +-2000dps, 1 -> +-1000dps, 2 -> +-500dps, 3 -> +-250dps, 4 -> +-125dps, 5 -> +-62.5dps, 6 -> +-31.25dps, 7 -> +-15.6225dps
#if ICM40600_GYR_RANGE_DPS == 2000
#define ICM40600_GYR_FS     0
#elif ICM40600_GYR_RANGE_DPS == 1000
#define ICM40600_GYR_FS     1
#else
#error "Invalid ICM40600_GYR_RANGE_DPS setting: valid values are 2000, 1000"
#endif
// Bandwidth for low-pass filters, using ODR/2 FIR
#define ICM40600_ACC_BW_IND     BIT_ACCEL_UI_BW_2_FIR
#define ICM40600_GYR_BW_IND     BIT_GYRO_UI_BW_2_FIR

/* INT1 configuration */
// Polarity: 0 -> Active Low, 1 -> Active High
#define INT1_POLARITY           1
// Drive circuit: 0 -> Open Drain, 1 -> Push-Pull, fixed for INT1 do not change!
#define INT1_DRIVE_CIRCUIT      1
// Mode: 0 -> Pulse, 1 -> Latch
#define INT1_MODE               0

/* Wake-on-Motion/No-Motion */
#define ICM40600_WOM_THRESHOLD_MG       160     // 160mg @ 25Hz
#define ICM40600_WOM_COMPUTE(val_mg)    ((256 * val_mg) / 1000)
// No-Motion duration: 5s
#define ICM40600_NOM_DURATION_NS        (5 * 1000000000ULL)

/*
 **********************
 * Factory calibration
 **********************
 */
#define CALIBRATION_ODR                 7       // 200Hz (support both LPM and LNM)
#define CALIBRATION_ACC_FS              0       // +-16g (= resolution of offset register)
#define CALIBRATION_GYR_FS              1       // +-1000dps (= resolution of offset register)
#define CALIBRATION_ACC_1G              2048    // LSB/g @+-16g
#define CALIBRATION_ACC_BW_IND          BIT_ACCEL_UI_BW_4_IIR
#define CALIBRATION_GYR_BW_IND          BIT_GYRO_UI_BW_4_IIR

#define CALIBRATION_READ_INTERVAL_US    (200 * 1000)    // 200ms (200/5ms=40 packets -> 40 * 16 = 640 bytes)
#define CALIBRATION_SAMPLE_NB           200             // 200/40packets = 5 loops
#define RETRY_CNT_CALIBRATION           10              // > 5 loops

/*
 **********************
 * Selftest
 **********************
 */
#define SELF_TEST_ODR                   6   // 1000Hz
#define SELF_TEST_ACC_FS                3   // +-2g
#define SELF_TEST_GYR_FS                3   // +-250dps
#define SELF_TEST_ACC_BW_IND            BIT_ACCEL_UI_BW_10_IIR
#define SELF_TEST_GYR_BW_IND            BIT_GYRO_UI_BW_10_IIR
#define SELF_TEST_MIN_ACC_MG            225 // mg
#define SELF_TEST_MAX_ACC_MG            675 // mg
#define SELF_TEST_MIN_GYR_DPS           60  // dps
#define SELF_TEST_MAX_GYR_OFF_DPS       20  // dps
#define SELF_TEST_ACC_SHIFT_DELTA       500 // = 0.5
#define SELF_TEST_GYR_SHIFT_DELTA       500 // = 0.5

#define SELF_TEST_ACC_SCALE             32768 / (16 >> SELF_TEST_ACC_FS) / 1000
#define SELF_TEST_GYR_SCALE             32768 / (2000 >> SELF_TEST_GYR_FS)

#define SELF_TEST_READ_INTERVAL_US      (20 * 1000) // 20ms (20/1ms=20 packets -> 20 * 16 = 320 bytes)
#define SELF_TEST_SAMPLE_NB             200 // 200/20packets = 10 loops
#define RETRY_CNT_SELF_TEST             20  // > 10 loops
#define SELF_TEST_PRECISION             1000
#define SELF_TEST_SETTLE_TIME_MS        25

#define SELF_TEST_MIN_ACC               (SELF_TEST_MIN_ACC_MG * SELF_TEST_ACC_SCALE * SELF_TEST_PRECISION)
#define SELF_TEST_MAX_ACC               (SELF_TEST_MAX_ACC_MG * SELF_TEST_ACC_SCALE * SELF_TEST_PRECISION)
#define SELF_TEST_MIN_GYR               (SELF_TEST_MIN_GYR_DPS * SELF_TEST_GYR_SCALE * SELF_TEST_PRECISION)
#define SELF_TEST_MAX_GYR               (SELF_TEST_MAX_GYR_DPS * SELF_TEST_GYR_SCALE * SELF_TEST_PRECISION)
#define SELF_TEST_MAX_GYR_OFFSET        (SELF_TEST_MAX_GYR_OFF_DPS * SELF_TEST_GYR_SCALE * SELF_TEST_PRECISION)

/*
 ****************
 * register map *
 ****************
 */
/* Bank 0 */
#define REG_DEVICE_CONFIG           0x11
#define REG_SPI_SPEED               0x13
#define REG_INT_CONFIG              0x14
#define REG_FIFO_CONFIG             0x16
#define REG_INT_STATUS              0x2D
#define REG_FIFO_BYTE_COUNT1        0x2E
#define REG_FIFO_BYTE_COUNT2        0x2F
#define REG_FIFO_DATA               0x30
#define REG_SIGNAL_PATH_RESET       0x4B
#define REG_INTF_CONFIG0            0x4C
#define REG_PWR_MGMT_0              0x4E
#define REG_GYRO_CONFIG0            0x4F
#define REG_ACCEL_CONFIG0           0x50
#define REG_GYRO_CONFIG1            0x51
#define REG_GYRO_ACCEL_CONFIG0      0x52
#define REG_ACCEL_CONFIG1           0x53
#define REG_ACCEL_WOM_X_THR         0x54
#define REG_ACCEL_WOM_Y_THR         0x55
#define REG_ACCEL_WOM_Z_THR         0x56
#define REG_SMD_CONFIG              0x57
#define REG_INT_STATUS2             0x59
#define REG_TMST_CONFIG             0x5A
#define REG_FIFO_CONFIG1            0x5F
#define REG_FIFO_CONFIG2            0x60
#define REG_FIFO_CONFIG3            0x61
#define REG_INT_CONFIG0             0x63
#define REG_INT_CONFIG1             0x64
#define REG_INT_SOURCE0             0x65
#define REG_INT_SOURCE1             0x66
#define REG_SELF_TEST_CONFIG        0x70
#define REG_WHO_AM_I                0x75
#define REG_REG_BANK_SEL            0x76
#define REG_GOS_USER0               0x77
#define REG_GOS_USER1               0x78
#define REG_GOS_USER2               0x79
#define REG_GOS_USER3               0x7A
#define REG_GOS_USER4               0x7B
#define REG_GOS_USER5               0x7C
#define REG_GOS_USER6               0x7D
#define REG_GOS_USER7               0x7E
#define REG_GOS_USER8               0x7F
/* Bank 1 */
#define REG_XG_ST_DATA              0x5F
#define REG_YG_ST_DATA              0x60
#define REG_ZG_ST_DATA              0x61
/* Bank 2 */
#define REG_XA_ST_DATA              0x3B
#define REG_YA_ST_DATA              0x3C
#define REG_ZA_ST_DATA              0x3D

/* REG_WHO_AM_I */
#define WHO_AM_I_ICM40604           0x32
#define WHO_AM_I_ICM40605           0x33
/* REG_REG_BANK_SEL */
#define BIT_BANK_SEL_0              0x00
#define BIT_BANK_SEL_1              0x01
#define BIT_BANK_SEL_2              0x02
/* REG_DEVICE_CONFIG */
#define BIT_SOFT_RESET              0x01
/* REG_SPI_SPEED */
#define BIT_SPI_SPEED_5MHZ          0x03
#define BIT_SPI_SPEED_17MHZ         0x05
/* REG_GYRO_CONFIG0/REG_ACCEL_CONFIG0 */
#define SHIFT_GYRO_FS_SEL           5
#define SHIFT_ACCEL_FS_SEL          5
#define SHIFT_ODR_CONF              0
/* REG_INT_CONFIG */
#define SHIFT_INT1_POLARITY         0
#define SHIFT_INT1_DRIVE_CIRCUIT    1
#define SHIFT_INT1_MODE             2
/* REG_PWR_MGMT_0 */
#define BIT_TEMP_DIS                0x20
#define BIT_IDLE                    0x10
#define BIT_GYRO_MODE_OFF           0x00
#define BIT_GYRO_MODE_STBY          0x04
#define BIT_GYRO_MODE_LN            0x0C
#define BIT_ACCEL_MODE_OFF          0x00
#define BIT_ACCEL_MODE_LN           0x03
/* REG_SIGNAL_PATH_RESET */
#define BIT_FIFO_FLUSH              0x02
/* REG_INTF_CONFIG0 */
#define BIT_FIFO_COUNT_REC          0x40
#define BIT_COUNT_BIG_ENDIAN        0x20
#define BIT_SENS_DATA_BIG_ENDIAN    0x10
#define BIT_UI_SIFS_DISABLE_SPI     0x02
#define BIT_UI_SIFS_DISABLE_I2C     0x03
/* REG_FIFO_CONFIG1 */
#define BIT_FIFO_ACCEL_EN           0x01
#define BIT_FIFO_GYRO_EN            0x02
#define BIT_FIFO_TEMP_EN            0x04
#define BIT_FIFO_TMST_FSYNC_EN      0x08
#define BIT_FIFO_HIRES_EN           0x10
#define BIT_FIFO_WM_TH              0x20
#define BIT_FIFO_RESUME_PART_RD     0x40
/* REG_INT_CONFIG1 */
#define BIT_INT_ASY_RST_DISABLE     0x10
/* REG_INT_SOURCE0 */
#define BIT_INT_UI_AGC_RDY_INT1_EN  0x01
#define BIT_INT_FIFO_FULL_INT1_EN   0x02
#define BIT_INT_FIFO_THS_INT1_EN    0x04
#define BIT_INT_UI_DRDY_INT1_EN     0x08
#define BIT_INT_RESET_DONE_INT1_EN  0x10
#define BIT_INT_PLL_RDY_INT1_EN     0x20
#define BIT_INT_UI_FSYNC_INT1_EN    0x40
/* REG_INT_SOURCE1 */
#define BIT_INT_WOM_X_INT1_EN       0x01
#define BIT_INT_WOM_Y_INT1_EN       0x02
#define BIT_INT_WOM_Z_INT1_EN       0x04
#define BIT_INT_SMD_INT1_EN         0x08
#define BIT_INT_WOM_XYZ_INT1_EN     (BIT_INT_WOM_X_INT1_EN | BIT_INT_WOM_Y_INT1_EN | BIT_INT_WOM_Z_INT1_EN)
/* REG_SELF_TEST_CONFIG */
#define BIT_SELF_TEST_REGULATOR_EN  0x40
#define BIT_TEST_AZ_EN              0x20
#define BIT_TEST_AY_EN              0x10
#define BIT_TEST_AX_EN              0x08
#define BIT_TEST_GZ_EN              0x04
#define BIT_TEST_GY_EN              0x02
#define BIT_TEST_GX_EN              0x01
/* REG_INT_STATUS */
#define BIT_INT_STATUS_AGC_RDY      0x01
#define BIT_INT_STATUS_FIFO_FULL    0x02
#define BIT_INT_STATUS_FIFO_THS     0x04
#define BIT_INT_STATUS_DRDY         0x08
#define BIT_INT_STATUS_RESET_DONE   0x10
#define BIT_INT_STATUS_PLL_DRY      0x20
#define BIT_INT_STATUS_UI_FSYNC     0x40
/* REG_INT_STATUS2 */
#define BIT_INT_STATUS_WOM_X        0x01
#define BIT_INT_STATUS_WOM_Y        0x02
#define BIT_INT_STATUS_WOM_Z        0x04
#define BIT_INT_STATUS_SMD          0x08
#define BIT_INT_STATUS_WOM_XYZ      (BIT_INT_STATUS_WOM_X | BIT_INT_STATUS_WOM_Y | BIT_INT_STATUS_WOM_Z)
/* REG_FIFO_CONFIG */
#define BIT_FIFO_MODE_BYPASS        0x00
#define BIT_FIFO_MODE_STREAM        0x40
#define BIT_FIFO_MODE_STOP_FULL     0x80
/* REG_GYRO_ACCEL_CONFIG0 */
#define BIT_ACCEL_UI_BW_2_FIR       0x00
#define BIT_ACCEL_UI_BW_4_IIR       0x10
#define BIT_ACCEL_UI_BW_5_IIR       0x20
#define BIT_ACCEL_UI_BW_8_IIR       0x30
#define BIT_ACCEL_UI_BW_10_IIR      0x40
#define BIT_ACCEL_UI_BW_16_IIR      0x50
#define BIT_ACCEL_UI_BW_20_IIR      0x60
#define BIT_ACCEL_UI_BW_40_IIR      0x70
#define BIT_GYRO_UI_BW_2_FIR        0x00
#define BIT_GYRO_UI_BW_4_IIR        0x01
#define BIT_GYRO_UI_BW_5_IIR        0x02
#define BIT_GYRO_UI_BW_8_IIR        0x03
#define BIT_GYRO_UI_BW_10_IIR       0x04
#define BIT_GYRO_UI_BW_16_IIR       0x05
#define BIT_GYRO_UI_BW_20_IIR       0x06
#define BIT_GYRO_UI_BW_40_IIR       0x07
/* fifo data packet header */
#define BIT_FIFO_HEAD_MSG           0x80
#define BIT_FIFO_HEAD_ACCEL         0x40
#define BIT_FIFO_HEAD_GYRO          0x20
#define BIT_FIFO_HEAD_TMSP_ODR      0x08
#define BIT_FIFO_HEAD_TMSP_NO_ODR   0x04
#define BIT_FIFO_HEAD_TMSP_FSYNC    0x0C
#define BIT_FIFO_HEAD_ODR_ACCEL     0x02
#define BIT_FIFO_HEAD_ODR_GYRO      0x01
/* REG_SMD_CONFIG */
#define BIT_WOM_INT_MODE_OR         0x00
#define BIT_WOM_INT_MODE_AND        0x08
#define BIT_WOM_MODE_INITIAL        0x00
#define BIT_WOM_MODE_PREV           0x04
#define BIT_SMD_MODE_OFF            0x00
#define BIT_SMD_MODE_OLD            0x01
#define BIT_SMD_MODE_SHORT          0x02
#define BIT_SMD_MODE_LONG           0x03
/* REG_TMST_CONFIG */
#define BIT_EN_DREG_FIFO_D2A        0x20
#define BIT_TMST_TO_REGS_EN         0x10
#define BIT_TMST_RESOL              0x08
#define BIT_TMST_DELTA_EN           0x04
#define BIT_TMST_FSYNC_EN           0x02
#define BIT_TMST_EN                 0x01

/* FIFO data definitions */
#define FIFO_PACKET_SIZE            16
#define FIFO_MAX_PACKET_NB          65
#define FIFO_MIN_PACKET_NB          62

/* sensor startup time */
#define ICM40600_GYRO_START_TIME_MS     40
#define ICM40600_ACCEL_START_TIME_MS    10

/* temperature sensor */
#define TEMP_SCALE                  (128.0f / 115.49f)      // scale, #9447
#define TEMP_OFFSET                 25                      // 25 degC offset

#define SPI_WRITE_0(addr, data) spiQueueWrite(addr, data, 2)
#define SPI_WRITE_1(addr, data, delay) spiQueueWrite(addr, data, delay)
#define GET_SPI_WRITE_MACRO(_1,_2,_3,NAME,...) NAME
#define SPI_WRITE(...) GET_SPI_WRITE_MACRO(__VA_ARGS__, SPI_WRITE_1, SPI_WRITE_0)(__VA_ARGS__)

#define SPI_READ_0(addr, size, buf) spiQueueRead(addr, size, buf, 0)
#define SPI_READ_1(addr, size, buf, delay) spiQueueRead(addr, size, buf, delay)
#define GET_SPI_READ_MACRO(_1,_2,_3,_4,NAME,...) NAME
#define SPI_READ(...) GET_SPI_READ_MACRO(__VA_ARGS__, SPI_READ_1, SPI_READ_0)(__VA_ARGS__)

#define EVT_SENSOR_MAG_DATA_RDY sensorGetMyEventType(SENS_TYPE_MAG)
#define EVT_SENSOR_NO_MOTION sensorGetMyEventType(SENS_TYPE_NO_MOTION)
#define EVT_SENSOR_ANY_MOTION sensorGetMyEventType(SENS_TYPE_ANY_MOTION)

#define MAX_NUM_COMMS_EVENT_SAMPLES 15

#define SPI_PACKET_SIZE     30
#define FIFO_READ_SIZE      (FIFO_MAX_PACKET_NB * FIFO_PACKET_SIZE)
#define BUF_MARGIN          32   // some extra buffer for additional reg RW when a FIFO read happens
#define SPI_BUF_SIZE        (FIFO_READ_SIZE + BUF_MARGIN)

#define ACC_FS_RANGE        (16 >> ICM40600_ACC_FS)
#define GYR_FS_RANGE        (2000 >> ICM40600_GYR_FS)
#define GRAVITY_NORM        9.80665f
#define kScale_acc          (GRAVITY_NORM * ACC_FS_RANGE / 32768.0f)
#define kScale_gyr          (NANO_PI / 180.0f * GYR_FS_RANGE / 32768.0f)

#define RATE_TO_HZ              SENSOR_HZ(1.0f)
#define DECIMATION_HIGH_RATE    SENSOR_HZ(1000.0f)
#define DECIMATION_LOW_RATE     SENSOR_HZ(25.0f)
#define NO_DECIMATION_MAX_RATE  SENSOR_HZ(200.0f)   // will set ODR to DECIMATION_HIGH_RATE if more than this rate
#define NO_DECIMATION_MIN_RATE  SENSOR_HZ(25.0f)    // will set ODR to DECIMATION_LOW_RATE if less than this rate
#define MAX_BATCH_SIZE          (((FIFO_MIN_PACKET_NB * 90) / 100) * FIFO_PACKET_SIZE)      // 90% of FIFO size

/* skip first some samples */
#define ACC_SKIP_SAMPLE_NB          0
#define GYR_SKIP_SAMPLE_NB          3

#define CHIP_TIME_RES_US        1
#define CHIP_TIME_OFFSET_US     64000000ULL // 64sec
#define MIN_INCREMENT_TIME_NS   1000000ULL  // 1ms for 1000Hz

enum SensorIndex {
    FIRST_CONT_SENSOR = 0,
    ACC = FIRST_CONT_SENSOR,
    GYR,
    NUM_CONT_SENSOR,
    FIRST_ONESHOT_SENSOR = NUM_CONT_SENSOR,
    WOM = FIRST_ONESHOT_SENSOR,
    NOMO,
    NUM_OF_SENSOR,
};

enum SensorEvents {
    NO_EVT = -1,
    EVT_SPI_DONE = EVT_APP_START + 1,
    EVT_SENSOR_INTERRUPT_1,
};

enum IntState {
    INT_READ_STATUS,
    INT_PROCESS_STATUS,
    INT_READ_FIFO_DATA,
    INT_DONE,
};

enum InitState {
    RESET_ICM40600,
    INIT_ICM40600,
    INIT_DONE,
};

enum CalibrationState {
    CALIBRATION_START,
    CALIBRATION_READ_STATUS,
    CALIBRATION_READ_DATA,
    CALIBRATION_SET_OFFSET,
    CALIBRATION_DONE
};

enum SelfTestState {
    TEST_START,
    TEST_READ_STATUS,
    TEST_READ_DATA,
    TEST_READ_OTP,
    TEST_REPORT,
    TEST_DONE
};

enum SensorState {
    // keep this in sync with getStateName
    SENSOR_BOOT,
    SENSOR_VERIFY_ID,
    SENSOR_INITIALIZING,
    SENSOR_IDLE,
    SENSOR_POWERING_UP,
    SENSOR_POWERING_DOWN,
    SENSOR_CONFIG_CHANGING,
    SENSOR_INT_1_HANDLING,
    SENSOR_CALIBRATING,
    SENSOR_TESTING,
    SENSOR_SAVE_CALIBRATION,
    SENSOR_NUM_OF_STATE
};
#if DBG_STATE
#define PRI_STATE "s"
static const char * getStateName(int32_t s) {
    // keep this in sync with SensorState
    static const char* const l[] = {"BOOT", "VERIFY_ID", "INIT", "IDLE", "PWR_UP",
            "PWR_DN", "CFG_CHANGE", "INT1", "CALIB", "TEST", "SAVE_CALIB"};
    if (s >= 0 && s < SENSOR_NUM_OF_STATE) {
        return l[s];
    }
    return "???";
#else
#define PRI_STATE PRIi32
static int32_t getStateName(int32_t s) {
    return s;
#endif
}

#if DBG_TIMESTAMP
struct StatisticsSet {
    uint32_t sync_reset_count;
    uint32_t sync_count;
    uint32_t sync_adjust_plus;
    uint32_t sync_adjust_minus;
    uint32_t sync_truncate;
};
#endif

struct ConfigStat {
    uint64_t latency;
    uint32_t rate;
    bool enable;
};

struct CalibrationData {
    struct HostHubRawPacket header;
    struct SensorAppEventHeader data_header;
    int32_t xBias;
    int32_t yBias;
    int32_t zBias;
} __attribute__((packed));

struct TestResultData {
    struct HostHubRawPacket header;
    struct SensorAppEventHeader data_header;
} __attribute__((packed));

struct ICM40600Sensor {
    struct ConfigStat pConfig; // pending config status request
    struct TripleAxisDataEvent *data_evt;
    uint32_t handle;
    uint32_t rate;
    uint64_t latency;
    uint64_t prev_rtc_time;
    int16_t offset[3];
    int16_t data[3];
    bool updated;
    bool powered; // activate status
    bool configed; // configure status
    bool wait_for_odr;
    enum SensorIndex idx;
    uint8_t flush;
    uint8_t decimator;
    uint8_t data_cnt;
    uint8_t skip_sample_cnt;
};

struct FifoPacketData {
    int16_t accel[3];
    int16_t gyro[3];
    uint16_t timestamp;
    bool odr_accel;
    bool odr_gyro;
    bool valid_accel;
    bool valid_gyro;
    int8_t temp;
};

struct ICM40600FactoryCal {
    int32_t data[3];
    int32_t data_count;
};

struct ICM40600SelfTest {
    int32_t data[3];
    int32_t data_st_on[3];
    int32_t data_st_off[3];
    int32_t data_count;
    bool st_mode;
    uint8_t otp_st_data_gyro[3];
    uint8_t otp_st_data_accel[3];
};

struct ICM40600Config {
    uint32_t accel_rate;
    uint32_t gyro_rate;
    uint32_t wom_threshold;
    uint32_t fifo_rate;
    uint16_t fifo_watermark;
    uint8_t fifo_sample_size;
    bool accel_on;
    bool gyro_on;
    bool wom_on;
};

struct ICM40600Task {
    uint32_t tid;
    struct ICM40600Sensor sensors[NUM_OF_SENSOR];

    // spi and interrupt
    spi_cs_t cs;
    struct SpiMode mode;
    struct SpiPacket packets[SPI_PACKET_SIZE];
    struct SpiDevice *spiDev;
    struct Gpio *Int1;
    IRQn_Type Irq1;
    struct ChainedIsr Isr1;
    bool Int1_EN;

    // spi buffers
    uint8_t *dataBuffer[3];
    uint8_t txrxBuffer[SPI_BUF_SIZE];

    // states
    volatile uint8_t state;  //task state, type enum SensorState, do NOT change this directly
    enum InitState init_state;
    enum IntState int_state;
    enum CalibrationState calibration_state;
    enum SelfTestState selftest_state;

    // pending config
    bool pending_int[1];
    bool pending_flush;
    bool pending_config[NUM_OF_SENSOR];
    bool pending_calibration_save;

    struct ICM40600Config config;
    uint32_t noMotionTimerHandle;
    uint16_t fifo_count;
    bool powered;
    bool flush;

    // calibration
#ifdef ACCEL_CAL_ENABLED
    struct AccelCal accel_cal;
#endif
#ifdef GYRO_CAL_ENABLED
    struct GyroCal gyro_cal;
#endif

    // timestamping
    time_sync_t gSensorTime2RTC;
    uint64_t data_timestamp;
    uint64_t chip_time_us;
    uint64_t last_sync_time;
    uint64_t last_sync_data_ts;
    uint16_t chip_timestamp;
    bool fifo_start_sync;

    // temperature data from chip in degC
    float chip_temperature;

    // spi rw
    struct SlabAllocator *mDataSlab;
    uint16_t mWbufCnt;
    uint8_t mRegCnt;
    uint8_t mRetryLeft;
    bool spiInUse;

    struct ICM40600FactoryCal factory_cal;

    struct ICM40600SelfTest self_test;

#if DBG_TIMESTAMP
    struct StatisticsSet statistics_set;
#endif
};

static uint32_t AccRates[] = {
    SENSOR_HZ(25.0f/8.0f),
    SENSOR_HZ(25.0f/4.0f),
    SENSOR_HZ(25.0f/2.0f),
    SENSOR_HZ(25.0f),
    SENSOR_HZ(50.0f),
    SENSOR_HZ(100.0f),
    SENSOR_HZ(200.0f),
    SENSOR_HZ(500.0f),
    0,
};

static uint32_t GyrRates[] = {
    SENSOR_HZ(25.0f/8.0f),
    SENSOR_HZ(25.0f/4.0f),
    SENSOR_HZ(25.0f/2.0f),
    SENSOR_HZ(25.0f),
    SENSOR_HZ(50.0f),
    SENSOR_HZ(100.0f),
    SENSOR_HZ(200.0f),
    SENSOR_HZ(500.0f),
    0,
};

static struct ICM40600Task mTask;

#define DEC_INFO(name, type, axis, inter, samples) \
    .sensorName = name, \
    .sensorType = type, \
    .numAxis = axis, \
    .interrupt = inter, \
    .minSamples = samples

#define DEC_INFO_RATE(name, rates, type, axis, inter, samples) \
    DEC_INFO(name, type, axis, inter, samples), \
    .supportedRates = rates

#define DEC_INFO_RATE_RAW(name, rates, type, axis, inter, samples, raw, scale) \
    DEC_INFO(name, type, axis, inter, samples), \
    .supportedRates = rates, \
    .flags1 = SENSOR_INFO_FLAGS1_RAW, \
    .rawType = raw, \
    .rawScale = scale

#define DEC_INFO_RATE_BIAS(name, rates, type, axis, inter, samples, bias) \
    DEC_INFO(name, type, axis, inter, samples), \
    .supportedRates = rates, \
    .flags1 = SENSOR_INFO_FLAGS1_BIAS, \
    .biasType = bias

#define DEC_INFO_RATE_RAW_BIAS(name, rates, type, axis, inter, samples, raw, scale, bias) \
    DEC_INFO_RATE_RAW(name, rates, type, axis, inter, samples, raw, scale), \
    .flags1 = SENSOR_INFO_FLAGS1_RAW | SENSOR_INFO_FLAGS1_BIAS, \
    .biasType = bias

typedef struct ICM40600Task _Task;
#define TASK  _Task* const _task

// To get rid of static variables all task functions should have a task structure pointer input.
// This is an intermediate step.
#define TDECL()  TASK = &mTask; (void)_task

// Access task variables without explicitly specify the task structure pointer.
#define T(v)  (_task->v)

// Atomic get state
#define GET_STATE() (atomicReadByte(&(_task->state)))

// Atomic set state, this set the state to arbitrary value, use with caution
#define SET_STATE(s) do{\
        DEBUG_PRINT_IF(DBG_STATE, "set state %" PRI_STATE "\n", getStateName(s));\
        atomicWriteByte(&(_task->state), (s));\
    }while(0)

// Atomic switch state from IDLE to desired state.
static bool trySwitchState_(TASK, enum SensorState newState) {
#if DBG_STATE
    bool ret = atomicCmpXchgByte(&T(state), SENSOR_IDLE, newState);
    uint8_t prevState = ret ? SENSOR_IDLE : GET_STATE();
    DEBUG_PRINT("switch state %" PRI_STATE "->%" PRI_STATE ", %s\n",
            getStateName(prevState), getStateName(newState), ret ? "ok" : "failed");
    return ret;
#else
    return atomicCmpXchgByte(&T(state), SENSOR_IDLE, newState);
#endif
}
// Short-hand
#define trySwitchState(s) trySwitchState_(_task, (s))

static const struct SensorInfo mSensorInfo[NUM_OF_SENSOR] =
{
#ifdef ACCEL_CAL_ENABLED
    { DEC_INFO_RATE_RAW_BIAS("Accelerometer", AccRates, SENS_TYPE_ACCEL, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 3000, SENS_TYPE_ACCEL_RAW, 1.0 / kScale_acc, SENS_TYPE_ACCEL_BIAS) },
#else
    { DEC_INFO_RATE_RAW("Accelerometer", AccRates, SENS_TYPE_ACCEL, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 3000, SENS_TYPE_ACCEL_RAW, 1.0 / kScale_acc) },
#endif
#ifdef GYRO_CAL_ENABLED
    { DEC_INFO_RATE_BIAS("Gyroscope", GyrRates, SENS_TYPE_GYRO, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 20, SENS_TYPE_GYRO_BIAS) },
#else
    { DEC_INFO_RATE("Gyroscope", GyrRates, SENS_TYPE_GYRO, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 20) },
#endif
    { DEC_INFO("Wake-on-Motion", SENS_TYPE_ANY_MOTION, NUM_AXIS_EMBEDDED, NANOHUB_INT_NONWAKEUP, 20) },
    { DEC_INFO("No-Motion", SENS_TYPE_NO_MOTION, NUM_AXIS_EMBEDDED, NANOHUB_INT_NONWAKEUP, 20) },
};

static void time_init(TASK)
{
    time_sync_init(&T(gSensorTime2RTC));
}

static bool sensortime_to_rtc_time(TASK, uint64_t sensor_time, uint64_t *rtc_time_ns)
{
    return time_sync_estimate_time1(&T(gSensorTime2RTC), sensor_time, rtc_time_ns);
}

static void map_sensortime_to_rtc_time(TASK, uint64_t sensor_time, uint64_t rtc_time_ns)
{
#if DBG_TIMESTAMP
    T(statistics_set).sync_count++;
#endif
    time_sync_add(&T(gSensorTime2RTC), rtc_time_ns, sensor_time);
}

static void invalidate_sensortime_to_rtc_time(TASK)
{
#if DBG_TIMESTAMP
    T(statistics_set).sync_reset_count++;
#endif
    time_sync_reset(&T(gSensorTime2RTC));
}

static void dataEvtFree(void *ptr)
{
    TDECL();
    struct TripleAxisDataEvent *ev = (struct TripleAxisDataEvent *)ptr;

    slabAllocatorFree(T(mDataSlab), ev);
}

static void spiQueueWrite(uint8_t addr, uint8_t data, uint32_t delay)
{
    TDECL();

    if (T(spiInUse)) {
        ERROR_PRINT("SPI in use, cannot queue write\n");
        return;
    }
    T(packets[T(mRegCnt)]).size = 2;
    T(packets[T(mRegCnt)]).txBuf = &T(txrxBuffer[T(mWbufCnt)]);
    T(packets[T(mRegCnt)]).rxBuf = &T(txrxBuffer[T(mWbufCnt)]);
    T(packets[T(mRegCnt)]).delay = delay * 1000;
    T(txrxBuffer[T(mWbufCnt++)]) = ICM40600_SPI_WRITE | addr;
    T(txrxBuffer[T(mWbufCnt++)]) = data;
    T(mRegCnt)++;
}

/*
 * need to be sure size of buf is larger than read size
 */
static void spiQueueRead(uint8_t addr, size_t size, uint8_t **buf, uint32_t delay)
{
    TDECL();

    if (T(spiInUse)) {
        ERROR_PRINT("SPI in use, cannot queue read %d %d\n", (int)addr, (int)size);
        return;
    }

    *buf = &T(txrxBuffer[T(mWbufCnt)]);
    T(packets[T(mRegCnt)]).size = size + 1; // first byte will not contain valid data
    T(packets[T(mRegCnt)]).txBuf = &T(txrxBuffer[T(mWbufCnt)]);
    T(packets[T(mRegCnt)]).rxBuf = *buf;
    T(packets[T(mRegCnt)]).delay = delay * 1000;
    T(txrxBuffer[T(mWbufCnt)++]) = ICM40600_SPI_READ | addr;
    T(mWbufCnt) += size;
    T(mRegCnt)++;
}

static void spiBatchTxRx(struct SpiMode *mode,
        SpiCbkF callback, void *cookie, const char * src)
{
    TDECL();

    if (T(mRegCnt) == 0) {
        callback(cookie, 0);
        return;
    }

    if (T(mWbufCnt) > SPI_BUF_SIZE) {
        ERROR_PRINT("NO enough SPI buffer space, dropping transaction.\n");
        return;
    }
    if (T(mRegCnt) > SPI_PACKET_SIZE) {
        ERROR_PRINT("spiBatchTxRx too many packets!\n");
        return;
    }

    T(spiInUse) = true;
    T(mWbufCnt) = 0;

    // Reset variables before issuing SPI transaction.
    // SPI may finish before spiMasterRxTx finish
    uint8_t regCount = T(mRegCnt);
    T(mRegCnt) = 0;

    if (spiMasterRxTx(T(spiDev), T(cs), T(packets), regCount, mode, callback, cookie) < 0) {
        ERROR_PRINT("spiMasterRxTx failed!\n");
    }
}

static bool icm40600Isr1(struct ChainedIsr *isr)
{
    TASK = container_of(isr, struct ICM40600Task, Isr1);

    if (!extiIsPendingGpio(T(Int1))) {
        return false;
    }

    /* better to use atomic read for the pending flag but not serious even not atomic */
    if (!T(pending_int[0])) {
        osEnqueuePrivateEvt(EVT_SENSOR_INTERRUPT_1, _task, NULL, T(tid));
    }
    extiClearPendingGpio(T(Int1));
    return true;
}

static void sensorSpiCallback(void *cookie, int err)
{
    TDECL();

    T(spiInUse) = false;

    if (!osEnqueuePrivateEvt(EVT_SPI_DONE, cookie, NULL, T(tid)))
        ERROR_PRINT("sensorSpiCallback: osEnqueuePrivateEvt() failed\n");
}

static void noMotionCallback(uint32_t timerId, void *data)
{
    const struct ICM40600Sensor *sensor = (const struct ICM40600Sensor *)data;

    if (!sensor->configed)
        return;

    osEnqueueEvt(EVT_SENSOR_NO_MOTION, NULL, NULL);
}

static void setOffsetReg(TASK)
{
    uint8_t val;
    const int16_t * const acc_offset = T(sensors[ACC]).offset;
    const int16_t * const gyr_offset = T(sensors[GYR]).offset;

    DEBUG_PRINT("Set ACC hw offset %d %d %d\n",
                -acc_offset[0], -acc_offset[1], -acc_offset[2]);
    DEBUG_PRINT("Set GYR hw offset %d %d %d\n",
                -gyr_offset[0], -gyr_offset[1], -gyr_offset[2]);

    // GX_L
    val = (-gyr_offset[0]) & 0xff;
    SPI_WRITE(REG_GOS_USER0, val);
    // GY_H / GX_H
    val = (-gyr_offset[1] >> 4) & 0xf0;
    val |= ((-gyr_offset[0] >> 8) & 0x0f);
    SPI_WRITE(REG_GOS_USER1, val);
    // GY_L
    val = (-gyr_offset[1]) & 0xff;
    SPI_WRITE(REG_GOS_USER2, val);
    // GZ_L
    val = (-gyr_offset[2]) & 0xff;
    SPI_WRITE(REG_GOS_USER3, val);
    // AX_H / GZ_H
    val = (-acc_offset[0] >> 4) & 0xf0;
    val |= ((-gyr_offset[2] >> 8) & 0x0f);
    SPI_WRITE(REG_GOS_USER4, val);
    // AX_H / GZ_H
    val = (-acc_offset[0] >> 4) & 0xf0;
    val |= ((-gyr_offset[2] >> 8) & 0x0f);
    SPI_WRITE(REG_GOS_USER4, val);
    // AX_L
    val = (-acc_offset[0]) & 0xff;
    SPI_WRITE(REG_GOS_USER5, val);
    // AY_L
    val = (-acc_offset[1]) & 0xff;
    SPI_WRITE(REG_GOS_USER6, val);
    // AZ_H / AY_H
    val = (-acc_offset[2] >> 4) & 0xf0;
    val |= ((-acc_offset[1] >> 8) & 0x0f);
    SPI_WRITE(REG_GOS_USER7, val);
    // AZ_L
    val = (-acc_offset[2]) & 0xff;
    SPI_WRITE(REG_GOS_USER8, val);
}

static void resetOffsetReg(TASK, enum SensorIndex idx)
{
    uint8_t val;

    DEBUG_PRINT("Reset offset registers sensor=%d\n", idx);

    if (idx == ACC) {
        val = (-T(sensors[GYR]).offset[2] >> 8) & 0x0f;
        SPI_WRITE(REG_GOS_USER4, val);  // AX_H / GZ_H
        SPI_WRITE(REG_GOS_USER5, 0x00); // AX_L
        SPI_WRITE(REG_GOS_USER6, 0x00); // AY_L
        SPI_WRITE(REG_GOS_USER7, 0x00); // AZ_H / AY_H
        SPI_WRITE(REG_GOS_USER8, 0x00); // AZ_L
    } else if (idx == GYR) {
        SPI_WRITE(REG_GOS_USER0, 0x00); // GX_L
        SPI_WRITE(REG_GOS_USER1, 0x00); // GY_H / GX_H
        SPI_WRITE(REG_GOS_USER2, 0x00); // GY_L
        SPI_WRITE(REG_GOS_USER3, 0x00); // GZ_L
        val = (-T(sensors[ACC]).offset[0] >> 4) & 0xf0;
        SPI_WRITE(REG_GOS_USER4, val);  // AX_H / GZ_H
    }
}

static bool saveCalibration(TASK)
{
    if (trySwitchState(SENSOR_SAVE_CALIBRATION)) {
        setOffsetReg(_task);
        spiBatchTxRx(&T(mode), sensorSpiCallback, _task, __FUNCTION__);
        return true;
    } else {
        return false;
    }
}

static bool accCfgData(void *data, void *cookie)
{
    TDECL();
    struct ICM40600Sensor *sensor = &T(sensors[ACC]);
    struct CfgData {
        int32_t hw[3];  // chip frame (hw unit @FSR=factory calibration)
        float sw[3];    // body frame
    };
    struct CfgData *values = data;
    int i;

    for (i = 0; i < 3; i++) {
        // offset register is 12bit
        if (values->hw[i] > 2047) {
            sensor->offset[i] = 2047;
        } else if (values->hw[i] < -2048) {
            sensor->offset[i] = -2048;
        } else {
            sensor->offset[i] = values->hw[i];
        }
    }
#ifdef ACCEL_CAL_ENABLED
    accelCalBiasSet(&T(accel_cal), values->sw[0], values->sw[1], values->sw[2]);
#endif
    if (!saveCalibration(_task)) {
        T(pending_calibration_save) = true;
    }
    INFO_PRINT("accCfgData hw bias: data=%d, %d, %d\n",
            sensor->offset[0], sensor->offset[1], sensor->offset[2]);

    return true;
}

static bool gyrCfgData(void *data, void *cookie)
{
    TDECL();
    struct ICM40600Sensor *sensor = &T(sensors[GYR]);
    const struct AppToSensorHalDataPayload *p = data;
    int i;

    if (p->type == HALINTF_TYPE_GYRO_CAL_BIAS && p->size == sizeof(struct GyroCalBias)) {
        const struct GyroCalBias *bias = p->gyroCalBias;
        for (i = 0; i < 3; i++) {
            // offset register is 12bit
            if (bias->hardwareBias[i] > 2047) {
                sensor->offset[i] = 2047;
            } else if (bias->hardwareBias[i] < -2048) {
                sensor->offset[i] = -2048;
            } else {
                sensor->offset[i] = bias->hardwareBias[i];
            }
        }
#ifdef GYRO_CAL_ENABLED
        gyroCalSetBias(&T(gyro_cal), bias->softwareBias[0], bias->softwareBias[1],
                       bias->softwareBias[2], sensorGetTime());
#endif
        if (!saveCalibration(_task)) {
            T(pending_calibration_save) = true;
        }
        INFO_PRINT("gyrCfgData hw bias: data=%d, %d, %d\n",
                sensor->offset[0], sensor->offset[1], sensor->offset[2]);
    } else if (p->type == HALINTF_TYPE_GYRO_OTC_DATA && p->size == sizeof(struct GyroOtcData)) {
        // Over-temperature gyro calibration not supported
    } else {
        ERROR_PRINT("Unknown gyro config data type 0x%04x, size %d\n", p->type, p->size);
    }

    return true;
}

static bool validateFifoData(const int16_t data[3])
{
    bool ret = true;

    if ((data[0] == -32768) && (data[1] == -32768) && (data[2] == -32768))
        ret = false;

    return ret;
}

static void getFifoData(const uint8_t *data, int idx, struct FifoPacketData *out)
{
    /* invalidate all flags */
    out->valid_accel = false;
    out->valid_gyro = false;

    /* Header        : 1 byte
     * Accel         : 6 byte
     * Gyro          : 6 byte
     * Temp          : 1 byte
     * Timestamp     : 2 byte */
    if (data[idx] & BIT_FIFO_HEAD_ACCEL) {
        out->accel[0] = (data[idx + 2] << 8) | data[idx + 1];
        out->accel[1] = (data[idx + 4] << 8) | data[idx + 3];
        out->accel[2] = (data[idx + 6] << 8) | data[idx + 5];
        out->valid_accel = validateFifoData(out->accel);
    }
    out->odr_accel = (data[idx] & BIT_FIFO_HEAD_ODR_ACCEL) ? true : false;

    if (data[idx] & BIT_FIFO_HEAD_GYRO) {
        out->gyro[0] = (data[idx + 8] << 8) | data[idx + 7];
        out->gyro[1] = (data[idx + 10] << 8) | data[idx + 9];
        out->gyro[2] = (data[idx + 12] << 8) | data[idx + 11];
        out->valid_gyro = validateFifoData(out->gyro);
    }
    out->odr_gyro = (data[idx] & BIT_FIFO_HEAD_ODR_GYRO) ? true : false;

    out->temp = (int8_t)data[idx + 13];
    out->timestamp = (data[idx + 15] << 8) | data[idx + 14];
}

static bool calSelftestGetOneData(enum SensorIndex sensor_idx, const uint8_t *data, int idx, int16_t raw_data[3])
{
    struct FifoPacketData fifo_data;
    bool ret = false;

    getFifoData(data, idx, &fifo_data);

    switch (sensor_idx) {
    case ACC:
        if (fifo_data.valid_accel) {
            raw_data[0] = fifo_data.accel[0];
            raw_data[1] = fifo_data.accel[1];
            raw_data[2] = fifo_data.accel[2];
            ret = true;
        }
        break;
    case GYR:
        if (fifo_data.valid_gyro) {
            raw_data[0] = fifo_data.gyro[0];
            raw_data[1] = fifo_data.gyro[1];
            raw_data[2] = fifo_data.gyro[2];
            ret = true;
        }
        break;
    default:
        break;
    }

    return ret;
}

static void calSelftestFifoEnable(TASK, enum SensorIndex idx, bool en, uint32_t delay)
{
    uint8_t val;

    if (en) {
        // enable FIFO output
        T(config).fifo_sample_size = FIFO_PACKET_SIZE;
        val = BIT_FIFO_ACCEL_EN | BIT_FIFO_GYRO_EN |
              BIT_FIFO_TEMP_EN | BIT_FIFO_TMST_FSYNC_EN;
        SPI_WRITE(REG_FIFO_CONFIG, BIT_FIFO_MODE_STREAM);
        SPI_WRITE(REG_FIFO_CONFIG1, val, delay); // with wait
    } else {
        // disable FIFO output
        T(config).fifo_sample_size = 0;
        SPI_WRITE(REG_FIFO_CONFIG, BIT_FIFO_MODE_BYPASS);
        SPI_WRITE(REG_FIFO_CONFIG1, 0);
    }
}

/*
 * Factory calibration
 */
static void sendCalibrationResult(uint8_t status, uint8_t sensorType,
        int32_t xBias, int32_t yBias, int32_t zBias)
{
    struct CalibrationData *data = heapAlloc(sizeof(struct CalibrationData));
    if (!data) {
        ERROR_PRINT("Couldn't alloc cal result pkt");
        return;
    }

    data->header.appId = ICM40600_APP_ID;
    data->header.dataLen = (sizeof(struct CalibrationData) - sizeof(struct HostHubRawPacket));
    data->data_header.msgId = SENSOR_APP_MSG_ID_CAL_RESULT;
    data->data_header.sensorType = sensorType;
    data->data_header.status = status;

    data->xBias = xBias;
    data->yBias = yBias;
    data->zBias = zBias;

    if (!osEnqueueEvtOrFree(EVT_APP_TO_HOST, data, heapFree))
        ERROR_PRINT("Couldn't send cal result evt");
}

static void calibrationInit(TASK, enum SensorIndex idx)
{
    uint8_t val;

    // Disable Interrupt
    SPI_WRITE(REG_INT_SOURCE0, 0);
    SPI_WRITE(REG_INT_SOURCE1, 0);

    // reset offset registers
    resetOffsetReg(_task, idx);

    // stop FIFO
    calSelftestFifoEnable(_task, idx, false, 0);

    // set rate
    SPI_WRITE(REG_GYRO_CONFIG0, (CALIBRATION_GYR_FS << SHIFT_GYRO_FS_SEL) |
                                (CALIBRATION_ODR << SHIFT_ODR_CONF));
    SPI_WRITE(REG_ACCEL_CONFIG0, (CALIBRATION_ACC_FS << SHIFT_ACCEL_FS_SEL) |
                                 (CALIBRATION_ODR << SHIFT_ODR_CONF));

    // set filter
    SPI_WRITE(REG_GYRO_ACCEL_CONFIG0, CALIBRATION_ACC_BW_IND | CALIBRATION_GYR_BW_IND);

    // turn on sensors
    switch (idx) {
    case ACC:
        val = BIT_ACCEL_MODE_LN;
        break;
    case GYR:
        val = BIT_GYRO_MODE_LN;
        break;
    default:
        val = 0;
        break;
    }
    SPI_WRITE(REG_PWR_MGMT_0, val, 200 * 1000);

    calSelftestFifoEnable(_task, idx, true, CALIBRATION_READ_INTERVAL_US);
}

static void calibrationDeinit(TASK, enum SensorIndex idx)
{
    calSelftestFifoEnable(_task, idx, false, 0);

    SPI_WRITE(REG_GYRO_ACCEL_CONFIG0, ICM40600_ACC_BW_IND | ICM40600_GYR_BW_IND);
    SPI_WRITE(REG_PWR_MGMT_0, 0, 200); // 9136

    // make register accesses happen when sensor is enabled next time
    T(config).gyro_rate = 0;
    T(config).accel_rate = 0;
    T(config).fifo_rate = 0;
    T(config).fifo_watermark = 0;
    T(config).accel_on = false;
    T(config).gyro_on = false;
    T(config).wom_on = false;
}

static void calibrationHandling(TASK, enum SensorIndex idx)
{
    const uint8_t *buf, *data;
    uint8_t int_status;
    uint16_t fifo_count;
    int i, t;
    bool r;
    int16_t raw_data[3] = { 0, 0, 0 };

    if (idx != ACC && idx != GYR) {
        ERROR_PRINT("Invalid sensor index\n");
        return;
    }

    switch (T(calibration_state)) {
        case CALIBRATION_START:
            T(mRetryLeft) = RETRY_CNT_CALIBRATION;
            calibrationInit(_task, idx);
            for (i = 0; i < 3; i++) {
                T(factory_cal).data[i] = 0;
            }
            SPI_READ(REG_INT_STATUS, 1, &T(dataBuffer[0]));
            SPI_READ(REG_FIFO_BYTE_COUNT1, 2, &T(dataBuffer[1]));
            T(calibration_state) = CALIBRATION_READ_STATUS;
            T(factory_cal).data_count = CALIBRATION_SAMPLE_NB;
            spiBatchTxRx(&T(mode), sensorSpiCallback, &T(sensors[idx]), __FUNCTION__);
            break;
        case CALIBRATION_READ_STATUS:
            buf = T(dataBuffer[0]);
            int_status = buf[1];
            buf = T(dataBuffer[1]);
            fifo_count = buf[2] << 8 | buf[1];
            fifo_count = fifo_count - fifo_count % T(config).fifo_sample_size;
            T(fifo_count) = fifo_count;
            DEBUG_PRINT("fifo_count = %d\n", fifo_count);
            if (int_status & BIT_INT_STATUS_FIFO_FULL) {
                ERROR_PRINT("fifo overflow\n");
                calibrationDeinit(_task, idx);
                T(calibration_state) = CALIBRATION_DONE;
                sendCalibrationResult(SENSOR_APP_EVT_STATUS_ERROR, mSensorInfo[idx].sensorType, 0, 0, 0);
            } else {
                T(calibration_state) = CALIBRATION_READ_DATA;
                SPI_READ(REG_FIFO_DATA, fifo_count, &T(dataBuffer[0]));
            }
            spiBatchTxRx(&T(mode), sensorSpiCallback, &T(sensors[idx]), __FUNCTION__);
            break;
        case CALIBRATION_READ_DATA:
            buf = T(dataBuffer[0]);
            data = &buf[1];
            for (i = 0; i < T(fifo_count); i += T(config).fifo_sample_size) {
                r = calSelftestGetOneData(idx, data, i, raw_data);
                if (r == false) {
                    ERROR_PRINT("invalid data packet\n");
                    calibrationDeinit(_task, idx);
                    T(calibration_state) = CALIBRATION_DONE;
                    sendCalibrationResult(SENSOR_APP_EVT_STATUS_ERROR, mSensorInfo[idx].sensorType, 0, 0, 0);
                    break;
                }
                for (t = 0; t < 3; t++) {
                    T(factory_cal).data[t] += raw_data[t];
                }
                T(factory_cal).data_count--;
                if (T(factory_cal).data_count == 0) {
                    break;
                }
            }
            if (T(factory_cal).data_count > 0) {
                if (--T(mRetryLeft) == 0) {
                    ERROR_PRINT("calibration timeout\n");
                    calibrationDeinit(_task, idx);
                    T(calibration_state) = CALIBRATION_DONE;
                    sendCalibrationResult(SENSOR_APP_EVT_STATUS_ERROR, mSensorInfo[idx].sensorType, 0, 0, 0);
                } else {
                    calSelftestFifoEnable(_task, idx, false, 0); // toggle FIFO to reset
                    calSelftestFifoEnable(_task, idx, true, CALIBRATION_READ_INTERVAL_US);
                    SPI_READ(REG_INT_STATUS, 1, &T(dataBuffer[0]));
                    SPI_READ(REG_FIFO_BYTE_COUNT1, 2, &T(dataBuffer[1]));
                    T(calibration_state) = CALIBRATION_READ_STATUS;
                }
                spiBatchTxRx(&T(mode), sensorSpiCallback, &T(sensors[idx]), __FUNCTION__);
                break;
            }
            T(calibration_state) = CALIBRATION_SET_OFFSET;
            // fall-through
        case CALIBRATION_SET_OFFSET:
            DEBUG_PRINT("calibration total: %ld, %ld, %ld, data count=%d\n",
                    T(factory_cal).data[0],
                    T(factory_cal).data[1],
                    T(factory_cal).data[2],
                    CALIBRATION_SAMPLE_NB);
            for (i = 0; i < 3; i++) {
                T(factory_cal).data[i] /= CALIBRATION_SAMPLE_NB;
            }
            DEBUG_PRINT("average: %ld, %ld, %ld\n",
                    T(factory_cal).data[0],
                    T(factory_cal).data[1],
                    T(factory_cal).data[2]);
            if (idx == ACC) {
                // assume the largest data axis shows +1 or -1 gee
                t = 0;
                for (i = 0; i < 3; i++) {
                    if (abs(T(factory_cal).data[i]) > abs(T(factory_cal).data[t]))
                        t = i;
                }
                if (T(factory_cal).data[t] > 0) {
                    DEBUG_PRINT("assume axis %d is %d\n", t, CALIBRATION_ACC_1G);
                    T(factory_cal).data[t] -= CALIBRATION_ACC_1G;
                } else {
                    DEBUG_PRINT("assume axis %d is %d\n", t, -CALIBRATION_ACC_1G);
                    T(factory_cal).data[t] += CALIBRATION_ACC_1G;
                }
            }
            // set bias to offset registers
            for (i = 0; i < 3; i++) {
                T(sensors[idx]).offset[i] = T(factory_cal).data[i];
            }
            setOffsetReg(_task);
            calibrationDeinit(_task, idx);

            sendCalibrationResult(SENSOR_APP_EVT_STATUS_SUCCESS,
                                  mSensorInfo[idx].sensorType,
                                  T(factory_cal).data[0],
                                  T(factory_cal).data[1],
                                  T(factory_cal).data[2]);
            INFO_PRINT("reported: %ld, %ld, %ld\n",
                        T(factory_cal).data[0],
                        T(factory_cal).data[1],
                        T(factory_cal).data[2]);
            T(calibration_state) = CALIBRATION_DONE;
            spiBatchTxRx(&T(mode), sensorSpiCallback, &T(sensors[idx]), __FUNCTION__);
            break;
        default:
            break;
    }
}

static bool accCalibration(void *cookie)
{
    TDECL();

    INFO_PRINT("Accel Calibration\n");

    if (!T(powered) && trySwitchState(SENSOR_CALIBRATING)) {
        T(calibration_state) = CALIBRATION_START;
        calibrationHandling(_task, ACC);
        return true;
    } else {
        ERROR_PRINT("Cannot run calibration because sensor is busy\n");
        sendCalibrationResult(SENSOR_APP_EVT_STATUS_BUSY, SENS_TYPE_ACCEL, 0, 0, 0);
        return false;
    }
}

static bool gyrCalibration(void *cookie)
{
    TDECL();

    INFO_PRINT("Gyro Calibration\n");

    if (!T(powered) && trySwitchState(SENSOR_CALIBRATING)) {
        T(calibration_state) = CALIBRATION_START;
        calibrationHandling(_task, GYR);
        return true;
    } else {
        ERROR_PRINT("Cannot run calibration because sensor is busy\n");
        sendCalibrationResult(SENSOR_APP_EVT_STATUS_BUSY, SENS_TYPE_GYRO, 0, 0, 0);
        return false;
    }
}

/*
 * Selftest
 */
static void sendTestResult(uint8_t status, uint8_t sensorType)
{
    struct TestResultData *data = heapAlloc(sizeof(struct TestResultData));
    if (!data) {
        ERROR_PRINT("Couldn't alloc test result packet");
        return;
    }

    data->header.appId = ICM40600_APP_ID;
    data->header.dataLen = (sizeof(struct TestResultData) - sizeof(struct HostHubRawPacket));
    data->data_header.msgId = SENSOR_APP_MSG_ID_TEST_RESULT;
    data->data_header.sensorType = sensorType;
    data->data_header.status = status;

    if (!osEnqueueEvtOrFree(EVT_APP_TO_HOST, data, heapFree))
        ERROR_PRINT("Couldn't send test result packet");
}

static const uint16_t SelfTestEquation[256] = {
    2620, 2646, 2672, 2699, 2726, 2753, 2781, 2808,
    2837, 2865, 2894, 2923, 2952, 2981, 3011, 3041,
    3072, 3102, 3133, 3165, 3196, 3228, 3261, 3293,
    3326, 3359, 3393, 3427, 3461, 3496, 3531, 3566,
    3602, 3638, 3674, 3711, 3748, 3786, 3823, 3862,
    3900, 3939, 3979, 4019, 4059, 4099, 4140, 4182,
    4224, 4266, 4308, 4352, 4395, 4439, 4483, 4528,
    4574, 4619, 4665, 4712, 4759, 4807, 4855, 4903,
    4953, 5002, 5052, 5103, 5154, 5205, 5257, 5310,
    5363, 5417, 5471, 5525, 5581, 5636, 5693, 5750,
    5807, 5865, 5924, 5983, 6043, 6104, 6165, 6226,
    6289, 6351, 6415, 6479, 6544, 6609, 6675, 6742,
    6810, 6878, 6946, 7016, 7086, 7157, 7229, 7301,
    7374, 7448, 7522, 7597, 7673, 7750, 7828, 7906,
    7985, 8065, 8145, 8227, 8309, 8392, 8476, 8561,
    8647, 8733, 8820, 8909, 8998, 9088, 9178, 9270,
    9363, 9457, 9551, 9647, 9743, 9841, 9939, 10038,
    10139, 10240, 10343, 10446, 10550, 10656, 10763, 10870,
    10979, 11089, 11200, 11312, 11425, 11539, 11654, 11771,
    11889, 12008, 12128, 12249, 12371, 12495, 12620, 12746,
    12874, 13002, 13132, 13264, 13396, 13530, 13666, 13802,
    13940, 14080, 14221, 14363, 14506, 14652, 14798, 14946,
    15096, 15247, 15399, 15553, 15709, 15866, 16024, 16184,
    16346, 16510, 16675, 16842, 17010, 17180, 17352, 17526,
    17701, 17878, 18057, 18237, 18420, 18604, 18790, 18978,
    19167, 19359, 19553, 19748, 19946, 20145, 20347, 20550,
    20756, 20963, 21173, 21385, 21598, 21814, 22033, 22253,
    22475, 22700, 22927, 23156, 23388, 23622, 23858, 24097,
    24338, 24581, 24827, 25075, 25326, 25579, 25835, 26093,
    26354, 26618, 26884, 27153, 27424, 27699, 27976, 28255,
    28538, 28823, 29112, 29403, 29697, 29994, 30294, 30597,
    30903, 31212, 31524, 31839, 32157, 32479, 32804
};

static void selfTestInit(TASK, enum SensorIndex idx)
{
    uint8_t val;

    // Disable Interrupt
    SPI_WRITE(REG_INT_SOURCE0, 0);
    SPI_WRITE(REG_INT_SOURCE1, 0);

    // reset offset registers
    resetOffsetReg(_task, idx);

    // stop FIFO
    calSelftestFifoEnable(_task, idx, false, 0);

    // self-test mode set
    val = 0;
    if (T(self_test).st_mode) {
        if (idx == ACC) {
            val |= (BIT_TEST_AX_EN | BIT_TEST_AY_EN | BIT_TEST_AZ_EN);
            val |= BIT_SELF_TEST_REGULATOR_EN;
        } else if (idx == GYR) {
            val |= (BIT_TEST_GX_EN | BIT_TEST_GY_EN | BIT_TEST_GZ_EN);
        }
    }
    SPI_WRITE(REG_SELF_TEST_CONFIG, val);

    // set rate
    SPI_WRITE(REG_GYRO_CONFIG0, (SELF_TEST_GYR_FS << SHIFT_GYRO_FS_SEL) |
                                (SELF_TEST_ODR << SHIFT_ODR_CONF));
    SPI_WRITE(REG_ACCEL_CONFIG0, (SELF_TEST_ACC_FS << SHIFT_ACCEL_FS_SEL) |
                                 (SELF_TEST_ODR << SHIFT_ODR_CONF));

    // set filter
    val = 0;
    if (idx == ACC) {
        val |= SELF_TEST_ACC_BW_IND;
    } else if (idx == GYR) {
        val |= SELF_TEST_GYR_BW_IND;
    }
    SPI_WRITE(REG_GYRO_ACCEL_CONFIG0, val);

    // turn on sensors
    val = 0;
    if (idx == ACC) {
        val |= BIT_ACCEL_MODE_LN;
    } else if (idx == GYR) {
        val |= BIT_GYRO_MODE_LN;
    }
    SPI_WRITE(REG_PWR_MGMT_0, val, 200 * 1000);

    calSelftestFifoEnable(_task, idx, true, SELF_TEST_READ_INTERVAL_US);
}

static void selfTestDeinit(TASK, enum SensorIndex idx)
{
    calSelftestFifoEnable(_task, idx, false, 0);

    SPI_WRITE(REG_SELF_TEST_CONFIG, 0);
    SPI_WRITE(REG_GYRO_ACCEL_CONFIG0, ICM40600_ACC_BW_IND | ICM40600_GYR_BW_IND);
    SPI_WRITE(REG_PWR_MGMT_0, 0, 200); // 9136

    // make register accesses happen when sensor is enabled next time
    T(config).gyro_rate = 0;
    T(config).accel_rate = 0;
    T(config).fifo_rate = 0;
    T(config).fifo_watermark = 0;
    T(config).accel_on = false;
    T(config).gyro_on = false;
    T(config).wom_on = false;
}

static bool checkAccelSelftest(TASK)
{
    int32_t st_res;
    uint32_t st_otp[3];
    int i;
    int32_t ratio;
    bool pass = true;
    bool otp_value_zero = false;

    /* calculate ST_OTP */
    for (i = 0; i < 3; i++) {
        if (T(self_test.otp_st_data_accel[i] != 0))
            st_otp[i] = SelfTestEquation[T(self_test).otp_st_data_accel[i] - 1];
        else
            otp_value_zero = true;
    }

    if (!otp_value_zero) {
        /* criteria a */
        for (i = 0; i < 3; i++) {
            st_res = T(self_test).data_st_on[i] - T(self_test).data_st_off[i];
            ratio = abs(st_res / st_otp[i] - SELF_TEST_PRECISION);
            if (ratio >= SELF_TEST_ACC_SHIFT_DELTA) {
                INFO_PRINT("error accel[%d] : st_res = %ld, st_otp = %ld\n", i, st_res, st_otp[i]);
                pass = false;
            }
        }
    } else {
        /* criteria b */
        for (i = 0; i < 3; i++) {
            st_res = abs(T(self_test).data_st_on[i] - T(self_test).data_st_off[i]);
            if (st_res < SELF_TEST_MIN_ACC || st_res > SELF_TEST_MAX_ACC) {
                INFO_PRINT("error accel[%d] : st_res = %ld, min = %d, max = %d\n", i, st_res, SELF_TEST_MIN_ACC, SELF_TEST_MAX_ACC);
                pass = false;
            }
        }
    }

    return pass;
}

static bool checkGyroSelftest(TASK)
{
    int32_t st_res;
    uint32_t st_otp[3];
    int i;
    bool pass = true;
    bool otp_value_zero = false;

    /* calculate ST_OTP */
    for (i = 0; i < 3; i++) {
        if (T(self_test).otp_st_data_gyro[i] != 0)
            st_otp[i] = SelfTestEquation[T(self_test).otp_st_data_gyro[i] - 1];
        else
            otp_value_zero = true;
    }

    if (!otp_value_zero) {
        /* criteria a */
        for (i = 0; i < 3; i++) {
            st_res = T(self_test).data_st_on[i] - T(self_test).data_st_off[i];
            if (st_res <= st_otp[i] * SELF_TEST_GYR_SHIFT_DELTA) {
                INFO_PRINT("error gyro[%d] : st_res = %ld, st_otp = %ld\n", i, st_res, st_otp[i]);
                pass = false;
            }
        }
    } else {
        /* criteria b */
        for (i = 0; i < 3; i++) {
            st_res = abs(T(self_test).data_st_on[i] - T(self_test).data_st_off[i]);
            if (st_res < SELF_TEST_MIN_GYR) {
                INFO_PRINT("error gyro[%d] : st_res = %ld, min = %d\n", i, st_res, SELF_TEST_MIN_GYR);
                pass = false;
            }
        }
    }

    if (pass) {
        /* criteria c */
        for (i = 0; i < 3; i++) {
            if (abs(T(self_test).data_st_off[i]) > SELF_TEST_MAX_GYR_OFFSET) {
                INFO_PRINT("error gyro[%d] = %d, max = %d\n", i, abs(T(self_test).data_st_off[i]), SELF_TEST_MAX_GYR_OFFSET);
                pass = false;
            }
        }
    }

    return pass;
}

static void selfTestHandling(TASK, enum SensorIndex idx)
{
    const uint8_t *buf, *data;
    uint8_t int_status;
    uint16_t fifo_count;
    int i, t;
    bool r;
    int16_t raw_data[3] = { 0, 0, 0 };
    bool pass;

    if (idx != ACC && idx != GYR) {
        ERROR_PRINT("Invalid sensor index\n");
        return;
    }

    switch (T(selftest_state)) {
    case TEST_START:
        T(mRetryLeft) = RETRY_CNT_SELF_TEST;
        selfTestInit(_task, idx);
        for (i = 0; i < 3; i++) {
            T(self_test).data[i] = 0;
        }
        SPI_READ(REG_INT_STATUS, 1, &T(dataBuffer[0]));
        SPI_READ(REG_FIFO_BYTE_COUNT1, 2, &T(dataBuffer[1]));
        T(selftest_state) = TEST_READ_STATUS;
        T(self_test).data_count = SELF_TEST_SAMPLE_NB;
        spiBatchTxRx(&T(mode), sensorSpiCallback, &T(sensors[idx]), __FUNCTION__);
        break;
    case TEST_READ_STATUS:
        buf = T(dataBuffer[0]);
        int_status = buf[1];
        buf = T(dataBuffer[1]);
        fifo_count = buf[2] << 8 | buf[1];
        fifo_count = fifo_count - fifo_count % T(config).fifo_sample_size;
        T(fifo_count) = fifo_count;
        DEBUG_PRINT("fifo_count = %d\n", fifo_count);
        if (int_status & BIT_INT_STATUS_FIFO_FULL) {
            ERROR_PRINT("fifo overflow\n");
            selfTestDeinit(_task, idx);
            T(selftest_state) = TEST_DONE;
            sendTestResult(SENSOR_APP_EVT_STATUS_ERROR, mSensorInfo[idx].sensorType);
        } else {
            T(selftest_state) = TEST_READ_DATA;
            SPI_READ(REG_FIFO_DATA, fifo_count, &T(dataBuffer[0]));
        }
        spiBatchTxRx(&T(mode), sensorSpiCallback, &T(sensors[idx]), __FUNCTION__);
        break;
    case TEST_READ_DATA:
        buf = T(dataBuffer[0]);
        data = &buf[1];
        for (i = 0; i < T(fifo_count); i += T(config).fifo_sample_size) {
            r = calSelftestGetOneData(idx, data, i, raw_data);
            if (r == false) {
                ERROR_PRINT("invalid data packet\n");
                selfTestDeinit(_task, idx);
                T(selftest_state) = TEST_DONE;
                sendTestResult(SENSOR_APP_EVT_STATUS_ERROR, mSensorInfo[idx].sensorType);
                break;
            }
            for (t = 0; t < 3; t++) {
                T(self_test).data[t] += raw_data[t];
            }
            T(self_test).data_count--;
            if (T(self_test).data_count == 0) {
                break;
            }
        }
        if (T(self_test).data_count > 0) {
            if (--T(mRetryLeft) == 0) {
                ERROR_PRINT("selftest timeout\n");
                selfTestDeinit(_task, idx);
                T(selftest_state) = TEST_DONE;
                sendTestResult(SENSOR_APP_EVT_STATUS_ERROR, mSensorInfo[idx].sensorType);
            } else {
                calSelftestFifoEnable(_task, idx, false, 0); // toggle FIFO to reset
                calSelftestFifoEnable(_task, idx, true, SELF_TEST_READ_INTERVAL_US);
                SPI_READ(REG_INT_STATUS, 1, &T(dataBuffer[0]));
                SPI_READ(REG_FIFO_BYTE_COUNT1, 2, &T(dataBuffer[1]));
                T(selftest_state) = TEST_READ_STATUS;
            }
            spiBatchTxRx(&T(mode), sensorSpiCallback, &T(sensors[idx]), __FUNCTION__);
            break;
        }
        T(selftest_state) = TEST_READ_OTP;
        // fall-through
    case TEST_READ_OTP:
        for (i = 0; i < 3; i++) {
            T(self_test).data[i] = T(self_test).data[i] / SELF_TEST_SAMPLE_NB * SELF_TEST_PRECISION;
        }
        INFO_PRINT("st_mode=%d average (scaled) : %ld, %ld, %ld\n",
                    T(self_test).st_mode,
                    T(self_test).data[0],
                    T(self_test).data[1],
                    T(self_test).data[2]);

        selfTestDeinit(_task, idx);

        for (i = 0; i < 3; i++) {
            if (T(self_test).st_mode) {
                T(self_test).data_st_on[i] = T(self_test).data[i];
            } else {
                T(self_test).data_st_off[i] = T(self_test).data[i];
            }
        }

        // Run again with self-test mode on
        if (!T(self_test).st_mode) {
            T(self_test).st_mode = true;
            T(selftest_state) = TEST_START;
        } else {
            INFO_PRINT("st mode on  : %ld %ld %ld\n",
                    T(self_test).data_st_on[0],
                    T(self_test).data_st_on[1],
                    T(self_test).data_st_on[2]);
            INFO_PRINT("st mode off : %ld %ld %ld\n",
                    T(self_test).data_st_off[0],
                    T(self_test).data_st_off[1],
                    T(self_test).data_st_off[2]);

            // read OTP
            if (idx == ACC) {
                SPI_WRITE(REG_REG_BANK_SEL, BIT_BANK_SEL_2);
                SPI_READ(REG_XA_ST_DATA, 3, &T(dataBuffer[0]));
            } else if (idx == GYR) {
                SPI_WRITE(REG_REG_BANK_SEL, BIT_BANK_SEL_1);
                SPI_READ(REG_XG_ST_DATA, 3, &T(dataBuffer[0]));
            }
            SPI_WRITE(REG_REG_BANK_SEL, BIT_BANK_SEL_0);

            T(selftest_state) = TEST_REPORT;
        }
        spiBatchTxRx(&T(mode), sensorSpiCallback, &T(sensors[idx]), __FUNCTION__);
        break;
    case TEST_REPORT:
        buf = T(dataBuffer[0]);
        if (idx == ACC) {
            T(self_test).otp_st_data_accel[0] = buf[1];
            T(self_test).otp_st_data_accel[1] = buf[2];
            T(self_test).otp_st_data_accel[2] = buf[3];
            INFO_PRINT("otp accel : %d %d %d\n",
                    T(self_test).otp_st_data_accel[0],
                    T(self_test).otp_st_data_accel[1],
                    T(self_test).otp_st_data_accel[2]);
        } else if (idx == GYR) {
            T(self_test).otp_st_data_gyro[0] = buf[1];
            T(self_test).otp_st_data_gyro[1] = buf[2];
            T(self_test).otp_st_data_gyro[2] = buf[3];
            INFO_PRINT("otp gyro  : %d %d %d\n",
                    T(self_test).otp_st_data_gyro[0],
                    T(self_test).otp_st_data_gyro[1],
                    T(self_test).otp_st_data_gyro[2]);
        }

        pass = false;
        if (idx == ACC) {
            pass = checkAccelSelftest(_task);
        } else if (idx == GYR) {
            pass = checkGyroSelftest(_task);
        }
        if (pass) {
            sendTestResult(SENSOR_APP_EVT_STATUS_SUCCESS,
                                  mSensorInfo[idx].sensorType);
        } else {
            sendTestResult(SENSOR_APP_EVT_STATUS_ERROR,
                                  mSensorInfo[idx].sensorType);
        }

        T(selftest_state) = TEST_DONE;
        selfTestDeinit(_task, idx);
        spiBatchTxRx(&T(mode), sensorSpiCallback, &T(sensors[idx]), __FUNCTION__);
        break;
    default:
        break;
    }
}

static bool accSelfTest(void *cookie)
{
    TDECL();

    INFO_PRINT("Accel Selftest\n");

   if (!T(powered) && trySwitchState(SENSOR_TESTING)) {
        T(self_test).st_mode = false;
        T(selftest_state) = TEST_START;
        selfTestHandling(_task, ACC);
        return true;
    } else {
        ERROR_PRINT("cannot test accel because sensor is busy\n");
        sendTestResult(SENSOR_APP_EVT_STATUS_BUSY, SENS_TYPE_ACCEL);
        return false;
    }
}

static bool gyrSelfTest(void *cookie)
{
    TDECL();

    INFO_PRINT("Gyro Selftest\n");

    if (!T(powered) && trySwitchState(SENSOR_TESTING)) {
        T(self_test).st_mode = false;
        T(selftest_state) = TEST_START;
        selfTestHandling(_task, GYR);
        return true;
    } else {
        ERROR_PRINT("cannot test accel because sensor is busy\n");
        sendTestResult(SENSOR_APP_EVT_STATUS_BUSY, SENS_TYPE_GYRO);
        return false;
    }
}

static bool sensorFirmwareUpload(void *cookie)
{
    TDECL();
    int i = (long int)cookie;

    sensorSignalInternalEvt(T(sensors[i]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}

static bool enableInterrupt(struct Gpio *pin, IRQn_Type irq, struct ChainedIsr *isr)
{
    gpioConfigInput(pin, GPIO_SPEED_LOW, GPIO_PULL_NONE);
    syscfgSetExtiPort(pin);
    extiEnableIntGpio(pin, EXTI_TRIGGER_RISING);
    extiChainIsr(irq, isr);
    return true;
}

static bool disableInterrupt(struct Gpio *pin, IRQn_Type irq, struct ChainedIsr *isr)
{
    extiUnchainIsr(irq, isr);
    extiDisableIntGpio(pin);
    return true;
}

static void configInt1(TASK, bool on)
{
    enum SensorIndex i;
    bool powered = false;

    if (on && !T(Int1_EN)) {
        enableInterrupt(T(Int1), T(Irq1), &T(Isr1));
        T(Int1_EN) = true;
    } else if (!on && T(Int1_EN)) {
        for (i = 0; i < NUM_OF_SENSOR; i++) {
            if (T(sensors[i]).powered) {
                powered = true;
                break;
            }
        }
        if (!powered) {
            disableInterrupt(T(Int1), T(Irq1), &T(Isr1));
            T(Int1_EN) = false;
        }
    }
}

static uint8_t computeOdrConf(uint32_t rate)
{
    switch (rate) {
    case SENSOR_HZ(1000.0f):
        return 6;
    case SENSOR_HZ(200.0f):
        return 7;
    case SENSOR_HZ(100.0f):
        return 8;
    case SENSOR_HZ(50.0f):
        return 9;
    case SENSOR_HZ(25.0f):
    default:
        return 10;
    }
}

static void computeConfig(TASK, struct ICM40600Config *config)
{
    uint64_t latency;
    uint64_t val;
    uint32_t latency_ms;
    int i;

    // copy current parameters
    *config = T(config);

    // compute sensors on
    if (T(sensors[ACC]).configed || T(sensors[WOM]).configed || T(sensors[NOMO]).configed) {
        config->accel_on = true;
    } else {
        config->accel_on = false;
    }
    config->gyro_on = T(sensors[GYR]).configed;
    if (T(sensors[WOM]).configed || T(sensors[NOMO]).configed) {
        config->wom_on = true;
    } else {
        config->wom_on = false;
    }

    // compute accel and gyro rates
    if (config->accel_on) {
        if (T(sensors[ACC]).configed) {
            if (T(sensors[ACC]).rate > NO_DECIMATION_MAX_RATE) {
                config->accel_rate = DECIMATION_HIGH_RATE;
            } else if (T(sensors[ACC]).rate < NO_DECIMATION_MIN_RATE) {
                config->accel_rate = DECIMATION_LOW_RATE;
            } else {
                config->accel_rate = T(sensors[ACC]).rate;
            }
        } else {
            config->accel_rate = NO_DECIMATION_MIN_RATE;
        }
    }
    if (config->gyro_on) {
        if (T(sensors[GYR]).configed) {
            if (T(sensors[GYR]).rate > NO_DECIMATION_MAX_RATE) {
                config->gyro_rate = DECIMATION_HIGH_RATE;
            } else if (T(sensors[GYR]).rate < NO_DECIMATION_MIN_RATE) {
                config->gyro_rate = DECIMATION_LOW_RATE;
            } else {
                config->gyro_rate = T(sensors[GYR]).rate;
            }
        } else {
            config->gyro_rate = NO_DECIMATION_MIN_RATE;
        }
    }

    // compute wom threshold
    if (config->wom_on) {
        config->wom_threshold = ICM40600_WOM_THRESHOLD_MG / (config->accel_rate / NO_DECIMATION_MIN_RATE);
    }

    // compute fifo configuration
    if (T(sensors[ACC]).configed || T(sensors[GYR]).configed) {
        config->fifo_sample_size = FIFO_PACKET_SIZE;
    } else {
        config->fifo_sample_size = 0;
    }

    // compute fifo rate and latency/watermark
    config->fifo_rate = 0;
    latency = SENSOR_LATENCY_NODATA;
    for (i = FIRST_CONT_SENSOR; i < NUM_CONT_SENSOR; ++i) {
        if (T(sensors[i]).configed) {
            // look for the highest rate
            if (T(sensors[i]).rate > config->fifo_rate) {
                config->fifo_rate = T(sensors[i]).rate;
            }
            // look for the shortest latency
            if (T(sensors[i]).latency < latency) {
                latency = T(sensors[i]).latency;
            }
        }
    }
    if (config->fifo_rate > NO_DECIMATION_MAX_RATE) {
        config->fifo_rate = DECIMATION_HIGH_RATE;
    } else if (config->fifo_rate < NO_DECIMATION_MIN_RATE) {
        config->fifo_rate = DECIMATION_LOW_RATE;
    }

    // add 0.5ms for rounding
    latency_ms = cpuMathU64DivByU16(cpuMathU64DivByU16(latency + 500000, 1000), 1000);
    val = (uint64_t)latency_ms * (uint64_t)config->fifo_rate;
    config->fifo_watermark = cpuMathU64DivByU16(cpuMathU64DivByU16(val, 1000), RATE_TO_HZ);
    config->fifo_watermark *= config->fifo_sample_size;
    if (config->fifo_watermark < config->fifo_sample_size) {
        config->fifo_watermark = config->fifo_sample_size;
    }
    if (config->fifo_watermark > MAX_BATCH_SIZE) {
        config->fifo_watermark = MAX_BATCH_SIZE - (MAX_BATCH_SIZE % config->fifo_sample_size);
    }
}

static void updateConfig(TASK, const struct ICM40600Config *config)
{
    uint32_t delay_us;
    uint8_t val, val2;

    // Disable Interrupt
    SPI_WRITE(REG_INT_SOURCE0, 0);
    SPI_WRITE(REG_INT_SOURCE1, 0);

    // set rate and WoM threshold
    if (config->gyro_rate != T(config).gyro_rate) {
        val = computeOdrConf(config->gyro_rate);
        SPI_WRITE(REG_GYRO_CONFIG0, (ICM40600_GYR_FS << SHIFT_GYRO_FS_SEL) |
                                    (val << SHIFT_ODR_CONF));
    }
    if (config->accel_rate != T(config).accel_rate) {
        val = computeOdrConf(config->accel_rate);
        SPI_WRITE(REG_ACCEL_CONFIG0, (ICM40600_ACC_FS << SHIFT_ACCEL_FS_SEL) |
                                     (val << SHIFT_ODR_CONF));
    }
    if (config->wom_threshold != T(config).wom_threshold) {
        val = ICM40600_WOM_COMPUTE(config->wom_threshold);
        SPI_WRITE(REG_ACCEL_WOM_X_THR, val);
        SPI_WRITE(REG_ACCEL_WOM_Y_THR, val);
        SPI_WRITE(REG_ACCEL_WOM_Z_THR, val);
    }

    // set WM
    if (config->fifo_watermark != T(config).fifo_watermark) {
        SPI_WRITE(REG_FIFO_CONFIG2, config->fifo_watermark & 0xFF);
        SPI_WRITE(REG_FIFO_CONFIG3, (config->fifo_watermark >> 8) & 0xFF);
    }

    // turn on/off accel and gyro if any change
    if (config->gyro_on != T(config).gyro_on || config->accel_on != T(config).accel_on) {
        val = 0;
        if (config->gyro_on) {
            val |= BIT_GYRO_MODE_LN;
        }
        if (config->accel_on) {
            val |= BIT_ACCEL_MODE_LN;
        }
        // delay needed if gyro or accel turning on
        if ((config->gyro_on && !T(config).gyro_on) || (config->accel_on && !T(config).accel_on)) {
            delay_us = 200; // 9136
        } else {
            delay_us = 0;
        }
        SPI_WRITE(REG_PWR_MGMT_0, val, delay_us);
    }

    // turn on/off WOM
    if (config->wom_on != T(config).wom_on) {
        if (config->wom_on) {
            val = BIT_WOM_INT_MODE_OR | BIT_WOM_MODE_PREV | BIT_SMD_MODE_OLD;
        } else {
            val = 0;
        }
        SPI_WRITE(REG_SMD_CONFIG, val);
    }

    // turn on/off FIFO
    if (config->fifo_sample_size != T(config).fifo_sample_size) {
        if (config->fifo_sample_size != 0) {
            // enabling FIFO
            val = BIT_FIFO_MODE_STREAM;
            val2 = BIT_FIFO_ACCEL_EN | BIT_FIFO_GYRO_EN | BIT_FIFO_TEMP_EN |
                    BIT_FIFO_TMST_FSYNC_EN | BIT_FIFO_WM_TH;
            // reset chip time
            T(chip_time_us) = 0;
            T(chip_timestamp) = 0;
            T(fifo_start_sync) = true;
            invalidate_sensortime_to_rtc_time(_task);
        } else {
            // disabling FIFO
            val = BIT_FIFO_MODE_BYPASS;
            val2 = 0;
        }
        SPI_WRITE(REG_FIFO_CONFIG, val);
        SPI_WRITE(REG_FIFO_CONFIG1, val2);
        if (val == BIT_FIFO_MODE_BYPASS) {
            SPI_READ(REG_FIFO_BYTE_COUNT1, 2, &T(dataBuffer[0])); // 9052
        }
    }

    // enable/disable FIFO data interrupt
    if (config->fifo_sample_size != 0) {
        val = BIT_INT_FIFO_THS_INT1_EN;
    } else {
        val = 0;
    }
    SPI_WRITE(REG_INT_SOURCE0, val);

    // enable/disable WOM interrupt (only when FIFO disabled or batch mode)
    if (config->wom_on && (config->fifo_sample_size == 0 || config->fifo_watermark > config->fifo_sample_size)) {
        val = BIT_INT_WOM_XYZ_INT1_EN;
    } else {
        val = 0;
    }
    SPI_WRITE(REG_INT_SOURCE1, val);
}

static void applyConfig(TASK, const struct ICM40600Config *config)
{
    uint32_t duration_us;
    uint32_t decimator;
    int i;

    // setup wait for odr change
    if (config->accel_rate != T(config).accel_rate) {
        T(sensors[ACC]).wait_for_odr = true;
    }
    if (config->gyro_rate != T(config).gyro_rate) {
        T(sensors[GYR]).wait_for_odr = true;
    }

    // setup first samples skip
    if (config->gyro_on && !T(config).gyro_on) {
        // gyro turning on
        duration_us = (1000000 * RATE_TO_HZ) / config->gyro_rate;
        if (duration_us * GYR_SKIP_SAMPLE_NB > ICM40600_GYRO_START_TIME_MS * 1000) {
            T(sensors[GYR]).skip_sample_cnt = GYR_SKIP_SAMPLE_NB;
        } else {
            T(sensors[GYR]).skip_sample_cnt = (ICM40600_GYRO_START_TIME_MS * 1000) / duration_us;
            if ((ICM40600_GYRO_START_TIME_MS * 1000) % duration_us) {
                T(sensors[GYR]).skip_sample_cnt++;
            }
        }
    }
    if (config->accel_on && !T(config).accel_on) {
        // accel turning on
        duration_us = (1000000 * RATE_TO_HZ) / config->accel_rate;
        if (duration_us * ACC_SKIP_SAMPLE_NB > ICM40600_ACCEL_START_TIME_MS * 1000) {
            T(sensors[ACC]).skip_sample_cnt = ACC_SKIP_SAMPLE_NB;
        } else {
            T(sensors[ACC]).skip_sample_cnt = (ICM40600_ACCEL_START_TIME_MS * 1000) / duration_us;
            if ((ICM40600_ACCEL_START_TIME_MS * 1000) % duration_us) {
                T(sensors[ACC]).skip_sample_cnt++;
            }
        }
    }

    // update all sensors decimators
    for (i = 0; i <= GYR; i++) {
        if (!T(sensors[i]).configed || T(sensors[i]).rate == 0) {
            decimator = 1;
        } else {
            if (i == ACC) {
                decimator = config->accel_rate / T(sensors[i]).rate;
            } else if (i == GYR) {
                decimator = config->gyro_rate / T(sensors[i]).rate;
            }
        }
        if (decimator != T(sensors[i]).decimator) {
            T(sensors[i]).decimator = decimator;
            T(sensors[i]).data_cnt = 0;
        }
    }

    // setup NOMO timer
    if (T(sensors[NOMO]).configed && !T(noMotionTimerHandle)) {
        T(noMotionTimerHandle) = timTimerSet(ICM40600_NOM_DURATION_NS, 0, 100, noMotionCallback, &T(sensors[NOMO]), false);
    } else if (!T(sensors[NOMO]).configed && T(noMotionTimerHandle)) {
        timTimerCancel(T(noMotionTimerHandle));
        T(noMotionTimerHandle) = 0;
    }

    // update config
    T(config) = *config;

    DEBUG_PRINT("config: accel(%d, %luHz/%u), gyro(%d, %luHz/%u), wom(%d, %lu), "
            "fifo(%u/%u, %luHz)\n",
            T(config).accel_on, T(config).accel_rate / RATE_TO_HZ, T(sensors[ACC]).decimator,
            T(config).gyro_on, T(config).gyro_rate / RATE_TO_HZ, T(sensors[GYR]).decimator,
            T(config).wom_on, T(config).wom_threshold,
            T(config).fifo_sample_size, T(config).fifo_watermark, T(config).fifo_rate / RATE_TO_HZ);
}

static void configSensor(TASK)
{
    struct ICM40600Config config;

    computeConfig(_task, &config);
    updateConfig(_task, &config);
    applyConfig(_task, &config);
}

static void sensorTurnOn(TASK)
{
    /* enable chip timestamp */
    SPI_WRITE(REG_TMST_CONFIG, BIT_EN_DREG_FIFO_D2A | BIT_TMST_EN);

    T(powered) = true;
#if DBG_TIMESTAMP
    memset(&T(statistics_set), 0, sizeof(struct StatisticsSet));
#endif
    DEBUG_PRINT("chip on\n");
}

static void sensorTurnOff(TASK)
{
    /* disable chip timestamp */
    SPI_WRITE(REG_TMST_CONFIG, BIT_EN_DREG_FIFO_D2A);

    T(powered) = false;
#if DBG_TIMESTAMP
    DEBUG_PRINT("time sync stats: reset: %ld, sync: %ld, adjust+: %ld, adjust-: %ld, truncate: %ld\n",
            T(statistics_set).sync_reset_count,
            T(statistics_set).sync_count,
            T(statistics_set).sync_adjust_plus,
            T(statistics_set).sync_adjust_minus,
            T(statistics_set).sync_truncate);
#endif
    DEBUG_PRINT("chip off\n");
}

static void sensorPower(TASK, int sensorType, bool on)
{
    bool chip_on;
    int i;

    if (on) {
        // turn on chip if needed
        if (!T(powered)) {
            sensorTurnOn(_task);
        }
    } else {
        // change chip configuration
        configSensor(_task);
        // turn chip off if needed
        chip_on = false;
        for (i = 0; i < NUM_OF_SENSOR; ++i) {
            if (T(sensors[i]).powered) {
                chip_on = true;
                break;
            }
        }
        if (!chip_on) {
            sensorTurnOff(_task);
        }
    }
}

static bool sensorSetPower(bool on, void *cookie)
{
    TDECL();
    int sensor_type = (int)cookie;
    struct ICM40600Sensor *sensor = &T(sensors[sensor_type]);

    DEBUG_PRINT("%s: on=%d, state=%" PRI_STATE "\n", mSensorInfo[sensor_type].sensorName, on, getStateName(GET_STATE()));

    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        sensor->powered = on;
        if (!on) {
            sensor->configed = false;
        }
        configInt1(_task, on);
        sensorPower(_task, sensor_type, on);
        spiBatchTxRx(&T(mode), sensorSpiCallback, sensor, __FUNCTION__);
    } else {
        T(pending_config[sensor_type]) = true;
        sensor->pConfig.enable = on;
    }

    return true;
}

static bool sensorSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();
    int sensor_type = (int)cookie;
    struct ICM40600Sensor *sensor = &T(sensors[sensor_type]);

    DEBUG_PRINT("%s: rate=%lu, latency=%llu, state=%" PRI_STATE "\n",
            mSensorInfo[sensor_type].sensorName, rate, latency, getStateName(GET_STATE()));

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        sensor->rate = rate;
        sensor->latency = latency;
        sensor->configed = true;
        configSensor(_task);
        spiBatchTxRx(&T(mode), sensorSpiCallback, sensor, __FUNCTION__);
    } else {
        T(pending_config[sensor_type]) = true;
        sensor->pConfig.enable = sensor->powered;
        sensor->pConfig.rate = rate;
        sensor->pConfig.latency = latency;
    }

    return true;
}

static void sendFlushEvt(void)
{
    TDECL();
    uint32_t evtType = 0;
    int i;

    for (i = FIRST_CONT_SENSOR; i < NUM_CONT_SENSOR; i++) {
        while (T(sensors[i]).flush > 0) {
            evtType = sensorGetMyEventType(mSensorInfo[i].sensorType);
            osEnqueueEvt(evtType, SENSOR_DATA_EVENT_FLUSH, NULL);
            T(sensors[i]).flush--;
        }
    }
}

static void int1Evt(TASK, bool flush);

static bool flushSensor(void *cookie)
{
    TDECL();
    int sensor_idx = (int)cookie;
    uint32_t evtType;

    DEBUG_PRINT("%s flush\n", mSensorInfo[sensor_idx].sensorName);

    if (sensor_idx >= FIRST_CONT_SENSOR && sensor_idx < NUM_CONT_SENSOR) {
        T(sensors[sensor_idx]).flush++;
        int1Evt(_task, true);
        return true;
    }
    if (sensor_idx >= FIRST_ONESHOT_SENSOR && sensor_idx < NUM_OF_SENSOR) {
        evtType = sensorGetMyEventType(mSensorInfo[sensor_idx].sensorType);
        osEnqueueEvt(evtType, SENSOR_DATA_EVENT_FLUSH, NULL);
        return true;
    }

    return false;
}

static bool flushData(struct ICM40600Sensor *sensor, uint32_t eventId)
{
    bool success = false;

    if (sensor->data_evt) {
        success = osEnqueueEvtOrFree(eventId, sensor->data_evt, dataEvtFree);
        sensor->data_evt = NULL;
    }

    return success;
}

static void flushAllData(void)
{
    TDECL();
    int i;

    for (i = FIRST_CONT_SENSOR; i < NUM_CONT_SENSOR; i++) {
        flushData(&T(sensors[i]),
                EVENT_TYPE_BIT_DISCARDABLE | sensorGetMyEventType(mSensorInfo[i].sensorType));
    }
}

static bool allocateDataEvt(struct ICM40600Sensor *mSensor, uint64_t rtc_time)
{
    TDECL();

    mSensor->data_evt = slabAllocatorAlloc(T(mDataSlab));
    if (mSensor->data_evt == NULL) {
        // slab allocation failed
        ERROR_PRINT("slabAllocatorAlloc() failed\n");
        return false;
    }

    // delta time for the first sample is sample count
    memset(&mSensor->data_evt->samples[0].firstSample, 0x00, sizeof(struct SensorFirstSample));
    mSensor->data_evt->referenceTime = rtc_time;
    mSensor->prev_rtc_time = rtc_time;

    return true;
}

static void parseOnePacket(TASK, const uint8_t *data, int idx)
{
    struct FifoPacketData fifo_data;
    uint16_t diff;

    getFifoData(data, idx, &fifo_data);

    /* wait for odr */
    if (T(sensors[ACC]).wait_for_odr) {
        if (fifo_data.odr_accel) {
            T(sensors[ACC]).wait_for_odr = false;
        } else {
            fifo_data.valid_accel = false;
        }
    }
    if (T(sensors[GYR]).wait_for_odr) {
        if (fifo_data.odr_gyro) {
            T(sensors[GYR]).wait_for_odr = false;
        } else {
            fifo_data.valid_gyro = false;
        }
    }

    /* drop first some samples */
    if (fifo_data.valid_accel) {
        if (T(sensors[ACC]).skip_sample_cnt > 0) {
            fifo_data.valid_accel = false;
            T(sensors[ACC]).skip_sample_cnt--;
        }
    }
    if (fifo_data.valid_gyro) {
        if (T(sensors[GYR]).skip_sample_cnt > 0) {
            fifo_data.valid_gyro = false;
            T(sensors[GYR]).skip_sample_cnt--;
        }
    }

    /* update sensors data */
    if (fifo_data.valid_accel) {
        ICM40600_TO_ANDROID_COORDINATE(fifo_data.accel[0], fifo_data.accel[1], fifo_data.accel[2]);
        if (T(sensors[ACC]).configed) {
            T(sensors[ACC]).data[0] = fifo_data.accel[0];
            T(sensors[ACC]).data[1] = fifo_data.accel[1];
            T(sensors[ACC]).data[2] = fifo_data.accel[2];
            T(sensors[ACC]).updated = true;
            T(sensors[ACC]).data_cnt++;
        }
    }
    if (fifo_data.valid_gyro) {
        ICM40600_TO_ANDROID_COORDINATE(fifo_data.gyro[0], fifo_data.gyro[1], fifo_data.gyro[2]);
        if (T(sensors[GYR]).configed) {
            T(sensors[GYR]).data[0] = fifo_data.gyro[0];
            T(sensors[GYR]).data[1] = fifo_data.gyro[1];
            T(sensors[GYR]).data[2] = fifo_data.gyro[2];
            T(sensors[GYR]).updated = true;
            T(sensors[GYR]).data_cnt++;
        }
    }

    // update temperature
    T(chip_temperature) = fifo_data.temp * TEMP_SCALE + TEMP_OFFSET;

    // count up chip time
    if (T(chip_time_us) == 0) {
        T(chip_time_us) = (uint64_t)fifo_data.timestamp * CHIP_TIME_RES_US + CHIP_TIME_OFFSET_US;
    } else {
        // unsigned difference handle counter roll-up
        diff = fifo_data.timestamp - T(chip_timestamp);
        T(chip_time_us) += diff * CHIP_TIME_RES_US;
    }
    T(chip_timestamp) = fifo_data.timestamp;
}

static void pushSensorData(TASK, struct ICM40600Sensor *mSensor, uint64_t rtc_time)
{
    float x, y, z;
    float offset[3];
    bool new_offset_update = false;
    struct TripleAxisDataPoint *sample;
    uint32_t delta_time;

    switch (mSensor->idx) {
    case ACC:
        // scale data to android units
        x = mSensor->data[0] * kScale_acc;
        y = mSensor->data[1] * kScale_acc;
        z = mSensor->data[2] * kScale_acc;
        // run and apply calibration on sensor data
#ifdef ACCEL_CAL_ENABLED
        accelCalRun(&T(accel_cal), rtc_time, x, y, z, T(chip_temperature));
        accelCalBiasRemove(&T(accel_cal), &x, &y, &z);
#  ifdef ACCEL_CAL_DBG_ENABLED
        accelCalDebPrint(&T(accel_cal), T(chip_temperature));
#  endif
#endif
#ifdef GYRO_CAL_ENABLED
        gyroCalUpdateAccel(&T(gyro_cal), rtc_time, x, y, z);
#endif
        break;
    case GYR:
        // scale data to android units
        x = mSensor->data[0] * kScale_gyr;
        y = mSensor->data[1] * kScale_gyr;
        z = mSensor->data[2] * kScale_gyr;
        // run and apply calibration on sensor data
#ifdef GYRO_CAL_ENABLED
        gyroCalUpdateGyro(&T(gyro_cal), rtc_time, x, y, z, T(chip_temperature));
        gyroCalRemoveBias(&T(gyro_cal), x, y, z, &x, &y, &z);
        new_offset_update = gyroCalNewBiasAvailable(&T(gyro_cal));
        if (new_offset_update) {
            float gyro_offset_temperature_celsius;
            gyroCalGetBias(&T(gyro_cal), &offset[0], &offset[1], &offset[2],
                    &gyro_offset_temperature_celsius);
        }
#endif
        break;
    default:
        return;
    }

    if (mSensor->data_evt == NULL) {
        if (!allocateDataEvt(mSensor, rtc_time))
            return;
    }

    if (mSensor->data_evt->samples[0].firstSample.numSamples >= MAX_NUM_COMMS_EVENT_SAMPLES) {
        ERROR_PRINT("samples bad index\n");
        return;
    }

    // handle bias sending
    if (new_offset_update) {
        if (mSensor->data_evt->samples[0].firstSample.numSamples > 0) {
            // flush existing samples so the bias appears after them.
            flushData(mSensor, EVENT_TYPE_BIT_DISCARDABLE | sensorGetMyEventType(mSensorInfo[mSensor->idx].sensorType));
            if (!allocateDataEvt(mSensor, rtc_time)) {
                return;
            }
        }
        mSensor->data_evt->samples[0].firstSample.biasCurrent = true;
        mSensor->data_evt->samples[0].firstSample.biasPresent = 1;
        mSensor->data_evt->samples[0].firstSample.biasSample = mSensor->data_evt->samples[0].firstSample.numSamples;
        sample = &mSensor->data_evt->samples[mSensor->data_evt->samples[0].firstSample.numSamples++];
        // Updates the offset in HAL.
        sample->x = offset[0];
        sample->y = offset[1];
        sample->z = offset[2];
        flushData(mSensor, sensorGetMyEventType(mSensorInfo[mSensor->idx].biasType));
        DEBUG_PRINT("send new gyro bias\n");
        if (!allocateDataEvt(mSensor, rtc_time)) {
            return;
        }
    }

    sample = &mSensor->data_evt->samples[mSensor->data_evt->samples[0].firstSample.numSamples++];

    // the first deltatime is for sample size
    if (mSensor->data_evt->samples[0].firstSample.numSamples > 1) {
        delta_time = rtc_time - mSensor->prev_rtc_time;
        delta_time = delta_time < 0 ? 0 : delta_time;
        sample->deltaTime = delta_time;
        mSensor->prev_rtc_time = rtc_time;
    }

    sample->x = x;
    sample->y = y;
    sample->z = z;

    if (mSensor->data_evt->samples[0].firstSample.numSamples == MAX_NUM_COMMS_EVENT_SAMPLES) {
        flushAllData();
    }
}

static void computeTimeSync(TASK, const uint8_t *data, uint32_t count, uint64_t data_timestamp)
{
    const uint64_t now = sensorGetTime();
    uint64_t chip_time_us;
    uint32_t sample_index;
    uint32_t duration_us;
    struct FifoPacketData packet;
    uint16_t sample_nb;
    uint16_t chip_timestamp;
    uint16_t diff;
    bool first_sync = false;
    bool sync = false;

    /* scan fifo data for the latest chip timestamp */
    chip_time_us = T(chip_time_us);
    chip_timestamp = T(chip_timestamp);
    sample_index = 0;
    sample_nb = 0;
    while (count >= T(config).fifo_sample_size) {
        sample_nb++;
        getFifoData(data, sample_index, &packet);
        sample_index += T(config).fifo_sample_size;
        count -= T(config).fifo_sample_size;
        // count up chip time
        if (chip_time_us == 0) {
            // first chip timestamp
            chip_time_us = (uint64_t)packet.timestamp * CHIP_TIME_RES_US + CHIP_TIME_OFFSET_US;
        } else {
            // unsigned difference handle counter roll-up
            diff = packet.timestamp - chip_timestamp;
            chip_time_us += diff * CHIP_TIME_RES_US;
        }
        chip_timestamp = packet.timestamp;
    }

    /* first time sync after FIFO enable */
    if (T(fifo_start_sync)) {
        if (T(config).fifo_rate != 0) {
            duration_us = (1000000 * RATE_TO_HZ) / T(config).fifo_rate;
        } else {
            duration_us = 0;
        }
        // use estimated duration
        map_sensortime_to_rtc_time(_task,
                chip_time_us - duration_us * sample_nb,
                data_timestamp - (uint64_t)duration_us * 1000ULL * sample_nb);
        T(fifo_start_sync) = false;
        T(last_sync_time) = now;
        first_sync = true;
    }

    /* periodical time sync */
    if ((now - T(last_sync_time)) > MSEC_TO_NANOS(100)) {
        // every 100ms
        uint64_t estimated_rtc;
        uint64_t min_rtc, max_rtc;
        uint64_t limit_ns, adjust_ns;
        uint64_t updated_data_timestamp = data_timestamp;
        bool valid = sensortime_to_rtc_time(_task, chip_time_us, &estimated_rtc);
        // check if appropriate to inject to time sync algorithm
        // adjust rtc time if necessary not to make large jitters
        if (valid) {
            limit_ns = U64_DIV_BY_CONST_U16((data_timestamp - T(last_sync_data_ts)) * 10, 1000);  // 1% (100ms * 1% = 1ms)
            adjust_ns = U64_DIV_BY_CONST_U16((data_timestamp - T(last_sync_data_ts)) * 1, 10000); // 0.01% (100ms * 0.01% = 10us)
            min_rtc = data_timestamp - limit_ns; // actual ts - x
            max_rtc = data_timestamp + limit_ns; // actual ts + x
            if ((estimated_rtc >= min_rtc) && (estimated_rtc <= max_rtc)) {
                sync = true;
            } else if (estimated_rtc < min_rtc) {
                sync = true;
                updated_data_timestamp = updated_data_timestamp - adjust_ns;
#if DBG_TIMESTAMP
                T(statistics_set).sync_adjust_minus++;
#endif
            } else if (estimated_rtc > max_rtc) {
                sync = true;
                updated_data_timestamp = updated_data_timestamp + adjust_ns;
#if DBG_TIMESTAMP
                T(statistics_set).sync_adjust_plus++;
#endif
            }
            // limit the adjustment
            if (sync) {
                if (updated_data_timestamp > (data_timestamp + MSEC_TO_NANOS(1))) {
                    updated_data_timestamp = data_timestamp + MSEC_TO_NANOS(1);
                }
            }
        }
        if (sync) {
            data_timestamp = updated_data_timestamp;
        }
    }

    /* do time sync */
    if (first_sync || sync) {
        map_sensortime_to_rtc_time(_task, chip_time_us, data_timestamp);
        T(last_sync_time) = now;
        T(last_sync_data_ts) = data_timestamp;
    }
}

static void dispatchData(TASK, const uint8_t *data, uint32_t count)
{
    uint64_t ts = 0;
    uint32_t sample_index = 0;
    int i;

    if (count == 0) {
        return;
    }

    /* do time sync if no flush or fist time starting FIFO */
    if (T(fifo_start_sync) || !T(flush)) {
        computeTimeSync(_task, data, count, T(data_timestamp));
    }

    /* process FIFO data */
    while (count >= T(config).fifo_sample_size) {
        for (i = FIRST_CONT_SENSOR; i < NUM_CONT_SENSOR; ++i) {
            T(sensors[i]).updated = false;
        }
        parseOnePacket(_task, data, sample_index);
        sample_index += T(config).fifo_sample_size;
        count -= T(config).fifo_sample_size;
        // compute timestamp
        if (!sensortime_to_rtc_time(_task, T(chip_time_us), &ts)) {
            continue;
        }
        // add data
        for (i = FIRST_CONT_SENSOR; i < NUM_CONT_SENSOR; ++i) {
            if (!T(sensors[i]).configed || !T(sensors[i]).updated) {
                continue;
            }
            if (T(sensors[i]).data_cnt % T(sensors[i]).decimator == 0) {
                if (ts <= T(sensors[i]).prev_rtc_time) {
                    ts = T(sensors[i]).prev_rtc_time + MIN_INCREMENT_TIME_NS;
#if DBG_TIMESTAMP
                    T(statistics_set).sync_truncate++;
#endif
                }
                pushSensorData(_task, &T(sensors[i]), ts);
            }
        }
    }

    flushAllData();
}

static void int1Handling(TASK)
{
    uint8_t int_status;
    uint8_t int_status2;
    uint16_t fifo_count = 0;
    union EmbeddedDataPoint trigger_axies;
    const uint8_t *buf;

    switch (T(int_state)) {
    case INT_READ_STATUS:
        T(int_state) = INT_PROCESS_STATUS;
        // read INT status1, status2 and fifo_count registers
        SPI_READ(REG_INT_STATUS, 1, &T(dataBuffer[0]));
        SPI_READ(REG_INT_STATUS2, 1, &T(dataBuffer[1]));
        SPI_READ(REG_FIFO_BYTE_COUNT1, 2, &T(dataBuffer[2]));
        T(data_timestamp) = sensorGetTime();
        spiBatchTxRx(&T(mode), sensorSpiCallback, _task, __FUNCTION__);
        break;
    case INT_PROCESS_STATUS:
        buf = T(dataBuffer[0]);
        int_status = buf[1];
        buf = T(dataBuffer[1]);
        int_status2 = buf[1];
        buf = T(dataBuffer[2]);
        fifo_count = buf[2] << 8 | buf[1];
        // INT status handling
        if (int_status2 & BIT_INT_STATUS_WOM_XYZ) {
            if (T(sensors[WOM]).configed) {
                trigger_axies.idata = ((int_status2 & BIT_INT_STATUS_WOM_X) >> 0) | ((int_status2 & BIT_INT_STATUS_WOM_Y) >> 1) | ((int_status2 & BIT_INT_STATUS_WOM_Z) >> 2);
                osEnqueueEvt(EVT_SENSOR_ANY_MOTION, trigger_axies.vptr, NULL);
            }
            if (T(sensors[NOMO]).configed && T(noMotionTimerHandle)) {
                timTimerCancel(T(noMotionTimerHandle));
                T(noMotionTimerHandle) = timTimerSet(ICM40600_NOM_DURATION_NS, 0, 100, noMotionCallback, (void *)&T(sensors[NOMO]), false);
            }
        }
        // FIFO overflow
        if (int_status & BIT_INT_STATUS_FIFO_FULL) {
            ERROR_PRINT("fifo overflow\n");
        }
        // FIFO WM status handling
        if ((int_status & BIT_INT_STATUS_FIFO_THS) || T(flush)) {
            T(int_state) = INT_READ_FIFO_DATA;
        } else {
            T(int_state) = INT_DONE;
            T(fifo_count) = 0;
            sensorSpiCallback(_task, 0);
            break;
        }
        // fall-through
    case INT_READ_FIFO_DATA:
        T(int_state) = INT_DONE;
        // compute fifo count and delete partial sample
        fifo_count -= fifo_count % T(config).fifo_sample_size;
        // read FIFO data
        T(fifo_count) = fifo_count;
        if (fifo_count > 0) {
            SPI_READ(REG_FIFO_DATA, fifo_count, &T(dataBuffer[0]));
            spiBatchTxRx(&T(mode), sensorSpiCallback, _task, __FUNCTION__);
        } else {
            sensorSpiCallback(_task, 0);
        }
        break;
    default:
        T(int_state) = INT_DONE;
        T(fifo_count) = 0;
        sensorSpiCallback(_task, 0);
        break;
    }
}

static void int1Evt(TASK, bool flush)
{
    if (trySwitchState(SENSOR_INT_1_HANDLING)) {
        T(flush) = flush;
        T(int_state) = INT_READ_STATUS;
        int1Handling(_task);
    } else {
        if (flush) {
            T(pending_flush) = true;
        } else {
            T(pending_int[0]) = true;
        }
    }
}

#define DEC_OPS(power, firmware, rate, flush) \
    .sensorPower = power, \
    .sensorFirmwareUpload = firmware, \
    .sensorSetRate = rate, \
    .sensorFlush = flush

#define DEC_OPS_CAL_CFG_TEST(power, firmware, rate, flush, cal, cfg, test) \
    DEC_OPS(power, firmware, rate, flush), \
    .sensorCalibrate = cal, \
    .sensorCfgData = cfg, \
    .sensorSelfTest = test,

#define DEC_OPS_CFG(power, firmware, rate, flush, cfg) \
    DEC_OPS(power, firmware, rate, flush), \
    .sensorCfgData = cfg

static const struct SensorOps mSensorOps[NUM_OF_SENSOR] =
{
    { DEC_OPS_CAL_CFG_TEST(sensorSetPower, sensorFirmwareUpload, sensorSetRate, flushSensor, accCalibration,
            accCfgData, accSelfTest) },
    { DEC_OPS_CAL_CFG_TEST(sensorSetPower, sensorFirmwareUpload, sensorSetRate, flushSensor, gyrCalibration,
            gyrCfgData, gyrSelfTest) },
    { DEC_OPS(sensorSetPower, sensorFirmwareUpload, sensorSetRate, flushSensor) },
    { DEC_OPS(sensorSetPower, sensorFirmwareUpload, sensorSetRate, flushSensor) },
};

static void configEvent(struct ICM40600Sensor *mSensor, struct ConfigStat *ConfigData)
{
    TDECL();
    int i;

    for (i = 0; &T(sensors[i]) != mSensor; i++) ;

    if (ConfigData->enable == 0 && mSensor->powered) {
        mSensorOps[i].sensorPower(false, (void *)i);
    } else if (ConfigData->enable == 1 && !mSensor->powered) {
        mSensorOps[i].sensorPower(true, (void *)i);
    } else {
        mSensorOps[i].sensorSetRate(ConfigData->rate, ConfigData->latency, (void *)i);
    }
}

static void processPendingEvt(TASK)
{
    enum SensorIndex i;

    if (T(pending_int[0])) {
        T(pending_int[0]) = false;
        int1Evt(_task, false);
        return;
    }

    if (T(pending_flush)) {
        T(pending_flush) = false;
        int1Evt(_task, true);
        return;
    }

    for (i = 0; i < NUM_OF_SENSOR; i++) {
        if (T(pending_config[i])) {
            T(pending_config[i]) = false;
            configEvent(&T(sensors[i]), &T(sensors[i]).pConfig);
            return;
        }
    }

    if (T(pending_calibration_save)) {
        T(pending_calibration_save) = !saveCalibration(_task);
        return;
    }
}

static void sensorInit(TASK)
{
    const uint8_t *buf;

    switch (T(init_state)) {
    case RESET_ICM40600:
        // reset chip and turn on
        SPI_WRITE(REG_DEVICE_CONFIG, BIT_SOFT_RESET, 100 * 1000);
        SPI_READ(REG_INT_STATUS, 1, &T(dataBuffer[0]));
        T(powered) = false;
        sensorTurnOn(_task);
        // set SPI speed register
        SPI_WRITE(REG_SPI_SPEED, ICM40600_SPI_SPEED_REG_VALUE);
        T(init_state) = INIT_ICM40600;
        spiBatchTxRx(&T(mode), sensorSpiCallback, _task, __FUNCTION__);
        break;
    case INIT_ICM40600:
        buf = T(dataBuffer[0]);
        if (!(buf[1] & BIT_INT_STATUS_RESET_DONE)) {
            ERROR_PRINT("chip reset failed!\n");
        }
        // reconfigure SPI speed
        T(mode).speed = ICM40600_SPI_SPEED_HZ;
        // configure interface
        // i2c disabled
        // fifo_count: little endian
        // sensor data: little endian
        SPI_WRITE(REG_INTF_CONFIG0, BIT_UI_SIFS_DISABLE_I2C);
        // configure interrupt
        SPI_WRITE(REG_INT_CONFIG, (INT1_POLARITY << SHIFT_INT1_POLARITY) |
                                  (INT1_DRIVE_CIRCUIT << SHIFT_INT1_DRIVE_CIRCUIT) |
                                  (INT1_MODE << SHIFT_INT1_MODE));
        SPI_WRITE(REG_INT_CONFIG1, BIT_INT_ASY_RST_DISABLE); // 9139
        // static config of sensors
        SPI_WRITE(REG_GYRO_ACCEL_CONFIG0, ICM40600_ACC_BW_IND | ICM40600_GYR_BW_IND);
        SPI_WRITE(REG_GYRO_CONFIG1, 0x1A);  // default reset value
        SPI_WRITE(REG_ACCEL_CONFIG1, 0x15); // default reset value
        // turn off FIFO and sensors
        SPI_WRITE(REG_FIFO_CONFIG, BIT_FIFO_MODE_BYPASS);
        SPI_WRITE(REG_FIFO_CONFIG1, 0);
        SPI_WRITE(REG_FIFO_CONFIG2, 0);
        SPI_WRITE(REG_FIFO_CONFIG3, 0);
        SPI_WRITE(REG_SMD_CONFIG, BIT_SMD_MODE_OFF);
        SPI_WRITE(REG_PWR_MGMT_0, BIT_GYRO_MODE_OFF | BIT_ACCEL_MODE_OFF, 200); // 9136
        sensorTurnOff(_task);
        T(init_state) = INIT_DONE;
        spiBatchTxRx(&T(mode), sensorSpiCallback, _task, __FUNCTION__);
        break;
    default:
        break;
    }
}

static void handleSpiDoneEvt(const void* evtData)
{
    TDECL();
    struct ICM40600Sensor *mSensor;
    bool returnIdle = false;
    uint32_t i;
    const uint8_t *buf;
#ifdef ACCEL_CAL_ENABLED
    bool accelCalNewBiasAvailable;
    struct TripleAxisDataPoint *sample;
    float accelCalBiasX, accelCalBiasY, accelCalBiasZ;
    bool fallThrough;
#endif

    switch (GET_STATE()) {
    case SENSOR_BOOT:
        SET_STATE(SENSOR_VERIFY_ID);
        SPI_READ(REG_WHO_AM_I, 1, &T(dataBuffer[0]));
        spiBatchTxRx(&T(mode), sensorSpiCallback, _task, __FUNCTION__);
        break;
    case SENSOR_VERIFY_ID:
        buf = T(dataBuffer[0]);
        if (buf[1] != WHO_AM_I_ICM40604 && buf[1] != WHO_AM_I_ICM40605) {
            ERROR_PRINT("chip not found (id=0x%x)\n", buf[1]);
            break;
        }
        INFO_PRINT("chip found (id=0x%x)\n", buf[1]);
        SET_STATE(SENSOR_INITIALIZING);
        T(init_state) = RESET_ICM40600;
        sensorInit(_task);
        break;
    case SENSOR_INITIALIZING:
        if (T(init_state) == INIT_DONE) {
            for (i = 0; i < NUM_OF_SENSOR; i++) {
                sensorRegisterInitComplete(T(sensors[i]).handle);
            }
            returnIdle = true;
        } else {
            sensorInit(_task);
        }
        break;
    case SENSOR_POWERING_UP:
        mSensor = (struct ICM40600Sensor *)evtData;
        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, 1, 0);
        returnIdle = true;
        break;
    case SENSOR_POWERING_DOWN:
        mSensor = (struct ICM40600Sensor *)evtData;
#ifdef ACCEL_CAL_ENABLED
        if (mSensor->idx == ACC) {
            // https://source.android.com/devices/sensors/sensor-types.html
            // "The bias and scale calibration must only be updated while the sensor is deactivated,
            // so as to avoid causing jumps in values during streaming."
            accelCalNewBiasAvailable = accelCalUpdateBias(&T(accel_cal), &accelCalBiasX, &accelCalBiasY, &accelCalBiasZ);
            // notify HAL about new accel bias calibration
            if (accelCalNewBiasAvailable) {
                fallThrough = true;
                if (mSensor->data_evt->samples[0].firstSample.numSamples > 0) {
                    // flush existing samples so the bias appears after them
                    flushData(mSensor, EVENT_TYPE_BIT_DISCARDABLE | sensorGetMyEventType(mSensorInfo[ACC].sensorType));
                    // try to allocate another data event and break if unsuccessful
                    if (!allocateDataEvt(mSensor, sensorGetTime())) {
                        fallThrough = false;
                    }
                }
                if (fallThrough) {
                    mSensor->data_evt->samples[0].firstSample.biasCurrent = true;
                    mSensor->data_evt->samples[0].firstSample.biasPresent = 1;
                    mSensor->data_evt->samples[0].firstSample.biasSample =
                            mSensor->data_evt->samples[0].firstSample.numSamples;
                    sample = &mSensor->data_evt->samples[mSensor->data_evt->samples[0].firstSample.numSamples++];
                    sample->x = accelCalBiasX;
                    sample->y = accelCalBiasY;
                    sample->z = accelCalBiasZ;
                    flushData(mSensor, sensorGetMyEventType(mSensorInfo[ACC].biasType));
                    DEBUG_PRINT("send new accel bias\n");
                    allocateDataEvt(mSensor, sensorGetTime());
                }
            }
        }
#endif
        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, 0, 0);
        returnIdle = true;
        break;
    case SENSOR_INT_1_HANDLING:
        if (T(int_state) == INT_DONE) {
            buf = T(dataBuffer[0]);
            dispatchData(_task, &buf[1], T(fifo_count));
            if (T(flush)) {
                sendFlushEvt();
            }
            returnIdle = true;
        } else {
            int1Handling(_task);
        }
        break;
    case SENSOR_CONFIG_CHANGING:
        mSensor = (struct ICM40600Sensor *)evtData;
        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_RATE_CHG, mSensor->rate, mSensor->latency);
        returnIdle = true;
        break;
    case SENSOR_CALIBRATING:
        mSensor = (struct ICM40600Sensor *)evtData;
        if (mSensor->idx == ACC) {
            if (T(calibration_state) == CALIBRATION_DONE) {
                INFO_PRINT("Accel Calibration done\n");
                returnIdle = true;
            } else {
                calibrationHandling(_task, ACC);
            }
        } else if (mSensor->idx == GYR) {
            if (T(calibration_state) == CALIBRATION_DONE) {
                INFO_PRINT("Gyro Calibration done\n");
                returnIdle = true;
            } else {
                calibrationHandling(_task, GYR);
            }
        }
        break;
    case SENSOR_TESTING:
        mSensor = (struct ICM40600Sensor *)evtData;
        if (mSensor->idx == ACC) {
            if (T(selftest_state) == TEST_DONE) {
                INFO_PRINT("Accel Selftest done\n");
                returnIdle = true;
            } else {
                selfTestHandling(_task, ACC);
            }
        } else if (mSensor->idx == GYR) {
            if (T(selftest_state) == TEST_DONE) {
                INFO_PRINT("Gyro Selftest done\n");
                returnIdle = true;
            } else {
                selfTestHandling(_task, GYR);
            }
        }
        break;
    case SENSOR_SAVE_CALIBRATION:
        returnIdle = true;
        break;
    default:
        break;
    }

    if (returnIdle) {
        SET_STATE(SENSOR_IDLE);
        processPendingEvt(_task);
    }
}

#ifdef GYRO_CAL_ENABLED
static void processMagEvents(TASK, const struct TripleAxisDataEvent *evtData)
{
    const struct SensorFirstSample * const first = &evtData->samples[0].firstSample;
    const struct TripleAxisDataPoint *sample;
    uint64_t ts = evtData->referenceTime;
    unsigned int i;

    for (i = 0; i < first->numSamples; ++i) {
        sample = &evtData->samples[i];
        if (i > 0) {
            ts += sample->deltaTime;
        }
        gyroCalUpdateMag(&T(gyro_cal), ts, sample->x, sample->y, sample->z);
    }
}
#endif

static void handleEvent(uint32_t evtType, const void* evtData)
{
    TDECL();

    switch (evtType) {
    case EVT_APP_START:
        SET_STATE(SENSOR_BOOT);
        osEventUnsubscribe(T(tid), EVT_APP_START);
#ifdef GYRO_CAL_ENABLED
        osEventSubscribe(T(tid), EVT_SENSOR_MAG_DATA_RDY);
#endif
        // fall through
    case EVT_SPI_DONE:
        handleSpiDoneEvt(evtData);
        break;
    case EVT_SENSOR_INTERRUPT_1:
        int1Evt(_task, false);
        break;
#ifdef GYRO_CAL_ENABLED
    case EVT_SENSOR_MAG_DATA_RDY:
        if (evtData != SENSOR_DATA_EVENT_FLUSH) {
            processMagEvents(_task, (const struct TripleAxisDataEvent *)evtData);
        }
        break;
#endif
    default:
        break;
    }
}

static void initSensorStruct(struct ICM40600Sensor *sensor, enum SensorIndex idx)
{
    sensor->data_evt = NULL;
    sensor->rate = 0;
    sensor->latency = 0;
    sensor->decimator = 1;
    sensor->data_cnt = 0;
    sensor->prev_rtc_time = 0;
    sensor->skip_sample_cnt = 0;
    sensor->data[0] = 0;
    sensor->data[1] = 0;
    sensor->data[2] = 0;
    sensor->offset[0] = 0;
    sensor->offset[1] = 0;
    sensor->offset[2] = 0;
    sensor->updated = false;
    sensor->powered = false;
    sensor->configed = false;
    sensor->wait_for_odr = false;
    sensor->idx = idx;
    sensor->flush = 0;
}

static bool startTask(uint32_t task_id)
{
    TDECL();
    enum SensorIndex i;
    size_t slabSize;

    time_init(_task);

    T(tid) = task_id;

    T(Int1) = gpioRequest(ICM40600_INT1_PIN);
    T(Irq1) = ICM40600_INT1_IRQ;
    T(Isr1).func = icm40600Isr1;
    T(Int1_EN) = false;
    T(pending_int[0]) = false;
    T(pending_flush) = false;
    T(pending_calibration_save) = false;

    T(mode).speed = ICM40600_SPI_DEFAULT_SPEED_HZ;
    T(mode).bitsPerWord = 8;
    T(mode).cpol = SPI_CPOL_IDLE_HI;
    T(mode).cpha = SPI_CPHA_TRAILING_EDGE;
    T(mode).nssChange = true;
    T(mode).format = SPI_FORMAT_MSB_FIRST;
    T(cs) = GPIO_PB(12);
    T(config).accel_rate = 0;
    T(config).gyro_rate = 0;
    T(config).fifo_rate = 0;
    T(config).wom_threshold = 0;
    T(config).fifo_watermark = 0;
    T(config).fifo_sample_size = 0;
    T(config).accel_on = false;
    T(config).gyro_on = false;
    T(config).wom_on = false;
    T(noMotionTimerHandle) = 0;
    T(fifo_count) = 0;
    T(powered) = false;
    T(flush) = false;
    T(data_timestamp) = 0;
    T(chip_time_us) = 0;
    T(last_sync_time) = 0;
    T(last_sync_data_ts) = 0;
    T(chip_timestamp) = 0;
    T(fifo_start_sync) = false;
    T(chip_temperature) = TEMP_OFFSET;

    spiMasterRequest(ICM40600_SPI_BUS_ID, &T(spiDev));

    for (i = 0; i < NUM_OF_SENSOR; i++) {
        initSensorStruct(&T(sensors[i]), i);
        T(sensors[i]).handle = sensorRegister(&mSensorInfo[i], &mSensorOps[i], (void *)i, false);
        T(pending_config[i]) = false;
    }

    osEventSubscribe(T(tid), EVT_APP_START);

#ifdef ACCEL_CAL_ENABLED
    // Init Accel Cal
    accelCalInit(&T(accel_cal),
                 800000000, // Stillness Time in ns (0.8s)
                 5,         // Minimum Sample Number
                 0.00025,   // Threshold
                 15,        // nx bucket count
                 15,        // nxb bucket count
                 15,        // ny bucket count
                 15,        // nyb bucket count
                 15,        // nz bucket count
                 15,        // nzb bucket count
                 15);       // nle bucket count
#endif
#ifdef GYRO_CAL_ENABLED
    // Init Gyro Cal
    gyroCalInit(&T(gyro_cal),
                SEC_TO_NANOS(5.0f),   // Min stillness period = 5.0 seconds
                SEC_TO_NANOS(5.9f),   // Max stillness period = 6.0 seconds (NOTE 1)
                0, 0, 0,              // Initial bias offset calibration
                0,                    // Time stamp of initial bias calibration
                SEC_TO_NANOS(1.5f),   // Analysis window length = 1.5 seconds
                7.5e-5f,              // Gyroscope variance threshold [rad/sec]^2
                1.5e-5f,              // Gyroscope confidence delta [rad/sec]^2
                4.5e-3f,              // Accelerometer variance threshold [m/sec^2]^2
                9.0e-4f,              // Accelerometer confidence delta [m/sec^2]^2
                5.0f,                 // Magnetometer variance threshold [uT]^2
                1.0f,                 // Magnetometer confidence delta [uT]^2
                0.95f,                // Stillness threshold [0,1]
                40.0f * MDEG_TO_RAD,  // Stillness mean variation limit [rad/sec]
                1.5f,                 // Max temperature delta during stillness [C]
                true);                // Gyro calibration enable
    // NOTE 1: This parameter is set to 5.9 seconds to achieve a max stillness
    // period of 6.0 seconds and avoid buffer boundary conditions that could push
    // the max stillness to the next multiple of the analysis window length
    // (i.e., 7.5 seconds).
#endif

    slabSize = sizeof(struct TripleAxisDataEvent) +
               MAX_NUM_COMMS_EVENT_SAMPLES * sizeof(struct TripleAxisDataPoint);
    // TODO: need to investigate slab items number
    T(mDataSlab) = slabAllocatorNew(slabSize, 4, 20);
    if (!T(mDataSlab)) {
        ERROR_PRINT("slabAllocatorNew() failed\n");
        return false;
    }
    T(mWbufCnt) = 0;
    T(mRegCnt) = 0;
    T(spiInUse) = false;

    return true;
}

static void endTask(void)
{
    TDECL();

#ifdef ACCEL_CAL_ENABLED
    accelCalDestroy(&T(accel_cal));
#endif
    slabAllocatorDestroy(T(mDataSlab));
    spiMasterRelease(T(spiDev));

    // disable and release interrupt.
    disableInterrupt(T(Int1), T(Irq1), &T(Isr1));
    gpioRelease(T(Int1));
}

INTERNAL_APP_INIT(ICM40600_APP_ID, ICM40600_APP_VERSION, startTask, endTask, handleEvent);
