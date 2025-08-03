/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.nearby.presence;

import static android.nearby.BroadcastRequest.PRESENCE_VERSION_V1;

import static com.android.server.nearby.NearbyService.TAG;
import static com.android.server.nearby.presence.EncryptionInfo.ENCRYPTION_INFO_LENGTH;
import static com.android.server.nearby.presence.PresenceConstants.PRESENCE_UUID_BYTES;

import android.annotation.Nullable;
import android.nearby.BroadcastRequest.BroadcastVersion;
import android.nearby.DataElement;
import android.nearby.DataElement.DataType;
import android.nearby.PresenceBroadcastRequest;
import android.nearby.PresenceCredential;
import android.nearby.PublicCredential;
import android.util.Log;

import com.android.server.nearby.util.ArrayUtils;
import com.android.server.nearby.util.encryption.Cryptor;
import com.android.server.nearby.util.encryption.CryptorMicImp;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A Nearby Presence advertisement to be advertised on BT5.0 devices.
 *
 * <p>Serializable between Java object and bytes formats. Java object is used at the upper scanning
 * and advertising interface as an abstraction of the actual bytes. Bytes format is used at the
 * underlying BLE and mDNS stacks, which do necessary slicing and merging based on advertising
 * capacities.
 *
 * The extended advertisement is defined in the format below:
 * Header (1 byte) | salt (1+2 bytes) | Identity + filter (2+16 bytes)
 * | repeated DE fields (various bytes)
 * The header contains:
 * version (3 bits) | 5 bit reserved for future use (RFU)
 */
public class ExtendedAdvertisement extends Advertisement {

    public static final int SALT_DATA_LENGTH = 2;
    static final int HEADER_LENGTH = 1;

    static final int IDENTITY_DATA_LENGTH = 16;
    // Identity Index is always 2 .
    // 0 is reserved, 1 is Salt or Credential Element.
    private static final int CIPHER_START_INDEX = 2;
    private final List<DataElement> mDataElements;
    private final byte[] mKeySeed;

    private final byte[] mData;

    private ExtendedAdvertisement(
            @PresenceCredential.IdentityType int identityType,
            byte[] identity,
            byte[] salt,
            byte[] keySeed,
            List<Integer> actions,
            List<DataElement> dataElements) {
        this.mVersion = PRESENCE_VERSION_V1;
        this.mIdentityType = identityType;
        this.mIdentity = identity;
        this.mSalt = salt;
        this.mKeySeed = keySeed;
        this.mDataElements = dataElements;
        this.mActions = actions;
        mData = toBytesInternal();
    }

    /**
     * Creates an {@link ExtendedAdvertisement} from a Presence Broadcast Request.
     *
     * @return {@link ExtendedAdvertisement} object. {@code null} when the request is illegal.
     */
    @Nullable
    public static ExtendedAdvertisement createFromRequest(PresenceBroadcastRequest request) {
        if (request.getVersion() != PRESENCE_VERSION_V1) {
            Log.v(TAG, "ExtendedAdvertisement only supports V1 now.");
            return null;
        }

        byte[] salt = request.getSalt();
        if (salt.length != SALT_DATA_LENGTH && salt.length != ENCRYPTION_INFO_LENGTH - 1) {
            Log.v(TAG, "Salt does not match correct length");
            return null;
        }

        byte[] identity = request.getCredential().getMetadataEncryptionKey();
        byte[] authenticityKey = request.getCredential().getAuthenticityKey();
        if (identity.length != IDENTITY_DATA_LENGTH) {
            Log.v(TAG, "Identity does not match correct length");
            return null;
        }

        List<Integer> actions = request.getActions();
        List<DataElement> dataElements = request.getExtendedProperties();
        // DataElements should include actions.
        for (int action : actions) {
            dataElements.add(
                    new DataElement(DataType.ACTION, new byte[]{(byte) action}));
        }
        return new ExtendedAdvertisement(
                request.getCredential().getIdentityType(),
                identity,
                salt,
                authenticityKey,
                actions,
                dataElements);
    }

