/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define FAILURE_DEBUG_PREFIX "jpeg"

#include <inttypes.h>
#include <setjmp.h>
#include <algorithm>
#include <vector>

extern "C" {
#include <jpeglib.h>
}
#include <libyuv/scale.h>
#include <system/camera_metadata.h>

#include "debug.h"
#include "exif.h"
#include "jpeg.h"
#include "yuv.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {
namespace jpeg {
namespace {
constexpr int kJpegMCUSize = 16;  // we have to feed `jpeg_write_raw_data` in multiples of this

// compressYUVImplPixelsFast handles the case where the image width is a multiple
// of kJpegMCUSize. In this case no additional memcpy is required. See
// compressYUVImplPixelsSlow below for the cases where the image width is not
// a multiple of kJpegMCUSize.
bool compressYUVImplPixelsFast(const android_ycbcr& image, jpeg_compress_struct* cinfo) {
    const uint8_t* y[kJpegMCUSize];
    const uint8_t* cb[kJpegMCUSize / 2];
    const uint8_t* cr[kJpegMCUSize / 2];
    const uint8_t** planes[] = { y, cb, cr };
    const int height = cinfo->image_height;
    const int height1 = height - 1;
    const int ystride = image.ystride;
    const int cstride = image.cstride;

    while (true) {
        const int nscl = cinfo->next_scanline;
        if (nscl >= height) {
            break;
        }

        for (int i = 0; i < kJpegMCUSize; ++i) {
            const int nscli = std::min(nscl + i, height1);
            y[i] = static_cast<const uint8_t*>(image.y) + nscli * ystride;
            if ((i & 1) == 0) {
                const int offset = (nscli / 2) * cstride;
                cb[i / 2] = static_cast<const uint8_t*>(image.cb) + offset;
                cr[i / 2] = static_cast<const uint8_t*>(image.cr) + offset;
            }
        }

        if (!jpeg_write_raw_data(cinfo, const_cast<JSAMPIMAGE>(planes), kJpegMCUSize)) {
            return FAILURE(false);
        }
    }

    return true;
}

// Since JPEG processes everything in blocks of kJpegMCUSize, we have to make
// both width and height a multiple of kJpegMCUSize. The height is handled by
// repeating the last line. compressYUVImplPixelsSlow handles the case when the
// image width is not a multiple of kJpegMCUSize by allocating a memory block
// large enough to hold kJpegMCUSize rows of the image with width aligned up to
// the next multiple of kJpegMCUSize. The original image has to be copied
// chunk-by-chunk into this memory block.
bool compressYUVImplPixelsSlow(const android_ycbcr& image, jpeg_compress_struct* cinfo,
                               const size_t alignedWidth, uint8_t* const alignedMemory) {
    uint8_t* y[kJpegMCUSize];
    uint8_t* cb[kJpegMCUSize / 2];
    uint8_t* cr[kJpegMCUSize / 2];
    uint8_t** planes[] = { y, cb, cr };

    {
        uint8_t* y0 = alignedMemory;
        for (int i = 0; i < kJpegMCUSize; ++i, y0 += alignedWidth) {
            y[i] = y0;
        }

        const size_t alignedWidth2 = alignedWidth / 2;
        uint8_t* cb0 = y0;
        uint8_t* cr0 = &cb0[kJpegMCUSize / 2 * alignedWidth2];

        for (int i = 0; i < kJpegMCUSize / 2; ++i, cb0 += alignedWidth2, cr0 += alignedWidth2) {
            cb[i] = cb0;
            cr[i] = cr0;
        }
    }

    const int width = cinfo->image_width;
    const int width2 = width / 2;
    const int height = cinfo->image_height;
    const int height1 = height - 1;
    const int ystride = image.ystride;
    const int cstride = image.cstride;

    while (true) {
        const int nscl = cinfo->next_scanline;
        if (nscl >= height) {
            break;
        }

        for (int i = 0; i < kJpegMCUSize; ++i) {
            const int nscli = std::min(nscl + i, height1);
            memcpy(y[i], static_cast<const uint8_t*>(image.y) + nscli * ystride, width);
            if ((i & 1) == 0) {
                const int offset = (nscli / 2) * cstride;
                memcpy(cb[i / 2], static_cast<const uint8_t*>(image.cb) + offset, width2);
                memcpy(cr[i / 2], static_cast<const uint8_t*>(image.cr) + offset, width2);
            }
        }

        if (!jpeg_write_raw_data(cinfo, const_cast<JSAMPIMAGE>(planes), kJpegMCUSize)) {
            return FAILURE(false);
        }
    }

    return true;
}

struct JpegErrorMgr : public jpeg_error_mgr {
    JpegErrorMgr() {
        error_exit = &onJpegErrorS;
    }

