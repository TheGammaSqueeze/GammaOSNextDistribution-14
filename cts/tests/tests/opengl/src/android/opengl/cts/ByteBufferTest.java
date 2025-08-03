/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.opengl.cts;

import static android.opengl.GLES20.GL_ALPHA;
import static android.opengl.GLES30.GL_ARRAY_BUFFER;
import static android.opengl.GLES30.GL_BUFFER_MAP_POINTER;
import static android.opengl.GLES30.GL_COLOR_ATTACHMENT0;
import static android.opengl.GLES30.GL_DYNAMIC_READ;
import static android.opengl.GLES30.GL_FRAMEBUFFER;
import static android.opengl.GLES30.GL_FRAMEBUFFER_COMPLETE;
import static android.opengl.GLES30.GL_MAP_READ_BIT;
import static android.opengl.GLES30.GL_NO_ERROR;
import static android.opengl.GLES30.GL_RGBA;
import static android.opengl.GLES30.GL_STATIC_DRAW;
import static android.opengl.GLES30.GL_TEXTURE_2D;
import static android.opengl.GLES30.GL_UNIFORM_BUFFER;
import static android.opengl.GLES30.GL_UNSIGNED_BYTE;
import static android.opengl.GLES30.glBindBuffer;
import static android.opengl.GLES30.glBindFramebuffer;
import static android.opengl.GLES30.glBindTexture;
import static android.opengl.GLES30.glBufferData;
import static android.opengl.GLES30.glCheckFramebufferStatus;
import static android.opengl.GLES30.glDeleteBuffers;
import static android.opengl.GLES30.glDeleteTextures;
import static android.opengl.GLES30.glFramebufferTexture2D;
import static android.opengl.GLES30.glGenBuffers;
import static android.opengl.GLES30.glGenFramebuffers;
import static android.opengl.GLES30.glGenTextures;
import static android.opengl.GLES30.glGetBufferPointerv;
import static android.opengl.GLES30.glGetError;
import static android.opengl.GLES30.glMapBufferRange;
import static android.opengl.GLES30.glReadPixels;
import static android.opengl.GLES30.glTexImage2D;
import static android.opengl.GLES30.glUnmapBuffer;
import static android.opengl.GLES30.glViewport;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

/**
 * Tests for functions that return a ByteBuffer or accept a ByteBuffer as an argument
 */
@SmallTest
@RunWith(BlockJUnit4ClassRunner.class) // DO NOT USE AndroidJUnit4, it messes up threading
public class ByteBufferTest extends GlTestBase {

    private static final byte[] byteArray = {0, 63, 127, 127, 15};

    @Test
    public void testMapBufferRange() {
        // Always pass on ES 2.0
        if (Egl14Utils.getMajorVersion() >= 3) {
            int[] buffer = new int[1];
            glGenBuffers(1, buffer, 0);
            glBindBuffer(GL_UNIFORM_BUFFER, buffer[0]);
            glBufferData(GL_UNIFORM_BUFFER, 1024, null, GL_DYNAMIC_READ);

            Buffer mappedBuffer = glMapBufferRange(GL_UNIFORM_BUFFER, 0, 1024, GL_MAP_READ_BIT);

            assertNotNull(mappedBuffer);
            assertTrue(mappedBuffer instanceof ByteBuffer);

            Buffer pointerBuffer = glGetBufferPointerv(GL_UNIFORM_BUFFER, GL_BUFFER_MAP_POINTER);
            assertNotNull(pointerBuffer);
            assertTrue(pointerBuffer instanceof ByteBuffer);

            glUnmapBuffer(GL_UNIFORM_BUFFER);

            glBindBuffer(GL_UNIFORM_BUFFER, 0);
            glDeleteBuffers(1, buffer, 0);
        }
    }

    @Test
    public void testArrayBackedTexImage2d() throws Throwable {
        IntBuffer textureHandles = IntBuffer.allocate(1);
        ByteBuffer texelData = ByteBuffer.wrap(byteArray);
        // Setting position to 1 will cause problems if the bindings don't
        // correctly handle the buffer offset when releasing the pointer
        texelData.position(1);
        glGenTextures(1, textureHandles);
        int textureHandle = textureHandles.get(0);
        glBindTexture(GL_TEXTURE_2D, textureHandle);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 2, 2, 0, GL_ALPHA, GL_UNSIGNED_BYTE, texelData);
        assertEquals(glGetError(), GL_NO_ERROR);
        glDeleteTextures(1, textureHandles);
    }

    @Test
    public void testArrayBackedBufferData() throws Throwable {
        IntBuffer bufferHandles = IntBuffer.allocate(1);
        ByteBuffer bufferData = ByteBuffer.wrap(byteArray);
        // Setting position to 1 will cause problems if the bindings don't
        // correctly handle the buffer offset when releasing the pointer
        bufferData.position(1);
        glGenBuffers(1, bufferHandles);
        int bufferHandle = bufferHandles.get(0);
        glBindBuffer(GL_ARRAY_BUFFER, bufferHandle);
        glBufferData(GL_ARRAY_BUFFER, 4, bufferData, GL_STATIC_DRAW);
        assertEquals(glGetError(), GL_NO_ERROR);
        glDeleteBuffers(1, bufferHandles);
    }

    @Test
    public void testArraybackedFBTextureReadPixels() throws Throwable {
        IntBuffer textureHandles = IntBuffer.allocate(1);
        IntBuffer fboHandles = IntBuffer.allocate(1);
        ByteBuffer texelData = ByteBuffer.wrap(byteArray);
        ByteBuffer readBuffer = ByteBuffer.allocate(byteArray.length);
        // Setting position to 1 will cause problems if the bindings don't
        // correctly handle the buffer offset when releasing the pointer
        texelData.position(1);
        readBuffer.position(1);
        glGenTextures(1, textureHandles);
        int textureHandle = textureHandles.get(0);
        glBindTexture(GL_TEXTURE_2D, textureHandle);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, texelData);
        glGenFramebuffers(1, fboHandles);
        int fbHandle = fboHandles.get(0);
        glBindFramebuffer(GL_FRAMEBUFFER, fbHandle);
        glViewport(0, 0, 1, 1);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureHandle, 0);
        assertEquals(glCheckFramebufferStatus(GL_FRAMEBUFFER), GL_FRAMEBUFFER_COMPLETE);
        glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, readBuffer);
        assertEquals(glGetError(), GL_NO_ERROR);
        for (int i = 0; i < 4; i++) {
            assertEquals(
                texelData.get(i + texelData.position()), readBuffer.get(i + readBuffer.position()));
        }
        glDeleteTextures(1, textureHandles);
    }
}
