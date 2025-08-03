/*
 * Copyright (C) 2012 The Android Open Source Project
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
#include "ExynosHWCHelper.h"

#include <android-base/properties.h>
#include <linux/videodev2.h>
#include <linux/videodev2_exynos_media.h>
#include <png.h>
#include <sync/sync.h>
#include <sys/mman.h>
#include <utils/CallStack.h>
#include <utils/Errors.h>

#include <iomanip>

#include "ExynosHWC.h"
#include "ExynosHWCDebug.h"
#include "ExynosLayer.h"
#include "ExynosResourceRestriction.h"
#include "VendorVideoAPI.h"
#include "exynos_sync.h"

using vendor::graphics::BufferUsage;
using vendor::graphics::VendorGraphicBufferUsage;
using vendor::graphics::VendorGraphicBufferMeta;
using namespace SOC_VERSION;

#define AFBC_MAGIC  0xafbc

#define FT_LOGD(msg, ...) \
    {\
        if (exynosHWCControl.fenceTracer >= 2) \
            ALOGD("[FenceTracer]::" msg, ##__VA_ARGS__); \
    }
#define FT_LOGE(msg, ...) \
    {\
        if (exynosHWCControl.fenceTracer > 0) \
            ALOGE("[FenceTracer]::" msg, ##__VA_ARGS__); \
    }
#define FT_LOGW(msg, ...) \
    {\
        if (exynosHWCControl.fenceTracer >= 1) \
            ALOGD("[FenceTracer]::" msg, ##__VA_ARGS__); \
    }

extern struct exynos_hwc_control exynosHWCControl;
extern char fence_names[FENCE_MAX][32];

uint32_t getHWC1CompType(int32_t type) {

    uint32_t cType = HWC_FRAMEBUFFER;

    switch(type) {
    case HWC2_COMPOSITION_DEVICE:
    case HWC2_COMPOSITION_EXYNOS:
        cType = HWC_OVERLAY;
        break;
    case HWC2_COMPOSITION_SOLID_COLOR:
        cType = HWC_BACKGROUND;
        break;
    case HWC2_COMPOSITION_CURSOR:
        cType = HWC_CURSOR_OVERLAY;
        break;
    case HWC2_COMPOSITION_SIDEBAND:
        cType = HWC_SIDEBAND;
        break;
    case HWC2_COMPOSITION_CLIENT:
    case HWC2_COMPOSITION_INVALID:
    default:
        cType = HWC_FRAMEBUFFER;
        break;
    }

    return cType;
}

uint32_t getDrmMode(uint64_t flags)
{
    if (flags & BufferUsage::PROTECTED) {
        if (flags & VendorGraphicBufferUsage::PRIVATE_NONSECURE)
            return NORMAL_DRM;
        else
            return SECURE_DRM;
    }
    return NO_DRM;
}

uint32_t getDrmMode(const buffer_handle_t handle)
{
    uint64_t usage = VendorGraphicBufferMeta::get_usage(handle);
    if (usage & BufferUsage::PROTECTED) {
        if (usage & VendorGraphicBufferUsage::PRIVATE_NONSECURE)
            return NORMAL_DRM;
        else
            return SECURE_DRM;
    }

    return NO_DRM;
}

unsigned int isNarrowRgb(int format, android_dataspace data_space)
{
    if (format == HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL)
        return 0;
    else {
        if (isFormatRgb(format))
            return 0;
        else {
            uint32_t data_space_range = (data_space & HAL_DATASPACE_RANGE_MASK);
            if (data_space_range == HAL_DATASPACE_RANGE_UNSPECIFIED) {
                return 1;
            } else if (data_space_range == HAL_DATASPACE_RANGE_FULL) {
                return 0;
            } else {
                return 1;
            }
        }
    }
}

const format_description_t* halFormatToExynosFormat(int inHalFormat, uint32_t inCompressType) {
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++) {
        const int descHalFormat = exynos_format_desc[i].halFormat;

        if ((inHalFormat == descHalFormat) &&
            exynos_format_desc[i].isCompressionSupported(inCompressType)) {
            return &exynos_format_desc[i];
        }
    }
    return nullptr;
}

uint8_t formatToBpp(int format)
{
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++){
        if (exynos_format_desc[i].halFormat == format)
            return exynos_format_desc[i].bpp;
    }

    ALOGW("unrecognized pixel format %u", format);
    return 0;
}

uint8_t DpuFormatToBpp(decon_pixel_format format)
{
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++){
        if (exynos_format_desc[i].s3cFormat == format)
            return exynos_format_desc[i].bpp;
    }
    ALOGW("unrecognized decon format %u", format);
    return 0;
}

bool isFormatRgb(int format)
{
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++){
        if (exynos_format_desc[i].halFormat == format) {
            if (exynos_format_desc[i].type & RGB)
                return true;
            else
                return false;
        }
    }
    return false;
}

bool isFormatYUV(int format)
{
    if (isFormatRgb(format))
        return false;
    return true;
}

bool isFormatSBWC(int format)
{
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++){
        if (exynos_format_desc[i].halFormat == format) {
            if (exynos_format_desc[i].type & COMP_TYPE_SBWC)
                return true;
            else
                return false;
        }
    }
    return false;
}

bool isFormatYUV420(int format)
{
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++){
        if (exynos_format_desc[i].halFormat == format) {
            if (exynos_format_desc[i].type & YUV420)
                return true;
            else
                return false;
        }
    }
    return false;
}

bool isFormatYUV8_2(int format)
{
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++){
        if (exynos_format_desc[i].halFormat == format) {
            if ((exynos_format_desc[i].type & YUV420) &&
                (exynos_format_desc[i].type & BIT8_2))
                return true;
            else
                return false;
        }
    }
    return false;
}

bool isFormat10BitYUV420(int format)
{
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++){
        if (exynos_format_desc[i].halFormat == format) {
            if ((exynos_format_desc[i].type & YUV420) &&
                (exynos_format_desc[i].type & BIT10))
                return true;
            else
                return false;
        }
    }
    return false;
}

bool isFormatYUV422(int format)
{
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++){
        if (exynos_format_desc[i].halFormat == format) {
            if (exynos_format_desc[i].type & YUV422)
                return true;
            else
                return false;
        }
    }
    return false;
}

bool isFormatP010(int format)
{
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++){
        if (exynos_format_desc[i].halFormat == format) {
            if (exynos_format_desc[i].type & P010)
                return true;
            else
                return false;
        }
    }
    return false;
}

bool isFormat10Bit(int format) {
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++) {
        if (exynos_format_desc[i].halFormat == format) {
            if ((exynos_format_desc[i].type & BIT_MASK) == BIT10)
                return true;
            else
                return false;
        }
    }
    return false;
}

bool isFormat8Bit(int format) {
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++) {
        if (exynos_format_desc[i].halFormat == format) {
            if ((exynos_format_desc[i].type & BIT_MASK) == BIT8)
                return true;
            else
                return false;
        }
    }
    return false;
}

bool isFormatYCrCb(int format)
{
    return format == HAL_PIXEL_FORMAT_EXYNOS_YV12_M;
}

bool isFormatLossy(int format)
{
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++) {
        if (exynos_format_desc[i].halFormat == format) {
            uint32_t sbwcType = exynos_format_desc[i].type & FORMAT_SBWC_MASK;
            if (sbwcType && sbwcType != SBWC_LOSSLESS)
                return true;
            else
                return false;
        }
    }
    return false;
}

bool formatHasAlphaChannel(int format)
{
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++) {
        if (exynos_format_desc[i].halFormat == format) {
            return exynos_format_desc[i].hasAlpha;
        }
    }
    return false;
}

bool isAFBCCompressed(const buffer_handle_t handle) {
    if (handle != NULL) {
        return VendorGraphicBufferMeta::is_afbc(handle);
    }

    return false;
}

bool isSBWCCompressed(const buffer_handle_t handle) {
    if (handle != NULL) {
        return VendorGraphicBufferMeta::is_sbwc(handle);
    }

    return false;
}

uint32_t getFormat(const buffer_handle_t handle) {
    if (handle != NULL) {
        return VendorGraphicBufferMeta::get_format(handle);
    }

    return 0;
}

uint64_t getFormatModifier(const buffer_handle_t handle) {
    if (handle != NULL) {
        return VendorGraphicBufferMeta::get_format_modifier(handle);
    }

    return 0;
}

uint32_t getCompressionType(const buffer_handle_t handle) {
    if (handle == NULL) return COMP_TYPE_NONE;

    if (isAFBCCompressed(handle)) {
        return COMP_TYPE_AFBC;
    } else if (isSBWCCompressed(handle)) {
        return COMP_TYPE_SBWC;
    }

    return COMP_TYPE_NONE;
}

CompressionInfo getCompressionInfo(buffer_handle_t handle) {
    CompressionInfo compressionInfo = {COMP_TYPE_NONE, 0};

    if (handle == NULL) return compressionInfo;

    if (isAFBCCompressed(handle)) {
        compressionInfo.type = COMP_TYPE_AFBC;
        compressionInfo.modifier = getFormatModifier(handle);
    } else if (isSBWCCompressed(handle)) {
        compressionInfo.type = COMP_TYPE_SBWC;

        uint32_t format = getFormat(handle);
        if (isFormat10BitYUV420(format))
            compressionInfo.modifier = SBWC_FORMAT_MOD_BLOCK_SIZE_32x5;
        else
            compressionInfo.modifier = SBWC_FORMAT_MOD_BLOCK_SIZE_32x4;
    } else {
        compressionInfo.type = COMP_TYPE_NONE;
    }

    return compressionInfo;
}

String8 getCompressionStr(CompressionInfo compression) {
    String8 result;
    if (compression.type == COMP_TYPE_NONE)
        result.append("None");
    else if (compression.type == COMP_TYPE_AFBC)
        result.appendFormat("AFBC(mod:0x%" PRIx64 ")", compression.modifier);
    else if (compression.type == COMP_TYPE_SBWC)
        result.appendFormat("SBWC(mod:0x%" PRIx64 ")", compression.modifier);
    else
        result.append("Unknown");
    return result;
}

bool isAFBC32x8(CompressionInfo compression) {
    return (compression.type == COMP_TYPE_AFBC) &&
            ((compression.modifier & AFBC_FORMAT_MOD_BLOCK_SIZE_MASK) ==
             AFBC_FORMAT_MOD_BLOCK_SIZE_32x8);
}

uint32_t halDataSpaceToV4L2ColorSpace(android_dataspace data_space)
{
    uint32_t standard_data_space = (data_space & HAL_DATASPACE_STANDARD_MASK);
    switch (standard_data_space) {
    case HAL_DATASPACE_STANDARD_BT2020:
    case HAL_DATASPACE_STANDARD_BT2020_CONSTANT_LUMINANCE:
        return V4L2_COLORSPACE_BT2020;
    case HAL_DATASPACE_STANDARD_DCI_P3:
        return V4L2_COLORSPACE_DCI_P3;
    case HAL_DATASPACE_STANDARD_BT709:
        return V4L2_COLORSPACE_REC709;
    default:
        return V4L2_COLORSPACE_DEFAULT;
    }
    return V4L2_COLORSPACE_DEFAULT;
}

enum decon_pixel_format halFormatToDpuFormat(int format, uint32_t compressType) {
    auto exynosFormat = halFormatToExynosFormat(format, compressType);
    return (exynosFormat != nullptr) ? exynosFormat->s3cFormat : DECON_PIXEL_FORMAT_MAX;
}

uint32_t DpuFormatToHalFormat(int format, uint32_t /*compressType*/) {
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++){
        if (exynos_format_desc[i].s3cFormat == static_cast<decon_pixel_format>(format))
            return exynos_format_desc[i].halFormat;
    }
    return HAL_PIXEL_FORMAT_EXYNOS_UNDEFINED;
}

