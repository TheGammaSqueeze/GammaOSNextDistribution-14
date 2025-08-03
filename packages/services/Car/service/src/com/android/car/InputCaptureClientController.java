/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car;

import static android.car.CarOccupantZoneManager.DisplayTypeEnum;

import static com.android.car.internal.common.CommonConstants.EMPTY_INT_ARRAY;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import static java.util.Map.entry;

import android.annotation.NonNull;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.builtin.PermissionHelper;
import android.car.builtin.util.Slogf;
import android.car.input.CarInputManager;
import android.car.input.CustomInputEvent;
import android.car.input.ICarInputCallback;
import android.car.input.RotaryEvent;
import android.content.ComponentName;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.SparseArray;
import android.view.KeyEvent;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Manages input capture request from clients
 */
public class InputCaptureClientController {
    private static final boolean DBG_STACK = false;
    private static final boolean DBG_DISPATCH = false;
    private static final boolean DBG_CALLS = false;

    private static final String TAG = CarLog.tagFor(InputCaptureClientController.class);
    /**
     * This table decides which input key goes into which input type. Not mapped here means it is
     * not supported for capturing. Rotary events are treated separately and this is only for
     * key events.
     */
    private static final Map<Integer, Integer> KEY_EVENT_TO_INPUT_TYPE = Map.ofEntries(
            entry(KeyEvent.KEYCODE_DPAD_CENTER, CarInputManager.INPUT_TYPE_DPAD_KEYS),
            entry(KeyEvent.KEYCODE_DPAD_DOWN, CarInputManager.INPUT_TYPE_DPAD_KEYS),
            entry(KeyEvent.KEYCODE_DPAD_UP, CarInputManager.INPUT_TYPE_DPAD_KEYS),
            entry(KeyEvent.KEYCODE_DPAD_LEFT, CarInputManager.INPUT_TYPE_DPAD_KEYS),
            entry(KeyEvent.KEYCODE_DPAD_RIGHT, CarInputManager.INPUT_TYPE_DPAD_KEYS),
            entry(KeyEvent.KEYCODE_DPAD_DOWN_LEFT, CarInputManager.INPUT_TYPE_DPAD_KEYS),
            entry(KeyEvent.KEYCODE_DPAD_DOWN_RIGHT, CarInputManager.INPUT_TYPE_DPAD_KEYS),
            entry(KeyEvent.KEYCODE_DPAD_UP_LEFT, CarInputManager.INPUT_TYPE_DPAD_KEYS),
            entry(KeyEvent.KEYCODE_DPAD_UP_RIGHT, CarInputManager.INPUT_TYPE_DPAD_KEYS),
            entry(KeyEvent.KEYCODE_NAVIGATE_IN, CarInputManager.INPUT_TYPE_NAVIGATE_KEYS),
            entry(KeyEvent.KEYCODE_NAVIGATE_OUT, CarInputManager.INPUT_TYPE_NAVIGATE_KEYS),
            entry(KeyEvent.KEYCODE_NAVIGATE_NEXT, CarInputManager.INPUT_TYPE_NAVIGATE_KEYS),
            entry(KeyEvent.KEYCODE_NAVIGATE_PREVIOUS, CarInputManager.INPUT_TYPE_NAVIGATE_KEYS),
            entry(KeyEvent.KEYCODE_BACK, CarInputManager.INPUT_TYPE_NAVIGATE_KEYS),
            entry(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN,
                    CarInputManager.INPUT_TYPE_SYSTEM_NAVIGATE_KEYS),
            entry(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP,
                    CarInputManager.INPUT_TYPE_SYSTEM_NAVIGATE_KEYS),
            entry(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
                    CarInputManager.INPUT_TYPE_SYSTEM_NAVIGATE_KEYS),
            entry(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
                    CarInputManager.INPUT_TYPE_SYSTEM_NAVIGATE_KEYS),
            entry(KeyEvent.KEYCODE_VOICE_ASSIST, CarInputManager.INPUT_TYPE_SYSTEM_NAVIGATE_KEYS)
    );

