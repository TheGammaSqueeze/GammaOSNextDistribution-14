/*
 * Copyright Samsung Electronics Co.,LTD.
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef __EXYNOS_HWJPEG_H__
#define __EXYNOS_HWJPEG_H__

#include <linux/videodev2.h>

#include <cstddef> // size_t

#if VIDEO_MAX_PLANES < 6
#error VIDEO_MAX_PLANES should not be smaller than 6
#endif

#include "FileLock.h"
#include "android-base/thread_annotations.h"

// Exynos JPEG specific device capabilities
// Defined in the driver. Not in videodev2.h
#define V4L2_CAP_EXYNOS_JPEG_DECOMPRESSION 0x0100
#define V4L2_CAP_EXYNOS_JPEG_B2B_COMPRESSION 0x0200
#define V4L2_CAP_EXYNOS_JPEG_HWFC 0x0400
#define V4L2_CAP_EXYNOS_JPEG_HWFC_EMBEDDED 0x0800
#define V4L2_CAP_EXYNOS_JPEG_MAX_STREAMSIZE 0x1000
#define V4L2_CAP_EXYNOS_JPEG_NO_STREAMBASE_ALIGN 0x2000
#define V4L2_CAP_EXYNOS_JPEG_NO_IMAGEBASE_ALIGN 0x4000
#define V4L2_CAP_EXYNOS_JPEG_NO_BUFFER_OVERRUN 0x8000
#define V4L2_CAP_EXYNOS_JPEG_DECOMPRESSION_FROM_SOS 0x10000
#define V4L2_CAP_EXYNOS_JPEG_DECOMPRESSION_CROP 0x20000
#define V4L2_CAP_EXYNOS_JPEG_DOWNSCALING 0x40000
#define V4L2_CAP_EXYNOS_JPEG_DMABUF_OFFSET 0x80000
// EXYNOS HWJPEG specific auxiliary option flags
// The flags are common to all derived classes of CHWJpegCompressor
// but if a derived class does not support for a specified flag,
// it is discarded and ignored silently.
#define EXYNOS_HWJPEG_AUXOPT_ENABLE_HWFC (1 << 4)
#define EXYNOS_HWJPEG_AUXOPT_SRC_NOCACHECLEAN (1 << 8)
#define EXYNOS_HWJPEG_AUXOPT_DST_NOCACHECLEAN (1 << 9)

/*
 * CHWJpegBase - The base class of JPEG compression and decompression
 *
 * This class contains the following information:
 * - The open file descriptor of the device node
 * - The flags to describe the state of the operations
 * - The falgs to indicate the capability of HWJPEG
 * This class also defines the getters and the setters of flags.
 */
class CHWJpegBase {
    int m_iFD;
    unsigned int m_uiDeviceCaps;
    /*
     * Auxiliary option flags are implementation specific to derived classes
     * of CHWJpegCompressor. Even though the flags are common to all derived
     * classes, they should identify their supporting flags and ignore the
     * other flags.
     * NOTE that the flag is volatile. That means the flags stored in
     * m_uiAuxFlags is cleared when it is read by GetAuxFlags().
     *
     */
    unsigned int m_uiAuxFlags;

protected:
    CHWJpegBase(const char *path);
    virtual ~CHWJpegBase();
    int GetDeviceFD() { return m_iFD; }
    void SetDeviceCapabilities(unsigned int cap) { m_uiDeviceCaps = cap; }
    unsigned int GetAuxFlags() { return m_uiAuxFlags; }

public:
    unsigned int GetDeviceCapabilities() { return m_uiDeviceCaps; }
    bool IsDeviceCapability(unsigned int cap_flags) {
        return (m_uiDeviceCaps & cap_flags) == cap_flags;
    }

    /*
     * Okay - Test if the object is correctly initialized
     * @return: true if this object is correctly initialized and ready to use.
     *          false, otherwise.
     *
     * A user that creates this object *must* test if the object is successfully
     * created because some initialization in the constructor may fail.
     */
    bool Okay() { return m_iFD >= 0; }
    operator bool() { return Okay(); }

