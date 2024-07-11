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

package com.android.app.viewcapture

import android.content.Context
import android.testing.AndroidTestingRunner
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@RunWith(AndroidTestingRunner::class)
@SmallTest
class ViewCaptureAwareWindowManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mockRootView = mock<View>()
    private val windowManager = mock<WindowManager>()
    private val viewCaptureSpy = spy(ViewCaptureFactory.getInstance(context))
    private val lazyViewCapture = mock<Lazy<ViewCapture>> { on { value } doReturn viewCaptureSpy }
    private var mViewCaptureAwareWindowManager: ViewCaptureAwareWindowManager? = null

    @Before
    fun setUp() {
        doAnswer { invocation: InvocationOnMock ->
                val view = invocation.getArgument<View>(0)
                val lp = invocation.getArgument<WindowManager.LayoutParams>(1)
                view.layoutParams = lp
                null
            }
            .`when`(windowManager)
            .addView(any(View::class.java), any(WindowManager.LayoutParams::class.java))
        `when`(mockRootView.context).thenReturn(context)
    }

    @Test
    fun testAddView_viewCaptureEnabled_verifyStartCaptureCall() {
        mViewCaptureAwareWindowManager =
            ViewCaptureAwareWindowManager(
                windowManager,
                lazyViewCapture,
                isViewCaptureEnabled = true
            )
        mViewCaptureAwareWindowManager?.addView(mockRootView, mockRootView.layoutParams)
        verify(viewCaptureSpy).startCapture(any(), anyString())
    }

    @Test
    fun testAddView_viewCaptureNotEnabled_verifyStartCaptureCall() {
        mViewCaptureAwareWindowManager =
            ViewCaptureAwareWindowManager(
                windowManager,
                lazyViewCapture,
                isViewCaptureEnabled = false
            )
        mViewCaptureAwareWindowManager?.addView(mockRootView, mockRootView.layoutParams)
        verify(viewCaptureSpy, times(0)).startCapture(any(), anyString())
    }
}
