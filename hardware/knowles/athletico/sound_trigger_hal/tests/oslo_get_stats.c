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

#define LOG_TAG "oslo_get_stats"

#include <errno.h>
#include <log/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/ioctl.h>

#include "oslo_iaxxx_sensor_control.h"

int main(int argc, char *argv[]) {
    (void)(argc);
    (void)(argv);
    int rc = 0;
    struct ia_sensor_mgr *smd;
    struct iaxxx_sensor_mode_stats sensor_stats[SENSOR_NUM_MODE];
    int i;

    smd = iaxxx_sensor_mgr_init();
    if (NULL == smd) {
        fprintf(stderr, "%s: ERROR Failed to init ia_sensor_mgr\n", __func__);
        ALOGE("%s: ERROR Failed to init ia_sensor_mgr\n", __func__);
        rc = -ENOMEM;
        goto out;
    }

    oslo_driver_set_param(smd, SENSOR_PARAM_DUMP_STATS, 1);
    rc = oslo_driver_get_stats(smd, sensor_stats);
    if (rc != 0) {
        fprintf(stderr, "%s: ERROR Failed to get stats\n", __func__);
        ALOGE("%s: ERROR Failed to get stats\n", __func__);
        goto out_err_get_stats;
    }

    for (i = 0; i < SENSOR_NUM_MODE; i++) {
        fprintf(stdout, "Stats for Sensor Mode %d\n", i);
        ALOGI("Stats for Sensor Mode %d\n", i);

        fprintf(stdout, "--------------------------\n");
        ALOGI("--------------------------\n");

        fprintf(stdout, "Total Number of Entries is %" PRIu64 "\n",
                sensor_stats[i].totalNumEntries);
        ALOGI("Total Number of Entries is %" PRIu64 "\n", sensor_stats[i].totalNumEntries);

        fprintf(stdout, "Total time spent (in Ms) is %" PRIu64 "\n",
                sensor_stats[i].totalTimeSpentMs);
        ALOGI("Total time spent (in Ms) is %" PRIu64 "\n", sensor_stats[i].totalTimeSpentMs);

        fprintf(stdout, "Time stamp for Last Entry is %" PRIu64 "\n",
                sensor_stats[i].lastEntryTimeStampMs);
        ALOGI("Time stamp for Last Entry is %" PRIu64 "\n", sensor_stats[i].lastEntryTimeStampMs);

        fprintf(stdout, "Time stamp for Last Exit is %" PRIu64 "\n",
                sensor_stats[i].lastExitTimeStampMs);
        ALOGI("Time stamp for Last Exit is %" PRIu64 "\n", sensor_stats[i].lastExitTimeStampMs);

        fprintf(stdout, "\n");
        ALOGD("\n");
    }

out_err_get_stats:
    iaxxx_sensor_mgr_deinit(smd);
out:

    return rc;
}
