/*
 * Copyright 2021 HIMSA II K/S - www.himsa.dk.
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

#include "mock_controller.h"

#include "device/include/controller.h"

static controller::MockControllerInterface* controller_interface = nullptr;

void controller::SetMockControllerInterface(
    MockControllerInterface* interface) {
  controller_interface = interface;
}

uint16_t get_iso_data_size(void) {
  LOG_ASSERT(controller_interface) << "Mock controller not set!";
  return controller_interface->GetIsoDataSize();
}

uint8_t get_iso_buffer_count(void) {
  LOG_ASSERT(controller_interface) << "Mock controller not set!";
  return controller_interface->GetIsoBufferCount();
}

bool supports_ble_isochronous_broadcaster(void) {
  LOG_ASSERT(controller_interface) << "Mock controller not set!";
  return controller_interface->SupportsBleIsochronousBroadcaster();
}

bool supports_ble_2m_phy(void) {
  LOG_ASSERT(controller_interface) << "Mock controller not set!";
  return controller_interface->SupportsBle2mPhy();
}

bool supports_ble_connected_isochronous_stream_central(void) {
  LOG_ASSERT(controller_interface) << "Mock controller not set!";
  return controller_interface->SupportsBleConnectedIsochronousStreamCentral();
}

bool supports_ble_connected_isochronous_stream_peripheral(void) {
  LOG_ASSERT(controller_interface) << "Mock controller not set!";
  return controller_interface
      ->SupportsBleConnectedIsochronousStreamPeripheral();
}

bool supports_configure_data_path(void) {
  LOG_ASSERT(controller_interface) << "Mock controller not set!";
  return controller_interface->SupportsConfigureDataPath();
}

const controller_t* controller_get_interface() {
  static controller_t* controller_instance = new controller_t();

  controller_instance->get_iso_data_size = &get_iso_data_size;
  controller_instance->get_iso_buffer_count = &get_iso_buffer_count;
  controller_instance->SupportsBleIsochronousBroadcaster =
      &supports_ble_isochronous_broadcaster;
  controller_instance->SupportsBle2mPhy = &supports_ble_2m_phy;
  controller_instance->SupportsBleConnectedIsochronousStreamCentral =
      &supports_ble_connected_isochronous_stream_central;
  controller_instance->SupportsBleConnectedIsochronousStreamPeripheral =
      &supports_ble_connected_isochronous_stream_peripheral;
  controller_instance->supports_configure_data_path =
      &supports_configure_data_path;

  return controller_instance;
}
