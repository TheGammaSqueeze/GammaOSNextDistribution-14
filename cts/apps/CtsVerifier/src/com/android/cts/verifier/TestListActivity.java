/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.cts.verifier;

import static android.content.pm.PermissionInfo.PROTECTION_DANGEROUS;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.SearchView;
import android.widget.Switch;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/** Top-level {@link ListActivity} for launching tests and managing results. */
public class TestListActivity extends AbstractTestListActivity implements View.OnClickListener {
    private static final int CTS_VERIFIER_PERMISSION_REQUEST = 1;
    private static final int CTS_VERIFIER_BACKGROUND_LOCATION_PERMISSION_REQUEST = 2;

    private static final String TAG = TestListActivity.class.getSimpleName();
    // Records the current display mode.
    // Default is unfolded mode, and it will be changed when clicking the switch button.
    public static volatile String sCurrentDisplayMode = DisplayMode.UNFOLDED.toString();

    // Whether the verifier-system plan is enabled.
    private static boolean sIsSystemEnabled = false;
    // Whether the system switch has been toggled.
    private static boolean sHasSystemToggled = false;

    private String[] mRequestedPermissions;

    // Enumerates the display modes, including unfolded and folded.
    protected enum DisplayMode {
        UNFOLDED,
        FOLDED;

        @Override
        public String toString() {
            return name().toLowerCase();
        }

        /**
         * Converts the mode as suffix with brackets for test name.
         *
         * @return a string containing mode with brackets for folded mode; empty string for unfolded
         *     mode
         */
        public String asSuffix() {
            if (name().equals(FOLDED.name())) {
                return String.format("[%s]", toString());
            }
            return "";
        }
    }

