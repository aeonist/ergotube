package app.freshtuber

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.domain.model.TextLink
import app.freshtuber.ui.components.VideoText
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class VideoTextInteractionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun timestampsLinksAndTextSelectionCoexist() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        val text = "Описание видео: начало 1:23, затем сайт. Этот текст можно выделить и скопировать."
        val linkAt = text.indexOf("сайт")
        var seek = -1L
        var opened: String? = null
        compose.setContent {
            VideoText(text, listOf(TextLink(linkAt, linkAt + 4, url = "https://example.com/full")), onTimestamp = { seek = it },
                onOpenUrl = { opened = it }, modifier = Modifier.padding(16.dp).testTag("rich:text"))
        }
        val node = compose.onNodeWithTag("rich:text")
        fun at(index: Int): androidx.compose.ui.geometry.Offset {
            val result = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
            return result.first().getBoundingBox(index).center
        }
        val time = at(text.indexOf("1:23") + 1)
        node.performTouchInput { click(time) }
        compose.runOnIdle { assertEquals(83_000L, seek) }
        val site = at(linkAt + 1)
        node.performTouchInput { click(site) }
        compose.runOnIdle { assertEquals("https://example.com/full", opened) }
        val body = at(3)
        node.performTouchInput { longClick(body) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        Thread.sleep(600)
        File(instrumentation.targetContext.getExternalFilesDir(null), "liquid12-text-selection.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        node.performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.C); keyUp(Key.CtrlLeft) }
        var copied: String? = null
        compose.waitUntil(5_000) {
            instrumentation.runOnMainSync {
                copied = instrumentation.targetContext.getSystemService(android.content.ClipboardManager::class.java)
                    .primaryClip?.getItemAt(0)?.text?.toString()
            }
            copied == "Описание"
        }
        compose.runOnIdle { assertEquals(83_000L, seek); assertEquals("https://example.com/full", opened) }
    }
}
