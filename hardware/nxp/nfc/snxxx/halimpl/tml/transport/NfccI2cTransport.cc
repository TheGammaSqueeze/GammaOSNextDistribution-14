/******************************************************************************
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

/*
 * DAL I2C port implementation for linux
 *
 * Project: Trusted NFC Linux
 *
 */
#include <errno.h>
#include <fcntl.h>
#include <hardware/nfc.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/select.h>
#include <termios.h>
#include <unistd.h>

#include <NfccI2cTransport.h>
#include <phNfcStatus.h>
#include <phNxpLog.h>
#include <string.h>
#include "phNxpNciHal_utils.h"

#define CRC_LEN 2
#define NORMAL_MODE_HEADER_LEN 3
#define FW_DNLD_HEADER_LEN 2
#define FW_DNLD_LEN_OFFSET 1
#define NORMAL_MODE_LEN_OFFSET 2
#define FLUSH_BUFFER_SIZE 0xFF
extern phTmlNfc_i2cfragmentation_t fragmentation_enabled;
extern phTmlNfc_Context_t* gpphTmlNfc_Context;
/*******************************************************************************
**
** Function         Close
**
** Description      Closes NFCC device
**
** Parameters       pDevHandle - device handle
**
** Returns          None
**
*******************************************************************************/
void NfccI2cTransport::Close(void* pDevHandle) {
  if (NULL != pDevHandle) {
    close((int)(intptr_t)pDevHandle);
  }
  sem_destroy(&mTxRxSemaphore);
  return;
}

/*******************************************************************************
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
*******************************************************************************/
NFCSTATUS NfccI2cTransport::OpenAndConfigure(pphTmlNfc_Config_t pConfig,
                                             void** pLinkHandle) {
  int nHandle;
  NFCSTATUS status = NFCSTATUS_SUCCESS;
  NXPLOG_TML_D("%s Opening port=%s\n", __func__, pConfig->pDevName);
  /* open port */
  nHandle = open((const char*)pConfig->pDevName, O_RDWR);
  if (nHandle < 0) {
    NXPLOG_TML_E("_i2c_open() Failed: retval %x", nHandle);
    *pLinkHandle = NULL;
    status = NFCSTATUS_INVALID_DEVICE;
  } else {
    *pLinkHandle = (void*)((intptr_t)nHandle);
    if (0 != sem_init(&mTxRxSemaphore, 0, 1)) {
      NXPLOG_TML_E("%s Failed: reason sem_init : retval %x", __func__, nHandle);
      status = NFCSTATUS_FAILED;
    }
  }
  return status;
}

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
bool NfccI2cTransport::Flushdata(pphTmlNfc_Config_t pConfig) {
  int retRead = 0;
  int nHandle;
  uint8_t pBuffer[FLUSH_BUFFER_SIZE];
  NXPLOG_TML_D("%s: Enter", __func__);
  nHandle = open((const char*)pConfig->pDevName, O_RDWR | O_NONBLOCK);
  if (nHandle < 0) {
    NXPLOG_TML_E("%s: _i2c_open() Failed: retval %x", __func__, nHandle);
    return false;
  }
  do {
    retRead = read(nHandle, pBuffer, sizeof(pBuffer));
    if (retRead > 0) {
      phNxpNciHal_print_packet("RECV", pBuffer, retRead);
      usleep(2 * 1000);
    }
  } while (retRead > 0);
  close(nHandle);
  NXPLOG_TML_D("%s: Exit", __func__);
  return true;
}

