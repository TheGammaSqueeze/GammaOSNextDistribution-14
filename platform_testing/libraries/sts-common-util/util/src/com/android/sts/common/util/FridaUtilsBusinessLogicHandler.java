package com.android.sts.common.util;

import java.util.Optional;

/** GCL-accessible Business Logic utility for FridaUtils */
public class FridaUtilsBusinessLogicHandler {
    // {0} = FRIDA_PACKAGE (e.g. 'frida-inject')
    // {1} = fridaVersion (e.g. '15.1.17')
    // {2} = FRIDA_OS (e.g. 'android')
    // {3} = fridaAbi (e.g. 'arm64')
    // Obtained from BusinessLogic, e.g. "{0}-{1}-{2}-{3}.xz"
    private static String fridaFilenameTemplate;
    private static String fridaVersion;

    public static String getFridaFilenameTemplate() {
        return fridaFilenameTemplate;
    }

    public void setFridaFilenameTemplate(String template) {
        fridaFilenameTemplate = template;
    }

    public static Optional<String> getFridaVersion() {
        return Optional.ofNullable(fridaVersion);
    }

    public void setFridaVersion(String version) {
        fridaVersion = version;
    }
}
