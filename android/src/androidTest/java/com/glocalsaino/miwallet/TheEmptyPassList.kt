package com.glocalsaino.miwallet

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.junit.Rule
import org.junit.Test
import com.glocalsaino.miwallet.R.id.emptyView
import com.glocalsaino.miwallet.functions.checkThatHelpIsThere
import com.glocalsaino.miwallet.ui.PassListActivity
import org.ligi.trulesk.TruleskIntentRule


class TheEmptyPassList {

    @get:Rule
    var rule = TruleskIntentRule(PassListActivity::class.java) {
        TestApp.emptyPassStore()
    }

    @Test
    fun testEmptyViewIsThereWhenThereAreNoPasses() {
        rule.screenShot("empty_view")
        onView(withId(emptyView)).check(matches(isDisplayed()))
    }

    @Test
    fun testHelpGoesToHelp() {
        onView(withId(R.id.menu_help)).perform(click())

        checkThatHelpIsThere()
    }

}
