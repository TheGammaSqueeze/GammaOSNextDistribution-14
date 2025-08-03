#ifndef __LIBSCALER_SWSCALER_H__
#define __LIBSCALER_SWSCALER_H__

#include "libscaler-common.h"

class CScalerSW {
    protected:
        char *m_pSrc[3];
        char *m_pDst[3];
        unsigned int m_nSrcLeft, m_nSrcTop;
        unsigned int m_nSrcWidth, m_nSrcHeight;
        unsigned int m_nSrcStride;
        unsigned int m_nDstLeft, m_nDstTop;
        unsigned int m_nDstWidth, m_nDstHeight;
        unsigned int m_nDstStride;
    public:
        CScalerSW() { Clear(); }
        virtual ~CScalerSW() { };
        void Clear();
        virtual bool Scale() = 0;

        void SetSrcRect(unsigned int left, unsigned int top, unsigned int width, unsigned int height, unsigned int stride) {
            m_nSrcLeft = left;
            m_nSrcTop = top;
            m_nSrcWidth = width;
            m_nSrcHeight = height;
            m_nSrcStride = stride;
        }

        void SetDstRect(unsigned int left, unsigned int top, unsigned int width, unsigned int height, unsigned int stride) {
            m_nDstLeft = left;
            m_nDstTop = top;
            m_nDstWidth = width;
            m_nDstHeight = height;
            m_nDstStride = stride;
        }
};

class CScalerSW_YUYV: public CScalerSW {
    public:
        CScalerSW_YUYV(char *src, char *dst) {
            m_pSrc[0] = src;
            m_pDst[0] = dst;
        }

        virtual bool Scale();
};

class CScalerSW_NV12: public CScalerSW {
    public:
        CScalerSW_NV12(char *src0, char *src1, char *dst0, char *dst1) {
            m_pSrc[0] = src0;
            m_pDst[0] = dst0;
            m_pSrc[1] = src1;
            m_pDst[1] = dst1;
        }

        virtual bool Scale();
};

#endif //__LIBSCALER_SWSCALER_H__
