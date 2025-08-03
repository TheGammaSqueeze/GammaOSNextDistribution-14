/**
* Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "InputChannelTest"

#include "../includes/common.h"

#include <android-base/stringprintf.h>
#include <input/InputTransport.h>

using namespace android;
using android::base::StringPrintf;

static std::string memoryAsHexString(const void* const address, size_t numBytes) {
    std::string str;
    for (size_t i = 0; i < numBytes; i++) {
        str += StringPrintf("%02X ", static_cast<const uint8_t* const>(address)[i]);
    }
    return str;
}

/**
 * There could be non-zero bytes in-between InputMessage fields. Force-initialize the entire
 * memory to zero, then only copy the valid bytes on a per-field basis.
 * Input: message msg
 * Output: cleaned message outMsg
 */
static void sanitizeMessage(const InputMessage& msg, InputMessage* outMsg) {
    memset(outMsg, 0, sizeof(*outMsg));

    // Write the header
    outMsg->header.type = msg.header.type;
    outMsg->header.seq = msg.header.seq;

    // Write the body
    switch(msg.header.type) {
        case InputMessage::Type::KEY: {
            // int32_t eventId
            outMsg->body.key.eventId = msg.body.key.eventId;
            // nsecs_t eventTime
            outMsg->body.key.eventTime = msg.body.key.eventTime;
            // int32_t deviceId
            outMsg->body.key.deviceId = msg.body.key.deviceId;
            // int32_t source
            outMsg->body.key.source = msg.body.key.source;
            // int32_t displayId
            outMsg->body.key.displayId = msg.body.key.displayId;
            // std::array<uint8_t, 32> hmac
            outMsg->body.key.hmac = msg.body.key.hmac;
            // int32_t action
            outMsg->body.key.action = msg.body.key.action;
            // int32_t flags
            outMsg->body.key.flags = msg.body.key.flags;
            // int32_t keyCode
            outMsg->body.key.keyCode = msg.body.key.keyCode;
            // int32_t scanCode
            outMsg->body.key.scanCode = msg.body.key.scanCode;
            // int32_t metaState
            outMsg->body.key.metaState = msg.body.key.metaState;
            // int32_t repeatCount
            outMsg->body.key.repeatCount = msg.body.key.repeatCount;
            // nsecs_t downTime
            outMsg->body.key.downTime = msg.body.key.downTime;
            break;
        }
        case InputMessage::Type::MOTION: {
            // int32_t eventId
            outMsg->body.motion.eventId = msg.body.key.eventId;
            // uint32_t pointerCount
            outMsg->body.motion.pointerCount = msg.body.motion.pointerCount;
            // nsecs_t eventTime
            outMsg->body.motion.eventTime = msg.body.motion.eventTime;
            // int32_t deviceId
            outMsg->body.motion.deviceId = msg.body.motion.deviceId;
            // int32_t source
            outMsg->body.motion.source = msg.body.motion.source;
            // int32_t displayId
            outMsg->body.motion.displayId = msg.body.motion.displayId;
            // std::array<uint8_t, 32> hmac
            outMsg->body.motion.hmac = msg.body.motion.hmac;
            // int32_t action
            outMsg->body.motion.action = msg.body.motion.action;
            // int32_t actionButton
            outMsg->body.motion.actionButton = msg.body.motion.actionButton;
            // int32_t flags
            outMsg->body.motion.flags = msg.body.motion.flags;
            // int32_t metaState
            outMsg->body.motion.metaState = msg.body.motion.metaState;
            // int32_t buttonState
            outMsg->body.motion.buttonState = msg.body.motion.buttonState;
            // MotionClassification classification
            outMsg->body.motion.classification = msg.body.motion.classification;
            // int32_t edgeFlags
            outMsg->body.motion.edgeFlags = msg.body.motion.edgeFlags;
            // nsecs_t downTime
            outMsg->body.motion.downTime = msg.body.motion.downTime;
            // float dsdx
            outMsg->body.motion.dsdx = msg.body.motion.dsdx;
            // float dtdx
            outMsg->body.motion.dtdx = msg.body.motion.dtdx;
            // float dtdy
            outMsg->body.motion.dtdy = msg.body.motion.dtdy;
            // float dsdy
            outMsg->body.motion.dsdy = msg.body.motion.dsdy;
            // float tx
            outMsg->body.motion.tx = msg.body.motion.tx;
            // float ty
            outMsg->body.motion.ty = msg.body.motion.ty;
            // float xPrecision
            outMsg->body.motion.xPrecision = msg.body.motion.xPrecision;
            // float yPrecision
            outMsg->body.motion.yPrecision = msg.body.motion.yPrecision;
            // float xCursorPosition
            outMsg->body.motion.xCursorPosition = msg.body.motion.xCursorPosition;
            // float yCursorPosition
            outMsg->body.motion.yCursorPosition = msg.body.motion.yCursorPosition;
            // float dsdxDisplay
            outMsg->body.motion.dsdxRaw = msg.body.motion.dsdxRaw;
            // float dtdxDisplay
            outMsg->body.motion.dtdxRaw = msg.body.motion.dtdxRaw;
            // float dtdyDisplay
            outMsg->body.motion.dtdyRaw = msg.body.motion.dtdyRaw;
            // float dsdyDisplay
            outMsg->body.motion.dsdyRaw = msg.body.motion.dsdyRaw;
            // float txDisplay
            outMsg->body.motion.txRaw = msg.body.motion.txRaw;
            // float tyDisplay
            outMsg->body.motion.tyRaw = msg.body.motion.tyRaw;
            //struct Pointer pointers[MAX_POINTERS]
            for (size_t i = 0; i < msg.body.motion.pointerCount; i++) {
                // PointerProperties properties
                outMsg->body.motion.pointers[i].properties.id =
                        msg.body.motion.pointers[i].properties.id;
                outMsg->body.motion.pointers[i].properties.toolType =
                        msg.body.motion.pointers[i].properties.toolType;
                // PointerCoords coords
                outMsg->body.motion.pointers[i].coords.bits =
                        msg.body.motion.pointers[i].coords.bits;
                const uint32_t count = BitSet64::count(msg.body.motion.pointers[i].coords.bits);
                memcpy(&outMsg->body.motion.pointers[i].coords.values[0],
                        &msg.body.motion.pointers[i].coords.values[0],
                        count * sizeof(msg.body.motion.pointers[i].coords.values[0]));
                outMsg->body.motion.pointers[i].coords.isResampled =
                        msg.body.motion.pointers[i].coords.isResampled;
            }
            break;
        }
        case InputMessage::Type::FINISHED: {
            outMsg->body.finished.handled = msg.body.finished.handled;
            outMsg->body.finished.consumeTime = msg.body.finished.consumeTime;
            break;
        }
        case InputMessage::Type::FOCUS: {
            outMsg->body.focus.eventId = msg.body.focus.eventId;
            outMsg->body.focus.hasFocus = msg.body.focus.hasFocus;
            break;
        }
        case InputMessage::Type::CAPTURE: {
            outMsg->body.capture.eventId = msg.body.capture.eventId;
            outMsg->body.capture.pointerCaptureEnabled = msg.body.capture.pointerCaptureEnabled;
            break;
        }
        case InputMessage::Type::DRAG: {
            outMsg->body.capture.eventId = msg.body.capture.eventId;
            outMsg->body.drag.isExiting = msg.body.drag.isExiting;
            outMsg->body.drag.x = msg.body.drag.x;
            outMsg->body.drag.y = msg.body.drag.y;
            break;
        }
        case InputMessage::Type::TIMELINE: {
            outMsg->body.timeline.eventId = msg.body.timeline.eventId;
            outMsg->body.timeline.graphicsTimeline = msg.body.timeline.graphicsTimeline;
            break;
        }
        case InputMessage::Type::TOUCH_MODE: {
            outMsg->body.touchMode.eventId = msg.body.timeline.eventId;
            outMsg->body.touchMode.isInTouchMode = msg.body.touchMode.isInTouchMode;
        }
    }
}

