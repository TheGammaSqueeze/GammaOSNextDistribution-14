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

#ifndef BASE_SESSION_CALLBACK_H
#define BASE_SESSION_CALLBACK_H

#include <ImsMediaDefine.h>

struct SessionCallbackParameter
{
public:
    SessionCallbackParameter(uint32_t t = 0, uint32_t p1 = 0, uint32_t p2 = 0) :
            type(t),
            param1(p1),
            param2(p2)
    {
    }
    uint32_t type;
    uint32_t param1;
    uint32_t param2;
};

class BaseSessionCallback
{
public:
    BaseSessionCallback() {}
    virtual ~BaseSessionCallback() {}
    virtual void SendEvent(int32_t type, uint64_t param1 = 0, uint64_t param2 = 0)
    {
        onEvent(type, param1, param2);
    }

protected:
    virtual void onEvent(int32_t type, uint64_t param1, uint64_t param2) = 0;
};

#endif