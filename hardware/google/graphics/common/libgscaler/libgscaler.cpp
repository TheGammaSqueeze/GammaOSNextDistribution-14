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
 * \file      libgscaler.cpp
 * \brief     source file for Gscaler HAL
 * \author    Sungchun Kang (sungchun.kang@samsung.com)
 * \date      2013/06/01
 *
 * <b>Revision History: </b>
 * - 2013.06.01 : Sungchun Kang (sungchun.kang@samsung.com) \n
 *   Create
 */

#include <linux/v4l2-subdev.h>

#include "libgscaler_obj.h"
#include "libgscaler_media.h"

void *exynos_gsc_create(void)
{
    CGscaler *gsc = new CGscaler(GSC_M2M_MODE);
    if (!gsc) {
        ALOGE("%s:: failed to allocate Gscaler handle", __func__);
        return NULL;
    }
    if (gsc->m_gsc_find_and_create(gsc) == false) {
        ALOGE("%s::m_exynos_gsc_find_and_create() fail", __func__);
        delete gsc;
        return NULL;
    }

    return reinterpret_cast<void *>(gsc);
}

void *exynos_gsc_create_exclusive(
    int dev_num,
    int mode,
    int out_mode,
    int allow_drm)
{
    Exynos_gsc_In();

    if ((dev_num < 0) || (dev_num >= HW_SCAL_MAX)) {
        ALOGE("%s::fail:: dev_num is not valid(%d) ", __func__, dev_num);
        return NULL;
    }

    if ((dev_num >= NUM_OF_GSC_HW) && (dev_num < HW_SCAL_MAX)) {
        CGscaler *gsc = new CGscaler(mode, out_mode, dev_num, allow_drm);
        if (!gsc) {
            ALOGE("%s:: failed to allocate Gscaler handle", __func__);
            return NULL;
        }

        gsc->scaler = exynos_sc_create_exclusive(dev_num - HW_SCAL0,
            allow_drm);
        if (!gsc->scaler) {
            delete(gsc);
            ALOGE("%s::exynos_sc_create fail", __func__);
            return NULL;
        }
        Exynos_gsc_Out();
        return reinterpret_cast<void *>(gsc);
    }

    if ((mode < 0) || (mode >= NUM_OF_GSC_HW)) {
        ALOGE("%s::fail:: mode is not valid(%d) ", __func__, mode);
        return NULL;
    }

    CGscaler *gsc = new CGscaler(mode, out_mode, dev_num, allow_drm);
    if (!gsc) {
        ALOGE("%s:: failed to allocate Gscaler handle", __func__);
        return NULL;
    }

    if (mode == GSC_M2M_MODE) {
        gsc->gsc_fd = gsc->m_gsc_m2m_create(dev_num);
        if (gsc->gsc_fd < 0) {
            ALOGE("%s::m_gsc_m2m_create(%i) fail", __func__, dev_num);
            goto err;
        }
    } else {
            ALOGE("%s::Unsupported Mode(%i) fail", __func__, dev_num);
	    goto err;
    }

    Exynos_gsc_Out();

    return reinterpret_cast<void *>(gsc);
err:
    switch (mode) {
    case GSC_M2M_MODE:
        gsc->m_gsc_m2m_destroy(gsc);
        break;
    }

    delete(gsc);

    Exynos_gsc_Out();

    return NULL;
}

void exynos_gsc_destroy(void *handle)
{
    Exynos_gsc_In();

    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return;
    }

    if (gsc->mode == GSC_M2M_MODE)
        gsc->m_gsc_m2m_destroy(gsc);

    delete(gsc);

    Exynos_gsc_Out();
}

int exynos_gsc_set_csc_property(
    void        *handle,
    unsigned int eq_auto,
    unsigned int range_full,
    unsigned int v4l2_colorspace)
{
    Exynos_gsc_In();

    CGscaler *gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    if (gsc->gsc_id >= HW_SCAL0) {
        int ret;
        ret = exynos_sc_csc_exclusive(gsc->scaler,
                            range_full, v4l2_colorspace);
        Exynos_gsc_Out();
        return ret;
    }
    gsc->eq_auto = eq_auto;
    gsc->range_full = range_full;
    gsc->v4l2_colorspace = v4l2_colorspace;

    Exynos_gsc_Out();

    return 0;
}