static void makeMessageValid(InputMessage& msg) {
    InputMessage::Type type = msg.header.type;
    if (type == InputMessage::Type::MOTION) {
        // Message is considered invalid if it has more than MAX_POINTERS pointers.
        msg.body.motion.pointerCount = MAX_POINTERS;
    }
    if (type == InputMessage::Type::TIMELINE) {
        // Message is considered invalid if presentTime <= gpuCompletedTime
        msg.body.timeline.graphicsTimeline[GraphicsTimeline::GPU_COMPLETED_TIME] = 10;
        msg.body.timeline.graphicsTimeline[GraphicsTimeline::PRESENT_TIME] = 20;
    }
}

/**
 * Return false if vulnerability is found for a given message type
 */
static bool checkMessage(InputChannel& server, InputChannel& client, InputMessage::Type type) {
    InputMessage serverMsg;
    // Set all potentially uninitialized bytes to 1, for easier comparison

    memset(&serverMsg, 1, sizeof(serverMsg));
    serverMsg.header.type = type;
    makeMessageValid(serverMsg);
    status_t result = server.sendMessage(&serverMsg);
    if (result != OK) {
        ALOGE("Could not send message to the input channel");
        return false;
    }

    InputMessage clientMsg;
    result = client.receiveMessage(&clientMsg);
    if (result != OK) {
        ALOGE("Could not receive message from the input channel");
        return false;
    }
    if (serverMsg.header.type != clientMsg.header.type) {
        ALOGE("Types do not match");
        return false;
    }

    InputMessage sanitizedClientMsg;
    sanitizeMessage(clientMsg, &sanitizedClientMsg);
    if (memcmp(&clientMsg, &sanitizedClientMsg, clientMsg.size()) != 0) {
        ALOGE("Client received un-sanitized message");
        ALOGE("Received message: %s", memoryAsHexString(&clientMsg, clientMsg.size()).c_str());
        ALOGE("Expected message: %s",
                memoryAsHexString(&sanitizedClientMsg, clientMsg.size()).c_str());
        return false;
    }

    return true;
}

/**
 * Create an unsanitized message
 * Send
 * Receive
 * Compare the received message to a sanitized expected message
 * Do this for all message types
 */
int main() {
    std::unique_ptr<InputChannel> server, client;

    status_t result = InputChannel::openInputChannelPair("channel name", server, client);
    if (result != OK) {
        ALOGE("Could not open input channel pair");
        return 0;
    }

    InputMessage::Type types[] = {
            InputMessage::Type::KEY,      InputMessage::Type::MOTION,
            InputMessage::Type::FINISHED, InputMessage::Type::FOCUS,
            InputMessage::Type::CAPTURE,  InputMessage::Type::DRAG,
            InputMessage::Type::TIMELINE, InputMessage::Type::TOUCH_MODE,
    };
    for (InputMessage::Type type : types) {
        bool success = checkMessage(*server, *client, type);
        if (!success) {
            ALOGE("Check message failed for type %i", type);
            return EXIT_VULNERABLE;
        }
    }

    return 0;
}
