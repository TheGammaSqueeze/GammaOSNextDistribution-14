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

package com.android.server.uwb.secure;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.server.uwb.secure.iso7816.CommandApdu;
import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.secure.omapi.OmapiConnection;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

public class SecureElementChannelTest {
    @Mock private OmapiConnection mMockOmapiConnection;
    @Mock private OmapiConnection.InitCompletionCallback mInitCompletionCallback;
    @Mock private CommandApdu mMockCommandApdu;
    @Mock private ResponseApdu mMockResponseApdu;

    @Captor
    private ArgumentCaptor<OmapiConnection.InitCompletionCallback>
            mInitCompletionCallbackCaptor;

    private SecureElementChannel mSecureElementChannel;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mSecureElementChannel =
                new SecureElementChannel(mMockOmapiConnection);
    }

    @Test
    public void init_callsInitOnOmapiConnection() {
        doNothing().when(mMockOmapiConnection).init(mInitCompletionCallbackCaptor.capture());

        mSecureElementChannel.init(mInitCompletionCallback);
        mInitCompletionCallbackCaptor.getValue().onInitCompletion();

        verify(mMockOmapiConnection).init(any());
        verify(mInitCompletionCallback).onInitCompletion();
    }

    @Test
    public void openChannel_getsSuccessResponse_success() throws Exception {
        when(mMockOmapiConnection.openChannel()).thenReturn(ResponseApdu.SW_SUCCESS_APDU);

        boolean result = mSecureElementChannel.openChannel();

        verify(mMockOmapiConnection).openChannel();
        assertThat(result).isTrue();
    }

    @Test
    public void openChannel_getsErrorResponse_returnsFalse() throws Exception {
        when(mMockOmapiConnection.openChannel()).thenReturn(ResponseApdu.SW_UNKNOWN_APDU);

        boolean result = mSecureElementChannel.openChannel();

        verify(mMockOmapiConnection).openChannel();
        assertThat(result).isFalse();
    }

    @Test
    public void openChannel_getsException_returnsFalse() throws Exception {
        doThrow(new IOException()).when(mMockOmapiConnection).openChannel();

        boolean result = mSecureElementChannel.openChannel();

        verify(mMockOmapiConnection).openChannel();
        assertThat(result).isFalse();
    }

    @Test
    public void openChannel_swTemporarilyUnavailableOnFirstTwoAttempts_succeedsOnThirdTry()
            throws Exception {
        init();
        when(mMockOmapiConnection.openChannel())
                .thenReturn(ResponseApdu.fromStatusWord(StatusWord.SW_CONDITIONS_NOT_SATISFIED))
                .thenReturn(ResponseApdu.fromStatusWord(StatusWord.SW_CONDITIONS_NOT_SATISFIED))
                .thenReturn(ResponseApdu.fromStatusWord(StatusWord.SW_NO_ERROR));

        boolean result = mSecureElementChannel.openChannel();

        verify(mMockOmapiConnection, times(3)).openChannel();
        assertThat(result).isTrue();
    }

    @Test
    public void openChannel_swTemporarilyUnavailableAndNoSpecificDiagnostic_succeedsOnThirdTry()
            throws Exception {
        init();
        when(mMockOmapiConnection.openChannel())
                .thenReturn(ResponseApdu.fromStatusWord(StatusWord.SW_CONDITIONS_NOT_SATISFIED))
                .thenReturn(ResponseApdu.fromStatusWord(StatusWord.SW_NO_SPECIFIC_DIAGNOSTIC))
                .thenReturn(ResponseApdu.fromStatusWord(StatusWord.SW_NO_ERROR));

        boolean result = mSecureElementChannel.openChannel();

        verify(mMockOmapiConnection, times(3)).openChannel();

        assertThat(result).isTrue();
    }

    @Test
    public void transmit_swTemporarilyUnavailableOnFirstTwoAttempts_succeedsOnThirdTry()
            throws Exception {
        init();
        when(mMockOmapiConnection.openChannel()).thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        when(mMockOmapiConnection.transmit(eq(mMockCommandApdu)))
                .thenReturn(ResponseApdu.fromStatusWord(StatusWord.SW_CONDITIONS_NOT_SATISFIED))
                .thenReturn(ResponseApdu.fromStatusWord(StatusWord.SW_CONDITIONS_NOT_SATISFIED))
                .thenReturn(mMockResponseApdu);
        mSecureElementChannel.openChannel();

        ResponseApdu actualResponse = mSecureElementChannel.transmit(mMockCommandApdu);

        verify(mMockOmapiConnection, times(3)).transmit(eq(mMockCommandApdu));
        assertThat(actualResponse).isEqualTo(mMockResponseApdu);
    }

    @Test
    public void openChannel_retriesExhausted_failure() throws Exception {
        init();
        ResponseApdu responseApdu =
                ResponseApdu.fromStatusWord(StatusWord.SW_CONDITIONS_NOT_SATISFIED);
        when(mMockOmapiConnection.openChannel()).thenReturn(responseApdu);

        boolean result = mSecureElementChannel.openChannel();

        verify(mMockOmapiConnection, times(3)).openChannel();
        assertThat(result).isFalse();
    }

    @Test
    public void openChannelWithResponse_unopened_success() throws IOException {
        when(mMockOmapiConnection.openChannel()).thenReturn(ResponseApdu.SW_SUCCESS_APDU);

        ResponseApdu response = mSecureElementChannel.openChannelWithResponse();

        assertThat(response.getStatusWord()).isEqualTo(StatusWord.SW_NO_ERROR.toInt());
    }


    @Test
    public void openChannelWithResponse_closed_success() throws IOException {
        when(mMockOmapiConnection.openChannel()).thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        mSecureElementChannel.openChannelWithResponse();
        mSecureElementChannel.closeChannel();

        ResponseApdu response = mSecureElementChannel.openChannelWithResponse();

        assertThat(response.getStatusWord()).isEqualTo(StatusWord.SW_NO_ERROR.toInt());
    }

    @Test
    public void transmit_retriesExhausted_failure() throws Exception {
        init();
        when(mMockOmapiConnection.openChannel()).thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        when(mMockOmapiConnection.transmit(eq(mMockCommandApdu)))
                .thenReturn(ResponseApdu.fromStatusWord(StatusWord.SW_CONDITIONS_NOT_SATISFIED));
        mSecureElementChannel.openChannel();

        ResponseApdu actualResponse = mSecureElementChannel.transmit(mMockCommandApdu);

        verify(mMockOmapiConnection, times(3)).transmit(eq(mMockCommandApdu));
        assertThat(actualResponse)
                .isEqualTo(ResponseApdu.fromStatusWord(StatusWord.SW_CONDITIONS_NOT_SATISFIED));
    }

    @Test
    public void closeChannel_unopened_success() throws IOException {
        boolean result = mSecureElementChannel.closeChannel();

        verify(mMockOmapiConnection).closeChannel();
        assertThat(result).isTrue();
    }

    @Test
    public void closeChannel_opened_success() throws IOException {
        when(mMockOmapiConnection.openChannel()).thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        mSecureElementChannel.openChannelWithResponse();

        boolean result = mSecureElementChannel.closeChannel();

        assertThat(result).isTrue();
    }

    @Test
    public void closeChannel_closed_success() throws IOException {
        when(mMockOmapiConnection.openChannel()).thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        mSecureElementChannel.openChannelWithResponse();
        mSecureElementChannel.closeChannel();

        boolean result = mSecureElementChannel.closeChannel();

        assertThat(result).isTrue();
    }

    @Test
    public void transmit_callsSeTransmit() throws Exception {
        when(mMockOmapiConnection.openChannel()).thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        when(mMockOmapiConnection.transmit(any())).thenReturn(mMockResponseApdu);
        mSecureElementChannel.openChannel();

        ResponseApdu responseFromCall = mSecureElementChannel.transmit(mMockCommandApdu);

        verify(mMockOmapiConnection).transmit(eq(mMockCommandApdu));
        assertThat(responseFromCall).isEqualTo(mMockResponseApdu);
    }

    @Test
    public void transmit_unopened_failure() throws IOException {
        ResponseApdu response = mSecureElementChannel.transmit(mMockCommandApdu);

        assertThat(response.getStatusWord())
                .isEqualTo(StatusWord.SW_CONDITIONS_NOT_SATISFIED.toInt());
    }

    @Test
    public void transmit_opened_success() throws IOException {
        when(mMockOmapiConnection.openChannel()).thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        when(mMockOmapiConnection.transmit(any())).thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        mSecureElementChannel.openChannel();

        ResponseApdu response = mSecureElementChannel.transmit(mMockCommandApdu);

        assertThat(response.getStatusWord()).isEqualTo(StatusWord.SW_NO_ERROR.toInt());
    }


    @Test
    public void transmit_closed_success() throws IOException {
        when(mMockOmapiConnection.openChannel()).thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        mSecureElementChannel.openChannelWithResponse();
        mSecureElementChannel.closeChannel();

        ResponseApdu response = mSecureElementChannel.transmit(mMockCommandApdu);

        assertThat(response.getStatusWord())
                .isEqualTo(StatusWord.SW_CONDITIONS_NOT_SATISFIED.toInt());
    }

    @Test
    public void isOpened_unopened_verifyResult() {
        assertThat(mSecureElementChannel.isOpened()).isFalse();
    }

    @Test
    public void isOpened_opened_verifyResult() throws IOException {
        when(mMockOmapiConnection.openChannel()).thenReturn(ResponseApdu.SW_SUCCESS_APDU);

        mSecureElementChannel.openChannel();

        assertThat(mSecureElementChannel.isOpened()).isTrue();
    }

    @Test
    public void isOpened_openFailed_verifyResult() throws IOException {
        when(mMockOmapiConnection.openChannel()).thenReturn(ResponseApdu.SW_UNKNOWN_APDU);

        mSecureElementChannel.openChannel();

        assertThat(mSecureElementChannel.isOpened()).isFalse();
    }

    @Test
    public void isOpened_closed_verifyResult() throws IOException {
        when(mMockOmapiConnection.openChannel()).thenReturn(ResponseApdu.SW_SUCCESS_APDU);

        mSecureElementChannel.openChannel();
        mSecureElementChannel.closeChannel();

        assertThat(mSecureElementChannel.isOpened()).isFalse();
    }

    private void init() {
        mSecureElementChannel =
                new SecureElementChannel(
                        mMockOmapiConnection,
                        /* removeDelayBetweenRetriesForTest= */ true);
    }
}
