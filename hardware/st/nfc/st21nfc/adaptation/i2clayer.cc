/** ----------------------------------------------------------------------
 *
 * Copyright (C) 2013 ST Microelectronics S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 ----------------------------------------------------------------------*/

#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <linux/input.h> /* not required for all builds */
#include <poll.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include "android_logmsg.h"
#include "halcore.h"
#include "halcore_private.h"
#include "hal_config.h"

#define ST21NFC_MAGIC 0xEA

#define ST21NFC_GET_WAKEUP _IOR(ST21NFC_MAGIC, 0x01, unsigned int)
#define ST21NFC_PULSE_RESET _IOR(ST21NFC_MAGIC, 0x02, unsigned int)
#define ST21NFC_SET_POLARITY_RISING _IOR(ST21NFC_MAGIC, 0x03, unsigned int)
#define ST21NFC_SET_POLARITY_FALLING _IOR(ST21NFC_MAGIC, 0x04, unsigned int)
#define ST21NFC_SET_POLARITY_HIGH _IOR(ST21NFC_MAGIC, 0x05, unsigned int)
#define ST21NFC_SET_POLARITY_LOW _IOR(ST21NFC_MAGIC, 0x06, unsigned int)
#define ST21NFC_RECOVERY _IOR(ST21NFC_MAGIC, 0x08, unsigned int)
#define ST21NFC_CLK_ENABLE _IOR(ST21NFC_MAGIC, 0x11, unsigned int)
#define ST21NFC_CLK_DISABLE _IOR(ST21NFC_MAGIC, 0x12, unsigned int)
#define ST21NFC_CLK_STATE _IOR(ST21NFC_MAGIC, 0x13, unsigned int)

#define LINUX_DBGBUFFER_SIZE 300

extern "C" { bool android_nfc_nfc_read_polling_loop_st_shim(); }

static int fidI2c = 0;
static int cmdPipe[2] = {0, 0};
static int notifyResetRequest = 0;
static bool recovery_mode = false;

static struct pollfd event_table[3];
static pthread_t threadHandle = (pthread_t)NULL;
pthread_mutex_t i2ctransport_mtx = PTHREAD_MUTEX_INITIALIZER;

unsigned long hal_ctrl_clk = 0;
unsigned long hal_activerw_timer = 0;

/**************************************************************************************************
 *
 *                                      Private API Declaration
 *
 **************************************************************************************************/

static int i2cSetPolarity(int fid, bool low, bool edge);
static int i2cResetPulse(int fid);
static int SetToRecoveryMode(int fid);
static int i2cRead(int fid, uint8_t* pvBuffer, int length);
static int i2cGetGPIOState(int fid);
static int i2cWrite(int fd, const uint8_t* pvBuffer, int length);

static const int T_CERx  = 0x09;
static const int T_fieldOn  = 0x10;
static const int T_fieldOff  = 0x11;
static const int T_CERxError  = 0x19;

static const unsigned char PROPRIETARY_GID = 0x6F;
static const unsigned char ANDROID_OID = 0x0C;
static const unsigned char TYPE_REMOTE_FIELD = 0x00;
static const unsigned char TYPE_REQA = 0x01;
static const unsigned char TYPE_REQB = 0x02;
static const unsigned char TYPE_REQF = 0x03;
static const unsigned char TYPE_UNKNOWN = 0x07;


typedef union timestamp_bytes {
  unsigned char ts1;
  unsigned char ts2;
  unsigned char ts3;
  unsigned char ts4;
} timestamp_bytes;

void sendFieldChange(HALHANDLE hHAL, timestamp_bytes timestamp, bool on) {
  unsigned char value_len = 0x06;
  unsigned char msg[10] = {PROPRIETARY_GID, ANDROID_OID, TYPE_REMOTE_FIELD,
                            value_len, timestamp.ts1, timestamp.ts2, timestamp.ts3, timestamp.ts4,
                            0xFF /* no gain data */, static_cast<unsigned char>(on ? 0x01 : 0x00)};
  HalSendUpstream(hHAL, msg, sizeof(msg));
}

void sendKnownPollingLoopFrame(HALHANDLE hHAL, char type, timestamp_bytes timestamp, unsigned char gain) {
  unsigned char value_len = 0x05;
  unsigned char type_code;
  switch (type) {
    case 'A':
      type_code = TYPE_REQA;
      break;
    case 'B':
      type_code = TYPE_REQB;
      break;
    case 'F':
      type_code = TYPE_REQF;
      break;
    default:
      STLOG_HAL_D("VSLogNFC unknown frame type in known frame");
      return;
  }
  unsigned char msg[9] = {PROPRIETARY_GID, ANDROID_OID, type_code, value_len,
                          timestamp.ts1, timestamp.ts2, timestamp.ts3, timestamp.ts4, gain};
  HalSendUpstream(hHAL, msg, sizeof(msg));
}