    private static final Set<Integer> VALID_INPUT_TYPES = Set.of(
            CarInputManager.INPUT_TYPE_ALL_INPUTS,
            CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION,
            CarInputManager.INPUT_TYPE_DPAD_KEYS,
            CarInputManager.INPUT_TYPE_NAVIGATE_KEYS,
            CarInputManager.INPUT_TYPE_SYSTEM_NAVIGATE_KEYS,
            CarInputManager.INPUT_TYPE_CUSTOM_INPUT_EVENT
    );

    private static final Set<Integer> VALID_ROTARY_TYPES = Set.of(
            CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION
    );

    // TODO(b/150818155) Need to migrate cluster code to use this to enable it.
    private static final List<Integer> SUPPORTED_DISPLAY_TYPES = List.of(
            CarOccupantZoneManager.DISPLAY_TYPE_MAIN,
            CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER
    );

    private static final int[] EMPTY_INPUT_TYPES = EMPTY_INT_ARRAY;

    private final class ClientInfoForDisplay implements IBinder.DeathRecipient {
        private final int mUid;
        private final int mPid;
        private final ICarInputCallback mCallback;
        private final int mTargetDisplayType;
        private final int[] mInputTypes;
        private final int mFlags;
        private final ArrayList<Integer> mGrantedTypes;

        private ClientInfoForDisplay(int uid, int pid, @NonNull ICarInputCallback callback,
                int targetDisplayType, int[] inputTypes, int flags) {
            mUid = uid;
            mPid = pid;
            mCallback = callback;
            mTargetDisplayType = targetDisplayType;
            mInputTypes = inputTypes;
            mFlags = flags;
            mGrantedTypes = new ArrayList<>(inputTypes.length);
        }

        private void linkToDeath() throws RemoteException {
            mCallback.asBinder().linkToDeath(this, 0);
        }

        private void unlinkToDeath() {
            mCallback.asBinder().unlinkToDeath(this, 0);
        }

        @Override
        public void binderDied() {
            onClientDeath(this);
        }

        @Override
        public String toString() {
            return new StringBuilder(128)
                    .append("Client{")
                    .append("uid:")
                    .append(mUid)
                    .append(",pid:")
                    .append(mPid)
                    .append(",callback:")
                    .append(mCallback)
                    .append(",inputTypes:")
                    .append(Arrays.toString(mInputTypes))
                    .append(",flags:")
                    .append(Integer.toHexString(mFlags))
                    .append(",grantedTypes:")
                    .append(mGrantedTypes)
                    .append("}")
                    .toString();
        }
    }

    private static final class ClientsToDispatch {
        // The same client can be added multiple times. Keeping only the last addition is ok.
        private final ArrayMap<ICarInputCallback, int[]> mClientsToDispatch =
                new ArrayMap<>();
        private final int mDisplayType;

        private ClientsToDispatch(int displayType) {
            mDisplayType = displayType;
        }

        private void add(ClientInfoForDisplay client) {
            int[] inputTypesToDispatch;
            if (client.mGrantedTypes.isEmpty()) {
                inputTypesToDispatch = EMPTY_INPUT_TYPES;
            } else {
                inputTypesToDispatch = client.mGrantedTypes.stream().mapToInt(
                        Integer::intValue).toArray();
            }
            mClientsToDispatch.put(client.mCallback, inputTypesToDispatch);
        }
    }

    private final Context mContext;

    private final Object mLock = new Object();

    /**
     * key: display type, for quick discovery of client
     * LinkedList is for implementing stack. First entry is the top.
     */
    @GuardedBy("mLock")
    private final SparseArray<LinkedList<ClientInfoForDisplay>> mFullDisplayEventCapturers =
            new SparseArray<>(2);

    /**
     * key: display type -> inputType, for quick discovery of client
     * LinkedList is for implementing stack. First entry is the top.
     */
    @GuardedBy("mLock")
    private final SparseArray<SparseArray<LinkedList<ClientInfoForDisplay>>>
            mPerInputTypeCapturers = new SparseArray<>(2);

