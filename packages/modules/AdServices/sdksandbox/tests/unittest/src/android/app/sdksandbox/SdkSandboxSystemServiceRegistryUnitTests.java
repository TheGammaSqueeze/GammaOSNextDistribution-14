/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.app.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link SdkSandboxSystemServiceRegistry}. */
@RunWith(JUnit4.class)
public class SdkSandboxSystemServiceRegistryUnitTests {

    @Test
    public void testGetInstanceIsSingeltone() throws Exception {
        SdkSandboxSystemServiceRegistry instance = SdkSandboxSystemServiceRegistry.getInstance();
        assertThat(instance).isSameInstanceAs(SdkSandboxSystemServiceRegistry.getInstance());
    }

    @Test
    public void testRegisterServiceMutator() throws Exception {
        SdkSandboxSystemServiceRegistry registry = new SdkSandboxSystemServiceRegistry();
        SdkSandboxSystemServiceRegistry.ServiceMutator mutator = (service, ctx) -> service;
        registry.registerServiceMutator("foo", mutator);
        assertThat(registry.getServiceMutator("foo")).isSameInstanceAs(mutator);
    }

    @Test
    public void testGetNoServiceMutator() throws Exception {
        SdkSandboxSystemServiceRegistry registry = new SdkSandboxSystemServiceRegistry();
        assertThat(registry.getServiceMutator("foo")).isNull();
    }
}
