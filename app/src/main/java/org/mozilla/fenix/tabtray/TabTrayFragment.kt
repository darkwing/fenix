/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabtray

import android.graphics.Bitmap
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.fragment_tab_tray.view.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import mozilla.components.browser.session.Session
import mozilla.components.browser.session.SessionManager
import mozilla.components.concept.engine.prompt.ShareData
import mozilla.components.feature.media.ext.getSession
import mozilla.components.feature.media.state.MediaStateMachine
import mozilla.components.lib.state.ext.consumeFrom
import org.mozilla.fenix.HomeActivity
import com.google.android.material.snackbar.Snackbar
import mozilla.components.feature.media.state.MediaState
import mozilla.components.feature.tab.collections.TabCollection
import mozilla.components.support.base.feature.UserInteractionHandler
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.components.FenixSnackbar
import org.mozilla.fenix.components.StoreProvider
import org.mozilla.fenix.components.TabCollectionStorage
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.ext.nav
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.sessionsOfType
import org.mozilla.fenix.ext.settings

@SuppressWarnings("LargeClass", "TooManyFunctions")
class TabTrayFragment : Fragment(), UserInteractionHandler {
    private lateinit var tabTrayView: TabTrayView
    private lateinit var tabTrayStore: TabTrayFragmentStore
    private lateinit var tabTrayInteractor: TabTrayInteractor
    private lateinit var tabTrayController: TabTrayController

    private var pendingSessionDeletion: PendingSessionDeletion? = null
    data class PendingSessionDeletion(val deletionJob: (suspend () -> Unit), val sessionIds: Set<String>)

    var snackbar: FenixSnackbar? = null

    var tabTrayMenu: Menu? = null

    private val sessionManager: SessionManager
        get() = requireComponents.core.sessionManager

    private val singleSessionObserver = object : Session.Observer {
        override fun onTitleChanged(session: Session, title: String) {
            if (pendingSessionDeletion == null) emitSessionChanges()
        }

        override fun onIconChanged(session: Session, icon: Bitmap?) {
            if (pendingSessionDeletion == null) emitSessionChanges()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        val sessionObserver = BrowserSessionsObserver(sessionManager, singleSessionObserver) {
            tabTrayStore.dispatch(TabTrayFragmentAction.UpdateTabs(getListOfSessions().toTabs()))
        }

        lifecycle.addObserver(sessionObserver)
    }

    private fun getListOfSessions(): List<Session> {
        return sessionManager.sessionsOfType(private = (activity as HomeActivity).browsingModeManager.mode.isPrivate)
            .toList()
    }

    private fun share(tabs: List<Tab>) {
        val data = tabs.map {
            ShareData(url = it.url, title = it.title)
        }
        val directions = TabTrayFragmentDirections.actionGlobalShareFragment(
            data = data.toTypedArray()
        )
        nav(R.id.tabTrayFragment, directions)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tab_tray, container, false)

        tabTrayStore = StoreProvider.get(this) {
            TabTrayFragmentStore(
                TabTrayFragmentState(
                    tabs = getListOfSessions().toTabs()
                )
            )
        }

        tabTrayController = DefaultTabTrayController(
            tabCollectionStorage = requireComponents.core.tabCollectionStorage,
            browsingModeManager = (activity as HomeActivity).browsingModeManager,
            navController = findNavController(),
            sessionManager = sessionManager,
            tabTrayFragmentStore = tabTrayStore,
            closeTabAction = ::closeTabAction,
            onModeChange = { newMode ->
                invokePendingDeleteJobs()

                if (newMode == BrowsingMode.Private) {
                    requireContext().settings().incrementNumTimesPrivateModeOpened()
                }

                emitSessionChanges()
            }
        )

        tabTrayInteractor = TabTrayInteractor(tabTrayController)

        tabTrayView = TabTrayView(view.tab_tray_list_wrapper, tabTrayInteractor)

        return view
    }

    @ExperimentalCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateUI(tabTrayStore.state)

