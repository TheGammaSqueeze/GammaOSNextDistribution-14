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

package android.telecom.cts.apps;

import static android.os.SystemClock.sleep;
import static android.telecom.cts.apps.StackTraceUtil.appendStackTraceList;
import static org.junit.Assert.assertEquals;

import android.os.SystemClock;

import android.telecom.Connection;

import java.util.List;
import java.util.Objects;

public class WaitUntil {
    public static final long DEFAULT_TIMEOUT_MS = 10000;
    private static final String CLASS_NAME = WaitUntil.class.getCanonicalName();
    private static final String TELECOM_ID_TOKEN = "_";

    // NOTE:
    // - This method should NOT be called from a telecom test app. The assertEquals will cause
    //     a DeadObjectException which will make any test failure log unreadable!
    // - This can be used for classes like BindUtils, BaseAppVerifierImpl, etc. that are running
    //    in the CTS test process
    public static void waitUntilConditionIsTrueOrTimeout(
            Condition condition,
            long timeout,
            String description) {

        long startTimeMillis = SystemClock.elapsedRealtime();
        long remainingTimeMillis = timeout;
        long elapsedTimeMillis;

        while (!Objects.equals(condition.expected(), condition.actual())
                && remainingTimeMillis > 0) {
            sleep(50);
            elapsedTimeMillis = SystemClock.elapsedRealtime() - startTimeMillis;
            remainingTimeMillis = timeout - elapsedTimeMillis;
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    // NOTE:
    // - This method should NOT be called from a telecom test app. The assertEquals will cause
    //     a DeadObjectException which will make any test failure log unreadable!
    // - This can be used for classes like BindUtils, BaseAppVerifierImpl, etc. that are running
    //    in the CTS test process
    public static void waitUntilConditionIsTrueOrTimeout(
            Condition condition) {
        long startTimeMillis = SystemClock.elapsedRealtime();
        long remainingTimeMillis = DEFAULT_TIMEOUT_MS;
        long elapsedTimeMillis;

        while (!Objects.equals(condition.expected(), condition.actual())
                && remainingTimeMillis > 0) {
            sleep(50);
            elapsedTimeMillis = SystemClock.elapsedRealtime() - startTimeMillis;
            remainingTimeMillis = DEFAULT_TIMEOUT_MS - elapsedTimeMillis;
        }
        assertEquals(condition.expected(), condition.actual());
    }

    // This helper is intended for test apps!
    private static boolean waitUntilConditionIsTrueOrReturnFalse(
            Condition condition) {
        long startTimeMillis = SystemClock.elapsedRealtime();
        long remainingTimeMillis = DEFAULT_TIMEOUT_MS;
        long elapsedTimeMillis;

        while (!Objects.equals(condition.expected(), condition.actual())
                && remainingTimeMillis > 0) {
            sleep(50);
            elapsedTimeMillis = SystemClock.elapsedRealtime() - startTimeMillis;
            remainingTimeMillis = DEFAULT_TIMEOUT_MS - elapsedTimeMillis;
        }
        return (condition.expected().equals(condition.actual()));
    }


    public interface ConnectionServiceImpl {
        Connection getLastConnection();
    }

    public static String waitUntilIdIsSet(
            String packageName,
            List<String> stackTrace,
            Connection connection) {

        boolean success = waitUntilConditionIsTrueOrReturnFalse(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return connection.getTelecomCallId() != null
                                && !(connection.getTelecomCallId().equals(""));
                    }
                }
        );

        if (!success) {
            throw new TestAppException(packageName,
                    appendStackTraceList(stackTrace,
                            CLASS_NAME + ".waitUntilIdIsSet"),
                    "expected:<Connection#getCallId() to return an id within the time window>"
                            + "actual:<hit timeout waiting for the Connection#getCallId() to be "
                            + "set>");
        }

        return extractTelecomId(connection);
    }

