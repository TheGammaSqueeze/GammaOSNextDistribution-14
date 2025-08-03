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
#define LOG_TAG "StEse-T1protocol"
#include "T1protocol.h"
#include <errno.h>
#include <string.h>
#include "SpiLayerComm.h"
#include "SpiLayerDriver.h"
#include "SpiLayerInterface.h"
#include "android_logmsg.h"
#include "utils-lib/DataMgmt.h"
#include "utils-lib/Iso13239CRC.h"
#include "utils-lib/Tpdu.h"

uint8_t SEQ_NUM_MASTER;
uint8_t SEQ_NUM_SLAVE;
uint8_t recoveryStatus;
T1TProtocol_TransceiveState gNextCmd = Idle;
T1TProtocol_TransceiveState gOriginalCmd = Idle;

/*******************************************************************************
**
** Function         T1protocol_getValidPcb
**
** Description       Form a valid pcb according to the Tpdu type, subtype,
**                   master sequence number,slave sequence number and isLast.
**
** Parameters       type         - The Tpdu type (IBlock, RBlock or SBlock).
**                  subtype      - The different subtype options(for RBlock).
**                  numSeqMaster - Master sequence number.
**                  numseqSlave  - Slave sequence number.
**                  isLast       - Indicate if there are more tpdus to send
**                  within the same APDU.
**
** Returns          pcb          - Computed PCB.
**
*******************************************************************************/
uint8_t T1protocol_getValidPcb(TpduType type, RBlockType subtype,
                               uint8_t numSeqMaster, uint8_t numseqSlave,
                               bool isLast) {
  uint8_t pcb = 0xFF;

  switch (type) {
    case IBlock:
      pcb = 0x00;
      // Set the Ns according to the numSeqMaster
      if (numSeqMaster == 1) {
        pcb |= (uint8_t)IBLOCK_NS_BIT_MASK;
      }

      // If the Tpdu is a part of chain, set the M bit inside the pcb.
      if (isLast == APDU_PART_IS_NOT_LAST) {
        pcb |= IBLOCK_M_BIT_MASK;
      }
      break;

    case RBlock:
      pcb = 0x80;
      if (subtype == ErrorFree) {
        // Set the bit for the N(R)
        pcb |= ((uint8_t)numseqSlave) << 4;
      } else if (subtype == ChecksumError) {
        // Set the bits for the subtype checksum error and the N(R)
        pcb |= 0b00000001;
        pcb |= ((uint8_t)numseqSlave) << 4;
      } else if (subtype == OtherErrors) {
        // Set the bits for the subtype other errors and the N(R)
        pcb |= 0b00000010;
        pcb |= ((uint8_t)numseqSlave) << 4;
      }
      break;

    default:
      break;
  }

  return pcb;
}

/*******************************************************************************
**
** Function         T1protocol_checkResponseTpduChecksum
**
** Description      Check if the checksum of a given tpdu is well formed.
**
** Parameters       cmdTpdu    -The TPDU to check
**
** Returns          0 If checksum is ok, -1 otherwise.
**
*******************************************************************************/
int T1protocol_checkResponseTpduChecksum(Tpdu* respTpdu) {
  if (ATP.checksumType == CRC) {
    // Check CRC
    uint8_t arrayTpdu[TPDU_PROLOGUE_LENGTH + respTpdu->len + TPDU_CRC_LENGTH];
    Tpdu_toByteArray(respTpdu, arrayTpdu);
    if (computeCrc(arrayTpdu, TPDU_PROLOGUE_LENGTH + respTpdu->len) !=
        respTpdu->checksum) {
      return -1;
    }
  } else if (ATP.checksumType == LRC) {
    // Check LRC
    // char arrayTpdu[TPDU_PROLOGUE_LENGTH + respTpdu->len + TPDU_LRC_LENGTH];
    // TODO: implement compute LRC
    return -1;
  }

  return 0;
}

/*******************************************************************************
**
** Function         T1protocol_checkResponsePcbConsistency
**
** Description      Check if the pcb of a given tpdu is valid.
**
** Parameters       cmdTpdu    -The TPDU to check
**
** Returns          0 If checksum is ok, -1 otherwise.
**
*******************************************************************************/
int T1protocol_checkResponsePcbConsistency(Tpdu* tpdu) {
  // Get the type of the incoming tpdu
  TpduType type = Tpdu_getType(tpdu);

  switch (type) {
    case IBlock:
      // Match the IBlock pcb received with the bits that must be 0. If
      // the result is higher than 0, means some of these bits was set to 1.
      if ((tpdu->pcb & 0b00011111)) {
        return -1;
      }
      break;

    case RBlock:
      // Match the RBlock pcb received with the bits that must be 0. If
      // the result is higher than 0, means some of these bits was set to 1.
      if ((tpdu->pcb & 0b01101100)) {
        return -1;
      }
      break;

    case SBlock:
      // Match the SBlock pcb received with the bits that must be 0. If
      // the result is higher than 0, means some of these bits was set to 1.
      if ((tpdu->pcb & 0b00010000)) {
        return -1;
      }
      break;

    default:
      break;
  }

  return 0;
}

