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
 * \file      libscaler-v4l2.cpp
 * \brief     source file for Scaler HAL
 * \author    Cho KyongHo <pullip.cho@samsung.com>
 * \date      2014/05/12
 *
 * <b>Revision History: </b>
 * - 2014.05.12 : Cho KyongHo (pullip.cho@samsung.com) \n
 *   Create
 */

#include <cstring>
#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/mman.h>

#include "libscaler-v4l2.h"
#include "libscaler-swscaler.h"


#define V4L2_CID_EXYNOS_BASE            (V4L2_CTRL_CLASS_USER | 0x2000)
#define V4L2_CID_CSC_EQ_MODE            (V4L2_CID_EXYNOS_BASE + 100)
#define V4L2_CID_CSC_EQ                 (V4L2_CID_EXYNOS_BASE + 101)
#define V4L2_CID_CSC_RANGE              (V4L2_CID_EXYNOS_BASE + 102)
#define V4L2_CID_CONTENT_PROTECTION     (V4L2_CID_EXYNOS_BASE + 201)

void CScalerV4L2::Initialize(int instance)
{
    snprintf(m_cszNode, SC_MAX_NODENAME, SC_DEV_NODE "%d", SC_NODE(instance));

    m_fdScaler = open(m_cszNode, O_RDWR);
    if (m_fdScaler < 0) {
        SC_LOGERR("Failed to open '%s'", m_cszNode);
        return;
    }

    m_fdValidate = -m_fdScaler;
}

CScalerV4L2::CScalerV4L2(int instance, int allow_drm)
{
    m_fdScaler = -1;
    m_iInstance = instance;
    m_nRotDegree = 0;
    m_fStatus = 0;
    m_filter = 0;

    memset(&m_frmSrc, 0, sizeof(m_frmSrc));
    memset(&m_frmDst, 0, sizeof(m_frmDst));

    m_frmSrc.fdAcquireFence = -1;
    m_frmDst.fdAcquireFence = -1;

    m_frmSrc.name = "output";
    m_frmDst.name = "capture";

    m_frmSrc.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    m_frmDst.type = V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE;

    m_frameRate = 0;

    Initialize(instance);

    if(Valid()) {
        if (allow_drm)
            SetFlag(m_fStatus, SCF_ALLOW_DRM);
        SC_LOGD("Successfully opened '%s'; returned fd %d; drmmode %s",
                m_cszNode, m_fdScaler, allow_drm ? "enabled" : "disabled");
    }
}

CScalerV4L2::~CScalerV4L2()
{
    if (m_fdScaler >= 0)
        close(m_fdScaler);

    m_fdScaler = -1;
}

bool CScalerV4L2::Stop()
{
    if (!ResetDevice(m_frmSrc)) {
        SC_LOGE("Failed to stop Scaler for the output frame");
        return false;
    }

    if (!ResetDevice(m_frmDst)) {
        SC_LOGE("Failed to stop Scaler for the cature frame");
        return false;
    }

    return true;
}

bool CScalerV4L2::Run()
{
    if (LibScaler::UnderOne16thScaling(
                m_frmSrc.crop.width, m_frmSrc.crop.height,
                m_frmDst.crop.width, m_frmDst.crop.height,
                m_nRotDegree))
        return RunSWScaling();

    if (!DevSetCtrl())
        return false;

    if (!DevSetFormat())
        return false;

    if (!ReqBufs())
        return false;

    if (!StreamOn())
        return false;

    if (!QBuf()) {
        Stop();
        return false;
    }

    return DQBuf();
}

