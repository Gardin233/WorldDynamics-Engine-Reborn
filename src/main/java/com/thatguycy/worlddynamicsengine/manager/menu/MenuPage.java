package com.thatguycy.worlddynamicsengine.manager.menu;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Stable entry points, excluding transient confirmation and input sessions. */
public enum MenuPage {
    HOME(0, 0), WAR(0, 1), WAR_DETAIL(1, 1), WAR_DECLARE(1, 2),
    TOWN(0, 1), NATION(0, 1), CONSCRIPT(2, 2),
    ORGANIZATIONS(0, 0), ORGANIZATION(1, 1), ORG_MEMBERS(1, 1),
    ORG_APPLICATIONS(1, 1), ORG_WAR_VOTES(1, 1), ORG_WARS(1, 1), SUBJECT(2, 2),
    PEACE(0, 1), PEACE_INBOX(0, 1), PEACE_RECEIVED(1, 2),
    BACKGROUND(0, 0), BACKGROUND_STAGE(1, 1), BACKGROUND_DETAIL(2, 2),
    CULTURE(0, 0), CULTURE_LIST(0, 0), CULTURE_MANAGE(0, 0), CULTURE_DETAIL(1, 1),
    TREASURY(1, 1), DOCTRINES(1, 1), ATTITUDES(2, 2), MEMES(1, 1);

    private final int min;
    private final int max;

    MenuPage(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public boolean accepts(int count) {
        return count >= min && count <= max;
    }

    public static MenuPage parse(String token) {
        return Arrays.stream(values()).filter(page -> page.id().equalsIgnoreCase(token))
                .findFirst().orElse(null);
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(MenuPage::id).toList();
    }
}
