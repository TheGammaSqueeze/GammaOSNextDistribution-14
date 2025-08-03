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
 * \file      libgscaler_obj.cpp
 * \brief     source file for Gscaler HAL
 * \author    Sungchun Kang (sungchun.kang@samsung.com)
 * \date      2013/06/01
 *
 * <b>Revision History: </b>
 * - 2013.06.01 : Sungchun Kang (sungchun.kang@samsung.com) \n
 *   Create
 */

#include <cstdio>
#include <cerrno>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <linux/v4l2-mediabus.h>

#include <exynos_scaler.h>

#include "libgscaler_media.h"
#include "libgscaler_obj.h"

// Definitions of values that are not present in enum v4l2_mbus_pixelcode
#define V4L2_MBUS_FMT_XRGB8888_4X8_LE 0x1009
#define V4L2_MBUS_FMT_YUV8_1X24       0x2014

#define V4L2_CID_EXYNOS_BASE            (V4L2_CTRL_CLASS_USER | 0x2000)
#define V4L2_CID_CSC_EQ_MODE            (V4L2_CID_EXYNOS_BASE + 100)
#define V4L2_CID_CSC_EQ                 (V4L2_CID_EXYNOS_BASE + 101)
#define V4L2_CID_CSC_RANGE              (V4L2_CID_EXYNOS_BASE + 102)
#define V4L2_CID_CONTENT_PROTECTION     (V4L2_CID_EXYNOS_BASE + 201)
#define V4L2_CID_CACHEABLE              (V4L2_CID_EXYNOS_BASE + 10)

#define V4L2_PIX_FMT_NV12N              v4l2_fourcc('N', 'N', '1', '2')
#define V4L2_PIX_FMT_NV12NT             v4l2_fourcc('T', 'N', '1', '2')
#define V4L2_PIX_FMT_YUV420N            v4l2_fourcc('Y', 'N', '1', '2')
#define V4L2_PIX_FMT_NV12N_10B          v4l2_fourcc('B', 'N', '1', '2')

#define V4L2_BUF_FLAG_USE_SYNC          0x00008000

static int v4l2_pixfmt_to_yuvinfo(unsigned int v4l2_pixel_format, unsigned int * bpp, unsigned int * planes)
{
    switch (v4l2_pixel_format) {
    case V4L2_PIX_FMT_NV12:
    case V4L2_PIX_FMT_NV21:
    case V4L2_PIX_FMT_NV12N:
    case V4L2_PIX_FMT_NV12NT:
    case V4L2_PIX_FMT_YUV420:
    case V4L2_PIX_FMT_YVU420:
    case V4L2_PIX_FMT_YUV420N:
        *bpp    = 12;
        *planes = 1;
        break;

    case V4L2_PIX_FMT_NV12M:
    case V4L2_PIX_FMT_NV21M:
    case V4L2_PIX_FMT_NV12MT:
    case V4L2_PIX_FMT_NV12MT_16X16:
        *bpp    = 12;
        *planes = 2;
        break;

    case V4L2_PIX_FMT_YUV420M:
    case V4L2_PIX_FMT_YVU420M:
        *bpp    = 12;
        *planes = 3;
        break;

    case V4L2_PIX_FMT_YUYV:
    case V4L2_PIX_FMT_YVYU:
    case V4L2_PIX_FMT_UYVY:
    case V4L2_PIX_FMT_VYUY:
    case V4L2_PIX_FMT_NV16:
    case V4L2_PIX_FMT_NV61:
        *bpp    = 16;
        *planes = 1;
        break;
        break;
    default:
        return -1;
        break;
    }

    return 0;
}

static int gsc_get_yuv_bpp(unsigned int v4l2_pixel_format)
{
    unsigned int bpp, planes;

    if (v4l2_pixfmt_to_yuvinfo(v4l2_pixel_format, &bpp, &planes) < 0)
        bpp = -1;

    return bpp;
}

static int gsc_get_yuv_planes(unsigned int v4l2_pixel_format)
{
    unsigned int bpp, planes;

    if (v4l2_pixfmt_to_yuvinfo(v4l2_pixel_format, &bpp, &planes) < 0)
        planes = -1;

    return planes;
}

#define VIDEODEV_MAX 255
static int exynos_v4l2_open_devname(const char *devname, int oflag, ...)
{
    bool found = false;
    int fd = -1;
    struct stat s;
    va_list ap;
    FILE *stream_fd;
    char filename[64], name[64];
    int i = 0;

    do {
        if (i > VIDEODEV_MAX)
            break;

        /* video device node */
        snprintf(filename, sizeof(filename), "/dev/video%d", i);

        /* if the node is video device */
        if ((lstat(filename, &s) == 0) && S_ISCHR(s.st_mode) &&
                ((int)((unsigned short)(s.st_rdev) >> 8) == 81)) {
            ALOGD("try node: %s", filename);
            /* open sysfs entry */
            snprintf(filename, sizeof(filename), "/sys/class/video4linux/video%d/name", i);
            if (S_ISLNK(s.st_mode)) {
                ALOGE("symbolic link detected");
                return -1;
            }
            stream_fd = fopen(filename, "r");
            if (stream_fd == NULL) {
                ALOGE("failed to open sysfs entry for videodev (%d - %s)", errno, strerror(errno));
                i++;
                continue;   /* try next */
            }

            /* read sysfs entry for device name */
            char *p = fgets(name, sizeof(name), stream_fd);
            fclose(stream_fd);

            /* check read size */
            if (p == NULL) {
                ALOGE("failed to read sysfs entry for videodev");
            } else {
                /* matched */
                if (strncmp(name, devname, strlen(devname)) == 0) {
                    ALOGI("node found for device %s: /dev/video%d", devname, i);
                    found = true;
                    break;
                }
            }
        }
        i++;
    } while (found == false);

    if (found) {
        snprintf(filename, sizeof(filename), "/dev/video%d", i);
        va_start(ap, oflag);
        fd = open(filename, oflag, ap);
        va_end(ap);

        if (fd > 0)
            ALOGI("open video device %s", filename);
        else
            ALOGE("failed to open video device %s", filename);
    } else {
        ALOGE("no video device found");
    }

    return fd;
}

int CGscaler::m_gsc_m2m_create(int dev)
{
    Exynos_gsc_In();

    int          fd = 0;
    int          video_node_num;
    char         node[32];

    switch(dev) {
    case 0:
        video_node_num = NODE_NUM_GSC_0;
        break;
    case 1:
        video_node_num = NODE_NUM_GSC_1;
        break;
#ifndef USES_ONLY_GSC0_GSC1
    case 2:
        video_node_num = NODE_NUM_GSC_2;
        break;
    case 3:
        video_node_num = NODE_NUM_GSC_3;
        break;
#endif
    default:
        ALOGE("%s::unexpected dev(%d) fail", __func__, dev);
        return -1;
        break;
    }

    snprintf(node, sizeof(node), "%s%d", PFX_NODE_GSC, video_node_num);
    fd = open(node, O_RDWR);
    if (fd < 0) {
        ALOGE("%s::exynos_v4l2_open(%s) fail", __func__, node);
        return -1;
    }

    Exynos_gsc_Out();

    return fd;
}

bool CGscaler::m_gsc_find_and_create(void *handle)
{
    Exynos_gsc_In();

    int          i                 = 0;
    bool         flag_find_new_gsc = false;
    unsigned int total_sleep_time  = 0;
    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return false;
    }

    do {
        for (i = 0; i < NUM_OF_GSC_HW; i++) {
#ifndef USES_ONLY_GSC0_GSC1
            if (i == 0 || i == 3)
#else
            if (i == 0)
#endif
                continue;

            gsc->gsc_id = i;
            gsc->gsc_fd = gsc->m_gsc_m2m_create(i);
            if (gsc->gsc_fd < 0) {
                gsc->gsc_fd = 0;
                continue;
            }

            flag_find_new_gsc = true;
            break;
        }

        if (flag_find_new_gsc == false) {
            usleep(GSC_WAITING_TIME_FOR_TRYLOCK);
            total_sleep_time += GSC_WAITING_TIME_FOR_TRYLOCK;
            ALOGV("%s::waiting for the gscaler availability", __func__);
        }

    } while(flag_find_new_gsc == false
            && total_sleep_time < MAX_GSC_WAITING_TIME_FOR_TRYLOCK);

    if (flag_find_new_gsc == false)
        ALOGE("%s::we don't have any available gsc.. fail", __func__);

    Exynos_gsc_Out();

    return flag_find_new_gsc;
}

