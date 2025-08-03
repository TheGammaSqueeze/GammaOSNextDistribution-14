/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * \file      libscaler-m2m1shot.cpp
 * \brief     source file for Scaler HAL
 * \author    Cho KyongHo <pullip.cho@samsung.com>
 * \date      2014/05/08
 *
 * <b>Revision History: </b>
 * - 2014.05.08 : Cho KyongHo (pullip.cho@samsung.com) \n
 *   Create
 */
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/mman.h>

#include <exynos_scaler.h>

#include "libscaler-common.h"
#include "libscaler-m2m1shot.h"
#include "libscaler-swscaler.h"

using namespace std;

const char *dev_base_name[] = {
    "/dev/m2m1shot_scaler0",
    "/dev/m2m1shot_scaler1",
    "/dev/m2m1shot_scaler2",
    "/dev/m2m1shot_scaler3",
};

struct PixFormat {
    unsigned int pixfmt;
    char planes;
    char bit_pp[3];
};

const static PixFormat g_pixfmt_table[] = {
    {V4L2_PIX_FMT_RGB32,        1, {32, 0, 0}, },
    {V4L2_PIX_FMT_BGR32,        1, {32, 0, 0}, },
    {V4L2_PIX_FMT_RGB565,       1, {16, 0, 0}, },
    {V4L2_PIX_FMT_RGB555X,      1, {16, 0, 0}, },
    {V4L2_PIX_FMT_RGB444,       1, {16, 0, 0}, },
    {V4L2_PIX_FMT_YUYV,         1, {16, 0, 0}, },
    {V4L2_PIX_FMT_YVYU,         1, {16, 0, 0}, },
    {V4L2_PIX_FMT_UYVY,         1, {16, 0, 0}, },
    {V4L2_PIX_FMT_NV16,         1, {16, 0, 0}, },
    {V4L2_PIX_FMT_NV61,         1, {16, 0, 0}, },
    {V4L2_PIX_FMT_YUV420,       1, {12, 0, 0}, },
    {V4L2_PIX_FMT_YVU420,       1, {12, 0, 0}, },
    {V4L2_PIX_FMT_NV12M,        2, {8, 4, 0}, },
    {V4L2_PIX_FMT_NV21M,        2, {8, 4, 0}, },
    {v4l2_fourcc('V', 'M', '1', '2'), 2, {8, 4, 0}, },
    {V4L2_PIX_FMT_NV12,         1, {12, 0, 0}, },
    {V4L2_PIX_FMT_NV21,         1, {12, 0, 0}, },
    {v4l2_fourcc('N', 'M', '2', '1'), 2, {8, 4, 0}, },
    {V4L2_PIX_FMT_YUV420M,      3, {8, 2, 2}, },
    {V4L2_PIX_FMT_YVU420M,      3, {8, 2, 2}, },
    {V4L2_PIX_FMT_NV12M_P010,   2, {16, 8, 0}, },
    {V4L2_PIX_FMT_NV24,         1, {24, 0, 0}, },
    {V4L2_PIX_FMT_NV42,         1, {24, 0, 0}, },
};


CScalerM2M1SHOT::CScalerM2M1SHOT(int devid, int __UNUSED__ drm) : m_iFD(-1)
{
    memset(&m_task, 0, sizeof(m_task));

    if ((devid < 0) || (devid > 3)) { // instance number must be between 0 ~ 3
        SC_LOGE("Invalid device instance ID %d", devid);
        return;
    }

    m_iFD = open(dev_base_name[devid], O_RDWR);
    if (m_iFD < 0) {
        SC_LOGERR("Failed to open '%s'", dev_base_name[devid]);
    } else {
        // default 3 planes not to miss any buffer address
        m_task.buf_out.num_planes = 3;
        m_task.buf_cap.num_planes = 3;
    }
}

CScalerM2M1SHOT::~CScalerM2M1SHOT()
{
    if (m_iFD >= 0)
        close(m_iFD);
}

bool CScalerM2M1SHOT::Run()
{
    int ret;

    if (LibScaler::UnderOne16thScaling(
                m_task.fmt_out.crop.width, m_task.fmt_out.crop.height,
                m_task.fmt_cap.crop.width, m_task.fmt_cap.crop.height,
                m_task.op.rotate))
        return RunSWScaling();

    ret = ioctl(m_iFD, M2M1SHOT_IOC_PROCESS, &m_task);
    if (ret < 0) {
        SC_LOGERR("Failed to process the given M2M1SHOT task");
        return false;
    }

    return true;
}

