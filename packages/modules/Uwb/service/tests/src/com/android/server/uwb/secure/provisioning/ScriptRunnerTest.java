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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.server.uwb.secure.SecureElementChannel;
import com.android.server.uwb.secure.iso7816.CommandApdu;
import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.util.DataTypeConversionUtil;
import com.android.server.uwb.util.ObjectIdentifier;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ScriptRunnerTest {
    private static final CommandApdu CREATE_ADF_CMD =
            CommandApdu.builder(0x84, 0xE0, 0x00, 0x00).build();
    private static final CommandApdu MANAGE_ADF_NOT_LAST_CMD =
            CommandApdu.builder(0xE0, 0xEA, 0x01, 0x00).build();
    private static final CommandApdu MANAGE_ADF_LAST_CMD =
            CommandApdu.builder(0xE0, 0xEA, 0x00, 0x00).build();
    private static final CommandApdu IMPORT_ADF_CMD =
            CommandApdu.builder(0x84, 0xEB, 0x00, 0x00).build();
    private static final CommandApdu DELETE_ADF_CMD =
            CommandApdu.builder(0x84, 0xE4, 0x00, 0x00).build();
    private static final CommandApdu NOT_ALLOWED_CMD =
            CommandApdu.builder(0x00, 0xE4, 0x00, 0x00).build();
    private static final ObjectIdentifier ADF_OID =
            ObjectIdentifier.fromBytes(new byte[] {(byte) 1});
    private static final UUID SERVICE_INSTANCE_ID = UUID.fromString("1-2-3-4-5");
    private static final ObjectIdentifier OID_IN_RESPONSE =
            ObjectIdentifier.fromBytes(new byte[] {(byte) 2});

    private ScriptRunner mUnderTest;

    @Mock
    private SecureElementChannel mSecureElementChannel;
    @Mock
    private ProvisioningManager.ProvisioningCallback mProvisioningCallback;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mUnderTest = new ScriptRunner(mSecureElementChannel);
    }

    private void successSetup() throws IOException {
        when(mSecureElementChannel.openChannel()).thenReturn(true);
        when(mSecureElementChannel.transmit(any(CommandApdu.class))).thenReturn(
                ResponseApdu.fromDataAndStatusWord(
                        DataTypeConversionUtil.hexStringToByteArray("060102"),
                        StatusWord.SW_NO_ERROR.toInt()));
    }

    private void createAdfSuccess(
            Optional<ObjectIdentifier> providedOid, ObjectIdentifier expectedOid)
            throws IOException, ProvisioningException {
        successSetup();
        ImmutableList<byte[]> cmdList = ImmutableList.of(CREATE_ADF_CMD.getEncoded());
        ScriptParser.ScriptContent scriptContent = new ScriptParser.ScriptContent(1, 1,
                cmdList, providedOid);

        mUnderTest.run(scriptContent, SERVICE_INSTANCE_ID, mProvisioningCallback);

        verify(mProvisioningCallback).onAdfCreated(eq(SERVICE_INSTANCE_ID), eq(expectedOid));
    }

    @Test
    public void createAdfSuccessWithKnownAdfOid() throws IOException, ProvisioningException {
        createAdfSuccess(Optional.of(ADF_OID), ADF_OID);
    }

    @Test
    public void createAdfSuccessWithoutKnownAdfOid() throws IOException, ProvisioningException {
        createAdfSuccess(Optional.empty(), OID_IN_RESPONSE);
    }

    private void manageAdfSuccess(
            Optional<ObjectIdentifier> providedOid, ObjectIdentifier expectedOid)
            throws IOException, ProvisioningException {
        successSetup();
        ImmutableList<byte[]> cmdList = ImmutableList.of(
                MANAGE_ADF_NOT_LAST_CMD.getEncoded(),
                MANAGE_ADF_LAST_CMD.getEncoded());
        ScriptParser.ScriptContent scriptContent = new ScriptParser.ScriptContent(1, 1,
                cmdList, providedOid);

        mUnderTest.run(scriptContent, SERVICE_INSTANCE_ID, mProvisioningCallback);

        verify(mProvisioningCallback).onAdfProvisioned(eq(SERVICE_INSTANCE_ID), eq(expectedOid));
    }

    @Test
    public void manageAdfSuccessWithKnownAdfOid()
            throws IOException, ProvisioningException {
        manageAdfSuccess(Optional.of(ADF_OID), ADF_OID);
    }

    @Test
    public void manageAdfSuccessWithoutKnownAdfOid()
            throws IOException, ProvisioningException {
        manageAdfSuccess(Optional.empty(), OID_IN_RESPONSE);
    }

    private void importAdfSuccess(
            Optional<ObjectIdentifier> providedOid, ObjectIdentifier expectedOid)
            throws IOException, ProvisioningException {
        successSetup();
        when(mSecureElementChannel.transmit(any(CommandApdu.class))).thenReturn(
                ResponseApdu.fromDataAndStatusWord(
                        DataTypeConversionUtil.hexStringToByteArray("DF51020A0B060102"),
                        StatusWord.SW_NO_ERROR.toInt()));
        List<byte[]> cmdList = ImmutableList.of(IMPORT_ADF_CMD.getEncoded());
        ScriptParser.ScriptContent scriptContent = new ScriptParser.ScriptContent(1, 1,
                cmdList, providedOid);

        mUnderTest.run(scriptContent, SERVICE_INSTANCE_ID, mProvisioningCallback);

        verify(mProvisioningCallback).onAdfImported(
                eq(SERVICE_INSTANCE_ID), eq(expectedOid),
                eq(DataTypeConversionUtil.hexStringToByteArray("0A0B")));
    }

    @Test
    public void importAdfSuccessWithKnownAdfOid()
            throws IOException, ProvisioningException {
        importAdfSuccess(Optional.of(ADF_OID), ADF_OID);
    }

    @Test
    public void importAdfSuccessWithoutKnownAdfOid()
            throws IOException, ProvisioningException {
        importAdfSuccess(Optional.empty(), OID_IN_RESPONSE);
    }

    private void deleteAdfSuccess(
            Optional<ObjectIdentifier> providedOid, ObjectIdentifier expectedOid)
            throws IOException, ProvisioningException {
        successSetup();
        ImmutableList<byte[]> cmdList = ImmutableList.of(
                DELETE_ADF_CMD.getEncoded());
        ScriptParser.ScriptContent scriptContent = new ScriptParser.ScriptContent(1, 1,
                cmdList, providedOid);

        mUnderTest.run(scriptContent, SERVICE_INSTANCE_ID, mProvisioningCallback);

        verify(mProvisioningCallback).onAdfDeleted(eq(SERVICE_INSTANCE_ID), eq(expectedOid));
    }

    @Test
    public void deleteAdfSuccessWithKnownAdfOid()
            throws IOException, ProvisioningException {
        deleteAdfSuccess(Optional.of(ADF_OID), ADF_OID);
    }

    @Test
    public void deleteAdfSuccessWithoutKnownAdfOid()
            throws IOException, ProvisioningException {
        deleteAdfSuccess(Optional.empty(), OID_IN_RESPONSE);
    }

    @Test(expected = ProvisioningException.class)
    public void notAllowedCmd() throws IOException, ProvisioningException {
        successSetup();
        ImmutableList<byte[]> cmdList = ImmutableList.of(
                NOT_ALLOWED_CMD.getEncoded());
        ScriptParser.ScriptContent scriptContent = new ScriptParser.ScriptContent(1, 1,
                cmdList, Optional.of(ADF_OID));

        mUnderTest.run(scriptContent, SERVICE_INSTANCE_ID, mProvisioningCallback);
    }

    @Test(expected = ProvisioningException.class)
    public void failedProvisioning() throws IOException, ProvisioningException {
        when(mSecureElementChannel.openChannel()).thenReturn(true);
        when(mSecureElementChannel.transmit(any(CommandApdu.class))).thenReturn(
                ResponseApdu.fromStatusWord(StatusWord.SW_COMMAND_NOT_ALLOWED));
        ImmutableList<byte[]> cmdList = ImmutableList.of(CREATE_ADF_CMD.getEncoded());
        ScriptParser.ScriptContent scriptContent = new ScriptParser.ScriptContent(1, 1,
                cmdList, Optional.empty());

        mUnderTest.run(scriptContent, SERVICE_INSTANCE_ID, mProvisioningCallback);
    }

    @Test(expected = ProvisioningException.class)
    public void failedProvisioningWithException() throws IOException, ProvisioningException {
        when(mSecureElementChannel.openChannel()).thenReturn(true);
        when(mSecureElementChannel.transmit(any(CommandApdu.class))).thenThrow(
                new IOException("error"));
        ImmutableList<byte[]> cmdList = ImmutableList.of(CREATE_ADF_CMD.getEncoded());
        ScriptParser.ScriptContent scriptContent = new ScriptParser.ScriptContent(1, 1,
                cmdList, Optional.empty());

        mUnderTest.run(scriptContent, SERVICE_INSTANCE_ID, mProvisioningCallback);
    }
}
