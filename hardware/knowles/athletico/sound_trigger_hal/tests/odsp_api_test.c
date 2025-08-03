/*
 * Copyright (C) 2018 Knowles Electronics
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

#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <getopt.h>
#include <stdbool.h>
#include <limits.h>
#include <string.h>

#define LOG_TAG "odsp_api_test"

#include <cutils/log.h>
#include "iaxxx_odsp_hw.h"

#define GETOPT_HELP_CHAR (CHAR_MIN - 2)

static struct option const long_options[] =
{
 {"setparamid", required_argument, NULL, 's'},
 {"getparamid", required_argument, NULL, 'g'},
 {"enableplugin", required_argument, NULL, 'e'},
 {"disableplugin", required_argument, NULL, 'd'},
 {"help", no_argument, NULL, GETOPT_HELP_CHAR},
 {NULL, 0, NULL, 0}
};

void usage() {
    fputs("\
    USAGE -\n\
    -------\n\
    1) odsp_api_test -s <param_id> <param_val> <inst_id> <block_id>\n\
    2) odsp_api_test -g <param_id> <inst_id> <block_id>\n\
    3) odsp_api_test -e <inst_id> <block_id>\n\
    4) odsp_api_test -d <inst_id> <block_id>\n\
    5) odsp_api_test -t <src_id> <event_id> <src_opaque>\n\
    \n\
    In the first form, set a parameter with a value, needs instance and block id.\n\
    In the second form, get a parameters value, needs instance and block id.\n\
    In the third & fourth form, needs instance and block id.\n\
    In the fifth form, manually trigger an event, needs src id, event id and src_opaque\n", stdout);

    exit(EXIT_FAILURE);
}

void set_param(struct iaxxx_odsp_hw *ioh, unsigned int param_id,
                unsigned int param_val, unsigned int inst_id,
                unsigned int block_id) {
    int err = 0;

    err = iaxxx_odsp_plugin_set_parameter(ioh, inst_id, param_id, param_val,
                                           block_id);
    if (err != 0) {
        ALOGE("Failed to set parameter id %u with error %d", param_id, err);
    }
}

void get_param(struct iaxxx_odsp_hw *ioh, unsigned int param_id,
                   unsigned int inst_id, unsigned int block_id) {
    int err = 0;
    unsigned int param_val = 0;

    err = iaxxx_odsp_plugin_get_parameter(ioh, inst_id, param_id, block_id,
                                          &param_val);
    if (err != 0) {
        ALOGE("Failed to get parameter value for id %u with error %d",
            param_id, err);
    } else {
        ALOGD("Value of parameter id %u is %u", param_id, param_val);
    }
}

void plugin_control(struct iaxxx_odsp_hw *ioh, unsigned int inst_id,
                   unsigned int block_id, int control) {
    int err = 0;

    if (control)
        err = iaxxx_odsp_plugin_enable(ioh, inst_id, block_id);
    else
        err = iaxxx_odsp_plugin_disable(ioh, inst_id, block_id);

    if (err != 0) {
        ALOGE("Failed to enable/disable the plugin. Error %d", err);
    } else {
        if (control) {
            ALOGD("Plugin enabled!!");
        } else {
            ALOGD("Plugin disbled!!");
        }
    }
}

void trigger_event(struct iaxxx_odsp_hw *ioh, unsigned int src_id,
        unsigned int event_id, unsigned int src_opaque)
{
    int err = 0;

    err = iaxxx_odsp_evt_trigger(ioh, src_id, event_id, src_opaque);

    if (err != 0) {
        ALOGE("Failed to trigger event! Error:%d", err);
    }
}


int main(int argc, char *argv[]) {
    struct iaxxx_odsp_hw *ioh = NULL;
    char use_case;
    unsigned int param_id, param_val, inst_id, block_id;
    unsigned int src_id, event_id, src_opaque;
    int c, err = 0;
    // Set param option needs 4 arguments
    const int set_param_req_arg = 4;
    // Get param option needs 3 arguments
    const int get_param_req_arg = 3;
    // Plugin control needs 2 arguments
    const int plugin_control_arg = 2;
    // Trigger event needs 3 args
    const int trigger_event_req_arg = 3;

    if (argc <= 1) {
        usage();
    }

    while ((c = getopt_long (argc, argv, "s:g:e:d:t:", long_options, NULL)) != -1) {
        switch (c) {
        case 's':
            use_case = 's';
            // reset optind by 1
            --optind;
            if (optind + set_param_req_arg > argc) {
                usage();
            }

            param_id = strtol(argv[optind], NULL, 0);
            optind++;
            param_val = strtol(argv[optind], NULL, 0);
            optind++;
            inst_id = strtol(argv[optind], NULL, 0);
            optind++;
            block_id = strtol(argv[optind], NULL, 0);
            optind++;

            ALOGE("Set parameter - param_id %d param_val %d inst_id %d "
                  "block_id %d", param_id, param_val, inst_id, block_id);
        break;
        case 'g':
            use_case = 'g';
            // reset optind by 1
            --optind;
            if (optind + get_param_req_arg > argc) {
                usage();
            }

            param_id = strtol(argv[optind], NULL, 0);
            optind++;
            inst_id = strtol(argv[optind], NULL, 0);
            optind++;
            block_id = strtol(argv[optind], NULL, 0);
            optind++;

            ALOGE("Get parameter - param_id %d inst_id %d block_id %d",
                    param_id, inst_id, block_id);
        break;

        case 'e':
            use_case = 'e';
            // reset optind by 1
            --optind;
            if (optind + plugin_control_arg > argc) {
                usage();
            }

            inst_id = strtol(argv[optind], NULL, 0);
            optind++;
            block_id = strtol(argv[optind], NULL, 0);
            optind++;

            ALOGE("Enable plugin - inst_id %d block_id %d", inst_id, block_id);
        break;

        case 'd':
            use_case = 'd';
            // reset optind by 1
            --optind;
            if (optind + plugin_control_arg > argc) {
                usage();
            }

            inst_id = strtol(argv[optind], NULL, 0);
            optind++;
            block_id = strtol(argv[optind], NULL, 0);
            optind++;

            ALOGE("Disable plugin - inst_id %d block_id %d", inst_id, block_id);
        break;

        case 't':
            use_case = 't';
            // reset optind by 1
            --optind;
            if (optind + trigger_event_req_arg > argc) {
                usage();
            }

            src_id = strtol(argv[optind], NULL, 0);
            optind++;
            event_id = strtol(argv[optind], NULL, 0);
            optind++;
            src_opaque = strtol(argv[optind], NULL, 0);
            optind++;

            ALOGE("Trigger Event - src_id %d event_id %d src_opaque %d",
                    src_id, event_id, src_opaque);
        break;

        default:
            usage();
        break;
        }
    }

    ioh = iaxxx_odsp_init();
    if (ioh == NULL) {
        ALOGE("ERROR: Failed to init odsp HAL");
        err = -1;
        goto exit;
    }

    switch(use_case) {
        case 's':
            set_param(ioh, param_id, param_val, inst_id, block_id);
            break;
        case 'g':
            get_param(ioh, param_id, inst_id, block_id);
            break;
        case 'e':
            plugin_control(ioh, inst_id, block_id, 1);
            break;
        case 'd':
            plugin_control(ioh, inst_id, block_id, 0);
            break;
        case 't':
            trigger_event(ioh, src_id, event_id, src_opaque);
            break;

        default:
            ALOGE("Invalid usecase");
            break;
    }
exit:
    if (ioh) {
        err = iaxxx_odsp_deinit(ioh);
        if (err != 0) {
            ALOGE("Failed to deinit the odsp HAL");
        }
    }

    return err;
}
