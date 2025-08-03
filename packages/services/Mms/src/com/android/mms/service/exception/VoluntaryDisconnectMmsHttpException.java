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

package com.android.mms.service.exception;

/**
 * Thrown when voluntarily disconnect an MMS http connection to trigger immediate retry. This
 * exception indicates the connection is voluntarily cancelled, instead of a failure.
 */
public class VoluntaryDisconnectMmsHttpException extends MmsHttpException{
    public VoluntaryDisconnectMmsHttpException(int statusCode, String message) {
        super(statusCode, message);
    }
}
