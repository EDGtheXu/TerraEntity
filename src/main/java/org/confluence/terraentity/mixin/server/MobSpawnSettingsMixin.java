package org.confluence.terraentity.mixin.server;

import com.github.edg_thexu.cafelib.data.pack.resources.LivingSpawnForbidden;
import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Map;

@Mixin(MobSpawnSettings.class)
public class MobSpawnSettingsMixin {

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableMap;copyOf(Ljava/util/Map;)Lcom/google/common/collect/ImmutableMap;", ordinal = 0))
    private static ImmutableMap<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> modifySpawnerData(
            Map<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> kvMap,
            Operation<ImmutableMap<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>>> original) {
        // 这里只在world gen时，从文件加载群系信息时，修改生物生成
        if(LivingSpawnForbidden.dynamicBiomeId != null) {
            ImmutableMap.Builder<MobCategory, WeightedRandomList<MobSpawnSettings.SpawnerData>> newMapBuilder = new ImmutableMap.Builder<>();

            kvMap.forEach((mobCategory, spawnerData) -> {
                List<MobSpawnSettings.SpawnerData> spawnerDataList = spawnerData.unwrap().stream().filter(data->{
                    return !LivingSpawnForbidden.getInstance().checkForbidden(EntityType.getKey(data.type), LivingSpawnForbidden.dynamicBiomeId);
                }).toList();

                newMapBuilder.put(mobCategory, WeightedRandomList.create(spawnerDataList));

            });
            return newMapBuilder.build();
        }

        return original.call(kvMap);
    }
}

