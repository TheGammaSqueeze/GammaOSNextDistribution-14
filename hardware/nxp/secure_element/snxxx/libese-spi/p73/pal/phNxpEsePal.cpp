/******************************************************************************
 *
 *  Copyright 2018-2020,2022 NXP
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

/*
 * DAL spi port implementation for linux
 *
 * Project: Trusted ESE Linux
 *
 */
#define LOG_TAG "NxpEseHal"
#include <EseTransportFactory.h>
#include <NxpTimer.h>
#include <errno.h>
#include <ese_config.h>
#include <ese_logs.h>
#include <fcntl.h>
#include <log/log.h>
#include <phEseStatus.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

/*!
 * \brief Normal mode header length
 */
#define NORMAL_MODE_HEADER_LEN 3
/*!
 * \brief Normal mode header offset
 */
#define NORMAL_MODE_LEN_OFFSET 2
/*!
 * \brief Start of frame marker
 */
#define SEND_PACKET_SOF 0x5A
/*!
 * \brief To enable SPI interface for ESE communication
 */
#define SPI_ENABLED 1

spTransport gpTransportObj;

static phPalEse_NxpTimer_t gNxpTimer;

/*******************************************************************************
**
** Function         phPalEse_close
**
** Description      Closes PN547 device
**
** Parameters       pDevHandle - device handle
**
** Returns          None
**
*******************************************************************************/
void phPalEse_close(void* pDevHandle) {
  if (NULL != pDevHandle) {
    gpTransportObj->Close(pDevHandle);
  }
  gpTransportObj = NULL;
  return;
}

/*******************************************************************************
**
** Function         phPalEse_open_and_configure
**
** Description      Open and configure ESE device
**
** Parameters       pConfig     - hardware information
**
** Returns          ESE status:
**                  ESESTATUS_SUCCESS            - open_and_configure operation
*success
**                  ESESTATUS_INVALID_DEVICE     - device open operation failure
**
*******************************************************************************/
ESESTATUS phPalEse_open_and_configure(pphPalEse_Config_t pConfig) {
  ESESTATUS status = ESESTATUS_FAILED;
  if (ESESTATUS_SUCCESS != phPalEse_ConfigTransport()) return ESESTATUS_FAILED;
  status = gpTransportObj->OpenAndConfigure(pConfig);
  return status;
}

/*******************************************************************************
**
** Function         phPalEse_ConfigTransport
**
** Description      Configure Transport channel based on transport type provided
**                  in config file
**
** Returns          ESESTATUS_SUCCESS If transport channel is configured
**                  ESESTATUS_FAILED If transport channel configuration failed
**
*******************************************************************************/
ESESTATUS phPalEse_ConfigTransport() {
  unsigned long transportType = UNKNOWN;

  transportType = EseConfig::getUnsigned(NAME_NXP_TRANSPORT, UNKNOWN);
  ALOGD("phPalEse_ConfigTransport transport type %ld", transportType);
  gpTransportObj = transportFactory.getTransport((transportIntf)transportType);
  if (gpTransportObj == nullptr) {
    return ESESTATUS_FAILED;
  }
  return ESESTATUS_SUCCESS;
}

/*******************************************************************************
**
** Function         phPalEse_read
**
** Description      Reads requested number of bytes from pn547 device into given
*buffer
**
** Parameters       pDevHandle       - valid device handle
**                  pBuffer          - buffer for read data
**                  nNbBytesToRead   - number of bytes requested to be read
**
** Returns          numRead   - number of successfully read bytes
**                  -1        - read operation failure
**
*******************************************************************************/
int phPalEse_read(void* pDevHandle, uint8_t* pBuffer, int nNbBytesToRead) {
  int ret = -1;
  ret = gpTransportObj->Read(pDevHandle, pBuffer, nNbBytesToRead);
  return ret;
}

