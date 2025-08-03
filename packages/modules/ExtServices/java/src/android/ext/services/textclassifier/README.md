ExtServices module - TextClassifier
=============================

### TextClassifierService
The TextClassifierService provides text classification related features for the system. It backs
TextClassifier APIs and Android features such as smart text selection and smart suggestions in
notifications. The ExtServices module contains the default text classifier implementation, the
vendors can configure a custom TextClassifierService by specifying the
config_defaultTextClassifierPackage in config.xml

### Test
- MTS
- Manual test (TextClassifier infra)
  - Select a text (e.g. phone number) and shows a popup menu to make sure the "Call" menu is shown.
- Manual test (Model downloader)
  1. Run: adb shell cmd device_config put textclassifier model_download_backoff_delay_in_millis 1
  && adb shell cmd device_config put textclassifier model_download_manager_enabled true
  && adb shell cmd device_config put textclassifier textclassifier_service_package_override
  com.google.android.ext.services && adb shell cmd device_config put textclassifier
  manifest_url_annotator_en
  https://www.gstatic.com/android/text_classifier/r/experimental/v999999999/en.fb.manifest
  && adb logcat | grep androidtc
  2. Select any text several times on screen and observe the printed log. You should see "androidtc:
     Loading ModelFile { type=annotator
     path=/data/user/0/com.google.android.ext.services/files/textclassifier/downloads/models/https___www_gstatic_com_android_text_classifier_r_experimental_v999999999_en_fb.model
     version=999999999 locales=en isAsset=false}".

### Other resources
- [Android 11 release text classifier](https://source.android.com/docs/core/display/textclassifier#11-release)