    @GuardedBy("mLock")
    /** key: display type -> client binder */
    private final SparseArray<HashMap<IBinder, ClientInfoForDisplay>> mAllClients =
            new SparseArray<>(1);

    @GuardedBy("mLock")
    /** Keeps events to dispatch together. FIFO, last one added to last */
    private final LinkedList<ClientsToDispatch> mClientDispatchQueue =
            new LinkedList<>();

    /** Accessed from dispatch thread only */
    private final ArrayList<KeyEvent> mKeyEventDispatchScratchList = new ArrayList<>(1);

    /** Accessed from dispatch thread only */
    private final ArrayList<RotaryEvent> mRotaryEventDispatchScratchList = new ArrayList<>(1);

    /** Accessed from dispatch thread only */
    private final ArrayList<CustomInputEvent> mCustomInputEventDispatchScratchList =
            new ArrayList<>(1);

    @GuardedBy("mLock")
    private int mNumKeyEventsDispatched;
    @GuardedBy("mLock")
    private int mNumRotaryEventsDispatched;

    private final String mClusterHomePackage;

    public InputCaptureClientController(Context context) {
        mContext = context;
        mClusterHomePackage = ComponentName.unflattenFromString(
                mContext.getString(R.string.config_clusterHomeActivity)).getPackageName();
    }