    @Override
    public void onClick(View v) {
        handleMenuItemSelected(v.getId());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            PackageManager pm = getPackageManager();
            PackageInfo packageInfo =
                    pm.getPackageInfo(
                            getApplicationInfo().packageName, PackageManager.GET_PERMISSIONS);
            mRequestedPermissions = packageInfo.requestedPermissions;

            if (mRequestedPermissions != null) {
                String[] permissionsToRequest =
                        removeString(
                                mRequestedPermissions,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                permissionsToRequest =
                        Arrays.stream(permissionsToRequest)
                                .filter(
                                        s -> {
                                            try {
                                                return (pm.getPermissionInfo(s, 0).getProtection()
                                                                & PROTECTION_DANGEROUS)
                                                        != 0;
                                            } catch (NameNotFoundException e) {
                                                return false;
                                            }
                                        })
                                .toArray(String[]::new);
                requestPermissions(permissionsToRequest, CTS_VERIFIER_PERMISSION_REQUEST);
            }
            createContinue();
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Unable to load package's permissions", e);
            Toast.makeText(this, R.string.runtime_permissions_error, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == CTS_VERIFIER_PERMISSION_REQUEST) {
            if (arrayContains(grantResults, PackageManager.PERMISSION_DENIED)) {
                Log.v(TAG, "Didn't grant all permissions.");
                // If we're sending them to settings we don't need to request background location
                // since they can just grant in settings.
                sendUserToSettings();
            } else if (new ArrayList<>(Arrays.asList(mRequestedPermissions))
                    .contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                requestPermissions(
                        new String[] {Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        CTS_VERIFIER_BACKGROUND_LOCATION_PERMISSION_REQUEST);
            }
            return;
        }
        if (requestCode == CTS_VERIFIER_BACKGROUND_LOCATION_PERMISSION_REQUEST) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Log.v(TAG, "Didn't grant background permission.");
                sendUserToSettings();
            }
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.test_list_menu, menu);

        // Switch button for unfolded and folded tests.
        MenuItem item = (MenuItem) menu.findItem(R.id.switch_item);
        item.setActionView(R.layout.display_mode_switch);
        Switch displayModeSwitch = item.getActionView().findViewById(R.id.switch_button);

        // Get the current display mode to show switch status.
        boolean isFoldedMode = sCurrentDisplayMode.equals(DisplayMode.FOLDED.toString());
        displayModeSwitch.setChecked(isFoldedMode);

        displayModeSwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            sCurrentDisplayMode = DisplayMode.FOLDED.toString();
                        } else {
                            sCurrentDisplayMode = DisplayMode.UNFOLDED.toString();
                        }
                        handleSwitchItemSelected();
                    }
                });

        // Switch button for verifier-system plan.
        item = (MenuItem) menu.findItem(R.id.system_switch_item);
        if (item != null) {
            item.setActionView(R.layout.system_switch);
            final Switch systemSwitch = item.getActionView().findViewById(
                    R.id.system_switch_button);

            systemSwitch.setChecked(sIsSystemEnabled);
            systemSwitch.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {

                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            if (!sHasSystemToggled && isChecked) {
                                sHasSystemToggled = true;
                                notifyUserSystemPlan(systemSwitch);
                            } else {
                                updateIsSystemEnabled(isChecked);
                            }
                        }
                    });
        }

        SearchView searchView = (SearchView) menu.findItem(R.id.search_test).getActionView();
        searchView.setOnQueryTextListener(
                new SearchView.OnQueryTextListener() {

                    public boolean onQueryTextSubmit(String query) {
                        Log.i(TAG, "Got submitted query: " + query);
                        handleQueryUpdated(query);
                        return true;
                    }

                    public boolean onQueryTextChange(String newText) {
                        if (newText == null || newText.isEmpty()) {
                            Log.i(TAG, "Clear filter");
                            handleQueryUpdated(newText);
                            return true;
                        } else {
                            return false;
                        }
                    }
                });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return handleMenuItemSelected(item.getItemId()) ? true : super.onOptionsItemSelected(item);
    }

    /** Gets the verifier-system plan enabled status. */
    static boolean getIsSystemEnabled() {
        return sIsSystemEnabled;
    }

    /** Checks if a list of int array contains a given int value. */
    private static boolean arrayContains(int[] array, int value) {
        if (array == null) {
            return false;
        }
        for (int element : array) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    /** Removes the first occurrence of a string from a given string array. */
    private static String[] removeString(String[] cur, String val) {
        if (cur == null) {
            return null;
        }
        final int n = cur.length;
        for (int i = 0; i < n; i++) {
            if (Objects.equals(cur[i], val)) {
                String[] ret = new String[n - 1];
                if (i > 0) {
                    System.arraycopy(cur, 0, ret, 0, i);
                }
                if (i < (n - 1)) {
                    System.arraycopy(cur, i + 1, ret, i, n - i - 1);
                }
                return ret;
            }
        }
        return cur;
    }

    private void createContinue() {
        if (!isTaskRoot()) {
            finish();
        }

        // Restores the last display mode when launching the app after killing the process.
        if (getCurrentDisplayMode().equals(DisplayMode.FOLDED.toString())) {
            sCurrentDisplayMode = DisplayMode.FOLDED.toString();
        }

        setTitle(getString(R.string.title_version, Version.getVersionName(this)));

        if (!getWindow().hasFeature(Window.FEATURE_ACTION_BAR)) {
            View footer = getLayoutInflater().inflate(R.layout.test_list_footer, null);

            footer.findViewById(R.id.clear).setOnClickListener(this);
            footer.findViewById(R.id.export).setOnClickListener(this);

            getListView().addFooterView(footer);
        }

        setTestListAdapter(
                new ManifestTestListAdapter(/* context= */ this, /* testParent= */ null));
    }

    private AlertDialog sendUserToSettings() {
        return new AlertDialog.Builder(this)
                .setTitle("Please grant all permissions")
                .setPositiveButton(
                        "Ok",
                        (dialog, which) -> {
                            if (which == AlertDialog.BUTTON_POSITIVE) {
                                startActivity(
                                        new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                .setData(
                                                        Uri.fromParts(
                                                                "package",
                                                                getPackageName(),
                                                                null)));
                            }
                        })
                .show();
    }

    /** Notifies the user about the verifier-system plan. */
    private AlertDialog notifyUserSystemPlan(Switch systemSwitch) {
        return new AlertDialog.Builder(this)
                .setTitle("Verifier System Plan")
                .setMessage(
                        "This is a feature to execute verifier tests for the system layer"
                            + " partitions. Click \"Yes\" to proceed or \"No\" to run all of the"
                            + " verifier tests.")
                .setPositiveButton(
                        "Yes",
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {
                                updateIsSystemEnabled(true);
                            }
                        })
                .setNegativeButton(
                        "No",
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {
                                systemSwitch.setChecked(false);
                            }
                        })
                .show();
    }

    /** Updates the verifier-system plan enabled status and refreshes the test list. */
    private void updateIsSystemEnabled(boolean isChecked) {
        Log.i(TAG, "verifier-system plan enabled: " + isChecked);
        sIsSystemEnabled = isChecked;
        handleSwitchItemSelected();
    }

    /** Sets up the flags after switching display mode and reloads tests on UI. */
    private void handleSwitchItemSelected() {
        setCurrentDisplayMode(sCurrentDisplayMode);
        mAdapter.loadTestResults();
    }

    private void handleClearItemSelected() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.test_results_clear_title)
                .setPositiveButton(
                        R.string.test_results_clear_yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                mAdapter.clearTestResults();
                                Toast.makeText(
                                                TestListActivity.this,
                                                R.string.test_results_cleared,
                                                Toast.LENGTH_SHORT)
                                        .show();
                            }
                        })
                .setNegativeButton(R.string.test_results_clear_cancel, null)
                .show();
    }

    private void handleExportItemSelected() {
        new ReportExporter(this, mAdapter).execute();
    }

    private boolean handleMenuItemSelected(int id) {
        if (id == R.id.clear) {
            handleClearItemSelected();
        } else if (id == R.id.export) {
            handleExportItemSelected();
        } else {
            return false;
        }

        return true;
    }

    /** Triggered when a new query is input. */
    private void handleQueryUpdated(String query) {
        if (query != null && !query.isEmpty()) {
            mAdapter.setTestFilter(query);
        } else {
            // Reset the filter as null to show all tests.
            mAdapter.setTestFilter(/* testFilter= */ null);
        }
        mAdapter.loadTestResults();
    }

    /**
     * Sets current display mode to sharedpreferences.
     *
     * @param mode a string of current display mode
     */
    private void setCurrentDisplayMode(String mode) {
        SharedPreferences pref = getSharedPreferences(DisplayMode.class.getName(), MODE_PRIVATE);
        pref.edit().putString(DisplayMode.class.getName(), mode).commit();
    }

    /**
     * Gets current display mode from sharedpreferences.
     *
     * @return a string of current display mode
     */
    private String getCurrentDisplayMode() {
        String mode =
                getSharedPreferences(DisplayMode.class.getName(), MODE_PRIVATE)
                        .getString(DisplayMode.class.getName(), "");
        return mode;
    }
}