void sendUnknownPollingLoopFrame(HALHANDLE hHAL, timestamp_bytes timestamp, unsigned char gain,
                                 unsigned char* buffer, unsigned char data_len) {
  size_t header_len = 9;
  size_t msg_len = header_len + data_len;
  unsigned char value_len = data_len + 5;
  unsigned char msg_header[9] = {PROPRIETARY_GID, ANDROID_OID, TYPE_UNKNOWN, value_len,
                                 timestamp.ts1, timestamp.ts2, timestamp.ts3, timestamp.ts4, gain};
  unsigned char* msg_buffer = (unsigned char*)malloc(msg_len);
  memcpy(msg_buffer, msg_header, header_len);
  memcpy(msg_buffer + header_len, buffer, data_len);
  HalSendUpstream(hHAL, msg_buffer, msg_len);
  free(msg_buffer);
}

void handlePollingLoopData(HALHANDLE hHAL, int data_len,
                           unsigned char* tlvBuffer) {
  timestamp_bytes timestamp;
  timestamp.ts1 = tlvBuffer[data_len - 4];
  timestamp.ts2 = tlvBuffer[data_len - 3];
  timestamp.ts3 = tlvBuffer[data_len - 2];
  timestamp.ts4 = tlvBuffer[data_len - 1];

  int t = tlvBuffer[0];
  switch (t) {
    case T_fieldOn:
      sendFieldChange(hHAL, timestamp, true);
      break;
    case T_fieldOff:
      sendFieldChange(hHAL, timestamp, false);
      break;
    case T_CERxError:
    case T_CERx:
    {
      unsigned char gain;
      if ((tlvBuffer[3] & 0x30) == 0x20) {
        // ST54J
        gain = (tlvBuffer[3] & 0xF0) >> 4;
      } else {
        // ST21
        gain = tlvBuffer[3];
      }
      switch (tlvBuffer[2] & 0xF) {
        case 0x1:
          sendKnownPollingLoopFrame(hHAL, 'A', timestamp, gain);
          break;
        case 0x7:
          sendKnownPollingLoopFrame(hHAL, 'B', timestamp, gain);
          break;
        case 0x9:
          sendKnownPollingLoopFrame(hHAL, 'F', timestamp, gain);
          break;
        case 0x3:
          sendUnknownPollingLoopFrame(hHAL, timestamp, gain, tlvBuffer + 4,
                                      data_len - 4);
        break;
      }
    }
  }
}

void notifyPollingLoopFrames(HALHANDLE hHAL, unsigned char* buffer, int data_len) {
  int current_tlv_length = 0;
  for (int current_tlv_pos = 6;current_tlv_pos + current_tlv_length + 1 <= data_len; current_tlv_pos+= current_tlv_length) {
    current_tlv_length = buffer[current_tlv_pos + 1] + 2;
    unsigned char* tlvBuffer = buffer + current_tlv_pos;
    handlePollingLoopData(hHAL, current_tlv_length, tlvBuffer);
  }
}

/**************************************************************************************************
 *
 *                                      Public API Entry-Points
 *
 **************************************************************************************************/

/**
 * Worker thread for I2C data processing.
 * On exit of this thread, destroy the HAL thread instance.
 * @param arg  Handle of the HAL layer
 */
