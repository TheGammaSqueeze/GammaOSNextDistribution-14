/*
 * Copyright Samsung Electronics Co.,LTD.  * Copyright (C) 2016 The Android Open Source Project
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

#ifndef __HARDWARE_EXYNOS_ACRYLIC_H__
#define __HARDWARE_EXYNOS_ACRYLIC_H__

#include <hardware/hwcomposer.h>
#include <log/log.h>
#include <system/graphics.h>
#include <unistd.h>
#include <cstdint>
#include <vector>
#include "android-base/macros.h"

/* basic primitives */

#define MAX_HW2D_PLANES 4

/*
 * Structure to express 2-dimensional coordinates.
 * Invented to pass or to return two values in a single word
 */
typedef struct hw2d_coord {
    int16_t hori;
    int16_t vert;
    void swap()
    {
        int16_t tmp = hori;
        hori = vert;
        vert = tmp;
    }
} hw2d_coord_t;

/*
 * Structure to express a rectangle in a 2-dimensional grid.
 * Invented to pass or to return four values in a single 64-bit word
 */
typedef struct hw2d_rect {
    hw2d_coord_t pos;
    hw2d_coord_t size;
} hw2d_rect_t;

/*
 * Structure to express the capability of HW 2D Accelerators
 */
struct stHW2DCapability {
    /* maximum magnification with filter applied */
    hw2d_coord_t max_upsampling_num;
    /* maximum minification factor with filter applied */
    hw2d_coord_t max_downsampling_factor;
    /* maximum maginifcation without filter applied */
    hw2d_coord_t max_upsizing_num;
    /* maximum minification factor without filter applied */
    hw2d_coord_t max_downsizing_factor;
    /* the smallest source image size the HW can process */
    hw2d_coord_t min_src_dimension;
    /* the largest source image size the HW can process */
    hw2d_coord_t max_src_dimension;
    /* the smallest output image size the HW can process */
    hw2d_coord_t min_dst_dimension;
    /* the largest output image size the HW can process */
    hw2d_coord_t max_dst_dimension;
    /*
     * the restriction of the alignments of the numbers of pixels
     * in both of horizontal and vertical direction
     */
    hw2d_coord_t min_pix_align;
    /*
     * The number of repeats of rescaling that the compositor supports. Note
     * that this is not the capability of the driver and H/W but the capability.
     * of the compositor(Acrylic).
     * If the driver and H/W supports rescaling, it is 0. If the compositor is
     * capable of running the H/W twice to overcome the minification restriction,
     * it is then 1.
     */
    int16_t rescaling_count;
    /*
     * the compositing mode that the HW 2D supports.
     * should be combination of the values of HW2DCapability::blend_ops_t.
     */
    uint32_t compositing_mode;
    /*
     * the geometric transformation in the 2-dimensional coordinate.
     * should be combination of the values of HW2DCapability::transform_t.
     */
    uint32_t transform_type;
    /*
     * the capabilities of HW. The value is the combination of the values of
     * HW2DCapability::feature_t.
     */
    uint32_t auxiliary_feature;
    /*
     * the number of color formats supported by HW 2D.
     * it is also the number of elements in pixformats array.
     */
    unsigned int num_formats;
    /*
     * the number of color spaces supported by HW 2D.
     * it is also the number of elements in dataspaces array.
     */
    unsigned int num_dataspaces;
    /*
     * the number of source images that HW 2D can process.
     */
    unsigned int max_layers;
    /*
     * the array of color formats that are supported by HW 2D. the values should
     * be selected from the definitions in <system/graphics.h> and exynos_formats.h.
     */
    uint32_t *pixformats;
    /*
     * the array of color spaces that are supported by HW 2D. the values should
     * be selected from the definitions in <system/graphics.h>.
     */
    int *dataspaces;
    /*
     * the restriction of the alignments of the base addresses of buffers of images.
     */
    size_t base_align;
};

/*
 * HW2DCapability - Description of the capability of HW 2D.
 */
class HW2DCapability {
public:
    /*
     * the values to indicate the capabilities of geometric transformations
     * supported by HW 2D accelerators.
     */
    enum transform_t {
        TRANSFORM_FLIP_H = 1,   /* horizontal flip: (symmetrical displacement by x = -x, y = y) */
        TRANSFORM_FLIP_V = 2,   /* vertical flip: (symmetrical displacement by x = x, y = -y) */
        TRANSFORM_FLIP_HV = 3,  /* horizontal flip then vertical flip */
        TRANSFORM_ROT_90 = 4,   /* clockwise rotation by 90 degree */
        TRANSFORM_ROT_180 = 8,  /* clockwise rotation by 180 degree */
        TRANSFORM_ROT_270 = 16, /* clockwise rotation by 270 degree */
        TRANSFORM_ROT_ALL = 28, /* bitmask to select rotation degrees */
        TRANSFORM_ALL = 31      /* bitmask to select the correct bits */
    };
    /*
     * the values to indicate the capabilities of layer compositing
     * supported by HW 2D accelerators.
     * Sc: Source color, Dc: Destination(background) color,
     * Sa: Source alpha, Pa: Plane alpha
     */
    enum blend_ops_t {
        BLEND_NONE = 1,     /* Sc * 1 + Dc * 0 */
        BLEND_SRC_COPY = 2, /* Sc * 1 * Pa + Dc * (1 - Sa) */
        BLEND_SRC_OVER = 4, /* Sc * Sa * Pa + Dc * (1 - Sa) */
    };

    /*
     * The values to indicate the capabilities of 2D H/W. The capabilities
     * listed here shows if a function is supported or not.
     */
    enum feature_t {
        FEATURE_PLANE_ALPHA  = 1 << 0,
        FEATURE_AFBC_ENCODE  = 1 << 4,
        FEATURE_AFBC_DECODE  = 1 << 5,
        FEATURE_UORDER_READ  = 1 << 6,
        FEATURE_UORDER_WRITE = 1 << 7,
        FEATURE_OTF_READ     = 1 << 8,
        FEATURE_OTF_WRITE    = 1 << 9,
        FEATURE_SOLIDCOLOR  = 1 << 10,
    };

    enum { RESAMPLING_FRACTION_BITS = 20 };

    HW2DCapability(const stHW2DCapability &cap): mCap(cap) { }

