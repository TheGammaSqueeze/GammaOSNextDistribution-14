/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.server.wm.app;

import static android.server.wm.app.Components.UiScalingTestActivity.COMMAND_ADD_SUBVIEW;
import static android.server.wm.app.Components.UiScalingTestActivity.COMMAND_CLEAR_DEFAULT_VIEW;
import static android.server.wm.app.Components.UiScalingTestActivity.COMMAND_GET_RESOURCES_CONFIG;
import static android.server.wm.app.Components.UiScalingTestActivity.COMMAND_GET_SUBVIEW_SIZE;
import static android.server.wm.app.Components.UiScalingTestActivity.COMMAND_UPDATE_RESOURCES_CONFIG;
import static android.server.wm.app.Components.UiScalingTestActivity.KEY_COMMAND_SUCCESS;
import static android.server.wm.app.Components.UiScalingTestActivity.KEY_RESOURCES_CONFIG;
import static android.server.wm.app.Components.UiScalingTestActivity.KEY_SUBVIEW_ID;
import static android.server.wm.app.Components.UiScalingTestActivity.KEY_TEXT_SIZE;
import static android.server.wm.app.Components.UiScalingTestActivity.KEY_VIEW_SIZE;
import static android.server.wm.app.Components.UiScalingTestActivity.SUBVIEW_ID1;
import static android.server.wm.app.Components.UiScalingTestActivity.SUBVIEW_ID2;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.util.HashMap;
import java.util.Map;

public class UiScalingTestActivity extends TestActivity {
    private Map<String, Integer> mResIdMap = new HashMap<>();
    private Map<String, Integer> mViewIdMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.simple_ui_elements);
        mResIdMap.put(SUBVIEW_ID1, R.layout.compat_scale_subview1);
        mResIdMap.put(SUBVIEW_ID2, R.layout.compat_scale_subview2);
        mViewIdMap.put(SUBVIEW_ID1, R.id.compat_scale_subview1);
        mViewIdMap.put(SUBVIEW_ID2, R.id.compat_scale_subview2);
    }

    @Override
    public void handleCommand(String command, Bundle data) {
        switch (command) {
            case COMMAND_CLEAR_DEFAULT_VIEW: {
                RelativeLayout view = findViewById(R.id.simple_ui_elements);
                view.removeAllViews();
                reply(COMMAND_CLEAR_DEFAULT_VIEW);
                break;
            }
            case COMMAND_GET_RESOURCES_CONFIG: {
                Bundle replyData = new Bundle();
                replyData.putParcelable(KEY_RESOURCES_CONFIG, getResources().getConfiguration());
                reply(COMMAND_GET_RESOURCES_CONFIG, replyData);
                break;
            }
            case COMMAND_UPDATE_RESOURCES_CONFIG: {
                Configuration config = data.getParcelable(KEY_RESOURCES_CONFIG,
                        Configuration.class);
                Bundle replyData = new Bundle();
                if (config != null) {
                    getResources().updateConfiguration(config, null);
                    replyData.putBoolean(KEY_COMMAND_SUCCESS, true);
                }
                reply(COMMAND_UPDATE_RESOURCES_CONFIG, replyData);
                break;
            }
            case COMMAND_ADD_SUBVIEW: {
                String subviewId = data.getString(KEY_SUBVIEW_ID);
                addContentView(getLayoutInflater().inflate(mResIdMap.get(subviewId), null),
                        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
                Bundle replyData = new Bundle();
                View view = findViewById(mViewIdMap.get(subviewId));
                replyData.putBoolean(KEY_COMMAND_SUCCESS, view != null);
                reply(COMMAND_ADD_SUBVIEW, replyData);
                break;
            }
            case COMMAND_GET_SUBVIEW_SIZE: {
                String subviewId = data.getString(KEY_SUBVIEW_ID);
                View view = findViewById(mViewIdMap.get(subviewId));
                Bundle replyData = new Bundle();
                replyData.putBoolean(KEY_COMMAND_SUCCESS, view != null);
                if (view != null) {
                    View v = view.findViewById(R.id.compat_view);
                    replyData.putParcelable(KEY_VIEW_SIZE,
                            new Rect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom()));
                    View t = view.findViewById(R.id.compat_text);
                    replyData.putParcelable(KEY_TEXT_SIZE,
                            new Rect(t.getLeft(), t.getTop(), t.getRight(), t.getBottom()));
                }
                reply(COMMAND_GET_SUBVIEW_SIZE, replyData);
                break;
            }
            default:
                super.handleCommand(command, data);
        }
    }
}
