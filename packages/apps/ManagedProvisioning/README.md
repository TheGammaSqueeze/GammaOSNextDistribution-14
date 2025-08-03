# Managed Provisioning

Bundled app responsible for provisioning an enterprise device

## Flows

### QR

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.afwsamples.testdpc/com.afwsamples.testdpc.DeviceAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://testdpc-latest-apk.appspot.com/preview",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "gJD2YwtOiWJHkSMkkIfLRlj-quNqG1fb6v100QmzM9w="
}
```

![Code](https://chart.googleapis.com/chart?chs=420x420&cht=qr&chl=%7B%27android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME%27%3A+%27com.afwsamples.testdpc%2Fcom.afwsamples.testdpc.DeviceAdminReceiver%27%2C+%27android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION%27%3A+%27https%3A%2F%2Ftestdpc-latest-apk.appspot.com%2Fpreview%27%2C+%27android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM%27%3A+%27gJD2YwtOiWJHkSMkkIfLRlj-quNqG1fb6v100QmzM9w%3D%27%7D&choe=UTF-8)

## AS Setup

```bash
aidegen -n -i=s -p=/opt/android-studio-with-blaze-canary \
    packages/apps/ManagedProvisioning \
    frameworks/base \
    cts \
    vendor/xts \
    packages/apps/Settings \
    vendor/unbundled_google/packages/SettingsGoogle \
    external/connectedappssdk \
    packages/services/Car/packages/CarManagedProvisioning \
    vendor/google/apps/SetupWizardOverlay/PixelSetupWizard
```

## References

- [Local Development - Flagging for Trunk Stable Development](http://go/trunk-stable-flags-local-development)
