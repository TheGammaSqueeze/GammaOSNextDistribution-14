#include "libscaler-swscaler.h"

void CScalerSW::Clear() {
    m_pSrc[0] = NULL;
    m_pSrc[1] = NULL;
    m_pSrc[2] = NULL;
    m_pDst[0] = NULL;
    m_pDst[1] = NULL;
    m_pDst[2] = NULL;

    m_nSrcLeft = 0;
    m_nSrcTop = 0;
    m_nSrcWidth = 0;
    m_nSrcHeight = 0;
    m_nSrcStride = 0;
    m_nDstLeft = 0;
    m_nDstTop = 0;
    m_nDstWidth = 0;
    m_nDstHeight = 0;
    m_nDstStride = 0;
}

bool CScalerSW_YUYV::Scale() {
    if (((m_nSrcLeft | m_nSrcWidth | m_nDstWidth | m_nSrcStride) % 2) != 0) {
        SC_LOGE("Width of YUV422 should be even");
        return false;
    }

    unsigned int h_ratio = (m_nSrcWidth << 16) / m_nDstWidth;
    unsigned int v_ratio = (m_nSrcHeight << 16) / m_nDstHeight;

    unsigned int src_x;
    unsigned int src_y = m_nSrcTop << 16;

    // Luminance + Chrominance at once
    for (unsigned int y = m_nDstTop; y < (m_nDstTop + m_nDstHeight); y++) {
        src_x = m_nSrcLeft << 16;
        for (unsigned int x = m_nDstLeft; x < (m_nDstLeft + m_nDstWidth); x++) {
            m_pDst[0][y * (m_nDstStride * 2) + x * 2] =
                m_pSrc[0][(src_y >> 16) * (m_nSrcStride * 2) + (src_x >> 16) * 2];

            if (!(x & 1)) {
                unsigned int cx = (src_x >> 16) & ~1;

                m_pDst[0][y * (m_nDstStride * 2) + x * 2 + 1] =
                    m_pSrc[0][(src_y >> 16) * (m_nSrcStride * 2) + cx * 2 + 1];
                m_pDst[0][y * (m_nDstStride * 2) + x * 2 + 3] =
                    m_pSrc[0][(src_y >> 16) * (m_nSrcStride * 2) + cx * 2 + 3];

            }

            src_x = LibScaler::min(src_x + h_ratio, (m_nSrcLeft + m_nSrcWidth) << 16);
        }

        src_y = LibScaler::min(src_y + v_ratio, (m_nSrcTop + m_nSrcHeight) << 16);
    }

    return true;
}

bool CScalerSW_NV12::Scale() {
    if (((m_nSrcLeft | m_nSrcTop | m_nSrcWidth | m_nSrcHeight | m_nSrcStride |
                    m_nDstLeft | m_nDstTop | m_nDstWidth | m_nDstHeight | m_nDstStride) % 2) != 0) {
        SC_LOGE("Both of width and height of YUV420 should be even");
        return false;
    }

    unsigned int h_ratio = (m_nSrcWidth << 16) / m_nDstWidth;
    unsigned int v_ratio = (m_nSrcHeight << 16) / m_nDstHeight;

    unsigned int src_x;
    unsigned int src_y = m_nSrcTop << 16;

    // Luminance
    for (unsigned int y = m_nDstTop; y < (m_nDstTop + m_nDstHeight); y++) {
        src_x = m_nSrcLeft << 16;
        for (unsigned int x = m_nDstLeft; x < (m_nDstLeft + m_nDstWidth); x++) {
            m_pDst[0][y * m_nDstStride + x] = m_pSrc[0][(src_y >> 16) * m_nSrcStride + (src_x >> 16)];

            src_x = LibScaler::min(src_x + h_ratio, (m_nSrcLeft + m_nSrcWidth) << 16);
        }

        src_y = LibScaler::min(src_y + v_ratio, (m_nSrcTop + m_nSrcHeight) << 16);
    }

    // Chrominance

    // change pointers to 1-byte to pointers to 2-byte storage.
    unsigned short *src = reinterpret_cast<unsigned short *>(m_pSrc[1]);
    unsigned short *dst = reinterpret_cast<unsigned short *>(m_pDst[1]);

    src_y = (m_nSrcTop / 2) << 16;
    for (unsigned int y = m_nDstTop / 2; y < ((m_nDstTop + m_nDstHeight) / 2); y++) {
        // Move 2 pixels at once (CbCr)
        src_x = (m_nSrcLeft / 2) << 16;
        for (unsigned int x = m_nDstLeft / 2; x < ((m_nDstLeft + m_nDstWidth) / 2); x++) {
            dst[y * (m_nDstStride / 2) + x] = src[(src_y >> 16) * (m_nSrcStride / 2) + (src_x >> 16)];

            src_x = LibScaler::min(src_x + h_ratio, ((m_nSrcLeft + m_nSrcWidth) / 2) << 16);
        }

        src_y = LibScaler::min(src_y + v_ratio, ((m_nSrcTop + m_nSrcHeight) / 2) << 16);
    }

    return true;
}
