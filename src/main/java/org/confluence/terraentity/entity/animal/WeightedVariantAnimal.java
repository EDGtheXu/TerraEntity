package org.confluence.terraentity.entity.animal;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.Map;

public class WeightedVariantAnimal extends SimpleVariantAnimal {
    private static final SimpleWeightedRandomList<Integer> DEFAULT = SimpleWeightedRandomList.<Integer>builder().add(0).build();
    private final SimpleWeightedRandomList<Integer> variants;

    public WeightedVariantAnimal(
            EntityType<? extends SimpleVariantAnimal> entityType,
            Level level,
            Map<Integer, ResourceLocation> texturesMap,
            SimpleWeightedRandomList<Integer> variants
    ) {
        super(entityType, level, texturesMap);
        this.variants = variants;
    }

    public static SimpleWeightedRandomList<Integer> createFromRarity(int variantSize, int specificVariant, int specificRarity) {
        if (variantSize < 2) {
            throw new IllegalArgumentException("Variant size can not less than 2, exactly is " + variantSize);
        }
        SimpleWeightedRandomList.Builder<Integer> builder = SimpleWeightedRandomList.builder();
        for (int variant = 0; variant < variantSize; variant++) {
            builder.add(variant, (variant == specificVariant ? variantSize : specificRarity) - 1);
        }
        return builder.build();
    }

    public static SimpleWeightedRandomList<Integer> createAverage(int variantSize) {
        SimpleWeightedRandomList.Builder<Integer> builder = SimpleWeightedRandomList.builder();
        for (int variant = 0; variant < variantSize; variant++) {
            builder.add(variant);
        }
        return builder.build();
    }

    public static SimpleWeightedRandomList<Integer> createSingleton() {
        return DEFAULT;
    }

    @Override
    protected void initVariant() {
        setVariant(variants.getRandomValue(random).orElse(0));
    }
}