static void* I2cWorkerThread(void* arg) {
  bool closeThread = false;
  HALHANDLE hHAL = (HALHANDLE)arg;
  STLOG_HAL_D("echo thread started...\n");
  bool readOk = false;
  int eventNum = (notifyResetRequest <= 0) ? 2 : 3;
  bool reseting = false;

  do {
    event_table[0].fd = fidI2c;
    event_table[0].events = POLLIN;
    event_table[0].revents = 0;

    event_table[1].fd = cmdPipe[0];
    event_table[1].events = POLLIN;
    event_table[1].revents = 0;

    event_table[2].fd = notifyResetRequest;
    event_table[2].events = POLLPRI;
    event_table[2].revents = 0;

    STLOG_HAL_V("echo thread go to sleep...\n");

    int poll_status = poll(event_table, eventNum, -1);

    if (-1 == poll_status) {
      poll_status = errno;
      STLOG_HAL_E("error in poll call : %d - %s\n", poll_status,
                  strerror(poll_status));
      if ((poll_status == EINTR) || (poll_status == EAGAIN)) continue;

      // other errors, we stop.
      break;
    }

    if (event_table[0].revents & POLLIN) {
      STLOG_HAL_V("echo thread wakeup from chip...\n");
      uint8_t buffer[300];
      int count = 0;

      do {
        if (recovery_mode) {
          break;
        }
        // load first four bytes:
        int bytesRead = i2cRead(fidI2c, buffer, 3);

        if (bytesRead == 3) {
          if ((buffer[0] != 0x7E) && (buffer[1] != 0x7E)) {
            readOk = true;
          } else {
            if (buffer[1] != 0x7E) {
              STLOG_HAL_W(
                  "Idle data: 2nd byte is 0x%02x\n, reading next 2 bytes",
                  buffer[1]);
              buffer[0] = buffer[1];
              buffer[1] = buffer[2];
              bytesRead = i2cRead(fidI2c, buffer + 2, 1);
              if (bytesRead == 1) {
                readOk = true;
              }
            } else if (buffer[2] != 0x7E) {
              STLOG_HAL_W("Idle data: 3rd byte is 0x%02x\n, reading next  byte",
                          buffer[2]);
              buffer[0] = buffer[2];
              bytesRead = i2cRead(fidI2c, buffer + 1, 2);
              if (bytesRead == 2) {
                readOk = true;
              }
            } else {
              STLOG_HAL_W("received idle data\n");
            }
          }

          if (readOk == true) {
            int remaining = buffer[2];
            bytesRead = 0;
            if (remaining != 0) {
              // read and pass to HALCore
              bytesRead = i2cRead(fidI2c, buffer + 3, remaining);
            }
            if (bytesRead == remaining) {
              DispHal("RX DATA", buffer, 3 + bytesRead);
              if (android_nfc_nfc_read_polling_loop_st_shim() &&
                buffer[0] == 0x6f /* Proprietary NTF */ &&
                buffer[1] == 0x02 /* VS Log */) {
                notifyPollingLoopFrames(hHAL, buffer, 3 + bytesRead);
              }
              HalSendUpstream(hHAL, buffer, 3 + bytesRead);
            } else {
              readOk = false;
              STLOG_HAL_E("! didn't read expected bytes from i2c\n");
            }
          }

        } else {
          STLOG_HAL_E("! didn't read 3 requested bytes from i2c\n");
        }

        readOk = false;
        memset(buffer, 0xca, sizeof(buffer));
        /* read while we have data available, up to 2 times then allow writes */
      } while ((i2cGetGPIOState(fidI2c) == 1) && (count++ < 2));
    }

    if (event_table[1].revents & POLLIN) {
      STLOG_HAL_V("thread received command.. \n");

      char cmd = 0;
      read(cmdPipe[0], &cmd, 1);

      switch (cmd) {
        case 'X':
          STLOG_HAL_D("received close command\n");
          closeThread = true;
          break;

        case 'W': {
          size_t length;
          uint8_t buffer[MAX_BUFFER_SIZE];
          STLOG_HAL_V("received write command\n");
          read(cmdPipe[0], &length, sizeof(length));
          if (length <= MAX_BUFFER_SIZE) {
            read(cmdPipe[0], buffer, length);
            i2cWrite(fidI2c, buffer, length);
          } else {
            STLOG_HAL_E(
                "! received bigger data than expected!! Data not transmitted "
                "to NFCC \n");
            size_t bytes_read = 1;
            // Read all the data to empty but do not use it as not expected
            while ((bytes_read > 0) && (length > 0)) {
              bytes_read = read(cmdPipe[0], buffer, MAX_BUFFER_SIZE);
              length = length - bytes_read;
            }
          }
        } break;
      }
    }

    if (event_table[2].revents & POLLPRI && eventNum > 2) {
      STLOG_HAL_W("thread received reset request command.. \n");
      char reset[10];
      int byte;
      reset[9] = '\0';
      lseek(notifyResetRequest, 0, SEEK_SET);
      byte = read(notifyResetRequest, &reset, sizeof(reset));
      if (byte < 10) {
        reset[byte] = '\0';
      }
      if (byte > 0 && reset[0] =='1' && reseting == false) {
        STLOG_HAL_E("trigger NFCC reset.. \n");
        reseting = true;
        i2cResetPulse(fidI2c);
      }
    }
  } while (!closeThread);

  // Stop here if we got a serious error above.
  assert(closeThread);

  close(fidI2c);
  close(cmdPipe[0]);
  close(cmdPipe[1]);
  if (notifyResetRequest > 0) {
    close(notifyResetRequest);
  }

  HalDestroy(hHAL);
  STLOG_HAL_D("thread exit\n");
  return 0;
}

