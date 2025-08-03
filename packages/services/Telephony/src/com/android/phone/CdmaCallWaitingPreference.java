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

package com.android.phone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;

public class CdmaCallWaitingPreference extends Preference {
    private static final String LOG_TAG = "CdmaCallWaitingPreference";
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private Context mContext;
    private Phone mPhone;
    private TimeConsumingPreferenceListener mTcpListener;
    private MyHandler mHandler = new MyHandler();
    private boolean mIsActionAvailable = true;

    public CdmaCallWaitingPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
    }

    public CdmaCallWaitingPreference(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.preferenceStyle);
    }

    public CdmaCallWaitingPreference(Context context) {
        this(context, null);
    }

    public void init(TimeConsumingPreferenceListener listener, Phone phone) {
        mPhone = phone;
        mTcpListener = listener;
        Log.d(LOG_TAG, "phone id= " + mPhone.getPhoneId());
        mPhone.getCallWaiting(mHandler.obtainMessage(MyHandler.MESSAGE_GET_CALL_WAITING,
                    MyHandler.MESSAGE_GET_CALL_WAITING, MyHandler.MESSAGE_GET_CALL_WAITING));
        if (mTcpListener != null) {
            mTcpListener.onStarted(this, true);
        }
    }

    /**
     * Enables this preference if Call waiting is available in the platform. If not, this will
     * override all attempts to enable the preference from the associated
     * TimeConsumingPreferenceActivity.
     */
    public void setActionAvailable(boolean isAvailable) {
        mIsActionAvailable = isAvailable;
        super.setEnabled(mIsActionAvailable);
    }

    @Override
    public void onClick() {
        super.onClick();

        AlertDialog.Builder builder = FrameworksUtils.makeAlertDialogBuilder(mContext);
        builder.setTitle(mContext.getText(R.string.cdma_call_waiting));
        builder.setMessage(mContext.getText(R.string.enable_cdma_call_waiting_setting));
        builder.setPositiveButton(R.string.enable_cdma_cw, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                mPhone.setCallWaiting(true,
                        mHandler.obtainMessage(MyHandler.MESSAGE_SET_CALL_WAITING));
                if (mTcpListener != null) {
                    mTcpListener.onStarted(CdmaCallWaitingPreference.this, false);
                }
            }
        });
        builder.setNegativeButton(R.string.disable_cdma_cw, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                mPhone.setCallWaiting(false,
                        mHandler.obtainMessage(MyHandler.MESSAGE_SET_CALL_WAITING));
                if (mTcpListener != null) {
                    mTcpListener.onStarted(CdmaCallWaitingPreference.this, false);
                }
            }
        });
        builder.create().show();
    }

    @Override
    public void setEnabled(boolean enabled) {
        // If this action is currently disabled due to configuration changes, do not allow anything
        // to enable it.
        if (!mIsActionAvailable) return;
        super.setEnabled(enabled);
    }

    private class MyHandler extends Handler {
        static final int MESSAGE_GET_CALL_WAITING = 0;
        static final int MESSAGE_SET_CALL_WAITING = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_CALL_WAITING:
                    handleGetCallWaitingResponse(msg);
                    break;
                case MESSAGE_SET_CALL_WAITING:
                    handleSetCallWaitingResponse(msg);
                    break;
            }
        }

        private void handleGetCallWaitingResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (mTcpListener != null) {
                if (msg.arg2 == MESSAGE_SET_CALL_WAITING) {
                    mTcpListener.onFinished(CdmaCallWaitingPreference.this, false);
                } else {
                    mTcpListener.onFinished(CdmaCallWaitingPreference.this, true);
                }
            }

            if (ar.exception instanceof CommandException) {
                if (DBG) {
                    Log.d(LOG_TAG, "handleGetCallWaitingResponse: CommandException=" +
                            ar.exception);
                }
                if (mTcpListener != null) {
                    mTcpListener.onException(CdmaCallWaitingPreference.this,
                                             (CommandException)ar.exception);
                }
            } else if (ar.userObj instanceof Throwable || ar.exception != null) {
                if (DBG) {
                    Log.d(LOG_TAG, "handleGetCallWaitingResponse: Exception" + ar.exception);
                }
                if (mTcpListener != null) {
                    mTcpListener.onError(CdmaCallWaitingPreference.this,
                                         TimeConsumingPreferenceActivity.RESPONSE_ERROR);
                }
            } else {
                if (DBG) {
                    Log.d(LOG_TAG, "handleGetCallWaitingResponse: CW state successfully queried.");
                }
                int[] cwArray = (int[])ar.result;
                if (cwArray == null) {
                    if (mTcpListener != null) {
                        mTcpListener.onError(CdmaCallWaitingPreference.this,
                                             TimeConsumingPreferenceActivity.RESPONSE_ERROR);
                    }
                    return;
                }

                try {
                    if (cwArray[0] == CommandsInterface.SS_STATUS_UNKNOWN) {
                        setSummary("");
                    } else if(cwArray[0] == 1) {
                        setSummary(mContext.getString(R.string.cdma_call_waiting_in_ims_on));
                    } else if(cwArray[0] == 0) {
                        setSummary(mContext.getString(R.string.cdma_call_waiting_in_ims_off));
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    setSummary("");
                    Log.e(LOG_TAG, "handleGetCallWaitingResponse: improper result: err ="
                            + e.getMessage());
                }
            }
        }

        private void handleSetCallWaitingResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (ar.exception != null) {
                if (DBG) {
                    Log.d(LOG_TAG, "handleSetCallWaitingResponse: ar.exception=" + ar.exception);
                }
            }

            if (ar.result != null) {
                int arr = (int)ar.result;
                if (arr == CommandsInterface.SS_STATUS_UNKNOWN) {
                    Log.d(LOG_TAG, "handleSetCallWaitingResponse: no need to re get in CDMA");
                    mTcpListener.onFinished(CdmaCallWaitingPreference.this, false);
                    return;
                }
            }

            if (DBG) Log.d(LOG_TAG, "handleSetCallWaitingResponse: re get");
            mPhone.getCallWaiting(obtainMessage(MESSAGE_GET_CALL_WAITING,
                        MESSAGE_SET_CALL_WAITING, MESSAGE_SET_CALL_WAITING, ar.exception));
        }
    }
}
