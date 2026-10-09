package com.mikimn.droiduvc.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the state helpers that need no camera. */
@RunWith(AndroidJUnit4::class)
class LifecycleTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rememberIsStartedFollowsTheLifecycle() {
        var started by mutableStateOf<Boolean?>(null)
        rule.setContent { started = rememberIsStarted() }
        rule.waitForIdle()
        assertTrue(started == true)

        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.waitForIdle()
        assertFalse(started == true)

        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        assertTrue(started == true)
    }

    @Test
    fun rememberUvcDevicesRegistersAndUnregistersItsReceiver() {
        rule.setContent { rememberUvcDevices() }
        rule.waitForIdle()
        // Leaving the composition must unregister the broadcast receiver (an unregistered
        // receiver would throw from unregisterReceiver and crash the activity here).
        rule.activityRule.scenario.close()
    }
}
