package com.thatguycy.worlddynamicsengine.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 描述模因与戒律态度之间的限制，用于筛选可选态度而非直接施加效果。
 */
public class IdeologyDoctrineMemeConstraint {
    private final String memeId;
    private final List<String> defaultAttitudeIds;
    private final Set<String> blockedAttitudeIds;

    public IdeologyDoctrineMemeConstraint(String memeId, Collection<String> defaultAttitudeIds, Collection<String> blockedAttitudeIds) {
        this.memeId = memeId == null ? "" : memeId.trim();
        this.defaultAttitudeIds = new ArrayList<>();
        if (defaultAttitudeIds != null) {
            for (String defaultAttitudeId : defaultAttitudeIds) {
                if (defaultAttitudeId != null && !defaultAttitudeId.isBlank()) {
                    this.defaultAttitudeIds.add(defaultAttitudeId.trim());
                }
            }
        }

        this.blockedAttitudeIds = new LinkedHashSet<>();
        if (blockedAttitudeIds != null) {
            for (String blockedAttitudeId : blockedAttitudeIds) {
                if (blockedAttitudeId != null && !blockedAttitudeId.isBlank()) {
                    this.blockedAttitudeIds.add(blockedAttitudeId.trim());
                }
            }
        }
    }

    public String getMemeId() {
        return memeId;
    }

    public List<String> getDefaultAttitudeIds() {
        return Collections.unmodifiableList(defaultAttitudeIds);
    }

    public Set<String> getBlockedAttitudeIds() {
        return Collections.unmodifiableSet(blockedAttitudeIds);
    }

    public boolean blocks(String attitudeId) {
        return attitudeId != null && blockedAttitudeIds.contains(attitudeId);
    }
}
