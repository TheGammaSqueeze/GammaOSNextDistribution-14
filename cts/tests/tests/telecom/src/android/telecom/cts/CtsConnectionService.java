/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.telecom.cts;

import android.content.Intent;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConference;
import android.telecom.RemoteConnection;
import android.util.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

/**
 * This is the official ConnectionService for Telecom's CTS App. Since telecom requires that a
 * CS be registered in the AndroidManifest.xml file, we have to have a single implementation
 * of a CS and this is it. To test specific CS behavior, tests will implement their own CS and
 * tell CtsConnectionService to forward any method invocations to that test's implementation.
 * This is set up using {@link #setUp} and should be cleaned up before the end of the test using
 * {@link #tearDown}.
 *
 * sConnectionService: Contains the connection service object provided by the current test in
 *                     progress. We use this object to forward any communication received from the
 *                     Telecom framework to the test connection service.
 * sTelecomConnectionService: Contains the connection service object registered to the Telecom
 *                            framework. We use this object to forward any communication from the
 *                            test connection service to the Telecom framework. After Telecom
 *                            binds to CtsConnectionService, this is set to be the instance of
 *                            CtsConnectionService created by the framework after Telecom binds.
 */
public class CtsConnectionService extends ConnectionService {
    private static String LOG_TAG = "CtsConnectionService";
    // This is the connection service implementation set locally during test setup. Telecom calls
    // these overwritten methods.
    private static ConnectionService sConnectionServiceTestImpl;
    // Represents the connection from the test ConnectionService to telecom. Only valid once telecom
    // has successfully bound.
    private static ConnectionService sTelecomConnectionService;
    private static CountDownLatch sTelecomUnboundLatch;
    // Lock managing the setup and usage of sConnectionServiceTestImpl.
    private static final Object sTestImplLock = new Object();
    // Lock managing the setup and usage of sTelecomConnectionService/sTelecomUnboundLatch.
    private static final Object sTelecomCSLock = new Object();

    @Override
    public void onBindClient(Intent intent) {
        Log.i("TelecomCTS", "CS bound");
        onTelecomConnected(this);
    }

    /**
     * Call when a test is being setup to prepare this instance for testing.
     * @param connectionService The ConnectionService impl to use to proxy commands to from telecom.
     * @throws Exception There was an illegal state that caused the setup to fail.
     */
    public static void setUp(ConnectionService connectionService) throws Exception {
        setTestImpl(connectionService);
    }

