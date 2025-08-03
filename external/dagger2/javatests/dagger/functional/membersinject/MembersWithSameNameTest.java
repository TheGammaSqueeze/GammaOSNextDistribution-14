/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.functional.membersinject;

import static com.google.common.truth.Truth.assertThat;

import dagger.Binds;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.functional.membersinject.subpackage.MembersWithSameNameParent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// https://github.com/google/dagger/issues/755
@RunWith(JUnit4.class)
public class MembersWithSameNameTest {
  @Test
  public void parentInjectsMaskedMembers() {
    MembersWithSameNameParent parent = new MembersWithSameNameParent();
    TestComponent component = DaggerMembersWithSameNameTest_TestComponent.create();
    component.injectParent(parent);
    assertThat(parent.parentSameName()).isNotNull();
    assertThat(parent.parentSameNameStringWasInvoked()).isTrue();
    assertThat(parent.parentSameNameObjectWasInvoked()).isTrue();
  }

  @Test
  public void childInjectsMaskedMembers() {
    MembersWithSameNameChild child = new MembersWithSameNameChild();
    TestComponent component = DaggerMembersWithSameNameTest_TestComponent.create();
    component.injectChild(child);
    assertThat(child.parentSameName()).isNotNull();
    assertThat(child.parentSameNameStringWasInvoked()).isTrue();
    assertThat(child.parentSameNameObjectWasInvoked()).isTrue();
    assertThat(child.childSameName()).isNotNull();
    assertThat(child.childSameNameStringWasInvoked()).isTrue();
    assertThat(child.childSameNameObjectWasInvoked()).isTrue();
  }

  @Test
  public void childInjectsParentMaskedMembersOnly() {
    MembersWithSameNameChild child = new MembersWithSameNameChild();
    TestComponent component = DaggerMembersWithSameNameTest_TestComponent.create();
    // Purposely only inject the parent members
    component.injectParent(child);
    assertThat(child.parentSameName()).isNotNull();
    assertThat(child.parentSameNameStringWasInvoked()).isTrue();
    assertThat(child.parentSameNameObjectWasInvoked()).isTrue();
    assertThat(child.childSameName()).isNull();
    assertThat(child.childSameNameStringWasInvoked()).isFalse();
    assertThat(child.childSameNameObjectWasInvoked()).isFalse();
  }

  @Module
  abstract static class TestModule {
    @Provides
    static String provideString() {
      return "";
    }

    @Binds
    abstract Object bindObject(String string);
  }

  @Component(modules = TestModule.class)
  interface TestComponent {
    void injectParent(MembersWithSameNameParent parent);
    void injectChild(MembersWithSameNameChild child);
  }
}