int halFormatToDrmFormat(int format, uint32_t compressType)
{
    auto exynosFormat = halFormatToExynosFormat(format, compressType);
    return (exynosFormat != nullptr) ? exynosFormat->drmFormat : DRM_FORMAT_UNDEFINED;
}

int32_t drmFormatToHalFormats(int format, std::vector<uint32_t> *halFormats)
{
    if (halFormats == NULL)
        return -EINVAL;

    halFormats->clear();
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++){
        if (exynos_format_desc[i].drmFormat == format) {
            halFormats->push_back(exynos_format_desc[i].halFormat);
        }
    }
    return NO_ERROR;
}

int drmFormatToHalFormat(int format)
{
    for (unsigned int i = 0; i < FORMAT_MAX_CNT; i++){
        if (exynos_format_desc[i].drmFormat == format)
            return exynos_format_desc[i].halFormat;
    }
    return HAL_PIXEL_FORMAT_EXYNOS_UNDEFINED;
}

android_dataspace colorModeToDataspace(android_color_mode_t mode)
{
    android_dataspace dataSpace = HAL_DATASPACE_UNKNOWN;
    switch (mode) {
        case HAL_COLOR_MODE_STANDARD_BT601_625:
            dataSpace = HAL_DATASPACE_STANDARD_BT601_625;
            break;
        case HAL_COLOR_MODE_STANDARD_BT601_625_UNADJUSTED:
            dataSpace = HAL_DATASPACE_STANDARD_BT601_625_UNADJUSTED;
            break;
        case HAL_COLOR_MODE_STANDARD_BT601_525:
            dataSpace = HAL_DATASPACE_STANDARD_BT601_525;
            break;
        case HAL_COLOR_MODE_STANDARD_BT601_525_UNADJUSTED:
            dataSpace = HAL_DATASPACE_STANDARD_BT601_525_UNADJUSTED;
            break;
        case HAL_COLOR_MODE_STANDARD_BT709:
            dataSpace = HAL_DATASPACE_STANDARD_BT709;
            break;
        case HAL_COLOR_MODE_DCI_P3:
            dataSpace = HAL_DATASPACE_DCI_P3;
            break;
        case HAL_COLOR_MODE_ADOBE_RGB:
            dataSpace = HAL_DATASPACE_ADOBE_RGB;
            break;
        case HAL_COLOR_MODE_DISPLAY_P3:
            dataSpace = HAL_DATASPACE_DISPLAY_P3;
            break;
        case HAL_COLOR_MODE_SRGB:
            dataSpace = HAL_DATASPACE_V0_SRGB;
            break;
        case HAL_COLOR_MODE_NATIVE:
            dataSpace = HAL_DATASPACE_UNKNOWN;
            break;
        default:
            break;
    }
    return dataSpace;
}

