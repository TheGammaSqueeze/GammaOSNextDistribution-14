/*
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

 /*
  * This file is based on:
  * hardware/interfaces/contexthub/1.0/default/Contexthub.cpp
  * with modifications to connect directly to the NanohubHAL and
  * support endpoints.
  */

#include "NanohubHidlAdapter.h"
#include "nanohub_perdevice.h"

#include <fcntl.h>
#include <inttypes.h>

#include <log/log.h>
#include <utils/String8.h>
#include <sys/stat.h>

#include <android/hardware/contexthub/1.0/IContexthub.h>
#include <hardware/context_hub.h>
#include <sys/endian.h>

#undef LOG_TAG
#define LOG_TAG "NanohubHidlAdapter"

using namespace android::nanohub;

namespace android {
namespace hardware {
namespace contexthub {
namespace V1_0 {
namespace implementation {

static constexpr uint64_t ALL_APPS = UINT64_C(0xFFFFFFFFFFFFFFFF);

Contexthub::Contexthub()
        : mDeathRecipient(new DeathRecipient(this)),
          mIsTransactionPending(false) {
}

bool Contexthub::setOsAppAsDestination(hub_message_t *msg, int hubId) {
    if (!isValidHubId(hubId)) {
        ALOGW("%s: Hub information is null for hubHandle %d",
              __FUNCTION__,
              hubId);
        return false;
    } else {
        msg->app_name = mCachedHubInfo[hubId].osAppName;
        return true;
    }
}

Return<void> Contexthub::getHubs(getHubs_cb _hidl_cb) {
    std::vector<ContextHub> hubs;
    const context_hub_t *hub = nanohub::get_hub_info();

    mCachedHubInfo.clear();

    CachedHubInformation info;
    ContextHub c;

    c.name = hub->name;
    c.vendor = hub->vendor;
    c.toolchain = hub->toolchain;
    c.platformVersion = hub->platform_version;
    c.toolchainVersion = hub->toolchain_version;
    c.hubId = hub->hub_id;
    c.peakMips = hub->peak_mips;
    c.stoppedPowerDrawMw = hub->stopped_power_draw_mw;
    c.sleepPowerDrawMw = hub->sleep_power_draw_mw;
    c.peakPowerDrawMw = hub->peak_power_draw_mw;
    // c.connectedSensors =
    c.maxSupportedMsgLen = hub->max_supported_msg_len;
    // TODO: get this information from nanohub
    c.chrePlatformId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0);
    c.chreApiMajorVersion = 0x01;
    c.chreApiMinorVersion = 0x02;
    c.chrePatchVersion = NANOHUB_OS_PATCH_LEVEL;

    info.callback = nullptr;
    info.osAppName = hub->os_app_name;
    mCachedHubInfo[hub->hub_id] = info;

    hubs.push_back(c);

