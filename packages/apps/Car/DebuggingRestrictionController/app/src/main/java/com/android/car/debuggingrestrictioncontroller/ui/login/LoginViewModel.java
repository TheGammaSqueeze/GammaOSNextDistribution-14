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

import android.util.Patterns;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.android.car.debuggingrestrictioncontroller.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginViewModel extends ViewModel {

  private static final String TAG = LoginViewModel.class.getSimpleName();
  private static final int ERROR_USER_LOGOUT = -12001;
  private static final int ERROR_INVALID_INPUTS = -12002;
  private static final int ERROR_AUTH_FAILED = -12003;

  private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
  private final MutableLiveData<LoginFormState> loginFormState = new MutableLiveData<>();
  private final MutableLiveData<LoginResult> loginResult = new MutableLiveData<>();

  LiveData<LoginFormState> getLoginFormState() {
    return loginFormState;
  }

  LiveData<LoginResult> getLoginResult() {
    return loginResult;
  }

  public void login(String email, String password) {
    if (email.isEmpty() || password.isEmpty()) {
      loginFormState.setValue(
          new LoginFormState(R.string.invalid_username, R.string.invalid_password));
      loginResult.postValue(new LoginResult(ERROR_INVALID_INPUTS));
      return;
    }
    firebaseAuth
        .signInWithEmailAndPassword(email, password)
        .addOnCompleteListener(
            task -> {
              if (task.isSuccessful()) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                loginResult.postValue(new LoginResult(new LoggedInUserView(user.getDisplayName())));
              } else {
                loginResult.postValue(new LoginResult(ERROR_AUTH_FAILED));
              }
            });
  }

  public void logout() {
    firebaseAuth.signOut();
    loginResult.postValue(new LoginResult(ERROR_USER_LOGOUT));
  }

  public void loginDataChanged(String username, String password) {
    if (!isUserNameValid(username)) {
      loginFormState.setValue(new LoginFormState(R.string.invalid_username, null));
    } else if (!isPasswordValid(password)) {
      loginFormState.setValue(new LoginFormState(null, R.string.invalid_password));
    } else {
      loginFormState.setValue(new LoginFormState(true));
    }
  }

  // A placeholder username validation check
  private boolean isUserNameValid(String username) {
    if (username == null) {
      return false;
    }
    if (username.contains("@")) {
      return Patterns.EMAIL_ADDRESS.matcher(username).matches();
    } else {
      return !username.trim().isEmpty();
    }
  }

  // A placeholder password validation check
  private boolean isPasswordValid(String password) {
    return password != null && password.trim().length() > 5;
  }
}
