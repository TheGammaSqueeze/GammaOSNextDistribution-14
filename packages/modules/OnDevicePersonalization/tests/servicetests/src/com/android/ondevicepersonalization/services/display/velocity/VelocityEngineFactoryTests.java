/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.ondevicepersonalization.services.display.velocity;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class VelocityEngineFactoryTests {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    // TODO(b/263180569): Add more tests to cover the different configuration options set.
    @Test
    public void renderBasicTemplate() throws Exception {
        VelocityEngine ve = VelocityEngineFactory.getVelocityEngine(mContext);
        String inputTemplate = "Hello $tool.encodeHtml($name)! I am $age.";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("name", "<script>script</script>");
        bundle.putInt("age", 100);

        Template template = ve.getTemplate(createTempTemplate(inputTemplate));
        org.apache.velocity.context.Context ctx =
                VelocityEngineFactory.createVelocityContext(bundle);

        StringWriter writer = new StringWriter();
        template.merge(ctx, writer);
        String expected = "Hello &lt;script&gt;script&lt;/script&gt;! I am 100.";
        assertEquals(expected, writer.toString());
    }

    @Test
    public void testMaxParseDepth() throws Exception {
        VelocityEngine ve = VelocityEngineFactory.getVelocityEngine(mContext);
        String parseFileName = createTempTemplate("world");
        String inputTemplate = "Hello #parse(\"" + parseFileName + "\")!";
        PersistableBundle bundle = new PersistableBundle();

        Template template = ve.getTemplate(createTempTemplate(inputTemplate));
        org.apache.velocity.context.Context ctx =
                VelocityEngineFactory.createVelocityContext(bundle);

        StringWriter writer = new StringWriter();
        template.merge(ctx, writer);
        // Parse depth >1 not allowed. The parse will do nothing.
        String expected = "Hello !";
        assertEquals(expected, writer.toString());
    }

    @Test
    public void testMaxForeach() throws Exception {
        List<String> testList = new ArrayList<>();
        String expectedResult = "";
        for (int i = 0; i < 500; i++) {
            testList.add(String.valueOf(i));
            if (i < 100) {
                expectedResult += testList.get(i);
            }
        }
        VelocityEngine ve = VelocityEngineFactory.getVelocityEngine(mContext);
        String inputTemplate = "#foreach($s in $testList)$s#end";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putStringArray("testList", testList.toArray(new String[0]));

        Template template = ve.getTemplate(createTempTemplate(inputTemplate));
        org.apache.velocity.context.Context ctx =
                VelocityEngineFactory.createVelocityContext(bundle);

        StringWriter writer = new StringWriter();
        template.merge(ctx, writer);
        assertEquals(expectedResult, writer.toString());
    }

    private String createTempTemplate(String s) throws Exception {
        File temp = File.createTempFile("VelocityEngineFactoryTests", ".vm",
                mContext.getCacheDir());
        try (PrintWriter out = new PrintWriter(temp)) {
            out.print(s);
        }
        temp.deleteOnExit();
        return temp.getName();
    }
}