uint64_t halTransformToDrmRot(uint32_t halTransform)
{
    switch (halTransform) {
    case HAL_TRANSFORM_FLIP_H:
        return DRM_MODE_REFLECT_Y|DRM_MODE_ROTATE_0;
    case HAL_TRANSFORM_FLIP_V:
        return DRM_MODE_REFLECT_X|DRM_MODE_ROTATE_0;
    case HAL_TRANSFORM_ROT_180:
        return DRM_MODE_ROTATE_180;
    case HAL_TRANSFORM_ROT_90:
        return DRM_MODE_ROTATE_90;
    case (HAL_TRANSFORM_ROT_90|HAL_TRANSFORM_FLIP_H):
        /*
         * HAL: HAL_TRANSFORM_FLIP_H -> HAL_TRANSFORM_ROT_90
         * DPP: ROT_90 -> XFLIP
         */
        return (DRM_MODE_ROTATE_90|DRM_MODE_REFLECT_X);
    case (HAL_TRANSFORM_ROT_90|HAL_TRANSFORM_FLIP_V):
        /*
         * HAL: HAL_TRANSFORM_FLIP_V -> HAL_TRANSFORM_ROT_90
         * DPP: ROT_90 -> YFLIP
         */
        return (DRM_MODE_ROTATE_90|DRM_MODE_REFLECT_Y);
    case HAL_TRANSFORM_ROT_270:
        return DRM_MODE_ROTATE_270;
    default:
        return DRM_MODE_ROTATE_0;
    }
}

void dumpHandle(uint32_t type, buffer_handle_t h)
{
    if (h == NULL)
        return;

    VendorGraphicBufferMeta gmeta(h);

    HDEBUGLOGD(type, "\t\tformat = %d, width = %u, height = %u, stride = %u, vstride = %u",
            gmeta.format, gmeta.width, gmeta.height, gmeta.stride, gmeta.vstride);
}

void dumpExynosImage(uint32_t type, exynos_image &img)
{
    if (!hwcCheckDebugMessages(type))
        return;

    String8 result;
    dumpExynosImage(result, img);

    ALOGD("%s", result.c_str());
}

void dumpExynosImage(String8& result, const exynos_image& img) {
    result.appendFormat("\tbufferHandle: %p, fullWidth: %d, fullHeight: %d, x: %d, y: %d, w: %d, "
                        "h: %d, format: %s\n",
                        img.bufferHandle, img.fullWidth, img.fullHeight, img.x, img.y, img.w, img.h,
                        getFormatStr(img.format, img.compressionInfo.type).c_str());
    result.appendFormat("\tusageFlags: 0x%" PRIx64 ", layerFlags: 0x%8x, acquireFenceFd: %d, releaseFenceFd: %d\n",
            img.usageFlags, img.layerFlags, img.acquireFenceFd, img.releaseFenceFd);
    result.appendFormat("\tdataSpace(%d), blending(%d), transform(0x%2x), compression: %s\n",
                        img.dataSpace, img.blending, img.transform,
                        getCompressionStr(img.compressionInfo).c_str());
    if (img.bufferHandle != NULL) {
        VendorGraphicBufferMeta gmeta(img.bufferHandle);
        result.appendFormat("\tbuffer's stride: %d, %d\n", gmeta.stride, gmeta.vstride);
    }
}