/*******************************************************************************
**
** Function         T1protocol_checkResponseLenConsistency
**
** Description      Check if the length field of a given tpdu is valid.
**
** Parameters       cmdTpdu    -The TPDU to check
**
** Returns          0 If checksum is ok, -1 otherwise.
**
*******************************************************************************/
int T1protocol_checkResponseLenConsistency(Tpdu* tpdu) {
  // Check the length consistency according to the block type
  TpduType type = Tpdu_getType(tpdu);

  switch (type) {
    case IBlock:
      // If the last Tpdu received was an IBlock, the len must be lower or
      // equal than the ATP ifsd field.
      if (tpdu->len > ATP.ifsc) {
        return -1;
      }
      break;

    case RBlock:
      // If the last Tpdu received was an RBlock, the len must be 0.
      if (tpdu->len > 0) {
        return -1;
      }
      break;

    case SBlock:
      // If the last Tpdu received was an SBlock WTX... or IFS..., the length
      // must be 1. If the last Tpdu received was an SBlock
      // ABORT... or RESYNCH... the length must be 0.
      if ((tpdu->pcb == (uint8_t)SBLOCK_WTX_REQUEST_MASK) ||
          (tpdu->pcb == (uint8_t)SBLOCK_WTX_RESPONSE_MASK) ||
          (tpdu->pcb == (uint8_t)SBLOCK_IFS_REQUEST_MASK) ||
          (tpdu->pcb == (uint8_t)SBLOCK_IFS_RESPONSE_MASK)) {
        if (tpdu->len != 1) {
          return -1;
        }
      } else if ((tpdu->pcb == (uint8_t)SBLOCK_ABORT_REQUEST_MASK) ||
                 (tpdu->pcb == (uint8_t)SBLOCK_ABORT_RESPONSE_MASK) ||
                 (tpdu->pcb == (uint8_t)SBLOCK_RESYNCH_REQUEST_MASK) ||
                 (tpdu->pcb == (uint8_t)SBLOCK_RESYNCH_RESPONSE_MASK)) {
        if (tpdu->len != 0) {
          return -1;
        }
      }
      break;
  }

  return 0;
}

/*******************************************************************************
**
** Function         T1protocol_checkResponseSeqNumberConsistency
**
** Description      Check if the sequence number of a given tpdu is valid.
**
** Parameters       cmdTpdu    -The TPDU to check
**
** Returns          0 If checksum is ok, -1 otherwise.
**
*******************************************************************************/
int T1protocol_checkResponseSeqNumberConsistency(Tpdu* tpdu) {
  // Check the length consistency according to the block type
  TpduType type = Tpdu_getType(tpdu);

  uint8_t seqNumber;

  switch (type) {
    case IBlock:
      // CHeck if the sequence number received in the last IBlock matches the
      // expected.
      seqNumber = (tpdu->pcb & 0b01000000) >> 6;
      if (seqNumber != SEQ_NUM_SLAVE) {
        return -1;
      }
      break;

    case RBlock:
      // TODO
      // If the original command Tpdu was Iblock chained, both sequence
      // numbers are expected. If the original command Tpdu was Iblock
      // without chaining an Rblock with sequence number different than the
      // actual master sequence number is considered as invalid.
      /*if ((cmdTpdu->pcb & IBLOCK_M_BIT_MASK) == 0) {
          // Original Iblock without chaining
          if (T1protocol_isSequenceNumberOk(
                  cmdTpdu,
                  respTpdu) == false) {
              // Sequence number different from actual master sequence number
              return -1;
          }
      }*/
      break;

    default:
      break;
  }

  return 0;
}

/*******************************************************************************
**
** Function         T1protocol_checkSBlockResponseConsistency
**
** Description      Check if an SBlock response was received after having
**                  transmitted a SBlock request.
**
** Parameters       lastCmdTpduSent      - Last Tpdu sent.
**                  lastRespTpduReceived - Response received.
**
** Returns          0 If checksum is ok, -1 otherwise.
**
*******************************************************************************/
int T1protocol_checkSBlockResponseConsistency(Tpdu* lastCmdTpduSent,
                                              Tpdu* lastRespTpduReceived) {
  // Check if last Tpdu received was an SBlock(...response) after having
  // transmitted a SBlock(...request).

  if ((lastCmdTpduSent->pcb == (uint8_t)SBLOCK_WTX_REQUEST_MASK) ||
      (lastCmdTpduSent->pcb == (uint8_t)SBLOCK_ABORT_REQUEST_MASK) ||
      (lastCmdTpduSent->pcb == (uint8_t)SBLOCK_IFS_REQUEST_MASK) ||
      (lastCmdTpduSent->pcb == (uint8_t)SBLOCK_RESYNCH_REQUEST_MASK) ||
      (lastCmdTpduSent->pcb == (uint8_t)SBLOCK_SWRESET_REQUEST_MASK)) {
    uint8_t expectedPcbResponse;
    // Calculate the expected response according to the SBlock request
    // previously sent.
    expectedPcbResponse = lastCmdTpduSent->pcb | 0b00100000;

    if (expectedPcbResponse != lastRespTpduReceived->pcb) {
      return -1;
    }
  }

  return 0;
}