bool CGscaler::m_gsc_m2m_destroy(void *handle)
{
    Exynos_gsc_In();

    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return false;
    }

    /*
     * just in case, we call stop here because we cannot afford to leave
     * secure side protection on if things failed.
     */
    gsc->m_gsc_m2m_stop(handle);

    if (gsc->gsc_id >= HW_SCAL0) {
        bool ret = exynos_sc_free_and_close(gsc->scaler);
        Exynos_gsc_Out();
        return ret;
    }

    if (0 < gsc->gsc_fd)
        close(gsc->gsc_fd);
    gsc->gsc_fd = 0;

    Exynos_gsc_Out();

    return true;
}

int CGscaler::m_gsc_m2m_stop(void *handle)
{
    Exynos_gsc_In();

    struct v4l2_requestbuffers req_buf;
    int ret = 0;
    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    if (!gsc->src_info.stream_on && !gsc->dst_info.stream_on) {
        /* wasn't streaming, return success */
        return 0;
    } else if (gsc->src_info.stream_on != gsc->dst_info.stream_on) {
        ALOGE("%s: invalid state, queue stream state doesn't match \
                (%d != %d)", __func__, gsc->src_info.stream_on,
                gsc->dst_info.stream_on);
        ret = -1;
    }

    /*
     * we need to plow forward on errors below to make sure that if we had
     * turned on content protection on secure side, we turn it off.
     *
     * also, if we only failed to turn on one of the streams, we'll turn
     * the other one off correctly.
     */
    if (gsc->src_info.stream_on == true) {
        if (ioctl(gsc->gsc_fd, VIDIOC_STREAMOFF, &gsc->src_info.buf.buf_type) < 0) {
            ALOGE("%s::exynos_v4l2_streamoff(src) fail", __func__);
            ret = -1;
        }
        gsc->src_info.stream_on = false;
    }

    if (gsc->dst_info.stream_on == true) {
        if (ioctl(gsc->gsc_fd, VIDIOC_STREAMOFF, &gsc->dst_info.buf.buf_type) < 0) {
            ALOGE("%s::exynos_v4l2_streamoff(dst) fail", __func__);
            ret = -1;
        }
        gsc->dst_info.stream_on = false;
    }

    /* Secure DRM support by GScaler is removed out */

    struct v4l2_control ctrl;

    ctrl.id = V4L2_CID_CONTENT_PROTECTION;
    ctrl.value = 0;
    if (ioctl(gsc->gsc_fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s::exynos_v4l2_s_ctrl(V4L2_CID_CONTENT_PROTECTION) fail",
              __func__);
        ret = -1;
    }

    /* src: clear_buf */
    req_buf.count  = 0;
    req_buf.type   = gsc->src_info.buf.buf_type;
    req_buf.memory = gsc->src_info.buf.mem_type;
    if (ioctl(gsc->gsc_fd, VIDIOC_REQBUFS, &req_buf) < 0) {
        ALOGE("%s::exynos_v4l2_reqbufs():src: fail", __func__);
        ret = -1;
    }

    /* dst: clear_buf */
    req_buf.count  = 0;
    req_buf.type   = gsc->dst_info.buf.buf_type;
    req_buf.memory = gsc->dst_info.buf.mem_type;;
    if (ioctl(gsc->gsc_fd, VIDIOC_REQBUFS, &req_buf) < 0) {
        ALOGE("%s::exynos_v4l2_reqbufs():dst: fail", __func__);
        ret = -1;
    }

    Exynos_gsc_Out();

    return ret;
}

int CGscaler::m_gsc_m2m_run_core(void *handle)
{
    Exynos_gsc_In();

    unsigned int rotate, hflip, vflip;
    bool is_dirty;
    bool is_drm;
    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    is_dirty = gsc->src_info.dirty || gsc->dst_info.dirty;
    is_drm = gsc->src_info.mode_drm;

    if (is_dirty && (gsc->src_info.mode_drm != gsc->dst_info.mode_drm)) {
        ALOGE("%s: drm mode mismatch between src and dst, \
                gsc%d (s=%d d=%d)", __func__, gsc->gsc_id,
                gsc->src_info.mode_drm, gsc->dst_info.mode_drm);
        return -1;
    } else if (is_drm && !gsc->allow_drm) {
        ALOGE("%s: drm mode is not supported on gsc%d", __func__,
              gsc->gsc_id);
        return -1;
    }

    CGscaler::rotateValueHAL2GSC(gsc->dst_img.rot, &rotate, &hflip, &vflip);

    if (CGscaler::m_gsc_check_src_size(&gsc->src_info.width,
            &gsc->src_info.height, &gsc->src_info.crop_left,
            &gsc->src_info.crop_top, &gsc->src_info.crop_width,
            &gsc->src_info.crop_height, gsc->src_info.v4l2_colorformat,
            (rotate == 90 || rotate == 270)) == false) {
        ALOGE("%s::m_gsc_check_src_size() fail", __func__);
        return -1;
    }

    if (CGscaler::m_gsc_check_dst_size(&gsc->dst_info.width,
            &gsc->dst_info.height, &gsc->dst_info.crop_left,
            &gsc->dst_info.crop_top, &gsc->dst_info.crop_width,
            &gsc->dst_info.crop_height, gsc->dst_info.v4l2_colorformat,
            gsc->dst_info.rotation) == false) {
        ALOGE("%s::m_gsc_check_dst_size() fail", __func__);
        return -1;
    }

    /* dequeue buffers from previous work if necessary */
    if (gsc->src_info.stream_on == true) {
        if (gsc->m_gsc_m2m_wait_frame_done(handle) < 0) {
            ALOGE("%s::exynos_gsc_m2m_wait_frame_done fail", __func__);
            return -1;
        }
    }

    /*
     * need to set the content protection flag before doing reqbufs
     * in set_format
     */
    if (is_dirty && gsc->allow_drm && is_drm) {
        struct v4l2_control ctrl;

        ctrl.id = V4L2_CID_CONTENT_PROTECTION;
        ctrl.value = is_drm;
        if (ioctl(gsc->gsc_fd,VIDIOC_S_CTRL, &ctrl) < 0) {
            ALOGE("%s::exynos_v4l2_s_ctrl() fail", __func__);
            return -1;
        }
    }

    /*
     * from this point on, we have to ensure to call stop to clean up
     * whatever state we have set.
     */

    if (gsc->src_info.dirty) {
        if (CGscaler::m_gsc_set_format(gsc->gsc_fd, &gsc->src_info) == false) {
            ALOGE("%s::m_gsc_set_format(src) fail", __func__);
            goto done;
        }
        gsc->src_info.dirty = false;
    }

    if (gsc->dst_info.dirty) {
        if (CGscaler::m_gsc_set_format(gsc->gsc_fd, &gsc->dst_info) == false) {
            ALOGE("%s::m_gsc_set_format(dst) fail", __func__);
            goto done;
        }
        gsc->dst_info.dirty = false;
    }

    /*
     * set up csc equation property
     */
    if (is_dirty) {
        struct v4l2_control ctrl;

        ctrl.id = V4L2_CID_CSC_EQ_MODE;
        ctrl.value = gsc->eq_auto;
        if (ioctl(gsc->gsc_fd, VIDIOC_S_CTRL, &ctrl) < 0) {
            ALOGE("%s::exynos_v4l2_s_ctrl(V4L2_CID_CSC_EQ_MODE) fail", __func__);
            return -1;
        }

        ctrl.id = V4L2_CID_CSC_EQ;
        ctrl.value = gsc->v4l2_colorspace;
        if (ioctl(gsc->gsc_fd, VIDIOC_S_CTRL, &ctrl) < 0) {
            ALOGE("%s::exynos_v4l2_s_ctrl(V4L2_CID_CSC_EQ) fail", __func__);
            return -1;
        }

        ctrl.id = V4L2_CID_CSC_RANGE;
        ctrl.value = gsc->range_full;
        if (ioctl(gsc->gsc_fd, VIDIOC_S_CTRL, &ctrl) < 0) {
            ALOGE("%s::exynos_v4l2_s_ctrl(V4L2_CID_CSC_RANGE) fail", __func__);
            return -1;
        }
    }

    /* if we are enabling drm, make sure to enable hw protection.
     * Need to do this before queuing buffers so that the mmu is reserved
     * and power domain is kept on.
     */
    /* Secure DRM upport by GScaler is removed out */

    if (gsc->m_gsc_set_addr(gsc->gsc_fd, &gsc->src_info) == false) {
        ALOGE("%s::m_gsc_set_addr(src) fail", __func__);
        goto done;
    }

    if (gsc->m_gsc_set_addr(gsc->gsc_fd, &gsc->dst_info) == false) {
        ALOGE("%s::m_gsc_set_addr(dst) fail", __func__);
        goto done;
    }

    if (gsc->src_info.stream_on == false) {
        if (ioctl(gsc->gsc_fd, VIDIOC_STREAMON, &gsc->src_info.buf.buf_type) < 0) {
            ALOGE("%s::exynos_v4l2_streamon(src) fail", __func__);
            goto done;
        }
        gsc->src_info.stream_on = true;
    }

    if (gsc->dst_info.stream_on == false) {
        if (ioctl(gsc->gsc_fd, VIDIOC_STREAMON, &gsc->dst_info.buf.buf_type) < 0) {
            ALOGE("%s::exynos_v4l2_streamon(dst) fail", __func__);
            goto done;
        }
        gsc->dst_info.stream_on = true;
    }

    Exynos_gsc_Out();

    return 0;

done:
    gsc->m_gsc_m2m_stop(handle);
    return -1;
}

