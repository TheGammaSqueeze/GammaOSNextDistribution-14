/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.enterprise.connectedapps.internal;

import android.os.Bundle;
import android.os.Parcel;
import com.google.android.enterprise.connectedapps.CrossProfileSender;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Build up parcels over multiple calls and prepare responses.
 *
 * <p>This is the counterpart to {@link BundleCallSender}. Calls by the {@link BundleCallSender}
 * should be relayed to an instance of this class.
 */
public final class BundleCallReceiver {

  static final byte STATUS_COMPLETE = 0;
  static final byte STATUS_INCOMPLETE = 1;
  static final byte STATUS_INCLUDES_BUNDLES = 2;

  private final Map<Long, byte[]> preparedCalls = new HashMap<>();
  private final Map<Long, Integer> preparedCallParts = new HashMap<>();
  private final Map<Long, byte[]> preparedResponses = new HashMap<>();
  private final Map<Long, Bundle> preparedCallBundles = new HashMap<>();
  private final Map<Long, Bundle> preparedResponseBundles = new HashMap<>();

  /**
   * Prepare a response to be returned by calls to {@link #getPreparedResponse(long, int)}.
   *
   * <p>The {@code byte[]} returned will begin with a 0 if all can be contained in a single call and
   * 1 if further calls to {@link #getPreparedResponse(long, int)} are required. If the first byte
   * is a 1, then the following 4 bytes will be an {@link Integer} representing the total number of
   * bytes in the response.
   *
   * <p>The {@code byte[]} returned will consist only of a 2 if a bundle needs to be fetched using
   * {@link #getPreparedResponseBundle(long, int)}.
   */
  public byte[] prepareResponse(long callId, Bundle responseBundle) {
    Parcel responseParcel = Parcel.obtain();
    responseBundle.writeToParcel(responseParcel, /* flags= */ 0);

    byte[] responseBytes;
    try {
      responseBytes = responseParcel.marshall();
    } catch (Exception | AssertionError e) {
      prepareResponseBundle(callId, /* bundleId= */ 0, responseBundle);
      return new byte[] {STATUS_INCLUDES_BUNDLES};
    } finally {
      responseParcel.recycle();
    }

    if (responseBytes.length <= CrossProfileSender.MAX_BYTES_PER_BLOCK) {
      return ByteUtilities.joinByteArrays(new byte[] {STATUS_COMPLETE}, responseBytes);
    }
    // Record the bytes to be sent and send the first block
    preparedResponses.put(callId, responseBytes);
    byte[] response = new byte[CrossProfileSender.MAX_BYTES_PER_BLOCK + 5];
    response[0] = STATUS_INCOMPLETE;
    byte[] sizeBytes = ByteBuffer.allocate(4).putInt(responseBytes.length).array();
    System.arraycopy(sizeBytes, /* srcPos= */ 0, response, /* destPos= */ 1, /* length= */ 4);
    System.arraycopy(
        responseBytes,
        /* srcPos= */ 0,
        response,
        /* destPos= */ 5,
        /* length= */ CrossProfileSender.MAX_BYTES_PER_BLOCK);
    return response;
  }

  public void prepareBundle(long callId, int bundleId, Bundle bundle) {
    if (preparedCallBundles.containsKey(callId)) {
      throw new IllegalStateException("Already prepared bundle for call " + callId);
    }

    preparedCallBundles.put(callId, bundle);
  }

  private void prepareResponseBundle(long callId, int bundleId, Bundle bundle) {
    if (preparedResponseBundles.containsKey(callId)) {
      throw new IllegalStateException("Already prepared bundle for response " + callId);
    }

    preparedResponseBundles.put(callId, bundle);
  }

  public Bundle getPreparedResponseBundle(long callId, int bundleId) {
    return preparedResponseBundles.remove(callId);
  }

  /**
   * Prepare a call, storing one block of bytes for a call which will be completed with a call to
   * {@link #getPreparedCall(long, int, byte[])}.
   */
  public void prepareCall(long callId, int blockId, int numBytes, byte[] paramBytes) {
    if (!preparedCalls.containsKey(callId)) {
      preparedCalls.put(callId, new byte[numBytes]);
      preparedCallParts.put(callId, 0);
    }
    System.arraycopy(
        paramBytes,
        /* srcPos= */ 0,
        preparedCalls.get(callId),
        /* destPos= */ blockId * CrossProfileSender.MAX_BYTES_PER_BLOCK,
        /* length= */ CrossProfileSender.MAX_BYTES_PER_BLOCK);
    preparedCallParts.put(
        callId,
        preparedCallParts.get(callId)
            + 1
            + blockId); // +1 to have a difference when preparing the 0th block
  }

  /**
   * Fetch the full {@link Bundle} using bytes previously stored by calls to {@link
   * #prepareCall(long, int, int, byte[])}.
   *
   * <p>If this is the only block, then the {@code paramBytes} will be unmarshalled directly into a
   * {@link Bundle}.
   *
   * @throws IllegalStateException If this is not the only block, and any previous blocks are
   *     missing.
   */
  public Bundle getPreparedCall(long callId, int blockId, byte[] paramBytes) {
    if (bytesRefersToBundle(paramBytes)) {
      return preparedCallBundles.remove(callId);
    }

    if (blockId > 0) {
      int expectedBlocks = 0;
      for (int i = 0; i < blockId; i++) {
        expectedBlocks += 1 + i;
      }
      if (!preparedCallParts.containsKey(callId)
          || expectedBlocks != preparedCallParts.get(callId)) {
        throw new IllegalStateException("Call " + callId + " not prepared");
      }
      byte[] fullParamBytes = preparedCalls.get(callId);
      System.arraycopy(
          paramBytes,
          /* srcPos= */ 0,
          fullParamBytes,
          /* destPos= */ blockId * CrossProfileSender.MAX_BYTES_PER_BLOCK,
          /* length= */ paramBytes.length);
      paramBytes = fullParamBytes;
      preparedCalls.remove(callId);
      preparedCallParts.remove(callId);
    }

    Parcel parcel = Parcel.obtain();
    parcel.unmarshall(paramBytes, 0, paramBytes.length);
    parcel.setDataPosition(0);
    Bundle bundle = new Bundle(Bundler.class.getClassLoader());
    bundle.readFromParcel(parcel);
    parcel.recycle();
    return bundle;
  }

  static boolean bytesRefersToBundle(byte[] bytes) {
    return bytes[0] == STATUS_INCLUDES_BUNDLES;
  }

  /**
   * Get a block from a response previously prepared with {@link #prepareResponse(long, Bundle)}.
   *
   * <p>If this is the final block, then the prepared blocks will be dropped, and future calls to
   * this method will fail.
   */
  public byte[] getPreparedResponse(long callId, int blockId) {
    byte[] preparedBytes = preparedResponses.get(callId);
    byte[] response =
        Arrays.copyOfRange(
            preparedBytes,
            /* from= */ blockId * CrossProfileSender.MAX_BYTES_PER_BLOCK,
            /* to= */ Math.min(
                preparedBytes.length, (blockId + 1) * CrossProfileSender.MAX_BYTES_PER_BLOCK));
    int numberOfBlocks =
        (int) Math.ceil(preparedBytes.length * 1.0 / CrossProfileSender.MAX_BYTES_PER_BLOCK);
    if (blockId == numberOfBlocks - 1) {
      preparedResponses.remove(callId);
    }
    return response;
  }
}
