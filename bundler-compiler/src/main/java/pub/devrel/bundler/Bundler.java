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
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Generates code to convert a POJO class to/from an Android Bundle.
 */
public class Bundler {

    /** Policy for matching two types. **/
    private enum MatchPolicy {
        // Types must be the same
        EXACT,

        // Type a must be assignable to type b
        ASSIGNABLE
    }

    // Access the Bundle class like this since we don't have the ability to get Android classes
    // in this Java module
    private static final ClassName BUNDLE_CLASS = ClassName.get("android.os", "Bundle");

    // Other Android classes
    private static final String BUNDLE_CLASS_NAME = "android.os.Bundle";
    private static final String I_BINDER_CLASS_NAME = "android.os.IBinder";
    private static final String PARCELABLE_CLASS_NAME = "android.os.Parcelable";
    private static final String SIZE_CLASS_NAME = "android.util.Size";
    private static final String SIZE_F_CLASS_NAME = "android.util.SizeF";

    private ProcessingEnvironment environment;
    private BundlerClassInfo info;

    public Bundler(ProcessingEnvironment environment, BundlerClassInfo info) {
        this.environment = environment;
        this.info = info;
    }

    /**
     * Returns the fully qualified name of the generated class. Ex: com.foo.far.BazBundler.
     */
    public String getQualifiedBundlerClassName() {
        return info.className.packageName() + "." + getBundlerClassName();
    }

    /**
     * Returns the simple class name of the generate class. Ex: BazBundler.
     */
    public String getBundlerClassName() {
        return info.className.simpleName() + "Bundler";
    }

    /**
     * Process the BundlerClass and return the source of a generated Bundler class, as a String.
     * The output of this method is intended for writing to a ".java" file.
     */
    public String getBundlerClassSource() {
        // Create class named {FooObject}Bundler
        TypeSpec bundlerType = TypeSpec.classBuilder(getBundlerClassName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(createToBundleMethod())
                .addMethod(createFromBundleMethod())
                .build();

        JavaFile javaFile = JavaFile.builder(info.className.packageName(), bundlerType)
                .build();

        return javaFile.toString();
    }

    /**
     * Create the "fromBundle" method that accepts a Bundle and returns a member
     * of the wrapped class.
     */
    private MethodSpec createFromBundleMethod() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("fromBundle")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(BUNDLE_CLASS, "bundle")
                .returns(info.className);

        // Ensure the class has an empty constructor
        boolean hasEmptyConstructor = false;
        for (Element e : info.typeElement.getEnclosedElements()) {
            if (e.getKind() == ElementKind.CONSTRUCTOR) {
                boolean isEmptyConstructor = ((ExecutableElement) e).getParameters().isEmpty();
                hasEmptyConstructor = hasEmptyConstructor || isEmptyConstructor;
            }
        }

        if (!hasEmptyConstructor) {
            String message = "[EasyBundler] Type " + info.className
                    + " does not have default constructor!";
            environment.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
        }

        // Create a new instance of the object
        builder.addStatement("$T object = new $T()", info.className, info.className);

        // Get each field from the bundle and set it on the object
        for (VariableElement field : getApplicableFields()) {
            // Decide on the key for the field and how to get it from the bundle
            String fieldKey = getFieldKey(field);
            String getMethod = bundleGetMethod(field);

            if (isPublic(field)) {
                // Public fields can be set directly
                // Ex: object.someField = (Type) bundle.getString("KEY")
                if (requiresCast(getMethod)) {
                    // Set with cast
                    builder.addStatement("object.$L = ($T) bundle.$L($S)",
                            field.getSimpleName(), field.asType(), getMethod, fieldKey);
                } else {
                    // Set without cast
                    builder.addStatement("object.$L = bundle.$L($S)",
                            field.getSimpleName(), getMethod, fieldKey);
                }
            } else {
                // Non-public fields are set with the setter
                // Ex: object.setSomeField((Type) bundle.getString("KEY"))
                if (requiresCast(getMethod)) {
                    // Set with cast
                    builder.addStatement("object.$L(($T) bundle.$L($S))",
                            setterName(field), field.asType(), getMethod, fieldKey);
                } else {
                    // Set without cast
                    builder.addStatement("object.$L(bundle.$L($S))",
                            setterName(field), getMethod, fieldKey);
                }
            }
        }

        // Return the object instance
        builder.addStatement("return object");

        return builder.build();
    }

    /**
     * Create the "toBundle" method that serializes the wrapped class as a Bundle.
     */
    private MethodSpec createToBundleMethod() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("toBundle")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(info.className, "object")
                .returns(BUNDLE_CLASS);

        // Create new bundle
        builder.addStatement("$T bundle = new $T()", BUNDLE_CLASS, BUNDLE_CLASS);

