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

package libcore.javax.xml.validation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.transform.Source;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

@RunWith(JUnit4.class)
public class SchemaFactoryTest {
    private static final SchemaFactory BASE_SCHEMA_FACTORY = new SchemaFactory() {
        @Override
        public boolean isSchemaLanguageSupported(String schemaLanguage) {
            return false;
        }

        @Override
        public void setErrorHandler(ErrorHandler errorHandler) {

        }

        @Override
        public ErrorHandler getErrorHandler() {
            return null;
        }

        @Override
        public void setResourceResolver(LSResourceResolver resourceResolver) {

        }

        @Override
        public LSResourceResolver getResourceResolver() {
            return null;
        }

        @Override
        public Schema newSchema(Source[] schemas) throws SAXException {
            return null;
        }

        @Override
        public Schema newSchema() throws SAXException {
            return null;
        }
    };

    @Test(expected = NullPointerException.class)
    public void newInstance3_nullSchemaLanguage() {
        SchemaFactory.newInstance(null, "factoryClassName", null);
    }

    @Test(expected = NullPointerException.class)
    public void newInstance3_nullFactoryClassName() {
        SchemaFactory.newInstance("schemaLanguage", null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstance3_ClassNotFoundException() throws ClassNotFoundException {
        ClassLoader classLoader = mock(ClassLoader.class);
        when(classLoader.loadClass(eq("factoryClassName")))
                .thenThrow(new ClassNotFoundException());

        SchemaFactory.newInstance("schemaLanguage", "factoryClassName", classLoader);
    }

    @Test(expected = NullPointerException.class)
    public void getFeature_nullString() throws SAXNotRecognizedException, SAXNotSupportedException {
        BASE_SCHEMA_FACTORY.getFeature(null);
    }

    @Test(expected = SAXNotRecognizedException.class)
    public void getFeature_validString()
            throws SAXNotRecognizedException, SAXNotSupportedException{
        BASE_SCHEMA_FACTORY.getFeature("myFeature");
    }

    @Test(expected = NullPointerException.class)
    public void setFeature_nullString() throws SAXNotRecognizedException, SAXNotSupportedException {
        BASE_SCHEMA_FACTORY.setFeature(null, true);
    }

    @Test(expected = SAXNotRecognizedException.class)
    public void setFeature_validString()
            throws SAXNotRecognizedException, SAXNotSupportedException{
        BASE_SCHEMA_FACTORY.setFeature("myFeature", true);
    }

    @Test(expected = NullPointerException.class)
    public void setProperty_nullString()
            throws SAXNotRecognizedException, SAXNotSupportedException {
        BASE_SCHEMA_FACTORY.setProperty(null, new Object());
    }

    @Test(expected = SAXNotRecognizedException.class)
    public void setProperty_validString()
            throws SAXNotRecognizedException, SAXNotSupportedException{
        BASE_SCHEMA_FACTORY.setProperty("myProperty", new Object());
    }

    @Test(expected = NullPointerException.class)
    public void getProperty_nullString()
            throws SAXNotRecognizedException, SAXNotSupportedException {
        BASE_SCHEMA_FACTORY.getProperty(null);
    }

    @Test(expected = SAXNotRecognizedException.class)
    public void getProperty_validString()
            throws SAXNotRecognizedException, SAXNotSupportedException{
        BASE_SCHEMA_FACTORY.getProperty("myProperty");
    }
}
