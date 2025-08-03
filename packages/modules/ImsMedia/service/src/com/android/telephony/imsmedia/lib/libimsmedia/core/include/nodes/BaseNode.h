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

#ifndef BASE_NODE_H
#define BASE_NODE_H

#include <stdint.h>
#include <ImsMediaDataQueue.h>
#include <BaseSessionCallback.h>
#include <StreamSchedulerCallback.h>

#define MAX_AUDIO_PAYLOAD_SIZE (1500)
#define MAX_FRAME_IN_PACKET    ((MAX_AUDIO_PAYLOAD_SIZE - 1) / 32)

enum kBaseNodeState
{
    /* the state after stop method called normally*/
    kNodeStateStopped,
    /* the state after start without error*/
    kNodeStateRunning,
};

enum kBaseNodeId
{
    kNodeIdUnknown,
    // for socket
    kNodeIdSocketWriter,
    kNodeIdSocketReader,
    // for rtp
    kNodeIdRtpEncoder,
    kNodeIdRtpDecoder,
    // for rtcp
    kNodeIdRtcpEncoder,
    kNodeIdRtcpDecoder,
    // for Audio
    kNodeIdAudioSource,
    kNodeIdAudioPlayer,
    kNodeIdDtmfEncoder,
    kNodeIdDtmfSender,
    kNodeIdAudioPayloadEncoder,
    kNodeIdAudioPayloadDecoder,
    // for Video
    kNodeIdVideoSource,
    kNodeIdVideoRenderer,
    kNodeIdVideoPayloadEncoder,
    kNodeIdVideoPayloadDecoder,
    // for Text
    kNodeIdTextSource,
    kNodeIdTextRenderer,
    kNodeIdTextPayloadEncoder,
    kNodeIdTextPayloadDecoder,
};

/**
 * @brief BaseNode object
 *
 */
class BaseNode
{
public:
    BaseNode(BaseSessionCallback* callback = nullptr);
    virtual ~BaseNode();
    /**
     * @brief Sets the BaseSession callback listener
     *
     * @param callback the callback instance
     */
    void SetSessionCallback(BaseSessionCallback* callback);

    /**
     * @brief Sets the session scheduler callback listener
     *
     * @param callback the instance of callback listener
     */
    void SetSchedulerCallback(const std::shared_ptr<StreamSchedulerCallback>& callback);

    /**
     * @brief Connects a node to rear to this node. It makes to pass the processed data to next node
     *
     * @param pRearNode The instance of node to connect to next node
     */
    void ConnectRearNode(BaseNode* pRearNode);

    /**
     * @brief Disconnect nodes connected to rear and front
     */
    void DisconnectNodes();

    /**
     * @brief Empty the data queue
     *
     */
    void ClearDataQueue();

    /**
     * @brief Gets the node id to identify the IAudioSourceNoce
     *
     * @return BaseNodeID The node id
     */
    virtual kBaseNodeId GetNodeId();

    /**
     * @brief Prepare the node before start it
     *
     * @return bool Returns true when it starts well without error
     */
    virtual bool Prepare();

    /**
     * @brief Starts to run the node with the configuration already set by the SetConfig method
     *
     * @return ImsMediaResult return RESULT_SUCCESS when it starts well without error
     */
    virtual ImsMediaResult Start();

    /**
     * @brief Starts to run node with the configuration already set by the SetConfig method in
     * scheduler thread
     *
     * @return ImsMediaResult return RESULT_SUCCESS when it starts well without error
     */
    virtual ImsMediaResult ProcessStart();

    /**
     * @brief Stops the node operation
     *
     */
    virtual void Stop() = 0;

    /**
     * @brief Checks the node processes data in main thread.
     */
    virtual bool IsRunTime();

    /**
     * @brief Checks the node to start in main thread
     */
    virtual bool IsRunTimeStart();

    /**
     * @brief Checks the node is initial node of data source
     *
     * @return true It is a source node
     * @return false It is not a source node
     */
    virtual bool IsSourceNode() = 0;

    /**
     * @brief Sets the config to delivers the parameter to use in the node
     *
     * @param config Sets the Audio/Video/TextConfig.
     */
    virtual void SetConfig(void* config);

    /**
     * @brief Compares the config with the member valuables in the node
     *
     * @param config Audio/Video/TextConfig to compare
     * @return true The member valuables in the config is same with the member valuables in the node
     * @return false There is at least one member valuables not same with config
     */
    virtual bool IsSameConfig(void* config);

    /**
     * @brief Updates the node member valuable and re start the running operation with the given
     * config.
     *
     * @param config The Audio/Video/TextConfig to update
     * @return ImsMediaResult Returns RETURN_SUCCESS when the update succeed
     */
    virtual ImsMediaResult UpdateConfig(void* config);

    /**
     * @brief This method is invoked by the thread created in StreamScheduler
     *
     */
    virtual void ProcessData();

    /**
     * @brief Gets the node name with char types
     *
     * @return const char* The node name
     */
    virtual const char* GetNodeName();

    /**
     * @brief Sets the media type
     *
     * @param eType the types can be Audio/Video/Text. Check the type definition.
     */
    virtual void SetMediaType(ImsMediaType eType);