    /**
     * Call when a test is complete to tear down.
     */
    public static void tearDown() {
        clearTestImpl();
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        ConnectionService testImpl = getTestImpl();
        if (testImpl != null) {
            return testImpl.onCreateOutgoingConnection(connectionManagerPhoneAccount, request);
        } else {
            Log.e(LOG_TAG,
                    "Tried to create outgoing connection when sConnectionService null!");
            return null;
        }
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        ConnectionService testImpl = getTestImpl();
        if (testImpl != null) {
            return testImpl.onCreateIncomingConnection(connectionManagerPhoneAccount, request);
        } else {
            Log.e(LOG_TAG,
                    "Tried to create incoming connection when sConnectionService null!");
            return null;
        }
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        ConnectionService testImpl = getTestImpl();
        if (testImpl != null) {
            testImpl.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request);
        } else {
            Log.e(LOG_TAG, "onCreateIncomingConnectionFailed called when "
                    + "sConnectionService null!");
        }
    }

    @Override
    public Conference onCreateOutgoingConference(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        ConnectionService testImpl = getTestImpl();
        if (testImpl != null) {
            return testImpl.onCreateOutgoingConference(connectionManagerPhoneAccount, request);
        } else {
            Log.e(LOG_TAG,
                    "onCreateOutgoingConference called when sConnectionService null!");
            return null;
        }
    }

    @Override
    public void onCreateOutgoingConferenceFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        ConnectionService testImpl = getTestImpl();
        if (testImpl != null) {
            testImpl.onCreateOutgoingConferenceFailed(connectionManagerPhoneAccount, request);
        } else {
            Log.e(LOG_TAG,
                    "onCreateOutgoingConferenceFailed called when sConnectionService null!");
        }
    }

    @Override
    public Conference onCreateIncomingConference(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        ConnectionService testImpl = getTestImpl();
        if (testImpl != null) {
            return testImpl.onCreateIncomingConference(connectionManagerPhoneAccount, request);
        } else {
            Log.e(LOG_TAG,
                    "onCreateIncomingConference called when sConnectionService null!");
            return null;
        }
    }

    @Override
    public void onCreateConnectionComplete(Connection connection) {
        ConnectionService testImpl = getTestImpl();
        if (testImpl != null) {
            testImpl.onCreateConnectionComplete(connection);
        } else {
            Log.e(LOG_TAG, "onCreateConnectionComplete called when "
                    + "sConnectionService null!");
        }
    }

    @Override
    public void onCreateConferenceComplete(Conference conference) {
        ConnectionService testImpl = getTestImpl();
        if (testImpl != null) {
            testImpl.onCreateConferenceComplete(conference);
        } else {
            Log.e(LOG_TAG, "onCreateConferenceComplete called when "
                    + "sConnectionService null!");
        }
    }

    @Override
    public void onCreateIncomingConferenceFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        ConnectionService testImpl = getTestImpl();
        if (testImpl != null) {
            testImpl.onCreateIncomingConferenceFailed(connectionManagerPhoneAccount, request);
        } else {
            Log.e(LOG_TAG,
                    "onCreateIncomingConferenceFailed called when sConnectionService null!");
        }
    }

    @Override
    public void onConference(Connection connection1, Connection connection2) {
        ConnectionService testImpl = getTestImpl();
        if (testImpl != null) {
            testImpl.onConference(connection1, connection2);
        } else {
            Log.e(LOG_TAG,
                    "onConference called when sConnectionService null!");
        }
    }

    @Override
    public void onRemoteExistingConnectionAdded(RemoteConnection connection) {
        ConnectionService testImpl = getTestImpl();
        if (testImpl != null) {
            testImpl.onRemoteExistingConnectionAdded(connection);
        } else {
            Log.e(LOG_TAG,
                    "onRemoteExistingConnectionAdded called when sConnectionService null!");
        }
    }

    public static void addConferenceToTelecom(Conference conference) {
        ConnectionService telecomConn = getTelecomConnection();
        if (telecomConn != null) {
            telecomConn.addConference(conference);
        } else {
            Log.e(LOG_TAG, "addConferenceToTelecom called when"
                    + " sTelecomConnectionService null!");
        }
    }

    public static void addExistingConnectionToTelecom(
            PhoneAccountHandle phoneAccountHandle, Connection connection) {
        ConnectionService telecomConn = getTelecomConnection();
        if (telecomConn != null) {
            telecomConn.addExistingConnection(phoneAccountHandle, connection);
        } else {
            Log.e(LOG_TAG, "addExistingConnectionToTelecom called when"
                    + " sTelecomConnectionService null!");
        }
    }

    public static Collection<Connection> getAllConnectionsFromTelecom() {
        ConnectionService telecomConn = getTelecomConnection();
        if (telecomConn == null) {
            return Collections.emptyList();
        }
        return telecomConn.getAllConnections();
    }

    public static RemoteConnection createRemoteOutgoingConnectionToTelecom(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        ConnectionService telecomConn = getTelecomConnection();
        if (telecomConn != null) {
            return telecomConn.createRemoteOutgoingConnection(
                    connectionManagerPhoneAccount, request);
        } else {
            Log.e(LOG_TAG, "createRemoteOutgoingConnectionToTelecom called when"
                    + " sTelecomConnectionService null!");
            return null;
        }
    }

    public static RemoteConnection createRemoteIncomingConnectionToTelecom(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        ConnectionService telecomConn = getTelecomConnection();
        if (telecomConn != null) {
            return telecomConn.createRemoteIncomingConnection(
                    connectionManagerPhoneAccount, request);
        } else {
            Log.e(LOG_TAG, "createRemoteIncomingConnectionToTelecom called when"
                    + " sTelecomConnectionService null!");
            return null;
        }
    }

    public static RemoteConference createRemoteIncomingConferenceToTelecom(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        ConnectionService telecomConn = getTelecomConnection();
        if (telecomConn != null) {
            return telecomConn.createRemoteIncomingConference(
                    connectionManagerPhoneAccount, request);
        } else {
            Log.e(LOG_TAG, "createRemoteIncomingConferenceToTelecom called when"
                    + " sTelecomConnectionService null!");
            return null;
        }
    }


    public static RemoteConference createRemoteOutgoingConferenceToTelecom(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        ConnectionService telecomConn = getTelecomConnection();
        if (telecomConn != null) {
            return telecomConn.createRemoteOutgoingConference(
                    connectionManagerPhoneAccount, request);
        } else {
            Log.e(LOG_TAG, "createRemoteOutgoingConferenceToTelecom called when"
                    + " sTelecomConnectionService null!");
            return null;
        }
    }

    @Override
    public void onRemoteConferenceAdded(RemoteConference conference) {
        ConnectionService telecomConn = getTestImpl();
        if (telecomConn != null) {
            telecomConn.onRemoteConferenceAdded(conference);
        } else {
            Log.e(LOG_TAG, "onRemoteConferenceAdded called when sConnectionService null!");
        }
    }

    @Override
    public void onConnectionServiceFocusGained() {
        ConnectionService telecomConn = getTestImpl();
        if (telecomConn != null) {
            telecomConn.onConnectionServiceFocusGained();
        } else {
            Log.e(LOG_TAG, "onConnectionServiceFocusGained called when sConnectionService null!");
        }
    }

    @Override
    public void onConnectionServiceFocusLost() {
        ConnectionService telecomConn = getTestImpl();
        if (telecomConn != null) {
            telecomConn.onConnectionServiceFocusLost();
        } else {
            Log.e(LOG_TAG, "onConnectionServiceFocusLost called when sConnectionService null!");
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(LOG_TAG, "Service has been unbound");
        onTelecomDisconnected();
        return super.onUnbind(intent);
    }

    public static boolean isServiceRegisteredToTelecom() {
        return getTelecomConnection() != null;
    }

    /**
     * @return Wait up to 5 seconds for the ConnectionService to be unbound from telecom. Return
     * true if unbinding occurred successfully, false if it did not.
     */
    public static boolean waitForUnBinding() {
        CountDownLatch latch = getUnboundLatch();
        return TestUtils.waitForLatchCountDown(latch);
    }

    /**
     * Setup the static connection to the ConnectionService that is handling commands to Telecom
     * through ConnectionService.
     */
    private static void onTelecomConnected(ConnectionService service) {
        synchronized (sTelecomCSLock) {
            sTelecomConnectionService = service;
            if (sTelecomUnboundLatch != null && sTelecomUnboundLatch.getCount() > 0) {
                Log.w(LOG_TAG, "Unexpected: Unbound latch has not counted down from previous "
                        + "usage");
            }
            sTelecomUnboundLatch = new CountDownLatch(1);
        }
    }

    /**
     * Teardown a previously connected ConnectionService when Telecom unbinds.
     */
    private static void onTelecomDisconnected() {
        synchronized (sTelecomCSLock) {
            if (sTelecomUnboundLatch != null) {
                sTelecomUnboundLatch.countDown();
            } else {
                Log.w(LOG_TAG, "Unexpected: null unbind latch, onBindClient never called.");
            }
            sTelecomConnectionService = null;
        }
    }

    /**
     * @return The ConnectionService that Telecom is connected through right now. This
     * ConnectionService must only be used to communicate back to Telecom. This must be called to
     * get the ConnectionService instance in order to keep synchronization across threads.
     */
    private static ConnectionService getTelecomConnection() {
        synchronized (sTelecomCSLock) {
            return sTelecomConnectionService;
        }
    }

    /**
     * @return The CountDownLatch tracking when the ConnectionService will be unbound by Telecom.
     */
    private static CountDownLatch getUnboundLatch() {
        synchronized (sTelecomCSLock) {
            return sTelecomUnboundLatch;
        }
    }

    /**
     * @return The ConnectionService that the test impl has provided to implement stub methods.
     * This must be called to get the ConnectionService instance in order to keep synchronization
     * across threads.
     */
    private static ConnectionService getTestImpl() {
        synchronized (sTestImplLock) {
            return sConnectionServiceTestImpl;
        }
    }

    /**
     * Clear the ConnectionService when the test is tearing down.
     */
    private static void clearTestImpl() {
        synchronized (sTestImplLock) {
            sConnectionServiceTestImpl = null;
        }
    }

    /**
     * Sets the Implementation of ConnectionService stub methods for this test. Must be called
     * before using any ConnectionService based tests.
     * @param impl The ConnectionService instance that implements ConnectionService stub methods
     * @throws Exception Invalid state occurred
     */
    private static void setTestImpl(ConnectionService impl) throws Exception {
        synchronized (sTestImplLock) {
            if (sConnectionServiceTestImpl != null) {
                // Clean up so following tests don't fail too, hiding the original culprit in noise
                sConnectionServiceTestImpl = null;
                throw new Exception("Mock ConnectionService exists.  Failed to call setUp(), "
                        + "or previous test failed to call tearDown().");
            }
            sConnectionServiceTestImpl = impl;
        }
    }
}