/*******************************************************************************
**
** Function         T1protocol_checkTpduConsistency
**
** Description      Check if the response TPDU is consistent
**                  (check the checksum, check if the pcb is valid and the
**                  expected one and check the len consistency).
**
** Parameters       lastCmdTpduSent       - Last Tpdu sent.
**                  lastRespTpduReceived  - Last response from the slave.
**
** Returns          0 If checksum is ok, -1 otherwise.
**
*******************************************************************************/
int T1protocol_checkTpduConsistency(Tpdu* lastCmdTpduSent,
                                    Tpdu* lastRespTpduReceived) {
  // Check checksum
  if (T1protocol_checkResponseTpduChecksum(lastRespTpduReceived) == -1) {
    return -1;
  }

  // Check pcb consistency
  if (T1protocol_checkResponsePcbConsistency(lastRespTpduReceived) == -1) {
    return -1;
  }

  // Check len consistency
  if (T1protocol_checkResponseLenConsistency(lastRespTpduReceived) == -1) {
    return -1;
  }

  // Check sequence number consistency
  if (T1protocol_checkResponseSeqNumberConsistency(lastRespTpduReceived) ==
      -1) {
    return -1;
  }

  // Check if a valid sBlock response has been received after having
  // transmitted an sBlock request
  if (T1protocol_checkSBlockResponseConsistency(lastCmdTpduSent,
                                                lastRespTpduReceived) == -1) {
    return -1;
  }

  return 0;
}

/*******************************************************************************
**
** Function         T1protocol_resetSequenceNumbers
**
** Description      Set the sequence numbers to it's initial values.
**
** Parameters       none
**
** Returns          void
**
*******************************************************************************/
void T1protocol_resetSequenceNumbers() {
  // Set the sequence numbers to it's initial values.
  SEQ_NUM_MASTER = 0;
  SEQ_NUM_SLAVE = 0;
}

/*******************************************************************************
**
** Function         T1protocol_updateMasterSequenceNumber
**
** Description      Update the master sequence number.
**
** Parameters       none
**
** Returns          void
**
*******************************************************************************/
void T1protocol_updateMasterSequenceNumber() {
  // Sequence numbers are module 2,
  SEQ_NUM_MASTER++;
  SEQ_NUM_MASTER %= 2;
}

/*******************************************************************************
**
** Function         T1protocol_updateSlaveSequenceNumber
**
** Description      Update the slave sequence number.
**
** Parameters       none
**
** Returns          void
**
*******************************************************************************/
void T1protocol_updateSlaveSequenceNumber() {
  // Sequence numbers are module 2,
  SEQ_NUM_SLAVE++;
  SEQ_NUM_SLAVE %= 2;
}

/*******************************************************************************
**
** Function         T1protocol_processIBlock
**
** Description      Process the last IBlock received from the slave.
**
** Parameters       originalCmdTpdu       - Original Tpdu sent.
**                  lastRespTpduReceived  - Last response from the slave.
**
** Returns          0 If all went is ok, -1 otherwise.
**
*******************************************************************************/
int T1protocol_processIBlock(Tpdu* originalCmdTpdu,
                             Tpdu* lastRespTpduReceived) {
  // The last IBlock received was the good one. Update the sequence
  // numbers needed.
  int rc = 0;
  TpduType type = Tpdu_getType(originalCmdTpdu);

  T1protocol_updateSlaveSequenceNumber();
  rc = DataMgmt_StoreDataInList(lastRespTpduReceived->len,
                                lastRespTpduReceived->data);

  if ((lastRespTpduReceived->pcb & IBLOCK_M_BIT_MASK) > 0) {
    gNextCmd = R_ACK;
  } else {
    if (type == IBlock) {
      T1protocol_updateMasterSequenceNumber();
    }
    gNextCmd = Idle;
  }
  return rc;
}

/*******************************************************************************
**
** Function         T1protocol_processRBlock
**
** Description      Process the last RBlock received from the slave.
**
** Parameters       originalCmdTpdu      - Original Tpdu sent.
**                  lastRespTpduReceived - Last response from the slave.
**
** Returns          -1 if the retransmission needed fails, 0 if no more
**                  retransmission were needed and 1 if extra retransmission
**                  success.
**
*******************************************************************************/
void T1protocol_processRBlock(Tpdu* originalCmdTpdu,
                              Tpdu* lastRespTpduReceived) {
  if ((originalCmdTpdu->pcb & IBLOCK_M_BIT_MASK) > 0) {
    // Last IBlock sent was chained. Expected RBlock(NS+1) for error free
    // operation and RBlock(NS) if something well bad.
    if (T1protocol_isSequenceNumberOk(originalCmdTpdu, lastRespTpduReceived) ==
        false) {
      STLOG_HAL_E("Wrong Seq number. Send again ");
      gNextCmd = I_block;
    } else {
      T1protocol_updateMasterSequenceNumber();
      gNextCmd = Idle;
    }
  } else {
    // Last IBlock sent wasn't chained. If we receive an RBlock(NS) means
    // retransmission of the original IBlock, otherwise do resend request.
    if (T1protocol_isSequenceNumberOk(originalCmdTpdu, lastRespTpduReceived) ==
        true) {
      STLOG_HAL_E("%s : Need retransmissiom :", __func__);
      if (gOriginalCmd == S_IFS_REQ) {
        gNextCmd = S_IFS_REQ;
      } else {
        gNextCmd = I_block;
      }
    } else {
      gNextCmd = S_Resync_REQ;
    }
  }
}

