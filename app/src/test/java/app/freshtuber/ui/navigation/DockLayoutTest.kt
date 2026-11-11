package app.freshtuber.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class DockLayoutTest {
    @Test fun requestedVisualOrder() {
        assertEquals(listOf(DockTab.Mine, DockTab.Subscriptions, DockTab.Search, DockTab.Shorts, DockTab.Home),
            DockLayout.order(left = false, shorts = true))
    }

    @Test fun leftHandInvertsTheOrder() {
        assertEquals(listOf(DockTab.Home, DockTab.Shorts, DockTab.Search, DockTab.Subscriptions, DockTab.Mine),
            DockLayout.order(left = true, shorts = true))
    }

    @Test fun hiddenShortsKeepsTheRestInPlace() {
        assertEquals(listOf(DockTab.Mine, DockTab.Subscriptions, DockTab.Search, DockTab.Home), DockLayout.order(left = false, shorts = false))
        assertEquals(listOf(DockTab.Home, DockTab.Search, DockTab.Subscriptions, DockTab.Mine), DockLayout.order(left = true, shorts = false))
    }

    @Test fun swappingInvertsTheWholeRowAroundTheCentre() {
        assertEquals(listOf(DockTab.Home, DockTab.Shorts, DockTab.Search, DockTab.Subscriptions, DockTab.Mine), DockLayout.order(false, true, swapHomeMine = true))
        assertEquals(listOf(DockTab.Mine, DockTab.Subscriptions, DockTab.Search, DockTab.Home), DockLayout.order(true, false, swapHomeMine = true))
    }

    @Test fun expandedScreenAlwaysUsesFullWidth() {
        assertEquals(300f, DockLayout.width(400f, 0, false))
        assertEquals(400f, DockLayout.width(400f, 100, false))
        assertEquals(840f, DockLayout.width(840f, 0, true))
        assertEquals(280f, DockLayout.width(280f, 0, false))
    }

    @Test fun mineTilesPutTheMostUsedLowestOnTheHandSide() {
        // Right hand: Watch later bottom right, Music beside it, Settings on top.
        val items = MineItem.entries.filter { it != MineItem.Shorts }
        assertEquals(listOf(MineItem.Settings, MineItem.Playlists, MineItem.History, MineItem.Downloads, MineItem.Music, MineItem.WatchLater),
            app.freshtuber.ui.components.handGrid(items, 2, left = false))
        assertEquals(listOf(MineItem.Playlists, MineItem.Settings, MineItem.Downloads, MineItem.History, MineItem.WatchLater, MineItem.Music),
            app.freshtuber.ui.components.handGrid(items, 2, left = true))
    }

    @Test fun musicTakesShortsPlaceWhenChosen() {
        assertEquals(listOf(DockTab.Mine, DockTab.Subscriptions, DockTab.Search, DockTab.Music, DockTab.Home),
            DockLayout.order(left = false, shorts = true, music = true))
        assertEquals(listOf(DockTab.Home, DockTab.Music, DockTab.Search, DockTab.Subscriptions, DockTab.Mine),
            DockLayout.order(left = true, shorts = false, music = true))
    }

    @Test fun musicDockKeepsVideoFarthestFromTheThumb() {
        assertEquals(listOf(MusicTab.Video, MusicTab.Search, MusicTab.Library, MusicTab.Listen), DockLayout.musicOrder(left = false))
        assertEquals(listOf(MusicTab.Listen, MusicTab.Library, MusicTab.Search, MusicTab.Video), DockLayout.musicOrder(left = true))
        assertEquals(DockLayout.musicOrder(left = true), DockLayout.musicOrder(left = false, swapHomeMine = true))
    }
}
