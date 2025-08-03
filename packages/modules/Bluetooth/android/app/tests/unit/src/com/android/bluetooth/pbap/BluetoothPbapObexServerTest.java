/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.pbap;

import static com.android.obex.ApplicationParameter.TRIPLET_LENGTH.FORMAT_LENGTH;
import static com.android.obex.ApplicationParameter.TRIPLET_LENGTH.LISTSTARTOFFSET_LENGTH;
import static com.android.obex.ApplicationParameter.TRIPLET_LENGTH.ORDER_LENGTH;
import static com.android.obex.ApplicationParameter.TRIPLET_LENGTH.PRIMARYVERSIONCOUNTER_LENGTH;
import static com.android.obex.ApplicationParameter.TRIPLET_LENGTH.PROPERTY_SELECTOR_LENGTH;
import static com.android.obex.ApplicationParameter.TRIPLET_LENGTH.SEARCH_ATTRIBUTE_LENGTH;
import static com.android.obex.ApplicationParameter.TRIPLET_LENGTH.SECONDARYVERSIONCOUNTER_LENGTH;
import static com.android.obex.ApplicationParameter.TRIPLET_LENGTH.SUPPORTEDFEATURE_LENGTH;
import static com.android.obex.ApplicationParameter.TRIPLET_LENGTH.VCARDSELECTOROPERATOR_LENGTH;
import static com.android.obex.ApplicationParameter.TRIPLET_LENGTH.VCARDSELECTOR_LENGTH;
import static com.android.obex.ApplicationParameter.TRIPLET_TAGID.FORMAT_TAGID;
import static com.android.obex.ApplicationParameter.TRIPLET_TAGID.LISTSTARTOFFSET_TAGID;
import static com.android.obex.ApplicationParameter.TRIPLET_TAGID.MAXLISTCOUNT_TAGID;
import static com.android.obex.ApplicationParameter.TRIPLET_TAGID.ORDER_TAGID;
import static com.android.obex.ApplicationParameter.TRIPLET_TAGID.PRIMARYVERSIONCOUNTER_TAGID;
import static com.android.obex.ApplicationParameter.TRIPLET_TAGID.PROPERTY_SELECTOR_TAGID;
import static com.android.obex.ApplicationParameter.TRIPLET_TAGID.SEARCH_ATTRIBUTE_TAGID;
import static com.android.obex.ApplicationParameter.TRIPLET_TAGID.SEARCH_VALUE_TAGID;
import static com.android.obex.ApplicationParameter.TRIPLET_TAGID.SECONDARYVERSIONCOUNTER_TAGID;
import static com.android.obex.ApplicationParameter.TRIPLET_TAGID.SUPPORTEDFEATURE_TAGID;
import static com.android.obex.ApplicationParameter.TRIPLET_TAGID.VCARDSELECTOROPERATOR_TAGID;
import static com.android.obex.ApplicationParameter.TRIPLET_TAGID.VCARDSELECTOR_TAGID;
import static com.android.obex.ApplicationParameter.TRIPLET_VALUE.ORDER.ORDER_BY_ALPHANUMERIC;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.UserManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.pbap.BluetoothPbapObexServer.AppParamValue;
import com.android.obex.ApplicationParameter;
import com.android.obex.HeaderSet;
import com.android.obex.Operation;
import com.android.obex.ResponseCodes;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;
import java.io.OutputStream;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothPbapObexServerTest {

    private static final String TAG = BluetoothPbapObexServerTest.class.getSimpleName();

    @Mock Handler mMockHandler;
    @Mock PbapStateMachine mMockStateMachine;

    @Spy
    BluetoothMethodProxy mPbapMethodProxy = BluetoothMethodProxy.getInstance();

    BluetoothPbapObexServer mServer;

    private static final byte[] WRONG_UUID = new byte[] {
            0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00,
            0x00,
    };

    private static final byte[] WRONG_LENGTH_UUID = new byte[] {
            0x79,
            0x61,
            0x35,
    };

    private static final String ILLEGAL_PATH = "some/random/path";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        BluetoothMethodProxy.setInstanceForTesting(mPbapMethodProxy);
        mServer = new BluetoothPbapObexServer(
                mMockHandler, InstrumentationRegistry.getTargetContext(), mMockStateMachine);
    }

    @After
    public void tearDown() throws Exception {
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void testOnConnect_whenIoExceptionIsThrownFromGettingTargetHeader()
            throws Exception {
        HeaderSet request = new HeaderSet();
        HeaderSet reply = new HeaderSet();

        doThrow(IOException.class).when(mPbapMethodProxy).getHeader(request, HeaderSet.TARGET);

        assertThat(mServer.onConnect(request, reply))
                .isEqualTo(ResponseCodes.OBEX_HTTP_INTERNAL_ERROR);
    }

    @Test
    public void testOnConnect_whenUuidIsNull() {
        // Create an empty header set.
        HeaderSet request = new HeaderSet();
        HeaderSet reply = new HeaderSet();

        assertThat(mServer.onConnect(request, reply))
                .isEqualTo(ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE);
    }

    @Test
    public void testOnConnect_whenUuidLengthIsWrong() {
        HeaderSet request = new HeaderSet();
        request.setHeader(HeaderSet.TARGET, WRONG_LENGTH_UUID);
        HeaderSet reply = new HeaderSet();

        assertThat(mServer.onConnect(request, reply))
                .isEqualTo(ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE);
    }

    @Test
    public void testOnConnect_whenUuidIsWrong() {
        HeaderSet request = new HeaderSet();
        request.setHeader(HeaderSet.TARGET, WRONG_UUID);
        HeaderSet reply = new HeaderSet();

        assertThat(mServer.onConnect(request, reply))
                .isEqualTo(ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE);
    }

    @Test
    public void testOnConnect_whenIoExceptionIsThrownFromGettingWhoHeader()
            throws Exception {
        HeaderSet request = new HeaderSet();
        request.setHeader(HeaderSet.TARGET, BluetoothPbapObexServer.PBAP_TARGET);
        HeaderSet reply = new HeaderSet();

        doThrow(IOException.class).when(mPbapMethodProxy).getHeader(request, HeaderSet.WHO);

        assertThat(mServer.onConnect(request, reply))
                .isEqualTo(ResponseCodes.OBEX_HTTP_INTERNAL_ERROR);
    }

    @Test
    public void testOnConnect_whenIoExceptionIsThrownFromGettingApplicationParameterHeader()
            throws Exception {
        HeaderSet request = new HeaderSet();
        request.setHeader(HeaderSet.TARGET, BluetoothPbapObexServer.PBAP_TARGET);
        HeaderSet reply = new HeaderSet();

        doThrow(IOException.class).when(mPbapMethodProxy)
                .getHeader(request, HeaderSet.APPLICATION_PARAMETER);

        assertThat(mServer.onConnect(request, reply))
                .isEqualTo(ResponseCodes.OBEX_HTTP_INTERNAL_ERROR);
    }

    @Test
    public void testOnConnect_whenApplicationParameterIsWrong() {
        HeaderSet request = new HeaderSet();
        request.setHeader(HeaderSet.TARGET, BluetoothPbapObexServer.PBAP_TARGET);
        HeaderSet reply = new HeaderSet();

        byte[] badApplicationParameter = new byte[] {0x00, 0x01, 0x02};
        request.setHeader(HeaderSet.APPLICATION_PARAMETER, badApplicationParameter);

        assertThat(mServer.onConnect(request, reply))
                .isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    @Test
    public void testOnConnect_success() {
        HeaderSet request = new HeaderSet();
        request.setHeader(HeaderSet.TARGET, BluetoothPbapObexServer.PBAP_TARGET);
        HeaderSet reply = new HeaderSet();

        assertThat(mServer.onConnect(request, reply)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    @Test
    public void testOnDisconnect() throws Exception {
        HeaderSet request = new HeaderSet();
        HeaderSet response = new HeaderSet();

        mServer.onDisconnect(request, response);

        assertThat(response.getResponseCode()).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    @Test
    public void testOnAbort() throws Exception {
        HeaderSet request = new HeaderSet();
        HeaderSet reply = new HeaderSet();

        assertThat(mServer.onAbort(request, reply)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
        assertThat(mServer.sIsAborted).isTrue();
    }

    @Test
    public void testOnPut_notSupported() {
        Operation operation = mock(Operation.class);
        assertThat(mServer.onPut(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    @Test
    public void testOnDelete_notSupported() {
        HeaderSet request = new HeaderSet();
        HeaderSet reply = new HeaderSet();

        assertThat(mServer.onDelete(request, reply)).isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    @Test
    public void testOnClose() {
        mServer.onClose();
        verify(mMockStateMachine).sendMessage(PbapStateMachine.DISCONNECT);
    }

    @Test
    public void testCloseStream_success() throws Exception{
        OutputStream outputStream = mock(OutputStream.class);
        Operation operation = mock(Operation.class);

        assertThat(BluetoothPbapObexServer.closeStream(outputStream, operation)).isTrue();
        verify(outputStream).close();
        verify(operation).close();
    }

    @Test
    public void testCloseStream_failOnClosingOutputStream() throws Exception {
        OutputStream outputStream = mock(OutputStream.class);
        doThrow(IOException.class).when(outputStream).close();
        Operation operation = mock(Operation.class);

        assertThat(BluetoothPbapObexServer.closeStream(outputStream, operation)).isFalse();
    }

    @Test
    public void testCloseStream_failOnClosingOperation() throws Exception {
        OutputStream outputStream = mock(OutputStream.class);
        Operation operation = mock(Operation.class);
        doThrow(IOException.class).when(operation).close();

        assertThat(BluetoothPbapObexServer.closeStream(outputStream, operation)).isFalse();
    }

    @Test
    public void testOnAuthenticationFailure() {
        byte[] userName = {0x57, 0x68, 0x79};
        try {
            mServer.onAuthenticationFailure(userName);
        } catch (Exception ex) {
            assertWithMessage("Exception should not happen.").fail();
        }
    }

    @Test
    public void testLogHeader() throws Exception{
        HeaderSet headerSet = new HeaderSet();
        try {
            BluetoothPbapObexServer.logHeader(headerSet);
        } catch (Exception ex) {
            assertWithMessage("Exception should not happen.").fail();
        }
    }

    @Test
    public void testOnSetPath_whenIoExceptionIsThrownFromGettingNameHeader()
            throws Exception {
        HeaderSet request = new HeaderSet();
        HeaderSet reply = new HeaderSet();
        boolean backup = true;
        boolean create = true;

        doThrow(IOException.class).when(mPbapMethodProxy)
                .getHeader(request, HeaderSet.NAME);

        assertThat(mServer.onSetPath(request, reply, backup, create))
                .isEqualTo(ResponseCodes.OBEX_HTTP_INTERNAL_ERROR);
    }

    @Test
    public void testOnSetPath_whenPathCreateIsForbidden() throws Exception {
        HeaderSet request = new HeaderSet();
        request.setHeader(HeaderSet.NAME, ILLEGAL_PATH);
        HeaderSet reply = new HeaderSet();
        boolean backup = false;
        boolean create = true;

        assertThat(mServer.onSetPath(request, reply, backup, create))
                .isEqualTo(ResponseCodes.OBEX_HTTP_FORBIDDEN);
    }

    @Test
    public void testOnSetPath_whenPathIsIllegal() throws Exception {
        HeaderSet request = new HeaderSet();
        request.setHeader(HeaderSet.NAME, ILLEGAL_PATH);
        HeaderSet reply = new HeaderSet();
        boolean backup = false;
        boolean create = false;

        assertThat(mServer.onSetPath(request, reply, backup, create))
                .isEqualTo(ResponseCodes.OBEX_HTTP_NOT_FOUND);
    }

    @Test
    public void testOnSetPath_success() throws Exception {
        HeaderSet request = new HeaderSet();
        request.setHeader(HeaderSet.NAME, BluetoothPbapObexServer.TELECOM_PATH);
        HeaderSet reply = new HeaderSet();
        boolean backup = false;
        boolean create = true;

        assertThat(mServer.onSetPath(request, reply, backup, create))
                .isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        backup = true;
        assertThat(mServer.onSetPath(request, reply, backup, create))
                .isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    @Test
    public void testOnGet_whenIoExceptionIsThrownFromGettingApplicationParameterHeader()
            throws Exception {
        Operation operation = mock(Operation.class);
        HeaderSet headerSet = new HeaderSet();
        when(operation.getReceivedHeader()).thenReturn(headerSet);

        doThrow(IOException.class).when(mPbapMethodProxy)
                .getHeader(headerSet, HeaderSet.APPLICATION_PARAMETER);

        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_INTERNAL_ERROR);
    }

    @Test
    public void testOnGet_whenTypeIsNull() throws Exception {
        Operation operation = mock(Operation.class);
        HeaderSet headerSet = new HeaderSet();
        when(operation.getReceivedHeader()).thenReturn(headerSet);

        headerSet.setHeader(HeaderSet.TYPE, null);

        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE);
    }

    @Test
    public void testOnGet_whenUserIsNotUnlocked() throws Exception {
        Operation operation = mock(Operation.class);
        HeaderSet headerSet = new HeaderSet();
        headerSet.setHeader(HeaderSet.TYPE, BluetoothPbapObexServer.TYPE_VCARD);
        when(operation.getReceivedHeader()).thenReturn(headerSet);
        UserManager userManager = mock(UserManager.class);
        doReturn(userManager).when(mPbapMethodProxy).getSystemService(any(), eq(UserManager.class));

        when(userManager.isUserUnlocked()).thenReturn(false);

        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_UNAVAILABLE);
    }

    @Test
    public void testOnGet_whenNameIsNotSet_andCurrentPathIsTelecom_andTypeIsListing()
            throws Exception {
        Operation operation = mock(Operation.class);
        HeaderSet request = new HeaderSet();
        when(operation.getReceivedHeader()).thenReturn(request);
        UserManager userManager = mock(UserManager.class);
        doReturn(userManager).when(mPbapMethodProxy).getSystemService(any(), eq(UserManager.class));
        when(userManager.isUserUnlocked()).thenReturn(true);

        mServer.setCurrentPath(BluetoothPbapObexServer.TELECOM_PATH);
        request.setHeader(HeaderSet.TYPE, BluetoothPbapObexServer.TYPE_LISTING);

        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_NOT_FOUND);
    }

    @Test
    public void testOnGet_whenNameIsNotSet_andCurrentPathIsInvalid() throws Exception {
        Operation operation = mock(Operation.class);
        HeaderSet request = new HeaderSet();
        request.setHeader(HeaderSet.TYPE, BluetoothPbapObexServer.TYPE_LISTING);
        when(operation.getReceivedHeader()).thenReturn(request);
        UserManager userManager = mock(UserManager.class);
        doReturn(userManager).when(mPbapMethodProxy).getSystemService(any(), eq(UserManager.class));
        when(userManager.isUserUnlocked()).thenReturn(true);

        mServer.setCurrentPath(ILLEGAL_PATH);

        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE);
    }

    @Test
    public void testOnGet_whenAppParamIsInvalid() throws Exception {
        Operation operation = mock(Operation.class);
        HeaderSet request = new HeaderSet();
        request.setHeader(HeaderSet.TYPE, BluetoothPbapObexServer.TYPE_LISTING);
        when(operation.getReceivedHeader()).thenReturn(request);
        UserManager userManager = mock(UserManager.class);
        doReturn(userManager).when(mPbapMethodProxy).getSystemService(any(), eq(UserManager.class));
        when(userManager.isUserUnlocked()).thenReturn(true);

        mServer.setCurrentPath(BluetoothPbapObexServer.PB_PATH);
        byte[] badApplicationParameter = new byte[] {0x00, 0x01, 0x02};
        request.setHeader(HeaderSet.APPLICATION_PARAMETER, badApplicationParameter);

        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_BAD_REQUEST);
    }

    @Test
    public void testOnGet_whenTypeIsInvalid() throws Exception {
        Operation operation = mock(Operation.class);
        HeaderSet request = new HeaderSet();
        when(operation.getReceivedHeader()).thenReturn(request);
        UserManager userManager = mock(UserManager.class);
        doReturn(userManager).when(mPbapMethodProxy).getSystemService(any(), eq(UserManager.class));
        when(userManager.isUserUnlocked()).thenReturn(true);

        mServer.setCurrentPath(BluetoothPbapObexServer.PB_PATH);
        request.setHeader(HeaderSet.TYPE, "someType");

        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE);
    }

    @Test
    public void testOnGet_whenNameIsNotSet_andTypeIsListing_success() throws Exception {
        Operation operation = mock(Operation.class);
        OutputStream outputStream = mock(OutputStream.class);
        when(operation.openOutputStream()).thenReturn(outputStream);
        HeaderSet request = new HeaderSet();
        when(operation.getReceivedHeader()).thenReturn(request);
        UserManager userManager = mock(UserManager.class);
        doReturn(userManager).when(mPbapMethodProxy).getSystemService(any(), eq(UserManager.class));
        when(userManager.isUserUnlocked()).thenReturn(true);
        mServer.setConnAppParamValue(new AppParamValue());
        request.setHeader(HeaderSet.TYPE, BluetoothPbapObexServer.TYPE_LISTING);

        mServer.setCurrentPath(BluetoothPbapObexServer.ICH_PATH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        mServer.setCurrentPath(BluetoothPbapObexServer.OCH_PATH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        mServer.setCurrentPath(BluetoothPbapObexServer.MCH_PATH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        mServer.setCurrentPath(BluetoothPbapObexServer.CCH_PATH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        mServer.setCurrentPath(BluetoothPbapObexServer.PB_PATH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        mServer.setCurrentPath(BluetoothPbapObexServer.FAV_PATH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    @Test
    public void testOnGet_whenNameIsNotSet_andTypeIsPb_success() throws Exception {
        Operation operation = mock(Operation.class);
        OutputStream outputStream = mock(OutputStream.class);
        when(operation.openOutputStream()).thenReturn(outputStream);
        HeaderSet request = new HeaderSet();
        when(operation.getReceivedHeader()).thenReturn(request);
        UserManager userManager = mock(UserManager.class);
        doReturn(userManager).when(mPbapMethodProxy).getSystemService(any(), eq(UserManager.class));
        when(userManager.isUserUnlocked()).thenReturn(true);
        mServer.setConnAppParamValue(new AppParamValue());
        request.setHeader(HeaderSet.TYPE, BluetoothPbapObexServer.TYPE_PB);

        mServer.setCurrentPath(BluetoothPbapObexServer.TELECOM_PATH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        mServer.setCurrentPath(BluetoothPbapObexServer.ICH_PATH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        mServer.setCurrentPath(BluetoothPbapObexServer.OCH_PATH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        mServer.setCurrentPath(BluetoothPbapObexServer.MCH_PATH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        mServer.setCurrentPath(BluetoothPbapObexServer.CCH_PATH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        mServer.setCurrentPath(BluetoothPbapObexServer.PB_PATH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        mServer.setCurrentPath(BluetoothPbapObexServer.FAV_PATH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    @Test
    public void testOnGet_whenSimPhoneBook() throws Exception {
        Operation operation = mock(Operation.class);
        HeaderSet request = new HeaderSet();
        when(operation.getReceivedHeader()).thenReturn(request);
        UserManager userManager = mock(UserManager.class);
        doReturn(userManager).when(mPbapMethodProxy).getSystemService(any(), eq(UserManager.class));
        when(userManager.isUserUnlocked()).thenReturn(true);

        request.setHeader(HeaderSet.NAME, BluetoothPbapObexServer.PB);
        request.setHeader(HeaderSet.TYPE, BluetoothPbapObexServer.TYPE_LISTING);
        mServer.setCurrentPath(BluetoothPbapSimVcardManager.SIM_PATH);

        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE);
    }

    @Test
    public void testOnGet_whenNameDoesNotMatch() throws Exception {
        Operation operation = mock(Operation.class);
        HeaderSet request = new HeaderSet();
        request.setHeader(HeaderSet.TYPE, BluetoothPbapObexServer.TYPE_LISTING);
        when(operation.getReceivedHeader()).thenReturn(request);
        UserManager userManager = mock(UserManager.class);
        doReturn(userManager).when(mPbapMethodProxy).getSystemService(any(), eq(UserManager.class));
        when(userManager.isUserUnlocked()).thenReturn(true);

        request.setHeader(HeaderSet.NAME, "someName");

        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_NOT_FOUND);
    }

    @Test
    public void testOnGet_whenNameIsSet_andTypeIsListing_success() throws Exception {
        Operation operation = mock(Operation.class);
        OutputStream outputStream = mock(OutputStream.class);
        when(operation.openOutputStream()).thenReturn(outputStream);
        HeaderSet request = new HeaderSet();
        when(operation.getReceivedHeader()).thenReturn(request);
        UserManager userManager = mock(UserManager.class);
        doReturn(userManager).when(mPbapMethodProxy).getSystemService(any(), eq(UserManager.class));
        when(userManager.isUserUnlocked()).thenReturn(true);
        mServer.setConnAppParamValue(new AppParamValue());
        request.setHeader(HeaderSet.TYPE, BluetoothPbapObexServer.TYPE_LISTING);

        request.setHeader(HeaderSet.NAME, BluetoothPbapObexServer.ICH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        request.setHeader(HeaderSet.NAME, BluetoothPbapObexServer.OCH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        request.setHeader(HeaderSet.NAME, BluetoothPbapObexServer.MCH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        request.setHeader(HeaderSet.NAME, BluetoothPbapObexServer.CCH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        request.setHeader(HeaderSet.NAME, BluetoothPbapObexServer.PB);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        request.setHeader(HeaderSet.NAME, BluetoothPbapObexServer.FAV);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    @Test
    public void testOnGet_whenNameIsSet_andTypeIsPb_success() throws Exception {
        Operation operation = mock(Operation.class);
        OutputStream outputStream = mock(OutputStream.class);
        when(operation.openOutputStream()).thenReturn(outputStream);
        HeaderSet request = new HeaderSet();
        when(operation.getReceivedHeader()).thenReturn(request);
        UserManager userManager = mock(UserManager.class);
        doReturn(userManager).when(mPbapMethodProxy).getSystemService(any(), eq(UserManager.class));
        when(userManager.isUserUnlocked()).thenReturn(true);
        mServer.setConnAppParamValue(new AppParamValue());
        request.setHeader(HeaderSet.TYPE, BluetoothPbapObexServer.TYPE_PB);

        request.setHeader(HeaderSet.NAME, BluetoothPbapObexServer.ICH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        request.setHeader(HeaderSet.NAME, BluetoothPbapObexServer.OCH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        request.setHeader(HeaderSet.NAME, BluetoothPbapObexServer.MCH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        request.setHeader(HeaderSet.NAME, BluetoothPbapObexServer.CCH);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        request.setHeader(HeaderSet.NAME, BluetoothPbapObexServer.PB);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);

        request.setHeader(HeaderSet.NAME, BluetoothPbapObexServer.FAV);
        assertThat(mServer.onGet(operation)).isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    @Test
    public void writeVCardEntry() {
        int vcfIndex = 1;
        String nameWithSpecialChars = "Name<>\"\'&";
        StringBuilder stringBuilder = new StringBuilder();

        BluetoothPbapObexServer.writeVCardEntry(vcfIndex, nameWithSpecialChars, stringBuilder);
        String result = stringBuilder.toString();

        String expectedResult = "<card handle=\"" + vcfIndex + ".vcf\" name=\"" +
                "Name&lt;&gt;&quot;&#039;&amp;" + "\"/>";
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void getDatabaseIdentifier() {
        long databaseIdentifierLow = 1;
        BluetoothPbapUtils.sDbIdentifier.set(databaseIdentifierLow);
        byte[] expected = new byte[] {0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 1}; // Big-endian

        assertThat(mServer.getDatabaseIdentifier()).isEqualTo(expected);
    }

    @Test
    public void getPBPrimaryFolderVersion() {
        long primaryVersion = 5;
        BluetoothPbapUtils.sPrimaryVersionCounter = primaryVersion;
        byte[] expected = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 5}; // Big-endian

        assertThat(BluetoothPbapObexServer.getPBPrimaryFolderVersion()).isEqualTo(expected);
    }

    @Test
    public void getPBSecondaryFolderVersion() {
        long secondaryVersion = 5;
        BluetoothPbapUtils.sSecondaryVersionCounter = secondaryVersion;
        byte[] expected = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 5}; // Big-endian

        assertThat(BluetoothPbapObexServer.getPBSecondaryFolderVersion()).isEqualTo(expected);
    }

    @Test
    public void setDbCounters() {
        ApplicationParameter param = new ApplicationParameter();

        mServer.setDbCounters(param);

        byte[] result = param.getHeader();
        assertThat(result).isNotNull();
        int expectedLength = 2 + ApplicationParameter.TRIPLET_LENGTH.DATABASEIDENTIFIER_LENGTH;
        assertThat(result.length).isEqualTo(expectedLength);
    }

    @Test
    public void setFolderVersionCounters() {
        ApplicationParameter param = new ApplicationParameter();

        BluetoothPbapObexServer.setFolderVersionCounters(param);

        byte[] result = param.getHeader();
        assertThat(result).isNotNull();
        int expectedLength = 2 + ApplicationParameter.TRIPLET_LENGTH.PRIMARYVERSIONCOUNTER_LENGTH
                + 2 + ApplicationParameter.TRIPLET_LENGTH.SECONDARYVERSIONCOUNTER_LENGTH;
        assertThat(result.length).isEqualTo(expectedLength);
    }

    @Test
    public void setCallversionCounters() {
        ApplicationParameter param = new ApplicationParameter();
        AppParamValue value = new AppParamValue();
        value.callHistoryVersionCounter = new byte[]
                {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

        BluetoothPbapObexServer.setCallversionCounters(param, value);

        byte[] expectedResult = new byte[] {
                PRIMARYVERSIONCOUNTER_TAGID, PRIMARYVERSIONCOUNTER_LENGTH,
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                SECONDARYVERSIONCOUNTER_TAGID, SECONDARYVERSIONCOUNTER_LENGTH,
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
        };
        assertThat(param.getHeader()).isEqualTo(expectedResult);
    }

    @Test
    public void pushHeader_returnsObexHttpOk() throws Exception {
        Operation op = mock(Operation.class);
        OutputStream os = mock(OutputStream.class);
        when(op.openOutputStream()).thenReturn(os);
        HeaderSet reply = new HeaderSet();

        assertThat(BluetoothPbapObexServer.pushHeader(op, reply))
                .isEqualTo(ResponseCodes.OBEX_HTTP_OK);
    }

    @Test
    public void pushHeader_withExceptionWhenOpeningOutputStream_returnsObexHttpInternalError()
            throws Exception {
        HeaderSet reply = new HeaderSet();
        Operation op = mock(Operation.class);
        when(op.openOutputStream()).thenThrow(new IOException());

        assertThat(BluetoothPbapObexServer.pushHeader(op, reply))
                .isEqualTo(ResponseCodes.OBEX_HTTP_INTERNAL_ERROR);
    }

    @Test
    public void pushHeader_withExceptionWhenClosingOutputStream_returnsObexHttpInternalError()
            throws Exception {
        HeaderSet reply = new HeaderSet();
        Operation op = mock(Operation.class);
        OutputStream os = mock(OutputStream.class);
        when(op.openOutputStream()).thenReturn(os);
        doThrow(new IOException()).when(os).close();

        assertThat(BluetoothPbapObexServer.pushHeader(op, reply))
                .isEqualTo(ResponseCodes.OBEX_HTTP_INTERNAL_ERROR);
    }

    @Test
    public void parseApplicationParameter_withInvalidTripletTagid_returnsFalse() {
        byte invalidTripletTagId = 0x00;
        byte[] rawBytes = new byte[] {invalidTripletTagId};
        AppParamValue appParamValue = new AppParamValue();

        assertThat(mServer.parseApplicationParameter(rawBytes, appParamValue)).isFalse();
    }

    @Test
    public void parseApplicationParameter_withPropertySelectorTagid() {
        byte[] rawBytes = new byte[] {PROPERTY_SELECTOR_TAGID, PROPERTY_SELECTOR_LENGTH,
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}; // non-zero value uses filter
        AppParamValue appParamValue = new AppParamValue();

        assertThat(mServer.parseApplicationParameter(rawBytes, appParamValue)).isTrue();
        assertThat(appParamValue.ignorefilter).isFalse();
    }

    @Test
    public void parseApplicationParameter_withSupportedFeatureTagid() {
        byte[] rawBytes = new byte[] {SUPPORTEDFEATURE_TAGID, SUPPORTEDFEATURE_LENGTH,
                0x01, 0x02, 0x03, 0x04};
        AppParamValue appParamValue = new AppParamValue();

        assertThat(mServer.parseApplicationParameter(rawBytes, appParamValue)).isTrue();
        byte[] expectedSupportedFeature = new byte[] {0x01, 0x02, 0x03, 0x04};
        assertThat(appParamValue.supportedFeature).isEqualTo(expectedSupportedFeature);
    }

    @Test
    public void parseApplicationParameter_withOrderTagid() {
        byte[] rawBytes = new byte[] {ORDER_TAGID, ORDER_LENGTH,
                ORDER_BY_ALPHANUMERIC};
        AppParamValue appParamValue = new AppParamValue();

        assertThat(mServer.parseApplicationParameter(rawBytes, appParamValue)).isTrue();
        assertThat(appParamValue.order).isEqualTo("1");
    }

    @Test
    public void parseApplicationParameter_withSearchValueTagid() {
        int searchLength = 4;
        byte[] rawBytes = new byte[] {SEARCH_VALUE_TAGID, (byte) searchLength,
                'a', 'b', 'c', 'd' };
        AppParamValue appParamValue = new AppParamValue();

        assertThat(mServer.parseApplicationParameter(rawBytes, appParamValue)).isTrue();
        assertThat(appParamValue.searchValue).isEqualTo("abcd");
    }

    @Test
    public void parseApplicationParameter_withSearchAttributeTagid() {
        byte[] rawBytes = new byte[] {SEARCH_ATTRIBUTE_TAGID, SEARCH_ATTRIBUTE_LENGTH,
                0x05};
        AppParamValue appParamValue = new AppParamValue();

        assertThat(mServer.parseApplicationParameter(rawBytes, appParamValue)).isTrue();
        assertThat(appParamValue.searchAttr).isEqualTo("5");
    }

    @Test
    public void parseApplicationParameter_withMaxListCountTagid() {
        byte[] rawBytes = new byte[] {MAXLISTCOUNT_TAGID, SEARCH_ATTRIBUTE_LENGTH,
                0x01, 0x02};
        AppParamValue appParamValue = new AppParamValue();

        assertThat(mServer.parseApplicationParameter(rawBytes, appParamValue)).isTrue();
        assertThat(appParamValue.maxListCount).isEqualTo(256 * 1 + 2);
    }

    @Test
    public void parseApplicationParameter_withListStartOffsetTagid() {
        byte[] rawBytes = new byte[] {LISTSTARTOFFSET_TAGID, LISTSTARTOFFSET_LENGTH,
                0x01, 0x02};
        AppParamValue appParamValue = new AppParamValue();

        assertThat(mServer.parseApplicationParameter(rawBytes, appParamValue)).isTrue();
        assertThat(appParamValue.listStartOffset).isEqualTo(256 * 1 + 2);
    }

    @Test
    public void parseApplicationParameter_withFormatTagid() {
        byte[] rawBytes = new byte[] {FORMAT_TAGID, FORMAT_LENGTH,
                0x01};
        AppParamValue appParamValue = new AppParamValue();

        assertThat(mServer.parseApplicationParameter(rawBytes, appParamValue)).isTrue();
        assertThat(appParamValue.vcard21).isFalse();
    }

    @Test
    public void parseApplicationParameter_withVCardSelectorTagid() {
        byte[] rawBytes = new byte[] {VCARDSELECTOR_TAGID, VCARDSELECTOR_LENGTH,
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        AppParamValue appParamValue = new AppParamValue();

        assertThat(mServer.parseApplicationParameter(rawBytes, appParamValue)).isTrue();
        byte[] expectedVcardSelector = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        assertThat(appParamValue.vCardSelector).isEqualTo(expectedVcardSelector);
    }

    @Test
    public void parseApplicationParameter_withVCardSelectorOperatorTagid() {
        byte[] rawBytes = new byte[] {VCARDSELECTOROPERATOR_TAGID, VCARDSELECTOROPERATOR_LENGTH,
                0x01};
        AppParamValue appParamValue = new AppParamValue();

        assertThat(mServer.parseApplicationParameter(rawBytes, appParamValue)).isTrue();
        assertThat(appParamValue.vCardSelectorOperator).isEqualTo("1");
    }

    @Test
    public void appParamValueDump_doesNotCrash() {
        AppParamValue appParamValue = new AppParamValue();
        appParamValue.dump();
    }
}