    /**
     * See
     * {@link CarInputManager#requestInputEventCapture(CarInputManager.CarInputCaptureCallback,
     * int, int[], int)}.
     */
    public int requestInputEventCapture(ICarInputCallback callback,
            @DisplayTypeEnum int targetDisplayType,
            int[] inputTypes, int requestFlags) {
        CarServiceUtils.assertAnyPermission(mContext,
                Car.PERMISSION_CAR_MONITOR_INPUT, PermissionHelper.MONITOR_INPUT);

        Preconditions.checkArgument(SUPPORTED_DISPLAY_TYPES.contains(targetDisplayType),
                "Display not supported yet:" + targetDisplayType);

        boolean isRequestingAllEvents =
                (requestFlags & CarInputManager.CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY) != 0;
        if (isRequestingAllEvents) {
            if (targetDisplayType != CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER) {
                CarServiceUtils.assertCallingFromSystemProcessOrSelf();
            } else {  // for DISPLAY_TYPE_INSTRUMENT_CLUSTER
                if (!CarServiceUtils.isCallingFromSystemProcessOrSelf()) {
                    CarServiceUtils.checkCalledByPackage(mContext, mClusterHomePackage);
                }
            }
            if (inputTypes.length != 1 || inputTypes[0] != CarInputManager.INPUT_TYPE_ALL_INPUTS) {
                throw new IllegalArgumentException("Input type should be INPUT_TYPE_ALL_INPUTS"
                        + " for CAPTURE_REQ_FLAGS_TAKE_ALL_EVENTS_FOR_DISPLAY");
            }
        }
        if (targetDisplayType != CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER
                && targetDisplayType != CarOccupantZoneManager.DISPLAY_TYPE_MAIN) {
            throw new IllegalArgumentException("Unrecognized display type:" + targetDisplayType);
        }
        if (inputTypes == null) {
            throw new IllegalArgumentException("inputTypes cannot be null");
        }
        assertInputTypeValid(inputTypes);
        Arrays.sort(inputTypes);
        IBinder clientBinder = callback.asBinder();
        boolean allowsDelayedGrant =
                (requestFlags & CarInputManager.CAPTURE_REQ_FLAGS_ALLOW_DELAYED_GRANT) != 0;
        int ret = CarInputManager.INPUT_CAPTURE_RESPONSE_SUCCEEDED;
        if (DBG_CALLS) {
            Slogf.i(TAG,
                    "requestInputEventCapture callback:" + callback
                            + ", display:" + targetDisplayType
                            + ", inputTypes:" + Arrays.toString(inputTypes)
                            + ", flags:" + requestFlags);
        }
        ClientsToDispatch clientsToDispatch = new ClientsToDispatch(targetDisplayType);
        synchronized (mLock) {
            HashMap<IBinder, ClientInfoForDisplay> allClientsForDisplay = mAllClients.get(
                    targetDisplayType);
            if (allClientsForDisplay == null) {
                allClientsForDisplay = new HashMap<IBinder, ClientInfoForDisplay>();
                mAllClients.put(targetDisplayType, allClientsForDisplay);
            }
            ClientInfoForDisplay oldClientInfo = allClientsForDisplay.remove(clientBinder);

            LinkedList<ClientInfoForDisplay> fullCapturersStack = mFullDisplayEventCapturers.get(
                    targetDisplayType);
            if (fullCapturersStack == null) {
                fullCapturersStack = new LinkedList<ClientInfoForDisplay>();
                mFullDisplayEventCapturers.put(targetDisplayType, fullCapturersStack);
            }

            if (!isRequestingAllEvents && fullCapturersStack.size() > 0
                    && fullCapturersStack.getFirst() != oldClientInfo && !allowsDelayedGrant) {
                // full capturing active. return failed if not delayed granting.
                return CarInputManager.INPUT_CAPTURE_RESPONSE_FAILED;
            }
            // Now we need to register client anyway, so do death monitoring from here.
            ClientInfoForDisplay newClient = new ClientInfoForDisplay(Binder.getCallingUid(),
                    Binder.getCallingPid(), callback, targetDisplayType,
                    inputTypes, requestFlags);
            try {
                newClient.linkToDeath();
            } catch (RemoteException e) {
                // client died
                Slogf.i(TAG, "requestInputEventCapture, cannot linkToDeath to client, pid:"
                        + Binder.getCallingUid());
                return CarInputManager.INPUT_CAPTURE_RESPONSE_FAILED;
            }

            SparseArray<LinkedList<ClientInfoForDisplay>> perInputStacks =
                    mPerInputTypeCapturers.get(targetDisplayType);
            if (perInputStacks == null) {
                perInputStacks = new SparseArray<LinkedList<ClientInfoForDisplay>>();
                mPerInputTypeCapturers.put(targetDisplayType, perInputStacks);
            }

            if (isRequestingAllEvents) {
                if (!fullCapturersStack.isEmpty()) {
                    ClientInfoForDisplay oldCapturer = fullCapturersStack.getFirst();
                    if (oldCapturer != oldClientInfo) {
                        oldCapturer.mGrantedTypes.clear();
                        clientsToDispatch.add(oldCapturer);
                    }
                    fullCapturersStack.remove(oldClientInfo);
                } else { // All per input type top stack client should be notified.
                    for (int i = 0; i < perInputStacks.size(); i++) {
                        LinkedList<ClientInfoForDisplay> perTypeStack = perInputStacks.valueAt(i);
                        if (!perTypeStack.isEmpty()) {
                            ClientInfoForDisplay topClient = perTypeStack.getFirst();
                            if (topClient != oldClientInfo) {
                                topClient.mGrantedTypes.clear();
                                clientsToDispatch.add(topClient);
                            }
                            // Even if the client was on top, the one in back does not need
                            // update.
                            perTypeStack.remove(oldClientInfo);
                        }
                    }
                }
                fullCapturersStack.addFirst(newClient);

            } else {
                boolean hadFullCapture = false;
                boolean fullCaptureActive = false;
                if (fullCapturersStack.size() > 0) {
                    if (fullCapturersStack.getFirst() == oldClientInfo) {
                        fullCapturersStack.remove(oldClientInfo);
                        // Now we need to check if there is other client in fullCapturersStack
                        if (fullCapturersStack.size() > 0) {
                            fullCaptureActive = true;
                            ret = CarInputManager.INPUT_CAPTURE_RESPONSE_DELAYED;
                            ClientInfoForDisplay topClient = fullCapturersStack.getFirst();
                            topClient.mGrantedTypes.clear();
                            topClient.mGrantedTypes.add(CarInputManager.INPUT_TYPE_ALL_INPUTS);
                            clientsToDispatch.add(topClient);
                        } else {
                            hadFullCapture = true;
                        }
                    } else {
                        // other client doing full capturing and it should have DELAYED_GRANT flag.
                        fullCaptureActive = true;
                        ret = CarInputManager.INPUT_CAPTURE_RESPONSE_DELAYED;
                    }
                }
                for (int i = 0; i < perInputStacks.size(); i++) {
                    LinkedList<ClientInfoForDisplay> perInputStack = perInputStacks.valueAt(i);
                    perInputStack.remove(oldClientInfo);
                }
                // Now go through per input stack
                for (int inputType : inputTypes) {
                    LinkedList<ClientInfoForDisplay> perInputStack = perInputStacks.get(
                            inputType);
                    if (perInputStack == null) {
                        perInputStack = new LinkedList<ClientInfoForDisplay>();
                        perInputStacks.put(inputType, perInputStack);
                    }
                    if (perInputStack.size() > 0) {
                        ClientInfoForDisplay oldTopClient = perInputStack.getFirst();
                        if (oldTopClient.mGrantedTypes.remove(Integer.valueOf(inputType))) {
                            clientsToDispatch.add(oldTopClient);
                        }
                    }
                    if (!fullCaptureActive) {
                        newClient.mGrantedTypes.add(inputType);
                    }
                    perInputStack.addFirst(newClient);
                }
                if (!fullCaptureActive && hadFullCapture) {
                    for (int i = 0; i < perInputStacks.size(); i++) {
                        int inputType = perInputStacks.keyAt(i);
                        LinkedList<ClientInfoForDisplay> perInputStack = perInputStacks.valueAt(
                                i);
                        if (perInputStack.size() > 0) {
                            ClientInfoForDisplay topStackClient = perInputStack.getFirst();
                            if (topStackClient == newClient) {
                                continue;
                            }
                            if (!topStackClient.mGrantedTypes.contains(inputType)) {
                                topStackClient.mGrantedTypes.add(inputType);
                                clientsToDispatch.add(topStackClient);
                            }
                        }
                    }
                }
            }
            allClientsForDisplay.put(clientBinder, newClient);
            dispatchClientCallbackLocked(clientsToDispatch);
        }
        return ret;
    }

