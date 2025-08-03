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
#define LOG_TAG "StEse-SpiLayerDriver"
#include "SpiLayerDriver.h"
#include <errno.h>
#include <string.h>
#include <sys/time.h>
#include "android_logmsg.h"
#include "utils-lib/Utils.h"

int spiDeviceId;
int currentMode;
struct timeval lastRxTxTime;
#define LINUX_DBGBUFFER_SIZE 300
int BGT = 1000;  // in us

/*******************************************************************************
**
** Function         SpiLayerDriver_init
**
** Description      Initialize
**
** Parameters       spiDevPath - Spi device path.
**
** Returns          null
**
*******************************************************************************/
void SpiLayerDriver_init(SpiDriver_config_t* tSpiDriver) {
  BGT = tSpiDriver->bgt;
  STLOG_HAL_D("SpiLayerDriver_init  BGT=  %d us", BGT);
}
/*******************************************************************************
**
** Function         SpiLayerDriver_open
**
** Description      Open the spi device driver.
**
** Parameters       spiDevPath - Spi device path.
**
** Returns          the file descriptor if everything is ok, -1 otherwise.
**
*******************************************************************************/
int SpiLayerDriver_open(char* spiDevPath) {
  char* spiDeviceName = spiDevPath;
  STLOG_HAL_D("%s : Enter ", __func__);
  // Open the master spi device and save the spi device identifier
  spiDeviceId = open(spiDeviceName, O_RDWR | O_NOCTTY);
  STLOG_HAL_V(" spiDeviceId: %d", spiDeviceId);
  if (spiDeviceId < 0) {
    return -1;
  }
  currentMode = MODE_RX;
  gettimeofday(&lastRxTxTime, 0);

  return spiDeviceId;
}

/*******************************************************************************
**
** Function         SpiLayerDriver_close
**
** Description      Close the spi device driver.
**
** Parameters       none
**
** Returns          void
**
*******************************************************************************/
void SpiLayerDriver_close() {
  if (spiDeviceId > 0) {
    close(spiDeviceId);
  }
}

/*******************************************************************************
**
** Function         SpiLayerDriver_read
**
** Description      Reads bytesToRead bytes from the SPI interface.
**
** Parameters       rxBuffer    - Buffer to store recieved datas.
**                  bytesToRead - Expected number of bytes to be read.
**
** Returns          The amount of bytes read from the slave, -1 if something
**                  failed.
**
*******************************************************************************/
int SpiLayerDriver_read(uint8_t* rxBuffer, unsigned int bytesToRead) {
  int retries = 0;
  int rc = -1;

  if (currentMode != MODE_RX) {
    currentMode = MODE_RX;
    STLOG_HAL_V(" Last TX: %ld,%ld", lastRxTxTime.tv_sec, lastRxTxTime.tv_usec);
    struct timeval currentTime;
    gettimeofday(&currentTime, 0);
    STLOG_HAL_V("     Now: %ld,%ld", currentTime.tv_sec, currentTime.tv_usec);

    int elapsedTimeUs = Utils_getElapsedTimeInUs(lastRxTxTime, currentTime);
    if (elapsedTimeUs < BGT) {
      int waitTime = BGT - elapsedTimeUs;
      STLOG_HAL_V("Waiting %d us to switch from TX to RX", waitTime);
      usleep(waitTime);
    }
    gettimeofday(&currentTime, 0);
    STLOG_HAL_V("Start RX: %ld,%ld", currentTime.tv_sec, currentTime.tv_usec);
  }

  while (retries < 3) {
    rc = read(spiDeviceId, rxBuffer, bytesToRead);

    if (rc < 0) {
      int e = errno;

      /* unexpected result */
      char msg[LINUX_DBGBUFFER_SIZE];
      strerror_r(e, msg, LINUX_DBGBUFFER_SIZE);
      STLOG_HAL_E("##  SpiRead returns %d errno %d (%s)", rc, e, msg);
      /* delays are different and increasing for the three retries. */
      static const uint8_t delayTab[] = {2, 3, 5};
      int delay = delayTab[retries];

      retries++;
      usleep(delay * 1000);
      STLOG_HAL_W("##  SpiRead retry %d/3 in %d milliseconds.", retries, delay);
    } else if (rc > 0) {
      gettimeofday(&lastRxTxTime, 0);
      return rc;
    } else {
      STLOG_HAL_W("read on spi failed, retrying\n");
      usleep(4000);
      retries++;
    }
  }
  gettimeofday(&lastRxTxTime, 0);

  if (bytesToRead == 1 && rxBuffer[0] != 0 && rxBuffer[0] != 0x12 &&
      rxBuffer[0] != 0x25) {
    STLOG_HAL_D("Unexpected byte read from SPI: 0x%02X", rxBuffer[0]);
  }
  return rc;
}

