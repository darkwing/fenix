package org.mozilla.fenix.tabtray

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.component_tab_tray.view.*
import kotlinx.android.synthetic.main.tab_tray_list_item.view.*
import org.mozilla.fenix.R
import org.mozilla.fenix.components.ui.SelectableListItemView

interface TabTrayInteractor {
    fun tabWasTapped(tab: Tab)
    fun closeButtonTapped(tab: Tab)
}

class TabItemViewHolder(
    private val view: SelectableListItemView,
    private val interactor: TabTrayInteractor
) : RecyclerView.ViewHolder(view) {
    private var tab: Tab? = null

    init {
        view.displayAs(SelectableListItemView.ItemType.CLOSABLE_ITEM)
        view.setOnClickListener {
            tab?.apply(interactor::tabWasTapped)
        }
        view.accessoryView.setOnClickListener {
            tab?.apply(interactor::closeButtonTapped)
        }
    }

    fun bind(tab: Tab) {
        this.tab = tab
        view.title.text = tab.title
        view.url.text= tab.url
    }

    companion object {
        const val LAYOUT_ID = R.layout.selectable_list_item
    }
}

class TabTrayAdapter(
    private val interactor: TabTrayInteractor
): RecyclerView.Adapter<TabItemViewHolder>() {
    private var tabs = listOf<Tab>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabItemViewHolder {
        val view = SelectableListItemView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

        return TabItemViewHolder(view, interactor)
    }

    override fun getItemCount() = tabs.size

    override fun onBindViewHolder(holder: TabItemViewHolder, position: Int) {
        holder.bind(tabs[position])
    }

    fun updateTabs(tabs: List<Tab>) {
        this.tabs = tabs
        notifyDataSetChanged()
    }
}

class TabTrayView(
    val container: ViewGroup,
    val interactor: TabTrayInteractor
) : LayoutContainer {

    val tabTrayAdapter = TabTrayAdapter(interactor)

    override val containerView: View?
        get() = container

    val view: View = LayoutInflater.from(container.context)
        .inflate(R.layout.component_tab_tray, container, true)

    init {
        view.tab_tray_list.apply {
            adapter = tabTrayAdapter
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }
    }

    fun update(state: TabTrayFragmentState) {
        tabTrayAdapter.updateTabs(state.tabs)
    }
}