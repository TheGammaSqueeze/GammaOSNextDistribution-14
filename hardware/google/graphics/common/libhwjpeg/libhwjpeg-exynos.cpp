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

#include <exynos-hwjpeg.h>
#include <fcntl.h>
#include <hwjpeglib-exynos.h>
#include <linux/videodev2.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <cstdio>
#include <cstring>

#include "hwjpeg-internal.h"

#define ALOGERR(fmt, args...) ((void)ALOG(LOG_ERROR, LOG_TAG, fmt " [%s]", ##args, strerror(errno)))

#define ROUND_DOWN(val, denom) ((val) & ~((denom)-1))
#define ROUND_UP(val, denom) ROUND_DOWN((val) + (denom)-1, denom)
#define TO_MASK(val) ((val)-1)

class CJpegStreamParser {
private:
    unsigned char *m_pStreamBase;
    size_t m_nStreamSize;

    unsigned char m_nComponents;
    unsigned short m_nWidth;
    unsigned short m_nHeight;

    void Initialize();
    size_t GetLength(unsigned char *addr);
    bool ParseFrame(unsigned char *addr);

    ptrdiff_t GetOffset(unsigned char *addr) {
        unsigned long beg = reinterpret_cast<unsigned long>(m_pStreamBase);
        unsigned long cur = reinterpret_cast<unsigned long>(addr);
        return static_cast<ptrdiff_t>(cur - beg);
    }

public:
    unsigned char m_iHorizontalFactor;
    unsigned char m_iVerticalFactor;

    CJpegStreamParser() : m_pStreamBase(NULL), m_nStreamSize(0) {}
    ~CJpegStreamParser() {}

    bool Parse(unsigned char *streambase, size_t length);

    int GetImageFormat();
    unsigned int GetWidth() { return m_nWidth; }
    unsigned int GetHeight() { return m_nHeight; }
    unsigned int GetNumComponents() { return m_nComponents; }
};

void CJpegStreamParser::Initialize() {
    m_nComponents = 0;
    m_nWidth = 0;
    m_nHeight = 0;
    m_iHorizontalFactor = 1;
    m_iVerticalFactor = 1;
}

size_t CJpegStreamParser::GetLength(unsigned char *addr) {
    size_t len = static_cast<size_t>(*addr++) * 0x100;
    return len + *addr;
}

bool CJpegStreamParser::Parse(unsigned char *streambase, size_t length) {
    Initialize();

    m_pStreamBase = streambase;
    m_nStreamSize = length;

    unsigned char *addr = m_pStreamBase;
    size_t filelen = m_nStreamSize;

    // Finding SOI (xFFD8)
    if ((filelen < 2) || (addr[0] != 0xFF) || (addr[1] != 0xD8)) {
        ALOGE("Not a valid JPEG stream (len %zu, marker %02x%02x", filelen, addr[0], addr[1]);
        return false;
    }
    addr += 2;
    filelen -= 2;

    while (true) { // DHT, DQT, SOF, SOS
        if (filelen < 2) {
            ALOGE("Incomplete JPEG Stream");
            return false;
        }

        if (*addr++ != 0xFF) {
            ALOGE("Corrupted JPEG stream");
            return false;
        }

        unsigned char marker = *addr++;
        filelen -= 2;

        if ((marker != 0xC4) && ((marker & 0xF0) == 0xC0)) { // SOFn
            if (marker != 0xC0) {
                ALOGE("SOF%d is not supported (offset %zu)", marker & 0xF, m_nStreamSize - filelen);
                return false;
            }

            if (filelen < 2 || filelen < GetLength(addr)) {
                ALOGE("Too small SOF0 segment");
                return false;
            }

            if (!ParseFrame(addr)) return false;

            return true;             // this is the successful exit point
        } else if (marker == 0xD9) { // EOI
            // This will not meet.
            ALOGE("Unexpected EOI found at %td\n", GetOffset(addr - 2));
            return false;
        } else {
            if ((marker == 0xCC) || (marker == 0xDC)) { // DAC and DNL
                ALOGE("Unsupported JPEG stream: found marker 0xFF%02X", marker);
                return false;
            }

            if (filelen < 2 || filelen < GetLength(addr)) {
                ALOGE("Corrupted JPEG stream");
                return false;
            }
        }

        if (filelen < 2 || GetLength(addr) == 0) {
            ALOGE("Invalid length 0 is read at offset %td", GetOffset(addr));
            return false;
        }

        if (filelen < GetLength(addr)) {
            ALOGE("Corrupted JPEG Stream");
            return false;
        }

        filelen -= GetLength(addr);
        addr += GetLength(addr);
    }

    // NEVER REACH HERE

    ALOGE("Unable to find the frame header");

    return false;
}