    /**
     * Deserialize from bytes into an {@link ExtendedAdvertisement} object.
     * Return {@code null} when there is an error in parsing.
     */
    @Nullable
    public static ExtendedAdvertisement fromBytes(byte[] bytes, PublicCredential sharedCredential) {
        @BroadcastVersion
        int version = ExtendedAdvertisementUtils.getVersion(bytes);
        if (version != PRESENCE_VERSION_V1) {
            Log.v(TAG, "ExtendedAdvertisement is used in V1 only and version is " + version);
            return null;
        }

        byte[] keySeed = sharedCredential.getAuthenticityKey();
        byte[] metadataEncryptionKeyUnsignedAdvTag = sharedCredential.getEncryptedMetadataKeyTag();
        if (keySeed == null || metadataEncryptionKeyUnsignedAdvTag == null) {
            return null;
        }

        int index = 0;
        // Header
        byte[] header = new byte[]{bytes[index]};
        index += HEADER_LENGTH;
        // Section header
        byte[] sectionHeader = new byte[]{bytes[index]};
        index += HEADER_LENGTH;
        // Salt or Encryption Info
        byte[] firstHeaderArray = ExtendedAdvertisementUtils.getDataElementHeader(bytes, index);
        DataElementHeader firstHeader = DataElementHeader.fromBytes(version, firstHeaderArray);
        if (firstHeader == null) {
            Log.v(TAG, "Cannot find salt.");
            return null;
        }
        @DataType int firstType = firstHeader.getDataType();
        if (firstType != DataType.SALT && firstType != DataType.ENCRYPTION_INFO) {
            Log.v(TAG, "First data element has to be Salt or Encryption Info.");
            return null;
        }
        index += firstHeaderArray.length;
        byte[] firstDeBytes = new byte[firstHeader.getDataLength()];
        for (int i = 0; i < firstHeader.getDataLength(); i++) {
            firstDeBytes[i] = bytes[index++];
        }
        byte[] nonce = getNonce(firstType, firstDeBytes);
        if (nonce == null) {
            return null;
        }
        byte[] saltBytes = firstType == DataType.SALT
                ? firstDeBytes : (new EncryptionInfo(firstDeBytes)).getSalt();

        // Identity header
        byte[] identityHeaderArray = ExtendedAdvertisementUtils.getDataElementHeader(bytes, index);
        DataElementHeader identityHeader =
                DataElementHeader.fromBytes(version, identityHeaderArray);
        if (identityHeader == null || identityHeader.getDataLength() != IDENTITY_DATA_LENGTH) {
            Log.v(TAG, "The second element has to be a 16-bytes identity.");
            return null;
        }
        index += identityHeaderArray.length;
        @PresenceCredential.IdentityType int identityType =
                toPresenceCredentialIdentityType(identityHeader.getDataType());
        if (identityType != PresenceCredential.IDENTITY_TYPE_PRIVATE
                && identityType != PresenceCredential.IDENTITY_TYPE_TRUSTED) {
            Log.v(TAG, "Only supports encrypted advertisement.");
            return null;
        }
        // Ciphertext
        Cryptor cryptor = CryptorMicImp.getInstance();
        byte[] ciphertext = new byte[bytes.length - index - cryptor.getSignatureLength()];
        System.arraycopy(bytes, index, ciphertext, 0, ciphertext.length);
        byte[] plaintext = cryptor.decrypt(ciphertext, nonce, keySeed);
        if (plaintext == null) {
            return null;
        }

        // Verification
        // Verify the computed metadata encryption key tag
        // First 16 bytes is metadata encryption key data
        byte[] metadataEncryptionKey = new byte[IDENTITY_DATA_LENGTH];
        System.arraycopy(plaintext, 0, metadataEncryptionKey, 0, IDENTITY_DATA_LENGTH);
        // Verify metadata encryption key tag
        byte[] computedMetadataEncryptionKeyTag =
                CryptorMicImp.generateMetadataEncryptionKeyTag(metadataEncryptionKey,
                        keySeed);
        if (!Arrays.equals(computedMetadataEncryptionKeyTag, metadataEncryptionKeyUnsignedAdvTag)) {
            Log.w(TAG,
                    "The calculated metadata encryption key tag is different from the metadata "
                            + "encryption key unsigned adv tag in the SharedCredential.");
            return null;
        }
        // Verify the computed HMAC tag is equal to HMAC tag in advertisement
        byte[] expectedHmacTag = new byte[cryptor.getSignatureLength()];
        System.arraycopy(
                bytes, bytes.length - cryptor.getSignatureLength(),
                expectedHmacTag, 0, cryptor.getSignatureLength());
        byte[] micInput =  ArrayUtils.concatByteArrays(
                PRESENCE_UUID_BYTES, header, sectionHeader,
                firstHeaderArray, firstDeBytes,
                nonce, identityHeaderArray, ciphertext);
        if (!cryptor.verify(micInput, keySeed, expectedHmacTag)) {
            Log.e(TAG, "HMAC tag not match.");
            return null;
        }

        byte[] otherDataElements = new byte[plaintext.length - IDENTITY_DATA_LENGTH];
        System.arraycopy(plaintext, IDENTITY_DATA_LENGTH,
                otherDataElements, 0, otherDataElements.length);
        List<DataElement> dataElements = getDataElementsFromBytes(version, otherDataElements);
        if (dataElements.isEmpty()) {
            return null;
        }
        List<Integer> actions = getActionsFromDataElements(dataElements);
        if (actions == null) {
            return null;
        }
        return new ExtendedAdvertisement(identityType, metadataEncryptionKey, saltBytes, keySeed,
                actions, dataElements);
    }

