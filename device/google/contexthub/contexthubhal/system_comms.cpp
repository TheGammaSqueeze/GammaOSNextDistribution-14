/*
 * Copyright (C) 2016 The Android Open Source Project
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

#define LOG_TAG "NanohubHAL"

#include "file.h"
#include <json/json.h>

#include <cassert>
#include <cerrno>
#include <cinttypes>
#include <string>

#include <endian.h>

#include <vector>

#include <log/log.h>

#include <endian.h>
#include <fcntl.h>
#include <sys/stat.h>

#include <media/stagefright/foundation/ADebug.h>

#include <hardware/context_hub.h>
#include "nanohub_perdevice.h"
#include "system_comms.h"
#include "nanohubhal.h"

#define CHRE_APP_DIR        "/data/vendor/sensor/chre"
#define CHRE_APP_DIR_PERMS  (S_IRUSR | S_IWUSR | S_IXUSR)
#define CHRE_APP_FILE_PERMS (S_IRUSR | S_IWUSR)
#define CHRE_APP_SETTINGS   CHRE_APP_DIR "/apps.json"

namespace android {

namespace nanohub {

static void readAppName(MessageBuf &buf, hub_app_name_t &name)
{
    name.id = buf.readU64();
}

static void writeAppName(MessageBuf &buf, const hub_app_name_t &name)
{
    buf.writeU64(name.id);
}

static void readNanohubMemInfo(MessageBuf &buf, NanohubMemInfo &mi)
{
    uint8_t type, len;
    uint32_t ramFree = NANOHUB_MEM_SZ_UNKNOWN;
    uint32_t eeFree = NANOHUB_MEM_SZ_UNKNOWN;
    uint32_t sharedFree = NANOHUB_MEM_SZ_UNKNOWN;
    uint32_t osFree = NANOHUB_MEM_SZ_UNKNOWN;

    mi.flashSz = NANOHUB_MEM_SZ_UNKNOWN;
    mi.blSz = NANOHUB_MEM_SZ_UNKNOWN;
    mi.osSz = NANOHUB_MEM_SZ_UNKNOWN;
    mi.sharedSz = NANOHUB_MEM_SZ_UNKNOWN;
    mi.eeSz = NANOHUB_MEM_SZ_UNKNOWN;
    mi.ramSz = NANOHUB_MEM_SZ_UNKNOWN;

    mi.blUse = NANOHUB_MEM_SZ_UNKNOWN;
    mi.osUse = NANOHUB_MEM_SZ_UNKNOWN;
    mi.sharedUse = NANOHUB_MEM_SZ_UNKNOWN;
    mi.eeUse = NANOHUB_MEM_SZ_UNKNOWN;
    mi.ramUse = NANOHUB_MEM_SZ_UNKNOWN;

    while (buf.getRoom() >= 2) {
        type = buf.readU8();
        len = buf.readU8();
        if (buf.getRoom() >= len) {
            switch(type) {
            case NANOHUB_HAL_SYS_INFO_HEAP_FREE:
                if (len == sizeof(ramFree))
                    ramFree = buf.readU32();
                else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_SYS_INFO_RAM_SIZE:
                if (len == sizeof(mi.ramSz))
                    mi.ramSz = buf.readU32();
                else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_SYS_INFO_EEDATA_SIZE:
                if (len == sizeof(mi.ramSz))
                    mi.eeSz = buf.readU32();
                else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_SYS_INFO_EEDATA_FREE:
                if (len == sizeof(eeFree))
                    eeFree = buf.readU32();
                else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_SYS_INFO_CODE_SIZE:
                if (len == sizeof(mi.osSz))
                    mi.osSz = buf.readU32();
                else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_SYS_INFO_CODE_FREE:
                if (len == sizeof(osFree))
                    osFree = buf.readU32();
                else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_SYS_INFO_SHARED_SIZE:
                if (len == sizeof(mi.sharedSz))
                    mi.sharedSz = buf.readU32();
                else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_SYS_INFO_SHARED_FREE:
                if (len == sizeof(sharedFree))
                    sharedFree = buf.readU32();
                else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_SYS_INFO_END:
                if (len != 0 || buf.getRoom() != 0) {
                    ALOGE("%s: failed to read object", __func__);
                    return;
                }
                break;
            default:
                ALOGI("%s: unsupported type: %d", __func__, type);
                buf.readRaw(len);
                break;
            }
        } else {
            ALOGE("%s: failed to read object", __func__);
            return;
        }
    }

    if (buf.getRoom() != 0) {
        ALOGE("%s: failed to read object", __func__);
        return;
    }

    if (mi.ramSz != NANOHUB_MEM_SZ_UNKNOWN && ramFree != NANOHUB_MEM_SZ_UNKNOWN)
        mi.ramUse = mi.ramSz - ramFree;
    if (mi.eeSz != NANOHUB_MEM_SZ_UNKNOWN && eeFree != NANOHUB_MEM_SZ_UNKNOWN)
        mi.eeUse = mi.eeSz - eeFree;
    if (mi.osSz != NANOHUB_MEM_SZ_UNKNOWN && osFree != NANOHUB_MEM_SZ_UNKNOWN)
        mi.osUse = mi.osSz - osFree;
    if (mi.sharedSz != NANOHUB_MEM_SZ_UNKNOWN && sharedFree != NANOHUB_MEM_SZ_UNKNOWN)
        mi.sharedUse = mi.sharedSz - sharedFree;
}

NanohubRsp::NanohubRsp(MessageBuf &buf, uint32_t transactionId, bool chre)
{
    // all responses start with command and have a 4-byte status (result code)
    buf.reset();
    if (buf.getSize() < 5) {
        mStatus = -EINVAL;
    } else {
        mCmd = buf.readU8();
        mStatus = buf.readU32();
        if (chre)
            mTransactionId = transactionId;
        else
            mTransactionId = 0;
    }
}

int SystemComm::sendToSystem(const void *data, size_t len, uint32_t transactionId)
{
    if (NanoHub::messageTracingEnabled()) {
        dumpBuffer("HAL -> SYS", getSystem()->mHostIfAppName, transactionId, 0, data, len);
    }
    return NanoHub::sendToDevice(&getSystem()->mHostIfAppName, data, len, transactionId);
}

inline hub_app_name_t deviceAppNameToHost(const hub_app_name_t src)
{
    hub_app_name_t res = { .id = le64toh(src.id) };
    return res;
}

inline hub_app_name_t hostAppNameToDevice(const hub_app_name_t src)
{
    hub_app_name_t res = { .id = htole64(src.id) };
    return res;
}

const uint8_t app_info_tags[] =
{
    NANOHUB_HAL_APP_INFO_APPID,
    NANOHUB_HAL_APP_INFO_CRC,
    NANOHUB_HAL_APP_INFO_TID,
    NANOHUB_HAL_APP_INFO_VERSION,
    NANOHUB_HAL_APP_INFO_ADDR,
    NANOHUB_HAL_APP_INFO_SIZE,
    NANOHUB_HAL_APP_INFO_HEAP,
    NANOHUB_HAL_APP_INFO_DATA,
    NANOHUB_HAL_APP_INFO_BSS,
    NANOHUB_HAL_APP_INFO_CHRE_MAJOR,
    NANOHUB_HAL_APP_INFO_CHRE_MINOR,
    NANOHUB_HAL_APP_INFO_END,
};

const uint8_t sys_info_tags[] =
{
    NANOHUB_HAL_SYS_INFO_HEAP_FREE,
    NANOHUB_HAL_SYS_INFO_RAM_SIZE,
    NANOHUB_HAL_SYS_INFO_EEDATA_SIZE,
    NANOHUB_HAL_SYS_INFO_EEDATA_FREE,
    NANOHUB_HAL_SYS_INFO_CODE_SIZE,
    NANOHUB_HAL_SYS_INFO_CODE_FREE,
    NANOHUB_HAL_SYS_INFO_SHARED_SIZE,
    NANOHUB_HAL_SYS_INFO_SHARED_FREE,
    NANOHUB_HAL_SYS_INFO_END,
};

int SystemComm::MemInfoSession::setup(const hub_message_t *, uint32_t transactionId, AppManager &)
{
    std::lock_guard<std::mutex> _l(mLock);
    char data[MAX_RX_PACKET];
    MessageBuf buf(data, sizeof(data));
    buf.writeU8(NANOHUB_HAL_SYS_INFO);
    buf.writeRaw(sys_info_tags, sizeof(sys_info_tags));

    setState(SESSION_USER);
    return sendToSystem(buf.getData(), buf.getPos(), transactionId);
}

int SystemComm::MemInfoSession::handleRx(MessageBuf &buf, uint32_t transactionId, AppManager &, bool chre)
{
    std::lock_guard<std::mutex> _l(mLock);
    NanohubRsp rsp(buf, transactionId, chre);

    if (rsp.mCmd != NANOHUB_HAL_SYS_INFO)
        return 1;

    size_t len = buf.getRoom();

    if (getState() != SESSION_USER) {
        ALOGE("%s: Invalid state; have %d, need %d", __func__, getState(), SESSION_USER);
        return -EINVAL;
    }

    std::vector<mem_range_t> ranges;
    ranges.reserve(4);
    if (len) {
        NanohubMemInfo mi;
        readNanohubMemInfo(buf, mi);

        //if each is valid, copy to output area
        if (mi.sharedSz != NANOHUB_MEM_SZ_UNKNOWN &&
            mi.sharedUse != NANOHUB_MEM_SZ_UNKNOWN)
            ranges.push_back({
                .total_bytes = mi.sharedSz,
                .free_bytes = mi.sharedSz - mi.sharedUse,
                .type = HUB_MEM_TYPE_MAIN,
            });

        if (mi.osSz != NANOHUB_MEM_SZ_UNKNOWN &&
            mi.osUse != NANOHUB_MEM_SZ_UNKNOWN)
            ranges.push_back({
                .total_bytes = mi.osSz,
                .free_bytes = mi.osSz - mi.osUse,
                .type = HUB_MEM_TYPE_OS,
            });

        if (mi.eeSz != NANOHUB_MEM_SZ_UNKNOWN &&
            mi.eeUse != NANOHUB_MEM_SZ_UNKNOWN)
            ranges.push_back({
                .total_bytes = mi.eeSz,
                .free_bytes = mi.eeSz - mi.eeUse,
                .type = HUB_MEM_TYPE_EEDATA,
            });

        if (mi.ramSz != NANOHUB_MEM_SZ_UNKNOWN &&
            mi.ramUse != NANOHUB_MEM_SZ_UNKNOWN)
            ranges.push_back({
                .total_bytes = mi.ramSz,
                .free_bytes = mi.ramSz - mi.ramUse,
                .type = HUB_MEM_TYPE_RAM,
            });
    }

    //send it out
    sendToApp(CONTEXT_HUB_QUERY_MEMORY, transactionId,
              static_cast<const void *>(ranges.data()),
              ranges.size() * sizeof(ranges[0]));

    complete();
    return 0;
}

int SystemComm::AppMgmtSession::setup(const hub_message_t *appMsg, uint32_t transactionId, AppManager &appManager)
{
    std::lock_guard<std::mutex> _l(mLock);

    char data[MAX_RX_PACKET];
    MessageBuf buf(data, sizeof(data));
    const uint8_t *msgData = static_cast<const uint8_t*>(appMsg->message);

    mCmd = appMsg->message_type;
    mLen = appMsg->message_len;
    mPos = 0;
    mNextPos = 0;
    mErrCnt = 0;

    switch (mCmd) {
    case  CONTEXT_HUB_APPS_ENABLE:
        return setupMgmt(appMsg, transactionId, NANOHUB_HAL_APP_MGMT_START, appManager);
    case  CONTEXT_HUB_APPS_DISABLE:
        return setupMgmt(appMsg, transactionId, NANOHUB_HAL_APP_MGMT_STOP, appManager);
    case  CONTEXT_HUB_UNLOAD_APP:
        return setupMgmt(appMsg, transactionId, NANOHUB_HAL_APP_MGMT_UNLOAD, appManager);
    case  CONTEXT_HUB_LOAD_APP:
    {
        const load_app_request_t *appReq = static_cast<const load_app_request_t*>(appMsg->message);
        if (appReq == nullptr || mLen <= sizeof(*appReq)) {
            ALOGE("%s: Invalid app header: too short\n", __func__);
            return -EINVAL;
        }
        mAppName = appReq->app_binary.app_id;
        if (!appManager.isAppLoaded(mAppName)) {
            appManager.addNewApp(mAppName, appReq->app_binary.app_version);
            appManager.writeApp(mAppName, msgData, mLen);
            mData.clear();
            mData = std::vector<uint8_t>(msgData, msgData + mLen);
            setState(TRANSFER);

            buf.writeU8(NANOHUB_HAL_START_UPLOAD);
            buf.writeU8(0);
            buf.writeU32(mLen);

            return sendToSystem(buf.getData(), buf.getPos(), transactionId);
        } else {
            if (appManager.cmpApp(mAppName, msgData, mLen)) {
                mFlashAddr = appManager.getFlashAddr(mAppName);
                if (appManager.isAppRunning(mAppName)) {
                    setState(STOP_RUN);

                    buf.writeU8(NANOHUB_HAL_APP_MGMT);
                    writeAppName(buf, mAppName);
                    buf.writeU8(NANOHUB_HAL_APP_MGMT_STOP);

                    return sendToSystem(buf.getData(), buf.getPos(), transactionId);
                } else {
                    setState(RUN);

                    buf.writeU8(NANOHUB_HAL_APP_MGMT);
                    writeAppName(buf, mAppName);
                    buf.writeU8(NANOHUB_HAL_APP_MGMT_START);

                    return sendToSystem(buf.getData(), buf.getPos(), transactionId);
                }
            } else {
                appManager.setCachedVersion(mAppName, appReq->app_binary.app_version);
                appManager.writeApp(mAppName, msgData, mLen);
                mData.clear();
                mData = std::vector<uint8_t>(msgData, msgData + mLen);
                if (appManager.isAppRunning(mAppName)) {
                    setState(STOP_TRANSFER);

                    buf.writeU8(NANOHUB_HAL_APP_MGMT);
                    writeAppName(buf, mAppName);
                    buf.writeU8(NANOHUB_HAL_APP_MGMT_STOP);

                    return sendToSystem(buf.getData(), buf.getPos(), transactionId);
                } else {
                    setState(TRANSFER);

                    buf.writeU8(NANOHUB_HAL_START_UPLOAD);
                    buf.writeU8(0);
                    buf.writeU32(mLen);

                    return sendToSystem(buf.getData(), buf.getPos(), transactionId);
                }
            }
        }
    }
    case  CONTEXT_HUB_OS_REBOOT:
        setState(REBOOT);

        buf.writeU8(NANOHUB_HAL_SYS_MGMT);
        buf.writeU8(NANOHUB_HAL_SYS_MGMT_REBOOT);

        return sendToSystem(buf.getData(), buf.getPos(), transactionId);

    case  CONTEXT_HUB_START_APPS:
        if (mLen == sizeof(mStatus))
            memcpy(&mStatus, msgData, mLen);
        appManager.eraseApps();
        setState(QUERY_START);

        buf.writeU8(NANOHUB_HAL_APP_INFO);
        buf.writeU32(0);
        buf.writeRaw(app_info_tags, sizeof(app_info_tags));

        return sendToSystem(buf.getData(), buf.getPos(), transactionId);
    }

    return -EINVAL;
}

int SystemComm::AppMgmtSession::setupMgmt(const hub_message_t *appMsg, uint32_t transactionId, uint32_t cmd, AppManager &appManager)
{
    int32_t result = -1; // results are only directly returned on failure
    const hub_app_name_t &appName = *static_cast<const hub_app_name_t*>(appMsg->message);
    if (appMsg->message_len != sizeof(appName)) {
        return -EINVAL;
    }
    mAppName = appName;

    switch (cmd) {
    case NANOHUB_HAL_APP_MGMT_START:
        if (appManager.isAppRunning(mAppName)) {
            appManager.setCachedStart(mAppName, true);
            sendToApp(mCmd, transactionId, &result, sizeof(result));
            complete();
            return 0;
        }
        break;
    case NANOHUB_HAL_APP_MGMT_STOP:
    case NANOHUB_HAL_APP_MGMT_UNLOAD:
        appManager.setCachedStart(mAppName, false);
        if (!appManager.isAppRunning(mAppName)) {
            sendToApp(mCmd, transactionId, &result, sizeof(result));
            complete();
            return 0;
        }
        break;
    }
    char data[MAX_RX_PACKET];
    MessageBuf buf(data, sizeof(data));
    buf.writeU8(NANOHUB_HAL_APP_MGMT);
    writeAppName(buf, appName);
    buf.writeU8(cmd);
    setState(MGMT);

    return sendToSystem(buf.getData(), buf.getPos(), transactionId);
}

int SystemComm::AppMgmtSession::handleRx(MessageBuf &buf, uint32_t transactionId, AppManager &appManager, bool chre)
{
    int ret = 0;
    std::lock_guard<std::mutex> _l(mLock);
    NanohubRsp rsp(buf, transactionId, chre);

    switch (getState()) {
    case TRANSFER:
        ret = handleTransfer(rsp, buf, appManager);
        break;
    case STOP_TRANSFER:
        ret = handleStopTransfer(rsp, buf, appManager);
         break;
    case QUERY_START:
        ret = handleQueryStart(rsp, buf, appManager);
        break;
    case START:
        ret = handleStart(rsp, buf, appManager);
        break;
    case FINISH:
        ret = handleFinish(rsp, buf, appManager);
        break;
    case RUN:
        ret = handleRun(rsp, buf, appManager);
        break;
    case STOP_RUN:
        ret = handleStopRun(rsp, buf, appManager);
        break;
    case REBOOT:
        ret = handleReboot(rsp, buf, appManager);
        break;
    case ERASE_TRANSFER:
        ret = handleEraseTransfer(rsp, buf, appManager);
        break;
    case MGMT:
        ret = handleMgmt(rsp, buf, appManager);
        break;
    case INFO:
        ret = handleInfo(rsp, buf, appManager);
        break;
    }

    return ret;
}

int SystemComm::AppMgmtSession::handleTransfer(NanohubRsp &rsp, MessageBuf &, AppManager &appManager)
{
    if (rsp.mCmd != NANOHUB_HAL_CONT_UPLOAD && rsp.mCmd != NANOHUB_HAL_START_UPLOAD)
        return 1;

    char data[MAX_RX_PACKET];
    MessageBuf buf(data, sizeof(data));
    int32_t result = 0;

    static_assert(NANOHUB_UPLOAD_CHUNK_SZ_MAX <= (MAX_RX_PACKET-5),
                  "Invalid chunk size");

    if (rsp.mStatus == NANOHUB_HAL_UPLOAD_ACCEPTED) {
        mPos = mNextPos;
        mErrCnt = 0;
    } else if (rsp.mStatus == NANOHUB_HAL_UPLOAD_RESEND) {
        mErrCnt ++;
    } else if (rsp.mStatus == NANOHUB_HAL_UPLOAD_RESTART) {
        mPos = 0;
        mErrCnt ++;
    } else if (rsp.mStatus == NANOHUB_HAL_UPLOAD_CANCEL ||
               rsp.mStatus == NANOHUB_HAL_UPLOAD_CANCEL_NO_RETRY) {
        mPos = mLen;
        result = NANOHUB_APP_NOT_LOADED;
    } else if (rsp.mStatus == NANOHUB_HAL_UPLOAD_NO_SPACE) {
        mPos = 0;
        mErrCnt = 0;
        setState(ERASE_TRANSFER);

        buf.writeU8(NANOHUB_HAL_SYS_MGMT);
        buf.writeU8(NANOHUB_HAL_SYS_MGMT_ERASE);

        return sendToSystem(buf.getData(), buf.getPos(), rsp.mTransactionId);
    } else if (mErrCnt > 5) {
        mPos = mLen;
        result = NANOHUB_APP_NOT_LOADED;
    } else {
        mErrCnt ++;
    }

    if (result != 0) {
        appManager.clearCachedApp(mAppName);

        sendToApp(mCmd, rsp.mTransactionId, &result, sizeof(result));
        complete();
        return 0;
    } else if (mPos < mLen) {
        uint32_t chunkSize = mLen - mPos;

        if (chunkSize > NANOHUB_UPLOAD_CHUNK_SZ_MAX) {
            chunkSize = NANOHUB_UPLOAD_CHUNK_SZ_MAX;
        }

        buf.writeU8(NANOHUB_HAL_CONT_UPLOAD);
        buf.writeU32(mPos);
        buf.writeRaw(&mData[mPos], chunkSize);
        mNextPos = mPos + chunkSize;
    } else {
        buf.writeU8(NANOHUB_HAL_FINISH_UPLOAD);
        setState(FINISH);
    }

    return sendToSystem(buf.getData(), buf.getPos(), rsp.mTransactionId);
}

int SystemComm::AppMgmtSession::handleStopTransfer(NanohubRsp &rsp, MessageBuf &buf, AppManager &)
{
    if (rsp.mCmd != NANOHUB_HAL_APP_MGMT)
        return 1;

    uint8_t cmd = buf.readU8();

    if (cmd != NANOHUB_HAL_APP_MGMT_STOP)
        return 1;

    MgmtStatus sts = { .value = buf.readU32() };

    ALOGI("Nanohub NEW APP STOP: %08" PRIX32 "\n", sts.value);
    if (rsp.mStatus == 0) {
        char data[MAX_RX_PACKET];
        MessageBuf buf(data, sizeof(data));
        setState(TRANSFER);

        buf.writeU8(NANOHUB_HAL_START_UPLOAD);
        buf.writeU8(0);
        buf.writeU32(mLen);
        return sendToSystem(buf.getData(), buf.getPos(), rsp.mTransactionId);
    } else {
        int32_t result = NANOHUB_APP_NOT_LOADED;

        sendToApp(mCmd, rsp.mTransactionId, &result, sizeof(result));
        complete();
        return 0;
    }
}

int SystemComm::AppMgmtSession::handleQueryStart(NanohubRsp &rsp, MessageBuf &buf, AppManager &appManager)
{

    if (rsp.mCmd != NANOHUB_HAL_APP_INFO)
        return 1;

    size_t len = buf.getRoom();
    if (len) {
        uint32_t nextAddr = appManager.readNanohubAppInfo(buf);

        if (nextAddr) {
            char data[MAX_RX_PACKET];
            MessageBuf buf(data, sizeof(data));

            buf.writeU8(NANOHUB_HAL_APP_INFO);
            buf.writeU32(nextAddr);
            buf.writeRaw(app_info_tags, sizeof(app_info_tags));

            return sendToSystem(buf.getData(), buf.getPos(), rsp.mTransactionId);
        }
    }

    appManager.getAppsToStart(mAppList);
    if (mAppList.empty()) {
        sendToApp(CONTEXT_HUB_OS_REBOOT, 0, &mStatus, sizeof(mStatus));
        complete();
        return 0;
    } else {
        char data[MAX_RX_PACKET];
        MessageBuf buf(data, sizeof(data));
        mAppName = mAppList.back();
        mAppList.pop_back();
        setState(START);

        buf.writeU8(NANOHUB_HAL_APP_MGMT);
        writeAppName(buf, mAppName);
        buf.writeU8(NANOHUB_HAL_APP_MGMT_START);
        return sendToSystem(buf.getData(), buf.getPos(), rsp.mTransactionId);
    }
}

int SystemComm::AppMgmtSession::handleStart(NanohubRsp &rsp, MessageBuf &buf, AppManager &)
{
    if (rsp.mCmd != NANOHUB_HAL_APP_MGMT)
        return 1;

    uint8_t cmd = buf.readU8();

    if (cmd != NANOHUB_HAL_APP_MGMT_START)
        return 1;

    MgmtStatus sts = { .value = buf.readU32() };

    ALOGI("Nanohub EXISTING APP START: %08" PRIX32 "\n", sts.value);
    if (mAppList.empty()) {
        sendToApp(CONTEXT_HUB_OS_REBOOT, 0, &mStatus, sizeof(mStatus));
        complete();
        return 0;
    } else {
        char data[MAX_RX_PACKET];
        MessageBuf buf(data, sizeof(data));
        mAppName = mAppList.back();
        mAppList.pop_back();

        buf.writeU8(NANOHUB_HAL_APP_MGMT);
        writeAppName(buf, mAppName);
        buf.writeU8(NANOHUB_HAL_APP_MGMT_START);
        return sendToSystem(buf.getData(), buf.getPos(), rsp.mTransactionId);
    }
}

int SystemComm::AppMgmtSession::handleFinish(NanohubRsp &rsp, MessageBuf &buf, AppManager &appManager)
{
    if (rsp.mCmd != NANOHUB_HAL_FINISH_UPLOAD)
        return 1;

    mFlashAddr = buf.readU32();
    uint32_t crc = buf.readU32();
    mData.clear();

    if (rsp.mStatus == 0) {
        appManager.setCachedCrc(mAppName, crc);
        char data[MAX_RX_PACKET];
        MessageBuf buf(data, sizeof(data));
        setState(RUN);

        buf.writeU8(NANOHUB_HAL_APP_MGMT);
        writeAppName(buf, mAppName);
        buf.writeU8(NANOHUB_HAL_APP_MGMT_START);
        return sendToSystem(buf.getData(), buf.getPos(), rsp.mTransactionId);
    } else {
        int32_t result = NANOHUB_APP_NOT_LOADED;
        appManager.clearCachedApp(mAppName);

        sendToApp(mCmd, rsp.mTransactionId, &result, sizeof(result));
        complete();
        return 0;
    }
}

int SystemComm::AppMgmtSession::handleRun(NanohubRsp &rsp, MessageBuf &buf, AppManager &appManager)
{
    if (rsp.mCmd != NANOHUB_HAL_APP_MGMT)
        return 1;

    uint8_t cmd = buf.readU8();

    if (cmd != NANOHUB_HAL_APP_MGMT_START)
        return 1;

    MgmtStatus sts = { .value = buf.readU32() };

    ALOGI("Nanohub NEW APP START: %08" PRIX32 "\n", sts.value);
    if (rsp.mStatus == 0) {
        appManager.setCachedStart(mAppName, true);
        char data[MAX_RX_PACKET];
        MessageBuf buf(data, sizeof(data));
        setState(INFO);

        buf.writeU8(NANOHUB_HAL_APP_INFO);
        buf.writeU32(mFlashAddr);
        buf.writeRaw(app_info_tags, sizeof(app_info_tags));

        return sendToSystem(buf.getData(), buf.getPos(), rsp.mTransactionId);
    } else {
        appManager.setCachedStart(mAppName, false);
        int32_t result = NANOHUB_APP_NOT_LOADED;
        sendToApp(mCmd, rsp.mTransactionId, &result, sizeof(result));
        complete();
        return 0;
    }
}

int SystemComm::AppMgmtSession::handleInfo(NanohubRsp &rsp, MessageBuf &buf, AppManager &appManager)
{
    if (rsp.mCmd != NANOHUB_HAL_APP_INFO)
        return 1;
    int32_t result = 0;
    size_t len = buf.getRoom();
    if (len) {
        appManager.readNanohubAppInfo(buf);
        appManager.saveApps();
    }
    sendToApp(mCmd, rsp.mTransactionId, &result, sizeof(result));
    complete();
    return 0;
}

int SystemComm::AppMgmtSession::handleStopRun(NanohubRsp &rsp, MessageBuf &buf, AppManager &)
{
    if (rsp.mCmd != NANOHUB_HAL_APP_MGMT)
        return 1;

    uint8_t cmd = buf.readU8();

    if (cmd != NANOHUB_HAL_APP_MGMT_STOP)
        return 1;

    MgmtStatus sts = { .value = buf.readU32() };

    ALOGI("Nanohub NEW APP STOP: %08" PRIX32 "\n", sts.value);
    if (rsp.mStatus == 0) {
        char data[MAX_RX_PACKET];
        MessageBuf buf(data, sizeof(data));
        setState(RUN);

        buf.writeU8(NANOHUB_HAL_APP_MGMT);
        writeAppName(buf, mAppName);
        buf.writeU8(NANOHUB_HAL_APP_MGMT_START);
        return sendToSystem(buf.getData(), buf.getPos(), rsp.mTransactionId);
    } else {
        int32_t result = NANOHUB_APP_NOT_LOADED;

        sendToApp(mCmd, rsp.mTransactionId, &result, sizeof(result));
        complete();
        return 0;
    }
}

/* reboot notification, when triggered by App request */
int SystemComm::AppMgmtSession::handleReboot(NanohubRsp &rsp, MessageBuf &buf, AppManager &)
{
    if (rsp.mCmd != NANOHUB_HAL_SYS_MGMT)
        return 1;

    uint8_t cmd = buf.readU8();

    if (cmd == NANOHUB_HAL_SYS_MGMT_REBOOT)
        ALOGI("Nanohub reboot status [USER REQ]: %08" PRIX32 "\n", rsp.mStatus);

    // reboot notification is sent by SessionManager
    complete();

    return 0;
}