/*******************************************************************************
**
** Function         T1protocol_sendRBlock
**
** Description      Send a R-block to the card.
**
** Parameters       rack    - if 1, send a ack frame, nack otherwise.
**                  lastRespTpduReceived - Last response from the slave.
**
** Returns          bytesRead if data was read, 0 if timeout expired with
**                  no response, -1 otherwise
**
*******************************************************************************/
int T1protocol_sendRBlock(int rack, Tpdu* lastRespTpduReceived) {
  int result = 0;
  Tpdu* TempTpdu = (Tpdu*)malloc(sizeof(Tpdu));
  TempTpdu->data = (uint8_t*)malloc(ATP.ifsc * sizeof(uint8_t));

  result = Tpdu_formTpdu(
      NAD_HOST_TO_SLAVE,
      T1protocol_getValidPcb(RBlock, rack ? ErrorFree : OtherErrors, 0,
                             SEQ_NUM_SLAVE, 0),
      0, NULL, TempTpdu);
  if (result == -1) {
    free(TempTpdu->data);
    free(TempTpdu);
    return -1;
  }
  result = SpiLayerInterface_transcieveTpdu(TempTpdu, lastRespTpduReceived,
                                            DEFAULT_NBWT);
  if (result < 0) {
    free(TempTpdu->data);
    free(TempTpdu);
    return -1;
  }
  free(TempTpdu->data);
  free(TempTpdu);
  return result;
}
/*******************************************************************************
**
** Function         T1protocol_formSblockResponse
**
** Description      Form a SBlock response according to a given SBlock Request.
**
** Parameters       responseTpdu - A valid SBlock response according to the
**                                 SBlock request in the requestTpdu param.
**                  requestTpdu  - Sblock request received from the eSE to
**                                 process.
**
** Returns          0 If all went is ok, -1 otherwise.
**
*******************************************************************************/
int T1protocol_formSblockResponse(Tpdu* responseTpdu, Tpdu* requestTpdu) {
  uint8_t i;

  responseTpdu->nad = NAD_HOST_TO_SLAVE;
  responseTpdu->pcb = requestTpdu->pcb | 0b00100000;
  responseTpdu->len = requestTpdu->len;
  for (i = 0; i < requestTpdu->len; i++) {
    responseTpdu->data[i] = requestTpdu->data[i];
  }
  responseTpdu->checksum = 0x0000;

  if (ATP.checksumType == CRC) {
    uint8_t buffer[TPDU_PROLOGUE_LENGTH + responseTpdu->len + TPDU_CRC_LENGTH];
    Tpdu_toByteArray(responseTpdu, buffer);
    responseTpdu->checksum =
        computeCrc(buffer, (TPDU_PROLOGUE_LENGTH + responseTpdu->len));
  } else if (ATP.checksumType == LRC) {
    // char buffer[TPDU_PROLOGUE_LENGTH + responseTpdu->len + TPDU_LRC_LENGTH];
    // TODO
    STLOG_HAL_E("LRC still not implemented.");
    return -1;
  }

  return 0;
}

/*******************************************************************************
**
** Function         T1protocol_processSBlock
**
** Description      Process the last SBlock received from the slave.
**
** Parameters       originalCmdTpdu      - Original Tpdu sent.
**                  lastCmdTpduSent      - Last Tpdu sent.
**                  lastRespTpduReceived - Last response from the slave.
**
** Returns          0 If all went is ok, -1 otherwise.
**
*******************************************************************************/
int T1protocol_processSBlock(__attribute((unused)) Tpdu* originalCmdTpdu,
                             __attribute((unused)) Tpdu* lastCmdTpduSent,
                             Tpdu* lastRespTpduReceived) {
  if (lastRespTpduReceived->pcb == (uint8_t)SBLOCK_WTX_REQUEST_MASK) {
    gNextCmd = S_WTX_RES;
  } else if (lastRespTpduReceived->pcb == (uint8_t)SBLOCK_IFS_REQUEST_MASK) {
    gNextCmd = S_IFS_RES;
  } else if (lastRespTpduReceived->pcb == (uint8_t)SBLOCK_IFS_RESPONSE_MASK) {
    ATP.ifsc = (uint8_t)lastRespTpduReceived->data[0];
    gNextCmd = Idle;
    return 0;
  } else if (lastRespTpduReceived->pcb ==
             (uint8_t)SBLOCK_RESYNCH_REQUEST_MASK) {
    T1protocol_resetSequenceNumbers();
    gNextCmd = S_Resync_RES;
  } else if (lastRespTpduReceived->pcb ==
             (uint8_t)SBLOCK_RESYNCH_RESPONSE_MASK) {
    T1protocol_resetSequenceNumbers();

    STLOG_HAL_E("RESYNCH response - resend the whole frame");
    return -2;

  } else if (lastRespTpduReceived->pcb == (uint8_t)SBLOCK_ABORT_REQUEST_MASK) {
    // TODO
    STLOG_HAL_E("ABORT request received still not supported.");
    return -1;
  } else if (lastRespTpduReceived->pcb ==
             (uint8_t)SBLOCK_SWRESET_RESPONSE_MASK) {
    if (Atp_setAtp(lastRespTpduReceived->data) != 0) {
      STLOG_HAL_E("Error setting ATP");
      return -1;
    }

    T1protocol_resetSequenceNumbers();
    // SW Reset done
    return -1;
  }
  return 0;
}

