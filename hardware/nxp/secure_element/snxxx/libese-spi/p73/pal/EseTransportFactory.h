/******************************************************************************
 *
 *  Copyright 2020 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

#pragma once
#include <EseTransport.h>
#include <memory>

#define transportFactory (EseTransportFactory::getInstance())
typedef std::shared_ptr<EseTransport> spTransport;
enum transportIntf { SPI, UNKNOWN };

extern spTransport gpTransportObj;
class EseTransportFactory {
  /*****************************************************************************
   **
   ** Function         EseTransportFactory
   **
   ** Description      Constructor for transportFactory. This will be private to
   **                  support singleton
   **
   ** Parameters       none
   **
   ** Returns          none
   ****************************************************************************/
  EseTransportFactory();

 public:
  /*****************************************************************************
   **
   ** Function         getInstance
   **
   ** Description      returns the static instance of TransportFactory
   **
   ** Parameters       none
   **
   ** Returns          TransportFactory instance
   ****************************************************************************/
  static EseTransportFactory& getInstance();

  /*****************************************************************************
  **
  ** Function         getTransport
  **
  ** Description      selects and returns transport channel based on the input
  **                  parameter
  **
  ** Parameters       Required transport Type
  **
  ** Returns          Selected transport channel
  ****************************************************************************/
  spTransport getTransport(transportIntf transportType);
};
