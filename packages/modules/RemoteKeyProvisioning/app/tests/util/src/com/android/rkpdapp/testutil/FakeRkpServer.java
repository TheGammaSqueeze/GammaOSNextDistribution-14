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

package com.android.rkpdapp.testutil;

import static com.google.common.truth.Truth.assertWithMessage;

import android.security.NetworkSecurityPolicy;
import android.util.Base64;

import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import fi.iki.elonen.NanoHTTPD;

public class FakeRkpServer implements AutoCloseable {
    private static final String EEK_RESPONSE_OK =
            "g4KCAYOEQ6EBJqBYTaUBAgMmIAEhWCD3FIrbl/TMU+/SZBHE43UfZh+kcQxsz/oJRoB0h1TyrSJY"
                    + "IF5/W/bs5PYZzP8TN/0PociT2xgGdsRd5tdqd4bDLa+PWEAvl45C+74HLZVHhUeTQLAf1JtHpMRE"
                    + "qfKhB4cQx5/LEfS/n+g74Oc0TBX8e8N+MwX00TQ87QIEYHoV4HnTiv8khEOhASagWE2lAQIDJiAB"
                    + "IVggUYCsz4+WjOwPUOGpG7eQhjSL48OsZQJNtPYxDghGMjkiWCBU65Sd/ra05HM6JU4vH52dvfpm"
                    + "wRGL6ZaMQ+Qw9tp2q1hAmDj7NDpl23OYsSeiFXTyvgbnjSJO3fC/wgF0xLcpayQctdjSZvpE7/Uw"
                    + "LAR07ejGYNrOn1ZXJ3Qh096Tj+O4zYRDoQEmoFhxpgECAlggg5/4/RAcEp+SQcdbjeRO9BkTmscb"
                    + "bacOlfJkU12nHcEDOBggASFYIBakUhJjs4ZWUNjf8qCofbzZbqdoYOqMXPGT5ZcZDazeIlggib7M"
                    + "bD9esDk0r5e6ONEWHaHMHWTTjEhO+HKBGzs+Me5YQPrazy2rpTAMc8Xlq0mSWWBE+sTyM+UEsmwZ"
                    + "ZOkc42Q7NIYAZS313a+qAcmvg8lO+FqU6GWTUeMYHjmAp2lLM82CAoOEQ6EBJ6BYKqQBAQMnIAYh"
                    + "WCCZue7dXuRS9oXGTGLcPmGrV0h9dTcprXaAMtKzy2NY2VhAHiIIS6S3pMjXTgMO/rivFEynO2+l"
                    + "zdzaecYrZP6ZOa9254D6ZgCFDQeYKqyRXKclFEkGNHXKiid62eNaSesCA4RDoQEnoFgqpAEBAycg"
                    + "BiFYIOovhQ6eagxc973Z+igyv9pV6SCiUQPJA5MYzqAVKezRWECCa8ddpjZXt8dxEq0cwmqzLCMq"
                    + "3RQwy4IUtonF0x4xu7hQIUpJTbqRDG8zTYO8WCsuhNvFWQ+YYeLB6ony0K4EhEOhASegWE6lAQEC"
                    + "WCBvktEEbXHYp46I2NFWgV+W0XiD5jAbh+2/INFKO/5qLgM4GCAEIVggtl0cS5qDOp21FVk3oSb7"
                    + "D9/nnKwB1aTsyDopAIhYJTlYQICyn9Aynp1K/rAl8sLSImhGxiCwqugWrGShRYObzElUJX+rFgVT"
                    + "8L01k/PGu1lOXvneIQcUo7ako4uPgpaWugNYHQAAAYBINcxrASC0rWP9VTSO7LdABvcdkv7W2vh+"
                    + "onV0aW1lX3RvX3JlZnJlc2hfaG91cnMYSHgabnVtX2V4dHJhX2F0dGVzdGF0aW9uX2tleXMU";