    /*
     * returns the bitmask of supporting flip directions
     * the returned value is the combination of the values of transform_t.
     */
    transform_t supportedFlip() const { return static_cast<transform_t>(mCap.transform_type & TRANSFORM_FLIP_HV); }
    /*
     * returns the bitmask of supporting rotation degrees
     * the returned value is the combination of the values of transform_t.
     */
    transform_t supportedRotation() const { return static_cast<transform_t>(mCap.transform_type & TRANSFORM_ROT_ALL); }
    /*
     * returns the pair of the maximum maginifcation
     * in the horizontal and vertical direction with filter applied
     */
    hw2d_coord_t supportedMaxMagnification() const { return mCap.max_upsampling_num;}
    /*
     * returns the pair of the maximum minifcation factor
     * in the horizontal and vertical direction with filter applied
     */
    hw2d_coord_t supportedMinMinification() const { return mCap.max_downsampling_factor;}
    /*
     * returns the pair of the maximum maginifcation
     * in the horizontal and vertical direction without filter applied
     */
    hw2d_coord_t supportedMaxEnlarging() const { return mCap.max_upsizing_num;}
    /*
     * returns the pair of the maximum minifcation factor
     * in the horizontal and vertical direction without filter applied
     */
    hw2d_coord_t supportedMinDecimation() const { return mCap.max_downsizing_factor;}
    /*
     * returns the bitmask of supporting compositing modes
     * The returned value is the combination of the value sof blend_ops_t.
     */
    uint32_t supportedCompositingMode() const { return mCap.compositing_mode; }
    /*
     * returns the alignment restriction of HW 2D in the base memory address.
     */
    size_t supportedBaseAlign() const { return mCap.base_align; }
    /*
     * returns the maximum number of horizontal and vertical pixels
     * of the source images that HW 2D can process
     */
    hw2d_coord_t supportedMaxSrcDimension() const { return mCap.max_src_dimension; }
    /*
     * returns the minimum number of horizontal and vertical pixels
     * of the source images that HW 2D can process
     */
    hw2d_coord_t supportedMinSrcDimension() const { return mCap.min_src_dimension; }
    /*
     * returns the maximum number of horizontal and vertical pixels
     * of the output image that HW 2D can process
     */
    hw2d_coord_t supportedMaxDstDimension() const { return mCap.max_dst_dimension; }
    /*
     * returns the minimum number of horizontal and vertical pixels
     * of the output image that HW 2D can process
     */
    hw2d_coord_t supportedMinDstDimension() const { return mCap.min_dst_dimension; }
    /*
     * returns the alignment restriction of the number of horizontal
     * and vertical pixels that HW 2D can process
     */
    hw2d_coord_t supportedDimensionAlign() const { return mCap.min_pix_align; }
    /*
     * returns the number of source images that HW 2D can composit
     */
    unsigned int maxLayerCount() const { return mCap.max_layers; }
    /*
     * study if the given format is supported by the HW 2D
     */
    bool isFormatSupported(uint32_t fmt) const {
        for (unsigned int i = 0; i < mCap.num_formats; i++)
            if (mCap.pixformats[i] == fmt)
                return true;
        return false;
    }
    /*
     * study if the given colorspace is supported by HW 2D
     */
    bool isDataspaceSupported(int dataspace) const {
        // discard transfer function information because it is required during display
        dataspace &= ~HAL_DATASPACE_TRANSFER_MASK;

        for (unsigned int i = 0; i < mCap.num_dataspaces; i++)
            if (mCap.dataspaces[i] == dataspace)
                return true;
        return false;
    }
    /*
     * convert the transformation mask in transform_type into the values
     * that Android Graphics HAL understands.
     */
    uint32_t getHWCTransformMask() const {
        uint32_t mask = 0;
        if (mCap.transform_type & TRANSFORM_FLIP_H)
            mask |= HAL_TRANSFORM_FLIP_H;
        if (mCap.transform_type & TRANSFORM_FLIP_V)
            mask |= HAL_TRANSFORM_FLIP_V;
        if (mCap.transform_type & TRANSFORM_ROT_90)
            mask |= HAL_TRANSFORM_ROT_90;
        if (mCap.transform_type & TRANSFORM_ROT_180)
            mask |= HAL_TRANSFORM_ROT_180;
        if (mCap.transform_type & TRANSFORM_ROT_270)
            mask |= HAL_TRANSFORM_ROT_270;
        return mask;
    }
    /*
     * discover if the given feature that is defined in feature_t is supported.
     */
    bool isFeatureSupported(uint32_t feature) const { return !!(mCap.auxiliary_feature & feature); }
    /*
     * study if the given horizontal resolution change from @from into @to is
     * supported by HW 2D
     */
    bool supportedHResampling(int16_t from, int16_t to, int16_t resamping_count = 1) const
    {
        return supportedResampling(from, to,
                                   mCap.max_upsampling_num.hori * resamping_count,
                                   mCap.max_downsampling_factor.hori * resamping_count);
    }
    /*
     * study if the given vertical resolution change from @from into @to is
     * supported by HW 2D
     */
    bool supportedVResampling(int16_t from, int16_t to, int16_t resamping_count = 1) const
    {
        return supportedResampling(from, to,
                                   mCap.max_upsampling_num.vert * resamping_count,
                                   mCap.max_downsampling_factor.vert * resamping_count);
    }
    /*
     * study if the given resampling from @from to @to is supported by HW 2D compositor
     * implementation.
     */
    bool supportedResampling(hw2d_coord_t from, hw2d_coord_t to, uint32_t transform) const
    {
        int count = supportedRescalingCount();

        if (!!(transform & HAL_TRANSFORM_ROT_90))
            to.swap();

        return supportedHResampling(from.hori, to.hori, count) &&
               supportedVResampling(from.vert, to.vert, count);
    }
    /*
     * study if the given resampling from @from to @to is supported by HW 2D
     */
    bool supportedHWResampling(hw2d_coord_t from, hw2d_coord_t to, uint32_t transform) const
    {
        if (!!(transform & HAL_TRANSFORM_ROT_90))
            to.swap();

        return supportedHResampling(from.hori, to.hori, 1) &&
               supportedVResampling(from.vert, to.vert, 1);
    }
    /*
     * study if the given number of horizontal pixels (@from) can be changed
     * to @to by HW 2D
     */
    bool supportedHResizing(int16_t from, int16_t to) const
    {
        return supportedResizing(from, to,
                mCap.max_upsizing_num.hori, mCap.max_downsizing_factor.hori);
    }
    /*
     * study if the given number of vertical pixels (@from) can be changed
     * to @to by HW 2D
     */
    bool supportedVResizing(int16_t from, int16_t to) const
    {
        return supportedResizing(from, to,
                mCap.max_upsizing_num.vert, mCap.max_downsizing_factor.vert);
    }
    /*
     * study if the given resizing from @from to @to is supported by HW 2D compositor
     * implementation.
     */
    bool supportedResizing(hw2d_coord_t from, hw2d_coord_t to, uint32_t transform) const
    {
        if (!!(transform & HAL_TRANSFORM_ROT_90))
            to.swap();

        return supportedHResizing(from.hori, to.hori) && supportedVResizing(from.vert, to.vert);
    }
    /*
     * study the number rescaling by the compositor for a layer
     * 1 if the compositor does not repeat the scaling. 2 if the compositor
     * repeat the scaling once.
     * Note that the return value of supportedRescalingCount() is the one
     * incremented from the struct stHW2DCapability.rescaling_count.
     */
    int supportedRescalingCount() const
    {
        return mCap.rescaling_count + 1;
    }
private:
    bool supportedResampling(int16_t from, int16_t to, int16_t upfactor, int16_t downfactor) const
    {
        if (UNLIKELY(from < 0 || to == 0 || upfactor == 0)) {
            ALOGW("%s: unsupported args: (from=%d), (to=%d), (upfactor=%d)", __func__, from, to,
                  upfactor);
            return false;
        }

        int64_t factor = static_cast<int64_t>(from);

        factor <<= RESAMPLING_FRACTION_BITS;
        factor /= to;

        if (factor > (static_cast<int64_t>(downfactor) << RESAMPLING_FRACTION_BITS))
            return false;

        // @upfactor is a reciprocal number of an upsampling factor
        if (factor < ((1 << RESAMPLING_FRACTION_BITS) / static_cast<int64_t>(upfactor)))
            return false;

        return true;
    }

