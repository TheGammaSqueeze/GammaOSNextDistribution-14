/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.wearable.cts;

import android.app.ambientcontext.AmbientContextEventRequest;
import android.app.ambientcontext.AmbientContextManager;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.SharedMemory;
import android.service.ambientcontext.AmbientContextDetectionResult;
import android.service.ambientcontext.AmbientContextDetectionServiceStatus;
import android.service.wearable.WearableSensingDataRequester;
import android.service.wearable.WearableSensingService;
import android.util.Log;

import com.google.common.collect.HashMultimap;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** An implementation of {@link WearableSensingService} for CTS testing. */
public class CtsWearableSensingService extends WearableSensingService {
    private static final String TAG = "CtsWearableSensingService";
    private static final String FAKE_APP_PACKAGE = "foo.bar.baz";
    private static final int INITIAL_STATUS_TO_CONSUME = -1;

    private static final AmbientContextDetectionServiceStatus INITIAL_SERVICE_STATUS_TO_CONSUME =
            new AmbientContextDetectionServiceStatus.Builder(FAKE_APP_PACKAGE).setStatusCode(
                    AmbientContextManager.STATUS_UNKNOWN).build();

    private static CountDownLatch sRespondLatch = new CountDownLatch(1);
    private static Consumer<Integer> sStatusConsumer;
    private static int sStatusToConsume;
    private static ParcelFileDescriptor sParcelFileDescriptor;
    private static SharedMemory sSharedMemory;
    private static PersistableBundle sData;

    private static Consumer<AmbientContextDetectionResult> sDetectionResultConsumer;
    private static Consumer<AmbientContextDetectionServiceStatus>
            sAmbientContextDetectionServiceStatusConsumer;
    private static AmbientContextDetectionServiceStatus sServiceStatusToConsume;
    private static String sPackageName = null;
    private static final HashMultimap<Integer, WearableSensingDataRequester> sDataRequesters =
            HashMultimap.create();
    private static boolean sOnStopHotwordRecognitionCalled = false;
    private static boolean sShouldCallParentAndReturn = false;

    @Override
    public void onDataStreamProvided(ParcelFileDescriptor parcelFileDescriptor,
            Consumer<Integer> statusConsumer) {
        Log.w(TAG, "onDataStreamProvided");
        sParcelFileDescriptor = parcelFileDescriptor;
        sStatusConsumer = statusConsumer;
        sRespondLatch.countDown();
    }

    @Override
    public void onDataProvided(PersistableBundle data, SharedMemory sharedMemory,
            Consumer<Integer> statusConsumer) {
        Log.w(TAG, "onDataProvided");
        sData = data;
        sSharedMemory = sharedMemory;
        sStatusConsumer = statusConsumer;
        sRespondLatch.countDown();
    }

    @Override
    public void onDataRequestObserverRegistered(
            int dataType,
            String packageName,
            WearableSensingDataRequester dataRequester,
            Consumer<Integer> statusConsumer) {
        if (sShouldCallParentAndReturn) {
            super.onDataRequestObserverRegistered(
                    dataType, packageName, dataRequester, statusConsumer);
            return;
        }
        sDataRequesters.put(dataType, dataRequester);
        sPackageName = packageName;
        sStatusConsumer = statusConsumer;
        sRespondLatch.countDown();
    }

    @Override
    public void onDataRequestObserverUnregistered(
            int dataType,
            String packageName,
            WearableSensingDataRequester dataRequester,
            Consumer<Integer> statusConsumer) {
        if (sShouldCallParentAndReturn) {
            super.onDataRequestObserverUnregistered(
                    dataType, packageName, dataRequester, statusConsumer);
            return;
        }
        sDataRequesters.remove(dataType, dataRequester);
        sPackageName = packageName;
        sStatusConsumer = statusConsumer;
        sRespondLatch.countDown();
    }

    @Override
    public void onStopHotwordRecognition(Consumer<Integer> statusConsumer) {
        if (sShouldCallParentAndReturn) {
            super.onStopHotwordRecognition(statusConsumer);
            return;
        }
        Log.i(TAG, "onStopHotwordRecognition");
        sOnStopHotwordRecognitionCalled = true;
        sStatusConsumer = statusConsumer;
        sRespondLatch.countDown();
    }

