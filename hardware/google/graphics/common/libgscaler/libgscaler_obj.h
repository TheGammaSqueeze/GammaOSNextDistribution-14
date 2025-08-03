/*
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

#ifndef LIBGSCALER_OBJ_H_
#define LIBGSCALER_OBJ_H_

#ifdef __cplusplus
extern "C" {
#endif

//#define LOG_NDEBUG 0
#define LOG_TAG "libexynosgscaler"
#include <log/log.h>

#include <sys/types.h>
#include <sys/ioctl.h>
#include <linux/videodev2.h>
#include <fcntl.h>
#include <stdbool.h>
#include <string.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <system/graphics.h>
#include "exynos_gscaler.h"

#include "exynos_format.h"
#include <hardware/exynos/LibMpp.h>

#include "exynos_scaler.h"

#define NUM_OF_GSC_PLANES           (3)
#define MAX_BUFFERS_GSCALER_OUT     (10)
#define MAX_BUFFERS_GSCALER_CAP     (1)
#define GSCALER_SUBDEV_PAD_SINK     (0)
#define GSCALER_SUBDEV_PAD_SOURCE   (1)
#define MIXER_V_SUBDEV_PAD_SINK     (0)
#define MIXER_V_SUBDEV_PAD_SOURCE   (3)
#define FIMD_SUBDEV_PAD_SINK        (0)
#define DECON_TV_WB_PAD             (0)
#define MAX_BUFFERS                 (6)

#define NUM_OF_GSC_HW               (4)
#define NODE_NUM_GSC_0              (23)
#define NODE_NUM_GSC_1              (26)
#define NODE_NUM_GSC_2              (29)
#define NODE_NUM_GSC_3              (32)

#define PFX_NODE_GSC                "/dev/video"
#define PFX_NODE_MEDIADEV         "/dev/media"
#define PFX_MXR_ENTITY              "s5p-mixer%d"
#define PFX_FIMD_ENTITY             "s3c-fb-window%d"
#if defined(USES_DT)
#define PFX_GSC_VIDEODEV_ENTITY0  "13c00000.gsc.output"
#define PFX_GSC_VIDEODEV_ENTITY1  "13c10000.gsc.output"
#define PFX_GSC_VIDEODEV_ENTITY2  "13c20000.gsc.output"
#else
#define PFX_GSC_VIDEODEV_ENTITY   "exynos-gsc.%d.output"
#endif
#define PFX_GSC_CAPTURE_ENTITY	  "13c20000.gsc.capture"
#define PFX_GSC_SUBDEV_ENTITY     "exynos-gsc-sd.%d"
#define PFX_SUB_DEV		"/dev/v4l-subdev%d"
#define GSC_WB_SD_NAME		"gsc-wb-sd"
#define DEX_WB_SD_NAME		"dex-wb-sd"
#define GSC_VD_PAD_SOURCE	0
#define GSC_SD_PAD_SINK	0
#define GSC_SD_PAD_SOURCE	1
#define GSC_OUT_PAD_SINK	0
#define WB_PATH_FORMAT		0x100D;

#define GSC_MIN_SRC_W_SIZE (64)
#define GSC_MIN_SRC_H_SIZE (32)
#define GSC_MIN_DST_W_SIZE (32)
#define GSC_MIN_DST_H_SIZE (16)

#define MAX_GSC_WAITING_TIME_FOR_TRYLOCK (16000) // 16msec
#define GSC_WAITING_TIME_FOR_TRYLOCK      (8000) //  8msec

typedef struct GscalerInfo {
    unsigned int width;
    unsigned int height;
    unsigned int crop_left;
    unsigned int crop_top;
    unsigned int crop_width;
    unsigned int crop_height;
    unsigned int v4l2_colorformat;
    unsigned int mode_drm;
    unsigned int cacheable;
    int rotation;
    int flip_horizontal;
    int flip_vertical;
    int qbuf_cnt;
    int acquireFenceFd;
    int releaseFenceFd;
    bool stream_on;
    bool dirty;
    struct v4l2_format format;
    struct v4l2_crop crop;
    struct Buffer_Info {
        enum v4l2_memory mem_type;
        enum v4l2_buf_type buf_type;
        void *addr[NUM_OF_GSC_PLANES];
        struct v4l2_plane planes[NUM_OF_GSC_PLANES];
        bool buffer_queued;
        struct v4l2_buffer buffer;
        int buf_idx;
    }buf;
}GscInfo;

struct MediaDevice {
    struct media_device *media0;
    struct media_device *media1;
    struct media_entity *gsc_sd_entity;
    struct media_entity *gsc_vd_entity;
    struct media_entity *sink_sd_entity;
};

class CGscaler : public LibMpp {
public:
    GscInfo src_info;
    GscInfo dst_info;
    exynos_mpp_img src_img;
    exynos_mpp_img dst_img;
    MediaDevice mdev;
    int out_mode;
    int gsc_id;
    bool allow_drm;
    bool protection_enabled;
    int gsc_fd;
    int mode;
    unsigned int eq_auto;           /* 0: user, 1: auto */
    unsigned int range_full;        /* 0: narrow, 1: full */
    unsigned int v4l2_colorspace;   /* 1: 601, 3: 709, see csc.h or videodev2.h */
    void *scaler;

    void __InitMembers(int __mode, int __out_mode, int __gsc_id,int __allow_drm)
    {
        memset(&mdev, 0, sizeof(mdev));
        scaler = NULL;

        mode = __mode;
        out_mode = __out_mode;
        gsc_id = __gsc_id;
        allow_drm = __allow_drm;
    }

    CGscaler(int __mode)
    {
        memset(&src_info, 0, sizeof(GscInfo));
        memset(&dst_info, 0, sizeof(GscInfo));
        memset(&src_img, 0, sizeof(exynos_mpp_img));
        memset(&dst_img, 0, sizeof(exynos_mpp_img));
        mode = __mode;
        protection_enabled = false;
        gsc_fd = -1;
        src_info.buf.buf_type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
        dst_info.buf.buf_type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
        eq_auto = 0;            /* user mode */
        range_full = 0;         /* narrow */
        v4l2_colorspace = 1;    /* SMPTE170M (601) */
        __InitMembers(__mode, 0, 0, 0);
    }
    CGscaler(int __mode, int __out_mode, int __gsc_id, int __allow_drm)
    {
        memset(&src_info, 0, sizeof(GscInfo));
        memset(&dst_info, 0, sizeof(GscInfo));
        memset(&src_img, 0, sizeof(exynos_mpp_img));
        memset(&dst_img, 0, sizeof(exynos_mpp_img));
        protection_enabled = false;
        gsc_fd = -1;
        src_info.buf.buf_type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
        dst_info.buf.buf_type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;
        eq_auto = 0;            /* user mode */
        range_full = 0;         /* narrow */
        v4l2_colorspace = 1;    /* SMPTE170M (601) */
        __InitMembers(__mode, __out_mode, __gsc_id, __allow_drm);
    }

    ~CGscaler()
    {
        ALOGD("%s", __func__);
    }
    virtual int ConfigMpp(void *handle, exynos_mpp_img *src,
			   exynos_mpp_img *dst);
     virtual int ConfigBlendMpp(void *handle, exynos_mpp_img *src,
                                              exynos_mpp_img *dst,
                                              SrcBlendInfo  *srcblendinfo);
    virtual int RunMpp(void *handle, exynos_mpp_img *src,
			   exynos_mpp_img *dst);
    virtual int StopMpp(void *handle);
    virtual void DestroyMpp(void *handle);
    virtual int SetCSCProperty(void *handle, unsigned int eqAuto,
		   unsigned int fullRange, unsigned int colorspace);
    virtual int FreeMpp(void *handle);
    virtual int SetInputCrop(void *handle, exynos_mpp_img *src, exynos_mpp_img *dst);
    bool m_gsc_find_and_create(void *handle);
    bool m_gsc_out_destroy(void *handle);
    bool m_gsc_cap_destroy(void *handle);
    bool m_gsc_m2m_destroy(void *handle);
    int m_gsc_m2m_create(int dev);
    int m_gsc_output_create(void *handle, int dev_num, int out_mode);
    int m_gsc_capture_create(void *handle, int dev_num, int out_mode);
    int m_gsc_out_stop(void *handle);
    int m_gsc_cap_stop(void *handle);
    int m_gsc_m2m_stop(void *handle);
    int m_gsc_m2m_run_core(void *handle);
    int m_gsc_m2m_wait_frame_done(void *handle);
    int m_gsc_m2m_config(void *handle,
        exynos_mpp_img *src_img, exynos_mpp_img *dst_img);
    int m_gsc_out_config(void *handle,
        exynos_mpp_img *src_img, exynos_mpp_img *dst_img);
    int m_gsc_cap_config(void *handle,
        exynos_mpp_img *src_img, exynos_mpp_img *dst_img);
    int m_gsc_m2m_run(void *handle,
        exynos_mpp_img *src_img, exynos_mpp_img *dst_img);
    int m_gsc_out_run(void *handle, exynos_mpp_img *src_img);
    int m_gsc_cap_run(void *handle, exynos_mpp_img *dst_img);
    static bool m_gsc_set_format(int fd, GscInfo *info);
    static unsigned int m_gsc_get_plane_count(int v4l_pixel_format);
    static bool m_gsc_set_addr(int fd, GscInfo *info);
    static unsigned int m_gsc_get_plane_size(
        unsigned int *plane_size, unsigned int width,
        unsigned int height, int v4l_pixel_format);
    static bool m_gsc_check_src_size(unsigned int *w, unsigned int *h,
        unsigned int *crop_x, unsigned int *crop_y,
        unsigned int *crop_w, unsigned int *crop_h,
        int v4l2_colorformat, bool rotation);
    static bool m_gsc_check_dst_size(unsigned int *w, unsigned int *h,
        unsigned int *crop_x, unsigned int *crop_y,
        unsigned int *crop_w, unsigned int *crop_h,
        int v4l2_colorformat, int rotation);
    static int m_gsc_multiple_of_n(int number, int N);
    static void rotateValueHAL2GSC(unsigned int transform,
        unsigned int *rotate, unsigned int *hflip, unsigned int *vflip);
    static bool tmp_get_plane_size(int V4L2_PIX,
        unsigned int * size, unsigned int width, unsigned int height, int src_planes);
};

inline CGscaler *GetGscaler(void* handle)
{
    if (handle == NULL) {
        ALOGE("%s::NULL Scaler handle", __func__);
        return NULL;
    }

    CGscaler *gsc = reinterpret_cast<CGscaler *>(handle);

    return gsc;
}
#ifdef __cplusplus
}
#endif

#endif
