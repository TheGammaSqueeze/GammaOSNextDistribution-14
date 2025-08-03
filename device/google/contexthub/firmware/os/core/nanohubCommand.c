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

#include <inttypes.h>
#include <string.h>
#include <stdint.h>
#include <sys/endian.h>

#include <variant/variant.h>
#include <eventnums.h>

#include <plat/taggedPtr.h>
#include <plat/plat.h>
#include <plat/wdt.h>

#include <nanohub/crc.h>
#include <nanohub/rsa.h>
#include <nanohub/nanohub.h>

#include <bl.h>
#include <atomicBitset.h>
#include <atomic.h>
#include <hostIntf.h>
#include <hostIntf_priv.h>
#include <nanohubCommand.h>
#include <nanohubPacket.h>
#include <eeData.h>
#include <seos.h>
#include <seos_priv.h>
#include <util.h>
#include <mpu.h>
#include <heap.h>
#include <slab.h>
#include <sensType.h>
#include <timer.h>
#include <appSec.h>
#include <cpu.h>
#include <cpu/cpuMath.h>
#include <algos/ap_hub_sync.h>
#include <sensors_priv.h>

#include <chre.h>

#define NANOHUB_COMMAND(_reason, _fastHandler, _handler, _minReqType, _maxReqType) \
        { .reason = _reason, .fastHandler = _fastHandler, .handler = _handler, \
          .minDataLen = sizeof(_minReqType), .maxDataLen = sizeof(_maxReqType) }

#define NANOHUB_HAL_LEGACY_COMMAND(_msg, _handler) \
        { .msg = _msg, .handler = _handler }

#define NANOHUB_HAL_COMMAND(_msg, _handler, _minReqType, _maxReqType) \
        { .msg = _msg, .handler = _handler, \
          .minDataLen = sizeof(_minReqType), .maxDataLen = sizeof(_maxReqType) }

// maximum number of bytes to feed into appSecRxData at once
// The bigger the number, the more time we block other event processing
// appSecRxData only feeds 16 bytes at a time into writeCbk, so large
// numbers don't buy us that much
#define MAX_APP_SEC_RX_DATA_LEN 64

#define REQUIRE_SIGNED_IMAGE    true
#define DEBUG_APHUB_TIME_SYNC   false

#if DEBUG_APHUB_TIME_SYNC
static void syncDebugAdd(uint64_t, uint64_t);
#endif

struct DownloadState
{
    struct AppSecState *appSecState;
    uint32_t size;      // document size, as reported by client
    uint32_t srcOffset; // bytes received from client
    uint32_t dstOffset; // bytes sent to flash
    struct AppHdr *start;     // start of flash segment, where to write
    uint32_t crc;       // document CRC-32, as reported by client
    uint32_t srcCrc;    // current state of CRC-32 we generate from input
    uint8_t  data[NANOHUB_PACKET_PAYLOAD_MAX];
    uint8_t  len;
    uint8_t  lenLeft;
    uint8_t  chunkReply;
    bool     erase;
    bool     eraseScheduled;
};

static struct DownloadState *mDownloadState;
static AppSecErr mAppSecStatus;
static struct AppHdr *mApp;
static struct SlabAllocator *mEventSlab;
static struct HostIntfDataBuffer mTxCurr, mTxNext;
static uint8_t mTxCurrLength, mTxNextLength;
static uint8_t mPrefetchActive, mPrefetchTx;
static uint32_t mTxWakeCnt[2];
static struct ApHubSync mTimeSync;

static inline bool isSensorEvent(uint32_t evtType)
{
    return evtType > EVT_NO_FIRST_SENSOR_EVENT && evtType <= EVT_NO_FIRST_SENSOR_EVENT + SENS_TYPE_LAST_USER;
}

static void slabFree(void *ptr)
{
    slabAllocatorFree(mEventSlab, ptr);
}

void nanohubInitCommand(void)
{
    mEventSlab = slabAllocatorNew(NANOHUB_PACKET_PAYLOAD_MAX-sizeof(__le32), 4, 2);
}

static inline uint64_t unaligned_u64(uint64_t *val) {
    uint64_t local;
    memcpy(&local, val, sizeof(local));
    return local;
}

static inline uint32_t unaligned_u32(uint32_t *val) {
    uint32_t local;
    memcpy(&local, val, sizeof(local));
    return local;
}

static uint32_t getOsHwVersion(void *rx, uint8_t rx_len, void *tx, uint64_t timestamp)
{
    struct NanohubOsHwVersionsResponse *resp = tx;
    resp->hwType = htole16(platHwType());
    resp->hwVer = htole16(platHwVer());
    resp->blVer = htole16(platBlVer());
    resp->osVer = htole16(OS_VER);
    resp->variantVer = htole32(VARIANT_VER);

    return sizeof(*resp);
}

static uint32_t getAppVersion(void *rx, uint8_t rx_len, void *tx, uint64_t timestamp)
{
    struct NanohubAppVersionsRequest *req = rx;
    struct NanohubAppVersionsResponse *resp = tx;
    uint32_t appIdx, appVer, appSize;

    if (osAppInfoById(le64toh(unaligned_u64(&req->appId)), &appIdx, &appVer, &appSize)) {
        resp->appVer = htole32(appVer);
        return sizeof(*resp);
    }

    return 0;
}

static uint32_t queryAppInfo(void *rx, uint8_t rx_len, void *tx, uint64_t timestamp)
{
    struct NanohubAppInfoRequest *req = rx;
    struct NanohubAppInfoResponse *resp = tx;
    uint64_t appId;
    uint32_t appVer, appSize;

    if (osAppInfoByIndex(le32toh(unaligned_u32(&req->appIdx)), &appId, &appVer, &appSize)) {
        resp->appId = htole64(appId);
        resp->appVer = htole32(appVer);
        resp->appSize = htole32(appSize);
        return sizeof(*resp);
    }

    return 0;
}

static AppSecErr writeCbk(const void *data, uint32_t len)
{
    AppSecErr ret = APP_SEC_BAD;

    if (osWriteShared((uint8_t*)(mDownloadState->start) + mDownloadState->dstOffset, data, len)) {
        ret = APP_SEC_NO_ERROR;
        mDownloadState->dstOffset += len;
    }

    return ret;
}

static AppSecErr pubKeyFindCbk(const uint32_t *gotKey, bool *foundP)
{
    const uint32_t *ptr;
    uint32_t numKeys, i;

    *foundP = false;
    ptr = BL.blGetPubKeysInfo(&numKeys);
    for (i = 0; ptr && i < numKeys; i++, ptr += RSA_LIMBS) {
        if (!memcmp(gotKey, ptr, RSA_BYTES)) {
            *foundP = true;
            break;
        }
    }

    return APP_SEC_NO_ERROR;
}

static AppSecErr osSecretKeyLookup(uint64_t keyId, void *keyBuf)
{
    struct SeosEedataEncrKeyData kd;
    void *state = NULL;

    while(1) {
        uint32_t sz = sizeof(struct SeosEedataEncrKeyData);

        if (!eeDataGetAllVersions(EE_DATA_NAME_ENCR_KEY, &kd, &sz, &state))
            break;

        if (sz == sizeof(struct SeosEedataEncrKeyData) && kd.keyID == keyId) {
            if (keyBuf)
                memcpy(keyBuf, kd.key, sizeof(kd.key));
            return APP_SEC_NO_ERROR;
        }
    }

    return APP_SEC_KEY_NOT_FOUND;
}

static AppSecErr osSecretKeyDelete(uint64_t keyId)
{
    struct SeosEedataEncrKeyData kd;
    void *state = NULL;
    bool good = true;
    int count = 0;

    while(1) {
        uint32_t sz = sizeof(struct SeosEedataEncrKeyData);
        void *addr = eeDataGetAllVersions(EE_DATA_NAME_ENCR_KEY, &kd, &sz, &state);

        if (!addr)
            break;

        if (sz == sizeof(kd) && kd.keyID == keyId) {
            good = eeDataEraseOldVersion(EE_DATA_NAME_ENCR_KEY, addr) && good;
            count++;
        }
    }

    return count == 0 ? APP_SEC_KEY_NOT_FOUND : good ? APP_SEC_NO_ERROR : APP_SEC_BAD;
}

static AppSecErr osSecretKeyAdd(uint64_t keyId, void *keyBuf)
{
    struct SeosEedataEncrKeyData kd;

    // do not add key if it already exists
    if (osSecretKeyLookup(keyId, NULL) != APP_SEC_KEY_NOT_FOUND)
        return APP_SEC_BAD;

    memcpy(&kd.key, keyBuf, 32);
    kd.keyID = keyId;

    return eeDataSet(EE_DATA_NAME_ENCR_KEY, &kd, sizeof(kd)) ? APP_SEC_NO_ERROR : APP_SEC_BAD;
}

static void freeDownloadState()
{
    if (mDownloadState->appSecState)
        appSecDeinit(mDownloadState->appSecState);
    heapFree(mDownloadState);
    mDownloadState = NULL;
}

static bool resetDownloadState(bool initial, bool erase)
{
    bool doCreate = true;

    mAppSecStatus = APP_SEC_NO_ERROR;
    if (mDownloadState->appSecState)
        appSecDeinit(mDownloadState->appSecState);
    mDownloadState->appSecState = appSecInit(writeCbk, pubKeyFindCbk, osSecretKeyLookup, REQUIRE_SIGNED_IMAGE);
    mDownloadState->srcOffset = 0;
    mDownloadState->srcCrc = ~0;
    if (!initial) {
        // if no data was written, we can reuse the same segment
        if (mDownloadState->dstOffset)
            osAppSegmentClose(mDownloadState->start, mDownloadState->dstOffset, SEG_ST_ERASED);
        else
            doCreate = false;
    }
    mDownloadState->dstOffset = 0;
    if (doCreate)
        mDownloadState->start = osAppSegmentCreate(mDownloadState->size);
    if (!mDownloadState->start) {
        if (erase)
            mDownloadState->erase = true;
        else
            return false;
    }
    return true;
}

