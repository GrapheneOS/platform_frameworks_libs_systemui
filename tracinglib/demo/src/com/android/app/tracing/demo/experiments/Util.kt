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

import com.android.app.tracing.coroutines.traceCoroutine
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val counter = AtomicInteger()

internal suspend fun doWork() {
    getNumber(0, 50)
    getNumber(50, 0)
    getNumber(50, 50)
}

/**
 * A simple suspending function that returns a unique sequential number, ordered by when it was
 * originally called. It can optionally be used to simulate slow functions by sleeping before or
 * after the suspension point
 */
suspend fun getNumber(delayBeforeSuspension: Long = 0, delayAfterSuspension: Long = 0): Int {
    val num = counter.incrementAndGet()
    traceCoroutine("getNumber#$num") {
        Thread.sleep(delayBeforeSuspension) // BAD
        return suspendCoroutine { continuation ->
            Thread.sleep(delayAfterSuspension) // BAD
            continuation.resume(num)
        }
    }
}
