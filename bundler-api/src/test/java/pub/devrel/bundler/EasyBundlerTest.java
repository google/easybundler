/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pub.devrel.bundler;

import android.os.Bundle;

import junit.framework.AssertionFailedError;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.robolectric.RobolectricTestRunner;
import org.unitils.reflectionassert.ReflectionAssert;

import pub.devrel.bundler.objects.AllPrivateFieldsObject;
import pub.devrel.bundler.objects.AllPublicFieldsObject;
import pub.devrel.bundler.objects.PrivateFieldsNoGetterObject;
import pub.devrel.bundler.objects.PrivateFieldsNoSetterObject;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;

/**
 * Tests for {@link EasyBundler} and related classes.
 */
@RunWith(RobolectricTestRunner.class)
public class EasyBundlerTest {

    @Test
    public void testAllPublicFields() {
        AllPublicFieldsObject obj = new AllPublicFieldsObject();
        obj.publicString = "Hello";
        obj.publicInt = 123;
        obj.publicDoubleArray = new double[]{1, 2, 3};

        checkSurvivesBundle(obj);
    }

    @Test
    public void testAllPrivateFields() {
        AllPrivateFieldsObject obj = new AllPrivateFieldsObject();
        obj.setPrivateCharSequence("12345");
        obj.setPrivateFloat(42f);
        obj.setPrivateStringArray(new String[]{"Hello", "World"});

        checkSurvivesBundle(obj);
    }

    @Test
    public void testPrivateFieldsNoSetter() {
        PrivateFieldsNoSetterObject obj1 = new PrivateFieldsNoSetterObject(456);
        obj1.publicString = "Hello";

        PrivateFieldsNoSetterObject obj2 = bundleAndUnbundle(obj1);

        // Public field should be bundled properly
        assertEquals(obj1.publicString, obj2.publicString);

        // Private field without setter should have been ignored
        assertFalse(obj1.getPrivateInt() == obj2.getPrivateInt());
    }

    @Test
    public void testPrivateFieldsNoGetter() {
        PrivateFieldsNoGetterObject obj1 = new PrivateFieldsNoGetterObject();
        obj1.publicString = "Hello";
        obj1.setPrivateInt(456);

        PrivateFieldsNoGetterObject obj2 = bundleAndUnbundle(obj1);

        // Public field should be bundled properly
        assertEquals(obj1.publicString, obj2.publicString);

        // Private field without getter should have been ignored
        try {
            ReflectionAssert.assertReflectionEquals(obj1, obj2);
            throw new RuntimeException("Should not have gotten here!");
        } catch (AssertionFailedError e) {
            // We expect to get to this branch.
        }
    }

    /**
     * Serialize an object into a bundle and then deserialize it back out.
     */
    private <T> T bundleAndUnbundle(T obj) {
        // Object to bundle
        Bundle bundle = EasyBundler.toBundle(obj);

        // Bundle to object
        return (T) EasyBundler.fromBundle(bundle, obj.getClass());
    }

    /**
     * Put an object into a Bundle and then extract it out again, use reflection to ensure that
     * the resulting object is the same.
     */
    private <T> void checkSurvivesBundle(T obj1) {
        // Bundle to object
        T obj2 = bundleAndUnbundle(obj1);

        // Make sure the objects are deep equal
        ReflectionAssert.assertReflectionEquals(obj1, obj2);
    }

}
