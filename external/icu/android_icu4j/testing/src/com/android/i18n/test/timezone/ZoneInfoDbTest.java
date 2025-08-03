/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.i18n.test.timezone;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import android.icu.testsharding.MainTestShard;

import com.android.i18n.timezone.TimeZoneDataFiles;
import com.android.i18n.timezone.ZoneInfoData;
import com.android.i18n.timezone.ZoneInfoDb;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import libcore.timezone.testing.ZoneInfoTestHelper;

@MainTestShard
public class ZoneInfoDbTest extends junit.framework.TestCase {

  // The base tzdata file, always present on a device.
  private static final String TZDATA_FILE =
          TimeZoneDataFiles.getTimeZoneModuleTzFile(ZoneInfoDb.TZDATA_FILE_NAME);

  // An empty override file should fall back to the default file.
  public void testLoadTzDataWithFallback_emptyOverrideFile() throws Exception {
    String emptyFilePath = makeEmptyFile().getPath();

    try (AutoCloseableZoneInfoDb db = AutoCloseableZoneInfoDb.loadTzData(TZDATA_FILE);
         AutoCloseableZoneInfoDb dbWithEmptyOverride =
                 AutoCloseableZoneInfoDb.loadTzDataWithFallback(emptyFilePath, TZDATA_FILE)) {
      ZoneInfoDb data = db.getDelegate();
      ZoneInfoDb dataWithEmptyOverride = dbWithEmptyOverride.getDelegate();
      assertEquals(data.getVersion(), dataWithEmptyOverride.getVersion());
      assertEquals(data.getAvailableIDs().length, dataWithEmptyOverride.getAvailableIDs().length);
    }
  }

  // A corrupt override file should fall back to the default file.
  public void testLoadTzDataWithFallback_corruptOverrideFile() throws Exception {
    String corruptFilePath = makeCorruptFile().getPath();
    try (AutoCloseableZoneInfoDb db = AutoCloseableZoneInfoDb.loadTzData(TZDATA_FILE);
         AutoCloseableZoneInfoDb dbWithCorruptOverride =
                 AutoCloseableZoneInfoDb.loadTzDataWithFallback(corruptFilePath, TZDATA_FILE)) {
      ZoneInfoDb data = db.getDelegate();
      ZoneInfoDb dataWithCorruptOverride = dbWithCorruptOverride.getDelegate();
      assertEquals(data.getVersion(), dataWithCorruptOverride.getVersion());
      assertEquals(data.getAvailableIDs().length, dataWithCorruptOverride.getAvailableIDs().length);
    }
  }

  // Given no tzdata files we can use, we should fall back to built-in "GMT".
  public void testLoadTzDataWithFallback_noGoodFile() throws Exception {
    String emptyFilePath = makeEmptyFile().getPath();
    try (AutoCloseableZoneInfoDb db =
                 AutoCloseableZoneInfoDb.loadTzDataWithFallback(emptyFilePath)) {
      ZoneInfoDb data = db.getDelegate();
      assertEquals("missing", data.getVersion());
      assertEquals(1, data.getAvailableIDs().length);
      assertEquals("GMT", data.getAvailableIDs()[0]);
    }
  }

  // Given a valid override file, we should find ourselves using that.
  public void testLoadTzDataWithFallback_goodOverrideFile() throws Exception {
    RandomAccessFile in = new RandomAccessFile(TZDATA_FILE, "r");
    byte[] content = new byte[(int) in.length()];
    in.readFully(content);
    in.close();

    // Bump the version number to one long past where humans will be extinct.
    content[6] = '9';
    content[7] = '9';
    content[8] = '9';
    content[9] = '9';
    content[10] = 'z';

    File goodFile = makeTemporaryFile(content);
    try (AutoCloseableZoneInfoDb dbWithOverride =
                 AutoCloseableZoneInfoDb.loadTzDataWithFallback(goodFile.getPath(), TZDATA_FILE);
         AutoCloseableZoneInfoDb db = AutoCloseableZoneInfoDb.loadTzData(TZDATA_FILE)) {
      ZoneInfoDb dataWithOverride = dbWithOverride.getDelegate();
      ZoneInfoDb data = db.getDelegate();
      assertEquals("9999z", dataWithOverride.getVersion());
      assertEquals(data.getAvailableIDs().length, dataWithOverride.getAvailableIDs().length);
    } finally {
      goodFile.delete();
    }
  }