    bool supportedResizing(int16_t from, int16_t to, int16_t upfactor, int16_t downfactor) const
    {
        if ((from < to) && (upfactor == 0))
            return true;

        if ((from > to) && (downfactor == 0))
            return true;

        return supportedResampling(from, to, upfactor, downfactor);
    }
    const stHW2DCapability &mCap;
};

class Acrylic;

/*
 * AcrylicCanvas - Description of an image and its buffers
 *
 * It includes the entire image area and how the image is stored in the memory,
 * where it is stored and the buffer synchronization objects called Fence.
 * AcrylicCanvas contains the following attributes:
 * - image dimension: number of horizontal and vertical pixels
 * - color format of the image
 * - colorspace of the image
 * - the identifiers of the image buffers (userptr or dmabuf)
 *
 * Creation of AcrylicCanvas by new operator is prohibited. The only way to
 * create an instance of AcrylicCavans is to call Acrylic::createLayer().
 */
class AcrylicCanvas {
    friend class Acrylic;
public:
    /*
     * The attributes of the image described by AcrylicCanvas:
     * The attributes are bit masks and therefore combination of them may be
     * also required.
     * - ATTR_NONE: indicates no special attributes are specified
     * - ATTR_PROTECTED: the buffer of the image is protected.  Reading or
     *                   writing to the buffer of the image is prohibited.
     * - ATTR_COMPRESSED: the image is or is to be stored in a compressed form.
     *                    e.g. AFBC
     * - ATTR_UORDER: the image data is written in U-order instead of raster-scan order.
     *                U-order memory access by GPU helps the BUS efficiency.
     * - ATTR_OTF: The image buffer is hard-wired. If this attribute is given, libacryl
     *             ignores the buffer configuration to the canvas.
     * - ATTR_SOLIDCOLOR : The image buffer is empty and should be filled with one RGBA value by H/W.
     */
    enum layer_attr_t {
        ATTR_NONE = 0,
        ATTR_PROTECTED = 1,
        ATTR_COMPRESSED = 2,
        ATTR_UORDER = 4,
        ATTR_OTF = 8,
        ATTR_SOLIDCOLOR = 16,
        ATTR_COMPRESSED_WIDEBLK = 32,
        ATTR_ALL_MASK = 0x3F
    };
    /*
     * Describes how the buffer of the image is identified.
     * - MT_DMABUF: the buffer is identified by an open file descriptor that is
     *              exported by dmabuf.
     * - MT_USERPTR: the buffer is identified by a memory address that is valid
     *               in the current process. Usally it is a bug if mAttributes
     *               has ATTR_PROTECTED whlie mMemoryType is MT_USERPTR.
     * - MT_EMTPY : the buffer is empty such as hare-wired buffer or colorfill layer.
     */
    enum memory_type { MT_DMABUF = 1, MT_USERPTR = 2, MT_EMPTY = 3 };
    /*
     * Indicates the configured or modified settings
     * - SETTING_TYPE: Image format and color space information is configured by users.
     * - SETTING_BUFFER: Image buffer information is configured by users.
     * - SETTING_DIMENSION: Image dimension information is configured by users.
     * - SETTING_TYPE_MODIFIED: Image format and color space information is configured
     *                          by users and it is not applied to HW yet.
     * - SETTING_BUFFER_MODIFIED: Image buffer information is configured by users and
     *                            it is not applied to HW yet.
     * - SETTING_DIMENSION_MODIFIED: Image dimension information is configured by users
     *                               and it is not applied to HW yet.
     */
    enum setting_check_t {
        SETTING_TYPE = 1,
        SETTING_BUFFER = 2,
        SETTING_DIMENSION = 4,
        SETTING_MASK = SETTING_TYPE | SETTING_BUFFER | SETTING_DIMENSION,
        SETTING_TYPE_MODIFIED = 16,
        SETTING_BUFFER_MODIFIED = 32,
        SETTING_DIMENSION_MODIFIED = 64,
        SETTIMG_MODIFIED_MASK = SETTING_TYPE_MODIFIED | SETTING_BUFFER_MODIFIED | SETTING_DIMENSION_MODIFIED,
    };

    /*
     * MEMBER FUNCTIONS FOR THE USERS
     */