int SystemComm::AppMgmtSession::handleEraseTransfer(NanohubRsp &rsp, MessageBuf &buf, AppManager &appManager)
{
    if (rsp.mCmd != NANOHUB_HAL_SYS_MGMT)
        return 1;

    uint8_t cmd = buf.readU8();

    if (cmd == NANOHUB_HAL_SYS_MGMT_ERASE && rsp.mStatus == 0) {
        char data[MAX_RX_PACKET];
        MessageBuf buf(data, sizeof(data));
        appManager.eraseApps();
        setState(TRANSFER);

        buf.writeU8(NANOHUB_HAL_START_UPLOAD);
        buf.writeU8(0);
        buf.writeU32(mLen);
        return sendToSystem(buf.getData(), buf.getPos(), rsp.mTransactionId);
    } else {
        int32_t result = NANOHUB_APP_NOT_LOADED;

        sendToApp(mCmd, rsp.mTransactionId, &result, sizeof(result));
        complete();
        return 0;
    }
}

int SystemComm::AppMgmtSession::handleMgmt(NanohubRsp &rsp, MessageBuf &buf, AppManager &appManager)
{
    if (rsp.mCmd != NANOHUB_HAL_APP_MGMT)
        return 1;

    uint8_t cmd = buf.readU8();
    MgmtStatus sts = { .value = buf.readU32() };

    bool valid = false;

    int32_t result = rsp.mStatus;

    if (result != 0)
        result = -1;

    switch (cmd) {
    case NANOHUB_HAL_APP_MGMT_STOP:
        valid = mCmd == CONTEXT_HUB_APPS_DISABLE;
        if (valid && rsp.mStatus == 0)
            appManager.clearRunning(mAppName);
        break;
    case NANOHUB_HAL_APP_MGMT_START:
        valid = mCmd == CONTEXT_HUB_APPS_ENABLE;
        if (valid && rsp.mStatus == 0) {
            appManager.setCachedStart(mAppName, true);
            char data[MAX_RX_PACKET];
            MessageBuf buf(data, sizeof(data));
            setState(INFO);

            buf.writeU8(NANOHUB_HAL_APP_INFO);
            buf.writeU32(appManager.getFlashAddr(mAppName));
            buf.writeRaw(app_info_tags, sizeof(app_info_tags));

            return sendToSystem(buf.getData(), buf.getPos(), rsp.mTransactionId);
        }
        break;
    case NANOHUB_HAL_APP_MGMT_UNLOAD:
        valid = mCmd == CONTEXT_HUB_UNLOAD_APP;
        if (valid && rsp.mStatus == 0)
            appManager.clearRunning(mAppName);
        break;
    default:
        return 1;
    }

    ALOGI("Nanohub MGMT response: CMD=%02X; STATUS=%08" PRIX32, rsp.mCmd, sts.value);
    if (!valid) {
        ALOGE("Invalid response for this state: APP CMD=%02X", mCmd);
        return -EINVAL;
    }

    sendToApp(mCmd, rsp.mTransactionId, &result, sizeof(result));
    complete();
    return 0;
}

