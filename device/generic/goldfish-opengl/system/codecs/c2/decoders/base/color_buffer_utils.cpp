/*
 * Copyright (C) 2021 The Android Open Source Project
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
#include <inttypes.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <log/log.h>
#include <gralloc_cb_bp.h>
#include <cb_handle_30.h>
#include <xf86drm.h>

#include <C2AllocatorGralloc.h>

#include "cros_gralloc_handle.h"
#include "virtgpu_drm.h"

static bool isMinigbmFromProperty() {
  static constexpr const auto kGrallocProp = "ro.hardware.gralloc";

  const auto grallocProp = android::base::GetProperty(kGrallocProp, "");
  ALOGD("%s:codecs: minigbm query prop value is: %s", __FUNCTION__, grallocProp.c_str());

  if (grallocProp == "minigbm") {
    ALOGD("%s:codecs: Using minigbm, in minigbm mode.\n", __FUNCTION__);
    return true;
  } else {
    ALOGD("%s:codecs: Is not using minigbm, in goldfish mode.\n", __FUNCTION__);
    return false;
  }
}

class ColorBufferUtilsGlobalState {
public:
    ColorBufferUtilsGlobalState() {
        m_isMinigbm = isMinigbmFromProperty();

        if (m_isMinigbm) {
            static constexpr int kRendernodeMinor = 128;
            m_rendernodeFd = drmOpenRender(kRendernodeMinor);
        }
    }

    uint32_t getColorBufferHandle(native_handle_t const* handle) {
        if (m_isMinigbm) {
            struct drm_virtgpu_resource_info info;
            if (!getResInfo(handle, &info)) {
                ALOGE("%s: Error gtting color buffer handle (minigbm case)", __func__);
                return -1;
            }
            return info.res_handle;
        } else {
            return cb_handle_t::from(handle)->hostHandle;
        }
    }

private:

    bool getResInfo(native_handle_t const* handle,
                    struct drm_virtgpu_resource_info* info) {
        memset(info, 0x0, sizeof(*info));
        if (m_rendernodeFd < 0) {
            ALOGE("%s: Error, rendernode fd missing\n", __func__);
            return false;
        }

        struct drm_gem_close gem_close;
        memset(&gem_close, 0x0, sizeof(gem_close));

        cros_gralloc_handle const* cros_handle =
            reinterpret_cast<cros_gralloc_handle const*>(handle);

        uint32_t prime_handle;
        int ret = drmPrimeFDToHandle(m_rendernodeFd, cros_handle->fds[0], &prime_handle);
        if (ret) {
            ALOGE("%s: DRM_IOCTL_PRIME_FD_TO_HANDLE failed: %s (errno %d)\n",
                  __func__, strerror(errno), errno);
            return false;
        }

        info->bo_handle = prime_handle;
        gem_close.handle = prime_handle;

        ret = drmIoctl(m_rendernodeFd, DRM_IOCTL_VIRTGPU_RESOURCE_INFO, info);
        if (ret) {
            ALOGE("%s: DRM_IOCTL_VIRTGPU_RESOURCE_INFO failed: %s (errno %d)\n",
                  __func__, strerror(errno), errno);
            drmIoctl(m_rendernodeFd, DRM_IOCTL_GEM_CLOSE, &gem_close);
            return false;
        }

        drmIoctl(m_rendernodeFd, DRM_IOCTL_GEM_CLOSE, &gem_close);
        return true;
    }

    bool m_isMinigbm;
    int m_rendernodeFd = -1; // to be closed when this process dies
};

static ColorBufferUtilsGlobalState* getGlobals() {
    static ColorBufferUtilsGlobalState* globals = new ColorBufferUtilsGlobalState;
    return globals;
}

uint32_t getColorBufferHandle(native_handle_t const* handle) {
    return getGlobals()->getColorBufferHandle(handle);
}

uint64_t getClientUsage(const std::shared_ptr<C2BlockPool> &pool) {
      std::shared_ptr<C2GraphicBlock> myOutBlock;
      const C2MemoryUsage usage = {0, 0};
      const uint32_t format = HAL_PIXEL_FORMAT_YCBCR_420_888;
      pool->fetchGraphicBlock(2, 2, format, usage, &myOutBlock);
      auto myc2Handle = myOutBlock->handle();
      native_handle_t *mygrallocHandle =
      android::UnwrapNativeCodec2GrallocHandle(myc2Handle);
      cb_handle_30_t* mycb = (cb_handle_30_t*)(mygrallocHandle);
      ALOGV("%s %s %d: client usage 0x%x", __FILE__, __func__, __LINE__, mycb->usage);
      return mycb->usage;
}