    /*
     * SetAuxFlags - Configure HWJPEG auxiliary options
     * @auxflags: a set of bit flags. The flags are prefixed by EXYNOS_HWJPEG_AUXOPT
     *            and defined separately in this file.
     *
     * SetAuxFlags() is *not* thread-safe. Racing calls to SetAuxFlags() between
     * multiple threads may cause the flags inconsistent. Moreover, Racing between
     * SetAuxFlags() call by the users and reading the flags by the libhwjpeg
     * causes incomplete hwjpeg configuration.
     */
    void SetAuxFlags(unsigned int auxflags);

    /*
     * ClearAuxFlags - Removes HWJPEG auxiliary options
     * @auxflags: a set of bit flags to clear. The flags are prefixed by EXYNOS_HWJPEG_AUXOPT
     *            and defined separately in this file.
     *
     * ClearAuxFlags() is *not* thread-safe. Racing calls to ClearAuxFlags() between
     * multiple threads may cause the flags inconsistent. Moreover, Racing between
     * ClearAuxFlags() call by the users and reading the flags by the libhwjpeg
     * causes incomplete hwjpeg configuration.
     */
    void ClearAuxFlags(unsigned int auxflags);
};

/*
 * CHWJpegCompressor - The abstract class of HW JPEG compression accelerator
 *
 * This class is *not* thread-safe. If an instance of this class is handled by
 * multiple threads, the users of the instance should care about the thread
 * synchronization.
 *
 * CHWJpegCompressor is able to check if the number of configured image buffers
 * are sufficient. It depends on the configured image format. Therefore, it is
 * better to configure the image format and the size prior to configure image
 * buffers. If back-to-back compression is required, it is *important* to know
 * how many buffers are required for an image. Thus it is *strongly* recommented
 * to configure image format and sizes before configuring image buffers.
 */
class CHWJpegCompressor : public CHWJpegBase {
    size_t m_nLastStreamSize;
    size_t m_nLastThumbStreamSize;

protected:
    void SetStreamSize(size_t main_size, size_t secondary_size = 0) {
        m_nLastStreamSize = main_size;
        m_nLastThumbStreamSize = secondary_size;
    }

    ssize_t GetStreamSize(size_t *secondary_size) {
        if (secondary_size) *secondary_size = m_nLastThumbStreamSize;
        return static_cast<ssize_t>(m_nLastStreamSize);
    }

public:
    CHWJpegCompressor(const char *path)
          : CHWJpegBase(path), m_nLastStreamSize(0), m_nLastThumbStreamSize(0) {}

    /*
     * SetImageFormat - Configure uncompressed image format, width and height
     * @v4l2_fmt[in] : Image pixel format defined in <linux/videodev2.h>
     * @width[in]      : Width of the primary uncompressed image in the number of pixels
     * @height[in]     : Height of the primary uncompressed image in the number of pixels
     * @sec_width[in]  : Width of the secondary uncompressed image in the number of pixels
     * (optional)
     * @sec_height[in] : Height of the secondary uncompressed image in the number of pixels
     * (optional)
     * @return : true if configuration of image pixel format and size is successful.
     *           false, otherwise.
     *
     * The primary and the secondary image format should be same. There is no way
     * to configure different image formats for them.
     */
    virtual bool SetImageFormat(unsigned int v4l2_fmt, unsigned int width, unsigned int height,
                                unsigned int sec_width = 0, unsigned int sec_height = 0) = 0;

