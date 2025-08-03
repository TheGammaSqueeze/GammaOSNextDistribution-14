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

package com.google.android.sample.rcsclient;

import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.libraries.rcs.simpleclient.SimpleRcsClient.State;

import com.google.android.sample.rcsclient.util.ChatManager;
import com.google.android.sample.rcsclient.util.ChatProvider;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** An activity to show the contacts which UE ever chatted before. */
public class ContactListActivity extends AppCompatActivity {

    private static final String TAG = "TestRcsApp.ContactListActivity";
    private static final int RENDER_LISTVIEW = 1;
    private static final int SHOW_STATUS = 2;
    private static final long TIMEOUT_IN_MS = 10000L;
    private final ExecutorService mSingleThread = Executors.newSingleThreadExecutor();
    private TextView mTips;
    private Button mStartChatButton;
    private Handler mHandler;
    private SummaryObserver mSummaryObserver;
    private ArrayAdapter mAdapter;
    private ListView mListview;
    private State mState;
    private ArrayList<ContactAttributes> mContactList;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.contact_list);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        mContactList = new ArrayList<>();
        mTips = findViewById(R.id.tips);
        mListview = findViewById(R.id.listview);
        mStartChatButton = findViewById(R.id.start_chat_btn);
        mStartChatButton.setOnClickListener(view -> {
            Intent intent = new Intent(ContactListActivity.this, PhoneNumberActivity.class);
            ContactListActivity.this.startActivity(intent);
        });
        setButtonClickable(false);

        mHandler = new Handler() {
            public void handleMessage(Message message) {
                Log.i(TAG, "handleMessage:" + message.what);
                switch (message.what) {
                    case RENDER_LISTVIEW:
                        renderListView();
                        break;
                    case SHOW_STATUS:
                        mTips.setText(message.obj.toString());
                        break;
                    default:
                        Log.i(TAG, "unknown msg:" + message.what);
                }
            }
        };
        initListView();
        initSipDelegate();
        mSummaryObserver = new SummaryObserver(mHandler);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
        querySummaryData();
        getContentResolver().registerContentObserver(ChatProvider.SUMMARY_URI, false,
                mSummaryObserver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
        getContentResolver().unregisterContentObserver(mSummaryObserver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        dispose();
    }

    private void dispose() {
        int subId = SubscriptionManager.getDefaultSmsSubscriptionId();
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Log.e(TAG, "invalid subId:" + subId);
            return;
        }
        ChatManager chatManager = ChatManager.getInstance(this, subId);
        chatManager.deregister();
    }

    private void initListView() {
        Log.i(TAG, "initListView");

        mAdapter = new ArrayAdapter<ContactAttributes>(this,
                android.R.layout.simple_list_item_2,
                android.R.id.text1) {
            @Override
            public View getView(int pos, View convert, ViewGroup group) {
                View v = super.getView(pos, convert, group);
                TextView t1 = (TextView) v.findViewById(android.R.id.text1);
                TextView t2 = (TextView) v.findViewById(android.R.id.text2);
                t1.setText(getItem(pos).phoneNumber);
                t2.setText(getItem(pos).message);
                if (!getItem(pos).isRead) {
                    t1.setTypeface(null, Typeface.BOLD);
                    t2.setTypeface(null, Typeface.BOLD);
                }
                return v;
            }
        };
        mListview.setAdapter(mAdapter);
    }

    private void querySummaryData() {
        mSingleThread.execute(() -> {
            Cursor cursor = getContentResolver().query(ChatProvider.SUMMARY_URI,
                    new String[]{ChatProvider.SummaryColumns.REMOTE_PHONE_NUMBER,
                            ChatProvider.SummaryColumns.LATEST_MESSAGE,
                            ChatProvider.SummaryColumns.IS_READ}, null, null, null);

            mContactList.clear();
            while (cursor.moveToNext()) {
                String phoneNumber = getPhoneNumber(cursor);
                String latestMessage = getLatestMessage(cursor);
                boolean isRead = getIsRead(cursor);
                mContactList.add(new ContactAttributes(phoneNumber, latestMessage, isRead));
            }
            mHandler.sendMessage(mHandler.obtainMessage(RENDER_LISTVIEW));
            cursor.close();
        });
    }

    private void renderListView() {
        mAdapter.clear();
        mAdapter.addAll(mContactList);
    }

    private void setListViewClickable(boolean clickable) {
        if (clickable) {
            mListview.setOnItemClickListener((parent, view, position, id) -> {
                Intent intent = new Intent(ContactListActivity.this, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_REMOTE_PHONE_NUMBER,
                        mContactList.get(position).phoneNumber);
                ContactListActivity.this.startActivity(intent);
            });
        } else {
            mListview.setOnItemClickListener(null);
        }
    }

    private void initSipDelegate() {
        int subId = SubscriptionManager.getDefaultSmsSubscriptionId();
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Log.e(TAG, "invalid subId:" + subId);
            return;
        }
        Log.i(TAG, "initSipDelegate");
        ChatManager chatManager = ChatManager.getInstance(this, subId);
        chatManager.setRcsStateChangedCallback((oldState, newState) -> {
            //Show toast when provisioning or registration is done.
            mState = newState;
            String tips = "";
            String timeoutTips = "";
            switch (newState) {
                case PROVISIONING:
                    tips = ContactListActivity.this.getResources().getString(
                            R.string.start_provisioning);
                    mHandler.sendMessage(mHandler.obtainMessage(SHOW_STATUS, tips));
                    timeoutTips = ContactListActivity.this.getResources().getString(
                            R.string.provisioning_timeout);
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(SHOW_STATUS, timeoutTips),
                            TIMEOUT_IN_MS);
                    break;
                case REGISTERING:
                    tips = ContactListActivity.this.getResources().getString(
                            R.string.provisioning_done);
                    if (mHandler.hasMessages(SHOW_STATUS)) {
                        mHandler.removeMessages(SHOW_STATUS);
                    }
                    mHandler.sendMessage(mHandler.obtainMessage(SHOW_STATUS, tips));
                    timeoutTips = ContactListActivity.this.getResources().getString(
                            R.string.registration_timeout);
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(SHOW_STATUS, timeoutTips),
                            TIMEOUT_IN_MS);
                    break;
                case REGISTERED:
                    tips = ContactListActivity.this.getResources().getString(
                            R.string.registration_done);
                    if (mHandler.hasMessages(SHOW_STATUS)) {
                        mHandler.removeMessages(SHOW_STATUS);
                    }
                    mHandler.sendMessage(mHandler.obtainMessage(SHOW_STATUS, tips));
                    setButtonClickable(true);
                    setListViewClickable(true);
                    break;
                case NOT_REGISTERED:
                    tips = ContactListActivity.this.getResources().getString(
                            R.string.registration_failed);
                    mHandler.sendMessage(mHandler.obtainMessage(SHOW_STATUS, tips));
                    setButtonClickable(false);
                    setListViewClickable(false);
                    break;

                default:
                    Log.i(TAG, "unknown state:" + newState);
            }
        });
        chatManager.register();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }


    private String getPhoneNumber(Cursor cursor) {
        return cursor.getString(
                cursor.getColumnIndex(ChatProvider.SummaryColumns.REMOTE_PHONE_NUMBER));
    }

    private String getLatestMessage(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(ChatProvider.SummaryColumns.LATEST_MESSAGE));
    }

    private boolean getIsRead(Cursor cursor) {
        return 1 == cursor.getInt(cursor.getColumnIndex(ChatProvider.SummaryColumns.IS_READ));
    }

    private void setButtonClickable(boolean clickable) {
        if (clickable) {
            mStartChatButton.setAlpha(1);
            mStartChatButton.setClickable(true);
        } else {
            mStartChatButton.setAlpha(.5f);
            mStartChatButton.setClickable(false);
        }
    }

    class ContactAttributes {
        public String phoneNumber;
        public String message;
        public boolean isRead;

        ContactAttributes(String phoneNumber, String message, boolean isRead) {
            this.phoneNumber = phoneNumber;
            this.message = message;
            this.isRead = isRead;
        }
    }

    private class SummaryObserver extends ContentObserver {
        SummaryObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            querySummaryData();
        }
    }
}