    void onJpegError(j_common_ptr cinfo) {
        {
            char errorMessage[JMSG_LENGTH_MAX];
            memset(errorMessage, 0, sizeof(errorMessage));
            (*format_message)(cinfo, errorMessage);
            ALOGE("%s:%d: JPEG compression failed with '%s'",
                  __func__, __LINE__, errorMessage);
        }

        longjmp(jumpBuffer, 1);
    }

    static void onJpegErrorS(j_common_ptr cinfo) {
        static_cast<JpegErrorMgr*>(cinfo->err)->onJpegError(cinfo);
    }

    jmp_buf jumpBuffer;
};

bool compressYUVImpl(const android_ycbcr& image, const Rect<uint16_t> imageSize,
                     unsigned char* const rawExif, const unsigned rawExifSize,
                     const int quality,
                     jpeg_destination_mgr* sink) {
    if (image.chroma_step != 1) {
        return FAILURE(false);
    }

    std::vector<uint8_t> alignedMemory;
    jpeg_compress_struct cinfo;
    JpegErrorMgr err;
    bool result;

    cinfo.err = jpeg_std_error(&err);
    jpeg_create_compress(&cinfo);
    cinfo.image_width = imageSize.width;
    cinfo.image_height = imageSize.height;
    cinfo.input_components = 3;
    cinfo.in_color_space = JCS_YCbCr;
    jpeg_set_defaults(&cinfo);
    jpeg_set_quality(&cinfo, quality, TRUE);
    jpeg_default_colorspace(&cinfo);
    cinfo.raw_data_in = TRUE;
    cinfo.dct_method = JDCT_IFAST;
    cinfo.comp_info[0].h_samp_factor = 2;
    cinfo.comp_info[0].v_samp_factor = 2;
    cinfo.comp_info[1].h_samp_factor = 1;
    cinfo.comp_info[1].v_samp_factor = 1;
    cinfo.comp_info[2].h_samp_factor = 1;
    cinfo.comp_info[2].v_samp_factor = 1;
    cinfo.dest = sink;

    if (setjmp(err.jumpBuffer)) {
        jpeg_destroy_compress(&cinfo);
        return FAILURE(false);
    }

    jpeg_start_compress(&cinfo, TRUE);

    if (rawExif) {
        jpeg_write_marker(&cinfo, JPEG_APP0 + 1, rawExif, rawExifSize);
    }

    if (imageSize.width % kJpegMCUSize) {
        const size_t alignedWidth =
            ((imageSize.width + kJpegMCUSize) / kJpegMCUSize) * kJpegMCUSize;
        alignedMemory.resize(alignedWidth * kJpegMCUSize * 3 / 2);
        result = compressYUVImplPixelsSlow(image, &cinfo, alignedWidth, alignedMemory.data());
    } else {
        result = compressYUVImplPixelsFast(image, &cinfo);
    }

    jpeg_finish_compress(&cinfo);
    jpeg_destroy_compress(&cinfo);

    return result;
}

android_ycbcr resizeYUV(const android_ycbcr& srcYCbCr,
                        const Rect<uint16_t> srcSize,
                        const Rect<uint16_t> dstSize,
                        std::vector<uint8_t>* pDstData) {
    if (srcYCbCr.chroma_step != 1) {
        return FAILURE(android_ycbcr());
    }

    const size_t dstWidth = dstSize.width;
    const size_t dstHeight = dstSize.height;
    if ((dstWidth & 1) || (dstHeight & 1)) {
        return FAILURE(android_ycbcr());
    }

    std::vector<uint8_t> dstData(yuv::NV21size(dstWidth, dstHeight));
    const android_ycbcr dstYCbCr = yuv::NV21init(dstWidth, dstHeight, dstData.data());

    const int result = libyuv::I420Scale(
        static_cast<const uint8_t*>(srcYCbCr.y), srcYCbCr.ystride,
        static_cast<const uint8_t*>(srcYCbCr.cb), srcYCbCr.cstride,
        static_cast<const uint8_t*>(srcYCbCr.cr), srcYCbCr.cstride,
        srcSize.width, srcSize.height,
        static_cast<uint8_t*>(dstYCbCr.y), dstYCbCr.ystride,
        static_cast<uint8_t*>(dstYCbCr.cb), dstYCbCr.cstride,
        static_cast<uint8_t*>(dstYCbCr.cr), dstYCbCr.cstride,
        dstWidth, dstHeight,
        libyuv::kFilterBilinear);

    if (result) {
        return FAILURE_V(android_ycbcr(), "libyuv::I420Scale failed with %d", result);
    } else {
        *pDstData = std::move(dstData);
        return dstYCbCr;
    }
}

struct StaticBufferSink : public jpeg_destination_mgr {
    StaticBufferSink(void* dst, const size_t dstCapacity) {
        next_output_byte = static_cast<JOCTET*>(dst);
        free_in_buffer = dstCapacity;
        init_destination = &initDestinationS;
        empty_output_buffer = &emptyOutputBufferS;
        term_destination = &termDestinationS;
    }