static bool doStartFirmwareUpload(struct NanohubStartFirmwareUploadRequest *req, bool erase)
{
    if (!mDownloadState) {
        mDownloadState = heapAlloc(sizeof(struct DownloadState));

        if (!mDownloadState)
            return false;
        else
            memset(mDownloadState, 0x00, sizeof(struct DownloadState));
    }

    mDownloadState->size = le32toh(req->size);
    mDownloadState->crc = le32toh(req->crc);
    mDownloadState->chunkReply = NANOHUB_FIRMWARE_CHUNK_REPLY_ACCEPTED;
    return resetDownloadState(true, erase);
}

static uint32_t startFirmwareUpload(void *rx, uint8_t rx_len, void *tx, uint64_t timestamp)
{
    struct NanohubStartFirmwareUploadRequest *req = rx;
    struct NanohubStartFirmwareUploadResponse *resp = tx;

    resp->accepted = doStartFirmwareUpload(req, true);

    return sizeof(*resp);
}

static void deferredUpdateOs(void *cookie)
{
    const struct AppHdr *app = cookie;
    struct OsUpdateHdr *os = (struct OsUpdateHdr *)(&(app->hdr) + 1);
    uint32_t uploadStatus = OS_UPDT_HDR_CHECK_FAILED;
    uint8_t marker = OS_UPDT_MARKER_DOWNLOADED;
    struct Segment *seg = osGetSegment(app);
    uint32_t segSize = osSegmentGetSize(seg);

    osLog(LOG_INFO, "%s: checking OS image @ %p\n", __func__, os);
    // some sanity checks before asking BL to do image lookup
    hostIntfSetBusy(true);
    if (segSize >= (sizeof(*app) + sizeof(*os)) && segSize > os->size) {
        if (osWriteShared(&os->marker, &marker, sizeof(os->marker))) {
            wdtDisableClk();
            uploadStatus = BL.blVerifyOsUpdate();
            wdtEnableClk();
        } else {
            osLog(LOG_ERROR, "%s: could not set marker on OS image\n", __func__);
        }
    }
    hostIntfSetBusy(false);
    osLog(LOG_INFO, "%s: status=%" PRIu32 "\n", __func__, uploadStatus);
}

static AppSecErr updateKey(const struct AppHdr *app)
{
    AppSecErr ret;
    struct KeyInfo *ki = (struct KeyInfo *)(&(app->hdr) + 1);
    uint8_t *data = (uint8_t *)(ki + 1);
    uint64_t keyId = KEY_ID_MAKE(APP_ID_GET_VENDOR(app->hdr.appId), ki->id);
    const char *op;

    if ((app->hdr.fwFlags & FL_KEY_HDR_DELETE) != 0) {
        // removing existing key
        ret = osSecretKeyDelete(keyId);
        op = "Removing";
    } else {
        // adding new key
        ret = osSecretKeyAdd(keyId, data);
        op = "Adding";
    }
    osLog(LOG_INFO, "%s: %s key: id=%016" PRIX64 "; ret=%" PRIu32 "\n",
          __func__, op, keyId, ret);

    return ret;
}

static uint32_t appSecErrToNanohubReply(AppSecErr status)
{
    uint32_t reply;

    switch (status) {
    case APP_SEC_NO_ERROR:
        reply = NANOHUB_FIRMWARE_UPLOAD_SUCCESS;
        break;
    case APP_SEC_KEY_NOT_FOUND:
        reply = NANOHUB_FIRMWARE_UPLOAD_APP_SEC_KEY_NOT_FOUND;
        break;
    case APP_SEC_HEADER_ERROR:
        reply = NANOHUB_FIRMWARE_UPLOAD_APP_SEC_HEADER_ERROR;
        break;
    case APP_SEC_TOO_MUCH_DATA:
        reply = NANOHUB_FIRMWARE_UPLOAD_APP_SEC_TOO_MUCH_DATA;
        break;
    case APP_SEC_TOO_LITTLE_DATA:
        reply = NANOHUB_FIRMWARE_UPLOAD_APP_SEC_TOO_LITTLE_DATA;
        break;
    case APP_SEC_SIG_VERIFY_FAIL:
        reply = NANOHUB_FIRMWARE_UPLOAD_APP_SEC_SIG_VERIFY_FAIL;
        break;
    case APP_SEC_SIG_DECODE_FAIL:
        reply = NANOHUB_FIRMWARE_UPLOAD_APP_SEC_SIG_DECODE_FAIL;
        break;
    case APP_SEC_SIG_ROOT_UNKNOWN:
        reply = NANOHUB_FIRMWARE_UPLOAD_APP_SEC_SIG_ROOT_UNKNOWN;
        break;
    case APP_SEC_MEMORY_ERROR:
        reply = NANOHUB_FIRMWARE_UPLOAD_APP_SEC_MEMORY_ERROR;
        break;
    case APP_SEC_INVALID_DATA:
        reply = NANOHUB_FIRMWARE_UPLOAD_APP_SEC_INVALID_DATA;
        break;
    case APP_SEC_VERIFY_FAILED:
        reply = NANOHUB_FIRMWARE_UPLOAD_APP_SEC_VERIFY_FAILED;
        break;
    default:
        reply = NANOHUB_FIRMWARE_UPLOAD_APP_SEC_BAD;
        break;
    }
    return reply;
}

static uint32_t firmwareFinish(bool valid)
{
    struct AppHdr *app;
    struct Segment *storageSeg;
    uint32_t segState;
    uint32_t ret = NANOHUB_FIRMWARE_UPLOAD_SUCCESS;

    if (!mDownloadState) {
        ret = appSecErrToNanohubReply(mAppSecStatus);
        osLog(LOG_INFO, "%s: no DL status; decoding secure status: %" PRIu32 "\n", __func__, ret);
        return ret;
    }

    app = mDownloadState->start;
    storageSeg = osGetSegment(app);

    if (mAppSecStatus == APP_SEC_NO_ERROR && valid) {
        osLog(LOG_INFO, "%s: Secure verification passed\n", __func__);
        if (storageSeg->state != SEG_ST_RESERVED ||
                mDownloadState->size < sizeof(struct FwCommonHdr) ||
                app->hdr.magic != APP_HDR_MAGIC ||
                app->hdr.fwVer != APP_HDR_VER_CUR) {
            segState = SEG_ST_ERASED;
            osLog(LOG_INFO, "%s: Header verification failed\n", __func__);
        } else {
            segState = SEG_ST_VALID;
        }
    } else {
        segState = SEG_ST_ERASED;
        osLog(LOG_INFO, "%s: Secure verification failed: valid=%d; status=%" PRIu32 "\n", __func__, valid, mAppSecStatus);
    }

    if (!osAppSegmentClose(app, mDownloadState->dstOffset, segState)) {
        osLog(LOG_INFO, "%s: Failed to close segment\n", __func__);
        valid = false;
        mApp = NULL;
    } else {
        segState = osAppSegmentGetState(app);
        mApp = app;
        valid = (segState == SEG_ST_VALID);
    }
    osLog(LOG_INFO, "Loaded %s image type %" PRIu8 ": %" PRIu32
                    " bytes @ %p; state=%02" PRIX32 "; crc=%08" PRIX32 "\n",
                    valid ? "valid" : "invalid",
                    app->hdr.payInfoType, mDownloadState->size,
                    mDownloadState->start, segState,
                    mApp ? osAppSegmentGetCrc(mApp) : 0xFFFFFFFF);

    freeDownloadState(); // no more access to mDownloadState

    if (!valid)
        ret = NANOHUB_FIRMWARE_UPLOAD_APP_SEC_BAD;

    // take extra care about some special payload types
    if (ret == NANOHUB_FIRMWARE_UPLOAD_SUCCESS) {
        switch(app->hdr.payInfoType) {
        case LAYOUT_OS:
            osLog(LOG_INFO, "Performing OS update\n");
            // we want to give this message a chance to reach host before we start erasing stuff
            osDefer(deferredUpdateOs, (void*)app, false);
            break;
        case LAYOUT_KEY:
            ret = appSecErrToNanohubReply(updateKey(app));
            break;
        }
    }

    if (ret != NANOHUB_FIRMWARE_UPLOAD_SUCCESS || (app->hdr.fwFlags & FL_APP_HDR_VOLATILE)) {
        if ((app->hdr.fwFlags & FL_APP_HDR_SECURE))
            osAppWipeData((struct AppHdr*)app);
        osAppSegmentSetState(app, SEG_ST_ERASED);
    }

    // if any error happened after we downloaded and verified image, we say it is unknown fault
    // we don't have download status, so e have to save returned value in secure status field, because
    // host may request the same status multiple times
    if (ret != NANOHUB_FIRMWARE_UPLOAD_SUCCESS)
        mAppSecStatus = APP_SEC_BAD;

    return ret;
}

static void firmwareErase(void *cookie)
{
    if (mDownloadState->erase == true) {
        osLog(LOG_INFO, "%s: erasing shared area\n", __func__);
        osEraseShared();
        mDownloadState->start = osAppSegmentCreate(mDownloadState->size);
        if (!mDownloadState->start)
            firmwareFinish(false);
        mDownloadState->erase = false;
        hostIntfSetInterrupt(NANOHUB_INT_CMD_WAIT);
    }
    mDownloadState->eraseScheduled = false;
}

SET_PACKED_STRUCT_MODE_ON
struct FirmwareWriteCookie
{
    uint32_t evtType;
    union {
#ifdef LEGACY_HAL_ENABLED
        struct NanohubHalLegacyContUploadTx respLegacy;
#endif
        struct NanohubHalContUploadTx resp;
    };
} ATTRIBUTE_PACKED;
SET_PACKED_STRUCT_MODE_OFF

static void writeCookieFree(void *ptr)
{
    struct FirmwareWriteCookie *buf = container_of(ptr, struct FirmwareWriteCookie, resp);
    heapFree(buf);
}

