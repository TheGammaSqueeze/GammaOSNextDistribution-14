/*
 * Copyright 2023 The Android Open Source Project
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

#include "bta/ag/bta_ag_int.h"
#include "common/message_loop_thread.h"

namespace bluetooth {
namespace audio {
namespace hfp {

/*
 * Client interface for HFP.
 * Only available if HFP is managed by AIDL sessions
 */
class HfpClientInterface {
 private:
  class IClientInterfaceEndpoint {
   public:
    virtual ~IClientInterfaceEndpoint() = default;
    virtual void Cleanup() = 0;
    virtual void StartSession() = 0;
    virtual void StopSession() = 0;
    virtual void UpdateAudioConfigToHal(
        const ::hfp::offload_config& config) = 0;
    virtual void ConfirmStreamingRequest() = 0;
    virtual void CancelStreamingRequest() = 0;
  };

 public:
  class Decode : public IClientInterfaceEndpoint {
   public:
    Decode(){};
    virtual ~Decode() = default;

    void Cleanup() override;
    void StartSession() override;
    void StopSession() override;
    void UpdateAudioConfigToHal(const ::hfp::offload_config& config) override;
    void ConfirmStreamingRequest() override;
    void CancelStreamingRequest() override;
    size_t Read(uint8_t* p_buf, uint32_t len);
  };

  class Encode : public IClientInterfaceEndpoint {
   public:
    virtual ~Encode() = default;

    void Cleanup() override;
    void StartSession() override;
    void StopSession() override;
    void UpdateAudioConfigToHal(const ::hfp::offload_config& config) override;
    void ConfirmStreamingRequest() override;
    void CancelStreamingRequest() override;
    size_t Write(const uint8_t* p_buf, uint32_t len);
  };

  class Offload : public IClientInterfaceEndpoint {
   public:
    virtual ~Offload() = default;

    void Cleanup() override;
    void StartSession() override;
    void StopSession() override;
    void UpdateAudioConfigToHal(const ::hfp::offload_config& config) override;
    void ConfirmStreamingRequest() override;
    void CancelStreamingRequest() override;
    std::unordered_map<int, ::hfp::sco_config> GetHfpScoConfig();
  };

  // Get HFP software decoding client interface if it's not previously acquired
  // and not yet released.
  Decode* GetDecode(bluetooth::common::MessageLoopThread* message_loop);
  // Release interface if belongs to HFP client interface
  bool ReleaseDecode(Decode* decode);

  // Get HFP software encoding client interface if it's not previously acquired
  // and not yet released.
  Encode* GetEncode(bluetooth::common::MessageLoopThread* message_loop);
  // Release interface if belongs to HFP client interface
  bool ReleaseEncode(Encode* encode);

  // Get HFP offload client interface
  Offload* GetOffload(bluetooth::common::MessageLoopThread* message_loop);
  // Release offload interface if belongs to HFP client interface
  bool ReleaseOffload(Offload* offload);

  // Get interface, if previously not initialized - it'll initialize singleton.
  static HfpClientInterface* Get();

 private:
  static HfpClientInterface* interface;
  Decode* decode_ = nullptr;
  Encode* encode_ = nullptr;
  Offload* offload_ = nullptr;
};
}  // namespace hfp
}  // namespace audio
}  // namespace bluetooth