/**
 * Put command into queue for worker thread to process it.
 * @param x Command 'X' to close I2C layer or 'W' to write data down to I2C
 * layer followed by data frame
 * @param len Size of command or data
 * @return
 */
int I2cWriteCmd(const uint8_t* x, size_t len) {
  return write(cmdPipe[1], x, len);
}

/**
 * Initialize the I2C layer.
 * @param dev NFC NCI device context, NFC callbacks for control/data, HAL handle
 * @param callb HAL Core callback upon reception on I2C
 * @param pHandle HAL context handle
 */
bool I2cOpenLayer(void* dev, HAL_CALLBACK callb, HALHANDLE* pHandle) {
  uint32_t NoDbgFlag = HAL_FLAG_DEBUG;
  char nfc_dev_node[64];
  char nfc_reset_req_node[128];

  /*Read device node path*/
  if (!GetStrValue(NAME_ST_NFC_DEV_NODE, (char *)nfc_dev_node,
                   sizeof(nfc_dev_node))) {
    STLOG_HAL_D("Open /dev/st21nfc\n");
    strcpy(nfc_dev_node, "/dev/st21nfc");
  }
  /*Read nfcc reset request sysfs*/
  if (GetStrValue(NAME_ST_NFC_RESET_REQ_SYSFS, (char *)nfc_reset_req_node,
                  sizeof(nfc_reset_req_node))) {
    STLOG_HAL_D("Open %s\n", nfc_reset_req_node);
    notifyResetRequest = open(nfc_reset_req_node, O_RDONLY);
    if (notifyResetRequest < 0) {
      STLOG_HAL_E("unable to open %s (%s) \n", nfc_reset_req_node, strerror(errno));
    }
  }

  (void)pthread_mutex_lock(&i2ctransport_mtx);

  fidI2c = open(nfc_dev_node, O_RDWR);
  if (fidI2c < 0) {
    STLOG_HAL_W("unable to open %s (%s) \n", nfc_dev_node, strerror(errno));
    (void)pthread_mutex_unlock(&i2ctransport_mtx);
    return false;
  }

  GetNumValue(NAME_STNFC_CONTROL_CLK, &hal_ctrl_clk, sizeof(hal_ctrl_clk));
  GetNumValue(NAME_STNFC_ACTIVERW_TIMER, &hal_activerw_timer,
              sizeof(hal_activerw_timer));

  if (hal_ctrl_clk) {
    if (ioctl(fidI2c, ST21NFC_CLK_DISABLE, NULL) < 0) {
      char msg[LINUX_DBGBUFFER_SIZE];
      strerror_r(errno, msg, LINUX_DBGBUFFER_SIZE);
      STLOG_HAL_E("ST21NFC_CLK_DISABLE failed errno %d(%s)", errno, msg);
    }
  }
  i2cSetPolarity(fidI2c, false, false);
  i2cResetPulse(fidI2c);

  if ((pipe(cmdPipe) == -1)) {
    STLOG_HAL_W("unable to open cmdpipe\n");
    (void)pthread_mutex_unlock(&i2ctransport_mtx);
    return false;
  }

  *pHandle = HalCreate(dev, callb, NoDbgFlag);

  if (!*pHandle) {
    STLOG_HAL_E("failed to create NFC HAL Core \n");
    (void)pthread_mutex_unlock(&i2ctransport_mtx);
    return false;
  }

  (void)pthread_mutex_unlock(&i2ctransport_mtx);

  return (pthread_create(&threadHandle, NULL, I2cWorkerThread, *pHandle) == 0);
}

/**
 * Terminates the I2C layer.
 */
void I2cCloseLayer() {
  uint8_t cmd = 'X';
  int ret;
  ALOGD("%s: enter\n", __func__);

  (void)pthread_mutex_lock(&i2ctransport_mtx);

  if (threadHandle == (pthread_t)NULL) {
    (void)pthread_mutex_unlock(&i2ctransport_mtx);
    return;
  }

  I2cWriteCmd(&cmd, sizeof(cmd));
  /* wait for terminate */
  ret = pthread_join(threadHandle, (void**)NULL);
  if (ret != 0) {
    ALOGE("%s: failed to wait for thread (%d)", __func__, ret);
  }
  threadHandle = (pthread_t)NULL;
  (void)pthread_mutex_unlock(&i2ctransport_mtx);
}

