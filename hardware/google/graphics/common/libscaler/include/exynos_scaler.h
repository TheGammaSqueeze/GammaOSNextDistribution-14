/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright@ Samsung Electronics Co. LTD
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

/*!
 * \file      exynos_scaler.c
 * \brief     header file for Scaler HAL
 * \author    Sunyoung Kang (sy0816.kang@samsung.com)
 * \date      2013/02/01
 *
 * <b>Revision History: </b>
 * - 2013.02.01 : Sunyoung Kang (sy0816.kang@samsung.com) \n
 *   Create
 *
 * - 2013.04.26 : Cho KyongHo (pullip.cho@samsung.com \n
 *   Library rewrite
 *
 */

#ifndef _EXYNOS_SCALER_H_
#define _EXYNOS_SCALER_H_


#include <linux/videodev2.h>
#include <stdbool.h>

#include "exynos_format.h"

#define SC_DEV_NODE     "/dev/video"
#define SC_NODE(x)      (50 + x)

#define SC_NUM_OF_PLANES    (3)

#define V4L2_PIX_FMT_NV12_RGB32         v4l2_fourcc('N', 'V', '1', 'R')
#define V4L2_PIX_FMT_NV12N_RGB32        v4l2_fourcc('N', 'N', '1', 'R')
#define V4L2_PIX_FMT_NV12M_RGB32        v4l2_fourcc('N', 'V', 'R', 'G')
#define V4L2_PIX_FMT_NV12M_BGR32        v4l2_fourcc('N', 'V', 'B', 'G')
#define V4L2_PIX_FMT_NV12M_RGB565       v4l2_fourcc('N', 'V', 'R', '6')
#define V4L2_PIX_FMT_NV12M_RGB444       v4l2_fourcc('N', 'V', 'R', '4')
#define V4L2_PIX_FMT_NV12M_RGB555X      v4l2_fourcc('N', 'V', 'R', '5')
#define V4L2_PIX_FMT_NV12MT_16X16_RGB32 v4l2_fourcc('V', 'M', 'R', 'G')
#define V4L2_PIX_FMT_NV21M_RGB32        v4l2_fourcc('V', 'N', 'R', 'G')
#define V4L2_PIX_FMT_NV21M_BGR32        v4l2_fourcc('V', 'N', 'B', 'G')
#define V4L2_PIX_FMT_NV21_RGB32         v4l2_fourcc('V', 'N', '1', 'R')
#define V4L2_PIX_FMT_YVU420_RGB32       v4l2_fourcc('Y', 'V', 'R', 'G')
#define V4L2_PIX_FMT_NV12M_P010         v4l2_fourcc('P', 'M', '1', '2')

// libgscaler's internal use only
typedef enum _HW_SCAL_ID {
    HW_SCAL0 = 4,
    HW_SCAL1,
    HW_SCAL2,
    HW_SCAL_MAX,
} HW_SCAL_ID;

// argument of non-blocking api
typedef struct {
    uint32_t x;
    uint32_t y;
    uint32_t w;
    uint32_t h;
    uint32_t fw;
    uint32_t fh;
    uint32_t format;
    unsigned long yaddr;
    unsigned long uaddr;
    unsigned long vaddr;
    uint32_t rot;
    uint32_t cacheable;
    uint32_t drmMode;
    uint32_t narrowRgb;
    int      acquireFenceFd;
    int      releaseFenceFd;
    int      mem_type;
    uint32_t pre_multi;
} exynos_sc_img;

enum colorspace {
    COLORSPACE_SMPTE170M,
    COLORSPACE_SMPTE240M,
    COLORSPACE_REC709,
    COLORSPACE_BT878,
    COLORSPACE_470_SYSTEM_M,
    COLORSPACE_470_SYSTEM_BG,
    COLORSPACE_JPEG,
    COLORSPACE_SRGB,
};

struct CSC_Spec{
	uint32_t enable;    // set 'true' for user-defined
	enum colorspace space;
	uint32_t wide;
};

