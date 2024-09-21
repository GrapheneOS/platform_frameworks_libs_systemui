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

import com.android.app.tracing.coroutines.nameCoroutine
import com.android.app.tracing.coroutines.traceCoroutine
import com.android.app.tracing.demo.FixedThread1
import com.android.app.tracing.demo.FixedThread2
import com.android.app.tracing.demo.Unconfined
import com.android.app.tracing.traceSection
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Singleton
class CombineDeferred
@Inject
constructor(
    @FixedThread1 private var fixedThreadContext1: CoroutineContext,
    @FixedThread2 private var fixedThreadContext2: CoroutineContext,
    @Unconfined private var unconfinedContext: CoroutineContext,
) : Experiment {
    override fun getDescription(): String = "async{} then start()"

    override suspend fun run() {
        traceCoroutine("start1") { incSlowly(50, 50) }
        traceCoroutine("start2") { incSlowly(50, 50) }
        traceCoroutine("start3") { incSlowly(50, 50) }
        traceCoroutine("start4") { incSlowly(50, 50) }
        traceCoroutine("coroutineScope") {
            coroutineScope {
                // deferred10 -> deferred20 -> deferred30
                val deferred30 =
                    async(start = LAZY, context = fixedThreadContext2) {
                        traceCoroutine("async#30") { incSlowly(25, 25) }
                    }
                val deferred20 =
                    async(start = LAZY, context = unconfinedContext) {
                        traceCoroutine("async#20") { incSlowly(5, 45) }
                        traceSection("start30") { deferred30.start() }
                    }
                val deferred10 =
                    async(start = LAZY, context = fixedThreadContext1) {
                        traceCoroutine("async#10") { incSlowly(10, 20) }
                        traceSection("start20") { deferred20.start() }
                    }

                // deferredA -> deferredB -> deferredC
                val deferredC =
                    async(start = LAZY, context = fixedThreadContext1) {
                        traceCoroutine("async#C") { incSlowly(35, 15) }
                    }
                val deferredB =
                    async(start = LAZY, context = unconfinedContext) {
                        traceCoroutine("async#B") { incSlowly(15, 35) }
                        traceSection("startC") { deferredC.start() }
                    }
                val deferredA =
                    async(start = LAZY, context = fixedThreadContext2) {
                        traceCoroutine("async#A") { incSlowly(20, 30) }
                        traceSection("startB") { deferredB.start() }
                    }

                // no dispatcher specified, so will inherit dispatcher from whoever called
                // run(),
                // meaning the ExperimentLauncherThread
                val deferredE =
                    async(nameCoroutine("overridden-scope-name-for-deferredE")) {
                        traceCoroutine("async#E") { incSlowly(30, 20) }
                    }

                launch(fixedThreadContext1) {
                    traceSection("start10") { deferred10.start() }
                    traceSection("startA") { deferredA.start() }
                    traceSection("startE") { deferredE.start() }
                }
            }
        }
        traceCoroutine("end") { incSlowly(50, 50) }
    }
}