bool CGscaler::m_gsc_check_src_size(
    unsigned int *w,      unsigned int *h,
    unsigned int __UNUSED__ *crop_x, unsigned int __UNUSED__ *crop_y,
    unsigned int __UNUSED__ *crop_w, unsigned int __UNUSED__ *crop_h,
    int v4l2_colorformat, bool rotation)
{
    unsigned int minWidth, minHeight, shift = 0;
    if (v4l2_colorformat == V4L2_PIX_FMT_RGB32 || v4l2_colorformat == V4L2_PIX_FMT_RGB565)
        shift = 1;
    if (rotation) {
        minWidth = GSC_MIN_SRC_H_SIZE >> shift;
        minHeight = GSC_MIN_SRC_W_SIZE >> shift;
    } else {
        minWidth = GSC_MIN_SRC_W_SIZE >> shift;
        minHeight = GSC_MIN_SRC_H_SIZE >> shift;
    }

    if (*w < minWidth || *h < minHeight) {
        ALOGE("%s::too small size (w : %d < %d) (h : %d < %d)",
            __func__, GSC_MIN_SRC_W_SIZE, *w, GSC_MIN_SRC_H_SIZE, *h);
        return false;
    }

    if (*crop_w < minWidth || *crop_h < minHeight) {
        ALOGE("%s::too small size (w : %d < %d) (h : %d < %d)",
            __func__, GSC_MIN_SRC_W_SIZE,* crop_w, GSC_MIN_SRC_H_SIZE, *crop_h);
        return false;
    }

    return true;
}

bool CGscaler::m_gsc_check_dst_size(
    unsigned int *w,      unsigned int *h,
    unsigned int __UNUSED__ *crop_x, unsigned int __UNUSED__ *crop_y,
    unsigned int __UNUSED__ *crop_w, unsigned int __UNUSED__ *crop_h,
    int __UNUSED__ v4l2_colorformat, int __UNUSED__ rotation)
{
    if (*w < GSC_MIN_DST_W_SIZE || *h < GSC_MIN_DST_H_SIZE) {
        ALOGE("%s::too small size (w : %d < %d) (h : %d < %d)",
            __func__, GSC_MIN_DST_W_SIZE, *w, GSC_MIN_DST_H_SIZE, *h);
        return false;
    }

    if (*crop_w < GSC_MIN_DST_W_SIZE || *crop_h < GSC_MIN_DST_H_SIZE) {
        ALOGE("%s::too small size (w : %d < %d) (h : %d < %d)",
            __func__, GSC_MIN_DST_W_SIZE,* crop_w, GSC_MIN_DST_H_SIZE, *crop_h);
        return false;
    }

    return true;
}


int CGscaler::m_gsc_multiple_of_n(int number, int N)
{
    int result = number;
    switch (N) {
    case 1:
    case 2:
    case 4:
    case 8:
    case 16:
    case 32:
    case 64:
    case 128:
    case 256:
        result = (number - (number & (N-1)));
        break;
    default:
        result = number - (number % N);
        break;
    }
    return result;
}

int CGscaler::m_gsc_m2m_wait_frame_done(void *handle)
{
    Exynos_gsc_In();

    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    if ((gsc->src_info.stream_on == false) ||
        (gsc->dst_info.stream_on == false)) {
        ALOGE("%s:: src_strean_on or dst_stream_on are false", __func__);
        return -1;
    }

    if (gsc->src_info.buf.buffer_queued) {
        if (ioctl(gsc->gsc_fd, VIDIOC_DQBUF, &gsc->src_info.buf.buffer) < 0) {
            ALOGE("%s::exynos_v4l2_dqbuf(src) fail", __func__);
            return -1;
        }
        gsc->src_info.buf.buffer_queued = false;
    }

    if (gsc->dst_info.buf.buffer_queued) {
        if (ioctl(gsc->gsc_fd, VIDIOC_DQBUF, &gsc->dst_info.buf.buffer) < 0) {
            ALOGE("%s::exynos_v4l2_dqbuf(dst) fail", __func__);
            return -1;
        }
        gsc->dst_info.buf.buffer_queued = false;
    }

    Exynos_gsc_Out();

    return 0;
}

