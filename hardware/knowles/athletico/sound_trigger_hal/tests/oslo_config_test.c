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

#define LOG_TAG "oslo_config_test"

#include <errno.h>
#include <getopt.h>
#include <inttypes.h>
#include <limits.h>
#include <log/log.h>
#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>

#include "oslo_iaxxx_sensor_control.h"
#include "oslo_sound_model_control.h"

#define GETOPT_HELP_CHAR (CHAR_MIN - 2)

#define BGT60TR24C_NUM_REGISTERS        0x60

/* Oslo Calibration */
#define CAL_FILE "/mnt/vendor/persist/oslo/oslo.cal"
#define CAL_MODES_MAX 10
#define CAL_INVALID_MODE -1
#define CAL_MODE_IS_VALID(x) (x >= 0 && x < CAL_MODES_MAX)
#define CAL_VERSION_DEFAULT 1.0f

/* Transmit power */
#define TX_CAL_FILE "/mnt/vendor/persist/oslo/tx_power.cal"

typedef enum {
    INJECT_MODE_OFF = 0,
    INJECT_MODE_PRESENCE,
    INJECT_MODE_REACH,
    INJECT_MODE_NUM,
} oslo_inject_mode_t;

typedef struct {
    int setting_id;
    char *setting_name;
} oslo_settings_t;

