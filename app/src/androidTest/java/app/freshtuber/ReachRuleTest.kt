package app.freshtuber

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.data.settings.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reach rule: in portrait, every fixed tappable control sits in the bottom part of the
 * screen, below 55% of its height. Scrolling content (feed cards, list rows) moves under the
 * thumb by itself and is not checked. Runs for both hands.
 */
@RunWith(AndroidJUnit4::class)
class ReachRuleTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val inScrollingList = hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
    private val fixedControl = hasClickAction() and !inScrollingList

    @Test fun rightHand() = checkAllScreens(left = false)

    @Test fun leftHand() = checkAllScreens(left = true)

    private fun checkAllScreens(left: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = AppSettings(context)
        runBlocking {
            settings.markWelcomeDone()
            settings.setLeftHanded(left)
        }
        val violations = mutableListOf<String>()
        ActivityScenario.launch(MainActivity::class.java).use {
            fun check(screen: String) {
                compose.waitForIdle()
                Thread.sleep(1_500)
                compose.waitForIdle()
                val root = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxBy { it.size.height }
                val line = root.size.height * 0.55f
                compose.onAllNodes(fixedControl).fetchSemanticsNodes().forEach { node ->
                    if (node.boundsInRoot.top < line - 1f) violations += "$screen (${if (left) "left" else "right"} hand): ${node.describe()} at ${node.boundsInRoot.top.toInt()}px, line ${line.toInt()}px"
                }
            }
            fun tap(tag: String) {
                compose.waitUntil(15_000) { compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag)).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag(tag).performClick()
            }
            tap("dock:Home"); check("Home")
            tap("dock:Subscriptions"); check("Subscriptions")
            tap("dock:Search"); check("Search")
            tap("dock:Shorts"); check("Shorts")
            tap("dock:Mine"); check("Mine list")
            tap("mine:Downloads"); check("Downloads")
            tap("dock:Mine"); tap("mine:WatchLater"); check("Watch later")
            tap("dock:Mine"); tap("mine:Settings"); check("Settings")
            // The player: open the first video card on Home.
            tap("dock:Home")
            compose.waitUntil(30_000) { compose.onAllNodes(hasClickAction() and inScrollingList).fetchSemanticsNodes().any { it.size.height > 300 } }
            val cards = compose.onAllNodes(hasClickAction() and inScrollingList)
            cards[cards.fetchSemanticsNodes().indexOfFirst { it.size.height > 300 }].performClick()
            Thread.sleep(6_000)
            check("Player")
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    private fun SemanticsNode.describe(): String =
        listOfNotNull(config.getOrNull(SemanticsProperties.TestTag), config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(),
            config.getOrNull(SemanticsProperties.Text)?.joinToString()).joinToString(" / ").ifEmpty { "node $id" }
}
