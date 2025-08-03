/*
 * Copyright (C) 2012 The Android Open Source Project
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

/*
 * @file    csc.h
 *
 * @brief   color space convertion abstract header
 *
 * @author  Pyoungjae Jung (pjet.jung@samsung.com)
 *
 * @version 1.0
 *
 * @history
 *   2011.12.27 : Create
 */

#ifndef CSC_H
#define CSC_H

#ifdef __cplusplus
extern "C" {
#endif

#define CSC_MAX_PLANES 3

typedef enum _CSC_ERRORCODE {
    CSC_ErrorNone = 0,
    CSC_Error,
    CSC_ErrorNotInit,
    CSC_ErrorInvalidAddress,
    CSC_ErrorUnsupportFormat,
    CSC_ErrorNotImplemented
} CSC_ERRORCODE;

typedef enum _CSC_METHOD {
    CSC_METHOD_SW = 0,
    CSC_METHOD_HW
} CSC_METHOD;

typedef enum _CSC_HW_PROPERTY_TYPE {
    CSC_HW_PROPERTY_FIXED_NODE = 0,
    CSC_HW_PROPERTY_MODE_DRM,
} CSC_HW_PROPERTY_TYPE;

typedef enum _CSC_MEMTYPE {
    CSC_MEMORY_MMAP = 1,
    CSC_MEMORY_USERPTR,
    CSC_MEMORY_OVERLAY,
    CSC_MEMORY_DMABUF,
    CSC_MEMORY_MFC,
} CSC_MEMTYPE;

typedef enum _CSC_HW_ID {
    CSC_HW_GSC0 = 0,
    CSC_HW_GSC1,
    CSC_HW_GSC2,
    CSC_HW_GSC3,
    CSC_HW_SC0,
    CSC_HW_SC1,
    CSC_HW_SC2,
    CSC_HW_MAX,
} CSC_HW_ID;

typedef enum _CSC_PLANE {
    CSC_Y_PLANE = 0,
    CSC_RGB_PLANE = 0,
    CSC_U_PLANE = 1,
    CSC_UV_PLANE = 1,
    CSC_V_PLANE = 2
} CSC_PLANE;

typedef enum _CSC_HW_TYPE {
    CSC_HW_TYPE_FIMC = 0,
    CSC_HW_TYPE_GSCALER
} CSC_HW_TYPE;

typedef enum _CSC_EQ_MODE {
    CSC_EQ_MODE_USER = 0,
    CSC_EQ_MODE_AUTO
} CSC_EQ_MODE;

typedef enum _CSC_EQ_COLORSPACE {
    CSC_EQ_COLORSPACE_SMPTE170M     = 1,
    CSC_EQ_COLORSPACE_SMPTE240M     = 2,
    CSC_EQ_COLORSPACE_REC709        = 3,
    CSC_EQ_COLORSPACE_BT878         = 4,
    CSC_EQ_COLORSPACE_470_SYSTEM_M  = 5,
    CSC_EQ_COLORSPACE_470_SYSTEM_BG = 6,
    CSC_EQ_COLORSPACE_BT2020        = 10,
} CSC_EQ_COLORSPACE;

typedef enum _CSC_EQ_RANGE {
    CSC_EQ_RANGE_NARROW = 0,
    CSC_EQ_RANGE_FULL
} CSC_EQ_RANGE;

typedef enum _CSC_HW_FILTER {
    CSC_FT_NONE = 0,
    CSC_FT_BLUR,
    CSC_FT_240,
    CSC_FT_480,
    CSC_FT_720,
    CSC_FT_960,
    CSC_FT_1080,
    CSC_FT_MAX
} CSC_HW_FILTER;

typedef struct _CSC_FORMAT {
    unsigned int width;
    unsigned int height;
    unsigned int crop_left;
    unsigned int crop_top;
    unsigned int crop_width;
    unsigned int crop_height;
    unsigned int color_format;
    unsigned int cacheable;
    unsigned int mode_drm;
} CSC_FORMAT;

typedef struct _CSC_BUFFER {
    void *planes[CSC_MAX_PLANES];
    int mem_type;
} CSC_BUFFER;

typedef struct _CSC_HW_PROPERTY {
    int fixed_node;
    int mode_drm;
} CSC_HW_PROPERTY;

typedef struct _CSC_HANDLE {
    CSC_FORMAT      dst_format;
    CSC_FORMAT      src_format;
    CSC_BUFFER      dst_buffer;
    CSC_BUFFER      src_buffer;
    CSC_METHOD      csc_method;
    CSC_HW_TYPE     csc_hw_type;
    void           *csc_hw_handle;
    CSC_HW_PROPERTY hw_property;

    /* CSC Equation */
    CSC_EQ_MODE       csc_mode;
    CSC_EQ_RANGE      csc_range;
    CSC_EQ_COLORSPACE colorspace;

    /* Denoising filter */
    CSC_HW_FILTER    filter;

    unsigned int     frame_rate;
} CSC_HANDLE;

/*
 * change hal pixel format to omx pixel format
 *
 * @param hal_format
 *   hal pixel format[in]
 *
 * @return
 *   omx pixel format
 */
unsigned int hal_2_omx_pixel_format(
    unsigned int hal_format);

/*
 * change omx pixel format to hal pixel format
 *
 * @param hal_format
 *   omx pixel format[in]
 *
 * @return
 *   hal pixel format
 */
unsigned int omx_2_hal_pixel_format(
    unsigned int omx_format);

/*
 * Init CSC handle
 *
 * @return
 *   csc handle
 */
void *csc_init(
    CSC_METHOD method);

/*
 * Deinit CSC handle
 *
 * @param handle
 *   CSC handle[in]
 *
 * @return
 *   error code
 */
CSC_ERRORCODE csc_deinit(
    void *handle);

/*
 * get color space converter method
 *
 * @param handle
 *   CSC handle[in]
 *
 * @param method
 *   CSC method[out]
 *
 * @return
 *   error code
 */
CSC_ERRORCODE csc_get_method(
    void           *handle,
    CSC_METHOD     *method);

/*
 * set color space converter method
 *
 * @param handle
 *   CSC handle[in]
 *
 * @param method
 *   CSC method[in]
 *
 * @return
 *   error code
 */
CSC_ERRORCODE csc_set_method(
    void           *handle,
    CSC_METHOD      method);

/*
 * Set hw property
 *
 * @param handle
 *   CSC handle[in]
 *
 * @param property
 *   csc hw property[in]
 *
 * @param value
 *   csc hw property value[in]
 *
 * @return
 *   csc handle
 */
CSC_ERRORCODE csc_set_hw_property(
    void                *handle,
    CSC_HW_PROPERTY_TYPE property,
    int                  value);

/*
 * Get csc equation property.
 *
 * @param handle
 *   CSC handle[in]
 *
 * @param mode
 *   csc equation mode[out]
 *
 * @param colorspace
 *   csc color space[out]
 *
 * @param range
 *   csc equation range[out]
 *
 * @return
 *   error code
 */
CSC_ERRORCODE csc_get_eq_property(
    void              *handle,
    CSC_EQ_MODE       *csc_mode,
    CSC_EQ_RANGE      *csc_range,
    CSC_EQ_COLORSPACE *colorspace);

/*
 * Set csc equation property.
 *
 * @param handle
 *   CSC handle[in]
 *
 * @param mode
 *   csc equation mode[in]
 *
 * @param colorspace
 *   csc color space[in]
 *
 * @param range
 *   csc equation range[in]
 *
 * @return
 *   error code
 */
CSC_ERRORCODE csc_set_eq_property(
    void              *handle,
    CSC_EQ_MODE        csc_mode,
    CSC_EQ_RANGE       csc_range,
    CSC_EQ_COLORSPACE  colorspace);

/*
 * Set csc filter property.
 *
 * @param handle
 *   CSC handle[in]
 *
 * @param filter
 *   csc filter info[in]
 *
 * @return
 *   error code
 */
CSC_ERRORCODE csc_set_filter_property(
    void              *handle,
    CSC_HW_FILTER     filter);

/*
 * Set framerate.
 *
 * @param handle
 *   CSC handle[in]
 *
 * @param frame_rate
 *   frame rate[in]
 *
 * @return
 *   error code
 */
CSC_ERRORCODE csc_set_framerate(
    void              *handle,
    unsigned int      frame_rate);

/*
 * Get source format.
 *
 * @param handle
 *   CSC handle[in]
 *
 * @param width
 *   address of image width[out]
 *
 * @param height
 *   address of image height[out]
 *
 * @param crop_left
 *   address of image left crop size[out]
 *
 * @param crop_top
 *   address of image top crop size[out]
 *
 * @param crop_width
 *   address of cropped image width[out]
 *
 * @param crop_height
 *   address of cropped image height[out]
 *
 * @param color_format
 *   address of source color format(HAL format)[out]
 *
 * @return
 *   error code
 */
CSC_ERRORCODE csc_get_src_format(
    void           *handle,
    unsigned int   *width,
    unsigned int   *height,
    unsigned int   *crop_left,
    unsigned int   *crop_top,
    unsigned int   *crop_width,
    unsigned int   *crop_height,
    unsigned int   *color_format,
    unsigned int   *cacheable);

/*
 * Set source format.
 * Don't call each converting time.
 * Pls call this function as below.
 *   1. first converting time
 *   2. format is changed
 *
 * @param handle
 *   CSC handle[in]
 *
 * @param width
 *   image width[in]
 *
 * @param height
 *   image height[in]
 *
 * @param crop_left
 *   image left crop size[in]
 *
 * @param crop_top
 *   image top crop size[in]
 *
 * @param crop_width
 *   cropped image width[in]
 *
 * @param crop_height
 *   cropped image height[in]
 *
 * @param color_format
 *   source color format(HAL format)[in]
 *
 * @return
 *   error code
 */
CSC_ERRORCODE csc_set_src_format(
    void           *handle,
    unsigned int    width,
    unsigned int    height,
    unsigned int    crop_left,
    unsigned int    crop_top,
    unsigned int    crop_width,
    unsigned int    crop_height,
    unsigned int    color_format,
    unsigned int    cacheable);

/*
 * Get destination format.
 *
 * @param handle
 *   CSC handle[in]
 *
 * @param width
 *   address of image width[out]
 *
 * @param height
 *   address of image height[out]
 *
 * @param crop_left
 *   address of image left crop size[out]
 *
 * @param crop_top
 *   address of image top crop size[out]
 *
 * @param crop_width
 *   address of cropped image width[out]
 *
 * @param crop_height
 *   address of cropped image height[out]
 *
 * @param color_format
 *   address of color format(HAL format)[out]
 *
 * @return
 *   error code
 */
CSC_ERRORCODE csc_get_dst_format(
    void           *handle,
    unsigned int   *width,
    unsigned int   *height,
    unsigned int   *crop_left,
    unsigned int   *crop_top,
    unsigned int   *crop_width,
    unsigned int   *crop_height,
    unsigned int   *color_format,
    unsigned int   *cacheable);

/*
 * Set destination format
 * Don't call each converting time.
 * Pls call this function as below.
 *   1. first converting time
 *   2. format is changed
 *
 * @param handle
 *   CSC handle[in]
 *
 * @param width
 *   image width[in]
 *
 * @param height
 *   image height[in]
 *
 * @param crop_left
 *   image left crop size[in]
 *
 * @param crop_top
 *   image top crop size[in]
 *
 * @param crop_width
 *   cropped image width[in]
 *
 * @param crop_height
 *   cropped image height[in]
 *
 * @param color_format
 *   destination color format(HAL format)[in]
 *
 * @return
 *   error code
 */
CSC_ERRORCODE csc_set_dst_format(
    void           *handle,
    unsigned int    width,
    unsigned int    height,
    unsigned int    crop_left,
    unsigned int    crop_top,
    unsigned int    crop_width,
    unsigned int    crop_height,
    unsigned int    color_format,
    unsigned int    cacheable);

/*
 * Setup source buffer
 * set_format func should be called before this this func.
 *
 * @param handle
 *   CSC handle[in]
 *
 * @param src_buffer
 *   source buffer pointer array[in]
 *
 * @param y
 *   y or RGB destination pointer[in]
 *
 * @param u
 *   u or uv destination pointer[in]
 *
 * @param v
 *   v or none destination pointer[in]
 *
 * @return
 *   error code
 */
CSC_ERRORCODE csc_set_src_buffer(
    void *handle,
    void *addr[CSC_MAX_PLANES],
    int mem_type);

/*
 * Setup destination buffer
 *
 * @param handle
 *   CSC handle[in]
 *
 * @param y
 *   y or RGB destination pointer[in]
 *
 * @param u
 *   u or uv destination pointer[in]
 *
 * @param v
 *   v or none destination pointer[in]
 *
 * @return
 *   error code
 */
CSC_ERRORCODE csc_set_dst_buffer(
    void *handle,
    void *addr[CSC_MAX_PLANES],
    int mem_type);

/*
 * Convert color space with presetup color format
 *
 * @param handle
 *   CSC handle[in]
 *
 * @return
 *   error code
 */
CSC_ERRORCODE csc_convert(
    void *handle);

CSC_ERRORCODE csc_convert_with_rotation(
    void *handle, int rotation, int flip_horizontal, int flip_vertical);

#ifdef __cplusplus
}
#endif

#endif
