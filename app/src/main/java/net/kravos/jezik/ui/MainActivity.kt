package net.kravos.jezik.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.kravos.jezik.App
import net.kravos.jezik.ui.screens.FalseFriendsScreen
import net.kravos.jezik.ui.screens.GuideScreen
import net.kravos.jezik.ui.screens.PathScreen
import net.kravos.jezik.ui.screens.ProfileScreen
import net.kravos.jezik.ui.screens.ReviewScreen
import net.kravos.jezik.ui.screens.SessionScreen
import net.kravos.jezik.ui.screens.SettingsScreen
import net.kravos.jezik.ui.screens.StoryScreen
import net.kravos.jezik.ui.screens.WordsScreen
import net.kravos.jezik.ui.theme.JezikTheme
import net.kravos.jezik.ui.theme.K
import net.kravos.jezik.update.Release
import net.kravos.jezik.update.Updater
import net.kravos.jezik.widget.WordWidget

class MainActivity : ComponentActivity() {
    private val nav = Navigator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        if (intent?.getBooleanExtra(EXTRA_TEST, false) == true) app.testMode = true
        if (intent?.getBooleanExtra(EXTRA_WORDS, false) == true) nav.tab(Tab.WORDS)
        setContent {
            val theme by app.settings.theme.collectAsState()
            val lang by app.lang.collectAsState()
            JezikTheme(theme) {
                CompositionLocalProvider(LocalNav provides nav, LocalApp provides app) {
                    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
                        // switching the language rebuilds every screen with the other course
                        androidx.compose.runtime.key(lang) { Root(app) }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_WORDS, false)) nav.tab(Tab.WORDS)
    }

    override fun onStop() {
        super.onStop()
        (application as App).speaker.stop()
        WordWidget.refreshAsync(applicationContext)
    }

    companion object {
        const val EXTRA_TEST = "kravos_test"
        const val EXTRA_WORDS = "open_words"
    }
}

@Composable
private fun Root(app: App) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<Release?>(null) }
    var bannerHidden by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (app.progress.autoUpdateCheck) release = Updater.latest() }

    BackHandler(enabled = nav.stack.isNotEmpty() || nav.tab.value != Tab.LEARN) {
        if (!nav.back()) nav.tab(Tab.LEARN)
    }

    val top = nav.stack.lastOrNull()
    if (top != null && top.fullScreen) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (top) {
                is Route.Session -> SessionScreen(top.lessonId)
                is Route.Story -> StoryScreen(top.lessonId)
                else -> Unit
            }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    val sel = nav.tab.value == t && top == null
                    NavigationBarItem(
                        selected = sel,
                        onClick = { nav.tab(t) },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.LEARN -> Icons.Filled.Home
                                    Tab.REVIEW -> Icons.Filled.Refresh
                                    Tab.WORDS -> Icons.AutoMirrored.Filled.MenuBook
                                    Tab.PROFILE -> Icons.Filled.Person
                                }, t.label,
                            )
                        },
                        label = { Text(t.label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = K.Blue, selectedTextColor = K.Blue,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize()) {
            val r = release
            val showBanner = r != null && Updater.isNewer(r) && !bannerHidden && r.build != app.progress.skippedBuild
            if (showBanner && r != null) {
                Row(
                    Modifier.fillMaxWidth().background(K.Blue).statusBarsPadding()
                        .clickable { bannerHidden = true; nav.go(Route.Settings) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("Nova verzija 1.0.${r.build} je spremna — dodirni za instalaciju", Modifier.weight(1f), color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Kasnije", Modifier.clickable { bannerHidden = true; app.progress.skippedBuild = r.build }, color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp)
                }
            }
            val p = PaddingValues(bottom = pad.calculateBottomPadding())
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                when (top) {
                    is Route.Guide -> GuideScreen(top.unitIdx, p) { nav.back() }
                    is Route.FalseFriends -> FalseFriendsScreen(p) { nav.back() }
                    is Route.Settings -> SettingsScreen(p, release, { nav.back() }) { scope.launch { release = Updater.latest() } }
                    else -> when (nav.tab.value) {
                        Tab.LEARN -> PathScreen(p)
                        Tab.REVIEW -> ReviewScreen(p)
                        Tab.WORDS -> WordsScreen(p)
                        Tab.PROFILE -> ProfileScreen(p, release) { scope.launch { release = Updater.latest() } }
                    }
                }
            }
        }
    }
}
