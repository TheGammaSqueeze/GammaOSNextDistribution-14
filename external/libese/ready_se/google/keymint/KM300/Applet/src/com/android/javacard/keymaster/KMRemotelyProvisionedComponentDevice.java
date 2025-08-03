/*
 * Copyright(C) 2021 The Android Open Source Project
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
 */
package com.android.javacard.keymaster;

import com.android.javacard.seprovider.KMException;
import com.android.javacard.seprovider.KMKey;
import com.android.javacard.seprovider.KMOperation;
import com.android.javacard.seprovider.KMSEProvider;
import javacard.framework.APDU;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * This class handles remote key provisioning. Generates an RKP key and generates a certificate
 * signing request(CSR). The generation of CSR is divided among multiple functions to the save the
 * memory inside the Applet. The set of functions to be called sequentially in the order to complete
 * the process of generating the CSR are processBeginSendData, processUpdateKey,
 * processUpdateEekChain, processUpdateChallenge, processFinishSendData, and getResponse.
 * ProcessUpdateKey is called Ntimes, where N is the number of keys. Similarly, getResponse is
 * called multiple times till the client receives the response completely.
 */
public class KMRemotelyProvisionedComponentDevice {

  // Below are the device info labels
  // The string "brand" in hex
  public static final byte[] BRAND = {0x62, 0x72, 0x61, 0x6E, 0x64};
  // The string "manufacturer" in hex
  public static final byte[] MANUFACTURER = {
    0x6D, 0x61, 0x6E, 0x75, 0x66, 0x61, 0x63, 0x74, 0x75, 0x72, 0x65, 0x72
  };
  // The string "product" in hex
  public static final byte[] PRODUCT = {0x70, 0x72, 0x6F, 0x64, 0x75, 0x63, 0x74};
  // The string "model" in hex
  public static final byte[] MODEL = {0x6D, 0x6F, 0x64, 0x65, 0x6C};
  // // The string "device" in hex
  public static final byte[] DEVICE = {0x64, 0x65, 0x76, 0x69, 0x63, 0x65};
  // The string "vb_state" in hex
  public static final byte[] VB_STATE = {0x76, 0x62, 0x5F, 0x73, 0x74, 0x61, 0x74, 0x65};
  // The string "bootloader_state" in hex.
  public static final byte[] BOOTLOADER_STATE = {
    0x62, 0x6F, 0x6F, 0x74, 0x6C, 0x6F, 0x61, 0x64, 0x65, 0x72, 0x5F, 0x73, 0x74, 0x61, 0x74, 0x65
  };
  // The string "vb_meta_digest" in hdex.
  public static final byte[] VB_META_DIGEST = {
    0X76, 0X62, 0X6D, 0X65, 0X74, 0X61, 0X5F, 0X64, 0X69, 0X67, 0X65, 0X73, 0X74
  };
  // The string "os_version" in hex.
  public static final byte[] OS_VERSION = {
    0x6F, 0x73, 0x5F, 0x76, 0x65, 0x72, 0x73, 0x69, 0x6F, 0x6E
  };
  // The string "system_patch_level" in hex.
  public static final byte[] SYSTEM_PATCH_LEVEL = {
    0x73, 0x79, 0x73, 0x74, 0x65, 0x6D, 0x5F, 0x70, 0x61, 0x74, 0x63, 0x68, 0x5F, 0x6C, 0x65, 0x76,
    0x65, 0x6C
  };
  // The string "boot_patch_level" in hex.
  public static final byte[] BOOT_PATCH_LEVEL = {
    0x62, 0x6F, 0x6F, 0x74, 0x5F, 0x70, 0x61, 0x74, 0x63, 0x68, 0x5F, 0x6C, 0x65, 0x76, 0x65, 0x6C
  };
  // The string "vendor_patch_level"  in hex.
  public static final byte[] VENDOR_PATCH_LEVEL = {
    0x76, 0x65, 0x6E, 0x64, 0x6F, 0x72, 0x5F, 0x70, 0x61, 0x74, 0x63, 0x68, 0x5F, 0x6C, 0x65, 0x76,
    0x65, 0x6C
  };
  // The string "version" in hex.
  public static final byte[] DEVICE_INFO_VERSION = {0x76, 0x65, 0x72, 0x73, 0x69, 0x6F, 0x6E};
  // The string "security_level" in hex.
  public static final byte[] SECURITY_LEVEL = {
    0x73, 0x65, 0x63, 0x75, 0x72, 0x69, 0x74, 0x79, 0x5F, 0x6C, 0x65, 0x76, 0x65, 0x6C
  };
  // The string "fused" in hex.
  public static final byte[] FUSED = {0x66, 0x75, 0x73, 0x65, 0x64};
  // The string "cert_type" in hex
  public static final byte[] CERT_TYPE = {0x63, 0x65, 0x72, 0x74, 0x5F, 0x74, 0x79, 0x70, 0x65};
  // Below are the Verified boot state values
  // The string "green" in hex.
  public static final byte[] VB_STATE_GREEN = {0x67, 0x72, 0x65, 0x65, 0x6E};
  // The string "yellow" in hex.
  public static final byte[] VB_STATE_YELLOW = {0x79, 0x65, 0x6C, 0x6C, 0x6F, 0x77};
  // The string "orange" in hex.
  public static final byte[] VB_STATE_ORANGE = {0x6F, 0x72, 0x61, 0x6E, 0x67, 0x65};
  // The string "red" in hex.
  public static final byte[] VB_STATE_RED = {0x72, 0x65, 0x64};
  // Below are the boot loader state values
  // The string "unlocked" in hex.
  public static final byte[] UNLOCKED = {0x75, 0x6E, 0x6C, 0x6F, 0x63, 0x6B, 0x65, 0x64};
  // The string "locked" in hex.
  public static final byte[] LOCKED = {0x6C, 0x6F, 0x63, 0x6B, 0x65, 0x64};
  // Device info CDDL schema version
  public static final byte DI_SCHEMA_VERSION = 2;
  // The string "strongbox" in hex.
  public static final byte[] DI_SECURITY_LEVEL = {
    0x73, 0x74, 0x72, 0x6F, 0x6E, 0x67, 0x62, 0x6F, 0x78
  };
  // The string "keymint" in hex.
  public static final byte[] DI_CERT_TYPE = {0x6B, 0x65, 0x79, 0x6D, 0x69, 0x6E, 0x74};
  // Represents each element size inside the data buffer. Each element has two entries
  // 1) Length of the element and 2) offset of the element in the data buffer.
  public static final byte DATA_INDEX_ENTRY_SIZE = 4;
  // It is the offset, which represents the position where the element is present
  // in the data buffer.
  public static final byte DATA_INDEX_ENTRY_OFFSET = 2;
  // Flag to denote TRUE
  private static final byte TRUE = 0x01;
  // Flag to denote FALSE
  private static final byte FALSE = 0x00;
  // RKP hardware info Version
  private static final byte RKP_VERSION = 0x03;
  // RKP supportedNumKeysInCsr
  private static final byte MIN_SUPPORTED_NUM_KEYS_IN_CSR = 20;
  // The CsrPayload CDDL Schema version.
  private static final byte CSR_PAYLOAD_CDDL_SCHEMA_VERSION = 3;
  // Boot params
  // Below constants used to denote the type of the boot parameters. Note that these
  // constants are only defined to be used internally.
  private static final byte OS_VERSION_ID = 0x00;
  private static final byte SYSTEM_PATCH_LEVEL_ID = 0x01;
  private static final byte BOOT_PATCH_LEVEL_ID = 0x02;
  private static final byte VENDOR_PATCH_LEVEL_ID = 0x03;
  // Configurable flag to denote if UDS certificate chain is supported in the
  // RKP server.
  private static final boolean IS_UDS_SUPPORTED_IN_RKP_SERVER = true;
  // Denotes COSE Integer lengths less than or equal to 23.
  private static final byte TINY_PAYLOAD = 0x17;
  // Denotes COSE Integer with short lengths.
  private static final short SHORT_PAYLOAD = 0x100;
  // The maximum possible output buffer.
  private static final short MAX_SEND_DATA = 512;
  // The string "Google Strongbox KeyMint 3" in hex.
  private static final byte[] uniqueId = {
    0x47, 0x6f, 0x6f, 0x67, 0x6c, 0x65, 0x20, 0x53, 0x74, 0x72, 0x6f, 0x6e, 0x67, 0x62, 0x6f, 0x78,
    0x20, 0x4b, 0x65, 0x79, 0x4d, 0x69, 0x6e, 0x74, 0x20, 0x33
  };
  // Flag to denote more response is available to the clients.
  private static final byte MORE_DATA = 0x01;
  // Flag to denote no response is available to the clients.
  private static final byte NO_DATA = 0x00;
  // Below are the response processing states.
  private static final byte START_PROCESSING = 0x00;
  private static final byte PROCESSING_DICE_CERTS_IN_PROGRESS = 0x02;
  private static final byte PROCESSING_DICE_CERTS_COMPLETE = 0x04;
  private static final byte PROCESSING_UDS_CERTS_IN_PROGRESS = 0x08;
  private static final byte PROCESSING_UDS_CERTS_COMPLETE = 0x0A;
  // The data table size.
  private static final short DATA_SIZE = 512;
  // Number of entries in the data table.
  private static final byte DATA_INDEX_SIZE = 6;
  // Below are the data table offsets.
  private static final byte TOTAL_KEYS_TO_SIGN = 0;
  private static final byte KEYS_TO_SIGN_COUNT = 1;
  private static final byte GENERATE_CSR_PHASE = 2;
  private static final byte RESPONSE_PROCESSING_STATE = 3;
  private static final byte UDS_PROCESSED_LENGTH = 4;
  private static final byte DICE_PROCESSED_LENGTH = 5;

