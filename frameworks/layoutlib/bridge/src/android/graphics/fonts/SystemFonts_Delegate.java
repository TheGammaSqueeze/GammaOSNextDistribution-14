/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.graphics.fonts;

import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.text.FontConfig;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Locale;
import java.util.Map;

/**
 * Delegate implementing the native methods of android.graphics.fonts.SystemFonts
 * <p>
 * Through the layoutlib_create tool, the original native methods of SystemFonts have been
 * replaced by calls to methods of the same name in this delegate class.
 * <p>
 * This class behaves like the original native implementation, but in Java, keeping previously
 * native data into its own objects and mapping them to int that are sent back and forth between it
 * and the original SystemFonts class.
 *
 */
public class SystemFonts_Delegate {

    private static final String TAG = "SystemFonts_Delegate";
    private static String sFontLocation;
    public static boolean sIsTypefaceInitialized = false;

    public static void setFontLocation(String fontLocation) {
        sFontLocation = fontLocation;
    }

    @LayoutlibDelegate
    /*package*/ static FontConfig getSystemFontConfigInternal(
            String fontsXml,
            String systemFontDir,
            String oemXml,
            String productFontDir,
            Map<String, File> updatableFontMap,
            long lastModifiedDate,
            int configVersion) {
        sIsTypefaceInitialized = true;
        return SystemFonts.getSystemFontConfigInternal_Original(
            sFontLocation + "fonts.xml", sFontLocation, null, null, updatableFontMap,
            lastModifiedDate, configVersion);
    }

    @LayoutlibDelegate
    /*package*/ static ByteBuffer mmap(@NonNull String fullPath) {
        // Android does memory mapping for font files. But Windows keeps files open
        // until the byte buffer from the memory mapping is garbage collected.
        // To avoid that, on Windows, read the file into a byte buffer instead.
        // See JDK-4715154.
        String osName = System.getProperty("os.name").toLowerCase(Locale.US);
        if (osName.startsWith("windows")) {
            try (FileInputStream file = new FileInputStream(fullPath)) {
                final FileChannel fileChannel = file.getChannel();
                final int size = (int) fileChannel.size();
                // Native code requires the ByteBuffer to be direct
                // (see android/graphics/fonts/Font.cpp)
                ByteBuffer buffer = ByteBuffer.allocateDirect(size);
                fileChannel.read(buffer);
                return buffer;
            } catch (IOException e) {
                Log.e(TAG, "Error mapping font file " + fullPath);
                return null;
            }
        } else {
            return SystemFonts.mmap_Original(fullPath);
        }
    }
}