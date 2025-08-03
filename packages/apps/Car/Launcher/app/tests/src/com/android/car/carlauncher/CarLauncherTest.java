/*
 * Copyright (C) 2020 Google Inc.
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

import static android.car.settings.CarSettings.Secure.KEY_USER_TOS_ACCEPTED;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.user.CarUserManager;
import android.content.Intent;
import android.provider.Settings;
import android.testing.TestableContext;
import android.util.ArraySet;

import androidx.lifecycle.Lifecycle;
import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.filters.Suppress;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.net.URISyntaxException;
import java.util.Set;

@Suppress // To be ignored until b/224978827 is fixed
@RunWith(AndroidJUnit4.class)
@SmallTest
public class CarLauncherTest extends AbstractExtendedMockitoTestCase {

    @Rule
    public TestableContext mContext = new TestableContext(InstrumentationRegistry.getContext());
    private ActivityScenario<CarLauncher> mActivityScenario;

    @Mock
    private CarUserManager mMockCarUserManager;

    private static final String TOS_MAP_INTENT = "intent:#Intent;"
            + "component=com.android.car.carlauncher/"
            + "com.android.car.carlauncher.homescreen.MapActivityTos;"
            + "action=android.intent.action.MAIN;end";
    private static final String DEFAULT_MAP_INTENT = "intent:#Intent;"
            + "component=com.android.car.maps/"
            + "com.android.car.maps.MapActivity;"
            + "action=android.intent.action.MAIN;end";
    private static final String CUSTOM_MAP_INTENT = "intent:#Intent;component=com.custom.car.maps/"
            + "com.custom.car.maps.MapActivity;"
            + "action=android.intent.action.MAIN;end";

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(AppLauncherUtils.class);
        session.spyStatic(CarLauncherUtils.class);
    }

    @After
    public void tearDown() {
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
    }

    @Test
    public void onResume_mapsCard_isVisible() {
        mActivityScenario = ActivityScenario.launch(CarLauncher.class);
        mActivityScenario.moveToState(Lifecycle.State.RESUMED);

        onView(withId(R.id.maps_card)).check(matches(isDisplayed()));
    }

    @Test
    public void onResume_assistiveCard_isVisible() {
        mActivityScenario = ActivityScenario.launch(CarLauncher.class);
        mActivityScenario.moveToState(Lifecycle.State.RESUMED);

        onView(withId(R.id.top_card)).check(matches(isDisplayed()));
    }

    @Test
    public void onResume_audioCard_isVisible() {
        mActivityScenario = ActivityScenario.launch(CarLauncher.class);
        mActivityScenario.moveToState(Lifecycle.State.RESUMED);

        onView(withId(R.id.bottom_card)).check(matches(isDisplayed()));
    }

    @Test
    public void onCreate_tosMapActivity_tosUnaccepted_canvasOptimizedMapsDisabledByTos() {
        doReturn(false).when(() -> AppLauncherUtils.tosAccepted(any()));
        doReturn(true)
                        .when(() ->
                                CarLauncherUtils.isSmallCanvasOptimizedMapIntentConfigured(any()));
        doReturn(createIntentFromString(TOS_MAP_INTENT))
                .when(() -> CarLauncherUtils.getTosMapIntent(any()));
        doReturn(createIntentFromString(DEFAULT_MAP_INTENT))
                .when(() -> CarLauncherUtils.getSmallCanvasOptimizedMapIntent(any()));
        doReturn(tosDisabledPackages())
                .when(() -> AppLauncherUtils.getTosDisabledPackages(any()));

        mActivityScenario = ActivityScenario.launch(CarLauncher.class);

        mActivityScenario.onActivity(activity -> {
            Intent mapIntent = activity.getMapsIntent();
            // If TOS is not accepted, and the default map is disabled by TOS, or
            // package name maybe null when resolving intent from package manager.
            // We replace the map intent with TOS map activity
            assertEquals(createIntentFromString(TOS_MAP_INTENT).getComponent().getClassName(),
                    mapIntent.getComponent().getClassName());
        });
    }

    @Test
    public void onCreate_tosMapActivity_tosUnaccepted_mapsNotDisabledByTos() {
        doReturn(false).when(() -> AppLauncherUtils.tosAccepted(any()));
        doReturn(true)
                .when(() -> CarLauncherUtils.isSmallCanvasOptimizedMapIntentConfigured(any()));
        doReturn(createIntentFromString(CUSTOM_MAP_INTENT))
                .when(() -> CarLauncherUtils.getSmallCanvasOptimizedMapIntent(any()));
        doReturn(tosDisabledPackages())
                .when(() -> AppLauncherUtils.getTosDisabledPackages(any()));

        mActivityScenario = ActivityScenario.launch(CarLauncher.class);

        mActivityScenario.onActivity(activity -> {
            Intent mapIntent = activity.getMapsIntent();
            // If TOS is not accepted, and the default map is not disabled by TOS,
            // these can be some other navigation app set as default,
            // package name will not be null.
            // We will not replace the map intent with TOS map activity
            assertEquals(
                    createIntentFromString(CUSTOM_MAP_INTENT).getComponent().getClassName(),
                    mapIntent.getComponent().getClassName());
        });
    }

    @Test
    public void onCreate_tosMapActivity_tosAccepted() {
        doReturn(true).when(() -> AppLauncherUtils.tosAccepted(any()));
        doReturn(createIntentFromString(TOS_MAP_INTENT))
                .when(() -> CarLauncherUtils.getTosMapIntent(any()));

        mActivityScenario = ActivityScenario.launch(CarLauncher.class);

        mActivityScenario.onActivity(activity -> {
            Intent mapIntent = activity.getMapsIntent();
            // If TOS is accepted, map intent is not replaced
            assertNotEquals("com.android.car.carlauncher.homescreen.MapActivityTos",
                    mapIntent.getComponent().getClassName());
        });
    }

    @Test
    public void onCreate_tosStateContentObserver_tosAccepted() {
        TestableContext mContext = new TestableContext(InstrumentationRegistry.getContext());
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 2);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, CarLauncher.class));
        mActivityScenario.moveToState(Lifecycle.State.RESUMED);

        mActivityScenario.onActivity(activity -> {
            // Content observer not setup because tos is accepted
            assertNull(activity.mTosContentObserver);
        });
    }

    @Test
    public void onCreate_registerTosStateContentObserver_tosNotAccepted() {
        TestableContext mContext = new TestableContext(InstrumentationRegistry.getContext());
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 1);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, CarLauncher.class));
        mActivityScenario.moveToState(Lifecycle.State.RESUMED);

        mActivityScenario.onActivity(activity -> {
            // Content observer is setup because tos is not accepted
            assertNotNull(activity.mTosContentObserver);
        });
    }

    @Test
    public void onCreate_registerTosStateContentObserver_tosNotInitialized() {
        TestableContext mContext = new TestableContext(InstrumentationRegistry.getContext());
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 0);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, CarLauncher.class));
        mActivityScenario.moveToState(Lifecycle.State.RESUMED);

        mActivityScenario.onActivity(activity -> {
            // Content observer is setup because tos is not initialized
            assertNotNull(activity.mTosContentObserver);
        });
    }

    @Test
    public void recreate_tosStateContentObserver_tosNotAccepted() {
        TestableContext mContext = new TestableContext(InstrumentationRegistry.getContext());
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 1);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, CarLauncher.class));

        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity.mTosContentObserver); // Content observer is setup

            // Accept TOS
            Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 2);
            activity.mTosContentObserver.onChange(true);
        });
        // Content observer is null after recreate
        mActivityScenario.onActivity(activity -> assertNull(activity.mTosContentObserver));
    }

    @Test
    public void recreate_tosStateContentObserver_tosNotInitialized() {
        TestableContext mContext = new TestableContext(InstrumentationRegistry.getContext());
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 0);

        mActivityScenario = ActivityScenario.launch(new Intent(mContext, CarLauncher.class));

        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity.mTosContentObserver); // Content observer is setup

            // TOS changed to unaccepted
            Settings.Secure.putInt(mContext.getContentResolver(), KEY_USER_TOS_ACCEPTED, 1);
            activity.mTosContentObserver.onChange(true);
        });
        // Content observer is not null after recreate
        mActivityScenario.onActivity(activity -> assertNotNull(activity.mTosContentObserver));
    }

    private Intent createIntentFromString(String intentString) {
        try {
            return Intent.parseUri(intentString, Intent.URI_ANDROID_APP_SCHEME);
        } catch (URISyntaxException se) {
            return null;
        }
    }

    private Set<String> tosDisabledPackages() {
        Set<String> packages = new ArraySet<>();
        packages.add("com.android.car.maps");
        packages.add("com.android.car.assistant");
        return packages;
    }
}