/**
 * Terminates the I2C layer.
 */
void I2cResetPulse() {
  ALOGD("%s: enter\n", __func__);

  (void)pthread_mutex_lock(&i2ctransport_mtx);

  i2cResetPulse(fidI2c);
  (void)pthread_mutex_unlock(&i2ctransport_mtx);
}
void I2cRecovery() {
  ALOGD("%s: enter\n", __func__);

  (void)pthread_mutex_lock(&i2ctransport_mtx);
  recovery_mode = true;
  SetToRecoveryMode(fidI2c);
  recovery_mode = false;
  (void)pthread_mutex_unlock(&i2ctransport_mtx);
}
/**************************************************************************************************
 *
 *                                      Private API Definition
 *
 **************************************************************************************************/
/**
 * Call the st21nfc driver to adjust wake-up polarity.
 * @param fid File descriptor for NFC device
 * @param low Polarity (HIGH or LOW)
 * @param edge Polarity (RISING or FALLING)
 * @return Result of IOCTL system call (0 if ok)
 */
static int i2cSetPolarity(int fid, bool low, bool edge) {
  int result;
  unsigned int io_code;

  if (low) {
    if (edge) {
      io_code = ST21NFC_SET_POLARITY_FALLING;
    } else {
      io_code = ST21NFC_SET_POLARITY_LOW;
    }

  } else {
    if (edge) {
      io_code = ST21NFC_SET_POLARITY_RISING;
    } else {
      io_code = ST21NFC_SET_POLARITY_HIGH;
    }
  }

  if (-1 == (result = ioctl(fid, io_code, NULL))) {
    result = -1;
  }

  return result;
} /* i2cSetPolarity*/

/**
 * Call the st21nfc driver to generate a 30ms pulse on RESET line.
 * @param fid File descriptor for NFC device
 * @return Result of IOCTL system call (0 if ok)
 */
static int i2cResetPulse(int fid) {
  int result;

  if (-1 == (result = ioctl(fid, ST21NFC_PULSE_RESET, NULL))) {
    result = -1;
  }
  STLOG_HAL_D("! i2cResetPulse!!, result = %d", result);
  return result;
} /* i2cResetPulse*/

/**
 * Call the st21nfc driver to generate pulses on RESET line to get a recovery.
 * @param fid File descriptor for NFC device
 * @return Result of IOCTL system call (0 if ok)
 */
static int SetToRecoveryMode(int fid) {
  int result;

  if (-1 == (result = ioctl(fid, ST21NFC_RECOVERY, NULL))) {
    result = -1;
  }
  STLOG_HAL_D("! SetToRecoveryMode!!, result = %d", result);
  return result;
} /* SetToRecoveryMode*/

/**
 * Write data to st21nfc, on failure do max 3 retries.
 * @param fid File descriptor for NFC device
 * @param pvBuffer Data to write
 * @param length Data size
 * @return 0 if bytes written, -1 if error
 */
