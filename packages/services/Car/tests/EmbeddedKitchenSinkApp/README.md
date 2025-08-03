# Embedded KitchenSink App

This is a privileged app for testing AAOS functionalities.

## Usage

**Build and Install**

```
m -j EmbeddedKitchenSinkApp && adb install -r -d $ANDROID_PRODUCT_OUT/system/priv-app/EmbeddedKitchenSinkApp/EmbeddedKitchenSinkApp.apk
```

**Start the app**

```
adb shell am start com.google.android.car.kitchensink/.KitchenSinkActivity
```

**Stop the app**

```
adb shell kill `adb shell pgrep kitchensink`
```