static void firmwareWrite(void *cookie)
{
    bool valid;
    bool finished = false;
    struct FirmwareWriteCookie *resp = cookie;
    // only check crc when cookie is NULL (write came from kernel, not HAL)
    bool checkCrc = !cookie;

    if (mAppSecStatus == APP_SEC_NEED_MORE_TIME) {
        mAppSecStatus = appSecDoSomeProcessing(mDownloadState->appSecState);
    } else if (mDownloadState->lenLeft) {
        const uint8_t *data = mDownloadState->data + mDownloadState->len - mDownloadState->lenLeft;
        uint32_t len = mDownloadState->lenLeft, lenLeft, lenRem = 0;

        if (len > MAX_APP_SEC_RX_DATA_LEN) {
            lenRem = len - MAX_APP_SEC_RX_DATA_LEN;
            len = MAX_APP_SEC_RX_DATA_LEN;
        }

        mAppSecStatus = appSecRxData(mDownloadState->appSecState, data, len, &lenLeft);
        mDownloadState->lenLeft = lenLeft + lenRem;
    }

    valid = (mAppSecStatus == APP_SEC_NO_ERROR);
    if (mAppSecStatus == APP_SEC_NEED_MORE_TIME || mDownloadState->lenLeft) {
        osDefer(firmwareWrite, cookie, false);
        return;
    } else if (valid) {
        if (mDownloadState->srcOffset == mDownloadState->size) {
            mAppSecStatus = appSecRxDataOver(mDownloadState->appSecState);
            finished = true;
            valid = !checkCrc || mDownloadState->crc == ~mDownloadState->srcCrc;
        } else if (mDownloadState->srcOffset > mDownloadState->size) {
            valid = false;
        }
    }
    if (!valid)
        finished = true;
    if (finished) {
        if (firmwareFinish(valid) != NANOHUB_FIRMWARE_UPLOAD_SUCCESS)
            valid = false;
    }
    if (resp) {
        if (resp->evtType == EVT_APP_TO_HOST) {
#ifdef LEGACY_HAL_ENABLED
            resp->respLegacy.success = valid;
            osEnqueueEvtOrFree(EVT_APP_TO_HOST, &resp->respLegacy, writeCookieFree);
#endif
        } else {
            resp->resp.ret.status = !valid;
            osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, &resp->resp, writeCookieFree);
        }
    }
}

static uint32_t doFirmwareChunk(uint8_t *data, uint32_t offset, uint32_t len, void *cookie)
{
    uint32_t reply, ret;

    if (!mDownloadState) {
        reply = NANOHUB_FIRMWARE_CHUNK_REPLY_CANCEL_NO_RETRY;
    } else if (mAppSecStatus == APP_SEC_NEED_MORE_TIME || mDownloadState->lenLeft) {
        reply = NANOHUB_FIRMWARE_CHUNK_REPLY_RESEND;
    } else if (mDownloadState->chunkReply != NANOHUB_FIRMWARE_CHUNK_REPLY_ACCEPTED) {
        reply = mDownloadState->chunkReply;
        firmwareFinish(false);
    } else {
        if (mDownloadState->erase == true) {
            reply = NANOHUB_FIRMWARE_CHUNK_REPLY_WAIT;
            if (!mDownloadState->eraseScheduled) {
                ret = osExtAppStopAppsByAppId(APP_ID_ANY);
                osLog(LOG_INFO, "%s: unloaded apps, ret=%08lx\n", __func__, ret);
                mDownloadState->eraseScheduled = osDefer(firmwareErase, NULL, false);
            }
        } else if (!mDownloadState->start) {
            // this means we can't allocate enough space even after we did erase
            reply = NANOHUB_FIRMWARE_CHUNK_REPLY_CANCEL_NO_RETRY;
            firmwareFinish(false);
        } else if (offset != mDownloadState->srcOffset) {
            reply = NANOHUB_FIRMWARE_CHUNK_REPLY_RESTART;
            resetDownloadState(false, true);
        } else {
            if (!cookie)
                mDownloadState->srcCrc = soft_crc32(data, len, mDownloadState->srcCrc);
            mDownloadState->srcOffset += len;
            memcpy(mDownloadState->data, data, len);
            mDownloadState->lenLeft = mDownloadState->len = len;
            reply = NANOHUB_FIRMWARE_CHUNK_REPLY_ACCEPTED;
            osDefer(firmwareWrite, cookie, false);
        }
    }

    return reply;
}

static uint32_t firmwareChunk(void *rx, uint8_t rx_len, void *tx, uint64_t timestamp)
{
    struct NanohubFirmwareChunkRequest *req = rx;
    struct NanohubFirmwareChunkResponse *resp = tx;
    uint32_t offset = le32toh(req->offset);
    uint8_t len = rx_len - sizeof(req->offset);

    resp->chunkReply = doFirmwareChunk(req->data, offset, len, NULL);

    return sizeof(*resp);
}

static uint32_t doFinishFirmwareUpload(uint32_t *addr, uint32_t *crc)
{
    uint32_t reply;

    if (!mDownloadState) {
        reply = appSecErrToNanohubReply(mAppSecStatus);
        if (addr) {
            if (mApp)
                *addr = (uint32_t)mApp;
            else
                *addr = 0xFFFFFFFF;
        }
        if (crc) {
            if (mApp)
                *crc = osAppSegmentGetCrc(mApp);
            else
                *crc = 0xFFFFFFFF;
        }
    } else if (mDownloadState->srcOffset == mDownloadState->size) {
        reply = NANOHUB_FIRMWARE_UPLOAD_PROCESSING;
    } else {
        reply = firmwareFinish(false);
    }

    return reply;
}

static uint32_t finishFirmwareUpload(void *rx, uint8_t rx_len, void *tx, uint64_t timestamp)
{
    struct NanohubFinishFirmwareUploadResponse *resp = tx;
    resp->uploadReply = doFinishFirmwareUpload(NULL, NULL);
    if (resp->uploadReply != NANOHUB_FIRMWARE_UPLOAD_PROCESSING)
        osLog(LOG_INFO, "%s: reply=%" PRIu8 "\n", __func__, resp->uploadReply);
    return sizeof(*resp);
}

static uint32_t getInterrupt(void *rx, uint8_t rx_len, void *tx, uint64_t timestamp)
{
    struct NanohubGetInterruptRequest *req = rx;
    struct NanohubGetInterruptResponse *resp = tx;
    int i;

    if (rx_len == sizeof(struct NanohubGetInterruptRequest)) {
        for (i = 0; i < HOSTINTF_MAX_INTERRUPTS; i++) {
            if (req->clear[i/32] & (1UL << (i & 31)))
                hostIntfClearInterrupt(i);
        }
    }

    hostIntfCopyInterrupts(resp->interrupts, HOSTINTF_MAX_INTERRUPTS);

    return sizeof(*resp);
}

static uint32_t maskInterrupt(void *rx, uint8_t rx_len, void *tx, uint64_t timestamp)
{
    struct NanohubMaskInterruptRequest *req = rx;
    struct NanohubMaskInterruptResponse *resp = tx;

    hostIntfSetInterruptMask(req->interrupt);

    resp->accepted = true;
    return sizeof(*resp);
}

static uint32_t unmaskInterrupt(void *rx, uint8_t rx_len, void *tx, uint64_t timestamp)
{
    struct NanohubUnmaskInterruptRequest *req = rx;
    struct NanohubUnmaskInterruptResponse *resp = tx;

    hostIntfClearInterruptMask(req->interrupt);

    resp->accepted = true;
    return sizeof(*resp);
}

static void addDelta(struct ApHubSync *sync, uint64_t apTime, uint64_t hubTime)
{
#if DEBUG_APHUB_TIME_SYNC
    syncDebugAdd(apTime, hubTime);
#endif
    apHubSyncAddDelta(sync, apTime, hubTime);
}

static int64_t getAvgDelta(struct ApHubSync *sync)
{
    return apHubSyncGetDelta(sync, sensorGetTime());
}

static int fillBuffer(void *tx, uint32_t totLength, uint32_t *wakeup, uint32_t *nonwakeup)
{
    struct HostIntfDataBuffer *packet = &mTxNext;
    struct HostIntfDataBuffer *firstPacket = tx;
    uint8_t *buf = tx;
    uint32_t length;
    uint32_t prevWakeup, prevNonWakeup;

    prevWakeup = *wakeup;
    prevNonWakeup = *nonwakeup;

    while (hostIntfPacketDequeue(&mTxNext, wakeup, nonwakeup)) {
        length = packet->length + sizeof(packet->evtType);
        if (packet->sensType == SENS_TYPE_INVALID) {
            switch (packet->dataType) {
            case HOSTINTF_DATA_TYPE_APP_TO_HOST:
                packet->evtType = htole32(EVT_APP_TO_HOST);
                break;
            case HOSTINTF_DATA_TYPE_RESET_REASON:
                packet->evtType = htole32(EVT_RESET_REASON);
                break;
            case HOSTINTF_DATA_TYPE_APP_TO_SENSOR_HAL:
                packet->evtType = htole32(EVT_APP_TO_SENSOR_HAL_DATA);
                break;
#ifdef DEBUG_LOG_EVT
            case HOSTINTF_DATA_TYPE_LOG:
                packet->evtType = htole32(HOST_EVT_DEBUG_LOG);
                break;
#endif
            default:
                packet->evtType = htole32(0x00000000);
                break;
            }
        } else {
            packet->evtType = htole32(EVT_NO_FIRST_SENSOR_EVENT + packet->sensType);
            if (packet->referenceTime)
                packet->referenceTime += getAvgDelta(&mTimeSync);

            if (*wakeup > 0)
                packet->firstSample.interrupt = NANOHUB_INT_WAKEUP;
        }

        if ((!totLength || (isSensorEvent(firstPacket->evtType) && isSensorEvent(packet->evtType))) &&
             totLength + length <= sizeof(struct HostIntfDataBuffer)) {
            memcpy(buf + totLength, &mTxNext, length);
            totLength += length;
            if (isSensorEvent(packet->evtType) && packet->firstSample.interrupt == NANOHUB_INT_WAKEUP)
                firstPacket->firstSample.interrupt = NANOHUB_INT_WAKEUP;
        } else {
            mTxNextLength = length;
            *wakeup = prevWakeup;
            *nonwakeup = prevNonWakeup;
            break;
        }

        prevWakeup = *wakeup;
        prevNonWakeup = *nonwakeup;
    }

    return totLength;
}

