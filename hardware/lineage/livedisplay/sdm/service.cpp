/*
 * Copyright (C) 2019-2021 The LineageOS Project
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
#define LOG_TAG "lineage.livedisplay@2.0-service-sdm"
#else
#define LOG_TAG "vendor.lineage.livedisplay@2.0-service-sdm"
#endif

#include <android-base/logging.h>
#include <binder/ProcessState.h>
#include <hidl/HidlTransportSupport.h>
#include <livedisplay/sdm/DisplayModes.h>
#include <livedisplay/sdm/PictureAdjustment.h>
#include <livedisplay/sdm/SDMController.h>

using ::android::OK;
using ::android::sp;
using ::android::status_t;
using ::android::hardware::configureRpcThreadpool;
using ::android::hardware::joinRpcThreadpool;

using ::vendor::lineage::livedisplay::V2_0::sdm::DisplayModes;
using ::vendor::lineage::livedisplay::V2_0::sdm::PictureAdjustment;
using ::vendor::lineage::livedisplay::V2_0::sdm::SDMController;

status_t RegisterAsServices() {
    status_t status = OK;
    std::shared_ptr<SDMController> controller = std::make_shared<SDMController>();

    sp<PictureAdjustment> pa = new PictureAdjustment(controller);
    status = pa->registerAsService();
    if (status != OK) {
        LOG(ERROR) << "Could not register service for LiveDisplay HAL PictureAdjustment Iface ("
                   << status << ")";
        return status;
    }

    if (DisplayModes::isSupported()) {
        sp<DisplayModes> dm = new DisplayModes(controller);
        status = dm->registerAsService();
        if (status != OK) {
            LOG(ERROR) << "Could not register service for LiveDisplay HAL DisplayModes Iface ("
                       << status << ")";
            return status;
        }
        // Update default PA on setDisplayMode
        dm->registerDisplayModeSetCallback(
                std::bind(&PictureAdjustment::updateDefaultPictureAdjustment, pa));
    }

    return OK;
}

int main() {
#ifdef LIVES_IN_SYSTEM
    android::ProcessState::initWithDriver("/dev/binder");
#else
    android::ProcessState::initWithDriver("/dev/vndbinder");
#endif

    LOG(INFO) << "LiveDisplay HAL service is starting.";

    configureRpcThreadpool(1, true /*callerWillJoin*/);

    if (RegisterAsServices() == OK) {
        LOG(INFO) << "LiveDisplay HAL service is ready.";
        joinRpcThreadpool();
    } else {
        LOG(ERROR) << "Could not register service for LiveDisplay HAL";
    }

    // In normal operation, we don't expect the thread pool to shutdown
    LOG(ERROR) << "LiveDisplay HAL service is shutting down.";
    return 1;
}
