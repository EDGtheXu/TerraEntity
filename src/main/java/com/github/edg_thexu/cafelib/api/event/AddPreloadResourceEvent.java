package com.github.edg_thexu.cafelib.api.event;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;


import java.util.ArrayList;
import java.util.List;

public class AddPreloadResourceEvent extends Event implements IModBusEvent {
    private final List<PreparableReloadListener> listeners;
    public AddPreloadResourceEvent(){
        this.listeners = new ArrayList<>();
    }
    public AddPreloadResourceEvent add(PreparableReloadListener listener) {
        this.listeners.add(listener);
        return this;
    }

    public List<PreparableReloadListener> getListeners() {
        return listeners;
    }
}