    /**
     * See {@link CarInputManager#releaseInputEventCapture(int)}.
     */
    public void releaseInputEventCapture(ICarInputCallback callback, int targetDisplayType) {
        Objects.requireNonNull(callback);
        Preconditions.checkArgument(SUPPORTED_DISPLAY_TYPES.contains(targetDisplayType),
                "Display not supported yet:" + targetDisplayType);

        if (DBG_CALLS) {
            Slogf.i(TAG, "releaseInputEventCapture callback:" + callback
                    + ", display:" + targetDisplayType);
        }
        ClientsToDispatch clientsToDispatch = new ClientsToDispatch(targetDisplayType);
        synchronized (mLock) {
            HashMap<IBinder, ClientInfoForDisplay> allClientsForDisplay = mAllClients.get(
                    targetDisplayType);
            ClientInfoForDisplay clientInfo = allClientsForDisplay.remove(callback.asBinder());
            if (clientInfo == null) {
                Slogf.w(TAG, "Cannot find client for releaseInputEventCapture:" + callback);
                return;
            }
            clientInfo.unlinkToDeath();
            LinkedList<ClientInfoForDisplay> fullCapturersStack = mFullDisplayEventCapturers.get(
                    targetDisplayType);
            boolean fullCaptureActive = false;
            if (fullCapturersStack.size() > 0) {
                if (fullCapturersStack.getFirst() == clientInfo) {
                    fullCapturersStack.remove(clientInfo);
                    if (fullCapturersStack.size() > 0) {
                        ClientInfoForDisplay newStopStackClient = fullCapturersStack.getFirst();
                        newStopStackClient.mGrantedTypes.clear();
                        newStopStackClient.mGrantedTypes.add(CarInputManager.INPUT_TYPE_ALL_INPUTS);
                        clientsToDispatch.add(newStopStackClient);
                        fullCaptureActive = true;
                    }
                } else { // no notification as other client is in top of the stack
                    fullCaptureActive = true;
                }
                fullCapturersStack.remove(clientInfo);
            }
            SparseArray<LinkedList<ClientInfoForDisplay>> perInputStacks =
                    mPerInputTypeCapturers.get(targetDisplayType);
            if (DBG_STACK) {
                Slogf.i(TAG, "releaseInputEventCapture, fullCaptureActive:"
                        + fullCaptureActive + ", perInputStacks:" + perInputStacks);
            }
            if (perInputStacks != null) {
                for (int i = 0; i < perInputStacks.size(); i++) {
                    int inputType = perInputStacks.keyAt(i);
                    LinkedList<ClientInfoForDisplay> perInputStack = perInputStacks.valueAt(i);
                    if (perInputStack.size() > 0) {
                        if (perInputStack.getFirst() == clientInfo) {
                            perInputStack.removeFirst();
                            if (perInputStack.size() > 0) {
                                ClientInfoForDisplay newTopClient = perInputStack.getFirst();
                                if (!fullCaptureActive) {
                                    newTopClient.mGrantedTypes.add(inputType);
                                    clientsToDispatch.add(newTopClient);
                                }
                            }
                        } else { // something else on top.
                            if (!fullCaptureActive) {
                                ClientInfoForDisplay topClient = perInputStack.getFirst();
                                if (!topClient.mGrantedTypes.contains(inputType)) {
                                    topClient.mGrantedTypes.add(inputType);
                                    clientsToDispatch.add(topClient);
                                }
                            }
                            perInputStack.remove(clientInfo);
                        }
                    }
                }
            }
            dispatchClientCallbackLocked(clientsToDispatch);
        }
    }

