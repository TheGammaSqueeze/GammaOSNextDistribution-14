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

package com.android.car.debuggingrestrictioncontroller.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.test.espresso.idling.CountingIdlingResource;
import com.android.car.debuggingrestrictioncontroller.R;
import com.android.car.debuggingrestrictioncontroller.ui.token.TokenActivity;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

  private static final String TAG = LoginActivity.class.getSimpleName();
  private static final int REQUEST_TOKEN = 1;

  private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

  @VisibleForTesting
  private final CountingIdlingResource idlingResource = new CountingIdlingResource(TAG);

  private final LoginViewModel loginViewModel = new LoginViewModel();
  private Button loginButton;
  private Button nextButton;

  @VisibleForTesting
  public CountingIdlingResource getIdlingResource() {
    return idlingResource;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_login);

    final EditText usernameEditText = findViewById(R.id.username);
    final EditText passwordEditText = findViewById(R.id.password);
    loginButton = findViewById(R.id.login);
    nextButton = findViewById(R.id.next);
    final ProgressBar loadingProgressBar = findViewById(R.id.loading);

    loginViewModel
        .getLoginFormState()
        .observe(
            this,
            new Observer<LoginFormState>() {
              @Override
              public void onChanged(@NonNull LoginFormState loginFormState) {
                loginButton.setEnabled(loginFormState.isDataValid());
                if (loginFormState.getUsernameError() != null) {
                  usernameEditText.setError(getString(loginFormState.getUsernameError()));
                }
                if (loginFormState.getPasswordError() != null) {
                  passwordEditText.setError(getString(loginFormState.getPasswordError()));
                }
              }
            });

    loginViewModel
        .getLoginResult()
        .observe(
            this,
            new Observer<LoginResult>() {
              @Override
              public void onChanged(@NonNull LoginResult loginResult) {
                loadingProgressBar.setVisibility(View.GONE);
                if (loginResult.getError() != null) {
                  showSnackBar(R.string.not_signed_in);
                  loginButton.setText(R.string.button_sign_in);
                  nextButton.setEnabled(false);
                }
                if (loginResult.getSuccess() != null) {
                  String userName = loginResult.getSuccess().getDisplayName();
                  Log.d(TAG, "User " + userName + " signed in");
                  loginButton.setText(R.string.button_sign_out);
                  nextButton.setEnabled(true);
                }
                if (!idlingResource.isIdleNow()) {
                  idlingResource.decrement();
                }
              }
            });

    TextWatcher afterTextChangedListener =
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // ignore
          }

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
            // ignore
          }

          @Override
          public void afterTextChanged(Editable s) {
            loginViewModel.loginDataChanged(
                usernameEditText.getText().toString(), passwordEditText.getText().toString());
          }
        };
    usernameEditText.addTextChangedListener(afterTextChangedListener);
    passwordEditText.addTextChangedListener(afterTextChangedListener);
    passwordEditText.setOnEditorActionListener(
        new TextView.OnEditorActionListener() {
          @Override
          public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
              idlingResource.increment();
              loginViewModel.login(
                  usernameEditText.getText().toString(), passwordEditText.getText().toString());
            }
            return false;
          }
        });

    loginButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            idlingResource.increment();
            loadingProgressBar.setVisibility(View.VISIBLE);
            if (firebaseAuth.getCurrentUser() == null) {
              loginViewModel.login(
                  usernameEditText.getText().toString(), passwordEditText.getText().toString());
            } else {
              loginViewModel.logout();
            }
          }
        });

    nextButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            Intent intent = new Intent(getApplicationContext(), TokenActivity.class);
            startActivityForResult(intent, REQUEST_TOKEN);
          }
        });
  }

  @Override
  protected void onResume() {
    updateButtonState();
    super.onResume();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_TOKEN) {
      if (resultCode == RESULT_OK) {
        showSnackBar(R.string.token_authorized);
      } else if (resultCode == RESULT_CANCELED) {
        showSnackBar(R.string.token_unauthorized);
      }
    }
  }

  private void showSnackBar(@StringRes Integer id) {
    final Snackbar snackbar =
        Snackbar.make(findViewById(R.id.login_container), id, Snackbar.LENGTH_SHORT);
    snackbar.show();
  }

  private void updateButtonState() {
    if (firebaseAuth.getCurrentUser() != null) {
      loginButton.setText(R.string.button_sign_out);
      loginButton.setEnabled(true);
      nextButton.setEnabled(true);
    } else {
      loginButton.setText(R.string.button_sign_in);
      loginButton.setEnabled(false);
      nextButton.setEnabled(false);
    }
  }
}