/* map oslo driver setting name to param id */
static const oslo_settings_t oslo_driver_settings[] =
{
    {SENSOR_PARAM_FRAMES_PROCESSED,                         "frames_processed"},
    {SENSOR_PARAM_PRESET_RADAR_CONFIG,                      "preset_radar_config"},
    {OSLO_CONFIG_DEFAULT,                                   "config_default"},
    {OSLO_CONFIG_PRESENCE,                                  "config_presence"},
    {OSLO_CONFIG_CONTINUOUS,                                "config_continuous"},
    {OSLO_CONFIG_PRESENCE_SLOW,                             "config_presence_slow"},
    {OSLO_CONFIG_FACTORY_PRESENCE,                          "config_factory_presence"},
    {OSLO_CONFIG_FACTORY_REACH,                             "config_factory_reach"},
    {OSLO_CONFIG_CW_MODE,                                   "config_cw_mode"},
    {OSLO_CONFIG_FACTORY_PRESENCE_V1,                       "config_factory_presence_v1"},
    {OSLO_CONFIG_FACTORY_REACH_V1,                          "config_factory_reach_v1"},
    {OSLO_CONFIG_REACH,                                     "config_reach"},
    {OSLO_CONFIG_PRESENCE_V1,                               "config_presence_v1"},
    {OSLO_CONFIG_REACH_V1,                                  "config_reach_v1"},
    {OSLO_CONFIG_OFF,                                       "config_off"},
    {OSLO_CONTROL_RESTART,                                  "oslo_control_restart"},
    {OSLO_CONTROL_STRIP_HEADERS,                            "oslo_control_strip_headers"},
    {OSLO_CONTROL_SLPI_INT,                                 "oslo_control_slpi_interrupt"},
    {OSLO_CONTROL_STOP,                                     "oslo_control_stop"},
    {OSLO_CONTROL_SIMULATE_RADAR_DATA,                      "oslo_control_simulate_radar_data"},
    {OSLO_CONTROL_ASSERT_RESET,                             "oslo_control_assert_reset"},
    {OSLO_PARAM_REQUEST_RATE,                               "param_request_rate"},
    {OSLO_PARAM_REQUEST_ANTENNA_MASK,                       "param_request_antenna_mask"},
    {OSLO_PARAM_TX_POWER,                                   "param_tx_power"},
    {OSLO_PARAM_LOWER_FREQ,                                 "param_lower_freq"},
    {OSLO_PARAM_UPPER_FREQ,                                 "param_upper_freq"},
    {OSLO_PARAM_SAMPLES_PER_CHIRP,                          "param_samples_per_chirp"},
    {OSLO_PARAM_VGA_GAIN_CH1,                               "param_vga_gain_ch1"},
    {OSLO_PARAM_VGA_GAIN_CH2,                               "param_vga_gain_ch2"},
    {OSLO_PARAM_VGA_GAIN_CH3,                               "param_vga_gain_ch3"},
    {OSLO_PARAM_VGA_GAIN_CH4,                               "param_vga_gain_ch4"},
    {OSLO_PARAM_BURST_CHIRP_COUNT,                          "param_burst_chirp_count"},
    {OSLO_PARAM_BURST_CHIRP_RATE,                           "param_burst_chirp_rate"},
    {OSLO_PARAM_BURST_POWER_MODE,                           "param_burst_power_mode"},
    {OSLO_PARAM_BURST_INTERCHIRP_POWER_MODE,                "param_burst_interchirp_power_mode"},
    {OSLO_PARAM_STARTUP_TIMING_WAKE_UP_TIME_100NS,          "param_startup_timing_wake_up_time_100ns"},
    {OSLO_PARAM_STARTUP_TIMING_PLL_SETTLE_TIME_COARSE_100NS,"param_startup_timing_pll_settle_time_coarse_100ns"},
    {OSLO_PARAM_STARTUP_TIMING_PLL_SETTLE_TIME_FINE_100NS,  "param_startup_timing_pll_settle_time_fine_100ns"},
    {OSLO_PARAM_STARTUP_TIMING_OSCILLATOR_USEC,             "param_startup_timing_oscillator_usec"},
    {OSLO_PARAM_PRE_CHIRP_DELAY_100NS,                      "param_pre_chirp_delay_100ns"},
    {OSLO_PARAM_POST_CHIRP_DELAY_100NS,                     "param_post_chirp_delay_100ns"},
    {OSLO_PARAM_CHIRP_PA_DELAY_100NS,                       "param_chirp_pa_delay_100ns"},
    {OSLO_PARAM_CHIRP_ADC_DELAY_100NS,                      "param_chirp_adc_delay_100ns"},
    {OSLO_PARAM_VISUALIZER_DATA_TYPE,                       "param_visualizer_data_type"},
    {OSLO_PARAM_OSCILLATOR_MODE,                            "param_oscillator_mode"},
    {OSLO_PARAM_HP_GAIN_CH1,                                "param_hp_gain_ch1"},
    {OSLO_PARAM_HP_GAIN_CH2,                                "param_hp_gain_ch2"},
    {OSLO_PARAM_HP_GAIN_CH3,                                "param_hp_gain_ch3"},
    {OSLO_PARAM_HP_GAIN_CH4,                                "param_hp_gain_ch4"},
    {OSLO_PARAM_BASEBAND_RESET_PERIOD_1NS,                  "param_baseband_reset_period_1ns"},
    {OSLO_PARAM_HP_CUTOFF_CH1,                              "param_hp_cutoff_ch1"},
    {OSLO_PARAM_HP_CUTOFF_CH2,                              "param_hp_cutoff_ch2"},
    {OSLO_PARAM_HP_CUTOFF_CH3,                              "param_hp_cutoff_ch3"},
    {OSLO_PARAM_HP_CUTOFF_CH4,                              "param_hp_cutoff_ch4"},
    {OSLO_PARAM_PHASE_CONFIG,                               "param_phase_config"},
    {OSLO_PARAM_IDLE_SETTINGS_ENABLE_PLL,                   "param_idle_settings_enable_pll"},
    {OSLO_PARAM_IDLE_SETTINGS_ENABLE_VCO,                   "param_idle_settings_enable_vco"},
    {OSLO_PARAM_IDLE_SETTINGS_ENABLE_FDIV,                  "param_idle_settings_enable_fdiv"},
    {OSLO_PARAM_IDLE_SETTINGS_ENABLE_BASEBAND,              "param_idle_settings_enable_baseband"},
    {OSLO_PARAM_IDLE_SETTINGS_ENABLE_RF,                    "param_idle_settings_enable_rf"},
    {OSLO_PARAM_IDLE_SETTINGS_ENABLE_MADC,                  "param_idle_settings_enable_madc"},
    {OSLO_PARAM_IDLE_SETTINGS_ENABLE_MADC_BANDGAP,          "param_idle_settings_enable_madc_bandgap"},
    {OSLO_PARAM_IDLE_SETTINGS_ENABLE_SADC,                  "param_idle_settings_enable_sadc"},
    {OSLO_PARAM_IDLE_SETTINGS_ENABLE_SADC_BANDGAP,          "param_idle_settings_enable_sadc_bandgap"},
    {OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_PLL,             "param_deep_sleep_settings_enable_pll"},
    {OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_VCO,             "param_deep_sleep_settings_enable_vco"},
    {OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_FDIV,            "param_deep_sleep_settings_enable_fdiv"},
    {OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_BASEBAND,        "param_deep_sleep_settings_enable_baseband"},
    {OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_RF,              "param_deep_sleep_settings_enable_rf"},
    {OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_MADC,            "param_deep_sleep_settings_enable_madc"},
    {OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_MADC_BANDGAP,    "param_deep_sleep_settings_enable_madc_bandgap"},
    {OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_SADC,            "param_deep_sleep_settings_enable_sadc"},
    {OSLO_PARAM_DEEP_SLEEP_SETTINGS_ENABLE_SADC_BANDGAP,    "param_deep_sleep_settings_enable_sadc_bandgap"},
    {OSLO_PARAM_CHIRP_DIRECTION,                            "param_chirp_direction"},
    {OSLO_PARAM_ADC_SAMPLE_RATE,                            "param_adc_sample_rate"},
    {OSLO_PARAM_CHARGE_PUMP,                                "param_charge_pump"},
};