  public void testGetAvailableIDsWithRawOffset() {
    ZoneInfoDb zoneInfoDb = ZoneInfoDb.getInstance();
    String[] allIds = zoneInfoDb.getAvailableIDs();
    List<ZoneInfoData> allZones = Arrays.stream(allIds)
            .map(id -> zoneInfoDb.makeZoneInfoData(id))
            .collect(toList());

    int[] allRawOffsets = allZones.stream()
            .mapToInt(zone -> zone.getRawOffset())
            .distinct()
            .toArray();

    for (int rawOffset : allRawOffsets) {
      Set<String> ids = new HashSet<>(Arrays.asList(zoneInfoDb.getAvailableIDs(rawOffset)));
      Set<String> expectedIds = allZones.stream()
              .filter(zone -> zone.getRawOffset() == rawOffset)
              .map(zone -> zone.getID())
              .collect(toSet());
      assertEquals("zoneInfoDb.getAvailableIDs(" + rawOffset + ")", expectedIds, ids);
    }
  }

  public void testLoadTzData_badHeader() throws Exception {
    RandomAccessFile in = new RandomAccessFile(TZDATA_FILE, "r");
    byte[] content = new byte[(int) in.length()];
    in.readFully(content);
    in.close();

    // Break the header.
    content[0] = 'a';
    checkInvalidDataDetected(content);
  }

  public void testLoadTzData_validTestData() throws Exception {
    byte[] data = new ZoneInfoTestHelper.TzDataBuilder().initializeToValid().build();
    File testFile = makeTemporaryFile(data);
    try {
      ZoneInfoDb db = ZoneInfoDb.loadTzData(testFile.getPath());
      assertNotNull(db);
      db.close();
    } finally {
      testFile.delete();
    }
  }

  public void testLoadTzData_invalidOffsets() throws Exception {
    ZoneInfoTestHelper.TzDataBuilder builder =
            new ZoneInfoTestHelper.TzDataBuilder().initializeToValid();

    // Sections must be in the correct order: section sizing is calculated using them.
    builder.setIndexOffsetOverride(10);
    builder.setDataOffsetOverride(30);

    byte[] data = builder.build();
    // The offsets must all be under the total size of the file for this test to be valid.
    assertTrue(30 < data.length);
    checkInvalidDataDetected(data);
  }

  public void testLoadTzData_finalOffsetOutsideFile() throws Exception {
    ZoneInfoTestHelper.TzDataBuilder builder =
            new ZoneInfoTestHelper.TzDataBuilder()
                    .initializeToValid();

    builder.setFinalOffsetOverride(3000); // This is invalid if it is outside of the file.

    byte[] data = builder.build();
    // The zoneTab offset must be outside of the file for this test to be valid.
    assertTrue(3000 > data.length);
    checkInvalidDataDetected(data);
  }

  public void testLoadTzData_nonDivisibleIndex() throws Exception {
    ZoneInfoTestHelper.TzDataBuilder builder =
            new ZoneInfoTestHelper.TzDataBuilder().initializeToValid();

    // Sections must be in the correct order: section sizing is calculated using them.
    int indexOffset = 10;
    builder.setIndexOffsetOverride(indexOffset);
    int dataOffset = indexOffset + ZoneInfoDb.SIZEOF_INDEX_ENTRY - 1;
    builder.setDataOffsetOverride(dataOffset);
    builder.setFinalOffsetOverride(dataOffset + 40);

    byte[] data = builder.build();
    // The zoneTab offset must be outside of the file for this test to be valid.
    assertTrue(3000 > data.length);
    checkInvalidDataDetected(data);
  }

  public void testLoadTzData_badId() throws Exception {
    ZoneInfoTestHelper.TzDataBuilder builder =
            new ZoneInfoTestHelper.TzDataBuilder().initializeToValid();
    builder.clearZicData();
    byte[] validZicData =
            new ZoneInfoTestHelper.ZicDataBuilder().initializeToValid().build();
    builder.addZicData("", validZicData); // "" is an invalid ID

    checkInvalidDataDetected(builder.build());
  }

  public void testLoadTzData_badIdOrder() throws Exception {
    ZoneInfoTestHelper.TzDataBuilder builder =
            new ZoneInfoTestHelper.TzDataBuilder().initializeToValid();
    builder.clearZicData();
    byte[] validZicData =
            new ZoneInfoTestHelper.ZicDataBuilder().initializeToValid().build();
    builder.addZicData("Europe/Zurich", validZicData);
    builder.addZicData("Europe/London", validZicData);

    checkInvalidDataDetected(builder.build());
  }