int exynos_gsc_set_src_format(
    void        *handle,
    unsigned int width,
    unsigned int height,
    unsigned int crop_left,
    unsigned int crop_top,
    unsigned int crop_width,
    unsigned int crop_height,
    unsigned int v4l2_colorformat,
    unsigned int cacheable,
    unsigned int mode_drm)
{
    Exynos_gsc_In();

    CGscaler *gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }
    gsc->src_info.width            = width;
    gsc->src_info.height           = height;
    gsc->src_info.crop_left        = crop_left;
    gsc->src_info.crop_top         = crop_top;
    gsc->src_info.crop_width       = crop_width;
    gsc->src_info.crop_height      = crop_height;
    gsc->src_info.v4l2_colorformat = v4l2_colorformat;
    gsc->src_info.cacheable        = cacheable;
    gsc->src_info.mode_drm         = mode_drm;
    gsc->src_info.dirty            = true;

    Exynos_gsc_Out();

    return 0;
}

int exynos_gsc_set_dst_format(
    void        *handle,
    unsigned int width,
    unsigned int height,
    unsigned int crop_left,
    unsigned int crop_top,
    unsigned int crop_width,
    unsigned int crop_height,
    unsigned int v4l2_colorformat,
    unsigned int cacheable,
    unsigned int mode_drm)
{
    Exynos_gsc_In();

    CGscaler *gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    gsc->dst_info.width            = width;
    gsc->dst_info.height           = height;
    gsc->dst_info.crop_left        = crop_left;
    gsc->dst_info.crop_top         = crop_top;
    gsc->dst_info.crop_width       = crop_width;
    gsc->dst_info.crop_height      = crop_height;
    gsc->dst_info.v4l2_colorformat = v4l2_colorformat;
    gsc->dst_info.dirty            = true;
    gsc->dst_info.cacheable        = cacheable;
    gsc->dst_info.mode_drm         = mode_drm;

    Exynos_gsc_Out();

    return 0;
}

int exynos_gsc_set_rotation(
    void *handle,
    int   rotation,
    int   flip_horizontal,
    int   flip_vertical)
{
    CGscaler *gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    int new_rotation = rotation % 360;

    if (new_rotation % 90 != 0) {
        ALOGE("%s::rotation(%d) cannot be acceptable fail", __func__,
            rotation);
        return -1;
    }

    if(new_rotation < 0)
        new_rotation = -new_rotation;

    gsc->dst_info.rotation        = new_rotation;
    gsc->dst_info.flip_horizontal = flip_horizontal;
    gsc->dst_info.flip_vertical   = flip_vertical;

    return 0;
}

int exynos_gsc_set_src_addr(
    void *handle,
    void *addr[3],
    int mem_type,
    int acquireFenceFd)
{
    Exynos_gsc_In();

    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    gsc->src_info.buf.addr[0] = addr[0];
    gsc->src_info.buf.addr[1] = addr[1];
    gsc->src_info.buf.addr[2] = addr[2];
    gsc->src_info.acquireFenceFd = acquireFenceFd;
    gsc->src_info.buf.mem_type = (enum v4l2_memory)mem_type;

    Exynos_gsc_Out();

    return 0;
}

int exynos_gsc_set_dst_addr(
    void *handle,
    void *addr[3],
    int mem_type,
    int acquireFenceFd)
{
    Exynos_gsc_In();

    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    gsc->dst_info.buf.addr[0] = addr[0];
    gsc->dst_info.buf.addr[1] = addr[1];
    gsc->dst_info.buf.addr[2] = addr[2];
    gsc->dst_info.acquireFenceFd = acquireFenceFd;
    gsc->dst_info.buf.mem_type = (enum v4l2_memory)mem_type;

    Exynos_gsc_Out();

    return 0;
}

