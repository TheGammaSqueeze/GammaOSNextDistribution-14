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

package com.android.car.bugreport;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Environment;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;
import android.window.ScreenCapture;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;


final class ScreenshotUtils {
    private static final String TAG = ScreenshotUtils.class.getSimpleName();

    private static final float TITLE_TEXT_SIZE = 30;
    private static final float TITLE_TEXT_MARGIN = 10;
    private static final String SCREENSHOT_FILE_EXTENSION = "png";
    private static final Bitmap.CompressFormat SCREENSHOT_BITMAP_COMPRESS_FORMAT =
            Bitmap.CompressFormat.PNG;
    private static final Bitmap.Config SCREENSHOT_BITMAP_CONFIG = Bitmap.Config.ARGB_8888;

    /**
     * Gets a screenshot directory in the Environment.getExternalStorageDirectory(). Creates if the
     * directory doesn't exist.
     */
    @Nullable
    public static String getScreenshotDir() {
        File filesDir = Environment.getExternalStorageDirectory();
        if (filesDir == null) {
            Log.e(TAG, "Failed to create a directory, filesDir is null.");
            return null;
        }

        String dir = filesDir.getAbsolutePath() + "/screenshots";
        File storeDirectory = new File(dir);
        if (!storeDirectory.exists()) {
            if (!storeDirectory.mkdirs()) {
                Log.e(TAG, "Failed to create file storage directory.");
                return null;
            }
        }

        return dir;
    }

    /** Takes screenshots of all displays and stores them to a storage. */
    public static void takeScreenshot(@NonNull Context context, @Nullable Car car) {
        Log.i(TAG, "takeScreenshot is started.");

        CarOccupantZoneManager carOccupantZoneManager = null;
        if (car != null) {
            carOccupantZoneManager = (CarOccupantZoneManager) car.getCarManager(
                    Car.CAR_OCCUPANT_ZONE_SERVICE);
        }

        Set<Integer> displayIds = getDisplayIds(context, carOccupantZoneManager);
        List<Bitmap> images = new ArrayList<>();
        for (int displayId : displayIds) {
            Bitmap image = takeScreenshotOfDisplay(displayId);
            if (image == null) {
                continue;
            }
            image = addTextToImage(image, "Display ID: " + displayId);
            images.add(image);
        }

        if (images.size() == 0) {
            Log.w(TAG, "There is no screenshot taken successfully.");
            return;
        }

        Bitmap fullImage = mergeImagesVertically(images);

        storeImage(fullImage, getScreenshotFilename());
        Log.i(TAG, "takeScreenshot is finished.");
    }

    /**
     * Gets all display ids including a cluster display id if possible. It requires a permission
     * android.car.permission.ACCESS_PRIVATE_DISPLAY_ID to get a cluster display's id.
     */
    @NonNull
    private static Set<Integer> getDisplayIds(@NonNull Context context,
            @Nullable CarOccupantZoneManager carOccupantZoneManager) {
        Set<Integer> displayIds = new HashSet<>();

        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        if (displayManager == null) {
            Log.e(TAG, "Failed to get DisplayManager.");
            return displayIds;
        }

        Display[] displays = displayManager.getDisplays(
                DisplayManager.DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED);
        for (Display display : displays) {
            displayIds.add(display.getDisplayId());
        }

        OptionalInt clusterDisplayId = getClusterDisplayId(context, carOccupantZoneManager);
        if (clusterDisplayId.isPresent()) {
            displayIds.add(clusterDisplayId.getAsInt());
        }

        Log.d(TAG, "Display ids : " + displayIds);

        return displayIds;
    }

    /** Gets cluster display id if possible. Or returns an empty instance. */
    private static OptionalInt getClusterDisplayId(@NonNull Context context,
            @Nullable CarOccupantZoneManager carOccupantZoneManager) {
        if (context.checkSelfPermission(Car.ACCESS_PRIVATE_DISPLAY_ID)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "android.car.permission.ACCESS_PRIVATE_DISPLAY_ID is not granted.");
            return OptionalInt.empty();
        }
        if (carOccupantZoneManager == null) {
            Log.w(TAG, "CarOccupantZoneManager is null.");
            return OptionalInt.empty();
        }