    /**
     * Dispatches the given {@code KeyEvent} to a capturing client if there is one.
     *
     * @param displayType the display type defined in {@code CarInputManager} such as
     *                    {@link CarOccupantZoneManager#DISPLAY_TYPE_MAIN}
     * @param event       the key event to handle
     * @return true if the event was consumed.
     */
    public boolean onKeyEvent(@DisplayTypeEnum int displayType, KeyEvent event) {
        if (!SUPPORTED_DISPLAY_TYPES.contains(displayType)) {
            return false;
        }
        Integer inputType = KEY_EVENT_TO_INPUT_TYPE.get(event.getKeyCode());
        if (inputType == null) { // not supported key
            inputType = CarInputManager.INPUT_TYPE_ALL_INPUTS;
        }
        ICarInputCallback callback;
        synchronized (mLock) {
            callback = getClientForInputTypeLocked(displayType, inputType);
            if (callback == null) {
                return false;
            }
            mNumKeyEventsDispatched++;
        }

        dispatchKeyEvent(displayType, event, callback);
        return true;
    }

    /**
     * Dispatches the given {@code RotaryEvent} to a capturing client if there is one.
     *
     * @param displayType the display type defined in {@code CarInputManager} such as
     *                    {@link CarOccupantZoneManager#DISPLAY_TYPE_MAIN}
     * @param event       the Rotary event to handle
     * @return true if the event was consumed.
     */
    public boolean onRotaryEvent(@DisplayTypeEnum int displayType, RotaryEvent event) {
        if (!SUPPORTED_DISPLAY_TYPES.contains(displayType)) {
            Slogf.w(TAG, "onRotaryEvent for not supported display:" + displayType);
            return false;
        }
        int inputType = event.getInputType();
        if (!VALID_ROTARY_TYPES.contains(inputType)) {
            Slogf.w(TAG, "onRotaryEvent for not supported input type:" + inputType);
            return false;
        }

        ICarInputCallback callback;
        synchronized (mLock) {
            callback = getClientForInputTypeLocked(displayType, inputType);
            if (callback == null) {
                if (DBG_DISPATCH) {
                    Slogf.i(TAG, "onRotaryEvent no client for input type:" + inputType);
                }
                return false;
            }
            mNumRotaryEventsDispatched++;
        }

        dispatchRotaryEvent(displayType, event, callback);
        return true;
    }

