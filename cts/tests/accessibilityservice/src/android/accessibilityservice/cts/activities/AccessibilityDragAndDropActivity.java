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

package android.accessibilityservice.cts.activities;

import android.accessibilityservice.cts.R;
import android.content.ClipData;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

public class AccessibilityDragAndDropActivity extends AccessibilityTestActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.accessibility_drag_and_drop);
        final TextView text = findViewById(R.id.source);

        View.AccessibilityDelegate delegate = new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_START);
                super.onInitializeAccessibilityNodeInfo(host, info);
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                boolean result = super.performAccessibilityAction(host, action, args);
                if (action == android.R.id.accessibilityActionDragStart) {
                    final ClipData clipData = ClipData.newPlainText("Text", text.getText());
                    View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder();
                    return host.startDragAndDrop(clipData, shadowBuilder, null,
                            View.DRAG_FLAG_ACCESSIBILITY_ACTION);
                }
                return result;
            }
        };

        text.setAccessibilityDelegate(delegate);
        final TextView target = findViewById(R.id.target);

        View.OnDragListener dragListener = new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                    case DragEvent.ACTION_DRAG_ENTERED:
                    case DragEvent.ACTION_DRAG_LOCATION:
                    case DragEvent.ACTION_DRAG_EXITED:
                    case DragEvent.ACTION_DRAG_ENDED:
                        return true;
                    case DragEvent.ACTION_DROP:
                        ((TextView) v).setText(event.getClipData().getItemAt(0).getText());
                        return true;

                    default:
                        break;
                }
                return false;
            }
        };
        target.setOnDragListener(dragListener);
    }
}