    /*
     * Configure the dimension of the image
     */
    virtual bool setImageDimension(int32_t width, int32_t height);
    /*
     * Find the dimension of the image configured to AcyrilicCanvas
     */
    hw2d_coord_t getImageDimension() { return mImageDimension; }
    /*
     * Configure the image color format and color space
     * You should configure the correct colorspace if you want to change the
     * the image is YCbCr and the target image is RGB because the brightness
     * of the pixel data encoded in YCbCr is generated from various colorspaces
     * including BT.601, BT.709(sRGB) and BT.2020. If the YCbCr image is a frame
     * of a moving picture, we should likely consider the 1/16 headroom and
     * footroom. Generally BT.601, BT.709 and BT.2020 have footroom and headroom
     * while JFIF(JPEG, SRGB) does not have.
     */
    bool setImageType(uint32_t fmt, int dataspace);
    /*
     * Configure color fill layer that fill only one RGBA color without actual buffer.
     * Note that this successes if the compositor supports FEATURE_SOLIDCOLOR.
     */
    bool setImageBuffer(int a, int r, int g, int b, uint32_t attr = ATTR_NONE);
    /*
     * Configure the image buffer of dmabuf type.
     */
    bool setImageBuffer(int fd[MAX_HW2D_PLANES], size_t len[MAX_HW2D_PLANES], off_t offset[MAX_HW2D_PLANES],
                        int num_buffers, int fence = -1, uint32_t attr = ATTR_NONE);
    /*
     * Configure the image buffer of dmabuf type.
     * This does not receives a parameter 'offset' compared to the above
     * setImageBuffer(). The latter type of setImageBuffer() is provided to
     * provide convenience to the users because offsets are mostly zero.
     */
    bool setImageBuffer(int fd[MAX_HW2D_PLANES], size_t len[MAX_HW2D_PLANES], int num_buffers,
                        int fence = -1, uint32_t attr = ATTR_NONE)
    {
        off_t offset[MAX_HW2D_PLANES] = {0, 0, 0};
        return setImageBuffer(fd, len, offset, num_buffers, fence, attr);
    }
    /*
     * Configure the image buffer of userptr type.
     * Note that you cannot pass a fence if the buffer type is userptr because
     * userptr buffer is not shareable between processes and devices.
     */
    bool setImageBuffer(void *addr[MAX_HW2D_PLANES], size_t len[MAX_HW2D_PLANES],
                        int num_buffers, uint32_t attr = ATTR_NONE);
    /*
     * Configure the image buffer is hard-wired which means that the buffer is always
     * prepared in the compositor H/W.
     * Note that this successes if the compositor supports FEATURE_OTF_READ or FEATURE_OTF_WRITE.
     */
    bool setImageOTFBuffer(uint32_t attr = AcrylicCanvas::ATTR_NONE);
    /*
     * Configure the fence. setFence() overwrites the fence specified by
     * setImageBuffer().
     */
    void setFence(int fence);
    /*
     * Called by the implementations of Acyrlic to determine if all required
     * configurations are completed.
     */
    bool isSettingOkay()
    {
        return (mSettingFlags & SETTING_MASK) == (SETTING_TYPE | SETTING_BUFFER | SETTING_DIMENSION);
    }

    /*
     * MEMBER FUNCTIONS FOR THE IMPLEMENTATIONS of Acrylic
     */

    /*
     * Determine if the buffer is protected. If the buffer or the image is
     * protected, the driver of HW 2D should specially care the buffer and the
     * processing involving the buffer.
     */
    bool isProtected() { return !!(mAttributes & ATTR_PROTECTED); }
    /*
     * Determine if the image in the buffer is or should be in a compressed form.
     */
    bool isCompressed() { return !!(mAttributes & ATTR_COMPRESSED); }

    /*Check if the AFBC 32x8 format size is being used*/
    bool isCompressedWideblk() { return !!(mAttributes & ATTR_COMPRESSED_WIDEBLK); }

    /*
     * Study if the image is or should be written in U-Order for accelerated
     * graphic processing instead of raster-scan order.
     */
    bool isUOrder() { return !!(mAttributes & ATTR_UORDER); }
    /*
     * Study if the canvas buffer is hard-wired.
     */
    bool isOTF() { return !!(mAttributes & ATTR_OTF); }
    /*
     * Study if the image is filled with solid color.
     */
    bool isSolidColor() { return !!(mAttributes & ATTR_SOLIDCOLOR); }
    /*
     * Obtain the acquire fence of the buffer.
     */
    int getFence() const { return mFence; }
    /*
     * Invalidate the confgured acquire fence without destryoing(close()) it
     */
    void clearFence() { mFence = -1; }
    /*
     * Obtain the image color format
     */
    uint32_t getFormat() { return mPixFormat; }
    /*
     * Obtain the color space information
     */
    int getDataspace() { return mDataSpace; }
    /*
     * Obtain the buffer type whether the buffer is MT_USERPTR or MT_DMABUF
     */
    memory_type getBufferType() { return mMemoryType; }
    /*
     * Obtain the number of buffers.
     * The parameter 'index' of the following member functions including
     * getDmabuf(), getUserptr(), getOffset(), getBufferLength() should be
     * smaller than the return value of getBufferCount(). Otherwise, you will
     * get invalid memory area referenced.
     */
    unsigned int getBufferCount() { return mNumBuffers; }
    /*
     * Obtain the open file descriptor exported by dmabuf if the buffer type is
     * MT_DMABUF
     */
    int getDmabuf(unsigned int index)
    {
        return (mMemoryType == MT_DMABUF) ? m.mBufferFd[index] : -1;
    }
    /*
     * Obtain the buffer address if the buffer type is MT_USERPTR
     */
    void *getUserptr(unsigned int index)
    {
        return (mMemoryType == MT_USERPTR) ? m.mBufferAddr[index] : NULL;
    }
    /*
     * Obtain the offset where the image is stored in the buffer if the buffer
     * type is MT_DMABUF
     */
    uint32_t getOffset(unsigned int index)
    {
        return (mMemoryType == MT_DMABUF) ? mBufferOffset[index] : 0;
    }
    /*
     * Obtain the length of the buffer
     */
    uint32_t getBufferLength(unsigned int index)
    {
        return mBufferLength[index];
    }
    /*
     * Clear all modified states. It is called by the Acrylic implementations after
     * they executes their HWs
     */
    void clearSettingModified()
    {
        unset(SETTING_TYPE_MODIFIED |
              SETTING_BUFFER_MODIFIED |
              SETTING_DIMENSION_MODIFIED);
    }
    /*
     * Obtain the flags that indicates the configuration status
     */
    uint32_t getSettingFlags() { return mSettingFlags; }
    /*
     * Obtain the solid color combined by 8bit of A, R, G, B
     */
    uint32_t getSolidColor() { return mSolidColor; }
protected:
    enum canvas_type_t {
        CANVAS_SOURCE,
        CANVAS_TARGET,
    };