        // Get each field from the object and set it on the bundle
        for (VariableElement field : getApplicableFields()) {
            // Decide on the key for the field and how to add it to the bundle
            String fieldKey = getFieldKey(field);
            String putMethod = bundlePutMethod(field);

            if (isPublic(field)) {
                // Public fields can be accessed directly
                // Ex: bundle.putString("KEY", object.someField)
                builder.addStatement("bundle.$L($S, object.$L)",
                        putMethod, fieldKey, field.getSimpleName());
            } else {
                // Non-public fields are accessed via getter
                // Ex: bundle.putString("KEY, object.getSomeField())
                builder.addStatement("bundle.$L($S, object.$L())",
                        putMethod, fieldKey, getterName(field));
            }
        }

        // Return statement
        builder.addStatement("return bundle");

        return builder.build();
    }

    /**
     * Returns a (probably) unique Bundle key for a field.
     */
    private String getFieldKey(VariableElement field) {
        return "KEY_" + info.className.reflectionName() + "_" + field.getSimpleName();
    }

    /**
     * Returns the name of "put" method from the Bundle class for a given field. Ex: putString
     * or putCharSequenceArray.
     */
    private String bundlePutMethod(VariableElement field) {
        return "put" + bundleMethodSuffix(field);
    }

    /**
     * Returns the name of "get" method from the Bundle class for a given field. Ex: getString
     * or getCharSequenceArray.
     */
    private String bundleGetMethod(VariableElement field) {
        return "get" + bundleMethodSuffix(field);
    }

    /**
     * Returns {@code true} if a cast is needed when using a certain bundle method.
     */
    private boolean requiresCast(String bundleMethodName) {
        // TL;DR - ParcelableArrayList is a pain in the ass!
        return !(bundleMethodName.contains("ParcelableArrayList"));
    }

    /**
     * Returns the suffix for a bundle method based on type.  For a String field this would be
     * "String", for an Integer field this would be "Int". Used by
     * {@link #bundlePutMethod(VariableElement)} and {@link #bundleGetMethod(VariableElement)}.
     */
    private String bundleMethodSuffix(VariableElement field) {
        // Method lists consulted:
        //   * https://developer.android.com/reference/android/os/BaseBundle.html
        //   * https://developer.android.com/reference/android/os/Bundle.html
        // Not supported:
        //   * SparseParcelableArray<? extends Parcelable>

        // Primitives and boxed primitives
        if (matchesClass(field, Boolean.class, MatchPolicy.ASSIGNABLE)) {
            return "Boolean";
        } else if (matchesClass(field, Byte.class, MatchPolicy.ASSIGNABLE)) {
            return "Byte";
        } else if (matchesClass(field, Character.class, MatchPolicy.ASSIGNABLE)) {
            return "Char";
        } else if (matchesClass(field, Double.class, MatchPolicy.ASSIGNABLE)) {
            return "Double";
        } else if (matchesClass(field, Float.class, MatchPolicy.ASSIGNABLE)) {
            return "Float";
        } else if (matchesClass(field, Integer.class, MatchPolicy.ASSIGNABLE)) {
            return "Int";
        } else if (matchesClass(field, Long.class, MatchPolicy.ASSIGNABLE)) {
            return "Long";
        } else if (matchesClass(field, Short.class, MatchPolicy.ASSIGNABLE)) {
            return "Short";
        }

        // Non-primitive classes
        if (matchesClass(field, String.class, MatchPolicy.EXACT)) {
            return "String";
        } if (matchesClass(field, CharSequence.class, MatchPolicy.EXACT)) {
            return "CharSequence";
        } else if (matchesClass(field, BUNDLE_CLASS_NAME, MatchPolicy.EXACT)) {
            return "Bundle";
        } else if (matchesClass(field, I_BINDER_CLASS_NAME, MatchPolicy.EXACT)) {
            return "Binder";
        } else if (matchesClass(field, SIZE_CLASS_NAME, MatchPolicy.EXACT)) {
            return "Size";
        } else if (matchesClass(field, SIZE_F_CLASS_NAME, MatchPolicy.EXACT)) {
            return "SizeF";
        }

        // Primitive array classes
        if (matchesPrimitiveArrayClass(field, TypeKind.BYTE)) {
            return "ByteArray";
        } else if (matchesPrimitiveArrayClass(field, TypeKind.BOOLEAN)) {
            return "BooleanArray";
        } else if (matchesPrimitiveArrayClass(field, TypeKind.CHAR)) {
            return "CharArray";
        } else if (matchesPrimitiveArrayClass(field, TypeKind.DOUBLE)) {
            return "DoubleArray";
        } else if (matchesPrimitiveArrayClass(field, TypeKind.FLOAT)) {
            return "FloatArray";
        } else if (matchesPrimitiveArrayClass(field, TypeKind.INT)) {
            return "IntArray";
        } else if (matchesPrimitiveArrayClass(field, TypeKind.LONG)) {
            return "LongArray";
        } else if (matchesPrimitiveArrayClass(field, TypeKind.SHORT)) {
            return "ShortArray";
        }

        // Non-primitive array classes
        if (matchesArrayClass(field, String.class, MatchPolicy.EXACT)) {
            return "StringArray";
        } else if (matchesArrayClass(field, CharSequence.class, MatchPolicy.EXACT)) {
            return "CharSequenceArray";
        }

        // ArrayList classes
        if (matchesArrayListClass(field, CharSequence.class, MatchPolicy.EXACT)) {
            return "CharSequenceArrayList";
        } else if (matchesArrayListClass(field, Integer.class, MatchPolicy.EXACT)) {
            return "IntegerArrayList";
        } else if (matchesArrayListClass(field, String.class, MatchPolicy.EXACT)) {
            return "StringArrayList";
        }

        // Parcelable[]
        if (matchesArrayClass(field, PARCELABLE_CLASS_NAME, MatchPolicy.ASSIGNABLE)) {
            return "ParcelableArray";
        }

        // ArrayList<Parcelable>
        if (matchesArrayListClass(field, PARCELABLE_CLASS_NAME, MatchPolicy.ASSIGNABLE)) {
            return "ParcelableArrayList";
        }

        // Serializable and Parcelable last to avoid masking something more specific
        if (matchesClass(field, Serializable.class, MatchPolicy.ASSIGNABLE)) {
            return "Serializable";
        } else if (matchesClass(field, PARCELABLE_CLASS_NAME, MatchPolicy.ASSIGNABLE)) {
            return "Parcelable";
        }

        // Could not find type, throw Exception
        String message = "[EasyBundler] Field " + field.getSimpleName() + " in class "
                + info.className + " cannot be included in bundle: unknown type " + field.asType();
        environment.getMessager().printMessage(Diagnostic.Kind.ERROR, message);

        return null;
    }

    /**
     * Returns {@code true} if the type of an {@link VariableElement} is the same as a
     * particular {@link Class}. This cannot be used with array classes or generic types.
     */
    private boolean matchesClass(VariableElement field, Class<?> clazz, MatchPolicy policy) {
        return matchesClass(field, clazz.getCanonicalName(), policy);
    }

    /**
     * See {@link #matchesClass(VariableElement, Class, MatchPolicy)}.
     */
    private boolean matchesClass(VariableElement field, String className, MatchPolicy policy) {
        TypeElement target = getTypeElementForClass(className);
        return target != null && typesMatch(field.asType(), target.asType(), policy);
    }

    /**
     * Returns {@code true} if the type of an {@link VariableElement} representing an array
     * is an array where the members are a particular {@link Class}.
     */
    private boolean matchesArrayClass(VariableElement field, Class<?> clazz, MatchPolicy policy) {
        return matchesArrayClass(field, clazz.getCanonicalName(), policy);
    }

    /**
     * See {@link #matchesArrayClass(VariableElement, Class, MatchPolicy)}.
     */
    private boolean matchesArrayClass(VariableElement field, String className, MatchPolicy policy) {
        // Check if the field is an array
        if (field.asType().getKind() != TypeKind.ARRAY) {
            return false;
        }

        // Get the type of array it is
        TypeMirror componentType = ((ArrayType) field.asType()).getComponentType();

        // Perform check
        TypeElement target = getTypeElementForClass(className);
        return target != null && typesMatch(componentType, target.asType(), policy);
    }

    /**
     * Returns {@code true} if the type of an {@link VariableElement} representing an array
     * is a primitive array where the members are a particular {@link TypeKind}.
     */
    private boolean matchesPrimitiveArrayClass(VariableElement field, TypeKind kind) {
        PrimitiveType primitiveType = environment.getTypeUtils().getPrimitiveType(kind);
        ArrayType arrayType = environment.getTypeUtils().getArrayType(primitiveType);

        return typesMatch(field.asType(), arrayType, MatchPolicy.EXACT);

    }

    /**
     * Returns {@code true} if the type of an {@link VariableElement} representing an ArrayList
     * is an ArrayList where the members are a particular {@link Class}.
     */
    private boolean matchesArrayListClass(VariableElement field, Class<?> clazz, MatchPolicy policy) {
        return matchesArrayListClass(field, clazz.getCanonicalName(), policy);
    }

    /**
     * See {@link #matchesArrayListClass(VariableElement, Class, MatchPolicy)}.
     */
    private boolean matchesArrayListClass(VariableElement field, String className, MatchPolicy policy) {
        if (!(field.asType() instanceof DeclaredType)) {
            return false;
        }

        // Get generic information
        DeclaredType declaredType = (DeclaredType) field.asType();

        // Check general form
        if (declaredType.getTypeArguments().size() != 1) {
            return false;
        }

        // Ensure that outer type is ArrayList
        TypeElement arrayList = getTypeElementForClass(ArrayList.class);
        TypeMirror erased = environment.getTypeUtils().erasure(declaredType);
        boolean isArrayList = typesMatch(erased, arrayList.asType(), MatchPolicy.ASSIGNABLE);

        // Make sure inner type matches
        TypeMirror innerType = declaredType.getTypeArguments().get(0);
        TypeElement target = getTypeElementForClass(className);
        boolean innerTypeMatches = target != null && typesMatch(innerType, target.asType(), policy);

        return isArrayList && innerTypeMatches;
    }

    /**
     * Returns {@code true} if two type mirrors match. This can be either exact match or
     * assignability depending on the {@link MatchPolicy}.
     */
    private boolean typesMatch(TypeMirror a, TypeMirror b, MatchPolicy policy) {
        switch (policy) {
            case EXACT:
                return environment.getTypeUtils().isSameType(a, b);
            case ASSIGNABLE:
                return environment.getTypeUtils().isAssignable(a, b);
            default:
                return false;
        }
    }

    /**
     * Returns an {@link TypeElement} representing a {@link Class} for comparison.
     */
    private TypeElement getTypeElementForClass(Class clazz) {
        return getTypeElementForClass(clazz.getCanonicalName());
    }

    /**
     * Returns an {@link TypeElement} representing a {@link Class} for comparison, by name.
     */
    private TypeElement getTypeElementForClass(String className) {
        return environment.getElementUtils().getTypeElement(className);
    }

    /**
     * Returns the list of {@link VariableElement} fields that can be properly bundled. This is
     * a list of public fields or private/protected fields that have predictably-named getters
     * and setters.
     */
    private List<VariableElement> getApplicableFields() {
        List<VariableElement> result = new ArrayList<>();

        for (VariableElement field : info.fields) {
            // Skip static fields
            if (isStatic(field)) {
                continue;
            }

            if (isPublic(field)) {
                // Public fields can always be considered
                result.add(field);
            } else {
                // Non-public fields can be considered if there is a getter and setter
                String getterName = getterName(field);
                String setterName = setterName(field);

                // Search for appropriate getters and setters
                boolean hasGetter = false;
                boolean hasSetter = false;
                for (ExecutableElement ee : info.methods) {
                    // Find methods that are named the getter
                    if (getterName.equals(ee.getSimpleName().toString())) {
                        // Ensure that they take no params and return the correct type
                        boolean noParams = ee.getParameters().isEmpty();
                        boolean correctReturn = ee.getReturnType().equals(field.asType());

                        hasGetter = hasGetter || (noParams && correctReturn);
                    }

                    // Find methods that are named like the setter
                    if (setterName.equals(ee.getSimpleName().toString())) {
                        // Ensure that they take exactly one param of the right type
                        List<? extends  VariableElement> params = ee.getParameters();
                        boolean correctParams = params.size() == 1
                                && params.get(0).asType().equals(field.asType());

                        hasSetter = hasSetter || correctParams;
                    }
                }

                // All conditions met, add the field
                if (hasGetter && hasSetter) {
                    result.add(field);
                }
            }
        }

        return result;
    }

    /**
     * Returns {@link true} if a {@link VariableElement} is a {@code static} field
     */
    private static boolean isStatic(VariableElement element) {
        return element.getModifiers().contains(Modifier.STATIC);
    }

    /**
     * Returns {@link true} if a {@link VariableElement} is a {@code public} field
     */
    private static boolean isPublic(VariableElement element) {
        return element.getModifiers().contains(Modifier.PUBLIC);
    }

    /**
     * Returns the standard getter method name for a {@link VariableElement}.  Ex: foo --> getFoo.
     */
    private static String getterName(VariableElement element) {
        return "get" + capitalizedName(element);
    }

    /**
     * Returns the standard setter method name for a {@link VariableElement}.  Ex: foo --> setFoo.
     */
    private static String setterName(VariableElement element) {
        return "set" + capitalizedName(element);
    }

    /**
     * Capitalizes the first letter of the name of a {@link VariableElement}. Used by
     * {@link #getterName(VariableElement)} and {@link #setterName(VariableElement)}.
     */
    private static String capitalizedName(VariableElement element) {
        // TODO(samstern): this will almost certainly choke on unicode
        String name = element.getSimpleName().toString();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
