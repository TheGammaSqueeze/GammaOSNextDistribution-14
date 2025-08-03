package android.provider.aidl;

/**
 * {@hide}
 */
interface IDeviceConfigManager {
// TODO(b/265948914): maybe rename this IDeviceConfigService ? ManagerService?

    Map<String, String> getProperties(String namespace, in String[] names);

    boolean setProperties(String namespace, in Map<String, String> values);

    boolean setProperty(String namespace, String key, String value, boolean makeDefault);

    boolean deleteProperty(String namespace, String key);

    // TODO(b/265948914): add remaining methods
}
