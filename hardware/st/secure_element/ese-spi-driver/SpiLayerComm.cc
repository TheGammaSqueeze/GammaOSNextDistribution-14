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

#define LOG_TAG "StEse-SpiLayerComm"
#include "SpiLayerComm.h"
#include <errno.h>
#include <stdbool.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <time.h>
#include "SpiLayerDriver.h"
#include "android_logmsg.h"
#include "utils-lib/Atp.h"
#include "utils-lib/Tpdu.h"
#include "utils-lib/Utils.h"

int pollInterval;

/*******************************************************************************
**
** Function         SpiLayerComm_init
**
** Description      Initialize
**
** Parameters       spiDevPath - Spi device path.
**
** Returns          null
**
*******************************************************************************/
void SpiLayerComm_init(SpiDriver_config_t* tSpiDriver) {
  pollInterval = tSpiDriver->polling_interval;
  STLOG_HAL_D("SpiLayerDriver_init  pollInterval=  %d us", pollInterval);
}
/*******************************************************************************
**
** Function         SpiLayerComm_waitForAtpLength
**
** Description      Starts the polling mechanism to read the length of the ATP.
**
** Parameters       none.
**
** Returns          0 if everything is ok, -1 otherwise.
**
*******************************************************************************/

int SpiLayerComm_waitForAtpLength() {
  STLOG_HAL_D("%s : Enter ", __func__);
  uint8_t spiLecture;
  struct timeval startTime;
  struct timeval currentTime;
  unsigned int elapsedTimeInMs = 0;

  // Wait PWT before to try to read the ATP length
  gettimeofday(&startTime, 0);

  while (elapsedTimeInMs < DEFAULT_PWT) {
    gettimeofday(&currentTime, 0);
    elapsedTimeInMs = Utils_getElapsedTimeInMs(startTime, currentTime);
  }

  // Try to read the ATP length
  if (SpiLayerDriver_read(&spiLecture, 1) == -1) {
    STLOG_HAL_E("Error reading the ATP length");
    return -1;
  }

  // Check if ATP length read is OK
  if ((spiLecture == 0x00) || (spiLecture == 0xFF)) {
    STLOG_HAL_E("Invalid ATP length read");
    return -1;
  }

  ATP.len = spiLecture;

  return 0;
}

/*******************************************************************************
**
** Function         SpiLayerComm_readAtp
**
** Description      Reads the ATP and stores it in the ATP parameter.
**
** Parameters       none.
**
** Returns          0 if everything is ok, -1 otherwise.
**
*******************************************************************************/

int SpiLayerComm_readAtp() {
  uint8_t i;
  STLOG_HAL_D("%s : Enter ", __func__);
  // Read the ATP length
  if (SpiLayerDriver_reset() != -1) {
    if (SpiLayerComm_waitForAtpLength() != 0) {
      return -1;
    }
  } else {
    return -1;
  }

  // Read the rest of the ATP (ATP.len is already set).
  int atpArrayLength = ATP.len + LEN_LENGTH_IN_ATP;
  uint8_t atpArray[atpArrayLength];

  if (SpiLayerDriver_read(atpArray, ATP.len) != ATP.len) {
    STLOG_HAL_E("Error reading the rest of the ATP");
    return -1;
  }

  // Put the ATP length at the beginning of the atpArray
  for (i = ATP.len; i > 0; i--) {
    atpArray[i] = atpArray[i - 1];
  }
  atpArray[LEN_OFFSET_IN_ATP] = ATP.len;

  DispHal("Rx", atpArray, ATP.len);

  // Set-up the ATP into the corresponding struct
  if (Atp_setAtp(atpArray) != 0) {
    STLOG_HAL_E("Error setting ATP");
    return -1;
  }

  return 0;
}

/*******************************************************************************
**
** Function         SpiLayerComm_readAtpFromFile
**
** Description      Reads the ATP previously stored in a file.
**
** Parameters       none.
**
** Returns          void.
**
*******************************************************************************/
void SpiLayerComm_readAtpFromFile() {
  STLOG_HAL_D("%s : Enter ", __func__);

  FILE* atp_file = fopen(ATP_FILE_PATH, "rb");
  if (atp_file) {
    struct stat st;

    if (stat(ATP_FILE_PATH, &st) < 0) {
      STLOG_HAL_E("Error reading ATP file.");
    }

    uint8_t atpArray[st.st_size];
    fread(atpArray, st.st_size, 1, atp_file);
    // Check if error occurs
    if (ferror(atp_file)) {
      STLOG_HAL_E("An error occurred.");
    }

    // Set-up the ATP into the corresponding struct
    Atp_setAtp(atpArray);
  }
}

/*******************************************************************************
**
** Function         SpiLayerComm_writeTpdu
**
** Description      Writes the specified TPDU to the SPI interface.
**
** Parameters       cmdTpdu The TPDU to be written.
**
** Returns          The number of bytes written if everything went well, -1 if
**                  an error occurred.
**
*******************************************************************************/