int exynos_gsc_convert(void *handle)
{
    Exynos_gsc_In();

    int ret    = -1;
    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return ret;
    }

    if (gsc->m_gsc_m2m_run_core(handle) < 0) {
        ALOGE("%s::exynos_gsc_run_core fail", __func__);
        goto done;
    }

    if (gsc->m_gsc_m2m_wait_frame_done(handle) < 0) {
        ALOGE("%s::exynos_gsc_m2m_wait_frame_done", __func__);
        goto done;
    }

    if (gsc->src_info.releaseFenceFd >= 0) {
        close(gsc->src_info.releaseFenceFd);
        gsc->src_info.releaseFenceFd = -1;
    }

    if (gsc->dst_info.releaseFenceFd >= 0) {
        close(gsc->dst_info.releaseFenceFd);
        gsc->dst_info.releaseFenceFd = -1;
    }

    if (gsc->m_gsc_m2m_stop(handle) < 0) {
        ALOGE("%s::m_gsc_m2m_stop", __func__);
        goto done;
    }

    ret = 0;

done:
    Exynos_gsc_Out();

    return ret;
}

int exynos_gsc_subdev_s_crop(void *handle,
        exynos_mpp_img __UNUSED__ *src_img, exynos_mpp_img *dst_img)
{
    struct v4l2_subdev_crop sd_crop;
    CGscaler *gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    sd_crop.pad = GSCALER_SUBDEV_PAD_SOURCE;
    sd_crop.which = V4L2_SUBDEV_FORMAT_ACTIVE;
    sd_crop.rect.left = dst_img->x;
    sd_crop.rect.top = dst_img->y;
    sd_crop.rect.width = dst_img->w;
    sd_crop.rect.height = dst_img->h;

    return exynos_subdev_s_crop(gsc->mdev.gsc_sd_entity->fd, &sd_crop);
}

int exynos_gsc_config_exclusive(void *handle,
    exynos_mpp_img *src_img, exynos_mpp_img *dst_img)
{
    Exynos_gsc_In();

    int ret = 0;
    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }
    if (gsc->gsc_id >= HW_SCAL0) {
        ret = exynos_sc_config_exclusive(gsc->scaler,
            (exynos_sc_img *)src_img, (exynos_sc_img *)dst_img);
        Exynos_gsc_Out();
        return ret;
    }

    switch (gsc->mode) {
    case GSC_M2M_MODE:
        ret = gsc->m_gsc_m2m_config(handle, src_img, dst_img);
        break;
    case GSC_OUTPUT_MODE:
        ret = gsc->m_gsc_out_config(handle, src_img, dst_img);
        break;
    case GSC_CAPTURE_MODE:
        ret = gsc->m_gsc_cap_config(handle, src_img, dst_img);
        break;
    default:
        break;
    }

    Exynos_gsc_Out();

    return ret;
}