    static void initDestinationS(j_compress_ptr) {}
    static boolean emptyOutputBufferS(j_compress_ptr) { return 0; }
    static void termDestinationS(j_compress_ptr) {}
};

constexpr int kDefaultQuality = 85;

int sanitizeJpegQuality(const int quality) {
    if (quality <= 0) {
        return kDefaultQuality;
    } else if (quality > 100) {
        return 100;
    } else {
        return quality;
    }
}

}  // namespace

size_t compressYUV(const android_ycbcr& image,
                   const Rect<uint16_t> imageSize,
                   const CameraMetadata& metadata,
                   void* const jpegData,
                   const size_t jpegDataCapacity) {
    std::vector<uint8_t> nv21data;
    const android_ycbcr imageNV21 =
        yuv::toNV21Shallow(imageSize.width, imageSize.height,
                           image, &nv21data);

    auto exifData = exif::createExifData(metadata, imageSize);
    if (!exifData) {
        return FAILURE(0);
    }

    const camera_metadata_t* const rawMetadata =
        reinterpret_cast<const camera_metadata_t*>(metadata.metadata.data());
    camera_metadata_ro_entry_t metadataEntry;

    do {
        Rect<uint16_t> thumbnailSize = {0, 0};
        int thumbnailQuality = 0;

        if (find_camera_metadata_ro_entry(rawMetadata, ANDROID_JPEG_THUMBNAIL_SIZE,
                                          &metadataEntry)) {
            break;
        } else {
            thumbnailSize.width = metadataEntry.data.i32[0];
            thumbnailSize.height = metadataEntry.data.i32[1];
            if ((thumbnailSize.width <= 0) || (thumbnailSize.height <= 0)) {
                break;
            }
        }

        if (find_camera_metadata_ro_entry(rawMetadata, ANDROID_JPEG_THUMBNAIL_QUALITY,
                                          &metadataEntry)) {
            thumbnailQuality = kDefaultQuality;
        } else {
            thumbnailQuality = sanitizeJpegQuality(metadataEntry.data.i32[0]);
        }

        std::vector<uint8_t> thumbnailData;
        const android_ycbcr thumbmnail = resizeYUV(imageNV21, imageSize,
                                                   thumbnailSize, &thumbnailData);
        if (!thumbmnail.y) {
            return FAILURE(0);
        }

        StaticBufferSink sink(jpegData, jpegDataCapacity);
        if (!compressYUVImpl(thumbmnail, thumbnailSize, nullptr, 0,
                             thumbnailQuality, &sink)) {
            return FAILURE(0);
        }

        const size_t thumbnailJpegSize = jpegDataCapacity - sink.free_in_buffer;
        void* exifThumbnailJpegDataPtr = exif::exifDataAllocThumbnail(
            exifData.get(), thumbnailJpegSize);
        if (!exifThumbnailJpegDataPtr) {
            return FAILURE(0);
        }

        memcpy(exifThumbnailJpegDataPtr, jpegData, thumbnailJpegSize);
    } while (false);

    int quality;
    if (find_camera_metadata_ro_entry(rawMetadata, ANDROID_JPEG_QUALITY,
                                      &metadataEntry)) {
        quality = kDefaultQuality;
    } else {
        quality = sanitizeJpegQuality(metadataEntry.data.i32[0]);
    }

    unsigned char* rawExif = nullptr;
    unsigned rawExifSize = 0;
    exif_data_save_data(const_cast<ExifData*>(exifData.get()),
                        &rawExif, &rawExifSize);
    if (!rawExif) {
        return FAILURE(0);
    }

    StaticBufferSink sink(jpegData, jpegDataCapacity);
    const bool success = compressYUVImpl(imageNV21, imageSize, rawExif, rawExifSize,
                                         quality, &sink);
    free(rawExif);

    return success ? (jpegDataCapacity - sink.free_in_buffer) : 0;
}

}  // namespace jpeg
}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