int SpiLayerComm_writeTpdu(Tpdu* cmdTpdu) {
  int txBufferLength;
  uint8_t txBuffer[TPDU_MAX_LENGTH];
  STLOG_HAL_D("%s : Enter ", __func__);
  // Build the tx buffer to allocate the array of bytes to be sent
  switch (ATP.checksumType) {
    case LRC:
      txBufferLength = TPDU_PROLOGUE_LENGTH + cmdTpdu->len + TPDU_LRC_LENGTH;
      break;
    case CRC:
      txBufferLength = TPDU_PROLOGUE_LENGTH + cmdTpdu->len + TPDU_CRC_LENGTH;
      break;
  }

  // Copy the array of bytes to be sent from the cmdTpdu struct
  Tpdu_toByteArray(cmdTpdu, txBuffer);

  // Send the txBuffer through SPI
  if (SpiLayerDriver_write(txBuffer, txBufferLength) != txBufferLength) {
    STLOG_HAL_E("Error writing a TPDU through the spi");
    return -1;
  }

  return txBufferLength;
}

/*******************************************************************************
**
** Function         SpiLayerComm_waitForResponse
**
** Description      Waits for a TPDU response to be available on the
**                  SPI interface.
**
** Parameters       respTpdu The buffer where to store the TDPU.
**                  nBwt The maximum number of BWT to wait for the response.
**
** Returns          0 if the response is available and the header could be read
**                  -2 if no response received before the timeout
**                  -1 otherwise.
**
*******************************************************************************/
int SpiLayerComm_waitForResponse(Tpdu* respTpdu, int nBwt) {
  uint8_t pollingRxByte;
  struct timeval startTime;
  struct timeval currentTime;

  STLOG_HAL_D("Waiting for TPDU response (nBwt = %d).", nBwt);

  // Initialize the timeout mechanism if the BWT is under a given threshold.
  bool isTimeoutRequired = false;
  unsigned int maxWaitingTime = 0;
  if (ATP.bwt < BWT_THRESHOlD) {
    // Enable and init the timeout mechanism
    isTimeoutRequired = true;
    maxWaitingTime = ATP.bwt * nBwt;
    STLOG_HAL_D("Maximum waiting time = %d", maxWaitingTime);
    gettimeofday(&startTime, 0);
  }

  // Start the polling mechanism
  while (true) {
    // Wait between each polling sequence
    usleep(pollInterval);

    // Read the slave response by sending three null bytes
    if (SpiLayerDriver_read(&pollingRxByte, 1) != 1) {
      STLOG_HAL_E("Error reading a valid NAD from the slave.");
      return -1;
    }

    // Look for a start of valid frame
    if (pollingRxByte == NAD_SLAVE_TO_HOST) {
      STLOG_HAL_V("Start of valid frame detected");
      break;
    }

    // Check the timeout status (if required)
    if (isTimeoutRequired) {
      gettimeofday(&currentTime, 0);
      unsigned int elapsedTimeInMs =
          Utils_getElapsedTimeInMs(startTime, currentTime);
      if (elapsedTimeInMs > maxWaitingTime) {
        STLOG_HAL_E("BWT timed out after %d ms before receiving a valid NAD",
                    elapsedTimeInMs);
        return -2;
      }
    }
  }

  // If the start of frame has been received continue reading the pending part
  // of the epilogue (PCB and LEN).
  uint8_t buffer[2];
  if (SpiLayerDriver_read(buffer, 2) != 2) {
    return -1;
  }
  // Save the prologue read into the tpduRx
  respTpdu->nad = pollingRxByte;
  respTpdu->pcb = buffer[0];
  respTpdu->len = (uint8_t)buffer[1];

  return 0;
}

/*******************************************************************************
**
** Function         SpiLayerComm_readTpdu
**
** Description      Reads the pending bytes of the response
**                  (data information and crc fields).
**
** Parameters       respTpdu The buffer where to store the response.
**
** Returns          0 if everything went well, -1 otherwise.
**
*******************************************************************************/

int SpiLayerComm_readTpdu(Tpdu* respTpdu) {
  int pendingBytes;
  unsigned int i;
  // Set the number of bytes to be read.
  switch (ATP.checksumType) {
    case LRC:
      pendingBytes = respTpdu->len + TPDU_LRC_LENGTH;
      break;
    case CRC:
      pendingBytes = respTpdu->len + TPDU_CRC_LENGTH;
      break;
  }

  // Read and store them in a buffer.
  uint8_t rxBuffer[pendingBytes];
  int bytesRead;

  bytesRead = SpiLayerDriver_read(rxBuffer, pendingBytes);

  // Check if the amount of bytesRead matches with the expected
  if (bytesRead != pendingBytes) {
    STLOG_HAL_E("Tpdu bytes read does not match the expected %d %d", bytesRead,
                pendingBytes);
    return -1;
  }

  // Save data values in respTpdu
  for (i = 0; i < respTpdu->len; i++) {
    respTpdu->data[i] = rxBuffer[i];
  }

  // Copy checksum read to its struct's position
  switch (ATP.checksumType) {
    case LRC:
      respTpdu->checksum = Tpdu_getChecksumValue(rxBuffer, respTpdu->len, LRC);
      break;
    case CRC:
      respTpdu->checksum = Tpdu_getChecksumValue(rxBuffer, respTpdu->len, CRC);
      break;
  }
  // Return the struct length
  // NAD + PCB + LEN + bytesRead (DATA + CHECKSUM).
  STLOG_HAL_V("%s : bytesRead = %d", __func__, bytesRead);
  return 3 + bytesRead;
}
