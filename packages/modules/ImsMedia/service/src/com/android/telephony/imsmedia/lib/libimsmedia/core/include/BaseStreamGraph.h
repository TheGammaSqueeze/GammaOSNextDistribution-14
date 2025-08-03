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

#ifndef BASE_STREAM_GRAPH_H
#define BASE_STREAM_GRAPH_H

#include <ImsMediaDefine.h>
#include <StreamScheduler.h>
#include <BaseNode.h>
#include <BaseSessionCallback.h>
#include <MediaQualityThreshold.h>
#include <RtpConfig.h>
#include <list>

/**
 * @class BaseStreamGraph
 */
class BaseStreamGraph
{
protected:
    virtual ImsMediaResult create(RtpConfig* config) = 0;
    virtual ImsMediaResult update(RtpConfig* config) = 0;
    void AddNode(BaseNode* pNode, bool bReverse = true);
    void RemoveNode(BaseNode* pNode);
    ImsMediaResult startNodes();
    ImsMediaResult stopNodes();
    void deleteNodes();
    BaseNode* findNode(kBaseNodeId id);

public:
    /**
     * @brief Construct
     *
     * @param callback Callback interface to send event to session
     * @param localFd
     */
    BaseStreamGraph(BaseSessionCallback* callback, int localFd = 0);
    virtual ~BaseStreamGraph();

    /**
     * @brief Sets the local socket file descriptor
     *
     * @param localFd socket file descriptor to set
     */
    void setLocalFd(int localFd);

    /**
     * @brief Gets the local socket file descriptor
     *
     * @return int The socket file descriptor
     */
    int getLocalFd();

    /**
     * @brief Starts the nodes in the graph
     *
     * @return ImsMediaResult RESULT_SUCCESS when the start succeeded
     */
    virtual ImsMediaResult start();

    /**
     * @brief Stops the nodes in the graph
     *
     * @return ImsMediaResult RESULT_SUCCESS when the stop succeeded
     */
    virtual ImsMediaResult stop();

    /**
     * @brief Sets the stream state
     *
     * @param state state to update.
     */
    void setState(StreamState state);

    /**
     * @brief Gets the stream state
     *
     * @return StreamState state of stream
     */
    StreamState getState();

    /**
     * @brief Checks StreamGraph is same graph based on the parameter
     *
     * @param config RtpConfig for the StreamGraph operates nodes in the graph
     * @return true The remote IP address and port number is same
     * @return false The remote IP address or port number is not the same
     */
    virtual bool isSameGraph(RtpConfig* config) = 0;

    /**
     * @brief Set the MediaQualityThreshold to the nodes.
     *
     * @param threshold threshold parameter to set.
     */
    virtual bool setMediaQualityThreshold(MediaQualityThreshold* threshold);

    /**
     * @brief Handles event from the session or trigger by the other nodes
     *
     * @param type event type check kImsMediaInternalRequestType in ImsMediaDefine.h
     * @param param1 parameter to set
     * @param param2 parameter to set
     * @return true The event sent to target node successfully
     * @return false The event cannot pass to the target node
     */
    virtual bool OnEvent(int32_t type, uint64_t param1, uint64_t param2);

protected:
    BaseSessionCallback* mCallback;
    int mLocalFd;
    StreamState mGraphState;
    std::list<BaseNode*> mListNodeToStart;
    std::list<BaseNode*> mListNodeStarted;
    std::shared_ptr<StreamScheduler> mScheduler;
};

#endif