    Acrylic *mCompositor;

    AcrylicCanvas(Acrylic *compositor, canvas_type_t type = CANVAS_SOURCE);
    virtual ~AcrylicCanvas();

    Acrylic *getCompositor() { return mCompositor; }

    void unset(uint32_t flag) { mSettingFlags &= ~flag; }
    void set(uint32_t flag) { mSettingFlags |= flag; }
private:
    /*
     * called when Acrylic is being destroyed to inform AcrylicCanvas
     * that no Acrylic has a reference to it.
     */
    void disconnectLayer() { mCompositor = NULL; }

    hw2d_coord_t mImageDimension;
    uint32_t mPixFormat;
    int mDataSpace;
    memory_type mMemoryType;
    union {
        void *mBufferAddr[MAX_HW2D_PLANES];
        int mBufferFd[MAX_HW2D_PLANES];
    } m;
    size_t mBufferLength[MAX_HW2D_PLANES];
    uint32_t mBufferOffset[MAX_HW2D_PLANES];
    int mNumBuffers;
    int mFence; // NOTE: this should be reset to -1 after Acrylic::execute()
    uint32_t mAttributes;
    uint32_t mSettingFlags;
    uint32_t mSolidColor; // [32:0] ARGB order.
    canvas_type_t mCanvasType;
};

/*
 * AcrylicLayer - Description of compositing properties
 *                as well as an image and its buffers
 *
 * AcrylicLayer extends the properties of AcrylicCanvas for compositing purpose.
 * It adds the following propertes to AcrylicCanvas:
 * - The rectangle region of interest in the image. It is usually called 'crop'
 * - The rectangle region to map the crop area in the target dimension. It is
 *   usually called 'window'. If the width or the height of the window is
 *   different from crop, resampling or resizing is invloved.
 * - How the input images are composited. Currently Acrylic supports only the
 *   following opertions:
 *   .HWC_BLENDING_NONE:    The alpha channel in the image should be ignored
 *   .HWC_BLENDING_PREMULT: The color brightness values are regardted as they
 *                          are multiplied with its alpha channel value
 *   .HWC_BLENDING_COVERAGE: The alpha value of each pixel is multiplied with
 *                           the brighness of each pixel during compositing.
 * - How to transfrom the image into window: flip, rotation
 * - The order of input images to be stacked on the background
 * - The transparent level of the image. It is multiplied to every pixel
 *   brightness values when compositing. The transparent level of the entire
 *   image is also called 'plane alpha' or 'global alpha'.
 *
 * The default properties are:
 * - mImageRect: the entire image dimension.
 * - mTargetRect: the entire target dimension
 * - mBlendingMode: HWC_BLENDING_NONE
 * - mTransform: 0 (no flip, no rotation)
 * - mZOrder: 0
 * - mPlaneAlpha: 1.0(255)
 *
 * Creation of AcrylicLayer by new operator is prohibited. The only way to
 * create an instance of AcrylicLayer is to call Acrylic::createLayer().
 */
class AcrylicLayer: public AcrylicCanvas {
    friend class Acrylic;
public:
    /*
     * The various/special attributes to the source images
     * - ATTR_NORESAMPLING: Force resize the image speicifed to the layer even
     *                      though the target image size is too small to apply
     *                      the interpolation filter correctly.
     */
    enum composit_attr_t { ATTR_NORESAMPLING = 1, ATTR_ALL_MASK = 1};

    virtual ~AcrylicLayer();

    /*
     * MEMBER FUNCTIONS FOR THE USERS
     */

    /*
     * Configure the compositing mode including the compositing operations,
     * plane alpha and z-order
     */
    bool setCompositMode(uint32_t mode, uint8_t alpha = 0xFF, int z_order = 0);
    /*
     * Configure the crop, window, transformation and special compositing attributes.
     */
    bool setCompositArea(hwc_rect_t &src_area, hwc_rect_t &out_area, uint32_t transform = 0, uint32_t attr = 0);
    /*
     * Configure the crop and transformation information. The window rect is
     * is regarded as the entire target dimension.
     */
    bool setCompositArea(hwc_rect_t &src_area, uint32_t transform = 0, uint32_t attr = 0)
    {
        hwc_rect_t out_area = {0, 0, 0, 0};
        return setCompositArea(src_area, out_area, transform, attr);
    }
    /*
     * Configure the dimension of the image. This function overrides
     * AcrylicCanvas::setImageDimension(). Once this function is called, the
     * crop rect, mImageRect reset to the entire image dimension configured
     * by this function. Note that the window, mTargetRect is not reset because
     * it is not dependent upon the image dimension.
     */
    virtual bool setImageDimension(int32_t width, int32_t height);
    /*
     * Configure the source image area(crop) with the target rect and transform
     * untouched while setCompositArea() modifies source rect, target rect and
     * transform with the given arguments.
     */
    bool setImageRect(hwc_rect_t &src_area)
    {
        hwc_rect_t out_area = {
            mTargetRect.pos.hori,
            mTargetRect.pos.vert,
            mTargetRect.pos.hori + mTargetRect.size.hori,
            mTargetRect.pos.vert + mTargetRect.size.vert
        };
        return setCompositArea(src_area, out_area, mTransform);
    }
    /*
     * Configure the minimum liminance and the maximum luminance of the display
     * used in the HDR video mastering environment. It is spepcified in the
     * metatata of the HDR video contents.
     * @min: minimum luminance in 0.0001 cd/m^2 unit
     * @max: maximum luminance in 1 cd/m^2 unit
     *
     * The followings are the default values:
     * @min: 0
     * @max: 100 (SDR)
     *
     * NOTE:
     * The configured video is treated as SDR until setMasterDisplayLuminance()
     * is called. It means that the target image is to be SDR.
     */
    void setMasterDisplayLuminance(uint16_t min, uint16_t max);

