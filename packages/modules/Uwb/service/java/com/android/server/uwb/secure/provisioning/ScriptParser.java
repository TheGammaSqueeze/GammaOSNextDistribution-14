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

package com.android.server.uwb.secure.provisioning;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.uwb.util.ObjectIdentifier;

import com.google.common.collect.ImmutableSet;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.Store;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.MajorType;
import co.nstant.in.cbor.model.UnsignedInteger;

class ScriptParser {
    private static final String LOG_TAG = "ScriptParser";

    private static final int VALID_DATA_ITEM_SIZE_2 = 2;
    private static final int VALID_DATA_ITEM_SIZE_3 = 3; // contains ADF OID
    private static final int VERSION_INDEX = 0;
    private static final int APDUS_INDEX = 1;
    private static final int ADF_OID_INDEX = 2;
    private static final String FIELD_VERSION = "ver";
    private static final String FIELD_APDUS = "APDUs";
    private static final String FIELD_ADF_OID = "adf_oid";
    private static final String SUB_FIELD_APDU = "APDU";

    private ScriptParser() {}

    /**
     * Verify the digital signature and parse the ADF provisioning script.
     * @param signedScript The CMS (PKCS#7) signed data along with script encapsulated
     * @return the ScriptContent if the signedScript is verified and parsed successfully,
     * empty otherwise.
     */
    @NonNull
    static ScriptContent parseSignedScript(@NonNull byte[] signedScript)
            throws ProvisioningException {
        if (signedScript == null || signedScript.length == 0) {
            throw new ProvisioningException("No script content.");
        }
        Optional<byte[]> script = verifyAndExtractScript(signedScript);
        return parseScript(script.get());
    }

    private static Optional<byte[]> verifyAndExtractScript(byte[] signedScript)
            throws ProvisioningException {
        try {
            CMSSignedData cmsSignedData = new CMSSignedData(signedScript);
            boolean verified = false;
            Store certStore = cmsSignedData.getCertificates();
            ImmutableSet<X509Certificate> untrustedCerts = getAllProvidedCerts(certStore);

            SignerInformationStore signers = cmsSignedData.getSignerInfos();
            Iterator iterator = signers.getSigners().iterator();

            while (iterator.hasNext()) {
                SignerInformation signer = (SignerInformation) iterator.next();
                Collection<?> certCollection =
                        (Collection<?>) certStore.getMatches(signer.getSID());
                if (!certCollection.isEmpty()) {
                    X509CertificateHolder certHolder =
                            (X509CertificateHolder) certCollection.iterator().next();
                    CertificateFactory cFact = CertificateFactory.getInstance("X.509");
                    X509Certificate leafCert = (X509Certificate) cFact.generateCertificate(
                            new ByteArrayInputStream(certHolder.getEncoded()));
                    if (!verifyCertAgainstTrustedCas(leafCert, untrustedCerts)) {
                        // The cert is not trusted, try next signer.
                        continue;
                    }

                    if (signer.verify(
                            new JcaSimpleSignerInfoVerifierBuilder()
                                    .setProvider("BC").build(certHolder))) {
                        verified = true;
                        break;
                    }
                }
            }

            if (verified) {
                return Optional.of((byte[]) cmsSignedData.getSignedContent().getContent());
            }
        } catch (IOException | CMSException | OperatorCreationException | CertificateException e) {
            throw new ProvisioningException("Invalid Input", e);
        }
        throw new ProvisioningException("the content cannot be trusted.");
    }

    private static ImmutableSet<X509Certificate> getAllProvidedCerts(@NonNull Store certStore) {
        ImmutableSet.Builder<X509Certificate> builder = ImmutableSet.builder();
        Collection<X509CertificateHolder> certHolders = certStore.getMatches(null);
        for (X509CertificateHolder holder : certHolders) {
            try {
                CertificateFactory cFact = CertificateFactory.getInstance("X.509");
                X509Certificate x509Certificate = (X509Certificate) cFact.generateCertificate(
                        new ByteArrayInputStream(holder.getEncoded()));
                builder.add(x509Certificate);
            } catch (IOException | CertificateException e) {
                // ignore, get next
            }
        }
        return builder.build();
    }

