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

package com.android.rkpdapp.interfaces;

import android.hardware.security.keymint.DeviceInfo;
import android.hardware.security.keymint.IRemotelyProvisionedComponent;
import android.hardware.security.keymint.MacedPublicKey;
import android.hardware.security.keymint.ProtectedData;
import android.hardware.security.keymint.RpcHardwareInfo;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.android.rkpdapp.GeekResponse;
import com.android.rkpdapp.RkpdException;
import com.android.rkpdapp.database.RkpKey;
import com.android.rkpdapp.metrics.ProvisioningAttempt;
import com.android.rkpdapp.utils.CborUtils;
import com.android.rkpdapp.utils.StopWatch;

import java.util.List;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.MajorType;
import co.nstant.in.cbor.model.Map;

/**
 * Provides convenience methods for interfacing with the IRemotelyProvisionedComponent
 * implementations. Since these APIs are internal only and subject to change, it is handy
 * to have an abstraction layer to reduce the impact of these changes on the app.
 */
public class SystemInterface {
    private static final String TAG = "RkpdSystemInterface";
    private final IRemotelyProvisionedComponent mBinder;
    private final String mServiceName;
    private final int mSupportedCurve;

    public SystemInterface(IRemotelyProvisionedComponent binder, String serviceName) {
        mServiceName = serviceName;
        mBinder = binder;
        try {
            mSupportedCurve = mBinder.getHardwareInfo().supportedEekCurve;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to call getHardwareInfo", e);
            throw e.rethrowAsRuntimeException();
        }
    }

    /**
     * @return the fully qualified name of the underlying IRemotelyProvisionedComponent
     */
    public String getServiceName() {
        return mServiceName;
    }

    /**
     * @return human readable string describing this object
     */
    public String toString() {
        return getClass().getName() + "{" + mServiceName + "}";
    }

    /**
     * Generates attestation keys pair through binder service.
     * Returns generated key in {@link RkpKey} format.
     */
    public RkpKey generateKey(ProvisioningAttempt metrics) throws CborException, RkpdException {
        MacedPublicKey macedPublicKey = new MacedPublicKey();
        try (StopWatch ignored = metrics.startBinderWait()) {
            byte[] privKey = mBinder.generateEcdsaP256KeyPair(false, macedPublicKey);
            return CborUtils.extractRkpKeyFromMacedKey(privKey, mServiceName, macedPublicKey);
        } catch (RemoteException e) {
            metrics.setStatus(ProvisioningAttempt.Status.GENERATE_KEYPAIR_FAILED);
            Log.e(TAG, "Failed to generate key.", e);
            throw e.rethrowAsRuntimeException();
        } catch (ServiceSpecificException e) {
            metrics.setStatus(ProvisioningAttempt.Status.GENERATE_KEYPAIR_FAILED);
            Log.e(TAG, "Failed to generate key. Failed with " + e.errorCode, e);
            throw e;
        } catch (CborException e) {
            metrics.setStatus(ProvisioningAttempt.Status.GENERATE_KEYPAIR_FAILED);
            throw e;
        }
    }

    /**
     * Sends a generateCsr request over the binder interface to generate the request to be sent to
     * Remote provisioning servers.
     * @param geekResponse Contains the challenge and GEEK chain for older implementations. Only has
     *                    challenge for the newer ones.
     * @param keysToSign array of keys to be signed.
     */
    public byte[] generateCsr(ProvisioningAttempt metrics, GeekResponse geekResponse,
            List<RkpKey> keysToSign) throws CborException, RkpdException {
        byte[] challenge = geekResponse.getChallenge();
        byte[] csrTag;
        MacedPublicKey[] macedKeysToSign = keysToSign.stream()
                .map(x -> {
                    MacedPublicKey key = new MacedPublicKey();
                    key.macedKey = x.getMacedPublicKey();
                    return key;
                }).toArray(MacedPublicKey[]::new);
        try (StopWatch ignored = metrics.startBinderWait()) {
            if (getVersion() < 3) {
                DeviceInfo deviceInfo = new DeviceInfo();
                ProtectedData protectedData = new ProtectedData();
                byte[] geekChain = geekResponse.getGeekChain(mSupportedCurve);
                csrTag = mBinder.generateCertificateRequest(false, macedKeysToSign, geekChain,
                        challenge, deviceInfo, protectedData);
                Array macedKeys = new Array();
                for (RkpKey key: keysToSign) {
                    macedKeys.add(key.getCoseKey());
                }
                try {
                    Array mac0Message = new Array()
                            .add(new ByteString(CborUtils.encodeCbor(
                                    CborUtils.makeProtectedHeaders())))
                            .add(new Map())
                            .add(new ByteString(CborUtils.encodeCbor(macedKeys)))
                            .add(new ByteString(csrTag));
                    return CborUtils.buildCertificateRequest(deviceInfo.deviceInfo,
                            challenge,
                            protectedData.protectedData,
                            CborUtils.encodeCbor(mac0Message),
                            CborUtils.buildUnverifiedDeviceInfo());
                } catch (CborException | RkpdException e) {
                    Log.e(TAG, "Failed to parse/build CBOR", e);
                    metrics.setStatus(ProvisioningAttempt.Status.GENERATE_CSR_FAILED);
                    throw e;
                }
            } else {
                byte[] csrBytes = mBinder.generateCertificateRequestV2(macedKeysToSign, challenge);
                Array array = (Array) CborUtils.decodeCbor(csrBytes, "CSR request",
                        MajorType.ARRAY);
                array.add(CborUtils.buildUnverifiedDeviceInfo());
                return CborUtils.encodeCbor(array);
            }
        } catch (RemoteException e) {
            metrics.setStatus(ProvisioningAttempt.Status.GENERATE_CSR_FAILED);
            Log.e(TAG, "Failed to generate CSR blob", e);
            throw e.rethrowAsRuntimeException();
        } catch (ServiceSpecificException ex) {
            metrics.setStatus(ProvisioningAttempt.Status.GENERATE_CSR_FAILED);
            Log.e(TAG, "Failed to generate CSR blob. Failed with " + ex.errorCode, ex);
            throw ex;
        } catch (CborException e) {
            metrics.setStatus(ProvisioningAttempt.Status.GENERATE_CSR_FAILED);
            throw e;
        }
    }

    /**
     * Gets the implemented version for IRemotelyProvisionedComponent.
     */
    public int getVersion() throws RemoteException {
        return mBinder.getHardwareInfo().versionNumber;
    }

    /**
     * Gets the maximum size of a batch that's supported by the underlying implementation. Since
     * memory may be tightly constrained in the trusted runtime, the maximum batch size may be
     * quite small. The HAL mandates an absolute minimum of 20, which is enforced via VTS.
     */
    public int getBatchSize() throws RemoteException {
        final int maxBatchSize = 512;

        int batchSize = mBinder.getHardwareInfo().supportedNumKeysInCsr;

        if (batchSize < RpcHardwareInfo.MIN_SUPPORTED_NUM_KEYS_IN_CSR) {
            Log.w(TAG, "HAL returned a batch size that's too small (" + batchSize
                    + "), defaulting to " + RpcHardwareInfo.MIN_SUPPORTED_NUM_KEYS_IN_CSR);
            return RpcHardwareInfo.MIN_SUPPORTED_NUM_KEYS_IN_CSR;
        }

        if (batchSize > maxBatchSize) {
            Log.w(TAG, "HAL returned a batch size that's too large (" + batchSize
                    + "), defaulting to " + maxBatchSize);
            return maxBatchSize;
        }

        return batchSize;
    }

}