bool CScalerV4L2::SetCtrl()
{
    struct v4l2_control ctrl;

    if (TestFlag(m_fStatus, SCF_DRM_FRESH)) {
        if (!Stop())
            return false;

        ctrl.id = V4L2_CID_CONTENT_PROTECTION;
        ctrl.value = TestFlag(m_fStatus, SCF_DRM);
        if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
            SC_LOGERR("Failed configure V4L2_CID_CONTENT_PROTECTION to %d", TestFlag(m_fStatus, SCF_DRM));
            return false;
        }

        ClearFlag(m_fStatus, SCF_DRM_FRESH);
    } else {
        SC_LOGD("Skipping DRM configuration");
    }

    if (TestFlag(m_fStatus, SCF_ROTATION_FRESH)) {
        if (!Stop())
            return false;

        ctrl.id = V4L2_CID_ROTATE;
        ctrl.value = m_nRotDegree;
        if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
            SC_LOGERR("Failed V4L2_CID_ROTATE with degree %d", m_nRotDegree);
            return false;
        }

        ctrl.id = V4L2_CID_VFLIP;
        ctrl.value = TestFlag(m_fStatus, SCF_HFLIP);
        if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
            SC_LOGERR("Failed V4L2_CID_VFLIP - %d", TestFlag(m_fStatus, SCF_VFLIP));
            return false;
        }

        ctrl.id = V4L2_CID_HFLIP;
        ctrl.value = TestFlag(m_fStatus, SCF_VFLIP);
        if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
            SC_LOGERR("Failed V4L2_CID_HFLIP - %d", TestFlag(m_fStatus, SCF_HFLIP));
            return false;
        }

        SC_LOGD("Successfully set CID_ROTATE(%d), CID_VFLIP(%d) and CID_HFLIP(%d)",
                m_nRotDegree, TestFlag(m_fStatus, SCF_VFLIP), TestFlag(m_fStatus, SCF_HFLIP));
        ClearFlag(m_fStatus, SCF_ROTATION_FRESH);
    } else {
        SC_LOGD("Skipping rotation and flip setting due to no change");
    }

    if (m_filter > 0) {
        if (!Stop())
            return false;

        ctrl.id = LIBSC_V4L2_CID_DNOISE_FT;
        ctrl.value = m_filter;
        if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
            SC_LOGERR("Failed LIBSC_V4L2_CID_DNOISE_FT to %d", m_filter);
            return false;
        }
    }

    if (TestFlag(m_fStatus, SCF_CSC_FRESH)) {
        if (!Stop())
            return false;

        ctrl.id = V4L2_CID_CSC_RANGE;
        ctrl.value = TestFlag(m_fStatus, SCF_CSC_WIDE) ? 1 : 0;
        if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
            SC_LOGERR("Failed V4L2_CID_CSC_RANGE to %d", TestFlag(m_fStatus, SCF_CSC_WIDE));
            return false;
        }

        ctrl.id = V4L2_CID_CSC_EQ;
        ctrl.value = m_colorspace;
        if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
            SC_LOGERR("Failed V4L2_CID_CSC_EQ to %d", m_colorspace);
        }
        ClearFlag(m_fStatus, SCF_CSC_FRESH);
    }

    /* This is optional, so we don't return failure. */
    if (TestFlag(m_fStatus, SCF_FRAMERATE)) {
        if (!Stop())
            return false;

        ctrl.id = SC_CID_FRAMERATE;
        ctrl.value = m_frameRate;
        if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
            SC_LOGD("Failed SC_CID_FRAMERATE to %d", m_frameRate);
        }
        ClearFlag(m_fStatus, SCF_FRAMERATE);
    }

    return true;
}

bool CScalerV4L2::DevSetCtrl()
{
    return SetCtrl();
}

bool CScalerV4L2::ResetDevice(FrameInfo &frm)
{
    DQBuf(frm);

    if (TestFlag(frm.flags, SCFF_STREAMING)) {
        if (ioctl(m_fdScaler, VIDIOC_STREAMOFF, &frm.type) < 0) {
            SC_LOGERR("Failed STREAMOFF for the %s", frm.name);
        }
        ClearFlag(frm.flags, SCFF_STREAMING);
    }

    SC_LOGD("VIDIC_STREAMOFF is successful for the %s", frm.name);

    if (TestFlag(frm.flags, SCFF_REQBUFS)) {
        v4l2_requestbuffers reqbufs;
        memset(&reqbufs, 0, sizeof(reqbufs));
        reqbufs.type = frm.type;
        reqbufs.memory = frm.memory;
        if (ioctl(m_fdScaler, VIDIOC_REQBUFS, &reqbufs) < 0 ) {
            SC_LOGERR("Failed to REQBUFS(0) for the %s", frm.name);
        }

        ClearFlag(frm.flags, SCFF_REQBUFS);
    }

    SC_LOGD("VIDIC_REQBUFS(0) is successful for the %s", frm.name);

    return true;
}

