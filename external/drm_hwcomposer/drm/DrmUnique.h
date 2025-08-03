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

#pragma once

#include <xf86drmMode.h>

#include <functional>
#include <memory>

template <typename T>
using DUniquePtr = std::unique_ptr<T, std::function<void(T *)>>;

using DrmModeAtomicReqUnique = DUniquePtr<drmModeAtomicReq>;
auto inline MakeDrmModeAtomicReqUnique() {
  return DrmModeAtomicReqUnique(drmModeAtomicAlloc(), [](drmModeAtomicReq *it) {
    drmModeAtomicFree(it);
  });
};

using DrmModeConnectorUnique = DUniquePtr<drmModeConnector>;
auto inline MakeDrmModeConnectorUnique(int fd, uint32_t connector_id) {
  return DrmModeConnectorUnique(drmModeGetConnector(fd, connector_id),
                                [](drmModeConnector *it) {
                                  drmModeFreeConnector(it);
                                });
}

using DrmModeCrtcUnique = DUniquePtr<drmModeCrtc>;
auto inline MakeDrmModeCrtcUnique(int fd, uint32_t crtc_id) {
  return DrmModeCrtcUnique(drmModeGetCrtc(fd, crtc_id),
                           [](drmModeCrtc *it) { drmModeFreeCrtc(it); });
}

using DrmModeEncoderUnique = DUniquePtr<drmModeEncoder>;
auto inline MakeDrmModeEncoderUnique(int fd, uint32_t encoder_id) {
  return DrmModeEncoderUnique(drmModeGetEncoder(fd, encoder_id),
                              [](drmModeEncoder *it) {
                                drmModeFreeEncoder(it);
                              });
}

using DrmModePlaneUnique = DUniquePtr<drmModePlane>;
auto inline MakeDrmModePlaneUnique(int fd, uint32_t plane_id) {
  return DrmModePlaneUnique(drmModeGetPlane(fd, plane_id),
                            [](drmModePlane *it) { drmModeFreePlane(it); });
}

using DrmModePlaneResUnique = DUniquePtr<drmModePlaneRes>;
auto inline MakeDrmModePlaneResUnique(int fd) {
  return DrmModePlaneResUnique(drmModeGetPlaneResources(fd),
                               [](drmModePlaneRes *it) {
                                 drmModeFreePlaneResources(it);
                               });
}

using DrmModeUserPropertyBlobUnique = DUniquePtr<uint32_t /*id*/>;

using DrmModePropertyBlobUnique = DUniquePtr<drmModePropertyBlobRes>;
auto inline MakeDrmModePropertyBlobUnique(int fd, uint32_t blob_id) {
  return DrmModePropertyBlobUnique(drmModeGetPropertyBlob(fd, blob_id),
                                   [](drmModePropertyBlobRes *it) {
                                     drmModeFreePropertyBlob(it);
                                   });
}

using DrmModeResUnique = DUniquePtr<drmModeRes>;
auto inline MakeDrmModeResUnique(int fd) {
  return DrmModeResUnique(drmModeGetResources(fd),
                          [](drmModeRes *it) { drmModeFreeResources(it); });
}
