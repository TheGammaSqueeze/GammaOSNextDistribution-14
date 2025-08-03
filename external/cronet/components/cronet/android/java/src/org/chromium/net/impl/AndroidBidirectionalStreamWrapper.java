// Copyright 2023 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net.impl;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.chromium.net.UrlResponseInfo;

import java.nio.ByteBuffer;

@RequiresApi(api = 34)
class AndroidBidirectionalStreamWrapper extends org.chromium.net.ExperimentalBidirectionalStream {
    private final android.net.http.BidirectionalStream mBackend;

    AndroidBidirectionalStreamWrapper(android.net.http.BidirectionalStream backend) {
        this.mBackend = backend;
    }

    @Override
    public void start() {
        mBackend.start();
    }

    @Override
    public void read(ByteBuffer buffer) {
        mBackend.read(buffer);
    }

    @Override
    public void write(ByteBuffer buffer, boolean endOfStream) {
        mBackend.write(buffer, endOfStream);
    }

    @Override
    public void flush() {
        mBackend.flush();
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
    public boolean isDelayRequestHeadersUntilFirstFlushEnabled() {
        return mBackend.isDelayRequestHeadersUntilFirstFlushEnabled();
    }

    @Override
    public int getPriority() {
        return mBackend.getPriority();
    }

    @NonNull
    @Override
    public UrlResponseInfo.HeaderBlock getHeaders() {
        return new AndroidHeaderBlockWrapper(mBackend.getHeaders());
    }

    @Override
    public int getTrafficStatsUid() {
        return mBackend.getTrafficStatsUid();
    }

    @Override
    public int getTrafficStatsTag() {
        return mBackend.getTrafficStatsTag();
    }

    @Override
    public boolean hasTrafficStatsUid() {
        return mBackend.hasTrafficStatsUid();
    }

    @Override
    public boolean hasTrafficStatsTag() {
        return mBackend.hasTrafficStatsTag();
    }

    @NonNull
    @Override
    public String getHttpMethod() {
        return mBackend.getHttpMethod();
    }
}
