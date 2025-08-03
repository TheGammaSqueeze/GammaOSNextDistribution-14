/*
 * Copyright (C) Texas Instruments Incorporated - http://www.ti.com/
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

#ifndef HWC_DEV_H
#define HWC_DEV_H

#include <mutex>
#include <thread>

#include <cstdbool>
#include <cstdint>

#include <hardware/hwcomposer.h>
#include <kms++/kms++.h>

#include "display.h"

typedef struct omap_hwc_module {
    hwc_module_t base;

} omap_hwc_module_t;

typedef struct omap_hwc_device {
    /* static data */
    hwc_composer_device_1_t device;

    std::mutex mutex;

    std::thread* hdmi_thread;
    std::thread* event_thread;

    kms::Card* card;

    HWCDisplay* displays[MAX_DISPLAYS];

    kms::Connector* primaryConector;
    kms::Connector* externalConector;

    drmEventContext evctx;

    const hwc_procs_t* cb_procs;
} omap_hwc_device_t;

#endif /* HWC_DEV_H */
