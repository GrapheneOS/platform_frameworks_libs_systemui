# Coroutine Tracing

This library contains utilities for tracing coroutines. Coroutines cannot normally be traced using
the `android.os.Trace` APIs because it will often lead to malformed trace sections. This is because
each `Trace.beginSection` must have a matching `Trace.endSection` on the same thread before the
scope is finished, so if they are used around a suspend point, the trace section will remain open
while other unrelated work executes. It could even remain open indefinitely if the coroutine is
canceled.

To address this, we introduce a function `traceCoroutine("name") {}` that can be used for tracing
sections of coroutine code. When invoked, a trace section with the given name will start
immediately, and its name will also be written to an object in the current `CoroutineContext` used
for coroutine-local storage. When the coroutine suspends, all trace sections will end immediately.
When resumed, the coroutine will read the names of the previous sections from coroutine-local
storage, and it will begin the sections again.

For example, the following coroutine code will be traced as follows:

```
traceCoroutine("Slice A") {
  println("Start")
  delay(10)
  println("End")
}
```

```
Thread #1 |  [==== Slice ====]          [==== Slice ====]
               ^ "Start" printed          ^ "End" printed
```

If multiple threads are used, it would be as follows:

```
traceCoroutine("Slice") {
  println("Start")
  delay(10)
  withContext(backgroundThread) {
    println("End")
  }
}
```

```
Thread #1 |  [==== Slice ====]
          |    ^ "Start" printed
----------+---------------------------------------------------------
Thread #2 |                              [==== Slice ====]
                                           ^ "End" printed
```

This library also provides wrappers for some of the coroutine functions provided in the
`kotlinx.coroutines.*` package.  For example, instead of:
`launch { traceCoroutine("my-launch") { /* block */ } }`, you can instead write:
`launch("my-launch") { /* block */ }`.

It also provides a wrapper for tracing Flow emissions. For example,

```
val coldFlow = flow {
  emit(1)
  emit(2)
  emit(3)
}.withTraceName("my-flow")

coldFlow.collect {
  println(it)
  delay(10)
}
```

Would be traced as follows:

```
Thread #1 |  [=== my-flow:collect ===]    [=== my-flow:collect ===]    [=== my-flow:collect ===]
          |    [== my-flow:emit ==]         [== my-flow:emit ==]         [== my-flow:emit ==]
```

# Building and Running

## Host Tests

Host tests are implemented in `tracinglib-host-test`. To run the host tests:

```
atest tracinglib-host-test
```

## Feature Flag

Coroutine tracing is flagged off by default. To enable coroutine tracing on a device, flip the flag
and restart the user-space system:

```
adb shell device_config override systemui com.android.systemui.coroutine_tracing true
adb shell am restart
```

## Demo App

Build and install the app using Soong and adevice:

```
adevice track CoroutineTracingDemoApp
m CoroutineTracingDemoApp
adevice update
```

Then, open the app and tap an experiment to run it. The experiments run in the background. To see
the effects of what coroutine tracing is doing, you will need to capture a Perfetto trace. The
[`coroutine_tracing` flag](#feature-flag) will need to be enabled for coroutine trace sections to
work.
