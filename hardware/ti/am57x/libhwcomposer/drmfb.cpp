/*
 * Copyright (C) Texas Instruments Incorporated - http://www.ti.com/
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

#include <log/log.h>
#include <drm/drm_fourcc.h>
#include <hardware/hwcomposer.h>

#include <xf86drm.h>
#include <xf86drmMode.h>

#include "drmfb.h"
#include "format.h"
#include "img_gralloc1_public.h"

DRMFramebuffer::DRMFramebuffer(int drm_fd, buffer_handle_t handle, bool is_overlay) :
    bo(), pitches(), offsets()
{
    if (!handle)
        return;

    uint32_t gem_handle;
    IMG_native_handle_t* img_hnd = (IMG_native_handle_t*)handle;
    int ret = drmPrimeFDToHandle(drm_fd, img_hnd->fd[0], &gem_handle);
    if (ret) {
        ALOGE("Failed to get DRM buffer object from handle");
        return;
    }

    this->width = img_hnd->iWidth;
    this->height = img_hnd->iHeight;
    this->format = convert_hal_to_drm_format(img_hnd->iFormat, true);
    this->bo[0] = gem_handle;
    this->pitches[0] = ALIGN(img_hnd->iWidth, HW_ALIGN) * get_format_bpp(img_hnd->iFormat) >> 3;
    this->offsets[0] = 0;
    this->drm_fd = drm_fd;

    if (is_overlay) {
        switch (this->format) {
        case DRM_FORMAT_NV12:
            this->bo[1] = gem_handle;

            this->pitches[0] = ALIGN(img_hnd->iWidth, HW_ALIGN);
            this->pitches[1] = this->pitches[0];

            this->offsets[1] = this->pitches[0] * img_hnd->iHeight;
            break;
        case DRM_FORMAT_ARGB8888:
        case DRM_FORMAT_XRGB8888:
        case DRM_FORMAT_RGB565:
            break;
        default:
            ALOGE("Bad format for overlay");
            return;
        }
    }

    ret = drmModeAddFB2(drm_fd, this->width, this->height,
                        this->format, this->bo,
                        this->pitches, this->offsets,
                        &this->fb_id, 0);
    if (ret) {
        ALOGE("Could not create DRM frame buffer %d", ret);
        return;
    }
}

DRMFramebuffer::~DRMFramebuffer()
{
    if (this->fb_id) {
        if (drmModeRmFB(this->drm_fd, this->fb_id))
            ALOGE("Failed to remove DRM frame buffer");
    }

    for (size_t i = 0; i < 4; i++) {
        if (this->bo[i]) {
            struct drm_gem_close close_args = {
                close_args.handle = this->bo[i],
                close_args.pad = 0,
            };
            int ret = drmIoctl(this->drm_fd, DRM_IOCTL_GEM_CLOSE, &close_args);
            if (ret) {
                ALOGE("Failed to close GEM handle");
                return;
            }
        }
    }
}
