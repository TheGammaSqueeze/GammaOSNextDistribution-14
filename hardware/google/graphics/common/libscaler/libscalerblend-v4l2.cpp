#include <sys/ioctl.h>
#include <sys/mman.h>
#include <linux/videodev2.h>

#include "libscaler-v4l2.h"
#include "libscalerblend-v4l2.h"

bool CScalerBlendV4L2::DevSetCtrl()
{
    struct v4l2_control ctrl;

    if (!SetCtrl())
        return false;

    /* Blending related ctls */
    if (!TestFlag(m_fStatus, SCF_SRC_BLEND))
        return false;

    ctrl.id = V4L2_CID_2D_BLEND_OP;
    ctrl.value = m_SrcBlndCfg.blop;
    if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
        SC_LOGERR("Failed S_CTRL V4L2_CID_2D_BLEND_OP");
        return false;
    }

    if (m_SrcBlndCfg.globalalpha.enable) {
        ctrl.id = V4L2_CID_GLOBAL_ALPHA;
        ctrl.value = m_SrcBlndCfg.globalalpha.val;
        if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
               SC_LOGERR("Failed S_CTRL V4L2_CID_GLOBAL_ALPHA");
               return false;
        }
    } else {
        ctrl.id = V4L2_CID_GLOBAL_ALPHA;
        ctrl.value = 0xff;
        if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
                SC_LOGERR("Failed S_CTRL V4L2_CID_GLOBAL_ALPHA 0xff");
                return false;
        }
    }

    if (m_SrcBlndCfg.cscspec.enable) {
        bool is_bt709 = (m_SrcBlndCfg.cscspec.space == COLORSPACE_REC709)? true : false;

        ctrl.id = V4L2_CID_CSC_EQ;
        ctrl.value = is_bt709;
        if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
            SC_LOGERR("Failed S_CTRL V4L2_CID_CSC_EQ - %d",
                                                   m_SrcBlndCfg.cscspec.space);
            return false;
        }

        ctrl.id = V4L2_CID_CSC_RANGE;
        ctrl.value = m_SrcBlndCfg.cscspec.wide;
        if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
            SC_LOGERR("Failed S_CTRL V4L2_CID_CSC_RANGE - %d",
                                                   m_SrcBlndCfg.cscspec.wide);
            return false;
        }
    }

    ctrl.id = V4L2_CID_2D_SRC_BLEND_SET_FMT;
    ctrl.value = m_SrcBlndCfg.srcblendfmt;
    if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
        SC_LOGERR("Failed V4L2_CID_2D_SRC_BLEND_SET_FMT - %d",
                                                  m_SrcBlndCfg.srcblendfmt);
        return false;
    }

    ctrl.id = V4L2_CID_2D_SRC_BLEND_FMT_PREMULTI;
    ctrl.value = m_SrcBlndCfg.srcblendpremulti;
    if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
        SC_LOGERR("Failed V4L2_CID_2D_BLEND_FMT_PREMULTI - %d",
                                                   m_SrcBlndCfg.srcblendpremulti);
        return false;
    }

    ctrl.id = V4L2_CID_2D_SRC_BLEND_SET_STRIDE;
    ctrl.value = m_SrcBlndCfg.srcblendstride;
    if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
        SC_LOGERR("Failed V4L2_CID_2D_SRC_BLEND_SET_STRIDE - %d",
                                                   m_SrcBlndCfg.srcblendstride);
        return false;
    }

    ctrl.id = V4L2_CID_2D_SRC_BLEND_SET_H_POS;
    ctrl.value = m_SrcBlndCfg.srcblendhpos;
    if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
        SC_LOGERR("Failed V4L2_CID_2D_SRC_BLEND_SET_H_POS with degree %d",
                                                   m_SrcBlndCfg.srcblendhpos);
        return false;
    }

    ctrl.id = V4L2_CID_2D_SRC_BLEND_SET_V_POS;
    ctrl.value = m_SrcBlndCfg.srcblendvpos;
    if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
        SC_LOGERR("Failed V4L2_CID_2D_SRC_BLEND_SET_V_POS - %d",
                                                   m_SrcBlndCfg.srcblendvpos);
        return false;
    }

    ctrl.id = V4L2_CID_2D_SRC_BLEND_SET_WIDTH;
    ctrl.value = m_SrcBlndCfg.srcblendwidth;
    if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
        SC_LOGERR("Failed V4L2_CID_2D_SRC_BLEND_SET_WIDTH with degree %d",
                                                   m_SrcBlndCfg.srcblendwidth);
        return false;
    }

    ctrl.id = V4L2_CID_2D_SRC_BLEND_SET_HEIGHT;
    ctrl.value = m_SrcBlndCfg.srcblendheight;
    if (ioctl(m_fdScaler, VIDIOC_S_CTRL, &ctrl) < 0) {
        SC_LOGERR("Failed V4L2_CID_2D_SRC_BLEND_SET_HEIGHT - %d",
                                                   m_SrcBlndCfg.srcblendheight);
        return false;
    }

    ClearFlag(m_fStatus, SCF_SRC_BLEND);
    return true;
}

