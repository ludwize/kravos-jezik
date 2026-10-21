package net.kravos.jezik.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import net.kravos.jezik.App

enum class Tab(val label: String) { LEARN("Učenje"), REVIEW("Ponavljanje"), WORDS("Rečnik"), PROFILE("Profil") }

sealed class Route {
    /** A lesson session; [lessonId] null = spaced-repetition review. */
    data class Session(val lessonId: String?) : Route()
    data class Story(val lessonId: String) : Route()
    data class Guide(val unitIdx: Int) : Route()
    data object FalseFriends : Route()
    data object Settings : Route()

    /** Full-screen routes hide the bottom bar. */
    val fullScreen: Boolean get() = this is Session || this is Story
}

class Navigator {
    val stack = mutableStateListOf<Route>()
    val tab = mutableStateOf(Tab.LEARN)
    fun go(r: Route) { stack.add(r) }
    fun back(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); true } else false
    fun tab(t: Tab) { stack.clear(); tab.value = t }
}

val LocalNav = staticCompositionLocalOf { Navigator() }
val LocalApp = staticCompositionLocalOf<App> { error("no app") }