    /*
     * Configure an opaque data associated to a layer
     * @data: pointer to data associated to this layer
     * @data_len: number of effective bytes pointed by @data
     *
     * The configured data is opaque to libacryl. It should be handled by the
     * implementations. How to specify and how to understand the data is just
     * contract between the user of a specific implementation and the implemtation.
     * The configured data is effective until the data is cleared by invoking
     * clearLayerData().
     * The second argument @data_lan can be used for checking the contract. The
     * implementation and the users should decide the data structure delivered by
     * setLayerData(). If the data structure has fixed length, the implementation
     * can determine if the delivered data is correctly configured with comparing
     * @data_len and the expected size.
     */
    void setLayerData(void *data, size_t data_len) {
        mLayerData = data;
        mLayerDataLen = data_len;
    }

    void setLayerHDR(bool hdr_en) { mLayerHDR = hdr_en; }

    /*
     * Clears the configured layer data.
     */
    void clearLayerData() {
        mLayerData = nullptr;
        mLayerDataLen = 0;
    }

    /*
     * MEMBER FUNCTIONS FOR THE IMPLEMENTATIONS of Acrylic
     */
    /*
     * Optain the configured compositing mode
     */
    uint32_t getCompositingMode() { return mBlendingMode; }
    /*
     * Obtain the z-order of the layer
     */
    int32_t getZOrder() { return mZOrder; }
    /*
     * Obtain the plane alpha value of the layer
     */
    uint8_t getPlaneAlpha() { return mPlaneAlpha; }
    /*
     * Obtain the crop region of the layer
     */
    hw2d_rect_t getImageRect() { return mImageRect; }
    /*
     * Obtain the window region onto the background. If all four values are
     * zero, the window region should be regarded as the entire background
     * dimension.
     */
    hw2d_rect_t getTargetRect() { return mTargetRect; }
    /*
     * Obtain the transform property of the layer
     */
    uint32_t getTransform() { return mTransform; }
    /*
     * Obtain the special compositing attribute flags
     */
    uint32_t getCompositAttr() { return mCompositAttr; }
    /*
     * Obtain the minimum luminance of the display of the mastering environment
     * of the video layer in the unit of 0.0001 cd/m^2.
     */
    uint16_t getMinMasteringLuminance() { return mMinLuminance; }
    /*
     * Obtain the maximum luminance of the display of the mastering environment
     * of the video layer in the unit of 1 cd/m^2.
     */
    uint16_t getMaxMasteringLuminance() { return mMaxLuminance; }
    /*
     * Copy the contents of @layer into the callee instance.
     * Note that the file descriptors of @layer are not duplicated. The caller
     * should care about that. Note also that importLayer() invalidates the
     * acquire fence of @layer because it is transferred to the callee.
     */
    void importLayer(AcrylicLayer &layer, bool inherit_transform);
    /*
     * Store the given transit data to the instance. It is generated by the
     * implementations of Acrylic class.
     */
    void storeTransit(void *transitData) { mTransitData = transitData; }
    /*
     * Retrieve the transit data from the instance.
     */
    void *getTransit() { return mTransitData; }
    /*
     * Retrieve the layer opaque data.
     */
    void *getLayerData() { return mLayerData; }
    size_t getLayerDataLength() { return mLayerDataLen; }

    bool getLayerHDR() { return mLayerHDR; }

private:
    AcrylicLayer(Acrylic *compositor);

    void *mTransitData;
    void *mLayerData;
    size_t mLayerDataLen;
    hw2d_rect_t mImageRect;
    hw2d_rect_t mTargetRect;
    uint32_t mBlendingMode;
    uint32_t mTransform;
    int32_t mZOrder;
    uint32_t mCompositAttr;
    uint16_t mMaxLuminance; // in nit
    uint16_t mMinLuminance; // in 0.0001 nit
    uint8_t mPlaneAlpha;
    bool mLayerHDR;
};

class AcrylicPerformanceRequest;

/*
 * DEPRECATED:
 * AcrylicFactory works as it did for now but it will be removed in the future
 * because new factory methods to create an instance of Acrylic is introduced:
 * use the followings instead of AcrylicFactory:
 * - Acrylic::createInstance() for a specific Acrylic backend
 * - Acrylic::createCompositor() to create the "default_compositor"
 * - Acrylic::createScaler() to create the "default_scaler"
 *
 * AcrylicFactory - The creator of Acrylic
 *
 * Users are prohibited to create an instance of Acrylic because the does not
 * have enough knowledge how to create. Instead, users should ask the factory
 * to create an instance of Acrylic with a special 'name'(spec) to identify a
 * specific HW 2D.
 */
class AcrylicFactory {
public:
    AcrylicFactory() { };
    ~AcrylicFactory() { };
    /*
     * Creator of Acrylic instance
     */
    static Acrylic *createAcrylic(const char *spec);
};

/*
 * Acrylic - The type of the object for 2D compositing with HW 2D
 *
 * Acrylic executes HW 2D with the image information specified on AcrylicLayers
 * and AcrylicCanvas that are created by Acrylic.
 * To create an instance of Acrylic, you should use AcrylicFactory.
 */
class Acrylic {
public:
    /*
     * Factory methods of an instance of Acrylic subclasses
     * createInstance() - create the instance exactly specified by @spec
     * createCompositor() - create an instance of HW 2D compositor defined in board definition
     * createScaler() - create an instance of image post processor defined in board definition
     * createBlter() - create an instance of H/W accelerator of bit block transfer defined in board definition
     */
    static Acrylic *createInstance(const char *spec);
    static Acrylic *createCompositor();
    static Acrylic *createScaler();
    static Acrylic *createBlter();

