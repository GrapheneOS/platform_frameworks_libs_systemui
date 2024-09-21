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

import android.app.Activity
import android.os.Bundle
import android.os.Trace
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.android.app.tracing.TraceUtils.trace
import com.android.app.tracing.coroutines.createCoroutineTracingContext
import com.android.app.tracing.coroutines.nameCoroutine
import com.android.app.tracing.demo.experiments.Experiment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TRACK_NAME = "Active experiments"

class MainActivity : Activity() {

    private val allExperiments = lazy {
        (applicationContext as MainApplication).appComponent.getAllExperiments()
    }

    private val experimentLaunchContext = lazy {
        (applicationContext as MainApplication).appComponent.getExperimentDispatcher()
    }

    private val scopeForExperiment = mutableMapOf<String, CoroutineScope>()

    private var logContainer: ScrollView? = null
    private var loggerView: TextView? = null

    private fun getScopeForExperiment(name: String): CoroutineScope {
        var scope = scopeForExperiment[name]
        if (scope == null) {
            scope =
                CoroutineScope(experimentLaunchContext.value + createCoroutineTracingContext(name))
            scopeForExperiment[name] = scope
        }
        return scope
    }

    private fun <T : Experiment> createButtonForExperiment(demo: T): Button {
        var launchCounter = 0
        var job: Job? = null
        val className = demo::class.simpleName ?: "<unknown class>"
        return Button(baseContext).apply {
            text =
                context.getString(
                    R.string.run_experiment_button_text,
                    className,
                    demo.getDescription(),
                )
            setOnClickListener {
                val experimentName = "$className #${launchCounter++}"
                trace("$className#onClick") {
                    job?.let { trace("cancel") { it.cancel("Cancelled due to click") } }
                    trace("launch") {
                        job =
                            getScopeForExperiment(className).launch(nameCoroutine("run")) {
                                demo.run()
                            }
                    }
                    trace("toast") { appendLine("$experimentName started") }
                    job?.let {
                        Trace.asyncTraceForTrackBegin(
                            Trace.TRACE_TAG_APP,
                            TRACK_NAME,
                            experimentName,
                            it.hashCode(),
                        )
                    }
                }
                job?.let {
                    it.invokeOnCompletion { cause ->
                        Trace.asyncTraceForTrackEnd(Trace.TRACE_TAG_APP, TRACK_NAME, it.hashCode())
                        mainExecutor.execute {
                            val message =
                                when (cause) {
                                    null -> "$experimentName completed normally"
                                    is CancellationException -> "$experimentName cancelled normally"
                                    else -> "$experimentName failed"
                                }
                            appendLine(message)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logContainer = requireViewById(R.id.log_container)
        loggerView = requireViewById(R.id.logger_view)
        val buttonContainer = requireViewById<LinearLayout>(R.id.button_container)
        allExperiments.value.forEach {
            buttonContainer.addView(createButtonForExperiment(it.value.get()))
        }
    }

    private fun appendLine(message: String) {
        loggerView?.append("$message\n")
        logContainer?.fullScroll(View.FOCUS_DOWN)
    }
}
