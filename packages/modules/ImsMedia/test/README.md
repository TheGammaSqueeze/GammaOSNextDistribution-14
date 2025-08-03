# ImsMedia test

## 1. Introduction
 - The `test/` directory in _ImsMedia_ contains test implementation of _ImsMedia_'.
 - Module `imsmediahal/` contains implementation of _ImsMedia_'s HAL APIs.


 ## 2. Procedure to test app

 Build `app` and `imsmediahal` under `test\` folder

 ```
 gcert
 cd test
 mma
 ```

 #### 2.1 Install test app from out folder and run
 ```
 adb install imsmediatestingapp.apk
 ```

 #### 2.2 Modify manifest file
 ```
 adb pull /vendor/etc/vintf/manifest.xml

 add below content in manifest file.

 <hal format="aidl">
        <name>android.hardware.radio.ims.media</name>
        <fqname>IImsMedia/default</fqname>
 </hal>

 push back manifest file to Device.

 adb push manifest.xml /vendor/etc/vintf/manifest.xml
 ```

 #### 2.3 Push imsmediahal apk into device from out folder
 ```
 adb push com.android.telephony.testimsmediahal /system/priv-app/

 adb reboot

 Test the Audio using test app
 ```
