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

package com.android.ondevicepersonalization.services.display;

import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.adservices.ondevicepersonalization.RenderOutput;
import android.adservices.ondevicepersonalization.RenderOutputParcel;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.content.ComponentName;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.PersistableBundle;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;
import com.android.ondevicepersonalization.services.data.vendor.OnDevicePersonalizationVendorDataDao;
import com.android.ondevicepersonalization.services.data.vendor.VendorData;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class DisplayHelperTest {
    private static final String SERVICE_CLASS = "com.test.TestPersonalizationService";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationVendorDataDao mDao;
    private ComponentName mService;

    @Before
    public void setup() throws Exception {
        mService = ComponentName.createRelative(
                mContext.getPackageName(), "com.test.TestPersonalizationService");
        mDao = OnDevicePersonalizationVendorDataDao.getInstanceForTest(
                mContext,
                mService,
                PackageUtils.getCertDigest(mContext, mContext.getPackageName()));
    }

    @Test
    public void testGenerateHtml() {
        DisplayHelper displayHelper = new DisplayHelper(mContext);
        RenderOutput renderContentResult = new RenderOutput.Builder()
                .setContent("html").build();
        RenderOutputParcel resultParcel = new RenderOutputParcel(renderContentResult);
        assertEquals("html", displayHelper.generateHtml(resultParcel,
                mService));
    }

    @Test
    public void testGenerateHtmlViaTemplate() {
        String templateContents = "Hello $tool.encodeHtml($name)! I am $age.";
        List<VendorData> data = new ArrayList<>();
        data.add(new VendorData.Builder().setKey("templateId").setData(templateContents.getBytes(
                StandardCharsets.UTF_8)).build());
        List<String> keysToRetain = new ArrayList<>();
        keysToRetain.add("templateId");
        mDao.batchUpdateOrInsertVendorDataTransaction(data, keysToRetain,
                System.currentTimeMillis());

        DisplayHelper displayHelper = new DisplayHelper(mContext);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("name", "odp");
        bundle.putInt("age", 100);
        RenderOutput renderContentResult = new RenderOutput.Builder()
                .setTemplateId("templateId")
                .setTemplateParams(bundle)
                .build();
        RenderOutputParcel resultParcel = new RenderOutputParcel(renderContentResult);
        String expected = "Hello odp! I am 100.";
        assertEquals(expected, displayHelper.generateHtml(resultParcel,
                mService));
    }

    @Test
    @LargeTest
    public void testDisplayHtml() throws Exception {
        // Permission needed to setView in the display.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INTERNAL_SYSTEM_WINDOW);

        DisplayHelper displayHelper = new DisplayHelper(mContext);
        SurfaceView surfaceView = new SurfaceView(mContext);
        RequestLogRecord logRecord = new RequestLogRecord.Builder().build();
        final DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        final Display primaryDisplay = dm.getDisplay(DEFAULT_DISPLAY);
        final Context windowContext = mContext.createDisplayContext(primaryDisplay);
        ListenableFuture<SurfaceControlViewHost.SurfacePackage> result =
                displayHelper.displayHtml("html", logRecord, 0,
                        ComponentName.createRelative(mContext.getPackageName(), SERVICE_CLASS),
                        surfaceView.getHostToken(), windowContext.getDisplay().getDisplayId(),
                        surfaceView.getWidth(), surfaceView.getHeight());
        // Give 2 minutes to create the webview. Should normally be ~25s.
        SurfaceControlViewHost.SurfacePackage surfacePackage =
                result.get(120, TimeUnit.SECONDS);
        assertNotNull(surfacePackage);
        surfacePackage.release();
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }
}