bool CScalerV4L2::DevSetFormat(FrameInfo &frm)
{

    if (!TestFlag(frm.flags, SCFF_BUF_FRESH)) {
        SC_LOGD("Skipping S_FMT for the %s since it is already done", frm.name);
        return true;
    }

    if (!ResetDevice(frm)) {
        SC_LOGE("Failed to VIDIOC_S_FMT for the %s", frm.name);
        return false;
    }

    v4l2_format fmt;
    memset(&fmt, 0, sizeof(fmt));
    fmt.type = frm.type;
    fmt.fmt.pix_mp.pixelformat = frm.color_format;
    fmt.fmt.pix_mp.width  = frm.width;
    fmt.fmt.pix_mp.height = frm.height;

    if (TestFlag(frm.flags, SCFF_PREMULTIPLIED)) {
#ifdef SCALER_USE_PREMUL_FMT
        fmt.fmt.pix_mp.flags = V4L2_PIX_FMT_FLAG_PREMUL_ALPHA;
#else
        fmt.fmt.pix_mp.reserved[1] = SC_V4L2_FMT_PREMULTI_FLAG;
#endif
    }

    if (ioctl(m_fdScaler, VIDIOC_S_FMT, &fmt) < 0) {
        SC_LOGERR("Failed S_FMT(fmt: %d, w:%d, h:%d) for the %s",
                fmt.fmt.pix_mp.pixelformat, fmt.fmt.pix_mp.width, fmt.fmt.pix_mp.height,
                frm.name);
        return false;
    }

    // returned fmt.fmt.pix_mp.num_planes and fmt.fmt.pix_mp.plane_fmt[i].sizeimage
    frm.out_num_planes = fmt.fmt.pix_mp.num_planes;

    for (int i = 0; i < frm.out_num_planes; i++)
        frm.out_plane_size[i] = fmt.fmt.pix_mp.plane_fmt[i].sizeimage;

    v4l2_crop crop;
    crop.type = frm.type;
    crop.c = frm.crop;

    if (ioctl(m_fdScaler, VIDIOC_S_CROP, &crop) < 0) {
        SC_LOGERR("Failed S_CROP(fmt: %d, l:%d, t:%d, w:%d, h:%d) for the %s",
                crop.type, crop.c.left, crop.c.top, crop.c.width, crop.c.height,
                frm.name);
        return false;
    }

    if (frm.out_num_planes > SC_MAX_PLANES) {
        SC_LOGE("Number of planes exceeds %d of %s", frm.out_num_planes, frm.name);
        return false;
    }

    ClearFlag(frm.flags, SCFF_BUF_FRESH);

    SC_LOGD("Successfully S_FMT and S_CROP for the %s", frm.name);

    return true;
}

bool CScalerV4L2::DevSetFormat()
{
    if (!DevSetFormat(m_frmSrc))
        return false;

    return DevSetFormat(m_frmDst);
}

bool CScalerV4L2::QBuf(FrameInfo &frm, int *pfdReleaseFence)
{
    v4l2_buffer buffer;
    v4l2_plane planes[SC_MAX_PLANES];

    if (!TestFlag(frm.flags, SCFF_REQBUFS)) {
        SC_LOGE("Trying to QBUF without REQBUFS for %s is not allowed",
                frm.name);
        return false;
    }

    if (!DQBuf(frm))
        return false;

    memset(&buffer, 0, sizeof(buffer));
    memset(&planes, 0, sizeof(planes));

    buffer.type   = frm.type;
    buffer.memory = frm.memory;
    buffer.index  = 0;
    buffer.length = frm.out_num_planes;

    if (pfdReleaseFence) {
        buffer.flags    = V4L2_BUF_FLAG_USE_SYNC;
        buffer.reserved = frm.fdAcquireFence;
    }

    buffer.m.planes = planes;
    for (unsigned long i = 0; i < buffer.length; i++) {
        planes[i].length = frm.out_plane_size[i];
        if (V4L2_TYPE_IS_OUTPUT(buffer.type))
            planes[i].bytesused = planes[i].length;
        if (buffer.memory == V4L2_MEMORY_DMABUF)
            planes[i].m.fd = static_cast<__s32>(reinterpret_cast<long>(frm.addr[i]));
        else
            planes[i].m.userptr = reinterpret_cast<unsigned long>(frm.addr[i]);
    }


    if (ioctl(m_fdScaler, VIDIOC_QBUF, &buffer) < 0) {
        SC_LOGERR("Failed to QBUF for the %s", frm.name);
        return false;
    }

    SetFlag(frm.flags, SCFF_QBUF);

    if (pfdReleaseFence) {
        if (frm.fdAcquireFence >= 0)
            close(frm.fdAcquireFence);
        frm.fdAcquireFence = -1;

        *pfdReleaseFence = static_cast<int>(buffer.reserved);
    }

    SC_LOGD("Successfully QBUF for the %s", frm.name);

    return true;
}