static int i2cWrite(int fid, const uint8_t* pvBuffer, int length) {
  int retries = 0;
  int result = 0;
  int halfsecs = 0;
  int clk_state = -1;
  char msg[LINUX_DBGBUFFER_SIZE];

  if ((hal_ctrl_clk || hal_activerw_timer) && length >= 4 &&
      pvBuffer[0] == 0x20 && pvBuffer[1] == 0x09) {
    if (hal_activerw_timer && (pvBuffer[3] == 0x01 || pvBuffer[3] == 0x03)) {
      // screen off cases
      hal_wrapper_set_state(HAL_WRAPPER_STATE_SET_ACTIVERW_TIMER);
    }
    if (hal_ctrl_clk && 0 > (clk_state = ioctl(fid, ST21NFC_CLK_STATE, NULL))) {
      strerror_r(errno, msg, LINUX_DBGBUFFER_SIZE);
      STLOG_HAL_E("ST21NFC_CLK_STATE failed errno %d(%s)", errno, msg);
      clk_state = -1;
    }
    STLOG_HAL_D("ST21NFC_CLK_STATE = %d", clk_state);
    if (clk_state == 1 && (pvBuffer[3] == 0x01 || pvBuffer[3] == 0x03)) {
      // screen off cases
      if (ioctl(fid, ST21NFC_CLK_DISABLE, NULL) < 0) {
        strerror_r(errno, msg, LINUX_DBGBUFFER_SIZE);
        STLOG_HAL_E("ST21NFC_CLK_DISABLE failed errno %d(%s)", errno, msg);
      } else if (0 > (clk_state = ioctl(fid, ST21NFC_CLK_STATE, NULL))) {
        strerror_r(errno, msg, LINUX_DBGBUFFER_SIZE);
        STLOG_HAL_E("ST21NFC_CLK_STATE failed errno %d(%s)", errno, msg);
        clk_state = -1;
      }
      if (clk_state != 0) {
        STLOG_HAL_E("CLK_DISABLE STATE ERROR clk_state = %d", clk_state);
      }
    } else if (clk_state == 0 && (pvBuffer[3] == 0x02 || pvBuffer[3] == 0x00)) {
      // screen on cases
      if (ioctl(fid, ST21NFC_CLK_ENABLE, NULL) < 0) {
        strerror_r(errno, msg, LINUX_DBGBUFFER_SIZE);
        STLOG_HAL_E("ST21NFC_CLK_ENABLE failed errno %d(%s)", errno, msg);
      } else if (0 > (clk_state = ioctl(fid, ST21NFC_CLK_STATE, NULL))) {
        strerror_r(errno, msg, LINUX_DBGBUFFER_SIZE);
        STLOG_HAL_E("ST21NFC_CLK_STATE failed errno %d(%s)", errno, msg);
        clk_state = -1;
      }
      if (clk_state != 1) {
        STLOG_HAL_E("CLK_ENABLE STATE ERROR clk_state = %d", clk_state);
      }
    }
  }

redo:
  while (retries < 3) {
    result = write(fid, pvBuffer, length);

    if (result < 0) {

      strerror_r(errno, msg, LINUX_DBGBUFFER_SIZE);
      STLOG_HAL_W("! i2cWrite!!, errno is '%s'", msg);
      usleep(4000);
      retries++;
    } else if (result > 0) {
      result = 0;
      return result;
    } else {
      STLOG_HAL_W("write on i2c failed, retrying\n");
      usleep(4000);
      retries++;
    }
  }
  /* If we're here, we failed to write to NFCC. Retry after 500ms because some
  CPUs have shown such long unavailability sometimes */
  if (halfsecs < 10) {
    usleep(500000);
    halfsecs++;
    goto redo;
  }
  /* The CLF did not recover, give up */
  return -1;
} /* i2cWrite */

/**
 * Read data from st21nfc, on failure do max 3 retries.
 *
 * @param fid File descriptor for NFC device
 * @param pvBuffer Buffer where to copy read data
 * @param length Data size to read
 * @return Length of read data, -1 if error
 */
static int i2cRead(int fid, uint8_t* pvBuffer, int length) {
  int retries = 0;
  int result = -1;

  while ((retries < 3) && (result < 0)) {
    result = read(fid, pvBuffer, length);

    if (result == -1) {
      int e = errno;
      if (e == EAGAIN) {
        /* File is nonblocking, and no data is available.
         * This is not an error condition!
         */
        result = 0;
        STLOG_HAL_D(
            "## i2cRead - got EAGAIN. No data available. return 0 bytes");
      } else {
        /* unexpected result */
        char msg[LINUX_DBGBUFFER_SIZE];
        strerror_r(e, msg, LINUX_DBGBUFFER_SIZE);
        STLOG_HAL_W("## i2cRead returns %d errno %d (%s)", result, e, msg);
      }
    }

    if (result < 0) {
      if (retries < 3) {
        /* delays are different and increasing for the three retries. */
        static const uint8_t delayTab[] = {2, 3, 5};
        int delay = delayTab[retries];

        retries++;
        STLOG_HAL_W("## i2cRead retry %d/3 in %d milliseconds.", retries,
                    delay);
        usleep(delay * 1000);
        continue;
      }
    }
  }
  return result;
} /* i2cRead */

/**
 * Get the activation status of wake-up pin from st21nfc.
 *  The decision 'active' depends on selected polarity.
 *  The decision is handled inside the driver(st21nfc).
 * @param fid File descriptor for NFC device
 * @return
 *  Result < 0:     Error condition
 *  Result > 0:     Pin active
 *  Result = 0:     Pin not active
 */
static int i2cGetGPIOState(int fid) {
  int result;

  if (-1 == (result = ioctl(fid, ST21NFC_GET_WAKEUP, NULL))) {
    result = -1;
  }

  return result;
} /* i2cGetGPIOState */
