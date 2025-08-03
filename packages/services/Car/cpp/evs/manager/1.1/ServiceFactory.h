/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef CPP_EVS_MANAGER_1_1_SERVICEFACTORY_H_
#define CPP_EVS_MANAGER_1_1_SERVICEFACTORY_H_

#include <android/hardware/automotive/evs/1.1/IEvsEnumerator.h>

namespace android::automotive::evs::V1_1::implementation {

// Provides services given names (used to remove static cling of getService).
class ServiceFactory {
public:
    virtual ~ServiceFactory() = default;

    virtual ::android::hardware::automotive::evs::V1_1::IEvsEnumerator* getService() = 0;
};

}  // namespace android::automotive::evs::V1_1::implementation

#endif  // CPP_EVS_MANAGER_1_1_SERVICEFACTORY_H_
