/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tv.feedbackconsent;

import android.annotation.LayoutRes;
import android.annotation.StyleRes;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

public class TvFeedbackConsentInformationDialog extends Dialog {

    private static final String TAG = TvFeedbackConsentInformationDialog.class.getSimpleName();
    private final int mLayoutId;
    private final TvFeedbackConsentRecyclerViewAdapter mAdapter;

    public TvFeedbackConsentInformationDialog(@NonNull Context context,
                                              @StyleRes int themeResId,
                                              @LayoutRes int layoutId,
                                              List<String> recyclerViewContent) {
        super(context, themeResId);
        mLayoutId = layoutId;
        mAdapter = new TvFeedbackConsentRecyclerViewAdapter(recyclerViewContent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(LayoutInflater.from(getContext()).inflate(mLayoutId, null));
        setCancelable(true);

        setUpRecyclerView();
    }

    private void setUpRecyclerView() {
        RecyclerView mViewLogsContentRecycler = requireViewById(R.id.feedbackViewLogsDialogContent);
        mViewLogsContentRecycler.setAdapter(mAdapter);
        mViewLogsContentRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    protected final void updateRecyclerView(List<String> viewData) {
        if (mAdapter == null) {
            Log.w(TAG, "Cannot update recycler view before setting an adapter ");
            return;
        }
        mAdapter.updateRecyclerView(viewData);
        mAdapter.notifyDataSetChanged();
    }

    private static final class TvFeedbackConsentRecyclerViewAdapter extends
            RecyclerView.Adapter<TvFeedbackConsentRecyclerViewAdapter.ViewHolder> {

        private List<String> mViewData = new ArrayList<>(0);;

        TvFeedbackConsentRecyclerViewAdapter(List<String> viewData) {
            mViewData.addAll(viewData);
        }

        void updateRecyclerView(List<String> viewData) {
            mViewData.clear();
            mViewData.addAll(viewData);
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView textView;

            public ViewHolder(View v) {
                super(v);
                textView = (TextView) v.findViewById(R.id.textView);
            }

            public TextView getTextView() {
                return textView;
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.text_line_item, viewGroup, /* attachToRoot= */ false);

            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, int position) {
            viewHolder.getTextView().setText(mViewData.get(position));
        }

        @Override
        public int getItemCount() {
            return mViewData.size();
        }
    }
}
