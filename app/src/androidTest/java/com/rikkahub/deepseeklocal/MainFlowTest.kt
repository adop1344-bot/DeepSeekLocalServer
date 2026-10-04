package com.rikkahub.deepseeklocal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rikkahub.deepseeklocal.data.local.prefs.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies DataStore persistence round-trips. */
@RunWith(AndroidJUnit4::class)
class MainFlowTest {
    @Test fun datastorePersistsPort() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val ds = SettingsDataStore(ctx)
        ds.setPort(9999)
        assertEquals(9999, ds.settings.first().port)
    }
}