/*******************************************************************************
**
** Function         phPalEse_write
**
** Description      Writes requested number of bytes from given buffer into
*pn547 device
**
** Parameters       pDevHandle       - valid device handle
**                  pBuffer          - buffer for read data
**                  nNbBytesToWrite  - number of bytes requested to be written
**
** Returns          numWrote   - number of successfully written bytes
**                  -1         - write operation failure
**
*******************************************************************************/
int phPalEse_write(void* pDevHandle, uint8_t* pBuffer, int nNbBytesToWrite) {
  int numWrote = 0;

  if (NULL == pDevHandle) {
    return -1;
  }
  numWrote = gpTransportObj->Write(pDevHandle, pBuffer, nNbBytesToWrite);
  return numWrote;
}

/*******************************************************************************
**
** Function         phPalEse_ioctl
**
** Description      Exposed ioctl by p61 spi driver
**
** Parameters       pDevHandle     - valid device handle
**                  level          - reset level
**
** Returns           0   - ioctl operation success
**                  -1   - ioctl operation failure
**
*******************************************************************************/
ESESTATUS phPalEse_ioctl(phPalEse_ControlCode_t eControlCode, void* pDevHandle,
                         long level) {
  ESESTATUS ret = ESESTATUS_FAILED;
  NXP_LOG_ESE_D("phPalEse_spi_ioctl(), ioctl %x , level %lx", eControlCode,
                level);
  if (GET_CHIP_OS_VERSION() == OS_VERSION_4_0) {
    if (NULL == pDevHandle) {
      return ESESTATUS_IOCTL_FAILED;
    }
  }
  if (pDevHandle == NULL) {
    phPalEse_ConfigTransport();
  }
  ret = gpTransportObj->Ioctl(eControlCode, pDevHandle, level);
  if (pDevHandle == NULL) {
    phPalEse_close(pDevHandle);
  }

  return ret;
}
/*******************************************************************************
**
** Function         phPalEse_BusyWait
**
** Description      This function  suspends execution of the calling thread for
**                  total_time usecs(max extra delay 1 usecs) with busy wait.
**                  Use this only for short delays (less than 500 microsecs)
**
** Returns          None
**
*******************************************************************************/

void phPalEse_BusyWait(long total_time /* usecs*/) {
  struct timespec ts1, ts2;
  clock_gettime(CLOCK_MONOTONIC, &ts1);
  long elapsed_time = 0;  // microseconds
  do {
    clock_gettime(CLOCK_MONOTONIC, &ts2);
    elapsed_time = 1e+6 * ts2.tv_sec + 1e-3 * ts2.tv_nsec -
                   (1e+6 * ts1.tv_sec + 1e-3 * ts1.tv_nsec);
  } while (elapsed_time < total_time);
}

/*******************************************************************************
**
** Function         phPalEse_print_packet
**
** Description      Print packet
**
** Returns          None
**
*******************************************************************************/
void phPalEse_print_packet(const char* pString, const uint8_t* p_data,
                           uint16_t len) {
  if (ese_log_level < NXPESE_LOGLEVEL_DEBUG) return;  // debug logs disabled

  uint32_t i;
  char print_buffer[len * 3 + 1];

  memset(print_buffer, 0, sizeof(print_buffer));
  for (i = 0; i < len; i++) {
    snprintf(&print_buffer[i * 2], 3, "%02X", p_data[i]);
  }
  if (0 == memcmp(pString, "SEND", 0x04)) {
    NXP_LOG_ESE_D("NxpEseDataX len = %3d > %s", len, print_buffer);
  } else if (0 == memcmp(pString, "RECV", 0x04)) {
    NXP_LOG_ESE_D("NxpEseDataR len = %3d > %s", len, print_buffer);
  }
  return;
}

/*******************************************************************************
**
** Function         phPalEse_sleep
**
** Description      This function  suspends execution of the calling thread for
**                  (at least) usec microseconds
**
** Returns          None
**
*******************************************************************************/
void phPalEse_sleep(long usec) {
  usleep(usec);
  return;
}

/*******************************************************************************
**
** Function         phPalEse_memset
**
** Description
**
** Returns          None
**
*******************************************************************************/

