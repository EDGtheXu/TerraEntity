package org.confluence.terraentity.mixin.server;

import com.github.edg_thexu.cafelib.data.pack.resources.LivingSpawnForbidden;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Decoder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(RegistryDataLoader.class)
public class RegistryDataLoaderMixin {
    @Inject(method = "load", at = @At("HEAD"))
    private static void preload(ResourceManager pResourceManager, RegistryAccess pRegistryAccess, List<RegistryDataLoader.RegistryData<?>> pRegistryData, CallbackInfoReturnable<RegistryAccess.Frozen> cir) {
        LivingSpawnForbidden.getInstance().preload(pResourceManager, Runnable::run);
    }

    // 由于codec，无法得到群系id，使用静态变量传入
    @WrapOperation(method = "loadContentsFromNetwork", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceKey;create(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/resources/ResourceKey;"))
    private static <T> ResourceKey<T> modifyResourceKey(ResourceKey<? extends Registry<T>> pRegistryKey, ResourceLocation pLocation, Operation<ResourceKey<T>> original) {
        ResourceKey<T> value =  original.call(pRegistryKey, pLocation);
        if(pRegistryKey.location().equals(Registries.BIOME.location()) ) {
            LivingSpawnForbidden.dynamicBiomeId = value.location();
        }
        return value;
    }

    @Inject(method = "loadContentsFromNetwork", at = @At("TAIL"))
    private static <E> void modifyRegistryContents(Map<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> elements, ResourceProvider resourceProvider, RegistryOps.RegistryInfoLookup registryInfoLookup, WritableRegistry<E> registry, Decoder<E> codec, Map<ResourceKey<?>, Exception> loadingErrors, CallbackInfo ci) {
        LivingSpawnForbidden.dynamicBiomeId = null;
    }
}
