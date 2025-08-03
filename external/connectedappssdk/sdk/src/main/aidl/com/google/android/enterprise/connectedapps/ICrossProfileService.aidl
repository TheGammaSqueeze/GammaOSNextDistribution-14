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
 package com.google.android.enterprise.connectedapps;

import com.google.android.enterprise.connectedapps.ICrossProfileCallback;
import android.os.Bundle;

interface ICrossProfileService {
  // When making a call containing params larger than
  // CrossProfileSender.MAX_BYTES_PER_BLOCK bytes, first split the marshalled
  // params parcel byte array into blocks of
  // CrossProfileSender.MAX_BYTES_PER_BLOCK bytes, and call prepareCall with
  // all but the final block.
  // callId is arbitrary and is used to link together calls to prepareCall and
  // call.
  // numBytes represents the full amount of bytes in total across all blocks
  // and is used to prepare the cache with the first use of prepareCall
  void prepareCall(long callId, int blockId, int numBytes, in byte[] params);


  void prepareBundle(long callId, int bundleId, in Bundle bundle);

  // When making a call with params smaller than
  // CrossProfileSender.MAX_BYTES_PER_BLOCK bytes bytes, or with the final
  // block in a larger call, this method is used.
  // crossProfileTypeIdentifier and methodIdentifier are used to identify the
  // method to call.
  byte[] call(long callId, int blockId, long crossProfileTypeIdentifier, int methodIdentifier, in byte[] params,
    ICrossProfileCallback callback);

  byte[] fetchResponse(long callId, int blockId);

  Bundle fetchResponseBundle(long callId, int bundleId);
}