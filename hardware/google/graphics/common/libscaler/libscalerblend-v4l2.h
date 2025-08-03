#ifndef _EXYNOS_SCALERBLEND_H_
#define _EXYNOS_SCALERBLEND_H_

#include "libscaler-v4l2.h"

class CScalerBlendV4L2 : public CScalerV4L2 {

public:
       CScalerBlendV4L2(int instance, int allow_drm);
       ~CScalerBlendV4L2();
       struct SrcBlendInfo m_SrcBlndCfg;

       // H/W Control
       virtual bool DevSetCtrl();

       void GetCustomAlphaBlendFmt(int32_t &src_color_space,
			                             unsigned int srcblendfmt);

       inline void SetSrcBlendOp(SRC_BL_OP op) {
               m_SrcBlndCfg.blop = op;
               SetFlag(m_fStatus, SCF_SRC_BLEND);
       }

       inline void SetSrcBlendHPos(int srcblendhpos) {
               m_SrcBlndCfg.srcblendhpos = srcblendhpos;
       }

       inline void SetSrcBlendVPos(int srcblendvpos) {
               m_SrcBlndCfg.srcblendvpos = srcblendvpos;
       }

       inline void SetSrcBlendPremulti(int srcblendpremulti) {
               m_SrcBlndCfg.srcblendpremulti = srcblendpremulti;
       }

       inline void SetSrcBlendFmt(int srcblendfmt) {
               m_SrcBlndCfg.srcblendfmt = srcblendfmt;
       }

       inline void SetSrcBlendStride(int srcblendstride) {
               m_SrcBlndCfg.srcblendstride = srcblendstride;
       }

       inline void SetSrcBlendWidth(int srcblendwidth) {
               m_SrcBlndCfg.srcblendwidth= srcblendwidth;
       }

       inline void SetSrcBlendHeight(int srcblendheight) {
               m_SrcBlndCfg.srcblendheight = srcblendheight;
       }

       inline void SetSrcGlobalAlpha(int globalalpha, bool enable) {
              m_SrcBlndCfg.globalalpha.enable = enable;
              m_SrcBlndCfg.globalalpha.val = globalalpha;
       }

       inline void SetSrcBlendCSCSpace(bool enable,
                                      enum colorspace space, bool wide) {
               m_SrcBlndCfg.cscspec.enable = enable;
               m_SrcBlndCfg.cscspec.space = space;
               m_SrcBlndCfg.cscspec.wide = wide;
       }
};
#endif
