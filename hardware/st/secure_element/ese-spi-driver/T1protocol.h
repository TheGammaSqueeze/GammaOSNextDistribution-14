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
#ifndef _T1PROTOCOL_H_
#define _T1PROTOCOL_H_

#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include "SpiLayerInterface.h"
#include "StEseApi.h"
#include "utils-lib/Tpdu.h"

// Recovery states
#define RECOVERY_STATUS_OK 0
#define RECOVERY_STATUS_RESEND_1 1
#define RECOVERY_STATUS_RESEND_2 2
#define RECOVERY_STATUS_RESYNC_1 3
#define RECOVERY_STATUS_RESYNC_2 4
#define RECOVERY_STATUS_RESYNC_3 5
#define RECOVERY_STATUS_WARM_RESET 6
#define RECOVERY_STATUS_KO 7

// Apdu modes
#define APDU_PART_IS_LAST true
#define APDU_PART_IS_NOT_LAST false

// SBlock types
#define SBLOCK_WTX_REQUEST_MASK 0b11000011
#define SBLOCK_WTX_RESPONSE_MASK 0b11100011
#define SBLOCK_IFS_REQUEST_MASK 0b11000001
#define SBLOCK_IFS_RESPONSE_MASK 0b11100001
#define SBLOCK_ABORT_REQUEST_MASK 0b11000010
#define SBLOCK_ABORT_RESPONSE_MASK 0b11100010
#define SBLOCK_RESYNCH_REQUEST_MASK 0b11000000
#define SBLOCK_RESYNCH_RESPONSE_MASK 0b11100000
#define SBLOCK_SWRESET_REQUEST_MASK 0b11001111
#define SBLOCK_SWRESET_RESPONSE_MASK 0b11101111

// IBlock parameters
#define IBLOCK_M_BIT_MASK 0b00100000
#define IBLOCK_NS_BIT_MASK 0b01000000

#define DEFAULT_NBWT 1

// Global variables
// uint8_t SEQ_NUM_MASTER;
// uint8_t SEQ_NUM_SLAVE;
// bool firstTransmission;
// uint8_t recoveryStatus;
// bool aborted;
// uint8_t IFSD;

typedef enum {
  Idle = 0,
  I_block,
  R_ACK,
  R_CRC_Error,
  R_Other_Error,
  S_Resync_REQ,
  S_Resync_RES,
  S_IFS_REQ,
  S_IFS_RES,
  S_WTX_RES,
  S_SWReset_REQ
} T1TProtocol_TransceiveState;
/**
 * Form a valid pcb according to the Tpdu type, subtype, master sequence number,
 * slave sequence number and isLast.
 *
 * @param type The Tpdu type (IBlock, RBlock or SBlock).
 * @param subtype The different subtype options when using RBlock. Error free,
 * checksum error or other errors.
 * @param numSeqMaster Master sequence number.
 * @param numseqSlave Slave sequence number.
 * @param isLast Indicate if there are more tpdus to send within the same APDU.
 *
 * @return pcb The value of the pcb computed.
 */
uint8_t T1protocol_getValidPcb(TpduType type, RBlockType subtype,
                               uint8_t numSeqMaster, uint8_t numseqSlave,
                               bool isLast);

/**
 * Check if the checksum of a given tpdu is well formed.
 *
 * @param tpdu Tpdu to check.
 *
 * @return 0 If checksum is ok, -1 otherwise.
 */
int T1protocol_checkResponseTpduChecksum(Tpdu *tpdu);

/**
 * Check if the pcb of a given tpdu is valid.
 *
 * @param tpdu Tpdu to check.
 *
 * @return 0 If checksum is ok, -1 otherwise.
 */
int T1protocol_checkResponsePcbConsistency(Tpdu *tpdu);

/**
 * Check if the length field of a given tpdu is valid.
 *
 * @param tpdu Tpdu to check.
 *
 * @return 0 If checksum is ok, -1 otherwise.
 */
int T1protocol_checkResponseLenConsistency(Tpdu *tpdu);

/**
 * Check if the sequence number of a given tpdu is valid.
 *
 * @param tpdu Tpdu to check.
 *
 * @return 0 If checksum is ok, -1 otherwise.
 */
int T1protocol_checkResponseSeqNumberConsistency(Tpdu *tpdu);

/**
 * Check if an SBlock response was received after having transmitted a SBlock
 * request.
 *
 * @param lastCmdTpduSent Last Tpdu sent.
 * @param lastRespTpduReceived Response received.
 *
 * @return 0 If checksum is ok, -1 otherwise.
 */
int T1protocol_checkSBlockResponseConsistency(Tpdu *lastCmdTpduSent,
                                              Tpdu *lastRespTpduReceived);