    Acrylic(const HW2DCapability &capability);
    virtual ~Acrylic();
    /*
     * Create a new instance of AcrylicLayer. If the number of created
     * AcrylicLayer is already mCapability.maxLayerCount(), createLayer()
     * fails.
     * The caller of createLayer() should destroy the instance of AcrylicLayer
     * by itself. The instance of AcrylicLayer is only available while the
     * Acrylic that created it lives. If the Acrylic that created AcrylicLayer
     * has been destroyed, all configuration to AcrylicLayer have no effect.
     */
    AcrylicLayer *createLayer();
    /*
     * Obtain HW2DCapability object to study the capability fo HW 2D that the
     * Acrylic handles.
     */
    const HW2DCapability &getCapabilities() { return mCapability; }
    /*
     * Configure the image dimension of the background. The background image
     * is not the input image with the lowest z-order but the target image in
     * this context.
     */
    bool setCanvasDimension(int32_t width, int32_t height)
    {
        return mCanvas.setImageDimension(width, height);
    }
    /*
     * Configure the image color format and the address space of the target image
     */
    bool setCanvasImageType(uint32_t fmt, int dataspace)
    {
        return mCanvas.setImageType(fmt, dataspace);
    }
    /*
     * Configure the buffers where to write the result (target) image. The
     * identifier of the buffer is a open file descriptor exported by dmabuf.
     * Users can also configure if the result image is in the compressed form
     * or to be protected from access by non-previliged users.
     */
    bool setCanvasBuffer(int fd[MAX_HW2D_PLANES], size_t len[MAX_HW2D_PLANES], off_t offset[MAX_HW2D_PLANES],
                         int num_buffers, int fence = -1, uint32_t attr = AcrylicCanvas::ATTR_NONE)
    {
        return mCanvas.setImageBuffer(fd, len, offset, num_buffers, fence, attr);
    }
    /*
     * Configure the buffers where to write the result (target) image.
     * This version ov setCanvasBuffer() is the same as the above version except
     * that this version does not require @offset as its argument.
     */
    bool setCanvasBuffer(int fd[MAX_HW2D_PLANES], size_t len[MAX_HW2D_PLANES], int num_buffers,
                         int fence = -1, uint32_t attr = AcrylicCanvas::ATTR_NONE)
    {
        return mCanvas.setImageBuffer(fd, len, num_buffers, fence, attr);
    }
    /*
     * Configure the buffers where to write the result (target) image. The
     * identifier of the buffer is a valid memory address in the address space
     * of the caller. AcrylicCanvas::ATTR_PROTECTED to @attr is not allowed
     * because userptr buffer should not be protected.
     */
    bool setCanvasBuffer(void *addr[MAX_HW2D_PLANES], size_t len[MAX_HW2D_PLANES],
                         int num_buffers, uint32_t attr = AcrylicCanvas::ATTR_NONE)
    {
        return mCanvas.setImageBuffer(addr, len, num_buffers, attr);
    }
    /*
     * Configure the target buffers as hard-wired.
     */
    bool setCanvasOTF(uint32_t attr = AcrylicCanvas::ATTR_NONE)
    {
        return mCanvas.setImageOTFBuffer(attr);
    }
    /*
     * Configure the default background color and the opacity. It is effecitve
     * when the source images are not opaque or the source images does not fill
     * the entire target image buffer.
     * Configuring the default color makes the result image more deterministic.
     * NOTE that the value(brightness) of each color components and the opacity
     * are 16-bit wide. If your system supports 8-bit brightness, you should
     * shift the brightness value to the left in the amount of 8 bits. Again,
     * you should multiply the value of each color components and the opacity
     * with 256 (2^8) if your color system supports 8-bit brightness levels.
     */
    void setDefaultColor(uint16_t red, uint16_t green, uint16_t blue, uint16_t alpha)
    {
        mBackgroundColor.R = red;
        mBackgroundColor.G = green;
        mBackgroundColor.B = blue;
        mBackgroundColor.A = alpha;
        mHasBackgroundColor = true;
    }
    /*
     * Cancel the configured default color values.
     */
    void clearDefaultColor()
    {
        mHasBackgroundColor = false;
    }
    /*
     * Configures cofficients the tone mapper if the user of Acrylic wants to
     * overrides the default coefficients of the tone mapper for HDR display.
     * The coefficients are a two dimensional array of coefficients of x-axis
     * and y-axis. If no coefficient is configured, the default function of the
     * implemntation of Acrylic is used. It may be ether of gamma 2.2 for SDR,
     * gamma 2.6 for HDR.
     * The configured coefficients are not enabled if no layer has HDR video
     * because HDR processing is not enabled.
     */
    virtual bool setHDRToneMapCoefficients(uint32_t *matrix[2], int num_elements);
    /*
     * Configure the minimum luminance and the maximum luminance of the target
     * display.
     * @min: minimum luminance in 0.0001 cd/m^2 unit
     * @max: maximum luminance in 1 cd/m^2 unit
     *
     * The following sare the default values:
     * @min: 0
     * @max 100 (SDR)
     *
     * NOTE:
     * How to handle the target display luminance depends on the implementation
     * of Acrylic. An implementation does not refer to the configured target
     * display luminance while another implementation may us the taret display
     * luminance to choose its HDR conversion logic.
     */
    inline void setTargetDisplayLuminance(uint16_t min, uint16_t max)
    {
        mMaxTargetLuminance = max;
        mMinTargetLuminance = min;
    }
    /*
     * Configure information of target display device.
     */
    inline void setTargetDisplayInfo(void *data)
    {
        mTargetDisplayInfo = data;
    }
    /*
     * Run HW 2D. If @fence is not NULL and num_fences is not zero, execute()
     * fills the release fences to the array of @fence. The number of fences
     * filled by execute() is min(num_fences, mLayers.size()). If num_fences is
     * larger than mLayers.size(), execute() fills -1 to the rest of the elements
     * of @fence.
     * execute() returns before HW 2D completes the processing, of course.
     */
    virtual bool execute(int fence[], unsigned int num_fences) = 0;
    /*
     * Run HW 2D. This version of execute() does not provides release fences.
     * If @handle is NULL, execute() does not return until HW 2D completes
     * the processng. If @handle is not NULL, execute() returns before HW 2D
     * completes and stores a value(handle) to @handle. Users can wait for HW 2D
     * to be finished with that handle. Users does not need to wait HW 2D. Then,
     * they sshould release the handle with releaseHandle().
     */
    virtual bool execute(int *handle = NULL) = 0;
    /*
     * Release @handle informed by execute()
     */
    virtual void releaseHandle(int __attribute__((__unused__)) handle) { }
    /*
     * Wait HW 2D to finish the processing associated with @handle. The handle
     * is released after the wait completes.
     */
    virtual bool waitExecution(int handle) = 0;
    /*
     * Return the last execution time of the H/W in micro seconds.
     * It is only vaild when the last call to execute() succeeded.
     */
    virtual unsigned int getLaptimeUSec() { return 0; }
    /*
     * Configure the priority of the image processing tasks requested
     * to this compositor object. The default priority is -1 and the
     * highest priority is 15. If a user configure a priority outside
     * of the range between -1 and 15, the user will get failure.
     * How the priority to be applied is the implmentation specific.
     * prioritize() returns a negative value (-1) on failure. On successful,
     * prioritize() returns zero or a positive value. An implementation may
     * return a positive value to inform additional information.
     */
    virtual int prioritize(int priority = -1);
    /*
     * Configure a value for guaranteeing quality of service in terms
     * of image processing performance. The types and ranges of the
     * value is not defined by libacryl but the end users including
     * HWC and the driver. libacryl just deliver the value to the
     * driver that the implementation of Acrylic is talking with.
     * If the implementation does not implement requestPerformanceQoS(),
     * calling requestPerformanceQoS() effects nothing.
     * The types, the size and the number of the parameters are defined
     * as required. They should be defined in acrylic_soc.h.
     */
    virtual bool requestPerformanceQoS(AcrylicPerformanceRequest *request);
    /*
     * Called when an AcrylicLayer is being destroyed
     */
    void removeLayer(AcrylicLayer *layer);
    /*
     * Obtains the instance AcrylicCanvas of the taret image. It is called by
     * the implementations of Acrylic and the test modules of Acrylic. The other
     * users of Acrylic do not need to call getCanvas(). Instead, consider
     * setCanvasDimension(), setCanvasImageType() and setCanvasBuffer().
     */
    AcrylicCanvas &getCanvas() { return mCanvas; }
    unsigned int layerCount() { return static_cast<unsigned int>(mLayers.size()); }
protected:
    /*
     * Called when an AcrylicLayer is destroyed. Unlike removeLayer(),
     * removeTransitData() is called whenever an AcrylicLayer which is still
     * associated with an instance of Acrylic is destroyed. It means that
     * removeTransitData() is called even when an instance of Acrylic is
     * destroyed to remove all transit data installed in an AcrylicLayer instance.
     * If an implmentation of Acrylic may install a transit data to an instance of
     * AcrylicLayer, it should implement removeTransitData().
     */
    virtual void removeTransitData(AcrylicLayer __attribute__((__unused__)) *layer) { }
    bool validateAllLayers();
    void sortLayers();
    AcrylicLayer *getLayer(unsigned int index)
    {
        return (index < mLayers.size()) ? mLayers[index] : nullptr;
    }
    void getBackgroundColor(uint16_t *red, uint16_t *green, uint16_t *blue,
                         uint16_t *alpha)
    {
        *red   = mBackgroundColor.R;
        *green = mBackgroundColor.G;
        *blue  = mBackgroundColor.B;
        *alpha = mBackgroundColor.A;
    }
    bool hasBackgroundColor() { return mHasBackgroundColor; }
    uint16_t getMaxTargetDisplayLuminance() { return mMaxTargetLuminance; }
    uint16_t getMinTargetDisplayLuminance() { return mMinTargetLuminance; }
    void *getTargetDisplayInfo() { return mTargetDisplayInfo; }
private:
    std::vector<AcrylicLayer *> mLayers;
    const HW2DCapability &mCapability;
    struct {
        uint16_t R;
        uint16_t G;
        uint16_t B;
        uint16_t A;
    } mBackgroundColor;
    bool mHasBackgroundColor;
    uint16_t mMaxTargetLuminance;
    uint16_t mMinTargetLuminance;
    void *mTargetDisplayInfo;
    AcrylicCanvas mCanvas;
};