void printExynosLayer(const ExynosLayer* layer) {
    if (layer == nullptr) {
        return;
    }

    const_cast<ExynosLayer*>(layer)->printLayer();
}

bool isSrcCropFloat(hwc_frect &frect)
{
    return (frect.left != (int)frect.left) ||
        (frect.top != (int)frect.top) ||
        (frect.right != (int)frect.right) ||
        (frect.bottom != (int)frect.bottom);
}

bool isScaled(exynos_image &src, exynos_image &dst)
{
    uint32_t srcW = src.w;
    uint32_t srcH = src.h;
    uint32_t dstW = dst.w;
    uint32_t dstH = dst.h;

    if (!!(src.transform & HAL_TRANSFORM_ROT_90)) {
        dstW = dst.h;
        dstH = dst.w;
    }

    return ((srcW != dstW) || (srcH != dstH));
}

bool isScaledDown(exynos_image &src, exynos_image &dst)
{
    uint32_t srcW = src.w;
    uint32_t srcH = src.h;
    uint32_t dstW = dst.w;
    uint32_t dstH = dst.h;

    if (!!(src.transform & HAL_TRANSFORM_ROT_90)) {
        dstW = dst.h;
        dstH = dst.w;
    }

    return ((srcW > dstW) || (srcH > dstH));
}

bool hasHdrInfo(const exynos_image& img) {
    uint32_t dataSpace = img.dataSpace;

    /* By reference Layer's dataspace */
    uint32_t standard = (dataSpace & HAL_DATASPACE_STANDARD_MASK);
    uint32_t transfer = (dataSpace & HAL_DATASPACE_TRANSFER_MASK);

    if ((standard == HAL_DATASPACE_STANDARD_BT2020) ||
            (standard == HAL_DATASPACE_STANDARD_BT2020_CONSTANT_LUMINANCE) ||
            (standard == HAL_DATASPACE_STANDARD_DCI_P3)) {
        if ((transfer == HAL_DATASPACE_TRANSFER_ST2084) ||
                (transfer == HAL_DATASPACE_TRANSFER_HLG))
            return true;
        else
            return false;
    }

    return false;
}

bool hasHdrInfo(android_dataspace dataSpace) {
    exynos_image img;
    img.dataSpace = dataSpace;
    return hasHdrInfo(img);
}

bool hasHdr10Plus(exynos_image &img) {
    /* TODO Check layer has hdr10 and dynamic metadata here */
    return (img.metaType & VIDEO_INFO_TYPE_HDR_DYNAMIC) ? true : false;
}

String8 getFormatStr(int format, uint32_t compressType) {
    auto exynosFormat = halFormatToExynosFormat(format, compressType);

    if (exynosFormat != nullptr) {
        return exynosFormat->name;
    }

    String8 result;
    result.appendFormat("? %08x", format);
    return result;
}

void adjustRect(hwc_rect_t &rect, int32_t width, int32_t height)
{
    if (rect.left < 0)
        rect.left = 0;
    if (rect.left > width)
        rect.left = width;
    if (rect.top < 0)
        rect.top = 0;
    if (rect.top > height)
        rect.top = height;
    if (rect.right < rect.left)
        rect.right = rect.left;
    if (rect.right > width)
        rect.right = width;
    if (rect.bottom < rect.top)
        rect.bottom = rect.top;
    if (rect.bottom > height)
        rect.bottom = height;
}

uint32_t getBufferNumOfFormat(int format, uint32_t compressType) {
    auto exynosFormat = halFormatToExynosFormat(format, compressType);
    return (exynosFormat != nullptr) ? exynosFormat->bufferNum : 0;
}

uint32_t getPlaneNumOfFormat(int format, uint32_t compressType) {
    auto exynosFormat = halFormatToExynosFormat(format, compressType);
    return (exynosFormat != nullptr) ? exynosFormat->planeNum : 0;
}

uint32_t getBytePerPixelOfPrimaryPlane(int format) {
    if (isFormatRgb(format))
        return (formatToBpp(format) / 8);
    else if (isFormat10BitYUV420(format))
        return 2;
    else if (isFormatYUV420(format))
        return 1;
    else
        return 0;
}

void setFenceName(int fenceFd, HwcFenceType fenceType) {
    if (fenceFd >= 3)
        ioctl(fenceFd, SYNC_IOC_FENCE_NAME, fence_names[fenceType]);
    else if (fenceFd == -1) {
        HDEBUGLOGD(eDebugFence, "%s : fence (type %d) is -1", __func__, (int)fenceType);
    }
    else {
        ALOGW("%s : fence (type %d) is less than 3", __func__, (int)fenceType);
        hwc_print_stack();
    }
}