    /*
     * GetImageBufferSizes - Ask the required buffer sizes for the given image format
     * @buf_sizes[out] : array of buffer sizes.
     * @num_buffers[in/out]: number of elements in @buf_sizes intially.
     *                       number assigned buffer sizes to @buf_sizes on return.
     * @return: true if the @buf_sizes and @num_buffers are initialized successfully.
     *          false, otherwise
     *
     * It should be called after SetImageFormat() SetImageSize() are called. Otherwise,
     * the returned buffer sizes are not correct.
     */
    virtual bool GetImageBufferSizes(size_t buf_sizes[], unsigned int *num_bufffers) = 0;
    /*
     * SetChromaSampFactor - Configure the chroma subsampling factor for JPEG stream
     * @horizontal[in] : horizontal chroma subsampling factor
     * @vertical[in]   : vertical chroma subsampling factor
     * @return: true if chroma subsamping factors are configured successfully,
     *          false if the factors are invalid.
     */
    virtual bool SetChromaSampFactor(unsigned int horizontal, unsigned int vertical) = 0;
    /*
     * SetQuality - Configure quality factor for JPEG compression
     * @quality_factor[in]  : JPEG compression quality factor between 1 and 100 for the primary
     * image
     * @quality_factor2[in] : JPEG compression quality factor for the secondary image (optional)
     * @return: true if quality factors are configured successfully.
     *          false, otherwise.
     */
    virtual bool SetQuality(unsigned int quality_factor, unsigned int quality_factor2 = 0) = 0;
    /*
     * SetQuality - Configure quantization tables for JPEG compression
     * @qtable[in]  : The 128 element array of quantization tables that contributes to JPEG
     *                compression. The first 64 elements are the quantization tables of the luma
     *                component. The other 64 elements are the quantization table of the chroma
     *                components. All the quantizers in the tables should be specified in the
     *                zig-zag scan order.
     * @return: true if the given quantization tables are configured successfully.
     *          false, otherwise.
     */
    virtual bool SetQuality(const unsigned char __unused qtable[]) { return false; };
    /*
     * SetPadding - Configures padding per plane for primary image
     * @padding[in]     : Padding per plane
     * @num_planes[in]  : Number of planes. This should match the number of elements in @padding
     * @return          : true if padding is congured successfully.
     *                    false, otherwise.
     */
    virtual bool SetPadding(const unsigned char padding[], unsigned int num_planes) = 0;
    /*
     * SetPadding2 - Configures padding per plane for thumbnail image
     * @padding[in]     : padding per plane
     * @num_planes[in]  : Number of planes. This should match the number of elements in @padding
     * @return          : true if padding is congured successfully.
     *                    false, otherwise.
     */
    virtual bool SetPadding2(const unsigned char __unused padding[],
                             unsigned int __unused num_planes) {
        return false;
    }
    /*
     * SetImageBuffer - Configure the uncompressed primary image buffers (userptr)
     * @buffers[in]     : addresses of the buffers
     * @len_buffers[in] : sizes of the buffers
     * @num_buffers[in] : the number of elements of @buffers and @len_buffers
     * @return          : true if buffer configuration is successful.
     *                    false, otherwise.
     */
    virtual bool SetImageBuffer(char *buffers[], size_t len_buffers[],
                                unsigned int num_buffers) = 0;
    /*
     * SetImageBuffer - Configure the uncompressed primary image buffers (dmabuf)
     * @buffers[in]     : file descriptors of the buffers exported by dma-buf
     * @len_buffers[in] : sizes of the buffers
     * @num_buffers[in] : the number of elements of @buffers and @len_buffers
     * @return          : true if buffer configuration is successful.
     *                    false, otherwise.
     */
    virtual bool SetImageBuffer(int buffers[], size_t len_buffers[], unsigned int num_buffers) = 0;
    /*
     * SetImageBuffer2 - Configure the uncompressed secondary image buffers (userptr)
     * @buffers[in]     : addresses of the buffers
     * @len_buffers[in] : sizes of the buffers
     * @num_buffers[in] : the number of elements of @buffers and @len_buffers
     * @return          : true if buffer configuration is successful.
     *                    false, otherwise.
     */
    virtual bool SetImageBuffer2(char __unused *buffers[], size_t __unused len_buffers[],
                                 unsigned int __unused num_buffers) {
        return false;
    }
    /*
     * SetImageBuffer2 - Configure the uncompressed secondary image buffers (dmabuf)
     * @buffers[in]     : file descriptors of the buffers exported by dma-buf
     * @len_buffers[in] : sizes of the buffers
     * @num_buffers[in] : the number of elements of @buffers and @len_buffers
     * @return          : true if buffer configuration is successful.
     *                    false, otherwise.
     */
    virtual bool SetImageBuffer2(int __unused buffers[], size_t __unused len_buffers[],
                                 unsigned int __unused num_buffers) {
        return false;
    }
    /*
     * SetJpegBuffer - Configure the buffer of JPEG stream of the primary image (userptr)
     * @buffer [in]     : The address of the buffer
     * @len_buffer [in] : The size of @buffer
     * @return          : true if buffer configuration is successful.
     *                    false, otherwise.
     */
    virtual bool SetJpegBuffer(char *buffer, size_t len_buffer) = 0;
    /*
     * SetJpegBuffer - Configure the buffer of JPEG stream of the primary image (dmabuf)
     * @buffer [in]     : The file descriptor of the buffer exported by dma-buf
     * @offset          : The buffer offset that main JPEG image is updated
     * @len_buffer [in] : The size of @buffer
     * @return          : true if buffer configuration is successful.
     *                    false, otherwise.
     */
    virtual bool SetJpegBuffer(int buffer, size_t len_buffer, int offset = 0) = 0;
    /*
     * SetJpegBuffer2 - Configure the buffer of JPEG stream of the secondary image (userptr)
     * @buffer [in]     : The address of the buffer
     * @len_buffer [in] : The size of @buffer
     * @return          : true if buffer configuration is successful.
     *                    false, otherwise.
     * The secondary image configuration is ignored if the secondary image size
     * is not configured with SetImageSize().
     */
    virtual bool SetJpegBuffer2(char __unused *buffer, size_t __unused len_buffer) { return false; }
    /*
     * SetJpegBuffer2 - Configure the buffer of JPEG stream of the secondary image (dmabuf)
     * @buffer [in]     : The file descriptor of the buffer exported by dma-buf
     * @len_buffer [in] : The size of @buffer
     * @return          : true if buffer configuration is successful.
     *                    false, otherwise.
     * The secondary image configuration is ignored if the secondary image size
     * is not configured with SetImageSize().
     */
    virtual bool SetJpegBuffer2(int __unused buffer, size_t __unused len_buffer) { return false; }
    /*
     * Compress - Compress the given image
     * secondary_stream_size[out] : The size of secondary JPEG stream
     * block_mode[in]             : If @block_mode is true this function does not return
     *                              until the compression finishes or error occurrs.
     *                              If a derived function does not support for non-block mode,
     *                              errur is returned.
     * @return : The size of the compressed JPEG stream
     *           (the offset of EOI from SOI plus sizeof(EOI))
     *           Zero If @block_mode is false and no error is occurred.
     *           Negative value on error.
     */
    virtual ssize_t Compress(size_t *secondary_stream_size = NULL, bool block_mode = true) = 0;
    /*
     * WaitForCompression - Wait for the compression finishes
     * secondary_stream_size[out] : The size of secondary JPEG stream
     * @return : The size of the compressed JPEG stream
     *           (the offset of EOI from SOI plus sizeof(EOI))
     *           Negative value on error.
     *
     * This function waits until the HWJPEG finishes JPEG compression if the second parameter
     * to Compress() is false. If the parameter is true, WaitForCompression() immeidately
     * returns and the returned size will be the stream sizes obtained by the last call to
     * Compress().
     */
    virtual ssize_t WaitForCompression(size_t __unused *secondary_stream_size = NULL) {
        return GetStreamSize(secondary_stream_size);
    }
    /*
     * GetImageBuffers - Retrieve the configured uncompressed image buffer information (dmabuf)
     * @buffers[out]: The file descriptors of the buffers exported by dma-buf
     * @len_buffers[out]: The size of each buffers in @buffers
     * @num_buffers[in]: The number of elements in @buffers and @len_buffers array
     * return: true if retrieving the buffer information is successful.
     *         false if no buffer is configured or the configured buffer is userptr type.
     * DEPREDCATED. DO NOT USE THIS FUNCTION.
     * This function is just provided to support the legacy ExynosJpegEncoder API.
     */
    virtual bool GetImageBuffers(int __unused buffers[], size_t __unused len_buffers[],
                                 unsigned int __unused num_buffers) {
        return false;
    }
    /*
     * GetImageBuffers - Retrieve the configured uncompressed image buffer information (userptr)
     * @buffers[out]: The addresses of the buffers
     * @len_buffers[out]: The size of each buffers in @buffers
     * @num_buffers[in]: The number of elements in @buffers and @len_buffers array
     * return: true if retrieving the buffer information is successful.
     *         false if no buffer is configured or the configured buffer is dmabuf type.
     * DEPREDCATED. DO NOT USE THIS FUNCTION.
     * This function is just provided to support the legacy ExynosJpegEncoder API.
     */
    virtual bool GetImageBuffers(char __unused *buffers[], size_t __unused len_buffers[],
                                 unsigned int __unused num_buffers) {
        return false;
    }
    /*
     * GetJpegBuffers - Retrieve the configured JPEG stream image buffer information (dmabuf)
     * @buffers[out]: The file descriptor of the buffer exported by dma-buf
     * @len_buffers[out]: The size of @buffer
     * return: true if retrieving the buffer information is successful.
     *         false if no buffer is configured or the configured buffer is userptr type.
     * DEPREDCATED. DO NOT USE THIS FUNCTION.
     * This function is just provided to support the legacy ExynosJpegEncoder API.
     */
    virtual bool GetJpegBuffer(int __unused *buffers, size_t __unused *len_buffer) { return false; }
    /*
     * GetJpegBuffers - Retrieve the configured JPEG stream buffer information (userptr)
     * @buffers[out]: The address of the buffer
     * @len_buffers[out]: The size of @buffers
     * return: true if retrieving the buffer information is successful.
     *         false if no buffer is configured or the configured buffer is dmabuf type.
     * DEPREDCATED. DO NOT USE THIS FUNCTION.
     * This function is just provided to support the legacy ExynosJpegEncoder API.
     */
    virtual bool GetJpegBuffer(char __unused **buffers, size_t __unused *len_buffer) {
        return false;
    }
    /*
     * Release - release the buffers acquired by CHWJpegCompressor
     */
    virtual void Release() {}
};

