/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.mockime;

import static android.inputmethodservice.InputMethodService.FINISH_INPUT_NO_FALLBACK_CONNECTION;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.UiAutomation;
import android.app.compat.CompatChanges;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.DeleteGesture;
import android.view.inputmethod.DeleteRangeGesture;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.HandwritingGesture;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.InsertGesture;
import android.view.inputmethod.PreviewableHandwritingGesture;
import android.view.inputmethod.SelectGesture;
import android.view.inputmethod.SelectRangeGesture;
import android.view.inputmethod.TextAttribute;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.AssumptionViolatedException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Represents an active Mock IME session, which provides basic primitives to write end-to-end tests
 * for IME APIs.
 *
 * <p>To use {@link MockIme} via {@link MockImeSession}, you need to </p>
 * <p>Public methods are not thread-safe.</p>
 */
public class MockImeSession implements AutoCloseable {

    private static final String TAG = "MockImeSession";

    private final String mImeEventActionName =
            "com.android.cts.mockime.action.IME_EVENT." + SystemClock.elapsedRealtimeNanos();

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);

    @NonNull
    private final Context mContext;

    @NonNull
    private final UiAutomation mUiAutomation;

    @NonNull
    private final AtomicBoolean mActive = new AtomicBoolean(true);

    @Nullable
    private ContentProviderClient mSettingsClient;

    @Nullable
    private SessionChannel mChannel;

    @NonNull
    private final ActivityManager mActivityManager;

    // Set with System.currentTimeMillis so it can be compatible with
    // ApplicationExitInfo.getTimestamp()
    private long mSessionCreateTimestamp;

    @MockImePackageNames
    @NonNull
    private final String mMockImePackageName;

    @MockImePackageNames
    @NonNull
    public String getMockImePackageName() {
        return mMockImePackageName;
    }

    @NonNull
    private final String mMockImeSettingsProviderAuthority;

    private final boolean mSuppressReset;

    private static final class EventStore {
        private static final int INITIAL_ARRAY_SIZE = 32;

        @NonNull
        public final ImeEvent[] mArray;
        public int mLength;

        EventStore() {
            mArray = new ImeEvent[INITIAL_ARRAY_SIZE];
            mLength = 0;
        }

        EventStore(EventStore src, int newLength) {
            mArray = new ImeEvent[newLength];
            mLength = src.mLength;
            System.arraycopy(src.mArray, 0, mArray, 0, src.mLength);
        }

        public EventStore add(ImeEvent event) {
            if (mLength + 1 <= mArray.length) {
                mArray[mLength] = event;
                ++mLength;
                return this;
            } else {
                return new EventStore(this, mLength * 2).add(event);
            }
        }

        public ImeEventStream.ImeEventArray takeSnapshot() {
            return new ImeEventStream.ImeEventArray(mArray, mLength);
        }
    }

    private static final class MockImeEventReceiver implements Consumer<Bundle> {
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        @NonNull
        private EventStore mCurrentEventStore = new EventStore();

        @Override
        public void accept(Bundle bundle) {
            synchronized (mLock) {
                mCurrentEventStore =
                        mCurrentEventStore.add(ImeEvent.fromBundle(bundle));
            }
        }

        public ImeEventStream.ImeEventArray takeEventSnapshot() {
            synchronized (mLock) {
                return mCurrentEventStore.takeSnapshot();
            }
        }
    }
    private final MockImeEventReceiver mEventReceiver =
            new MockImeEventReceiver();

    private final ImeEventStream mEventStream =
            new ImeEventStream(mEventReceiver::takeEventSnapshot);

    private static String executeShellCommand(
            @NonNull UiAutomation uiAutomation, @NonNull String command) throws IOException {
        Log.d(TAG, "executeShellCommand(): command=" + command);
        try (ParcelFileDescriptor.AutoCloseInputStream in =
                     new ParcelFileDescriptor.AutoCloseInputStream(
                             uiAutomation.executeShellCommand(command))) {
            final StringBuilder sb = new StringBuilder();
            final byte[] buffer = new byte[4096];
            while (true) {
                final int numRead = in.read(buffer);
                if (numRead <= 0) {
                    break;
                }
                sb.append(new String(buffer, 0, numRead));
            }
            String result = sb.toString();
            Log.d(TAG, "executeShellCommand(): result=" + result);
            return result;
        }
    }

    private String executeImeCmd(String cmd, @Nullable String...args) throws IOException {
        StringBuilder fullCmd = new StringBuilder("ime ").append(cmd);
        fullCmd.append(" --user ").append(mContext.getUserId()).append(' ');
        for (String arg : args) {
            // Ideally it should check if there's more args, but adding an extra space is fine
            fullCmd.append(' ').append(arg);
        }
        return executeShellCommand(mUiAutomation, fullCmd.toString());
    }

    @Nullable
    private String getCurrentInputMethodId() {
        // TODO: Replace this with IMM#getCurrentInputMethodIdForTesting()
        String settingsValue = Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        Log.v(TAG, "getCurrentInputMethodId(): returning " + settingsValue + " for user "
                + mContext.getUser().getIdentifier());
        return settingsValue;
    }

    @Nullable
    private void writeMockImeSettings(@NonNull Context context,
            @NonNull String imeEventActionName,
            @Nullable ImeSettings.Builder imeSettings,
            @NonNull RemoteCallback channel) throws Exception {
        final var bundle = ImeSettings.serializeToBundle(imeEventActionName, imeSettings, channel);
        Log.i(TAG, "Writing MockIme settings: session=" + this);
        context.getContentResolver().call(mMockImeSettingsProviderAuthority, "write", null, bundle);
    }

    private void setAdditionalSubtypes(@NonNull Context context,
            @Nullable InputMethodSubtype[] additionalSubtypes) {
        final Bundle bundle = new Bundle();
        bundle.putParcelableArray(SettingsProvider.SET_ADDITIONAL_SUBTYPES_KEY, additionalSubtypes);
        context.getContentResolver().call(mMockImeSettingsProviderAuthority,
                SettingsProvider.SET_ADDITIONAL_SUBTYPES_COMMAND, null, bundle);
    }

    private ComponentName getMockImeComponentName() {
        return new ComponentName(mMockImePackageName, MockIme.class.getName());
    }

    /**
     * @return the IME ID of the {@link MockIme}.
     * @see android.view.inputmethod.InputMethodInfo#getId()
     */
    public String getImeId() {
        return getMockImeComponentName().flattenToShortString();
    }

    private MockImeSession(@NonNull Context context, @NonNull UiAutomation uiAutomation,
            @NonNull ImeSettings.Builder imeSettings) {
        mContext = context;
        mUiAutomation = uiAutomation;
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mMockImePackageName = imeSettings.mMockImePackageName;
        mMockImeSettingsProviderAuthority = mMockImePackageName + ".provider";
        mSuppressReset = imeSettings.mSuppressResetIme;
        updateSessionCreateTimestamp();
    }

    public long getSessionCreateTimestamp() {
        return mSessionCreateTimestamp;
    }

    private void updateSessionCreateTimestamp() {
        mSessionCreateTimestamp = System.currentTimeMillis();
    }

    @Nullable
    public InputMethodInfo getInputMethodInfo() {
        for (InputMethodInfo imi :
                mContext.getSystemService(InputMethodManager.class).getInputMethodList()) {
            if (TextUtils.equals(getImeId(), imi.getId())) {
                return imi;
            }
        }
        return null;
    }

    private void initialize(@Nullable ImeSettings.Builder imeSettings) throws Exception {
        PollingCheck.check("MockIME was not in getInputMethodList() after timeout.", TIMEOUT_MILLIS,
                () -> getInputMethodInfo() != null);

        // Make sure that MockIME is not selected.
        if (!mSuppressReset
                && mContext.getSystemService(InputMethodManager.class)
                .getInputMethodList()
                .stream()
                .anyMatch(info -> getMockImeComponentName().equals(info.getComponent()))) {
            executeImeCmd("reset");
        }
        if (mContext.getSystemService(InputMethodManager.class)
                .getEnabledInputMethodList()
                .stream()
                .anyMatch(info -> getMockImeComponentName().equals(info.getComponent()))) {
            throw new IllegalStateException();
        }

        // Make sure to set up additional subtypes before launching MockIme.
        InputMethodSubtype[] additionalSubtypes = imeSettings.mAdditionalSubtypes;
        if (additionalSubtypes == null) {
            additionalSubtypes = new InputMethodSubtype[0];
        }
        if (additionalSubtypes.length > 0) {
            setAdditionalSubtypes(mContext, additionalSubtypes);
        } else {
            final InputMethodInfo imi = getInputMethodInfo();
            if (imi == null) {
                throw new IllegalStateException("MockIME was not in getInputMethodList().");
            }
            if (imi.getSubtypeCount() != 0) {
                // Somehow the previous run failed to remove additional subtypes. Clean them up.
                setAdditionalSubtypes(mContext, null);
            }
        }
        {
            final InputMethodInfo imi = getInputMethodInfo();
            if (imi == null) {
                throw new IllegalStateException("MockIME not found while checking subtypes.");
            }
            if (imi.getSubtypeCount() != additionalSubtypes.length) {
                throw new IllegalStateException("MockIME subtypes were not correctly set.");
            }
        }

        mSettingsClient = mContext.getContentResolver().acquireUnstableContentProviderClient(
                mMockImeSettingsProviderAuthority);
        var sessionEstablished = new CountDownLatch(1);
        mChannel = new SessionChannel(sessionEstablished::countDown);
        mChannel.registerListener(mEventReceiver);
        writeMockImeSettings(mContext, mImeEventActionName, imeSettings,
                mChannel.takeTransport());
        sessionEstablished.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        String imeId = getImeId();
        executeImeCmd("enable", imeId);
        executeImeCmd("set", imeId);

        PollingCheck.check("Make sure that MockIME becomes available", TIMEOUT_MILLIS,
                () -> getImeId().equals(getCurrentInputMethodId()));
    }

    @Override
    public String toString() {
        return TAG + "{active=" + mActive + "}";
    }

    /** @see #create(Context, UiAutomation, ImeSettings.Builder) */
    @NonNull
    public static MockImeSession create(@NonNull Context context) throws Exception {
        return create(context, getInstrumentation().getUiAutomation(), new ImeSettings.Builder());
    }

    /**
     * Creates a new Mock IME session. During this session, you can receive various events from
     * {@link MockIme}.
     *
     * @param context {@link Context} to be used to receive inter-process events from the
     *                {@link MockIme} (e.g. via {@link BroadcastReceiver}
     * @param uiAutomation {@link UiAutomation} object to change the device state that are typically
     *                     guarded by permissions.
     * @param imeSettings Key-value pairs to be passed to the {@link MockIme}.
     * @return A session object, with which you can retrieve event logs from the {@link MockIme} and
     *         can clean up the session.
     */
    @NonNull
    public static MockImeSession create(
            @NonNull Context context,
            @NonNull UiAutomation uiAutomation,
            @Nullable ImeSettings.Builder imeSettings) throws Exception {
        final String unavailabilityReason = getUnavailabilityReason(context);
        if (unavailabilityReason != null) {
            throw new AssumptionViolatedException(unavailabilityReason);
        }
        final MockImeSession client = new MockImeSession(context, uiAutomation, imeSettings);
        client.initialize(imeSettings);
        return client;
    }

    /**
     * Checks if the {@link MockIme} can be used in this device.
     *
     * @return {@code null} if it can be used, or message describing why if it cannot.
     */
    @Nullable
    public static String getUnavailabilityReason(@NonNull Context context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_INPUT_METHODS)) {
            return "Device must support installable IMEs that implement InputMethodService API";
        }
        return null;
    }

    /**
     * Whether {@link MockIme} enabled a compatibility flag to finish input without fallback
     * input connection when device interactive state changed. See detailed description in
     * {@link MockImeSession#setEnabledFinishInputNoFallbackConnection}.
     *
     * @return {@code true} if the compatibility flag is enabled.
     */
    public boolean isFinishInputNoFallbackConnectionEnabled() {
        AtomicBoolean result = new AtomicBoolean();
        runWithShellPermissionIdentity(() ->
                result.set(CompatChanges.isChangeEnabled(FINISH_INPUT_NO_FALLBACK_CONNECTION,
                        mMockImePackageName, UserHandle.CURRENT)));
        return result.get();
    }

    /**
     * Checks whether there are any pending IME visibility requests.
     *
     * @see InputMethodManager#hasPendingImeVisibilityRequests()
     *
     * @return {@code true} iff there are pending IME visibility requests.
     */
    public boolean hasPendingImeVisibilityRequests() {
        final var imm = mContext.getSystemService(InputMethodManager.class);
        return runWithShellPermissionIdentity(imm::hasPendingImeVisibilityRequests);
    }

    /**
     * @return {@link ImeEventStream} object that stores events sent from {@link MockIme} since the
     *         session is created.
     */
    public ImeEventStream openEventStream() {
        return mEventStream.copy();
    }

    /**
     * Logs the event stream to logcat.
     */
    public void logEventStream() {
        Log.i(TAG, mEventStream.dump());
    }

    /**
     * @return {@code true} until {@link #close()} gets called.
     */
    @AnyThread
    public boolean isActive() {
        return mActive.get();
    }

    /**
     * Closes the active session and de-selects {@link MockIme}. Currently which IME will be
     * selected next is up to the system.
     */
    public void close() throws Exception {
        String exitReason = retrieveExitReasonIfMockImeCrashed();
        if (exitReason != null) {
            Log.e(TAG, String.format("MockIme process exit reason: {%s}, event stream: {%s}",
                    exitReason, mEventStream.dump()));
        }

        mActive.set(false);

        if (!mSuppressReset) {
            executeImeCmd("reset");

            PollingCheck.check("Make sure that MockIME becomes unavailable", TIMEOUT_MILLIS, () ->
                    mContext.getSystemService(InputMethodManager.class)
                            .getEnabledInputMethodList()
                            .stream()
                            .noneMatch(
                                    info -> getMockImeComponentName().equals(info.getComponent())));
        }

        if (mChannel != null) {
            mChannel.close();
        }
        Log.i(TAG, "Deleting MockIme settings: session=" + this);
        mContext.getContentResolver().call(
                mMockImeSettingsProviderAuthority, "delete", null, null);

        // Clean up additional subtypes if any.
        final InputMethodInfo imi = getInputMethodInfo();
        if (imi != null && imi.getSubtypeCount() != 0) {
            setAdditionalSubtypes(mContext, null);
        }
        if (mSettingsClient != null) {
            mSettingsClient.close();
            mSettingsClient = null;
        }
        updateSessionCreateTimestamp();
    }

    @Nullable
    String retrieveExitReasonIfMockImeCrashed() {
        final ApplicationExitInfo lastExitReason = findLatestMockImeSessionExitInfo();
        if (lastExitReason == null) {
            return null;
        }
        if (lastExitReason.getTimestamp() <= mSessionCreateTimestamp) {
            return null;
        }
        final StringBuilder err = new StringBuilder();
        err.append("MockIme crashed and exited with code: ").append(lastExitReason.getReason())
                .append("; ");
        err.append("session create time: ").append(mSessionCreateTimestamp).append("; ");
        err.append("process exit time: ").append(lastExitReason.getTimestamp()).append("; ");
        err.append("see android.app.ApplicationExitInfo for more info on the exit code ");
        final String exitDescription = lastExitReason.getDescription();
        if (exitDescription != null) {
            err.append("(exit Description: ").append(exitDescription).append(")");
        }
        return err.toString();
    }

    @Nullable
    private ApplicationExitInfo findLatestMockImeSessionExitInfo() {
        final ApplicationExitInfo[] exitReasons = new ApplicationExitInfo[1];

        // Requires android.permission.DUMP permission.
        runWithShellPermissionIdentity(() -> {
            List<ApplicationExitInfo> latestExitReasons =
                    mActivityManager.getHistoricalProcessExitReasons(
                            mMockImePackageName, /* pid= */ 0, /* maxNum= */ 1);
            if (!latestExitReasons.isEmpty()) {
                exitReasons[0] = latestExitReasons.get(0);
            }
        });

        return exitReasons[0];
    }

    /**
     * Common logic to send a special command to {@link MockIme}.
     *
     * @param commandName command to be passed to {@link MockIme}
     * @param params {@link Bundle} to be passed to {@link MockIme} as a parameter set of
     *               {@code commandName}
     * @return {@link ImeCommand} that is sent to {@link MockIme}.  It can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    private ImeCommand callCommandInternal(@NonNull String commandName, @NonNull Bundle params) {
        final ImeCommand command = new ImeCommand(
                commandName, SystemClock.elapsedRealtimeNanos(), true, params);
        if (!mChannel.send(command.toBundle())) {
            throw new IllegalStateException("Channel already closed: " + commandName);
        }
        return command;
    }

    /**
     * Lets {@link MockIme} suspend {@link MockIme.AbstractInputMethodImpl#createSession(
     * android.view.inputmethod.InputMethod.SessionCallback)} until {@link #resumeCreateSession()}.
     *
     * <p>This is useful to test a tricky timing issue that the IME client initiated the
     * IME session but {@link android.view.inputmethod.InputMethodSession} is not available
     * yet.</p>
     *
     * <p>For simplicity and stability, {@link #suspendCreateSession()} must be called before
     * {@link MockIme.AbstractInputMethodImpl#createSession(
     * android.view.inputmethod.InputMethod.SessionCallback)} gets called again.</p>
     *
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand suspendCreateSession() {
        return callCommandInternal("suspendCreateSession", new Bundle());
    }

    /**
     * Lets {@link MockIme} resume suspended {@link MockIme.AbstractInputMethodImpl#createSession(
     * android.view.inputmethod.InputMethod.SessionCallback)}.
     *
     * <p>Does nothing if {@link #suspendCreateSession()} was not called.</p>
     *
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand resumeCreateSession() {
        return callCommandInternal("resumeCreateSession", new Bundle());
    }


    /**
     * Lets {@link MockIme} to call
     * {@link android.inputmethodservice.InputMethodService#getCurrentInputConnection()} and
     * memorize  it for later {@link InputConnection}-related operations.
     *
     * <p>Only the last one will be memorized if this method gets called multiple times.</p>
     *
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     * @see #unmemorizeCurrentInputConnection()
     */
    @NonNull
    public ImeCommand memorizeCurrentInputConnection() {
        final Bundle params = new Bundle();
        return callCommandInternal("memorizeCurrentInputConnection", params);
    }

    /**
     * Lets {@link MockIme} to forget memorized {@link InputConnection} if any. Does nothing
     * otherwise.
     *
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     * @see #memorizeCurrentInputConnection()
     */
    @NonNull
    public ImeCommand unmemorizeCurrentInputConnection() {
        final Bundle params = new Bundle();
        return callCommandInternal("unmemorizeCurrentInputConnection", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#getTextBeforeCursor(int, int)} with the
     * given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().getTextBeforeCursor(n, flag)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnCharSequenceValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param n to be passed as the {@code n} parameter.
     * @param flag to be passed as the {@code flag} parameter.
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callGetTextBeforeCursor(int n, int flag) {
        final Bundle params = new Bundle();
        params.putInt("n", n);
        params.putInt("flag", flag);
        return callCommandInternal("getTextBeforeCursor", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#getTextAfterCursor(int, int)} with the
     * given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().getTextAfterCursor(n, flag)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnCharSequenceValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param n to be passed as the {@code n} parameter.
     * @param flag to be passed as the {@code flag} parameter.
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callGetTextAfterCursor(int n, int flag) {
        final Bundle params = new Bundle();
        params.putInt("n", n);
        params.putInt("flag", flag);
        return callCommandInternal("getTextAfterCursor", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#getSelectedText(int)} with the
     * given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().getSelectedText(flag)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnCharSequenceValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param flag to be passed as the {@code flag} parameter.
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callGetSelectedText(int flag) {
        final Bundle params = new Bundle();
        params.putInt("flag", flag);
        return callCommandInternal("getSelectedText", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#getSurroundingText(int, int, int)} with
     * the given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().getSurroundingText(int, int, int)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnParcelableValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param beforeLength The expected length of the text before the cursor.
     * @param afterLength The expected length of the text after the cursor.
     * @param flags Supplies additional options controlling how the text is returned. May be either
     *              {@code 0} or {@link InputConnection#GET_TEXT_WITH_STYLES}.
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callGetSurroundingText(@IntRange(from = 0) int beforeLength,
            @IntRange(from = 0) int afterLength, int flags) {
        final Bundle params = new Bundle();
        params.putInt("beforeLength", beforeLength);
        params.putInt("afterLength", afterLength);
        params.putInt("flags", flags);
        return callCommandInternal("getSurroundingText", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#getCursorCapsMode(int)} with the given
     * parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().getCursorCapsMode(reqModes)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnIntegerValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param reqModes to be passed as the {@code reqModes} parameter.
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callGetCursorCapsMode(int reqModes) {
        final Bundle params = new Bundle();
        params.putInt("reqModes", reqModes);
        return callCommandInternal("getCursorCapsMode", params);
    }

    /**
     * Lets {@link MockIme} to call
     * {@link InputConnection#getExtractedText(ExtractedTextRequest, int)} with the given
     * parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().getExtractedText(request, flags)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnParcelableValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param request to be passed as the {@code request} parameter
     * @param flags to be passed as the {@code flags} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callGetExtractedText(@Nullable ExtractedTextRequest request, int flags) {
        final Bundle params = new Bundle();
        params.putParcelable("request", request);
        params.putInt("flags", flags);
        return callCommandInternal("getExtractedText", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#deleteSurroundingText(int, int)} with the
     * given parameters.
     *
     * <p>This triggers
     * {@code getCurrentInputConnection().deleteSurroundingText(beforeLength, afterLength)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param beforeLength to be passed as the {@code beforeLength} parameter
     * @param afterLength to be passed as the {@code afterLength} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callDeleteSurroundingText(int beforeLength, int afterLength) {
        final Bundle params = new Bundle();
        params.putInt("beforeLength", beforeLength);
        params.putInt("afterLength", afterLength);
        return callCommandInternal("deleteSurroundingText", params);
    }

    /**
     * Lets {@link MockIme} to call
     * {@link InputConnection#deleteSurroundingTextInCodePoints(int, int)} with the given
     * parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().deleteSurroundingTextInCodePoints(
     * beforeLength, afterLength)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param beforeLength to be passed as the {@code beforeLength} parameter
     * @param afterLength to be passed as the {@code afterLength} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callDeleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        final Bundle params = new Bundle();
        params.putInt("beforeLength", beforeLength);
        params.putInt("afterLength", afterLength);
        return callCommandInternal("deleteSurroundingTextInCodePoints", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#setComposingText(CharSequence, int)} with
     * the given parameters.
     *
     * <p>This triggers
     * {@code getCurrentInputConnection().setComposingText(text, newCursorPosition)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param text to be passed as the {@code text} parameter
     * @param newCursorPosition to be passed as the {@code newCursorPosition} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callSetComposingText(@Nullable CharSequence text, int newCursorPosition) {
        final Bundle params = new Bundle();
        params.putCharSequence("text", text);
        params.putInt("newCursorPosition", newCursorPosition);
        return callCommandInternal("setComposingText(CharSequence,int)", params);
    }

    /**
     * Lets {@link MockIme} to call
     * {@link InputConnection#setComposingText(CharSequence, int, TextAttribute)} with the given
     * parameters.
     *
     * <p>This triggers
     * {@code getCurrentInputConnection().setComposingText(text, newCursorPosition, textAttribute)}.
     * </p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param text to be passed as the {@code text} parameter
     * @param newCursorPosition to be passed as the {@code newCursorPosition} parameter
     * @param textAttribute to be passed as the {@code textAttribute} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callSetComposingText(@Nullable CharSequence text, int newCursorPosition,
            @Nullable TextAttribute textAttribute) {
        final Bundle params = new Bundle();
        params.putCharSequence("text", text);
        params.putInt("newCursorPosition", newCursorPosition);
        params.putParcelable("textAttribute", textAttribute);
        return callCommandInternal("setComposingText(CharSequence,int,TextAttribute)", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#setComposingRegion(int, int)} with the
     * given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().setComposingRegion(start, end)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param start to be passed as the {@code start} parameter
     * @param end to be passed as the {@code end} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callSetComposingRegion(int start, int end) {
        final Bundle params = new Bundle();
        params.putInt("start", start);
        params.putInt("end", end);
        return callCommandInternal("setComposingRegion(int,int)", params);
    }

    /**
     * Lets {@link MockIme} to call
     * {@link InputConnection#setComposingRegion(int, int, TextAttribute)} with the given
     * parameters.
     *
     * <p>This triggers
     * {@code getCurrentInputConnection().setComposingRegion(start, end, TextAttribute)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param start to be passed as the {@code start} parameter
     * @param end to be passed as the {@code end} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callSetComposingRegion(int start, int end,
            @Nullable TextAttribute textAttribute) {
        final Bundle params = new Bundle();
        params.putInt("start", start);
        params.putInt("end", end);
        params.putParcelable("textAttribute", textAttribute);
        return callCommandInternal("setComposingRegion(int,int,TextAttribute)", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#finishComposingText()} with the given
     * parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().finishComposingText()}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callFinishComposingText() {
        final Bundle params = new Bundle();
        return callCommandInternal("finishComposingText", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#commitText(CharSequence, int)} with the
     * given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().commitText(text, newCursorPosition)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param text to be passed as the {@code text} parameter
     * @param newCursorPosition to be passed as the {@code newCursorPosition} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callCommitText(@Nullable CharSequence text, int newCursorPosition) {
        final Bundle params = new Bundle();
        params.putCharSequence("text", text);
        params.putInt("newCursorPosition", newCursorPosition);
        return callCommandInternal("commitText(CharSequence,int)", params);
    }

    /**
     * Lets {@link MockIme} to call
     * {@link InputConnection#commitText(CharSequence, int, TextAttribute)} with the given
     * parameters.
     *
     * <p>This triggers
     * {@code getCurrentInputConnection().commitText(text, newCursorPosition, TextAttribute)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param text to be passed as the {@code text} parameter
     * @param newCursorPosition to be passed as the {@code newCursorPosition} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callCommitText(@Nullable CharSequence text, int newCursorPosition,
            @Nullable TextAttribute textAttribute) {
        final Bundle params = new Bundle();
        params.putCharSequence("text", text);
        params.putInt("newCursorPosition", newCursorPosition);
        params.putParcelable("textAttribute", textAttribute);
        return callCommandInternal("commitText(CharSequence,int,TextAttribute)", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#commitCompletion(CompletionInfo)} with
     * the given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().commitCompletion(text)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param text to be passed as the {@code text} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callCommitCompletion(@Nullable CompletionInfo text) {
        final Bundle params = new Bundle();
        params.putParcelable("text", text);
        return callCommandInternal("commitCompletion", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#commitCorrection(CorrectionInfo)} with
     * the given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().commitCorrection(correctionInfo)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param correctionInfo to be passed as the {@code correctionInfo} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callCommitCorrection(@Nullable CorrectionInfo correctionInfo) {
        final Bundle params = new Bundle();
        params.putParcelable("correctionInfo", correctionInfo);
        return callCommandInternal("commitCorrection", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#setSelection(int, int)} with the given
     * parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().setSelection(start, end)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param start to be passed as the {@code start} parameter
     * @param end to be passed as the {@code end} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callSetSelection(int start, int end) {
        final Bundle params = new Bundle();
        params.putInt("start", start);
        params.putInt("end", end);
        return callCommandInternal("setSelection", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#performEditorAction(int)} with the given
     * parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().performEditorAction(editorAction)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param editorAction to be passed as the {@code editorAction} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callPerformEditorAction(int editorAction) {
        final Bundle params = new Bundle();
        params.putInt("editorAction", editorAction);
        return callCommandInternal("performEditorAction", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#performContextMenuAction(int)} with the
     * given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().performContextMenuAction(id)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param id to be passed as the {@code id} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callPerformContextMenuAction(int id) {
        final Bundle params = new Bundle();
        params.putInt("id", id);
        return callCommandInternal("performContextMenuAction", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#beginBatchEdit()} with the given
     * parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().beginBatchEdit()}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callBeginBatchEdit() {
        final Bundle params = new Bundle();
        return callCommandInternal("beginBatchEdit", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#endBatchEdit()} with the given
     * parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().endBatchEdit()}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callEndBatchEdit() {
        final Bundle params = new Bundle();
        return callCommandInternal("endBatchEdit", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#sendKeyEvent(KeyEvent)} with the given
     * parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().sendKeyEvent(event)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param event to be passed as the {@code event} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callSendKeyEvent(@Nullable KeyEvent event) {
        final Bundle params = new Bundle();
        params.putParcelable("event", event);
        return callCommandInternal("sendKeyEvent", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#performSpellCheck()}.
     *
     * <p>This triggers {@code getCurrentInputConnection().performSpellCheck()}.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>

     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callPerformSpellCheck() {
        return callCommandInternal("performSpellCheck", new Bundle());
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#takeSnapshot()}.
     *
     * <p>This triggers {@code getCurrentInputConnection().takeSnapshot()}.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>

     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callTakeSnapshot() {
        return callCommandInternal("takeSnapshot", new Bundle());
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#clearMetaKeyStates(int)} with the given
     * parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().sendKeyEvent(event)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param states to be passed as the {@code states} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callClearMetaKeyStates(int states) {
        final Bundle params = new Bundle();
        params.putInt("states", states);
        return callCommandInternal("clearMetaKeyStates", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#reportFullscreenMode(boolean)} with the
     * given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().reportFullscreenMode(enabled)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param enabled to be passed as the {@code enabled} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callReportFullscreenMode(boolean enabled) {
        final Bundle params = new Bundle();
        params.putBoolean("enabled", enabled);
        return callCommandInternal("reportFullscreenMode", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#performPrivateCommand(String, Bundle)}
     * with the given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().performPrivateCommand(action, data)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param action to be passed as the {@code action} parameter
     * @param data to be passed as the {@code data} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callPerformPrivateCommand(@Nullable String action, Bundle data) {
        final Bundle params = new Bundle();
        params.putString("action", action);
        params.putBundle("data", data);
        return callCommandInternal("performPrivateCommand", params);
    }

    /**
     * Lets {@link MockIme} to call
     * {@link InputConnection#performHandwritingGesture(HandwritingGesture, Executor, IntConsumer)}
     * with the given parameters.
     *
     * <p>The result callback will be recorded as an {@code onPerformHandwritingGestureResult}
     * event.
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param gesture {@link SelectGesture} or {@link InsertGesture} or {@link DeleteGesture}.
     * @param useDelayedCancellation {@code true} to use delayed {@link CancellationSignal#cancel()}
     *  on a supported gesture like {@link android.view.inputmethod.InsertModeGesture}.
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callPerformHandwritingGesture(
            @NonNull HandwritingGesture gesture, boolean useDelayedCancellation) {
        final Bundle params = new Bundle();
        params.putByteArray("gesture", gesture.toByteArray());
        params.putBoolean("useDelayedCancellation", useDelayedCancellation);
        return callCommandInternal("performHandwritingGesture", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#requestTextBoundsInfo}.
     *
     * <p>The result callback will be recorded as an {@code onRequestTextBoundsInfoResult} event.
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param gesture {@link SelectGesture} or {@link InsertGesture} or {@link DeleteGesture}.
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callRequestTextBoundsInfo(RectF rectF) {
        final Bundle params = new Bundle();
        params.putParcelable("rectF", rectF);
        return callCommandInternal("requestTextBoundsInfo", params);
    }

    /**
     * Lets {@link MockIme} to call
     * {@link InputConnection#previewHandwritingGesture(PreviewableHandwritingGesture,
     *  CancellationSignal)} with the given parameters.
     *
     * <p>Use {@link ImeEvent#getReturnIntegerValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param gesture one of {@link SelectGesture}, {@link SelectRangeGesture},
     * {@link DeleteGesture}, {@link DeleteRangeGesture}.
     * @param useDelayedCancellation {@code true} to use delayed {@link CancellationSignal#cancel()}
     *  on a gesture preview.
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callPreviewHandwritingGesture(
            @NonNull PreviewableHandwritingGesture gesture, boolean useDelayedCancellation) {
        final Bundle params = new Bundle();
        params.putByteArray("gesture", gesture.toByteArray());
        params.putBoolean("useDelayedCancellation", useDelayedCancellation);
        return callCommandInternal("previewHandwritingGesture", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#requestCursorUpdates(int)} with the given
     * parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().requestCursorUpdates(cursorUpdateMode)}.
     * </p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param cursorUpdateMode to be passed as the {@code cursorUpdateMode} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callRequestCursorUpdates(int cursorUpdateMode) {
        final Bundle params = new Bundle();
        params.putInt("cursorUpdateMode", cursorUpdateMode);
        return callCommandInternal("requestCursorUpdates", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#requestCursorUpdates(int, int)} with the
     * given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().requestCursorUpdates(
     * cursorUpdateMode, cursorUpdateFilter)}.
     * </p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param cursorUpdateMode to be passed as the {@code cursorUpdateMode} parameter
     * @param cursorUpdateFilter to be passed as the {@code cursorUpdateFilter} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callRequestCursorUpdates(int cursorUpdateMode, int cursorUpdateFilter) {
        final Bundle params = new Bundle();
        params.putInt("cursorUpdateMode", cursorUpdateMode);
        params.putInt("cursorUpdateFilter", cursorUpdateFilter);
        return callCommandInternal("requestCursorUpdates", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#getHandler()} with the given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().getHandler()}.</p>
     *
     * <p>Use {@link ImeEvent#isNullReturnValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API was {@code null} or not.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callGetHandler() {
        final Bundle params = new Bundle();
        return callCommandInternal("getHandler", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#closeConnection()} with the given
     * parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().closeConnection()}.</p>
     *
     * <p>Return value information is not available for this command.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callCloseConnection() {
        final Bundle params = new Bundle();
        return callCommandInternal("closeConnection", params);
    }

    /**
     * Lets {@link MockIme} to call
     * {@link InputConnection#commitContent(InputContentInfo, int, Bundle)} with the given
     * parameters.
     *
     * <p>This triggers
     * {@code getCurrentInputConnection().commitContent(inputContentInfo, flags, opts)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param inputContentInfo to be passed as the {@code inputContentInfo} parameter
     * @param flags to be passed as the {@code flags} parameter
     * @param opts to be passed as the {@code opts} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callCommitContent(@NonNull InputContentInfo inputContentInfo, int flags,
            @Nullable Bundle opts) {
        final Bundle params = new Bundle();
        params.putParcelable("inputContentInfo", inputContentInfo);
        params.putInt("flags", flags);
        params.putBundle("opts", opts);
        return callCommandInternal("commitContent", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#setImeConsumesInput(boolean)} with the
     * given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().setImeConsumesInput(boolean)}.</p>
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from
     * {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the
     * value returned from the API.</p>
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param imeConsumesInput to be passed as the {@code imeConsumesInput} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callSetImeConsumesInput(boolean imeConsumesInput) {
        final Bundle params = new Bundle();
        params.putBoolean("imeConsumesInput", imeConsumesInput);
        return callCommandInternal("setImeConsumesInput", params);
    }

    /**
     * Lets {@link MockIme} to call {@link InputConnection#replaceText(int, int, CharSequence, int,
     * TextAttribute)} with the given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().replaceText(int, int, CharSequence, int,
     * TextAttribute)}.
     *
     * <p>Use {@link ImeEvent#getReturnBooleanValue()} for {@link ImeEvent} returned from {@link
     * ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to see the value
     * returned from the API.
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.
     *
     * @param start the character index where the replacement should start
     * @param end the character index where the replacement should end
     * @param newCursorPosition the new cursor position around the text. If > 0, this is relative to
     *     the end of the text - 1; if <= 0, this is relative to the start of the text. So a value
     *     of 1 will always advance you to the position after the full text being inserted. Note
     *     that this means you can't position the cursor within the text.
     * @param text the text to replace. This may include styles.
     * @param textAttribute The extra information about the text. This value may be null.
     * @return {@link ImeCommand} object that can be passed to {@link
     *     ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to wait until
     *     this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callReplaceText(
            int start,
            int end,
            @NonNull CharSequence text,
            int newCursorPosition,
            @Nullable TextAttribute textAttribute) {
        final Bundle params = new Bundle();
        params.putInt("start", start);
        params.putInt("end", end);
        params.putCharSequence("text", text);
        params.putInt("newCursorPosition", newCursorPosition);
        params.putParcelable("textAttribute", textAttribute);
        return callCommandInternal("replaceText", params);
    }

    /**
     * Lets {@link MockIme} to call
     * {@link InputMethodManager#setExplicitlyEnabledInputMethodSubtypes(String, int[])} with the
     * given parameters.
     *
     * <p>This triggers {@code setExplicitlyEnabledInputMethodSubtypes(imeId, subtypeHashCodes)}.
     * </p>
     *
     * @param imeId the IME ID.
     * @param subtypeHashCodes An array of {@link InputMethodSubtype#hashCode()}. An empty array and
     *                   {@code null} can reset the enabled subtypes.
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callSetExplicitlyEnabledInputMethodSubtypes(String imeId,
            @Nullable int[] subtypeHashCodes) {
        final Bundle params = new Bundle();
        params.putString("imeId", imeId);
        params.putIntArray("subtypeHashCodes", subtypeHashCodes);
        return callCommandInternal("setExplicitlyEnabledInputMethodSubtypes", params);
    }

    /**
     * Lets {@link MockIme} to call
     * {@link InputMethodManager#setAdditionalInputMethodSubtypes(String, InputMethodSubtype[])}
     * with the given parameters.
     *
     * @param imeId the IME ID
     * @param subtypes A non-null array of {@link InputMethodSubtype}
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callSetAdditionalInputMethodSubtypes(@NonNull String imeId,
            @NonNull InputMethodSubtype[] subtypes) {
        final Bundle params = new Bundle();
        params.putString("imeId", imeId);
        params.putParcelableArray("subtypes", subtypes);
        return callCommandInternal("setAdditionalInputMethodSubtypes", params);
    }

    /**
     * Makes {@link MockIme} call {@link
     * android.inputmethodservice.InputMethodService#switchInputMethod(String)}
     * with the given parameters.
     *
     * @param id the IME ID.
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callSwitchInputMethod(String id) {
        final Bundle params = new Bundle();
        params.putString("id", id);
        return callCommandInternal("switchInputMethod", params);
    }

    /**
     * Lets {@link MockIme} to call {@link
     * android.inputmethodservice.InputMethodService#switchInputMethod(String, InputMethodSubtype)}
     * with the given parameters.
     *
     * <p>This triggers {@code switchInputMethod(id, subtype)}.</p>
     *
     * @param id the IME ID.
     * @param subtype {@link InputMethodSubtype} to be switched to. Ignored if {@code null}.
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callSwitchInputMethod(String id, @Nullable InputMethodSubtype subtype) {
        final Bundle params = new Bundle();
        params.putString("id", id);
        params.putParcelable("subtype", subtype);
        return callCommandInternal("switchInputMethod(String,InputMethodSubtype)", params);
    }

    /**
     * Lets {@link MockIme} to call
     * {@link android.inputmethodservice.InputMethodService#setBackDisposition(int)} with the given
     * parameters.
     *
     * <p>This triggers {@code setBackDisposition(backDisposition)}.</p>
     *
     * @param backDisposition to be passed as the {@code backDisposition} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callSetBackDisposition(int backDisposition) {
        final Bundle params = new Bundle();
        params.putInt("backDisposition", backDisposition);
        return callCommandInternal("setBackDisposition", params);
    }

    /**
     * Lets {@link MockIme} to call
     * {@link android.inputmethodservice.InputMethodService#requestHideSelf(int)} with the given
     * parameters.
     *
     * <p>This triggers {@code requestHideSelf(flags)}.</p>
     *
     * @param flags to be passed as the {@code flags} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callRequestHideSelf(int flags) {
        final Bundle params = new Bundle();
        params.putInt("flags", flags);
        return callCommandInternal("requestHideSelf", params);
    }

    /**
     * Lets {@link MockIme} to call
     * {@link android.inputmethodservice.InputMethodService#requestShowSelf(int)} with the given
     * parameters.
     *
     * <p>This triggers {@code requestShowSelf(flags)}.</p>
     *
     * @param flags to be passed as the {@code flags} parameter
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callRequestShowSelf(int flags) {
        final Bundle params = new Bundle();
        params.putInt("flags", flags);
        return callCommandInternal("requestShowSelf", params);
    }

    /**
     * Requests hiding the current soft input window, with the request origin on the server side.
     *
     * @see InputMethodManager#hideSoftInputFromServerForTest()
     */
    public void hideSoftInputFromServerForTest() {
        final var imm = mContext.getSystemService(InputMethodManager.class);
        runWithShellPermissionIdentity(imm::hideSoftInputFromServerForTest);
    }

    /**
     * Lets {@link MockIme} call
     * {@link android.inputmethodservice.InputMethodService#sendDownUpKeyEvents(int)} with the given
     * {@code keyEventCode}.
     *
     * @param keyEventCode to be passed as the {@code keyEventCode} parameter.
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand callSendDownUpKeyEvents(int keyEventCode) {
        final Bundle params = new Bundle();
        params.putInt("keyEventCode", keyEventCode);
        return callCommandInternal("sendDownUpKeyEvents", params);
    }

    /**
     * Lets {@link MockIme} call
     * {@link android.content.pm.PackageManager#getApplicationInfo(String, int)} with the given
     * {@code packageName} and {@code flags}.
     *
     * @param packageName the package name to be passed to
     *                    {@link android.content.pm.PackageManager#getApplicationInfo(String, int)}.
     * @param flags the flags to be passed to
     *                    {@link android.content.pm.PackageManager#getApplicationInfo(String, int)}.
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callGetApplicationInfo(@NonNull String packageName, int flags) {
        final Bundle params = new Bundle();
        params.putString("packageName", packageName);
        params.putInt("flags", flags);
        return callCommandInternal("getApplicationInfo", params);
    }

    @NonNull
    public ImeCommand callSetEnableOnBackInvokedCallback(Boolean isEnabled) {
        final Bundle params = new Bundle();
        params.putBoolean("isEnabled", isEnabled);
        return callCommandInternal("setEnableOnBackInvokedCallback", params);
    }

    @NonNull
    public ImeCommand callGetDisplayId() {
        final Bundle params = new Bundle();
        return callCommandInternal("getDisplayId", params);
    }

    /**
     * Verifies {@code InputMethodService.getLayoutInflater().getContext()} is equal to
     * {@code InputMethodService.this}.
     *
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}
     */
    @NonNull
    public ImeCommand verifyLayoutInflaterContext() {
        final Bundle params = new Bundle();
        return callCommandInternal("verifyLayoutInflaterContext", params);
    }

    @NonNull
    public ImeCommand callSetHeight(int height) {
        final Bundle params = new Bundle();
        params.putInt("height", height);
        return callCommandInternal("setHeight", params);
    }

    @NonNull
    public void callSetInlineSuggestionsExtras(@NonNull Bundle bundle) {
        mContext.getContentResolver().call(mMockImeSettingsProviderAuthority,
                SettingsProvider.SET_INLINE_SUGGESTION_EXTRAS_COMMAND, null, bundle);
    }

    /**
     * Lets {@link MockIme} call
     * {@link android.inputmethodservice.InputMethodService#setExtractView(View)} with a custom
     * extract view hierarchy.
     *
     * @param label The label text to show in the extract view hierarchy.
     * @return {@link ImeCommand} object that can be passed to
     *         {@link ImeEventStreamTestUtils#expectCommand(ImeEventStream, ImeCommand, long)} to
     *         wait until this event is handled by {@link MockIme}.
     */
    @NonNull
    public ImeCommand callSetExtractView(String label) {
        Bundle params = new Bundle();
        params.putString("label", label);
        return callCommandInternal("setExtractView", params);
    }

    @NonNull
    public ImeCommand callVerifyExtractViewNotNull() {
        return callCommandInternal("verifyExtractViewNotNull", new Bundle());
    }

    @NonNull
    public ImeCommand callVerifyGetDisplay() {
        return callCommandInternal("verifyGetDisplay", new Bundle());
    }

    @NonNull
    public ImeCommand callVerifyIsUiContext() {
        return callCommandInternal("verifyIsUiContext", new Bundle());
    }

    @NonNull
    public ImeCommand callVerifyGetWindowManager() {
        return callCommandInternal("verifyGetWindowManager", new Bundle());
    }

    @NonNull
    public ImeCommand callVerifyGetViewConfiguration() {
        return callCommandInternal("verifyGetViewConfiguration", new Bundle());
    }

    @NonNull
    public ImeCommand callVerifyGetGestureDetector() {
        return callCommandInternal("verifyGetGestureDetector", new Bundle());
    }

    @NonNull
    public ImeCommand callVerifyGetWindowManagerOnDisplayContext() {
        return callCommandInternal("verifyGetWindowManagerOnDisplayContext", new Bundle());
    }

    @NonNull
    public ImeCommand callVerifyGetViewConfigurationOnDisplayContext() {
        return callCommandInternal("verifyGetViewConfigurationOnDisplayContext", new Bundle());
    }

    @NonNull
    public ImeCommand callVerifyGetGestureDetectorOnDisplayContext() {
        return callCommandInternal("verifyGetGestureDetectorOnDisplayContext", new Bundle());
    }

    @NonNull
    public ImeCommand callGetStylusHandwritingWindowVisibility() {
        return callCommandInternal("getStylusHandwritingWindowVisibility", new Bundle());
    }

    @NonNull
    public ImeCommand callGetWindowLayoutInfo() {
        return callCommandInternal("getWindowLayoutInfo", new Bundle());
    }

    @NonNull
    public ImeCommand callHasStylusHandwritingWindow() {
        return callCommandInternal("hasStylusHandwritingWindow", new Bundle());
    }

    @NonNull
    public ImeCommand callSetStylusHandwritingInkView() {
        return callCommandInternal("setStylusHandwritingInkView", new Bundle());
    }

    @NonNull
    public ImeCommand callSetStylusHandwritingTimeout(long timeoutMs) {
        Bundle params = new Bundle();
        params.putLong("timeoutMs", timeoutMs);
        return callCommandInternal("setStylusHandwritingTimeout", params);
    }

    @NonNull
    public ImeCommand callGetStylusHandwritingTimeout() {
        return callCommandInternal("getStylusHandwritingTimeout", new Bundle());
    }

    @NonNull
    public ImeCommand callGetStylusHandwritingEvents() {
        return callCommandInternal("getStylusHandwritingEvents", new Bundle());
    }

    @NonNull
    public ImeCommand callFinishStylusHandwriting() {
        return callCommandInternal("finishStylusHandwriting", new Bundle());
    }

    /**
     * Lets {@link MockIme} call
     * {@link android.inputmethodservice.InputMethodService#finishConnectionlessStylusHandwriting}
     * with the given {@code text}.
     */
    @NonNull
    public ImeCommand callFinishConnectionlessStylusHandwriting(CharSequence text) {
        Bundle params = new Bundle();
        params.putCharSequence("text", text);
        return callCommandInternal("finishConnectionlessStylusHandwriting", params);
    }

    @NonNull
    public ImeCommand callGetCurrentWindowMetricsBounds() {
        return callCommandInternal("getCurrentWindowMetricsBounds", new Bundle());
    }

    @NonNull
    public ImeCommand callSetImeCaptionBarVisible(boolean visible) {
        final Bundle params = new Bundle();
        params.putBoolean("visible", visible);
        return callCommandInternal("setImeCaptionBarVisible", params);
    }
}