enum SRC_BL_OP {
	/* [0, 0] */
	SRC_BL_OP_CLR = 1,
	/* [Sa, Sc] */
	SRC_BL_OP_SRC,
	/* [Da, Dc] */
	SRC_BL_OP_DST,
	/* [Sa + (1 - Sa)*Da, Rc = Sc + (1 - Sa)*Dc] */
	SRC_BL_OP_SRC_OVER,
	/* [Sa + (1 - Sa)*Da, Rc = Dc + (1 - Da)*Sc] */
	SRC_BL_OP_DST_OVER,
	/* [Sa * Da, Sc * Da] */
	SRC_BL_OP_SRC_IN,
	/* [Sa * Da, Sa * Dc] */
	SRC_BL_OP_DST_IN,
	/* [Sa * (1 - Da), Sc * (1 - Da)] */
	SRC_BL_OP_SRC_OUT,
	/* [Da * (1 - Sa), Dc * (1 - Sa)] */
	SRC_BL_OP_DST_OUT,
	/* [Da, Sc * Da + (1 - Sa) * Dc] */
	SRC_BL_OP_SRC_ATOP,
	/* [Sa, Sc * (1 - Da) + Sa * Dc ] */
	SRC_BL_OP_DST_ATOP,
	/* [-(Sa * Da), Sc * (1 - Da) + (1 - Sa) * Dc] */
	SRC_BL_OP_XOR,
	/* [Sa + Da - Sa*Da, Sc*(1 - Da) + Dc*(1 - Sa) + min(Sc, Dc)] */
	SRC_BL_OP_DARKEN,
	/* [Sa + Da - Sa*Da, Sc*(1 - Da) + Dc*(1 - Sa) + max(Sc, Dc)] */
	SRC_BL_OP_LIGHTEN,
	/** [Sa * Da, Sc * Dc] */
	SRC_BL_OP_MULTIPLY,
	/* [Sa + Da - Sa * Da, Sc + Dc - Sc * Dc] */
	SRC_BL_OP_SCREEN,
	/* Saturate(S + D) */
	SRC_BL_OP_ADD
};

struct SrcGlobalAlpha {
	uint32_t enable;
	unsigned int val;
};

struct SrcBlendInfo {
	enum SRC_BL_OP blop;
	unsigned int srcblendfmt;
	unsigned int srcblendhpos;
	unsigned int srcblendvpos;
	unsigned int srcblendpremulti;
	unsigned int srcblendstride;
	unsigned int srcblendwidth;
	unsigned int srcblendheight;
	struct SrcGlobalAlpha globalalpha;
	struct CSC_Spec cscspec;
};

