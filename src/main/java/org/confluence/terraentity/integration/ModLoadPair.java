package org.confluence.terraentity.integration;

import org.confluence.lib.util.LibUtils;

public class ModLoadPair {
    private final String id;
    private final boolean loaded;

    public ModLoadPair(String key) {
        this.id = key;
        this.loaded = LibUtils.isModLoaded(key);
    }

    public boolean isLoaded() {
        return loaded;
    }

    public String getId() {
        return id;
    }
}
