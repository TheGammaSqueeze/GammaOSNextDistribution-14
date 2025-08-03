/**
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

#ifndef MOCK_BASE_SESSION_CALLBACK_H
#define MOCK_BASE_SESSION_CALLBACK_H

#include <BaseSessionCallback.h>
#include <ImsMediaDefine.h>
#include <gmock/gmock.h>

class MockBaseSessionCallback : public BaseSessionCallback
{
public:
    MockBaseSessionCallback() :
            mFake(nullptr)
    {
    }
    virtual ~MockBaseSessionCallback() {}
    MOCK_METHOD(void, onEvent, (int32_t type, uint64_t param1, uint64_t param2), (override));

    void DelegateToFake()
    {
        ON_CALL(*this, onEvent)
                .WillByDefault(
                        [this](int32_t type, uint64_t param1, uint64_t param2)
                        {
                            if (mFake != nullptr)
                            {
                                mFake->SendEvent(type, param1, param2);
                            }
                        });
    }

    void SetDelegate(BaseSessionCallback* fake) { mFake = fake; }

private:
    BaseSessionCallback* mFake;
};

#endif