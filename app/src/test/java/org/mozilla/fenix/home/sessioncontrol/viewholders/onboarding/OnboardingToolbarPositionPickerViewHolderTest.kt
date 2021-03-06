/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.sessioncontrol.viewholders.onboarding

import android.view.LayoutInflater
import android.view.View
import io.mockk.every
import io.mockk.mockk
import kotlinx.android.synthetic.main.onboarding_toolbar_position_picker.view.*
import mozilla.components.support.test.robolectric.testContext
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.helpers.FenixRobolectricTestRunner
import org.mozilla.fenix.utils.Settings

@RunWith(FenixRobolectricTestRunner::class)
class OnboardingToolbarPositionPickerViewHolderTest {

    private lateinit var view: View
    private lateinit var settings: Settings

    @Before
    fun setup() {
        view = LayoutInflater.from(testContext)
            .inflate(OnboardingToolbarPositionPickerViewHolder.LAYOUT_ID, null)
        settings = mockk(relaxed = true)

        Settings.instance = settings
    }

    @After
    fun teardown() {
        Settings.instance = null
    }

    @Test
    fun `bottom illustration should select corresponding radio button`() {
        every { settings.shouldUseBottomToolbar } returns false
        OnboardingToolbarPositionPickerViewHolder(view)
        assertTrue(view.toolbar_top_radio_button.isChecked)
        assertFalse(view.toolbar_bottom_radio_button.isChecked)

        view.toolbar_bottom_image.performClick()
        assertFalse(view.toolbar_top_radio_button.isChecked)
        assertTrue(view.toolbar_bottom_radio_button.isChecked)
    }

    @Test
    fun `top illustration should select corresponding radio button`() {
        every { settings.shouldUseBottomToolbar } returns true
        OnboardingToolbarPositionPickerViewHolder(view)
        assertFalse(view.toolbar_top_radio_button.isChecked)
        assertTrue(view.toolbar_bottom_radio_button.isChecked)

        view.toolbar_top_image.performClick()
        assertTrue(view.toolbar_top_radio_button.isChecked)
        assertFalse(view.toolbar_bottom_radio_button.isChecked)
    }
}