bool CGscaler::m_gsc_set_format(int fd, GscInfo *info)
{
    Exynos_gsc_In();

    struct v4l2_requestbuffers req_buf;
    int                        plane_count;

    plane_count = m_gsc_get_plane_count(info->v4l2_colorformat);
    if (plane_count < 0) {
        ALOGE("%s::not supported v4l2_colorformat", __func__);
        return false;
    }

    struct v4l2_control ctrl;

    ctrl.id = V4L2_CID_ROTATE;
    ctrl.value = info->rotation;
    if (ioctl(fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s::exynos_v4l2_s_ctrl(V4L2_CID_ROTATE) fail", __func__);
        return false;
    }

    ctrl.id = V4L2_CID_VFLIP;
    ctrl.value = info->flip_horizontal;
    if (ioctl(fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s::exynos_v4l2_s_ctrl(V4L2_CID_VFLIP) fail", __func__);
        return false;
    }

    ctrl.id = V4L2_CID_HFLIP;
    ctrl.value = info->flip_vertical;
    if (ioctl(fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s::exynos_v4l2_s_ctrl(V4L2_CID_HFLIP) fail", __func__);
        return false;
    }

    info->format.type = info->buf.buf_type;
    info->format.fmt.pix_mp.width       = info->width;
    info->format.fmt.pix_mp.height      = info->height;
    info->format.fmt.pix_mp.pixelformat = info->v4l2_colorformat;
    info->format.fmt.pix_mp.field       = V4L2_FIELD_ANY;
    info->format.fmt.pix_mp.num_planes  = plane_count;

    if (ioctl(fd, VIDIOC_S_FMT, &info->format) < 0) {
        ALOGE("%s::exynos_v4l2_s_fmt() fail", __func__);
        return false;
    }

    info->crop.type     = info->buf.buf_type;
    info->crop.c.left   = info->crop_left;
    info->crop.c.top    = info->crop_top;
    info->crop.c.width  = info->crop_width;
    info->crop.c.height = info->crop_height;

    if (ioctl(fd, VIDIOC_S_CROP, &info->crop) < 0) {
        ALOGE("%s::exynos_v4l2_s_crop() fail", __func__);
        return false;
    }

    ctrl.id = V4L2_CID_CACHEABLE;
    ctrl.value = info->cacheable;
    if (ioctl(fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s::exynos_v4l2_s_ctrl() fail", __func__);
        return false;
    }

    req_buf.count  = 1;
    req_buf.type   = info->buf.buf_type;
    req_buf.memory = info->buf.mem_type;
    if (ioctl(fd, VIDIOC_REQBUFS, &req_buf) < 0) {
        ALOGE("%s::exynos_v4l2_reqbufs() fail", __func__);
        return false;
    }

    Exynos_gsc_Out();

    return true;
}

unsigned int CGscaler::m_gsc_get_plane_count(int v4l_pixel_format)
{
    int plane_count = 0;

    switch (v4l_pixel_format) {
    case V4L2_PIX_FMT_RGB32:
    case V4L2_PIX_FMT_BGR32:
    case V4L2_PIX_FMT_RGB24:
    case V4L2_PIX_FMT_RGB565:
    case V4L2_PIX_FMT_RGB555X:
    case V4L2_PIX_FMT_RGB444:
    case V4L2_PIX_FMT_YUYV:
    case V4L2_PIX_FMT_UYVY:
    case V4L2_PIX_FMT_NV16:
    case V4L2_PIX_FMT_NV61:
    case V4L2_PIX_FMT_YVU420:
    case V4L2_PIX_FMT_YUV420:
    case V4L2_PIX_FMT_NV12:
    case V4L2_PIX_FMT_NV21:
    case V4L2_PIX_FMT_YUV422P:
        plane_count = 1;
        break;
    case V4L2_PIX_FMT_NV12M:
    case V4L2_PIX_FMT_NV12MT_16X16:
    case V4L2_PIX_FMT_NV21M:
        plane_count = 2;
        break;
    case V4L2_PIX_FMT_YVU420M:
    case V4L2_PIX_FMT_YUV420M:
        plane_count = 3;
        break;
    default:
        ALOGE("%s::unmatched v4l_pixel_format color_space(0x%x)\n",
             __func__, v4l_pixel_format);
        plane_count = -1;
        break;
    }

    return plane_count;
}

bool CGscaler::m_gsc_set_addr(int fd, GscInfo *info)
{
    unsigned int i;
    unsigned int plane_size[NUM_OF_GSC_PLANES];

    CGscaler::m_gsc_get_plane_size(plane_size, info->width,
                         info->height, info->v4l2_colorformat);

    info->buf.buffer.index    = 0;
    info->buf.buffer.flags    = V4L2_BUF_FLAG_USE_SYNC;
    info->buf.buffer.type     = info->buf.buf_type;
    info->buf.buffer.memory   = info->buf.mem_type;
    info->buf.buffer.m.planes = info->buf.planes;
    info->buf.buffer.length   = info->format.fmt.pix_mp.num_planes;
    info->buf.buffer.reserved = info->acquireFenceFd;

    for (i = 0; i < info->format.fmt.pix_mp.num_planes; i++) {
        if (info->buf.buffer.memory == V4L2_MEMORY_DMABUF)
            info->buf.buffer.m.planes[i].m.fd = (long)info->buf.addr[i];
        else
            info->buf.buffer.m.planes[i].m.userptr =
                (unsigned long)info->buf.addr[i];
        info->buf.buffer.m.planes[i].length    = plane_size[i];
        info->buf.buffer.m.planes[i].bytesused = 0;
    }

    if (ioctl(fd, VIDIOC_QBUF, &info->buf.buffer) < 0) {
        ALOGE("%s::exynos_v4l2_qbuf() fail", __func__);
        return false;
    }
    info->buf.buffer_queued = true;

    info->releaseFenceFd = info->buf.buffer.reserved;

    return true;
}

unsigned int CGscaler::m_gsc_get_plane_size(
    unsigned int *plane_size,
    unsigned int  width,
    unsigned int  height,
    int           v4l_pixel_format)
{
    switch (v4l_pixel_format) {
    /* 1 plane */
    case V4L2_PIX_FMT_RGB32:
    case V4L2_PIX_FMT_BGR32:
        plane_size[0] = width * height * 4;
        plane_size[1] = 0;
        plane_size[2] = 0;
        break;
    case V4L2_PIX_FMT_RGB24:
        plane_size[0] = width * height * 3;
        plane_size[1] = 0;
        plane_size[2] = 0;
        break;
    case V4L2_PIX_FMT_RGB565:
    case V4L2_PIX_FMT_RGB555X:
    case V4L2_PIX_FMT_RGB444:
    case V4L2_PIX_FMT_YUYV:
    case V4L2_PIX_FMT_UYVY:
        plane_size[0] = width * height * 2;
        plane_size[1] = 0;
        plane_size[2] = 0;
        break;
    /* 2 planes */
    case V4L2_PIX_FMT_NV12M:
    case V4L2_PIX_FMT_NV21M:
        plane_size[0] = width * height;
        plane_size[1] = width * (height / 2);
        plane_size[2] = 0;
        break;
    case V4L2_PIX_FMT_NV12:
    case V4L2_PIX_FMT_NV21:
        plane_size[0] = width * height * 3 / 2;
        plane_size[1] = 0;
        plane_size[2] = 0;
	break;
    case V4L2_PIX_FMT_NV16:
    case V4L2_PIX_FMT_NV61:
    case V4L2_PIX_FMT_YUV422P:
        plane_size[0] = width * height * 2;
        plane_size[1] = 0;
        plane_size[2] = 0;
        break;
    case V4L2_PIX_FMT_NV12MT_16X16:
        plane_size[0] = ALIGN(width, 16) * ALIGN(height, 16);
        plane_size[1] = ALIGN(width, 16) * ALIGN(height / 2, 8);
        plane_size[2] = 0;
        break;
    /* 3 planes */
    case V4L2_PIX_FMT_YUV420M:
        plane_size[0] = width * height;
        plane_size[1] = (width / 2) * (height / 2);
        plane_size[2] = (width / 2) * (height / 2);
        break;
    case V4L2_PIX_FMT_YVU420:
        plane_size[0] = ALIGN(width, 16) * height + ALIGN(width / 2, 16) * height;
        plane_size[1] = 0;
        plane_size[2] = 0;
        break;
    case V4L2_PIX_FMT_YUV420:
        plane_size[0] = width * height * 3 / 2;
        plane_size[1] = 0;
        plane_size[2] = 0;
        break;
    case V4L2_PIX_FMT_YVU420M:
        plane_size[0] = ALIGN(width, 16) * height;
        plane_size[1] = ALIGN(width / 2, 16) * (height / 2);
        plane_size[2] = plane_size[1];
        break;
    default:
        ALOGE("%s::unmatched v4l_pixel_format color_space(0x%x)\n",
             __func__, v4l_pixel_format);
        return -1;
    }

    return 0;
}

int CGscaler::m_gsc_m2m_config(void *handle,
    exynos_mpp_img *src_img, exynos_mpp_img *dst_img)
{
    Exynos_gsc_In();

    int32_t      src_color_space;
    int32_t      dst_color_space;
    int ret;
    unsigned int rotate;
    unsigned int hflip;
    unsigned int vflip;
    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    if ((src_img->drmMode && !gsc->allow_drm) ||
        (src_img->drmMode != dst_img->drmMode)) {
        ALOGE("%s::invalid drm state request for gsc%d (s=%d d=%d)",
              __func__, gsc->gsc_id, src_img->drmMode, dst_img->drmMode);
        return -1;
    }

    src_color_space = hal_pixfmt_to_v4l2(src_img->format);
    dst_color_space = hal_pixfmt_to_v4l2(dst_img->format);
    CGscaler::rotateValueHAL2GSC(dst_img->rot, &rotate, &hflip, &vflip);
    exynos_gsc_set_rotation(gsc, rotate, hflip, vflip);

    ret = exynos_gsc_set_src_format(gsc,  src_img->fw, src_img->fh,
          src_img->x, src_img->y, src_img->w, src_img->h,
          src_color_space, src_img->cacheable, src_img->drmMode);
    if (ret < 0) {
        ALOGE("%s: fail: exynos_gsc_set_src_format \
            [fw %d fh %d x %d y %d w %d h %d f %x rot %d]",
            __func__, src_img->fw, src_img->fh, src_img->x, src_img->y,
            src_img->w, src_img->h, src_color_space, src_img->rot);
        return -1;
    }

    ret = exynos_gsc_set_dst_format(gsc, dst_img->fw, dst_img->fh,
          dst_img->x, dst_img->y, dst_img->w, dst_img->h,
          dst_color_space, dst_img->cacheable, dst_img->drmMode);
    if (ret < 0) {
        ALOGE("%s: fail: exynos_gsc_set_dst_format \
            [fw %d fh %d x %d y %d w %d h %d f %x rot %d]",
            __func__, dst_img->fw, dst_img->fh, dst_img->x, dst_img->y,
            dst_img->w, dst_img->h, src_color_space, dst_img->rot);
        return -1;
    }

    Exynos_gsc_Out();

    return 0;
}

int CGscaler::m_gsc_out_config(void *handle,
    exynos_mpp_img *src_img, exynos_mpp_img *dst_img)
{
    Exynos_gsc_In();

    struct v4l2_format  fmt;
    struct v4l2_crop    crop;
    struct v4l2_requestbuffers reqbuf;
    struct v4l2_subdev_format sd_fmt;
    struct v4l2_subdev_crop   sd_crop;
    unsigned int rotate;
    unsigned int hflip;
    unsigned int vflip;
    bool rgb;

    int32_t      src_color_space;
    int32_t      dst_color_space;
    int32_t      src_planes;

    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    if (gsc->src_info.stream_on != false) {
        ALOGE("Error: Src is already streamed on !!!!");
        return -1;
    }

    memcpy(&gsc->src_img, src_img, sizeof(exynos_mpp_img));
    memcpy(&gsc->dst_img, dst_img, sizeof(exynos_mpp_img));
    src_color_space = hal_pixfmt_to_v4l2(src_img->format);
    dst_color_space = hal_pixfmt_to_v4l2(dst_img->format);
    src_planes = m_gsc_get_plane_count(src_color_space);
    src_planes = (src_planes == -1) ? 1 : src_planes;
    rgb = gsc_get_yuv_planes(dst_color_space) == -1;
    CGscaler::rotateValueHAL2GSC(dst_img->rot, &rotate, &hflip, &vflip);

    if (CGscaler::m_gsc_check_src_size(&gsc->src_img.fw,
            &gsc->src_img.fh, &gsc->src_img.x, &gsc->src_img.y,
            &gsc->src_img.w, &gsc->src_img.h, src_color_space,
            (rotate == 90 || rotate == 270)) == false) {
            ALOGE("%s::m_gsc_check_src_size() fail", __func__);
            return -1;
    }

    /*set: src v4l2_buffer*/
    gsc->src_info.buf.buf_idx = 0;
    gsc->src_info.qbuf_cnt = 0;
    /* set format: src pad of GSC sub-dev*/
    sd_fmt.pad   = GSCALER_SUBDEV_PAD_SOURCE;
    sd_fmt.which = V4L2_SUBDEV_FORMAT_ACTIVE;
    if (gsc->out_mode == GSC_OUT_FIMD) {
        sd_fmt.format.width  = gsc->dst_img.fw;
        sd_fmt.format.height = gsc->dst_img.fh;
    } else {
        sd_fmt.format.width  = gsc->dst_img.w;
        sd_fmt.format.height = gsc->dst_img.h;
    }
    sd_fmt.format.code = rgb ? V4L2_MBUS_FMT_XRGB8888_4X8_LE :
                               V4L2_MBUS_FMT_YUV8_1X24;
    if (exynos_subdev_s_fmt(gsc->mdev.gsc_sd_entity->fd, &sd_fmt) < 0) {
            ALOGE("%s::GSC subdev set format failed", __func__);
            return -1;
    }

    /* set crop: src crop of GSC sub-dev*/
    sd_crop.pad   = GSCALER_SUBDEV_PAD_SOURCE;
    sd_crop.which = V4L2_SUBDEV_FORMAT_ACTIVE;
    if (gsc->out_mode == GSC_OUT_FIMD) {
        sd_crop.rect.left   = gsc->dst_img.x;
        sd_crop.rect.top    = gsc->dst_img.y;
        sd_crop.rect.width  = gsc->dst_img.w;
        sd_crop.rect.height = gsc->dst_img.h;
    } else {
        sd_crop.rect.left   = 0;
        sd_crop.rect.top    = 0;
        sd_crop.rect.width  = gsc->dst_img.w;
        sd_crop.rect.height = gsc->dst_img.h;
    }

    /* sink pad is connected to GSC out */
    /*  set format: sink sub-dev */
    if (gsc->out_mode == GSC_OUT_FIMD) {
        sd_fmt.pad   = FIMD_SUBDEV_PAD_SINK;
        sd_fmt.format.width  = gsc->dst_img.w;
        sd_fmt.format.height = gsc->dst_img.h;
    } else {
        sd_fmt.pad   = MIXER_V_SUBDEV_PAD_SINK;
        sd_fmt.format.width  = gsc->dst_img.w + gsc->dst_img.x*2;
        sd_fmt.format.height = gsc->dst_img.h + gsc->dst_img.y*2;
    }

    sd_fmt.which = V4L2_SUBDEV_FORMAT_ACTIVE;
    sd_fmt.format.code = rgb ? V4L2_MBUS_FMT_XRGB8888_4X8_LE :
                               V4L2_MBUS_FMT_YUV8_1X24;
    if (exynos_subdev_s_fmt(gsc->mdev.sink_sd_entity->fd, &sd_fmt) < 0) {
        ALOGE("%s::sink:set format failed (PAD=%d)", __func__,
        sd_fmt.pad);
        return -1;
    }

    /*  set crop: sink sub-dev */
    if (gsc->out_mode == GSC_OUT_FIMD)
        sd_crop.pad   = FIMD_SUBDEV_PAD_SINK;
    else
        sd_crop.pad   = MIXER_V_SUBDEV_PAD_SINK;

    sd_crop.which = V4L2_SUBDEV_FORMAT_ACTIVE;
    if (gsc->out_mode == GSC_OUT_FIMD) {
        sd_crop.rect.left   = gsc->dst_img.x;
        sd_crop.rect.top    = gsc->dst_img.y;
        sd_crop.rect.width  = gsc->dst_img.w;
        sd_crop.rect.height = gsc->dst_img.h;
    } else {
        sd_crop.rect.left   = 0;
        sd_crop.rect.top    = 0;
        sd_crop.rect.width  = gsc->dst_img.w;
        sd_crop.rect.height = gsc->dst_img.h;
    }

    if (gsc->out_mode != GSC_OUT_FIMD) {
        sd_fmt.pad = MIXER_V_SUBDEV_PAD_SOURCE;
        sd_fmt.which = V4L2_SUBDEV_FORMAT_ACTIVE;
        sd_fmt.format.width = gsc->dst_img.w + gsc->dst_img.x*2;
        sd_fmt.format.height = gsc->dst_img.h + gsc->dst_img.y*2;
        sd_fmt.format.code = V4L2_MBUS_FMT_XRGB8888_4X8_LE;
        if (exynos_subdev_s_fmt(gsc->mdev.sink_sd_entity->fd, &sd_fmt) < 0) {
            ALOGE("%s::sink:set format failed (PAD=%d)", __func__,
            sd_fmt.pad);
            return -1;
        }

        sd_fmt.pad   = MIXER_V_SUBDEV_PAD_SOURCE;
        sd_crop.which = V4L2_SUBDEV_FORMAT_ACTIVE;
        sd_crop.rect.left   = gsc->dst_img.x;
        sd_crop.rect.top    = gsc->dst_img.y;
        sd_crop.rect.width  = gsc->dst_img.w;
        sd_crop.rect.height = gsc->dst_img.h;
        if (exynos_subdev_s_crop(gsc->mdev.sink_sd_entity->fd, &sd_crop) < 0) {
            ALOGE("%s::sink: subdev set crop failed(PAD=%d)", __func__,
            sd_crop.pad);
            return -1;
        }
    }

    /*set GSC ctrls */
    struct v4l2_control ctrl;

    ctrl.id = V4L2_CID_ROTATE;
    ctrl.value = rotate;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s:: exynos_v4l2_s_ctrl (V4L2_CID_ROTATE: %d) failed",
            __func__,  rotate);
        return -1;
    }

    ctrl.id = V4L2_CID_HFLIP;
    ctrl.value = vflip;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s:: exynos_v4l2_s_ctrl (V4L2_CID_HFLIP: %d) failed",
            __func__,  vflip);
        return -1;
    }

    ctrl.id = V4L2_CID_VFLIP;
    ctrl.value = hflip;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s:: exynos_v4l2_s_ctrl (V4L2_CID_VFLIP: %d) failed",
            __func__,  hflip);
        return -1;
    }

    ctrl.id = V4L2_CID_CACHEABLE;
    ctrl.value = 1;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s:: exynos_v4l2_s_ctrl (V4L2_CID_CACHEABLE: 1) failed",
            __func__);
        return -1;
    }

    ctrl.id = V4L2_CID_CONTENT_PROTECTION;
    ctrl.value = gsc->src_img.drmMode;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s::exynos_v4l2_s_ctrl(V4L2_CID_CONTENT_PROTECTION) fail",
            __func__);
        return -1;
    }

    ctrl.id = V4L2_CID_CSC_EQ_MODE;
    ctrl.value = gsc->eq_auto;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s::exynos_v4l2_s_ctrl(V4L2_CID_CSC_EQ_MODE) fail", __func__);
        return -1;
    }

    ctrl.id = V4L2_CID_CSC_EQ;
    ctrl.value = gsc->v4l2_colorspace;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s::exynos_v4l2_s_ctrl(V4L2_CID_CSC_EQ) fail", __func__);
        return -1;
    }

    ctrl.id = V4L2_CID_CSC_RANGE;
    ctrl.value = gsc->range_full;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s::exynos_v4l2_s_ctrl(V4L2_CID_CSC_RANGE) fail", __func__);
        return -1;
    }

      /* set src format  :GSC video dev*/
    fmt.type  = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    fmt.fmt.pix_mp.width            = gsc->src_img.fw;
    fmt.fmt.pix_mp.height           = gsc->src_img.fh;
    fmt.fmt.pix_mp.pixelformat    = src_color_space;
    fmt.fmt.pix_mp.field              = V4L2_FIELD_NONE;
    fmt.fmt.pix_mp.num_planes   = src_planes;

    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_FMT, &fmt) < 0) {
        ALOGE("%s::videodev set format failed", __func__);
        return -1;
    }

    /* set src crop info :GSC video dev*/
    crop.type     = fmt.type;
    crop.c.left    = gsc->src_img.x;
    crop.c.top     = gsc->src_img.y;
    crop.c.width  = gsc->src_img.w;
    crop.c.height = gsc->src_img.h;

    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CROP, &crop) < 0) {
        ALOGE("%s::videodev set crop failed", __func__);
        return -1;
    }

    reqbuf.type   = fmt.type;
    reqbuf.memory = V4L2_MEMORY_DMABUF;
    reqbuf.count  = MAX_BUFFERS_GSCALER_OUT;

    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_REQBUFS, &reqbuf) < 0) {
        ALOGE("%s::request buffers failed", __func__);
        return -1;
    }

    Exynos_gsc_Out();

    return 0;
}

