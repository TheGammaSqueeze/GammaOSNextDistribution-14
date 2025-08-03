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
#ifndef SPILAYERCOMM_H_
#define SPILAYERCOMM_H_

#include "SpiLayerInterface.h"
#include "utils-lib/Tpdu.h"

#define NAD_HOST_TO_SLAVE 0x21
#define NAD_SLAVE_TO_HOST 0x12
#define BWT_THRESHOlD 0xFFFF
#define DEFAULT_PWT 50

// Global variables

/**
 * Initialize internal variables.
 *
 * @return  null
 */

void SpiLayerComm_init(SpiDriver_config_t* tSpiDriver);
/**
 * Starts the polling mechanism to read the length of the ATP.
 *
 * @returns 0 if everything is ok, -1 otherwise.
 */
int SpiLayerComm_waitForAtpLength();

/**
 * Reads the ATP and stores it in the ATP parameter.
 *
 * @returns 0 if everything is ok, -1 otherwise.
 */
int SpiLayerComm_readAtp();

/**
 * Writes the specified TPDU to the SPI interface.
 *
 * @param cmdTpdu The TPDU to be written.
 *
 * @return The number of bytes written if everything went well, -1 if an error
 * 			occurred.
 */
int SpiLayerComm_writeTpdu(Tpdu* cmdTpdu);

/**
 * Waits for a TPDU response to be available on the SPI interface.
 *
 * @param respTpdu The buffer where to store the TDPU.
 * @param nBwt The maximum number of BWT to wait for the response.
 *
 * @return 0 if the response is available and the header could be read, -2
 *           if no response received before the timeout, -1 otherwise.
 */
int SpiLayerComm_waitForResponse(Tpdu* respTpdu, int nBwt);

/**
 * Reads the pending bytes of the response (data information and crc fields).
 * Assumes respTpdu epilogue is initialized.
 *
 * @param respTpdu The buffer where to store the response. Should have the
 *      epilogue initialized.
 *
 * @return 0 if everything went well, -1 otherwise.
 */
int SpiLayerComm_readTpdu(Tpdu* respTpdu);

void SpiLayerComm_readAtpFromFile();

#endif /* SPILAYERCOMM_H_ */
