package com.android.tv.samples.sampletunertvinput;

import android.app.Activity;
import android.content.Intent;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputService;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.dvr.DvrPlayback;
import android.media.tv.tuner.dvr.DvrSettings;
import android.media.tv.tuner.filter.Filter;
import android.media.tv.tuner.filter.FilterCallback;
import android.media.tv.tuner.filter.FilterEvent;
import android.media.tv.tuner.filter.SectionEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.android.tv.common.util.Clock;
import com.android.tv.testing.data.ChannelInfo;
import com.android.tv.testing.data.ChannelUtils;
import com.android.tv.testing.data.ProgramInfo;
import com.android.tv.testing.data.ProgramUtils;

import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Setup activity for SampleTunerTvInput */
public class SampleTunerTvInputSetupActivity extends Activity {
    private static final String TAG = "SampleTunerTvInput";
    private static final boolean DEBUG = true;

    private static final boolean USE_DVR = true;
    private static final String SETUP_INPUT_FILE_NAME = "setup.ts";

    private Tuner mTuner;
    private DvrPlayback mDvr;
    private Filter mSectionFilter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initTuner();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mTuner != null) {
            mTuner.close();
            mTuner = null;
        }
        if (mDvr != null) {
            mDvr.close();
            mDvr = null;
        }
        if (mSectionFilter != null) {
            mSectionFilter.close();
            mSectionFilter = null;
        }
    }

    private void setChannel(byte[] sectionData) {
        SampleTunerTvInputSectionParser.TvctChannelInfo channelInfo =
                SampleTunerTvInputSectionParser.parseTvctSection(sectionData);

        String channelNumber = "";
        String channelName = "";

        if(channelInfo == null) {
            Log.e(TAG, "Did not receive channel description from parser");
        } else {
            channelNumber = String.format(Locale.US, "%d-%d", channelInfo.getMajorChannelNumber(),
                    channelInfo.getMinorChannelNumber());
            channelName = channelInfo.getChannelName();
        }

        ChannelInfo channel =
                new ChannelInfo.Builder()
                        .setNumber(channelNumber)
                        .setName(channelName)
                        .setLogoUrl(
                                ChannelInfo.getUriStringForChannelLogo(this, 100))
                        .setOriginalNetworkId(1)
                        .setVideoWidth(640)
                        .setVideoHeight(480)
                        .setAudioChannel(2)
                        .setAudioLanguageCount(1)
                        .setHasClosedCaption(false)
                        .build();

        Intent intent = getIntent();
        String inputId = intent.getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        ChannelUtils.updateChannels(this, inputId, Collections.singletonList(channel));
        ProgramUtils.updateProgramForAllChannelsOf(this, inputId, Clock.SYSTEM,
                TimeUnit.DAYS.toMillis(1));

        setResult(Activity.RESULT_OK);
        finish();
    }

    private FilterCallback sectionFilterCallback() {
        return new FilterCallback() {
            @Override
            public void onFilterEvent(Filter filter, FilterEvent[] events) {
                if (DEBUG) {
                    Log.d(TAG, "onFilterEvent setup section, size=" + events.length);
                }
                for (int i = 0; i < events.length; i++) {
                    if (DEBUG) {
                        Log.d(TAG, "events[" + i + "] is "
                                + events[i].getClass().getSimpleName());
                    }
                    if (events[i] instanceof SectionEvent) {
                        SectionEvent sectionEvent = (SectionEvent) events[i];
                        int dataSize = (int)sectionEvent.getDataLengthLong();
                        if (DEBUG) {
                            Log.d(TAG, "section dataSize:" + dataSize);
                        }

                        byte[] data = new byte[dataSize];
                        filter.read(data, 0, dataSize);

                        setChannel(data);
                    }
                }
            }

            @Override
            public void onFilterStatusChanged(Filter filter, int status) {
                if (DEBUG) {
                    Log.d(TAG, "onFilterStatusChanged setup section, status=" + status);
                }
            }
        };
    }

    private void initTuner() {
        mTuner = new Tuner(getApplicationContext(), null,
                TvInputService.PRIORITY_HINT_USE_CASE_TYPE_LIVE);
        Handler handler = new Handler(Looper.myLooper());

        mSectionFilter = SampleTunerTvInputUtils.createSectionFilter(mTuner, handler,
                sectionFilterCallback());
        mSectionFilter.start();

        // Dvr Playback can be used to read a file instead of relying on physical tuner
        if (USE_DVR) {
            mDvr = SampleTunerTvInputUtils.configureDvrPlayback(mTuner, handler,
                    DvrSettings.DATA_FORMAT_TS);
            SampleTunerTvInputUtils.readFilePlaybackInput(getApplicationContext(), mDvr,
                    SETUP_INPUT_FILE_NAME);
            mDvr.start();
        } else {
            SampleTunerTvInputUtils.tune(mTuner, handler);
        }
    }

}