    @PresenceCredential.IdentityType
    private static int toPresenceCredentialIdentityType(@DataType int type) {
        switch (type) {
            case DataType.PRIVATE_IDENTITY:
                return PresenceCredential.IDENTITY_TYPE_PRIVATE;
            case DataType.PROVISIONED_IDENTITY:
                return PresenceCredential.IDENTITY_TYPE_PROVISIONED;
            case DataType.TRUSTED_IDENTITY:
                return PresenceCredential.IDENTITY_TYPE_TRUSTED;
            case DataType.PUBLIC_IDENTITY:
            default:
                return PresenceCredential.IDENTITY_TYPE_UNKNOWN;
        }
    }

    @DataType
    private static int toDataType(@PresenceCredential.IdentityType int identityType) {
        switch (identityType) {
            case PresenceCredential.IDENTITY_TYPE_PRIVATE:
                return DataType.PRIVATE_IDENTITY;
            case PresenceCredential.IDENTITY_TYPE_PROVISIONED:
                return DataType.PROVISIONED_IDENTITY;
            case PresenceCredential.IDENTITY_TYPE_TRUSTED:
                return DataType.TRUSTED_IDENTITY;
            case PresenceCredential.IDENTITY_TYPE_UNKNOWN:
            default:
                return DataType.PUBLIC_IDENTITY;
        }
    }

    /**
     * Returns {@code true} if the given {@link DataType} is salt, or one of the
     * identities. Identities should be able to convert to {@link PresenceCredential.IdentityType}s.
     */
    private static boolean isSaltOrIdentity(@DataType int type) {
        return type == DataType.SALT || type == DataType.ENCRYPTION_INFO
                || type == DataType.PRIVATE_IDENTITY
                || type == DataType.TRUSTED_IDENTITY
                || type == DataType.PROVISIONED_IDENTITY
                || type == DataType.PUBLIC_IDENTITY;
    }

    /** Serialize an {@link ExtendedAdvertisement} object into bytes with {@link DataElement}s */
    @Nullable
    public byte[] toBytes() {
        return mData.clone();
    }

    /** Serialize an {@link ExtendedAdvertisement} object into bytes with {@link DataElement}s */
    @Nullable
    public byte[] toBytesInternal() {
        int sectionLength = 0;
        // Salt
        DataElement saltDe;
        byte[] nonce;
        try {
            switch (mSalt.length) {
                case SALT_DATA_LENGTH:
                    saltDe = new DataElement(DataType.SALT, mSalt);
                    nonce = CryptorMicImp.generateAdvNonce(mSalt);
                    break;
                case ENCRYPTION_INFO_LENGTH - 1:
                    saltDe = new DataElement(DataType.ENCRYPTION_INFO,
                            EncryptionInfo.toByte(EncryptionInfo.EncodingScheme.MIC, mSalt));
                    nonce = CryptorMicImp.generateAdvNonce(mSalt, CIPHER_START_INDEX);
                    break;
                default:
                    Log.w(TAG, "Invalid salt size.");
                    return null;
            }
        } catch (GeneralSecurityException e) {
            Log.w(TAG, "Failed to generate the IV for encryption.", e);
            return null;
        }

        byte[] saltOrEncryptionInfoBytes =
                ExtendedAdvertisementUtils.convertDataElementToBytes(saltDe);
        sectionLength += saltOrEncryptionInfoBytes.length;
        // 16 bytes encrypted identity
        @DataType int identityDataType = toDataType(getIdentityType());
        byte[] identityHeaderBytes = new DataElementHeader(PRESENCE_VERSION_V1,
                identityDataType, mIdentity.length).toBytes();
        sectionLength += identityHeaderBytes.length;
        final List<DataElement> dataElementList = getDataElements();
        byte[] ciphertext = getCiphertext(nonce, dataElementList);
        if (ciphertext == null) {
            return null;
        }
        sectionLength += ciphertext.length;
        // mic
        sectionLength += CryptorMicImp.MIC_LENGTH;
        mLength = sectionLength;
        // header
        byte header = ExtendedAdvertisementUtils.constructHeader(getVersion());
        mLength += HEADER_LENGTH;
        // section header
        if (sectionLength > 255) {
            Log.e(TAG, "A section should be shorter than 255 bytes.");
            return null;
        }
        byte sectionHeader = (byte) sectionLength;
        mLength += HEADER_LENGTH;

        // generates mic
        ByteBuffer micInputBuffer = ByteBuffer.allocate(
                mLength + PRESENCE_UUID_BYTES.length + nonce.length - CryptorMicImp.MIC_LENGTH);
        micInputBuffer.put(PRESENCE_UUID_BYTES);
        micInputBuffer.put(header);
        micInputBuffer.put(sectionHeader);
        micInputBuffer.put(saltOrEncryptionInfoBytes);
        micInputBuffer.put(nonce);
        micInputBuffer.put(identityHeaderBytes);
        micInputBuffer.put(ciphertext);
        byte[] micInput = micInputBuffer.array();
        byte[] mic = CryptorMicImp.getInstance().sign(micInput, mKeySeed);
        if (mic == null) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.allocate(mLength);
        buffer.put(header);
        buffer.put(sectionHeader);
        buffer.put(saltOrEncryptionInfoBytes);
        buffer.put(identityHeaderBytes);
        buffer.put(ciphertext);
        buffer.put(mic);
        return buffer.array();
    }

