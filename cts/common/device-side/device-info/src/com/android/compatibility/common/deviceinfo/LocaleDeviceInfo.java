/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.deviceinfo;

import android.icu.util.ULocale;
import android.icu.util.VersionInfo;
import android.util.Log;
import androidx.annotation.Nullable;
import com.android.compatibility.common.util.DeviceInfoStore;
import com.google.common.base.Strings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Locale device info collector.
 */
public final class LocaleDeviceInfo extends DeviceInfo {
    private static final String TAG = "LocaleDeviceInfo";

    private static final Pattern HYPHEN_BINARY_PATTERN = Pattern.compile("hyph-(.*?).hyb");
    private static final String HYPHEN_BINARY_LOCATION = "/system/usr/hyphen-data/";

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        List<String> locales = Arrays.asList(
                getInstrumentation().getContext().getAssets().getLocales());
        if (locales.isEmpty()) {
            // default locale
            locales.add("en_US");
        }
        store.addListResult("locale", locales);

        List<String> icuLocales = Arrays.stream(ULocale.getAvailableLocales())
            .map((uLocale -> uLocale.toLanguageTag()))
            .collect(Collectors.toList());
        if (icuLocales.isEmpty()) {
            // default locale
            icuLocales.add(ULocale.US.toLanguageTag());
        }
        store.addListResult("icu_locale", icuLocales);

        // Collect hyphenation supported locale
        List<String> hyphenLocalesList = new ArrayList<>();
        // Retrieve locale from the file name of binary
        try (Stream<Path> stream = Files.walk(Paths.get(HYPHEN_BINARY_LOCATION))) {
            hyphenLocalesList = stream
                    .filter(file -> !Files.isDirectory(file))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(HYPHEN_BINARY_PATTERN.asPredicate())
                    .map(s -> {
                        Matcher matcher = HYPHEN_BINARY_PATTERN.matcher(s);
                        return matcher.find() ? Strings.nullToEmpty(matcher.group(1)) : "";
                    })
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            Log.w(TAG,"Hyphenation binary folder is not exist" , e);
        }
        store.addListResult("hyphenation_locale", hyphenLocalesList);

        collectLocaleDataFilesInfo(store);
    }

    /**
     * Collect the fingerprints of ICU data files. On AOSP build, there are only 2 data files.
     * The example paths are /apex/com.android.tzdata/etc/icu/icu_tzdata.dat and
     * /apex/com.android.i18n/etc/icu/icudt65l.dat
     */
    private void collectLocaleDataFilesInfo(DeviceInfoStore store) throws IOException {
        int icuVersion = VersionInfo.ICU_VERSION.getMajor();
        File[] fixedDatPaths = new File[] {
                new File("/apex/com.android.tzdata/etc/icu/icu_tzdata.dat"),
                new File("/apex/com.android.i18n/etc/icu/icudt" + icuVersion + "l.dat"),
        };

        // This property has been deprecated since Android 12. The property will not work if this
        // app targets SDK level 31 or higher. Thus, we add the above fixedDatPaths in case that
        // the property is not working. When this comment was written, this CTS app still targets
        // SDK level 23.
        String prop = System.getProperty("android.icu.impl.ICUBinary.dataPath");
        store.startArray("icu_data_file_info");

        List<File> datFiles = new ArrayList<>();
        if (prop != null) {
            String[] dataDirs = prop.split(":");
            // List all ".dat" files in the directories.
            datFiles = Arrays.stream(dataDirs)
                .filter((dir) -> dir != null && !dir.isEmpty())
                .map((dir) -> new File(dir))
                .filter((f) -> f.canRead() && f.isDirectory())
                .map((f) -> f.listFiles())
                .filter((files) -> files != null)
                .flatMap(files -> Stream.of(files))
                .collect(Collectors.toList());
        }

        datFiles.addAll(Arrays.asList(fixedDatPaths));

        // Keep the readable paths only.
        datFiles = datFiles.stream()
            .distinct()
            .filter((f) -> f != null && f.canRead() && f.getName().endsWith(".dat"))
            .collect(Collectors.toList());

        for (File datFile : datFiles) {
            String sha256Hash = sha256(datFile);

            store.startGroup();
            store.addResult("file_path", datFile.getPath());
            // Still store the null hash to indicate an error occurring when obtaining the hash.
            store.addResult("file_sha256", sha256Hash);
            store.endGroup();
        }
        store.endArray();
    }

    public static @Nullable String sha256(File file) {
        try (FileInputStream in = new FileInputStream(file);
            FileChannel fileChannel = in.getChannel()) {

            MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0,
                fileChannel.size());

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(mappedByteBuffer);

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for(int i = 0; i < digest.length; i++){
                sb.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
                sb.append(Character.forDigit((digest[i] & 0xF), 16));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            Log.w(TAG, String.format("Can't obtain the hash of file: %s", file), e);
            return null;
        }
    }
}