// TODO(b/273890355): remove this function and get buffer size from gralloc
uint32_t getExynosBufferYLength(uint32_t width, uint32_t height, int format)
{
    switch (format) {
    case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M:
    case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_PRIV:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M:
    case HAL_PIXEL_FORMAT_EXYNOS_YV12_M:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P_M:
        return NV12M_Y_SIZE(width, height);
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B:
        HDEBUGLOGD(eDebugMPP, "8bit size(Y) : %d, extra size : %d", NV12M_Y_SIZE(width, height), NV12M_Y_2B_SIZE(width, height));
        return NV12M_Y_SIZE(width, height) + NV12M_Y_2B_SIZE(width, height);
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_S10B:
        return NV12N_10B_Y_8B_SIZE(width, height) + NV12N_10B_Y_2B_SIZE(width, height);
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_M:
        HDEBUGLOGD(eDebugMPP, "size(Y) : %d", P010M_Y_SIZE(width, height));
        return P010M_Y_SIZE(width, height);
    case HAL_PIXEL_FORMAT_YCBCR_P010:
        HDEBUGLOGD(eDebugMPP, "size(Y) : %d", P010_Y_SIZE(width, height));
        return P010_Y_SIZE(width, height);
    case MALI_GRALLOC_FORMAT_INTERNAL_P010:
        return 2 * __ALIGN_UP(width, 32) * __ALIGN_UP(height, 2);
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN:
        return NV12N_Y_SIZE(width, height);
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_SPN:
        return 2 * __ALIGN_UP(width, 64) * __ALIGN_UP(height, 16);
    case HAL_PIXEL_FORMAT_GOOGLE_NV12_SP_10B:
        return 2 * __ALIGN_UP(width, 64) * __ALIGN_UP(height, 16);
    case HAL_PIXEL_FORMAT_GOOGLE_NV12_SP:
        return __ALIGN_UP(width, 64) * __ALIGN_UP(height, 16);
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L50:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L75:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L50:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L75:
    case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_SBWC:
        return SBWC_8B_Y_SIZE(width, height) +
            SBWC_8B_Y_HEADER_SIZE(width, height);
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L40:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L60:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L80:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L40:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L60:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L80:
    case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_10B_SBWC:
        return SBWC_10B_Y_SIZE(width, height) +
            SBWC_10B_Y_HEADER_SIZE(width, height);
    case MALI_GRALLOC_FORMAT_INTERNAL_NV21:
        return __ALIGN_UP(width, 64) * __ALIGN_UP(height, 2);
    }

    return NV12M_Y_SIZE(width, height) + ((width % 128) == 0 ? 0 : 256);
}

// TODO(b/273890355): no one is using this function. It can be removed.
uint32_t getExynosBufferCbCrLength(uint32_t width, uint32_t height, int format)
{
    switch (format) {
    case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M:
    case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_FULL:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_PRIV:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M:
    case HAL_PIXEL_FORMAT_EXYNOS_YV12_M:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_P_M:
        return NV12M_CBCR_SIZE(width, height);
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_S10B:
        HDEBUGLOGD(eDebugMPP, "8bit size(CbCr) : %d, extra size : %d",NV12M_CBCR_SIZE(width, height), NV12M_CBCR_2B_SIZE(width, height));
        return NV12M_CBCR_SIZE(width, height) + NV12M_CBCR_2B_SIZE(width, height);
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_M:
        HDEBUGLOGD(eDebugMPP, "size(CbCr) : %d", P010M_CBCR_SIZE(width, height));
        return P010M_CBCR_SIZE(width, height);
    case HAL_PIXEL_FORMAT_YCBCR_P010:
        HDEBUGLOGD(eDebugMPP, "size(CbCr) : %d", P010_CBCR_SIZE(width, height));
        return P010_CBCR_SIZE(width, height);
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_P010_SPN:
        return __ALIGN_UP(width, 64) * __ALIGN_UP(height, 16);
    case HAL_PIXEL_FORMAT_GOOGLE_NV12_SP_10B:
        return __ALIGN_UP(width, 64) * __ALIGN_UP(height, 16);
    case HAL_PIXEL_FORMAT_GOOGLE_NV12_SP:
        return __ALIGN_UP(width, 64) * __ALIGN_UP(height, 16) / 2;
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L50:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_SBWC_L75:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L50:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_SBWC_L75:
    case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_SBWC:
        return SBWC_8B_CBCR_SIZE(width, height) +
            SBWC_8B_CBCR_HEADER_SIZE(width, height);
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L40:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L60:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SP_M_10B_SBWC_L80:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L40:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L60:
    case HAL_PIXEL_FORMAT_EXYNOS_YCbCr_420_SPN_10B_SBWC_L80:
    case HAL_PIXEL_FORMAT_EXYNOS_YCrCb_420_SP_M_10B_SBWC:
        return SBWC_10B_CBCR_SIZE(width, height) +
            SBWC_10B_CBCR_HEADER_SIZE(width, height);
    }

    return NV12M_CBCR_SIZE(width, height);
}

int getBufLength(buffer_handle_t handle, uint32_t planerNum, size_t *length, int format, uint32_t width, uint32_t height)
{
    uint32_t bufferNumber = getBufferNumOfFormat(format, getCompressionType(handle));
    if ((bufferNumber == 0) || (bufferNumber > planerNum))
        return -EINVAL;

    VendorGraphicBufferMeta gmeta(handle);

    switch (bufferNumber) {
        case 1:
            length[0] = gmeta.size;
            break;
        case 2:
            HDEBUGLOGD(eDebugMPP, "-- %s x : %d y : %d format : %d",__func__, width, height, format);
            length[0] = gmeta.size;
            length[1] = gmeta.size1;
            HDEBUGLOGD(eDebugMPP, "Y size : %zu CbCr size : %zu", length[0], length[1]);
            break;
        case 3:
            length[0] = width * height;
            length[1]= (length[0]/4);
            length[2]= (length[0]/4);
            break;
    }
    return NO_ERROR;
}

int fence_close(int fence, ExynosDisplay* display, HwcFdebugFenceType type, HwcFdebugIpType ip) {
    if (display != NULL) setFenceInfo(fence, display, type, ip, HwcFenceDirection::CLOSE);
    return hwcFdClose(fence);
}

bool fence_valid(int fence) {
    if (fence == -1){
        HDEBUGLOGD(eDebugFence, "%s : fence is -1", __func__);
        return false;
    } else if (fence < 3) {
        ALOGW("%s : fence (fd:%d) is less than 3", __func__, fence);
        hwc_print_stack();
        return true;
    }
    return true;
}