/*******************************************************************************
**
** Function         Read
**
** Description      Reads requested number of bytes from NFCC device into given
**                  buffer
**
** Parameters       pDevHandle       - valid device handle
**                  pBuffer          - buffer for read data
**                  nNbBytesToRead   - number of bytes requested to be read
**
** Returns          numRead   - number of successfully read bytes
**                  -1        - read operation failure
**
*******************************************************************************/
int NfccI2cTransport::Read(void* pDevHandle, uint8_t* pBuffer,
                           int nNbBytesToRead) {
  int ret_Read;
  int ret_Select;
  int numRead = 0;
  struct timeval tv;
  fd_set rfds;
  uint16_t totalBtyesToRead = 0;

  UNUSED_PROP(nNbBytesToRead);
  if (NULL == pDevHandle) {
    return -1;
  }

  if (bFwDnldFlag == false) {
    totalBtyesToRead = NORMAL_MODE_HEADER_LEN;
  } else {
    totalBtyesToRead = FW_DNLD_HEADER_LEN;
  }

  /* Read with 2 second timeout, so that the read thread can be aborted
     when the NFCC does not respond and we need to switch to FW download
     mode. This should be done via a control socket instead. */
  FD_ZERO(&rfds);
  FD_SET((int)(intptr_t)pDevHandle, &rfds);
  tv.tv_sec = 2;
  tv.tv_usec = 1;

  ret_Select =
      select((int)((intptr_t)pDevHandle + (int)1), &rfds, NULL, NULL, &tv);
  if (ret_Select < 0) {
    NXPLOG_TML_D("%s errno : %x", __func__, errno);
    return -1;
  } else if (ret_Select == 0) {
    NXPLOG_TML_D("%s Timeout", __func__);
    return -1;
  } else {
    ret_Read =
        read((int)(intptr_t)pDevHandle, pBuffer, totalBtyesToRead - numRead);
    if (ret_Read > 0 && !(pBuffer[0] == 0xFF && pBuffer[1] == 0xFF)) {
      numRead += ret_Read;
    } else if (ret_Read == 0) {
      NXPLOG_TML_E("%s [hdr]EOF", __func__);
      return -1;
    } else {
      NXPLOG_TML_E("%s [hdr] errno : %x", __func__, errno);
      NXPLOG_TML_E(" %s pBuffer[0] = %x pBuffer[1]= %x", __func__, pBuffer[0],
                   pBuffer[1]);
      return -1;
    }

    if (bFwDnldFlag && (pBuffer[0] != 0x00)) {
      bFwDnldFlag = false;
    }

    if (bFwDnldFlag == false) {
      totalBtyesToRead = NORMAL_MODE_HEADER_LEN;
    } else {
      totalBtyesToRead = FW_DNLD_HEADER_LEN;
    }

    if (numRead < totalBtyesToRead) {
      ret_Read = read((int)(intptr_t)pDevHandle, (pBuffer + numRead),
                      totalBtyesToRead - numRead);

      if (ret_Read != totalBtyesToRead - numRead) {
        NXPLOG_TML_E("%s [hdr] errno : %x", __func__, errno);
        return -1;
      } else {
        numRead += ret_Read;
      }
    }
    if (bFwDnldFlag == true) {
      totalBtyesToRead =
          pBuffer[FW_DNLD_LEN_OFFSET] + FW_DNLD_HEADER_LEN + CRC_LEN;
    } else {
      totalBtyesToRead =
          pBuffer[NORMAL_MODE_LEN_OFFSET] + NORMAL_MODE_HEADER_LEN;
    }
    if ((totalBtyesToRead - numRead) != 0) {
      ret_Read = read((int)(intptr_t)pDevHandle, (pBuffer + numRead),
                      totalBtyesToRead - numRead);
      if (ret_Read > 0) {
        numRead += ret_Read;
      } else if (ret_Read == 0) {
        NXPLOG_TML_E("%s [pyld] EOF", __func__);
        return -1;
      } else {
        if (bFwDnldFlag == false) {
          NXPLOG_TML_D("_i2c_read() [hdr] received");
          phNxpNciHal_print_packet("RECV", pBuffer, NORMAL_MODE_HEADER_LEN);
        }
        NXPLOG_TML_E("%s [pyld] errno : %x", __func__, errno);
        return -1;
      }
    } else {
      NXPLOG_TML_E("%s _>>>>> Empty packet received !!", __func__);
    }
  }
  return numRead;
}