bool CJpegStreamParser::ParseFrame(unsigned char *addr) { // 2 bytes of length
    // 1 byte of bits per sample
    // 2 bytes of height
    // 2 bytes of width
    // 1 byte of number of components
    // n * 3 byte component specifications
    if (GetLength(addr) < 17) {
        ALOGE("SOF0 should include all three components");
        return false;
    }
    addr += 2; // skip length

    if (*addr != 8) { // bits per sample
        ALOGE("Bits Per Sample should be 8 but it is %d", *addr);
        return false;
    }
    addr++;

    m_nHeight = static_cast<unsigned short>(GetLength(addr));
    if ((m_nHeight < 8) || (m_nHeight > 16383)) {
        ALOGE("Height %d is not supported", m_nHeight);
        return false;
    }
    addr += 2;

    m_nWidth = static_cast<unsigned short>(GetLength(addr));
    if ((m_nWidth < 8) || (m_nWidth > 16383)) {
        ALOGE("Width %d is not supported", m_nWidth);
        return false;
    }
    addr += 2;

    m_nComponents = *addr;
    if (m_nComponents != 3) {
        ALOGE("Number of components should be 3 but it is %d", m_nComponents);
        return false;
    }
    addr++;

    // Only the first component is needed to find chroma subsampling factor
    addr++; // skip component identifier
    if ((*addr != 0x11) && (*addr != 0x21) && (*addr != 0x12) && (*addr != 0x22)) {
        ALOGE("Invalid Luma sampling factor %#02x", *addr);
        return false;
    }
    m_iHorizontalFactor = *addr >> 4;
    m_iVerticalFactor = *addr & 0xF;

    return true;
}

class CLibhwjpegDecompressor : public hwjpeg_decompressor_struct {
    enum {
        HWJPG_FLAG_NEED_MUNMAP = 1,
    };

    unsigned int m_flags;
    bool m_bPrepared;
    CHWJpegDecompressor *m_hwjpeg;

    unsigned char *m_pStreamBuffer;
    size_t m_nStreamLength;
    size_t m_nDummyBytes;

    CJpegStreamParser m_jpegStreamParser;

public:
    CLibhwjpegDecompressor() : m_flags(0) {
        // members of hwjpeg_decompressor_struct
        image_width = 0;
        image_height = 0;
        num_components = 3;
        chroma_h_samp_factor = 1;
        chroma_v_samp_factor = 1;
        scale_factor = 1;
        output_width = 0;
        output_height = 0;
        m_bPrepared = false;
        m_pStreamBuffer = NULL;

        output_format = V4L2_PIX_FMT_RGB32;

        // members of this
        m_nStreamLength = 0;
        m_nDummyBytes = 0;

        m_hwjpeg = new CHWJpegV4L2Decompressor;
        if (!m_hwjpeg || !*m_hwjpeg) {
            ALOGE("Failed to create HWJPEG decompressor");
            delete m_hwjpeg;
        }
    }

    ~CLibhwjpegDecompressor() {
        delete m_hwjpeg;

        if (!!(m_flags & HWJPG_FLAG_NEED_MUNMAP))
            munmap(m_pStreamBuffer, m_nStreamLength + m_nDummyBytes);
    }

    bool SetStreamPath(const char *path) {
        if ((m_pStreamBuffer != NULL) && !!(m_flags & HWJPG_FLAG_NEED_MUNMAP)) {
            munmap(m_pStreamBuffer, m_nStreamLength + m_nDummyBytes);
            m_flags &= ~HWJPG_FLAG_NEED_MUNMAP;
            m_pStreamBuffer = NULL;
            m_nStreamLength = 0;
        }

        int fd = open(path, O_RDONLY);
        if (fd < 0) {
            ALOGERR("Failed to open '%s' for decompression", path);
            return false;
        }

        struct stat st;
        if (fstat(fd, &st) < 0) {
            ALOGERR("Failed to read size of '%s'", path);
            close(fd);
            return false;
        }

        m_nStreamLength = st.st_size;
        m_nDummyBytes = 0;

        m_pStreamBuffer = reinterpret_cast<unsigned char *>(
                mmap(NULL, m_nStreamLength, PROT_READ | PROT_WRITE, MAP_PRIVATE, fd, 0));
        if (m_pStreamBuffer == MAP_FAILED) {
            m_pStreamBuffer = NULL;
            close(fd);
            ALOGERR("Failed to mmap %zu bytes of '%s'", m_nStreamLength, path);
            return false;
        }

        m_bPrepared = false;

        m_flags |= HWJPG_FLAG_NEED_MUNMAP;

        close(fd);
        return true;
    }

