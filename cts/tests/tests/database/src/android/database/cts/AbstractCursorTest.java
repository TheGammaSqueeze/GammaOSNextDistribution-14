/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.database.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Context;
import android.database.AbstractCursor;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.CursorWindow;
import android.database.DataSetObserver;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link AbstractCursor}.
 */
@RunWith(AndroidJUnit4.class)
public class AbstractCursorTest {
    @Rule public final RavenwoodRule mRavenwood = new RavenwoodRule();

    private static final int POSITION0 = 0;
    private static final int POSITION1 = 1;
    private  static final int ROW_MAX = 10;
    private static final int DATA_COUNT = 10;
    private static final String[] COLUMN_NAMES1 = new String[] {
        "_id",             // 0
        "number"           // 1
    };
    private static final String[] COLUMN_NAMES = new String[] { "name", "number", "profit" };
    private TestAbstractCursor mTestAbstractCursor;
    private Object mLockObj = new Object();

    private SQLiteDatabase mDatabase;
    private File mDatabaseFile;
    private AbstractCursor mDatabaseCursor;
    private Context mContext;

    private static final long ON_CHANGE_TIMEOUT_MS = 5000;

    // This timeout value allows for the 10s delay that occurs if an app moves to the CACHED_EMPTY
    // process state.
    // TODO(b/292616209) Remove the possibility of the 10s delay and eliminate this long timeout.
    private static final long ON_CHANGE_TIMEOUT_LONG_MS = ON_CHANGE_TIMEOUT_MS + 10_000;

    @Before
    public void setUp() throws Exception {
        ArrayList<ArrayList> list = createTestList(ROW_MAX, COLUMN_NAMES.length);
        mTestAbstractCursor = new TestAbstractCursor(COLUMN_NAMES, list);

        if (mRavenwood.isUnderRavenwood()) return;
        setupDatabase();
        mContext = getInstrumentation().getContext();
    }

    @After
    public void tearDown() throws Exception {
        mTestAbstractCursor.close();

        if (mRavenwood.isUnderRavenwood()) return;
        mDatabaseCursor.close();
        mDatabase.close();
        if (mDatabaseFile.exists()) {
            mDatabaseFile.delete();
        }
    }

    private Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    @Test
    public void testConstructor() {
        TestAbstractCursor abstractCursor = new TestAbstractCursor();
        assertEquals(-1, abstractCursor.getPosition());
    }