#ifdef __cplusplus
extern "C" {
#endif

/*!
 * Create libscaler handle
 *
 * \ingroup exynos_scaler
 *
 * \param dev_num
 *  scaler dev_num[in]
 *
 * \return
 * libscaler handle
 */
void *exynos_sc_create(int dev_num);

/*!
 * Destroy libscaler handle
 *
 * \ingroup exynos_scaler
 *
 * \param handle
 *   libscaler handle[in]
 */
int exynos_sc_destroy(void *handle);

/*!
 * Convert color space with presetup color format
 *
 * \ingroup exynos_scaler
 *
 * \param handle
 *   libscaler handle[in]
 *
 * \return
 *   error code
 */
int exynos_sc_convert(void *handle);

/*!
 * Convert color space with presetup color format
 *
 * \ingroup exynos_scaler
 *
 * \param handle
 *   libscaler handle
 *
 * \param csc_range
 *   csc narrow/wide property
 *
 * \param v4l2_colorspace
 *   csc equation property
 *
 * \param filter
 *   denoise filter info
 *
 * \return
 *   error code
 */
int exynos_sc_set_csc_property(
    void        *handle,
    unsigned int csc_range,
    unsigned int v4l2_colorspace,
    unsigned int filter);

/*!
 * Set source format.
 *
 * \ingroup exynos_scaler
 *
 * \param handle
 *   libscaler handle[in]
 *
 * \param width
 *   image width[in]
 *
 * \param height
 *   image height[in]
 *
 * \param crop_left
 *   image left crop size[in]
 *
 * \param crop_top
 *   image top crop size[in]
 *
 * \param crop_width
 *   cropped image width[in]
 *
 * \param crop_height
 *   cropped image height[in]
 *
 * \param v4l2_colorformat
 *   color format[in]
 *
 * \param cacheable
 *   ccacheable[in]
 *
 * \param mode_drm
 *   mode_drm[in]
 *
 * \param premultiplied
 *   pre-multiplied format[in]
 *
 * \return
 *   error code
 */
int exynos_sc_set_src_format(
    void        *handle,
    unsigned int width,
    unsigned int height,
    unsigned int crop_left,
    unsigned int crop_top,
    unsigned int crop_width,
    unsigned int crop_height,
    unsigned int v4l2_colorformat,
    unsigned int cacheable,
    unsigned int mode_drm,
    unsigned int premultiplied);

/*!
 * Set destination format.
 *
 * \ingroup exynos_scaler
 *
 * \param handle
 *   libscaler handle[in]
 *
 * \param width
 *   image width[in]
 *
 * \param height
 *   image height[in]
 *
 * \param crop_left
 *   image left crop size[in]
 *
 * \param crop_top
 *   image top crop size[in]
 *
 * \param crop_width
 *   cropped image width[in]
 *
 * \param crop_height
 *   cropped image height[in]
 *
 * \param v4l2_colorformat
 *   color format[in]
 *
 * \param cacheable
 *   ccacheable[in]
 *
 * \param mode_drm
 *   mode_drm[in]
 *
 * \param premultiplied
 *   pre-multiplied format[in]
 *
 * \return
 *   error code
 */
int exynos_sc_set_dst_format(
    void        *handle,
    unsigned int width,
    unsigned int height,
    unsigned int crop_left,
    unsigned int crop_top,
    unsigned int crop_width,
    unsigned int crop_height,
    unsigned int v4l2_colorformat,
    unsigned int cacheable,
    unsigned int mode_drm,
    unsigned int premultiplied);

/*!
 * Set source buffer
 *
 * \ingroup exynos_scaler
 *
 * \param handle
 *   libscaler handle[in]
 *
 * \param addr
 *   buffer pointer array[in]
 *
 * \param mem_type
 *   memory type[in]
 *
 * \param acquireFenceFd
 *   acquire fence fd for the buffer or -1[in]
 *
 * \return
 *   error code
 */

int exynos_sc_set_src_addr(
    void *handle,
    void *addr[SC_NUM_OF_PLANES],
    int mem_type,
    int acquireFenceFd);

/*!
 * Set destination buffer
 *
 * \param handle
 *   libscaler handle[in]
 *
 * \param addr
 *   buffer pointer array[in]
 *
 * \param mem_type
 *   memory type[in]
 *
 * \param acquireFenceFd
 *   acquire fence fd for the buffer or -1[in]
 *
 * \return
 *   error code
 */
int exynos_sc_set_dst_addr(
    void *handle,
    void *addr[SC_NUM_OF_PLANES],
    int mem_type,
    int acquireFenceFd);

/*!
 * Set rotation.
 *
 * \ingroup exynos_scaler
 *
 * \param handle
 *   libscaler handle[in]
 *
 * \param rot
 *   image rotation. It should be multiple of 90[in]
 *
 * \param flip_h
 *   image flip_horizontal[in]
 *
 * \param flip_v
 *   image flip_vertical[in]
 *
 * \return
 *   error code
 */
int exynos_sc_set_rotation(
    void *handle,
    int rot,
    int flip_h,
    int flip_v);

/*!
 * Set framerate (optional).
 *
 * \ingroup exynos_scaler
 *
 * \param handle
 *   libscaler handle[in]
 *
 * \param framerate
 *   frame rate[in]
 */
void exynos_sc_set_framerate(
        void *handle,
        int framerate);
////// non-blocking /////

void *exynos_sc_create_exclusive(
    int dev_num,
    int allow_drm);

int exynos_sc_csc_exclusive(void *handle,
    unsigned int range_full,
    unsigned int v4l2_colorspace);

int exynos_sc_config_exclusive(
    void *handle,
    exynos_sc_img *src_img,
    exynos_sc_img *dst_img);

int exynos_sc_run_exclusive(
    void *handle,
    exynos_sc_img *src_img,
    exynos_sc_img *dst_img);

void *exynos_sc_create_blend_exclusive(
        int dev_num,
        int allow_drm);

int exynos_sc_config_blend_exclusive(
    void *handle,
    exynos_sc_img *src_img,
    exynos_sc_img *dst_img,
    struct SrcBlendInfo  *srcblendinfo);

int exynos_sc_wait_frame_done_exclusive
(void *handle);

int exynos_sc_stop_exclusive
(void *handle);

int exynos_sc_free_and_close
(void *handle);


/******************************************************************************
 ******** API for Copy Pixels between RGB data ********************************
 ******************************************************************************/

/*!
 * Description of an image for both of the source and the destination.
 *
 * \ingroup exynos_scaler
 */
struct exynos_sc_pxinfo_img
{
    void *addr;
    unsigned int width;
    unsigned int height;
    unsigned int crop_left;
    unsigned int crop_top;
    unsigned int crop_width;
    unsigned int crop_height;
    unsigned int pxfmt;  // enum EXYNOS_SC_FMT_PXINFO
};

/*!
 * Description of a pixel copy
 *
 * \ingroup exynos_scaler
 */
struct exynos_sc_pxinfo {
    struct exynos_sc_pxinfo_img src;
    struct exynos_sc_pxinfo_img dst;
    unsigned short rotate; // 0 ~ 360
    char hflip;  // non-zero value for hflip
    char vflip;  // non-zero value for vflip
};

/*!
 * Pixel format definition for pixel copy
 *
 * \ingroup exynos_scaler
 */
enum SC_FMT_PXINFO {
    EXYNOS_SC_FMT_RGB32 = 0x10,
    EXYNOS_SC_FMT_BGR32,
    EXYNOS_SC_FMT_RGB565,
    EXYNOS_SC_FMT_RGB555X,
    EXYNOS_SC_FMT_RGB444,
};

/*!
 * Copy pixel data from RGB to RGB
 *
 * \ingroup exynos_scaler
 *
 * \param pxinfo
 *   information for pixel data copy [in]
 *
 * \param dev_num
 *   Scaler H/W instance number. Starts from 0 [in]
 *
 * \return
 *   true on success in copying pixel data.
 *   false on failure.
 */
bool exynos_sc_copy_pixels(
    struct exynos_sc_pxinfo *pxinfo,
    int dev_num);

int hal_pixfmt_to_v4l2(int hal_pixel_format);

#ifdef __cplusplus
}
#endif

#endif /* _EXYNOS_SCALER_H_ */
