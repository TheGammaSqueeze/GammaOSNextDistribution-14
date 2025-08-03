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

package androidx.emoji2.bundled;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.text.PositionedGlyphs;
import android.graphics.text.TextRunShaper;
import android.text.Spanned;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.emoji.text.EmojiCompat;
import androidx.emoji.text.EmojiSpan;
import androidx.emoji.text.MetadataRepo;
import androidx.emoji2.bundled.util.EmojiMatcher;
import androidx.emoji2.bundled.util.Emoji;
import androidx.emoji2.bundled.util.TestString;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Reads raw/allemojis.txt which includes all the emojis known to human kind and tests that
 * EmojiCompat creates EmojiSpans for each one of them.
 */
@LargeTest
@RunWith(Parameterized.class)
@SdkSuppress(minSdkVersion = 19)
public class AllEmojisTest {
    /**
     * String representation for a single emoji
     */
    private final String mString;

    /**
     * Codepoints of emoji for better assert error message.
     */
    private final String mCodepoints;

    private static class TestConfig extends EmojiCompat.Config {
        TestConfig(String fontPath) {
            super(new TestEmojiDataLoader(fontPath));
            setReplaceAll(true);
        }
    }

    private static class TestEmojiDataLoader implements EmojiCompat.MetadataRepoLoader {
        static final Object S_METADATA_REPO_LOCK = new Object();
        // keep a static instance to in order not to slow down the tests
        @GuardedBy("sMetadataRepoLock")
        static volatile MetadataRepo sMetadataRepo;

        private final String mFontPath;

        TestEmojiDataLoader(String fontPath) {
            mFontPath = fontPath;
        }

        @Override
        public void load(@NonNull EmojiCompat.MetadataRepoLoaderCallback loaderCallback) {
            if (sMetadataRepo == null) {
                synchronized (S_METADATA_REPO_LOCK) {
                    if (sMetadataRepo == null) {
                        try {
                            final Context context = ApplicationProvider.getApplicationContext();
                            final AssetManager assetManager = context.getAssets();
                            sMetadataRepo = MetadataRepo.create(assetManager, mFontPath);
                        } catch (Throwable e) {
                            loaderCallback.onFailed(e);
                            throw new RuntimeException(e);
                        }
                    }
                }
            }

            loaderCallback.onLoaded(sMetadataRepo);
        }
    }

    @Parameterized.Parameters(name = "Emoji Render Test: {1}")
    public static Collection<Object[]> data() throws IOException {
        final Context context = ApplicationProvider.getApplicationContext();
        try (InputStream inputStream = context.getAssets().open("emojis.txt")) {
            final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            final Collection<Object[]> data = new ArrayList<>();
            final StringBuilder stringBuilder = new StringBuilder();
            final StringBuilder codePointsBuilder = new StringBuilder();

            String s;
            while ((s = reader.readLine()) != null) {
                s = s.trim();
                // pass comments
                if (s.isEmpty() || s.startsWith("#")) continue;

                stringBuilder.setLength(0);
                codePointsBuilder.setLength(0);

                // emoji codepoints are space separated: i.e. 0x1f1e6 0x1f1e8
                final String[] split = s.split(" ");

                for (int index = 0; index < split.length; index++) {
                    final String part = split[index].trim();
                    codePointsBuilder.append(part);
                    codePointsBuilder.append(",");
                    stringBuilder.append(Character.toChars(Integer.parseInt(part, 16)));
                }

                String string = stringBuilder.toString();
                String codePoints = codePointsBuilder.toString();

                // TODO(nona): Enable test case for flags.
                if (!isFlagEmoji(string)) {
                    data.add(new Object[]{string, codePoints});
                }
            }

            return data;
        }

    }

    public AllEmojisTest(String string, String codepoints) {
        mString = string;
        mCodepoints = codepoints;

        // TODO(nona): Add full covered EmojiCompat font.
        EmojiCompat.reset(new TestConfig("NotoColorEmojiCompat.ttf"));
    }

    @Test
    public void testEmoji() {
        assertTrue("EmojiCompat should have emoji: " + mCodepoints,
                EmojiCompat.get().hasEmojiGlyph(mString));
        assertEmojiCompatAddsEmoji(mString);
        assertSpanCanRenderEmoji(mString);
    }

    private void assertSpanCanRenderEmoji(final String str) {
        final Spanned spanned = (Spanned) EmojiCompat.get().process(new TestString(str).toString());
        final EmojiSpan[] spans = spanned.getSpans(0, spanned.length(), EmojiSpan.class);

        Canvas canvas = mock(Canvas.class);

        List<PositionedGlyphs> result = new ArrayList<>();

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                char[] text = invocation.getArgument(0);
                int index = invocation.getArgument(1);
                int count = invocation.getArgument(2);
                Paint paint = invocation.getArgument(5);

                PositionedGlyphs glyphs = TextRunShaper.shapeTextRun(
                        text, index, count, index, count, 0, 0, false, paint
                );
                result.add(glyphs);
                return null;
            };
        }).when(canvas)
                .drawText(any(char[].class), anyInt(), anyInt(), anyFloat(), anyFloat(),
                        any(Paint.class));
        spans[0].draw(canvas, spanned, 0, spanned.length(), 0, 0, 0, 0, new Paint());

        assertEquals(1, result.size());
        PositionedGlyphs glyphs = result.get(0);

        // All inputs are single emojis. Thus if multiple glyphs are generated, likely the emoji
        // sequence is decomposed.
        assertEquals(mCodepoints, 1, glyphs.glyphCount());
        assertNotEquals(mCodepoints, 0, glyphs.getGlyphId(0));
        // null file path means the glyph is NOT came from system font.
        assertNull(mCodepoints, glyphs.getFont(0).getFile());
    }

    private static boolean isFlagEmoji(String str) {
        return str.codePoints().allMatch(cp -> 0x1F1E6 <= cp && cp <= 0x1F1FF);
    }

    private void assertEmojiCompatAddsEmoji(final String str) {
        TestString string = new TestString(str);
        CharSequence sequence = EmojiCompat.get().process(string.toString());
        assertThat(sequence, EmojiMatcher.hasEmojiCount(1));
        assertThat(sequence,
                EmojiMatcher.hasEmojiAt(string.emojiStartIndex(), string.emojiEndIndex()));

        // case where Emoji is in the middle of string
        string = new TestString(str).withPrefix().withSuffix();
        sequence = EmojiCompat.get().process(string.toString());
        assertThat(sequence, EmojiMatcher.hasEmojiCount(1));
        assertThat(sequence,
                EmojiMatcher.hasEmojiAt(string.emojiStartIndex(), string.emojiEndIndex()));

        // case where Emoji is at the end of string
        string = new TestString(str).withSuffix();
        sequence = EmojiCompat.get().process(string.toString());
        assertThat(sequence, EmojiMatcher.hasEmojiCount(1));
        assertThat(sequence,
                EmojiMatcher.hasEmojiAt(string.emojiStartIndex(), string.emojiEndIndex()));
    }

}
