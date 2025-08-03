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

package com.android.libraries.rcs.simpleclient.filetransfer.requestexecutor;

import android.util.Log;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;

import java.net.HttpURLConnection;
import java.util.concurrent.Executors;

/** Executes GBA authenticated HTTP requests. */
public class GbaRequestExecutor implements HttpRequestExecutor {

    private static final String TAG = "GbaRequestExecutor";
    private final GbaAuthenticationProvider gbaAuthenticationProvider;
    private final ListeningExecutorService executor =
            MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4));

    public GbaRequestExecutor(GbaAuthenticationProvider gbaAuthenticationProvider) {
        this.gbaAuthenticationProvider = gbaAuthenticationProvider;
    }

    @Override
    @SuppressWarnings("CheckReturnValue")
    public ListenableFuture<HttpResponse> executeAuthenticatedRequest(
            DefaultHttpClient httpClient, HttpContext context, HttpRequestBase request,
            AuthScheme authScheme) {

        // Set authentication for the client.
        ListenableFuture<Credentials> credentialsFuture =
                gbaAuthenticationProvider.provideCredentials(/*forceBootrapping*/ false);

        ListenableFuture<HttpResponse> responseFuture =
                Futures.transformAsync(
                        credentialsFuture,
                        credentials -> {
                            Log.i(TAG,
                                    "Obtained credentialsFuture, making the POST with credentials"
                                            + ".");
                            httpClient.addRequestInterceptor((req, ctx) -> {
                                AuthState authState = (AuthState) context.getAttribute(
                                        ClientContext.TARGET_AUTH_STATE);
                                authState.setAuthScope(AuthScope.ANY);
                                authState.setAuthScheme(authScheme);
                                authState.setCredentials(credentials);
                            }, /* index= */ 0);

                            // Make the first request.
                            return executor.submit(() -> httpClient.execute(request, context));
                        },
                        executor);

        return Futures.transformAsync(
                responseFuture,
                response -> {

                    // If the response code is 401, the keys might be invalid so force boostrapping.
                    if (response.getStatusLine().getStatusCode()
                            != HttpURLConnection.HTTP_UNAUTHORIZED) {
                        return Futures.immediateFuture(response);
                    }
                    Log.i(TAG, "Obtained 401 for the authneticated request. Forcing boostrapping.");

                    ListenableFuture<Credentials> forceBootstrappedCredentialsFuture =
                            gbaAuthenticationProvider.provideCredentials(/*forceBoostrapping*/
                                    true);

                    return Futures.transformAsync(
                            forceBootstrappedCredentialsFuture,
                            forceBootstrappedCredentials -> {
                                httpClient
                                        .getCredentialsProvider()
                                        .setCredentials(AuthScope.ANY,
                                                forceBootstrappedCredentials);

                                // Make a second request.
                                Log.i(TAG,
                                        "Obtained new credentialsFuture, making POST with the new"
                                                + " credentials.");
                                return Futures.submit(() -> httpClient.execute(request, context),
                                        executor);
                            },
                            executor);
                },
                executor);
    }
}
