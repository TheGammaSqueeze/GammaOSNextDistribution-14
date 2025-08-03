/*
 * Copyright (C) 2018 Google Inc.
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

package android.mediapc.cts;

/*
 * KeyRequesterException is used to hold data received from the license server response when a key
 * request fails. This data is used by the ExpectException criteria to validate certain responses
 * from the license server when invalid Policy configurations are requested in the license request.
 */
public class KeyRequesterException extends Exception {
    private final int mResponseCode;
    private final String mResponseMessage;
    private final byte[] mResponseBody;

    public KeyRequesterException(int responseCode, String responseMessage, byte[] responseBody) {
        mResponseCode = responseCode;
        mResponseMessage = responseMessage;
        mResponseBody = responseBody;
    }

    public int getResponseCode() {
        return mResponseCode;
    }

    public String getResponseMessage() {
        return mResponseMessage;
    }

    public byte[] getResponseBody() {
        return mResponseBody;
    }

    @Override
    public String getMessage() {
        return getResponseMessage();
    }
}