    @Test
    public void testGetBlob() {
        try {
            mTestAbstractCursor.getBlob(0);
            fail("getBlob should throws a UnsupportedOperationException here");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testRegisterDataSetObserver() {
        MockDataSetObserver datasetObserver = new MockDataSetObserver();

        try {
            mTestAbstractCursor.unregisterDataSetObserver(datasetObserver);
            fail("Can't unregister DataSetObserver before it is registered.");
        } catch (IllegalStateException e) {
            // expected
        }

        mTestAbstractCursor.registerDataSetObserver(datasetObserver);

        try {
            mTestAbstractCursor.registerDataSetObserver(datasetObserver);
            fail("Can't register DataSetObserver twice before unregister it.");
        } catch (IllegalStateException e) {
            // expected
        }

        mTestAbstractCursor.unregisterDataSetObserver(datasetObserver);
        mTestAbstractCursor.registerDataSetObserver(datasetObserver);
    }

    @Test
    public void testRegisterContentObserver() {
        MockContentObserver contentObserver = new MockContentObserver();

        try {
            mTestAbstractCursor.unregisterContentObserver(contentObserver);
            fail("Can't unregister ContentObserver before it is registered.");
        } catch (IllegalStateException e) {
            // expected
        }

        mTestAbstractCursor.registerContentObserver(contentObserver);

        try {
            mTestAbstractCursor.registerContentObserver(contentObserver);
            fail("Can't register DataSetObserver twice before unregister it.");
        } catch (IllegalStateException e) {
            // expected
        }

        mTestAbstractCursor.unregisterContentObserver(contentObserver);
        mTestAbstractCursor.registerContentObserver(contentObserver);
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ContentResolver.class)
    public void testSetNotificationUri() {
        final Uri testUri = Settings.System.getUriFor(Settings.System.TIME_12_24);
        mDatabaseCursor.setNotificationUri(getInstrumentation().getContext().getContentResolver(),
                testUri);
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ContentResolver.class)
    public void testSetNotificationUris_selfNotify() throws Exception {
        final Uri testUri1 = Settings.System.getUriFor(Settings.System.TIME_12_24);
        final Uri testUri2 = Settings.Global.getUriFor(
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
        mDatabaseCursor.setNotificationUris(mContext.getContentResolver(),
                Arrays.asList(testUri1, testUri2));
        final MockContentObserver observer = new MockContentObserver();
        mDatabaseCursor.registerContentObserver(observer);

        mContext.getContentResolver().notifyChange(testUri1, null);
        observer.waitForOnChange(ON_CHANGE_TIMEOUT_MS);
        observer.resetOnChangeWatch();
        mContext.getContentResolver().notifyChange(testUri2, null);
        observer.waitForOnChange(ON_CHANGE_TIMEOUT_MS);
    }

    @Test
    @AppModeFull
    @IgnoreUnderRavenwood(blockedBy = ContentResolver.class)
    public void testSetNotificationsUris() throws Exception {
        final Uri queryUri = Uri.parse("content://com.android.cts.providerapp");
        try (Cursor cursor = mContext.getContentResolver().query(queryUri, null, null, null)) {
            final MockContentObserver observer = new MockContentObserver();
            cursor.registerContentObserver(observer);

            mContext.getContentResolver().call(queryUri, "notify", "1", null);
            observer.waitForOnChange(ON_CHANGE_TIMEOUT_LONG_MS);
            observer.resetOnChangeWatch();
            mContext.getContentResolver().call(queryUri, "notify", "2", null);
            observer.waitForOnChange(ON_CHANGE_TIMEOUT_LONG_MS);
        }
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ContentResolver.class)
    public void testGetNotificationUris() throws Exception {
        final Uri[] notificationUris = new Uri[] {
                Settings.Global.getUriFor(Settings.Global.MODE_RINGER),
                Settings.Global.getUriFor(Settings.Global.BOOT_COUNT)
        };
        mDatabaseCursor.setNotificationUris(mContext.getContentResolver(),
                Arrays.asList(notificationUris));
        final List<Uri> actualNotificationUris = mDatabaseCursor.getNotificationUris();
        Assert.assertArrayEquals(notificationUris, actualNotificationUris.toArray(new Uri[0]));
    }

    @Test
    public void testRespond() {
        Bundle b = new Bundle();
        Bundle bundle = mTestAbstractCursor.respond(b);
        assertSame(Bundle.EMPTY, bundle);

        bundle = mTestAbstractCursor.respond(null);
        assertSame(Bundle.EMPTY, bundle);
    }

    @Test
    public void testRequery() {
        MockDataSetObserver mock = new MockDataSetObserver();
        mTestAbstractCursor.registerDataSetObserver(mock);
        assertFalse(mock.hadCalledOnChanged());
        mTestAbstractCursor.requery();
        assertTrue(mock.hadCalledOnChanged());
    }

    @Test
    public void testOnChange() throws InterruptedException {
        MockContentObserver mock = new MockContentObserver();
        mTestAbstractCursor.registerContentObserver(mock);
        mTestAbstractCursor.onChange(true);
        mock.waitForOnChange(ON_CHANGE_TIMEOUT_MS);
    }

    @Test
    public void testOnMove() {
        assertFalse(mTestAbstractCursor.getOnMoveRet());
        mTestAbstractCursor.moveToFirst();
        assertTrue(mTestAbstractCursor.getOnMoveRet());
        assertEquals(1, mTestAbstractCursor.getRowsMovedSum());

        mTestAbstractCursor.moveToPosition(5);
        assertTrue(mTestAbstractCursor.getOnMoveRet());
        assertEquals(6, mTestAbstractCursor.getRowsMovedSum());
        assertEquals(0, mTestAbstractCursor.getOldPos());
        assertEquals(5, mTestAbstractCursor.getNewPos());
    }

    @Test
    public void testOnMove_samePosition() {
        mTestAbstractCursor.moveToFirst();
        mTestAbstractCursor.moveToPosition(5);
        assertEquals(6, mTestAbstractCursor.getRowsMovedSum());
        mTestAbstractCursor.moveToPosition(5);
        // Moving to the same position should either call onMove(5, 5)
        // or be a no-op. It should no change the RowsMovedSum.
        assertEquals(6, mTestAbstractCursor.getRowsMovedSum());
    }

    @Test
    public void testMoveToPrevious() {
        // Test moveToFirst, isFirst, moveToNext, getPosition
        assertTrue(mTestAbstractCursor.moveToFirst());
        assertTrue(mTestAbstractCursor.isFirst());
        assertEquals(0, mTestAbstractCursor.getPosition());
        assertTrue(mTestAbstractCursor.moveToNext());
        assertEquals(1, mTestAbstractCursor.getPosition());
        assertFalse(mTestAbstractCursor.isFirst());
        assertTrue(mTestAbstractCursor.moveToNext());
        assertEquals(2, mTestAbstractCursor.getPosition());

        // invoke moveToPosition with a number larger than row count.
        assertFalse(mTestAbstractCursor.moveToPosition(30000));
        assertEquals(mTestAbstractCursor.getCount(), mTestAbstractCursor.getPosition());

        assertFalse(mTestAbstractCursor.moveToPosition(-1));
        assertEquals(-1, mTestAbstractCursor.getPosition());
        assertTrue(mTestAbstractCursor.isBeforeFirst());

        mTestAbstractCursor.moveToPosition(5);
        assertEquals(5, mTestAbstractCursor.getPosition());

        // Test moveToPrevious
        assertTrue(mTestAbstractCursor.moveToPrevious());
        assertEquals(4, mTestAbstractCursor.getPosition());
        assertTrue(mTestAbstractCursor.moveToPrevious());
        assertEquals(3, mTestAbstractCursor.getPosition());
        assertTrue(mTestAbstractCursor.moveToPrevious());
        assertEquals(2, mTestAbstractCursor.getPosition());

        // Test moveToLast, isLast, moveToPrevius, isAfterLast.
        assertFalse(mTestAbstractCursor.isLast());
        assertTrue(mTestAbstractCursor.moveToLast());
        assertTrue(mTestAbstractCursor.isLast());
        assertFalse(mTestAbstractCursor.isAfterLast());

        assertFalse(mTestAbstractCursor.moveToNext());
        assertTrue(mTestAbstractCursor.isAfterLast());
        assertFalse(mTestAbstractCursor.moveToNext());
        assertTrue(mTestAbstractCursor.isAfterLast());
        assertFalse(mTestAbstractCursor.isLast());
        assertTrue(mTestAbstractCursor.moveToPrevious());
        assertTrue(mTestAbstractCursor.isLast());
        assertTrue(mTestAbstractCursor.moveToPrevious());
        assertFalse(mTestAbstractCursor.isLast());

        // Test move(int).
        mTestAbstractCursor.moveToFirst();
        assertEquals(0, mTestAbstractCursor.getPosition());
        assertFalse(mTestAbstractCursor.move(-1));
        assertEquals(-1, mTestAbstractCursor.getPosition());
        assertTrue(mTestAbstractCursor.move(1));
        assertEquals(0, mTestAbstractCursor.getPosition());

        assertTrue(mTestAbstractCursor.move(5));
        assertEquals(5, mTestAbstractCursor.getPosition());
        assertTrue(mTestAbstractCursor.move(-1));
        assertEquals(4, mTestAbstractCursor.getPosition());

        mTestAbstractCursor.moveToLast();
        assertTrue(mTestAbstractCursor.isLast());
        assertFalse(mTestAbstractCursor.isAfterLast());
        assertFalse(mTestAbstractCursor.move(1));
        assertFalse(mTestAbstractCursor.isLast());
        assertTrue(mTestAbstractCursor.isAfterLast());
        assertTrue(mTestAbstractCursor.move(-1));
        assertTrue(mTestAbstractCursor.isLast());
        assertFalse(mTestAbstractCursor.isAfterLast());
    }

    @Test
    public void testIsClosed() {
        assertFalse(mTestAbstractCursor.isClosed());
        mTestAbstractCursor.close();
        assertTrue(mTestAbstractCursor.isClosed());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = CursorWindow.class)
    public void testGetWindow() {
        CursorWindow window = new CursorWindow(false);
        assertEquals(0, window.getNumRows());
        // fill window from position 0
        mDatabaseCursor.fillWindow(0, window);

        assertNotNull(mDatabaseCursor.getWindow());
        assertEquals(mDatabaseCursor.getCount(), window.getNumRows());

        while (mDatabaseCursor.moveToNext()) {
            assertEquals(mDatabaseCursor.getInt(POSITION1),
                    window.getInt(mDatabaseCursor.getPosition(), POSITION1));
        }
        window.clear();
    }

    @Test
    public void testGetWantsAllOnMoveCalls() {
        assertFalse(mTestAbstractCursor.getWantsAllOnMoveCalls());
    }

    @Test
    public void testIsFieldUpdated() {
        mTestAbstractCursor.moveToFirst();
        assertFalse(mTestAbstractCursor.isFieldUpdated(0));
    }

    @Test
    public void testGetUpdatedField() {
        mTestAbstractCursor.moveToFirst();
        assertNull(mTestAbstractCursor.getUpdatedField(0));
    }

    @Test
    public void testGetExtras() {
        assertSame(Bundle.EMPTY, mTestAbstractCursor.getExtras());
    }

    @Test
    public void testGetCount() {
        assertEquals(DATA_COUNT, mTestAbstractCursor.getCount());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = SQLiteDatabase.class)
    public void testGetColumnNames() {
        String[] names = mDatabaseCursor.getColumnNames();
        assertEquals(COLUMN_NAMES1.length, names.length);

        for (int i = 0; i < COLUMN_NAMES1.length; i++) {
            assertEquals(COLUMN_NAMES1[i], names[i]);
        }
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = SQLiteDatabase.class)
    public void testGetColumnName() {
        assertEquals(COLUMN_NAMES1[0], mDatabaseCursor.getColumnName(0));
        assertEquals(COLUMN_NAMES1[1], mDatabaseCursor.getColumnName(1));
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = SQLiteDatabase.class)
    public void testGetColumnIndexOrThrow() {
        final String COLUMN_FAKE = "fake_name";
        assertEquals(POSITION0, mDatabaseCursor.getColumnIndex(COLUMN_NAMES1[POSITION0]));
        assertEquals(POSITION1, mDatabaseCursor.getColumnIndex(COLUMN_NAMES1[POSITION1]));
        assertEquals(POSITION0, mDatabaseCursor.getColumnIndexOrThrow(COLUMN_NAMES1[POSITION0]));
        assertEquals(POSITION1, mDatabaseCursor.getColumnIndexOrThrow(COLUMN_NAMES1[POSITION1]));

        try {
            mDatabaseCursor.getColumnIndexOrThrow(COLUMN_FAKE);
            fail("IllegalArgumentException expected, but not thrown");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = SQLiteDatabase.class)
    public void testGetColumnIndex() {
        assertEquals(POSITION0, mDatabaseCursor.getColumnIndex(COLUMN_NAMES1[POSITION0]));
        assertEquals(POSITION1, mDatabaseCursor.getColumnIndex(COLUMN_NAMES1[POSITION1]));
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = SQLiteDatabase.class)
    public void testGetColumnCount() {
        assertEquals(COLUMN_NAMES1.length, mDatabaseCursor.getColumnCount());
    }

    @Test
    public void testDeactivate() {
        MockDataSetObserver mock = new MockDataSetObserver();
        mTestAbstractCursor.registerDataSetObserver(mock);
        assertFalse(mock.hadCalledOnInvalid());
        mTestAbstractCursor.deactivate();
        assertTrue(mock.hadCalledOnInvalid());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = CursorWindow.class)
    public void testCopyStringToBuffer() {
        CharArrayBuffer ca = new CharArrayBuffer(1000);
        mTestAbstractCursor.moveToFirst();
        mTestAbstractCursor.copyStringToBuffer(0, ca);
        CursorWindow window = new CursorWindow(false);
        mTestAbstractCursor.fillWindow(0, window);

        StringBuffer sb = new StringBuffer();
        sb.append(window.getString(0, 0));
        String str = mTestAbstractCursor.getString(0);
        assertEquals(str.length(), ca.sizeCopied);
        assertEquals(sb.toString(), new String(ca.data, 0, ca.sizeCopied));
    }

    @Test
    public void testCheckPosition() {
        // Test with position = -1.
        try {
            mTestAbstractCursor.checkPosition();
            fail("copyStringToBuffer() should throws CursorIndexOutOfBoundsException here.");
        } catch (CursorIndexOutOfBoundsException e) {
            // expected
        }

        // Test with position = count.
        assertTrue(mTestAbstractCursor.moveToPosition(mTestAbstractCursor.getCount() - 1));
        mTestAbstractCursor.checkPosition();

        try {
            assertFalse(mTestAbstractCursor.moveToPosition(mTestAbstractCursor.getCount()));
            assertEquals(mTestAbstractCursor.getCount(), mTestAbstractCursor.getPosition());
            mTestAbstractCursor.checkPosition();
            fail("copyStringToBuffer() should throws CursorIndexOutOfBoundsException here.");
        } catch (CursorIndexOutOfBoundsException e) {
            // expected
        }
    }

    @Test
    public void testSetExtras() {
        Bundle b = new Bundle();
        mTestAbstractCursor.setExtras(b);
        assertSame(b, mTestAbstractCursor.getExtras());
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<ArrayList> createTestList(int rows, int cols) {
        ArrayList<ArrayList> list = new ArrayList<ArrayList>();
        Random ran = new Random();

        for (int i = 0; i < rows; i++) {
            ArrayList<Integer> col = new ArrayList<Integer>();
            list.add(col);

            for (int j = 0; j < cols; j++) {
                // generate random number
                Integer r = ran.nextInt();
                col.add(r);
            }
        }

        return list;
    }

    private void setupDatabase() {
        File dbDir = getInstrumentation().getTargetContext().getDir("tests",
                Context.MODE_PRIVATE);
        mDatabaseFile = new File(dbDir, "database_test.db");
        if (mDatabaseFile.exists()) {
            mDatabaseFile.delete();
        }
        mDatabase = SQLiteDatabase.openOrCreateDatabase(mDatabaseFile.getPath(), null);
        assertNotNull(mDatabaseFile);
        mDatabase.execSQL("CREATE TABLE test1 (_id INTEGER PRIMARY KEY, number TEXT);");
        generateData();
        mDatabaseCursor = (AbstractCursor) mDatabase.query("test1", null, null, null, null, null,
                null);
    }

    private void generateData() {
        for ( int i = 0; i < DATA_COUNT; i++) {
            mDatabase.execSQL("INSERT INTO test1 (number) VALUES ('" + i + "');");
        }
    }

    private class TestAbstractCursor extends AbstractCursor {
        private boolean mOnMoveReturnValue;
        private int mOldPosition;
        private int mNewPosition;
        /** The accumulated number of rows this cursor has moved over. */
        private int mRowsMovedSum;
        private String[] mColumnNames;
        private ArrayList<Object>[] mRows;
        private boolean mHadCalledOnChange = false;

        public TestAbstractCursor() {
            super();
        }
        @SuppressWarnings("unchecked")
        public TestAbstractCursor(String[] columnNames, ArrayList<ArrayList> rows) {
            int colCount = columnNames.length;
            boolean foundID = false;

            // Add an _id column if not in columnNames
            for (int i = 0; i < colCount; ++i) {
                if (columnNames[i].compareToIgnoreCase("_id") == 0) {
                    mColumnNames = columnNames;
                    foundID = true;
                    break;
                }
            }

            if (!foundID) {
                mColumnNames = new String[colCount + 1];
                System.arraycopy(columnNames, 0, mColumnNames, 0, columnNames.length);
                mColumnNames[colCount] = "_id";
            }

            int rowCount = rows.size();
            mRows = new ArrayList[rowCount];

            for (int i = 0; i < rowCount; ++i) {
                mRows[i] = rows.get(i);

                if (!foundID) {
                    mRows[i].add(Long.valueOf(i));
                }
            }
        }

        public boolean getOnMoveRet() {
            return mOnMoveReturnValue;
        }

        public void resetOnMoveRet() {
            mOnMoveReturnValue = false;
        }

        public int getOldPos() {
            return mOldPosition;
        }

        public int getNewPos() {
            return mNewPosition;
        }

        public int getRowsMovedSum() {
            return mRowsMovedSum;
        }

        @Override
        public boolean onMove(int oldPosition, int newPosition) {
            mOnMoveReturnValue = super.onMove(oldPosition, newPosition);
            mOldPosition = oldPosition;
            mNewPosition = newPosition;
            mRowsMovedSum += Math.abs(newPosition - oldPosition);
            return mOnMoveReturnValue;
        }

        @Override
        public int getCount() {
            return mRows.length;
        }

        @Override
        public String[] getColumnNames() {
            return mColumnNames;
        }

        @Override
        public String getString(int columnIndex) {
            Object cell = mRows[mPos].get(columnIndex);
            return (cell == null) ? null : cell.toString();
        }

        @Override
        public short getShort(int columnIndex) {
            Number num = (Number) mRows[mPos].get(columnIndex);
            return num.shortValue();
        }

        @Override
        public int getInt(int columnIndex) {
            Number num = (Number) mRows[mPos].get(columnIndex);
            return num.intValue();
        }

        @Override
        public long getLong(int columnIndex) {
            Number num = (Number) mRows[mPos].get(columnIndex);
            return num.longValue();
        }

        @Override
        public float getFloat(int columnIndex) {
            Number num = (Number) mRows[mPos].get(columnIndex);
            return num.floatValue();
        }

        @Override
        public double getDouble(int columnIndex) {
            Number num = (Number) mRows[mPos].get(columnIndex);
            return num.doubleValue();
        }

        @Override
        public boolean isNull(int column) {
            return false;
        }

        public boolean hadCalledOnChange() {
            return mHadCalledOnChange;
        }

        // the following are protected methods
        @Override
        protected void checkPosition() {
            super.checkPosition();
        }

        @Override
        protected Object getUpdatedField(int columnIndex) {
            return super.getUpdatedField(columnIndex);
        }

        @Override
        protected boolean isFieldUpdated(int columnIndex) {
            return super.isFieldUpdated(columnIndex);
        }

        @Override
        protected void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mHadCalledOnChange = true;
        }
    }

    private class MockContentObserver extends ContentObserver {
        private CountDownLatch mCountDownLatch;

        public MockContentObserver() {
            super(null);

            mCountDownLatch = new CountDownLatch(1);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mCountDownLatch.countDown();
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        public void resetOnChangeWatch() {
            mCountDownLatch = new CountDownLatch(1);
        }

        public void waitForOnChange(long timeoutMs) throws InterruptedException {
            if (!mCountDownLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                fail("Timed out waiting for onChange() to get called");
            }
        }
    }

    private class MockDataSetObserver extends DataSetObserver {
        private boolean mHadCalledOnChanged;
        private boolean mHadCalledOnInvalid;

        @Override
        public void onChanged() {
            super.onChanged();
            mHadCalledOnChanged = true;
        }

        @Override
        public void onInvalidated() {
            super.onInvalidated();
            mHadCalledOnInvalid = true;
        }

        public boolean hadCalledOnChanged() {
            return mHadCalledOnChanged;
        }

        public boolean hadCalledOnInvalid() {
            return mHadCalledOnInvalid;
        }
    }
}
