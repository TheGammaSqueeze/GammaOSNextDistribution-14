/*
 * Copyright (c) 2016-2017, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.qti.confuridialer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;

/**
 * This class gives a Conference URI dialer UI and provide
 * user to dial Conference URI Call, dial Empty URI List, add
 * participant etc.
 */
public class ConfURIDialer extends Activity {

    private EditText mEditText;
    private Button mStartCallButton;
    private Button mVideoCallButton;
    private ImageButton mContactButton;
    private ImageButton mCallLogButton;
    private Button mCancelButton;
    private Cursor mCursor;
    private ListView mListView;
    private String mEditNumber;
    private static final int ACTIVITY_REQUEST_CONTACT_PICK = 100;
    private static final String INTENT_PICK_ACTION = "android.intent.action.PICK";
    private static final String TAG = "ConfURIDialer";
    public static final String ADD_PARTICIPANT_KEY = "add_participant";
    private static final String KEY_IS_CALL_LOG_PICKER_SHOWN = "is_call_log_picker_shown";
    private static final String KEY_EXISTING_EDIT_TEXT = "existing_edit_text";
    //ensure this extra is same the one defined in QtiCallUtils.java
    private static final String EXTRA_ADD_PARTICIPANT_NUMBER =
            "org.codeaurora.extra.ADD_PARTICIPANT_NUMBER";
    private boolean mIsAddParticipants = false;
    private boolean mIsInCall;
    private ConfURIDialerPhoneStateListener mPhoneStateListener;
    private Context mContext;
    private AlertDialog mAlertDialog = null;

    // List of permissions that need to be mapped to corresponding request code
    private static String[] permissionsList = new String[] {
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE
    };

