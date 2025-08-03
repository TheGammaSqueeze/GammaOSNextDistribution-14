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

package com.android.car.debuggingrestrictioncontroller.ui.token;

import android.app.Activity;
import android.os.Bundle;
import android.os.UserManager;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.test.espresso.idling.CountingIdlingResource;
import com.android.car.debuggingrestrictioncontroller.R;
import com.google.common.collect.ImmutableSet;
import com.google.firebase.auth.FirebaseAuth;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TokenActivity extends AppCompatActivity {

  private static final String TAG = TokenActivity.class.getSimpleName();
  private static final Set<String> SUPPORTED_RESTRICTIONS =
      ImmutableSet.of(UserManager.DISALLOW_DEBUGGING_FEATURES);
  private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

  @VisibleForTesting
  private final CountingIdlingResource idlingResource = new CountingIdlingResource(TAG);

  private final TokenViewModel tokenViewModel = new TokenViewModel();
  private UserManager userManager;
  private Button agreeButton;
  private Button disagreeButton;

  @VisibleForTesting
  public CountingIdlingResource getIdlingResource() {
    return idlingResource;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    userManager = getSystemService(UserManager.class);
    setContentView(R.layout.activity_token);

    final TextView agreementTextView = findViewById(R.id.agreement);
    final ProgressBar loadingProgressBar = findViewById(R.id.token_loading);
    agreeButton = findViewById(R.id.agree);
    disagreeButton = findViewById(R.id.disagree);

    Spanned agreementString =
        Html.fromHtml(getString(R.string.agreement_text), Html.FROM_HTML_MODE_LEGACY);
    agreementTextView.setText(agreementString);

    tokenViewModel
        .getTokenResult()
        .observe(
            this,
            new Observer<TokenResult>() {
              @Override
              public void onChanged(@NonNull TokenResult result) {
                loadingProgressBar.setVisibility(View.GONE);
                if (!idlingResource.isIdleNow()) {
                  idlingResource.decrement();
                }
                if (result.getError() != null) {
                  setResult(Activity.RESULT_CANCELED);
                  finish();
                }
                if (result.getSuccess() != null) {

                  Log.d(TAG, "Message: " + result.getSuccess().getMessage());
                  HashMap<String, Boolean> approvedRestrictions =
                      result.getSuccess().getApprovedRestrictions();
                  approvedRestrictions
                      .entrySet()
                      .removeIf(entry -> !SUPPORTED_RESTRICTIONS.contains(entry.getKey()));
                  try {
                    approvedRestrictions
                        .entrySet()
                        .forEach(
                            entry ->
                                userManager.setUserRestriction(entry.getKey(), entry.getValue()));
                    setResult(Activity.RESULT_OK);
                  } catch (SecurityException e) {
                    Log.e(TAG, "This app requires the MANAGE_USERS permission");
                    setResult(Activity.RESULT_CANCELED);
                  }
                  finish();
                }
              }
            });

    agreeButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            idlingResource.increment();
            Map<String, Object> query = new HashMap<>();
            loadingProgressBar.setVisibility(View.VISIBLE);
            tokenViewModel.requestAccessToken(query);
          }
        });

    disagreeButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            setResult(Activity.RESULT_CANCELED);
            finishAffinity();
          }
        });
  }

  @Override
  protected void onResume() {
    updateButtonState();
    super.onResume();
  }

  private void updateButtonState() {
    if (firebaseAuth.getCurrentUser() == null) {
      agreeButton.setEnabled(false);
      setResult(Activity.RESULT_CANCELED);
      finish();
    } else {
      agreeButton.setEnabled(true);
    }
  }
}
