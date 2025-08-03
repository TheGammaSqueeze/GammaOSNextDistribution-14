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

package libcore.tools.analyzer.openjdk;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Util functions for parsing .jar / .jmod / .class file.
 */
public class ClassFileUtil {
    static ClassNode parseClass(InputStream in) throws IOException {
        ClassReader classReader = new ClassReader(in);
        ClassNode node = new ClassNode();
        classReader.accept(node, 0);
        return node;
    }

    static boolean isAnonymousClass(ClassNode classNode) {
        if (classNode.outerClass == null) {
            return false;
        }
        for (InnerClassNode node : classNode.innerClasses) {
            if (classNode.name.equals(node.name)
                    && node.outerName == null && node.innerName == null) {
                return true;
            }
        }

        return false;
    }

    static ZipEntry getEntryFromClassName(ZipFile zipFile, String className,
            boolean useSlash) {
        String entryName = useSlash ? className : className.replaceAll("\\.", "/");
        entryName += ".class";
        ZipEntry e = zipFile.getEntry(entryName);
        if (e == null) {
            String secondName = "classes/" + entryName;
            e = zipFile.getEntry(secondName);
        }
        return e;
    }

    static ZipEntry getEntryFromClassNameOrThrow(ZipFile zipFile,
            String className) throws IllegalArgumentException {
        ZipEntry e =  getEntryFromClassName(zipFile, className, false);
        if (e == null) {
            String entryName = className + ".class";
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Neither %s nor %s is found in %s", entryName, "classes/" + entryName,
                    zipFile.getName()));
        }
        return e;
    }

}
