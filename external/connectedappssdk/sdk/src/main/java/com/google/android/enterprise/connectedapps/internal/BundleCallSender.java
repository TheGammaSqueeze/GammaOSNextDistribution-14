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

import static com.google.android.enterprise.connectedapps.CrossProfileSender.MAX_BYTES_PER_BLOCK;
import static com.google.android.enterprise.connectedapps.internal.BundleCallReceiver.STATUS_INCLUDES_BUNDLES;
import static com.google.android.enterprise.connectedapps.internal.BundleCallReceiver.STATUS_INCOMPLETE;
import static com.google.android.enterprise.connectedapps.internal.BundleCallReceiver.bytesRefersToBundle;

import android.os.Bundle;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.TransactionTooLargeException;
import android.util.Log;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

/**
 * This represents a single action of (sending a {@link Parcel} and possibly fetching a response,
 * which may be split up over many calls (if the payload is large).
 *
 * <p>The receiver should relay calls to a {@link BundleCallReceiver}.
 */
abstract class BundleCallSender {

  private static final String LOG_TAG = "BundleCallSender";

  private static final long RETRY_DELAY_MILLIS = 10;
  private static final int MAX_RETRIES = 10;

  /**
   * The arguments passed to this should be passed to {@link BundleCallReceiver#prepareCall(long,
   * int, int, byte[])}.
   */
  abstract void prepareCall(long callId, int blockId, int totalBytes, byte[] bytes)
      throws RemoteException;

  /**
   * The arguments passed to this should be passed to {@link BundleCallReceiver#prepareBundle(long,
   * int, Bundle)}.
   */
  abstract void prepareBundle(long callId, int bundleId, Bundle bundle) throws RemoteException;

  private void prepareCallAndRetry(
      long callId, int blockId, int totalBytes, byte[] bytes, int retries) throws RemoteException {
    while (true) {
      try {
        prepareCall(callId, blockId, totalBytes, bytes);
        break;
      } catch (TransactionTooLargeException e) {
        if (retries-- <= 0) {
          throw e;
        }

        try {
          Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException ex) {
          Log.w(LOG_TAG, "Interrupted on prepare retry", ex);
          // If we can't sleep we'll just try again immediately
        }
      }
    }
  }

  private void prepareBundleAndRetry(long callId, int bundleId, Bundle bundle, int retries)
      throws RemoteException {
    while (true) {
      try {
        prepareBundle(callId, bundleId, bundle);
        break;
      } catch (TransactionTooLargeException e) {
        if (retries-- <= 0) {
          throw e;
        }

        try {
          Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException ex) {
          Log.w(LOG_TAG, "Interrupted on prepare retry", ex);
          // If we can't sleep we'll just try again immediately
        }
      }
    }
  }

  /**
   * The arguments passed to this should be passed to {@link
   * BundleCallReceiver#getPreparedCall(long, int, byte[])} and used to complete the call.
   */
  abstract byte[] call(long callId, int blockId, byte[] bytes) throws RemoteException;

  private byte[] callAndRetry(long callId, int blockId, byte[] bytes, int retries)
      throws RemoteException {
    while (true) {
      try {
        return call(callId, blockId, bytes);
      } catch (TransactionTooLargeException e) {
        if (retries-- <= 0) {
          throw e;
        }

        try {
          Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException ex) {
          Log.w(LOG_TAG, "Interrupted on prepare retry", ex);
          // If we can't sleep we'll just try again immediately
        }
      }
    }
  }

  /**
   * The arguments passed to this should be passed to {@link
   * BundleCallReceiver#getPreparedResponse(long, int)}.
   */
  abstract byte[] fetchResponse(long callId, int blockId) throws RemoteException;

  /**
   * The arguments passed to this should be passed to {@link
   * BundleCallReceiver#getPreparedResponseBundle(long, int)}.
   */
  abstract Bundle fetchResponseBundle(long callId, int bundleId) throws RemoteException;

  private byte[] fetchResponseAndRetry(long callId, int blockId, int retries)
      throws RemoteException {
    while (true) {
      try {
        return fetchResponse(callId, blockId);
      } catch (TransactionTooLargeException e) {
        if (retries-- <= 0) {
          throw e;
        }

        try {
          Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException ex) {
          Log.w(LOG_TAG, "Interrupted on prepare retry", ex);
          // If we can't sleep we'll just try again immediately
        }
      }
    }
  }

  private Bundle fetchResponseBundleAndRetry(long callId, int bundleId, int retries)
      throws RemoteException {
    while (true) {
      try {
        Bundle b = fetchResponseBundle(callId, bundleId);
        b.setClassLoader(Bundler.class.getClassLoader());
        return b;
      } catch (TransactionTooLargeException e) {
        if (retries-- <= 0) {
          throw e;
        }

        try {
          Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException ex) {
          Log.w(LOG_TAG, "Interrupted on prepare retry", ex);
          // If we can't sleep we'll just try again immediately
        }
      }
    }
  }

