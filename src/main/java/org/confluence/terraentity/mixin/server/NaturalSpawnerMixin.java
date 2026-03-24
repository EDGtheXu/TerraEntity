package org.confluence.terraentity.mixin.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {

    @Inject(method = "isValidSpawnPostitionForType", at = @At("HEAD"))
    private static void check(ServerLevel pLevel, MobCategory pCategory, StructureManager pStructureManager, ChunkGenerator pGenerator, MobSpawnSettings.SpawnerData pData, BlockPos.MutableBlockPos pPos, double pDistance, CallbackInfoReturnable<Boolean> cir) {

//        var opt = pLevel.getBiome(pPos).unwrapKey();
//        if(opt.isPresent()) {
//            if(LivingSpawnForbidden.getInstance().checkForbidden(EntityType.getKey(pData.type), opt.get().location())) {
//                cir.setReturnValue(false);
//                cir.cancel();
//            }
//        }

    }
}