static void updateInterrupts(void)
{
    uint32_t wakeup = atomicRead32bits(&mTxWakeCnt[0]);
    uint32_t nonwakeup = atomicRead32bits(&mTxWakeCnt[1]);
    bool wakeupStatus = hostIntfGetInterrupt(NANOHUB_INT_WAKEUP);
    bool nonwakeupStatus = hostIntfGetInterrupt(NANOHUB_INT_NONWAKEUP);

    if (!wakeup && wakeupStatus)
        hostIntfClearInterrupt(NANOHUB_INT_WAKEUP);
    else if (wakeup && !wakeupStatus)
        hostIntfSetInterrupt(NANOHUB_INT_WAKEUP);

    if (!nonwakeup && nonwakeupStatus)
        hostIntfClearInterrupt(NANOHUB_INT_NONWAKEUP);
    else if (nonwakeup && !nonwakeupStatus)
        hostIntfSetInterrupt(NANOHUB_INT_NONWAKEUP);
}

void nanohubPrefetchTx(uint32_t interrupt, uint32_t wakeup, uint32_t nonwakeup)
{
    uint64_t state;

    if (wakeup < atomicRead32bits(&mTxWakeCnt[0]))
        wakeup = atomicRead32bits(&mTxWakeCnt[0]);

    if (nonwakeup < atomicRead32bits(&mTxWakeCnt[1]))
        nonwakeup = atomicRead32bits(&mTxWakeCnt[1]);

    if (interrupt == HOSTINTF_MAX_INTERRUPTS && !hostIntfGetInterrupt(NANOHUB_INT_WAKEUP) && !hostIntfGetInterrupt(NANOHUB_INT_NONWAKEUP))
        return;

    atomicWriteByte(&mPrefetchActive, 1);

    if (interrupt < HOSTINTF_MAX_INTERRUPTS)
        hostIntfSetInterrupt(interrupt);

    do {
        if (atomicReadByte(&mTxCurrLength) == 0 && mTxNextLength > 0) {
            memcpy(&mTxCurr, &mTxNext, mTxNextLength);
            atomicWriteByte(&mTxCurrLength, mTxNextLength);
            mTxNextLength = 0;
        }

        if (mTxNextLength == 0) {
            atomicWriteByte(&mTxCurrLength, fillBuffer(&mTxCurr, atomicReadByte(&mTxCurrLength), &wakeup, &nonwakeup));
            atomicWrite32bits(&mTxWakeCnt[0], wakeup);
            atomicWrite32bits(&mTxWakeCnt[1], nonwakeup);
        }

        atomicWriteByte(&mPrefetchActive, 0);

        if (atomicReadByte(&mPrefetchTx)) {
            state = cpuIntsOff();

            // interrupt occured during this call
            // take care of it
            hostIntfTxAck(&mTxCurr, atomicReadByte(&mTxCurrLength));
            atomicWriteByte(&mPrefetchTx, 0);
            atomicWriteByte(&mTxCurrLength, 0);

            cpuIntsRestore(state);

            updateInterrupts();
        } else {
            break;
        }
    } while (mTxNextLength > 0);
}

static void nanohubPrefetchTxDefer(void *cookie)
{
    nanohubPrefetchTx(HOSTINTF_MAX_INTERRUPTS, 0, 0);
}

static uint32_t readEventFast(void *rx, uint8_t rx_len, void *tx, uint64_t timestamp)
{
    struct NanohubReadEventRequest *req = rx;
    uint8_t ret = 0;

    if (atomicReadByte(&mPrefetchActive)) {
        atomicWriteByte(&mPrefetchTx, 1);
        return NANOHUB_FAST_DONT_ACK;
    } else {
        if ((ret = atomicReadByte(&mTxCurrLength))) {
            addDelta(&mTimeSync, req->apBootTime, timestamp);

            memcpy(tx, &mTxCurr, ret);
            atomicWriteByte(&mTxCurrLength, 0);

            updateInterrupts();
            osDefer(nanohubPrefetchTxDefer, NULL, true);
        } else {
            return NANOHUB_FAST_UNHANDLED_ACK;
        }
    }

    return ret;
}

static uint32_t readEvent(void *rx, uint8_t rx_len, void *tx, uint64_t timestamp)
{
    struct NanohubReadEventRequest *req = rx;
    uint8_t *buf = tx;
    uint32_t length, wakeup, nonwakeup;
    uint32_t totLength = 0;

    addDelta(&mTimeSync, req->apBootTime, timestamp);

    if ((totLength = atomicReadByte(&mTxCurrLength))) {
        memcpy(tx, &mTxCurr, totLength);
        atomicWriteByte(&mTxCurrLength, 0);
        updateInterrupts();
        return totLength;
    }

    wakeup = atomicRead32bits(&mTxWakeCnt[0]);
    nonwakeup = atomicRead32bits(&mTxWakeCnt[1]);

    if (mTxNextLength > 0) {
        length = mTxNextLength;
        memcpy(buf, &mTxNext, length);
        totLength = length;
        mTxNextLength = 0;
    }

    totLength = fillBuffer(buf, totLength, &wakeup, &nonwakeup);
    atomicWrite32bits(&mTxWakeCnt[0], wakeup);
    atomicWrite32bits(&mTxWakeCnt[1], nonwakeup);

    if (totLength) {
        updateInterrupts();
    } else {
        hostIntfClearInterrupt(NANOHUB_INT_WAKEUP);
        hostIntfClearInterrupt(NANOHUB_INT_NONWAKEUP);
    }

    return totLength;
}

static bool forwardPacket(uint32_t event, void *data, size_t data_size,
                          void *hdr, size_t hdr_size, uint32_t tid)
{
    bool res;
    uint8_t *hostPacket = data;
    uint8_t *packet = slabAllocatorAlloc(mEventSlab);
    EventFreeF free = slabFree;

    if (!packet) {
        packet = heapAlloc(data_size + hdr_size);
        free = heapFree;
    }
    if (!packet)
        return false;

    if (hdr && hdr_size)
        memcpy(packet, hdr, hdr_size);

    memcpy(packet + hdr_size, hostPacket, data_size);
    if (tid) {
        // send to specific TID
        res = osEnqueuePrivateEvt(event, packet, free, tid);
        if (!res)
            free(packet);
    } else {
        // broadcast to all
        res = osEnqueueEvtOrFree(event, packet, free);
    }

    return res;
}

static uint32_t writeEvent(void *rx, uint8_t rx_len, void *tx, uint64_t timestamp)
{
    struct NanohubWriteEventRequest *req = rx;
    struct NanohubWriteEventResponse *resp = tx;
    uint32_t tid;
    uint32_t event = le32toh(req->evtType);

    if (event == EVT_APP_FROM_HOST) {
        // old version of HAL; message_type is not delivered to CHRE apps
        struct HostMsgHdr *hostPacket = rx;
        if (rx_len >= sizeof(struct HostMsgHdr) &&
            rx_len == sizeof(struct HostMsgHdr) + hostPacket->len &&
            osTidById(&hostPacket->appId, &tid)) {
            resp->accepted = forwardPacket(event, hostPacket + 1, hostPacket->len,
                                           &hostPacket->len, sizeof(hostPacket->len), tid);
        } else {
            resp->accepted = false;
        }
    } else if (event == EVT_APP_FROM_HOST_CHRE) {
        // new version of HAL; full support for CHRE apps
        struct HostMsgHdrChreV10 *hostPacketV10 = rx;
        struct HostMsgHdrChre *hostPacket = rx;
        if (rx_len >= sizeof(struct HostMsgHdrChre) &&
            rx_len == sizeof(struct HostMsgHdrChre) + hostPacket->len &&
            osTidById(&hostPacket->appId, &tid)) {
            if (osAppChreVersion(tid) >= CHRE_API_VERSION_1_1) {
                struct NanohubMsgChreHdr hdr = {
                    .size = hostPacket->len,
                    .endpoint = hostPacket->endpoint,
                    .appEvent = hostPacket->appEventId,
                };
                // CHRE app receives message in new format
                resp->accepted = forwardPacket(event, hostPacket + 1, hostPacket->len,
                                               &hdr, sizeof(hdr), tid);
            } else if (osAppChreVersion(tid) == CHRE_API_VERSION_1_0) {
                struct NanohubMsgChreHdrV10 hdr = {
                    .size = hostPacket->len,
                    .appEvent = hostPacket->appEventId,
                };
                // CHRE app receives message in new format
                resp->accepted = forwardPacket(event, hostPacket + 1, hostPacket->len,
                                               &hdr, sizeof(hdr), tid);
            } else {
                // legacy app receives message in old format
                resp->accepted = forwardPacket(EVT_APP_FROM_HOST, hostPacket + 1, hostPacket->len,
                                               &hostPacket->len, sizeof(hostPacket->len), tid);
            }
        } else if (rx_len >= sizeof(struct HostMsgHdrChreV10) &&
                   rx_len == sizeof(struct HostMsgHdrChreV10) + hostPacketV10->len &&
                   osTidById(&hostPacketV10->appId, &tid)) {
            if (osAppChreVersion(tid) >= CHRE_API_VERSION_1_1) {
                struct NanohubMsgChreHdr hdr = {
                    .size = hostPacketV10->len,
                    .endpoint = CHRE_HOST_ENDPOINT_UNSPECIFIED,
                    .appEvent = hostPacketV10->appEventId,
                };
                // CHRE app receives message in new format
                resp->accepted = forwardPacket(event, hostPacketV10 + 1, hostPacketV10->len,
                                               &hdr, sizeof(hdr), tid);
            } else if (osAppChreVersion(tid) == CHRE_API_VERSION_1_0) {
                struct NanohubMsgChreHdrV10 hdr = {
                    .size = hostPacketV10->len,
                    .appEvent = hostPacketV10->appEventId,
                };
                // CHRE app receives message in new format
                resp->accepted = forwardPacket(event, hostPacketV10 + 1, hostPacketV10->len,
                                               &hdr, sizeof(hdr), tid);
            } else {
                // legacy app receives message in old format
                resp->accepted = forwardPacket(EVT_APP_FROM_HOST, hostPacketV10 + 1, hostPacketV10->len,
                                               &hostPacketV10->len, sizeof(hostPacketV10->len), tid);
            }
        } else {
            resp->accepted = false;
        }
    } else {
        resp->accepted = forwardPacket(event,
                                       req->evtData, rx_len - sizeof(req->evtType),
                                       NULL, 0, 0);
    }

    return sizeof(*resp);
}

