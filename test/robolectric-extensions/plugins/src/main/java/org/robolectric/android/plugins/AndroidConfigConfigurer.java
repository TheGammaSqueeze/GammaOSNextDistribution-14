package org.robolectric.android.plugins;

import com.google.auto.service.AutoService;

import org.robolectric.pluginapi.config.Configurer;
import org.robolectric.pluginapi.config.GlobalConfigProvider;
import org.robolectric.plugins.ConfigConfigurer;
import org.robolectric.plugins.PackagePropertiesLoader;
import org.robolectric.util.Logger;
import org.robolectric.util.inject.Supercedes;

@AutoService(Configurer.class)
@Supercedes(ConfigConfigurer.class)
public class AndroidConfigConfigurer extends ConfigConfigurer {

  static {
    System.setProperty("robolectric.logging.enabled", "true");
    Logger.info("Logging turned on by AndroidConfigConfigurer.class");
  }

  protected AndroidConfigConfigurer(
          PackagePropertiesLoader packagePropertiesLoader) {
    super(packagePropertiesLoader);
  }

  public AndroidConfigConfigurer(PackagePropertiesLoader packagePropertiesLoader,
          GlobalConfigProvider defaultConfigProvider) {
    super(packagePropertiesLoader, defaultConfigProvider);
  }
}