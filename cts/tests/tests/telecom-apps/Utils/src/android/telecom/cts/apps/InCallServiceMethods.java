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

import android.telecom.Call;

import java.util.List;

/**
 * This interface should be implemented by every CTS test process that uses the BaseAppVerifier
 * class.  Each method will need to statically call into the CTS test process InCallService in
 * order to implement the method.
 */
public interface InCallServiceMethods {
    boolean isBound();

    List<Call> getOngoingCalls();

    Call getLastAddedCall();

    int getCurrentCallCount();
}
