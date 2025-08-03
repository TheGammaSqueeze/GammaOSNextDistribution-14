/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.imsserviceentitlement;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.WindowManager;

import com.android.imsserviceentitlement.ActivityConstants;
import com.android.imsserviceentitlement.R;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

/** Main activity to handle VoWiFi activation on QNS module*/
public class WfcQnsActivationActivity extends FragmentActivity {

  public static final String TAG = "IMSSE-WfcQnsActivationActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_wfc_activation);
    new Handler(getMainLooper()).post(this::startWebPortalFragment);
  }

  private void startWebPortalFragment() {
    String url = ActivityConstants.getUrl(getIntent());
    if (url.isEmpty()) {
      return;
    }
    WfcQnsWebPortalFragment wfcQnsWebPortalFragment = WfcQnsWebPortalFragment.newInstance(url);
    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
    ft.replace(R.id.wfc_activation_container, wfcQnsWebPortalFragment);
    ft.commit();
  }
}
