/******************************************************************************
 *
 *  Copyright 2018-2022 NXP
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

/**
 * \addtogroup eSe_PAL
 * \brief PAL implementation
 * @{ */

#ifndef _PHNXPESE_PAL_H
#define _PHNXPESE_PAL_H

/* Basic type definitions */
#include <NxpTimer.h>
#include <errno.h>
#include <phEseStatus.h>
#include <phNxpEseFeatures.h>
#include <stdint.h>
/*!
 * \brief Value indicates to reset device
 */
#define PH_PALESE_RESETDEVICE (0x00008001)

/*!
 * \ingroup eSe_PAL
 *
 * \brief Enum definition contains supported ioctl control codes.
 *
 * phPalEse_IoCtl
 */
typedef enum {
  phPalEse_e_Invalid = 0,                         /*!< Invalid control code */
  phPalEse_e_ResetDevice = PH_PALESE_RESETDEVICE, /*!< Reset the device */
  phPalEse_e_EnableLog,      /*!< Enable the spi driver logs */
  phPalEse_e_EnablePollMode, /*!< Enable the polling for SPI */
  phPalEse_e_GetEseAccess,   /*!< get the bus access in specified timeout */
  phPalEse_e_ChipRst,        /*!< eSE Chip reset using ISO RST pin*/
  phPalEse_e_EnableThroughputMeasurement, /*!< Enable throughput measurement */
  phPalEse_e_SetPowerScheme,              /*!< Set power scheme */
  phPalEse_e_GetSPMStatus,                /*!< Get SPM(power mgt) status */
  phPalEse_e_DisablePwrCntrl,
  phPalEse_e_SetJcopDwnldState,    /*!< Set Jcop Download state */
  phPalEse_e_SetClientUpdateState, /*!< Set Jcop Download state */
  phPalEse_e_SetSecureMode,        /*!< Set the Trusted SE Mode */
  phPalEse_e_ResetProtection,
} phPalEse_ControlCode_t; /*!< Control code for IOCTL call */

/*!
 * \ingroup eSe_PAL
 *
 * \brief PAL Configuration exposed to upper layer.
 */
typedef struct phPalEse_Config {
  int8_t* pDevName;
  /*!< Port name connected to ESE
   *
   * Platform specific canonical device name to which ESE is connected.
   *
   * e.g. On Linux based systems this would be /dev/p73
   */

  uint32_t dwBaudRate;
  /*!< Communication speed between DH and ESE
   *
   * This is the baudrate of the bus for communication between DH and ESE
   */

  void* pDevHandle;
  /*!< Device handle output */
} phPalEse_Config_t, *pphPalEse_Config_t; /* pointer to phPalEse_Config_t */

/*!
 * \ingroup eSe_PAL
 *
 * \brief NxpTimer struct to measure cmd TX and response RX time.
 */
typedef struct phPalEse_NxpTimer {
  NxpTimer* tx_timer;
  /*!< timer to capture time taken for cmd transfer
   */

  NxpTimer* rx_timer;
  /*!< timer to capture time taken for response receival
   */

  bool is_enabled;
  /*!< TRUE if KPI measurement is enabled else FALSE
   */

} phPalEse_NxpTimer_t;

/* Function declarations */
/**
 * \ingroup eSe_PAL
 * \brief This function is used to close the ESE device
 *
 * \retval None
 *
 */
void phPalEse_close(void* pDevHandle);

/**
 * \ingroup eSe_PAL
 * \brief Open and configure ESE device
 *
 * \param[in]       pConfig: Config to open the device
 *
 * \retval  ESESTATUS On Success ESESTATUS_SUCCESS else proper error code
 *
 */
ESESTATUS phPalEse_open_and_configure(pphPalEse_Config_t pConfig);

/**
 * \ingroup eSe_PAL
 * \brief ConfigTransport to get SPI terminal
 *
 * \retval  ESESTATUS On Success ESESTATUS_SUCCESS else proper error code
 *
 */
ESESTATUS phPalEse_ConfigTransport();

/**
 * \ingroup eSe_PAL
 * \brief Reads requested number of bytes from ESE into given buffer
 *
 * \param[in]    pDevHandle       - valid device handle
 **\param[in]    pBuffer           - buffer for read data
 **\param[in]    nNbBytesToRead    - number of bytes requested to be read
 *
 * \retval   numRead      - number of successfully read bytes.
 * \retval      -1             - read operation failure
 *
 */
int phPalEse_read(void* pDevHandle, uint8_t* pBuffer, int nNbBytesToRead);

/**
 * \ingroup eSe_PAL
 * \brief Writes requested number of bytes from given buffer into pn547 device
 *
 * \param[in]    pDevHandle                 - valid device handle
 * \param[in]    pBuffer                    - buffer to write
 * \param[in]    nNbBytesToWrite            - number of bytes to write
 *
 * \retval  numWrote   - number of successfully written bytes
 * \retval      -1         - write operation failure
 *
 */
int phPalEse_write(void* pDevHandle, uint8_t* pBuffer, int nNbBytesToWrite);

