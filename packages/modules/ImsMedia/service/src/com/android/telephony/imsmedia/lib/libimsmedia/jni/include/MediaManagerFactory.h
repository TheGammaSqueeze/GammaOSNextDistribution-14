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

#ifndef MEDIA_MANAGER_FACTORY_H
#define MEDIA_MANAGER_FACTORY_H

#include <BaseManager.h>

enum IMS_MEDIA_TYPE
{
    MEDIA_TYPE_AUDIO = 0,
    MEDIA_TYPE_VIDEO,
    MEDIA_TYPE_TEXT,
};

class MediaManagerFactory
{
private:
    MediaManagerFactory();
    ~MediaManagerFactory();

public:
    static BaseManager* getInterface(int mediatype);
};

#endif