    /** Returns the {@link DataElement}s in the advertisement. */
    public List<DataElement> getDataElements() {
        return new ArrayList<>(mDataElements);
    }

    /** Returns the {@link DataElement}s in the advertisement according to the key. */
    public List<DataElement> getDataElements(@DataType int key) {
        List<DataElement> res = new ArrayList<>();
        for (DataElement dataElement : mDataElements) {
            if (key == dataElement.getKey()) {
                res.add(dataElement);
            }
        }
        return res;
    }

    @Override
    public String toString() {
        return String.format(
                "ExtendedAdvertisement:"
                        + "<VERSION: %s, length: %s, dataElementCount: %s, identityType: %s,"
                        + " identity: %s, salt: %s, actions: %s>",
                getVersion(),
                getLength(),
                getDataElements().size(),
                getIdentityType(),
                Arrays.toString(getIdentity()),
                Arrays.toString(getSalt()),
                getActions());
    }

    @Nullable
    private byte[] getCiphertext(byte[] nonce, List<DataElement> dataElements) {
        Cryptor cryptor = CryptorMicImp.getInstance();
        byte[] rawDeBytes = mIdentity;
        for (DataElement dataElement : dataElements) {
            rawDeBytes = ArrayUtils.concatByteArrays(rawDeBytes,
                    ExtendedAdvertisementUtils.convertDataElementToBytes(dataElement));
        }
        return cryptor.encrypt(rawDeBytes, nonce, mKeySeed);
    }

    private static List<DataElement> getDataElementsFromBytes(
            @BroadcastVersion int version, byte[] bytes) {
        List<DataElement> res = new ArrayList<>();
        if (ArrayUtils.isEmpty(bytes)) {
            return res;
        }
        int index = 0;
        while (index < bytes.length) {
            byte[] deHeaderArray = ExtendedAdvertisementUtils
                    .getDataElementHeader(bytes, index);
            DataElementHeader deHeader = DataElementHeader.fromBytes(version, deHeaderArray);
            index += deHeaderArray.length;
            @DataType int type = Objects.requireNonNull(deHeader).getDataType();
            if (isSaltOrIdentity(type)) {
                Log.v(TAG, "Type " + type + " is duplicated. There should be only one salt"
                        + " and one identity in the advertisement.");
                return new ArrayList<>();
            }
            byte[] deData = new byte[deHeader.getDataLength()];
            for (int i = 0; i < deHeader.getDataLength(); i++) {
                deData[i] = bytes[index++];
            }
            res.add(new DataElement(type, deData));
        }
        return res;
    }

    @Nullable
    private static byte[] getNonce(@DataType int type, byte[] data) {
        try {
            if (type == DataType.SALT) {
                if (data.length != SALT_DATA_LENGTH) {
                    Log.v(TAG, "Salt DataElement needs to be 2 bytes.");
                    return null;
                }
                return CryptorMicImp.generateAdvNonce(data);
            } else if (type == DataType.ENCRYPTION_INFO) {
                try {
                    EncryptionInfo info = new EncryptionInfo(data);
                    if (info.getEncodingScheme() != EncryptionInfo.EncodingScheme.MIC) {
                        Log.v(TAG, "Not support Signature yet.");
                        return null;
                    }
                    return CryptorMicImp.generateAdvNonce(info.getSalt(), CIPHER_START_INDEX);
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Salt DataElement needs to be 17 bytes.", e);
                    return null;
                }
            }
        }  catch (GeneralSecurityException e) {
            Log.w(TAG, "Failed to decrypt metadata encryption key.", e);
            return null;
        }
        return null;
    }

    @Nullable
    private static List<Integer> getActionsFromDataElements(List<DataElement> dataElements) {
        List<Integer> actions = new ArrayList<>();
        for (DataElement dataElement : dataElements) {
            if (dataElement.getKey() == DataElement.DataType.ACTION) {
                byte[] value = dataElement.getValue();
                if (value.length != 1) {
                    Log.w(TAG, "Action should be only 1 byte.");
                    return null;
                }
                actions.add(Byte.toUnsignedInt(value[0]));
            }
        }
        return actions;
    }
}