const static struct NanohubCommand mBuiltinCommands[] = {
    NANOHUB_COMMAND(NANOHUB_REASON_GET_OS_HW_VERSIONS,
                    getOsHwVersion,
                    getOsHwVersion,
                    struct NanohubOsHwVersionsRequest,
                    struct NanohubOsHwVersionsRequest),
    NANOHUB_COMMAND(NANOHUB_REASON_GET_APP_VERSIONS,
                    NULL,
                    getAppVersion,
                    struct NanohubAppVersionsRequest,
                    struct NanohubAppVersionsRequest),
    NANOHUB_COMMAND(NANOHUB_REASON_QUERY_APP_INFO,
                    NULL,
                    queryAppInfo,
                    struct NanohubAppInfoRequest,
                    struct NanohubAppInfoRequest),
    NANOHUB_COMMAND(NANOHUB_REASON_START_FIRMWARE_UPLOAD,
                    NULL,
                    startFirmwareUpload,
                    struct NanohubStartFirmwareUploadRequest,
                    struct NanohubStartFirmwareUploadRequest),
    NANOHUB_COMMAND(NANOHUB_REASON_FIRMWARE_CHUNK,
                    NULL,
                    firmwareChunk,
                    __le32,
                    struct NanohubFirmwareChunkRequest),
    NANOHUB_COMMAND(NANOHUB_REASON_FINISH_FIRMWARE_UPLOAD,
                    NULL,
                    finishFirmwareUpload,
                    struct NanohubFinishFirmwareUploadRequest,
                    struct NanohubFinishFirmwareUploadRequest),
    NANOHUB_COMMAND(NANOHUB_REASON_GET_INTERRUPT,
                    getInterrupt,
                    getInterrupt,
                    struct { },
                    struct NanohubGetInterruptRequest),
    NANOHUB_COMMAND(NANOHUB_REASON_MASK_INTERRUPT,
                    maskInterrupt,
                    maskInterrupt,
                    struct NanohubMaskInterruptRequest,
                    struct NanohubMaskInterruptRequest),
    NANOHUB_COMMAND(NANOHUB_REASON_UNMASK_INTERRUPT,
                    unmaskInterrupt,
                    unmaskInterrupt,
                    struct NanohubUnmaskInterruptRequest,
                    struct NanohubUnmaskInterruptRequest),
    NANOHUB_COMMAND(NANOHUB_REASON_READ_EVENT,
                    readEventFast,
                    readEvent,
                    struct NanohubReadEventRequest,
                    struct NanohubReadEventRequest),
    NANOHUB_COMMAND(NANOHUB_REASON_WRITE_EVENT,
                    writeEvent,
                    writeEvent,
                    __le32,
                    struct NanohubWriteEventRequest),
};

const struct NanohubCommand *nanohubFindCommand(uint32_t packetReason)
{
    uint32_t i;

    for (i = 0; i < ARRAY_SIZE(mBuiltinCommands); i++) {
        const struct NanohubCommand *cmd = &mBuiltinCommands[i];
        if (cmd->reason == packetReason)
            return cmd;
    }
    return NULL;
}

#ifdef LEGACY_HAL_ENABLED

static void halSendLegacyMgmtResponse(uint32_t cmd, uint32_t status)
{
    struct NanohubHalLegacyMgmtTx *resp;

    resp = heapAlloc(sizeof(*resp));
    if (resp) {
        resp->hdr = (struct NanohubHalLegacyHdr) {
            .appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0),
            .len = sizeof(*resp) - sizeof(resp->hdr) + sizeof(resp->hdr.msg),
            .msg = cmd,
        };
        resp->status = htole32(status);
        osEnqueueEvtOrFree(EVT_APP_TO_HOST, resp, heapFree);
    }
}

static void halLegacyExtAppsOn(void *rx, uint8_t rx_len)
{
    struct NanohubHalLegacyMgmtRx *req = rx;

    halSendLegacyMgmtResponse(NANOHUB_HAL_LEGACY_EXT_APPS_ON, osExtAppStartAppsByAppId(le64toh(unaligned_u64(&req->appId))));
}

static void halLegacyExtAppsOff(void *rx, uint8_t rx_len)
{
    struct NanohubHalLegacyMgmtRx *req = rx;

    halSendLegacyMgmtResponse(NANOHUB_HAL_LEGACY_EXT_APPS_OFF, osExtAppStopAppsByAppId(le64toh(unaligned_u64(&req->appId))));
}

static void halLegacyExtAppDelete(void *rx, uint8_t rx_len)
{
    struct NanohubHalLegacyMgmtRx *req = rx;

    halSendLegacyMgmtResponse(NANOHUB_HAL_LEGACY_EXT_APP_DELETE, osExtAppEraseAppsByAppId(le64toh(unaligned_u64(&req->appId))));
}

static void halLegacyQueryMemInfo(void *rx, uint8_t rx_len)
{
}

static void halLegacyQueryApps(void *rx, uint8_t rx_len)
{
    struct NanohubHalLegacyQueryAppsRx *req = rx;
    struct NanohubHalLegacyQueryAppsTx *resp;
    struct NanohubHalLegacyHdr *hdr;
    uint64_t appId;
    uint32_t appVer, appSize;

    if (osExtAppInfoByIndex(le32toh(req->idx), &appId, &appVer, &appSize)) {
        resp = heapAlloc(sizeof(*resp));
        if (resp) {
            resp->hdr.appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0);
            resp->hdr.len = sizeof(*resp) - sizeof(struct NanohubHalLegacyHdr) + 1;
            resp->hdr.msg = NANOHUB_HAL_LEGACY_QUERY_APPS;
            resp->appId = appId;
            resp->version = appVer;
            resp->flashUse = appSize;
            resp->ramUse = 0;
            osEnqueueEvtOrFree(EVT_APP_TO_HOST, resp, heapFree);
        }
    } else {
        hdr = heapAlloc(sizeof(*hdr));
        if (hdr) {
            hdr->appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0);
            hdr->len = 1;
            hdr->msg = NANOHUB_HAL_LEGACY_QUERY_APPS;
            osEnqueueEvtOrFree(EVT_APP_TO_HOST, hdr, heapFree);
        }
    }
}

static void halLegacyQueryRsaKeys(void *rx, uint8_t rx_len)
{
    struct NanohubHalLegacyQueryRsaKeysRx *req = rx;
    struct NanohubHalLegacyQueryRsaKeysTx *resp;
    int len = 0;
    const uint32_t *ptr;
    uint32_t numKeys;

    if (!(resp = heapAlloc(sizeof(*resp) + NANOHUB_RSA_KEY_CHUNK_LEN)))
        return;

    ptr = BL.blGetPubKeysInfo(&numKeys);
    if (ptr && numKeys * RSA_BYTES > req->offset) {
        len = numKeys * RSA_BYTES - req->offset;
        if (len > NANOHUB_RSA_KEY_CHUNK_LEN)
            len = NANOHUB_RSA_KEY_CHUNK_LEN;
        memcpy(resp->data, (uint8_t *)ptr + req->offset, len);
    }

    resp->hdr.appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0);
    resp->hdr.len = sizeof(*resp) - sizeof(struct NanohubHalLegacyHdr) + 1 + len;
    resp->hdr.msg = NANOHUB_HAL_LEGACY_QUERY_RSA_KEYS;

    osEnqueueEvtOrFree(EVT_APP_TO_HOST, resp, heapFree);
}

static void halLegacyStartUpload(void *rx, uint8_t rx_len)
{
    struct NanohubHalLegacyStartUploadRx *req = rx;
    struct NanohubStartFirmwareUploadRequest hwReq = {
        .size = req->length
    };
    struct NanohubHalLegacyStartUploadTx *resp;

    if (!(resp = heapAlloc(sizeof(*resp))))
        return;

    resp->hdr.appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0);
    resp->hdr.len = sizeof(*resp) - sizeof(struct NanohubHalLegacyHdr) + 1;
    resp->hdr.msg = NANOHUB_HAL_LEGACY_START_UPLOAD;
    resp->success = doStartFirmwareUpload(&hwReq, true);

    osEnqueueEvtOrFree(EVT_APP_TO_HOST, resp, heapFree);
}