int SystemComm::KeyInfoSession::setup(const hub_message_t *, uint32_t transactionId, AppManager &) {
    std::lock_guard<std::mutex> _l(mLock);
    mKeyNum = 0;
    mKeyOffset = 0;
    mRsaKeyData.clear();
    setState(SESSION_USER);
    mStatus = -EBUSY;
    return requestRsaKeys(transactionId);
}

int SystemComm::KeyInfoSession::handleRx(MessageBuf &buf, uint32_t transactionId, AppManager &, bool chre)
{
    std::lock_guard<std::mutex> _l(mLock);
    NanohubRsp rsp(buf, transactionId, chre);

    if (getState() != SESSION_USER) {
        // invalid state
        mStatus = -EFAULT;
        return mStatus;
    }

    uint32_t keyLen = buf.readU32();
    uint32_t dataLen = buf.getRoom();

    if (dataLen) {
        mRsaKeyData.insert(mRsaKeyData.end(),
                           buf.getData() + buf.getPos(),
                           buf.getData() + buf.getSize());
        if (mKeyOffset + dataLen >= keyLen) {
            mKeyNum++;
            mKeyOffset = 0;
        } else {
            mKeyOffset += dataLen;
        }
        return requestRsaKeys(transactionId);
    } else {
        mStatus = 0;
        complete();
        return 0;
    }
}

