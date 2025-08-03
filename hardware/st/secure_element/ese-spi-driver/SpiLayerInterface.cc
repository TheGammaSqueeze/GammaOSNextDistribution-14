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
#define LOG_TAG "StEse-SpiLayerInterface"
#include "SpiLayerInterface.h"
#include <errno.h>
#include <string.h>
#include <sys/time.h>
#include "SpiLayerComm.h"
#include "SpiLayerDriver.h"
#include "T1protocol.h"
#include "android_logmsg.h"
#include "utils-lib/Atp.h"

#define SPI_BITS_PER_WORD 8
#define SPI_MODE SPI_MODE_0

#define KHZ_TO_HZ 1000

static bool mFirstActivation = false;

/*******************************************************************************
**
** Function         SpiLayerInterface_init
**
** Description      Initialize the SPI link access
**
** Parameters       tSpiDriver     - hardware information
**
** Returns          0 if connection could be initialized, -1 otherwise.
**
*******************************************************************************/
int SpiLayerInterface_init(SpiDriver_config_t* tSpiDriver) {
  STLOG_HAL_D("Initializing SPI Driver interface...");

  // Configure the SPI before start the data exchange with the eSE
  char* spiDevPath = tSpiDriver->pDevName;

  SpiLayerComm_init(tSpiDriver);
  SpiLayerDriver_init(tSpiDriver);

  int DevHandle = SpiLayerDriver_open(spiDevPath);
  tSpiDriver->pDevHandle = (void*)((intptr_t)DevHandle);
  if (DevHandle == -1) {
    // Error configuring the SPI bus
    STLOG_HAL_E("Error configuring the SPI bus.");
    return -1;
  }

  if (!mFirstActivation) {
    if (SpiLayerInterface_setup() == -1) {
      return -1;
    }
    mFirstActivation = true;
    STLOG_HAL_D("SPI bus working at ATP.msf =  %i KHz", ATP.msf);
  }

  STLOG_HAL_D("SPI Driver interface initialized.");
  return 0;
}

/*******************************************************************************
**
** Function         SpiLayerInterface_transcieveTpdu
**
** Description       Sends a TPDU to the SE, waits for the response
**                   and returns it.
**
** Parameters       cmdTpdu    -The TPDU to be sent.
**                  respTpdu   -The memory position where to store the response.
**                  numberOfBwt-The maximum number of BWT to wait.
**
** Returns          bytesRead if data was read, 0 if timeout expired with
**                  no response, -1 otherwise
**
*******************************************************************************/
int SpiLayerInterface_transcieveTpdu(Tpdu* cmdTpdu, Tpdu* respTpdu,
                                     int numberOfBwt) {
  // Send the incoming Tpdu to the slave
  if (SpiLayerComm_writeTpdu(cmdTpdu) < 0) {
    return -1;
  }

  if (numberOfBwt <= 0) {
    STLOG_HAL_W("Buffer overflow happened, restoring numberOfBwt");
    numberOfBwt = DEFAULT_NBWT;
  }
  // Wait for response
  int result = SpiLayerComm_waitForResponse(respTpdu, numberOfBwt);

  // Unable to receive the response from slave
  if (result == -1) {
    return -1;
  } else if (result == -2) {
    // 0 bytes read
    return 0;
  }

  // Read the response
  int bytesRead = SpiLayerComm_readTpdu(respTpdu);
  if (bytesRead < 0) {
    STLOG_HAL_E("Error when reading from SPI interface (%d).", bytesRead);
    return -1;
  }
  STLOG_HAL_D("%d bytes read from SPI interface", bytesRead);

  uint8_t buffer[(5 + respTpdu->len)];
  uint16_t length = Tpdu_toByteArray(respTpdu, buffer);
  if (length > 0) {
    DispHal("Rx", buffer, length);
  }
  return bytesRead;
}

/*******************************************************************************
**
** Function         SpiLayerInterface_close
**
** Description      Close the device
**
** Parameters       pDevHandle - device handle
**
** Returns          None
**
*******************************************************************************/
void SpiLayerInterface_close(void* pDevHandle) {
  if (NULL != pDevHandle) {
    STLOG_HAL_D("SpiLayerInterface_close");
    SpiLayerDriver_close();
  }
}
/*******************************************************************************
**
** Function         SpiLayerInterface_setup
**
** Description      Read the ATP by performing SE reset and negotiate the IFSD.
**
** Parameters       pDevHandle - device handle
**
** Returns          0 if connection could be initialized, -1 otherwise.
**
*******************************************************************************/
int SpiLayerInterface_setup() {
  const char ese_reset_property[] = "persist.vendor.se.reset";
  // First of all, read the ATP from the slave
  if (SpiLayerComm_readAtp() != 0) {
    // Error reading the ATP
    STLOG_HAL_E("Error reading the ATP.");
    // eSE needs cold reset.
    property_set(ese_reset_property, "needed");
    return -1;
  }
  T1protocol_resetSequenceNumbers();

  uint8_t* pRsp = (uint8_t*)malloc(ATP.ifsc * sizeof(uint8_t));
  int rc =
      T1protocol_transcieveApduPart(0, 0, false, (StEse_data*)pRsp, S_IFS_REQ);

  if (rc < 0) {
    STLOG_HAL_E(" %s ESE - Error transmitting IFS request\n", __FUNCTION__);
    free(pRsp);
    return -1;
  }
  // Set noneed if SPI worked normally.
  property_set(ese_reset_property, "noneed");

  free(pRsp);
  return 0;
}