int CGscaler::m_gsc_cap_config(void *handle,
    exynos_mpp_img *src_img, exynos_mpp_img *dst_img)
{
    Exynos_gsc_In();

    struct v4l2_format  fmt;
    struct v4l2_crop    crop;
    struct v4l2_requestbuffers reqbuf;
    struct v4l2_subdev_format sd_fmt;
    struct v4l2_subdev_crop   sd_crop;
    unsigned int rotate;
    unsigned int hflip;
    unsigned int vflip;
    bool rgb;

    int32_t      src_color_space;
    int32_t      dst_color_space;
    int32_t      dst_planes;

    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    memcpy(&gsc->src_img, src_img, sizeof(exynos_mpp_img));
    memcpy(&gsc->dst_img, dst_img, sizeof(exynos_mpp_img));
    src_color_space = hal_pixfmt_to_v4l2(src_img->format);
    dst_color_space = hal_pixfmt_to_v4l2(dst_img->format);
    dst_planes = m_gsc_get_plane_count(dst_color_space);
    dst_planes = (dst_planes == -1) ? 1 : dst_planes;
    rgb = gsc_get_yuv_planes(src_color_space) == -1;
    CGscaler::rotateValueHAL2GSC(src_img->rot, &rotate, &hflip, &vflip);

    if (CGscaler::m_gsc_check_src_size(&gsc->src_img.fw,
            &gsc->src_img.fh, &gsc->src_img.x, &gsc->src_img.y,
            &gsc->src_img.w, &gsc->src_img.h, src_color_space,
            (rotate == 90 || rotate == 270)) == false) {
            ALOGE("%s::m_gsc_check_src_size() fail", __func__);
            return -1;
    }

    /*set GSC ctrls */
    struct v4l2_control ctrl;

    ctrl.id = V4L2_CID_ROTATE;
    ctrl.value = rotate;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s:: exynos_v4l2_s_ctrl (V4L2_CID_ROTATE: %d) failed",
            __func__,  rotate);
        return -1;
    }

    ctrl.id = V4L2_CID_HFLIP;
    ctrl.value = vflip;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s:: exynos_v4l2_s_ctrl (V4L2_CID_HFLIP: %d) failed",
            __func__,  vflip);
        return -1;
    }

    ctrl.id = V4L2_CID_VFLIP;
    ctrl.value = hflip;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s:: exynos_v4l2_s_ctrl (V4L2_CID_VFLIP: %d) failed",
            __func__,  hflip);
        return -1;
    }

    ctrl.id = V4L2_CID_CACHEABLE;
    ctrl.value = 1;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s:: exynos_v4l2_s_ctrl (V4L2_CID_CACHEABLE: 1) failed",
            __func__);
        return -1;
    }

    ctrl.id = V4L2_CID_CONTENT_PROTECTION;
    ctrl.value = gsc->src_img.drmMode;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s::exynos_v4l2_s_ctrl(V4L2_CID_CONTENT_PROTECTION) fail",
            __func__);
        return -1;
    }

    ctrl.id = V4L2_CID_CSC_RANGE;
    ctrl.value = gsc->range_full;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CTRL, &ctrl) < 0) {
        ALOGE("%s::exynos_v4l2_s_ctrl(V4L2_CID_CSC_RANGE: %d) fail",
            __func__, gsc->range_full);
        return -1;
    }
      /* set format: source pad of Decon-TV sub-dev*/
    sd_fmt.pad   = DECON_TV_WB_PAD;
    sd_fmt.which = V4L2_SUBDEV_FORMAT_ACTIVE;
    sd_fmt.format.width  = gsc->src_img.w;
    sd_fmt.format.height = gsc->src_img.h;
    sd_fmt.format.code = WB_PATH_FORMAT;
    if (exynos_subdev_s_fmt(gsc->mdev.sink_sd_entity->fd, &sd_fmt) < 0) {
            ALOGE("%s::Decon-TV subdev set format failed", __func__);
            return -1;
    }

    fmt.type  = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    if (!gsc->dst_info.stream_on) {
        /* set src format: GSC video dev*/
	fmt.fmt.pix_mp.width            = gsc->dst_img.fw;
	fmt.fmt.pix_mp.height           = gsc->dst_img.fh;
	fmt.fmt.pix_mp.pixelformat    = dst_color_space;
	fmt.fmt.pix_mp.field              = V4L2_FIELD_NONE;
	fmt.fmt.pix_mp.num_planes   = dst_planes;

	if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_FMT, &fmt) < 0) {
	    ALOGE("%s::videodev set format failed", __func__);
	    return -1;
	}
        gsc->dst_info.buf.buf_idx = 0;
        gsc->dst_info.qbuf_cnt = 0;
    }

    /* set format: sink pad of GSC sub-dev*/
	sd_fmt.pad   = GSCALER_SUBDEV_PAD_SINK;
	sd_fmt.which = V4L2_SUBDEV_FORMAT_ACTIVE;
	sd_fmt.format.width  = gsc->src_img.w;
	sd_fmt.format.height = gsc->src_img.h;
	sd_fmt.format.code = WB_PATH_FORMAT;
	if (exynos_subdev_s_fmt(gsc->mdev.gsc_sd_entity->fd, &sd_fmt) < 0) {
            ALOGE("%s::GSC subdev set format failed", __func__);
	    return -1;
	}

    /* set src crop info :GSC video dev*/
    crop.type     = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    crop.c.left    = gsc->dst_img.x;
    crop.c.top     = gsc->dst_img.y;
    crop.c.width  = gsc->dst_img.w;
    crop.c.height = gsc->dst_img.h;
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CROP, &crop) < 0) {
        ALOGE("%s::videodev set crop failed", __func__);
        return -1;
    }

    /* set crop: src crop of GSC sub-dev*/
    sd_crop.pad   = GSCALER_SUBDEV_PAD_SINK;
    sd_crop.which = V4L2_SUBDEV_FORMAT_ACTIVE;
    sd_crop.rect.left   = 0;
    sd_crop.rect.top    = 0;
    sd_crop.rect.width  = gsc->src_img.w;
    sd_crop.rect.height = gsc->src_img.h;

    if (exynos_subdev_s_crop(gsc->mdev.gsc_sd_entity->fd, &sd_crop) < 0) {
        ALOGE("%s::GSC subdev set crop failed(PAD=%d)", __func__,
        sd_crop.pad);
        return -1;
    }
    reqbuf.type   = fmt.type;
    reqbuf.memory = V4L2_MEMORY_DMABUF;
    reqbuf.count  = MAX_BUFFERS_GSCALER_CAP;

    if (!gsc->dst_info.stream_on) {
        if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_REQBUFS, &reqbuf) < 0) {
	    ALOGE("%s::request buffers failed", __func__);
            return -1;
        }
    }

    Exynos_gsc_Out();

    return 0;
}


