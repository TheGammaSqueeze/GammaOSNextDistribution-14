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
 * \file      libscaler-v4l2.h
 * \brief     source file for Scaler HAL
 * \author    Cho KyongHo <pullip.cho@samsung.com>
 * \date      2014/05/12
 *
 * <b>Revision History: </b>
 * - 2014.05.12 : Cho KyongHo (pullip.cho@samsung.com) \n
 *   Create
 */
#ifndef _LIBSCALER_V4L2_H_
#define _LIBSCALER_V4L2_H_

#include <fcntl.h>

#include <exynos_scaler.h>

#include "libscaler-common.h"

#define V4L2_CID_EXYNOS_BASE            (V4L2_CTRL_CLASS_USER | 0x2000)
#define V4L2_CID_CSC_EQ_MODE            (V4L2_CID_EXYNOS_BASE + 100)
#define V4L2_CID_CSC_EQ                 (V4L2_CID_EXYNOS_BASE + 101)
#define V4L2_CID_CSC_RANGE              (V4L2_CID_EXYNOS_BASE + 102)
#define V4L2_CID_CONTENT_PROTECTION     (V4L2_CID_EXYNOS_BASE + 201)

#define V4L2_PIX_FMT_NV12N              v4l2_fourcc('N', 'N', '1', '2')
#define V4L2_PIX_FMT_NV12NT             v4l2_fourcc('T', 'N', '1', '2')
#define V4L2_PIX_FMT_YUV420N            v4l2_fourcc('Y', 'N', '1', '2')
#define V4L2_PIX_FMT_NV12N_10B          v4l2_fourcc('B', 'N', '1', '2')

#define V4L2_BUF_FLAG_USE_SYNC          0x00008000

class CScalerV4L2 {
public:
    enum { SC_MAX_PLANES = SC_NUM_OF_PLANES };
    enum { SC_MAX_NODENAME = 14 };
    enum { SC_V4L2_FMT_PREMULTI_FLAG = 10 };

    enum SC_FRAME_FLAG {
        // frame status
        SCFF_BUF_FRESH = 0,
        // h/w setting
        SCFF_CACHEABLE,
        SCFF_PREMULTIPLIED,
        // v4l2 status
        SCFF_REQBUFS,
        SCFF_QBUF,
        SCFF_STREAMING,
    };

    enum SC_FLAG {
        SCF_RESERVED = 0,
        // session status
        SCF_ROTATION_FRESH,
        SCF_CSC_FRESH,
        SCF_DRM_FRESH,
        // h/w setting setting
        SCF_HFLIP,
        SCF_VFLIP,
        SCF_DRM,
        SCF_ALLOW_DRM,
        SCF_CSC_WIDE,
	SCF_SRC_BLEND,
	SCF_FRAMERATE,
    };

    struct FrameInfo {
        const char *name;
        v4l2_buf_type type;
        unsigned int width, height;
        v4l2_rect crop;
        unsigned int color_format;
        void *addr[SC_MAX_PLANES];
        int fdAcquireFence;
        enum v4l2_memory memory;
        int out_num_planes;
        unsigned long out_plane_size[SC_MAX_PLANES];
        unsigned long flags; // enum SC_FRAME_FLAG
    };

private:
    FrameInfo m_frmSrc;
    FrameInfo m_frmDst;

    unsigned int m_nRotDegree;
    unsigned int m_frameRate;
    char m_cszNode[SC_MAX_NODENAME]; // /dev/videoXX
    int m_iInstance;

    int m_fdValidate;

    unsigned int m_filter;
    unsigned int m_colorspace;

    void Initialize(int instance);
    bool ResetDevice(FrameInfo &frm);

    inline void SetRotDegree(int rot) {
        rot = rot % 360;
        if (rot < 0)
            rot = 360 + rot;

        m_nRotDegree = rot;
        SetFlag(m_fStatus, SCF_ROTATION_FRESH);
    }

    bool DevSetFormat(FrameInfo &frm);
    bool ReqBufs(FrameInfo &frm);
    bool QBuf(FrameInfo &frm, int *pfdReleaseFence);
    bool StreamOn(FrameInfo &frm);
    bool DQBuf(FrameInfo &frm);

    inline bool SetFormat(FrameInfo &frm, unsigned int width, unsigned int height,
                   unsigned int v4l2_colorformat) {
        frm.color_format = v4l2_colorformat;
        frm.width = width;
        frm.height = height;
        SetFlag(frm.flags, SCFF_BUF_FRESH);
        return true;
    }

    inline bool SetCrop(FrameInfo &frm, unsigned int left, unsigned int top,
                 unsigned int width, unsigned int height) {
        frm.crop.left = left;
        frm.crop.top = top;
        frm.crop.width = width;
        frm.crop.height = height;
        SetFlag(frm.flags, SCFF_BUF_FRESH);
        return true;
    }

    inline void SetPremultiplied(FrameInfo &frm, unsigned int premultiplied) {
        if (premultiplied)
            SetFlag(frm.flags, SCFF_PREMULTIPLIED);
        else
            ClearFlag(frm.flags, SCFF_PREMULTIPLIED);
    }

    inline void SetCacheable(FrameInfo &frm, bool __UNUSED__ cacheable) {
        SetFlag(frm.flags, SCFF_CACHEABLE);
    }