#define SCALER_EXT_SIZE		512
bool CScalerM2M1SHOT::SetFormat(m2m1shot_pix_format &fmt, m2m1shot_buffer &buf,
        unsigned int width, unsigned int height, unsigned int v4l2_fmt) {
    const PixFormat *pixfmt = NULL;

    fmt.width = width;
    fmt.height = height;
    fmt.fmt = v4l2_fmt;

    for (size_t i = 0; i < ARRSIZE(g_pixfmt_table); i++) {
        if (g_pixfmt_table[i].pixfmt == v4l2_fmt) {
            pixfmt = &g_pixfmt_table[i];
            break;
        }
    }

    if (!pixfmt) {
        SC_LOGE("Format %#x is not supported", v4l2_fmt);
        return false;
    }

    for (int i = 0; i < pixfmt->planes; i++) {
        if (((pixfmt->bit_pp[i] * width) % 8) != 0) {
            SC_LOGE("Plane %d of format %#x must have even width", i, v4l2_fmt);
            return false;
        }
        buf.plane[i].len = (pixfmt->bit_pp[i] * width * height) / 8;
    }

    if (pixfmt->pixfmt == V4L2_PIX_FMT_YVU420) {
        unsigned int y_size = width * height;
        unsigned int c_span = ALIGN(width / 2, 16);
        buf.plane[0].len = y_size + (c_span * height / 2) * 2;
    }

#ifdef SCALER_ALIGN_RESTRICTION
    for (int i = 0; i < pixfmt->planes; i++)
        buf.plane[i].len += (i == 0) ? SCALER_EXT_SIZE : SCALER_EXT_SIZE / 2;
#endif

    buf.num_planes = pixfmt->planes;

    return true;
}

bool CScalerM2M1SHOT::SetCrop(m2m1shot_pix_format &fmt,
        unsigned int l, unsigned int t, unsigned int w, unsigned int h) {
    if (fmt.width <= l) {
        SC_LOGE("crop left %d is larger than image width %d", l, fmt.width);
        return false;
    }
    if (fmt.height <= t) {
        SC_LOGE("crop top %d is larger than image height %d", t, fmt.height);
        return false;
    }
    if (fmt.width < (l + w)) {
        SC_LOGE("crop width %d@%d  exceeds image width %d", w, l, fmt.width);
        return false;
    }
    if (fmt.height < (t + h)) {
        SC_LOGE("crop height %d@%d  exceeds image height %d", h, t, fmt.height);
        return false;
    }

    fmt.crop.left = l;
    fmt.crop.top = t;
    fmt.crop.width = w;
    fmt.crop.height = h;

    return true;
}

bool CScalerM2M1SHOT::SetAddr(
                m2m1shot_buffer &buf, void *addr[SC_NUM_OF_PLANES], int mem_type) {
    if (mem_type == V4L2_MEMORY_DMABUF) {
        buf.type = M2M1SHOT_BUFFER_DMABUF;
        for (int i = 0; i < buf.num_planes; i++)
            buf.plane[i].fd = static_cast<__s32>(reinterpret_cast<long>(addr[i]));
    } else if (mem_type == V4L2_MEMORY_USERPTR) {
        buf.type = M2M1SHOT_BUFFER_USERPTR;
        for (int i = 0; i < buf.num_planes; i++)
            buf.plane[i].userptr = reinterpret_cast<unsigned long>(addr[i]);
    } else {
        SC_LOGE("Unknown buffer type %d", mem_type);
        return false;
    }

    return true;
}

bool CScalerM2M1SHOT::SetRotate(int rot, int hflip, int vflip) {
    if ((rot % 90) != 0) {
        SC_LOGE("Rotation degree %d must be multiple of 90", rot);
        return false;
    }

    rot = rot % 360;
    if (rot < 0)
        rot = 360 + rot;

    m_task.op.rotate = rot;
    m_task.op.op &= ~(M2M1SHOT_OP_FLIP_HORI | M2M1SHOT_OP_FLIP_VIRT);
    if (hflip)
        m_task.op.op |= M2M1SHOT_OP_FLIP_HORI;
    if (vflip)
        m_task.op.op |= M2M1SHOT_OP_FLIP_VIRT;

    return true;
}