  // Below are some of the sizes defined in the data table.
  // The size of the Ephemeral Mac key used to sign the rkp public key.
  private static final byte EPHEMERAL_MAC_KEY_SIZE = 32;
  // The size of short types.
  private static final byte SHORT_SIZE = 2;
  // The size of byte types
  private static final byte BYTE_SIZE = 1;
  // Below are the different processing stages for generateCSR.
  // BEGIN -        It is the initial stage where the process is initialized and construction of
  //                MacedPublickeys are initiated.
  // UPDATE -       Challenge, EEK and RKP keys are sent to the applet for further process.
  // FINISH -       MacedPublicKeys are constructed and construction of protected data is initiated.
  // GET_UDS_CERTS_RESPONSE - Constructed the UDSCerts in the response.
  private static final byte BEGIN = 0x01;
  private static final byte UPDATE = 0x02;
  private static final byte FINISH = 0x04;
  private static final byte GET_UDS_CERTS_RESPONSE = 0x06;

  // RKP mac key size
  private static final byte RKP_MAC_KEY_SIZE = 32;
  // RKP CDDL Schema version
  private static final byte RKP_AUTHENTICATE_CDDL_SCHEMA_VERSION = 1;
  // The maximum size of the encoded buffer size.
  private static final short MAX_ENCODED_BUF_SIZE = 1024;
  // Used to hold the temporary results.
  public short[] rkpTmpVariables;
  // Data table to hold the entries at the initial stages of generateCSR and which are used
  // at later stages to construct the response data.
  private byte[] data;
  // Instance of the CBOR encoder.
  private KMEncoder encoder;
  // Instance of the CBOR decoder.
  private KMDecoder decoder;
  // Instance of the KMRepository for memory management.
  private KMRepository repository;
  // Instance of the provider for cyrpto operations.
  private KMSEProvider seProvider;
  // Instance of the KMKeymintDataStore to save or retrieve the data.
  private KMKeymintDataStore storeDataInst;
  // Holds the KMOperation instance. This is used to do multi part update operations.
  private Object[] operation;
  // Holds the current index in the data table.
  private short[] dataIndex;

