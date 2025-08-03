/*
 * Copyright (C) 2019-2020 The LineageOS Project
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

#ifdef LIVES_IN_SYSTEM
#define LOG_TAG "lineage.livedisplay@2.0-service-sysfs"
#else
#define LOG_TAG "vendor.lineage.livedisplay@2.0-service-sysfs"
#endif

#include <android-base/logging.h>
#include <hidl/HidlTransportSupport.h>
#include <livedisplay/sysfs/AdaptiveBacklight.h>
#include <livedisplay/sysfs/DisplayColorCalibration.h>
#include <livedisplay/sysfs/SimpleMode.h>
#include <livedisplay/sysfs/SunlightEnhancement.h>

using ::android::OK;
using ::android::sp;
using ::android::status_t;
using ::android::hardware::configureRpcThreadpool;
using ::android::hardware::joinRpcThreadpool;

using ::vendor::lineage::livedisplay::V2_0::sysfs::AdaptiveBacklight;
using ::vendor::lineage::livedisplay::V2_0::sysfs::AutoContrast;
using ::vendor::lineage::livedisplay::V2_0::sysfs::ColorEnhancement;
using ::vendor::lineage::livedisplay::V2_0::sysfs::DisplayColorCalibration;
using ::vendor::lineage::livedisplay::V2_0::sysfs::ReadingEnhancement;
using ::vendor::lineage::livedisplay::V2_0::sysfs::SunlightEnhancement;

status_t RegisterAsServices() {
    status_t status = OK;

    sp<AdaptiveBacklight> ab = new AdaptiveBacklight();
    if (ab->isSupported()) {
        status = ab->registerAsService();
        if (status != OK) {
            LOG(ERROR) << "Could not register service for LiveDisplay HAL AdaptiveBacklight Iface ("
                       << status << ")";
            return status;
        }
    }

    if (AutoContrast::isSupported()) {
        sp<AutoContrast> ac = new AutoContrast();
        status = ac->registerAsService();
        if (status != OK) {
            LOG(ERROR) << "Could not register service for LiveDisplay HAL AutoContrast Iface ("
                       << status << ")";
            return status;
        }
    }

    if (ColorEnhancement::isSupported()) {
        sp<ColorEnhancement> ce = new ColorEnhancement();
        status = ce->registerAsService();
        if (status != OK) {
            LOG(ERROR) << "Could not register service for LiveDisplay HAL ColorEnhancement Iface ("
                       << status << ")";
            return status;
        }
    }

    if (DisplayColorCalibration::isSupported()) {
        sp<DisplayColorCalibration> dcc = new DisplayColorCalibration();
        status = dcc->registerAsService();
        if (status != OK) {
            LOG(ERROR) << "Could not register service for LiveDisplay HAL DisplayColorCalibration"
                       << " Iface (" << status << ")";
            return status;
        }
    }

    if (ReadingEnhancement::isSupported()) {
        sp<ReadingEnhancement> re = new ReadingEnhancement();
        status = re->registerAsService();
        if (status != OK) {
            LOG(ERROR) << "Could not register service for LiveDisplay HAL ReadingEnhancement Iface"
                       << " (" << status << ")";
            return status;
        }
    }

    sp<SunlightEnhancement> se = new SunlightEnhancement();
    if (se->isSupported()) {
        status = se->registerAsService();
        if (status != OK) {
            LOG(ERROR) << "Could not register service for LiveDisplay HAL SunlightEnhancement Iface"
                       << " (" << status << ")";
            return status;
        }
    }

    return OK;
}

int main() {
    LOG(DEBUG) << "LiveDisplay HAL service is starting.";

    configureRpcThreadpool(1, true /*callerWillJoin*/);

    if (RegisterAsServices() == OK) {
        LOG(DEBUG) << "LiveDisplay HAL service is ready.";
        joinRpcThreadpool();
    } else {
        LOG(ERROR) << "Could not register service for LiveDisplay HAL";
    }

    // In normal operation, we don't expect the thread pool to shutdown
    LOG(ERROR) << "LiveDisplay HAL service is shutting down.";
    return 1;
}