/*******************************************************************************
**
** Function         T1protocol_isSequenceNumberOk
**
** Description      Check if the sequence number of the response TPDU is the
**                   expected one.
**
** Parameters       originalTpdu - Original tpdu sent.
**                  respTpdu     - The last response received from the slave.
**
** Returns          true If sequence number is ok, false otherwise.
**
*******************************************************************************/
bool T1protocol_isSequenceNumberOk(Tpdu* originalTpdu, Tpdu* respTpdu) {
  int seqNumber;

  // Get the type of the TPDU and act consequently
  TpduType tpduType = Tpdu_getType(respTpdu);

  switch (tpduType) {
    case IBlock:
      seqNumber = (respTpdu->pcb & 0b01000000) >> 6;
      if (seqNumber == SEQ_NUM_SLAVE) {
        return true;
      } else {
        return false;
      }
      break;

    case RBlock:
      // If the original Tpdu sent was chained, the expected sequence number
      // inside the RBlock i the next master sequence number.
      // If the original Tpdu sent wans't chained, no RBlock expected. If an
      // RBlock with sequence number equal to the master sequence number is
      // received, retransmission is needed, otherwise retransmission request
      // is needed.
      // TODO
      if ((originalTpdu->pcb & IBLOCK_M_BIT_MASK) > 0) {
        seqNumber = (respTpdu->pcb & 0x10) >> 4;
        if (seqNumber == ((SEQ_NUM_MASTER + 1) % 2)) {
          return true;
        } else {
          return false;
        }
      } else {
        seqNumber = (respTpdu->pcb & 0x10) >> 4;
        if (seqNumber == SEQ_NUM_MASTER) {
          return true;
        } else {
          return false;
        }
      }
      break;

    default:
      break;
  }
  return false;
}

/*******************************************************************************
**
** Function         T1protocol_updateRecoveryStatus
**
** Description      Updates the recovery state to the following step.
**
** Parameters       none
**
** Returns         void
**
*******************************************************************************/
void T1protocol_updateRecoveryStatus() {
  switch (recoveryStatus) {
    case RECOVERY_STATUS_OK:
      STLOG_HAL_E("recoveryStatus: OK -> RESEND 1");
      recoveryStatus = RECOVERY_STATUS_RESEND_1;
      break;

    case RECOVERY_STATUS_RESEND_1:
      STLOG_HAL_E("recoveryStatus: RESEND 1 -> RESYNC 1");
      recoveryStatus = RECOVERY_STATUS_RESYNC_1;
      break;

    case RECOVERY_STATUS_RESYNC_1:
      STLOG_HAL_E("recoveryStatus: RESYNC 1 -> WARM RESET");
      recoveryStatus = RECOVERY_STATUS_WARM_RESET;
      break;

    case RECOVERY_STATUS_WARM_RESET:
      STLOG_HAL_E("recoveryStatus: WARM_RESET (recovery completed)");
      recoveryStatus = RECOVERY_STATUS_KO;
      break;
  }
}

/*******************************************************************************
**
** Function         T1protocol_setRespApduData
**
** Description      Copy the data in the response Tpdu into the respApduBuffer.
**
** Parameters       respTpdu       - Response tpdu where the data is stored.
**                  respApduBuffer - Apdu buffer to store the data received
**                                    in the response Tpdu.
**
** Returns          The amount of data bytes saved into the apdu buffer.
**
*******************************************************************************/
uint8_t T1protocol_setRespApduData(Tpdu* respTpdu, uint8_t** respApduBuffer) {
  uint8_t i;
  STLOG_HAL_D("%s : Enter", __func__);

  for (i = 0; i < respTpdu->len; i++) {
    (*respApduBuffer)[i] = respTpdu->data[i];
  }

  return respTpdu->len;
}

/*******************************************************************************
**
** Function         T1protocol_doWTXResponse
**
** Description      If the eSE send a S(WTX request), acknowledge it by sending
**                  a S(WTX response)
**
** Parameters       lastRespTpduReceived - Last response received.
**
** Returns          bytesRead if data was read, 0 if timeout expired with
**                  no response, -1 otherwise
**
*******************************************************************************/
int T1protocol_doWTXResponse(Tpdu* lastRespTpduReceived) {
  Tpdu* TempTpdu = (Tpdu*)malloc(sizeof(Tpdu));
  TempTpdu->data = (uint8_t*)malloc(ATP.ifsc * sizeof(uint8_t));
  // Form a SBlock Resynch request Tpdu to sent.
  int result = Tpdu_formTpdu(NAD_HOST_TO_SLAVE, SBLOCK_WTX_RESPONSE_MASK, 0,
                             NULL, TempTpdu);
  T1protocol_formSblockResponse(TempTpdu, lastRespTpduReceived);
  if (result == -1) {
    free(TempTpdu->data);
    free(TempTpdu);
    return -1;
  }

  // Send the SBlock and read the response from the slave.
  result = SpiLayerInterface_transcieveTpdu(TempTpdu, lastRespTpduReceived,
                                            DEFAULT_NBWT);
  if (result < 0) {
    free(TempTpdu->data);
    free(TempTpdu);
    return -1;
  }
  free(TempTpdu->data);
  free(TempTpdu);
  return result;
}