void CGscaler::rotateValueHAL2GSC(unsigned int transform,
    unsigned int *rotate, unsigned int *hflip, unsigned int *vflip)
{
    int rotate_flag = transform & 0x7;
    *rotate = 0;
    *hflip = 0;
    *vflip = 0;

    switch (rotate_flag) {
    case HAL_TRANSFORM_ROT_90:
        *rotate = 90;
        break;
    case HAL_TRANSFORM_ROT_180:
        *rotate = 180;
        break;
    case HAL_TRANSFORM_ROT_270:
        *rotate = 270;
        break;
    case HAL_TRANSFORM_FLIP_H | HAL_TRANSFORM_ROT_90:
        *rotate = 90;
        *vflip = 1; /* set vflip to compensate the rot & flip order. */
        break;
    case HAL_TRANSFORM_FLIP_V | HAL_TRANSFORM_ROT_90:
        *rotate = 90;
        *hflip = 1; /* set hflip to compensate the rot & flip order. */
        break;
    case HAL_TRANSFORM_FLIP_H:
        *hflip = 1;
         break;
    case HAL_TRANSFORM_FLIP_V:
        *vflip = 1;
         break;
    default:
        break;
    }
}

int CGscaler::m_gsc_m2m_run(void *handle,
    exynos_mpp_img *src_img, exynos_mpp_img *dst_img)
{
    Exynos_gsc_In();

    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }
    void *addr[3] = {NULL, NULL, NULL};
    int ret = 0;

    addr[0] = (void *)src_img->yaddr;
    addr[1] = (void *)src_img->uaddr;
    addr[2] = (void *)src_img->vaddr;
    ret = exynos_gsc_set_src_addr(handle, addr, src_img->mem_type,
            src_img->acquireFenceFd);
    if (ret < 0) {
        ALOGE("%s::fail: exynos_gsc_set_src_addr[%p %p %p]", __func__,
            addr[0], addr[1], addr[2]);
        return -1;
    }

    addr[0] = (void *)dst_img->yaddr;
    addr[1] = (void *)dst_img->uaddr;
    addr[2] = (void *)dst_img->vaddr;
    ret = exynos_gsc_set_dst_addr(handle, addr, dst_img->mem_type,
            dst_img->acquireFenceFd);
    if (ret < 0) {
        ALOGE("%s::fail: exynos_gsc_set_dst_addr[%p %p %p]", __func__,
            addr[0], addr[1], addr[2]);
        return -1;
    }

    ret = gsc->m_gsc_m2m_run_core(handle);
     if (ret < 0) {
        ALOGE("%s::fail: m_gsc_m2m_run_core", __func__);
        return -1;
    }

    if (src_img->acquireFenceFd >= 0) {
        close(src_img->acquireFenceFd);
        src_img->acquireFenceFd = -1;
    }

    if (dst_img->acquireFenceFd >= 0) {
        close(dst_img->acquireFenceFd);
        dst_img->acquireFenceFd = -1;
    }

    src_img->releaseFenceFd = gsc->src_info.releaseFenceFd;
    dst_img->releaseFenceFd = gsc->dst_info.releaseFenceFd;

    Exynos_gsc_Out();

    return 0;
}

