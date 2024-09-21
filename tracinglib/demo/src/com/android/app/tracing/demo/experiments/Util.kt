/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.app.tracing.demo.experiments

import android.os.Trace
import java.util.concurrent.Executors.newFixedThreadPool
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private val counter = AtomicInteger()

internal suspend fun doWork() {
    incSlowly(0, 50)
    incSlowly(50, 0)
    incSlowly(50, 50)
}

// BAD - wastefully use a thread pool for resuming continuations in a contrived manner
val threadPoolForSleep = newFixedThreadPool(4)

/**
 * A simple suspending function that returns a unique sequential number, ordered by when it was
 * originally called. It can optionally be used to simulate slow functions by sleeping before or
 * after the suspension point
 */
@Suppress("BlockingMethodInNonBlockingContext")
suspend fun incSlowly(delayBeforeSuspension: Long = 0, delayBeforeResume: Long = 0): Int {
    val num = counter.incrementAndGet()
    Trace.traceBegin(Trace.TRACE_TAG_APP, "inc#$num:sleep-before-suspend:$delayBeforeSuspension")
    try {
        Thread.sleep(delayBeforeSuspension) // BAD - sleep for demo purposes only
    } finally {
        Trace.traceEnd(Trace.TRACE_TAG_APP)
    }
    return suspendCancellableCoroutine { continuation ->
        threadPoolForSleep.submit {
            Trace.traceBegin(Trace.TRACE_TAG_APP, "inc#$num:sleep-before-resume:$delayBeforeResume")
            try {
                Thread.sleep(delayBeforeResume) // BAD - sleep for demo purposes only
            } finally {
                Trace.traceEnd(Trace.TRACE_TAG_APP)
            }
            continuation.resume(num)
        }
    }
}
