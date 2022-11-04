/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.app.viewcapture

import android.app.Activity
import android.content.Intent
import android.media.permission.SafeCloseable
import android.os.Bundle
import android.testing.AndroidTestingRunner
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.app.viewcapture.TestActivity.Companion.TEXT_VIEW_COUNT
import com.android.app.viewcapture.data.nano.ExportedData
import junit.framework.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidTestingRunner::class)
class ViewCaptureTest {

    private val viewCaptureMemorySize = 100
    private val viewCaptureInitPoolSize = 15
    private val viewCapture =
        ViewCapture.getInstance(false, viewCaptureMemorySize, viewCaptureInitPoolSize)

    private val activityIntent =
        Intent(InstrumentationRegistry.getInstrumentation().context, TestActivity::class.java)

    @get:Rule
    val activityScenarioRule = ActivityScenarioRule<TestActivity>(activityIntent)


    @Test
    fun testViewCaptureDumpsOneFrameAfterInvalidate() {
        val closeable = startViewCaptureAndInvalidateNTimes(1)

        // waits until main looper has no remaining tasks and is idle
        activityScenarioRule.scenario.onActivity {
            val rootView = it.findViewById<View>(android.R.id.content)
            val exportedData = viewCapture.getDumpTask(rootView).get().get()

            assertEquals(1, exportedData.frameData.size)
            verifyTestActivityViewHierarchy(exportedData)
        }
        closeable?.close()
    }

    @Test
    fun testViewCaptureDumpsCorrectlyAfterRecyclingStarted() {
        val closeable = startViewCaptureAndInvalidateNTimes(viewCaptureMemorySize + 5)

        // waits until main looper has no remaining tasks and is idle
        activityScenarioRule.scenario.onActivity {
            val rootView = it.findViewById<View>(android.R.id.content)
            val exportedData = viewCapture.getDumpTask(rootView).get().get()

            // since ViewCapture MEMORY_SIZE is [viewCaptureMemorySize], only
            // [viewCaptureMemorySize] frames are exported, although the view is invalidated
            // [viewCaptureMemorySize + 5] times
            assertEquals(viewCaptureMemorySize, exportedData.frameData.size)
            verifyTestActivityViewHierarchy(exportedData)
        }
        closeable?.close()
    }

    private fun startViewCaptureAndInvalidateNTimes(n: Int): SafeCloseable? {
        var closeable: SafeCloseable? = null
        activityScenarioRule.scenario.onActivity {
            val rootView = it.findViewById<View>(android.R.id.content)
            closeable = viewCapture.startCapture(rootView, "rootViewId")
            invalidateView(rootView, times = n)
        }
        return closeable
    }

    private fun invalidateView(view: View, times: Int) {
        if (times <= 0) return
        view.post {
            view.invalidate()
            invalidateView(view, times - 1)
        }
    }

    private fun verifyTestActivityViewHierarchy(exportedData: ExportedData) {
        val classnames = exportedData.classname
        for (frame in exportedData.frameData) {
            val root = frame.node // FrameLayout (android.R.id.content)
            val testActivityRoot = root.children.first() // LinearLayout (set by setContentView())
            assertEquals(TEXT_VIEW_COUNT, testActivityRoot.children.size)
            assertEquals(
                LinearLayout::class.qualifiedName,
                classnames[testActivityRoot.classnameIndex]
            )
            assertEquals(
                TextView::class.qualifiedName,
                classnames[testActivityRoot.children.first().classnameIndex]
            )
        }
    }
}

/**
 * Activity with the content set to a [LinearLayout] with [TextView] children.
 */
class TestActivity : Activity() {

    companion object {
        const val TEXT_VIEW_COUNT = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
    }

    private fun createContentView(): LinearLayout {
        val root = LinearLayout(this)
        for (i in 0 until TEXT_VIEW_COUNT) {
            root.addView(TextView(this))
        }
        return root
    }
}
