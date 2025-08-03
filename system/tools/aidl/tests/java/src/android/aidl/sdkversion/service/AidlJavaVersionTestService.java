/*
 * Copyright (C) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.aidl.sdkversion.service;

import android.aidl.sdkversion.ITestService;
import android.aidl.sdkversion.ITestService.TypedObject;
import android.os.Binder;
import android.os.ServiceManager;
import java.util.List;

public class AidlJavaVersionTestService {
  static class TestServiceServer extends ITestService.Stub {
    @Override
    public boolean RepeatBoolean(boolean token) {
      return token;
    }

    @Override
    public TypedObject RepeatTypedObject(TypedObject token) {
      return token;
    }

    @Override
    public List<TypedObject> RepeatTypedList(List<TypedObject> token) {
      return token;
    }
  }

  public static void main(String[] args) {
    TestServiceServer myServer = new TestServiceServer();
    ServiceManager.addService(ITestService.class.getName(), myServer);
    Binder.joinThreadPool();
  }
}