void CScalerBlendV4L2::GetCustomAlphaBlendFmt(int32_t &src_color_space,
                                                     unsigned int srcblendfmt) {

    if (src_color_space == V4L2_PIX_FMT_NV12M) {
        if (srcblendfmt == V4L2_PIX_FMT_RGB32)
            src_color_space = V4L2_PIX_FMT_NV12M_RGB32;
        else if (srcblendfmt == V4L2_PIX_FMT_BGR32)
            src_color_space = V4L2_PIX_FMT_NV12M_BGR32;
        else if (srcblendfmt == V4L2_PIX_FMT_RGB565)
            src_color_space = V4L2_PIX_FMT_NV12M_RGB565;
        else if (srcblendfmt == V4L2_PIX_FMT_RGB444)
            src_color_space = V4L2_PIX_FMT_NV12M_RGB444;
        else if (srcblendfmt == V4L2_PIX_FMT_RGB555X)
            src_color_space = V4L2_PIX_FMT_NV12M_RGB555X;
    } else if (src_color_space == V4L2_PIX_FMT_NV12) {
            if (srcblendfmt == V4L2_PIX_FMT_RGB32)
            src_color_space = V4L2_PIX_FMT_NV12_RGB32;
    } else if (src_color_space == V4L2_PIX_FMT_NV12N) {
            if (srcblendfmt == V4L2_PIX_FMT_RGB32)
            src_color_space = V4L2_PIX_FMT_NV12N_RGB32;
    } else if (src_color_space == V4L2_PIX_FMT_NV12MT_16X16) {
            if (srcblendfmt == V4L2_PIX_FMT_RGB32)
            src_color_space = V4L2_PIX_FMT_NV12MT_16X16_RGB32;
    } else if (src_color_space == V4L2_PIX_FMT_NV21M) {
        if (srcblendfmt == V4L2_PIX_FMT_RGB32)
            src_color_space = V4L2_PIX_FMT_NV21M_RGB32;
        else if (srcblendfmt == V4L2_PIX_FMT_BGR32)
            src_color_space = V4L2_PIX_FMT_NV21M_BGR32;
    } else if (src_color_space == V4L2_PIX_FMT_NV21) {
        if (srcblendfmt == V4L2_PIX_FMT_RGB32)
            src_color_space = V4L2_PIX_FMT_NV21_RGB32;
    } else if (src_color_space == V4L2_PIX_FMT_YVU420) {
        if (srcblendfmt == V4L2_PIX_FMT_RGB32)
            src_color_space = V4L2_PIX_FMT_YVU420_RGB32;
    } else {
        src_color_space = -1;
    }
}

CScalerBlendV4L2::CScalerBlendV4L2(int dev_num, int allow_drm) : CScalerV4L2(dev_num, allow_drm){

}

CScalerBlendV4L2::~CScalerBlendV4L2(){

}