/*******************************************************************************
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
*******************************************************************************/
int NfccI2cTransport::Write(void* pDevHandle, uint8_t* pBuffer,
                            int nNbBytesToWrite) {
  int ret;
  int numWrote = 0;
  int numBytes = nNbBytesToWrite;
  if (NULL == pDevHandle) {
    return -1;
  }
  if (fragmentation_enabled == I2C_FRAGMENATATION_DISABLED &&
      nNbBytesToWrite > gpphTmlNfc_Context->fragment_len) {
    NXPLOG_TML_D(
        "%s data larger than maximum I2C  size,enable I2C fragmentation",
        __func__);
    return -1;
  }
  while (numWrote < nNbBytesToWrite) {
    if (fragmentation_enabled == I2C_FRAGMENTATION_ENABLED &&
        nNbBytesToWrite > gpphTmlNfc_Context->fragment_len) {
      if (nNbBytesToWrite - numWrote > gpphTmlNfc_Context->fragment_len) {
        numBytes = numWrote + gpphTmlNfc_Context->fragment_len;
      } else {
        numBytes = nNbBytesToWrite;
      }
    }
    ret = write((int)(intptr_t)pDevHandle, pBuffer + numWrote,
                numBytes - numWrote);
    if (ret > 0) {
      numWrote += ret;
      if (fragmentation_enabled == I2C_FRAGMENTATION_ENABLED &&
          numWrote < nNbBytesToWrite) {
        usleep(500);
      }
    } else if (ret == 0) {
      NXPLOG_TML_D("%s EOF", __func__);
      return -1;
    } else {
      NXPLOG_TML_D("%s errno : %x", __func__, errno);
      if (errno == EINTR || errno == EAGAIN) {
        continue;
      }
      return -1;
    }
  }

  return numWrote;
}

/*******************************************************************************
**
** Function         Reset
**
** Description      Reset NFCC device, using VEN pin
**
** Parameters       pDevHandle     - valid device handle
**                  eType          - reset level
**
** Returns           0   - reset operation success
**                  -1   - reset operation failure
**
*******************************************************************************/
int NfccI2cTransport::NfccReset(void* pDevHandle, NfccResetType eType) {
  int ret = -1;
  NXPLOG_TML_D("%s, VEN eType %u", __func__, eType);

  if (NULL == pDevHandle) {
    return -1;
  }

  ret = ioctl((int)(intptr_t)pDevHandle, NFC_SET_PWR, eType);
  if (ret < 0) {
    NXPLOG_TML_E("%s :failed errno = 0x%x", __func__, errno);
  }
  if ((eType != MODE_FW_DWNLD_WITH_VEN && eType != MODE_FW_DWND_HIGH) &&
      ret == 0) {
    bFwDnldFlag = false;
  }

  return ret;
}
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
int NfccI2cTransport::UpdateReadPending(void* pDevHandle,
                                        NfcReadPending eType) {
  int ret = -1;
  if (NULL == pDevHandle) {
    return -1;
  }
  NXPLOG_TML_D("%s, %u", __func__, eType);
  ret = ioctl((int)(intptr_t)pDevHandle, NFC_SET_RESET_READ_PENDING, eType);
  if (ret != 0) {
    NXPLOG_TML_E("%s: %u ret = 0x%x", __func__, eType, ret);
  }
  return ret;
}

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
int NfccI2cTransport ::NfcGetGpioStatus(void* pDevHandle, uint32_t* status) {
  int ret = -1;
  if (NULL == pDevHandle) {
    return ret;
  }
  ret = ioctl((int)(intptr_t)pDevHandle, NFC_GET_GPIO_STATUS, status);
  if (ret != 0) {
    NXPLOG_TML_E("%s: ret = 0x%x", __func__, ret);
  }
  NXPLOG_TML_D("%s, %d", __func__, *status);
  return ret;
}
/*******************************************************************************
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
*******************************************************************************/
int NfccI2cTransport::EseReset(void* pDevHandle, EseResetType eType) {
  int ret = -1;
  NXPLOG_TML_D("%s, eType %u", __func__, eType);

  if (NULL == pDevHandle) {
    return -1;
  }
  ret = ioctl((int)(intptr_t)pDevHandle, ESE_SET_PWR, eType);
  if (ret < 0) {
    NXPLOG_TML_E("%s :failed errno = 0x%x", __func__, errno);
  }
  return ret;
}

/*******************************************************************************
**
** Function         EnableFwDnldMode
**
** Description      updates the state to Download mode
**
** Parameters       True/False
**
** Returns          None
*******************************************************************************/
void NfccI2cTransport::EnableFwDnldMode(bool mode) { bFwDnldFlag = mode; }

/*******************************************************************************
**
** Function         IsFwDnldModeEnabled
**
** Description      Returns the current mode
**
** Parameters       none
**
** Returns           Current mode download/NCI
*******************************************************************************/
bool_t NfccI2cTransport::IsFwDnldModeEnabled(void) { return bFwDnldFlag; }
