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

import com.android.app.tracing.coroutines.createCoroutineTracingContext
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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class Main

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class Default

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class IO

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class Unconfined

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class FixedThread1

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class FixedThread2

@Qualifier @MustBeDocumented @Retention(RUNTIME) annotation class Tracer

@Module
class ConcurrencyModule {

    @Provides
    @Singleton
    @Default
    fun provideDefaultCoroutineContext(@Tracer tracerContext: CoroutineContext): CoroutineContext {
        return Dispatchers.Default + tracerContext
    }

    @Provides
    @Singleton
    @IO
    fun provideIOCoroutineContext(@Tracer tracerContext: CoroutineContext): CoroutineContext {
        return Dispatchers.IO + tracerContext
    }

    @Provides
    @Singleton
    @Unconfined
    fun provideUnconfinedCoroutineContext(
        @Tracer tracerContext: CoroutineContext
    ): CoroutineContext {
        return Dispatchers.Unconfined + tracerContext
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Provides
    @Singleton
    @FixedThread1
    fun provideFixedThread1CoroutineContext(
        @Tracer tracerContext: CoroutineContext
    ): CoroutineContext {
        return newSingleThreadContext("FixedThread #1") + tracerContext
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Provides
    @Singleton
    @FixedThread2
    fun provideFixedThread2CoroutineContext(
        @Tracer tracerContext: CoroutineContext
    ): CoroutineContext {
        return newSingleThreadContext("FixedThread #2") + tracerContext
    }

    @Provides
    @Tracer
    @Singleton
    fun provideTracerCoroutineContext(): CoroutineContext {
        return createCoroutineTracingContext()
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
}
