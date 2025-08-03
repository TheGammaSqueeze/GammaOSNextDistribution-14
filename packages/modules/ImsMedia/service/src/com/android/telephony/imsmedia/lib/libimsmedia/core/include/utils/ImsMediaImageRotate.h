/**
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef IMS_MEDIA_IMAGE_ROTATE
#define IMS_MEDIA_IMAGE_ROTATE

#include <string.h>

class ImsMediaImageRotate
{
public:
    /**
     * @brief Rotates YUVImage_420_Planar Image by 90 degrees and flips.
     * Supports input row stride equal to width.
     *
     *  Source Image    Destination Image
     *  + - - - - +     + - - - - +
     *  | 1  2  3 |     | 9  6  3 |
     *  | 4  5  6 |     | 8  5  2 |
     *  | 7  8  9 |     | 7  4  1 |
     *  + - - - - +     + - - - - +
     *
     * @param pOutBuffer Pointer to output buffer with size nDstWidth*nDstHeight*1.5.
     * @param pbSrc Source buffer with size nDstWidth*nDstHeight*1.5.
     * @param nSrcWidth Source Image width.
     * @param nSrcHeight Source Image height.
     */
    static void YUV420_Planar_Rotate90_Flip(
            uint8_t* pOutBuffer, uint8_t* pbSrc, uint16_t nSrcWidth, uint16_t nSrcHeight);

    /**
     * @brief Rotates YUVImage_420_888 Image by 90 degrees.
     * Supports input row stride equal to width and adds padding when outputStride is not same
     * as output image width.
     *
     *  Source Image    Destination Image
     *  + - - - - +     + - - - - +
     *  | 1  2  3 |     | 7  4  1 |
     *  | 4  5  6 |     | 8  5  2 |
     *  | 7  8  9 |     | 9  6  3 |
     *  + - - - - +     + - - - - +
     *
     * @param pOutBuffer Pointer to output buffer with size outputStride*nDstHeight*1.5.
     * @param nOutBufSize size of output buffer.
     * @param outputStride Stride of the output image >= nDstWidth.
     * @param pYPlane Y-Plane data of size nDstWidth*nDstHeight.
     * @param pUVPlane UV-Plane data of size (nDstWidth*nDstHeight)/2.
     * @param nSrcWidth Source Image width.
     * @param nSrcHeight Source Image height.
     *
     * @return -1 on error and 0 on success.
     */
    static int YUV420_SP_Rotate90(uint8_t* pOutBuffer, size_t nOutBufSize, uint16_t outputStride,
            uint8_t* pYPlane, uint8_t* pUVPlane, uint16_t nSrcWidth, uint16_t nSrcHeight);

    /**
     * @brief Rotates YUVImage_420_888 Image by 90 degrees and flip.
     * Supports input row stride equal to width.
     *
     *  Source Image    Destination Image
     *  + - - - - +     + - - - - +
     *  | 1  2  3 |     | 9  6  3 |
     *  | 4  5  6 |     | 8  5  2 |
     *  | 7  8  9 |     | 7  4  1 |
     *  + - - - - +     + - - - - +
     *
     * @param pOutBuffer Pointer to output buffer with size nDstWidth*nDstHeight*1.5.
     * @param pYPlane Y-Plane data of size nDstWidth*nDstHeight.
     * @param pUVPlane UV-Plane data of size (nDstWidth*nDstHeight)/2.
     * @param nSrcWidth Source Image width.
     * @param nSrcHeight Source Image height.
     */
    static void YUV420_SP_Rotate90_Flip(uint8_t* pOutBuffer, uint8_t* pYPlane, uint8_t* pUVPlane,
            uint16_t nSrcWidth, uint16_t nSrcHeight);

    /**
     * @brief Rotates YUVImage_420_888 Image by 270 degrees.
     * Supports input row stride equal to width and adds padding when outputStride is not same
     * as output image width.
     *
     *  Source Image    Destination Image
     *  + - - - - +     + - - - - +
     *  | 1  2  3 |     | 3  6  9 |
     *  | 4  5  6 |     | 2  5  8 |
     *  | 7  8  9 |     | 1  4  7 |
     *  + - - - - +     + - - - - +
     *
     * @param pOutBuffer Pointer to output buffer with size nDstWidth*nDstHeight*1.5.
     * @param nOutBufSize size of output buffer.
     * @param outputStride Stride of the output image >= nDstWidth.
     * @param pYPlane Y-Plane data of size nDstWidth*nDstHeight.
     * @param pUVPlane UV-Plane data of size (nDstWidth*nDstHeight)/2.
     * @param nSrcWidth Source Image width.
     * @param nSrcHeight Source Image height.
     *
     * @return -1 on error and 0 on success.
     */
    static int YUV420_SP_Rotate270(uint8_t* pOutBuffer, size_t nOutBufSize, uint16_t outputStride,
            uint8_t* pYPlane, uint8_t* pUVPlane, uint16_t nSrcWidth, uint16_t nSrcHeight);
};

#endif  // IMS_MEDIA_IMAGE_ROTATE