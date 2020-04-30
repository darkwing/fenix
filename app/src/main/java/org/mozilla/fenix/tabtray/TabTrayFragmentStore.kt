/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabtray

import android.graphics.Bitmap
import mozilla.components.browser.session.Session
import mozilla.components.browser.session.SessionManager
import mozilla.components.browser.state.state.MediaState
import mozilla.components.lib.state.Action
import mozilla.components.lib.state.State
import mozilla.components.lib.state.Store

/**
 * The [Store] for holding the [TabTrayFragmentState] and applying [TabTrayFragmentAction]s.
 */
class TabTrayFragmentStore(
    initialState: TabTrayFragmentState
) : Store<TabTrayFragmentState, TabTrayFragmentAction>(
    initialState, ::tabTrayFragmentStateReducer
)

data class Tab(
    val sessionId: String,
    val url: String,
    val hostname: String,
    val title: String,
    val selected: Boolean? = null,
    val icon: Bitmap? = null,
    val screenshot: Bitmap? = null,
    val mediaState: MediaState.State
)

fun List<Tab>.toSessionBundle(sessionManager: SessionManager): List<Session> {
    return this.mapNotNull { sessionManager.findSessionById(it.sessionId) }
}

/**
 * The state for the [TabTrayFragment].
 *
 * @property tabs The list of opened [Tab] in the [TabTrayFragment].
 */
data class TabTrayFragmentState(
    val tabs: List<Tab>
) : State

sealed class TabTrayFragmentAction : Action {
    data class Change(
        val tabs: List<Tab>
    ) :
        TabTrayFragmentAction()

    data class TabsChange(val tabs: List<Tab>) : TabTrayFragmentAction()
}

private fun tabTrayFragmentStateReducer(
    state: TabTrayFragmentState,
    action: TabTrayFragmentAction
): TabTrayFragmentState {
    return when (action) {
        is TabTrayFragmentAction.Change -> state.copy(
            tabs = action.tabs
        )
        is TabTrayFragmentAction.TabsChange -> state.copy(tabs = action.tabs)
    }
}