    /**
     * Dispatches the given {@link CustomInputEvent} to a capturing client if there is one.
     * Nothing happens if no callback was registered for the incoming event. In this case this
     * method will return {@code false}.
     * <p>
     * In case of there are more than one client registered for this event, then only the first one
     * will be notified.
     *
     * @param event the {@link CustomInputEvent} to dispatch
     * @return {@code true} if the event was consumed.
     */
    public boolean onCustomInputEvent(CustomInputEvent event) {
        int displayType = event.getTargetDisplayType();
        if (!SUPPORTED_DISPLAY_TYPES.contains(displayType)) {
            Slogf.w(TAG, "onCustomInputEvent for not supported display:" + displayType);
            return false;
        }
        ICarInputCallback callback;
        synchronized (mLock) {
            callback = getClientForInputTypeLocked(displayType,
                    CarInputManager.INPUT_TYPE_CUSTOM_INPUT_EVENT);
            if (callback == null) {
                Slogf.w(TAG, "No client for input: " + CarInputManager.INPUT_TYPE_CUSTOM_INPUT_EVENT
                        + " and display: " + displayType);
                return false;
            }
        }
        dispatchCustomInputEvent(displayType, event, callback);
        return true;
    }

    @GuardedBy("mLock")
    ICarInputCallback getClientForInputTypeLocked(int targetDisplayType, int inputType) {
        LinkedList<ClientInfoForDisplay> fullCapturersStack = mFullDisplayEventCapturers.get(
                targetDisplayType);
        if (fullCapturersStack != null && fullCapturersStack.size() > 0) {
            return fullCapturersStack.getFirst().mCallback;
        }

        SparseArray<LinkedList<ClientInfoForDisplay>> perInputStacks =
                mPerInputTypeCapturers.get(targetDisplayType);
        if (perInputStacks == null) {
            return null;
        }

        LinkedList<ClientInfoForDisplay> perInputStack = perInputStacks.get(inputType);
        if (perInputStack != null && perInputStack.size() > 0) {
            return perInputStack.getFirst().mCallback;
        }

        return null;
    }

    private void onClientDeath(ClientInfoForDisplay client) {
        releaseInputEventCapture(client.mCallback, client.mTargetDisplayType);
    }

    /** dump for debugging */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(PrintWriter writer) {
        writer.println("**InputCaptureClientController**");
        synchronized (mLock) {
            for (int display : SUPPORTED_DISPLAY_TYPES) {
                writer.println("***Display:" + display);

                HashMap<IBinder, ClientInfoForDisplay> allClientsForDisplay = mAllClients.get(
                        display);
                if (allClientsForDisplay != null) {
                    writer.println("****All clients:");
                    for (ClientInfoForDisplay client : allClientsForDisplay.values()) {
                        writer.println(client);
                    }
                }

                LinkedList<ClientInfoForDisplay> fullCapturersStack =
                        mFullDisplayEventCapturers.get(display);
                if (fullCapturersStack != null) {
                    writer.println("****Full capture stack");
                    for (ClientInfoForDisplay client : fullCapturersStack) {
                        writer.println(client);
                    }
                }
                SparseArray<LinkedList<ClientInfoForDisplay>> perInputStacks =
                        mPerInputTypeCapturers.get(display);
                if (perInputStacks != null) {
                    for (int i = 0; i < perInputStacks.size(); i++) {
                        int inputType = perInputStacks.keyAt(i);
                        LinkedList<ClientInfoForDisplay> perInputStack = perInputStacks.valueAt(i);
                        if (perInputStack.size() > 0) {
                            writer.println("**** Per Input stack, input type:" + inputType);
                            for (ClientInfoForDisplay client : perInputStack) {
                                writer.println(client);
                            }
                        }
                    }
                }
            }
            writer.println("mNumKeyEventsDispatched:" + mNumKeyEventsDispatched
                    + ",mNumRotaryEventsDispatched:" + mNumRotaryEventsDispatched);
        }
    }

