/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.autofillservice.cts.activities;

import android.autofillservice.cts.R;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.autofill.AutofillManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

public class MultipleStepsSignInActivity extends AbstractAutoFillActivity {

    private static final String TAG = "AbstractMultipleStepsActivity";
    private static final String MESSAGE_STEP = "Showing step ";
    private static final String MESSAGE_FINISH = "Finished";

    private static MultipleStepsSignInActivity sCurrentActivity;

    /**
     * Gests the latest instance.
     *
     * <p>Typically used in test cases that rotates the activity
     */
    @SuppressWarnings("unchecked") // Its up to caller to make sure it's setting the right one
    public static <T extends MultipleStepsSignInActivity> T getCurrentActivity() {
        return (T) sCurrentActivity;
    }

    private TextView mStatus;
    private ViewGroup mContainer;

    private Button mPrevButton;
    private Button mNextButton;
    private Button mFinishButton;

    private int mCurrentStep;
    private boolean mFinished;

    protected List<LinearLayout> mSteps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sCurrentActivity = this;
        mCurrentStep = 0;

        setContentView(R.layout.multiple_steps_activity);

        mStatus = findViewById(R.id.status);
        mContainer = findViewById(R.id.container);
        mPrevButton = findViewById(R.id.prev);
        mNextButton = findViewById(R.id.next);
        mFinishButton = findViewById(R.id.finish);

        View.OnClickListener onClickListener = (v) -> {
            if (v == mPrevButton) {
                showStep(mCurrentStep - 1);
            } else if (v == mNextButton) {
                showStep(mCurrentStep + 1);
            } else {
                finishSelf();
            }
        };
        mPrevButton.setOnClickListener(onClickListener);
        mNextButton.setOnClickListener(onClickListener);
        mFinishButton.setOnClickListener(onClickListener);

        mSteps = getStepsMap();

        showStep(0);
    }

    public void nextPage() {
        runOnUiThread(() -> mNextButton.performClick());
    }

    public void prevPage() {
        runOnUiThread(() -> mPrevButton.performClick());
    }

    public void hideSoftInput() {
        final InputMethodManager imm = getSystemService(InputMethodManager.class);
        imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
    }

    private void showStep(int i) {
        if (mFinished || i < 0 || i >= mSteps.size()) {
            Log.w(TAG, String.format("Invalid step: %d (finished=%b, range=[%d,%d])",
                    i, mFinished, 0, mSteps.size() - 1));
            return;
        }

        View step = mSteps.get(i);
        mStatus.setText(MESSAGE_STEP + i);
        Log.d(TAG, "Showing step " + i);

        if (mContainer.getChildCount() > 0) {
            mContainer.removeViewAt(0);
        }
        mContainer.addView(step);
        mCurrentStep = i;

        mPrevButton.setEnabled(mCurrentStep != 0);
        mNextButton.setEnabled(mCurrentStep != mSteps.size() - 1);
    }

    private void finishSelf() {
        mStatus.setText(MESSAGE_FINISH);
        mContainer.removeAllViews();
        mFinished = true;
        AutofillManager afm = getSystemService(AutofillManager.class);
        if (afm != null) {
            afm.commit();
        }
    }

    protected List<LinearLayout> getStepsMap() {
        List<LinearLayout> steps = new ArrayList<>(2);
        steps.add(newStep(R.layout.username));
        steps.add(newStep(R.layout.password));
        return ImmutableList.copyOf(steps);
    }

    private LinearLayout newStep(int resId) {
        final LayoutInflater inflater = LayoutInflater.from(this);
        return (LinearLayout) inflater.inflate(resId, null);
    }
}