        consumeFrom(tabTrayStore) {
            updateUI(it)
        }
    }

    private fun updateUI(state: TabTrayFragmentState) {
        tabTrayView.update(state, (activity as HomeActivity).browsingModeManager.mode)
        updateMenuItems()
    }

    override fun onResume() {
        super.onResume()
        activity?.title = requireContext().getString(R.string.tab_tray_title)
        (activity as AppCompatActivity).supportActionBar?.show()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.tab_tray_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        this.tabTrayMenu = menu
        updateMenuItems()
    }

    override fun onBackPressed(): Boolean {
        if (tabTrayStore.state.tabs.isEmpty()) {
            findNavController().popBackStack(R.id.homeFragment, false)
            return true
        }

        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.tab_tray_select_to_save_menu_item -> {
                tabTrayController.navigateToCollectionCreator()
                true
            }
            R.id.tab_tray_share_menu_item -> {
                share(tabTrayStore.state.tabs.toList())
                true
            }
            R.id.tab_tray_close_menu_item -> {
                tabTrayController.closeAllTabs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun closeTabAction(tabs: Sequence<Session>, isPrivate: Boolean) {
        val job = pendingSessionDeletion?.deletionJob ?: { }

        viewLifecycleOwner.lifecycleScope.launch {
            job()
        }.invokeOnCompletion {
            pendingSessionDeletion = null
            val sessionIdsToRemove = tabs.map { it.id }.toSet()

            val temporaryListOfTabs = tabTrayStore.state.tabs.filterNot {
                sessionIdsToRemove.contains(it.sessionId)
            }

            tabTrayStore.dispatch(TabTrayFragmentAction.UpdateTabs(temporaryListOfTabs))

            val deleteOperation: (suspend () -> Unit) = {
                tabs.forEach {
                    sessionManager.remove(it)
                }
            }

            pendingSessionDeletion = PendingSessionDeletion(deleteOperation, sessionIdsToRemove)

            val undoOperation: (suspend () -> Unit) = {
                if (isPrivate) {
                    requireComponents.analytics.metrics.track(Event.PrivateBrowsingSnackbarUndoTapped)
                }

                pendingSessionDeletion = null
                emitSessionChanges()
            }

            val snackbarMessage = when (Pair(sessionIdsToRemove.size > 1, isPrivate)) {
                Pair(first = true, second = true) -> getString(R.string.snackbar_private_tabs_closed)
                Pair(first = true, second = false) -> getString(R.string.snackbar_tabs_closed)
                Pair(first = false, second = true) -> getString(R.string.snackbar_private_tab_closed)
                Pair(first = false, second = false) -> getString(R.string.snackbar_tabs_closed)
                else -> getString(R.string.snackbar_tabs_closed)
            }

            tabTrayView.showUndoSnackbar(
                snackbarMessage,
                deleteOperation,
                undoOperation,
                viewLifecycleOwner
            )
        }
    }

    override fun onStart() {
        super.onStart()
        requireComponents.core.tabCollectionStorage.register(collectionStorageObserver, this)
    }

    override fun onStop() {
        invokePendingDeleteJobs()
        tabTrayView.hideSnackbar()
        // We only want this observer live just before we navigate away to the collection creation screen
        requireComponents.core.tabCollectionStorage.unregister(collectionStorageObserver)
        super.onStop()
    }

    private fun invokePendingDeleteJobs() {
        pendingSessionDeletion?.deletionJob?.let {
            viewLifecycleOwner.lifecycleScope.launch {
                it.invoke()
            }.invokeOnCompletion {
                pendingSessionDeletion = null
            }
        }
    }

    private fun emitSessionChanges() {
        tabTrayStore.dispatch(TabTrayFragmentAction.UpdateTabs(getVisibleSessions().toTabs()))
    }

    private fun List<Session>.toTabs(): List<Tab> {
        val selected = sessionManager.selectedSession
        val mediaStateSession = MediaStateMachine.state.getSession()

        return this.map {
            val mediaState = if (mediaStateSession?.id == it.id) MediaStateMachine.state else MediaState.None
            it.toTab(requireContext(), it == selected, mediaState)
        }
    }

    private fun getVisibleSessions(): List<Session> {
        val pendingSessionIdsForRemoval = pendingSessionDeletion?.sessionIds ?: setOf()
        val sessions = getListOfSessions().filterNot { pendingSessionIdsForRemoval.contains(it.id) }

        return sessions
    }

    private fun showSavedSnackbar(tabSize: Int) {
        view?.let { view ->
            @StringRes
            val stringRes = if (tabSize > 1) {
                R.string.create_collection_tabs_saved
            } else {
                R.string.create_collection_tab_saved
            }

            val snackbar = FenixSnackbar.make(view, Snackbar.LENGTH_LONG).setText(view.context.getString(stringRes))
            snackbar.apply {
                this.view.setPadding(
                    0,
                    0,
                    0,
                    this.view.context.resources.getDimensionPixelSize(R.dimen.tab_tray_menu_height)
                )
            }
            snackbar.show()
        }
    }

    private fun updateMenuItems() {
        val inPrivateMode = (activity as HomeActivity).browsingModeManager.mode.isPrivate

        // Hide all icons when in selection mode with nothing selected
        val showAnyOverflowIcons = tabTrayStore.state.tabs.isNotEmpty()
        this.tabTrayMenu?.findItem(R.id.tab_tray_select_to_save_menu_item)?.isVisible =
            showAnyOverflowIcons && !inPrivateMode
        this.tabTrayMenu?.findItem(R.id.tab_tray_share_menu_item)?.isVisible = showAnyOverflowIcons
        this.tabTrayMenu?.findItem(R.id.tab_tray_close_menu_item)?.isVisible = showAnyOverflowIcons
    }

    private val collectionStorageObserver = object : TabCollectionStorage.Observer {
        override fun onCollectionCreated(title: String, sessions: List<Session>) {
            emitSessionChanges()
            showSavedSnackbar(sessions.size)
        }

        override fun onTabsAdded(tabCollection: TabCollection, sessions: List<Session>) {
            emitSessionChanges()
            showSavedSnackbar(sessions.size)
        }
    }

    companion object {
        const val ICON_ENABLED_ALPHA = 255
        const val ICON_DISABLED_ALPHA = 102
        const val CLOSE_ALL_DISABLED_ALPHA = 0.4f
        const val CLOSE_ALL_ENABLED_ALPHA = 1.0f
    }
}

/**
 * Wrapper around sessions manager to observe changes in sessions.
 * Similar to [mozilla.components.browser.session.utils.AllSessionsObserver] but ignores CustomTab sessions.
 *
 * Call [onStart] to start receiving updates into [onChanged] callback.
 * Call [onStop] to stop receiving updates.
 *
 * @param manager [SessionManager] instance to subscribe to.
 * @param observer [Session.Observer] instance that will recieve updates.
 * @param onChanged callback that will be called when any of [SessionManager.Observer]'s events are fired.
 */
private class BrowserSessionsObserver(
    private val manager: SessionManager,
    private val observer: Session.Observer,
    private val onChanged: () -> Unit
) : LifecycleObserver {

    /**
     * Start observing
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        MediaStateMachine.register(managerObserver)
        manager.register(managerObserver)
        subscribeToAll()
    }

    /**
     * Stop observing (will not receive updates till next [onStop] call)
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        MediaStateMachine.unregister(managerObserver)
        manager.unregister(managerObserver)
        unsubscribeFromAll()
    }

    private fun subscribeToAll() {
        manager.sessions.forEach(::subscribeTo)
    }

    private fun unsubscribeFromAll() {
        manager.sessions.forEach(::unsubscribeFrom)
    }

    private fun subscribeTo(session: Session) {
        session.register(observer)
    }

    private fun unsubscribeFrom(session: Session) {
        session.unregister(observer)
    }

    private val managerObserver = object : SessionManager.Observer, MediaStateMachine.Observer {
        override fun onStateChanged(state: MediaState) {
            onChanged()
        }

        override fun onSessionAdded(session: Session) {
            subscribeTo(session)
            onChanged()
        }

        override fun onSessionsRestored() {
            subscribeToAll()
            onChanged()
        }

        override fun onAllSessionsRemoved() {
            unsubscribeFromAll()
            onChanged()
        }

        override fun onSessionRemoved(session: Session) {
            unsubscribeFrom(session)
            onChanged()
        }

        override fun onSessionSelected(session: Session) {
            onChanged()
        }
    }
}
