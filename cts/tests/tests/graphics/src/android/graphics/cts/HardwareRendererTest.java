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

package android.graphics.cts;

import static android.graphics.cts.utils.LeakTest.runNotLeakingTest;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.HardwareBufferRenderer;
import android.graphics.HardwareRenderer;
import android.graphics.RenderNode;
import android.hardware.HardwareBuffer;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class HardwareRendererTest {

    @Test
    public void isDrawingEnabled_defaultsTrue() {
        assertThat(HardwareRenderer.isDrawingEnabled()).isTrue();
    }

    @Test
    public void setDrawingEnabled() {
        HardwareRenderer.setDrawingEnabled(false);

        assertThat(HardwareRenderer.isDrawingEnabled()).isFalse();

        HardwareRenderer.setDrawingEnabled(true);
        assertThat(HardwareRenderer.isDrawingEnabled()).isTrue();
    }

    @Test
    @LargeTest
    public void hardwareBufferRendererLeakTest() {
        HardwareBuffer buffer = HardwareBuffer.create(128, 128, HardwareBuffer.RGBA_8888,
                1, HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        HardwareBufferRenderer renderer = new HardwareBufferRenderer(buffer);
        RenderNode content = new RenderNode("leaktest");
        content.setPosition(0, 0, 128, 128);
        renderer.setContentRoot(content);

        Executor executor = Runnable::run;
        Consumer<HardwareBufferRenderer.RenderResult> resultListener = renderResult -> {
            renderResult.getFence().close();
        };

        runNotLeakingTest(() -> {
            Canvas canvas = content.beginRecording();
            canvas.drawColor(Color.RED);
            content.endRecording();
            renderer.obtainRenderRequest().draw(executor, resultListener);
        });
    }
}