/* map oslo plugin setting name to param id */
static const oslo_settings_t oslo_plugin_settings[] =
{
    {OSLO_SENSOR_PARAM_MODE,                                "plugin_mode"},
    {OSLO_SENSOR_PARAM_SLPY_STATE,                          "plugin_slpy_state"},
    {OSLO_SENSOR_PARAM_ENABLE_SLPY_RAW,                     "plugin_slpy_raw"},
    {OSLO_SENSOR_PARAM_HOST,                                "plugin_set_host"},
    {OSLO_SENSOR_STATE,                                     "plugin_oslo_state"},
    {OSLO_SENSOR_CONFIG_RECOVERY_COUNTER,                   "plugin_config_retries"},
    {OSLO_SENSOR_PARAM_ENABLE_PRESENCE,                     "plugin_enable_presence"},
    {OSLO_SENSOR_PARAM_ENABLE_REACH,                        "plugin_enable_reach"},
    {OSLO_SENSOR_PARAM_ENABLE_FLICK,                        "plugin_enable_flick"},
    {OSLO_SENSOR_PARAM_ENABLE_SWIPE,                        "plugin_enable_swipe"},
    {OSLO_SENSOR_PARAM_ENABLE_TAP,                          "plugin_enable_tap"},
    {OSLO_SENSOR_PARAM_ENABLE_AUDIO_FILTER,                 "plugin_enable_audio_filter"},
    {OSLO_SENSOR_PARAM_ENABLE_WLC_FILTER,                   "plugin_enable_wlc_filter"},
    {OSLO_SENSOR_PARAM_CPS,                                 "plugin_cps"},
    {OSLO_SENSOR_MAX_POWER_MODE,                            "plugin_max_power_mode"},
    {OSLO_SENSOR_PARAM_SLPY_TEST_MODE,                      "plugin_slpy_test_mode"},
};

/* map oslo plugin test mode name to param id */
static const oslo_settings_t oslo_plugin_test_mode[] =
{
    {OSLO_TESTMODE_RESET,                                   "reset"},
    {OSLO_TESTMODE_PRESENCE_ON,                             "presence_on"},
    {OSLO_TESTMODE_PRESENCE_OFF,                            "presence_off"},
    {OSLO_TESTMODE_SWIPE,                                   "swipe"},
    {OSLO_TESTMODE_FLICK,                                   "flick"},
    {OSLO_TESTMODE_REACH_IN,                                "reach_in"},
    {OSLO_TESTMODE_REACH_OUT,                               "reach_out"},
    {OSLO_TESTMODE_REACH_SWIPE,                             "reach+swipe"},
    {OSLO_TESTMODE_REACH_FLICK,                             "reach+flick"},
    {OSLO_TESTMODE_REACH_SWIPE_FLICK,                       "reach+swipe+flick"},
};
#define COUNT_OF(x) ((sizeof(x)/sizeof(0[x])) / ((size_t)(!(sizeof(x) % sizeof(0[x])))))
#define OSLO_DRIVER_SETTINGS_SIZE COUNT_OF(oslo_driver_settings)
#define OSLO_PLUGIN_SETTINGS_SIZE COUNT_OF(oslo_plugin_settings)
#define OSLO_PLUGIN_TESTMODE_SIZE COUNT_OF(oslo_plugin_test_mode)

