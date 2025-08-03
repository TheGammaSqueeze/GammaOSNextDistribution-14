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

package com.android.server.telecom.voip;

import static android.telecom.CallException.CODE_OPERATION_TIMED_OUT;

import android.os.OutcomeReceiver;
import android.telecom.TelecomManager;
import android.telecom.CallException;
import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.telecom.flags.Flags;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

public class TransactionManager {
    private static final String TAG = "VoipCallTransactionManager";
    private static final int TRANSACTION_HISTORY_SIZE = 20;
    private static TransactionManager INSTANCE = null;
    private static final Object sLock = new Object();
    private final Queue<VoipCallTransaction> mTransactions;
    private final Deque<VoipCallTransaction> mCompletedTransactions;
    private VoipCallTransaction mCurrentTransaction;

    public interface TransactionCompleteListener {
        void onTransactionCompleted(VoipCallTransactionResult result, String transactionName);
        void onTransactionTimeout(String transactionName);
    }

    private TransactionManager() {
        mTransactions = new ArrayDeque<>();
        mCurrentTransaction = null;
        if (Flags.enableCallSequencing()) {
            mCompletedTransactions = new ArrayDeque<>();
        } else
            mCompletedTransactions = null;
    }

    public static TransactionManager getInstance() {
        synchronized (sLock) {
            if (INSTANCE == null) {
                INSTANCE = new TransactionManager();
            }
        }
        return INSTANCE;
    }

    @VisibleForTesting
    public static TransactionManager getTestInstance() {
        return new TransactionManager();
    }

    public void addTransaction(VoipCallTransaction transaction,
            OutcomeReceiver<VoipCallTransactionResult, CallException> receiver) {
        synchronized (sLock) {
            mTransactions.add(transaction);
        }
        transaction.setCompleteListener(new TransactionCompleteListener() {
            @Override
            public void onTransactionCompleted(VoipCallTransactionResult result,
                    String transactionName) {
                Log.i(TAG, String.format("transaction %s completed: with result=[%d]",
                        transactionName, result.getResult()));
                try {
                    if (result.getResult() == TelecomManager.TELECOM_TRANSACTION_SUCCESS) {
                        receiver.onResult(result);
                    } else {
                        receiver.onError(
                                new CallException(result.getMessage(),
                                        result.getResult()));
                    }
                } catch (Exception e) {
                    Log.e(TAG, String.format("onTransactionCompleted: Notifying transaction result"
                            + " %s resulted in an Exception.", result), e);
                }
                finishTransaction();
            }

            @Override
            public void onTransactionTimeout(String transactionName){
                Log.i(TAG, String.format("transaction %s timeout", transactionName));
                try {
                    receiver.onError(new CallException(transactionName + " timeout",
                            CODE_OPERATION_TIMED_OUT));
                } catch (Exception e) {
                    Log.e(TAG, String.format("onTransactionTimeout: Notifying transaction "
                            + " %s resulted in an Exception.", transactionName), e);
                }
                finishTransaction();
            }
        });

        startTransactions();
    }

    private void startTransactions() {
        synchronized (sLock) {
            if (mTransactions.isEmpty()) {
                // No transaction waiting for process
                return;
            }

            if (mCurrentTransaction != null) {
                // Ongoing transaction
                return;
            }
            mCurrentTransaction = mTransactions.poll();
        }
        mCurrentTransaction.start();
    }

    private void finishTransaction() {
        synchronized (sLock) {
            if (mCurrentTransaction != null) {
                addTransactionToHistory(mCurrentTransaction);
                mCurrentTransaction = null;
            }
        }
        startTransactions();
    }

    @VisibleForTesting
    public void clear() {
        List<VoipCallTransaction> pendingTransactions;
        synchronized (sLock) {
            pendingTransactions = new ArrayList<>(mTransactions);
        }
        for (VoipCallTransaction t : pendingTransactions) {
            t.finish(new VoipCallTransactionResult(VoipCallTransactionResult.RESULT_FAILED,
                    "clear called"));
        }
    }

