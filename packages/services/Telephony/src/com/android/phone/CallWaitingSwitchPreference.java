package com.android.phone;

import static com.android.phone.TimeConsumingPreferenceActivity.EXCEPTION_ERROR;
import static com.android.phone.TimeConsumingPreferenceActivity.FDN_CHECK_FAILURE;
import static com.android.phone.TimeConsumingPreferenceActivity.RESPONSE_ERROR;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.preference.SwitchPreference;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.telephony.Phone;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CallWaitingSwitchPreference extends SwitchPreference {
    private static final String LOG_TAG = "CallWaitingSwitchPreference";
    private static final int DELAY_MILLIS_FOR_USSD = 1000;
    private final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private final MyHandler mHandler = new MyHandler();
    private Phone mPhone;
    private TimeConsumingPreferenceListener mTcpListener;
    private ScheduledExecutorService mExecutor;
    private TelephonyManager mTelephonyManager;
    private boolean mIsDuringUpdateProcess = false;
    private int mUpdateStatus = TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR;
    private int mQueryStatus = TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR;
    private boolean mUssdMode = false;

    public CallWaitingSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public CallWaitingSwitchPreference(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.switchPreferenceStyle);
    }

    public CallWaitingSwitchPreference(Context context) {
        this(context, null);
    }

    /* package */ void init(
            TimeConsumingPreferenceListener listener, boolean skipReading, Phone phone) {
        mPhone = phone;
        mTcpListener = listener;
        mExecutor = Executors.newSingleThreadScheduledExecutor();
        mTelephonyManager = getContext().getSystemService(
                TelephonyManager.class).createForSubscriptionId(phone.getSubId());
        CarrierConfigManager configManager = getContext().getSystemService(
                CarrierConfigManager.class);
        PersistableBundle bundle = configManager.getConfigForSubId(phone.getSubId());
        mUssdMode = (bundle != null) ? bundle.getBoolean(
                CarrierConfigManager.KEY_USE_CALL_WAITING_USSD_BOOL, false) : false;

        if (!skipReading) {
            Log.d(LOG_TAG, "init getCallWaitingStatus");
            mTelephonyManager.getCallWaitingStatus(mExecutor, this::queryStatusCallBack);
            if (mTcpListener != null) {
                mTcpListener.onStarted(this, true);
            }
        }
    }

    private void queryStatusCallBack(int result) {
        Log.d(LOG_TAG, "queryStatusCallBack: CW state " + result);
        mQueryStatus = result;
        mHandler.sendMessage(mHandler.obtainMessage(MyHandler.MESSAGE_UPDATE_CALL_WAITING));
    }

    private void updateStatusCallBack(int result) {
        Log.d(LOG_TAG, "updateStatusCallBack: CW state " + result + ", and re get");
        mUpdateStatus = result;
        if (mUssdMode) {
            Log.d(LOG_TAG, "updateStatusCallBack: USSD mode needs to wait 1s since Framework"
                    + " has the limitation");
            Consumer<Integer> resultListener = this::queryStatusCallBack;
            try {
                mExecutor.schedule(new Runnable() {
                    @Override
                    public void run() {
                        mTelephonyManager.getCallWaitingStatus(mExecutor, resultListener);
                    }
                }, DELAY_MILLIS_FOR_USSD, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                Log.d(LOG_TAG, "Exception while waiting: " + e);
            }
        } else {
            mTelephonyManager.getCallWaitingStatus(mExecutor, this::queryStatusCallBack);
        }
    }

    @Override
    protected void onClick() {
        super.onClick();
        mTelephonyManager.setCallWaitingEnabled(isChecked(), mExecutor, this::updateStatusCallBack);
        if (mTcpListener != null) {
            mIsDuringUpdateProcess = true;
            mTcpListener.onStarted(this, false);
        }
    }

    private class MyHandler extends Handler {
        static final int MESSAGE_UPDATE_CALL_WAITING = 0;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_UPDATE_CALL_WAITING:
                    updateUi();
                    break;
            }
        }

        private void updateUi() {
            if (mTcpListener != null) {
                if (mIsDuringUpdateProcess) {
                    mTcpListener.onFinished(CallWaitingSwitchPreference.this, false);
                } else {
                    mTcpListener.onFinished(CallWaitingSwitchPreference.this, true);
                }
            }

            if (mQueryStatus != TelephonyManager.CALL_WAITING_STATUS_ENABLED
                    && mQueryStatus != TelephonyManager.CALL_WAITING_STATUS_DISABLED
                    && mQueryStatus != TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR) {
                Log.d(LOG_TAG, "handleGetCallWaitingResponse: Exception:" + mQueryStatus);
                int error = EXCEPTION_ERROR;
                switch (mQueryStatus) {
                    case TelephonyManager.CALL_WAITING_STATUS_FDN_CHECK_FAILURE:
                        error = FDN_CHECK_FAILURE;
                        break;
                    default:
                        error = EXCEPTION_ERROR;
                        break;
                }
                if (mTcpListener != null) {
                    mTcpListener.onError(CallWaitingSwitchPreference.this, error);
                }
            } else if (mQueryStatus == TelephonyManager.CALL_WAITING_STATUS_UNKNOWN_ERROR
                    || (mIsDuringUpdateProcess && (
                    mUpdateStatus != TelephonyManager.CALL_WAITING_STATUS_ENABLED
                            && mUpdateStatus != TelephonyManager.CALL_WAITING_STATUS_DISABLED))) {
                Log.d(LOG_TAG, "handleSetCallWaitingResponse: Exception");
                if (mTcpListener != null) {
                    mTcpListener.onError(CallWaitingSwitchPreference.this, RESPONSE_ERROR);
                }
            } else {
                if (mQueryStatus == TelephonyManager.CALL_WAITING_STATUS_ENABLED) {
                    setChecked(true);
                } else {
                    setChecked(false);
                }
            }
            mIsDuringUpdateProcess = false;
        }
    }
}