struct cal_coefficient {
    float version;
    int mode;
    float ch1_i_val;
    float ch1_q_val;
    float ch2_i_val;
    float ch2_q_val;
    float ch3_i_val;
    float ch3_q_val;
};
static struct cal_coefficient cal_table[CAL_MODES_MAX];
static uint32_t tx_power_cal;

static struct option const long_options[] =
{
 {"setparamid", required_argument, NULL, 's'},
 {"value", required_argument, NULL, 'v'},
 {"getparamid", required_argument, NULL, 'g'},
 {"ping", required_argument, NULL, 'p'},
 {"route", required_argument, NULL, 'r'},
 {"readregister", required_argument, NULL, 'd'},
 {"writeregister", required_argument, NULL, 'w'},
 {"calibration", required_argument, NULL, 'c'},
 {"tx power cal", required_argument, NULL, 'e'},
 {"injection", required_argument, NULL, 'i'},
 {"help", no_argument, NULL, GETOPT_HELP_CHAR},
 {NULL, 0, NULL, 0}
};

void usage() {
    fputs("\
    USAGE -\n\
    -------\n\
    1) oslo_config_test -s <param_name> -v <param_val>\n\
    2) oslo_config_test -g <param_name>\n\
    \n\
    In the first form, set a parameter with a value.\n\
    In the second form, get a value of a parameter\n\
    \n\
    3) oslo_config_test -p <timeout>\n\
    4) oslo_config_test -r <1/0>\n\
    5) oslo_config_test -d <reg_addr>\n\
    6) oslo_config_test -w <reg_addr> -v <reg_val>\n\
    7) oslo_config_test -c 'V:<ver> M:<mode> <ch1 I_val> <ch1 Q_val> <ch2 I_val> <ch2 Q_val> <ch3 I_val> <ch3 Q_val>' \n\
    8) oslo_config_test -t <test_mode> -v <elapsed time>\n\
    9) oslo_config_test -i <0/1/2>      # 0:off 1:entrance 2:interactive \n\
    10) oslo_config_test -e '<tx_val>'\n\
    ", stdout);

    fputs("\n\
    OPTIONS - \n\
    ---------\n\
    -s          Set a parameter using its <param_name>.\n\
    -v          Set this value for the parameter ID that was passed with\n\
                the option '-s'. Using this option alone is invalid.\n\
    -g          Get the value of a parameter using its <param_name>.\n\
    -p          Ping oslo sensor.\n\
    -r          Set sensor route.\n\
    -d          Read register.\n\
    -w          Write register.\n\
    -c          Store Calibration coefficients to persist file.\n\
    -t          Set the system into a test mode with optional gesture detection spoofing.\n\
    -i          Set the system into data injection mode.\n\
    -e          Store Tx power calibration to persist file.\n\
    ", stdout);

    fputs("\n\
    List of all <param_name>\n\
    ---------\
    ", stdout);
    fputs("\n", stdout);

    unsigned int i;
    for (i = 0; i < OSLO_DRIVER_SETTINGS_SIZE; i++) {
        fprintf(stdout, "    %s\n", oslo_driver_settings[i].setting_name, stdout);
    }
    for (i = 0; i < OSLO_PLUGIN_SETTINGS_SIZE; i++) {
        fprintf(stdout, "    %s\n", oslo_plugin_settings[i].setting_name, stdout);
    }

    fputs("\n\
    List of all <test_mode>\n\
    ---------\
    ", stdout);
    fputs("\n", stdout);

    for (i = 0; i < OSLO_PLUGIN_TESTMODE_SIZE; i++) {
        fprintf(stdout, "    %s\n", oslo_plugin_test_mode[i].setting_name, stdout);
    }

    exit(EXIT_FAILURE);
}

int oslo_driver_setting_lookup(char *in)
{
    unsigned int i;
    int ret = -1;

    for (i = 0; i < OSLO_DRIVER_SETTINGS_SIZE; i++)
    {
        if (strcmp(in, oslo_driver_settings[i].setting_name) == 0)
        {
            ret = oslo_driver_settings[i].setting_id;
            break;
        }
    }

    return ret;
}

int oslo_plugin_setting_lookup(char *in)
{
    unsigned int i;
    int ret = -1;

    for (i = 0; i < OSLO_PLUGIN_SETTINGS_SIZE; i++)
    {
        if (strcmp(in, oslo_plugin_settings[i].setting_name) == 0)
        {
            ret = oslo_plugin_settings[i].setting_id;
            break;
        }
    }

    return ret;
}