/*******************************************************************************
**
** Function         SpiLayerDriver_write
**
** Description      Write txBufferLength bytes to the SPI interface.
**
** Parameters       txBuffer       - Buffer to transmit.
**                  txBufferLength - Number of bytes to be written.
**
** Returns          The amount of bytes written to the slave, -1 if something
**                  failed.
**
*******************************************************************************/
int SpiLayerDriver_write(uint8_t* txBuffer, unsigned int txBufferLength) {
  int retries = 0;
  int rc = 0;

  if (currentMode != MODE_TX) {
    currentMode = MODE_TX;
    STLOG_HAL_V(" Last RX: %ld,%ld", lastRxTxTime.tv_sec, lastRxTxTime.tv_usec);
    struct timeval currentTime;
    gettimeofday(&currentTime, 0);

    int elapsedTimeUs = Utils_getElapsedTimeInUs(lastRxTxTime, currentTime);
    if (elapsedTimeUs < BGT) {
      int waitTime = BGT - elapsedTimeUs;
      STLOG_HAL_V("Waiting %d us to switch from RX to TX", waitTime);
      usleep(waitTime);
    }
    gettimeofday(&currentTime, 0);
    STLOG_HAL_V("Start TX: %ld,%ld", currentTime.tv_sec, currentTime.tv_usec);
  }

  DispHal("Tx", txBuffer, txBufferLength);

  while (retries < 3) {
    rc = write(spiDeviceId, txBuffer, txBufferLength);

    if (rc < 0) {
      int e = errno;

      /* unexpected result */
      char msg[LINUX_DBGBUFFER_SIZE];
      strerror_r(e, msg, LINUX_DBGBUFFER_SIZE);
      STLOG_HAL_E("##  Spiwrite returns %d errno %d (%s)", rc, e, msg);
      /* delays are different and increasing for the three retries. */
      static const uint8_t delayTab[] = {2, 3, 5};
      int delay = delayTab[retries];

      retries++;
      usleep(delay * 1000);
      STLOG_HAL_W("##  SpiWrite retry %d/3 in %d milliseconds.", retries,
                  delay);

    } else if (rc > 0) {
      gettimeofday(&lastRxTxTime, 0);
      return rc;
    } else {
      STLOG_HAL_W("write on spi failed, retrying\n");
      usleep(4000);
      retries++;
    }
  }

  gettimeofday(&lastRxTxTime, 0);
  return rc;
}

/*******************************************************************************
**
** Function         SpiLayerDriver_reset
**
** Description      Send a Reset pulse to the eSE.
**
** Parameters       none
**
** Returns          O if success, -1 otherwise
**
*******************************************************************************/
int SpiLayerDriver_reset() {
  int rc = ioctl(spiDeviceId, ST54J_SE_PULSE_RESET, NULL);
  if (rc < 0) {
    char msg[LINUX_DBGBUFFER_SIZE];

    strerror_r(errno, msg, LINUX_DBGBUFFER_SIZE);
    STLOG_HAL_E("! Se reset!!, errno is '%s'", msg);
  }
  return rc;
}
