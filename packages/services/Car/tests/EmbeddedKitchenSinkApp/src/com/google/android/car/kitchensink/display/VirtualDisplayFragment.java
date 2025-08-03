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
package com.google.android.car.kitchensink.display;

import static android.widget.Toast.LENGTH_SHORT;

import static com.google.android.car.kitchensink.KitchenSinkActivity.DUMP_ARG_CMD;
import static com.google.android.car.kitchensink.KitchenSinkActivity.DUMP_ARG_FRAGMENT;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Provides a virtual display that could be used by other apps.
 *
 * <p>Once the activity hosting this fragment is launched, it can be controlled using {@code adb}.
 * Example:
 *
 * <pre><code>
 adb shell 'am start -n com.google.android.car.kitchensink/.KitchenSinkActivity --es select "virtual display"'
 adb shell 'dumpsys activity com.google.android.car.kitchensink/.KitchenSinkActivity fragment "virtual display" cmd create'
 * </code></pre>
 */
public final class VirtualDisplayFragment extends Fragment {

    private static final String TAG = VirtualDisplayFragment.class.getSimpleName();

    public static final String FRAGMENT_NAME = "virtual display";
    private static final String DISPLAY_BASE_NAME = "KitchenSinkDisplay-";

    private static final String CMD_HELP = "help";
    private static final String CMD_CREATE = "create";
    private static final String CMD_DELETE = "delete";
    private static final String CMD_MAXIMIZE = "maximize";
    private static final String CMD_MINIMIZE = "minimize";
    private static final String CMD_SET_NUMBER_DISPLAYS = "set-number-of-displays";

    private static final int MAX_NUMBER_DISPLAYS = 4;

    private Spinner mNumberDisplaySpinner;
    private RelativeLayout mDisplaysContainer;

    private SelfManagedVirtualDisplayView[] mDisplays =
            new SelfManagedVirtualDisplayView[MAX_NUMBER_DISPLAYS];

    private int mCurrentNumberOfDisplays;

    // TODO(b/231499090): should not need those if we figure out how to automatically wrap the views
    private int mDisplayWidth;
    private int mDisplayHeight;

    private Button mMaximizeButton;
    private boolean mMaximized;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Log.v(TAG, "onCreateView(): mMaximizeButton=" + mMaximizeButton);
        Context context = getContext();

        if (mMaximizeButton == null) {
            mMaximizeButton = new Button(context);
            mMaximizeButton.setText("Maximize");
            Log.v(TAG, "Created mMaximizeButton: " + mMaximizeButton);
            mMaximizeButton.setOnClickListener((v) -> maximizeScreen());
            ((KitchenSinkActivity) getActivity()).addHeaderView(mMaximizeButton);
        }

        if (mNumberDisplaySpinner == null) {
            ArrayList<String> spinnerValues = new ArrayList<String>(MAX_NUMBER_DISPLAYS);

            for (int i = 0; i < MAX_NUMBER_DISPLAYS; i++) {
                int displayNumber = i + 1;
                spinnerValues.add(displayNumber == 1 ? "1 display" : displayNumber + " displays");
            }
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(context,
                    android.R.layout.simple_spinner_dropdown_item, spinnerValues);

            mNumberDisplaySpinner = new Spinner(context);
            mNumberDisplaySpinner.setAdapter(spinnerAdapter);
            mNumberDisplaySpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position,
                        long id) {
                    updateNumberDisplays(position + 1, /* force= */ false);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

            Log.v(TAG, "Created mNumberDisplaySpinner: " + mNumberDisplaySpinner);
            ((KitchenSinkActivity) getActivity()).addHeaderView(mNumberDisplaySpinner);
        }

        View view = inflater.inflate(R.layout.virtual_display, container, false);

        mDisplaysContainer =  view.findViewById(R.id.displays_container);