static void halLegacyContUpload(void *rx, uint8_t rx_len)
{
    uint32_t offset;
    uint32_t reply;
    uint8_t len;
    struct NanohubHalLegacyContUploadRx *req = rx;
    struct FirmwareWriteCookie *cookie;

    if (!(cookie = heapAlloc(sizeof(*cookie))))
        return;

    cookie->evtType = EVT_APP_TO_HOST;
    cookie->respLegacy.hdr = (struct NanohubHalLegacyHdr) {
        .appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0),
        .len = sizeof(cookie->respLegacy) - sizeof(struct NanohubHalLegacyHdr) + 1,
        .msg = NANOHUB_HAL_LEGACY_CONT_UPLOAD,
    };
    cookie->respLegacy.success = false;

    if (!mDownloadState) {
        reply = NANOHUB_FIRMWARE_CHUNK_REPLY_CANCEL_NO_RETRY;
    } else {
        offset = le32toh(req->offset);
        len = rx_len - sizeof(req->offset);
        reply = doFirmwareChunk(req->data, offset, len, cookie);
    }
    if (reply != NANOHUB_FIRMWARE_CHUNK_REPLY_ACCEPTED) {
        osLog(LOG_ERROR, "%s: reply=%" PRIu32 "\n", __func__, reply);

        osEnqueueEvtOrFree(EVT_APP_TO_HOST, &cookie->respLegacy, writeCookieFree);
    }
}

static void halLegacyFinishUpload(void *rx, uint8_t rx_len)
{
    struct NanohubHalLegacyFinishUploadTx *resp;
    uint32_t reply;

    if (!(resp = heapAlloc(sizeof(*resp))))
        return;

    resp->hdr.appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0);
    resp->hdr.len = sizeof(*resp) - sizeof(struct NanohubHalLegacyHdr) + 1;
    resp->hdr.msg = NANOHUB_HAL_LEGACY_FINISH_UPLOAD;

    reply = doFinishFirmwareUpload(NULL, NULL);

    osLog(LOG_INFO, "%s: reply=%" PRIu32 "\n", __func__, reply);

    resp->success = (reply == NANOHUB_FIRMWARE_UPLOAD_SUCCESS);

    osEnqueueEvtOrFree(EVT_APP_TO_HOST, resp, heapFree);
}

static void halLegacyReboot(void *rx, uint8_t rx_len)
{
    BL.blReboot();
}

const static struct NanohubHalLegacyCommand mBuiltinHalLegacyCommands[] = {
    NANOHUB_HAL_LEGACY_COMMAND(NANOHUB_HAL_LEGACY_EXT_APPS_ON,
                            halLegacyExtAppsOn),
    NANOHUB_HAL_LEGACY_COMMAND(NANOHUB_HAL_LEGACY_EXT_APPS_OFF,
                            halLegacyExtAppsOff),
    NANOHUB_HAL_LEGACY_COMMAND(NANOHUB_HAL_LEGACY_EXT_APP_DELETE,
                            halLegacyExtAppDelete),
    NANOHUB_HAL_LEGACY_COMMAND(NANOHUB_HAL_LEGACY_QUERY_MEMINFO,
                            halLegacyQueryMemInfo),
    NANOHUB_HAL_LEGACY_COMMAND(NANOHUB_HAL_LEGACY_QUERY_APPS,
                            halLegacyQueryApps),
    NANOHUB_HAL_LEGACY_COMMAND(NANOHUB_HAL_LEGACY_QUERY_RSA_KEYS,
                            halLegacyQueryRsaKeys),
    NANOHUB_HAL_LEGACY_COMMAND(NANOHUB_HAL_LEGACY_START_UPLOAD,
                            halLegacyStartUpload),
    NANOHUB_HAL_LEGACY_COMMAND(NANOHUB_HAL_LEGACY_CONT_UPLOAD,
                            halLegacyContUpload),
    NANOHUB_HAL_LEGACY_COMMAND(NANOHUB_HAL_LEGACY_FINISH_UPLOAD,
                            halLegacyFinishUpload),
    NANOHUB_HAL_LEGACY_COMMAND(NANOHUB_HAL_LEGACY_REBOOT,
                            halLegacyReboot),
};

const struct NanohubHalLegacyCommand *nanohubHalLegacyFindCommand(uint8_t msg)
{
    uint32_t i;

    for (i = 0; i < ARRAY_SIZE(mBuiltinHalLegacyCommands); i++) {
        const struct NanohubHalLegacyCommand *cmd = &mBuiltinHalLegacyCommands[i];
        if (cmd->msg == msg)
            return cmd;
    }
    return NULL;
}

#endif /* LEGACY_HAL_ENABLED */

static void halSendAppMgmtResponse(struct NanohubHalAppMgmtRx *req, uint32_t status, struct MgmtStatus stat, uint32_t transactionId)
{
    struct NanohubHalAppMgmtTx *resp;

    resp = heapAlloc(sizeof(*resp));
    if (resp) {
        resp->hdr = (struct NanohubHalHdr) {
            .appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0),
            .len = sizeof(*resp) - sizeof(resp->hdr),
            .transactionId = transactionId,
        };
        resp->ret = (struct NanohubHalRet) {
            .msg = NANOHUB_HAL_APP_MGMT,
            .status = htole32(status),
        };
        resp->cmd = req->cmd;
        resp->stat = stat;
        osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, resp, heapFree);
    }
}

static void halAppMgmt(void *rx, uint8_t rx_len, uint32_t transactionId)
{
    struct NanohubHalAppMgmtRx *req = rx;
    struct MgmtStatus stat;
    uint32_t ret;

    switch (req->cmd) {
    case NANOHUB_HAL_APP_MGMT_START:
        stat.value= osExtAppStartAppsByAppId(le64toh(unaligned_u64(&req->appId)));
        ret = stat.op > 0 ? 0 : -1;
        break;
    case NANOHUB_HAL_APP_MGMT_STOP:
        stat.value = osExtAppStopAppsByAppId(le64toh(unaligned_u64(&req->appId)));
        ret = stat.op > 0 ? 0 : -1;
        break;
    case NANOHUB_HAL_APP_MGMT_UNLOAD:
        stat.value = osExtAppStopAppsByAppId(le64toh(unaligned_u64(&req->appId)));
        ret = stat.op > 0 ? 0 : -1;
        break;
    case NANOHUB_HAL_APP_MGMT_DELETE:
        stat.value = osExtAppEraseAppsByAppId(le64toh(unaligned_u64(&req->appId)));
        ret = stat.erase > 0 ? 0 : -1;
        break;
    default:
        return;
    }

    halSendAppMgmtResponse(req, ret, stat, transactionId);
}

static void deferHalSysMgmtErase(void *cookie)
{
    struct NanohubHalSysMgmtTx *resp = cookie;

    bool success = osEraseShared();

    if (success)
        resp->ret.status = htole32(0);
    else
        resp->ret.status = htole32(-1);

    osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, resp, heapFree);
}

static void halSysMgmt(void *rx, uint8_t rx_len, uint32_t transactionId)
{
    struct NanohubHalSysMgmtRx *req = rx;
    struct NanohubHalSysMgmtTx *resp;
    uint32_t ret = 0;

    if (!(resp = heapAlloc(sizeof(*resp))))
        return;

    resp->hdr = (struct NanohubHalHdr) {
        .appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0),
        .len = sizeof(*resp) - sizeof(resp->hdr),
        .transactionId = transactionId,
    };
    resp->ret = (struct NanohubHalRet) {
        .msg = NANOHUB_HAL_SYS_MGMT,
    };
    resp->cmd = req->cmd;

    switch (req->cmd) {
    case NANOHUB_HAL_SYS_MGMT_ERASE:
        ret = osExtAppStopAppsByAppId(APP_ID_ANY);
        osLog(LOG_INFO, "%s: unloaded apps, ret=%08lx\n", __func__, ret);
        // delay to make sure all apps are unloaded before erasing
        if (osDefer(deferHalSysMgmtErase, resp, false) == false) {
            resp->ret.status = htole32(-1);
            osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, resp, heapFree);
        }
        break;
    case NANOHUB_HAL_SYS_MGMT_REBOOT:
        BL.blReboot();
        break;
    default:
        resp->ret.status = htole32(-1);
        osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, resp, heapFree);
    }
}

static bool copyTLV64(uint8_t *buf, size_t *offset, size_t max_len, uint8_t tag, uint64_t val)
{
    if (*offset + sizeof(uint8_t) + sizeof(uint8_t) + sizeof(uint64_t) > max_len)
        return false;
    buf[(*offset)++] = tag;
    buf[(*offset)++] = sizeof(uint64_t);
    memcpy(&buf[*offset], &val, sizeof(uint64_t));
    *offset += sizeof(uint64_t);
    return true;
}

static bool copyTLV32(uint8_t *buf, size_t *offset, size_t max_len, uint8_t tag, uint32_t val)
{
    if (*offset + sizeof(uint8_t) + sizeof(uint8_t) + sizeof(uint32_t) > max_len)
        return false;
    buf[(*offset)++] = tag;
    buf[(*offset)++] = sizeof(uint32_t);
    memcpy(&buf[*offset], &val, sizeof(uint32_t));
    *offset += sizeof(uint32_t);
    return true;
}

static bool copyTLV8(uint8_t *buf, size_t *offset, size_t max_len, uint8_t tag, uint8_t val)
{
    if (*offset + sizeof(uint8_t) + sizeof(uint8_t) + sizeof(uint8_t) > max_len)
        return false;
    buf[(*offset)++] = tag;
    buf[(*offset)++] = sizeof(uint8_t);
    memcpy(&buf[*offset], &val, sizeof(uint8_t));
    *offset += sizeof(uint8_t);
    return true;
}

static bool copyTLVEmpty(uint8_t *buf, size_t *offset, size_t max_len, uint8_t tag)
{
    if (*offset + sizeof(uint8_t) + sizeof(uint8_t) > max_len)
        return false;
    buf[(*offset)++] = tag;
    buf[(*offset)++] = 0;
    return true;
}

