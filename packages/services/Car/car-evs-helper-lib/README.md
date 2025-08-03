<!--
  Copyright (C) 2022 The Android Open Source Project
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
       http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License
  -->
# car-evs-helper-lib
This directory contains two modules that are used by other apps to process
CarEvsBufferDescriptor and render its contents to the display with EGL.
* `car-evs-helper-lib:` This library contains `CarEvsGLSurfaceView` and
  `CarEvsBufferRenderer` classes.
* `libcarevsglrenderer_jni`: This is a JNI library `CarEvsBufferRenderer` uses
  to render the contents of `CarEvsBufferDescriptor` with EGL.
## How to use
Please follow below instructions to delegate a `CarEvsBufferDescriptor` rendering
to this library.  A reference implementation is also available at
`packages/services/Car/tests/CarEvsCameraPreviewApp`.
1. Make the application refer to `car-evs-helper-lib` and
   `libcarevsglrenderer_jni` libraries by adding below lines to `Android.bp`.
```
static_libs: ["car-evs-helper-lib"],
jni_libs: ["libcarevsglrenderer_jni"],
```
2. Implement `CarEvsGLSurfaceView.Callback` interface. For example,
```
/**
 * This method is called by the renderer to fetch a new frame to draw.
 */
@Override
public CarEvsBufferDescriptor getNewFrame() {
    synchronized(mLock) {
        // Return a buffer to render.
        return mBufferToRender;
    }
}
/**
 * This method is called by the renderer when it is done with a passed
 * CarEvsBufferDescriptor object.
 */
@Override
public void returnBuffer(CarEvsBufferDescriptor buffer) {
    // Return a buffer to CarEvsService.
    try {
        mEvsManager.returnFrameBuffer(buffer);
    } catch (Exception e) {
        ...
    }
    ...
}
```
3. Create `CarEvsGLSurfaceView` with the application context,
   `CarEvsGLSurfaceView.Callback` object, and, optionally, a desired in-plane
   rotation angle.
```
private CarEvsGLSurfaceView mView;
@Override
protected void onCreate(Bundle savedInstanceState) {
    ...
    mView = CarEvsGLSurfaceView(getAppliation(), this, /* angleInDegree= */ 0);
    ...
}
```
4. Start a video stream and update wheneven new frame buffer arrives.  For
   example,
```
private final CarEvsManager.CarEvsStreamCallback mStreamHandler =
    new CarEvsManager.CarEvsStreamCallback() {
    ...
    @Override
    public void onNewFrame(CarEvsBufferDescriptor buffer) {
      synchronized(mLock) {
        mBufferToRender = buffer;
      }
    }
}
```