int SystemComm::KeyInfoSession::requestRsaKeys(uint32_t transactionId)
{
    char data[MAX_RX_PACKET];
    MessageBuf buf(data, sizeof(data));

    buf.writeU8(NANOHUB_HAL_KEY_INFO);
    buf.writeU32(mKeyNum);
    buf.writeU32(mKeyOffset);

    return sendToSystem(buf.getData(), buf.getPos(), transactionId);
}

void SystemComm::AppManager::dumpAppInfo(std::string &result)
{
    char buffer[256];

    for (auto &it : apps_) {
        uint64_t id = it.first;
        const auto &app = it.second;

        snprintf(buffer, sizeof(buffer), "App: 0x%016" PRIx64 "\n", id);
        result.append(buffer);
        if (app->loaded) {
            snprintf(buffer, sizeof(buffer),
                "  Version: 0x%08" PRIx32 "\n"
                "  flashAddr: 0x%08" PRIx32 "\n"
                "  Running: %s\n",
                app->version,
                app->flashAddr,
                app->running ? "true" : "false");
            result.append(buffer);

            if (app->flashUse != NANOHUB_MEM_SZ_UNKNOWN) {
                snprintf(buffer, sizeof(buffer),
                    "  flashUse: %d\n"
                    "  CRC: 0x%08" PRIx32 "\n",
                    app->flashUse,
                    app->crc);
                result.append(buffer);
            }

            if (app->running) {
                snprintf(buffer, sizeof(buffer),
                    "  TID: %04x\n"
                    "  ramUse: %d\n",
                    app->tid,
                    app->ramUse);
                result.append(buffer);
            }

            if (app->chre) {
                snprintf(buffer, sizeof(buffer), "  CHRE: %d.%d\n",
                    app->chre_major, app->chre_minor);
                result.append(buffer);
            }
        }

        if (app->cached_napp) {
            snprintf(buffer, sizeof(buffer),
                "  Cached Version: 0x%08" PRIx32 "\n"
                "  Cached Start: %s\n"
                "  Cached CRC: 0x%08" PRIx32 "\n",
                app->cached_version,
                app->cached_start ? "true" : "false",
                app->cached_crc);
            result.append(buffer);
        }
    }
}

