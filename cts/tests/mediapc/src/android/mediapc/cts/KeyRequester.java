/*
 * Copyright (C) 2016 Google Inc.
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

import android.media.MediaDrm;
import android.media.MediaDrm.MediaDrmStateException;
import android.media.NotProvisionedException;
import android.util.Base64;
import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

/*
 * KeyRequester is used to request and update the current set of
 * keys in the CDM. KeyRequester should not be created, used, and
 * thrown away. A single KeyRequester should last the same period as
 * the session as it will track the changes in key servers.
 */
public class KeyRequester {
    private final MediaDrm mDrm;
    private final UUID mCryptoScheme;
    private int mKeyType;
    private byte[] mSessionId;
    private final byte[] mEmeInitData;
    private static String mMime = null;
    private static final String TAG = "KeyRequester";
    private static final UUID PLAYREADY_UUID = new UUID(0x9A04F07998404286L, 0xAB92E65BE0885F95L);
    private static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
    private static final String PLAYREADY_CUSTOM_DATA_KEY = "PRCustomData";
    private static final int MAX_KEY_REQUEST_ATTEMPTS = 4;

    /*
     * The server url will change during runtime. The additional URLs will get added through
     * calls to getDefaultUrl(). We keep the original in mServerUrl as a fallback.
     */
    private final String mServerUrl;
    private Post.Response mResponse = null;

    public KeyRequester(
            MediaDrm drm,
            byte[] sessionId,
            int keyType,
            String mimeType,
            byte[] emeInitData,
            String initialServerUrl) {

        this(drm, sessionId, keyType, mimeType, emeInitData, initialServerUrl, WIDEVINE_UUID);
    }

    public KeyRequester(
            MediaDrm drm,
            byte[] sessionId,
            int keyType,
            String mimeType,
            byte[] emeInitData,
            String initialServerUrl,
            UUID cryptoScheme) {

        mDrm = drm;
        mSessionId = sessionId;
        mKeyType = keyType;
        mMime = mimeType;
        mEmeInitData = emeInitData;
        mServerUrl = initialServerUrl;
        mCryptoScheme = cryptoScheme;
    }

    public MediaDrm.KeyRequest getKeyRequest() throws Exception {
        HashMap<String, String> optionalKeyRequestParameters = null;
        return getKeyRequest(optionalKeyRequestParameters);
    }

    public MediaDrm.KeyRequest getKeyRequest(String customData) throws Exception {
        HashMap<String, String> optionalKeyRequestParameters = new HashMap<>();
        optionalKeyRequestParameters.put(PLAYREADY_CUSTOM_DATA_KEY, customData);
        return getKeyRequest(optionalKeyRequestParameters);
    }

    public MediaDrm.KeyRequest getKeyRequest(HashMap<String, String> optionalKeyRequestParameters)
            throws Exception {
        MediaDrm.KeyRequest keyRequest = null;
        int tries = 1;
        boolean needsRetry;
        do {
            try {
                needsRetry = false;
                if (mEmeInitData == null) {
                    keyRequest = mDrm.getKeyRequest(
                            mSessionId,
                            null,
                            null,
                            mKeyType,
                            optionalKeyRequestParameters);
                } else {
                    keyRequest = mDrm.getKeyRequest(
                            mSessionId,
                            mEmeInitData,
                            mMime,
                            mKeyType,
                            optionalKeyRequestParameters);
                }
            } catch (NotProvisionedException ex) {
                // From Android 12(/S) onwards, because of the introduction of DRM certificates
                // expiration, getKeyRequest may be throw NotProvisionedException.
                // The exception is handled here.
                if (tries == MAX_KEY_REQUEST_ATTEMPTS) {
                    throw ex;
                }
                // Provision the device
                new ProvisionRequester(mDrm).send();
                needsRetry = true;
                tries++;
            }
        } while (needsRetry);

        return keyRequest;
    }

    public byte[] send() throws Exception {
        return send(getKeyRequest());
    }

    public byte[] send(MediaDrm.KeyRequest request) throws Exception {
        sendRequest(request);
        return provideResponse();
    }

    public void sendRequest() throws Exception {
        sendRequest(getKeyRequest());
    }