    inline void SetAddr(FrameInfo &frm, void *addr[SC_NUM_OF_PLANES], int mem_type, int fence)
    {
        for (int i = 0; i < SC_MAX_PLANES; i++)
            frm.addr[i] = addr[i];

        frm.memory = static_cast<v4l2_memory>(mem_type);
        frm.fdAcquireFence = fence;
    }

    bool RunSWScaling();

protected:
    unsigned long m_fStatus; // enum SC_FLAG

    int m_fdScaler;

    inline void SetFlag(unsigned long &flags, unsigned long flag) {
        flags |= (1 << flag);
    }

    inline void ClearFlag(unsigned long &flags, unsigned long flag) {
        flags &= ~(1 << flag);
    }

    inline bool TestFlag(unsigned long &flags, unsigned long flag) {
        return (flags & (1 << flag)) != 0;
    }


public:
    inline bool Valid() { return (m_fdScaler >= 0) && (m_fdScaler == -m_fdValidate); }

    CScalerV4L2(int instance, int allow_drm = 0);
    virtual ~CScalerV4L2();

    bool SetCtrl();

    inline bool IsDRMAllowed() { return TestFlag(m_fStatus, SCF_ALLOW_DRM); }
    inline int GetScalerID() { return m_iInstance; }

    bool Stop();
    bool Run(); // Blocking mode

    // H/W Control
    virtual bool DevSetCtrl();
    bool DevSetFormat();

    inline bool ReqBufs() {
        if (!ReqBufs(m_frmSrc))
            return false;

        return ReqBufs(m_frmDst);
    }

    inline bool QBuf(int *pfdSrcReleaseFence = NULL, int *pfdDstReleaseFence = NULL) {
        if (!QBuf(m_frmSrc, pfdSrcReleaseFence))
            return false;

        if (!QBuf(m_frmDst, pfdDstReleaseFence)) {
            ClearFlag(m_frmSrc.flags, SCFF_QBUF);
            return false;
        }
        return true;
    }

    inline bool StreamOn() {
        if (!StreamOn(m_frmSrc))
            return false;

        return StreamOn(m_frmDst);
    }

    inline bool DQBuf() {
        if (!DQBuf(m_frmSrc))
            return false;

        return DQBuf(m_frmDst);
    }

    inline bool SetSrcFormat(unsigned int width, unsigned int height,
                      unsigned int v4l2_colorformat) {
        return SetFormat(m_frmSrc, width, height, v4l2_colorformat);
    }

    inline bool SetDstFormat(unsigned int width, unsigned int height,
                      unsigned int v4l2_colorformat) {
        return SetFormat(m_frmDst, width, height, v4l2_colorformat);
    }

    inline bool SetSrcCrop(unsigned int left, unsigned int top,
                    unsigned int width, unsigned int height) {
        return SetCrop(m_frmSrc, left, top, width, height);
    }

    inline bool SetDstCrop(unsigned int left, unsigned int top,
                    unsigned int width, unsigned int height) {
        return SetCrop(m_frmDst, left, top, width, height);
    }

    inline void SetDRM(bool drm) {
        if (drm != TestFlag(m_fStatus, SCF_DRM)) {
            if (drm)
                SetFlag(m_fStatus, SCF_DRM);
            else
                ClearFlag(m_fStatus, SCF_DRM);
            SetFlag(m_fStatus, SCF_DRM_FRESH);
        }
    }

    inline void SetCSCWide(bool wide) {
        if (wide)
            SetFlag(m_fStatus, SCF_CSC_WIDE);
        else
            ClearFlag(m_fStatus, SCF_CSC_WIDE);

        SetFlag(m_fStatus, SCF_CSC_FRESH);
    }

    inline void SetCSCEq(unsigned int v4l2_colorspace) {
        if (v4l2_colorspace == V4L2_COLORSPACE_SMPTE170M)
            m_colorspace = V4L2_COLORSPACE_DEFAULT;
        else
            m_colorspace = v4l2_colorspace;
        SetFlag(m_fStatus, SCF_CSC_FRESH);
    }

    inline void SetFilter(unsigned int filter) {
        m_filter = filter;
    }

    inline void SetSrcCacheable(bool cacheable) {
        return SetCacheable(m_frmSrc, cacheable);
    }

    inline void SetDstCacheable(bool cacheable) {
        return SetCacheable(m_frmDst, cacheable);
    }

    inline void SetSrcPremultiplied(bool premultiplied) {
        return SetPremultiplied(m_frmSrc, premultiplied);
    }

    inline void SetDstPremultiplied(bool premultiplied) {
        return SetPremultiplied(m_frmDst, premultiplied);
    }

    // Parameter Extraction
    bool SetRotate(int rot, int flip_h, int flip_v);

    inline bool SetSrcAddr(void *addr[SC_NUM_OF_PLANES], int mem_type, int fence = -1) {
        SetAddr(m_frmSrc, addr, mem_type, fence);
        return true;
    }

    inline bool SetDstAddr(void *addr[SC_NUM_OF_PLANES], int mem_type, int fence = -1) {
        SetAddr(m_frmDst, addr, mem_type, fence);
        return true;
    }

    inline void SetFrameRate(int framerate) {
        m_frameRate = framerate;
        SetFlag(m_fStatus, SCF_FRAMERATE);
    }
};

#endif //_LIBSCALER_V4L2_H_