  /**
   * Use the prepareCall(long, int, int, byte[])} and {@link #call(long, int, byte[])} methods to
   * make a call.
   *
   * @throws UnavailableProfileException if any call fails
   */
  public Bundle makeBundleCall(Bundle bundle) throws UnavailableProfileException {
    long callIdentifier = UUID.randomUUID().getMostSignificantBits();

    Parcel parcel = Parcel.obtain();
    bundle.writeToParcel(parcel, /* flags= */ 0);
    parcel.setDataPosition(0);

    byte[] bytes;

    try {
      bytes = parcel.marshall();
    } catch (RuntimeException | AssertionError e) {
      // We can't marshall the parcel so we send the bundle directly
      try {
        prepareBundleAndRetry(callIdentifier, /* bundleId= */ 0, bundle, MAX_RETRIES);
      } catch (RemoteException e1) {
        throw new UnavailableProfileException("Error passing bundle for call", e1);
      }
      bytes = new byte[] {STATUS_INCLUDES_BUNDLES};
    } finally {
      parcel.recycle();
    }

    byte[] returnBytes = makeParcelCall(callIdentifier, bytes);

    if (returnBytes.length == 0) {
      return null;
    }

    if (bytesRefersToBundle(returnBytes)) {
      try {
        return fetchResponseBundleAndRetry(callIdentifier, /* bundleId= */ 0, MAX_RETRIES);
      } catch (RemoteException e) {
        throw new UnavailableProfileException("Error fetching bundle for response", e);
      }
    }

    Parcel returnParcel = fetchResponseParcel(callIdentifier, returnBytes);
    Bundle returnBundle = new Bundle(Bundler.class.getClassLoader());
    returnBundle.readFromParcel(returnParcel);
    returnParcel.recycle();

    return returnBundle;
  }

  private boolean bytesAreIncomplete(byte[] bytes) {
    return bytes[0] == STATUS_INCOMPLETE;
  }

  private byte[] makeParcelCall(long callIdentifier, byte[] bytes)
      throws UnavailableProfileException {
    try {
      int numberOfBlocks = (int) Math.ceil(bytes.length * 1.0 / MAX_BYTES_PER_BLOCK);
      int blockIdentifier = 0;

      if (numberOfBlocks > 1) {
        byte[] block = new byte[MAX_BYTES_PER_BLOCK];

        // Loop through all but the last one and send them over to be cached (retrying any failures)
        while (blockIdentifier < numberOfBlocks - 1) {
          System.arraycopy(
              bytes, blockIdentifier * MAX_BYTES_PER_BLOCK, block, 0, MAX_BYTES_PER_BLOCK);

          // Since we know block size is below the limit any errors will be temporary so we should
          // retry
          prepareCallAndRetry(callIdentifier, blockIdentifier, bytes.length, block, MAX_RETRIES);
          blockIdentifier++;
        }

        bytes = Arrays.copyOfRange(bytes, blockIdentifier * MAX_BYTES_PER_BLOCK, bytes.length);
      }

      // Since we know block size is below the limit any errors will be temporary so we should retry
      return callAndRetry(callIdentifier, blockIdentifier, bytes, MAX_RETRIES);
    } catch (RemoteException e) {
      throw new UnavailableProfileException("Could not access other profile", e);
    }
  }

  private Parcel fetchResponseParcel(long callIdentifier, byte[] returnBytes)
      throws UnavailableProfileException {

    // returnBytes[0] is 0 if the bytes are complete, or 1 if we need to fetch more
    int byteOffset = 1;
    if (bytesAreIncomplete(returnBytes)) {
      // returnBytes[1] - returnBytes[4] are an int representing the total size of the return
      // value
      int totalBytes = ByteBuffer.wrap(returnBytes).getInt(/* index= */ 1);

      try {
        returnBytes = fetchReturnBytes(totalBytes, callIdentifier, returnBytes);
      } catch (RemoteException e) {
        throw new UnavailableProfileException("Could not access other profile", e);
      }
      byteOffset = 0;
    }
    Parcel p = Parcel.obtain(); // Recycled by caller
    p.unmarshall(
        returnBytes, /* offset= */ byteOffset, /* length= */ returnBytes.length - byteOffset);
    p.setDataPosition(0);
    return p;
  }

  private byte[] fetchReturnBytes(int totalBytes, long callId, byte[] initialBytes)
      throws RemoteException {
    byte[] returnBytes = new byte[totalBytes];

    // Skip the first 5 bytes which are used for status
    System.arraycopy(
        initialBytes,
        /* srcPos= */ 5,
        returnBytes,
        /* destPos= */ 0,
        /* length= */ MAX_BYTES_PER_BLOCK);

    int numberOfBlocks = (int) Math.ceil(totalBytes * 1.0 / MAX_BYTES_PER_BLOCK);

    for (int block = 1; block < numberOfBlocks; block++) { // Skip 0 as we already have it
      // Since we know block size is below the limit any errors will be temporary so we should retry
      byte[] bytes = fetchResponseAndRetry(callId, block, MAX_RETRIES);
      System.arraycopy(
          bytes,
          /* srcPos= */ 0,
          returnBytes,
          /* destPos= */ block * MAX_BYTES_PER_BLOCK,
          /* length= */ bytes.length);
    }
    return returnBytes;
  }
}
