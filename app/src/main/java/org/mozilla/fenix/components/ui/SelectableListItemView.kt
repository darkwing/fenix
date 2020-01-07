/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.selectable_list_item.view.*
import org.mozilla.fenix.R
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.increaseTapArea
import org.mozilla.fenix.ext.loadIntoView

/**
 * Interactor for items that can be selected on the bookmarks and history screens.
 */
interface SelectionInteractor<T> {
    /**
     * Called when an item is tapped to open it.
     * @param item the tapped item to open.
     */
    fun open(item: T)

    /**
     * Called when an item is long pressed and selection mode is started,
     * or when selection mode has already started an an item is tapped.
     * @param item the item to select.
     */
    fun select(item: T)

    /**
     * Called when a selected item is tapped in selection mode and should no longer be selected.
     * @param item the item to deselect.
     */
    fun deselect(item: T)

    /**
     *
     */
    fun shouldAllowSelect(): Boolean = true
}

interface SelectionHolder<T> {
    val selectedItems: Set<T>
}

class SelectableListItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    val titleView: TextView get() = title

    val urlView: TextView get() = url

    val iconView: ImageView get() = favicon

    val playPauseButton: ImageView get() = play_pause_button

    val accessoryView: ImageButton get() = accessory_view

    init {
        LayoutInflater.from(context).inflate(R.layout.selectable_list_item, this, true)

        accessory_view.increaseTapArea(OVERFLOW_EXTRA_DIPS)
    }

    /**
     * Change visibility of parts of this view based on what type of item is being represented.
     */
    fun displayAs(mode: ItemType) {
        favicon.isVisible = mode != ItemType.SEPARATOR
        title.isVisible = mode != ItemType.SEPARATOR
        url.isVisible = mode == ItemType.SITE || mode == ItemType.CLOSABLE_ITEM
        accessory_view.isVisible = mode != ItemType.SEPARATOR
        separator.isVisible = mode == ItemType.SEPARATOR
        isClickable = mode != ItemType.SEPARATOR
        isFocusable = mode != ItemType.SEPARATOR
        accessoryView.setImageResource(mode.accessoryType.resourceId)
    }

    /**
     * Changes the icon to show a check mark if [isSelected]
     */
    fun changeSelected(isSelected: Boolean) {
        icon.displayedChild = if (isSelected) 1 else 0
    }

    fun loadFavicon(url: String) {
        context.components.core.icons.loadIntoView(favicon, url)
    }

    fun <T> setSelectionInteractor(item: T, holder: SelectionHolder<T>, interactor: SelectionInteractor<T>) {
        setOnClickListener {
            if (!interactor.shouldAllowSelect()) {
                interactor.open(item)
                return@setOnClickListener
            }

            if (item in holder.selectedItems) {
                interactor.deselect(item)
            } else {
                interactor.select(item)
            }
        }

        setOnLongClickListener {
            this.context.components.analytics.metrics.track(Event.CollectionTabLongPressed)
            if (holder.selectedItems.isEmpty()) {
                interactor.select(item)
                true
            } else {
                false
            }
        }
    }

    enum class AccessoryType {
        MENU, CLOSE;

        val resourceId: Int
            get() = if (this == MENU) R.drawable.ic_menu else R.drawable.ic_close
    }

    enum class ItemType {
        SITE, FOLDER, SEPARATOR, CLOSABLE_ITEM;

        val accessoryType: AccessoryType
            get() = when (this) {
                CLOSABLE_ITEM -> AccessoryType.CLOSE
                else -> AccessoryType.MENU
            }
    }

    companion object {
        private const val OVERFLOW_EXTRA_DIPS = 16
    }
}