    public static void waitUntilCallAudioStateIsSet(
            String packageName,
            List<String> stackTrace,
            boolean isManaged,
            Connection connection) {
        boolean success = waitUntilConditionIsTrueOrReturnFalse(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        if (isManaged) {
                            return ((ManagedConnection) connection).getCurrentCallEndpointFromCallback()
                                    != null;
                        } else {
                            return ((VoipConnection) connection).getCurrentCallEndpointFromCallback()
                                    != null;
                        }
                    }
                }
        );

        if (!success) {
            throw new TestAppException(packageName,
                    appendStackTraceList(stackTrace,
                            CLASS_NAME + ".waitUntilCallAudioStateIsSet"),
                    "expected:<Connection#onCallEndpointChanged() to set"
                            + " Connection#mCallEndpoints within the time window> "
                            + "actual:<hit timeout waiting for the CallEndpoint to be set>");
        }
    }

    public static void waitUntilAvailableEndpointsIsSet(
            String packageName,
            List<String> stackTrace,
            boolean isManaged,
            Connection connection) {
        boolean success = waitUntilConditionIsTrueOrReturnFalse(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        if (isManaged) {
                            return ((ManagedConnection) connection).getCallEndpoints()
                                    != null;
                        } else {
                            return ((VoipConnection) connection).getCallEndpoints()
                                    != null;
                        }
                    }
                }
        );

        if (!success) {
            throw new TestAppException(packageName,
                    appendStackTraceList(stackTrace,
                            CLASS_NAME + ".waitUntilAvailableEndpointsIsSet"),
                    "expected:<Connection#onAvailableCallEndpointsChanged() to set"
                            + " ManagedConnection#mCallEndpoints within the time window> "
                            + "actual:<hit timeout waiting for the CallEndpoints to be set>");
        }
    }

    public static Connection waitUntilConnectionIsNonNull(
            String packageName,
            List<String> stackTrace,
            ConnectionServiceImpl s) {

        boolean success = waitUntilConditionIsTrueOrReturnFalse(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return getLastConnection(s) != null;
                    }
                }
        );

        if (!success) {
            throw new TestAppException(packageName,
                    appendStackTraceList(stackTrace,
                            CLASS_NAME + ".waitUntilConnectionIsNonNull_Voip"),
                    "expected:<Connection to be added to the ConnectionService> "
                            + "actual:<hit timeout waiting for Connection>");
        }

        return getLastConnection(s);
    }

    private static String extractTelecomId(Connection connection) {
        String str = connection.getTelecomCallId();
        return str.substring(0, str.indexOf(TELECOM_ID_TOKEN));
    }

    private static Connection getLastConnection(ConnectionServiceImpl s) {
        return s.getLastConnection();
    }

    public static void waitUntilCurrentCallEndpointIsSet(
            String packageName,
            List<String> stackTrace,
            TransactionalCallEvents events) throws TestAppException {
        boolean success = WaitUntil.waitUntilConditionIsTrueOrReturnFalse(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return events.getCurrentCallEndpoint() != null;
                    }
                }
        );

        if (!success) {
            throw new TestAppException(packageName,
                    appendStackTraceList(stackTrace,
                            CLASS_NAME + ".waitUntilCurrentCallEndpointIsSet"),
                    "expected:<TransactionalCallEvents#onCallEndpointChanged() to set"
                            + " TransactionalCallEvents#mCurrentCallEndpoint within time window>"
                            + " actual:<hit timeout waiting for the CallEndpoint to be set>");
        }
    }

    public static void waitUntilAvailableEndpointAreSet(
            String packageName,
            List<String> stackTrace,
            TransactionalCallEvents events) throws TestAppException {

        boolean success = WaitUntil.waitUntilConditionIsTrueOrReturnFalse(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return events.getCallEndpoints() != null;
                    }
                }
        );

        if (!success) {
            throw new TestAppException(packageName,
                    appendStackTraceList(stackTrace,
                            CLASS_NAME + ".waitUntilAvailableEndpointAreSet"),
                    "expected:<TransactionalCallEvents#onAvailableCallEndpointsChanged() to set"
                            + " TransactionalCallEvents#mCallEndpoints within the time window> "
                            + "actual:<hit timeout waiting for the CallEndpoints to be set>");
        }
    }
}