/*******************************************************************************
**
** Function         T1protocol_doResendRequest
**
** Description      First thing to do in the recovery mechanism is to ask
**                  for a retransmission.
**
** Parameters       lastCmdTpduSent      - Last Tpdu sent
**                  lastRespTpduReceived - Last response received.
**                  bytesRead            - If a retransmission occurs, this
**                  field contains the amount of bytes read from the slave
**                  in the new transaction.
**
** Returns          0 if everything went fine, -1 if something failed.
**
*******************************************************************************/
int T1protocol_doResendRequest(Tpdu* lastCmdTpduSent,
                               Tpdu* lastRespTpduReceived, int* bytesRead) {
  // Form a RBlock - other errors tpdu with the expected sequence number to
  // receive.
  int result = Tpdu_formTpdu(
      NAD_HOST_TO_SLAVE,
      T1protocol_getValidPcb(RBlock, OtherErrors, 0, SEQ_NUM_SLAVE, 0), 0, NULL,
      lastCmdTpduSent);
  if (result == -1) {
    return -1;
  }

  // Send the RBlock an read the response
  result = SpiLayerInterface_transcieveTpdu(lastCmdTpduSent,
                                            lastRespTpduReceived, DEFAULT_NBWT);
  if (result < 0) {
    return -1;
  }
  *bytesRead = result;
  return 1;
}

/*******************************************************************************
**
** Function         T1protocol_doResyncRequest
**
** Description      Second thing to do in the recovery mechanism if the resend
**                  fails is to perform a Resync.
**
** Parameters       lastCmdTpduSent      - Last Tpdu sent
**                  lastRespTpduReceived - Last response received.
**                  bytesRead            - If a retransmission occurs, this
**                  field contains the amount of bytes read from the slave
**                  in the new transaction.
**
** Returns          0 if everything went fine, -1 if something failed.
**
*******************************************************************************/
int T1protocol_doResyncRequest(Tpdu* lastRespTpduReceived) {
  Tpdu* TempTpdu = (Tpdu*)malloc(sizeof(Tpdu));
  TempTpdu->data = (uint8_t*)malloc(ATP.ifsc * sizeof(uint8_t));
  // Form a SBlock Resynch request Tpdu to sent.
  int result = Tpdu_formTpdu(NAD_HOST_TO_SLAVE, SBLOCK_RESYNCH_REQUEST_MASK, 0,
                             NULL, TempTpdu);
  if (result == -1) {
    free(TempTpdu->data);
    free(TempTpdu);
    return -1;
  }

  // Send the SBlock and read the response from the slave.
  result = SpiLayerInterface_transcieveTpdu(TempTpdu, lastRespTpduReceived,
                                            DEFAULT_NBWT);
  if (result < 0) {
    free(TempTpdu->data);
    free(TempTpdu);
    return -1;
  }
  free(TempTpdu->data);
  free(TempTpdu);
  return result;
}

/*******************************************************************************
**
** Function         T1protocol_doSoftReset
**
** Description      Third thing to do in the recovery mechanism is to send
**                  a software reset to reset SPI interface.
**
** Parameters       lastRespTpduReceived - memory position whre to store the
**                  response.
**
** Returns          1 if interface reseted, -1 if something failed.
**
*******************************************************************************/
int T1protocol_doSoftReset(Tpdu* lastRespTpduReceived) {
  Tpdu* TempTpdu = (Tpdu*)malloc(sizeof(Tpdu));
  TempTpdu->data = (uint8_t*)malloc(ATP.ifsc * sizeof(uint8_t));
  // Form a SBlock Resynch request Tpdu to sent.
  int result = Tpdu_formTpdu(NAD_HOST_TO_SLAVE, SBLOCK_SWRESET_REQUEST_MASK, 0,
                             NULL, TempTpdu);
  if (result == -1) {
    free(TempTpdu->data);
    free(TempTpdu);
    return -1;
  }

  // Send the SBlock and read the response from the slave.
  result = SpiLayerInterface_transcieveTpdu(TempTpdu, lastRespTpduReceived,
                                            DEFAULT_NBWT);
  if (result < 0) {
    free(TempTpdu->data);
    free(TempTpdu);
    return -1;
  }
  free(TempTpdu->data);
  free(TempTpdu);
  return result;
}

/*******************************************************************************
**
** Function         T1protocol_doRecovery
**
** Description      Implements the recovery mechanism when a non-consistent
**                  TPDU has been received or no response has been received
**                  before the timeout.
**
** Parameters       lastCmdTpduSent      - Last Tpdu sent
**                  lastRespTpduReceived - Last response received.
**                  bytesRead            - If a retransmission occurs, this
**                  field contains the amount of bytes read from the slave
**                  in the new transaction.
**
** Returns          0 if everything went fine, -1 if something failed.
**
*******************************************************************************/
int T1protocol_doRecovery() {
  STLOG_HAL_W("Entering recovery");

  // Update the recovery status
  T1protocol_updateRecoveryStatus();

  // Do the resend request or the resynck request according to the recovery
  // status
  switch (recoveryStatus) {
    case RECOVERY_STATUS_RESEND_1:
    case RECOVERY_STATUS_RESEND_2:
      gNextCmd = R_Other_Error;
      break;
    case RECOVERY_STATUS_RESYNC_1:
    case RECOVERY_STATUS_RESYNC_2:
    case RECOVERY_STATUS_RESYNC_3:
      gNextCmd = S_Resync_REQ;
      break;
    case RECOVERY_STATUS_WARM_RESET:

      // At this point, we consider that SE is dead and a reboot is requried
      gNextCmd = S_SWReset_REQ;
      break;
    case RECOVERY_STATUS_KO:
    default:
      return -1;
      break;
  }

  return 0;
}

