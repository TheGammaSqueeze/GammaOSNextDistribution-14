package org.robolectric.android.plugins;

import com.google.auto.service.AutoService;

import org.robolectric.internal.dependency.DependencyResolver;
import org.robolectric.pluginapi.Sdk;
import org.robolectric.pluginapi.SdkProvider;
import org.robolectric.plugins.DefaultSdkProvider;
import org.robolectric.versioning.AndroidVersionInitTools;
import org.robolectric.versioning.AndroidVersions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import javax.annotation.Priority;

@AutoService(SdkProvider.class)
@Priority(2)
public class AndroidLocalSdkProvider extends DefaultSdkProvider {

    public AndroidLocalSdkProvider(DependencyResolver dependencyResolver) {
        super(dependencyResolver);
    }

    /**
     * In this method, a default known name for all android-all jars that are compiled in android's
     * default build system, soong, and currently produced by /external/robolectric-shadows/android.b
     */
    protected Path findTargetJar() throws IOException {
        DefaultSdk localBuiltSdk = new DefaultSdk(10000, "current", "r0", "UpsideDownCake", getJdkVersion());
        return localBuiltSdk.getJarPath();
    }

    protected int getJdkVersion(){
        String fullVersionStr = System.getProperty("java.version");
        if (fullVersionStr != null) {
            String[] parts = fullVersionStr.split("\\.");
            String versionStr = parts[0];
            if ("1".equals(parts[0]) && parts.length > 1) {
                versionStr = parts[1];
            }
            try {
                return Integer.parseInt(versionStr);
            } catch (NumberFormatException ex) {
                throw new RuntimeException("Could not determine jdk version ", ex);
            }
        }
        throw new RuntimeException("Could not determine jdk version");
    }

    protected void populateSdks(TreeMap<Integer, Sdk> knownSdks) {
        try {
            Path location = findTargetJar();
            try {
                JarFile jarFile = new JarFile(location.toFile());
                final AndroidVersions.AndroidRelease release = AndroidVersionInitTools.computeReleaseVersion(jarFile);
                if (release != null) {
                    DefaultSdk currentSdk = new ProvidedJarSdk(release.getSdkInt(), "current",
                            release.getShortCode(), location);
                    knownSdks.keySet()
                            .stream()
                            .filter(entry -> entry >= release.getSdkInt())
                            .forEach(entry -> knownSdks.remove(entry));
                    knownSdks.put(release.getSdkInt(), currentSdk);
                } else {
                    throw new RuntimeException(
                            "Could not read the version of the current android-all sdk"
                                    + "this prevents robolectric from determining which shadows to apply.");
                }
            } catch (IOException ioe) {
                throw new RuntimeException(
                        "Could not read the version of the current android-all sdk"
                                + "this prevents robolectric from determining which shadows to apply.",
                        ioe);
            }
        } catch (IOException ioe) {
            throw new RuntimeException("Could not populate robolectric sdks as dynamic config failed", ioe);
        }
        super.populateSdks(knownSdks);
    }

    /**
     * Provides an sdk that is detected from a Jar path.
     */
    private class ProvidedJarSdk extends DefaultSdk {

        private Path mJarPath;

        ProvidedJarSdk(int apiLevel, String androidVersion, String codeName, Path jarPath) {
            super(apiLevel, androidVersion, "", codeName, getJdkVersion());
            this.mJarPath = jarPath;
        }

        @Override
        public synchronized Path getJarPath() {
            return mJarPath;
        }
    }

}
