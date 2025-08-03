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

package com.android.car.carlauncher;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.ComponentName;
import android.graphics.drawable.Drawable;

import androidx.lifecycle.Observer;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public final class LauncherViewModelTest extends AbstractExtendedMockitoTestCase {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule =
            new InstantTaskExecutorRule();
    private LauncherViewModel mLauncherModel;
    private AppLauncherUtils.LauncherAppsInfo mLauncherAppsInfo;
    private Drawable mDrawable = mock(Drawable.class);
    private Consumer mConsumer = mock(Consumer.class);
    private List<LauncherItem> mCustomizedApps;
    private List<LauncherItem> mAlphabetizedApps;
    private List<AppMetaData> mApps;

    @Before
    public void setUp() throws Exception {
        mLauncherModel = new LauncherViewModel(
                new File("/data/user/10/com.android.car.carlauncher/files"));
        mCustomizedApps = new ArrayList<>();
        mAlphabetizedApps = new ArrayList<>();
        AppMetaData app1 = createTestAppMetaData("App1", "A");
        AppMetaData app2 = createTestAppMetaData("App2", "B");
        AppMetaData app3 = createTestAppMetaData("App3", "C");
        LauncherItem launcherItem1 = new AppItem(app1);
        LauncherItem launcherItem2 = new AppItem(app2);
        LauncherItem launcherItem3 = new AppItem(app3);
        mApps = new ArrayList<>();
        mApps.add(app1);
        mApps.add(app2);
        mApps.add(app3);
        mAlphabetizedApps = new ArrayList<>();
        mAlphabetizedApps.add(launcherItem1);
        mAlphabetizedApps.add(launcherItem2);
        mAlphabetizedApps.add(launcherItem3);
        mCustomizedApps = new ArrayList<>();
        mCustomizedApps.add(launcherItem2);
        mCustomizedApps.add(launcherItem3);
        mCustomizedApps.add(launcherItem1);

        mLauncherAppsInfo = mock(AppLauncherUtils.LauncherAppsInfo.class);
        when(mLauncherAppsInfo.getLaunchableComponentsList()).thenReturn(mApps);
    }

    private AppMetaData createTestAppMetaData(String displayName, String componentName) {
        return new AppMetaData(displayName, new ComponentName(componentName, componentName),
                mDrawable, true, false, true, mConsumer, mConsumer);
    }

    @Test
    @Ignore("b/304484141")
    public void test_concurrentExecution() throws InterruptedException {
        ExecutorService pool = Executors.newCachedThreadPool();
        for (int i = 0; i < 100; i++) {
            pool.execute(() -> {
                mLauncherModel.loadAppsOrderFromFile();
            });
            pool.execute(() -> {
                mLauncherModel.processAppsInfoFromPlatform(mLauncherAppsInfo);
            });
        }
        pool.shutdown(); // Disable new tasks from being submitted
        if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
            pool.shutdownNow(); // Cancel currently executing tasks
        }
        mLauncherModel.getCurrentLauncher().observeForever(new Observer<>() {
            @Override
            public void onChanged(List<LauncherItem> launcherItems) {
                assertEquals(3, launcherItems.size());
                assertEquals("A", launcherItems.get(0).getPackageName());
                assertEquals("B", launcherItems.get(1).getPackageName());
                assertEquals("C", launcherItems.get(2).getPackageName());
                //remove observer after assertion
                mLauncherModel.getCurrentLauncher().removeObserver(this);
            }
        });
    }

    @Test
    public void loadAppsOrderFromFile_first_noOrderFile() throws IOException {
        mLauncherModel.loadAppsOrderFromFile();
        mLauncherModel.processAppsInfoFromPlatform(mLauncherAppsInfo);
        mLauncherModel.getCurrentLauncher().observeForever(launcherItems -> {
            assertEquals(3, launcherItems.size());
            assertEquals("A", launcherItems.get(0).getPackageName());
            assertEquals("B", launcherItems.get(1).getPackageName());
            assertEquals("C", launcherItems.get(2).getPackageName());
        });
    }

    @Test
    public void loadAppsOrderFromFile_first_existsOrderFile() {
        mLauncherModel.processAppsInfoFromPlatform(mLauncherAppsInfo);
        mLauncherModel.loadAppsOrderFromFile();

        mLauncherModel.setAppPosition(0, mApps.get(2));
        // normally, the observer would make this call
        mLauncherModel.handleAppListChange();

        mLauncherModel.loadAppsOrderFromFile();
        mLauncherModel.getCurrentLauncher().observeForever(it -> {
            assertEquals("C", mApps.get(2).getPackageName());
            assertEquals(3, it.size());
            assertEquals("C", it.get(0).getPackageName());
        });
    }

    @Test
    public void processAppsInfoFromPlatform_first_noCustomOrderFile() {
        mLauncherModel.processAppsInfoFromPlatform(mLauncherAppsInfo);
        mLauncherModel.loadAppsOrderFromFile();
        mLauncherModel.getCurrentLauncher().observeForever(it -> {
            assertEquals(3, it.size());
            assertEquals("A", it.get(0).getPackageName());
        });
    }
}