    @Override
    public void onStartDetection(AmbientContextEventRequest request,
            String packageName,
            Consumer<AmbientContextDetectionServiceStatus> statusConsumer,
            Consumer<AmbientContextDetectionResult> detectionResultConsumer) {
        Log.w(TAG, "onStartDetection");
        sPackageName = packageName;
        sDetectionResultConsumer = detectionResultConsumer;
        sAmbientContextDetectionServiceStatusConsumer = statusConsumer;
        sRespondLatch.countDown();
    }

    @Override
    public void onStopDetection(String packageName) {
        Log.w(TAG, "onStopDetection");
        sPackageName = packageName;
        sRespondLatch.countDown();
    }

    @Override
    public void onQueryServiceStatus(Set<Integer> eventTypes,
            String packageName,
            Consumer<AmbientContextDetectionServiceStatus> consumer) {
        Log.w(TAG, "onQueryServiceStatus");
        sPackageName = packageName;
        sAmbientContextDetectionServiceStatusConsumer = consumer;
        sRespondLatch.countDown();
    }

    public static void reset() {
        sRespondLatch = new CountDownLatch(1);
        sStatusConsumer = null;
        sStatusToConsume = INITIAL_STATUS_TO_CONSUME;
        sParcelFileDescriptor = null;
        sSharedMemory = null;
        sData = null;
        sDetectionResultConsumer = null;
        sAmbientContextDetectionServiceStatusConsumer = null;
        sServiceStatusToConsume = null;
        sPackageName = null;
        sDataRequesters.clear();
        sOnStopHotwordRecognitionCalled = false;
        sShouldCallParentAndReturn = false;
    }

    public static void whenCallbackTriggeredRespondWithStatus(int status) {
        Log.w(TAG, "whenCallbackTriggeredRespondWithStatus");
        sStatusToConsume = status;
    }

    public static void whenCallbackTriggeredRespondWithServiceStatus(int status) {
        sServiceStatusToConsume = new AmbientContextDetectionServiceStatus.Builder(
                FAKE_APP_PACKAGE).setStatusCode(status).build();
    }

    public static void expectTimeOut() {
        try {
            if (!sRespondLatch.await(3000, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Timed out waiting for result, this is expected");
            } else {
                throw new AssertionError("CtsWearableSensingService"
                        + " expected timeout but did not timeout.");
            }
            // reset for next
            sRespondLatch = new CountDownLatch(1);
            sStatusToConsume = AmbientContextManager.STATUS_UNKNOWN;
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage());
            Thread.currentThread().interrupt();
            throw new AssertionError("Got InterruptedException while waiting for serviceStatus.");
        }
    }

    public static void awaitResultAmbientContextDetectionService() {
        try {
            if (!sRespondLatch.await(3000, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("CtsWearableSensingService"
                        + " timed out while expecting a call.");
            }
            sAmbientContextDetectionServiceStatusConsumer.accept(sServiceStatusToConsume);
            // reset for next
            sRespondLatch = new CountDownLatch(1);
            sServiceStatusToConsume = INITIAL_SERVICE_STATUS_TO_CONSUME;
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage());
            Thread.currentThread().interrupt();
            throw new AssertionError("Got InterruptedException while waiting for serviceStatus.");
        }
    }

    public static void awaitResult() {
        try {
            if (!sRespondLatch.await(3000, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("CtsWearableSensingService"
                        + " timed out while expecting a call.");
            }
            Log.i(TAG, "Sending status " + sStatusToConsume);
            sStatusConsumer.accept(sStatusToConsume);
            // reset for next
            sRespondLatch = new CountDownLatch(1);
            sStatusToConsume = INITIAL_STATUS_TO_CONSUME;
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage());
            Thread.currentThread().interrupt();
            throw new AssertionError("Got InterruptedException while waiting for serviceStatus.");
        }
    }


    public static PersistableBundle getData() {
        return sData;
    }

    public static ParcelFileDescriptor getParcelFileDescriptor() {
        return sParcelFileDescriptor;
    }

    public static String getLastCallingPackage() {
        return sPackageName;
    }

    /** Gets the data requesters registered for the provided data type. */
    public static Set<WearableSensingDataRequester> getDataRequesters(int dataType) {
        return sDataRequesters.get(dataType);
    }

    public static boolean getOnStopHotwordRecognitionCalled() {
        return sOnStopHotwordRecognitionCalled;
    }

    /**
     * Configures this WearableSensingService to delegate to its parent when a non-abstract method
     * is called.
     */
    public static void configureMethodsToCallParentAndReturn() {
        sShouldCallParentAndReturn = true;
    }
}