        return view;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        Log.v(TAG, "onHiddenChanged(hidden=" + hidden + "): mMaximizeButton=" + mMaximizeButton);
        if (mMaximizeButton == null) {
            // NOTE: onHiddenChanged(false) is called before onCreateView(...)
            Log.v(TAG, "Ignoring onHiddenChanged() call as fragment was not created yet");
            return;
        }
        boolean on = !hidden;
        toggleView(mMaximizeButton, on);
        toggleView(mNumberDisplaySpinner, on);
    }

    @Override
    public void onDestroyView() {
        onAllDisplays((index, display) -> display.release());

        super.onDestroyView();
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        Log.v(TAG, "dump(): " + Arrays.toString(args));

        if (args != null && args.length > 0 && args[0].equals(DUMP_ARG_CMD)) {
            runCmd(writer, args);
            return;
        }

        writer.printf("%sMaximized: %b\n", prefix, mMaximized);
        dumpView(prefix, writer, mMaximizeButton, "Maximize screen button");

        writer.printf("%sCurrent number of displays: %d\n", prefix, mCurrentNumberOfDisplays);

        writer.printf("%sDisplay resolution: %d x %d\n", prefix, mDisplayWidth, mDisplayHeight);

        String prefix2 = prefix + "  ";

        onAllDisplays((index, display) -> {
            writer.printf("%sDisplay #%d:\n", prefix, (index + 1));
            display.dump(prefix2, writer, args);
        });

    }

    /**
     * Visits all instantiated displays, including the hidden ones.
     */
    private void onAllDisplays(DisplayVisitor visitor) {
        onDisplays(MAX_NUMBER_DISPLAYS, visitor);
    }

    /**
     * Visits the visible displays.
     */
    private void onVisibleDisplays(DisplayVisitor visitor) {
        onDisplays(mCurrentNumberOfDisplays, visitor);
    }

    private void onDisplays(int upperLimit, DisplayVisitor visitor) {
        for (int i = 0; i < mDisplays.length && i < upperLimit; i++) {
            SelfManagedVirtualDisplayView display = mDisplays[i];
            if (display == null) {
                // All done!
                return;
            }
            visitor.visit(i, display);
        }
    }

    private void updateNumberDisplays(int numberDisplays, boolean force) {
        if (numberDisplays < 0 || numberDisplays > MAX_NUMBER_DISPLAYS) {
            throw new IllegalArgumentException("Invalid number of displays: " + numberDisplays);
        }
        if (numberDisplays == mCurrentNumberOfDisplays && !force) {
            Log.v(TAG, "updateNumberDisplays(): ignoring, already " + numberDisplays);
            return;
        }
        Log.i(TAG, "updating number of displays from " + mCurrentNumberOfDisplays + " to "
                + numberDisplays);
        mCurrentNumberOfDisplays = numberDisplays;

        // TODO(b/231499090): figure out how to use properly use WRAP_CONTENT without one of the
        // displays taking the full view
        mDisplayWidth = mDisplaysContainer.getRight();
        mDisplayHeight = mDisplaysContainer.getBottom();
        Log.v(TAG, "Full dimension: " + mDisplayWidth + "x" + mDisplayHeight);
        switch (numberDisplays) {
            case 3:
            case 4:
                mDisplayHeight /= 2;
                // Fall through
            case 2:
                mDisplayWidth /= 2;
                // Fall through
            default:
                // No op.
        }
        Log.v(TAG, "Display dimension: " + mDisplayWidth + "x" + mDisplayHeight);

        for (int i = 0; i < MAX_NUMBER_DISPLAYS; i++) {
            SelfManagedVirtualDisplayView display = mDisplays[i];

            if (i >= numberDisplays && display != null) {
                Log.v(TAG, "Disabling display at index " + i);
                toggleView(display, /* on= */ false);
                continue;
            }

            boolean isNew = false;
            if (display == null) {
                int subject = i + 1;
                String name = DISPLAY_BASE_NAME + subject;
                Log.i(TAG, "Creating display " + name + " at index " + i
                        + " and RelativeLayout subject " + subject);
                display = new SelfManagedVirtualDisplayView(getContext(), name);
                display.setId(subject);
                display.enableUserSwitching();
                mDisplays[i] = display;
                isNew = true;
            } else {
                Log.v(TAG, "Updating dimensions of display at index " + i);
            }
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                    mDisplayWidth, mDisplayHeight);
            switch (i) {
                // Display 0 is the reference (Subject 1), it doesn't need any rule
                case 1: // Subject 2
                    params.addRule(RelativeLayout.RIGHT_OF, /* subject= */ 1);
                    break;
                case 2: // Subect 3
                    params.addRule(RelativeLayout.BELOW, /* subject= */ 1);
                    break;
                case 3: // Subject 4
                    params.addRule(RelativeLayout.BELOW, /* subject= */ 2);
                    params.addRule(RelativeLayout.RIGHT_OF, /* subject= */ 3);
                    break;
                default:
                    // No op.
            }
            display.setLayoutParams(params);
            toggleView(display, /* on= */ true);

            if (isNew) {
                Log.v(TAG, "Adding display to container");
                mDisplaysContainer.addView(display);
            }
        }
    }

    private void runCmd(PrintWriter writer, String[] args) {
        if (args.length < 2) {
            writer.println("missing command\n");
            return;
        }
        String cmd = args[1];
        switch (cmd) {
            case CMD_HELP:
                cmdShowHelp(writer);
                break;
            case CMD_CREATE:
                cmdCreateDisplay(writer, args);
                break;
            case CMD_DELETE:
                cmdDeleteDisplay(writer, args);
                break;
            case CMD_MAXIMIZE:
                cmdMinimizeOrMaximizeScreen(writer, /* maximize= */ true);
                break;
            case CMD_MINIMIZE:
                cmdMinimizeOrMaximizeScreen(writer, /* maximize= */ false);
                break;
            case CMD_SET_NUMBER_DISPLAYS:
                cmdSetNumberOfDisplays(writer, args);
                break;

            default:
                cmdShowHelp(writer);
                writer.printf("Invalid cmd: %s\n", Arrays.toString(args));
        }
        return;
    }

    private void cmdDeleteDisplay(PrintWriter writer, String[] args) {
        // TODO(b/231499090): parse args to get display #
        try {
            mDisplays[0].deleteDisplay();
            printMessage(writer, "Deleted virtual display");
        } catch (Exception e) {
            writer.printf("Failed: %s\n", e);
        }
    }

    private void cmdCreateDisplay(PrintWriter writer, String[] args) {
        // TODO(b/231499090): parse args to get display #
        try {
            int displayId = mDisplays[0].createDisplay();
            printMessage(writer, "Created virtual display with id %d", displayId);
        } catch (Exception e) {
            writer.printf("Failed: %s\n", e);
        }
    }

    private void cmdSetNumberOfDisplays(PrintWriter writer, String[] args) {
        // TODO(b/231499090): use helper to parse args
        int number;
        try {
            number = Integer.parseInt(args[2]);
        } catch (Exception e) {
            writer.printf("Invalid args: %s\n", Arrays.toString(args));
            return;
        }
        if (number < 1 || number > MAX_NUMBER_DISPLAYS) {
            writer.printf("Invalid number of display (%d) - must be between 1 and %d\n",
                    number, MAX_NUMBER_DISPLAYS);
            return;
        }
        mNumberDisplaySpinner.setSelection(number - 1);
    }

    private void cmdShowHelp(PrintWriter writer) {
        writer.println("Available commands:\n");
        showCommandHelp(writer, "Shows this help message.", CMD_HELP);
        showCommandHelp(writer, "Maximizes the display view so it takes the whole screen.",
                CMD_MAXIMIZE);
        showCommandHelp(writer, "Minimizes the display view so the screen show the controls.",
                CMD_MINIMIZE);
        showCommandHelp(writer, "Creates the virtual display.",
                CMD_CREATE);
        showCommandHelp(writer, "Deletes the virtual display.",
                CMD_DELETE);
        showCommandHelp(writer, "Sets the number of virtual displays.",
                CMD_SET_NUMBER_DISPLAYS, "<NUMBER>");
    }

    private void showCommandHelp(PrintWriter writer, String description, String cmd,
            String... args) {
        writer.printf("%s", cmd);
        if (args != null) {
            for (String arg : args) {
                writer.printf(" %s", arg);
            }
        }
        writer.println(":");
        writer.printf("  %s\n\n", description);
    }

    private interface DisplayVisitor {
        void visit(int index, SelfManagedVirtualDisplayView display);
    }

    private void toggleView(View view, boolean on) {
        view.setEnabled(on);
        view.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    // TODO(b/231499090): use custom Writer interface to reuse logic for toast / writer below

    private void maximizeScreen() {
        if (mMaximized) {
            logAndToastError(/* exception= */ null, "Already maximized");
            return;
        }
        String msg1 = "Maximizing display. To minimize, run:";
        String pkg = getContext().getPackageName();
        String activity = KitchenSinkActivity.class.getSimpleName();
        String msg2 = String.format("adb shell 'dumpsys activity %s/.%s %s \"%s\" %s %s'",
                pkg, activity, DUMP_ARG_FRAGMENT, FRAGMENT_NAME, DUMP_ARG_CMD, CMD_MINIMIZE);
        logAndToastMessage(msg1);
        logAndToastMessage(msg2);
        minimizeOrMaximizeViews(/* maximize= */ true);
    }

    private void cmdMinimizeOrMaximizeScreen(PrintWriter writer, boolean maximize) {
        if (maximize && mMaximized) {
            printError(writer, /* exception= */ null, "Already maximized");
            return;
        }
        if (!maximize && !mMaximized) {
            printError(writer, /* exception= */ null, "Already minimized");
            return;
        }
        String msg1;
        if (maximize) {
            msg1 = "Maximizing display. To minimize, run:";
        } else {
            msg1 = "Minimizing display. To maximize, run:";
        }
        String pkg = getContext().getPackageName();
        String activity = KitchenSinkActivity.class.getSimpleName();
        String msg2 = String.format("adb shell 'dumpsys activity %s/.%s %s \"%s\" %s %s'",
                pkg, activity, DUMP_ARG_FRAGMENT, FRAGMENT_NAME, DUMP_ARG_CMD,
                (maximize ? CMD_MINIMIZE : CMD_MAXIMIZE));
        printMessage(writer, msg1);
        printMessage(writer, msg2);
        minimizeOrMaximizeViews(maximize);
    }

    private void minimizeOrMaximizeViews(boolean maximize) {
        mMaximized = maximize;
        boolean visible = !maximize;

        // TODO(b/231499090): must call updateNumberDisplays() as the dimensions are manually
        // calculated (hence the force argument)
        updateNumberDisplays(mCurrentNumberOfDisplays, /* force= */ true);

        onVisibleDisplays((index, display) -> display.setHeaderVisible(visible));

        ((KitchenSinkActivity) getActivity()).setHeaderVisibility(!maximize);
    }

    // TODO(b/231499090): move plumbing below to common code

    private void dumpView(String prefix, PrintWriter writer, View view, String name) {
        writer.printf("%s%s: %s %s\n", prefix, name,
                (view.isEnabled() ? "enabled" : "disabled"),
                visibilityToString(view.getVisibility()));
    }

    protected void logAndToastMessage(String format, Object...args) {
        String message = String.format(format, args);
        Log.i(TAG, message);
        Toast.makeText(getContext(), message, LENGTH_SHORT).show();
    }

    protected void printMessage(PrintWriter writer, String format, Object...args) {
        String message = String.format(format, args);
        writer.printf("%s\n", message);
    }

    protected void logAndToastError(Exception e, String format, Object...args) {
        String message = String.format(format, args);
        if (e != null) {
            Log.e(TAG, message, e);
        } else {
            Log.e(TAG, message);
        }
        Toast.makeText(getContext(), message, LENGTH_SHORT).show();
    }

    protected void printError(PrintWriter writer, Exception exception,
            String format, Object...args) {
        String message = String.format(format, args);
        if (exception != null) {
            writer.printf("%s: %s\n", message, exception);
        } else {
            writer.printf("%s\n", message);
        }
    }

    public static String visibilityToString(int visibility) {
        switch (visibility) {
            case View.VISIBLE:
                return "VISIBLE";
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return "UNKNOWN-" + visibility;
        }
    }
}