/*******************************************************************************
**
** Function         T1protocol_handleTpduResponse
**
** Description      Handles any TPDU response iteratively.
**
** Parameters       originalCmdTpdu      - Original Tpdu sent.
**                  lastCmdTpduSent      - Last Tpdu sent
**                  lastRespTpduReceived - Last response received.
**                  bytesRead            - If a retransmission occurs, this
**                  field contains the amount of bytes read from the slave
**                  in the new transaction.
**
** Returns          0 if everything went fine, -1 if something failed.
**
*******************************************************************************/
int T1protocol_handleTpduResponse(Tpdu* originalCmdTpdu, Tpdu* lastCmdTpduSent,
                                  Tpdu* lastRespTpduReceived, int* bytesRead) {
  int rc = 0;
  STLOG_HAL_D("%s : Enter :", __func__);

  // If the last transmission ends without response from the slave, do
  // recovery mechanism.
  if (*bytesRead == 0) {
    STLOG_HAL_E("bytesRead = 0 -> Going into recovery.");
    rc = T1protocol_doRecovery();
    return rc;
  }

  // Check the consistency of the last received tpdu
  rc = T1protocol_checkTpduConsistency(lastCmdTpduSent, lastRespTpduReceived);
  if (rc < 0) {
    STLOG_HAL_E("%s : TPDU consistency check failed -> Going into recovery.",
                __func__);
    rc = T1protocol_doRecovery();
    return rc;
  }

  // Reset the recovery if a valid Tpdu has been received from the slave
  if (recoveryStatus != RECOVERY_STATUS_OK) {
    recoveryStatus = RECOVERY_STATUS_OK;
  }

  // If all went OK, process the last tpdu received
  TpduType type = Tpdu_getType(lastRespTpduReceived);
  switch (type) {
    case IBlock:
      rc = T1protocol_processIBlock(originalCmdTpdu, lastRespTpduReceived);
      break;

    case RBlock:
      T1protocol_processRBlock(originalCmdTpdu, lastRespTpduReceived);
      break;

    case SBlock:
      rc = T1protocol_processSBlock(originalCmdTpdu, lastCmdTpduSent,
                                    lastRespTpduReceived);
      break;
  }

  return rc;
}

/*******************************************************************************
**
** Function         T1protocol_formCommandTpduToSend
**
** Description      Form a valid Tpdu to send according to the if we need to
**                  send an IBlock or a RBlock.
**
** Parameters       cmdApduPart - Data to sent within an IBlock.
**                  cmdLength   - Amount of data to sent.
**                  isLast      - Flag if there are more data to send.
**                  cmdTpdu     - Resulting Tpdu.
**
** Returns          0 if everything went fine, -1 if something failed.
**
*******************************************************************************/
int T1protocol_formCommandTpduToSend(uint8_t* cmdApduPart, uint8_t cmdLength,
                                     bool isLast, Tpdu* cmdTpdu) {
  STLOG_HAL_D("%s : Enter ", __func__);
  if (cmdLength == 0) {
    // Send RBlock to get the pending IBlock responses from the slave
    if (Tpdu_formTpdu(
            NAD_HOST_TO_SLAVE,
            T1protocol_getValidPcb(RBlock, ErrorFree, 0, SEQ_NUM_SLAVE, isLast),
            0, cmdApduPart, cmdTpdu) == -1) {
      STLOG_HAL_E("Error forming an RBlock to send.");
      return -1;
    }
  } else {
    // Send IBlock containing the data in cmdApduPart. Set it as chained if
    // isLast is false.
    if (Tpdu_formTpdu(NAD_HOST_TO_SLAVE,
                      T1protocol_getValidPcb(IBlock, ErrorFree, SEQ_NUM_MASTER,
                                             0, isLast),
                      cmdLength, cmdApduPart, cmdTpdu) == -1) {
      STLOG_HAL_E("Error forming an IBlock to send.");
      return -1;
    }
  }
  return 0;
}

/*******************************************************************************
**
** Function         T1protocol_doRequestIFS
**
** Description      Send a IFS request to negotiate the IFSD value. Use the same
**                  value for IFSD than the IFSC received in the ATP.
**
** Parameters      None
**
** Returns         0 if everything went fine, -1 if something failed.
**
*******************************************************************************/
int T1protocol_doRequestIFS(Tpdu* lastRespTpduReceived) {
  Tpdu* TempTpdu = (Tpdu*)malloc(sizeof(Tpdu));
  TempTpdu->data = (uint8_t*)malloc(ATP.ifsc * sizeof(uint8_t));

  STLOG_HAL_D("%s : Enter ", __func__);

  // Form a SBlock IFS request Tpdu to sent.
  int result = Tpdu_formTpdu(NAD_HOST_TO_SLAVE, SBLOCK_IFS_REQUEST_MASK, 1,
                             &ATP.ifsc, TempTpdu);
  if (result == -1) {
    free(TempTpdu->data);
    free(TempTpdu);
    return -1;
  }

  // Send the SBlock and read the response from the secondary (eSE).
  result = SpiLayerInterface_transcieveTpdu(TempTpdu, lastRespTpduReceived,
                                            DEFAULT_NBWT);

  if (result < 0) {
    free(TempTpdu->data);
    free(TempTpdu);
    return -1;
  }

  free(TempTpdu->data);
  free(TempTpdu);
  return result;
}

