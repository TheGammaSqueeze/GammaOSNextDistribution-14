/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include <base/functional/bind.h>
#include <base/location.h>
#include <base/logging.h>
#include <bluetooth/log.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_csis.h>

#include "bind_helpers.h"
#include "bta_csis_api.h"
#include "btif_common.h"
#include "btif_profile_storage.h"
#include "stack/include/main_thread.h"

using base::Bind;
using base::Owned;
using base::Passed;
using base::Unretained;
using bluetooth::csis::ConnectionState;
using bluetooth::csis::CsisClientCallbacks;
using bluetooth::csis::CsisClientInterface;
using bluetooth::csis::CsisGroupLockStatus;

using bluetooth::csis::CsisClient;
using namespace bluetooth;

namespace {
std::unique_ptr<CsisClientInterface> csis_client_instance;
std::atomic_bool initialized = false;

class CsipSetCoordinatorServiceInterfaceImpl : public CsisClientInterface,
                                               public CsisClientCallbacks {
  ~CsipSetCoordinatorServiceInterfaceImpl() override = default;

  void Init(CsisClientCallbacks* callbacks) override {
    this->callbacks_ = callbacks;

    do_in_main_thread(
        FROM_HERE,
        Bind(&CsisClient::Initialize, this,
             jni_thread_wrapper(FROM_HERE,
                                Bind(&btif_storage_load_bonded_csis_devices))));
    /* It might be not yet initialized, but setting this flag here is safe,
     * because other calls will check this and the native instance
     */
    initialized = true;
  }

  void Connect(const RawAddress& addr) override {
    if (!initialized || !CsisClient::IsCsisClientRunning()) {
      log::verbose(
          "call ignored, due to already started cleanup procedure or service "
          "being not read");
      return;
    }

    do_in_main_thread(FROM_HERE, Bind(&CsisClient::Connect,
                                      Unretained(CsisClient::Get()), addr));
  }

  void Disconnect(const RawAddress& addr) override {
    if (!initialized || !CsisClient::IsCsisClientRunning()) {
      log::verbose(
          "call ignored, due to already started cleanup procedure or service "
          "being not read");
      return;
    }

    do_in_main_thread(FROM_HERE, Bind(&CsisClient::Disconnect,
                                      Unretained(CsisClient::Get()), addr));
  }

  void RemoveDevice(const RawAddress& addr) override {
    if (!initialized || !CsisClient::IsCsisClientRunning()) {
      log::verbose(
          "call ignored, due to already started cleanup procedure or service "
          "being not ready");

      /* Clear storage */
      do_in_jni_thread(FROM_HERE, Bind(&btif_storage_remove_csis_device, addr));
      return;
    }

    do_in_main_thread(FROM_HERE, Bind(&CsisClient::RemoveDevice,
                                      Unretained(CsisClient::Get()), addr));
    /* Clear storage */
    do_in_jni_thread(FROM_HERE, Bind(&btif_storage_remove_csis_device, addr));
  }

  void LockGroup(int group_id, bool lock) override {
    if (!initialized || !CsisClient::IsCsisClientRunning()) {
      log::verbose(
          "call ignored, due to already started cleanup procedure or service "
          "being not read");
      return;
    }

    do_in_main_thread(
        FROM_HERE, Bind(&CsisClient::LockGroup, Unretained(CsisClient::Get()),
                        group_id, lock, base::DoNothing()));
  }

  void Cleanup(void) override {
    if (!initialized || !CsisClient::IsCsisClientRunning()) {
      log::verbose(
          "call ignored, due to already started cleanup procedure or service "
          "being not read");
      return;
    }

    initialized = false;
    do_in_main_thread(FROM_HERE, Bind(&CsisClient::CleanUp));
  }

  void OnConnectionState(const RawAddress& addr,
                         ConnectionState state) override {
    do_in_jni_thread(FROM_HERE, Bind(&CsisClientCallbacks::OnConnectionState,
                                     Unretained(callbacks_), addr, state));
  }

  void OnDeviceAvailable(const RawAddress& addr, int group_id, int group_size,
                         int rank, const bluetooth::Uuid& uuid) override {
    do_in_jni_thread(FROM_HERE, Bind(&CsisClientCallbacks::OnDeviceAvailable,
                                     Unretained(callbacks_), addr, group_id,
                                     group_size, rank, uuid));
  }

  void OnSetMemberAvailable(const RawAddress& addr, int group_id) override {
    do_in_jni_thread(FROM_HERE, Bind(&CsisClientCallbacks::OnSetMemberAvailable,
                                     Unretained(callbacks_), addr, group_id));
  }

  /* Callback for lock changed in the group */
  virtual void OnGroupLockChanged(int group_id, bool locked,
                                  CsisGroupLockStatus status) override {
    do_in_jni_thread(FROM_HERE,
                     Bind(&CsisClientCallbacks::OnGroupLockChanged,
                          Unretained(callbacks_), group_id, locked, status));
  }

 private:
  CsisClientCallbacks* callbacks_;
};

} /* namespace */

CsisClientInterface* btif_csis_client_get_interface(void) {
  if (!csis_client_instance)
    csis_client_instance.reset(new CsipSetCoordinatorServiceInterfaceImpl());

  return csis_client_instance.get();
}
