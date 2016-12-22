/*
 * Copyright Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pub.devrel.bundler;

import com.google.testing.compile.JavaFileObjects;

import org.junit.Test;

import javax.tools.JavaFileObject;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

/**
 * Tests for the annotation processor for EasyBundler. Tests different cases where compilation
 * should fail. For tests in the case where compilation succeeds, see the class
 * {@code EasyBundlerTest} in the bundler-api module.
 */
public class ProcessorTest {

    @Test
    public void testNoDefaultConstructor() {
        String[] source = new String[]{
                "package pub.devrel.bundler.objects;",
                "",
                "import pub.devrel.bundler.BundlerClass;",
                "",
                "@BundlerClass",
                "public class NoDefaultConstructor {",
                "    public NoDefaultConstructor(String string) {}",
                "}"
        };

        JavaFileObject object = JavaFileObjects.forSourceLines(
                "pub.devrel.bundler.objects.NoDefaultConstructor",
                source);

        assertAbout(javaSource()).that(object)
                .processedWith(new BundlerClassProcessor())
                .failsToCompile()
                .withErrorContaining("default constructor");
    }

    @Test
    public void testInvalidType() {
        String[] source = new String[]{
                "package pub.devrel.bundler.objects;",
                "",
                "import java.util.Queue;",
                "import pub.devrel.bundler.BundlerClass;",
                "",
                "@BundlerClass",
                "public class HasInvalidField {",
                "",
                "    public Queue<Object> queue;",
                "",
                "    public HasInvalidField() {}",
                "}"
        };

        JavaFileObject object = JavaFileObjects.forSourceLines(
                "pub.devrel.bundler.objects.HasInvalidField",
                source);

        assertAbout(javaSource()).that(object)
                .processedWith(new BundlerClassProcessor())
                .failsToCompile()
                .withErrorContaining("unknown type");
    }

}
