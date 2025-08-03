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
#include <sys/ioctl.h>
#include <limits.h>
#include <string.h>
#include <linux/mfd/adnc/iaxxx-module.h>

#define LOG_TAG "ia_sensor_param_test"

#include <cutils/log.h>

#define GETOPT_HELP_CHAR (CHAR_MIN - 2)

#define DEV_NODE "/dev/iaxxx-module-celldrv"

struct ia_sensor_mgr {
    FILE *dev_node;
};

static struct option const long_options[] =
{
 {"setparamid", required_argument, NULL, 's'},
 {"value", required_argument, NULL, 'v'},
 {"getparamid", required_argument, NULL, 'g'},
 {"help", no_argument, NULL, GETOPT_HELP_CHAR},
 {NULL, 0, NULL, 0}
};

void usage() {
    fputs("\
    USAGE -\n\
    -------\n\
    1) sensor_param_test -s <param_id> -v <param_val>\n\
    2) sensor_param_test -g <param_id>\n\
    \n\
    In the first form, set a parameter with a value.\n\
    In the second form, get a value of a parameter\n\
    ", stdout);

    fputs("\n\
    OPTIONS - \n\
    ---------\n\
    -s          Set a parameter using its ID.\n\
    -v          Set this value for the parameter ID that was passed with\n\
                the option '-s'. Using this option alone is invalid.\n\
    -g          Get the value of a parameter using its ID.\n\
    ", stdout);

    exit(EXIT_FAILURE);
}

void set_param(struct ia_sensor_mgr *smd, int param_id, float param_val) {
    int err = 0;
    struct iaxxx_sensor_param sp;

    if (NULL == smd) {
        ALOGE("%s: NULL handle passed", __func__);
        return;
    }

    sp.inst_id      = 0;
    sp.block_id     = 0;

    sp.param_id = param_id;
    sp.param_val = param_val;

    ALOGD("Set sensor param 0x%X with value %f",
          param_id, param_val);
    fprintf(stdout, "Set sensor param 0x%X with value %f\n",
          param_id, param_val);

    err = ioctl(fileno(smd->dev_node), MODULE_SENSOR_SET_PARAM, (unsigned long) &sp);
    if (-1 == err) {
        ALOGE("%s: ERROR: MODULE_SENSOR_SET_PARAM IOCTL failed with error %d(%s)", __func__, errno, strerror(errno));
        return;
    }

}

void get_param(struct ia_sensor_mgr *smd, int param_id) {
    struct iaxxx_sensor_param sp;
    int err = 0;
    ALOGD ("Get param - param_id 0x%X", param_id);

    if (NULL == smd) {
        ALOGE("%s: NULL handle passed", __func__);
        return;
    }

    sp.inst_id      = 0;
    sp.block_id     = 0;

    sp.param_id = param_id;
    err = ioctl(fileno(smd->dev_node), MODULE_SENSOR_GET_PARAM, (unsigned long) &sp);
    if (-1 == err) {
        ALOGE("%s: ERROR: MODULE_SENSOR_GET_PARAM IOCTL failed with error %d(%s)", __func__, errno, strerror(errno));
        return;
    } else {
        ALOGD("Value of param 0x%X is %zu",
              sp.param_id, sp.param_val);
        fprintf(stdout, "Value of param 0x%X is %zu\n",
              sp.param_id, sp.param_val);
    }
}


int main(int argc, char *argv[]) {
    struct ia_sensor_mgr * smd;
    char use_case;
    int param_id = -1;
    int c;
    float param_val;

    if (argc <= 1) {
        usage();
    }

    while ((c = getopt_long (argc, argv, "s:v:g:", long_options, NULL)) != -1) {
        switch (c) {
        case 's':
            if (NULL == optarg) {
                fprintf(stderr, "Incorrect usage, s option requires an argument");
                usage();
            } else {
                param_id = strtol(optarg, NULL, 0);
                use_case = 's';
            }
        break;
        case 'v':
            if (NULL == optarg) {
                fprintf(stderr, "Incorrect usage, v option requires an argument");
                usage();
            } else {
                if ('s' == use_case) {
                    param_val = strtof(optarg, NULL);
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
                param_id = strtol(optarg, NULL, 0);
                use_case = 'g';
            }
        break;
        case GETOPT_HELP_CHAR:
        default:
            usage();
        break;
        }
    }

    smd = (struct ia_sensor_mgr*) malloc(sizeof(struct ia_sensor_mgr));
    if (NULL == smd) {
        ALOGE("%s: ERROR Failed to allocated memory for ia_sensor_mgr", __func__);
        return -EINVAL;
    }

    if((smd->dev_node = fopen(DEV_NODE, "rw")) == NULL) {
        ALOGE("%s: ERROR file %s open for write error: %s\n", __func__, DEV_NODE, strerror(errno));
        free(smd);
        return -EINVAL;
    }


    if ('s' == use_case) {
        set_param(smd, param_id, param_val);
    } else if ('g' == use_case) {
        get_param(smd, param_id);
    }

    if (smd->dev_node) {
        fclose(smd->dev_node);
    }

    if (smd) {
        free(smd);
    }

    return 0;
}
