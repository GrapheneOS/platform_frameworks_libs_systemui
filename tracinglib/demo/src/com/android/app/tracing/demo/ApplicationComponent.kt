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
package com.android.app.tracing.demo

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Trace
import com.android.app.tracing.coroutines.nameCoroutine
import com.android.app.tracing.demo.experiments.CollectFlow
import com.android.app.tracing.demo.experiments.CombineDeferred
import com.android.app.tracing.demo.experiments.Experiment
import com.android.app.tracing.demo.experiments.LaunchNested
import com.android.app.tracing.demo.experiments.LaunchSequentially
import com.android.app.tracing.demo.experiments.NestedLaunchesWithParentSpan
import com.android.app.tracing.demo.experiments.NestedLaunchesWithoutName
import com.android.app.tracing.demo.experiments.UnconfinedThreadSwitch
import dagger.Binds
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class Main

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class Default

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class IO

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class Unconfined

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class FixedThread1

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class FixedThread2

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class ExperimentLauncherThread

const val NAME_DISPATCHERS = false

private fun nameDispatcher(name: String) =
    if (NAME_DISPATCHERS) {
        nameCoroutine(name)
    } else {
        EmptyCoroutineContext
    }

@Module
class ConcurrencyModule {

    @Provides
    @Singleton
    @Default
    fun provideDefaultCoroutineContext(): CoroutineContext {
        return Dispatchers.Default + nameDispatcher("Dispatchers.Default")
    }

    @Provides
    @Singleton
    @IO
    fun provideIOCoroutineContext(): CoroutineContext {
        return Dispatchers.IO + nameDispatcher("Dispatchers.IO")
    }

    @Provides
    @Singleton
    @Unconfined
    fun provideUnconfinedCoroutineContext(): CoroutineContext {
        return Dispatchers.Unconfined + nameDispatcher("Dispatchers.Unconfined")
    }

    @Provides
    @Singleton
    @FixedThread1
    fun provideFixedThread1CoroutineContext(): CoroutineContext {
        val looper = startThreadWithLooper("FixedThread #1")
        return Handler(looper).asCoroutineDispatcher("FixedCoroutineDispatcher #1") +
            nameDispatcher("FixedCoroutineDispatcher #1")
    }

    @Provides
    @Singleton
    @FixedThread2
    fun provideFixedThread2CoroutineContext(): CoroutineContext {
        val looper = startThreadWithLooper("FixedThread #2")
        return Handler(looper).asCoroutineDispatcher("FixedCoroutineDispatcher #2") +
            nameDispatcher("FixedCoroutineDispatcher #2")
    }

    @Provides
    @Singleton
    @ExperimentLauncherThread
    fun provideExperimentDispatcher(): CoroutineDispatcher {
        val looper = startThreadWithLooper("Experiment Launcher Thread")
        return Handler(looper).asCoroutineDispatcher("Experiment Launcher CoroutineDispatcher")
    }
}

@Module
interface ExperimentModule {
    @Binds
    @IntoMap
    @ClassKey(CollectFlow::class)
    fun bindCollectFlow(service: CollectFlow): Experiment

    @Binds
    @IntoMap
    @ClassKey(CombineDeferred::class)
    fun bindCombineDeferred(service: CombineDeferred): Experiment

    @Binds
    @IntoMap
    @ClassKey(LaunchNested::class)
    fun bindLaunchNested(service: LaunchNested): Experiment

    @Binds
    @IntoMap
    @ClassKey(LaunchSequentially::class)
    fun bindLaunchSequentially(service: LaunchSequentially): Experiment

    @Binds
    @IntoMap
    @ClassKey(NestedLaunchesWithParentSpan::class)
    fun bindNestedLaunchesWithNames(service: NestedLaunchesWithParentSpan): Experiment

    @Binds
    @IntoMap
    @ClassKey(NestedLaunchesWithoutName::class)
    fun bindNestedLaunchesWithoutNames(service: NestedLaunchesWithoutName): Experiment

    @Binds
    @IntoMap
    @ClassKey(UnconfinedThreadSwitch::class)
    fun bindUnconfinedThreadSwitch(service: UnconfinedThreadSwitch): Experiment
}

@Singleton
@Component(modules = [ConcurrencyModule::class, ExperimentModule::class])
interface ApplicationComponent {
    /** Returns [Experiment]s that should be used with the application. */
    @Singleton fun getAllExperiments(): Map<Class<*>, Provider<Experiment>>

    @Singleton @ExperimentLauncherThread fun getExperimentDispatcher(): CoroutineDispatcher
}

private fun startThreadWithLooper(name: String): Looper {
    val thread = HandlerThread(name)
    thread.start()
    val looper = thread.looper
    looper.setTraceTag(Trace.TRACE_TAG_APP)
    return looper
}
