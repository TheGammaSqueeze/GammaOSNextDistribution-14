package org.robolectric.shadows;

import android.os.Build;
import android.provider.DeviceConfig;
import android.provider.DeviceConfig.Properties;
import java.util.HashMap;
import java.util.Map;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;
import org.robolectric.util.ReflectionHelpers;

@Implements(value = DeviceConfig.class, isInAndroidSdk = false, minSdk = Build.VERSION_CODES.Q)
public class ShadowDeviceConfig {

  @Implementation
  protected static String getProperty(String namespace, String name) {
    // avoid call to Settings.Config
    return null;
  }

  @Implementation
  protected static Properties getProperties(String namespace, String... names) {
    Map<String, String> keyValueMap = new HashMap<>();
    for (String name : names) {
      keyValueMap.put(name, "false");
    }
    return new Properties(namespace, keyValueMap);
  }

  @Implements(className = "android.provider.DeviceConfig$Properties")
  public static class ShadowProperties {
    @Implementation
    protected boolean getBoolean(String namespace, boolean defaultValue) {
      return defaultValue;
    }
  }

  @Resetter
  public static void reset() {
    Object lock = ReflectionHelpers.getStaticField(DeviceConfig.class, "sLock");
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (lock) {
      if (RuntimeEnvironment.getApiLevel() == Build.VERSION_CODES.Q) {
        Map singleListeners =
            ReflectionHelpers.getStaticField(DeviceConfig.class, "sSingleListeners");
        singleListeners.clear();
      }

      Map listeners = ReflectionHelpers.getStaticField(DeviceConfig.class, "sListeners");
      listeners.clear();

      Map namespaces = ReflectionHelpers.getStaticField(DeviceConfig.class, "sNamespaces");
      namespaces.clear();
    }
  }
}
