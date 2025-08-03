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

package com.android.server.telecom;

import android.telecom.Log;
import android.telecom.PhoneAccountHandle;

import com.android.internal.telecom.ICallEventCallback;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks all TransactionalServiceWrappers that have an ongoing call. Removes wrappers that have no
 * more calls.
 */
public class TransactionalServiceRepository {
    private static final String TAG = TransactionalServiceRepository.class.getSimpleName();
    private static final Map<PhoneAccountHandle, TransactionalServiceWrapper> mServiceLookupTable =
            new HashMap<>();

    public TransactionalServiceRepository() {
    }

    public TransactionalServiceWrapper addNewCallForTransactionalServiceWrapper
            (PhoneAccountHandle phoneAccountHandle, ICallEventCallback callEventCallback,
                    CallsManager callsManager, Call call) {
        TransactionalServiceWrapper service;
        // Only create a new TransactionalServiceWrapper if this is the first call for a package.
        // Otherwise, get the existing TSW and add the new call to the service.
        if (!hasExistingServiceWrapper(phoneAccountHandle)) {
            Log.d(TAG, "creating a new TSW; handle=[%s]", phoneAccountHandle);
            service = new TransactionalServiceWrapper(callEventCallback,
                    callsManager, phoneAccountHandle, call, this);
        } else {
            Log.d(TAG, "add a new call to an existing TSW; handle=[%s]", phoneAccountHandle);
            service = getTransactionalServiceWrapper(phoneAccountHandle);
            if (service == null) {
                throw new IllegalStateException("service is null");
            } else {
                service.trackCall(call);
            }
        }

        mServiceLookupTable.put(phoneAccountHandle, service);

        return service;
    }

    private TransactionalServiceWrapper getTransactionalServiceWrapper(PhoneAccountHandle pah) {
        return mServiceLookupTable.get(pah);
    }

    private boolean hasExistingServiceWrapper(PhoneAccountHandle pah) {
        return mServiceLookupTable.containsKey(pah);
    }

    public boolean removeServiceWrapper(PhoneAccountHandle pah) {
        Log.i(TAG, "removeServiceWrapper: for phoneAccountHandle=[%s]", pah);
        if (!hasExistingServiceWrapper(pah)) {
            return false;
        }
        mServiceLookupTable.remove(pah);
        return true;
    }
}