    public void sendRequest(MediaDrm.KeyRequest request) throws Exception {

        String url;
        String defaultUrl = request.getDefaultUrl();

        // Use mServerUrl for PLAYREADY_UUID.
        if (!mCryptoScheme.equals(PLAYREADY_UUID) && !defaultUrl.isEmpty()) {
            url = defaultUrl;
        } else {
            url = mServerUrl;
        }

        try {
            Log.d(TAG, "CURRENT_URL: " + url);
            logLicensingRequest(request);

            final Post post = new Post(url, request.getData());

            if (mCryptoScheme.equals(PLAYREADY_UUID)) {
                post.setProperty("Content-Type", "text/xml");
                post.setProperty("SOAPAction",
                        "http://schemas.microsoft.com/DRM/2007/03/protocols/AcquireLicense");
            } else {
                post.setProperty("User-Agent", "Widevine CDM v1.0");
                post.setProperty("Connection", "close");
                post.setProperty("Accept", "*/*");
            }

            mResponse = post.send();
            Log.d(TAG, "RESPONSE_CODE: " + Integer.toString(mResponse.code));
            logLicensingResponse(mResponse);

            if (mResponse.code != 200) {
                throw new KeyRequesterException(
                        mResponse.code,
                        "Server returned HTTP error code " + mResponse.code,
                        mResponse.body);
            }

            if (mResponse.body == null) {
                throw new KeyRequesterException(
                        mResponse.code, "No response from license service!", null);
            }

            if (mResponse.body.length == 0) {
                throw new KeyRequesterException(
                        mResponse.code, "Empty response from license service!",
                        mResponse.body);
            }

        } catch (Exception e) {
            Log.e(TAG, "EXCEPTION: " + e.toString());
            Log.e(TAG, "StackTrace: " + e.fillInStackTrace());
            throw e;
        }
    }

    public byte[] provideResponse() throws Exception {

        byte[] keySetId = null;
        try {
            // Additional null check on response to appease "null response" dereference warning.
            byte[] responseBody =
                    mResponse != null ? parseResponseBody(mResponse.body) : new byte[0];

            keySetId = mDrm.provideKeyResponse(mSessionId, responseBody);
        } catch (MediaDrmStateException mdse) {
            // Test is likely shutting down on main thread, the network thread just needs to return.
            Log.w(TAG, "MediaDrmStateException received providing key response to MediaDrm. "
                    + "Likely means the test has completed on the main thread. "
                    + "Details: " + mdse.fillInStackTrace());
            return null;
        }

        if (keySetId == null) {
            throw new Exception("Received null keySetId from provideKeyResponse.");
        }

        return keySetId; /* Empty byte array for streaming/release requests, keySetId for offline */
    }

    // Public due to use in MediaDrmTest
    public byte[] parseResponseBody(byte[] responseBody) throws Exception {
        final String bodyString = new String(responseBody, "UTF-8");

        if (!bodyString.startsWith("GLS/")) {
            return responseBody;
        }

        if (!bodyString.startsWith("GLS/1.")) {
            throw new Exception("Invalid server version, expected 1.x");
        }

        final int drmMessageOffset = bodyString.indexOf("\r\n\r\n");

        if (drmMessageOffset == -1) {
            throw new Exception("Invalid server response, could not locate drm message");
        }

        return Arrays.copyOfRange(
                responseBody,
                drmMessageOffset + 4,
                responseBody.length);
    }

    /*
     * In the case of offline keys, where the session that first retrieved the keys may not be
     * the session that uses the keys during playback, need to allow a way to update the
     * session to use in future license service calls.
     */
    public void updateSessionId(byte[] sessionId) {
        mSessionId = sessionId;
    }

    public void updateKeyType(int keyType) {
        mKeyType = keyType;
    }

    public String getInitialServerUrl() {
        return mServerUrl;
    }

    private void logLicensingRequest(MediaDrm.KeyRequest request) {
        try {
            String myRequest = Base64.encodeToString(request.getData(), Base64.NO_WRAP);
            Log.i(TAG, "LICENSE_REQUEST: " + myRequest);

        } catch (Exception ex) {
            Log.e(TAG,
                    "LICENSE_REQUEST: Failure to log licensing request. ", ex);
        }
    }

    private void logLicensingResponse(Post.Response response) {
        try {
            String myResponse;
            String failed_template = "Response failed with code: %d \n Body: \n%s";

            if ((response.body == null) || (response.body.length == 0)) {
                myResponse = String.format(
                        Locale.getDefault(), failed_template, response.code, "NULL or EMPTY");
            } else if (response.code < 400) {
                myResponse = Base64.encodeToString(response.body, Base64.NO_WRAP);
            } else {
                myResponse = String.format(Locale.getDefault(), failed_template, response.code,
                        new String(response.body, "UTF-8"));
            }

            Log.i(TAG, "LICENSE_RESPONSE: " + myResponse);

        } catch (Exception ex) {
            Log.e(TAG, "LICENSE_RESPONSE: Failure to log licensing response. ", ex);
        }
    }
}
