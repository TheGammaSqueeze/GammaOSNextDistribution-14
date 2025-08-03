/*
 * Copyright (C) 2019 Knowles Electronics
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
#include <sys/ioctl.h>
#include <errno.h>
#include <getopt.h>
#include <stdbool.h>
#include <limits.h>
#include <string.h>
#include <inttypes.h>
#include <linux/mfd/adnc/iaxxx-module.h>

#define LOG_TAG "ia_crash_trigger_test"

#include <log/log.h>

#define GETOPT_HELP_CHAR (CHAR_MIN - 2)

#define DEV_NODE "/dev/iaxxx-module-celldrv"

#define CM4_SCRIPT_ID      0x1205
#define HMD_SCRIPT_ID      0x1206
#define DMX_SCRIPT_ID      0

struct ia_script_mgr {
    FILE *dev_node;
};

static struct option const long_options[] =
{
 {"simulate CM4 crash", no_argument, NULL, 'c'},
 {"simulate HMD crash", no_argument, NULL, 'h'},
 {"simulate DMX crash", no_argument, NULL, 'd'},
 {"help", no_argument, NULL, GETOPT_HELP_CHAR},
 {NULL, 0, NULL, 0}
};

void usage() {
    fputs("\
    USAGE -\n\
    -------\n\
    1) crash_trigger_test -c \n\
    2) crash_trigger_test -h \n\
    3) crash_trigger_test -d \n\
    \n\
    In the first form, simulate CM4 crash by script trigger\n\
    In the second form, simulate HMD crash by script trigger\n\
    In the third form, simulate DMX crash by script trigger\n\
    ", stdout);

    fputs("\n\
    OPTIONS - \n\
    ---------\n\
    -c          Simulate CM4 FW crash.\n\
    -h          Simulate HMD FW crash.\n\
    -d          Simulate DMX FW crash.\n\
    ", stdout);

    exit(EXIT_FAILURE);
}

int main(int argc, char *argv[]) {
    struct ia_script_mgr * smd = NULL;
    char use_case;
    uint16_t script_id;
    int g;
    int err = 0;

    if (argc <= 1) {
        usage();
    }

    while ((g = getopt_long (argc, argv, "chd", long_options, NULL)) != -1) {
        switch (g) {
        case 'c':
            use_case = 'c';
        break;
        case 'h':
            use_case = 'h';
        break;
        case 'd':
            use_case = 'd';
        break;
        case GETOPT_HELP_CHAR:
        default:
            usage();
        break;
        }
    }

    smd = (struct ia_script_mgr*) malloc(sizeof(struct ia_script_mgr));
    if (NULL == smd) {
        ALOGE("%s: ERROR Failed to allocated memory for ia_script_mgr",
                __func__);
        return -ENOMEM;
    }

    if((smd->dev_node = fopen(DEV_NODE, "rw")) == NULL) {
        ALOGE("%s: ERROR file %s open for write error: %s\n",
                __func__, DEV_NODE, strerror(errno));
        err = -EBADFD;
        goto out;
    }

    if ('c' == use_case) {
        // Trigger CM4 crash
        script_id = CM4_SCRIPT_ID;
    } else if ('h' == use_case) {
        // Trigger HMD crash
        script_id = HMD_SCRIPT_ID;
    } else if ('d' == use_case) {
        // Trigger DMX crash
        script_id = DMX_SCRIPT_ID;
    }
    err = ioctl(fileno(smd->dev_node), SCRIPT_TRIGGER, (unsigned long) &script_id);
    if ( 0 != err) {
        ALOGE("%s: ERROR: SCRIPT EXECUTE failed %d(%s): script_id: %x\n", __func__, errno, strerror(errno), script_id);
        goto out;
    }
    ALOGI("%s: script_id: %x SCRIPT TRIGGER success\n", __func__, script_id);

out:

    if (smd) {
        if (smd->dev_node) {
            fclose(smd->dev_node);
        }
        free(smd);
        smd = NULL;
    }

    return err;
}