int hwcFdClose(int fd) {
    if (fd>= 3)
        close(fd);
    else if (fd == -1){
        HDEBUGLOGD(eDebugFence, "%s : Fd is -1", __func__);
    } else {
        ALOGW("%s : Fd:%d is less than 3", __func__, fd);
        hwc_print_stack();
    }
    return -1;
}

int hwc_dup(int fd, ExynosDisplay* display, HwcFdebugFenceType type, HwcFdebugIpType ip,
            bool pendingAllowed) {
    int dup_fd = -1;

    if (fd>= 3)
        dup_fd = dup(fd);
    else if (fd == -1){
        HDEBUGLOGD(eDebugFence, "%s : Fd is -1", __func__);
    } else {
        ALOGW("%s : Fd:%d is less than 3", __func__, fd);
        hwc_print_stack();
    }

    if ((dup_fd < 3) && (dup_fd != -1)) {
        ALOGW("%s : Dupulicated Fd:%d is less than 3 : %d", __func__, fd, dup_fd);
        hwc_print_stack();
    }

    setFenceInfo(dup_fd, display, type, ip, HwcFenceDirection::DUP, pendingAllowed, fd);
    FT_LOGD("duplicated %d from %d", dup_fd, fd);

    return dup_fd;
}

int hwc_print_stack() {
    /* CallStack stack; */
    /* stack.update(); */
    /* stack.log("HWCException", ANDROID_LOG_ERROR, "HWCException"); */
    return 0;
}

String8 getLocalTimeStr(struct timeval tv) {
    struct tm* localTime = (struct tm*)localtime((time_t*)&tv.tv_sec);
    return String8::format("%02d-%02d %02d:%02d:%02d.%03lu(%lu)", localTime->tm_mon + 1,
                           localTime->tm_mday, localTime->tm_hour, localTime->tm_min,
                           localTime->tm_sec, tv.tv_usec / 1000,
                           ((tv.tv_sec * 1000) + (tv.tv_usec / 1000)));
}

void setFenceInfo(uint32_t fd, const ExynosDisplay *display, HwcFdebugFenceType type,
                  HwcFdebugIpType ip, HwcFenceDirection direction, bool pendingAllowed,
                  int32_t dupFrom) {
    if (!fence_valid(fd) || display == NULL) return;

    ExynosDevice* device = display->mDevice;
    device->mFenceTracker.updateFenceInfo(fd, display, type, ip, direction, pendingAllowed,
                                          dupFrom);
}

void FenceTracker::updateFenceInfo(uint32_t fd, const ExynosDisplay *display,
                                   HwcFdebugFenceType type, HwcFdebugIpType ip,
                                   HwcFenceDirection direction, bool pendingAllowed,
                                   int32_t dupFrom) {
    std::scoped_lock lock(mFenceMutex);
    HwcFenceInfo &info = mFenceInfos[fd];
    info.displayId = display->mDisplayId;

    if (info.leaking) {
        return;
    }

    switch (direction) {
        case HwcFenceDirection::FROM:
            info.usage++;
            break;
        case HwcFenceDirection::TO:
            info.usage--;
            break;
        case HwcFenceDirection::DUP:
            info.usage++;
            info.dupFrom = dupFrom;
            break;
        case HwcFenceDirection::CLOSE:
            info.usage--;
            if (info.usage < 0) info.usage = 0;
            break;
        case HwcFenceDirection::UPDATE:
            break;
        default:
            ALOGE("Fence trace : Undefined direction!");
            break;
    }

    if (info.usage == 0) {
        mFenceInfos.erase(fd);
        return;
    } else if (info.usage < 0) {
        ALOGE("%s : Invalid negative usage (%d) for Fence FD:%d", __func__, info.usage, fd);
        printLastFenceInfoLocked(fd);
    }

    HwcFenceTrace trace = {.direction = direction, .type = type, .ip = ip, .time = {0, 0}};

    gettimeofday(&trace.time, NULL);

    info.traces.push_back(trace);

    FT_LOGW("FD : %d, direction : %d, type : %d, ip : %d", fd, direction, type, ip);

    // Fence's usage count shuld be zero at end of frame(present done).
    // This flag means usage count of the fence can be pended over frame.
    info.pendingAllowed = pendingAllowed;
}

void FenceTracker::printLastFenceInfoLocked(uint32_t fd) {
    if (!fence_valid(fd)) return;

    auto it = mFenceInfos.find(fd);
    if (it == mFenceInfos.end()) return;
    HwcFenceInfo &info = it->second;
    FT_LOGD("---- Fence FD : %d, Display(%d) ----", fd, info.displayId);
    FT_LOGD("usage: %d, dupFrom: %d, pendingAllowed: %d, leaking: %d", info.usage, info.dupFrom,
            info.pendingAllowed, info.leaking);

    for (const auto &trace : info.traces) {
        FT_LOGD("> dir: %d, type: %d, ip: %d, time:%s", trace.direction, trace.type, trace.ip,
                getLocalTimeStr(trace.time).c_str());
    }
}

void FenceTracker::dumpFenceInfoLocked(int32_t count) {
    FT_LOGD("Dump fence (up to %d fences) ++", count);
    for (const auto &[fd, info] : mFenceInfos) {
        if (info.pendingAllowed) continue;
        if (count-- <= 0) break;
        printLastFenceInfoLocked(fd);
    }
    FT_LOGD("Dump fence --");
}

void FenceTracker::printLeakFdsLocked() {
    auto reportLeakFdsLocked = [&fenceInfos = mFenceInfos](int sign) REQUIRES(mFenceMutex) {
        String8 errString;
        errString.appendFormat("Leak Fds (%d) :\n", sign);

        int cnt = 0;
        for (const auto &[fd, info] : fenceInfos) {
            if (!info.leaking) continue;
            if (info.usage * sign > 0) {
                errString.appendFormat("%d,", fd);
                if ((++cnt % 10) == 0) {
                    errString.append("\n");
                }
            }
        }

        FT_LOGW("%s", errString.c_str());
    };

    reportLeakFdsLocked(+1);
    reportLeakFdsLocked(-1);
}

