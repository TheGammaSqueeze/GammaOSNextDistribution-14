/*
 * Copyright (c) 2008, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/* @test
 * @bug 4313887 6838333 6925932 7006126 8037945 8072495 8140449 8254876
 * @summary Unit test for java.nio.file.Path path operations
 */
package test.java.nio.file.Path;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.testng.annotations.Test;
import org.testng.Assert;

public class PathOps {

    private Path path;
    private Exception exc;

    private PathOps(String first, String... more) {
        try {
            path = FileSystems.getDefault().getPath(first, more);
        } catch (Exception x) {
            exc = x;
        }
    }

    private PathOps(URI uri) {
        try {
            path = Path.of(uri);
        } catch (Exception x) {
            exc = x;
        }
    }

    void checkPath() {
        if (path == null) {
            throw new InternalError("path is null");
        }
    }

    void check(Object result, String expected) {
        if (result == null) {
            Assert.assertNull(expected);
        } else {
            // compare string representations
            if (expected == null) {
                Assert.fail("Expected is null but result was non-null");
            }
            Assert.assertEquals(result.toString(), expected.toString());
        }
    }

    void check(Object result, boolean expected) {
        check(result, Boolean.toString(expected));
    }

    PathOps root(String expected) {
        checkPath();
        check(path.getRoot(), expected);
        return this;
    }

    PathOps parent(String expected) {
        checkPath();
        check(path.getParent(), expected);
        return this;
    }

    PathOps name(String expected) {
        checkPath();
        check(path.getFileName(), expected);
        return this;
    }

    PathOps element(int index, String expected) {
        checkPath();
        check(path.getName(index), expected);
        return this;
    }

    PathOps subpath(int startIndex, int endIndex, String expected) {
        checkPath();
        check(path.subpath(startIndex, endIndex), expected);
        return this;
    }

    PathOps starts(String prefix) {
        checkPath();
        Path s = FileSystems.getDefault().getPath(prefix);
        check(path.startsWith(s), true);
        return this;
    }

    PathOps notStarts(String prefix) {
        checkPath();
        Path s = FileSystems.getDefault().getPath(prefix);
        check(path.startsWith(s), false);
        return this;
    }

    PathOps ends(String suffix) {
        checkPath();
        Path s = FileSystems.getDefault().getPath(suffix);
        check(path.endsWith(s), true);
        return this;
    }

    PathOps notEnds(String suffix) {
        checkPath();
        Path s = FileSystems.getDefault().getPath(suffix);
        check(path.endsWith(s), false);
        return this;
    }

    PathOps makeAbsolute() {
        this.path = path.toAbsolutePath();
        return this;
    }

    PathOps absolute() {
        checkPath();
        check(path.isAbsolute(), true);
        return this;
    }

    PathOps notAbsolute() {
        checkPath();
        check(path.isAbsolute(), false);
        return this;
    }

    PathOps resolve(String other, String expected) {
        checkPath();
        check(path.resolve(other), expected);
        return this;
    }

    PathOps resolveSibling(String other, String expected) {
        checkPath();
        check(path.resolveSibling(other), expected);
        return this;
    }

    PathOps relativize(String other, String expected) {
        checkPath();
        Path that = FileSystems.getDefault().getPath(other);
        check(path.relativize(that), expected);
        return this;
    }

    PathOps relativizeFail(String other) {
        checkPath();
        Path that = FileSystems.getDefault().getPath(other);
        try {
            Path result = path.relativize(that);
            Assert.fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException expected) { }
        return this;
    }

    PathOps normalize(String expected) {
        checkPath();
        check(path.normalize(), expected);
        return this;
    }

    PathOps string(String expected) {
        checkPath();
        check(path, expected);
        return this;
    }

    PathOps invalid() {
        if (!(exc instanceof InvalidPathException)) {
            Assert.fail("InvalidPathException not thrown");
        }
        return this;
    }

    static PathOps test(String first, String... more) {
        return new PathOps(first, more);
    }

