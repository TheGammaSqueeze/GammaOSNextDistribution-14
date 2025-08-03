/******************************************************************************
 *
 *  Copyright 2018 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

/* Hearing Aid Profile Interface */

#include <base/functional/bind.h>
#include <base/location.h>
#include <base/logging.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_hearing_aid.h>

#include "bta_hearing_aid_api.h"
#include "btif_common.h"
#include "btif_profile_storage.h"
#include "stack/include/main_thread.h"
#include "types/raw_address.h"

using base::Bind;
using base::Unretained;
using bluetooth::hearing_aid::ConnectionState;
using bluetooth::hearing_aid::HearingAidCallbacks;
using bluetooth::hearing_aid::HearingAidInterface;

// template specialization
template <>
base::Callback<void()> jni_thread_wrapper(const base::Location& from_here,
                                          base::Callback<void()> cb) {
  return base::Bind(
      [](const base::Location& from_here, base::Callback<void()> cb) {
        do_in_jni_thread(from_here, cb);
      },
      from_here, std::move(cb));
}

namespace {
class HearingAidInterfaceImpl;
std::unique_ptr<HearingAidInterface> hearingAidInstance;

class HearingAidInterfaceImpl
    : public bluetooth::hearing_aid::HearingAidInterface,
      public HearingAidCallbacks {
  ~HearingAidInterfaceImpl() override = default;

  void Init(HearingAidCallbacks* callbacks) override {
    this->callbacks = callbacks;
    do_in_main_thread(
        FROM_HERE,
        Bind(&HearingAid::Initialize, this,
             jni_thread_wrapper(FROM_HERE,
                                Bind(&btif_storage_load_bonded_hearing_aids))));
  }

  void OnConnectionState(ConnectionState state,
                         const RawAddress& address) override {
    do_in_jni_thread(FROM_HERE, Bind(&HearingAidCallbacks::OnConnectionState,
                                     Unretained(callbacks), state, address));
  }

  void OnDeviceAvailable(uint8_t capabilities, uint64_t hiSyncId,
                         const RawAddress& address) override {
    do_in_jni_thread(FROM_HERE, Bind(&HearingAidCallbacks::OnDeviceAvailable,
                                     Unretained(callbacks), capabilities,
                                     hiSyncId, address));
  }

  void Connect(const RawAddress& address) override {
    do_in_main_thread(FROM_HERE, Bind(&HearingAid::Connect, address));
  }

  void Disconnect(const RawAddress& address) override {
    do_in_main_thread(FROM_HERE, Bind(&HearingAid::Disconnect, address));
    do_in_jni_thread(FROM_HERE, Bind(&btif_storage_set_hearing_aid_acceptlist,
                                     address, false));
  }

  void AddToAcceptlist(const RawAddress& address) override {
    do_in_main_thread(FROM_HERE, Bind(&HearingAid::AddToAcceptlist, address));
    do_in_jni_thread(FROM_HERE, Bind(&btif_storage_set_hearing_aid_acceptlist,
                                     address, true));
  }

  void SetVolume(int8_t volume) override {
    do_in_main_thread(FROM_HERE, Bind(&HearingAid::SetVolume, volume));
  }

  void RemoveDevice(const RawAddress& address) override {
    // RemoveDevice can be called on devices that don't have HA enabled
    if (HearingAid::IsHearingAidRunning()) {
      do_in_main_thread(FROM_HERE, Bind(&HearingAid::Disconnect, address));
    }

    do_in_jni_thread(FROM_HERE,
                     Bind(&btif_storage_remove_hearing_aid, address));
  }

  void Cleanup(void) override {
    do_in_main_thread(FROM_HERE, Bind(&HearingAid::CleanUp));
  }

 private:
  HearingAidCallbacks* callbacks;
};

}  // namespace

HearingAidInterface* btif_hearing_aid_get_interface() {
  if (!hearingAidInstance)
    hearingAidInstance.reset(new HearingAidInterfaceImpl());

  return hearingAidInstance.get();
}