        int clusterDisplayId = carOccupantZoneManager.getDisplayIdForDriver(
                CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER);
        return OptionalInt.of(clusterDisplayId);
    }

    /** Gets filename of screenshot based on the current time. */
    private static String getScreenshotFilename() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(
                ZoneId.systemDefault());
        String nowInDateTimeFormat = formatter.format(Instant.now());
        return "extra_screenshot_" + nowInDateTimeFormat + "." + SCREENSHOT_FILE_EXTENSION;
    }

    /** Adds a text to the top of the image. */
    @NonNull
    private static Bitmap addTextToImage(@NonNull Bitmap image, String text) {
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(TITLE_TEXT_SIZE);
        Rect textBounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), textBounds);

        float extraHeight = textBounds.height() + TITLE_TEXT_MARGIN * 2;

        Bitmap imageWithTitle = Bitmap.createBitmap(image.getWidth(),
                image.getHeight() + (int) extraHeight, image.getConfig());
        Canvas canvas = new Canvas(imageWithTitle);
        canvas.drawColor(Color.WHITE);
        canvas.drawText(text, TITLE_TEXT_MARGIN,
                extraHeight - TITLE_TEXT_MARGIN - textBounds.bottom, paint);
        canvas.drawBitmap(image, 0, extraHeight, null);
        return imageWithTitle;
    }

    @NonNull
    private static Bitmap mergeImagesVertically(@NonNull List<Bitmap> images) {
        int width = images.stream().mapToInt(Bitmap::getWidth).max().orElse(0);
        int height = images.stream().mapToInt(Bitmap::getHeight).sum();

        Bitmap mergedImage = Bitmap.createBitmap(width, height, SCREENSHOT_BITMAP_CONFIG);
        Canvas canvas = new Canvas(mergedImage);
        canvas.drawColor(Color.WHITE);

        float curHeight = 0;
        for (Bitmap image : images) {
            canvas.drawBitmap(image, 0f, curHeight, null);
            curHeight += image.getHeight();
        }
        return mergedImage;
    }

    /** Stores an image with the given fileName. */
    private static void storeImage(@NonNull Bitmap image, String fileName) {
        String screenshotDir = getScreenshotDir();
        if (screenshotDir == null) {
            return;
        }

        String filePath = screenshotDir + "/" + fileName;
        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            image.compress(SCREENSHOT_BITMAP_COMPRESS_FORMAT, 100, fos);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File " + filePath + " not found to store screenshot.", e);
            return;
        }

        Log.i(TAG, "Screenshot is stored in " + filePath);
    }

    /** Takes screenshots of the certain display. Returns null if it fails to take a screenshot. */
    @Nullable
    private static Bitmap takeScreenshotOfDisplay(int displayId) {
        Log.d(TAG, "Take screenshot of display " + displayId);
        IWindowManager windowManager = WindowManagerGlobal.getWindowManagerService();

        ScreenCapture.CaptureArgs captureArgs = new ScreenCapture.CaptureArgs.Builder<>().build();
        ScreenCapture.SynchronousScreenCaptureListener syncScreenCaptureListener =
                ScreenCapture.createSyncCaptureListener();

        try {
            windowManager.captureDisplay(displayId, captureArgs, syncScreenCaptureListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to take screenshot", e);
            return null;
        }

        final ScreenCapture.ScreenshotHardwareBuffer screenshotBuffer =
                syncScreenCaptureListener.getBuffer();
        if (screenshotBuffer == null) {
            return null;
        }
        return screenshotBuffer.asBitmap().copy(SCREENSHOT_BITMAP_CONFIG, /* isMutable= */ true);
    }
}
