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

package com.android.cts.verifier.companion;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.companion.CompanionDeviceService;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.compatibility.common.util.CddTest;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;

/**
 * Test that Companion Device Awake {@link CompanionDeviceService} API is functional.
 */
@CddTest(requirements = {"3.16/C-1-2", "C-1-3", "H-1-1"})
public class CompanionDeviceServiceTestActivity extends PassFailButtons.Activity {
    private static final String LOG_TAG = "CDMServiceTestActivity";
    private static final int REQUEST_CODE_CHOOSER = 0;

    private final ArrayList<TestStep> mTests = new ArrayList<>();

    private CompanionDeviceManager mCompanionDeviceManager;

    private TextView mTestTitle;
    private TextView mTestDescription;
    private ViewGroup mTestStepButtonLayout;
    private Button mTestAction;
    private Button mTestStepPassed;
    private Button mTestStepFailed;
    private int mCurrentTestIndex;
    private AssociationInfo mCurrentAssociation;

    // Test state verification loop will be launched on a new thread.
    // Null until first verification is required.
    private Runnable mVerifier;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.companion_service_test_main);

        mTestTitle = findViewById(R.id.companion_service_test_title);
        mTestDescription = findViewById(R.id.companion_service_test_description);
        mTestAction = findViewById(R.id.companion_service_test_button);
        mTestStepButtonLayout = findViewById(R.id.button_layout);
        mTestStepPassed = findViewById(R.id.test_step_passed);
        mTestStepFailed = findViewById(R.id.test_step_failed);

        mCurrentTestIndex = -1;
        mCurrentAssociation = null;
        mVerifier = null;
        mHandler = new Handler(Looper.myLooper());

        mCompanionDeviceManager = getSystemService(CompanionDeviceManager.class);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            // Cannot move forward if bluetooth feature is not supported on the device.
            mTests.add(new BluetoothFeatureTestStep());
        } else {
            // Add tests.
            mTests.add(new DeviceAssociationTestStep());
            mTests.add(new DevicePresentTestStep());
            mTests.add(new DeviceGoneTestStep());
            mTests.add(new DeviceDisassociationTestStep());
        }

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(R.string.companion_service_test, R.string.companion_service_test_info, -1);
        runNextTestOrShowSummary();
        cleanUp();
    }

    /**
     * Get association info with matching ID. Returns null if no match.
     */
    private AssociationInfo getAssociation(int id) {
        for (AssociationInfo association : mCompanionDeviceManager.getMyAssociations()) {
            if (association.getId() == id) return association;
        }
        return null;
    }

    /** Stop observing to associated device and then disassociate. */
    private void disassociate(AssociationInfo association) {
        String deviceAddress = association.getDeviceMacAddress().toString();
        mCompanionDeviceManager.stopObservingDevicePresence(deviceAddress);
        mCompanionDeviceManager.disassociate(association.getId());
        Log.d(LOG_TAG, "Disassociated with device: " + deviceAddress);
    }

    /** Clean up any associated devices from this app. */
    private void cleanUp() {
        for (AssociationInfo association : mCompanionDeviceManager.getMyAssociations()) {
            disassociate(association);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // On "associate()" success
        if (requestCode == REQUEST_CODE_CHOOSER) {
            if (resultCode != RESULT_OK) {
                fail("Activity result code " + resultCode);
                return;
            }
            AssociationInfo association =
                    data.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION,
                    AssociationInfo.class);

            // This test is for bluetooth devices, which should all have a MAC address.
            if (association == null || association.getDeviceMacAddress() == null) {
                fail("The device was present but its address was null.");
                return;
            }

            String deviceAddress = association.getDeviceMacAddress().toString();
            mCompanionDeviceManager.startObservingDevicePresence(deviceAddress);
            mCurrentAssociation = getAssociation(association.getId());
            Log.d(LOG_TAG, "Associated with device: " + deviceAddress);
        } else super.onActivityResult(requestCode, resultCode, data);
    }

    private void fail(Throwable reason) {
        Log.e(LOG_TAG, "Test failed", reason);
        fail(reason.getMessage());
    }

    private void fail(CharSequence reason) {
        Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
        Log.e(LOG_TAG, reason.toString());
        cleanUp();
        setTestResultAndFinish(false);
    }

    private TestStep getCurrentTest() {
        return mTests.get(mCurrentTestIndex);
    }

    private void runNextTestOrShowSummary() {
        if (mCurrentTestIndex + 1 >= mTests.size()) {
            updateViewForCompletionSummary();
        } else {
            mCurrentTestIndex++;
            updateViewForTest(getCurrentTest());
        }
    }

    /** Populates the UI based on the provided test step. */
    private void updateViewForTest(TestStep test) {
        mTestStepButtonLayout.setVisibility(VISIBLE);
        mTestTitle.setText(test.mTitleResId);
        mTestDescription.setText(test.mDescriptionResId);

        // Can't pass until test result is verified.
        mTestStepPassed.setEnabled(false);

        mTestStepPassed.setOnClickListener(v -> getCurrentTest().onPass());
        mTestStepFailed.setOnClickListener(v -> getCurrentTest().onFail());
        mTestAction.setOnClickListener(v -> getCurrentTest().performTestAction());

        if (mVerifier != null) {
            mHandler.removeCallbacks(mVerifier);
        }

        // Display test action button if specified.
        if (test.mButtonTextResId != 0) {
            mTestAction.setText(test.mButtonTextResId);
            mTestAction.setVisibility(VISIBLE);
        } else {
            mTestAction.setVisibility(INVISIBLE);
        }

        // Wait for test verification.
        mVerifier = new Runnable() {
            @Override
            public void run() {
                if (test.verify()) {
                    mTestStepPassed.setEnabled(true);
                } else {
                    mHandler.postDelayed(this, 3000);
                }
            }
        };
        mHandler.postDelayed(mVerifier, 1000);
    }

    /** Populates the UI indicating results of test & updates test buttons as needed */
    private void updateViewForCompletionSummary() {
        // No longer need any of these buttons
        mTestStepButtonLayout.setVisibility(INVISIBLE);
        mTestAction.setVisibility(INVISIBLE);

        // Can only reach here if all other tests passed. Enable pass button.
        getPassButton().setEnabled(true);
        mTestTitle.setText(R.string.companion_service_test_summary_title);
        mTestDescription.setText(R.string.companion_service_test_summary);
    }

    /**
     * This activity specifically tests for CDM interactions with bluetooth devices.
     * Pass the test if device does not support bluetooth.
     */
    private class BluetoothFeatureTestStep extends TestStep {
        BluetoothFeatureTestStep() {
            super(R.string.companion_service_bluetooth_feature_title,
                    R.string.companion_service_bluetooth_feature_text);
        }

        @Override
        boolean verify() {
            return true;
        }
    }

    /**
     * Tests that an association can be made with a device and that the app can subscribe
     * to its presence.
     */
    private class DeviceAssociationTestStep extends TestStep {
        final CompanionDeviceManager.Callback mCallback =
                new CompanionDeviceManager.Callback() {
            @Override
            public void onAssociationPending(IntentSender chooserLauncher) {
                try {
                    startIntentSenderForResult(chooserLauncher,
                            REQUEST_CODE_CHOOSER, null, 0, 0, 0);
                } catch (IntentSender.SendIntentException error) {
                    fail(error);
                }
            }

            @Override
            public void onFailure(CharSequence error) {
                fail(error);
            }
        };

        DeviceAssociationTestStep() {
            super(R.string.companion_service_associate_title,
                    R.string.companion_service_associate_text,
                    R.string.companion_service_associate_button);
        }

        @Override
        void performTestAction() {
            AssociationRequest request = new AssociationRequest.Builder()
                    .addDeviceFilter(new BluetoothDeviceFilter.Builder().build())
                    .build();
            mCompanionDeviceManager.associate(request, mCallback, null);
        }

        @Override
        boolean verify() {
            // Check that it is associated and being observed.
            // Bypass inaccessible AssociationInfo#isNotifyOnDeviceNearby() with toString()
            return mCurrentAssociation != null
                    && mCurrentAssociation.toString().contains("mNotifyOnDeviceNearby=true");
        }
    }

    /**
     * Tests that app can correctly detect associated device's presence.
     */
    private class DevicePresentTestStep extends TestStep {
        DevicePresentTestStep() {
            super(R.string.companion_service_present_title,
                    R.string.companion_service_present_text);
        }

        @Override
        boolean verify() {
            return DevicePresenceListener.isDeviceNearby(mCurrentAssociation.getId());
        }
    }

    /**
     * Tests that app can correctly detect device's disappearance.
     */
    private class DeviceGoneTestStep extends TestStep {
        DeviceGoneTestStep() {
            super(R.string.companion_service_gone_title,
                    R.string.companion_service_gone_text);
        }

        @Override
        public boolean verify() {
            return !DevicePresenceListener.isDeviceNearby(mCurrentAssociation.getId());
        }
    }

    /**
     * Tests that device can be correctly disassociated from the app.
     */
    private class DeviceDisassociationTestStep extends TestStep {
        DeviceDisassociationTestStep() {
            super(R.string.companion_service_disassociate_title,
                    R.string.companion_service_disassociate_text,
                    R.string.companion_service_disassociate_button);
        }

        @Override
        void performTestAction() {
            disassociate(mCurrentAssociation);
        }

        @Override
        boolean verify() {
            // Check that it is no longer associated.
            return getAssociation(mCurrentAssociation.getId()) == null;
        }
    }

    /**
     * Interface for individual test steps.
     */
    private abstract class TestStep {
        final int mTitleResId;
        final int mDescriptionResId;
        final int mButtonTextResId;

        TestStep(int titleResId, int descriptionResId) {
            this(titleResId, descriptionResId, 0);
        }

        TestStep(int titleResId, int descriptionResId, int buttonTextResId) {
            this.mTitleResId = titleResId;
            this.mDescriptionResId = descriptionResId;
            this.mButtonTextResId = buttonTextResId;
        }

        /** Code to run when the button is activated; only used if {@link #mButtonTextResId} != 0 */
        void performTestAction() {
            // optional
        }

        /** Checks device state to see if the test passed.  */
        abstract boolean verify();

        /**
         * Code to run on failure.
         */
        void onPass() {
            runNextTestOrShowSummary();
        }

        /**
         * Code to run on failure.
         */
        void onFail() {
            cleanUp();
            fail("Test failed manually.");
        }
    }
}
