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

// NOLINTNEXTLINE(cppcoreguidelines-macro-usage)
#define ATRACE_TAG ATRACE_TAG_GRAPHICS
// NOLINTNEXTLINE(cppcoreguidelines-macro-usage)
#define LOG_TAG "hwc-platform-drm-generic"

#include "DrmFbImporter.h"

#include <hardware/gralloc.h>
#include <utils/Trace.h>
#include <xf86drm.h>
#include <xf86drmMode.h>

#include <cinttypes>
#include <system_error>

#include "utils/log.h"
#include "utils/properties.h"

namespace android {

auto DrmFbIdHandle::CreateInstance(BufferInfo *bo, GemHandle first_gem_handle,
                                   DrmDevice &drm)
    -> std::shared_ptr<DrmFbIdHandle> {
  // NOLINTNEXTLINE(misc-const-correctness)
  ATRACE_NAME("Import dmabufs and register FB");

  // NOLINTNEXTLINE(cppcoreguidelines-owning-memory): priv. constructor usage
  std::shared_ptr<DrmFbIdHandle> local(new DrmFbIdHandle(drm));

  local->gem_handles_[0] = first_gem_handle;
  int32_t err = 0;

  /* Framebuffer object creation require gem handle for every used plane */
  for (size_t i = 1; i < local->gem_handles_.size(); i++) {
    if (bo->prime_fds[i] > 0) {
      if (bo->prime_fds[i] != bo->prime_fds[0]) {
        err = drmPrimeFDToHandle(*drm.GetFd(), bo->prime_fds[i],
                                 &local->gem_handles_.at(i));
        if (err != 0) {
          ALOGE("failed to import prime fd %d errno=%d", bo->prime_fds[i],
                errno);
        }
      } else {
        local->gem_handles_.at(i) = local->gem_handles_[0];
      }
    }
  }

  auto has_modifiers = bo->modifiers[0] != DRM_FORMAT_MOD_NONE &&
                       bo->modifiers[0] != DRM_FORMAT_MOD_INVALID;

  if (!drm.HasAddFb2ModifiersSupport() && has_modifiers) {
    ALOGE("No ADDFB2 with modifier support. Can't import modifier %" PRIu64,
          bo->modifiers[0]);
    local.reset();
    return local;
  }

  /* Create framebuffer object */
  if (!has_modifiers) {
    err = drmModeAddFB2(*drm.GetFd(), bo->width, bo->height, bo->format,
                        local->gem_handles_.data(), &bo->pitches[0],
                        &bo->offsets[0], &local->fb_id_, 0);
  } else {
    err = drmModeAddFB2WithModifiers(*drm.GetFd(), bo->width, bo->height,
                                     bo->format, local->gem_handles_.data(),
                                     &bo->pitches[0], &bo->offsets[0],
                                     &bo->modifiers[0], &local->fb_id_,
                                     DRM_MODE_FB_MODIFIERS);
  }
  if (err != 0) {
    ALOGE("could not create drm fb %d", err);
    local.reset();
  }

  return local;
}

DrmFbIdHandle::~DrmFbIdHandle() {
  // NOLINTNEXTLINE(misc-const-correctness)
  ATRACE_NAME("Close FB and dmabufs");

  /* Destroy framebuffer object */
  if (drmModeRmFB(*drm_->GetFd(), fb_id_) != 0) {
    ALOGE("Failed to rm fb");
  }

  /* Close GEM handles.
   *
   * WARNING: TODO(nobody):
   * From Linux side libweston relies on libgbm to get KMS handle and never
   * closes it (handle is closed by libgbm on buffer destruction)
   * Probably we should offer similar approach to users (at least on user
   * request via system properties)
   */
  struct drm_gem_close gem_close {};
  for (size_t i = 0; i < gem_handles_.size(); i++) {
    /* Don't close invalid handle. Close handle only once in cases
     * where several YUV planes located in the single buffer. */
    if (gem_handles_[i] == 0 ||
        (i != 0 && gem_handles_[i] == gem_handles_[0])) {
      continue;
    }
    gem_close.handle = gem_handles_[i];
    auto err = drmIoctl(*drm_->GetFd(), DRM_IOCTL_GEM_CLOSE, &gem_close);
    if (err != 0) {
      ALOGE("Failed to close gem handle %d, errno: %d", gem_handles_[i], errno);
    }
  }
}

auto DrmFbImporter::GetOrCreateFbId(BufferInfo *bo)
    -> std::shared_ptr<DrmFbIdHandle> {
  /* Lookup DrmFbIdHandle in cache first. First handle serves as a cache key. */
  GemHandle first_handle = 0;
  auto err = drmPrimeFDToHandle(*drm_->GetFd(), bo->prime_fds[0],
                                &first_handle);

  if (err != 0) {
    ALOGE("Failed to import prime fd %d ret=%d", bo->prime_fds[0], err);
    return {};
  }

  auto drm_fb_id_cached = drm_fb_id_handle_cache_.find(first_handle);

  if (drm_fb_id_cached != drm_fb_id_handle_cache_.end()) {
    if (auto drm_fb_id_handle_shared = drm_fb_id_cached->second.lock()) {
      return drm_fb_id_handle_shared;
    }
    drm_fb_id_handle_cache_.erase(drm_fb_id_cached);
  }

  /* Cleanup cached empty weak pointers */
  const int minimal_cleanup_size = 128;
  if (drm_fb_id_handle_cache_.size() > minimal_cleanup_size) {
    CleanupEmptyCacheElements();
  }

  /* No DrmFbIdHandle found in cache, create framebuffer object */
  auto fb_id_handle = DrmFbIdHandle::CreateInstance(bo, first_handle, *drm_);
  if (fb_id_handle) {
    drm_fb_id_handle_cache_[first_handle] = fb_id_handle;
  }

  return fb_id_handle;
}

}  // namespace android