  public KMRemotelyProvisionedComponentDevice(
      KMEncoder encoder,
      KMDecoder decoder,
      KMRepository repository,
      KMSEProvider seProvider,
      KMKeymintDataStore storeDInst) {
    this.encoder = encoder;
    this.decoder = decoder;
    this.repository = repository;
    this.seProvider = seProvider;
    this.storeDataInst = storeDInst;
    rkpTmpVariables = JCSystem.makeTransientShortArray((short) 32, JCSystem.CLEAR_ON_RESET);
    data = JCSystem.makeTransientByteArray(DATA_SIZE, JCSystem.CLEAR_ON_RESET);
    operation = JCSystem.makeTransientObjectArray((short) 1, JCSystem.CLEAR_ON_RESET);
    dataIndex = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_RESET);
    // Initialize RKP mac key
    if (!seProvider.isUpgrading()) {
      short offset = repository.allocReclaimableMemory((short) RKP_MAC_KEY_SIZE);
      byte[] buffer = repository.getHeap();
      seProvider.getTrueRandomNumber(buffer, offset, RKP_MAC_KEY_SIZE);
      storeDataInst.createRkpMacKey(buffer, offset, RKP_MAC_KEY_SIZE);
      repository.reclaimMemory(RKP_MAC_KEY_SIZE);
    }
    operation[0] = null;
  }

  private void initializeDataTable() {
    clearDataTable();
    releaseOperation();
    dataIndex[0] = (short) (DATA_INDEX_SIZE * DATA_INDEX_ENTRY_SIZE);
  }

  private short dataAlloc(short length) {
    if ((short) (dataIndex[0] + length) > (short) data.length) {
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }
    dataIndex[0] += length;
    return (short) (dataIndex[0] - length);
  }

  private void clearDataTable() {
    Util.arrayFillNonAtomic(data, (short) 0, (short) data.length, (byte) 0x00);
    dataIndex[0] = 0x00;
  }

  private void releaseOperation() {
    if (operation[0] != null) {
      ((KMOperation) operation[0]).abort();
      operation[0] = null;
    }
  }

  private short createEntry(short index, short length) {
    index = (short) (index * DATA_INDEX_ENTRY_SIZE);
    short ptr = dataAlloc(length);
    Util.setShort(data, index, length);
    Util.setShort(data, (short) (index + DATA_INDEX_ENTRY_OFFSET), ptr);
    return ptr;
  }

  private short getEntry(short index) {
    index = (short) (index * DATA_INDEX_ENTRY_SIZE);
    return Util.getShort(data, (short) (index + DATA_INDEX_ENTRY_OFFSET));
  }

  private void processGetRkpHwInfoCmd(APDU apdu) {
    // Make the response
    // Author name - Google.
    short respPtr = KMArray.instance((short) 6);
    KMArray resp = KMArray.cast(respPtr);
    resp.add((short) 0, KMInteger.uint_16(KMError.OK));
    resp.add((short) 1, KMInteger.uint_16(RKP_VERSION));
    resp.add(
        (short) 2,
        KMByteBlob.instance(
            KMKeymasterApplet.Google, (short) 0, (short) KMKeymasterApplet.Google.length));
    // This field is no longer used in version 3
    resp.add((short) 3, KMInteger.uint_8(KMType.RKP_CURVE_NONE));
    resp.add((short) 4, KMByteBlob.instance(uniqueId, (short) 0, (short) uniqueId.length));
    resp.add((short) 5, KMInteger.uint_16(MIN_SUPPORTED_NUM_KEYS_IN_CSR));
    KMKeymasterApplet.sendOutgoing(apdu, respPtr);
  }

  /**
   * This function generates an EC key pair with attest key as purpose and creates an encrypted key
   * blob. It then generates a COSEMac message which includes the ECDSA public key.
   */
  public void processGenerateRkpKey(APDU apdu) {
    // Re-purpose the apdu buffer as scratch pad.
    byte[] scratchPad = apdu.getBuffer();
    KMKeymasterApplet.generateRkpKey(scratchPad, getEcAttestKeyParameters());
    short pubKey = KMKeymasterApplet.getPubKey();
    short coseMac0 = constructCoseMacForRkpKey(scratchPad, pubKey);
    // Encode the COSE_MAC0 object
    short arr = KMArray.instance((short) 3);
    KMArray.cast(arr).add((short) 0, KMInteger.uint_16(KMError.OK));
    KMArray.cast(arr).add((short) 1, coseMac0);
    KMArray.cast(arr).add((short) 2, KMKeymasterApplet.getPivateKey());
    KMKeymasterApplet.sendOutgoing(apdu, arr);
  }

  public short getHeaderLen(short length) {
    if (length <= TINY_PAYLOAD) {
      return (short) 1;
    } else if (length < SHORT_PAYLOAD) {
      return (short) 2;
    } else {
      return (short) 3;
    }
  }

  public void constructPartialSignedData(
      byte[] scratchPad,
      short coseKeysCount,
      short totalCoseKeysLen,
      short challengeByteBlob,
      short deviceInfo,
      short versionPtr,
      short certTypePtr) {
    // Initialize ECDSA operation
    initECDSAOperation();

    short versionLength = encoder.getEncodedLength(versionPtr);
    short certTypeLen = encoder.getEncodedLength(certTypePtr);
    short challengeLen = KMByteBlob.cast(challengeByteBlob).length();
    if (challengeLen > 64) {
      KMException.throwIt(KMError.STATUS_FAILED);
    }
    short challengeHeaderLen = encoder.getEncodedBytesLength(challengeLen);
    short deviceInfoLen = encoder.getEncodedLength(deviceInfo);

    // Calculate the keysToSign length
    // keysToSignLen = coseKeysArrayHeaderLen + totalCoseKeysLen
    short coseKeysArrHeaderLen = getHeaderLen(coseKeysCount);
    short keysToSignLen = (short) (coseKeysArrHeaderLen + totalCoseKeysLen);

    // Calculate the payload array header len
    /*
     * paylaodArrHeaderLen is Array of 2 elements that occupies 1 byte.
     * SignedData = [challenge, AuthenticatedRequest<CsrPayload>]
     */
    short paylaodArrHeaderLen = 1;
    /*
     * csrPaylaodArrHeaderLen is Array of 4 elements that occupies 1 byte.
     * CsrPayload = [version: 3, CertificateType, DeviceInfo, KeysToSign]
     */
    short csrPaylaodArrHeaderLen = 1;
    short csrPayloadLen =
        (short)
            (csrPaylaodArrHeaderLen + versionLength + certTypeLen + deviceInfoLen + keysToSignLen);
    short csrPaylaodByteHeaderLen = encoder.getEncodedBytesLength(csrPayloadLen);
    short payloadLen =
        (short)
            (paylaodArrHeaderLen
                + challengeHeaderLen
                + challengeLen
                + csrPaylaodByteHeaderLen
                + csrPaylaodArrHeaderLen
                + versionLength
                + certTypeLen
                + deviceInfoLen
                + keysToSignLen);

    // Empty aad
    short aad = KMByteBlob.instance(scratchPad, (short) 0, (short) 0);

    /* construct protected header */
    short protectedHeaders =
        KMCose.constructHeaders(
            rkpTmpVariables,
            KMNInteger.uint_8(KMCose.COSE_ALG_ES256),
            KMType.INVALID_VALUE,
            KMType.INVALID_VALUE,
            KMType.INVALID_VALUE);
    protectedHeaders =
        KMKeymasterApplet.encodeToApduBuffer(
            protectedHeaders, scratchPad, (short) 0, KMKeymasterApplet.MAX_COSE_BUF_SIZE);
    protectedHeaders = KMByteBlob.instance(scratchPad, (short) 0, protectedHeaders);

    // Construct partial signature
    short signStructure =
        KMCose.constructCoseSignStructure(protectedHeaders, aad, KMType.INVALID_VALUE);
    short partialSignStructureLen =
        KMKeymasterApplet.encodeToApduBuffer(
            signStructure, scratchPad, (short) 0, KMKeymasterApplet.MAX_COSE_BUF_SIZE);
    ((KMOperation) operation[0]).update(scratchPad, (short) 0, partialSignStructureLen);

    // Add payload Byte Header
    short prevReclaimIndex = repository.getHeapReclaimIndex();
    byte[] heap = repository.getHeap();
    short heapIndex = repository.allocReclaimableMemory(MAX_ENCODED_BUF_SIZE);
    short byteBlobHeaderLen = encoder.encodeByteBlobHeader(payloadLen, heap, heapIndex, (short) 3);
    ((KMOperation) operation[0]).update(heap, heapIndex, byteBlobHeaderLen);

    short arr = KMArray.instance((short) 2);
    KMArray.cast(arr).add((short) 0, challengeByteBlob);
    KMArray.cast(arr).add((short) 1, KMType.INVALID_VALUE);
    short payloadArrayLen = encoder.encode(arr, heap, heapIndex, prevReclaimIndex);
    ((KMOperation) operation[0]).update(heap, heapIndex, payloadArrayLen);

    byteBlobHeaderLen = encoder.encodeByteBlobHeader(csrPayloadLen, heap, heapIndex, (short) 3);
    ((KMOperation) operation[0]).update(heap, heapIndex, byteBlobHeaderLen);

    // Construct partial csr payload array
    arr = KMArray.instance((short) 4);
    KMArray.cast(arr).add((short) 0, versionPtr);
    KMArray.cast(arr).add((short) 1, certTypePtr);
    KMArray.cast(arr).add((short) 2, deviceInfo);
    KMArray.cast(arr).add((short) 3, KMType.INVALID_VALUE);
    short partialCsrPayloadArrayLen = encoder.encode(arr, heap, heapIndex, prevReclaimIndex);
    ((KMOperation) operation[0]).update(heap, heapIndex, partialCsrPayloadArrayLen);

    // Encode keysToSign Array Header length
    short keysToSignArrayHeaderLen =
        encoder.encodeArrayHeader(coseKeysCount, heap, heapIndex, (short) 3);
    ((KMOperation) operation[0]).update(heap, heapIndex, keysToSignArrayHeaderLen);
    repository.reclaimMemory(MAX_ENCODED_BUF_SIZE);
  }

  /**
   * This is the first command of the generateCSR.
   * Input:
   *   1) Number of RKP keys.
   *   2) Total size of the encoded CoseKeys (Each RKP key is represented in CoseKey)
   *   3) Challenge
   * Process:
   *   1) creates device info, initializes version and cert type.
   *   2) Initialize the ECDSA operation with the deviceUniqueKeyPair and do partial sign of the
   *      CsrPayload with the initial input data received. A Multipart update on ECDSA is
   *      called on each updateKey command in the second stage.
   *   3) Store the number of RKP keys in the temporary data buffer.
   *   4) Update the phase of the generateCSR function to BEGIN.
   *   5) generates RKP device info
   * Response:
   *   1) Send OK response.
   *   2) Encoded device info
   *   3) CSR payload CDDL schema version
   *   4) Cert type
   *
   * @param apdu Input apdu
   */
  public void processBeginSendData(APDU apdu) throws Exception {
    try {
      initializeDataTable();
      short arr = KMArray.instance((short) 3);
      KMArray.cast(arr).add((short) 0, KMInteger.exp()); // Array length
      KMArray.cast(arr).add((short) 1, KMInteger.exp()); // Total length of the encoded CoseKeys.
      KMArray.cast(arr).add((short) 2, KMByteBlob.exp()); // challenge
      arr = KMKeymasterApplet.receiveIncoming(apdu, arr);
      // Re-purpose the apdu buffer as scratch pad.
      byte[] scratchPad = apdu.getBuffer();
      short deviceInfo = createDeviceInfo(scratchPad);
      short versionPtr = KMInteger.uint_16(CSR_PAYLOAD_CDDL_SCHEMA_VERSION);
      short certTypePtr =
          KMTextString.instance(DI_CERT_TYPE, (short) 0, (short) DI_CERT_TYPE.length);

      constructPartialSignedData(
          scratchPad,
          KMInteger.cast(KMArray.cast(arr).get((short) 0)).getShort(),
          KMInteger.cast(KMArray.cast(arr).get((short) 1)).getShort(),
          KMArray.cast(arr).get((short) 2),
          deviceInfo,
          versionPtr,
          certTypePtr);
      // Store the total keys in data table.
      short dataEntryIndex = createEntry(TOTAL_KEYS_TO_SIGN, SHORT_SIZE);
      Util.setShort(
          data, dataEntryIndex, KMInteger.cast(KMArray.cast(arr).get((short) 0)).getShort());
      // Store the current csr status, which is BEGIN.
      createEntry(GENERATE_CSR_PHASE, BYTE_SIZE);
      updateState(BEGIN);
      if (0 == KMInteger.cast(KMArray.cast(arr).get((short) 0)).getShort()) {
        updateState(UPDATE);
      }
      short prevReclaimIndex = repository.getHeapReclaimIndex();
      short offset = repository.allocReclaimableMemory(MAX_ENCODED_BUF_SIZE);
      short length =
          encoder.encode(
              deviceInfo, repository.getHeap(), offset, prevReclaimIndex, MAX_ENCODED_BUF_SIZE);
      short encodedDeviceInfo = KMByteBlob.instance(repository.getHeap(), offset, length);
      // release memory
      repository.reclaimMemory(MAX_ENCODED_BUF_SIZE);
      // Send response.
      short array = KMArray.instance((short) 4);
      KMArray.cast(array).add((short) 0, KMInteger.uint_16(KMError.OK));
      KMArray.cast(array).add((short) 1, encodedDeviceInfo);
      KMArray.cast(array).add((short) 2, versionPtr);
      KMArray.cast(array).add((short) 3, certTypePtr);
      KMKeymasterApplet.sendOutgoing(apdu, array);
    } catch (Exception e) {
      clearDataTable();
      releaseOperation();
      throw e;
    }
  }

  /**
   * This is the second command of the generateCSR. This command will be called in a loop
   * for the number of keys.
   * Input:
   *   CoseMac0 containing the RKP Key
   * Process:
   *   1) Validate the phase of generateCSR. Prior state should be either BEGIN or UPDATE.
   *   2) Validate the number of RKP Keys received against the value received in the first command.
   *   3) Validate the CoseMac0 structure and extract the RKP Key.
   *   4) Do Multipart ECDSA update operation with the input as RKP key.
   *   5) Update the number of keys received count into the data buffer.
   *   6) Update the phase of the generateCSR function to UPDATE.
   * Response:
   *   1) Send OK response.
   *   2) encoded Cose Key
   * @param apdu Input apdu
   */
  public void processUpdateKey(APDU apdu) throws Exception {
    try {
      // The prior state can be BEGIN or UPDATE
      validateState((byte) (BEGIN | UPDATE));
      validateKeysToSignCount();
      short headers = KMCoseHeaders.exp();
      short arrInst = KMArray.instance((short) 4);
      short byteBlobExp = KMByteBlob.exp();
      KMArray.cast(arrInst).add((short) 0, byteBlobExp);
      KMArray.cast(arrInst).add((short) 1, headers);
      KMArray.cast(arrInst).add((short) 2, byteBlobExp);
      KMArray.cast(arrInst).add((short) 3, byteBlobExp);
      short arr = KMArray.exp(arrInst);
      arr = KMKeymasterApplet.receiveIncoming(apdu, arr);
      arrInst = KMArray.cast(arr).get((short) 0);
      // Re-purpose the apdu buffer as scratch pad.
      byte[] scratchPad = apdu.getBuffer();

      // Validate and extract the CoseKey from CoseMac0 message.
      short coseKey = validateAndExtractPublicKey(arrInst, scratchPad);
      // Encode CoseKey
      short length =
          KMKeymasterApplet.encodeToApduBuffer(
              coseKey, scratchPad, (short) 0, KMKeymasterApplet.MAX_COSE_BUF_SIZE);
      // Do ECDSA update with input as encoded CoseKey.
      ((KMOperation) operation[0]).update(scratchPad, (short) 0, length);
      short encodedCoseKey = KMByteBlob.instance(scratchPad, (short) 0, length);

      // Increment the count each time this function gets executed.
      // Store the count in data table.
      short dataEntryIndex = getEntry(KEYS_TO_SIGN_COUNT);
      if (dataEntryIndex == 0) {
        dataEntryIndex = createEntry(KEYS_TO_SIGN_COUNT, SHORT_SIZE);
      }
      length = Util.getShort(data, dataEntryIndex);
      Util.setShort(data, dataEntryIndex, ++length);
      // Update the csr state
      updateState(UPDATE);
      // Send response.
      short array = KMArray.instance((short) 2);
      KMArray.cast(array).add((short) 0, KMInteger.uint_16(KMError.OK));
      KMArray.cast(array).add((short) 1, encodedCoseKey);
      KMKeymasterApplet.sendOutgoing(apdu, array);
    } catch (Exception e) {
      clearDataTable();
      releaseOperation();
      throw e;
    }
  }

  /**
   * This is the third command of generateCSR.
   * Input:
   *   No input data.
   * Process:
   *   1) Validate the phase of generateCSR. Prior state should be UPDATE.
   *   2) Check if all the RKP keys are received, if not, throw exception.
   *   3) Finalize the ECDSA operation and get the signature, signature of SignedDataSigStruct
   *   where SignedDataSigStruct is
   *     [context: "Signature1",
   *       protected: bstr .cbor {1 : AlgorithmEdDSA / AlgorithmES256},
   *       external_aad: bstr .size 0,
   *       payload: bstr .cbor [challenge,
   *                            bstr .cbor CsrPayload = [version, CertificateType, DeviceInfo, KeysToSign]
   *                           ]
   *    ]
   *   4) Construct protected header data.
   *   5) Update the phase of the generateCSR function to FINISH.
   * Response:
   *   OK
   *   protectedHeader - SignedData protected header
   *   Signature of SignedDataSigStruct
   *   Version - The AuthenticatedRequest CDDL Schema version.
   *   Flag to represent there is more data to retrieve.
   * @param apdu Input apdu.
   */
  public void processFinishSendData(APDU apdu) throws Exception {
    try {
      // The prior state should be UPDATE.
      validateState(UPDATE);
      byte[] scratchPad = apdu.getBuffer();
      if (data[getEntry(TOTAL_KEYS_TO_SIGN)] != data[getEntry(KEYS_TO_SIGN_COUNT)]) {
        // Mismatch in the number of keys sent.
        ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
      }
      // PubKeysToSignMac
      short empty = repository.alloc((short) 0);
      short len =
          ((KMOperation) operation[0])
              .sign(repository.getHeap(), (short) empty, (short) 0, scratchPad, (short) 0);
      releaseOperation();
      short signatureData = KMByteBlob.instance(scratchPad, (short) 0, len);
      len = KMAsn1Parser.instance().decodeEcdsa256Signature(signatureData, scratchPad, (short) 0);

      signatureData = KMByteBlob.instance(scratchPad, (short) 0, len);

      /* construct protected header */
      short protectedHeaders =
          KMCose.constructHeaders(
              rkpTmpVariables,
              KMNInteger.uint_8(KMCose.COSE_ALG_ES256),
              KMType.INVALID_VALUE,
              KMType.INVALID_VALUE,
              KMType.INVALID_VALUE);
      protectedHeaders =
          KMKeymasterApplet.encodeToApduBuffer(
              protectedHeaders, scratchPad, (short) 0, KMKeymasterApplet.MAX_COSE_BUF_SIZE);
      protectedHeaders = KMByteBlob.instance(scratchPad, (short) 0, protectedHeaders);

      updateState(FINISH);
      short arr = KMArray.instance((short) 5);
      KMArray.cast(arr).add((short) 0, KMInteger.uint_16(KMError.OK));
      KMArray.cast(arr).add((short) 1, protectedHeaders);
      KMArray.cast(arr).add((short) 2, signatureData);
      KMArray.cast(arr).add((short) 3, KMInteger.uint_8(RKP_AUTHENTICATE_CDDL_SCHEMA_VERSION));
      KMArray.cast(arr).add((short) 4, KMInteger.uint_8(MORE_DATA));
      KMKeymasterApplet.sendOutgoing(apdu, arr);
    } catch (Exception e) {
      clearDataTable();
      releaseOperation();
      throw e;
    }
  }

  /**
   * This is the fourth command of generateCSR. This command is called multiple times by the
   * HAL until complete UdsCerts are received. On each call, a chunk of 512 bytes data is sent.
   * Input:
   *   No input data.
   * Process:
   *   1) Validate the phase of generateCSR. Prior state should be FINISH.
   *   2) checks if Uds cert is present and sends the certs in chunks of 512 bytes.
   *   3) Update the phase of the generateCSR function to GET_UDS_CERTS_RESPONSE. In-case of
   *     a) No Uds certs present and
   *     b) When last chunk of Uds cert is sent
   * Response:
   *   OK
   *   Uds cert data
   *   Flag to represent there is more data to retrieve.
   * @param apdu Input apdu.
   */
  public void processGetUdsCerts(APDU apdu) throws Exception {
    try {
      // The prior state should be FINISH.
      validateState((byte) (FINISH));
      short len;
      byte moreData;
      byte[] scratchPad = apdu.getBuffer();
      if (!isUdsCertsChainPresent()) {
        createEntry(RESPONSE_PROCESSING_STATE, BYTE_SIZE);
        updateState(GET_UDS_CERTS_RESPONSE);
        moreData = NO_DATA;
        scratchPad[0] = (byte) 0xA0; // CBOR Encoded empty map is A0
        len = 1;
      } else {
        len = processUdsCertificateChain(scratchPad);
        moreData = MORE_DATA;
        byte state = getCurrentOutputProcessingState();
        switch (state) {
          case PROCESSING_UDS_CERTS_IN_PROGRESS:
            moreData = MORE_DATA;
            break;
          case PROCESSING_UDS_CERTS_COMPLETE:
            updateState(GET_UDS_CERTS_RESPONSE);
            moreData = NO_DATA;
            break;
          default:
            KMException.throwIt(KMError.INVALID_STATE);
        }
      }
      short data = KMByteBlob.instance(scratchPad, (short) 0, len);
      short arr = KMArray.instance((short) 3);
      KMArray.cast(arr).add((short) 0, KMInteger.uint_16(KMError.OK));
      KMArray.cast(arr).add((short) 1, data);
      // represents there is more output to retrieve
      KMArray.cast(arr).add((short) 2, KMInteger.uint_8(moreData));
      KMKeymasterApplet.sendOutgoing(apdu, arr);
    } catch (Exception e) {
      clearDataTable();
      throw e;
    }
  }

  /**
   * This is the fifth command of generateCSR. This command is called multiple times by the
   * HAL until complete Dice cert chain is received. On each call, a chunk of 512 bytes data is sent.
   * Input:
   *   No input data.
   * Process:
   *   1) Validate the phase of generateCSR. Prior state should be GET_UDS_CERTS_RESPONSE.
   *   2) Sends the Dice cert chain data in chunks of 512 bytes.
   *
   *   After receiving a complete dice cert chain in HAL, Hal constructs the final CSR using the output data
   *   returned from all the 5 generateCSR commands in Applet.
   *
   * Response:
   *   OK
   *   Dice cert chain data
   *   Flag to represent there is more data to retrieve.
   * @param apdu Input apdu.
   */
  public void processGetDiceCertChain(APDU apdu) throws Exception {
    try {
      // The prior state should be GET_UDS_CERTS_RESPONSE.
      validateState((byte) (GET_UDS_CERTS_RESPONSE));
      byte[] scratchPad = apdu.getBuffer();
      short len = 0;
      len = processDiceCertChain(scratchPad);
      byte moreData = MORE_DATA;
      byte state = getCurrentOutputProcessingState();
      switch (state) {
        case PROCESSING_DICE_CERTS_IN_PROGRESS:
          moreData = MORE_DATA;
          break;
        case PROCESSING_DICE_CERTS_COMPLETE:
          moreData = NO_DATA;
          clearDataTable();
          break;
        default:
          KMException.throwIt(KMError.INVALID_STATE);
      }
      short data = KMByteBlob.instance(scratchPad, (short) 0, len);
      short arr = KMArray.instance((short) 3);
      KMArray.cast(arr).add((short) 0, KMInteger.uint_16(KMError.OK));
      KMArray.cast(arr).add((short) 1, data);
      // represents there is more output to retrieve
      KMArray.cast(arr).add((short) 2, KMInteger.uint_8(moreData));
      KMKeymasterApplet.sendOutgoing(apdu, arr);
    } catch (Exception e) {
      clearDataTable();
      throw e;
    }
  }

  private boolean isUdsCertsChainPresent() {
    if (!IS_UDS_SUPPORTED_IN_RKP_SERVER || (storeDataInst.getUdsCertChainLength() == 0)) {
      return false;
    }
    return true;
  }

  public void process(short ins, APDU apdu) throws Exception {
    switch (ins) {
      case KMKeymasterApplet.INS_GET_RKP_HARDWARE_INFO:
        processGetRkpHwInfoCmd(apdu);
        break;
      case KMKeymasterApplet.INS_GENERATE_RKP_KEY_CMD:
        processGenerateRkpKey(apdu);
        break;
      case KMKeymasterApplet.INS_BEGIN_SEND_DATA_CMD:
        processBeginSendData(apdu);
        break;
      case KMKeymasterApplet.INS_UPDATE_KEY_CMD:
        processUpdateKey(apdu);
        break;
      case KMKeymasterApplet.INS_FINISH_SEND_DATA_CMD:
        processFinishSendData(apdu);
        break;
      case KMKeymasterApplet.INS_GET_UDS_CERTS_CMD:
        processGetUdsCerts(apdu);
        break;
      case KMKeymasterApplet.INS_GET_DICE_CERT_CHAIN_CMD:
        processGetDiceCertChain(apdu);
        break;
      default:
        ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
    }
  }

  private byte getCurrentOutputProcessingState() {
    short index = getEntry(RESPONSE_PROCESSING_STATE);
    if (index == 0) {
      return START_PROCESSING;
    }
    return data[index];
  }

  private void updateOutputProcessingState(byte state) {
    short dataEntryIndex = getEntry(RESPONSE_PROCESSING_STATE);
    data[dataEntryIndex] = state;
  }

  /**
   * Validates the CoseMac message and extracts the CoseKey from it.
   *
   * @param coseMacPtr CoseMac instance to be validated.
   * @param scratchPad Scratch buffer used to store temp results.
   * @return CoseKey instance.
   */
  private short validateAndExtractPublicKey(short coseMacPtr, byte[] scratchPad) {
    // Version 3 removes the need to have testMode in function calls
    boolean testMode = false;
    // Exp for KMCoseHeaders
    short coseHeadersExp = KMCoseHeaders.exp();
    // Exp for coseky
    short coseKeyExp = KMCoseKey.exp();

    // validate protected Headers
    short ptr = KMArray.cast(coseMacPtr).get(KMCose.COSE_MAC0_PROTECTED_PARAMS_OFFSET);
    ptr =
        decoder.decode(
            coseHeadersExp,
            KMByteBlob.cast(ptr).getBuffer(),
            KMByteBlob.cast(ptr).getStartOff(),
            KMByteBlob.cast(ptr).length());

    if (!KMCoseHeaders.cast(ptr)
        .isDataValid(rkpTmpVariables, KMCose.COSE_ALG_HMAC_256, KMType.INVALID_VALUE)) {
      KMException.throwIt(KMError.STATUS_FAILED);
    }

    // Validate payload.
    ptr = KMArray.cast(coseMacPtr).get(KMCose.COSE_MAC0_PAYLOAD_OFFSET);
    ptr =
        decoder.decode(
            coseKeyExp,
            KMByteBlob.cast(ptr).getBuffer(),
            KMByteBlob.cast(ptr).getStartOff(),
            KMByteBlob.cast(ptr).length());

    if (!KMCoseKey.cast(ptr)
        .isDataValid(
            rkpTmpVariables,
            KMCose.COSE_KEY_TYPE_EC2,
            KMType.INVALID_VALUE,
            KMCose.COSE_ALG_ES256,
            KMCose.COSE_ECCURVE_256)) {
      KMException.throwIt(KMError.STATUS_FAILED);
    }

    boolean isTestKey = KMCoseKey.cast(ptr).isTestKey();
    if (isTestKey && !testMode) {
      KMException.throwIt(KMError.STATUS_TEST_KEY_IN_PRODUCTION_REQUEST);
    } else if (!isTestKey && testMode) {
      KMException.throwIt(KMError.STATUS_PRODUCTION_KEY_IN_TEST_REQUEST);
    }

    // Compute CoseMac Structure and compare the macs.
    short macStructure =
        KMCose.constructCoseMacStructure(
            KMArray.cast(coseMacPtr).get(KMCose.COSE_MAC0_PROTECTED_PARAMS_OFFSET),
            KMByteBlob.instance((short) 0),
            KMArray.cast(coseMacPtr).get(KMCose.COSE_MAC0_PAYLOAD_OFFSET));
    short encodedLen =
        KMKeymasterApplet.encodeToApduBuffer(
            macStructure, scratchPad, (short) 0, KMKeymasterApplet.MAX_COSE_BUF_SIZE);

    short hmacLen = rkpHmacSign(scratchPad, (short) 0, encodedLen, scratchPad, encodedLen);

    if (hmacLen
        != KMByteBlob.cast(KMArray.cast(coseMacPtr).get(KMCose.COSE_MAC0_TAG_OFFSET)).length()) {
      KMException.throwIt(KMError.STATUS_INVALID_MAC);
    }

    if (0
        != Util.arrayCompare(
            scratchPad,
            encodedLen,
            KMByteBlob.cast(KMArray.cast(coseMacPtr).get(KMCose.COSE_MAC0_TAG_OFFSET)).getBuffer(),
            KMByteBlob.cast(KMArray.cast(coseMacPtr).get(KMCose.COSE_MAC0_TAG_OFFSET))
                .getStartOff(),
            hmacLen)) {
      KMException.throwIt(KMError.STATUS_INVALID_MAC);
    }
    return ptr;
  }

  private void validateKeysToSignCount() {
    short index = getEntry(KEYS_TO_SIGN_COUNT);
    short keysToSignCount = 0;
    if (index != 0) {
      keysToSignCount = Util.getShort(data, index);
    }
    if (Util.getShort(data, getEntry(TOTAL_KEYS_TO_SIGN)) <= keysToSignCount) {
      // Mismatch in the number of keys sent.
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }
  }

  private void validateState(byte expectedState) {
    short dataEntryIndex = getEntry(GENERATE_CSR_PHASE);
    if (0 == (data[dataEntryIndex] & expectedState)) {
      KMException.throwIt(KMError.INVALID_STATE);
    }
  }

  private void updateState(byte state) {
    short dataEntryIndex = getEntry(GENERATE_CSR_PHASE);
    if (dataEntryIndex == 0) {
      KMException.throwIt(KMError.INVALID_STATE);
    }
    data[dataEntryIndex] = state;
  }

  /*
   * Create DeviceInfo structure as specified in the RKPV3.0 specification.
   */
  private short createDeviceInfo(byte[] scratchpad) {
    // Device Info Key Value pairs.
    for (short i = 0; i < 32; i++) {
      rkpTmpVariables[i] = KMType.INVALID_VALUE;
    }
    short dataOffset = 2;
    rkpTmpVariables[0] = dataOffset;
    rkpTmpVariables[1] = 0;
    short metaOffset = 0;
    updateItem(
        rkpTmpVariables,
        metaOffset,
        BRAND,
        getAttestationId(KMType.ATTESTATION_ID_BRAND, scratchpad));
    updateItem(
        rkpTmpVariables,
        metaOffset,
        MANUFACTURER,
        getAttestationId(KMType.ATTESTATION_ID_MANUFACTURER, scratchpad));
    updateItem(
        rkpTmpVariables,
        metaOffset,
        PRODUCT,
        getAttestationId(KMType.ATTESTATION_ID_PRODUCT, scratchpad));
    updateItem(
        rkpTmpVariables,
        metaOffset,
        MODEL,
        getAttestationId(KMType.ATTESTATION_ID_MODEL, scratchpad));
    updateItem(
        rkpTmpVariables,
        metaOffset,
        DEVICE,
        getAttestationId(KMType.ATTESTATION_ID_DEVICE, scratchpad));
    updateItem(rkpTmpVariables, metaOffset, VB_STATE, getVbState());
    updateItem(rkpTmpVariables, metaOffset, BOOTLOADER_STATE, getBootloaderState());
    updateItem(rkpTmpVariables, metaOffset, VB_META_DIGEST, getVerifiedBootHash(scratchpad));
    updateItem(rkpTmpVariables, metaOffset, OS_VERSION, getBootParams(OS_VERSION_ID, scratchpad));
    updateItem(
        rkpTmpVariables,
        metaOffset,
        SYSTEM_PATCH_LEVEL,
        getBootParams(SYSTEM_PATCH_LEVEL_ID, scratchpad));
    updateItem(
        rkpTmpVariables,
        metaOffset,
        BOOT_PATCH_LEVEL,
        getBootParams(BOOT_PATCH_LEVEL_ID, scratchpad));
    updateItem(
        rkpTmpVariables,
        metaOffset,
        VENDOR_PATCH_LEVEL,
        getBootParams(VENDOR_PATCH_LEVEL_ID, scratchpad));
    updateItem(
        rkpTmpVariables,
        metaOffset,
        SECURITY_LEVEL,
        KMTextString.instance(DI_SECURITY_LEVEL, (short) 0, (short) DI_SECURITY_LEVEL.length));
    updateItem(rkpTmpVariables, metaOffset, FUSED, KMInteger.uint_8(storeDataInst.secureBootMode));
    // Create device info map.
    short map = KMMap.instance(rkpTmpVariables[1]);
    short mapIndex = 0;
    short index = 2;
    while (index < (short) 32) {
      if (rkpTmpVariables[index] != KMType.INVALID_VALUE) {
        KMMap.cast(map)
            .add(mapIndex++, rkpTmpVariables[index], rkpTmpVariables[(short) (index + 1)]);
      }
      index += 2;
    }
    KMMap.cast(map).canonicalize();
    return map;
  }

  // Below 6 methods are helper methods to create device info structure.
  // ----------------------------------------------------------------------------

  /**
   * Update the item inside the device info structure.
   *
   * @param deviceIds Device Info structure to be updated.
   * @param metaOffset Out parameter meta information. Offset 0 is index and Offset 1 is length.
   * @param item Key info to be updated.
   * @param value value to be updated.
   */
  private void updateItem(short[] deviceIds, short metaOffset, byte[] item, short value) {
    if (KMType.INVALID_VALUE != value) {
      deviceIds[deviceIds[metaOffset]++] =
          KMTextString.instance(item, (short) 0, (short) item.length);
      deviceIds[deviceIds[metaOffset]++] = value;
      deviceIds[(short) (metaOffset + 1)]++;
    }
  }

  private short getAttestationId(short attestId, byte[] scratchpad) {
    short attIdTagLen = storeDataInst.getAttestationId(attestId, scratchpad, (short) 0);
    if (attIdTagLen == 0) {
      KMException.throwIt(KMError.INVALID_STATE);
    }
    return KMTextString.instance(scratchpad, (short) 0, attIdTagLen);
  }

  private short getVerifiedBootHash(byte[] scratchPad) {
    short len = storeDataInst.getVerifiedBootHash(scratchPad, (short) 0);
    if (len == 0) {
      KMException.throwIt(KMError.INVALID_STATE);
    }
    return KMByteBlob.instance(scratchPad, (short) 0, len);
  }

  private short getBootloaderState() {
    short bootloaderState;
    if (storeDataInst.isDeviceBootLocked()) {
      bootloaderState = KMTextString.instance(LOCKED, (short) 0, (short) LOCKED.length);
    } else {
      bootloaderState = KMTextString.instance(UNLOCKED, (short) 0, (short) UNLOCKED.length);
    }
    return bootloaderState;
  }

  private short getVbState() {
    short state = storeDataInst.getBootState();
    short vbState = KMType.INVALID_VALUE;
    if (state == KMType.VERIFIED_BOOT) {
      vbState = KMTextString.instance(VB_STATE_GREEN, (short) 0, (short) VB_STATE_GREEN.length);
    } else if (state == KMType.SELF_SIGNED_BOOT) {
      vbState = KMTextString.instance(VB_STATE_YELLOW, (short) 0, (short) VB_STATE_YELLOW.length);
    } else if (state == KMType.UNVERIFIED_BOOT) {
      vbState = KMTextString.instance(VB_STATE_ORANGE, (short) 0, (short) VB_STATE_ORANGE.length);
    } else if (state == KMType.FAILED_BOOT) {
      vbState = KMTextString.instance(VB_STATE_RED, (short) 0, (short) VB_STATE_RED.length);
    }
    return vbState;
  }

  private short converIntegerToTextString(short intPtr, byte[] scratchPad) {
    // Prepare Hex Values
    short index = 1;
    scratchPad[0] = 0x30; // Ascii 0
    while (index < 10) {
      scratchPad[index] = (byte) (scratchPad[(short) (index - 1)] + 1);
      index++;
    }
    scratchPad[index++] = 0x41; // Ascii 'A'
    while (index < 16) {
      scratchPad[index] = (byte) (scratchPad[(short) (index - 1)] + 1);
      index++;
    }

    short intLen = KMInteger.cast(intPtr).length();
    short intOffset = KMInteger.cast(intPtr).getStartOff();
    byte[] buf = repository.getHeap();
    short tsPtr = KMTextString.instance((short) (intLen * 2));
    short tsStartOff = KMTextString.cast(tsPtr).getStartOff();
    index = 0;
    byte nibble;
    while (index < intLen) {
      nibble = (byte) ((byte) (buf[intOffset] >> 4) & (byte) 0x0F);
      buf[tsStartOff] = scratchPad[nibble];
      nibble = (byte) (buf[intOffset] & 0x0F);
      buf[(short) (tsStartOff + 1)] = scratchPad[nibble];
      index++;
      intOffset++;
      tsStartOff += 2;
    }
    return tsPtr;
  }

  private short getBootParams(byte bootParam, byte[] scratchPad) {
    short value = KMType.INVALID_VALUE;
    switch (bootParam) {
      case OS_VERSION_ID:
        value = storeDataInst.getOsVersion();
        break;
      case SYSTEM_PATCH_LEVEL_ID:
        value = storeDataInst.getOsPatch();
        break;
      case BOOT_PATCH_LEVEL_ID:
        value = storeDataInst.getBootPatchLevel();
        break;
      case VENDOR_PATCH_LEVEL_ID:
        value = storeDataInst.getVendorPatchLevel();
        break;
      default:
        KMException.throwIt(KMError.INVALID_ARGUMENT);
    }
    // Convert Integer to Text String for OS_VERSION.
    if (bootParam == OS_VERSION_ID) {
      value = converIntegerToTextString(value, scratchPad);
    }
    return value;
  }
  // ----------------------------------------------------------------------------

  // ----------------------------------------------------------------------------
  private void initECDSAOperation() {
    KMKey deviceUniqueKeyPair = storeDataInst.getRkpDeviceUniqueKeyPair();
    operation[0] =
        seProvider.getRkpOperation(
            KMType.SIGN,
            KMType.EC,
            KMType.SHA2_256,
            KMType.PADDING_NONE,
            (byte) 0,
            deviceUniqueKeyPair,
            null,
            (short) 0,
            (short) 0,
            (short) 0);
    if (operation[0] == null) {
      KMException.throwIt(KMError.STATUS_FAILED);
    }
  }

  private short getResponseProcessedLength(short index) {
    short dataEntryIndex = getEntry(index);
    if (dataEntryIndex == 0) {
      dataEntryIndex = createEntry(index, SHORT_SIZE);
      Util.setShort(data, dataEntryIndex, (short) 0);
      return (short) 0;
    }
    return Util.getShort(data, dataEntryIndex);
  }

  private void updateResponseProcessedLength(short index, short processedLen) {
    short dataEntryIndex = getEntry(index);
    Util.setShort(data, dataEntryIndex, processedLen);
  }

  private short processUdsCertificateChain(byte[] scratchPad) {
    byte[] persistedData = storeDataInst.getUdsCertChain();
    short totalUccLen = Util.getShort(persistedData, (short) 0);
    createEntry(RESPONSE_PROCESSING_STATE, BYTE_SIZE);
    if (totalUccLen == 0) {
      // No Uds certificate chain present.
      updateOutputProcessingState(PROCESSING_UDS_CERTS_COMPLETE);
      return 0;
    }
    short processedLen = getResponseProcessedLength(UDS_PROCESSED_LENGTH);
    short lengthToSend = (short) (totalUccLen - processedLen);
    if (lengthToSend > MAX_SEND_DATA) {
      lengthToSend = MAX_SEND_DATA;
    }
    Util.arrayCopyNonAtomic(
        persistedData, (short) (2 + processedLen), scratchPad, (short) 0, lengthToSend);

    processedLen += lengthToSend;
    updateResponseProcessedLength(UDS_PROCESSED_LENGTH, processedLen);
    // Update the output processing state.
    updateOutputProcessingState(
        (processedLen == totalUccLen)
            ? PROCESSING_UDS_CERTS_COMPLETE
            : PROCESSING_UDS_CERTS_IN_PROGRESS);
    return lengthToSend;
  }

  // Dice cert chain for STRONGBOX has chain length of 2. So it can be returned in a single go.
  private short processDiceCertChain(byte[] scratchPad) {
    byte[] diceCertChain = storeDataInst.getDiceCertificateChain();
    short totalDccLen = Util.getShort(diceCertChain, (short) 0);
    if (totalDccLen == 0) {
      // No Uds certificate chain present.
      updateOutputProcessingState(PROCESSING_DICE_CERTS_COMPLETE);
      return 0;
    }
    short processedLen = getResponseProcessedLength(DICE_PROCESSED_LENGTH);
    short lengthToSend = (short) (totalDccLen - processedLen);
    if (lengthToSend > MAX_SEND_DATA) {
      lengthToSend = MAX_SEND_DATA;
    }
    Util.arrayCopyNonAtomic(
        diceCertChain, (short) (2 + processedLen), scratchPad, (short) 0, lengthToSend);

    processedLen += lengthToSend;
    updateResponseProcessedLength(DICE_PROCESSED_LENGTH, processedLen);
    // Update the output processing state.
    updateOutputProcessingState(
        (processedLen == totalDccLen)
            ? PROCESSING_DICE_CERTS_COMPLETE
            : PROCESSING_DICE_CERTS_IN_PROGRESS);
    return lengthToSend;
  }

  private short constructCoseMacForRkpKey(byte[] scratchPad, short pubKey) {
    // prepare cosekey
    short coseKey =
        KMCose.constructCoseKey(
            rkpTmpVariables,
            KMInteger.uint_8(KMCose.COSE_KEY_TYPE_EC2),
            KMType.INVALID_VALUE,
            KMNInteger.uint_8(KMCose.COSE_ALG_ES256),
            KMInteger.uint_8(KMCose.COSE_ECCURVE_256),
            KMByteBlob.cast(pubKey).getBuffer(),
            KMByteBlob.cast(pubKey).getStartOff(),
            KMByteBlob.cast(pubKey).length(),
            KMType.INVALID_VALUE);
    // Encode the cose key and make it as payload.
    short len =
        KMKeymasterApplet.encodeToApduBuffer(
            coseKey, scratchPad, (short) 0, KMKeymasterApplet.MAX_COSE_BUF_SIZE);
    short payload = KMByteBlob.instance(scratchPad, (short) 0, len);
    // Prepare protected header, which is required to construct the COSE_MAC0
    short headerPtr =
        KMCose.constructHeaders(
            rkpTmpVariables,
            KMInteger.uint_8(KMCose.COSE_ALG_HMAC_256),
            KMType.INVALID_VALUE,
            KMType.INVALID_VALUE,
            KMType.INVALID_VALUE);
    // Encode the protected header as byte blob.
    len =
        KMKeymasterApplet.encodeToApduBuffer(
            headerPtr, scratchPad, (short) 0, KMKeymasterApplet.MAX_COSE_BUF_SIZE);
    short protectedHeader = KMByteBlob.instance(scratchPad, (short) 0, len);
    // create MAC_Structure
    short macStructure =
        KMCose.constructCoseMacStructure(protectedHeader, KMByteBlob.instance((short) 0), payload);
    // Encode the Mac_structure and do HMAC_Sign to produce the tag for COSE_MAC0
    len =
        KMKeymasterApplet.encodeToApduBuffer(
            macStructure, scratchPad, (short) 0, KMKeymasterApplet.MAX_COSE_BUF_SIZE);
    // HMAC Sign.
    short hmacLen = rkpHmacSign(scratchPad, (short) 0, len, scratchPad, len);
    // Create COSE_MAC0 object
    short coseMac0 =
        KMCose.constructCoseMac0(
            protectedHeader,
            KMCoseHeaders.instance(KMArray.instance((short) 0)),
            payload,
            KMByteBlob.instance(scratchPad, len, hmacLen));
    len =
        KMKeymasterApplet.encodeToApduBuffer(
            coseMac0, scratchPad, (short) 0, KMKeymasterApplet.MAX_COSE_BUF_SIZE);
    return KMByteBlob.instance(scratchPad, (short) 0, len);
  }

  private short getEcAttestKeyParameters() {
    short tagIndex = 0;
    short arrPtr = KMArray.instance((short) 6);
    // Key size - 256
    short keySize =
        KMIntegerTag.instance(KMType.UINT_TAG, KMType.KEYSIZE, KMInteger.uint_16((short) 256));
    // Digest - SHA256
    short byteBlob = KMByteBlob.instance((short) 1);
    KMByteBlob.cast(byteBlob).add((short) 0, KMType.SHA2_256);
    short digest = KMEnumArrayTag.instance(KMType.DIGEST, byteBlob);
    // Purpose - Attest
    byteBlob = KMByteBlob.instance((short) 1);
    KMByteBlob.cast(byteBlob).add((short) 0, KMType.ATTEST_KEY);
    short purpose = KMEnumArrayTag.instance(KMType.PURPOSE, byteBlob);

    KMArray.cast(arrPtr).add(tagIndex++, purpose);
    // Algorithm - EC
    KMArray.cast(arrPtr).add(tagIndex++, KMEnumTag.instance(KMType.ALGORITHM, KMType.EC));
    KMArray.cast(arrPtr).add(tagIndex++, keySize);
    KMArray.cast(arrPtr).add(tagIndex++, digest);
    // Curve - P256
    KMArray.cast(arrPtr).add(tagIndex++, KMEnumTag.instance(KMType.ECCURVE, KMType.P_256));
    // No Authentication is required to use this key.
    KMArray.cast(arrPtr).add(tagIndex, KMBoolTag.instance(KMType.NO_AUTH_REQUIRED));
    return KMKeyParameters.instance(arrPtr);
  }

  private short rkpHmacSign(
      byte[] data, short dataStart, short dataLength, byte[] signature, short signatureStart) {
    short result =
        seProvider.hmacSign(
            storeDataInst.getRkpMacKey(), data, dataStart, dataLength, signature, signatureStart);
    return result;
  }
}