static int processAppTags(const struct AppHdr *app, uint32_t crc, uint32_t size, uint8_t *data, uint8_t *tags, int cnt, bool req_tid)
{
    int i;
    size_t offset = 0;
    const size_t max_len = HOST_HUB_CHRE_PACKET_MAX_LEN - sizeof(struct NanohubHalRet);
    bool success = true;
    uint32_t tid;
    bool tid_valid = false;
    struct Task *task;

    if (app->hdr.magic != APP_HDR_MAGIC ||
        app->hdr.fwVer != APP_HDR_VER_CUR ||
        (app->hdr.fwFlags & FL_APP_HDR_APPLICATION) == 0 ||
        app->hdr.payInfoType != LAYOUT_APP) {
        return 0;
    }

    if (osTidById(&app->hdr.appId, &tid)) {
        tid_valid = true;
        task = osTaskFindByTid(tid);
        if (task) {
            if (task->app != app)
                tid_valid = false;
        } else
            tid_valid = false;
    }

    if (!tid_valid && req_tid)
        return 0;

    for (i=0; i<cnt && success; i++) {
        switch(tags[i]) {
        case NANOHUB_HAL_APP_INFO_APPID:
            success = copyTLV64(data, &offset, max_len, tags[i], app->hdr.appId);
            break;
        case NANOHUB_HAL_APP_INFO_CRC:
            if (size)
                success = copyTLV32(data, &offset, max_len, tags[i], crc);
            else
                success = copyTLVEmpty(data, &offset, max_len, tags[i]);
            break;
        case NANOHUB_HAL_APP_INFO_TID:
            if (tid_valid)
                success = copyTLV32(data, &offset, max_len, tags[i], tid);
            else
                success = copyTLVEmpty(data, &offset, max_len, tags[i]);
            break;
        case NANOHUB_HAL_APP_INFO_VERSION:
            success = copyTLV32(data, &offset, max_len, tags[i], app->hdr.appVer);
            break;
        case NANOHUB_HAL_APP_INFO_ADDR:
            success = copyTLV32(data, &offset, max_len, tags[i], (uint32_t)app);
            break;
        case NANOHUB_HAL_APP_INFO_SIZE:
            if (size)
                success = copyTLV32(data, &offset, max_len, tags[i], size);
            else
                success = copyTLVEmpty(data, &offset, max_len, tags[i]);
            break;
        case NANOHUB_HAL_APP_INFO_HEAP:
            if (tid_valid)
                success = copyTLV32(data, &offset, max_len, tags[i], heapGetTaskSize(tid));
            else
                success = copyTLVEmpty(data, &offset, max_len, tags[i]);
            break;
        case NANOHUB_HAL_APP_INFO_DATA:
            success = copyTLV32(data, &offset, max_len, tags[i], app->sect.got_end - app->sect.data_start);
            break;
        case NANOHUB_HAL_APP_INFO_BSS:
            success = copyTLV32(data, &offset, max_len, tags[i], app->sect.bss_end - app->sect.bss_start);
            break;
        case NANOHUB_HAL_APP_INFO_CHRE_MAJOR:
            if (app->hdr.fwFlags & FL_APP_HDR_CHRE)
                success = copyTLV8(data, &offset, max_len, tags[i],
                    (app->hdr.chreApiMajor == 0xFF && app->hdr.chreApiMinor == 0xFF) ? 0x01 :
                        app->hdr.chreApiMajor);
            else
                success = copyTLVEmpty(data, &offset, max_len, tags[i]);
            break;
        case NANOHUB_HAL_APP_INFO_CHRE_MINOR:
            if (app->hdr.fwFlags & FL_APP_HDR_CHRE)
                success = copyTLV8(data, &offset, max_len, tags[i],
                    (app->hdr.chreApiMajor == 0xFF && app->hdr.chreApiMinor == 0xFF) ? 0x00 :
                        app->hdr.chreApiMinor);
            else
                success = copyTLVEmpty(data, &offset, max_len, tags[i]);
            break;
        case NANOHUB_HAL_APP_INFO_END:
        default:
            success = false;
            copyTLVEmpty(data, &offset, max_len, NANOHUB_HAL_APP_INFO_END);
            break;
        }
    }

    return offset;
}

static void halAppInfo(void *rx, uint8_t rx_len, uint32_t transactionId)
{
    struct NanohubHalAppInfoRx *req = rx;
    struct NanohubHalAppInfoTx *resp;
    struct SegmentIterator it;
    uint32_t state;
    int ret, i;
    uint32_t sharedSize, numApps;
    const struct AppHdr *internal;
    const uint8_t *shared;

    if (!(resp = heapAlloc(sizeof(*resp))))
        return;

    resp->hdr = (struct NanohubHalHdr) {
        .appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0),
        .len = sizeof(*resp) - sizeof(resp->hdr) - sizeof(resp->data),
        .transactionId = transactionId,
    };
    resp->ret = (struct NanohubHalRet) {
        .msg = NANOHUB_HAL_APP_INFO,
    };

    shared = platGetSharedAreaInfo(&sharedSize);
    internal = platGetInternalAppList(&numApps);

    if ((le32toh(req->addr) >= (uint32_t)shared && le32toh(req->addr) < (uint32_t)shared + sharedSize) ||
        (le32toh(req->addr) < (uint32_t)shared &&
            ((uint32_t)shared < (uint32_t)internal ||
                (numApps > 0 && le32toh(req->addr) > (uint32_t)(internal+numApps-1))))) {
        osSegmentIteratorInit(&it);
        while (osSegmentIteratorNext(&it)) {
            state = osSegmentGetState(it.seg);
            switch (state) {
            case SEG_ST_EMPTY:
            case SEG_ST_RESERVED:
                 osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, resp, heapFree);
                 return;
            case SEG_ST_ERASED:
            case SEG_ST_VALID:
                if (le32toh(req->addr) <= (uint32_t)osSegmentGetData(it.seg)) {
                    ret = processAppTags(osSegmentGetData(it.seg), osSegmentGetCrc(it.seg), osSegmentGetSize(it.seg), resp->data, req->tags, rx_len - 4, state == SEG_ST_ERASED);
                    if (ret > 0) {
                        resp->hdr.len += ret;
                        osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, resp, heapFree);
                        return;
                    }
                }
                break;
            }
        }
    } else {
        for (i = 0; i < numApps; i++, internal++) {
            if (le32toh(req->addr) <= (uint32_t)internal) {
                ret = processAppTags(internal, 0, 0, resp->data, req->tags, rx_len - 4, false);
                if (ret > 0) {
                    resp->hdr.len += ret;
                    osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, resp, heapFree);
                    return;
                }
            }
        }
    }

    osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, resp, heapFree);
}

static void halSysInfo(void *rx, uint8_t rx_len, uint32_t transactionId)
{
    extern uint8_t __code_start[];
    extern uint8_t __code_end[];
    extern uint8_t __text_end[];
    extern uint8_t __ram_start[];
    extern uint8_t __ram_end[];

    struct NanohubHalSysInfoRx *req = rx;
    struct NanohubHalSysInfoTx *resp;
    int i;
    size_t offset = 0;
    const size_t max_len = HOST_HUB_CHRE_PACKET_MAX_LEN - sizeof(struct NanohubHalRet);
    bool success = true;
    int free, chunks, largest;
    uint32_t shared_size;

    free = heapGetFreeSize(&chunks, &largest);

    if (!(resp = heapAlloc(sizeof(*resp))))
        return;

    resp->hdr = (struct NanohubHalHdr) {
        .appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0),
        .len = sizeof(*resp) - sizeof(resp->hdr) - sizeof(resp->data),
        .transactionId = transactionId,
    };
    resp->ret = (struct NanohubHalRet) {
        .msg = NANOHUB_HAL_SYS_INFO,
    };

    for (i=0; i<rx_len && success; i++) {
        switch(req->tags[i]) {
        case NANOHUB_HAL_SYS_INFO_HEAP_FREE:
            if (free >= 0)
                success = copyTLV32(resp->data, &offset, max_len, req->tags[i], free);
            else
                success = copyTLVEmpty(resp->data, &offset, max_len, req->tags[i]);
            break;
        case NANOHUB_HAL_SYS_INFO_RAM_SIZE:
            success = copyTLV32(resp->data, &offset, max_len, req->tags[i], __ram_end - __ram_start);
            break;
        case NANOHUB_HAL_SYS_INFO_EEDATA_SIZE:
            success = copyTLV32(resp->data, &offset, max_len, req->tags[i], eeDataGetSize());
            break;
        case NANOHUB_HAL_SYS_INFO_EEDATA_FREE:
            success = copyTLV32(resp->data, &offset, max_len, req->tags[i], eeDataGetFree());
            break;
        case NANOHUB_HAL_SYS_INFO_CODE_SIZE:
            success = copyTLV32(resp->data, &offset, max_len, req->tags[i], __code_end - __code_start);
            break;
        case NANOHUB_HAL_SYS_INFO_CODE_FREE:
            success = copyTLV32(resp->data, &offset, max_len, req->tags[i], __code_end - __text_end);
            break;
        case NANOHUB_HAL_SYS_INFO_SHARED_SIZE:
            platGetSharedAreaInfo(&shared_size);
            success = copyTLV32(resp->data, &offset, max_len, req->tags[i], shared_size);
            break;
        case NANOHUB_HAL_SYS_INFO_SHARED_FREE:
            success = copyTLV32(resp->data, &offset, max_len, req->tags[i], osSegmentGetFree());
            break;
        case NANOHUB_HAL_SYS_INFO_END:
        default:
            success = false;
            copyTLVEmpty(resp->data, &offset, max_len, NANOHUB_HAL_APP_INFO_END);
            break;
        }
    }

    resp->hdr.len += offset;

    osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, resp, heapFree);
}

