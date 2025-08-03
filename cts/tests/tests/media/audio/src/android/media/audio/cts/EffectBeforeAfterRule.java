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

package android.media.audio.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.media.audiofx.AudioEffect;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.ExternalResource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple before and after rule for audio effect tests
 */
final class EffectBeforeAfterRule extends ExternalResource {
    private static final String TAG = EffectBeforeAfterRule.class.getSimpleName();

    private static final int CONTROL_PRIORITY = 100;

    private Map<UUID, Boolean> mOriginalEffectState = new HashMap<>();

    /**
     * All potential effects under test are disabled at setup.
     * Initial state is backuped, restored in tearDown
     * @throws Exception
     */
    @Override
    protected void before() {

        final UUID[] effectTypes = {
                AudioEffect.EFFECT_TYPE_BASS_BOOST,
                AudioEffect.EFFECT_TYPE_EQUALIZER,
                AudioEffect.EFFECT_TYPE_VIRTUALIZER,
                AudioEffect.EFFECT_TYPE_PRESET_REVERB,
                AudioEffect.EFFECT_TYPE_ENV_REVERB,
        };
        for (UUID effectType : effectTypes) {
            try {
                if (AudioEffect.isEffectTypeAvailable(effectType)) {
                    AudioEffect effect = new AudioEffect(effectType,
                            AudioEffect.EFFECT_TYPE_NULL,
                            CONTROL_PRIORITY,
                            0);
                    assertTrue("effect does not have control", effect.hasControl());
                    mOriginalEffectState.put(effectType, effect.getEnabled());

                    effect.setEnabled(false);
                    assertFalse("Could not disable effect", effect.getEnabled());
                    effect.release();
                }
            } catch (IllegalStateException e) {
            } catch (IllegalArgumentException e) {
            } catch (UnsupportedOperationException e) {
            } catch (RuntimeException e) {
                assumeFalse("skipping for instant",
                        InstrumentationRegistry.getInstrumentation()
                                        .getContext().getPackageManager().isInstantApp());
            }
        }
    }

    @Override
    protected void after() {
        for (Map.Entry<UUID, Boolean> entry : mOriginalEffectState.entrySet()) {
            try {
                AudioEffect effect = new AudioEffect(entry.getKey(),
                        AudioEffect.EFFECT_TYPE_NULL,
                        CONTROL_PRIORITY,
                        0);
                assertTrue("effect does not have control", effect.hasControl());
                effect.setEnabled(entry.getValue());
                effect.release();
            } catch (IllegalStateException e) {
            } catch (IllegalArgumentException e) {
            } catch (UnsupportedOperationException e) {
            }
        }
    }
}
