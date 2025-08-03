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

#pragma once

#include <memory>
#include <vector>

namespace android {

class DrmConnector;
class DrmDevice;
class DrmPlane;
class DrmCrtc;
class DrmEncoder;
class DrmAtomicStateManager;

struct DrmDisplayPipeline;

template <class O>
class BindingOwner;

template <class O>
class PipelineBindable {
  friend class BindingOwner<O>;

 public:
  auto *GetPipeline() {
    return bound_pipeline_;
  }

  auto BindPipeline(DrmDisplayPipeline *pipeline,
                    bool return_object_if_bound = false)
      -> std::shared_ptr<BindingOwner<O>>;

 private:
  DrmDisplayPipeline *bound_pipeline_;
  std::weak_ptr<BindingOwner<O>> owner_object_;
};

template <class B>
class BindingOwner {
 public:
  explicit BindingOwner(B *pb) : bindable_(pb){};
  ~BindingOwner() {
    bindable_->bound_pipeline_ = nullptr;
  }

  B *Get() {
    return bindable_;
  }

 private:
  B *const bindable_;
};

struct DrmDisplayPipeline {
  static auto CreatePipeline(DrmConnector &connector)
      -> std::unique_ptr<DrmDisplayPipeline>;

  auto GetUsablePlanes()
      -> std::vector<std::shared_ptr<BindingOwner<DrmPlane>>>;

  ~DrmDisplayPipeline();

  DrmDevice *device;

  std::shared_ptr<BindingOwner<DrmConnector>> connector;
  std::shared_ptr<BindingOwner<DrmEncoder>> encoder;
  std::shared_ptr<BindingOwner<DrmCrtc>> crtc;
  std::shared_ptr<BindingOwner<DrmPlane>> primary_plane;

  std::shared_ptr<DrmAtomicStateManager> atomic_state_manager;
};

}  // namespace android
