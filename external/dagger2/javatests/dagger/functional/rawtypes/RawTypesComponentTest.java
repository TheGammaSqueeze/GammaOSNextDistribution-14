/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.functional.rawtypes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RawTypesComponentTest {
  private static final String STRING_VALUE = "someString";
  private static final int INT_VALUE = 3;

  @SuppressWarnings("rawtypes")
  @Component(modules = RawTypesModule.class)
  interface RawTypesComponent {
    Set rawSet();

    Map rawMap();
  }

  @SuppressWarnings("rawtypes")
  @Module
  interface RawTypesModule {
    @Provides
    static Map rawMap() {
      return ImmutableMap.of(INT_VALUE, STRING_VALUE);
    }

    @Provides
    static Set rawSet() {
      return ImmutableSet.of(STRING_VALUE, INT_VALUE);
    }
  }

  @Test
  public void testComponent() {
    RawTypesComponent component = DaggerRawTypesComponentTest_RawTypesComponent.create();
    assertThat(component.rawSet()).containsExactly(STRING_VALUE, INT_VALUE);
    assertThat(component.rawMap()).containsExactly(INT_VALUE, STRING_VALUE);
  }
}
