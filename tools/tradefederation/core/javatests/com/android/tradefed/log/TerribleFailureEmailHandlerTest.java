/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tradefed.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.util.IEmail;
import com.android.tradefed.util.IEmail.Message;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/** Unit tests for {@link TerribleFailureEmailHandler}. */
@RunWith(JUnit4.class)
public class TerribleFailureEmailHandlerTest {
    @Mock IEmail mMockEmail;
    private TerribleFailureEmailHandler mWtfEmailHandler;
    private static final String MOCK_HOST_NAME = "myhostname.mydomain.com";
    private long mCurrentTimeMillis;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mWtfEmailHandler =
                new TerribleFailureEmailHandler(mMockEmail) {
                    @Override
                    protected String getLocalHostName() {
                        return MOCK_HOST_NAME;
                    }

                    @Override
                    protected long getCurrentTimeMillis() {
                        return mCurrentTimeMillis;
                    }
                };
        mCurrentTimeMillis = System.currentTimeMillis();
    }

    /**
     * Test normal success case for {@link TerribleFailureEmailHandler#onTerribleFailure(String,
     * Throwable)}.
     *
     * @throws IOException
     */
    @Test
    public void testOnTerribleFailure() throws IllegalArgumentException, IOException {

        mWtfEmailHandler.addDestination("user@domain.com");
        boolean retValue = mWtfEmailHandler.onTerribleFailure("something terrible happened", null);

        verify(mMockEmail).send(Mockito.<Message>any());
        assertTrue(retValue);
    }

    /**
     * Test that onTerribleFailure catches IllegalArgumentException when Mailer state is incorrect
     */
    @Test
    public void testOnTerribleFailure_catchesIllegalArgumentException() throws IOException {
        doThrow(new IllegalArgumentException("Mailer state illegal"))
                .when(mMockEmail)
                .send(Mockito.<Message>any());

        mWtfEmailHandler.addDestination("user@domain.com");
        boolean retValue = mWtfEmailHandler.onTerribleFailure("something terrible happened", null);
        assertFalse(retValue);
    }

    /** Test that onTerribleFailure catches IOException */
    @Test
    public void testOnTerribleFailure_catchesIOException() throws IOException {
        doThrow(new IOException("Mailer had an IO Exception"))
                .when(mMockEmail)
                .send(Mockito.<Message>any());

        mWtfEmailHandler.addDestination("user@domain.com");
        boolean retValue = mWtfEmailHandler.onTerribleFailure("something terrible happened", null);
        assertFalse(retValue);
    }

    /** Test that no email is attempted to be sent when there is no destination set */
    @Test
    public void testOnTerribleFailure_emptyDestinations() {
        boolean retValue = mWtfEmailHandler.onTerribleFailure("something terrible happened", null);
        assertFalse(retValue);
    }

    /** Test that no email is attempted to be sent if it is too adjacent to the previous failure. */
    @Test
    public void testOnTerribleFailure_adjacentFailures()
            throws IllegalArgumentException, IOException {
        mWtfEmailHandler.setMinEmailInterval(60000);

        mWtfEmailHandler.addDestination("user@domain.com");
        boolean retValue = mWtfEmailHandler.onTerribleFailure("something terrible happened", null);
        assertTrue(retValue);
        mCurrentTimeMillis += 30000;
        retValue = mWtfEmailHandler.onTerribleFailure("something terrible happened again", null);
        assertFalse(retValue);

        verify(mMockEmail).send(Mockito.<Message>any());
    }

    /**
     * Test that the second email is attempted to be sent if it is not adjacent to the previous
     * failure.
     */
    @Test
    public void testOnTerribleFailure_notAdjacentFailures()
            throws IllegalArgumentException, IOException {
        mWtfEmailHandler.setMinEmailInterval(60000);

        mWtfEmailHandler.addDestination("user@domain.com");
        boolean retValue = mWtfEmailHandler.onTerribleFailure("something terrible happened", null);
        assertTrue(retValue);
        mCurrentTimeMillis += 90000;
        retValue = mWtfEmailHandler.onTerribleFailure("something terrible happened again", null);
        assertTrue(retValue);

        verify(mMockEmail, times(2)).send(Mockito.<Message>any());
    }

    /**
     * Test that the generated email message actually contains the sender and destination email
     * addresses.
     */
    @Test
    public void testGenerateEmailMessage() {
        Collection<String> destinations = new ArrayList<String>();
        String sender = "alerter@email.address.com";
        String destA = "a@email.address.com";
        String destB = "b@email.address.com";
        destinations.add(destA);
        destinations.add(destB);

        mWtfEmailHandler.setSender(sender);
        mWtfEmailHandler.addDestination(destA);
        mWtfEmailHandler.addDestination(destB);
        Message msg =
                mWtfEmailHandler.generateEmailMessage(
                        "something terrible happened", new Throwable("hello"));
        assertEquals(msg.getSender(), sender);
        assertTrue(msg.getTo().equals(destinations));
    }

    /** Test normal success case for {@link TerribleFailureEmailHandler#generateEmailSubject()}. */
    @Test
    public void testGenerateEmailSubject() {
        assertEquals(
                "WTF happened to tradefed on " + MOCK_HOST_NAME,
                mWtfEmailHandler.generateEmailSubject());
    }
}