/*
 * CHWJpegDecompressor - The abstract class of HW JPEG accelerator for decompression
 *
 * This class is *not* thread-safe. If an instance of this class is handled by
 * multiple threads, the users of the instance should care about the thread
 * synchronization.
 *
 * CHWJpegDecompressor supports for downscaling during decompression by 1/2, 1/4 and
 * 1/8 if HWJPEG supports. The users should test if the HWJPEG supports for downscaling
 * before configuring smaller output image size.
 * The users also test if the HWJPEG (driver) requires the address SOI or SOS. If it
 * needs SOI, there is no need to feed the driver DHT and DQT. If it needs SOS, DHT
 * DQT should be informed to the driver because it is unable to find DHT and DQT
 * from SOS.
 *
 * V4L2_CAP_EXYNOS_JPEG_DOWNSCALING is set if HWJPEG supports for downscaling.
 * V4L2_CAP_EXYNOS_JPEG_DECOMPRESSION_FROM_SOS is set if HWJPEG driver needs the
 * address of SOS and the users to specify DHT and DQT to the driver.
 */
class CHWJpegDecompressor : public CHWJpegBase {
public:
    CHWJpegDecompressor(const char *path) : CHWJpegBase(path) {}
    virtual ~CHWJpegDecompressor() {}
    /*
     * SetImageFormat - Configure decompressed image pixel format
     * @v4l2_fmt[in] : Image pixel format defined in <linux/videodev2.h>
     * @width[in]      : Width of the decompressed image in the number of pixels
     * @height[in]     : Height of the decompressed image in the number of pixels
     * @return : true if configuration of image pixel format is successful.
     *           false, otherwise.
     *
     * @width and @height can be smaller than the compressed image size specified
     * by SetStreamPixelSize() if downscaling during decompression is supported.
     * The subclasses should test if V4L2_CAP_EXYNOS_JPEG_DOWNSCALING is set
     * in the device capabilities. Even though downscaling is supported by HWJPEG,
     * it has strict limitation that the downscaling factor should be one of
     * 1, 2, 4 and 8. If the specified decompressed image size is not one of
     * the compressed image size divided by 1, 2, 4 or 8, decompression should fail.
     */
    virtual bool SetImageFormat(unsigned int v4l2_fmt, unsigned int width, unsigned int height) = 0;

