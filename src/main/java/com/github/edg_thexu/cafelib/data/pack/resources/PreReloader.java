package com.github.edg_thexu.cafelib.data.pack.resources;

import com.github.edg_thexu.cafelib.CafeLib;
import com.github.edg_thexu.cafelib.api.event.AddPreloadResourceEvent;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.confluence.terraentity.utils.AdapterUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

public class PreReloader {

    public SinglePattern recipeForbidden;

    public SinglePattern lootTableForbidden;

    private static volatile PreReloader instance;
    public static PreReloader getInstance() {
        if (instance == null) {
            synchronized (PreReloader.class) {
                if (instance == null) {
                    instance = new PreReloader();
                }
            }
        }
        return instance;

    }

    private PreReloader() {
        this.recipeForbidden = new SinglePattern("forbidden/recipe");
        this.lootTableForbidden = new SinglePattern("forbidden/loottable");

    }

    public List<PreparableReloadListener> listeners() {
        List<PreparableReloadListener> listeners = new ArrayList<>();
        listeners.add(this.recipeForbidden);
        listeners.add(this.lootTableForbidden);
        listeners.addAll(AdapterUtils.postEvent(new AddPreloadResourceEvent()).getListeners());
        return listeners;
    }

    public static void reloadAsync(ResourceManager manager, Executor backgroundExecutor, Executor gameExecutor) {

        var barrier = new PreparableReloadListener.PreparationBarrier() {
            public <T> CompletableFuture<T> wait(@NotNull T backgroundResult) {
                return CompletableFuture.completedFuture(backgroundResult);
            }
        };

        List<CompletableFuture<Void>> allTask = PreReloader.getInstance().listeners().stream().map(
                        listener -> listener.reload(barrier, manager, InactiveProfiler.INSTANCE,
                                InactiveProfiler.INSTANCE, backgroundExecutor, Runnable::run))
                .toList();

        CompletableFuture<Void> allOf = CompletableFuture.allOf(allTask.toArray(new CompletableFuture[0]));
        allOf.join();

        System.out.println("预加载数据包完成");

    }

    public static class SinglePattern extends SimpleJsonResourceReloadListener {

        List<Pattern> patterns = new ArrayList<>();

        private record ModifierList(List<String> list) {
            private static final Codec<ModifierList> CODEC = Codec.STRING.listOf().xmap(ModifierList::new, ModifierList::list).fieldOf("values").codec();
        }

        public SinglePattern(String folderName) {
            super(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create(),folderName);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> resourceLocationJsonElementMap, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
            patterns.clear();
            List<String> forbiddenList = new ArrayList<>();
            resourceLocationJsonElementMap.forEach((location, jsonElement) -> {
                if(location.toString().equals("cafelib:example")) {
                    return;
                }
                DataResult<ModifierList> data = ModifierList.CODEC.parse(JsonOps.INSTANCE, jsonElement);
                if(data.error().isPresent()){
                    CafeLib.LOGGER.error("Error parsing {}: {}", location, data.error().get().message());
                }else{
                    forbiddenList.addAll(data.getOrThrow().list());
                }

            });
            patterns.addAll(forbiddenList.stream().map(Pattern::compile).toList());

        }

        public boolean check(String name) {
            for(Pattern pattern : patterns) {
                if(pattern.matcher(name).matches()) {
                    return true;
                }
            }
            return false;
        }
    }

}
