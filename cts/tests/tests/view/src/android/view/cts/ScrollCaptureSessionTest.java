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

package android.view.cts;

import static org.junit.Assert.assertEquals;

import android.graphics.Point;
import android.graphics.Rect;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.Presubmit;
import android.view.ScrollCaptureSession;
import android.view.Surface;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

/**
 * Tests of {@link ScrollCaptureSession}.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ScrollCaptureSessionTest {

    private Surface mSurface;

    @Before
    public void setUp() {
        mSurface = Mockito.mock(Surface.class);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_requiresNonNullSurface() {
        new ScrollCaptureSession(null, new Rect(1, 2, 3, 4), new Point(5, 6));
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_requiresNonNullBounds() {
        new ScrollCaptureSession(mSurface, null, new Point(5, 6));
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_requiresNonNullPosition() {
        new ScrollCaptureSession(mSurface, new Rect(1, 2, 3, 4), null);
    }

    @Test
    public void testGetSurface() {
        ScrollCaptureSession session = new ScrollCaptureSession(mSurface,
                new Rect(1, 2, 3, 4), new Point(5, 6));
        assertEquals(mSurface, session.getSurface());
    }

    @Test
    public void testGetScrollBounds() {
        ScrollCaptureSession session = new ScrollCaptureSession(mSurface,
                new Rect(1, 2, 3, 4), new Point(5, 6));
        assertEquals(new Rect(1, 2, 3, 4), session.getScrollBounds());
    }

    @Test
    public void testGetPositionInWindow() {
        ScrollCaptureSession session = new ScrollCaptureSession(mSurface,
                new Rect(1, 2, 3, 4), new Point(5, 6));
        assertEquals(new Point(5, 6), session.getPositionInWindow());
    }
}