    // Request codes for required permission(s)
    public static final int REQUEST_READ_PHONE_STATE = 0;
    public static final int REQUEST_READ_CALL_LOG = 1;
    public static final int REQUEST_READ_CONTACTS = 2;
    public static final int REQUEST_CALL_PHONE = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conf_uri_dialer);
        mContext = this;
        mEditText = (EditText) findViewById(R.id.edit_number);
        mContactButton = (ImageButton) findViewById(R.id.contactlist);
        mCallLogButton = (ImageButton) findViewById(R.id.btn_pick_callLog);
        mStartCallButton = (Button) findViewById(R.id.btn_start_call);
        mVideoCallButton = (Button) findViewById(R.id.btn_video_call);
        mCancelButton = (Button) findViewById(R.id.btn_cancel);
        mIsAddParticipants = getIntent().getBooleanExtra(ADD_PARTICIPANT_KEY, false);

        mPhoneStateListener = new ConfURIDialerPhoneStateListener(this);
        registerCallStateListener();
        if (checkPermissions(REQUEST_READ_PHONE_STATE) &&
                checkPermissions(REQUEST_CALL_PHONE)) {
            mIsInCall = isInCall(mContext);
            displayStartCall();
            displayVideoConf();
        }

        // Allow the title to be set to a custom String using an extra on the intent
        String title = getIntent().getStringExtra("Title");
        if (title != null) {
            setTitle(title);
        }

        if (savedInstanceState != null) {
            mEditText.setText(savedInstanceState.getString(KEY_EXISTING_EDIT_TEXT));
            if (savedInstanceState.getBoolean(KEY_IS_CALL_LOG_PICKER_SHOWN)) {
                if (checkPermissions(REQUEST_READ_CALL_LOG)) {
                    getCallLog();
                }
            }
        }

        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (mIsAddParticipants && mIsInCall) {
                    if (isDigitsEmpty()) {
                        mStartCallButton.setBackgroundColor(Color.parseColor("#C1C1C1"));
                        mStartCallButton.setEnabled(false);
                        mStartCallButton.setVisibility(View.VISIBLE);
                    } else {
                        mStartCallButton.setBackgroundColor(Color.parseColor("#00C853"));
                        mStartCallButton.setEnabled(true);
                        mStartCallButton.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        mCallLogButton.setOnClickListener(new ImageButton.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                hideInputMethod(getCurrentFocus());
                if (checkPermissions(REQUEST_READ_CALL_LOG)) {
                    getCallLog();
                }
            }
        });

        mContactButton.setOnClickListener(new ImageButton.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (checkPermissions(REQUEST_READ_CONTACTS)) {
                    Intent intent = new Intent(INTENT_PICK_ACTION, Contacts.CONTENT_URI);
                    startActivityForResult(intent,ACTIVITY_REQUEST_CONTACT_PICK);
                }
            }
        });

        mStartCallButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                //Disable call buttons to prevent multiple clicks.
                mStartCallButton.setEnabled(false);
                mVideoCallButton.setEnabled(false);
                mEditNumber = mEditText.getText().toString();
                Log.d(TAG, "onClick of CallButton number = " + mEditNumber);
                startButtonPressed(mEditNumber, false /*isVideoCall*/);
            }
        });

        mVideoCallButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                //Disable call buttons to prevent multiple clicks.
                mVideoCallButton.setEnabled(false);
                mStartCallButton.setEnabled(false);
                mEditNumber = mEditText.getText().toString();
                Log.d(TAG, "onClick of VideoCallButton number = " + mEditNumber);
                startButtonPressed(mEditNumber, true /*isVideoCall*/);
            }
        });

        mCancelButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                finish();
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermissions(REQUEST_READ_PHONE_STATE) &&
                checkPermissions(REQUEST_CALL_PHONE)) {
            mIsInCall = isInCall(mContext);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideInputMethod(getCurrentFocus());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_EXISTING_EDIT_TEXT, mEditText.getText().toString());
        outState.putBoolean(KEY_IS_CALL_LOG_PICKER_SHOWN,
                mAlertDialog != null && mAlertDialog.isShowing());
    }

    /**
    * Registers a call state listener.
    */
    public void registerCallStateListener() {
        Log.v(TAG, "registerCallStateListener() invoked.");
        TelephonyManager tm =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
    * Unregisters a call state listener.
    */
    public void unRegisterCallStateListener() {
        Log.v(TAG, "unRegisterCallStateListener() invoked.");
        TelephonyManager tm =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    /* Return Uri with an appropriate scheme, accepting both SIP and usual phone call numbers. */
    private static Uri getCallUri(String number) {
        return Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
    }

    private List<Uri> getConferenceCallList(String numbers) {
        Log.d(TAG, "getConferenceCallList numbers: " + numbers);
        String[] splitNum = numbers.split(";");
        List<Uri> numList = new ArrayList<>();
        for (String num : splitNum) {
            numList.add(Uri.parse(num));
            Log.d(TAG, "ConferenceCall uri: " + Uri.parse(num));
        }
        return numList;
    }

    /** @return true if the EditText of phone number or uri digit is empty. */
    private boolean isDigitsEmpty() {
        return mEditText.length() == 0;
    }

    private boolean isVideoTelephonyAvailable() {
        TelecomManager telecommMgr =
                (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        if (telecommMgr == null) {
            return false;
        }
        List<PhoneAccountHandle> accountHandles = telecommMgr.getCallCapablePhoneAccounts();
        for (PhoneAccountHandle accountHandle : accountHandles) {
            PhoneAccount account = telecommMgr.getPhoneAccount(accountHandle);
            if (account != null) {
                if (account.hasCapabilities(PhoneAccount.CAPABILITY_VIDEO_CALLING)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean validateAddParticipants(String numbers){
        Log.d(TAG, "Add Participants number: " + numbers);
        if (numbers == null || numbers.isEmpty()) {
            Toast.makeText(this, R.string.add_participant_imposible,
                    Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private static TelecomManager getTelecomManager(Context context) {
        return (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
    }

    private static boolean isInCall(Context context) {
        return getTelecomManager(context).isInCall();
    }

    private void startButtonPressed(String number, boolean isVideoCall) {
        Log.d(TAG, "startButtonPressed, number: " + number);
        if (mIsAddParticipants && isInCall(this)) {
            boolean isAddParticipant = validateAddParticipants(number);
            if (isAddParticipant) {
                getIntent().putExtra(EXTRA_ADD_PARTICIPANT_NUMBER, number);
                setResult(Activity.RESULT_OK, getIntent());
                finish();
            } else {
                //Enable call buttons if process button click failed.
                mStartCallButton.setEnabled(true);
                mVideoCallButton.setEnabled(true);
            }
        } else {
            List<Uri> numList = getConferenceCallList(number);
            TelecomManager tm = getTelecomManager(mContext);
            if (tm == null) {
                //Enable call buttons if process button click failed.
                mStartCallButton.setEnabled(true);
                mVideoCallButton.setEnabled(true);
                return;
            }
            Bundle extras = new Bundle();
            extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                    isVideoCall ? VideoProfile.STATE_BIDIRECTIONAL : VideoProfile.STATE_AUDIO_ONLY);
            extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE,
                    isVideoCall? true : false);
            tm.startConference(numList, extras);
            finish();
        }
    }

    private void getCallLog() {
        String[] strFields = { android.provider.CallLog.Calls._ID,
                android.provider.CallLog.Calls.NUMBER,
                android.provider.CallLog.Calls.CACHED_NAME, };
        String strOrder = android.provider.CallLog.Calls.DATE + " DESC";
        final Cursor cursorCall = getContentResolver().query(
                android.provider.CallLog.Calls.CONTENT_URI, strFields,
                null, null, strOrder);
        AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
        dialog.setTitle(R.string.select_from_call_log);
        android.content.DialogInterface.OnClickListener listener =
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int item) {
                cursorCall.moveToPosition(item);
                String callLogNumber = cursorCall.getString(
                        cursorCall.getColumnIndex(android.provider.CallLog.Calls.NUMBER));
                String existingNumber = mEditText.getText().toString();
                existingNumber = (existingNumber == null || mEditText.length() == 0 ||
                        existingNumber.endsWith(";") || existingNumber.endsWith(",")) ?
                        existingNumber : existingNumber + ";";
                mEditText.setText(existingNumber +
                        PhoneNumberUtils.stripSeparators(callLogNumber));
                mEditText.setSelection(mEditText.getText().length());
                cursorCall.close();
                return;
            }
        };
        dialog.setCursor(cursorCall, listener, android.provider.CallLog.Calls.NUMBER);
        mAlertDialog =  dialog.create();
        mAlertDialog.setCanceledOnTouchOutside(false);
        mAlertDialog.show();
    }

    private void hideInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (data == null) {
            Log.w(TAG, "Data is null from intent" );
            return;
        }

        if (resultCode == Activity.RESULT_OK) {
            Uri contactData = data.getData();
            Cursor c = managedQuery(contactData, null, null, null, null);
            if (c.moveToFirst()) {
                final String id = c.getString(c.getColumnIndexOrThrow(
                        ContactsContract.Contacts._ID));
                final String hasPhone =
                        c.getString(c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER));
                String number = "";
                if (hasPhone.equalsIgnoreCase("1")) {
                    Cursor phones = getContentResolver().query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + id,
                            null, null);
                    phones.moveToFirst();
                    number = phones.getString(phones.getColumnIndex("data1"));
                    String existingNumber = mEditText.getText().toString();
                    existingNumber = (existingNumber == null || mEditText.length() == 0 ||
                            existingNumber.endsWith(";") || existingNumber.endsWith(",")) ?
                            existingNumber : existingNumber + ";";
                    mEditText.setText(existingNumber + PhoneNumberUtils.stripSeparators(number));
                    mEditText.setSelection(mEditText.getText().length());
                }
            }
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                hideInputMethod(getCurrentFocus());
                finishActivity(ACTIVITY_REQUEST_CONTACT_PICK);
                finish();
                return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        hideInputMethod(getCurrentFocus());
        unRegisterCallStateListener();
        super.onDestroy();
    }

    private final class ConfURIDialerPhoneStateListener extends PhoneStateListener {

        private int mPhoneCallState;

        public ConfURIDialerPhoneStateListener(ConfURIDialer confURIDialer) {
            Log.v(TAG, "ConfURIDialerPhoneStateListener() invoked.");
            mPhoneCallState = -1;
        }

        @Override
        public void onCallStateChanged(int state, String ignored) {
            if (mPhoneCallState == -1) {
                mPhoneCallState = state;
            }
            Log.d(TAG, "PhoneStateListener new state: " + state + " old state: " + mPhoneCallState);
            if (state == TelephonyManager.CALL_STATE_IDLE && state != mPhoneCallState) {
                mPhoneCallState = state;
                //PhoneState idle, clear ConfURIDialer if it's for add participant.
                if (mIsAddParticipants) {
                    mStartCallButton.setText(R.string.button_start_conference_call);
                    mEditText.setHint(R.string.add_uri_list_to_dial);
                    hideInputMethod(getCurrentFocus());
                    setTitle(R.string.applicationLabel);
                    mIsAddParticipants = false;
                    mIsInCall = false;
                    finishActivity(ACTIVITY_REQUEST_CONTACT_PICK);
                    finish();
                }
            }
        }
    }

    private boolean checkPermissions(int permission) {
        if (permission < 0 && permission >= permissionsList.length) {
            Log.e(TAG, "Invalid permission request");
            return false;
        }
        if (checkSelfPermission(permissionsList[permission]) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] { permissionsList[permission] }, permission);
            return false;
        }
        return true;
    }

    private void displayStartCall() {
        if (mIsAddParticipants && mIsInCall) {
            mStartCallButton.setText(R.string.button_add_participant);
            mEditText.setHint(R.string.add_recipient_to_add_participant);
            setTitle(R.string.applicationLabel_add_participant);
            mStartCallButton.setEnabled(false);
            mStartCallButton.setBackgroundColor(Color.parseColor("#C1C1C1"));
        } else {
            mStartCallButton.setEnabled(true);
        }
        mStartCallButton.setVisibility(View.VISIBLE);
    }

    private void displayVideoConf() {
        final boolean isVideoConfUriDialEnabled = this.getResources().getBoolean(
                R.bool.video_conference_uri_call_enabled);
        // Show video call buttton if video calling is enabled and not in add participant mode.
        if (isVideoConfUriDialEnabled && isVideoTelephonyAvailable() &&
                !(mIsAddParticipants && mIsInCall)) {
            mVideoCallButton.setEnabled(true);
            mVideoCallButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int reqCode, String permissions[], int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult: " + reqCode);
        if (grantResults.length <= 0) {
            Log.e(TAG, "onRequestPermissionsResult: incorrect grantResults length");
            return;
        }
        if (reqCode == REQUEST_READ_PHONE_STATE ||
                reqCode == REQUEST_CALL_PHONE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mIsInCall = isInCall(this);
                displayStartCall();
                displayVideoConf();
            } else {
                Log.d(TAG, "User denied the permissions request. Closing activity");
                Toast.makeText(
                        this, "The feature is disabled as required permission(s) not granted",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (reqCode == REQUEST_READ_CALL_LOG
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCallLog();
        } else if (reqCode == REQUEST_READ_CONTACTS
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(INTENT_PICK_ACTION, Contacts.CONTENT_URI);
            startActivityForResult(intent,ACTIVITY_REQUEST_CONTACT_PICK);
        }
    }
}
