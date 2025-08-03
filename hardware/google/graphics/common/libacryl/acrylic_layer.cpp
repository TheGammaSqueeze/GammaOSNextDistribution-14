/*
 * Copyright Samsung Electronics Co.,LTD.
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <log/log.h>

#include <hardware/exynos/acryl.h>
#include <hardware/hwcomposer2.h>

#include "acrylic_internal.h"

AcrylicCanvas::AcrylicCanvas(Acrylic *compositor, canvas_type_t type)
    : mCompositor(compositor), mPixFormat(0), mNumBuffers(0), mFence(-1), mAttributes(ATTR_NONE),
      mSettingFlags(0), mCanvasType(type)
{
    // Initialize the image size to the possible smallest size
    mImageDimension = compositor->getCapabilities().supportedMinSrcDimension();
}

AcrylicCanvas::~AcrylicCanvas()
{
    setFence(-1);
}

static const char *canvasTypeName(unsigned int type)
{
    static const char canvas_type_name[2][7] = {"source", "target"};

    return canvas_type_name[type];
}

bool AcrylicCanvas::setImageDimension(int32_t width, int32_t height)
{
    if (((getSettingFlags() & SETTING_DIMENSION) != 0) &&
            (width == mImageDimension.hori) && (height == mImageDimension.vert))
        return true;

    if (!getCompositor()) {
        ALOGE("Trying to set image dimension to an orphaned layer");
        return false;
    }

    unset(SETTING_DIMENSION);

    const HW2DCapability &cap = getCompositor()->getCapabilities();

    hw2d_coord_t minsize, maxsize;

    if (mCanvasType == CANVAS_SOURCE) {
        minsize = cap.supportedMinSrcDimension();
        maxsize = cap.supportedMaxSrcDimension();
    } else {
        minsize = cap.supportedMinDstDimension();
        maxsize = cap.supportedMaxDstDimension();
    }

    if ((width < minsize.hori) || (height < minsize.vert) || (width > maxsize.hori) || (height > maxsize.vert)) {
        ALOGE("Invalid %s image size %dx%d (limit: %dx%d ~ %dx%d )",
              canvasTypeName(mCanvasType), width, height,
              minsize.hori, minsize.vert, maxsize.hori, maxsize.vert);
        return false;
    }

    minsize = cap.supportedDimensionAlign();
    if (!!(width & (minsize.hori - 1)) || !!(height & (minsize.vert - 1))) {
        ALOGE("%s image size %dx%d violates alignment restriction %dx%d",
              canvasTypeName(mCanvasType), width, height, minsize.hori, minsize.vert);
        return false;
    }

    mImageDimension.hori = static_cast<int16_t>(width);
    mImageDimension.vert = static_cast<int16_t>(height);

    set(SETTING_DIMENSION | SETTING_DIMENSION_MODIFIED);

    ALOGD_TEST("Configured dimension: %dx%d (type: %s)", width, height, canvasTypeName(mCanvasType));

    return true;
}

bool AcrylicCanvas::setImageBuffer(int a, int r, int g, int b, uint32_t attr)
{
    if (!getCompositor()) {
        ALOGE("Trying to set buffer to an orphaned layer");
        return false;
    }

    const HW2DCapability &cap = getCompositor()->getCapabilities();

    if (((mCanvasType == CANVAS_SOURCE) && !cap.isFeatureSupported(HW2DCapability::FEATURE_SOLIDCOLOR)) ||
        (mCanvasType == CANVAS_TARGET)) {
        ALOGE("SolidColor is not supported for %s", canvasTypeName(mCanvasType));
        return false;
    }

    setFence(-1);
    mMemoryType = MT_EMPTY;
    mNumBuffers = 0;

    mAttributes = (attr & ATTR_ALL_MASK) | ATTR_SOLIDCOLOR;

    set(SETTING_BUFFER | SETTING_BUFFER_MODIFIED);

    mSolidColor  = ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | ((b & 0xFF));

    return true;
}

bool AcrylicCanvas::setImageBuffer(int fd[MAX_HW2D_PLANES], size_t len[MAX_HW2D_PLANES], off_t offset[MAX_HW2D_PLANES],
                                   int num_buffers, int fence, uint32_t attr)
{
    if ((attr & ATTR_OTF) != 0)
        return setImageOTFBuffer(attr);

    if (!getCompositor()) {
        ALOGE("Trying to set buffer to an orphaned layer");
        return false;
    }

    const HW2DCapability &cap = getCompositor()->getCapabilities();
    unsigned long alignmask = static_cast<unsigned long>(cap.supportedBaseAlign()) - 1;

    if (num_buffers > MAX_HW2D_PLANES) {
        ALOGE("Too many buffers %d are set passed to setImageBuffer(dmabuf)", num_buffers);
        return false;
    }

    for (int i = 0; i < num_buffers; i++) {
        if ((offset[i] < 0) || (static_cast<size_t>(offset[i]) >= len[i])) {
            ALOGE("Too large offset %jd for length %zu of buffer[%d]",
                  static_cast<intmax_t>(offset[i]), len[i], i);
            return false;
        }

        if ((offset[i] & alignmask) != 0) {
            ALOGE("Alignment of offset %#lx of buffer[%d] violates the alignment of %#lx",
                    offset[i], i, alignmask + 1);
            return false;
        }
    }

    for (int i = 0; i < num_buffers; i++) {
        m.mBufferFd[i] = fd[i];
        mBufferLength[i] = len[i];
        mBufferOffset[i] = offset[i];
        ALOGD_TEST("Configured buffer[%d]: fd %d, len %zu, offset %jd (type: %s)", i,
                   m.mBufferFd[i], mBufferLength[i], static_cast<intmax_t>(mBufferOffset[i]),
                   canvasTypeName(mCanvasType));
    }

    ALOGE_IF((attr & ~ATTR_ALL_MASK) != 0,
             "Configured unsupported attribute %#x to setImageBuffer(dmabuf))", attr);

    setFence(fence);
    mMemoryType = MT_DMABUF;
    mNumBuffers = num_buffers;

    mAttributes = attr & ATTR_ALL_MASK;
    ALOGD_TEST("Configured buffer: fence %d, type %d, count %d, attr %#x (type: %s)",
               mFence, mMemoryType, mNumBuffers, mAttributes, canvasTypeName(mCanvasType));

    set(SETTING_BUFFER | SETTING_BUFFER_MODIFIED);

    return true;
}

bool AcrylicCanvas::setImageBuffer(void *addr[MAX_HW2D_PLANES], size_t len[MAX_HW2D_PLANES],
                                   int num_buffers, uint32_t attr)
{
    if ((attr & ATTR_OTF) != 0)
        return setImageOTFBuffer(attr);

    if (!getCompositor()) {
        ALOGE("Trying to set buffer to an orphaned layer");
        return false;
    }

    const HW2DCapability &cap = getCompositor()->getCapabilities();
    unsigned long alignmask = static_cast<unsigned long>(cap.supportedBaseAlign()) - 1;

    if (num_buffers > MAX_HW2D_PLANES) {
        ALOGE("Too many buffers %d are set passed to setImageBuffer(userptr)", num_buffers);
        return false;
    }

    for (int i = 0; i < num_buffers; i++) {
        if ((reinterpret_cast<unsigned long>(addr[i]) & alignmask) != 0) {
            ALOGE("Alignment of address %p of buffer[%d] violates the alignment of %#lx",
                    addr[i], i, alignmask + 1);
            return false;
        }
    }

    for (int i = 0; i < num_buffers; i++) {
        m.mBufferAddr[i] = addr[i];
        mBufferLength[i] = len[i];
        mBufferOffset[i] = 0;
        ALOGD_TEST("Configured buffer[%d]: addr %p, len %zu, offset %u (type: %s)",
                   i, m.mBufferAddr[i], mBufferLength[i], mBufferOffset[i],
                   canvasTypeName(mCanvasType));
    }

    ALOGE_IF((attr & ~ATTR_ALL_MASK) != 0,
             "Configured unsupported attribute %#x to setImageBuffer(userptr))", attr);

    setFence(-1);
    mMemoryType = MT_USERPTR;
    mNumBuffers = num_buffers;

    mAttributes = attr & ATTR_ALL_MASK;

    ALOGD_TEST("Configured buffer: fence %d, type %d, count %d, attr %#x (type: %s)",
               mFence, mMemoryType, mNumBuffers, mAttributes, canvasTypeName(mCanvasType));

    set(SETTING_BUFFER | SETTING_BUFFER_MODIFIED);

    return true;
}

bool AcrylicCanvas::setImageOTFBuffer(uint32_t attr)
{
    if (!getCompositor()) {
        ALOGE("Trying to set buffer to an orphaned layer");
        return false;
    }

    const HW2DCapability &cap = getCompositor()->getCapabilities();

    if (((mCanvasType == CANVAS_SOURCE) && !cap.isFeatureSupported(HW2DCapability::FEATURE_OTF_READ)) ||
        ((mCanvasType == CANVAS_TARGET) && !cap.isFeatureSupported(HW2DCapability::FEATURE_OTF_WRITE))) {
        ALOGE("OTF is not supported for %s", canvasTypeName(mCanvasType));
        return false;
    }

    setFence(-1);
    mMemoryType = MT_EMPTY;
    mNumBuffers = 0;

    mAttributes = (attr & ATTR_ALL_MASK) | ATTR_OTF;

    set(SETTING_BUFFER | SETTING_BUFFER_MODIFIED);

    return true;
}

bool AcrylicCanvas::setImageType(uint32_t fmt, int dataspace)
{
    if (((getSettingFlags() & SETTING_TYPE) != 0) &&
            (mPixFormat == fmt) && (mDataSpace == dataspace))
        return true;

    if (!getCompositor()) {
        ALOGE("Trying to set image type to an orphaned layer");
        return false;
    }

    unset(SETTING_TYPE);

    const HW2DCapability &cap = getCompositor()->getCapabilities();

    if (!cap.isFormatSupported(fmt)) {
        ALOGE("fmt %#x is not supported.", fmt);
        return false;
    }

    if (!cap.isDataspaceSupported(dataspace)) {
        ALOGE("dataspace %d is not supported.", dataspace);
        return false;
    }

    mPixFormat = fmt;
    mDataSpace = dataspace;

    ALOGD_TEST("Configured format %#x and dataspace %#x (type: %s)",
               mPixFormat, mDataSpace, canvasTypeName(mCanvasType));

    set(SETTING_TYPE | SETTING_TYPE_MODIFIED);

    return true;
}

void AcrylicCanvas::setFence(int fence)
{
    if (mFence >= 0)
        close(mFence);

    mFence = fence;
}

AcrylicLayer::AcrylicLayer(Acrylic *compositor)
    : AcrylicCanvas(compositor), mTransitData(nullptr), mBlendingMode(HWC_BLENDING_NONE),
      mTransform(0), mZOrder(0), mMaxLuminance(100), mMinLuminance(0), mPlaneAlpha(255)
{
    // Default settings:
    // - Bleding mode: SRC_OVER
    // - Rotaion: 0 degree
    // - Flip: none
    // - z-order: 0
    // - plane alpha: 1 (255)
    // - master display: [0.0000 nit ~ 100.0000 nit] (SDR)
    // - target area: full area of the target image
    mTargetRect.pos = {0, 0};
    mTargetRect.size = {0, 0};
}

AcrylicLayer::~AcrylicLayer()
{
    if (mCompositor)
        mCompositor->removeLayer(this);
}

bool AcrylicLayer::setCompositMode(uint32_t mode, uint8_t alpha, int z_order)
{
    if (!getCompositor()) {
        ALOGE("Trying to set compositing mode to an orphaned layer");
        return false;
    }

    const HW2DCapability &cap = getCompositor()->getCapabilities();

    bool okay = true;
    switch (mode) {
        case HWC_BLENDING_NONE:
        case HWC2_BLEND_MODE_NONE:
            if (!(cap.supportedCompositingMode() & HW2DCapability::BLEND_SRC_COPY))
                okay = false;
            break;
        case HWC_BLENDING_PREMULT:
        case HWC2_BLEND_MODE_PREMULTIPLIED:
            if (!(cap.supportedCompositingMode() & HW2DCapability::BLEND_SRC_OVER))
                okay = false;
            break;
        case HWC_BLENDING_COVERAGE:
        case HWC2_BLEND_MODE_COVERAGE:
            if (!(cap.supportedCompositingMode() & HW2DCapability::BLEND_NONE))
                okay = false;
            break;
        default:
            ALOGE("Unknown bleding mode %#x", mode);
            return false;
    }

    if (!okay) {
        ALOGE("Unsupported blending mode %#x", mode);
        return false;
    }

    mBlendingMode = mode;

    mZOrder = z_order;
    mPlaneAlpha = alpha;

    ALOGD_TEST("Configured compositing mode: mode %d, z-order %d, alpha %d",
               mBlendingMode, mZOrder, mPlaneAlpha);

    return true;
}

#define ALOGE_RECT(msg, title, rect)   ALOGE(msg ": (%d, %d) -> (%d, %d)", title, (rect).left, (rect).top, (rect).right, (rect).bottom);

bool AcrylicLayer::setCompositArea(hwc_rect_t &src_area, hwc_rect_t &out_area, uint32_t transform, uint32_t attr)
{
    if (!getCompositor()) {
        ALOGE("Trying to set compositing area to an orphaned layer");
        return false;
    }

    const HW2DCapability &cap = getCompositor()->getCapabilities();
    hw2d_coord_t limit;

    // 1. Transform capability check
    if ((transform & cap.getHWCTransformMask()) != transform) {
        ALOGE("transform value %#x is not supported: supported transform mask: %#x",
              transform, cap.getHWCTransformMask());
        return false;
    }

    // 2. Source area verification
    int32_t val = src_area.left | src_area.top | src_area.right | src_area.bottom;
    if (val < 0) {
        ALOGE_RECT("Negative position in the %s area", "source", src_area);
        return false;
    }

    if ((src_area.left >= src_area.right) || (src_area.top >= src_area.bottom)) {
        ALOGE_RECT("Invalid %s position and area", "source", out_area);
        return false;
    }

    limit = cap.supportedMinSrcDimension();
    if ((get_width(src_area) < limit.hori) || (get_height(src_area) < limit.vert)) {
        ALOGE_RECT("Too small %s area", "source", src_area);
        return false;
    }

    limit = getImageDimension();
    if ((src_area.right > limit.hori) || (src_area.bottom > limit.vert)) {
        ALOGE_RECT("Too large %s area", "source", src_area);
        ALOGE("        Image full size: %dx%d", limit.hori, limit.vert);
        return false;
    }

    // 3. Target area verification
    val = out_area.left | out_area.top | out_area.right | out_area.bottom;
    if (val != 0) {
        // The following checks on the target area are deferred to commit()
        // - if area size is larger than the limit
        // - if the right/bottom position exceed the limit
        if (val < 0) {
            ALOGE_RECT("Negative position in the %s area", "target", out_area);
            return false;
        }

        if ((out_area.left >= out_area.right) || (out_area.top >= out_area.bottom)) {
            ALOGE_RECT("Invalid %s position and area", "target", out_area);
            return false;
        }

        limit = cap.supportedMinDstDimension();
        if ((get_width(out_area) < limit.hori) || (get_height(out_area) < limit.vert)) {
            ALOGE_RECT("too small %s area", "target", out_area);
            return false;
        }

        // 4. Scaling limit verification if target area is specified
        hw2d_coord_t src_xy, out_xy;

        src_xy.hori = get_width(src_area);
        src_xy.vert = get_height(src_area);
        out_xy.hori = get_width(out_area);
        out_xy.vert = get_height(out_area);

        bool scaling_ok = !(attr & ATTR_NORESAMPLING)
            ? cap.supportedResampling(src_xy, out_xy, transform)
            : cap.supportedResizing(src_xy, out_xy, transform);

        if (!scaling_ok) {
            ALOGE("Unsupported scaling from %dx%d@(%d,%d) --> %dx%d@(%d,%d) with transform %d and attr %#x",
                    get_width(src_area), get_height(src_area), src_area.left, src_area.top,
                    get_width(out_area), get_height(out_area), out_area.left, out_area.top, transform, attr);
            return false;
        }
    }

    mTargetRect.pos.hori = static_cast<int16_t>(out_area.left);
    mTargetRect.pos.vert = static_cast<int16_t>(out_area.top);
    mTargetRect.size.hori = static_cast<int16_t>(get_width(out_area));
    mTargetRect.size.vert = static_cast<int16_t>(get_height(out_area));

    mImageRect.pos.hori = static_cast<int16_t>(src_area.left);
    mImageRect.pos.vert = static_cast<int16_t>(src_area.top);
    mImageRect.size.hori = static_cast<int16_t>(get_width(src_area));
    mImageRect.size.vert = static_cast<int16_t>(get_height(src_area));

    mTransform = transform;
    mCompositAttr = attr & ATTR_ALL_MASK;

    ALOGD_TEST("Configured area: %dx%d@%dx%d -> %dx%d@%dx%d, transform: %d, attr: %#x",
                mImageRect.size.hori, mImageRect.size.vert, mImageRect.pos.hori, mImageRect.pos.vert,
                mTargetRect.size.hori, mTargetRect.size.vert, mTargetRect.pos.hori, mTargetRect.pos.vert,
                mTransform, mCompositAttr);

    return true;
}

bool AcrylicLayer::setImageDimension(int32_t width, int32_t height)
{
    if (!AcrylicCanvas::setImageDimension(width, height))
        return false;

    // NOTE: the crop area should be initialized with the new image size
    mImageRect.pos = {0, 0};
    mImageRect.size = getImageDimension();

    ALOGD_TEST("Reset the image rect to %dx%d@0x0", mImageRect.size.hori, mImageRect.size.vert);

    return true;
}

void AcrylicLayer::setMasterDisplayLuminance(uint16_t min, uint16_t max)
{
    if (max < 100) {
        ALOGE("Too small max display luminance %u.", max);
    } else {
        mMaxLuminance = max;
        mMinLuminance = min;
    }
}

void AcrylicLayer::importLayer(AcrylicLayer &other, bool inherit_transform)
{
    // Data to import
    // - image size and the image rect
    // - buffer and its attributes
    // - acquire fence (the fence of @other should be invalidated)
    // - pixel format, color space
    // - geometric transformation if @inherit_transform is true
    // Data NOT to import
    // - the target rect
    // - the blending attribute
    // - z-order and plane alpha
    hw2d_coord_t xy = other.getImageDimension();
    AcrylicCanvas::setImageDimension(xy.hori, xy.vert);
    setImageType(other.getFormat(), other.getDataspace());

    uint32_t attr = ATTR_NONE;
    if (other.isProtected())
        attr |= ATTR_PROTECTED;
    if (other.isCompressed())
        attr |= ATTR_COMPRESSED;
    if (other.isCompressedWideblk()) attr |= ATTR_COMPRESSED_WIDEBLK;

    if (other.getBufferType() == MT_DMABUF) {
        int fd[3];
        off_t off[3];
        size_t len[3];

        for (unsigned int i = 0; i < other.getBufferCount(); i++) {
            fd[i] = other.getDmabuf(i);
            off[i] = other.getOffset(i);
            len[i] = other.getBufferLength(i);
        }

        setImageBuffer(fd, len, off, other.getBufferCount(), other.getFence(), attr);
    } else {
        void *addr[3];
        size_t len[3];

        for (unsigned int i = 0; i < other.getBufferCount(); i++) {
            addr[i] = other.getUserptr(i);
            len[i] = other.getBufferLength(i);
        }

        setImageBuffer(addr, len, other.getBufferCount(), attr);
    }

    other.clearFence();
    mImageRect = other.mImageRect;
    if (inherit_transform)
        mTransform = other.mTransform;
}
