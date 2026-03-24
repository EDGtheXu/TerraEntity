package org.confluence.terraentity.mixin.server;

import com.github.edg_thexu.cafelib.data.pack.resources.LivingSpawnForbidden;
import com.github.edg_thexu.cafelib.mixed.IBiomeInfo;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobSpawnSettings.Builder.class)
public class MobSpawnSettingsBuilderMixin implements IBiomeInfo {

    @Unique
    Holder<Biome> cafe_lib$biomeHolder;


    @Override
    public void cafe_lib$setBiome(Holder<Biome> biome) {
        this.cafe_lib$biomeHolder = biome;

    }

    @Override
    public Holder<Biome> cafe_lib$getBiomeHolder() {
        return this.cafe_lib$biomeHolder;
    }

    @Inject(method = "addSpawn", at = @At("HEAD"), cancellable = true)
    private void forbidAddSpawn(MobCategory pClassification, MobSpawnSettings.SpawnerData pSpawner, CallbackInfoReturnable<MobSpawnSettings.Builder> cir) {
        if(this.cafe_lib$getBiomeHolder() != null && this.cafe_lib$getBiomeHolder().unwrapKey().isPresent()) {
            if(LivingSpawnForbidden.getInstance().checkForbidden(EntityType.getKey(pSpawner.type), this.cafe_lib$getBiomeHolder().unwrapKey().get().location())) {
                cir.cancel();
                cir.setReturnValue((MobSpawnSettings.Builder) (Object) this);
            }
        }
    }

}
