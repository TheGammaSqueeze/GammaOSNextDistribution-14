/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "CapoDetector.h"

#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <google/protobuf/message.h>
#include <log/log.h>

#ifdef LOG_TAG
#undef LOG_TAG
#define LOG_TAG "CapoDetector"
#endif

namespace android {
namespace chre {

/**
 * Called when initializing connection with CHRE socket.
 */
sp<CapoDetector> CapoDetector::start() {
    sp<CapoDetector> listener = new CapoDetector();
    if (!listener->connectInBackground(kChreSocketName, listener)) {
        ALOGE("Couldn't connect to CHRE socket");
        return nullptr;
    }
    ALOGI("%s connect to CHRE socket.", __func__);

    return listener;
}

/**
 * Called when the socket is successfully (re-)connected.
 * Reset the position and try to send NanoappList request.
 */
void CapoDetector::onConnected() {
    flatbuffers::FlatBufferBuilder builder;

    // Reset the last position type.
    last_position_type_ = capo::PositionType::UNKNOWN;

    HostProtocolHost::encodeNanoappListRequest(builder);
    if (!sendMessage(builder.GetBufferPointer(), builder.GetSize())) {
        ALOGE("Failed to send NanoappList request");
        // We don't return nullptr here so that we don't change the behavior
    }
}

/**
 * Called when we have failed to (re-)connect the socket after many attempts
 * and are giving up.
 */
void CapoDetector::onConnectionAborted() {
    ALOGE("%s, Capo Aborting Connection!", __func__);
}

/**
 * Invoked when the socket is disconnected, and this connection loss was not
 * the result of an explicit call to disconnect().
 * Reset the position while disconnecting.
 */

void CapoDetector::onDisconnected() {
    last_position_type_ = capo::PositionType::UNKNOWN;
}

/**
 * Decode unix socket msgs to CHRE messages, and call the appropriate
 * callback depending on the CHRE message.
 */
void CapoDetector::onMessageReceived(const void *data, size_t length) {
    if (!HostProtocolHost::decodeMessageFromChre(data, length, *this)) {
        ALOGE("Failed to decode message");
    }
}

/**
 * Listen for messages from capo nanoapp and handle the message.
 */
void CapoDetector::handleNanoappMessage(const fbs::NanoappMessageT &message) {
    ALOGI("%s, Id %" PRIu64 ", type %d, size %d", __func__, message.app_id, message.message_type,
          static_cast<int>(message.message.size()));
    // Exclude the message with unmatched nanoapp id.
    if (message.app_id != kCapoNanoappId)
        return;

    // Handle the message with message_type.
    switch (message.message_type) {
        case capo::MessageType::ACK_NOTIFICATION: {
            capo::AckNotification gd;
            gd.set_notification_type(static_cast<capo::NotificationType>(message.message[1]));
            ALOGD("%s, get notification event from capo nanoapp, type %d", __func__,
                  gd.notification_type());
            break;
        }
        case capo::MessageType::POSITION_DETECTED: {
            uint8_t position;
            uint32_t time;
            {
                std::lock_guard<std::mutex> lock(mCapoMutex);
                capo::PositionDetected gd;
                time = getCurrentTimeInMs();
                gd.set_position_type(static_cast<capo::PositionType>(message.message[1]));
                ALOGD("CapoDetector: [%u] get position event from capo nanoapp, from %d to %d",
                      time, last_position_type_, gd.position_type());

                // Record the last moment we were in FACE_UP state
                if (last_position_type_ == capo::PositionType::ON_TABLE_FACE_UP ||
                    gd.position_type() == capo::PositionType::ON_TABLE_FACE_UP) {
                    mLastFaceUpEvent = time;
                }
                last_position_type_ = gd.position_type();
                position = last_position_type_;
            }
            // Callback to function while getting carried position event.
            if (callback_func_ != nullptr) {
                ALOGD("%s, sent position type %d to callback function", __func__,
                      last_position_type_);
                callback_func_(last_position_type_);
            }
            break;
        }
        default:
            ALOGE("%s, get invalid message, type: %" PRIu32 ", from capo nanoapp.", __func__,
                  message.message_type);
            break;
    }
}

/**
 * Handle the response of a NanoappList request.
 * Ensure that capo nanoapp is running.
 */
void CapoDetector::handleNanoappListResponse(const fbs::NanoappListResponseT &response) {
    for (const std::unique_ptr<fbs::NanoappListEntryT> &nanoapp : response.nanoapps) {
        if (nanoapp->app_id == kCapoNanoappId) {
            if (nanoapp->enabled)
                enable();
            else
                ALOGE("Capo nanoapp not enabled");
            return;
        }
    }
    ALOGE("Capo nanoapp not found");
}

/**
 * Send enabling message to the nanoapp.
 */
void CapoDetector::enable() {
    // Create CHRE message with serialized message
    flatbuffers::FlatBufferBuilder builder, config_builder, force_builder;

    auto config_data = std::make_unique<capo::ConfigureDetector_ConfigData>();
    auto msg = std::make_unique<capo::ConfigureDetector>();

    config_data->set_still_time_threshold_nanosecond(
            mCapoDetectorMDParameters.still_time_threshold_ns);
    config_data->set_window_width_nanosecond(mCapoDetectorMDParameters.window_width_ns);
    config_data->set_motion_confidence_threshold(
            mCapoDetectorMDParameters.motion_confidence_threshold);
    config_data->set_still_confidence_threshold(
            mCapoDetectorMDParameters.still_confidence_threshold);
    config_data->set_var_threshold(mCapoDetectorMDParameters.var_threshold);
    config_data->set_var_threshold_delta(mCapoDetectorMDParameters.var_threshold_delta);

    msg->set_allocated_config_data(config_data.release());

    auto pb_size = msg->ByteSizeLong();
    auto pb_data = std::make_unique<uint8_t[]>(pb_size);

    if (!msg->SerializeToArray(pb_data.get(), pb_size)) {
        ALOGE("Failed to serialize message.");
    }

    ALOGI("Configuring CapoDetector");
    // Configure the detector from host-side
    ::android::chre::HostProtocolHost::encodeNanoappMessage(
            config_builder, getNanoppAppId(), capo::MessageType::CONFIGURE_DETECTOR,
            getHostEndPoint(), pb_data.get(), pb_size);
    ALOGI("Sending capo config message to Nanoapp, %" PRIu32 " bytes", config_builder.GetSize());
    if (!sendMessage(config_builder.GetBufferPointer(), config_builder.GetSize())) {
        ALOGE("Failed to send config event for capo nanoapp");
    }

    ALOGI("Enabling CapoDetector");
    ::android::chre::HostProtocolHost::encodeNanoappMessage(
            builder, getNanoppAppId(), capo::MessageType::ENABLE_DETECTOR, getHostEndPoint(),
            /*messageData*/ nullptr, /*messageDataLenbuffer*/ 0);
    ALOGI("Sending enable message to Nanoapp, %" PRIu32 " bytes", builder.GetSize());
    if (!sendMessage(builder.GetBufferPointer(), builder.GetSize())) {
        ALOGE("Failed to send enable event for capo nanoapp");
    }

    ALOGI("Forcing CapoDetector to update state");
    // Force an updated state upon connection
    ::android::chre::HostProtocolHost::encodeNanoappMessage(
            force_builder, getNanoppAppId(), capo::MessageType::FORCE_UPDATE, getHostEndPoint(),
            /*messageData*/ nullptr, /*messageDataLenbuffer*/ 0);
    ALOGI("Sending force-update message to Nanoapp, %" PRIu32 " bytes", force_builder.GetSize());
    if (!sendMessage(force_builder.GetBufferPointer(), force_builder.GetSize())) {
        ALOGE("Failed to send force-update event for capo nanoapp");
    }
}

/**
 * Method for gathering the position and time tuple simultaneously to avoid any
 * concurrency issues.
 */
void CapoDetector::getCarriedPositionInfo(uint8_t *position, uint32_t *time) {
    std::lock_guard<std::mutex> lock(mCapoMutex);
    if (position)
        *position = last_position_type_;
    if (time)
        *time = mLastFaceUpEvent;
}

}  // namespace chre
}  // namespace android