    static PathOps test(Path path) {
        return new PathOps(path.toString());
    }

    @Test
    public static void doUnixTests() {
        Path cwd = Paths.get("").toAbsolutePath();

        // construction
        test("/")
                .string("/");
        test("/", "")
                .string("/");
        test("/", "foo")
                .string("/foo");
        test("/", "/foo")
                .string("/foo");
        test("/", "foo/")
                .string("/foo");
        test("foo", "bar", "gus")
                .string("foo/bar/gus");
        test("")
                .string("");
        test("", "/")
                .string("/");
        test("", "foo", "", "bar", "", "/gus")
                .string("foo/bar/gus");

        // all components
        test("/a/b/c")
                .root("/")
                .parent("/a/b")
                .name("c");

        // root component only
        test("/")
                .root("/")
                .parent(null)
                .name(null);

        // no root component
        test("a/b")
                .root(null)
                .parent("a")
                .name("b");

        // name component only
        test("foo")
                .root(null)
                .parent(null)
                .name("foo");
        test("")
                .root(null)
                .parent(null)
                .name("");

        // startsWith
        test("/")
                .starts("/")
                .notStarts("")
                .notStarts("/foo");
        test("/foo")
                .starts("/")
                .starts("/foo")
                .notStarts("/f");
        test("/foo/bar")
                .starts("/")
                .starts("/foo")
                .starts("/foo/bar")
                .notStarts("/f")
                .notStarts("foo")
                .notStarts("foo/bar");
        test("foo")
                .starts("foo")
                .notStarts("")
                .notStarts("f");
        test("foo/bar")
                .starts("foo")
                .starts("foo/bar")
                .notStarts("f")
                .notStarts("/foo")
                .notStarts("/foo/bar");
        test("")
                .starts("")
                .notStarts("/");

        // endsWith
        test("/")
                .ends("/")
                .notEnds("")
                .notEnds("foo")
                .notEnds("/foo");
        test("/foo")
                .ends("foo")
                .ends("/foo")
                .notEnds("fool");
        test("/foo/bar")
                .ends("bar")
                .ends("foo/bar")
                .ends("/foo/bar")
                .notEnds("ar")
                .notEnds("barack")
                .notEnds("/bar")
                .notEnds("o/bar");
        test("foo")
                .ends("foo")
                .notEnds("")
                .notEnds("oo")
                .notEnds("oola");
        test("foo/bar")
                .ends("bar")
                .ends("foo/bar")
                .notEnds("r")
                .notEnds("barmaid")
                .notEnds("/bar");
        test("foo/bar/gus")
                .ends("gus")
                .ends("bar/gus")
                .ends("foo/bar/gus")
                .notEnds("g")
                .notEnds("/gus")
                .notEnds("r/gus")
                .notEnds("barack/gus")
                .notEnds("bar/gust");
        test("")
                .ends("")
                .notEnds("/");

        // elementso
        test("a/b/c")
                .element(0, "a")
                .element(1, "b")
                .element(2, "c");
        test("")
                .element(0, "");

        // subpath
        test("/foo")
                .subpath(0, 1, "foo");
        test("foo")
                .subpath(0, 1, "foo");
        test("/foo/bar")
                .subpath(0, 1, "foo")
                .subpath(1, 2, "bar")
                .subpath(0, 2, "foo/bar");
        test("foo/bar")
                .subpath(0, 1, "foo")
                .subpath(1, 2, "bar")
                .subpath(0, 2, "foo/bar");
        test("/foo/bar/gus")
                .subpath(0, 1, "foo")
                .subpath(1, 2, "bar")
                .subpath(2, 3, "gus")
                .subpath(0, 2, "foo/bar")
                .subpath(1, 3, "bar/gus")
                .subpath(0, 3, "foo/bar/gus");
        test("foo/bar/gus")
                .subpath(0, 1, "foo")
                .subpath(1, 2, "bar")
                .subpath(2, 3, "gus")
                .subpath(0, 2, "foo/bar")
                .subpath(1, 3, "bar/gus")
                .subpath(0, 3, "foo/bar/gus");
        test("")
                .subpath(0, 1, "");

        // isAbsolute
        test("/")
                .absolute();
        test("/tmp")
                .absolute();
        test("tmp")
                .notAbsolute();
        test("")
                .notAbsolute();
        test(cwd)
                .absolute();

        // toAbsolutePath
        test("/")
                .makeAbsolute()
                .absolute();
        test("/tmp")
                .makeAbsolute()
                .absolute();
        test("tmp")
                .makeAbsolute()
                .absolute();
        test("")
                .makeAbsolute()
                .absolute();

        // resolve
        test("/tmp")
                .resolve("foo", "/tmp/foo")
                .resolve("/foo", "/foo")
                .resolve("", "/tmp");
        test("tmp")
                .resolve("foo", "tmp/foo")
                .resolve("/foo", "/foo")
                .resolve("", "tmp");
        test("")
                .resolve("", "")
                .resolve("foo", "foo")
                .resolve("/foo", "/foo");

        // resolveSibling
        test("foo")
                .resolveSibling("bar", "bar")
                .resolveSibling("/bar", "/bar")
                .resolveSibling("", "");
        test("foo/bar")
                .resolveSibling("gus", "foo/gus")
                .resolveSibling("/gus", "/gus")
                .resolveSibling("", "foo");
        test("/foo")
                .resolveSibling("gus", "/gus")
                .resolveSibling("/gus", "/gus")
                .resolveSibling("", "/");
        test("/foo/bar")
                .resolveSibling("gus", "/foo/gus")
                .resolveSibling("/gus", "/gus")
                .resolveSibling("", "/foo");
        test("")
                .resolveSibling("foo", "foo")
                .resolveSibling("/foo", "/foo")
                .resolve("", "");

        // normalize
        test("/")
                .normalize("/");
        test("foo")
                .normalize("foo");
        test("/foo")
                .normalize("/foo");
        test("")
                .normalize("");
        test(".")
                .normalize("");
        test("..")
                .normalize("..");
        test("/..")
                .normalize("/");
        test("/../..")
                .normalize("/");
        test("foo/.")
                .normalize("foo");
        test("./foo")
                .normalize("foo");
        test("foo/..")
                .normalize("");
        test("../foo")
                .normalize("../foo");
        test("../../foo")
                .normalize("../../foo");
        test("foo/bar/..")
                .normalize("foo");
        test("foo/bar/gus/../..")
                .normalize("foo");
        test("/foo/bar/gus/../..")
                .normalize("/foo");

        // invalid
        test("foo\u0000bar")
                .invalid();
        test("\u0000foo")
                .invalid();
        test("bar\u0000")
                .invalid();
        test("//foo\u0000bar")
                .invalid();
        test("//\u0000foo")
                .invalid();
        test("//bar\u0000")
                .invalid();

        // normalization of input
        test("//foo//bar")
                .string("/foo/bar")
                .root("/")
                .parent("/foo")
                .name("bar");
    }

    // BEGIN Android-added: Tests for of().
    static PathOps testOf(String first) {
        try {
          return new PathOps(new URI (first));
        } catch (Exception e) {
          return null;
        }
    }

    @Test
    public static void doOfTests() {
        // construction
        testOf("file:///foo/")
                .string("/foo");
        testOf("file:///foo/bar/gus/")
                .string("/foo/bar/gus");
    }
    // END Android-added: Tests for of().

    @Test
    public static void npes() {
        try {
            Path.of("foo", null);
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException npe) {
        }

        Path path = FileSystems.getDefault().getPath("foo");

        try {
            path.resolve((String)null);
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException npe) {
        }

        try {
            path.relativize(null);
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException npe) {
        }

        try {
            path.compareTo(null);
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException npe) {
        }

        try {
            path.startsWith((Path)null);
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException npe) {
        }

        try {
            path.endsWith((Path)null);
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException npe) {
        }

    }

}
