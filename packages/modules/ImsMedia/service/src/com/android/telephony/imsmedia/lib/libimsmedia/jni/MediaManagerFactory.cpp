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

#include <MediaManagerFactory.h>
#include <AudioManager.h>
#include <VideoManager.h>
#include <TextManager.h>

BaseManager* MediaManagerFactory::getInterface(int mediatype)
{
    BaseManager* pManager = nullptr;

    switch ((IMS_MEDIA_TYPE)mediatype)
    {
        case MEDIA_TYPE_AUDIO:
            pManager = AudioManager::getInstance();
            break;
        case MEDIA_TYPE_VIDEO:
            pManager = VideoManager::getInstance();
            break;
        case MEDIA_TYPE_TEXT:
            pManager = TextManager::getInstance();
            break;
        default:
            return nullptr;
    }

    return pManager;
}
