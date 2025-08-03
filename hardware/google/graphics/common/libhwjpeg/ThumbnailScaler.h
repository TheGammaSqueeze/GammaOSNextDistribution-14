#ifndef __HARDWARE_EXYNOS_THUMBNAIL_SCALER_H__
#define __HARDWARE_EXYNOS_THUMBNAIL_SCALER_H__

#include <cstddef>

class ThumbnailScaler {
public:
    const static unsigned int SCALER_MAX_PLANES = 3;
    ThumbnailScaler() {}
    virtual ~ThumbnailScaler() {}

    virtual bool SetSrcImage(unsigned int width, unsigned int height, unsigned int v4l2_format) = 0;
    virtual bool SetDstImage(unsigned int width, unsigned int height, unsigned int v4l2_format) = 0;

    virtual bool RunStream(int srcBuf[SCALER_MAX_PLANES], int srcLen[SCALER_MAX_PLANES], int dstBuf,
                           size_t dstLen) = 0;
    virtual bool RunStream(char *srcBuf[SCALER_MAX_PLANES], int srcLen[SCALER_MAX_PLANES],
                           int dstBuf, size_t dstLen) = 0;

    static ThumbnailScaler *createInstance();

    virtual bool available() { return false; }
};

#endif //__HARDWARE_EXYNOS_THUMBNAIL_SCALER_H__
