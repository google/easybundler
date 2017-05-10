# EasyBundler

EasyBundler is a library to simplify converting state objects into Bundles for Android applications.
This repository is also a demonstration of simple annotation processing for Android libraries.

## Download
In order to use EasyBundler you will need to add a `compile` dependency for the API as well
as an `annotationProcessor` dependency for the compiler:

```groovy
dependencies {
    compile 'pub.devrel.easybundler:easybundler-api:0.1.1'
    annotationProcessor 'pub.devrel.easybundler:easybundler-compiler:0.1.1'
}
```

## Basic Usage
First, define a simple state class in your application and annotate it with `@BundlerClass`:

```java
@BundlerClass
public class MyState {

    public String message;

    private int[] favoriteNumbers;

    public MyState() {}

    public int[] getFavoriteNumbers() {
        return favoriteNumbers;
    }

    public void setFavoriteNumbers(int[] favoriteNumbers) {
        this.favoriteNumbers = favoriteNumbers;
    }
}
```

There are a few important requirements for a state object to work with EasyBundler:

  * The class must have a public constructor with no arguments.
  * Any private fields that should be put into the bundle must have JavaBean-style
    getters and setters.  So field `foo` must come with `getFoo()` and `setFoo()`.
    Any private fields that do not meet this requirement will be ignored.
    
At compile time, EasyBundler will generate code like this:

```java
public final class MyStateBundler {
  public static Bundle toBundle(MyState object) {
    Bundle bundle = new Bundle();
    bundle.putString("KEY_pub.devrel.bundler.objects.MyState_message", object.message);
    bundle.putIntArray("KEY_pub.devrel.bundler.objects.MyState_favoriteNumbers", object.getFavoriteNumbers());
    return bundle;
  }

  public static MyState fromBundle(Bundle bundle) {
    MyState object = new MyState();
    object.message = (String) bundle.getString("KEY_pub.devrel.bundler.objects.MyState_message");
    object.setFavoriteNumbers((int[]) bundle.getIntArray("KEY_pub.devrel.bundler.objects.MyState_favoriteNumbers"));
    return object;
  }
}

```

You can use the `Bundler` class directly in your application, but it's even easier to use
the helper methods provided by `EasyBundler`.

For example to serialize an object to `Bundle`, use the `EasyBundler.toBundle(Object)` method.
And to turn that `Bundle` back into an object, use the `EasyBundler.fromBundle(Bundle, Class)`
method. Both of these methods will fail if there is no generated `Bundler` class available.

If you are passing objects through `Intents`, you can use the `EasyBundler.putExtra(Intent, Object)`
and `EasyBundler.fromIntent(Intent, Class)` methods to quickly add objects to and retrieve objects
from an `Intent`.

## FAQs

### Is EasyBundler efficient?
EasyBundler does most of the heavy lifting at compile time to generate the `Bundler` classes,
so bundling and unbundling objects should be very fast due to the limited use of reflection at
runtime. 

If you want to maximize efficiency by eliminating all reflection, use the `Bundler` classes
directly rather than the `EasyBundler` helper methods (which have to do a `Class` lookup at
runtime to find the `Bundler` classes).

### Can I customize how EasyBundler serializes and deserializes?
Not yet! But if you have a use case that is blocked by the lack of customization please 
open an Issue so we can discuss it.

### Does EasyBundler support inheritance?
No, the current version of EasyBundler only looks at properties of the annotated class, not its
parent class(es).


## Publishing

To install the library to your `mavenLocal()` repository, run:

```
./gradlew clean build :bundler-api:jarRelease publishToMavenLocal
```

To publish to Bintray, run:

```
./gradlew clean build test :bundler-api:jarRelease bintrayUpload
```
