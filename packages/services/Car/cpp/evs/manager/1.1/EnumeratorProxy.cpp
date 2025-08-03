/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include "EnumeratorProxy.h"

#include <android/hardware/automotive/evs/1.1/IEvsEnumerator.h>

#include <string>
#include <vector>

namespace {

using ::android::sp;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using CameraDesc_1_0 = ::android::hardware::automotive::evs::V1_0::CameraDesc;
using CameraDesc_1_1 = ::android::hardware::automotive::evs::V1_1::CameraDesc;

}  // namespace

namespace android::automotive::evs::V1_1::implementation {

// TODO(b/206829268): As EnumeratorProxy is introduced piece meal the proxied
// methods will be added here (this is being done in order to introduce tests).
#ifdef TEMPORARILY_DISABLE_SEE_B_206829268
hardware::Return<void> EnumeratorProxy::getCameraList(getCameraList_cb hidlCallback) {
    hidlCallback(stlToHidlVec(mEnumeratorManager->getCameraList()));
    return Void();
}

hardware::Return<sp<hardware::automotive::evs::V1_0::IEvsCamera>> EnumeratorProxy::openCamera(
        const hardware::hidl_string& cameraId) {
    return sp<hardware::automotive::evs::V1_0::IEvsCamera>(
            mEnumeratorManager->openCamera(hidlToStlString(cameraId)).release());
}

hardware::Return<void> EnumeratorProxy::closeCamera(
        const sp<hardware::automotive::evs::V1_0::IEvsCamera>& camera) {
    mEnumeratorManager->closeCamera(*camera);
    return Void();
}

hardware::Return<sp<hardware::automotive::evs::V1_0::IEvsDisplay>> EnumeratorProxy::openDisplay() {
    return sp<hardware::automotive::evs::V1_0::IEvsDisplay>(
            mEnumeratorManager->openDisplay().release());
}

hardware::Return<void> EnumeratorProxy::closeDisplay(
        const sp<hardware::automotive::evs::V1_0::IEvsDisplay>& display) {
    mEnumeratorManager->closeDisplay(display.get());

    return Void();
}

hardware::Return<hardware::automotive::evs::V1_0::DisplayState> EnumeratorProxy::getDisplayState() {
    return mEnumeratorManager->getDisplayState();
}

hardware::Return<void> EnumeratorProxy::getCameraList_1_1(
        ::android::hardware::automotive::evs::V1_1::IEvsEnumerator::getCameraList_1_1_cb
                hidlCallback) {
    hidlCallback(stlToHidlVec(mEnumeratorManager->getCameraList_1_1()));
    return Void();
}

hardware::Return<sp<hardware::automotive::evs::V1_1::IEvsCamera>> EnumeratorProxy::openCamera_1_1(
        const hardware::hidl_string& cameraId,
        const hardware::camera::device::V3_2::Stream& streamCfg) {
    return sp<hardware::automotive::evs::V1_1::IEvsCamera>{
            mEnumeratorManager->openCamera_1_1(hidlToStlString(cameraId), streamCfg).release()};
}

hardware::Return<bool> EnumeratorProxy::isHardware() {
    return mEnumeratorManager->isHardware();
}

hardware::Return<void> EnumeratorProxy::getDisplayIdList(
        hardware::automotive::evs::V1_1::IEvsEnumerator::getDisplayIdList_cb list_callback) {
    list_callback(stlToHidlVec(mEnumeratorManager->getDisplayIdList()));

    return Void();
}

hardware::Return<sp<hardware::automotive::evs::V1_1::IEvsDisplay>> EnumeratorProxy::openDisplay_1_1(
        uint8_t id) {
    return sp<hardware::automotive::evs::V1_1::IEvsDisplay>{
            mEnumeratorManager->openDisplay_1_1(id).release()};
}

hardware::Return<void> EnumeratorProxy::getUltrasonicsArrayList(
        ::android::hardware::automotive::evs::V1_1::IEvsEnumerator::getUltrasonicsArrayList_cb
                list_callback) {
    list_callback(stlToHidlVec(mEnumeratorManager->getUltrasonicsArrayList()));

    return Void();
}

hardware::Return<sp<::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray>>
EnumeratorProxy::openUltrasonicsArray(const hardware::hidl_string& ultrasonicsArrayId) {
    return sp<::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray>(
            mEnumeratorManager->openUltrasonicsArray(hidlToStlString(ultrasonicsArrayId))
                    .release());
}

hardware::Return<void> EnumeratorProxy::closeUltrasonicsArray(
        const sp<::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray>&
                evsUltrasonicsArray) {
    mEnumeratorManager->closeUltrasonicsArray(*evsUltrasonicsArray);

    return Void();
}

hardware::Return<void> EnumeratorProxy::debug(
        const ::android::hardware::hidl_handle& fileDescriptor,
        const hidl_vec<hardware::hidl_string>& options) {
    mEnumeratorManager->debug(fileDescriptor, hidlToStlVecOfStrings(options));

    return Void();
}

#endif  // TEMPORARILY_DISABLE_SEE_B_206829268

}  // namespace android::automotive::evs::V1_1::implementation