/**
 * Check if the response TPDU is consistent (check the checksum, check if the
 * pcb is valid and the expected one and check the len consistency).
 *
 * @param lastCmdTpduSent Last Tpdu sent, could be different than the
 * originalCmdTpdu if there was retransmissions request or SBlocks.
 * @param lastRespTpduReceived Last response received from the slave.
 *
 * @return 0 If consistency is ok, -1 otherwise.
 */
int T1protocol_checkTpduConsistency(Tpdu *lastCmdTpduSent,
                                    Tpdu *lastRespTpduReceived);

/**
 * Set the sequence numbers to it's initial values.
 */
void T1protocol_resetSequenceNumbers();

/**
 * Update the master sequence number. Increase the value, taking into account is
 * module 2 type.
 */
void T1protocol_updateMasterSequenceNumber();

/**
 * Update the slave sequence number. Increase the value, taking into account is
 * module 2 type.
 */
void T1protocol_updateSlaveSequenceNumber();

/**
 * Process the last IBlock received from the slave.
 *
 * @param originalCmdTpdu Original Tpdu sent.
 * @param lastRespTpduReceived Last response received from the slave.
 *
 * @return 0 If all went is ok, -1 otherwise.
 */
int T1protocol_processIBlock(Tpdu* originalCmdTpdu, Tpdu* lastRespTpduReceived);

/**
 * Process the last RBlock received from the slave.
 *
 * @param originalCmdTpdu Original Tpdu sent.
 * @param lastRespTpduReceived Last response received from the slave.
 *
 */
void T1protocol_processRBlock(Tpdu *originalCmdTpdu,
                              Tpdu *lastRespTpduReceived);

/**
 * Process the last RBlock received from the slave.
 *
 * @param rack if 1, send a ack frame, nack otherwise.
 * @param lastRespTpduReceived Last response received from the slave.
 *
 * @return -1 if the retransmission needed fails, 0 if no more retransmission
 * were needed and 1 if extra retransmission success.
 */
int T1protocol_sendRBlock(int rack, Tpdu *lastRespTpduReceived);

/**
 * Form a SBlock response according to a given SBlock Request.
 *
 * @param responseTpdu A valid SBlock response according to the SBlock request
 * in the requestTpdu param.
 * @param requestTpdu The Sblock request received from the eSE to process.
 *
 * @return 0 If all went is ok, -1 otherwise.
 */
int T1protocol_formSblockResponse(Tpdu *responseTpdu, Tpdu *requestTpdu);

/**
 * Process the last SBlock received from the slave.
 *
 * @param originalCmdTpdu Original Tpdu sent.
 * @param lastCmdTpduSent Last Tpdu sent, could be different than the
 * originalCmdTpdu if there was retransmissions request or SBlocks.
 * @param lastRespTpduReceived Last response received from the slave.
 *
 * @return -1 if the extra retransmission needed fails or  1 if extra
 * retransmission success.
 */
int T1protocol_processSBlock(Tpdu *originalCmdTpdu, Tpdu *lastCmdTpduSent,
                             Tpdu *lastRespTpduReceived);

/**
 * Check if the sequence number of the response TPDU is the expected one.
 *
 * @param originalTpdu The original tpdu sent.
 * @param respTpdu The last response received from the slave.
 *
 * @return true If sequence number is ok, false otherwise.
 */
bool T1protocol_isSequenceNumberOk(Tpdu *originalTpdu, Tpdu *respTpdu);

/**
 * Updates the recovery state to the following step.
 */
void T1protocol_updateRecoveryStatus();

/**
 * Copy the data in the response Tpdu into the respApduBuffer.
 *
 * @param respTpdu Response tpdu where the data is stored.
 * @param respApduBuffer Apdu buffer where to store the data received in the
 * response Tpdu.
 *
 * @return The amount of data bytes saved into the apdu buffer.
 */
uint8_t T1protocol_setRespApduData(Tpdu *respTpdu, uint8_t *respApduBuffer);

/**
 * If the eSE send a S(WTX request), acknowledge it by sending
 * a S(WTX response)
 *
 * @param lastRespTpduReceived Last response received from the slave.
 *
 * @return bytesRead if data was read, 0 if timeout expired with
 *         no response, -1 otherwise
 */
int T1protocol_doWTXResponse(Tpdu *lastRespTpduReceived);

/**
 * The first thing to do in the recovery mechanism is to ask for a
 * retransmission.
 *
 * @param lastCmdTpduSent Last Tpdu sent, could be different than the
 * originalCmdTpdu if there was retransmissions request or SBlocks.
 * @param lastRespTpduReceived Last response received from the slave.
 * @param bytesRead If a retransmission occurs, this field contains the amout of
 * bytes read from the slave in the new transaction.
 *
 * @return 0 if everything went fine, -1 if something failed.
 */
int T1protocol_doResendRequest(Tpdu *lastCmdTpduSent,
                               Tpdu *lastRespTpduReceived, int *bytesRead);

