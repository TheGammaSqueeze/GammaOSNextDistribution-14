/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com.
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
  return controller_interface->GetIsoDataSize();
}

uint8_t get_iso_buffer_count(void) {
  return controller_interface->GetIsoBufferCount();
}
uint16_t get_acl_data_size_ble(void) {
  return controller_interface->GetAclDataSizeBle();
}

const controller_t* controller_get_interface() {
  static controller_t* controller_instance = new controller_t();

  controller_instance->get_iso_data_size = &get_iso_data_size;
  controller_instance->get_iso_buffer_count = &get_iso_buffer_count;
  controller_instance->get_acl_data_size_ble = &get_acl_data_size_ble;

  return controller_instance;
}