    /*
     * SetImageBuffer - Configure the decompressed image buffer (userptr)
     * @buffer[in]     : address of the buffer
     * @len_buffer[in] : size of the buffer
     * @return          : true if buffer configuration is successful.
     *                    false, otherwise.
     */
    virtual bool SetImageBuffer(char *buffer, size_t len_buffer) = 0;

    /*
     * SetImageBuffer - Configure the decompressed image buffer (dmabuf)
     * @buffer[in]     : file descriptor of the buffer exported by dma-buf
     * @len_buffer[in] : size of the buffer
     * @return          : true if buffer configuration is successful.
     *                    false, otherwise.
     */
    virtual bool SetImageBuffer(int buffer, size_t len_buffer) = 0;

    /*
     * SetStreamPixelSize - Configure the width and the height of the compressed stream
     * @width[in] : The number of horizontal pixels of the compressed image
     * @height[in] : The number of vertical pixels of the compressed image
     */
    virtual bool SetStreamPixelSize(unsigned int __unused width, unsigned int __unused height) {
        return true;
    }

    /*
     * SetChromaSampFactor - Configure the chroma subsampling factor for JPEG stream
     * @horizontal[in] : horizontal chroma subsampling factor
     * @vertical[in]   : vertical chroma subsampling factor
     * @return: true if chroma subsamping factors are configured successfully,
     *          false if the factors are invalid.
     *
     * If V4L2_CAP_EXYNOS_JPEG_DECOMPRESSION_FROM_SOS is specified in the device
     * capabilities, it is needed to configure chroma subsampling fractors because
     * Neither of HWJPEG nor its driver is able to find the chroma subsampling factors
     * of the compressed stream because it is specified in SOF and SOF is written
     * ahead of SOS in the JPEG stream.
     * If it is required to specify chroma subsampling factors separately, you should
     * override SetChromaSampFactor().
     */
    virtual bool SetChromaSampFactor(unsigned int __unused horizontal,
                                     unsigned int __unused vertical) {
        return true;
    }

