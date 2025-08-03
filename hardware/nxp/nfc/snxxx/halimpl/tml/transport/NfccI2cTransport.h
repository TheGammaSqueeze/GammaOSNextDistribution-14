/******************************************************************************
 *
 *  Copyright 2020-2023 NXP
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
#include <NfccTransport.h>

#define NFC_MAGIC 0xE9
/*
 * NFCC power control via ioctl
 * NFC_SET_PWR(0): power off
 * NFC_SET_PWR(1): power on
 * NFC_SET_PWR(2): reset and power on with firmware download enabled
 */
#define NFC_SET_PWR _IOW(NFC_MAGIC, 0x01, uint32_t)
/*
 * 1. SPI Request NFCC to enable ESE power, only in param
 *   Only for SPI
 *   level 1 = Enable power
 *   level 0 = Disable power
 * 2. NFC Request the eSE cold reset, only with MODE_ESE_COLD_RESET
 */
#define ESE_SET_PWR _IOW(NFC_MAGIC, 0x02, uint32_t)

/*
 * SPI or DWP can call this ioctl to get the current
 * power state of ESE
 */
#define ESE_GET_PWR _IOR(NFC_MAGIC, 0x03, uint32_t)
/*
 *  NFC_SET_RESET_READ_PENDING(1): set read pending flag of NFC
 *  NFC_SET_RESET_READ_PENDING(0): reset read pending flag of NFC
 */
#define NFC_SET_RESET_READ_PENDING _IOW(NFC_MAGIC, 0x04, uint32_t)

/*
 * read the gpios status flag encoded byte from kernel space
 */
#define NFC_GET_GPIO_STATUS _IOR(NFC_MAGIC, 0x05, uint32_t)

extern phTmlNfc_i2cfragmentation_t fragmentation_enabled;

class NfccI2cTransport : public NfccTransport {
 private:
  bool_t bFwDnldFlag = false;
  sem_t mTxRxSemaphore;

 public:
  /*****************************************************************************
  **
  ** Function         Close
  **
  ** Description      Closes NFCC device
  **
  ** Parameters       pDevHandle - device handle
  **
  ** Returns          None
  **
  *****************************************************************************/
  void Close(void* pDevHandle);

  /*****************************************************************************
   **
   ** Function         OpenAndConfigure
   **
   ** Description      Open and configure NFCC device
   **
   ** Parameters       pConfig     - hardware information
   **                  pLinkHandle - device handle
   **
   ** Returns          NFC status:
   **                  NFCSTATUS_SUCCESS - open_and_configure operation success
   **                  NFCSTATUS_INVALID_DEVICE - device open operation failure
   **
   ****************************************************************************/
  NFCSTATUS OpenAndConfigure(pphTmlNfc_Config_t pConfig, void** pLinkHandle);

  /*****************************************************************************
   **
   ** Function         Read
   **
   ** Description      Reads requested number of bytes from NFCC device into
   *given
   **                  buffer
   **
   ** Parameters       pDevHandle       - valid device handle
   **                  pBuffer          - buffer for read data
   **                  nNbBytesToRead   - number of bytes requested to be read
   **
   ** Returns          numRead   - number of successfully read bytes
   **                  -1        - read operation failure
   **
   ****************************************************************************/
  int Read(void* pDevHandle, uint8_t* pBuffer, int nNbBytesToRead);

  /*****************************************************************************
  **
  ** Function         Write
  **
  ** Description      Writes requested number of bytes from given buffer into
  **                  NFCC device
  **
  ** Parameters       pDevHandle       - valid device handle
  **                  pBuffer          - buffer for read data
  **                  nNbBytesToWrite  - number of bytes requested to be written
  **
  ** Returns          numWrote   - number of successfully written bytes
  **                  -1         - write operation failure
  **
  *****************************************************************************/
  int Write(void* pDevHandle, uint8_t* pBuffer, int nNbBytesToWrite);

  /*****************************************************************************
   **
   ** Function         Reset
   **
   ** Description      Reset NFCC device, using VEN pin
   **
   ** Parameters       pDevHandle     - valid device handle
   **                  level          - reset level
   **
   ** Returns           0   - reset operation success
   **                  -1   - reset operation failure
   **
   ****************************************************************************/
  int NfccReset(void* pDevHandle, NfccResetType eType);

  /*****************************************************************************
   **
   ** Function         UpdateReadPending
   **
   ** Description      Set/Reset Read Pending of NFC
   **
   ** Parameters       pDevHandle     - valid device handle
   **                  eType          - set or clear the flag
   **
   ** Returns           0   - operation success
   **                  -1   - operation failure
   **
   ****************************************************************************/
  int UpdateReadPending(void* pDevHandle, NfcReadPending eType);

  /*****************************************************************************
   **
   ** Function         NfcGetGpioStatus
   **
   ** Description      Get the gpio status flag byte from kernel space
   **
   ** Parameters       pDevHandle     - valid device handle
   **
   **
   ** Returns           0   - operation success
   **                  -1   - operation failure
   **
   ****************************************************************************/
  int NfcGetGpioStatus(void* pDevHandle, uint32_t* status);

  /*****************************************************************************
   **
   ** Function         EseReset
   **
   ** Description      Request NFCC to reset the eSE
   **
   ** Parameters       pDevHandle     - valid device handle
   **                  eType          - EseResetType
   **
   ** Returns           0   - reset operation success
   **                  else - reset operation failure
   **
   ****************************************************************************/
  int EseReset(void* pDevHandle, EseResetType eType);

  /*****************************************************************************
   **
   ** Function         EnableFwDnldMode
   **
   ** Description      updates the state to Download mode
   **
   ** Parameters       True/False
   **
   ** Returns          None
   ****************************************************************************/
  void EnableFwDnldMode(bool mode);

  /*****************************************************************************
   **
   ** Function         IsFwDnldModeEnabled
   **
   ** Description      Returns the current mode
   **
   ** Parameters       none
   **
   ** Returns           Current mode download/NCI
   ****************************************************************************/
  bool_t IsFwDnldModeEnabled(void);

  /*******************************************************************************
  **
  ** Function         Flushdata
  **
  ** Description      Reads payload of FW rsp from NFCC device into given buffer
  **
  ** Parameters       pConfig     - hardware information
  **
  ** Returns          True(Success)/False(Fail)
  **
  *******************************************************************************/
  bool Flushdata(pphTmlNfc_Config_t pConfig);
};
