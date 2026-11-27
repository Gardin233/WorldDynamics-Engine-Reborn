package com.thatguycy.worlddynamicsengine.model.background;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 描述一个阶段背景的标识、故事和技能增量，实际选择结果存入居民档案。
 */
public class RoleBackgroundDefinition {
    private final RoleBackgroundStage stage;
    private final String id;
    private final String title;
    private final String author;
    private final List<String> lore;
    private final Map<String, Integer> skillPoints;

    public RoleBackgroundDefinition(RoleBackgroundStage stage, String id, String title, List<String> lore, Map<String, Integer> skillLevelBonuses) {
        this(stage, id, title, "", lore, skillLevelBonuses);
    }

    public RoleBackgroundDefinition(RoleBackgroundStage stage, String id, String title, String author,
                                   List<String> lore, Map<String, Integer> skillPoints) {
        this.stage = stage;
        this.id = id;
        this.title = title;
        this.author = author == null ? "" : author;
        this.lore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        this.skillPoints = skillPoints == null ? new LinkedHashMap<>() : new LinkedHashMap<>(skillPoints);
    }

    public RoleBackgroundDefinition(String id, String title, List<String> lore,
                                   Map<String, Integer> skillPoints) {
        this(null, id, title, "", lore, skillPoints);
    }

    public RoleBackgroundStage getStage() {
        return stage;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public List<String> getLore() {
        return new ArrayList<>(lore);
    }

    public Map<String, Integer> getSkillLevelBonuses() {
        return getSkillPoints();
    }

    public Map<String, Integer> getSkillPoints() {
        return new LinkedHashMap<>(skillPoints);
    }
}