    private static boolean verifyCertAgainstTrustedCas(
            @NonNull X509Certificate leafCert,
            @NonNull ImmutableSet<X509Certificate> untrustedCerts) {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidCAStore");
            ks.load(null, null);

            X509CertSelector selector = new X509CertSelector();
            selector.setCertificate(leafCert);
            CertStore certStore = CertStore.getInstance(
                    "Collection", new CollectionCertStoreParameters(untrustedCerts));
            PKIXBuilderParameters parameters = new PKIXBuilderParameters(ks, selector);
            parameters.addCertStore(certStore);
            parameters.setRevocationEnabled(false);
            CertPathBuilder certPathBuilder = CertPathBuilder.getInstance("PKIX", "BC");
            certPathBuilder.build(parameters);

        } catch (GeneralSecurityException | IOException e) {
            // failed to verify the certificate.
            return false;
        }
        return true;
    }

    /**
     * Parses the CBOR(RFC 7949) encoded provisioning script.
     * @param cborEncodedData CBOR encoded script.
     * @return empty if the script is not encoded as correct CBOR, otherwise return the content.
     * @throws ProvisioningException The script is not encoded correctly.
     */
    @VisibleForTesting
    @NonNull
    static ScriptContent parseScript(@NonNull byte[] cborEncodedData)
            throws ProvisioningException {
        try {
            List<DataItem> dataItems = CborDecoder.decode(cborEncodedData);
            if (dataItems.size() != VALID_DATA_ITEM_SIZE_2
                    && dataItems.size() != VALID_DATA_ITEM_SIZE_3) {
                throw new ProvisioningException(dataItems.size() + " The script only allows "
                        + VALID_DATA_ITEM_SIZE_2 + " or " + VALID_DATA_ITEM_SIZE_3 + "data items");
            }
            DataItem versionDataItem = dataItems.get(VERSION_INDEX);
            if (!checkType(versionDataItem, MajorType.UNSIGNED_INTEGER, FIELD_VERSION)) {
                throw new ProvisioningException("the data type is not correct for version");
            }
            int version = ((UnsignedInteger) versionDataItem).getValue().intValue();
            int minorVersion = version & 0xFF;
            int majorVersion = version >> 8 & 0xFF;

            DataItem apdusDataItem = dataItems.get(APDUS_INDEX);
            if (!checkType(apdusDataItem, MajorType.ARRAY, FIELD_APDUS)) {
                throw new ProvisioningException("the data type is not correct for APDUs");
            }
            List<byte[]> apdus = new ArrayList<>();
            List<DataItem> apduDataItemList = ((Array) apdusDataItem).getDataItems();
            for (DataItem apduDataItem : apduDataItemList) {
                if (!checkType(apduDataItem, MajorType.BYTE_STRING, SUB_FIELD_APDU)) {
                    throw new ProvisioningException("the data type is not correct for APDU");
                }
                apdus.add(((ByteString) apduDataItem).getBytes());
            }
            Optional<ObjectIdentifier> adfOid = Optional.empty();
            if (dataItems.size() == VALID_DATA_ITEM_SIZE_3) {
                DataItem adfOidDataItem = dataItems.get(ADF_OID_INDEX);
                if (!checkType(adfOidDataItem, MajorType.BYTE_STRING, FIELD_ADF_OID)) {
                    throw new ProvisioningException("the data type is not correct for ADF_OID");
                }
                adfOid = Optional.of(ObjectIdentifier.fromBytes(
                        ((ByteString) adfOidDataItem).getBytes()));
            }
            return new ScriptContent(majorVersion, minorVersion, apdus, adfOid);

        } catch (CborException e) {
            throw new ProvisioningException("the script is not correct CBOR encoded.", e);
        }
    }

    private static boolean checkType(DataItem item, MajorType majorType, String field) {
        if (item.getMajorType() != majorType) {
            logw("Wrong CBOR type for field: " + field
                    + ". Expected " + majorType.name()
                    + ", actual: " + item.getMajorType().name());
            return false;
        }

        return true;
    }

    private static void logw(String dbgMsg) {
        android.util.Log.w(LOG_TAG, dbgMsg);
    }

    static class ScriptContent {
        final int mMajorVersion;
        final int mMinorVersion;
        final List<byte[]> mProvisioningApdus;
        final Optional<ObjectIdentifier> mAdfOid;

        @VisibleForTesting
        ScriptContent(int majorVersion, int minorVersion,
                List<byte[]> provisioningApdus, Optional<ObjectIdentifier> adfOid) {
            this.mMajorVersion = majorVersion;
            this.mMinorVersion = minorVersion;
            this.mProvisioningApdus = provisioningApdus;
            this.mAdfOid = adfOid;
        }
    }
}
