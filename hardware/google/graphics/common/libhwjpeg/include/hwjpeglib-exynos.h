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

#ifndef __HARDWARE_SAMSUNG_EXYNOS_HWJPEGDECOMPRESSOR_H__
#define __HARDWARE_SAMSUNG_EXYNOS_HWJPEGDECOMPRESSOR_H__

#ifdef __cplusplus
extern "C" {

/*
 * hwjpeg_decompress_ptr - handle of decompressor instance
 */
typedef struct hwjpeg_decompressor_struct {
    unsigned int image_width;     /* width of the compressed image */
    unsigned int image_height;    /* height of the compressed image */
    unsigned char num_components; /* number of components of the compressed image */
    unsigned char
            chroma_h_samp_factor; /* horizontal chroma sampling factor of the compressed image */
    unsigned char
            chroma_v_samp_factor; /* vertical chroma sampling factor of the compressed image */
    unsigned char scale_factor; /* down-scaling factor during decompression: one of 1, 2, 4 and 8 */

    unsigned int output_width;  /* width of the output image (image_width/scale_factor) */
    unsigned int output_height; /* height of the output image (image_height/scale_factor) */
    __u32 output_format; /* 4CC style format identifier of the output image defined in videodev2.h
                          */
} *hwjpeg_decompress_ptr;

/*
 * hwjpeg_create_decompress - create an instance of decompressor
 *
 * @return: the handle of the decompressor instance. NULL on failure.
 *
 * The decompresson process starts from calling this function. The return value
 * is the handle of the decompressor instance. Every step of the decompression
 * process needs the handle. The handle should be destroyed with
 * hwjpeg_destroy_decompress() when it is no longer required.
 */
hwjpeg_decompress_ptr hwjpeg_create_decompress(void);

/*
 * hwjpeg_file_src - configure the path to a file of compressed JPEG stream
 *
 * @cinfo: decompressor instance handle
 * @path: path to the file of compressed JPEG stream.
 *        It is assumed that EOI is at the end of the file.
 * @return: false on failure
 */
bool hwjpeg_file_src(hwjpeg_decompress_ptr cinfo, const char *path);

/*
 * hwjpeg_dmabuf_src - configure the buffer file descriptor that contains the compressed JPEG stream
 *
 * @cinfo: decompressor instance handle
 * @infd: the file descriptor exported by ION of the buffer that contains the compressed JPEG stream
 * @insize: the length in bytes of @infd. It is assumed that EOI is at the end of the buffer.
 * @dummybytes: The available dummy bytes after @insize.
 * @return: false on failure
 */
bool hwjpeg_dmabuf_src(hwjpeg_decompress_ptr cinfo, int infd, size_t insize, size_t dummybytes);

/*
 * hwjpeg_mem_src - configure the buffer that contains the compressed JPEG stream
 *
 * @cinfo: decompressor instance handle
 * @inbuffer: the address of the buffer that contains the compressed JPEG stream
 * @insize: the length in bytes of @inbuffer. It is assumed that EOI is at the end of the buffer.
 * @dummybytes: The available dummy bytes after @insize.
 * @return: false on failure
 */
bool hwjpeg_mem_src(hwjpeg_decompress_ptr cinfo, unsigned char *inbuffer, size_t insize,
                    size_t dummybytes);

/*
 * hwjpeg_config_image_format - configure output image format
 *
 * @cinfo: decompressor instance handle
 * @v4l2_pix_fmt: fourcc format identifier defined in linux/videodev2.h
 */
void hwjpeg_config_image_format(hwjpeg_decompress_ptr cinfo, __u32 v4l2_pix_fmt);

/*
 * hwjpeg_mem_dst - configure the buffer to store decompressed image
 *
 * @cinfo: decompressor instance handle
 * @outbuffer: The array of addresses of the buffers to stroe decompressed image
 *             The maximum number of elements of @outbuffer is 3. The number of elements
 *             of @outbuffer depends on the image format configured by hwjpeg_config_image_format().
 * @outsize: The lengths in bytes of the buffers of @outbuffer.
 * @num_buffers: The number of elements in @outsizes and @outbuffer
 * @return: false on failure.
 */
bool hwjpeg_mem_dst(hwjpeg_decompress_ptr cinfo, unsigned char *outbuffer[], size_t outsize[],
                    unsigned int num_buffers);

/*
 * hwjpeg_dmabuf_dst - configure the buffer to store decompressed image
 *
 * @cinfo: decompressor instance handle
 * @outfd: The array of file descriptors exported by ION of the buffers to stroe decompressed image
 *         The maximum number of elements of @outfd is 3. The number of elements of @outfd depends
 *         on the image format configured by hwjpeg_config_image_format().
 * @outsizes: The lengths in bytes of the buffers of @outfd.
 * @num_buffers: The number of elements in @outsizes and @outfd
 * @return: false on failure.
 */
bool hwjpeg_dmabuf_dst(hwjpeg_decompress_ptr cinfo, int outfd[], size_t outsize[],
                       unsigned int num_buffers);

/*
 * hwjpeg_set_downscale_factor - configure the downscaling factor during decompression
 *
 * @cinfo: decompressor instance handle
 * @factor: downscaling factor. @factor should be one of 1, 2, 4 and 8.
 *
 * Downscaling factor is the inverse number of the downscaling ratio. @cinfo->output_width and
 * @cinfo->output_height is decided by @factor.
 *  - @cinfo->output_width = @cinfo->image_width / @factor
 *  - @cinfo->output_height = @cinfo->image_height / @factor
 * Note that both of @cinfo->image_width / @factor and @cinfo->image_height / @factor
 * should be also integers. The results should be also even number according to the
 * output image format configured by hwjpeg_config_image_format().
 * Otherwise, the decompression will fail.
 */
void hwjpeg_set_downscale_factor(hwjpeg_decompress_ptr cinfo, unsigned int factor);

/*
 * hwjpeg_read_header - reads the headers of the compressed JPEG stream
 *
 * @cinfo: decompressor instance handle
 * @return: false on failure.
 *
 * NOTE that the fields of hwjpeg_decompression_ptr is available after hwjpeg_read_header()
 * returns true.
 */
bool hwjpeg_read_header(hwjpeg_decompress_ptr cinfo);

/*
 * hwjpeg_has_enough_stream_buffer - Confirm if the stream buffer is enough
 *
 * @cinfo: decompressor instance handle
 * @return: true if the stream buffer is enough to decompress by H/W
 *
 * This function should be called after hwjpeg_read_header() is called
 * successfully.
 */
bool hwjpeg_has_enough_stream_buffer(hwjpeg_decompress_ptr cinfo);

/*
 * hwjpeg_start_decompress - starts decompression
 *
 * @cinfo: decompressor instance handle
 * @return: false on failure.
 *
 * This function blocks until the decompression finishes.
 */
bool hwjpeg_start_decompress(hwjpeg_decompress_ptr cinfo);

/*
 * hwjpeg_destroy_decompress - releases all resources of the decompressor instance
 *
 * @cinfo: decompressor instance handle to destroy
 */
void hwjpeg_destroy_decompress(hwjpeg_decompress_ptr cinfo);

};     /* extern "C" */
#endif /* __cplusplus */

#endif /*__HARDWARE_SAMSUNG_EXYNOS7420_HWJPEGDECOMPRESSOR_H__*/
