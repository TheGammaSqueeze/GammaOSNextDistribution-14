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

package android.translation.cts.unittests;

import static android.view.translation.TranslationResponseValue.STATUS_SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.translation.cts.R;
import android.translation.cts.SimpleActivity;
import android.util.Log;
import android.view.translation.TranslationResponseValue;
import android.view.translation.TranslationSpec;
import android.view.translation.ViewTranslationRequest;
import android.view.translation.ViewTranslationResponse;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

/**
 * Tests for {@link TextView} translation related APIs.
 */
@RunWith(AndroidJUnit4.class)
public class TextViewTranslationTest {

    private static final String TAG = "TextViewTranslationTest";
    private static final int[] SUPPORTED_DATA_FORMAT = new int[]{TranslationSpec.DATA_FORMAT_TEXT};

    @Rule
    public ActivityScenarioRule<SimpleActivity> mActivityRule = new ActivityScenarioRule<>(
            SimpleActivity.class);

    ActivityScenario<SimpleActivity> mScenario;
    TextView mTestTextView;
    TestRequestConsumer mRequestConsumer;

    @Before
    public void setup() throws Exception {
        mScenario = mActivityRule.getScenario();
        mScenario.onActivity(activity -> {
            mTestTextView = activity.findViewById(R.id.hello);
        });
        mRequestConsumer = new TestRequestConsumer();
    }

    @After
    public void cleanup() throws Exception {
        if (mScenario != null) {
            mScenario.close();
            mScenario = null;
        }
        mTestTextView = null;
    }

    @Test
    public void testOnCreateViewTranslationRequest() throws Throwable {
        mScenario.onActivity(activity -> {
            // Normal text view
            mTestTextView.onCreateViewTranslationRequest(SUPPORTED_DATA_FORMAT, mRequestConsumer);
        });
        ViewTranslationRequest request = mRequestConsumer.getRequest();
        Log.d(TAG, "Collect request = " + request);

        assertThat(request).isNotNull();
        assertThat(request.getAutofillId()).isEqualTo(mTestTextView.getAutofillId());
        assertThat(request.getValue(ViewTranslationRequest.ID_TEXT).getText().toString())
                .isEqualTo(mTestTextView.getText().toString());
    }

    @Test
    public void testOnCreateViewTranslationRequest_emptyText_returnsNull() throws Throwable {
        mScenario.onActivity(activity -> {
            // TextView with empty
            mTestTextView.setText("");
            mTestTextView.onCreateViewTranslationRequest(SUPPORTED_DATA_FORMAT, mRequestConsumer);
        });
        Log.d(TAG, "Collect request = " + mRequestConsumer.getRequest());

        assertThat(mRequestConsumer.getRequest()).isNull();
    }

    @Test
    public void testOnCreateViewTranslationRequest_hasPassword_returnsNull() throws Throwable {
        mScenario.onActivity(activity -> {
            // TextView with android:password true
            mTestTextView = activity.findViewById(R.id.textview_password);
            mTestTextView.setText("fake_password");
            mTestTextView.onCreateViewTranslationRequest(SUPPORTED_DATA_FORMAT, mRequestConsumer);
        });
        Log.d(TAG, "Collect request = " + mRequestConsumer.getRequest());

        assertThat(mRequestConsumer.getRequest()).isNull();
    }

    @Test
    public void testOnCreateViewTranslationRequest_hasPasswordTransformationMethod_returnsNull()
            throws Throwable {
        mScenario.onActivity(activity -> {
            // TextView with PasswordTransformationMethod
            mTestTextView.setTransformationMethod(new PasswordTransformationMethod());
            mTestTextView.onCreateViewTranslationRequest(SUPPORTED_DATA_FORMAT, mRequestConsumer);
        });
        Log.d(TAG, "Collect request = " + mRequestConsumer.getRequest());

        assertThat(mRequestConsumer.getRequest()).isNull();
    }

    @Test
    public void testOnCreateViewTranslationRequest_textSelectable_returnsRequest()
            throws Throwable {
        mScenario.onActivity(activity -> {
            // TextView is selectable with default callback
            mTestTextView.setTextIsSelectable(true);
            mTestTextView.onCreateViewTranslationRequest(SUPPORTED_DATA_FORMAT, mRequestConsumer);
        });
        ViewTranslationRequest request = mRequestConsumer.getRequest();
        Log.d(TAG, "Collect request = " + request);

        assertThat(request).isNotNull();
        assertThat(request.getAutofillId()).isEqualTo(mTestTextView.getAutofillId());
        assertThat(request.getValue(ViewTranslationRequest.ID_TEXT).getText().toString())
                .isEqualTo(mTestTextView.getText().toString());
    }

    @Test
    public void testOnCreateViewTranslationRequest_textEditable_returnsNull() throws Throwable {
        mScenario.onActivity(activity -> {
            // TextView is editable
            mTestTextView.setText("Test", TextView.BufferType.EDITABLE);
            mTestTextView.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
            mTestTextView.onCreateViewTranslationRequest(SUPPORTED_DATA_FORMAT, mRequestConsumer);
        });
        Log.d(TAG, "Collect request = " + mRequestConsumer.getRequest());

        assertThat(mRequestConsumer.getRequest()).isNull();
    }

    @Test
    public void testonViewTranslationResponse() throws Throwable {
        final ViewTranslationResponse expectedResponse =
                new ViewTranslationResponse.Builder(mTestTextView.getAutofillId())
                        .setValue(ViewTranslationRequest.ID_TEXT,
                                new TranslationResponseValue.Builder(STATUS_SUCCESS)
                                        .setText("HELLO WORLD").build()).build();
        mScenario.onActivity(activity -> {
            // ViewTranslationResponse is set in onViewTranslationResponse
            mTestTextView.onViewTranslationResponse(expectedResponse);
        });
        final ViewTranslationResponse response = mTestTextView.getViewTranslationResponse();
        Log.d(TAG, "response = " + response);

        assertThat(response).isNotNull();
        assertThat(mTestTextView.getViewTranslationResponse()).isEqualTo(expectedResponse);
    }

    private static final class TestRequestConsumer implements Consumer<ViewTranslationRequest> {
        private ViewTranslationRequest mRequest;

        @Override
        public void accept(ViewTranslationRequest request) {
            mRequest = request;
        }

        ViewTranslationRequest getRequest() {
            return mRequest;
        }
    }
}
