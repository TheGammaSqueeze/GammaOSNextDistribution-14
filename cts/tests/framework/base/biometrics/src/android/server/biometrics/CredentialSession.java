/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.server.biometrics;

public class CredentialSession implements AutoCloseable {

    private static final String SET_PASSWORD = "locksettings set-pin 1234";
    private static final String CLEAR_PASSWORD = "locksettings clear --old 1234";
    private static final String VERIFY_CREDENTIAL = "locksettings verify --old 1234";
    private static final String VERIFY_INCORRECT_CREDENTIAL = "locksettings verify --old 9999";

    public void setCredential() {
        Utils.executeShellCommand(SET_PASSWORD);
    }

    public void verifyCredential() {
        Utils.executeShellCommand(VERIFY_CREDENTIAL);
    }

    public void verifyIncorrectCredential() {
        Utils.executeShellCommand(VERIFY_INCORRECT_CREDENTIAL);
    }

    @Override
    public void close() throws Exception {
        Utils.executeShellCommand(CLEAR_PASSWORD);
    }
}
