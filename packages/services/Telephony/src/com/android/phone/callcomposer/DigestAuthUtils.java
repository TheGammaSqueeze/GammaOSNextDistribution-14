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

package com.android.phone.callcomposer;

import android.text.TextUtils;
import android.util.Log;

import com.google.common.io.BaseEncoding;

import gov.nist.javax.sip.address.GenericURI;
import gov.nist.javax.sip.header.Authorization;
import gov.nist.javax.sip.header.WWWAuthenticate;
import gov.nist.javax.sip.parser.WWWAuthenticateParser;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.util.Locale;

public class DigestAuthUtils {
    private static final String TAG = DigestAuthUtils.class.getSimpleName();

    public static final String WWW_AUTHENTICATE = "www-authenticate";
    private static final String MD5_ALGORITHM = "md5";
    private static final int CNONCE_LENGTH_BYTES = 16;
    private static final String AUTH_QOP = "auth";

    public static WWWAuthenticate parseAuthenticateHeader(String header) {
        String reconstitutedHeader = WWW_AUTHENTICATE + ": " + header;
        WWWAuthenticate parsedHeader;
        try {
            return (WWWAuthenticate) (new WWWAuthenticateParser(reconstitutedHeader).parse());
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing received auth header: " + e);
            return null;
        }
    }

    // Generates the Authorization header for use in future requests to the call composer server.
    public static String generateAuthorizationHeader(WWWAuthenticate parsedHeader,
            GbaCredentials credentials, String method, String uri) {
        if (!TextUtils.isEmpty(parsedHeader.getAlgorithm())
                && !MD5_ALGORITHM.equals(parsedHeader.getAlgorithm().toLowerCase(Locale.ROOT))) {
            Log.e(TAG, "This client only supports MD5 auth");
            return "";
        }
        if (!TextUtils.isEmpty(parsedHeader.getQop())
                && !AUTH_QOP.equals(parsedHeader.getQop().toLowerCase(Locale.ROOT))) {
            Log.e(TAG, "This client only supports the auth qop");
            return "";
        }

        String clientNonce = makeClientNonce();

        String response = computeResponse(parsedHeader.getNonce(), clientNonce, AUTH_QOP,
                credentials.getTransactionId(), parsedHeader.getRealm(), credentials.getKey(),
                method, uri);

        Authorization replyHeader = new Authorization();
        try {
            replyHeader.setScheme(parsedHeader.getScheme());
            replyHeader.setUsername(credentials.getTransactionId());
            replyHeader.setURI(new WorkaroundURI(uri));
            replyHeader.setRealm(parsedHeader.getRealm());
            replyHeader.setQop(AUTH_QOP);
            replyHeader.setNonce(parsedHeader.getNonce());
            replyHeader.setCNonce(clientNonce);
            replyHeader.setNonceCount(1);
            replyHeader.setResponse(response);
            replyHeader.setOpaque(parsedHeader.getOpaque());
            replyHeader.setAlgorithm(parsedHeader.getAlgorithm());

        } catch (ParseException e) {
            Log.e(TAG, "Error parsing while constructing reply header: " + e);
            return null;
        }

        return replyHeader.encodeBody();
    }

    public static String computeResponse(String serverNonce, String clientNonce, String qop,
            String username, String realm, byte[] password, String method, String uri) {
        String a1Hash = generateA1Hash(username, realm, password);
        String a2Hash = generateA2Hash(method, uri);

        // this is the nonce-count; since we don't reuse, it's always 1
        String nonceCount = "00000001";
        MessageDigest md5Digest = getMd5Digest();

        String hashInput = String.join(":",
                a1Hash,
                serverNonce,
                nonceCount,
                clientNonce,
                qop,
                a2Hash);
        md5Digest.update(hashInput.getBytes());
        return base16(md5Digest.digest());
    }

    private static String makeClientNonce() {
        SecureRandom rand = new SecureRandom();
        byte[] clientNonceBytes = new byte[CNONCE_LENGTH_BYTES];
        rand.nextBytes(clientNonceBytes);
        return base16(clientNonceBytes);
    }

    private static String generateA1Hash(
            String bootstrapTransactionId, String realm, byte[] gbaKey) {
        MessageDigest md5Digest = getMd5Digest();

        String gbaKeyBase64 = BaseEncoding.base64().encode(gbaKey);
        String hashInput = String.join(":", bootstrapTransactionId, realm, gbaKeyBase64);
        md5Digest.update(hashInput.getBytes());

        return base16(md5Digest.digest());
    }

    private static String generateA2Hash(String method, String requestUri) {
        MessageDigest md5Digest = getMd5Digest();
        md5Digest.update(String.join(":", method, requestUri).getBytes());
        return base16(md5Digest.digest());
    }

    private static String base16(byte[] input) {
        return BaseEncoding.base16().encode(input).toLowerCase(Locale.ROOT);
    }

    private static MessageDigest getMd5Digest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Couldn't find MD5 algorithm: " + e);
        }
    }

    private static class WorkaroundURI extends GenericURI {
        public WorkaroundURI(String uriString) {
            this.uriString = uriString;
            this.scheme = "";
        }
    }
}
