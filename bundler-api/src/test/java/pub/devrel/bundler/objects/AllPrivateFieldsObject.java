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
package pub.devrel.bundler.objects;

import pub.devrel.bundler.BundlerClass;

/**
 * Class with a bunch of private fields.
 */
@BundlerClass
public class AllPrivateFieldsObject {

    private float privateFloat;
    private String[] privateStringArray;
    private CharSequence privateCharSequence;

    public AllPrivateFieldsObject() {}

    public float getPrivateFloat() {
        return privateFloat;
    }

    public void setPrivateFloat(float privateFloat) {
        this.privateFloat = privateFloat;
    }

    public String[] getPrivateStringArray() {
        return privateStringArray;
    }

    public void setPrivateStringArray(String[] privateStringArray) {
        this.privateStringArray = privateStringArray;
    }

    public CharSequence getPrivateCharSequence() {
        return privateCharSequence;
    }

    public void setPrivateCharSequence(CharSequence privateCharSequence) {
        this.privateCharSequence = privateCharSequence;
    }

}