void* phPalEse_memset(void* buff, int val, size_t len) {
  return memset(buff, val, len);
}

/*******************************************************************************
**
** Function         phPalEse_memcpy
**
** Description
**
** Returns          None
**
*******************************************************************************/

void* phPalEse_memcpy(void* dest, const void* src, size_t len) {
  return memcpy(dest, src, len);
}

/*******************************************************************************
**
** Function         phPalEse_memalloc
**
** Description
**
** Returns          None
**
*******************************************************************************/

void* phPalEse_memalloc(uint32_t size) { return malloc(size); }

/*******************************************************************************
**
** Function         phPalEse_calloc
**
** Description
**
** Returns          None
**
*******************************************************************************/

void* phPalEse_calloc(size_t datatype, size_t size) {
  return calloc(datatype, size);
}

/*******************************************************************************
**
** Function         phPalEse_free
**
** Description
**
** Returns          None
**
*******************************************************************************/
void phPalEse_free(void* ptr) {
  if (ptr != NULL) {
    free(ptr);
    ptr = NULL;
  }
  return;
}
/*******************************************************************************
**
** Function         phPalEse_initTimer
**
** Description      Initializes phPalEse_NxpTimer_t global struct
**
** Returns          None
**
*******************************************************************************/

void phPalEse_initTimer() {
  bool is_kpi_enabled =
      EseConfig::getUnsigned(NAME_SE_KPI_MEASUREMENT_ENABLED, 0);
  gNxpTimer.is_enabled = (is_kpi_enabled != 0) ? true : false;
  if (!gNxpTimer.is_enabled) return;

  gNxpTimer.tx_timer = new NxpTimer("TX");
  gNxpTimer.rx_timer = new NxpTimer("RX");
}
/*******************************************************************************
**
** Function         phPalEse_getTimer
**
** Description      Get handle to phPalEse_NxpTimer_t global struct variable
**
** Returns          pointer to phPalEse_NxpTimer_t struct variable
**
*******************************************************************************/

const phPalEse_NxpTimer_t* phPalEse_getTimer() { return &gNxpTimer; }
/*******************************************************************************
**
** Function         phPalEse_startTimer
**
** Description      Wrapper function to start the given timer
**
** Returns          None
**
*******************************************************************************/

void phPalEse_startTimer(NxpTimer* timer) {
  if (!gNxpTimer.is_enabled) return;

  timer->startTimer();
}
/*******************************************************************************
**
** Function         phPalEse_stopTimer
**
** Description      Wrapper function to stop the given timer
**
** Returns          None
**
*******************************************************************************/

void phPalEse_stopTimer(NxpTimer* timer) {
  if (!gNxpTimer.is_enabled) return;

  timer->stopTimer();
}
/*******************************************************************************
**
** Function         phPalEse_timerDuration
**
** Description      Wrapper function to get total time (usecs) recorded by the
**                  given timer
**
** Returns          total time (in usecs) recorded by the timer
**
*******************************************************************************/

unsigned long phPalEse_timerDuration(NxpTimer* timer) {
  if (!gNxpTimer.is_enabled) return 0;

  return timer->totalDuration();
}
/*******************************************************************************
**
** Function         phPalEse_resetTimer
**
** Description      Function to reset both timers in gNxpTimer object
**
** Returns          None
**
*******************************************************************************/

void phPalEse_resetTimer() {
  if (!gNxpTimer.is_enabled) return;

  gNxpTimer.tx_timer->resetTimer();
  gNxpTimer.rx_timer->resetTimer();
}

/*******************************************************************************
**
** Function         phPalEse_deInitTimer
**
** Description      Wrapper function to de-construct the timer objects
**
** Returns          None
**
*******************************************************************************/

void phPalEse_deInitTimer() {
  if (!gNxpTimer.is_enabled) return;

  delete gNxpTimer.tx_timer;
  gNxpTimer.tx_timer = nullptr;

  delete gNxpTimer.rx_timer;
  gNxpTimer.rx_timer = nullptr;

  gNxpTimer.is_enabled = false;
}