    // Same as EEK_RESPONSE_OK, but the "num_extra_attestation_keys" value is 0, disabling RKP.
    private static final String EEK_RESPONSE_RKP_DISABLED =
            "g4KCAYOEQ6EBJqBYTaUBAgMmIAEhWCD3FIrbl/TMU+/SZBHE43UfZh+kcQxsz/oJRoB0h1TyrSJY"
                    + "IF5/W/bs5PYZzP8TN/0PociT2xgGdsRd5tdqd4bDLa+PWEAvl45C+74HLZVHhUeTQLAf1JtHpMRE"
                    + "qfKhB4cQx5/LEfS/n+g74Oc0TBX8e8N+MwX00TQ87QIEYHoV4HnTiv8khEOhASagWE2lAQIDJiAB"
                    + "IVggUYCsz4+WjOwPUOGpG7eQhjSL48OsZQJNtPYxDghGMjkiWCBU65Sd/ra05HM6JU4vH52dvfpm"
                    + "wRGL6ZaMQ+Qw9tp2q1hAmDj7NDpl23OYsSeiFXTyvgbnjSJO3fC/wgF0xLcpayQctdjSZvpE7/Uw"
                    + "LAR07ejGYNrOn1ZXJ3Qh096Tj+O4zYRDoQEmoFhxpgECAlggg5/4/RAcEp+SQcdbjeRO9BkTmscb"
                    + "bacOlfJkU12nHcEDOBggASFYIBakUhJjs4ZWUNjf8qCofbzZbqdoYOqMXPGT5ZcZDazeIlggib7M"
                    + "bD9esDk0r5e6ONEWHaHMHWTTjEhO+HKBGzs+Me5YQPrazy2rpTAMc8Xlq0mSWWBE+sTyM+UEsmwZ"
                    + "ZOkc42Q7NIYAZS313a+qAcmvg8lO+FqU6GWTUeMYHjmAp2lLM82CAoOEQ6EBJ6BYKqQBAQMnIAYh"
                    + "WCCZue7dXuRS9oXGTGLcPmGrV0h9dTcprXaAMtKzy2NY2VhAHiIIS6S3pMjXTgMO/rivFEynO2+l"
                    + "zdzaecYrZP6ZOa9254D6ZgCFDQeYKqyRXKclFEkGNHXKiid62eNaSesCA4RDoQEnoFgqpAEBAycg"
                    + "BiFYIOovhQ6eagxc973Z+igyv9pV6SCiUQPJA5MYzqAVKezRWECCa8ddpjZXt8dxEq0cwmqzLCMq"
                    + "3RQwy4IUtonF0x4xu7hQIUpJTbqRDG8zTYO8WCsuhNvFWQ+YYeLB6ony0K4EhEOhASegWE6lAQEC"
                    + "WCBvktEEbXHYp46I2NFWgV+W0XiD5jAbh+2/INFKO/5qLgM4GCAEIVggtl0cS5qDOp21FVk3oSb7"
                    + "D9/nnKwB1aTsyDopAIhYJTlYQICyn9Aynp1K/rAl8sLSImhGxiCwqugWrGShRYObzElUJX+rFgVT"
                    + "8L01k/PGu1lOXvneIQcUo7ako4uPgpaWugNYHQAAAYBINcxrASC0rWP9VTSO7LdABvcdkv7W2vh+"
                    + "onV0aW1lX3RvX3JlZnJlc2hfaG91cnMYSHgabnVtX2V4dHJhX2F0dGVzdGF0aW9uX2tleXMA";


    public enum Response {
        // canned responses for :fetchEekChain
        FETCH_EEK_OK(EEK_RESPONSE_OK),
        FETCH_EEK_RKP_DISABLED(EEK_RESPONSE_RKP_DISABLED),

        // canned responses for :signCertificates
        SIGN_CERTS_OK_VALID_CBOR("gkCA"),
        SIGN_CERTS_OK_INVALID_CBOR(200, "OK"),
        SIGN_CERTS_DEVICE_UNREGISTERED(444, "Device Not Registered"),
        SIGN_CERTS_USER_UNAUTHORIZED(403, "User not authorized"),

        // canned responses for any request
        INTERNAL_ERROR(500, "Internal Server Error");

        private final int mStatusCode;
        private final String mDescription;
        private final ByteString mBody;
        private final String mMime;

        // Text response (generally used to indicate an error)
        Response(int code, String description) {
            this(code, description, description.getBytes(StandardCharsets.UTF_8), "text/plain");
        }

        // Standard OK CBOR response
        Response(String base64Body) {
            this(200, "OK", Base64.decode(base64Body, Base64.DEFAULT), "application/cbor");
        }

        // Arbitrary response
        Response(int code, String description, byte[] body, String mime) {
            mStatusCode = code;
            mDescription = code + " " + description;
            mBody = ByteString.copyFrom(body);
            mMime = mime;
        }

        NanoHTTPD.Response toNanoResponse() {
            NanoHTTPD.Response.IStatus status = new NanoHTTPD.Response.IStatus() {
                @Override
                public String getDescription() {
                    return mDescription;
                }

                @Override
                public int getRequestStatus() {
                    return mStatusCode;
                }
            };
            return NanoHTTPD.newFixedLengthResponse(status, mMime, mBody.newInput(), mBody.size());
        }
    }

    final NanoHTTPD mServer;
    final boolean mCleartextPolicy;

    // Interface allowing users to plug in completely custom handlers.
    public interface RequestHandler {
        NanoHTTPD.Response serve(NanoHTTPD.IHTTPSession session, int bodySize)
                throws IOException, NanoHTTPD.ResponseException;
    }

    // Create a test server that has a customer handler for all requests
    public FakeRkpServer(RequestHandler handler) throws IOException {
        mCleartextPolicy = NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted();
        NetworkSecurityPolicy.getInstance().setCleartextTrafficPermitted(true);

        mServer = new NanoHTTPD("localhost", 0) {
            @Override
            public Response serve(IHTTPSession session) {
                try {
                    return handler.serve(session, (int) ((HTTPSession) session).getBodySize());
                } catch (IOException | NanoHTTPD.ResponseException e) {
                    StringWriter stack = new StringWriter();
                    e.printStackTrace(new PrintWriter(stack));
                    assertWithMessage("Error handling request: " + stack).fail();
                }
                return null;
            }
        };

        mServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    // Create a test server that returns pre-defined responses for fetchEek and
    // signCertificates
    public FakeRkpServer(Response fetchEekResponse, Response signCertResponse)
            throws IOException {
        this((session, bodySize) -> {
            session.getInputStream().readNBytes(bodySize);
            if (session.getUri().contains(":fetchEekChain")) {
                return fetchEekResponse.toNanoResponse();
            } else if (session.getUri().contains(":signCertificates")) {
                return signCertResponse.toNanoResponse();
            }
            assertWithMessage("Unexpected HTTP request: " + session.getUri()).fail();
            return null;
        });
    }

    @Override
    public void close() {
        mServer.stop();
        NetworkSecurityPolicy.getInstance().setCleartextTrafficPermitted(mCleartextPolicy);
    }

    public String getUrl() {
        return "http://localhost:" + mServer.getListeningPort() + "/";
    }
}
