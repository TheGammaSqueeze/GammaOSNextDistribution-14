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
package com.android.tradefed.invoker.shard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.build.ExistingBuildProvider;
import com.android.tradefed.build.StubBuildProvider;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDef;
import com.android.tradefed.config.DeviceConfigurationHolder;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IDeviceConfiguration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link ParentShardReplicate}. */
@RunWith(JUnit4.class)
public class ParentShardReplicateTest {

    /** Ensure sharded config with ExistingBuildProvider can still be replicated */
    @Test
    public void testMultiDevices_replicate_sharded() throws Exception {
        IConfiguration configuration = new Configuration("test", "test");
        configuration.getCommandOptions().setReplicateSetup(true);
        configuration.getCommandOptions().setShardCount(2);
        configuration.setCommandLine(new String[] {"tf/bootstrap"});
        List<IDeviceConfiguration> listConfigs = new ArrayList<>();
        IDeviceConfiguration holder1 =
                new DeviceConfigurationHolder(ConfigurationDef.DEFAULT_DEVICE_NAME);
        ExistingBuildProvider provider1 = new ExistingBuildProvider(null, null);
        holder1.addSpecificConfig(provider1);
        listConfigs.add(holder1);
        configuration.setDeviceConfigList(listConfigs);

        assertEquals(1, configuration.getDeviceConfig().size());
        ParentShardReplicate.replicatedSetup(configuration, null);
        assertEquals(2, configuration.getDeviceConfig().size());

        assertTrue(
                configuration.getDeviceConfig().get(1).getBuildProvider()
                        instanceof StubBuildProvider);
    }
}
