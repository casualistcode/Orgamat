package de.tastenhalter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.tastenhalter.ui.SettingsScreen
import org.robolectric.RuntimeEnvironment
import com.github.takahirom.roborazzi.captureRoboImage
import de.tastenhalter.control.RunController
import de.tastenhalter.control.TemperamentController
import de.tastenhalter.core.OrganConfig
import de.tastenhalter.midi.BundledSongs
import de.tastenhalter.player.MidiPlayer
import de.tastenhalter.tuning.Temperaments
import de.tastenhalter.ui.AppFrame
import de.tastenhalter.ui.KeyboardContent
import de.tastenhalter.ui.LocalStaticLight
import de.tastenhalter.ui.Palette
import de.tastenhalter.ui.PlayerContent
import de.tastenhalter.ui.StatusInfo
import de.tastenhalter.ui.TastenhalterTheme
import de.tastenhalter.ui.TuningContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Bildschirmfotos der Oberfläche (nur zur Kontrolle beim Entwickeln):
 * gradlew testDebugUnitTest --tests de.tastenhalter.ScreenshotTest → app/build/screenshots/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest {

    private val songs = BundledSongs.parseIndex(File("src/main/assets/lieder/lieder.tsv").readText())

    private fun shot(name: String, tab: Tab, content: @Composable () -> Unit) {
        captureRoboImage("build/screenshots/$name.png") {
            TastenhalterTheme {
                CompositionLocalProvider(LocalStaticLight provides true) {
                    Box(Modifier.fillMaxSize().background(Palette.Ground)) {
                        AppFrame(
                            tab = tab,
                            status = StatusInfo(Palette.Ok, "USB-MIDI · 2 Töne"),
                            onTab = {}, onStatus = {}, onSettings = {}, onEmergencyStop = {},
                        ) { content() }
                    }
                }
            }
        }
    }

    private fun tuning(id: String, index: Int, probe: Int = -1, holding: Boolean = false): TemperamentController.State {
        val c = Temperaments.byId(id)
        return TemperamentController.State(c, 440.0, Temperaments.steps(c, 440.0), index, probe, holding)
    }

    @Composable
    private fun Tuning(s: TemperamentController.State) {
        TuningContent(s, fits = true, onPrev = {}, onNext = {}, onHold = {}, onGoTo = {}, onProbe = {}, onSettings = {})
    }

    @Composable
    private fun Keyboard() {
        KeyboardContent(
            cfg = OrganConfig(),
            active = setOf(60, 64, 67, 70),
            held = setOf(60, 64),
            holdMode = true,
            firstWhiteRaw = 7,
            keysSetting = null,
            run = RunController.State(from = 36, to = 91, stepMs = 800, loop = false),
            onFirstWhite = {}, onHoldMode = {}, onReleaseAll = {}, onRun = {}, onDown = {}, onUp = {},
        )
    }

    @Composable
    private fun Player(title: String = "Lobe den Herren") {
        val song = songs.first { it.title == title }
        PlayerContent(
            st = MidiPlayer.UiState(
                fileName = song.title,
                subtitle = song.composer,
                status = MidiPlayer.Status.PAUSED,
                positionUs = 23_000_000,
                durationUs = 63_000_000,
                tempoPercent = 90,
            ),
            songs = songs,
            player = null,
            onSelect = {},
            onOpenFile = {},
        )
    }

    @Test
    @Config(qualifiers = "w393dp-h852dp-xxhdpi")
    fun tuningPortrait() = shot("1_stimmen_hochkant", Tab.TUNING) { Tuning(tuning("quinten", 2, holding = true)) }

    @Test
    @Config(qualifiers = "w393dp-h852dp-xxhdpi")
    fun tuningProbePortrait() = shot("2_stimmen_probe_hochkant", Tab.TUNING) { Tuning(tuning("vallotti", 10, probe = 1)) }

    @Test
    @Config(qualifiers = "w852dp-h393dp-land-xxhdpi")
    fun tuningLandscape() = shot("3_stimmen_quer", Tab.TUNING) { Tuning(tuning("neidhardt", 6)) }

    @Test
    @Config(qualifiers = "w393dp-h852dp-xxhdpi")
    fun keyboardPortrait() = shot("4_klaviatur_hochkant", Tab.KEYBOARD) { Keyboard() }

    @Test
    @Config(qualifiers = "w852dp-h393dp-land-xxhdpi")
    fun keyboardLandscape() = shot("5_klaviatur_quer", Tab.KEYBOARD) { Keyboard() }

    @Test
    @Config(qualifiers = "w393dp-h852dp-xxhdpi")
    fun playerPortrait() = shot("6_lieder_hochkant", Tab.PLAYER) { Player("GL 380 – Großer Gott, wir loben dich") }

    @Test
    @Config(qualifiers = "w852dp-h393dp-land-xxhdpi")
    fun playerLandscape() = shot("7_lieder_quer", Tab.PLAYER) { Player() }

    private fun settingsShot(name: String, section: SettingsSection) {
        val vm = TastenhalterViewModel(RuntimeEnvironment.getApplication())
        captureRoboImage("build/screenshots/$name.png") {
            TastenhalterTheme { SettingsScreen(vm, section) {} }
        }
    }

    @Test
    @Config(qualifiers = "w393dp-h852dp-xxhdpi")
    fun settingsConnection() = settingsShot("8_einstellungen_verbindung", SettingsSection.CONNECTION)

    @Test
    @Config(qualifiers = "w393dp-h852dp-xxhdpi")
    fun settingsTuning() = settingsShot("9_einstellungen_stimmen", SettingsSection.TUNING)

    @Test
    @Config(qualifiers = "w393dp-h852dp-xxhdpi")
    fun settingsOrgan() = settingsShot("10_einstellungen_orgel", SettingsSection.ORGAN)

    @Test
    @Config(qualifiers = "w200dp-h200dp-xxhdpi")
    fun launcherIcon() {
        captureRoboImage("build/screenshots/11_logo.png") {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(150.dp).clip(CircleShape).background(Palette.Ground),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(225.dp))
                }
            }
        }
    }
}
