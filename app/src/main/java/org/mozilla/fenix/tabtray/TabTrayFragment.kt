/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabtray

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import mozilla.components.concept.engine.prompt.ShareData
import androidx.fragment.app.Fragment
import mozilla.components.feature.tabs.tabstray.TabsFeature
import kotlinx.android.synthetic.main.fragment_tab_tray.tabsTray
import kotlinx.android.synthetic.main.fragment_tab_tray.view.*
import mozilla.components.support.base.feature.UserInteractionHandler
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.requireComponents
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.findNavController
import mozilla.components.browser.session.Session
import mozilla.components.browser.session.SessionManager
import mozilla.components.concept.tabstray.Tab
import mozilla.components.concept.tabstray.TabsTray
import org.mozilla.fenix.BrowserDirection
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.collections.SaveCollectionStep
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.nav
import org.mozilla.fenix.ext.sessionsOfType
import org.mozilla.fenix.ext.showToolbar

@SuppressWarnings("TooManyFunctions", "LargeClass")
class TabTrayFragment : Fragment(), TabsTray.Observer, UserInteractionHandler {
    private var tabsFeature: TabsFeature? = null
    var tabTrayMenu: Menu? = null

    private val sessionManager: SessionManager
        get() = requireComponents.core.sessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_tab_tray, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showToolbar(getString(R.string.tab_tray_title))

        tabsFeature = TabsFeature(
            tabsTray,
            requireComponents.core.store,
            requireComponents.useCases.tabsUseCases,
            { !it.content.private },
            ::closeTabsTray)

        view.tab_tray_open_new_tab.setOnClickListener {
            val directions = TabTrayFragmentDirections.actionGlobalSearch(null)
            val navController = findNavController(it)
            navController.navigate(directions)
        }

        view.tab_tray_go_home.setOnClickListener {
            val directions = TabTrayFragmentDirections.actionGlobalHome()
            val navController = findNavController(it)
            navController.navigate(directions)
        }

        view.private_browsing_button.setOnClickListener {

        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.tab_tray_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        this.tabTrayMenu = menu
        // updateMenuItems()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.tab_tray_select_to_save_menu_item -> {
                val tabs = getListOfSessions()
                val tabIds = tabs.map { it.id }.toList().toTypedArray()
                val tabCollectionStorage = (activity as HomeActivity).components.core.tabCollectionStorage

                val step = when {
                    // If there is an existing tab collection, show the SelectCollection fragment to save
                    // the selected tab to a collection of your choice.
                    tabCollectionStorage.cachedTabCollections.isNotEmpty() -> SaveCollectionStep.SelectCollection
                    // Show the NameCollection fragment to create a new collection for the selected tab.
                    else -> SaveCollectionStep.NameCollection
                }

                val directions = TabTrayFragmentDirections.actionTabTrayFragmentToCreateCollectionFragment(
                    tabIds = tabIds,
                    previousFragmentId = R.id.tabTrayFragment,
                    saveCollectionStep = step,
                    selectedTabIds = tabIds,
                    selectedTabCollectionId = -1
                )

                view?.let {
                    val navController = findNavController(it)
                    navController.navigate(directions)
                }

                true
            }
            R.id.tab_tray_share_menu_item -> {
                share(getListOfSessions().toList())
                true
            }
            R.id.tab_tray_close_menu_item -> {
                val tabs = getListOfSessions()
                tabs.forEach {
                    sessionManager.remove(it)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onStart() {
        super.onStart()

        tabsFeature?.start()
        tabsTray.register(this)
    }

    override fun onStop() {
        super.onStop()

        tabsFeature?.stop()
        tabsTray.unregister(this)
    }

    override fun onBackPressed(): Boolean {
        if (getListOfSessions().isEmpty()) {
            findNavController().popBackStack(R.id.homeFragment, false)
            return true
        }

        return false
    }

    private fun closeTabsTray() {
        activity?.supportFragmentManager?.beginTransaction()?.apply {
            //replace(R.id.container, BrowserFragment.create())
            commit()
        }
    }

    override fun onTabClosed(tab: Tab) {
        // noop
    }

    override fun onTabSelected(tab: Tab) {
        (activity as HomeActivity).openToBrowser(BrowserDirection.FromTabTray)
    }

    private fun getListOfSessions(): List<Session> {
        val isPrivate = (activity as HomeActivity).browsingModeManager.mode.isPrivate
        return sessionManager.sessionsOfType(private = isPrivate)
            .toList()
    }

    private fun share(tabs: List<Session>) {
        val data = tabs.map {
            ShareData(url = it.url, title = it.title)
        }
        val directions = TabTrayFragmentDirections.actionGlobalShareFragment(
            data = data.toTypedArray()
        )
        nav(R.id.tabTrayFragment, directions)
    }
}
