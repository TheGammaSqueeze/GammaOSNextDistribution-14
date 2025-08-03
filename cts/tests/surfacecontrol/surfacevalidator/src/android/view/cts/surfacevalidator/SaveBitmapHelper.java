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

package android.view.cts.surfacevalidator;

import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import org.junit.rules.TestName;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class SaveBitmapHelper {
    private static final String TAG = "SaveBitmapHelper";

    public static void saveBitmap(Bitmap failFrame, Class<?> clazz, TestName testName,
            String fileName) {
        String directoryName = Environment.getExternalStorageDirectory()
                + "/" + clazz.getSimpleName()
                + "/" + testName.getMethodName();
        File testDirectory = new File(directoryName);
        if (testDirectory.exists()) {
            String[] children = testDirectory.list();
            if (children != null) {
                for (String file : children) {
                    new File(testDirectory, file).delete();
                }
            }
        } else {
            testDirectory.mkdirs();
        }

        String bitmapName = fileName + ".png";
        Log.d(TAG, "Saving file : " + bitmapName + " in directory : " + directoryName);

        File file = new File(directoryName, bitmapName);
        try (FileOutputStream fileStream = new FileOutputStream(file)) {
            failFrame.compress(Bitmap.CompressFormat.PNG, 85, fileStream);
            fileStream.flush();
        } catch (IOException e) {
        }
    }
}
