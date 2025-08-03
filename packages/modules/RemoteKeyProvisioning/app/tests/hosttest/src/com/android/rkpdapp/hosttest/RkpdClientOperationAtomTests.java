/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.rkpdapp.hosttest;

import static com.google.common.truth.Truth.assertThat;

import com.android.os.rkpd.RkpdClientOperation;
import com.android.os.rkpd.RkpdClientOperation.Operation;
import com.android.os.rkpd.RkpdClientOperation.Result;
import com.android.os.rkpd.RkpdExtensionAtoms;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class RkpdClientOperationAtomTests extends AtomsHostTest {
    public RkpdClientOperationAtomTests() {
        super(RkpdExtensionAtoms.RKPD_CLIENT_OPERATION_FIELD_NUMBER);
    }

    @Test
    public void testGetKeySuccess() throws Exception {
        runIntegrationTest("testKeyCreationUsesRemotelyProvisionedCertificate",
                "KeystoreIntegrationTest");
        List<RkpdClientOperation> atoms = getAtoms(RkpdExtensionAtoms.rkpdClientOperation);
        assertThat(atoms).hasSize(2);
        verifyE2eTestAtom(atoms.get(0), Operation.OPERATION_GET_REGISTRATION,
                Result.RESULT_SUCCESS);
        verifyE2eTestAtom(atoms.get(1), Operation.OPERATION_GET_KEY, Result.RESULT_SUCCESS);
    }

    @Test
    public void testGetKeyWithResultUnknown() throws Exception {
        runKeyStoreIntegrationTest("testKeyCreationFailsWhenRkpFails");
        List<RkpdClientOperation> atoms = getAtoms(RkpdExtensionAtoms.rkpdClientOperation);
        assertThat(atoms).hasSize(2);
        verifyE2eTestAtom(atoms.get(0), Operation.OPERATION_GET_REGISTRATION,
                Result.RESULT_SUCCESS);
        verifyE2eTestAtom(atoms.get(1), Operation.OPERATION_GET_KEY, Result.RESULT_UNKNOWN);
    }

    @Test
    public void testGetKeyWithResultCanceled() throws Exception {
        runUnitTest("getKeyHandlesCancelBeforeProvisioning", "RegistrationBinderTest");
        List<RkpdClientOperation> atoms = getAtoms(RkpdExtensionAtoms.rkpdClientOperation);
        assertThat(atoms).hasSize(2);
        verifyUnitTestAtom(atoms.get(0), Operation.OPERATION_CANCEL_GET_KEY, Result.RESULT_SUCCESS);
        verifyUnitTestAtom(atoms.get(1), Operation.OPERATION_GET_KEY, Result.RESULT_CANCELED);
    }

    @Test
    public void testGetKeyWithErrorInternal() throws Exception {
        runUnitTest("getKeyInternalError", "RegistrationBinderTest");
        List<RkpdClientOperation> atoms = getAtoms(RkpdExtensionAtoms.rkpdClientOperation);
        assertThat(atoms).hasSize(1);
        verifyUnitTestAtom(atoms.get(0), Operation.OPERATION_GET_KEY, Result.RESULT_ERROR_INTERNAL);
    }

    @Test
    public void testGetKeyWithErrorPermanent() throws Exception {
        runKeyStoreIntegrationTest("testRetryNeverWhenDeviceNotRegistered");
        List<RkpdClientOperation> atoms = getAtoms(RkpdExtensionAtoms.rkpdClientOperation);
        assertThat(atoms).hasSize(2);
        verifyE2eTestAtom(atoms.get(0), Operation.OPERATION_GET_REGISTRATION,
                Result.RESULT_SUCCESS);
        verifyE2eTestAtom(atoms.get(1), Operation.OPERATION_GET_KEY, Result.RESULT_ERROR_PERMANENT);
    }

    @Test
    public void testGetKeyWithErrorPendingInternetConnectivity() throws Exception {
        runUnitTest("getKeyNoInternetConnectivity", "RegistrationBinderTest");
        List<RkpdClientOperation> atoms = getAtoms(RkpdExtensionAtoms.rkpdClientOperation);
        assertThat(atoms).hasSize(1);
        verifyUnitTestAtom(atoms.get(0), Operation.OPERATION_GET_KEY,
                Result.RESULT_ERROR_PENDING_INTERNET_CONNECTIVITY);
    }

    @Test
    public void testStoreUpgradedKeySuccess() throws Exception {
        runUnitTest("storeUpgradedKeyAsyncSuccess", "RegistrationBinderTest");
        List<RkpdClientOperation> atoms = getAtoms(RkpdExtensionAtoms.rkpdClientOperation);
        assertThat(atoms).hasSize(1);
        verifyUnitTestAtom(atoms.get(0), Operation.OPERATION_STORE_UPGRADED_KEY,
                Result.RESULT_SUCCESS);
    }

    @Test
    public void testStoreUpgradedKeyWithErrorKeyNotFound() throws Exception {
        runUnitTest("storeUpgradedKeyAsyncKeyNotFound", "RegistrationBinderTest");
        List<RkpdClientOperation> atoms = getAtoms(RkpdExtensionAtoms.rkpdClientOperation);
        assertThat(atoms).hasSize(1);
        verifyUnitTestAtom(atoms.get(0), Operation.OPERATION_STORE_UPGRADED_KEY,
                Result.RESULT_ERROR_KEY_NOT_FOUND);
    }

    @Test
    public void testStoreUpgradedKeyWithErrorInternal() throws Exception {
        runUnitTest("storeUpgradedKeyAsyncInternalError", "RegistrationBinderTest");
        List<RkpdClientOperation> atoms = getAtoms(RkpdExtensionAtoms.rkpdClientOperation);
        assertThat(atoms).hasSize(1);
        verifyUnitTestAtom(atoms.get(0), Operation.OPERATION_STORE_UPGRADED_KEY,
                Result.RESULT_ERROR_INTERNAL);
    }

    @Test
    public void testGetRegistrationSuccess() throws Exception {
        runUnitTest("getRegistrationSuccess", "RemoteProvisioningServiceTest");
        List<RkpdClientOperation> atoms = getAtoms(RkpdExtensionAtoms.rkpdClientOperation);
        assertThat(atoms).hasSize(1);
        verifyUnitTestAtom(atoms.get(0), Operation.OPERATION_GET_REGISTRATION,
                Result.RESULT_SUCCESS);
    }

    @Test
    public void testGetRegistrationWithErrorInvalidHal() throws Exception {
        runUnitTest("getRegistrationWithInvalidHalName", "RemoteProvisioningServiceTest");
        List<RkpdClientOperation> atoms = getAtoms(RkpdExtensionAtoms.rkpdClientOperation);
        assertThat(atoms).hasSize(1);
        verifyUnitTestAtom(atoms.get(0), Operation.OPERATION_GET_REGISTRATION,
                Result.RESULT_ERROR_INVALID_HAL);
    }

    @Test
    public void testGetRegistrationWhenUnsupported() throws Exception {
        runUnitTest("getRegistrationNoHostName", "RemoteProvisioningServiceTest");
        List<RkpdClientOperation> atoms = getAtoms(RkpdExtensionAtoms.rkpdClientOperation);
        assertThat(atoms).hasSize(1);
        verifyUnitTestAtom(atoms.get(0), Operation.OPERATION_GET_REGISTRATION,
                Result.RESULT_RKP_UNSUPPORTED);
    }

    private void runKeyStoreIntegrationTest(String testName) throws Exception {
        runIntegrationTest(testName, "KeystoreIntegrationTest");
    }

    private void verifyE2eTestAtom(RkpdClientOperation atom, Operation expectedOperation,
            Result expectedResult) {
        final int keystoreUid = 1017;
        assertThat(atom.getRemotelyProvisionedComponent()).isEqualTo(
                "android.hardware.security.keymint.IRemotelyProvisionedComponent/default");
        assertThat(atom.getOperation()).isEqualTo(expectedOperation);
        assertThat(atom.getResult()).isEqualTo(expectedResult);
        assertThat(atom.getOperationTimeMillis()).isAtLeast(0);
        assertThat(atom.getClientUid()).isEqualTo(keystoreUid);
    }

    private void verifyUnitTestAtom(RkpdClientOperation atom, Operation expectedOperation,
            Result expectedResult) {
        assertThat(atom.getOperation()).isEqualTo(expectedOperation);
        assertThat(atom.getResult()).isEqualTo(expectedResult);
        assertThat(atom.getOperationTimeMillis()).isAtLeast(0);
        // The IRPC component name and client UID vary from unit test to unit test, so we cannot
        // rely on stable values, unlike the integration test that works with real components.
    }
}