bool SystemComm::AppManager::saveApps()
{
    mkdir(CHRE_APP_DIR, CHRE_APP_DIR_PERMS);
    File saved_apps_file(CHRE_APP_SETTINGS, "w");
    std::shared_ptr<Json::Value> appsObject(new Json::Value);
    status_t err;

    if ((err = saved_apps_file.initCheck()) != OK) {
        ALOGW("saved_apps file open (w) failed %d (%s)",
              err,
              strerror(-err));
        return false;
    }

    for (auto &it : apps_) {
        uint64_t id = it.first;
        const auto &app = it.second;

        if (app->cached_napp) {
            char hexId[17];
            snprintf(hexId, sizeof(hexId), "%016" PRIX64, id);
            Json::Value array(Json::arrayValue);
            array[0] = app->cached_version;
            array[1] = app->cached_start;
            array[2] = app->cached_crc;
            (*appsObject)[hexId] = array;
        }
    }

    // Write the JSON string to disk.
    Json::StreamWriterBuilder factory;
    std::string serializedSettings(Json::writeString(factory, *appsObject));
    size_t size = serializedSettings.size();
    if ((err = saved_apps_file.write(serializedSettings.c_str(), size)) != (ssize_t)size) {
        ALOGW("saved_apps file write failed %d (%s)",
              err,
              strerror(-err));
        return false;
    }

    return true;
}

