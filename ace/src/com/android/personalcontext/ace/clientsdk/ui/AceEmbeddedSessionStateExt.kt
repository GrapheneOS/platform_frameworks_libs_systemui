/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.personalcontext.ace.clientsdk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.android.personalcontext.ace.clientlib.AceEmbeddedProviderImpl
import com.android.personalcontext.ace.clientsdk.state.AceEmbeddedSessionState
import com.android.personalcontext.ace.clientsdk.state.AceEmbeddedSessionStateImpl

/**
 * Create a new [AceEmbeddedSessionState] to allow observing and controlling an embedded ACE
 * session. This must be passed into a single [AceEmbeddedSurfaceView].
 */
@Composable
fun rememberSessionState(): AceEmbeddedSessionState {
    val scope = rememberCoroutineScope()

    return remember {
        val provider = AceEmbeddedProviderImpl(backgroundScope = scope)
        AceEmbeddedSessionStateImpl(coroutineScope = scope, provider = provider)
    }
}