    /*
     * SetDQT - Configure the address of DQT
     * @dqt[in] : The address of DQT in the JPEG stream
     * @return: true if the specified DQT has no problem. false if DQT does not exist
     *          in @dqt or the tables in @dqt are incomplete.
     *
     * If V4L2_CAP_EXYNOS_JPEG_DECOMPRESSION_FROM_SOS is specified in the device
     * capabilities, the HWJPEG needs DQT separately. Therefore every subcalss
     * will need to override SetDQT().
     */
    virtual bool SetDQT(const char __unused *dqt) { return true; }

    /*
     * SetDHT - Configure the address of DHT
     * @dht[in] : The address of DHT in the JPEG stream
     * @return: true if the specified DHT has no problem. false if DHT does not exist
     *          in @dht or the tables in @dqt are incomplete.
     *
     * If V4L2_CAP_EXYNOS_JPEG_DECOMPRESSION_FROM_SOS is specified in the device
     * capabilities, the HWJPEG needs DHT separately. Therefore every subcalss
     * will need to override SetDHT().
     */
    virtual bool SetDHT(const char __unused *dht) { return true; }

    /*
     * Decompress - Decompress the given JPEG stream
     * @buffer[in] : The buffer of JPEG stream.
     * @len[in] : The length of the JPEG stream. It includes EOI marker.
     * @return : true if the decompression succeeded. false, otherwise.
     *
     * If V4L2_CAP_EXYNOS_JPEG_DECOMPRESSION_FROM_SOS is set in the device capability
     * SOS marker should be at the start of @buffer. Otherwise, SOI marker should be
     * at the start. If @buffer is start with SOS marker, DHT, DQT and chroma
     * subsampling factors should be separately configured with SetDHT(), SetDQT() and
     * SetChromaSampFactor(), respectively.
     */
    virtual bool Decompress(const char *buffer, size_t len) = 0;
};

