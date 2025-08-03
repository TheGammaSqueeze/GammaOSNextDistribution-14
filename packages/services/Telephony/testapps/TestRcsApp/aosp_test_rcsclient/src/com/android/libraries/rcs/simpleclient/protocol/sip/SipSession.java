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

package com.android.libraries.rcs.simpleclient.protocol.sip;

import com.google.common.util.concurrent.ListenableFuture;

import javax.sip.message.Message;

/**
 * Abstraction of the underlying SIP channel for sending and receiving SIP messages.
 */
public interface SipSession {

    SipSessionConfiguration getSessionConfiguration();

    ListenableFuture<Boolean> send(Message message);

    void setSessionListener(SipSessionListener listener);
}