int oslo_plugin_test_mode_lookup(char *in)
{
    unsigned int i;
    int ret = -1;

    for (i = 0; i < OSLO_PLUGIN_TESTMODE_SIZE; i++)
    {
        if (strcmp(in, oslo_plugin_test_mode[i].setting_name) == 0)
        {
            ret = oslo_plugin_test_mode[i].setting_id;
            break;
        }
    }

    return ret;
}

void oslo_enable(struct ia_sensor_mgr *smd, bool enable) {
    if (enable) {
        osloSoundModelEnable(true);
        oslo_driver_set_param(smd, OSLO_CONTROL_RESTART, 1);
    }
    else {
        oslo_driver_set_param(smd, OSLO_CONTROL_STOP, 0);
        sleep(1);
        osloSoundModelEnable(false);
    }
}

bool ping_test(struct ia_sensor_mgr *smd, uint32_t ping_timeout_sec) {
    bool ret = false;
    uint32_t radar_frames_initial;
    time_t start_time;

    oslo_enable(smd, true);

    start_time = time(NULL);
    radar_frames_initial = oslo_driver_get_param(smd, SENSOR_PARAM_FRAMES_PROCESSED);

    do {
        uint32_t radar_frames = oslo_driver_get_param(smd, SENSOR_PARAM_FRAMES_PROCESSED);
        if (radar_frames > radar_frames_initial) {
            ALOGD("%s: frame number increased (%d, %d)",
                  __func__, radar_frames_initial, radar_frames);
            ret = true;
            break;
        }
        else
            usleep(50 * 1000); // 50ms
    } while (difftime(time(NULL), start_time) <= ping_timeout_sec);

    oslo_enable(smd, false);

    ALOGD("%s: %s", __func__, (ret ? "PASS" : "FAIL"));
    fprintf(stdout, "%s: %s\n", __func__, (ret ? "PASS" : "FAIL"));

    return ret;
}

void read_register(struct ia_sensor_mgr *smd, uint32_t reg_addr) {
    uint32_t reg_val;

    if (reg_addr >= BGT60TR24C_NUM_REGISTERS) {
        fprintf(stdout, "Invalid reg addr:0x%02x\n", reg_addr);
        return;
    }

    reg_val = oslo_driver_get_param(smd, OSLO_REGISTER_MIN + reg_addr);

    ALOGD("reg[0x%02x]: 0x%06x\n", reg_addr, reg_val);
    fprintf(stdout, "reg[0x%02x]: 0x%06x\n", reg_addr, reg_val);
}

void write_register(struct ia_sensor_mgr *smd, uint32_t reg_addr, uint32_t reg_val) {
    if (reg_addr >= BGT60TR24C_NUM_REGISTERS) {
        fprintf(stdout, "Invalid reg addr:0x%02x\n", reg_addr);
        return;
    }

    oslo_driver_set_param(smd, OSLO_REGISTER_MIN + reg_addr, reg_val);

    ALOGD("Write reg[0x%02x] val:0x%06x\n", reg_addr, reg_val);
    fprintf(stdout, "Write reg[0x%02x] val:0x%06x\n", reg_addr, reg_val);
}

int cal_read_persist(void) {
    FILE *fid;
    struct cal_coefficient coef;

    fid = fopen(CAL_FILE, "r");
    if (fid == NULL) {
        ALOGD("%s: Cannot open '%s'\n", __func__, CAL_FILE);
        return -errno;
    }

    while (!feof(fid)) {
        int num;

        memset(&coef, 0, sizeof(coef));
        coef.mode = CAL_INVALID_MODE;

        num = fscanf(fid, "Version: %f\n", &coef.version);
        if (num != 1) {
            ALOGE("%s: Parse Version failed, num:%d\n", __func__, num);
            coef.version = CAL_VERSION_DEFAULT;
        }

        num = fscanf(fid, "Mode: %d\n", &coef.mode);
        if (num != 1) {
            ALOGE("%s: Parse Mode failed, num:%d\n", __func__, num);
            break;
        }

        num = fscanf(fid, "ch1: %f %f\n", &coef.ch1_i_val, &coef.ch1_q_val);
        if (num != 2) {
            ALOGE("%s: Parse ch1 failed, num:%d\n", __func__, num);
            break;
        }

        num = fscanf(fid, "ch2: %f %f\n", &coef.ch2_i_val, &coef.ch2_q_val);
        if (num != 2) {
            ALOGE("%s: Parse ch2 failed, num:%d\n", __func__, num);
            break;
        }

        num = fscanf(fid, "ch3: %f %f\n", &coef.ch3_i_val, &coef.ch3_q_val);
        if (num != 2) {
            ALOGE("%s: Parse ch3 failed, num:%d\n", __func__, num);
            break;
        }

        if (CAL_MODE_IS_VALID(coef.mode)) {
            memcpy(&cal_table[coef.mode], &coef, sizeof(coef));
            ALOGD("%s: %.1f %d  %f %f %f %f %f %f\n", __func__,
                  coef.version,
                  coef.mode,
                  coef.ch1_i_val, coef.ch1_q_val,
                  coef.ch2_i_val, coef.ch2_q_val,
                  coef.ch3_i_val, coef.ch3_q_val);
        } else {
            ALOGE("%s: Invalid mode:%d\n", __func__, coef.mode);
        }
    }

    fclose(fid);

    return 0;
}