bool CScalerV4L2::ReqBufs(FrameInfo &frm)
{
    v4l2_requestbuffers reqbufs;

    if (TestFlag(frm.flags, SCFF_REQBUFS)) {
        SC_LOGD("Skipping REQBUFS for the %s since it is already done", frm.name);
        return true;
    }

    memset(&reqbufs, 0, sizeof(reqbufs));

    reqbufs.type    = frm.type;
    reqbufs.memory  = frm.memory;
    reqbufs.count   = 1;

    if (ioctl(m_fdScaler, VIDIOC_REQBUFS, &reqbufs) < 0) {
        SC_LOGERR("Failed to REQBUFS for the %s", frm.name);
        return false;
    }

    SetFlag(frm.flags, SCFF_REQBUFS);

    SC_LOGD("Successfully REQBUFS for the %s", frm.name);

    return true;
}

bool CScalerV4L2::SetRotate(int rot, int flip_h, int flip_v)
{
    if ((rot % 90) != 0) {
        SC_LOGE("Rotation of %d degree is not supported", rot);
        return false;
    }

    SetRotDegree(rot);

    if (flip_h)
        SetFlag(m_fStatus, SCF_VFLIP);
    else
        ClearFlag(m_fStatus, SCF_VFLIP);

    if (flip_v)
        SetFlag(m_fStatus, SCF_HFLIP);
    else
        ClearFlag(m_fStatus, SCF_HFLIP);

    SetFlag(m_fStatus, SCF_ROTATION_FRESH);

    return true;
}

bool CScalerV4L2::StreamOn(FrameInfo &frm)
{
    if (!TestFlag(frm.flags, SCFF_REQBUFS)) {
        SC_LOGE("Trying to STREAMON without REQBUFS for %s is not allowed",
                frm.name);
        return false;
    }

    if (!TestFlag(frm.flags, SCFF_STREAMING)) {
        if (ioctl(m_fdScaler, VIDIOC_STREAMON, &frm.type) < 0 ) {
            SC_LOGERR("Failed StreamOn for the %s", frm.name);
            return false;
        }

        SetFlag(frm.flags, SCFF_STREAMING);

        SC_LOGD("Successfully VIDIOC_STREAMON for the %s", frm.name);
    }

    return true;
}

bool CScalerV4L2::DQBuf(FrameInfo &frm)
{
    if (!TestFlag(frm.flags, SCFF_QBUF))
        return true;

    v4l2_buffer buffer;
    v4l2_plane plane[SC_NUM_OF_PLANES];

    memset(&buffer, 0, sizeof(buffer));

    buffer.type = frm.type;
    buffer.memory = frm.memory;

    if (V4L2_TYPE_IS_MULTIPLANAR(buffer.type)) {
        memset(plane, 0, sizeof(plane));

        buffer.length = frm.out_num_planes;
        buffer.m.planes = plane;
    }

    ClearFlag(frm.flags, SCFF_QBUF);

    if (ioctl(m_fdScaler, VIDIOC_DQBUF, &buffer) < 0 ) {
        SC_LOGERR("Failed to DQBuf the %s", frm.name);
        return false;
    }

    if (buffer.flags & V4L2_BUF_FLAG_ERROR) {
        SC_LOGE("Error occurred while processing streaming data");
        return false;
    }

    SC_LOGD("Successfully VIDIOC_DQBUF for the %s", frm.name);

    return true;
}

static bool GetBuffer(CScalerV4L2::FrameInfo &frm, char *addr[])
{
    for (int i = 0; i < frm.out_num_planes; i++) {
        if (frm.memory == V4L2_MEMORY_DMABUF) {
            addr[i] = reinterpret_cast<char *>(mmap(NULL, frm.out_plane_size[i],
                        PROT_READ | PROT_WRITE, MAP_SHARED,
                        static_cast<int>(reinterpret_cast<long>(frm.addr[i])), 0));
            if (addr[i] == MAP_FAILED) {
                SC_LOGE("Failed to map FD %ld", reinterpret_cast<long>(frm.addr[i]));
                while (i-- > 0)
                    munmap(addr[i], frm.out_plane_size[i]);
                return false;
            }
        } else {
            addr[i] = reinterpret_cast<char *>(frm.addr[i]);
        }
    }

    return true;
}

