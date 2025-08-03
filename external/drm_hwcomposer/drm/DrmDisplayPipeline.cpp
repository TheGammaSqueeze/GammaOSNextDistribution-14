/*
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

#define LOG_TAG "hwc-drm-display-pipeline"

#include "DrmDisplayPipeline.h"

#include "DrmAtomicStateManager.h"
#include "DrmConnector.h"
#include "DrmCrtc.h"
#include "DrmDevice.h"
#include "DrmEncoder.h"
#include "DrmPlane.h"
#include "utils/log.h"
#include "utils/properties.h"

namespace android {

template <class O>
auto PipelineBindable<O>::BindPipeline(DrmDisplayPipeline *pipeline,
                                       bool return_object_if_bound)
    -> std::shared_ptr<BindingOwner<O>> {
  auto owner_object = owner_object_.lock();
  if (owner_object) {
    if (bound_pipeline_ == pipeline && return_object_if_bound) {
      return owner_object;
    }

    return {};
  }
  owner_object = std::make_shared<BindingOwner<O>>(static_cast<O *>(this));

  owner_object_ = owner_object;
  bound_pipeline_ = pipeline;
  return owner_object;
}

static auto TryCreatePipeline(DrmDevice &dev, DrmConnector &connector,
                              DrmEncoder &enc, DrmCrtc &crtc)
    -> std::unique_ptr<DrmDisplayPipeline> {
  /* Check if resources are available */

  auto pipe = std::make_unique<DrmDisplayPipeline>();
  pipe->device = &dev;

  pipe->connector = connector.BindPipeline(pipe.get());
  pipe->encoder = enc.BindPipeline(pipe.get());
  pipe->crtc = crtc.BindPipeline(pipe.get());

  if (!pipe->connector || !pipe->encoder || !pipe->crtc) {
    return {};
  }

  std::vector<DrmPlane *> primary_planes;
  std::vector<DrmPlane *> overlay_planes;

  /* Attach necessary resources */
  auto display_planes = std::vector<DrmPlane *>();
  for (const auto &plane : dev.GetPlanes()) {
    if (plane->IsCrtcSupported(crtc)) {
      if (plane->GetType() == DRM_PLANE_TYPE_PRIMARY) {
        primary_planes.emplace_back(plane.get());
      } else if (plane->GetType() == DRM_PLANE_TYPE_OVERLAY) {
        overlay_planes.emplace_back(plane.get());
      } else {
        ALOGI("Ignoring cursor plane %d", plane->GetId());
      }
    }
  }

  if (primary_planes.empty()) {
    ALOGE("Primary plane for CRTC %d not found", crtc.GetId());
    return {};
  }

  for (const auto &plane : primary_planes) {
    pipe->primary_plane = plane->BindPipeline(pipe.get());
    if (pipe->primary_plane) {
      break;
    }
  }

  if (!pipe->primary_plane) {
    ALOGE("Failed to bind primary plane");
    return {};
  }

  pipe->atomic_state_manager = DrmAtomicStateManager::CreateInstance(
      pipe.get());

  return pipe;
}

static auto TryCreatePipelineUsingEncoder(DrmDevice &dev, DrmConnector &conn,
                                          DrmEncoder &enc)
    -> std::unique_ptr<DrmDisplayPipeline> {
  /* First try to use the currently-bound crtc */
  auto *crtc = dev.FindCrtcById(enc.GetCurrentCrtcId());
  if (crtc != nullptr) {
    auto pipeline = TryCreatePipeline(dev, conn, enc, *crtc);
    if (pipeline) {
      return pipeline;
    }
  }

  /* Try to find a possible crtc which will work */
  for (const auto &crtc : dev.GetCrtcs()) {
    if (enc.SupportsCrtc(*crtc)) {
      auto pipeline = TryCreatePipeline(dev, conn, enc, *crtc);
      if (pipeline) {
        return pipeline;
      }
    }
  }

  /* We can't use this encoder, but nothing went wrong, try another one */
  return {};
}

auto DrmDisplayPipeline::CreatePipeline(DrmConnector &connector)
    -> std::unique_ptr<DrmDisplayPipeline> {
  auto &dev = connector.GetDev();
  /* Try to use current setup first */
  auto *encoder = dev.FindEncoderById(connector.GetCurrentEncoderId());

  if (encoder != nullptr) {
    auto pipeline = TryCreatePipelineUsingEncoder(dev, connector, *encoder);
    if (pipeline) {
      return pipeline;
    }
  }

  for (const auto &enc : dev.GetEncoders()) {
    if (connector.SupportsEncoder(*enc)) {
      auto pipeline = TryCreatePipelineUsingEncoder(dev, connector, *enc);
      if (pipeline) {
        return pipeline;
      }
    }
  }

  ALOGE("Could not find a suitable encoder/crtc for connector %s",
        connector.GetName().c_str());

  return {};
}

static bool ReadUseOverlayProperty() {
  char use_overlay_planes_prop[PROPERTY_VALUE_MAX];
  property_get("vendor.hwc.drm.use_overlay_planes", use_overlay_planes_prop,
               "1");
  constexpr int kStrtolBase = 10;
  return strtol(use_overlay_planes_prop, nullptr, kStrtolBase) != 0;
}

auto DrmDisplayPipeline::GetUsablePlanes()
    -> std::vector<std::shared_ptr<BindingOwner<DrmPlane>>> {
  std::vector<std::shared_ptr<BindingOwner<DrmPlane>>> planes;
  planes.emplace_back(primary_plane);

  const static bool kUseOverlayPlanes = ReadUseOverlayProperty();

  if (kUseOverlayPlanes) {
    for (const auto &plane : device->GetPlanes()) {
      if (plane->IsCrtcSupported(*crtc->Get())) {
        if (plane->GetType() == DRM_PLANE_TYPE_OVERLAY) {
          auto op = plane->BindPipeline(this, true);
          if (op) {
            planes.emplace_back(op);
          }
        }
      }
    }
  }

  return planes;
}

DrmDisplayPipeline::~DrmDisplayPipeline() {
  if (atomic_state_manager)
    atomic_state_manager->StopThread();
}

}  // namespace android