bool SystemComm::AppManager::restoreApps()
{
    File saved_apps_file(CHRE_APP_SETTINGS, "r");
    std::shared_ptr<Json::Value> appsObject;
    status_t err;

    if ((err = saved_apps_file.initCheck()) != OK) {
        ALOGW("saved_apps file open (r) failed %d (%s)",
              err,
              strerror(-err));
        return false;
    }

    off64_t size = saved_apps_file.seekTo(0, SEEK_END);
    saved_apps_file.seekTo(0, SEEK_SET);

    if (size > 0) {
        char *buf = (char *)malloc(size);
        CHECK_EQ(saved_apps_file.read(buf, size), (ssize_t)size);

        std::string str(buf);
        std::shared_ptr<Json::Value> in(new Json::Value);
        Json::CharReaderBuilder builder;
        std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
        bool valid = reader->parse(
            str.data(), str.data() + str.size(), in.get(), /* errorMessage = */ nullptr);
        free(buf);

        if (valid && in->isObject()) {
            appsObject = in;
        }
    }

    if (appsObject == nullptr) {
        appsObject = std::shared_ptr<Json::Value>(new Json::Value(Json::objectValue));
    }

    Json::Value::Members apps = appsObject->getMemberNames();
    for (auto &it : apps) {
        Json::Value &val = (*appsObject)[it];
        if (val.isArray()) {
            uint32_t version = val[0].asUInt();
            uint32_t start = val[1].asUInt();
            uint32_t crc = val[2].asUInt();

            uint64_t id = strtoull(it.c_str(), nullptr, 16);
            apps_[id] = std::unique_ptr<AppData>(new AppData);
            apps_[id]->loaded = false;
            apps_[id]->running = false;
            apps_[id]->chre = false;
            apps_[id]->cached_napp = true;
            apps_[id]->cached_version = version;
            apps_[id]->cached_start = start;
            apps_[id]->cached_crc = crc;
        }
    }

    return true;
}

bool SystemComm::AppManager::eraseApps()
{
    for (auto it=apps_.begin(); it != apps_.end();) {
        if (!it->second->cached_napp)
            it = apps_.erase(it);
        else {
            it->second->loaded = false;
            it->second->running = false;
            it->second->chre = false;
            ++it;
        }
    }

    return true;
}

bool SystemComm::AppManager::writeApp(hub_app_name_t &appName, const uint8_t *data, int32_t len)
{
    mkdir(CHRE_APP_DIR, CHRE_APP_DIR_PERMS);
    char file[strlen(CHRE_APP_DIR) + strlen("/") + 16 + strlen(".napp") + 1];

    snprintf(file, sizeof(file), "%s/%016" PRIX64 ".napp", CHRE_APP_DIR, appName.id);

    int fd = open(file, O_CREAT | O_WRONLY | O_TRUNC, CHRE_APP_FILE_PERMS);
    if (fd == -1)
        return false;

    if (write(fd, data, len) == len) {
        close(fd);
        return true;
    } else {
        close(fd);
        return false;
    }
}