static void PutBuffer(CScalerV4L2::FrameInfo &frm, char *addr[])
{
    for (int i = 0; i < frm.out_num_planes; i++) {
        if (frm.memory == V4L2_MEMORY_DMABUF) {
            munmap(addr[i], frm.out_plane_size[i]);
        }
    }
}

bool CScalerV4L2::RunSWScaling()
{
    if (m_frmSrc.color_format != m_frmDst.color_format) {
        SC_LOGE("Source and target image format must be the same");
        return false;
    }

    if (m_nRotDegree != 0) {
        SC_LOGE("Rotation is not allowed for S/W Scaling");
        return false;
    }

    SC_LOGI("Running S/W Scaler: %dx%d -> %dx%d",
            m_frmSrc.crop.width, m_frmSrc.crop.height,
            m_frmDst.crop.width, m_frmDst.crop.height);

    CScalerSW *swsc;
    char *src[3], *dst[3];

    switch (m_frmSrc.color_format) {
        case V4L2_PIX_FMT_YUYV:
        case V4L2_PIX_FMT_YVYU:
            m_frmSrc.out_num_planes = 1;
            m_frmSrc.out_plane_size[0] = m_frmSrc.width * m_frmSrc.height * 2;
            m_frmDst.out_num_planes = 1;
            m_frmDst.out_plane_size[0] = m_frmDst.width * m_frmDst.height * 2;

            if (!GetBuffer(m_frmSrc, src))
                return false;

            if (!GetBuffer(m_frmDst, dst)) {
                PutBuffer(m_frmSrc, src);
                return false;
            }

            swsc = new CScalerSW_YUYV(src[0], dst[0]);
            break;
        case V4L2_PIX_FMT_NV12M:
        case V4L2_PIX_FMT_NV21M:
            m_frmSrc.out_num_planes = 2;
            m_frmDst.out_num_planes = 2;
            m_frmSrc.out_plane_size[0] = m_frmSrc.width * m_frmSrc.height;
            m_frmDst.out_plane_size[0] = m_frmDst.width * m_frmDst.height;
            m_frmSrc.out_plane_size[1] = m_frmSrc.out_plane_size[0] / 2;
            m_frmDst.out_plane_size[1] = m_frmDst.out_plane_size[0] / 2;

            if (!GetBuffer(m_frmSrc, src))
                return false;

            if (!GetBuffer(m_frmDst, dst)) {
                PutBuffer(m_frmSrc, src);
                return false;
            }

            swsc = new CScalerSW_NV12(src[0], src[1], dst[0], dst[1]);
            break;
        case V4L2_PIX_FMT_NV12:
        case V4L2_PIX_FMT_NV21:
            m_frmSrc.out_num_planes = 1;
            m_frmDst.out_num_planes = 1;
            m_frmSrc.out_plane_size[0] = m_frmSrc.width * m_frmSrc.height;
            m_frmDst.out_plane_size[0] = m_frmDst.width * m_frmDst.height;
            m_frmSrc.out_plane_size[0] += m_frmSrc.out_plane_size[0] / 2;
            m_frmDst.out_plane_size[0] += m_frmDst.out_plane_size[0] / 2;

            if (!GetBuffer(m_frmSrc, src))
                return false;

            if (!GetBuffer(m_frmDst, dst)) {
                PutBuffer(m_frmSrc, src);
                return false;
            }

            src[1] = src[0] + m_frmSrc.width * m_frmSrc.height;
            dst[1] = dst[0] + m_frmDst.width * m_frmDst.height;

            swsc = new CScalerSW_NV12(src[0], src[1], dst[0], dst[1]);
            break;
        case V4L2_PIX_FMT_UYVY: // TODO: UYVY is not implemented yet.
        default:
            SC_LOGE("Format %x is not supported", m_frmSrc.color_format);
            return false;
    }

    if (swsc == NULL) {
        SC_LOGE("Failed to allocate SW Scaler");
        PutBuffer(m_frmSrc, src);
        PutBuffer(m_frmDst, dst);
        return false;
    }

    swsc->SetSrcRect(m_frmSrc.crop.left, m_frmSrc.crop.top,
            m_frmSrc.crop.width, m_frmSrc.crop.height, m_frmSrc.width);

    swsc->SetDstRect(m_frmDst.crop.left, m_frmDst.crop.top,
            m_frmDst.crop.width, m_frmDst.crop.height, m_frmDst.width);

    bool ret = swsc->Scale();

    delete swsc;

    PutBuffer(m_frmSrc, src);
    PutBuffer(m_frmDst, dst);

    return ret;
}
