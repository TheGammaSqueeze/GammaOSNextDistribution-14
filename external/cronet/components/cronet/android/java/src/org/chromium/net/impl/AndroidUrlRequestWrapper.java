// Copyright 2023 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.chromium.net.UrlResponseInfo;

import java.nio.ByteBuffer;

@RequiresApi(34)
class AndroidUrlRequestWrapper extends org.chromium.net.ExperimentalUrlRequest {
    private final android.net.http.UrlRequest mBackend;

    AndroidUrlRequestWrapper(android.net.http.UrlRequest backend) {
        this.mBackend = backend;
    }

    @Override
    public void start() {
        mBackend.start();
    }

    @Override
    public void followRedirect() {
        mBackend.followRedirect();
    }

    @Override
    public void read(ByteBuffer buffer) {
        mBackend.read(buffer);
    }

    @Override
    public void cancel() {
        mBackend.cancel();
    }

    @Override
    public boolean isDone() {
        return mBackend.isDone();
    }

    @Override
    public void getStatus(StatusListener listener) {
        mBackend.getStatus(new UrlRequestStatusListenerWrapper(listener));
    }

    @Override
    public int getTrafficStatsUid() {
        return mBackend.getTrafficStatsUid();
    }

    @Override
    public int getPriority() {
        return mBackend.getPriority();
    }

    @Override
    public boolean hasTrafficStatsTag() {
        return mBackend.hasTrafficStatsTag();
    }

    @Override
    public boolean hasTrafficStatsUid() {
        return mBackend.hasTrafficStatsUid();
    }

    @Override
    public int getTrafficStatsTag() {
        return mBackend.getTrafficStatsTag();
    }

    @Override
    public boolean isDirectExecutorAllowed() {
        return mBackend.isDirectExecutorAllowed();
    }

    @Override
    public boolean isCacheDisabled() {
        return mBackend.isCacheDisabled();
    }

    @NonNull
    @Override
    public UrlResponseInfo.HeaderBlock getHeaders() {
        return new AndroidHeaderBlockWrapper(mBackend.getHeaders());
    }

    @Nullable
    @Override
    public String getHttpMethod() {
        return mBackend.getHttpMethod();
    }
}