void FenceTracker::dumpNCheckLeakLocked() {
    FT_LOGD("Dump leaking fence ++");
    for (auto &[fd, info] : mFenceInfos) {
        if (!info.pendingAllowed) {
            // leak is occurred in this frame first
            if (!info.leaking) {
                info.leaking = true;
                printLastFenceInfoLocked(fd);
            }
        }
    }

    int priv = exynosHWCControl.fenceTracer;
    exynosHWCControl.fenceTracer = 3;
    printLeakFdsLocked();
    exynosHWCControl.fenceTracer = priv;

    FT_LOGD("Dump leaking fence --");
}

bool FenceTracker::fenceWarnLocked(uint32_t threshold) {
    uint32_t cnt = mFenceInfos.size();

    if (cnt > threshold) {
        ALOGE("Fence leak! -- the number of fences(%d) exceeds threshold(%d)", cnt, threshold);
        int priv = exynosHWCControl.fenceTracer;
        exynosHWCControl.fenceTracer = 3;
        dumpFenceInfoLocked(10);
        exynosHWCControl.fenceTracer = priv;
    }

    return (cnt > threshold);
}

bool FenceTracker::validateFencePerFrameLocked(const ExynosDisplay *display) {
    bool ret = true;

    for (const auto &[fd, info] : mFenceInfos) {
        if (info.displayId != display->mDisplayId) continue;
        if ((!info.pendingAllowed) && (!info.leaking)) {
            ret = false;
            break;
        }
    }

    if (!ret) {
        int priv = exynosHWCControl.fenceTracer;
        exynosHWCControl.fenceTracer = 3;
        dumpNCheckLeakLocked();
        exynosHWCControl.fenceTracer = priv;
    }

    return ret;
}

bool FenceTracker::validateFences(ExynosDisplay *display) {
    std::scoped_lock lock(mFenceMutex);

    if (!validateFencePerFrameLocked(display)) {
        ALOGE("You should doubt fence leak!");
        saveFenceTraceLocked(display);
        return false;
    }

    if (fenceWarnLocked(MAX_FENCE_THRESHOLD)) {
        printLeakFdsLocked();
        saveFenceTraceLocked(display);
        return false;
    }

    if (exynosHWCControl.doFenceFileDump) {
        ALOGD("Fence file dump !");
        saveFenceTraceLocked(display);
        exynosHWCControl.doFenceFileDump = false;
    }

    return true;
}

int32_t FenceTracker::saveFenceTraceLocked(ExynosDisplay *display) {
    int32_t ret = NO_ERROR;
    auto &fileWriter = display->mFenceFileWriter;

    if (!fileWriter.chooseOpenedFile()) {
        return -1;
    }

    String8 saveString;

    struct timeval tv;
    gettimeofday(&tv, NULL);
    saveString.appendFormat("\n====== Fences at time:%s ======\n", getLocalTimeStr(tv).c_str());

    for (const auto &[fd, info] : mFenceInfos) {
        saveString.appendFormat("---- Fence FD : %d, Display(%d) ----\n", fd, info.displayId);
        saveString.appendFormat("usage: %d, dupFrom: %d, pendingAllowed: %d, leaking: %d\n",
                                info.usage, info.dupFrom, info.pendingAllowed, info.leaking);

        for (const auto &trace : info.traces) {
            saveString.appendFormat("> dir: %d, type: %d, ip: %d, time:%s\n", trace.direction,
                                    trace.type, trace.ip, getLocalTimeStr(trace.time).c_str());
        }
    }

    fileWriter.write(saveString);
    fileWriter.flush();
    return ret;
}

String8 getMPPStr(int typeId) {
    if (typeId < MPP_DPP_NUM){
        int cnt = sizeof(available_otf_mpp_units)/sizeof(exynos_mpp_t);
        for (int i = 0; i < cnt; i++){
            if (available_otf_mpp_units[i].physicalType == typeId)
                return String8(available_otf_mpp_units[i].name);
        }
    } else {
        int cnt = sizeof(AVAILABLE_M2M_MPP_UNITS)/sizeof(exynos_mpp_t);
        for (int i = 0; i < cnt; i++){
            if (AVAILABLE_M2M_MPP_UNITS[i].physicalType == typeId)
                return String8(AVAILABLE_M2M_MPP_UNITS[i].name);
        }
    }
    String8 result;
    result.appendFormat("? %08x", typeId);
    return result;
}

bool hasPPC(uint32_t physicalType, uint32_t formatIndex, uint32_t rotIndex) {
    if (ppc_table_map.find(PPC_IDX(physicalType, formatIndex, rotIndex)) !=
            ppc_table_map.end()) {
        return true;
    }
    return false;
}

TableBuilder& TableBuilder::add(const std::string& key, const uint64_t& value, bool toHex) {
    std::stringstream v;
    if (toHex)
        v << "0x" << std::hex << value;
    else
        v << value;
    data.emplace_back(std::make_pair(key, v.str()));
    return *this;
}

TableBuilder& TableBuilder::add(const std::string& key, const std::vector<uint64_t>& values,
                                bool toHex) {
    std::stringstream value;
    for (int i = 0; i < values.size(); i++) {
        if (i) value << ", ";

        if (toHex)
            value << "0x" << std::hex << values[i];
        else
            value << values[i];
    }

    data.emplace_back(std::make_pair(key, value.str()));
    return *this;
}