    @GuardedBy("mLock")
    private void dispatchClientCallbackLocked(ClientsToDispatch clientsToDispatch) {
        if (clientsToDispatch.mClientsToDispatch.isEmpty()) {
            return;
        }
        if (DBG_DISPATCH) {
            Slogf.i(TAG, "dispatchClientCallbackLocked, number of clients:"
                    + clientsToDispatch.mClientsToDispatch.size());
        }
        mClientDispatchQueue.add(clientsToDispatch);
        CarServiceUtils.runOnCommon(() -> {
            ClientsToDispatch clients;
            synchronized (mLock) {
                if (mClientDispatchQueue.isEmpty()) {
                    return;
                }
                clients = mClientDispatchQueue.pop();
            }

            if (DBG_DISPATCH) {
                Slogf.i(TAG, "dispatching to clients, num of clients:"
                        + clients.mClientsToDispatch.size()
                        + ", display:" + clients.mDisplayType);
            }
            for (int i = 0; i < clients.mClientsToDispatch.size(); i++) {
                ICarInputCallback callback = clients.mClientsToDispatch.keyAt(i);
                int[] inputTypes = clients.mClientsToDispatch.valueAt(i);
                Arrays.sort(inputTypes);
                if (DBG_DISPATCH) {
                    Slogf.i(TAG, "dispatching to client, callback:"
                            + callback + ", inputTypes:" + Arrays.toString(inputTypes));
                }
                try {
                    callback.onCaptureStateChanged(clients.mDisplayType, inputTypes);
                } catch (RemoteException e) {
                    // Ignore. Let death handler deal with it.
                }
            }
        });
    }

    private void dispatchKeyEvent(int targetDisplayType, KeyEvent event,
            ICarInputCallback callback) {
        CarServiceUtils.runOnCommon(() -> {
            mKeyEventDispatchScratchList.clear();
            mKeyEventDispatchScratchList.add(event);
            try {
                callback.onKeyEvents(targetDisplayType, mKeyEventDispatchScratchList);
            } catch (RemoteException e) {
                if (DBG_DISPATCH) {
                    Slogf.e(TAG, "Failed to dispatch KeyEvent " + event, e);
                }
            }
        });
    }

    private void dispatchRotaryEvent(int targetDisplayType, RotaryEvent event,
            ICarInputCallback callback) {
        if (DBG_DISPATCH) {
            Slogf.i(TAG, "dispatchRotaryEvent:" + event);
        }
        // TODO(b/159623196): Use HandlerThread for dispatching rather than relying on the main
        //     thread. Change here and other dispatch methods.
        CarServiceUtils.runOnCommon(() -> {
            mRotaryEventDispatchScratchList.clear();
            mRotaryEventDispatchScratchList.add(event);
            try {
                callback.onRotaryEvents(targetDisplayType, mRotaryEventDispatchScratchList);
            } catch (RemoteException e) {
                if (DBG_DISPATCH) {
                    Slogf.e(TAG, "Failed to dispatch RotaryEvent " + event, e);
                }
            }
        });
    }

    private void dispatchCustomInputEvent(@DisplayTypeEnum int targetDisplayType,
            CustomInputEvent event,
            ICarInputCallback callback) {
        if (DBG_DISPATCH) {
            Slogf.d(TAG, "dispatchCustomInputEvent:" + event);
        }
        CarServiceUtils.runOnCommon(() -> {
            mCustomInputEventDispatchScratchList.clear();
            mCustomInputEventDispatchScratchList.add(event);
            try {
                callback.onCustomInputEvents(targetDisplayType,
                        mCustomInputEventDispatchScratchList);
            } catch (RemoteException e) {
                if (DBG_DISPATCH) {
                    Slogf.e(TAG, "Failed to dispatch CustomInputEvent " + event, e);
                }
            }
        });
    }

    private static void assertInputTypeValid(int[] inputTypes) {
        for (int inputType : inputTypes) {
            if (!VALID_INPUT_TYPES.contains(inputType)) {
                throw new IllegalArgumentException("Invalid input type:" + inputType
                        + ", inputTypes:" + Arrays.toString(inputTypes));
            }
        }
    }
}
