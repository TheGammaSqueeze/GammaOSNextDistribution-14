/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.phone.testapps.imstestapp;

import android.telephony.ims.stub.SipTransportImplBase;

import java.util.concurrent.Executor;

/**
 * Test stub implementation of SipTransport.
 */
public class SipTransportImpl extends SipTransportImplBase {

    private static SipTransportImpl sSipTransportInstance;

    public static SipTransportImpl getInstance(Executor e) {
        if (sSipTransportInstance == null) {
            sSipTransportInstance = new SipTransportImpl(e);
        }
        return sSipTransportInstance;
    };

    public SipTransportImpl(Executor e) {
        super(e);
    }
}