int exynos_gsc_run_exclusive(void *handle,
    exynos_mpp_img *src_img, exynos_mpp_img *dst_img)
{
    Exynos_gsc_In();

    int ret = 0;
    CGscaler* gsc = GetGscaler(handle);
    if (handle == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    if (gsc->gsc_id >= HW_SCAL0) {
        ret = exynos_sc_run_exclusive(gsc->scaler,
            (exynos_sc_img *)src_img, (exynos_sc_img *)dst_img);
        Exynos_gsc_Out();
        return ret;
    }

    switch (gsc->mode) {
    case GSC_M2M_MODE:
        ret = gsc->m_gsc_m2m_run(handle, src_img, dst_img);
        break;
    case GSC_OUTPUT_MODE:
        ret = gsc->m_gsc_out_run(handle, src_img);
        break;
    case GSC_CAPTURE_MODE:
        ret = gsc->m_gsc_cap_run(handle, dst_img);
        break;
    default:
        break;
    }

    Exynos_gsc_Out();

    return ret;
}

void *exynos_gsc_create_blend_exclusive(int dev_num, int mode, int out_mode,
                                                                int allow_drm)
{
    Exynos_gsc_In();

    if ((dev_num < 0) || (dev_num >= HW_SCAL_MAX)) {
        ALOGE("%s::fail:: dev_num is not valid(%d) ", __func__, dev_num);
        return NULL;
    }

    if ((dev_num >= NUM_OF_GSC_HW) && (dev_num < HW_SCAL_MAX)) {
        CGscaler *gsc = new CGscaler(mode, out_mode, dev_num, allow_drm);
        if (!gsc) {
            ALOGE("%s:: failed to allocate Gscaler handle", __func__);
            return NULL;
        }

        gsc->scaler = exynos_sc_create_blend_exclusive(dev_num - HW_SCAL0, allow_drm);
        if (!gsc->scaler) {
            Exynos_gsc_Out();
            delete(gsc);
            ALOGE("%s::exynos_sc_create_blend_exclusive failed", __func__);
            return NULL;
        }
        Exynos_gsc_Out();

        return reinterpret_cast<void *>(gsc);
    }

    Exynos_gsc_Out();

    return NULL;
}

int exynos_gsc_config_blend_exclusive(void *handle,
    exynos_mpp_img *src_img, exynos_mpp_img *dst_img,
    struct SrcBlendInfo  *srcblendinfo)
{
    Exynos_gsc_In();

    int ret = 0;
    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }
    if (gsc->gsc_id >= HW_SCAL0) {
        ret = exynos_sc_config_blend_exclusive(gsc->scaler,
                                               (exynos_sc_img *)src_img,
                                               (exynos_sc_img *)dst_img,
                                               srcblendinfo);
        Exynos_gsc_Out();
        return ret;
    }
        Exynos_gsc_Out();
        return ret;
}

int exynos_gsc_wait_frame_done_exclusive(void *handle)
{
    Exynos_gsc_In();

    int ret = 0;
    CGscaler* gsc = GetGscaler(handle);
    if (handle == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    if (gsc->gsc_id >= HW_SCAL0) {
        ret = exynos_sc_wait_frame_done_exclusive(gsc->scaler);
        Exynos_gsc_Out();
        return ret;
    }

    if (gsc->mode == GSC_M2M_MODE)
        ret = gsc->m_gsc_m2m_wait_frame_done(handle);

    Exynos_gsc_Out();

    return ret;
}

int exynos_gsc_stop_exclusive(void *handle)
{
    Exynos_gsc_In();

    int ret = 0;
    CGscaler* gsc = GetGscaler(handle);
    if (handle == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    if (gsc->gsc_id >= HW_SCAL0) {
        ret = exynos_sc_stop_exclusive(gsc->scaler);
        Exynos_gsc_Out();
        return ret;
    }

    switch (gsc->mode) {
    case GSC_M2M_MODE:
        ret = gsc->m_gsc_m2m_stop(handle);
        break;
    default:
        break;
    }

    Exynos_gsc_Out();

    return ret;
}

int exynos_gsc_free_and_close(void *handle)
{
    Exynos_gsc_In();

    struct v4l2_requestbuffers reqbuf;
    int ret = 0;
    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }


    if (gsc->gsc_id >= HW_SCAL0) {
        ret = exynos_sc_free_and_close(gsc->scaler);
        Exynos_gsc_Out();
        return ret;
    }

    memset(&reqbuf, 0, sizeof(struct v4l2_requestbuffers));
    if (gsc->mode == GSC_OUTPUT_MODE)
	    reqbuf.type   = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    else
	    reqbuf.type   = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;

    reqbuf.memory = V4L2_MEMORY_DMABUF;
    reqbuf.count  = 0;

    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_REQBUFS, &reqbuf) < 0) {
        ALOGE("%s::request buffers failed", __func__);
        return -1;
    }

    exynos_gsc_destroy(gsc);
    Exynos_gsc_Out();

    return 0;
}