int32_t SystemComm::AppManager::readApp(hub_app_name_t &appName, void **data)
{
    char file[strlen(CHRE_APP_DIR) + strlen("/") + 16 + strlen(".napp") + 1];

    snprintf(file, sizeof(file), "%s/%016" PRIX64 ".napp", CHRE_APP_DIR, appName.id);

    int32_t ret = -1;
    *data = nullptr;
    int fd = open(file, O_RDONLY);

    if (fd >= 0) {
        struct stat sb;
        if (fstat(fd, &sb) == 0) {
            *data = malloc(sb.st_size);
            if (*data != nullptr && read(fd, *data, sb.st_size) == sb.st_size)
                ret = sb.st_size;
            else {
                free(*data);
                *data = nullptr;
            }
        }
        close(fd);
    }
    return ret;
}

bool SystemComm::AppManager::cmpApp(hub_app_name_t &appName, const uint8_t *data, uint32_t len)
{
    char file[strlen(CHRE_APP_DIR) + strlen("/") + 16 + strlen(".napp") + 1];

    snprintf(file, sizeof(file), "%s/%016" PRIX64 ".napp", CHRE_APP_DIR, appName.id);

    if (!isAppLoaded(appName))
        return false;

    if ((!apps_[appName.id]->cached_napp) ||
        (apps_[appName.id]->crc != apps_[appName.id]->cached_crc))
        return false;

    bool success = false;
    int fd = open(file, O_RDONLY);

    if (fd >= 0) {
        struct stat sb;
        if (fstat(fd, &sb) == 0 && sb.st_size == len) {
            void *buf = malloc(len);
            if (buf != nullptr && read(fd, buf, sb.st_size) == sb.st_size && memcmp(data, buf, len) == 0)
                success = true;
            free(buf);
        }
        close(fd);
    }
    return success;
}

uint32_t SystemComm::AppManager::readNanohubAppInfo(MessageBuf &buf)
{
    hub_app_name_t name;

    uint8_t tag, len;
    uint32_t ramUse = 0;
    bool ramUseValid = true;

    // first tag must be the appid
    if (buf.getRoom() < 2 + sizeof(name.id)) {
        ALOGE("%s: failed to read object", __func__);
        return 0;
    }

    tag = buf.readU8();
    len = buf.readU8();
    if (tag != NANOHUB_HAL_APP_INFO_APPID || len != sizeof(name.id)) {
        ALOGE("%s: invalid first tag: %d", __func__, tag);
        return 0;
    }

    readAppName(buf, name);
    if (!isAppPresent(name)) {
        apps_[name.id] = std::unique_ptr<AppData>(new AppData);
        apps_[name.id]->loaded = false;
        apps_[name.id]->chre = false;
        apps_[name.id]->running = false;
        apps_[name.id]->cached_napp = false;
    }
    const auto &app = apps_[name.id];

    while (buf.getRoom() >= 2) {
        tag = buf.readU8();
        len = buf.readU8();
        if (buf.getRoom() >= len) {
            switch(tag) {
            case NANOHUB_HAL_APP_INFO_CRC:
                if (len == 0)
                    app->crc = 0;
                else if (len == sizeof(app->crc))
                    app->crc = buf.readU32();
                else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_APP_INFO_TID:
                if (len == 0) {
                    app->tid = NANOHUB_TID_UNKNOWN;
                    ramUseValid = false;
                    app->loaded = true;
                    app->running = false;
                } else if (len  == sizeof(app->tid)) {
                    app->tid = buf.readU32();
                    app->loaded = true;
                    app->running = true;
                } else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_APP_INFO_VERSION:
                if (len == sizeof(app->version))
                    app->version = buf.readU32();
                else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_APP_INFO_ADDR:
                if (len == sizeof(app->flashAddr))
                    app->flashAddr = buf.readU32();
                else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_APP_INFO_SIZE:
                if (len == 0)
                    app->flashUse = NANOHUB_MEM_SZ_UNKNOWN;
                else if (len == sizeof(app->flashUse))
                    app->flashUse = buf.readU32();
                else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_APP_INFO_HEAP:
            case NANOHUB_HAL_APP_INFO_DATA:
            case NANOHUB_HAL_APP_INFO_BSS:
                if (len == 0)
                    ramUseValid = false;
                else if (len == sizeof(uint32_t))
                    ramUse += buf.readU32();
                else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_APP_INFO_CHRE_MAJOR:
                if (len == 0)
                    app->chre = false;
                else if (len == sizeof(app->chre_major)) {
                    app->chre = true;
                    app->chre_major = buf.readU8();
                } else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_APP_INFO_CHRE_MINOR:
                if (len == 0)
                    app->chre = false;
                else if (len == sizeof(app->chre_minor)) {
                    app->chre = true;
                    app->chre_minor = buf.readU8();
                } else
                    buf.readRaw(len);
                break;
            case NANOHUB_HAL_APP_INFO_END:
                if (len != 0 || buf.getRoom() != 0) {
                    ALOGE("%s: failed to read object", __func__);
                    return 0;
                }
                break;
            default:
                ALOGI("%s: unsupported tag: %d", __func__, tag);
                buf.readRaw(len);
                break;
            }
        } else {
            ALOGE("%s: failed to read object", __func__);
            return 0;
        }
    }

    if (buf.getRoom() != 0) {
        ALOGE("%s: failed to read object", __func__);
        return 0;
    }

    if (ramUseValid)
        app->ramUse = ramUse;
    else
        app->ramUse = NANOHUB_MEM_SZ_UNKNOWN;

    return app->flashAddr +
        (app->flashUse != NANOHUB_MEM_SZ_UNKNOWN ? ((app->flashUse+3)&~3) : 4);
}

