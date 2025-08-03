/*
 * Copyright (C) 2021 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.hilt.android.simple;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.annotation.RetentionPolicy.CLASS;

import android.content.Context;
import androidx.activity.ComponentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.DefineComponent;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.AndroidEntryPoint;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.android.scopes.ActivityScoped;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.migration.AliasOf;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Scope;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public final class AliasOfMultipleScopesTest {

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @Inject @ApplicationContext Context context;
  @Inject CustomComponent.Builder customComponentBuilder;

  @Scope
  @Retention(CLASS)
  @Target({ElementType.METHOD, ElementType.TYPE})
  public @interface CustomScoped {}

  @DefineComponent(parent = SingletonComponent.class)
  @CustomScoped
  public interface CustomComponent {
    @DefineComponent.Builder
    public interface Builder {
      CustomComponent build();
    }
  }

  @Scope
  @AliasOf({ActivityScoped.class, CustomScoped.class})
  public @interface AliasScoped {}

  public interface UnscopedDep {}

  public interface ActivityScopedDep {}

  public interface CustomScopedDep {}

  public interface AliasScopedDep {}

  @Module
  @InstallIn(SingletonComponent.class)
  interface SingletonTestModule {
    @Provides
    static UnscopedDep unscopedDep() {
      return new UnscopedDep() {};
    }
  }

  @Module
  @InstallIn(ActivityComponent.class)
  interface ActivityTestModule {
    @Provides
    @ActivityScoped
    static ActivityScopedDep activityScopedDep() {
      return new ActivityScopedDep() {};
    }

    @Provides
    @AliasScoped
    static AliasScopedDep aliasScopedDep() {
      return new AliasScopedDep() {};
    }
  }

  @Module
  @InstallIn(CustomComponent.class)
  interface CustomTestModule {
    @Provides
    @CustomScoped
    static CustomScopedDep customScopedDep() {
      return new CustomScopedDep() {};
    }

    @Provides
    @AliasScoped
    static AliasScopedDep aliasScopedDep() {
      return new AliasScopedDep() {};
    }
  }

  /** An activity to test injection. */
  @AndroidEntryPoint(ComponentActivity.class)
  public static final class TestActivity extends Hilt_AliasOfMultipleScopesTest_TestActivity {
    @Inject Provider<UnscopedDep> unscopedDep;
    @Inject Provider<ActivityScopedDep> activityScopedDep;
    @Inject Provider<AliasScopedDep> aliasScopedDep;
  }

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  interface CustomComponentBuilderEntryPoint {
    CustomComponent.Builder customComponentBuilder();
  }

  @EntryPoint
  @InstallIn(CustomComponent.class)
  interface CustomComponentEntryPoint {
    Provider<UnscopedDep> unscopedDep();

    Provider<CustomScopedDep> customScopedDep();

    Provider<AliasScopedDep> aliasScopedDep();
  }

  @Before
  public void setUp() {
    rule.inject();
  }

  @Test
  public void testActivityScoped() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> {
            assertThat(activity.unscopedDep.get()).isNotSameInstanceAs(activity.unscopedDep.get());
            assertThat(activity.activityScopedDep.get())
                .isSameInstanceAs(activity.activityScopedDep.get());
            assertThat(activity.aliasScopedDep.get())
                .isSameInstanceAs(activity.aliasScopedDep.get());
          });
    }
  }

  @Test
  public void testCustomScoped() {
    CustomComponent customComponent =
        EntryPoints.get(context, CustomComponentBuilderEntryPoint.class)
            .customComponentBuilder()
            .build();
    CustomComponentEntryPoint entryPoint =
        EntryPoints.get(customComponent, CustomComponentEntryPoint.class);
    assertThat(entryPoint.unscopedDep().get()).isNotSameInstanceAs(entryPoint.unscopedDep().get());
    assertThat(entryPoint.customScopedDep().get())
        .isSameInstanceAs(entryPoint.customScopedDep().get());
    assertThat(entryPoint.aliasScopedDep().get())
        .isSameInstanceAs(entryPoint.aliasScopedDep().get());
  }
}
