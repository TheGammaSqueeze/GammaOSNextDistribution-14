/*
 * Copyright 2019 The Android Open Source Project
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

package android.security.identity.cts;

import android.security.identity.ResultData;
import android.security.identity.IdentityCredentialStore;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.FeatureInfo;
import android.os.SystemProperties;
import android.security.keystore.KeyProperties;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Formatter;
import java.util.Map;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.AbstractFloat;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.DoublePrecisionFloat;
import co.nstant.in.cbor.model.MajorType;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.SimpleValueType;
import co.nstant.in.cbor.model.SpecialType;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;

class TestUtil {
    private static final String TAG = "Util";

    // Returns 0 if not implemented. Otherwise returns the feature version.
    //
    static int getFeatureVersion() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        PackageManager pm = appContext.getPackageManager();

        int featureVersionFromPm = 0;
        if (pm.hasSystemFeature(PackageManager.FEATURE_IDENTITY_CREDENTIAL_HARDWARE)) {
            FeatureInfo info = null;
            FeatureInfo[] infos = pm.getSystemAvailableFeatures();
            for (int n = 0; n < infos.length; n++) {
                FeatureInfo i = infos[n];
                if (i.name.equals(PackageManager.FEATURE_IDENTITY_CREDENTIAL_HARDWARE)) {
                    info = i;
                    break;
                }
            }
            if (info != null) {
                featureVersionFromPm = info.version;
            }
        }

        // Use of the system feature is not required since Android 12. So for Android 11
        // return 202009 which is the feature version shipped with Android 11.
        if (featureVersionFromPm == 0) {
            IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);
            if (store != null) {
                featureVersionFromPm = 202009;
            }
        }

        return featureVersionFromPm;
    }

    // Returns true if, and only if, the Identity Credential HAL (and credstore) is implemented
    // on the device under test.
    static boolean isHalImplemented() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);
        if (store != null) {
            return true;
        }
        return false;
    }

    // Returns true if, and only if, the Direct Access Identity Credential HAL (and credstore) is
    // implemented on the device under test.
    static boolean isDirectAccessHalImplemented() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getDirectAccessInstance(appContext);
        if (store != null) {
            return true;
        }
        return false;
    }

    // Returns true if the device supports secure lock screen.
    static boolean isLockScreenSupported() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        PackageManager pm = appContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN);
    }
}
