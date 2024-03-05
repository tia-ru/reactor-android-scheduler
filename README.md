# Reactor Scheduler for Android

![Reactor Scheduler at Maven Central](https://img.shields.io/maven-central/v/io.github.tia-ru/reactor-android-scheduler?style=plastic&logo=apachemaven&logoColor=%23C71A36)
![GitHub License](https://img.shields.io/github/license/tia-ru/reactor-android-scheduler?style=plastic)


Android specific extension for [Reactor](https://github.com/reactor/reactor-core).

This module provides a `Scheduler` that executes `Reactor's` reactive sequence in
the main thread of an Android application or in any other `Looper`.
This makes it possible to use `Reactor` to write reactive components for Android.

**Android SDK version:** 26 (Android 8.0) and above

_The module was tested with Reactor 3.6.3 that is part of 2023.0.3 Release Train._

## Binaries
Add dependency to `reactor-android-scheduler` in your `build.gradle`.
You also have to explicitly depend on `reactor-core` latest version
(see [Reactor releases](https://github.com/reactor/reactor-core/releases) for latest version)
```groovy
dependencies {
    implementation 'io.github.tia-ru:reactor-android-scheduler:1.0.0'
    
    implementation platform(io.projectreactor:reactor-bom:2023.0.3)
    implementation 'io.projectreactor:reactor-core'
}
```

## Build

To build:

Setup path to JDK 17 or above in JAVA_HOME variable in your environment. Then
```
git clone https://github.com/tia-ru/reactor-android-scheduler
cd reactor-android-scheduler/
./gradlew assembleRelease
```
Then use `build/outputs/aar/reactor-android-scheduler-release.aar` in your project

## Observing on the main thread

One of the most common operations when dealing with asynchronous tasks on Android is to observe the task's
result or outcome on the main thread. Using vanilla Android, this would typically be accomplished with an
`AsyncTask`. With `Reactor` instead you would declare your `Flux` and use `AndroidSchedulers.mainThread()` from `reactor-android-scheduler` to be observed on the main thread:

```java
Flux.just("one", "two", "three")
    .subscribeOn(Schedulers.boundedElastic())
    .publishOn(AndroidSchedulers.mainThread())
    .subscribe(/* a Subscriber */);
```

This will emit items on elastic thread pool, and observe results on Android application main thread by `Subscriber`.

## Observing on arbitrary loopers

The previous sample is merely a specialization of a more general concept: binding asynchronous
communication to an Android message loop, or `Looper`. In order to observe an `Subscriber` on an arbitrary
`Looper`, create an associated `Scheduler` by calling `AndroidSchedulers.from`:

```java
Looper backgroundLooper = // ...
Flux.just("one", "two", "three")
    .publishOn(AndroidSchedulers.from(backgroundLooper))
    .subscribe(/* a Subscriber */);
```

This will execute the Flux and emit results on whatever thread is running `backgroundLooper` 

## Main thread scheduler as Reactor's Schedulers.single()

`AndroidSchedulers.mainThread()` does not support testing with `StepVerifier`
It is recommended to [replace Reactor's schedulers factory](https://projectreactor.io/docs/core/release/reference/index.html#scheduler-factory)
to provide `AndroidSchedulers.mainThread()` on call to `Schedulers.single()`.
So `.publishOn(Schedulers.single())` will run on Android main thread.
It makes testing easier and a code becomes portable better.

There is helper method `AndroidSchedulers.installMainThreadAsSingle()` to do the replacement.
The method should be called once on app startup before any call to `Schedulers`.
Then use `Schedulers.single()` instead of `AndroidSchedulers.mainThread()`

```java
public class MyApplication extends Application {
    public void onCreate() {
        AndroidSchedulers.installMainThreadAsSingle();
    }
}

Flux.just("one", "two", "three")
    .publishOn(Schedulers.single()) // Executes in Android main thread
    .subscribe(/* a Subscriber */);
```
     
It allows tests of reactive sequences to use `StepVerifier`
```java
Flux createMyFlyx() {
    return Flux.interval(Duration.ofSeconds(1))
            .publishOn(Schedulers.single()) // AndroidSchedulers.mainThread() fails in tests
            .take(2);
}
 
@Test
public void testMyFlux() {
    StepVerifier
            .withVirtualTime(() ->
                    createMyFlyx()
            )
        ...
}
``` 

## LICENSE
 

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