struct AcrylicPerformanceRequestLayer {
    hw2d_coord_t    mSourceDimension;
    uint32_t        mPixFormat;
    hw2d_rect_t     mSourceRect;
    hw2d_rect_t     mTargetRect;
    uint32_t        mTransform;
    uint32_t        mAttribute;
};

struct AcrylicPerformanceRequestFrame {
    int             mNumLayers;
    int             mNumAllocLayers;
    int             mFrameRate;
    uint32_t        mTargetPixFormat;
    hw2d_coord_t    mTargetDimension;
    bool            mHasBackgroundLayer;
    struct AcrylicPerformanceRequestLayer *mLayers;

    AcrylicPerformanceRequestFrame();
    ~AcrylicPerformanceRequestFrame();

    bool reset(int num_layers = 0);

    void setSourceDimension(int layer, int width, int height, uint32_t fmt) {
        if (layer < mNumLayers) {
            mLayers[layer].mSourceDimension.hori = width;
            mLayers[layer].mSourceDimension.vert = height;
            mLayers[layer].mPixFormat = fmt;
        }
    }

    void setAttribute(int layer, uint32_t attribute) {
        if (layer < mNumLayers)
            mLayers[layer].mAttribute = attribute;
    }

    void setTransfer(int layer, hwc_rect_t &src_area, hwc_rect_t &out_area, uint32_t transform) {
        if (layer < mNumLayers) {
            mLayers[layer].mSourceRect.pos.hori = src_area.left;
            mLayers[layer].mSourceRect.pos.vert = src_area.top;
            mLayers[layer].mSourceRect.size.hori = src_area.right - src_area.left;
            mLayers[layer].mSourceRect.size.vert = src_area.bottom - src_area.top;
            mLayers[layer].mTargetRect.pos.hori = out_area.left;
            mLayers[layer].mTargetRect.pos.vert = out_area.top;
            mLayers[layer].mTargetRect.size.hori = out_area.right - out_area.left;
            mLayers[layer].mTargetRect.size.vert = out_area.bottom - out_area.top;
            mLayers[layer].mTransform = transform;
        }
    }

    void setTargetDimension(int width, int height, uint32_t fmt, bool bBackground) {
        mTargetDimension.hori = width;
        mTargetDimension.vert = height;
        mTargetPixFormat = fmt;
        mHasBackgroundLayer = bBackground;
    }

    void setFrameRate(int rate) { mFrameRate = rate; }

    int getLayerCount() { return mNumLayers; }
};

class AcrylicPerformanceRequest {
public:
    AcrylicPerformanceRequest();
    ~AcrylicPerformanceRequest();

    bool reset(int num_frames = 0);

    int getFrameCount() { return mNumFrames; }
    AcrylicPerformanceRequestFrame *getFrame(int idx) { return (idx < mNumFrames) ? &mFrames[idx] : NULL; }

private:
    int mNumFrames;
    int mNumAllocFrames;
    AcrylicPerformanceRequestFrame *mFrames;
};

#endif /*__HARDWARE_EXYNOS_ACRYLIC_H__*/