void SystemComm::AppManager::sendAppInfoToApp(uint32_t transactionId) {
    std::vector<hub_app_info> appInfo;
    for (auto &it : apps_) {
        uint64_t id = it.first;
        const auto &app = it.second;

        // TODO: Still have some non-chre apps that need to be reported
        // if (!app->chre || !app->running || app->flashUse == NANOHUB_MEM_SZ_UNKNOWN)
        if (!app->running || app->flashUse == NANOHUB_MEM_SZ_UNKNOWN)
            continue;

        hub_app_info info;
        info.app_name = { .id = id };
        info.version = app->version;
        info.num_mem_ranges = 0;
        if (app->flashUse != NANOHUB_MEM_SZ_UNKNOWN) {
            mem_range_t &range = info.mem_usage[info.num_mem_ranges++];
            range.type = HUB_MEM_TYPE_MAIN;
            range.total_bytes = app->flashUse;
        }
        if (app->ramUse != NANOHUB_MEM_SZ_UNKNOWN) {
            mem_range_t &range = info.mem_usage[info.num_mem_ranges++];
            range.type = HUB_MEM_TYPE_RAM;
            range.total_bytes = app->ramUse;
        }

        appInfo.push_back(info);
    }
    sendToApp(CONTEXT_HUB_QUERY_APPS, transactionId,
              static_cast<const void *>(appInfo.data()),
              appInfo.size() * sizeof(appInfo[0]));
}

int SystemComm::AppManager::getAppsToStart(std::vector<hub_app_name_t> &apps)
{
    int cnt = 0;
    apps.clear();

    for (auto &it : apps_) {
        uint64_t id = it.first;
        const auto &app = it.second;

        if (app->cached_napp && app->cached_start && app->loaded &&
            !app->running && app->flashUse != NANOHUB_MEM_SZ_UNKNOWN) {
            apps.push_back({ .id = id });
            cnt++;
        }
    }

    return cnt;
}

int SystemComm::doHandleRx(uint64_t appId, uint32_t transactionId, const char *data, int len, bool chre)
{
    bool reboot = false;
    uint32_t rebootStatus;
    //we only care for messages from HostIF
    if (appId != mHostIfAppName.id)
        return 1;

    //they must all be at least 1 byte long
    if (!len) {
        return -EINVAL;
    }
    MessageBuf buf(data, len);
    if (NanoHub::messageTracingEnabled()) {
        dumpBuffer("SYS -> HAL", mHostIfAppName, transactionId, 0, buf.getData(), buf.getSize());
    }
    int status = mSessions.handleRx(buf, transactionId, mAppManager, chre, reboot, rebootStatus);
    if (status) {
        // provide default handler for any system message, that is not properly handled
        dumpBuffer(status > 0 ? "HAL (not handled)" : "HAL (error)",
                   mHostIfAppName, transactionId, 0, buf.getData(), buf.getSize(), status);
        status = status > 0 ? 0 : status;
    }
    if (reboot) {
        hub_message_t msg =
        {
            .app_name.id = appId,
            .message_type = CONTEXT_HUB_START_APPS,
            .message_len = sizeof(rebootStatus),
            .message = &rebootStatus,
        };

        status = doHandleTx(&msg, 0xFFFFFFFF);
    }

    return status;
}

void SystemComm::doDumpAppInfo(std::string &result)
{
    mAppManager.dumpAppInfo(result);
}

int SystemComm::SessionManager::handleRx(MessageBuf &buf, uint32_t transactionId, AppManager &appManager, bool chre, bool &reboot, uint32_t &rebootStatus)
{
    int status = 1;
    std::unique_lock<std::mutex> lk(lock);

    // pass message to all active sessions, in arbitrary order
    // 1st session that handles the message terminates the loop
    for (auto pos = sessions_.begin(); pos != sessions_.end() && status > 0; next(pos)) {
        if (!isActive(pos)) {
            continue;
        }
        Session *session = pos->second;
        status = session->handleRx(buf, transactionId, appManager, chre);
        if (status < 0) {
            session->complete();
        }
    }

    NanohubRsp rsp(buf, transactionId, chre);
    if (rsp.mCmd == NANOHUB_HAL_SYS_MGMT) {
        uint8_t cmd = buf.readU8();

        if (cmd == NANOHUB_HAL_SYS_MGMT_REBOOT) {
            // if this is reboot notification, kill all sessions
            for (auto pos = sessions_.begin(); pos != sessions_.end(); next(pos)) {
                if (!isActive(pos)) {
                    continue;
                }
                Session *session = pos->second;
                session->abort(-EINTR);
            }
            lk.unlock();
            // log the reboot event, if not handled
            if (status > 0) {
                ALOGW("Nanohub reboot status [UNSOLICITED]: %08" PRIX32, rsp.mStatus);
                status = 0;
            }
            reboot = true;
            rebootStatus = rsp.mStatus;
        }
    }

    return status;
}

int SystemComm::SessionManager::setup_and_add(int id, Session *session, const hub_message_t *appMsg, uint32_t transactionId, AppManager &appManager)
{
    std::lock_guard<std::mutex> _l(lock);

    // scan sessions to release those that are already done
    for (auto pos = sessions_.begin(); pos != sessions_.end(); next(pos)) {
        continue;
    }

    if (sessions_.count(id) == 0 && !session->isRunning()) {
        sessions_[id] = session;
        int ret = session->setup(appMsg, transactionId, appManager);
        if (ret < 0) {
            session->complete();
        }
        return ret;
    }
    return -EBUSY;
}

int SystemComm::doHandleTx(const hub_message_t *appMsg, uint32_t transactionId)
{
    int status = 0;

    switch (appMsg->message_type) {
    case CONTEXT_HUB_LOAD_APP:
        if (!mKeySession.haveKeys()) {
            status = mSessions.setup_and_add(CONTEXT_HUB_LOAD_APP, &mKeySession, appMsg, transactionId, mAppManager);
            if (status < 0) {
                break;
            }
            mKeySession.wait();
            status = mKeySession.getStatus();
            if (status < 0) {
                break;
            }
        }
        status = mSessions.setup_and_add(CONTEXT_HUB_LOAD_APP, &mAppMgmtSession, appMsg, transactionId, mAppManager);
        break;
    case CONTEXT_HUB_APPS_ENABLE:
    case CONTEXT_HUB_APPS_DISABLE:
    case CONTEXT_HUB_UNLOAD_APP:
        // all APP-modifying commands share session key, to ensure they can't happen at the same time
        status = mSessions.setup_and_add(CONTEXT_HUB_LOAD_APP, &mAppMgmtSession, appMsg, transactionId, mAppManager);
        break;

    case CONTEXT_HUB_QUERY_APPS:
        mAppManager.sendAppInfoToApp(transactionId);
        break;

    case CONTEXT_HUB_QUERY_MEMORY:
        status = mSessions.setup_and_add(CONTEXT_HUB_QUERY_MEMORY, &mMemInfoSession, appMsg, transactionId, mAppManager);
        break;

    case CONTEXT_HUB_START_APPS:
        status = mSessions.setup_and_add(CONTEXT_HUB_START_APPS, &mAppMgmtSession, appMsg, transactionId, mAppManager);
        break;

    default:
        ALOGW("Unknown os message type %u\n", appMsg->message_type);
        return -EINVAL;
    }

   return status;
}

}; // namespace nanohub

}; // namespace android
