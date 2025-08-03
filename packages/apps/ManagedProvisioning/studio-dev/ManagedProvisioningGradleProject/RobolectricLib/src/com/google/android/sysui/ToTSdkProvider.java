package com.google.android.sysui;

import com.google.auto.service.AutoService;

import org.robolectric.android.plugins.AndroidLocalSdkProvider;
import org.robolectric.internal.dependency.DependencyResolver;
import org.robolectric.pluginapi.SdkProvider;
import org.robolectric.util.inject.Supercedes;

import java.io.File;
import java.nio.file.Path;

import javax.annotation.Priority;
import javax.inject.Inject;

/**
 * SDK provider to latest system image from build server.
 */
@AutoService(SdkProvider.class)
@Priority(10)
@Supercedes(AndroidLocalSdkProvider.class)
public class ToTSdkProvider extends AndroidLocalSdkProvider {

    @Inject
    public ToTSdkProvider(DependencyResolver dependencyResolver) {
        super(dependencyResolver);
    }

    @Override
    protected Path findTargetJar() {
        File jarDir = new File(BuildConfig.OUT_PATH, "android_all");
        for (File f : jarDir.listFiles()) {
            if (f.isFile()) {
                String name = f.getName();
                if (name.startsWith("android-all") && name.endsWith(".jar")) {
                    return f.toPath();
                }
            }
        }
        return null;
    }
}
