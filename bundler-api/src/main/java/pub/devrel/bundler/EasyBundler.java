
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

import android.content.Intent;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Main entry point for automatically bundling (and un-bundling) classes annotated with
 * {@link BundlerClass}.
 *
 * Example state class:
 * <pre>
 *     {@literal @}BundlerClass
 *     public class MyState {
 *
 *          public String field;
 *
 *          public MyState() {}
 *
 *     }
 * </pre>
 *
 * To turn a state object into a Bundle:
 * <pre>
 *     MyState state = new MyState();
 *     Bundle stateBundle = EasyBundler.toBundle(state);
 * </pre>
 *
 * To turn a Bundle into a state object:
 * <pre>
 *     MyState state = EasyBundler.fromBundle(stateBundle, MyState.class)
 * </pre>
 *
 * For convenience, methods are provided to use the above techniques to pack and unpack
 * objects from an Intent. To add a state object to an Intent:
 * <pre>
 *     Intent intent = new Intent();
 *     intent = EasyBundler.putExtra(intent, state);
 * </pre>
 *
 * To retrieve an object from an Intent:
 * <pre>
 *     MyState state = EasyBundler.fromIntent(intent, MyState.class);
 * </pre>
 */
public class EasyBundler {

    private static final Map<Class<?>, Class<?>> BUNDLER_CACHE = new HashMap<>();

    /**
     * Determines if a class can be automatically bundled by EasyBundler.
     * @param clazz the {@link Class} to bundle.
     * @return {@code true} if the class has a generated Bundler class, {@code false} otherwise.
     */
    public static boolean hasBundler(Class<?> clazz) {
        return (getBundlerClass(clazz) != null);
    }

    /**
     * Convert an object to a {@link Bundle}.
     * @param target object to bundle. Should be an instance of a class annotated with
     *               {@link BundlerClass}.
     * @return a {@link Bundle} containing all of the object's fields.
     */
    public static Bundle toBundle(Object target) {
        Class<?> bundlerClass = getBundlerClass(target.getClass());
        if (bundlerClass == null) {
            throw new RuntimeException("Could not find Bundler class for " + target.getClass());
        }

        try {
            Method method = bundlerClass.getMethod("toBundle", target.getClass());
            return (Bundle) method.invoke(null, target);
        } catch (Exception e) {
            throw new RuntimeException("Could not invoke toBundle on class " + bundlerClass, e);
        }
    }

    /**
     * Conver a {@link Bundle} to an Object,
     * @param bundle the {@link Bundle}, should be produced be {@link #toBundle(Object)}.
     * @param clazz the {@link Class} of the desired result object,
     * @param <T> the type of the result object, should be same type as the Class parameter.
     * @return an object instance of type {@code T}.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromBundle(Bundle bundle, Class<T> clazz) {
        Class<?> bundlerClass = getBundlerClass(clazz);
        if (bundlerClass == null) {
            throw new RuntimeException("Could not find Bundler class for " + clazz);
        }

        try {
            Method method = bundlerClass.getMethod("fromBundle", Bundle.class);
            return (T) method.invoke(null, bundle);
        } catch (Exception e) {
            throw new RuntimeException("Could not invoke fromBundle on class " + bundlerClass, e);
        }
    }

    /**
     * Convenience method to bundle an object and put the entire bundle into an Intent.
     * @param intent the {@link Intent} to pack the object into.
     * @param target the object to pack into the intent, see {@link #toBundle(Object)}.
     * @return the modified {@link Intent}.
     */
    public static Intent putExtra(Intent intent, Object target) {
        Bundle bundle = toBundle(target);
        return intent.putExtra(getClassKey(target.getClass()), bundle);
    }

    /**
     * Retrieve an object that was packed into an {@link Intent} via
     * {@link #putExtra(Intent, Object)}.
     * @param intent the {@link Intent} to unpack.
     * @param clazz the {@link Class} of the object to deserialize from the {@link Intent}.
     * @return an object of type {@code T}, or {@code null} if no object was found.
     */
    public static <T> T fromIntent(Intent intent, Class<T> clazz) {
        String key = getClassKey(clazz);
        Bundle bundle = intent.getBundleExtra(key);
        if (bundle == null) {
            return null;
        }

        return fromBundle(bundle, clazz);
    }

    /**
     * Find the Bundler class for a given {@link Class}, or {@code null} if none exists.
     */
    private static Class<?> getBundlerClass(Class<?> clazz) {
        // Check cache for hit
        Class<?> fromMap = BUNDLER_CACHE.get(clazz);
        if (fromMap != null) {
            return fromMap;
        }

        String bundlerClassName = clazz.getName() + "Bundler";
        try {
            // Cache and return
            Class<?> bundlerClass = Class.forName(bundlerClassName);
            BUNDLER_CACHE.put(clazz, bundlerClass);
            return bundlerClass;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Get a unique key for putting a class into a Bundle/Intent.
     */
    private static String getClassKey(Class<?> clazz) {
        return "KEY_" + clazz.getCanonicalName() + "_bundle";
    }

}