static bool GetBuffer(m2m1shot_buffer &buf, char *addr[])
{
    for (int i = 0; i < buf.num_planes; i++) {
            if (buf.type == M2M1SHOT_BUFFER_DMABUF) {
                addr[i] = reinterpret_cast<char *>(mmap(NULL, buf.plane[i].len,
                                 PROT_READ | PROT_WRITE, MAP_SHARED,
                                 buf.plane[i].fd, 0));
                if (addr[i] == MAP_FAILED) {
                    SC_LOGE("Failed to map FD %d", buf.plane[i].fd);
                    while (i-- > 0)
                        munmap(addr[i], buf.plane[i].len);
                    return false;
                }
            } else {
                addr[i] = reinterpret_cast<char *>(buf.plane[i].userptr);
            }
    }

    return true;
}

static void PutBuffer(m2m1shot_buffer &buf, char *addr[])
{
    for (int i = 0; i < buf.num_planes; i++) {
        if (buf.type == M2M1SHOT_BUFFER_DMABUF)
            munmap(addr[i], buf.plane[i].len);
    }
}

bool CScalerM2M1SHOT::RunSWScaling()
{
    if (m_task.fmt_cap.fmt != m_task.fmt_out.fmt) {
        SC_LOGE("Source and target image format must be the same");
        return false;
    }

    if (m_task.op.rotate != 0) {
        SC_LOGE("Rotation is not allowed for S/W Scaling");
        return false;
    }

    SC_LOGI("Running S/W Scaler: %dx%d -> %dx%d",
            m_task.fmt_out.crop.width, m_task.fmt_out.crop.height,
            m_task.fmt_cap.crop.width, m_task.fmt_cap.crop.height);

    CScalerSW *swsc;
    char *src[3], *dst[3];

    switch (m_task.fmt_cap.fmt) {
        case V4L2_PIX_FMT_YUYV:
        case V4L2_PIX_FMT_YVYU:
            if (!GetBuffer(m_task.buf_out, src))
                return false;

            if (!GetBuffer(m_task.buf_cap, dst)) {
                PutBuffer(m_task.buf_out, src);
                return false;
            }

            swsc = new CScalerSW_YUYV(src[0], dst[0]);
            break;
        case V4L2_PIX_FMT_NV12M:
        case V4L2_PIX_FMT_NV21M:
        case V4L2_PIX_FMT_NV12:
        case V4L2_PIX_FMT_NV21:
            if (!GetBuffer(m_task.buf_out, src))
                return false;

            if (!GetBuffer(m_task.buf_cap, dst)) {
                PutBuffer(m_task.buf_out, src);
                return false;
            }

            if (m_task.buf_out.num_planes == 1)
                src[1] = src[0] + m_task.fmt_out.width * m_task.fmt_out.height;

            if (m_task.buf_cap.num_planes == 1)
                dst[1] = dst[0] + m_task.fmt_cap.width * m_task.fmt_cap.height;

            swsc = new CScalerSW_NV12(src[0], src[1], dst[0], dst[1]);
            break;
        case V4L2_PIX_FMT_UYVY: // TODO: UYVY is not implemented yet.
        default:
            SC_LOGE("Format %x is not supported", m_task.fmt_out.fmt);
            return false;
    }

    if (swsc == NULL) {
        SC_LOGE("Failed to allocate SW Scaler");
        PutBuffer(m_task.buf_out, src);
        PutBuffer(m_task.buf_cap, dst);
        return false;
    }

    swsc->SetSrcRect(m_task.fmt_out.crop.left, m_task.fmt_out.crop.top,
            m_task.fmt_out.crop.width, m_task.fmt_out.crop.height,
            m_task.fmt_out.width);

    swsc->SetDstRect(m_task.fmt_cap.crop.left, m_task.fmt_cap.crop.top,
            m_task.fmt_cap.crop.width, m_task.fmt_cap.crop.height,
            m_task.fmt_cap.width);

    bool ret = swsc->Scale();

    delete swsc;

    PutBuffer(m_task.buf_out, src);
    PutBuffer(m_task.buf_cap, dst);

    return ret;
}