/**
 * \ingroup eSe_PAL
 * \brief Exposed ioctl by ESE driver
 *
 * \param[in]    eControlCode       - phPalEse_ControlCode_t for the respective
 *configs
 * \param[in]    pDevHandle         - valid device handle
 * \param[in]    level              - reset level
 *
 * \retval    0   - ioctl operation success
 * \retval   -1  - ioctl operation failure
 *
 */
ESESTATUS phPalEse_ioctl(phPalEse_ControlCode_t eControlCode, void* pDevHandle,
                         long level);

/**
 * \ingroup eSe_PAL
 * \brief Print packet data
 *
 * \param[in]    pString            - String to be printed
 * \param[in]    p_data             - data to be printed
 * \param[in]    len                - Length of data to be printed
 *
 * \retval   void
 *
 */
void phPalEse_print_packet(const char* pString, const uint8_t* p_data,
                           uint16_t len);

/**
 * \ingroup eSe_PAL
 * \brief This function  suspends execution of the calling thread for
 *                  (at least) usec microseconds
 *
 * \param[in]    usec                - number of micro seconds to sleep
 *
 * \retval   void
 *
 */
void phPalEse_sleep(long usec);

/**
 * \ingroup eSe_PAL
 * \brief This function  suspends execution of the calling thread for
 *        total_time usecs(max extra delay 1 usecs) with busy loop wait.
 *        Use this only for short delays (less than 500 microseconds)
 *
 * \param[in]    usec                - number of micro seconds to sleep
 *
 * \retval   void
 *
 */
void phPalEse_BusyWait(long total_time /* usecs*/);

/**
 * \ingroup eSe_PAL
 * \brief This function updates destination buffer with val
 *                 data in len size
 *
 * \param[in]    buff                - Array to be updated
 * \param[in]    val                 - value to be updated
 * \param[in]    len                 - length of array to be updated
 *
 * \retval   void
 *
 */
void* phPalEse_memset(void* buff, int val, size_t len);

/**
 * \ingroup eSe_PAL
 * \brief This function copies source buffer to  destination buffer
 *                 data in len size
 *
 * \param[in]    dest                - Destination array to be updated
 * \param[in]    src                 - Source array to be updated
 * \param[in]    len                 - length of array to be updated
 *
 * \retval   void
 *
 */
void* phPalEse_memcpy(void* dest, const void* src, size_t len);

/**
 * \ingroup eSe_PAL
 * \brief This is utility function for runtime heap memory allocation
 *
 * \param[in]    size                 - number of bytes to be allocated
 *
 * \retval   void
 *
 */
void* phPalEse_memalloc(uint32_t size);

/**
 * \ingroup eSe_PAL
 * \brief This is utility function for runtime heap memory allocation
 *
 * \param[in]    dataType                 - type of data
 * \param[in]    size                         - number of bytes to be allocated
 * \retval   void
 *
 */
void* phPalEse_calloc(size_t dataType, size_t size);

/**
 * \ingroup eSe_PAL
 * \brief This is utility function for freeing heap memory allocated
 *
 * \param[in]    ptr                 - Address pointer to previous allocation
 *
 * \retval   void
 *
 */
void phPalEse_free(void* ptr);

/**
 * \ingroup eSe_PAL
 * \brief This is wrapper function for constructing timer objects.
 *  Timer is used for cmd TX and response RX time measurement.
 *  Applicable only for KPI measurement
 *
 * \param[in]    void
 *
 * \retval   void
 *
 */
void phPalEse_initTimer();

/**
 * \ingroup eSe_PAL
 * \brief Function for getting handle to initialized timers.
 *  Applicable only for KPI measurement
 *
 * \param[in]    void
 *
 * \retval   pointer to global phPalEse_NxpTimer_t struct or null
 *
 */
const phPalEse_NxpTimer_t* phPalEse_getTimer();

/**
 * \ingroup eSe_PAL
 * \brief Wrapper function to start timer.
 *  Applicable only for KPI measurement
 *
 * \param[in]    pointer to NxpTimer object
 *
 * \retval   void
 *
 */
void phPalEse_startTimer(NxpTimer* timer);

/**
 * \ingroup eSe_PAL
 * \brief Wrapper function to stop timer.
 *  Applicable only for KPI measurement
 *
 * \param[in]    pointer to NxpTimer object
 *
 * \retval   void
 *
 */
void phPalEse_stopTimer(NxpTimer* timer);

/**
 * \ingroup eSe_PAL
 * \brief Wrapper function to get total time recorded by given timer.
 *  Applicable only for KPI measurement
 *
 * \param[in]    pointer to NxpTimer object
 *
 * \retval   total time recorded by this timer
 *
 */
unsigned long phPalEse_timerDuration(NxpTimer* timer);

/**
 * \ingroup eSe_PAL
 * \brief Wrapper function to reset the both timer state.
 *  Applicable only for KPI measurement
 *
 * \param[in]    void
 *
 * \retval   void
 *
 */
void phPalEse_resetTimer();

/**
 * \ingroup eSe_PAL
 * \brief Wrapper function to delete the objects created by initTimer.
 *  Applicable only for KPI measurement
 *
 * \param[in]    void
 *
 * \retval   void
 *
 */
void phPalEse_deInitTimer();

/** @} */
#endif /*  _PHNXPESE_PAL_H    */