int CGscaler::m_gsc_out_run(void *handle, exynos_mpp_img *src_img)
{
    struct v4l2_plane  planes[NUM_OF_GSC_PLANES];
    struct v4l2_buffer buf;
    int32_t      src_color_space;
    int32_t      src_planes;
    unsigned int i;
    unsigned int plane_size[NUM_OF_GSC_PLANES];
    int ret = 0;
    unsigned int dq_retry_cnt = 0;

    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    /* All buffers have been queued, dequeue one */
    if (gsc->src_info.qbuf_cnt == MAX_BUFFERS_GSCALER_OUT) {
        memset(&buf, 0, sizeof(struct v4l2_buffer));
        for (i = 0; i < NUM_OF_GSC_PLANES; i++)
            memset(&planes[i], 0, sizeof(struct v4l2_plane));

        buf.type     = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
        buf.memory   = V4L2_MEMORY_DMABUF;
        buf.m.planes = planes;

        src_color_space = hal_pixfmt_to_v4l2(gsc->src_img.format);
        src_planes = m_gsc_get_plane_count(src_color_space);
        src_planes = (src_planes == -1) ? 1 : src_planes;
        buf.length   = src_planes;


        do {
            ret = ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_DQBUF, &buf);
            if (ret == -EAGAIN) {
                ALOGE("%s::Retry DQbuf(index=%d)", __func__, buf.index);
                usleep(10000);
                dq_retry_cnt++;
                continue;
            }
            break;
        } while (dq_retry_cnt <= 10);

        if (ret < 0) {
            ALOGE("%s::dq buffer failed (index=%d)", __func__, buf.index);
            return -1;
        }
        gsc->src_info.qbuf_cnt--;
    }

    memset(&buf, 0, sizeof(struct v4l2_buffer));
    for (i = 0; i < NUM_OF_GSC_PLANES; i++)
        memset(&planes[i], 0, sizeof(struct v4l2_plane));

    src_color_space = hal_pixfmt_to_v4l2(gsc->src_img.format);
    src_planes = m_gsc_get_plane_count(src_color_space);
    src_planes = (src_planes == -1) ? 1 : src_planes;

    buf.type     = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    buf.memory   = V4L2_MEMORY_DMABUF;
    buf.flags    = 0;
    buf.length   = src_planes;
    buf.index    = gsc->src_info.buf.buf_idx;
    buf.m.planes = planes;
    buf.reserved = -1;

    gsc->src_info.buf.addr[0] = (void*)src_img->yaddr;
    gsc->src_info.buf.addr[1] = (void*)src_img->uaddr;
    gsc->src_info.buf.addr[2] = (void*)src_img->vaddr;

    if (CGscaler::tmp_get_plane_size(src_color_space, plane_size,
        gsc->src_img.fw,  gsc->src_img.fh, src_planes) != true) {
        ALOGE("%s:get_plane_size:fail", __func__);
        return -1;
    }

    for (i = 0; i < buf.length; i++) {
        buf.m.planes[i].m.fd = (long)gsc->src_info.buf.addr[i];
        buf.m.planes[i].length    = plane_size[i];
        buf.m.planes[i].bytesused = plane_size[i];
    }

    /* Queue the buf */
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_QBUF, &buf) < 0) {
        ALOGE("%s::queue buffer failed (index=%d)(mSrcBufNum=%d)",
                __func__, gsc->src_info.buf.buf_idx,
                MAX_BUFFERS_GSCALER_OUT);
        return -1;
    }
    gsc->src_info.buf.buf_idx++;
    gsc->src_info.buf.buf_idx =
        gsc->src_info.buf.buf_idx % MAX_BUFFERS_GSCALER_OUT;
    gsc->src_info.qbuf_cnt++;

    if (gsc->src_info.stream_on == false) {
        if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_STREAMON, &buf.type) < 0) {
            ALOGE("%s::stream on failed", __func__);
            return -1;
        }
        gsc->src_info.stream_on = true;
    }

    return 0;
}

