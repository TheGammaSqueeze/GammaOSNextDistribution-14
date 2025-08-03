/*
 * Copyright 2021 The Android Open Source Project
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
#ifndef ANDROID_CARSERVICE_EVSSERVICECALLBACK_H
#define ANDROID_CARSERVICE_EVSSERVICECALLBACK_H

#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/EvsEventDesc.h>

namespace android::automotive::evs {

/*
 * This abstract class defines callback methods to listen to the native Extended
 * View System service.
 */
class EvsServiceCallback {
public:
    virtual ~EvsServiceCallback(){};

    // Called upon the arrival of the new stream event.
    virtual void onNewEvent(const ::aidl::android::hardware::automotive::evs::EvsEventDesc&) = 0;

    // Called upon the arrival of the new frame.
    virtual bool onNewFrame(const ::aidl::android::hardware::automotive::evs::BufferDesc&) = 0;
};

}  // namespace android::automotive::evs

#endif  // ANDROID_CARSERVICE_EVSSERVICECALLBACK_H