    _hidl_cb(hubs);
    return Void();
}

Contexthub::DeathRecipient::DeathRecipient(sp<Contexthub> contexthub)
        : mContexthub(contexthub) {}

void Contexthub::DeathRecipient::serviceDied(
        uint64_t cookie,
        const wp<::android::hidl::base::V1_0::IBase>& /*who*/) {
    uint32_t hubId = static_cast<uint32_t>(cookie);
    mContexthub->handleServiceDeath(hubId);
}

bool Contexthub::isValidHubId(uint32_t hubId) {
    if (!mCachedHubInfo.count(hubId)) {
        ALOGW("Hub information not found for hubId %" PRIu32, hubId);
        return false;
    } else {
        return true;
    }
}

sp<IContexthubCallback> Contexthub::getCallBackForHubId(uint32_t hubId) {
    if (!isValidHubId(hubId)) {
        return nullptr;
    } else {
        return mCachedHubInfo[hubId].callback;
    }
}

Return<Result> Contexthub::sendMessageToHub(uint32_t hubId,
                                            const ContextHubMsg &msg) {
    if (!isValidHubId(hubId) || msg.msg.size() > UINT32_MAX) {
        return Result::BAD_PARAMS;
    }

    hub_message_t txMsg = {
        .app_name.id = msg.appName,
        .message_type = msg.msgType,
        .message_len = static_cast<uint32_t>(msg.msg.size()), // Note the check above
        .message = static_cast<const uint8_t *>(msg.msg.data()),
    };

    // Use a dummy to prevent send_message with empty message from failing prematurely
    static uint8_t dummy;
    if (txMsg.message_len == 0 && txMsg.message == nullptr) {
        txMsg.message = &dummy;
    }

    ALOGI("Sending msg of type %" PRIu32 ", size %" PRIu32 " to app 0x%" PRIx64,
          txMsg.message_type,
          txMsg.message_len,
          txMsg.app_name.id);

    if(NanoHub::sendToNanohub(hubId, &txMsg, 0, msg.hostEndPoint) != 0) {
        return Result::TRANSACTION_FAILED;
    }

    return Result::OK;
}

Return<Result> Contexthub::registerCallback(uint32_t hubId,
                                            const sp<IContexthubCallback> &cb) {
    Return<Result> retVal = Result::BAD_PARAMS;

    if (!isValidHubId(hubId)) {
        // Initialized, but hubId is  not valid
        retVal = Result::BAD_PARAMS;
    } else if (NanoHub::subscribeMessages(hubId,
                                          contextHubCb,
                                          this) == 0) {
        // Initialized && valid hub && subscription successful
        if (mCachedHubInfo[hubId].callback != nullptr) {
            ALOGD("Modifying callback for hubId %" PRIu32, hubId);
            mCachedHubInfo[hubId].callback->unlinkToDeath(mDeathRecipient);
        }

        mCachedHubInfo[hubId].callback = cb;
        if (cb != nullptr) {
            Return<bool> linkResult = cb->linkToDeath(mDeathRecipient, hubId);
            bool linkSuccess = linkResult.isOk() ?
                static_cast<bool>(linkResult) : false;
            if (!linkSuccess) {
                ALOGW("Couldn't link death recipient for hubId %" PRIu32,
                      hubId);
            }
        }
        retVal = Result::OK;
    } else {
        // Initalized && valid hubId - but subscription unsuccessful
        // This is likely an internal error in the HAL implementation, but we
        // cannot add more information.
        ALOGW("Could not subscribe to the hub for callback");
        retVal = Result::UNKNOWN_FAILURE;
    }

    return retVal;
}

static bool isValidOsStatus(const uint8_t *msg,
                            size_t msgLen,
                            status_response_t *rsp) {
    // Workaround a bug in some HALs
    if (msgLen == 1) {
        rsp->result = msg[0];
        return true;
    }

    if (msg == nullptr || msgLen != sizeof(*rsp)) {
        ALOGI("Received invalid response (is null : %d, size %zu)",
              msg == nullptr ? 1 : 0,
              msgLen);
        return false;
    }

    memcpy(rsp, msg, sizeof(*rsp));

    // No sanity checks on return values
    return true;
}

int Contexthub::handleOsMessage(sp<IContexthubCallback> cb,
                                uint32_t msgType,
                                const uint8_t *msg,
                                int msgLen,
                                uint32_t transactionId) {
    int retVal = -1;


    switch(msgType) {
        case CONTEXT_HUB_APPS_ENABLE:
        case CONTEXT_HUB_APPS_DISABLE:
        case CONTEXT_HUB_LOAD_APP:
        case CONTEXT_HUB_UNLOAD_APP:
        {
            struct status_response_t rsp;
            TransactionResult result;
            if (isValidOsStatus(msg, msgLen, &rsp) && rsp.result == 0) {
                retVal = 0;
                result = TransactionResult::SUCCESS;
            } else {
                result = TransactionResult::FAILURE;
            }

            mIsTransactionPending = false;
            if (cb != nullptr) {
                cb->handleTxnResult(transactionId, result);
            }
            retVal = 0;
            break;
        }

        case CONTEXT_HUB_QUERY_APPS:
        {
            std::vector<HubAppInfo> apps;
            int numApps = msgLen / sizeof(hub_app_info);
            const hub_app_info *unalignedInfoAddr = reinterpret_cast<const hub_app_info *>(msg);

            for (int i = 0; i < numApps; i++) {
                hub_app_info query_info;
                memcpy(&query_info, &unalignedInfoAddr[i], sizeof(query_info));
                HubAppInfo app;
                app.appId = query_info.app_name.id;
                app.version = query_info.version;
                // TODO :: Add memory ranges

                apps.push_back(app);
            }

            if (cb != nullptr) {
                cb->handleAppsInfo(apps);
            }
            retVal = 0;
            break;
        }

        case CONTEXT_HUB_QUERY_MEMORY:
        {
            // Deferring this use
            retVal = 0;
            break;
        }

        case CONTEXT_HUB_OS_REBOOT:
        {
            mIsTransactionPending = false;
            if (cb != nullptr) {
                cb->handleHubEvent(AsyncEventType::RESTARTED);
            }
            retVal = 0;
            break;
        }

        default:
        {
            retVal = -1;
            break;
        }
      }

      return retVal;
}

void Contexthub::handleServiceDeath(uint32_t hubId) {
    ALOGI("Callback/service died for hubId %" PRIu32, hubId);
    int ret = NanoHub::subscribeMessages(hubId, nullptr, nullptr);
    if (ret != 0) {
        ALOGW("Failed to unregister callback from hubId %" PRIu32 ": %d",
              hubId, ret);
    }
    mCachedHubInfo[hubId].callback.clear();
}

int Contexthub::contextHubCb(uint32_t hubId,
                             const nanohub::HubMessage &rxMsg,
                             void *cookie) {
    Contexthub *obj = static_cast<Contexthub *>(cookie);

    if (!obj->isValidHubId(hubId)) {
        ALOGW("Invalid hub Id %" PRIu32, hubId);
        return -1;
    }

    sp<IContexthubCallback> cb = obj->getCallBackForHubId(hubId);

    if (cb == nullptr) {
        // This should not ever happen
        ALOGW("No callback registered, returning");
        return -1;
    }

    if (rxMsg.message_type < CONTEXT_HUB_TYPE_PRIVATE_MSG_BASE) {
        obj->handleOsMessage(cb,
                             rxMsg.message_type,
                             static_cast<const uint8_t *>(rxMsg.message),
                             rxMsg.message_len,
                             rxMsg.message_transaction_id);
    } else {
        ContextHubMsg msg;

        msg.appName = rxMsg.app_name.id;
        msg.msgType = rxMsg.message_type;
        msg.hostEndPoint = rxMsg.message_endpoint;
        msg.msg = std::vector<uint8_t>(static_cast<const uint8_t *>(rxMsg.message),
                                       static_cast<const uint8_t *>(rxMsg.message) +
                                       rxMsg.message_len);

        cb->handleClientMsg(msg);
    }

    return 0;
}

Return<Result> Contexthub::unloadNanoApp(uint32_t hubId,
                                         uint64_t appId,
                                         uint32_t transactionId) {
    if (mIsTransactionPending) {
        return Result::TRANSACTION_PENDING;
    }

    hub_message_t msg;

    if (setOsAppAsDestination(&msg, hubId) == false) {
        return Result::BAD_PARAMS;
    }

    struct apps_disable_request_t req;

    msg.message_type = CONTEXT_HUB_UNLOAD_APP;
    msg.message_len = sizeof(req);
    msg.message = &req;
    req.app_name.id = appId;

    if(NanoHub::sendToNanohub(hubId,
                              &msg,
                              transactionId,
                              static_cast<uint16_t>(HostEndPoint::UNSPECIFIED)) != 0) {
        return Result::TRANSACTION_FAILED;
    } else {
        mIsTransactionPending = true;
        return Result::OK;
    }
}

Return<Result> Contexthub::loadNanoApp(uint32_t hubId,
                                       const NanoAppBinary& appBinary,
                                       uint32_t transactionId) {
    if (mIsTransactionPending) {
        return Result::TRANSACTION_PENDING;
    }

    hub_message_t hubMsg;

    if (setOsAppAsDestination(&hubMsg, hubId) == false) {
        return Result::BAD_PARAMS;
    }

    // Data from the nanoapp header is passed through HIDL as explicit fields,
    // but the legacy HAL expects it prepended to the binary, therefore we must
    // reconstruct it here prior to passing to the legacy HAL.
    const struct nano_app_binary_t header = {
        .header_version = htole32(1),
        .magic = htole32(NANOAPP_MAGIC),
        .app_id.id = htole64(appBinary.appId),
        .app_version = htole32(appBinary.appVersion),
        .flags = htole32(appBinary.flags),
        .hw_hub_type = htole64(0),
        .target_chre_api_major_version = appBinary.targetChreApiMajorVersion,
        .target_chre_api_minor_version = appBinary.targetChreApiMinorVersion,
    };
    const uint8_t *headerBytes = reinterpret_cast<const uint8_t *>(&header);

    std::vector<uint8_t> binaryWithHeader(appBinary.customBinary);
    binaryWithHeader.insert(binaryWithHeader.begin(),
                            headerBytes,
                            headerBytes + sizeof(header));

    hubMsg.message_type = CONTEXT_HUB_LOAD_APP;
    hubMsg.message_len = binaryWithHeader.size();
    hubMsg.message = binaryWithHeader.data();

    if(NanoHub::sendToNanohub(hubId,
                              &hubMsg,
                              transactionId,
                              static_cast<uint16_t>(HostEndPoint::UNSPECIFIED)) != 0) {
        return Result::TRANSACTION_FAILED;
    } else {
        mIsTransactionPending = true;
        return Result::OK;
    }
}

Return<Result> Contexthub::enableNanoApp(uint32_t hubId,
                                         uint64_t appId,
                                         uint32_t transactionId) {
    if (mIsTransactionPending) {
        return Result::TRANSACTION_PENDING;
    }

    hub_message_t msg;

    if (setOsAppAsDestination(&msg, hubId) == false) {
        return Result::BAD_PARAMS;
    }

    struct apps_enable_request_t req;

    msg.message_type = CONTEXT_HUB_APPS_ENABLE;
    msg.message_len = sizeof(req);
    req.app_name.id = appId;
    msg.message = &req;

    if(NanoHub::sendToNanohub(hubId,
                              &msg,
                              transactionId,
                              static_cast<uint16_t>(HostEndPoint::UNSPECIFIED)) != 0) {
        return Result::TRANSACTION_FAILED;
    } else {
        mIsTransactionPending = true;
        return Result::OK;
    }
}

Return<Result> Contexthub::disableNanoApp(uint32_t hubId,
                                          uint64_t appId,
                                          uint32_t transactionId) {
    if (mIsTransactionPending) {
        return Result::TRANSACTION_PENDING;
    }

    hub_message_t msg;

    if (setOsAppAsDestination(&msg, hubId) == false) {
        return Result::BAD_PARAMS;
    }

    struct apps_disable_request_t req;

    msg.message_type = CONTEXT_HUB_APPS_DISABLE;
    msg.message_len = sizeof(req);
    req.app_name.id = appId;
    msg.message = &req;

    if(NanoHub::sendToNanohub(hubId,
                              &msg,
                              transactionId,
                              static_cast<uint16_t>(HostEndPoint::UNSPECIFIED)) != 0) {
        return Result::TRANSACTION_FAILED;
    } else {
        mIsTransactionPending = true;
        return Result::OK;
    }
}

Return<Result> Contexthub::queryApps(uint32_t hubId) {
    hub_message_t msg;

    if (setOsAppAsDestination(&msg, hubId) == false) {
        ALOGW("Could not find hubId %" PRIu32, hubId);
        return Result::BAD_PARAMS;
    }

    query_apps_request_t payload;
    payload.app_name.id = ALL_APPS; // TODO : Pass this in as a parameter
    msg.message = &payload;
    msg.message_len = sizeof(payload);
    msg.message_type = CONTEXT_HUB_QUERY_APPS;

    if(NanoHub::sendToNanohub(hubId,
                              &msg,
                              0,
                              static_cast<uint16_t>(HostEndPoint::UNSPECIFIED)) != 0) {
        ALOGW("Query Apps sendMessage failed");
        return Result::TRANSACTION_FAILED;
    }

    return Result::OK;
}

IContexthub *HIDL_FETCH_IContexthub(const char *) {
    return new Contexthub();
}

static bool readApp(const char *file, NanoAppBinary *appBinary)
{
    bool success = false;
    int fd = open(file, O_RDONLY);

    if (fd >= 0) {
        struct stat sb;
        if (fstat(fd, &sb) == 0) {
            void *buf = malloc(sb.st_size);
            if (buf != nullptr && read(fd, buf, sb.st_size) == sb.st_size) {
                success = true;
                const struct nano_app_binary_t *header = static_cast<const struct nano_app_binary_t *>(buf);
                appBinary->appId = header->app_id.id;
                appBinary->appVersion = header->app_version;
                appBinary->flags = header->flags;
                appBinary->targetChreApiMajorVersion = header->target_chre_api_major_version;
                appBinary->targetChreApiMinorVersion = header->target_chre_api_minor_version;
                appBinary->customBinary = std::vector<uint8_t>(static_cast<const uint8_t *>(buf) + sizeof(struct nano_app_binary_t), static_cast<const uint8_t *>(buf) + sb.st_size);
            }
            free(buf);
        }
        close(fd);
    }
    return success;
}

Return<void> Contexthub::debug(const hidl_handle& hh_fd,
                               const hidl_vec<hidl_string>& hh_data) {
    if (hh_fd == nullptr || hh_fd->numFds < 1) {
        return Void();
    }

    String8 result;
    int fd = hh_fd.getNativeHandle()->data[0];

    if (hh_data.size() == 0) {
        result.appendFormat("debug: %d\n", NanoHub::getDebugFlags());
        std::string appInfo;
        NanoHub::dumpAppInfo(appInfo);
        result.append(appInfo.c_str());
    } else if (hh_data.size() == 1) {
        NanoHub::setDebugFlags(atoi(hh_data[0].c_str()));
        result.appendFormat("debug: %d\n", NanoHub::getDebugFlags());
    } else if (hh_data.size() == 2) {
        if (strncmp(hh_data[0].c_str(), "load", 4) == 0) {
            NanoAppBinary appBinary;
            if (readApp(hh_data[1].c_str(), &appBinary))
                loadNanoApp(0, appBinary, 0);
        } else if (strncmp(hh_data[0].c_str(), "unload", 6) == 0) {
            unloadNanoApp(0, strtoul(hh_data[1].c_str(), NULL, 16), 0);
        } else if (strncmp(hh_data[0].c_str(), "enable", 6) == 0) {
            enableNanoApp(0, strtoul(hh_data[1].c_str(), NULL, 16), 0);
        } else if (strncmp(hh_data[0].c_str(), "disable", 7) == 0) {
            disableNanoApp(0, strtoul(hh_data[1].c_str(), NULL, 16), 0);
        }
    } else {
        result.appendFormat("unknown debug options");
    }
    write(fd, result.c_str(), result.size());

    return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace contexthub
}  // namespace hardware
}  // namespace android
