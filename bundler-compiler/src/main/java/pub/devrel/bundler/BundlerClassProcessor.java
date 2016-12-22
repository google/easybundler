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

import com.google.auto.service.AutoService;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;

@AutoService(Processor.class)
public class BundlerClassProcessor extends AbstractProcessor {

    // This class is identified by its qualified string name to avoid a dependency
    // between the bundler and bundler-api modules
    private static final String ANNOTATION_CLASS = "pub.devrel.bundler.BundlerClass";

    private ProcessingEnvironment processingEnvironment;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        this.processingEnvironment = processingEnvironment;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(ANNOTATION_CLASS);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        TypeElement annotationElement = processingEnvironment.getElementUtils()
                .getTypeElement(ANNOTATION_CLASS);

        for (Element e : roundEnvironment.getElementsAnnotatedWith(annotationElement)) {
            if (e.getKind() == ElementKind.CLASS) {
                TypeElement te = (TypeElement) e;
                processClass(te);
            }
        }

        return true;
    }

    private void processClass(TypeElement typeElement) {
        // Get some metadata about the class to be processed
        BundlerClassInfo info = new BundlerClassInfo(typeElement);

        // Log a message for each class we process
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                "[EasyBundler] processing class " + info);

        // Create a new Bundler and generate the source
        Bundler bundler = new Bundler(processingEnvironment, info);
        String javaSource = bundler.getBundlerClassSource();

        try {
            // Create a source file
            FileObject file = processingEnvironment.getFiler()
                    .createSourceFile(bundler.getQualifiedBundlerClassName(), typeElement);

            // Log a note for each class file created
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "[EasyBundler] Writing class file " + file.getName());

            // Write the generated source
            Writer writer = file.openWriter();
            writer.write(javaSource);
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
