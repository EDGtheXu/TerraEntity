package com.github.edg_thexu.cafelib.mixed;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

public interface IBiomeInfo {

    void cafe_lib$setBiome(Holder<Biome> biome);

    Holder<Biome> cafe_lib$getBiomeHolder();

}
