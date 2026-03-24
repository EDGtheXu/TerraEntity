package org.confluence.terraentity.mixin.server;

import com.github.edg_thexu.cafelib.mixed.IBiomeInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ModifiableBiomeInfo.class, remap = false)
public class ModifiableBiomeInfoMixin {
    @WrapOperation(method = "applyBiomeModifiers", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/world/BiomeModifier;modify(Lnet/minecraft/core/Holder;Lnet/neoforged/neoforge/common/world/BiomeModifier$Phase;Lnet/neoforged/neoforge/common/world/ModifiableBiomeInfo$BiomeInfo$Builder;)V"))
    private void forbidLivingSpawn(BiomeModifier instance, Holder<Biome> biomeHolder, BiomeModifier.Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder, Operation<Void> original) {

        ((IBiomeInfo) builder.getMobSpawnSettings()).cafe_lib$setBiome(biomeHolder);
        original.call(instance, biomeHolder, phase, builder);

    }
}
