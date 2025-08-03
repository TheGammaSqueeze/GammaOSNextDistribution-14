/*
 * Copyright 2022 The Android Open Source Project
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
#pragma once

#include <aidl/android/hardware/automotive/evs/IEvsEnumerator.h>

namespace android::automotive::evs {

class IEvsServiceFactory {
public:
    virtual ~IEvsServiceFactory() = default;

    virtual bool init() = 0;
    virtual aidl::android::hardware::automotive::evs::IEvsEnumerator* getService() = 0;
    virtual void clear() = 0;
};

}  // namespace android::automotive::evs
