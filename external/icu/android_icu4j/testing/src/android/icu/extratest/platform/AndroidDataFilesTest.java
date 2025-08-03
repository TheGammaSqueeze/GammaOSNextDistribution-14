/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.icu.extratest.platform;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.icu.platform.AndroidDataFiles;
import android.icu.testsharding.MainTestShard;
import android.icu.util.VersionInfo;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@MainTestShard
@RunWith(JUnit4.class)
public class AndroidDataFilesTest {

    private static final String TZDATA_DAT_PATH =
        "/apex/com.android.tzdata/etc/icu/icu_tzdata.dat";

    private static final String ICU_DAT_PATH =
        "/apex/com.android.i18n/etc/icu/icudt" + VersionInfo.ICU_VERSION.getMajor() + "l.dat";


    /**
     * If this test fails and needs to be fixed, please also fix
     * {@link com.android.compatibility.common.deviceinfo.LocaleDeviceInfo} which has the same
     * assumption on the data file paths.
     */
    @Test
    public void testGenerateIcuDataPath() {
        String path = AndroidDataFiles.generateIcuDataPath();

        String[] dataDirs = path.split(":");
        // List all readable".dat" files in the directories.
        Set<String> datFiles = Arrays.stream(dataDirs)
                .filter((dir) -> dir != null && !dir.isEmpty())
                .map((dir) -> new File(dir))
                .filter((f) -> f.canRead() && f.isDirectory())
                .map((f) -> f.listFiles())
                .filter((files) -> files != null)
                .flatMap(files -> Stream.of(files))
                .filter((f) -> f != null && f.canRead() && f.getName().endsWith(".dat"))
                .map(f -> f.getPath())
                .collect(Collectors.toSet());

        assertContains(datFiles, TZDATA_DAT_PATH);
        assertContains(datFiles, ICU_DAT_PATH);
    }

    private static void assertContains(Set<String> set, String member) {
        assertTrue("Expect to contain \"" + member + "\" but not", set.contains(member));
    }

    /**
     * Make sure that the core icu .dat contains none of the 4 time zone resources that exist
     * in the Time Zone Data Module. http://b/171542040
     */
    @Test
    public void testTimezoneResAbsence() throws Exception {
        // We use existing PackageDataFile class to read the .dat files.
        // But the class is private, and thus use reflection to access
        Class<?> clsPackageDataFile = Class.forName("android.icu.impl.ICUBinary$PackageDataFile");
        Constructor<?> constructor = clsPackageDataFile.getDeclaredConstructor(
            String.class, ByteBuffer.class);
        constructor.setAccessible(true);
        Method getData = clsPackageDataFile.getDeclaredMethod("getData", String.class);
        getData.setAccessible(true);

        Class<?> clsDatPackageReader = Class.forName("android.icu.impl.ICUBinary$DatPackageReader");
        Method validate = clsDatPackageReader.getDeclaredMethod("validate", ByteBuffer.class);
        validate.setAccessible(true);


        ByteBuffer icuDatBuffer = mmapPath(ICU_DAT_PATH);
        assertTrue((Boolean) validate.invoke(null, icuDatBuffer));
        Object icuDat = constructor.newInstance(ICU_DAT_PATH, icuDatBuffer);
        assertNotNull(getData.invoke(icuDat, "root.res")); // an example of locale data
        assertNull(getData.invoke(icuDat, "metaZones.res"));
        assertNull(getData.invoke(icuDat, "timezoneTypes.res"));
        assertNull(getData.invoke(icuDat, "windowsZones.res"));
        assertNull(getData.invoke(icuDat, "zoneinfo64.res"));

        ByteBuffer tzDatBuffer = mmapPath(TZDATA_DAT_PATH);
        assertTrue((Boolean) validate.invoke(null, tzDatBuffer));
        Object tzDat = constructor.newInstance(TZDATA_DAT_PATH, tzDatBuffer);
        assertNull(getData.invoke(tzDat, "root.res"));
        assertNotNull(getData.invoke(tzDat, "metaZones.res"));
        assertNotNull(getData.invoke(tzDat, "timezoneTypes.res"));
        assertNotNull(getData.invoke(tzDat, "windowsZones.res"));
        assertNotNull(getData.invoke(tzDat, "zoneinfo64.res"));
    }

    private static ByteBuffer mmapPath(String path) throws IOException {
        try (FileInputStream file = new FileInputStream(path)) {
            FileChannel channel = file.getChannel();
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }
    }
}