int cal_write_persist(const struct cal_coefficient* coef) {
    FILE *fid;

    if (!coef) {
        ALOGE("%s: Invalid coef", __func__);
        fprintf(stdout, "%s: Invalid coef\n", __func__);
        return -EINVAL;
    }

    if (!CAL_MODE_IS_VALID(coef->mode)) {
        ALOGE("%s: Invalid mode:%d", __func__, coef->mode);
        fprintf(stdout, "%s: Invalid mode:%d\n", __func__, coef->mode);
        return -EINVAL;
    }

    fid = fopen(CAL_FILE, "w");
    if (fid == NULL) {
        ALOGE("Cannot open '%s' (%s)\n", CAL_FILE, strerror(errno));
        fprintf(stdout, "Cannot open '%s' (%s)\n", CAL_FILE, strerror(errno));
        return -errno;
    }

    memcpy(&cal_table[coef->mode], coef, sizeof(struct cal_coefficient));
    for (int i = 0; i < CAL_MODES_MAX; i++) {
        if (CAL_MODE_IS_VALID(cal_table[i].mode)) {
            fprintf(fid, "Version: %.1f\n", cal_table[i].version);
            fprintf(fid, "Mode: %u\n", cal_table[i].mode);
            fprintf(fid, "ch1: %f %f\n",
                         cal_table[i].ch1_i_val,
                         cal_table[i].ch1_q_val);
            fprintf(fid, "ch2: %f %f\n",
                         cal_table[i].ch2_i_val,
                         cal_table[i].ch2_q_val);
            fprintf(fid, "ch3: %f %f\n",
                         cal_table[i].ch3_i_val,
                         cal_table[i].ch3_q_val);
            ALOGD("%s: %.1f %d %f %f %f %f %f %f\n", __func__,
                  cal_table[i].version,
                  cal_table[i].mode,
                  cal_table[i].ch1_i_val, cal_table[i].ch1_q_val,
                  cal_table[i].ch2_i_val, cal_table[i].ch2_q_val,
                  cal_table[i].ch3_i_val, cal_table[i].ch3_q_val);
        }
    }

    fclose(fid);

    return 0;
}

static int read_tx_power_from_persist(void) {
    FILE *fid;
    uint32_t tx_power = 0;

    fid = fopen(TX_CAL_FILE, "r");
    if (fid == NULL) {
        ALOGD("%s: Cannot open '%s'\n", __func__, TX_CAL_FILE);
        return -errno;
    }

    while (!feof(fid)) {
        int num;
        num = fscanf(fid, "tx_power: %d\n", &tx_power);
        if (num != 1) {
            ALOGE("%s: Parse tx power failed, num:%d\n", __func__, num);
            break;
        }

        memcpy(&tx_power_cal, &tx_power, sizeof(tx_power));
        ALOGD("%s: tx_power: %d\n", __func__, tx_power);
    }

    fclose(fid);
    return 0;
}