int CGscaler::m_gsc_cap_run(void *handle, exynos_mpp_img *dst_img)
{
    struct v4l2_plane  planes[NUM_OF_GSC_PLANES];
    struct v4l2_buffer buf;
    int32_t      dst_color_space;
    int32_t      dst_planes;
    unsigned int i;
    unsigned int plane_size[NUM_OF_GSC_PLANES];
    CGscaler* gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    /* All buffers have been queued, dequeue one */
    if (gsc->dst_info.qbuf_cnt == MAX_BUFFERS_GSCALER_CAP) {
        memset(&buf, 0, sizeof(struct v4l2_buffer));
        for (i = 0; i < NUM_OF_GSC_PLANES; i++)
            memset(&planes[i], 0, sizeof(struct v4l2_plane));

        buf.type     = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
        buf.memory   = V4L2_MEMORY_DMABUF;
        buf.m.planes = planes;

        dst_color_space = hal_pixfmt_to_v4l2(gsc->dst_img.format);
        dst_planes = m_gsc_get_plane_count(dst_color_space);
        dst_planes = (dst_planes == -1) ? 1 : dst_planes;
        buf.length   = dst_planes;


        if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_DQBUF, &buf) < 0) {
            ALOGE("%s::dequeue buffer failed (index=%d)(mSrcBufNum=%d)",
                    __func__, gsc->src_info.buf.buf_idx,
                    MAX_BUFFERS_GSCALER_CAP);
            return -1;
        }
        gsc->dst_info.qbuf_cnt--;
    }

    memset(&buf, 0, sizeof(struct v4l2_buffer));
    for (i = 0; i < NUM_OF_GSC_PLANES; i++)
        memset(&planes[i], 0, sizeof(struct v4l2_plane));

    dst_color_space = hal_pixfmt_to_v4l2(gsc->dst_img.format);
    dst_planes = m_gsc_get_plane_count(dst_color_space);
    dst_planes = (dst_planes == -1) ? 1 : dst_planes;

    buf.type     = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
    buf.memory   = V4L2_MEMORY_DMABUF;
    buf.flags    = V4L2_BUF_FLAG_USE_SYNC;
    buf.length   = dst_planes;
    buf.index    = gsc->dst_info.buf.buf_idx;
    buf.m.planes = planes;
    buf.reserved = dst_img->acquireFenceFd;

    gsc->dst_info.buf.addr[0] = (void*)dst_img->yaddr;
    gsc->dst_info.buf.addr[1] = (void*)dst_img->uaddr;
    gsc->dst_info.buf.addr[2] = (void*)dst_img->vaddr;

    if (CGscaler::tmp_get_plane_size(dst_color_space, plane_size,
        gsc->dst_img.fw,  gsc->dst_img.fh, dst_planes) != true) {
        ALOGE("%s:get_plane_size:fail", __func__);
        return -1;
    }

    for (i = 0; i < buf.length; i++) {
        buf.m.planes[i].m.fd = (int)(long)gsc->dst_info.buf.addr[i];
        buf.m.planes[i].length    = plane_size[i];
        buf.m.planes[i].bytesused = plane_size[i];
    }

    /* Queue the buf */
    if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_QBUF, &buf) < 0) {
        ALOGE("%s::queue buffer failed (index=%d)(mDstBufNum=%d)",
                __func__, gsc->dst_info.buf.buf_idx,
                MAX_BUFFERS_GSCALER_CAP);
        return -1;
    }

    gsc->dst_info.buf.buf_idx++;
    gsc->dst_info.buf.buf_idx =
        gsc->dst_info.buf.buf_idx % MAX_BUFFERS_GSCALER_CAP;
    gsc->dst_info.qbuf_cnt++;

    if (gsc->dst_info.stream_on == false) {
        if (ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_STREAMON, &buf.type) < 0) {
            ALOGE("%s::stream on failed", __func__);
            return -1;
        }
        gsc->dst_info.stream_on = true;
    }

    dst_img->releaseFenceFd = buf.reserved;
    return 0;
}

bool CGscaler::tmp_get_plane_size(int V4L2_PIX,
    unsigned int * size, unsigned int width, unsigned int height, int src_planes)
{
    unsigned int frame_ratio = 1;
    int src_bpp    = gsc_get_yuv_bpp(V4L2_PIX);
    unsigned int frame_size = width * height;

    src_planes = (src_planes == -1) ? 1 : src_planes;
    frame_ratio = 8 * (src_planes -1) / (src_bpp - 8);

    switch (src_planes) {
    case 1:
        switch (V4L2_PIX) {
        case V4L2_PIX_FMT_BGR32:
        case V4L2_PIX_FMT_RGB32:
            size[0] = frame_size << 2;
            break;
	case V4L2_PIX_FMT_RGB565:
        case V4L2_PIX_FMT_NV16:
        case V4L2_PIX_FMT_NV61:
        case V4L2_PIX_FMT_YUYV:
        case V4L2_PIX_FMT_UYVY:
        case V4L2_PIX_FMT_VYUY:
        case V4L2_PIX_FMT_YVYU:
            size[0] = frame_size << 1;
            break;
        case V4L2_PIX_FMT_YUV420:
        case V4L2_PIX_FMT_NV12:
        case V4L2_PIX_FMT_NV21:
        case V4L2_PIX_FMT_NV21M:
            size[0] = (frame_size * 3) >> 1;
            break;
        case V4L2_PIX_FMT_YVU420:
            size[0] = frame_size + (ALIGN((width >> 1), 16) * ((height >> 1) * 2));
            break;
        default:
            ALOGE("%s::invalid color type (%x)", __func__, V4L2_PIX);
            return false;
            break;
        }
        size[1] = 0;
        size[2] = 0;
        break;
    case 2:
        size[0] = frame_size;
        size[1] = frame_size / frame_ratio;
        size[2] = 0;
        break;
    case 3:
        size[0] = frame_size;
        size[1] = frame_size / frame_ratio;
        size[2] = frame_size / frame_ratio;
        break;
    default:
        ALOGE("%s::invalid color foarmt", __func__);
        return false;
        break;
    }

    return true;
}

int CGscaler::ConfigMpp(void *handle, exynos_mpp_img *src,
					exynos_mpp_img *dst)
{
    return exynos_gsc_config_exclusive(handle, src, dst);
}

int CGscaler::ConfigBlendMpp(void *handle, exynos_mpp_img *src,
                                           exynos_mpp_img *dst,
                                           SrcBlendInfo  *srcblendinfo)
{
    return exynos_gsc_config_blend_exclusive(handle, src, dst, srcblendinfo);
}

int CGscaler::RunMpp(void *handle, exynos_mpp_img *src,
					exynos_mpp_img *dst)
{
    return exynos_gsc_run_exclusive(handle, src, dst);
}

int CGscaler::StopMpp(void *handle)
{
    return exynos_gsc_stop_exclusive(handle);
}

void CGscaler::DestroyMpp(void *handle)
{
    return exynos_gsc_destroy(handle);
}

int CGscaler::SetCSCProperty(void *handle, unsigned int eqAuto,
		   unsigned int fullRange, unsigned int colorspace)
{
    return exynos_gsc_set_csc_property(handle, eqAuto, fullRange,
					    colorspace);
}

int CGscaler::FreeMpp(void *handle)
{
    return exynos_gsc_free_and_close(handle);
}

int CGscaler::SetInputCrop(void *handle,
        exynos_mpp_img *src_img, exynos_mpp_img __UNUSED__ *dst_img)
{
    struct v4l2_crop crop;
    CGscaler *gsc = GetGscaler(handle);
    if (gsc == NULL) {
        ALOGE("%s::handle == NULL() fail", __func__);
        return -1;
    }

    crop.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    crop.c.left = src_img->x;
    crop.c.top = src_img->y;
    crop.c.width = src_img->w;
    crop.c.height = src_img->h;

    return ioctl(gsc->mdev.gsc_vd_entity->fd, VIDIOC_S_CROP, &crop);
}