    /**
     * @brief Gets the media type
     *
     * @return ImsMediaType the types of the node
     */
    virtual ImsMediaType GetMediaType();

    /**
     * @brief Gets the state of the node
     *
     * @return kBaseNodeState The returning node states is running or stopped.
     */
    virtual kBaseNodeState GetState();

    virtual void SetState(kBaseNodeState state);
    /**
     * @brief Gets the number of data stored in this node
     *
     * @return uint32_t The data count
     */
    virtual uint32_t GetDataCount();

    /**
     * @brief Gets one data stored in front of data queue in the node
     *
     * @param subtype The subtype of data stored in the queue. It can be various subtype according
     * to the characteristics of the given data
     * @param data The data buffer
     * @param dataSize The size of data
     * @param timestamp The timestamp of data, it can be milliseconds unit or rtp timestamp unit
     * @param mark It is true when the data has marker bit set
     * @param seq The sequence number of data. it is 0 when there is no valid sequence number set
     * @param dataType The additional data type for the video frames
     * @param arrivalTime The arrival time of the packet
     * @return true Succeeds to gets the valid data
     * @return false Fails to gets the valid data
     */
    virtual bool GetData(ImsMediaSubType* subtype, uint8_t** data, uint32_t* dataSize,
            uint32_t* timestamp, bool* mark, uint32_t* seq, ImsMediaSubType* dataType = nullptr,
            uint32_t* arrivalTime = nullptr);

    /**
     * @brief This method is to add data frame to the queue in the node
     *
     * @param data The data buffer
     * @param size The size of data
     * @param timestamp The timestamp of data, it can be milliseconds unit or rtp timestamp unit
     * @param mark It is true when the data has marker bit set
     * @param seq The sequence number of data. it is 0 when there is no valid sequence number set
     * @param subtype The subtype of data stored in the queue. It can be various subtype according
     * to the characteristics of the given data
     * @param dataType The additional data type for the video frames
     * @param arrivalTime The arrival time of the packet
     * @param index The index of the queue to add, if it is not set, add the frame to the end of
     * the queue
     */
    virtual void AddData(uint8_t* data, uint32_t size, uint32_t timestamp, bool mark, uint32_t seq,
            ImsMediaSubType subtype = ImsMediaSubType::MEDIASUBTYPE_UNDEFINED,
            ImsMediaSubType dataType = ImsMediaSubType::MEDIASUBTYPE_UNDEFINED,
            uint32_t arrivalTime = 0, int32_t index = -1);

    /**
     * @brief Deletes the data stored in the front of the data queue
     *
     */
    virtual void DeleteData();

    /**
     * @brief Sends processed data to next node
     *
     * @param subtype The subtype of data stored in the queue. It can be various subtype according
     * to the characteristics of the given data
     * @param data The data buffer
     * @param dataSize The size of data
     * @param timestamp The timestamp of data, it can be milliseconds unit or rtp timestamp unit
     * @param mark It is true when the data has marker bit set
     * @param seq The sequence number of data. it is 0 when there is no valid sequence number set
     * @param dataType The additional data type for the video frames
     * @param arrivalTime The arrival time of the packet in milliseconds unit
     */
    virtual void SendDataToRearNode(ImsMediaSubType subtype, uint8_t* data, uint32_t dataSize,
            uint32_t timestamp, bool mark, uint32_t seq,
            ImsMediaSubType nDataType = ImsMediaSubType::MEDIASUBTYPE_UNDEFINED,
            uint32_t arrivalTime = 0);

    /**
     * @brief This method is invoked when the front node calls SendDataToRearNode to pass the
     * processed data to next connected rear node
     *
     * @param subtype The subtype of data stored in the queue. It can be various subtype according
     * to the characteristics of the given data
     * @param data The data buffer
     * @param dataSize The size of data
     * @param timestamp The timestamp of data, it can be milliseconds unit or rtp timestamp unit
     * @param mark It is true when the data has marker bit set
     * @param seq The sequence number of data. it is 0 when there is no valid sequence number set
     * @param dataType The additional data type for the video frames
     * @param arrivalTime The arrival time of the packet
     */
    virtual void OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData, uint32_t nDataSize,
            uint32_t timestamp, bool mark, uint32_t nSeqNum,
            ImsMediaSubType nDataType = ImsMediaSubType::MEDIASUBTYPE_UNDEFINED,
            uint32_t arrivalTime = 0);

protected:
    /**
     * @brief Disconnects the front node from this node.
     *
     * @param pFrontNode The instance of node to disconnect
     */
    void DisconnectFrontNode(BaseNode* pFrontNode);

    /**
     * @brief Disconnects the rear node from this node.
     *
     * @param pRearNode The instance of node to disconnect.
     */
    void DisconnectRearNode(BaseNode* pRearNode);

    std::shared_ptr<StreamSchedulerCallback> mScheduler;
    BaseSessionCallback* mCallback;
    kBaseNodeState mNodeState;
    ImsMediaDataQueue mDataQueue;
    std::list<BaseNode*> mListFrontNodes;
    std::list<BaseNode*> mListRearNodes;
    ImsMediaType mMediaType;
};

#endif