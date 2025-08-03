/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef _OSLO_IAXXX_SENSOR_CONTROL_H_
#define _OSLO_IAXXX_SENSOR_CONTROL_H_

#if __cplusplus
extern "C" {
#endif

#include <errno.h>
#include <getopt.h>
#include <inttypes.h>
#include <limits.h>
#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/param.h>
#include <time.h>
#include <unistd.h>

#include <linux/mfd/adnc/iaxxx-module.h>

/* copy of SensorDriver.h from polygon */
#define SENSOR_MAX_PARAMBLK_SIZE (1024 * 4)

/* copy of oslo.h from polygon */
#define OSLO_PRESET_CONFIG_START_INDEX 100
#define OSLO_CONTROL_START_INDEX 200
#define OSLO_SETTING_START_INDEX 300
#define OSLO_REGISTER_RW_START_INDEX 400

typedef enum {
    SENSOR_PARAM_SENSOR_SPEC = 0,
    SENSOR_PARAM_SAMP_RATE,
    SENSOR_PARAM_SAMP_SIZE,
    SENSOR_PARAM_INTF_SPEED,
    SENSOR_PARAM_DRIVER_STATE,
    SENSOR_PARAM_FRAMES_PROCESSED,
    SENSOR_PARAM_WATCHDOG_RECOVERY_COUNT,
    SENSOR_PARAM_PRESET_RADAR_CONFIG,
    SENSOR_PARAM_DUMP_STATS,

    /* oslo preset configurations */
    OSLO_CONFIG_DEFAULT = OSLO_PRESET_CONFIG_START_INDEX,
    OSLO_CONFIG_PRESENCE,
    OSLO_CONFIG_CONTINUOUS,
    OSLO_CONFIG_PRESENCE_SLOW,
    OSLO_CONFIG_FACTORY_PRESENCE,
    OSLO_CONFIG_FACTORY_REACH,
    OSLO_CONFIG_CW_MODE,
    OSLO_CONFIG_IDLE,
    OSLO_CONFIG_FACTORY_PRESENCE_V1,
    OSLO_CONFIG_FACTORY_REACH_V1,
    OSLO_CONFIG_REACH,
    OSLO_CONFIG_PRESENCE_V1,
    OSLO_CONFIG_REACH_V1,
    OSLO_CONFIG_OFF,

    /* oslo control - restart oslo when settings change */
    OSLO_CONTROL_RESTART = OSLO_CONTROL_START_INDEX,
    OSLO_CONTROL_STRIP_HEADERS,
    OSLO_CONTROL_SLPI_INT,
    OSLO_CONTROL_STOP,
    OSLO_CONTROL_SIMULATE_RADAR_DATA,
    OSLO_CONTROL_INJECT_RADAR_DATA,
    OSLO_CONTROL_INJECT_FRAME_READY,
    OSLO_CONTROL_ASSERT_RESET,

    /* oslo settings */
    OSLO_PARAM_REQUEST_RATE = OSLO_SETTING_START_INDEX,
    OSLO_PARAM_REQUEST_ANTENNA_MASK,
    OSLO_PARAM_TX_POWER,
    OSLO_PARAM_LOWER_FREQ,
    OSLO_PARAM_UPPER_FREQ,
    OSLO_PARAM_SAMPLES_PER_CHIRP,
    OSLO_PARAM_VGA_GAIN_CH1,
    OSLO_PARAM_VGA_GAIN_CH2,
    OSLO_PARAM_VGA_GAIN_CH3,
    OSLO_PARAM_VGA_GAIN_CH4,
    OSLO_PARAM_BURST_CHIRP_COUNT,
    OSLO_PARAM_BURST_CHIRP_RATE,
    OSLO_PARAM_BURST_POWER_MODE,
    OSLO_PARAM_BURST_INTERCHIRP_POWER_MODE,
    OSLO_PARAM_STARTUP_TIMING_WAKE_UP_TIME_100NS,
    OSLO_PARAM_STARTUP_TIMING_PLL_SETTLE_TIME_COARSE_100NS,
    OSLO_PARAM_STARTUP_TIMING_PLL_SETTLE_TIME_FINE_100NS,
    OSLO_PARAM_STARTUP_TIMING_OSCILLATOR_USEC,
    OSLO_PARAM_PRE_CHIRP_DELAY_100NS,
    OSLO_PARAM_POST_CHIRP_DELAY_100NS,
    OSLO_PARAM_CHIRP_PA_DELAY_100NS,
    OSLO_PARAM_CHIRP_ADC_DELAY_100NS,
    OSLO_PARAM_VISUALIZER_DATA_TYPE,
    OSLO_PARAM_OSCILLATOR_MODE,
    OSLO_PARAM_HP_GAIN_CH1,
    OSLO_PARAM_HP_GAIN_CH2,
    OSLO_PARAM_HP_GAIN_CH3,
    OSLO_PARAM_HP_GAIN_CH4,
    OSLO_PARAM_BASEBAND_RESET_PERIOD_1NS,
    OSLO_PARAM_HP_CUTOFF_CH1,
    OSLO_PARAM_HP_CUTOFF_CH2,
    OSLO_PARAM_HP_CUTOFF_CH3,
    OSLO_PARAM_HP_CUTOFF_CH4,
    OSLO_PARAM_PHASE_CONFIG,
    OSLO_PARAM_IDLE_SETTINGS_ENABLE_PLL,
    OSLO_PARAM_IDLE_SETTINGS_ENABLE_VCO,
    OSLO_PARAM_IDLE_SETTINGS_ENABLE_FDIV,
    OSLO_PARAM_IDLE_SETTINGS_ENABLE_BASEBAND,
    OSLO_PARAM_IDLE_SETTINGS_ENABLE_RF,
    OSLO_PARAM_IDLE_SETTINGS_ENABLE_MADC,
    OSLO_PARAM_IDLE_SETTINGS_ENABLE_MADC_BANDGAP,
    OSLO_PARAM_IDLE_SETTINGS_ENABLE_SADC,
    OSLO_PARAM_IDLE_SETTINGS_ENABLE_SADC_BANDGAP,
    OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_PLL,
    OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_VCO,
    OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_FDIV,
    OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_BASEBAND,
    OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_RF,
    OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_MADC,
    OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_MADC_BANDGAP,
    OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_SADC,
    OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_SADC_BANDGAP,
    OSLO_PARAM_TOSS_COUNT,
    OSLO_PARAM_CHIRP_DIRECTION,
    OSLO_PARAM_ADC_SAMPLE_RATE,
    OSLO_PARAM_CHARGE_PUMP,

    /* oslo register r/w */
    OSLO_REGISTER_MIN = OSLO_REGISTER_RW_START_INDEX,
    OSLO_REGISTER_MAX = OSLO_REGISTER_RW_START_INDEX + 99,

    SENSOR_PARAM_NUM,

    /* Force enums to be of size int */
    SENSOR_PARAM_FORCE_SIZE = INT_MAX,
} sensor_param_t;

typedef enum {
    PARAM_BLK_ID_SET_PARAM = 1,
    PARAM_BLK_ID_FRAME_DATA_INJECTION,
} sensor_param_blk_id_t;

/* copy of OsloSensorPluginInternal.h from polygon */
/* OsloSensor Plugin Param Ids */
typedef enum oslo_sensor_param_id_e {
    OSLO_SENSOR_PARAM_MODE_1_SWITCH_COUNT = 0,
    OSLO_SENSOR_PARAM_MODE_2_SWITCH_COUNT,
    OSLO_SENSOR_PARAM_MODE,
    OSLO_SENSOR_PARAM_SLPY_STATE,
    OSLO_SENSOR_PARAM_ENABLE_SLPY_RAW,
    OSLO_SENSOR_PARAM_HOST,
    OSLO_SENSOR_STATE,
    OSLO_TESTMODE_RESET,
    OSLO_TESTMODE_PRESENCE_ON,
    OSLO_TESTMODE_PRESENCE_OFF,
    OSLO_TESTMODE_SWIPE,
    OSLO_TESTMODE_FLICK,
    OSLO_TESTMODE_REACH_IN,
    OSLO_TESTMODE_REACH_OUT,
    OSLO_TESTMODE_REACH_SWIPE,
    OSLO_TESTMODE_REACH_FLICK,
    OSLO_TESTMODE_REACH_SWIPE_FLICK,
    OSLO_SENSOR_CONFIG_RECOVERY_COUNTER,
    OSLO_SENSOR_PARAM_ENABLE_REACH,
    OSLO_SENSOR_PARAM_ENABLE_FLICK,
    OSLO_SENSOR_PARAM_ENABLE_AUDIO_FILTER,
    OSLO_SENSOR_PARAM_ENABLE_WLC_FILTER,
    OSLO_SENSOR_PARAM_CPS,
    OSLO_SENSOR_MAX_POWER_MODE,
    OSLO_SENSOR_PARAM_SLPY_TEST_MODE,
    OSLO_SENSOR_PARAM_ENABLE_PRESENCE,
    OSLO_SENSOR_PARAM_ENABLE_SWIPE,
    OSLO_SENSOR_PARAM_ENABLE_TAP,

    /* Force enums to be of size int */
    OSLO_SENSOR_PARAM_ID_FORCE_SIZE = INT_MAX,
} oslo_sensor_param_id_t;

struct ia_sensor_mgr {
    FILE *dev_node;
};

struct ia_sensor_mgr *iaxxx_sensor_mgr_init(void);
int iaxxx_sensor_mgr_deinit(struct ia_sensor_mgr *smd);
void oslo_driver_set_param(struct ia_sensor_mgr *smd, int param_id, float param_val);
uint32_t oslo_driver_get_param(struct ia_sensor_mgr *smd, int param_id);
size_t oslo_driver_set_param_blk(struct ia_sensor_mgr *smd, uint32_t param_blk_id,
                                 const void *blk_data, uint32_t blk_size);
int oslo_driver_get_stats(struct ia_sensor_mgr *smd, struct iaxxx_sensor_mode_stats stats[]);
void oslo_plugin_set_param(int param_id, uint32_t param_val);
uint32_t oslo_plugin_get_param(int param_id);

#if __cplusplus
}  // extern "C"
#endif

#endif  //_OSLO_IAXXX_SENSOR_CONTROL_H_
