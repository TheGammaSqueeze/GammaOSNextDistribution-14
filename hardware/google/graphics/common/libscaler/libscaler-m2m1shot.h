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
 * \file      libscaler-m2m1shot.h
 * \brief     source file for Scaler HAL
 * \author    Cho KyongHo <pullip.cho@samsung.com>
 * \date      2014/05/08
 *
 * <b>Revision History: </b>
 * - 2014.05.08 : Cho KyongHo (pullip.cho@samsung.com) \n
 *   Create
 */
#ifndef _LIBSCALER_M2M1SHOT_H_
#define _LIBSCALER_M2M1SHOT_H_

#include "m2m1shot.h"

class CScalerM2M1SHOT {
    int m_iFD;
    m2m1shot m_task;

    bool SetFormat(m2m1shot_pix_format &fmt, m2m1shot_buffer &buf,
                   unsigned int width, unsigned int height, unsigned int v4l2_fmt);
    bool SetCrop(m2m1shot_pix_format &fmt,
                   unsigned int l, unsigned int t, unsigned int w, unsigned int h);
    bool SetAddr(m2m1shot_buffer &buf, void *addr[SC_NUM_OF_PLANES], int mem_type);

    bool RunSWScaling();
public:
    CScalerM2M1SHOT(int devid, int allow_drm = 0);
    ~CScalerM2M1SHOT();

    bool Run();

    inline bool Valid() { return m_iFD >= 0; }

    inline bool SetSrcFormat(unsigned int width, unsigned int height, unsigned int v4l2_fmt) {
        return SetFormat(m_task.fmt_out, m_task.buf_out, width, height, v4l2_fmt);
    }

    inline bool SetDstFormat(unsigned int width, unsigned int height, unsigned int v4l2_fmt) {
        return SetFormat(m_task.fmt_cap, m_task.buf_cap, width, height, v4l2_fmt);
    }

    inline bool SetSrcCrop(unsigned int l, unsigned int t, unsigned int w, unsigned int h) {
        return SetCrop(m_task.fmt_out, l, t, w, h);
    }

    inline bool SetDstCrop(unsigned int l, unsigned int t, unsigned int w, unsigned int h) {
        return SetCrop(m_task.fmt_cap, l, t, w, h);
    }

    inline bool SetSrcAddr(void *addr[SC_NUM_OF_PLANES], int mem_type) {
        return SetAddr(m_task.buf_out, addr, mem_type);
    }

    inline bool SetDstAddr(void *addr[SC_NUM_OF_PLANES], int mem_type) {
        return SetAddr(m_task.buf_cap, addr, mem_type);
    }

    bool SetRotate(int rot, int hflip, int vflip);

    inline void SetCSCWide(bool wide) {
        m_task.op.op &= ~(M2M1SHOT_OP_CSC_WIDE | M2M1SHOT_OP_CSC_NARROW);
        m_task.op.op |= wide ? M2M1SHOT_OP_CSC_WIDE : M2M1SHOT_OP_CSC_NARROW;
    }

    inline void SetCSCEq(unsigned int colorspace) {
        /* TODO: need to add M2M1SHOT_OP_CSC_2020 */
        m_task.op.op &= ~(M2M1SHOT_OP_CSC_601 | M2M1SHOT_OP_CSC_709);
        if (colorspace == V4L2_COLORSPACE_REC709)
            m_task.op.op |= M2M1SHOT_OP_CSC_709;
        else
            m_task.op.op |= M2M1SHOT_OP_CSC_601;
    }

    inline void SetFilter(unsigned int filter) {
        m_task.op.op &= ~LIBSC_M2M1SHOT_OP_FILTER_MASK;
        m_task.op.op |= filter << LIBSC_M2M1SHOT_OP_FILTER_SHIFT;
    }

    inline void SetFrameRate(int framerate) {
        m_task.reserved[0] = (unsigned long)framerate;
    }

    /* No effect in M2M1SHOT */
    inline void SetDRM(bool __UNUSED__ drm) { }
    inline void SetSrcPremultiplied(bool __UNUSED__ premultiplied) { }
    inline void SetDstPremultiplied(bool __UNUSED__ premultiplied) { }
    inline void SetSrcCacheable(bool __UNUSED__ cacheable) { }
    inline void SetDstCacheable(bool __UNUSED__ cacheable) { }
    inline bool Stop() { return true; }
    inline bool DevSetCtrl() { return false; }
    inline bool DevSetFormat() { return false; }
    inline bool ReqBufs() { return false; }
    inline bool StreamOn() { return false; }
    inline bool DQBuf() { return false; }
    inline bool QBuf(int __UNUSED__ *pfdSrcReleaseFence = NULL, int __UNUSED__ *pfdDstReleaseFence = NULL) {
	    return false;
    }

};

#endif //_LIBSCALER_M2M1SHOT_H_
