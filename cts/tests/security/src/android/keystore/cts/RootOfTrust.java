/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.keystore.cts;

import com.google.common.io.BaseEncoding;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Sequence;

import java.security.cert.CertificateParsingException;

public class RootOfTrust {
    private static final int VERIFIED_BOOT_KEY_INDEX = 0;
    private static final int DEVICE_LOCKED_INDEX = 1;
    private static final int VERIFIED_BOOT_STATE_INDEX = 2;
    private static final int VERIFIED_BOOT_HASH_INDEX = 3;

    public static final int KM_VERIFIED_BOOT_VERIFIED = 0;
    public static final int KM_VERIFIED_BOOT_SELF_SIGNED = 1;
    public static final int KM_VERIFIED_BOOT_UNVERIFIED = 2;
    public static final int KM_VERIFIED_BOOT_FAILED = 3;

    private final byte[] mVerifiedBootKey;
    private final boolean mDeviceLocked;
    private final int mVerifiedBootState;
    private final byte[] mVerifiedBootHash;

    public RootOfTrust(ASN1Encodable asn1Encodable, int attestationVersion) throws CertificateParsingException {
        this(asn1Encodable, attestationVersion, true);
    }

    public RootOfTrust(ASN1Encodable asn1Encodable, int attestationVersion, boolean strictParsing)
            throws CertificateParsingException {
        if (!(asn1Encodable instanceof ASN1Sequence)) {
            throw new CertificateParsingException("Expected sequence for root of trust, found "
                + asn1Encodable.getClass().getName());
        }

        ASN1Sequence sequence = (ASN1Sequence) asn1Encodable;
        mVerifiedBootKey =
            Asn1Utils.getByteArrayFromAsn1(sequence.getObjectAt(VERIFIED_BOOT_KEY_INDEX));
        mDeviceLocked = Asn1Utils.getBooleanFromAsn1(
            sequence.getObjectAt(DEVICE_LOCKED_INDEX), strictParsing);
        mVerifiedBootState =
            Asn1Utils.getIntegerFromAsn1(sequence.getObjectAt(VERIFIED_BOOT_STATE_INDEX));
        // Verified boot hash was presented after attestation version v3.0
        if (attestationVersion >= 3) {
            mVerifiedBootHash =
                Asn1Utils.getByteArrayFromAsn1(sequence.getObjectAt(VERIFIED_BOOT_HASH_INDEX));
        } else {
            mVerifiedBootHash = null;
        }
    }

    RootOfTrust(byte[] verifiedBootKey, boolean deviceLocked, int verifiedBootState,
                byte[] verifiedBootHash) {
        this.mVerifiedBootKey = verifiedBootKey;
        this.mDeviceLocked = deviceLocked;
        this.mVerifiedBootState = verifiedBootState;
        this.mVerifiedBootHash = verifiedBootHash;
    }

    public static String verifiedBootStateToString(int verifiedBootState) {
        switch (verifiedBootState) {
            case KM_VERIFIED_BOOT_VERIFIED:
                return "Verified";
            case KM_VERIFIED_BOOT_SELF_SIGNED:
                return "Self-signed";
            case KM_VERIFIED_BOOT_UNVERIFIED:
                return "Unverified";
            case KM_VERIFIED_BOOT_FAILED:
                return "Failed";
            default:
                return "Unknown";
        }
    }

    public byte[] getVerifiedBootKey() {
        return mVerifiedBootKey;
    }

    public boolean isDeviceLocked() {
        return mDeviceLocked;
    }

    public int getVerifiedBootState() {
        return mVerifiedBootState;
    }

    public byte[] getVerifiedBootHash() {
        return mVerifiedBootHash;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("\nVerified boot Key: ")
                .append(mVerifiedBootKey != null
                            ? BaseEncoding.base64().encode(mVerifiedBootKey)
                            : "null")
                .append("\nDevice locked: ")
                .append(mDeviceLocked)
                .append("\nVerified boot state: ")
                .append(verifiedBootStateToString(mVerifiedBootState))
                .append("\nVerified boot hash: ")
                .append(mVerifiedBootHash != null
                            ? BaseEncoding.base64().encode(mVerifiedBootHash)
                            : "null")
                .toString();
    }

    public static class Builder {
        private byte[] mVerifiedBootKey;
        private boolean mDeviceLocked = false;
        private int mVerifiedBootState = -1;
        private byte[] mVerifiedBootHash;

        public Builder setVerifiedBootKey(byte[] verifiedBootKey) {
            this.mVerifiedBootKey = verifiedBootKey;
            return this;
        }
        public Builder setDeviceLocked(boolean deviceLocked) {
            this.mDeviceLocked = deviceLocked;
            return this;
        }
        public Builder setVerifiedBootState(int verifiedBootState) {
            this.mVerifiedBootState = verifiedBootState;
            return this;
        }
        public Builder setVerifiedBootHash(byte[] verifiedBootHash) {
            this.mVerifiedBootHash = verifiedBootHash;
            return this;
        }
        public RootOfTrust build() {
            return new RootOfTrust(mVerifiedBootKey, mDeviceLocked,
                                   mVerifiedBootState, mVerifiedBootHash);
        }
    }
}