std::string TableBuilder::build() {
    std::stringstream splitter, header, content;
    splitter << "+";
    header << "|";
    content << "|";
    for (const auto& [key, value] : data) {
        int size = std::max(key.size(), value.size()) + 2 /* for spaces around the string */;
        splitter << std::string(size, '-') << "+";
        header << buildPaddedString(key, size) << "|";
        content << buildPaddedString(value, size) << "|";
    }

    std::string output = splitter.str() + "\n" +
                         header.str() + "\n" +
                         splitter.str() + "\n" +
                         content.str() + "\n" +
                         splitter.str() + "\n";
    return output;
}

std::string TableBuilder::buildPaddedString(const std::string& str, int size) {
    int totalPadding = size - str.size();
    int leftPadding = totalPadding / 2.0;
    int rightPadding = (totalPadding / 2.0) + 0.6; // Poor person's ceil

    return std::string(leftPadding, ' ') + str + std::string(rightPadding, ' ');
}

void writeFileNode(FILE* fd, int value) {
    constexpr uint32_t kMaxWriteFileLen = 16;
    char val[kMaxWriteFileLen] = {0};

    if (fd == nullptr) {
        ALOGE("invalid fd pass to %s!", __func__);
        return;
    }

    if (int32_t ret = snprintf(val, kMaxWriteFileLen, "%d", value) <= 0) {
        ALOGE("failed to write file node, ret =%d", ret);
    } else {
        fwrite(val, sizeof(val), 1, fd);
        if (ferror(fd)) {
            ALOGE("write failed: %s", strerror(errno));
            clearerr(fd);
        }
        rewind(fd);
    }
}

int32_t writeIntToFile(const char* file, uint32_t value) {
    FILE* fd = fopen(file, "w+");
    if (fd == nullptr) {
        ALOGE("%s open failed! %s", file, strerror(errno));
        return -EINVAL;
    }
    writeFileNode(fd, value);
    fclose(fd);
    return 0;
}

int32_t load_png_image(const char* filepath, buffer_handle_t buffer) {
    png_structp png_ptr;
    png_infop info_ptr;
    int width, height, bpp, color_type;

    VendorGraphicBufferMeta gmeta(buffer);

    FILE* fp = fopen(filepath, "rb");
    if (fp == NULL) {
        ALOGE("%s open failed ", filepath);
        return -ENOENT;
    }

    png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING, 0, 0, 0);
    if (png_ptr == NULL) {
        fclose(fp);
        return -ENOMEM;
    }

    info_ptr = png_create_info_struct(png_ptr);
    if (info_ptr == NULL) {
        fclose(fp);
        png_destroy_read_struct(&png_ptr, NULL, NULL);
        return -ENOMEM;
    }

    if (setjmp(png_jmpbuf(png_ptr))) {
        fclose(fp);
        png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
        return -EIO;
    }

    png_init_io(png_ptr, fp);

    png_set_sig_bytes(png_ptr, 0);
    png_read_info(png_ptr, info_ptr);

    width = png_get_image_width(png_ptr, info_ptr);
    height = png_get_image_height(png_ptr, info_ptr);
    if (width != gmeta.width || height != gmeta.height) {
        fclose(fp);
        png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
        return -EINVAL;
    }

    bpp = png_get_bit_depth(png_ptr, info_ptr) * png_get_channels(png_ptr, info_ptr);
    color_type = png_get_color_type(png_ptr, info_ptr);
    if (color_type != PNG_COLOR_TYPE_RGB_ALPHA || bpp != formatToBpp(gmeta.format)) {
        fclose(fp);
        png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
        return -EINVAL;
    }

    size_t bufferHandleSize = gmeta.stride * gmeta.vstride * formatToBpp(gmeta.format) / 8;
    size_t png_size = png_get_rowbytes(png_ptr, info_ptr) * height;
    if (bufferHandleSize > gmeta.size || (bufferHandleSize < png_size)) {
        fclose(fp);
        png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
        return -EINVAL;
    }
    void* bufferHandleData =
            mmap(0, bufferHandleSize, PROT_READ | PROT_WRITE, MAP_SHARED, gmeta.fd, 0);

    if (bufferHandleData != MAP_FAILED && bufferHandleData != NULL) {
        int strideBytes = gmeta.stride * (formatToBpp(gmeta.format) / 8);
        png_bytep row_ptr = (png_bytep)bufferHandleData;
        for (int y = 0; y < height; ++y) {
            png_read_row(png_ptr, row_ptr, NULL);
            row_ptr += strideBytes;
        }
        munmap(bufferHandleData, bufferHandleSize);
    }

    fclose(fp);
    png_destroy_read_struct(&png_ptr, &info_ptr, NULL);

    return 0;
}

int readLineFromFile(const std::string &filename, std::string &out, char delim) {
    std::ifstream in(filename);

    if (!in) {
        return -ENOENT;
    }
    std::getline(in, out, delim);
    if (!in) {
        return -EIO;
    }

    return android::OK;
}

std::optional<std::string> waitForPropertyValue(const std::string& property, int64_t timeoutMs) {
    if (!android::base::WaitForPropertyCreation(property, std::chrono::milliseconds(timeoutMs))) {
        return std::nullopt;
    }
    std::string out = android::base::GetProperty(property, "unknown");
    if (out == "unknown") {
        return std::nullopt;
    }
    return std::make_optional(out);
}

uint32_t rectSize(const hwc_rect_t& rect) {
    auto width = rect.right - rect.left;
    auto height = rect.bottom - rect.top;

    if (width <= 0 || height <= 0) return 0;

    return width * height;
}

void assign(decon_win_rect& win_rect, uint32_t left, uint32_t right, uint32_t width,
            uint32_t height) {
    win_rect.x = left;
    win_rect.y = right;
    win_rect.w = std::max(0U, width);
    win_rect.h = std::max(0U, height);
}

uint32_t nanoSec2Hz(uint64_t ns) {
    if (ns == 0) return 0;
    constexpr auto nsecsPerSec = std::chrono::nanoseconds(1s).count();
    return round(static_cast<float>(nsecsPerSec) / ns);
};
