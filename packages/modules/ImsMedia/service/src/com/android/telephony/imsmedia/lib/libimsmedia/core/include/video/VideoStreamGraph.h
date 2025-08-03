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

#ifndef VIDEO_STREAM_GRAPH_H
#define VIDEO_STREAM_GRAPH_H

#include <ImsMediaDefine.h>
#include <BaseStreamGraph.h>
#include <VideoConfig.h>

class VideoStreamGraph : public BaseStreamGraph
{
public:
    VideoStreamGraph(BaseSessionCallback* callback, int localFd = 0) :
            BaseStreamGraph(callback, localFd),
            mConfig(nullptr)
    {
    }

    virtual ~VideoStreamGraph()
    {
        if (mConfig != nullptr)
        {
            delete mConfig;
            mConfig = nullptr;
        }
    }

    virtual bool isSameGraph(RtpConfig* config)
    {
        if (config == nullptr || mConfig == nullptr)
        {
            return false;
        }

        return (mConfig->getRemoteAddress() == config->getRemoteAddress() &&
                mConfig->getRemotePort() == config->getRemotePort());
    }

protected:
    VideoConfig* mConfig;
};

#endif