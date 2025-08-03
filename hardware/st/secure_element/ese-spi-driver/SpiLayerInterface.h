/******************************************************************************
 *
 *  Copyright (C) 2018 ST Microelectronics S.A.
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
 *
 ******************************************************************************/
#ifndef SPILAYERINTERFACE_H_
#define SPILAYERINTERFACE_H_

#include "utils-lib/Tpdu.h"

typedef struct SpiDriver_config {
  char* pDevName;
  /*!< Port name connected to ESE
   *
   * Platform specific canonical device name to which ESE is connected.
   *
   * e.g. On Linux based systems this would be /dev/p73
   */

  int bgt;

  int polling_interval;

  uint32_t devMaxFreq;
  /*!< Communication speed between DH and ESE
   *
   * This is the baudrate of the bus for communication between DH and ESE
   */

  void* pDevHandle;
  /*!< Device handle output */
} SpiDriver_config_t, *pSpiDriver_config_t; /* pointer to SpiDriver_config_t */

/**
 * Initializes the connection to the SE:
 *  1- Initial configuration of the SPI bus (if needed)
 *  2- Start the polling mechanism to read the ATP from the eSE and set-up the
 *     atp struct.
 *  3- Reconfigure SPI bus after the atp (if needed)
 *
 * @return 0 if connection could be initialized, -1 otherwise.
 */
int SpiLayerInterface_init(SpiDriver_config_t* tSpiDriver);

/**
 * Sends a TPDU to the SE, waits for the response and returns it.
 *
 * @param cmdTpdu The TPDU to be sent.
 * @param respTpdu The memory position where to store the response.
 * @param numberOfBwt The maximum number of BWT to wait.
 *
 * @return 0 if everything went ok, -1 otherwise. If timeout expired with no
 * response, 0 will be returned and respTpdu will be NULL.
 */
int SpiLayerInterface_transcieveTpdu(Tpdu* cmdTpdu, Tpdu* respTpdu,
                                     int numberOfBwt);

void SpiLayerInterface_close(void* pDevHandle);

/**
 * Setup communication to the SE:
 *  1- Perform a SE reset pulse
 *  2- Start the polling mechanism to read the ATP from the eSE and set-up the
 *     atp struct.
 *  3- Configure the IFSD length with the SE.
 *
 * @return 0 if connection could be initialized, -1 otherwise.
 */
int SpiLayerInterface_setup();

#endif /* SPILAYERINTERFACE_H_ */