    bool SetStreamBuffer(unsigned char *buffer, size_t len, size_t dummybytes) {
        if ((m_pStreamBuffer != NULL) && !!(m_flags & HWJPG_FLAG_NEED_MUNMAP)) {
            munmap(m_pStreamBuffer, m_nStreamLength + m_nDummyBytes);
            m_flags &= ~HWJPG_FLAG_NEED_MUNMAP;
        }

        m_pStreamBuffer = buffer;
        m_nStreamLength = len;
        m_nDummyBytes = dummybytes;

        m_bPrepared = false;

        return true;
    }

    bool SetStreamBuffer(int buffer, size_t len, size_t dummybytes) {
        if ((m_pStreamBuffer != NULL) && !!(m_flags & HWJPG_FLAG_NEED_MUNMAP)) {
            munmap(m_pStreamBuffer, m_nStreamLength + m_nDummyBytes);
            m_flags &= ~HWJPG_FLAG_NEED_MUNMAP;
        }

        m_nStreamLength = len;
        m_nDummyBytes = dummybytes;

        m_pStreamBuffer = reinterpret_cast<unsigned char *>(
                mmap(NULL, m_nStreamLength + m_nDummyBytes, PROT_READ | PROT_WRITE, MAP_SHARED,
                     buffer, 0));
        if (m_pStreamBuffer == MAP_FAILED) {
            m_pStreamBuffer = NULL;
            ALOGERR("Failed to mmap %zu bytes of dmabuf fd %d", m_nStreamLength, buffer);
            return false;
        }

        m_flags |= HWJPG_FLAG_NEED_MUNMAP;

        m_bPrepared = false;

        return true;
    }

    bool SetImageBuffer(unsigned char *buffer[3], size_t len[3], unsigned int num_bufs) {
        if (num_bufs != 1) {
            ALOGE("multi-planar image is not supported(%u planes)", num_bufs);
            return false;
        }

        return m_hwjpeg->SetImageBuffer(reinterpret_cast<char *>(buffer[0]), len[0]);
    }

    bool SetImageBuffer(int buffer[3], size_t len[3], unsigned int num_bufs) {
        if (num_bufs != 1) {
            ALOGE("multi-planar image is not supported(%u planes)", num_bufs);
            return false;
        }

        return m_hwjpeg->SetImageBuffer(buffer[0], len[0]);
    }

    void SetDownscaleFactor(unsigned int factor) { scale_factor = factor; }

    bool PrepareDecompression();
    bool Decompress();

    bool IsEnoughStreamBuffer() { return true; }
};

bool CLibhwjpegDecompressor::PrepareDecompression() {
    if (!m_hwjpeg) {
        ALOGE("device node is not opened!");
        return false;
    }

    if ((scale_factor != 1) && (scale_factor != 2) && (scale_factor != 4) && (scale_factor != 8)) {
        ALOGE("Invalid downscaling factor %d", scale_factor);
        return false;
    }

    if (m_pStreamBuffer == NULL) {
        ALOGE("No stream buffer is configured");
        return false;
    }

    if (!m_jpegStreamParser.Parse(m_pStreamBuffer, m_nStreamLength)) return false;

    image_width = m_jpegStreamParser.GetWidth();
    image_height = m_jpegStreamParser.GetHeight();
    num_components = m_jpegStreamParser.GetNumComponents();
    chroma_h_samp_factor = m_jpegStreamParser.m_iHorizontalFactor;
    chroma_v_samp_factor = m_jpegStreamParser.m_iVerticalFactor;

    if (((image_width % (chroma_h_samp_factor * scale_factor)) != 0) ||
        ((image_height % (chroma_v_samp_factor * scale_factor)) != 0)) {
        ALOGE("Downscaling by factor %d of compressed image size %dx%d(chroma %d:%d) is not "
              "supported",
              scale_factor, image_width, image_height, chroma_h_samp_factor, chroma_v_samp_factor);
        return false;
    }

    output_width = image_width / scale_factor;
    output_height = image_height / scale_factor;

    if (!m_hwjpeg->SetStreamPixelSize(image_width, image_height)) {
        ALOGE("Failed to configure stream pixel size (%ux%u)", image_width, image_height);
        return false;
    }

    if (!m_hwjpeg->SetImageFormat(output_format, output_width, output_height)) {
        ALOGE("Failed to configure image format (%ux%u/%08X)", output_width, output_height,
              output_format);
        return false;
    }

    m_bPrepared = true;

    return true;
}