static int write_tx_power_to_persist(const uint32_t* tx_power) {
    FILE *fid;

    if (!tx_power) {
        ALOGE("%s: Invalid tx power", __func__);
        fprintf(stdout, "%s: Invalid tx power\n", __func__);
        return -EINVAL;
    }

    fid = fopen(TX_CAL_FILE, "w");
    if (fid == NULL) {
        ALOGE("Cannot open '%s' (%s)\n", TX_CAL_FILE, strerror(errno));
        fprintf(stdout, "Cannot open '%s' (%s)\n", TX_CAL_FILE, strerror(errno));
        return -errno;
    }

    memcpy(&tx_power_cal, tx_power, sizeof(tx_power_cal));
    fprintf(fid, "tx_power: %u\n", tx_power_cal);
    ALOGD("%s: %d\n", __func__, tx_power_cal);
    fclose(fid);
    return 0;
}

int main(int argc, char *argv[]) {
    struct ia_sensor_mgr * smd;
    char use_case;
    int driver_param_id = -1;
    int plugin_param_id = -1;
    int c;
    float param_val = 0.0;
    uint32_t ping_timeout_sec;
    bool route_enable;
    uint32_t reg_addr;
    uint32_t reg_val;
    bool reg_val_set = false;
    struct cal_coefficient cal_coef = { .mode = CAL_INVALID_MODE };
    uint32_t tx_power = 0;
    int test_mode_param_id = -1;
    oslo_inject_mode_t inject_mode = INJECT_MODE_OFF;

    if (argc <= 1) {
        usage();
    }

    while ((c = getopt_long (argc, argv, "s:v:g:p:r:d:w:c:t:i:e:", long_options, NULL)) != -1) {
        switch (c) {
        case 's':
            if (NULL == optarg) {
                fprintf(stderr, "Incorrect usage, s option requires an argument");
                usage();
            } else {
                driver_param_id = oslo_driver_setting_lookup(optarg);
                plugin_param_id = oslo_plugin_setting_lookup(optarg);
                if (driver_param_id == -1 && plugin_param_id == -1) {
                    fprintf(stderr, "Invalid setting %s", optarg);
                    usage();
                } else {
                    use_case = 's';
                }
            }
        break;
        case 'v':
            if (NULL == optarg) {
                fprintf(stderr, "Incorrect usage, v option requires an argument");
                usage();
            } else {
                if ('s' == use_case) {
                    param_val = strtof(optarg, NULL);
                    use_case = 'v';
                } else if ('w' == use_case) {
                    reg_val = strtoul(optarg, NULL, 0);
                    reg_val_set = true;
                } else if ('t' == use_case) {
                    param_val = strtof(optarg, NULL);
                    use_case = 'v';
                } else {
                    fprintf(stderr, "Incorrect usage, v option should be the second option");
                    usage();
                }
            }
        break;
        case 'g':
            if (NULL == optarg) {
                fprintf(stderr, "Incorrect usage, g option requires an argument");
                usage();
            } else {
                driver_param_id = oslo_driver_setting_lookup(optarg);
                plugin_param_id = oslo_plugin_setting_lookup(optarg);
                if (driver_param_id == -1 && plugin_param_id == -1) {
                    fprintf(stderr, "Invalid setting %s", optarg);
                    usage();
                } else {
                    use_case = 'g';
                }
            }
        break;
        case 'p':
            if (NULL == optarg) {
                fprintf(stderr, "Incorrect usage, p option requires an argument");
                usage();
            } else {
                ping_timeout_sec = strtoul(optarg, NULL, 0);
                use_case = 'p';
            }
        break;
        case 'r':
            if (NULL == optarg) {
                fprintf(stderr, "Incorrect usage, r option requires an argument");
                usage();
            } else {
                route_enable = (strtoul(optarg, NULL, 0) != 0) ? true : false;
                use_case = 'r';
            }
        break;
        case 'd':
            if (NULL == optarg) {
                fprintf(stderr, "Incorrect usage, d option requires an argument");
                usage();
            } else {
                reg_addr = strtoul(optarg, NULL, 0);
                use_case = 'd';
            }
        break;
        case 'w':
            if (NULL == optarg) {
                fprintf(stderr, "Incorrect usage, w option requires an argument");
                usage();
            } else {
                reg_addr = strtoul(optarg, NULL, 0);
                use_case = 'w';
            }
        break;
        case 'c':
            if (NULL == optarg) {
                fprintf(stderr, "Incorrect usage, c option requires an argument");
                usage();
            } else {
                int num_matched;
                num_matched = sscanf(optarg, "V:%f M:%d %f %f %f %f %f %f",
                                     &cal_coef.version,
                                     &cal_coef.mode,
                                     &cal_coef.ch1_i_val, &cal_coef.ch1_q_val,
                                     &cal_coef.ch2_i_val, &cal_coef.ch2_q_val,
                                     &cal_coef.ch3_i_val, &cal_coef.ch3_q_val);
                if (num_matched == 8) {
                    use_case = 'c';
                } else {
                    fprintf(stderr, "Incorrect -c arguments %s\n", optarg);
                    usage();
                }
            }
        break;
        case 'e':
            if (NULL == optarg) {
                fprintf(stderr, "Incorrect usage, e option requires an argument");
                usage();
            } else {
                int num_matched;
                num_matched = sscanf(optarg, "%d", &tx_power);
                if (num_matched == 1) {
                    use_case = 'e';
                } else {
                    fprintf(stderr, "Incorrect -e arguments %s \n", optarg);
                    usage();
                }
            }
        break;
        case 't':
            if (NULL == optarg) {
                fprintf(stderr, "Incorrect usage, t option requires an argument");
                usage();
            } else {
                test_mode_param_id = oslo_plugin_test_mode_lookup(optarg);
                if (test_mode_param_id == -1) {
                    fprintf(stderr, "Invalid setting %s", optarg);
                    usage();
                } else {
                    use_case = 't';
                    fprintf(stderr, "Executing test mode %s\n", optarg);
                }
            }
        break;
        case 'i':
            if (NULL == optarg) {
                fprintf(stderr, "Incorrect usage, i option requires an argument");
                usage();
            } else {
                inject_mode = strtoul(optarg, NULL, 0);
                if (inject_mode >= INJECT_MODE_NUM) {
                    fprintf(stderr, "Invalid setting %s", optarg);
                    usage();
                } else {
                    use_case = 'i';
                }
            }
        break;
        case GETOPT_HELP_CHAR:
        default:
            usage();
        break;
        }

        smd = iaxxx_sensor_mgr_init();
        if (NULL == smd) {
            ALOGE("%s: ERROR Failed to init ia_sensor_mgr", __func__);
            return -EINVAL;
        }

        if ('v' == use_case) {
            if (driver_param_id != -1) {
                oslo_driver_set_param(smd, driver_param_id, param_val);
            } else if (plugin_param_id != -1) {
                oslo_plugin_set_param(plugin_param_id, param_val);
            } else if (test_mode_param_id != -1) {
                uint32_t integer_param = (uint32_t)lrintf(param_val);
                if (param_val < 0) {
                    ALOGD("%s: Test mode: %d with no event", __func__, test_mode_param_id);
                    integer_param = UINT32_MAX;
                } else if (param_val > 0) {
                    ALOGD("%s: Test mode: %d with duration: %" PRIu32, __func__, test_mode_param_id, integer_param);
                } else {
                    ALOGD("%s: Test mode: %d with no duration", __func__, test_mode_param_id);
                }
                oslo_plugin_set_param(test_mode_param_id, integer_param);
            }
        } else if ('g' == use_case) {
            if (driver_param_id != -1) {
                oslo_driver_get_param(smd, driver_param_id);
            } else if (plugin_param_id != -1) {
                oslo_plugin_get_param(plugin_param_id);
            }
        } else if ('p' == use_case) {
            ping_test(smd, ping_timeout_sec);
        } else if ('r' == use_case) {
            route_enable ? osloSoundModelEnable(true) : oslo_enable(smd, false);
        } else if ('d' == use_case) {
            read_register(smd, reg_addr);
        } else if ('w' == use_case) {
            if (reg_val_set)
                write_register(smd, reg_addr, reg_val);
        } else if ('c' == use_case) {
            for (int i = 0; i < CAL_MODES_MAX; i++) {
                cal_table[i].mode = CAL_INVALID_MODE;
            }
            cal_read_persist();
            cal_write_persist(&cal_coef);
        } else if ('e' == use_case) {
            read_tx_power_from_persist();
            write_tx_power_to_persist(&tx_power);
        } else if ('i' == use_case) {
            oslo_plugin_set_param(OSLO_SENSOR_PARAM_SLPY_STATE, 0);
            oslo_driver_set_param(smd, OSLO_CONTROL_INJECT_RADAR_DATA, inject_mode);
            oslo_plugin_set_param(OSLO_SENSOR_PARAM_SLPY_STATE, inject_mode);
        }

        iaxxx_sensor_mgr_deinit(smd);
    }

    return 0;
}