/**
 * The second thing to do in the recovery mechanism if the resend fails
 * is to perform a Resync.
 *
 * @param lastCmdTpduSent Last Tpdu sent, could be different than the
 * originalCmdTpdu if there was retransmissions request or SBlocks.
 * @param lastRespTpduReceived Last response received from the slave.
 * @param bytesRead If a retransmission occurs, this field contains the amout of
 * bytes read from the slave in the new transaction.
 *
 * @return 0 if everything went fine, -1 if something failed.
 */
int T1protocol_doResyncRequest(Tpdu *lastRespTpduReceived);

/**
 * Implements the recovery mechanism when a non-consistent TPDU has been
 * received or no response has been received before the timeout.
 *
 * @param lastCmdTpduSent Last Tpdu sent, could be different than the
 * originalCmdTpdu if there was retransmissions request or SBlocks.
 * @param lastRespTpduReceived Last response received from the slave.
 * @param bytesRead If a retransmission occurs, this field contains the amout of
 * bytes read from the slave in the new transaction.
 *
 * @return 0 if everything went fine,
 *        -1 if something failed, or
 *         RC_WARM_RESET_REQUIERD if a warm reset needs to be performed.
 *       TODO: If a warm reset needs to be performed, the service will die
 *       and the user will need do the reset manually, as Power Manager is not
 *       yet implemented.
 */
int T1protocol_doRecovery();

/**
 * Send a soft reset (S-Block)
 *
 * @param lastRespTpduReceived Last response received from the slave.
 *
 * @return 0 if everything went fine, -1 if an error occurred.
 */
int T1protocol_doSoftReset(Tpdu *lastRespTpduReceived);

/**
 * Send IFS request(S-Block)
 *
 * @param  None.
 *
 * @return 0 if everything went fine, -1 if an error occurred.
 */
int T1protocol_doRequestIFS(Tpdu* lastRespTpduReceived);

/**
 * Handles any TPDU response iteratively.
 *
 * @param originalCmdTpdu Original Tpdu sent.
 * @param lastCmdTpduSent Last Tpdu sent, could be different than the
 * originalCmdTpdu if there was retransmissions request or SBlocks.
 * @param lastRespTpduReceived Last response received from the slave.
 * @param bytesRead If a retransmission occurs, this field contains the amout of
 * bytes read from the slave in the new transaction.
 *
 * @return 0 if everything went fine, -1 if an error occurred.
 */
int T1protocol_handleTpduResponse(Tpdu *originalCmdTpdu, Tpdu *lastCmdTpduSent,
                                  Tpdu *lastRespTpduReceived, int *bytesRead);

/**
 * Form a valid Tpdu to send according to the if we need to send
 * an IBlock or a RBlock (for chained response from the slave).
 *
 * @param cmdApduPart Data to sent within an IBlock.
 * @param cmdLength Amount of data to sent.
 * @param isLast Flag indicating if there are more data to send.
 * @param cmdTpdu Resulting Tpdu.
 *
 * @return 0 if everything went fine, -1 if something failed.
 */
int T1protocol_formCommandTpduToSend(uint8_t *cmdApduPart, uint8_t cmdLength,
                                     bool isLast, Tpdu *cmdTpdu);

/**
 * Initializes the T1 Protocol.
 *
 * @return 0 if initialization was ok, -1 otherwise.
 */
int T1protocol_init(SpiDriver_config_t *tSpiDriver);

/**
 * This method is used to send and/or receive an APDU part. There are 3 ways of
 * calling it:
 * 1. transcieveApduPart(&cmdApduPart, cmdLength, APDU_PART_IS_NOT_LAST, null,
 *      null): will send an APDU part and expect no response.
 * 2. transcieveApduPart(&cmdApduPart, cmdLength, APDU_PART_IS_LAST,
 *      &respApduPart, &respLength): will send an APDU part and get a response
 *      part.
 * 3. transcieveApduPart(null, null, null, &respApduPart, &respLength): sends no
 *      APDU part and gets a response part.
 *
 * It will for and send the required TPDU, receive the response and handle it.
 *
 * @param cmdApduPart The cmdApdu part that shall be sent (or null).
 * @param cmdLength The length of the cmdApduPart to be sent (or null).
 * @param isLast Either APDU_PART_IS_NOT_LAST or APDU_PART_IS_LAST (or null).
 * @param pRsp Structure to the response buffer and length.
 *      (or null).
 *
 * @return If no response is expected:
 *          - 0 if everything was ok
 *          - -1 if an error occurred
 *         If response is expected:
 *          - 0 if it is the last response part
 *          - 1 if there are more response parts
 *          - -1 if an error occurred.
 */
int T1protocol_transcieveApduPart(uint8_t* cmdApduPart, uint8_t cmdLength,
                                  bool isLast, StEse_data* pRsp,
                                  T1TProtocol_TransceiveState Block_type);

#endif /* _T1PROTOCOL_H_ */