    private void addTransactionToHistory(VoipCallTransaction t) {
        if (!Flags.enableCallSequencing()) return;

        mCompletedTransactions.add(t);
        if (mCompletedTransactions.size() > TRANSACTION_HISTORY_SIZE) {
            mCompletedTransactions.poll();
        }
    }

    /**
     * Called when the dumpsys is created for telecom to capture the current state.
     */
    public void dump(IndentingPrintWriter pw) {
        if (!Flags.enableCallSequencing()) {
            pw.println("<<Flag not enabled>>");
            return;
        }
        synchronized (sLock) {
            pw.println("Pending Transactions:");
            pw.increaseIndent();
            for (VoipCallTransaction t : mTransactions) {
                printPendingTransactionStats(t, pw);
            }
            pw.decreaseIndent();

            pw.println("Ongoing Transaction:");
            pw.increaseIndent();
            if (mCurrentTransaction != null) {
                printPendingTransactionStats(mCurrentTransaction, pw);
            }
            pw.decreaseIndent();

            pw.println("Completed Transactions:");
            pw.increaseIndent();
            for (VoipCallTransaction t : mCompletedTransactions) {
                printCompleteTransactionStats(t, pw);
            }
            pw.decreaseIndent();
        }
    }

    /**
     * Recursively print the pending {@link VoipCallTransaction} stats for logging purposes.
     * @param t The transaction that stats should be printed for
     * @param pw The IndentingPrintWriter to print the result to
     */
    private void printPendingTransactionStats(VoipCallTransaction t, IndentingPrintWriter pw) {
        VoipCallTransaction.Stats s = t.getStats();
        if (s == null) {
            pw.println(String.format(Locale.getDefault(), "%s: <NO STATS>", t.mTransactionName));
            return;
        }
        pw.println(String.format(Locale.getDefault(),
                "[%s] %s: (result=[%s]), (created -> now : [%+d] mS),"
                        + " (created -> started : [%+d] mS),"
                        + " (started -> now : [%+d] mS)",
                s.addedTimeStamp, t.mTransactionName, parseTransactionResult(s),
                s.measureTimeSinceCreatedMs(), s.measureCreatedToStartedMs(),
                s.measureTimeSinceStartedMs()));

        if (t.mSubTransactions == null || t.mSubTransactions.isEmpty()) {
            return;
        }
        pw.increaseIndent();
        for (VoipCallTransaction subTransaction : t.mSubTransactions) {
            printPendingTransactionStats(subTransaction, pw);
        }
        pw.decreaseIndent();
    }

    /**
     * Recursively print the complete Transaction stats for logging purposes.
     * @param t The transaction that stats should be printed for
     * @param pw The IndentingPrintWriter to print the result to
     */
    private void printCompleteTransactionStats(VoipCallTransaction t, IndentingPrintWriter pw) {
        VoipCallTransaction.Stats s = t.getStats();
        if (s == null) {
            pw.println(String.format(Locale.getDefault(), "%s: <NO STATS>", t.mTransactionName));
            return;
        }
        pw.println(String.format(Locale.getDefault(),
                "[%s] %s: (result=[%s]), (created -> started : [%+d] mS), "
                        + "(started -> completed : [%+d] mS)",
                s.addedTimeStamp, t.mTransactionName, parseTransactionResult(s),
                s.measureCreatedToStartedMs(), s.measureStartedToCompletedMs()));

        if (t.mSubTransactions == null || t.mSubTransactions.isEmpty()) {
            return;
        }
        pw.increaseIndent();
        for (VoipCallTransaction subTransaction : t.mSubTransactions) {
            printCompleteTransactionStats(subTransaction, pw);
        }
        pw.decreaseIndent();
    }

    private String parseTransactionResult(VoipCallTransaction.Stats s) {
        if (s.isTimedOut()) return "TIMED OUT";
        if (s.getTransactionResult() == null) return "PENDING";
        if (s.getTransactionResult().getResult() == VoipCallTransactionResult.RESULT_SUCCEED) {
            return "SUCCESS";
        }
        return s.getTransactionResult().toString();
    }
}