  public void testLoadTzData_duplicateId() throws Exception {
    ZoneInfoTestHelper.TzDataBuilder builder =
            new ZoneInfoTestHelper.TzDataBuilder().initializeToValid();
    builder.clearZicData();
    byte[] validZicData =
            new ZoneInfoTestHelper.ZicDataBuilder().initializeToValid().build();
    builder.addZicData("Europe/London", validZicData);
    builder.addZicData("Europe/London", validZicData);

    checkInvalidDataDetected(builder.build());
  }

  public void testLoadTzData_badZicLength() throws Exception {
    ZoneInfoTestHelper.TzDataBuilder builder =
            new ZoneInfoTestHelper.TzDataBuilder().initializeToValid();
    builder.clearZicData();
    byte[] invalidZicData = "This is too short".getBytes();
    builder.addZicData("Europe/London", invalidZicData);

    checkInvalidDataDetected(builder.build());
  }

  private static void checkInvalidDataDetected(byte[] data) throws Exception {
    File testFile = makeTemporaryFile(data);
    try {
      assertNull(ZoneInfoDb.loadTzData(testFile.getPath()));
    } finally {
      testFile.delete();
    }
  }

  // Confirms any caching that exists correctly handles ZoneInfoData mutability.
  public void testMakeTimeZone_timeZoneMutability() {
    try (AutoCloseableZoneInfoDb db = AutoCloseableZoneInfoDb.loadTzData(TZDATA_FILE)) {
      ZoneInfoDb data = db.getDelegate();
      String tzId = "Europe/London";
      ZoneInfoData first = data.makeZoneInfoData(tzId);
      ZoneInfoData second = first.createCopyWithRawOffset(3600);
      // Confirms that makeZoneInfoData(tzId) creates a new different instance.
      assertNotSame(first, second);

      assertFalse(first.getRawOffset() == second.getRawOffset());
      assertFalse(first.hasSameRules(second));
    }
  }

  public void testMakeTimeZone_notFound() {
    try (AutoCloseableZoneInfoDb db = AutoCloseableZoneInfoDb.loadTzData(TZDATA_FILE)) {
      ZoneInfoDb data = db.getDelegate();
      assertNull(data.makeZoneInfoData("THIS_TZ_DOES_NOT_EXIST"));
      assertFalse(data.hasTimeZone("THIS_TZ_DOES_NOT_EXIST"));
    }
  }

  public void testMakeTimeZone_found() {
    try (AutoCloseableZoneInfoDb db = AutoCloseableZoneInfoDb.loadTzData(TZDATA_FILE)) {
      ZoneInfoDb data = db.getDelegate();
      assertNotNull(data.makeZoneInfoData("Europe/London"));
      assertTrue(data.hasTimeZone("Europe/London"));
    }
  }

  private static File makeCorruptFile() throws Exception {
    return makeTemporaryFile("invalid content".getBytes());
  }

  private static File makeEmptyFile() throws Exception {
    return makeTemporaryFile(new byte[0]);
  }

  private static File makeTemporaryFile(byte[] content) throws Exception {
    File f = File.createTempFile("temp-", ".txt");
    FileOutputStream fos = new FileOutputStream(f);
    fos.write(content);
    fos.close();
    return f;
  }


  /**
   * Helper class to auto close {@link ZoneInfoDb} with try-with-resource during testing.
   */
  private static class AutoCloseableZoneInfoDb implements AutoCloseable {

    private final ZoneInfoDb delegate;

    public static AutoCloseableZoneInfoDb loadTzData(String path) {
      ZoneInfoDb zoneInfoDb = ZoneInfoDb.loadTzData(path);
      assertNotNull(zoneInfoDb);
      return new AutoCloseableZoneInfoDb(zoneInfoDb);
    }

    public static AutoCloseableZoneInfoDb loadTzDataWithFallback(String... paths) {
      ZoneInfoDb zoneInfoDb = ZoneInfoDb.loadTzDataWithFallback(paths);
      assertNotNull(zoneInfoDb);
      return new AutoCloseableZoneInfoDb(zoneInfoDb);
    }

    private AutoCloseableZoneInfoDb(ZoneInfoDb zoneInfoDb) {
      delegate = zoneInfoDb;
    }

    public ZoneInfoDb getDelegate() {
      return delegate;
    }

    @Override
    public void close() {
      delegate.close();
    }
  }
}