static void halKeyInfo(void *rx, uint8_t rx_len, uint32_t transactionId)
{
    struct NanohubHalKeyInfoRx *req = rx;
    struct NanohubHalKeyInfoTx *resp;
    const uint32_t *ptr;
    uint32_t numKeys;
    uint32_t dataLength;

    if (!(resp = heapAlloc(sizeof(*resp))))
        return;

    ptr = BL.blGetPubKeysInfo(&numKeys);

    resp->hdr = (struct NanohubHalHdr) {
        .appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0),
        .len = sizeof(*resp) - sizeof(resp->hdr) - sizeof(resp->data),
        .transactionId = transactionId,
    };
    resp->ret = (struct NanohubHalRet) {
        .msg = NANOHUB_HAL_KEY_INFO,
    };

    resp->keyLength = 0;

    if (ptr && req->keyNum < numKeys) {
        if (req->dataOffset < RSA_BYTES) {
            resp->keyLength = RSA_BYTES;
            if (RSA_BYTES - req->dataOffset > NANOHUB_RSA_KEY_CHUNK_LEN)
                dataLength = NANOHUB_RSA_KEY_CHUNK_LEN;
            else
                dataLength = RSA_BYTES - req->dataOffset;
            memcpy(resp->data, (const uint8_t *)ptr + (req->keyNum * RSA_BYTES) + req->dataOffset, dataLength);
            resp->hdr.len += dataLength;
        }
    }

    osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, resp, heapFree);
}

static void halStartUpload(void *rx, uint8_t rx_len, uint32_t transactionId)
{
    struct NanohubHalStartUploadRx *req = rx;
    struct NanohubStartFirmwareUploadRequest hwReq = {
        .size = req->length
    };
    struct NanohubHalStartUploadTx *resp;

    if (!(resp = heapAlloc(sizeof(*resp))))
        return;

    resp->hdr = (struct NanohubHalHdr) {
        .appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0),
        .len = sizeof(*resp) - sizeof(resp->hdr),
        .transactionId = transactionId,
    };

    resp->ret.msg = NANOHUB_HAL_START_UPLOAD;
    if (doStartFirmwareUpload(&hwReq, false))
        resp->ret.status = NANOHUB_FIRMWARE_CHUNK_REPLY_ACCEPTED;
    else
        resp->ret.status = NANOHUB_FIRMWARE_CHUNK_REPLY_NO_SPACE;

    osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, resp, heapFree);
}

static void halContUpload(void *rx, uint8_t rx_len, uint32_t transactionId)
{
    uint32_t offset;
    uint32_t reply;
    uint8_t len;
    struct NanohubHalContUploadRx *req = rx;
    struct FirmwareWriteCookie *cookie;

    if (!(cookie = heapAlloc(sizeof(*cookie))))
        return;

    cookie->evtType = EVT_APP_TO_HOST_CHRE;
    cookie->resp.hdr = (struct NanohubHalHdr) {
        .appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0),
        .len = sizeof(cookie->resp) - sizeof(cookie->resp.hdr),
        .transactionId = transactionId,
    };
    cookie->resp.ret = (struct NanohubHalRet) {
        .msg = NANOHUB_HAL_CONT_UPLOAD,
    };

    if (!mDownloadState) {
        reply = NANOHUB_FIRMWARE_CHUNK_REPLY_CANCEL_NO_RETRY;
    } else {
        offset = le32toh(req->offset);
        len = rx_len - sizeof(req->offset);
        reply = doFirmwareChunk(req->data, offset, len, cookie);
    }
    if (reply != NANOHUB_FIRMWARE_CHUNK_REPLY_ACCEPTED) {
        osLog(LOG_ERROR, "%s: reply=%" PRIu32 "\n", __func__, reply);

        cookie->resp.ret.status = reply;

        osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, &cookie->resp, writeCookieFree);
    }
}

static void halFinishUpload(void *rx, uint8_t rx_len, uint32_t transactionId)
{
    struct NanohubHalFinishUploadTx *resp;
    uint32_t reply;
    uint32_t addr = 0xFFFFFFFF;
    uint32_t crc = 0xFFFFFFFF;

    if (!(resp = heapAlloc(sizeof(*resp))))
        return;

    resp->hdr = (struct NanohubHalHdr) {
        .appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0),
        .len = sizeof(*resp) - sizeof(resp->hdr),
        .transactionId = transactionId,
    };

    reply = doFinishFirmwareUpload(&addr, &crc);

    osLog(LOG_INFO, "%s: reply=%" PRIu32 "\n", __func__, reply);

    resp->ret = (struct NanohubHalRet) {
        .msg = NANOHUB_HAL_FINISH_UPLOAD,
        .status = reply,
    };

    resp->addr = addr;
    resp->crc = crc;

    osEnqueueEvtOrFree(EVT_APP_TO_HOST_CHRE, resp, heapFree);
}

const static struct NanohubHalCommand mBuiltinHalCommands[] = {
    NANOHUB_HAL_COMMAND(NANOHUB_HAL_APP_MGMT,
                            halAppMgmt,
                            struct NanohubHalAppMgmtRx,
                            struct NanohubHalAppMgmtRx),
    NANOHUB_HAL_COMMAND(NANOHUB_HAL_SYS_MGMT,
                            halSysMgmt,
                            struct NanohubHalSysMgmtRx,
                            struct NanohubHalSysMgmtRx),
    NANOHUB_HAL_COMMAND(NANOHUB_HAL_APP_INFO,
                            halAppInfo,
                            __le32,
                            struct NanohubHalAppInfoRx),
    NANOHUB_HAL_COMMAND(NANOHUB_HAL_SYS_INFO,
                            halSysInfo,
                            struct { },
                            struct NanohubHalSysInfoRx),
    NANOHUB_HAL_COMMAND(NANOHUB_HAL_KEY_INFO,
                            halKeyInfo,
                            struct NanohubHalKeyInfoRx,
                            struct NanohubHalKeyInfoRx),
    NANOHUB_HAL_COMMAND(NANOHUB_HAL_START_UPLOAD,
                            halStartUpload,
                            struct NanohubHalStartUploadRx,
                            struct NanohubHalStartUploadRx),
    NANOHUB_HAL_COMMAND(NANOHUB_HAL_CONT_UPLOAD,
                            halContUpload,
                            __le32,
                            struct NanohubHalContUploadRx),
    NANOHUB_HAL_COMMAND(NANOHUB_HAL_FINISH_UPLOAD,
                            halFinishUpload,
                            struct { },
                            struct { }),
};

const struct NanohubHalCommand *nanohubHalFindCommand(uint8_t msg)
{
    uint32_t i;

    for (i = 0; i < ARRAY_SIZE(mBuiltinHalCommands); i++) {
        const struct NanohubHalCommand *cmd = &mBuiltinHalCommands[i];
        if (cmd->msg == msg)
            return cmd;
    }
    return NULL;
}


int64_t hostGetTimeDelta(void)
{
    int64_t delta = getAvgDelta(&mTimeSync);

    if (delta == INT64_MIN)
        return 0ULL;
    else
        return delta;
}

uint64_t hostGetTime(void)
{
    int64_t delta = getAvgDelta(&mTimeSync);

    if (!delta || delta == INT64_MIN)
        return 0ULL;
    else
        return sensorGetTime() + delta;
}

#if DEBUG_APHUB_TIME_SYNC

#define N_APHUB_SYNC_DATA 256
#define PRINT_DELAY 20000000  // unit ns, 20ms
struct ApHubSyncDebug {
    uint64_t apFirst;
    uint64_t hubFirst;
    uint32_t apDelta[N_APHUB_SYNC_DATA]; // us
    uint32_t hubDelta[N_APHUB_SYNC_DATA]; // us
    int printIndex; //negative means not printing
    int writeIndex;

    uint32_t printTimer;
};

static struct ApHubSyncDebug mApHubSyncDebug;

static void syncDebugCallback(uint32_t timerId, void *data)
{

    if (mApHubSyncDebug.printIndex >= mApHubSyncDebug.writeIndex ||
        mApHubSyncDebug.printIndex >= N_APHUB_SYNC_DATA) {
        timTimerCancel(mApHubSyncDebug.printTimer);

        osLog(LOG_DEBUG, "APHUB Done printing %d items", mApHubSyncDebug.printIndex);
        mApHubSyncDebug.writeIndex = 0;
        mApHubSyncDebug.printIndex = -1;

        mApHubSyncDebug.printTimer = 0;
    } else {
        if (mApHubSyncDebug.printIndex == 0) {
            osLog(LOG_DEBUG, "APHUB init %" PRIu64 " %" PRIu64,
                  mApHubSyncDebug.apFirst,
                  mApHubSyncDebug.hubFirst);
        }

        osLog(LOG_DEBUG, "APHUB %d %" PRIu32 " %" PRIu32,
              mApHubSyncDebug.printIndex,
              mApHubSyncDebug.apDelta[mApHubSyncDebug.printIndex],
              mApHubSyncDebug.hubDelta[mApHubSyncDebug.printIndex]);

        mApHubSyncDebug.printIndex++;
    }
}

static void syncDebugTriggerPrint()
{
    if (mApHubSyncDebug.printTimer) {
        //printing already going
        return;
    }

    mApHubSyncDebug.printIndex = 0;

    syncDebugCallback(0, NULL);
    if (!(mApHubSyncDebug.printTimer =
          timTimerSet(PRINT_DELAY, 0, 50, syncDebugCallback, NULL, false /*oneShot*/))) {
        osLog(LOG_WARN, "Cannot get timer for printing");

        mApHubSyncDebug.writeIndex = 0; // discard all data
        mApHubSyncDebug.printIndex = -1; // not printing
    }
}

static void syncDebugAdd(uint64_t ap, uint64_t hub)
{
    if (mApHubSyncDebug.writeIndex >= N_APHUB_SYNC_DATA) {
        //full
        syncDebugTriggerPrint();
        return;
    }

    if (mApHubSyncDebug.writeIndex == 0) {
        mApHubSyncDebug.apFirst = ap;
        mApHubSyncDebug.hubFirst = hub;
    }

    // convert ns to us
    mApHubSyncDebug.apDelta[mApHubSyncDebug.writeIndex] =
            (uint32_t) U64_DIV_BY_CONST_U16((ap - mApHubSyncDebug.apFirst), 1000u);
    mApHubSyncDebug.hubDelta[mApHubSyncDebug.writeIndex] =
            (uint32_t) U64_DIV_BY_CONST_U16((hub - mApHubSyncDebug.hubFirst), 1000u);

    ++mApHubSyncDebug.writeIndex;
}
#endif
