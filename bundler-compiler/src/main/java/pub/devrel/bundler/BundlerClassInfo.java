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

import com.squareup.javapoet.ClassName;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * Metadata about a class annotated with {@code BundlerClass}.
 */
public class BundlerClassInfo {

    public ClassName className;

    public TypeElement typeElement;
    public List<VariableElement> fields = new ArrayList<>();
    public List<ExecutableElement> methods = new ArrayList<>();

    public BundlerClassInfo(TypeElement te) {
        typeElement = te;
        className = ClassName.get(typeElement);

        for (Element e : te.getEnclosedElements()) {
            if (e.getKind() == ElementKind.FIELD) {
                VariableElement ve = (VariableElement) e;
                fields.add(ve);
            }

            if (e.getKind() == ElementKind.METHOD) {
                ExecutableElement ee = (ExecutableElement) e;
                methods.add(ee);
            }
        }
    }

    @Override
    public String toString() {
        return "{ " +
                "name: " + typeElement + ", " +
                "fields: " + fields + ", " +
                "methods: " + methods +
                " }";
    }

}
