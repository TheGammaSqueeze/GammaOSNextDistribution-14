package com.android.tv.twopanelsettings.slices;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.SeekBarPreference;
import androidx.slice.core.SliceActionImpl;

/**
 * Slices version of SeekBarPreference.
 */
public class SliceSeekbarPreference extends SeekBarPreference implements HasSliceAction {
    private int mActionId;
    protected SliceActionImpl mAction;
    private SliceActionImpl mFollowupSliceAction;

    public SliceSeekbarPreference(Context context, SliceActionImpl action, int min, int max,
            int value) {
        super(context);
        mAction = action;
        update(min, max, value);
    }

    public SliceSeekbarPreference(Context context, AttributeSet attrs, SliceActionImpl action,
            int min, int max, int value) {
        super(context, attrs);
        mAction = action;
        update(min, max, value);
    }

    @Override
    public int getActionId() {
        return mActionId;
    }

    @Override
    public void setActionId(int actionId) {
        mActionId = actionId;
    }

    public SliceSeekbarPreference(Context context) {
        super(context);
    }

    public SliceSeekbarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public SliceActionImpl getSliceAction() {
        return mAction;
    }

    @Override
    public void setSliceAction(SliceActionImpl sliceAction) {
        mAction = sliceAction;
    }

    @Override
    public SliceActionImpl getFollowupSliceAction() {
        return mFollowupSliceAction;
    }

    @Override
    public void setFollowupSliceAction(SliceActionImpl sliceAction) {
        mFollowupSliceAction = sliceAction;
    }

    private void update(int min, int max, int value) {
        this.setMin(min);
        this.setMax(max);
        this.setValue(value);
    }
}
