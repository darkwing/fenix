/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.sessioncontrol

import android.view.View
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.session.SessionManager
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.prompt.ShareData
import mozilla.components.feature.media.ext.pauseIfPlaying
import mozilla.components.feature.media.ext.playIfPaused
import mozilla.components.feature.tab.collections.TabCollection
import mozilla.components.feature.tab.collections.ext.restore
import mozilla.components.feature.top.sites.TopSite
import org.mozilla.fenix.BrowserDirection
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.browser.browsingmode.BrowsingModeManager
import org.mozilla.fenix.collections.SaveCollectionStep
import org.mozilla.fenix.components.TabCollectionStorage
import org.mozilla.fenix.components.TopSiteStorage
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.components.metrics.MetricController
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.nav
import org.mozilla.fenix.ext.sessionsOfType
import org.mozilla.fenix.home.HomeFragment
import org.mozilla.fenix.home.HomeFragmentAction
import org.mozilla.fenix.home.HomeFragmentDirections
import org.mozilla.fenix.home.HomeFragmentStore
import org.mozilla.fenix.home.Tab
import org.mozilla.fenix.components.tips.Tip
import org.mozilla.fenix.settings.SupportUtils
import org.mozilla.fenix.tabtray.TabTrayFragmentDirections
import mozilla.components.feature.tab.collections.Tab as ComponentTab

/**
 * [HomeFragment] controller. An interface that handles the view manipulation of the Tabs triggered
 * by the Interactor.
 */
@SuppressWarnings("TooManyFunctions")
interface TabTrayController {
    /**
     * @see [TabSessionInteractor.onCloseTab]
     */
    fun handleCloseTab(sessionId: String)

    /**
     * @see [TabSessionInteractor.onCloseAllTabs]
     */
    fun handleCloseAllTabs(isPrivateMode: Boolean)

    /**
     * @see [TabSessionInteractor.onPauseMediaClicked]
     */
    fun handlePauseMediaClicked()

    /**
     * @see [TabSessionInteractor.onPlayMediaClicked]
     */
    fun handlePlayMediaClicked()

    /**
     * @see [TabSessionInteractor.onPrivateBrowsingLearnMoreClicked]
     */
    fun handlePrivateBrowsingLearnMoreClicked()

    /**
     * @see [TabSessionInteractor.onSelectTab]
     */
    fun handleSelectTab(tabView: View, sessionId: String)

    /**
     * @see [TabSessionInteractor.onOpenNewTabClicked]
     */
    fun handleonOpenNewTabClicked()
}

@SuppressWarnings("TooManyFunctions", "LargeClass")
class DefaultTabTrayController(
    private val store: BrowserStore,
    private val activity: HomeActivity,
    private val fragmentStore: TabTrayFragmentStore,
    private val navController: NavController,
    private val browsingModeManager: BrowsingModeManager,
    private val closeTab: (sessionId: String) -> Unit,
    private val closeAllTabs: (isPrivateMode: Boolean) -> Unit,
    private val getListOfTabs: () -> List<Tab>,
    private val invokePendingDeleteJobs: () -> Unit,
    private val registerCollectionStorageObserver: () -> Unit, ,
) : SessionControlController {
    private val metrics: MetricController
        get() = activity.components.analytics.metrics
    private val sessionManager: SessionManager
        get() = activity.components.core.sessionManager

    override fun handleCloseTab(sessionId: String) {
        closeTab.invoke(sessionId)
    }

    override fun handleCloseAllTabs(isPrivateMode: Boolean) {
        closeAllTabs.invoke(isPrivateMode)
    }

    override fun handlePauseMediaClicked() {
        store.state.media.pauseIfPlaying()
    }

    override fun handlePlayMediaClicked() {
        store.state.media.playIfPaused()
    }

    override fun handlePrivateBrowsingLearnMoreClicked() {
        activity.openToBrowserAndLoad(
            searchTermOrURL = SupportUtils.getGenericSumoURLForTopic
                (SupportUtils.SumoTopic.PRIVATE_BROWSING_MYTHS),
            newTab = true,
            from = BrowserDirection.FromHome
        )
    }

    override fun handleRenameCollectionTapped(collection: TabCollection) {
        showCollectionCreationFragment(
            step = SaveCollectionStep.RenameCollection,
            selectedTabCollectionId = collection.id
        )
        metrics.track(Event.CollectionRenamePressed)
    }

    override fun handleSaveTabToCollection(selectedTabId: String?) {
        if (browsingModeManager.mode.isPrivate) return

        invokePendingDeleteJobs()

        val tabs = getListOfTabs()
        val step = when {
            // Show the SelectTabs fragment if there are multiple opened tabs to select which tabs
            // you want to save to a collection.
            tabs.size > 1 -> SaveCollectionStep.SelectTabs
            // If there is an existing tab collection, show the SelectCollection fragment to save
            // the selected tab to a collection of your choice.
            tabCollectionStorage.cachedTabCollections.isNotEmpty() -> SaveCollectionStep.SelectCollection
            // Show the NameCollection fragment to create a new collection for the selected tab.
            else -> SaveCollectionStep.NameCollection
        }

        showCollectionCreationFragment(step, selectedTabId?.let { arrayOf(it) })
    }

    override fun handleSelectTab(tabView: View, sessionId: String) {
        invokePendingDeleteJobs()
        val session = sessionManager.findSessionById(sessionId)
        sessionManager.select(session!!)
        activity.openToBrowser(BrowserDirection.FromHome)
    }

    override fun handleShareTabs() {
        invokePendingDeleteJobs()
        val shareData = sessionManager
            .sessionsOfType(private = browsingModeManager.mode.isPrivate)
            .map { ShareData(url = it.url, title = it.title) }
            .toList()
        showShareFragment(shareData)
    }

    override fun handleonOpenNewTabClicked() {

    }

    private fun showCollectionCreationFragment(
        step: SaveCollectionStep,
        selectedTabIds: Array<String>? = null,
        selectedTabCollectionId: Long? = null
    ) {
        if (navController.currentDestination?.id == R.id.collectionCreationFragment) return

        // Only register the observer right before moving to collection creation
        registerCollectionStorageObserver()

        val tabIds = getListOfTabs().map { it.sessionId }.toTypedArray()
        val directions = HomeFragmentDirections.actionHomeFragmentToCreateCollectionFragment(
            tabIds = tabIds,
            previousFragmentId = R.id.homeFragment,
            saveCollectionStep = step,
            selectedTabIds = selectedTabIds,
            selectedTabCollectionId = selectedTabCollectionId ?: -1
        )
        navController.nav(R.id.homeFragment, directions)
    }

    private fun showShareFragment(data: List<ShareData>) {
        val directions = TabTrayFragmentDirections.actionGlobalShareFragment(
            data = data.toTypedArray()
        )
        navController.nav(R.id.homeFragment, directions)
    }
}
