/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.textclassifier.testing;

import android.provider.DeviceConfig.Properties;
import androidx.annotation.NonNull;
import com.android.textclassifier.common.TextClassifierSettings;
import java.util.HashMap;
import javax.annotation.Nullable;

/** A fake DeviceConfig implementation for testing purpose. */
public final class TestingDeviceConfig implements TextClassifierSettings.IDeviceConfig {

  private final HashMap<String, String> configs;

  public TestingDeviceConfig() {
    this.configs = new HashMap<>();
  }

  public void setConfig(String key, String value) {
    configs.put(key, value);
  }

  public void setConfig(String key, boolean value) {
    configs.put(key, Boolean.toString(value));
  }

  public void setConfig(String key, int value) {
    configs.put(key, Integer.toString(value));
  }

  public void setConfig(String key, float value) {
    configs.put(key, Float.toString(value));
  }

  public void setConfig(String key, long value) {
    configs.put(key, Long.toString(value));
  }

  @Override
  public Properties getProperties(@NonNull String namespace, @NonNull String... names) {
    Properties.Builder builder = new Properties.Builder(namespace);
    for (String key : configs.keySet()) {
      builder.setString(key, configs.get(key));
    }
    return builder.build();
  }

  @Override
  public boolean getBoolean(@NonNull String namespace, @NonNull String name, boolean defaultValue) {
    return configs.containsKey(name) ? Boolean.parseBoolean(configs.get(name)) : defaultValue;
  }

  @Override
  public String getString(
      @NonNull String namespace, @NonNull String name, @Nullable String defaultValue) {
    return configs.containsKey(name) ? configs.get(name) : defaultValue;
  }

  @Override
  public int getInt(@NonNull String namespace, @NonNull String name, int defaultValue) {
    return configs.containsKey(name) ? Integer.parseInt(configs.get(name)) : defaultValue;
  }

  @Override
  public float getFloat(@NonNull String namespace, @NonNull String name, float defaultValue) {
    return configs.containsKey(name) ? Float.parseFloat(configs.get(name)) : defaultValue;
  }

  @Override
  public long getLong(@NonNull String namespace, @NonNull String name, long defaultValue) {
    return configs.containsKey(name) ? Long.parseLong(configs.get(name)) : defaultValue;
  }
}