/*******************************************************************************
**
** Function         T1protocol_init
**
** Description      Initializes the T1 Protocol.
**
** Parameters       tSpiDriver - hardware information
**
** Returns          0 if everything went fine, -1 if something failed.
**
*******************************************************************************/
int T1protocol_init(SpiDriver_config_t* tSpiDriver) {
  STLOG_HAL_D("%s : Enter ", __func__);
  if (SpiLayerInterface_init(tSpiDriver) != 0) {
    return -1;
  }

  return 0;
}

/*******************************************************************************
**
** Function         T1protocol_transcieveApduPart
**
** Description      Send and/or receive an APDU part.
**
** Parameters       cmdApduPart  - cmdApdu part that shall be sent
**                  cmdLength    - Length of the cmdApduPart to be sent.
**                  isLast       - APDU_PART_IS_NOT_LAST/APDU_PART_IS_LAST
**                  pRsp         - Structure to the response buffer and length.
**
** Returns          0 if everything went fine, -1 if something failed.
**
*******************************************************************************/
int T1protocol_transcieveApduPart(uint8_t* cmdApduPart, uint8_t cmdLength,
                                  bool isLast, StEse_data* pRsp,
                                  T1TProtocol_TransceiveState Block_type) {
  Tpdu originalCmdTpdu, lastCmdTpduSent, lastRespTpduReceived;
  originalCmdTpdu.data = (uint8_t*)malloc(ATP.ifsc * sizeof(uint8_t));
  lastCmdTpduSent.data = (uint8_t*)malloc(ATP.ifsc * sizeof(uint8_t));
  lastRespTpduReceived.data = (uint8_t*)malloc(ATP.ifsc * sizeof(uint8_t));
  StEse_data pRes;

  memset(&pRes, 0x00, sizeof(StEse_data));
  STLOG_HAL_E("%s : Enter cmdLength = 0x%02X", __func__, cmdLength);

  // Form the cmdTpdu according to the cmdApduPart, cmdLength and isLast
  // fields.
  if (T1protocol_formCommandTpduToSend(cmdApduPart, cmdLength, isLast,
                                       &originalCmdTpdu) < 0) {
    return -1;
  }

  // Send the command Tpdu and receive the response.
  int rc;
  recoveryStatus = RECOVERY_STATUS_OK;
  Tpdu_copy(&lastCmdTpduSent, &originalCmdTpdu);

  gOriginalCmd = Block_type;
  gNextCmd = Block_type;
  while (gNextCmd != 0) {
    switch (gNextCmd) {
      case I_block:
        rc = SpiLayerInterface_transcieveTpdu(
            &originalCmdTpdu, &lastRespTpduReceived, DEFAULT_NBWT);
        if (rc < 0) {
          return rc;
        }
        break;

      case R_ACK:
        rc = T1protocol_sendRBlock(true, &lastRespTpduReceived);
        if (rc < 0) {
          return rc;
        }
        break;
      case R_Other_Error:
        rc = T1protocol_sendRBlock(false, &lastRespTpduReceived);
        if (rc < 0) {
          return rc;
        }
        break;

      case S_Resync_REQ:
        rc = T1protocol_doResyncRequest(&lastRespTpduReceived);
        if (rc < 0) {
          return rc;
        }
        break;

      case S_SWReset_REQ:
        rc = T1protocol_doSoftReset(&lastRespTpduReceived);
        if (rc < 0) {
          return rc;
        }
        break;

      case S_WTX_RES:
        rc = T1protocol_doWTXResponse(&lastRespTpduReceived);
        if (rc < 0) {
          return rc;
        }
        break;

      case S_IFS_REQ:
        rc = T1protocol_doRequestIFS(&lastRespTpduReceived);
        if (rc < 0) {
          return rc;
        }
        break;

      default:
        return -1;
        break;
    }

    rc = T1protocol_handleTpduResponse(&originalCmdTpdu, &lastCmdTpduSent,
                                       &lastRespTpduReceived, &rc);

    if (rc < 0) {
      return rc;
    }
  }
  TpduType type = Tpdu_getType(&lastRespTpduReceived);

  if ((type == IBlock) && (DataMgmt_GetData(&pRes.len, &pRes.p_data) != 0)) {
    return -1;
  }

  pRsp->len = pRes.len;
  pRsp->p_data = pRes.p_data;

  free(originalCmdTpdu.data);
  originalCmdTpdu.data = NULL;
  free(lastCmdTpduSent.data);
  lastCmdTpduSent.data = NULL;
  free(lastRespTpduReceived.data);
  lastRespTpduReceived.data = NULL;

  if ((lastRespTpduReceived.pcb & IBLOCK_M_BIT_MASK) > 0) {
    return 1;
  }

  return 0;
}
