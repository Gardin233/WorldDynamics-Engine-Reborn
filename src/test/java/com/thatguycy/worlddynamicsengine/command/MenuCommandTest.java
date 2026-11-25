package com.thatguycy.worlddynamicsengine.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.thatguycy.worlddynamicsengine.manager.menu.MenuPage;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuCommandTest {
    @Test
    void opensHomeByDefaultAndParsesTargetedWarPages() {
        assertEquals(new MenuCommand.Request(null, MenuPage.HOME, List.of()), parse("menu"));
        assertEquals(new MenuCommand.Request(null, MenuPage.WAR, List.of("North")), parse("menu", "WAR", "North"));
        assertEquals(new MenuCommand.Request("Alice", MenuPage.WAR, List.of("North")), parse("menu", "Alice", "war", "North"));
    }

    @Test
    void explicitViewerDisambiguatesNamesAndPreservesArguments() {
        assertEquals(new MenuCommand.Request("war", MenuPage.WAR_DETAIL, List.of("nation:North:South")),
                parse("menu", "@war", "war-detail", "nation:North:South"));
        assertEquals(new MenuCommand.Request(null, MenuPage.PEACE_RECEIVED, List.of("North", "Trade Group")),
                parse("menu", "peace-received", "North", "Trade Group"));
    }

    @Test
    void rejectsUnsupportedPagesAndIncorrectArgumentCounts() {
        assertNull(parse("menu", "unknown"));
        assertNull(parse("menu", "@", "home"));
        assertNull(parse("menu", "Alice", "war-detail"));
        assertNull(parse("menu", "home", "unexpected"));
        assertNull(parse("menu", "background-detail", "childhood"));
        assertNull(parse("menu", "war", "North", "South"));
        assertNull(parse("menu", "confirm"));
    }

    private static MenuCommand.Request parse(String... args) {
        return MenuCommand.parse(args);
    }
}
