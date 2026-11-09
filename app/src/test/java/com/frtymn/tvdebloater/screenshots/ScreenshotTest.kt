package com.frtymn.tvdebloater.screenshots

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.frtymn.tvdebloater.core.PkgAction
import com.frtymn.tvdebloater.ui.App
import com.frtymn.tvdebloater.ui.ConnState
import com.frtymn.tvdebloater.ui.MainViewModel
import com.frtymn.tvdebloater.ui.Screen
import com.frtymn.tvdebloater.ui.TvDebloaterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import java.util.TimeZone

/**
 * Renders the main screens with demo data to PNG (1080x2400) on the JVM. No emulator, no TV.
 * Run with: SCREENSHOTS=1 ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotBase(private val suffix: String, private val locale: Locale) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String, arrange: (MainViewModel) -> Unit) {
        Locale.setDefault(locale)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"))
        val vm = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { TvDebloaterTheme { App(vm) } }
        compose.waitForIdle()
        vm.loadDemo()
        arrange(vm)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/$name$suffix.png")
    }

    @Test fun s1_onboarding() = shoot("01-onboarding") { vm -> vm.demoArrange { it.copy(screen = Screen.ONBOARDING, onboardingDone = false) } }

    @Test fun s2_connect() = shoot("02-connect") { vm -> vm.demoArrange { it.copy(screen = Screen.CONNECT, conn = ConnState.Idle) } }

    @Test fun s3_packages() = shoot("03-packages") { vm ->
        vm.demoArrange { it.copy(dryRun = true) }
        vm.toggle("com.google.android.tv.bugreportsender")
        vm.toggle("com.google.android.feedback")
    }

    @Test fun s4_confirm() = shoot("04-confirm-dry-run") { vm ->
        vm.demoArrange { it.copy(dryRun = true) }
        vm.applyLightProfile()
        vm.planChange(PkgAction.DISABLE)
    }

    @Test fun s5_results() = shoot("05-results") { vm ->
        vm.demoArrange { it.copy(dryRun = false) }
        vm.applyLightProfile()
        vm.planChange(PkgAction.DISABLE)
        vm.execute()
    }

    @Test fun s6_history() = shoot("06-history") { vm -> vm.demoArrange { it.copy(screen = Screen.HISTORY) } }
}

@Config(sdk = [34], qualifiers = "en-w360dp-h800dp-xxhdpi")
class ScreenshotTestEn : ScreenshotBase("", Locale.ENGLISH)

@Config(sdk = [34], qualifiers = "tr-w360dp-h800dp-xxhdpi")
class ScreenshotTestTr : ScreenshotBase("-tr", Locale("tr"))