bool CLibhwjpegDecompressor::Decompress() {
    if (!m_bPrepared) {
        ALOGE("JPEG header is not parsed");
        return false;
    }

    if (!IsEnoughStreamBuffer()) {
        ALOGE("Not enough buffer length for HWJPEG");
        return false;
    }

    m_bPrepared = false;

    if (!m_hwjpeg->Decompress(reinterpret_cast<char *>(m_pStreamBuffer), m_nStreamLength)) {
        ALOGE("Failed to decompress");
        return false;
    }

    return true;
}

hwjpeg_decompress_ptr hwjpeg_create_decompress() {
    hwjpeg_decompress_ptr p = new CLibhwjpegDecompressor();
    if (!p) ALOGE("Failed to create decompress struct");
    return p;
}

bool hwjpeg_file_src(hwjpeg_decompress_ptr cinfo, const char *path) {
    CLibhwjpegDecompressor *decomp = reinterpret_cast<CLibhwjpegDecompressor *>(cinfo);
    return decomp->SetStreamPath(path);
}

void hwjpeg_config_image_format(hwjpeg_decompress_ptr cinfo, __u32 v4l2_pix_fmt) {
    cinfo->output_format = v4l2_pix_fmt;
}

bool hwjpeg_dmabuf_src(hwjpeg_decompress_ptr cinfo, int infd, size_t insize, size_t dummybytes) {
    CLibhwjpegDecompressor *decomp = reinterpret_cast<CLibhwjpegDecompressor *>(cinfo);
    return decomp->SetStreamBuffer(infd, insize, dummybytes);
}

bool hwjpeg_mem_src(hwjpeg_decompress_ptr cinfo, unsigned char *inbuffer, size_t insize,
                    size_t dummybytes) {
    CLibhwjpegDecompressor *decomp = reinterpret_cast<CLibhwjpegDecompressor *>(cinfo);
    return decomp->SetStreamBuffer(inbuffer, insize, dummybytes);
}

bool hwjpeg_mem_dst(hwjpeg_decompress_ptr cinfo, unsigned char *outbuffer[], size_t outsize[],
                    unsigned int num_buffers) {
    CLibhwjpegDecompressor *decomp = reinterpret_cast<CLibhwjpegDecompressor *>(cinfo);
    return decomp->SetImageBuffer(outbuffer, outsize, num_buffers);
}

bool hwjpeg_dmabuf_dst(hwjpeg_decompress_ptr cinfo, int outfd[], size_t outsize[],
                       unsigned int num_buffers) {
    CLibhwjpegDecompressor *decomp = reinterpret_cast<CLibhwjpegDecompressor *>(cinfo);
    return decomp->SetImageBuffer(outfd, outsize, num_buffers);
}

void hwjpeg_set_downscale_factor(hwjpeg_decompress_ptr cinfo, unsigned int factor) {
    cinfo->scale_factor = factor;
}

bool hwjpeg_read_header(hwjpeg_decompress_ptr cinfo) {
    CLibhwjpegDecompressor *decomp = reinterpret_cast<CLibhwjpegDecompressor *>(cinfo);
    return decomp->PrepareDecompression();
}

bool hwjpeg_start_decompress(hwjpeg_decompress_ptr cinfo) {
    CLibhwjpegDecompressor *decomp = reinterpret_cast<CLibhwjpegDecompressor *>(cinfo);
    return decomp->Decompress();
}

void hwjpeg_destroy_decompress(hwjpeg_decompress_ptr cinfo) {
    CLibhwjpegDecompressor *decomp = reinterpret_cast<CLibhwjpegDecompressor *>(cinfo);
    delete decomp;
}

bool hwjpeg_has_enough_stream_buffer(hwjpeg_decompress_ptr cinfo) {
    CLibhwjpegDecompressor *decomp = reinterpret_cast<CLibhwjpegDecompressor *>(cinfo);
    return decomp->IsEnoughStreamBuffer();
}