class CHWJpegFlagManager {
    unsigned int m_uiHWConfigFlags;

public:
    CHWJpegFlagManager() : m_uiHWConfigFlags(0) {}
    void SetFlag(unsigned int flag) { m_uiHWConfigFlags |= flag; }
    void ClearFlag(unsigned int flag) { m_uiHWConfigFlags &= ~flag; }
    bool TestFlag(unsigned int flag) { return (m_uiHWConfigFlags & flag) == flag; }
    bool TestFlagEither(unsigned int flag) { return !!(m_uiHWConfigFlags & flag); }
    unsigned int GetFlags() { return m_uiHWConfigFlags; }
};
/*
class CHWJpegM2M1SHOTCompressor: public CHWJpegCompressor {
};
*/

#define TO_SEC_IMG_SIZE(val) (((val) >> 16) & 0xFFFF)

class CAPABILITY("CHWJpegV4L2Compressor") CHWJpegV4L2Compressor : public CHWJpegCompressor,
                                                                  private CHWJpegFlagManager {
    enum {
        HWJPEG_CTRL_CHROMFACTOR = 0,
        HWJPEG_CTRL_QFACTOR,
        HWJPEG_CTRL_QFACTOR2,
        HWJPEG_CTRL_HWFC,
        HWJPEG_CTRL_PADDING,
        HWJPEG_CTRL_PADDING2,
        HWJPEG_CTRL_NUM,
    };

    enum {
        HWJPEG_FLAG_PIX_FMT = 0x1, // Set if unapplied image format exists

        HWJPEG_FLAG_QBUF_OUT = 0x100, // Set if the image buffer is queued
        HWJPEG_FLAG_QBUF_CAP = 0x200, // Set if the JPEG stream buffer is queued
        HWJPEG_FLAG_REQBUFS = 0x400,
        HWJPEG_FLAG_STREAMING = 0x800,

        HWJPEG_FLAG_SRC_BUFFER = 0x10000,  // Set if SetImageBuffer() is invoked successfully
        HWJPEG_FLAG_SRC_BUFFER2 = 0x20000, // Set if SetImageBuffer2() is invoked successfully
        HWJPEG_FLAG_DST_BUFFER = 0x40000,  // Set if SetJpegBuffer() is invoked successfully
        HWJPEG_FLAG_DST_BUFFER2 = 0x80000, // Set if SetJpegBuffer2() is invoked successfully
    };

    struct hwjpeg_v4l2_controls {
        __u32 id;
        __s32 value;
    } m_v4l2Controls[HWJPEG_CTRL_NUM];

    unsigned int m_uiControlsToSet;
    // H/W delay of the last compressoin in usec.
    // Only valid after Compression() successes.
    unsigned int m_uiHWDelay;

    v4l2_format m_v4l2Format;    // v4l2 format for the source image
    v4l2_buffer m_v4l2SrcBuffer; // v4l2 source buffer
    v4l2_plane m_v4l2SrcPlanes[6];
    v4l2_buffer m_v4l2DstBuffer;
    v4l2_plane m_v4l2DstPlanes[2];

    bool m_bEnableHWFC;

    // File lock required for interprocess synchronization.
    FileLock file_lock_;

    // Mutex required for in-process synchronization. This is necessary because the file lock does
    // not block if the same process attempts to acquire it again.
    std::mutex mutex_;

    bool IsB2BCompression() {
        return (TO_SEC_IMG_SIZE(m_v4l2Format.fmt.pix_mp.width) +
                TO_SEC_IMG_SIZE(m_v4l2Format.fmt.pix_mp.height)) != 0;
    }

    // V4L2 Helpers
    bool TryFormat() REQUIRES(this);
    bool SetFormat() REQUIRES(this);
    bool UpdateControls() REQUIRES(this);
    bool ReqBufs(unsigned int count = 1) REQUIRES(this);
    bool StreamOn() REQUIRES(this);
    bool StreamOff() REQUIRES(this);
    bool QBuf() REQUIRES(this);
    ssize_t DQBuf(size_t *secondary_stream_size) REQUIRES(this);
    bool StopStreaming() REQUIRES(this);

public:
    CHWJpegV4L2Compressor();
    virtual ~CHWJpegV4L2Compressor();

    // Acquires exclusive lock to V4L2 device. This must be called before starting image
    // configuration. This is a blocking call.
    int lock();
    // Releases exclusive lock to V4L2 device. This should be called after encoding is complete.
    int unlock();

    unsigned int GetHWDelay() { return m_uiHWDelay; }

    // SetChromaSampFactor can be called during streaming
    virtual bool SetChromaSampFactor(unsigned int horizontal, unsigned int vertical);
    virtual bool SetQuality(unsigned int quality_factor, unsigned int quality_factor2 = 0);
    virtual bool SetQuality(const unsigned char qtable[]) REQUIRES(this);
    virtual bool SetPadding(const unsigned char padding[], unsigned int num_planes);
    virtual bool SetPadding2(const unsigned char padding[], unsigned int num_planes);

    virtual bool SetImageFormat(unsigned int v4l2_fmt, unsigned int width, unsigned int height,
                                unsigned int sec_width = 0, unsigned sec_height = 0);
    virtual bool GetImageBufferSizes(size_t buf_sizes[], unsigned int *num_bufffers);
    virtual bool SetImageBuffer(char *buffers[], size_t len_buffers[], unsigned int num_buffers);
    virtual bool SetImageBuffer(int buffers[], size_t len_buffers[], unsigned int num_buffers);
    virtual bool SetImageBuffer2(char *buffers[], size_t len_buffers[], unsigned int num_buffers);
    virtual bool SetImageBuffer2(int buffers[], size_t len_buffers[], unsigned int num_buffers);
    virtual bool SetJpegBuffer(char *buffer, size_t len_buffer);
    virtual bool SetJpegBuffer(int buffer, size_t len_buffer, int offset = 0);
    virtual bool SetJpegBuffer2(char *buffer, size_t len_buffer);
    virtual bool SetJpegBuffer2(int buffer, size_t len_buffer);
    virtual ssize_t Compress(size_t *secondary_stream_size = NULL, bool bock_mode = true);
    virtual bool GetImageBuffers(int buffers[], size_t len_buffers[], unsigned int num_buffers);
    virtual bool GetImageBuffers(char *buffers[], size_t len_buffers[], unsigned int num_buffers);
    virtual bool GetJpegBuffer(char **buffer, size_t *len_buffer);
    virtual bool GetJpegBuffer(int *buffer, size_t *len_buffer);
    virtual ssize_t WaitForCompression(size_t *secondary_stream_size = NULL);
    virtual void Release();
};

class CHWJpegV4L2Decompressor : public CHWJpegDecompressor, private CHWJpegFlagManager {
    enum {
        HWJPEG_FLAG_OUTPUT_READY = 0x10,  /* the output stream is ready */
        HWJPEG_FLAG_CAPTURE_READY = 0x20, /* the capture stream is ready */
    };

    unsigned int m_uiHWDelay;

    v4l2_format m_v4l2Format;
    v4l2_buffer m_v4l2DstBuffer; /* multi-planar foramt is not supported */

    bool PrepareCapture();
    void CancelCapture();

    bool PrepareStream();
    void CancelStream();
    bool QBufAndWait(const char *buffer, size_t len);

public:
    CHWJpegV4L2Decompressor();
    virtual ~CHWJpegV4L2Decompressor();
    virtual bool SetImageFormat(unsigned int v4l2_fmt, unsigned int width, unsigned int height);
    virtual bool SetImageBuffer(char *buffer, size_t len_buffer);
    virtual bool SetImageBuffer(int buffer, size_t len_buffer);
    virtual bool Decompress(const char *buffer, size_t len);

    unsigned int GetHWDelay() { return m_uiHWDelay; }
};
#endif /* __EXYNOS_HWJPEG_H__ */
