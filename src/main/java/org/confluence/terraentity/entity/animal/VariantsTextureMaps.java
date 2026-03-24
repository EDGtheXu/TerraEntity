package org.confluence.terraentity.entity.animal;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.random.SimpleWeightedRandomList;
import org.confluence.terraentity.TerraEntity;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 存储变种的TextureMap
 */
public class VariantsTextureMaps {
    public static final int GOLD_RARITY = 400;

    private VariantsTextureMaps() {
        throw new IllegalStateException("Utility class");
    }

    private static <T> Map<Integer, T> makeMap(List<T> values) {
        return new Int2ObjectOpenHashMap<>(IntStream.range(0, values.size())
                .mapToObj(i -> new AbstractMap.SimpleEntry<>(i, values.get(i)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private static Map<Integer, ResourceLocation> makeTextureMap(String prefix, List<ResourceLocation> values) {
        return new Int2ObjectOpenHashMap<>(IntStream.range(0, values.size())
                .mapToObj(i -> new AbstractMap.SimpleEntry<>(i, values.get(i).withPrefix(prefix)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private static Map<Integer, ResourceLocation> makeAnimalTextureMap(String folder, List<ResourceLocation> values) {
        return makeTextureMap("textures/entity/animal/" + folder + "/", values);
    }

    public static final int GOLD_BUTTERFLY_ID = 0;
    public static final int JULIA_BUTTERFLY_ID = 1;
    public static final int MONARCH_BUTTERFLY_ID = 2;
    public static final int PURPLE_EMPEROR_BUTTERFLY_ID = 3;
    public static final int RED_ADMIRAL_BUTTERFLY_ID = 4;
    public static final int SULPHUR_BUTTERFLY_ID = 5;
    public static final int TREE_NYMPH_BUTTERFLY_ID = 6;
    public static final int ULYSSES_BUTTERFLY_ID = 7;
    public static final int ZEBRA_SWALLOWTAIL_BUTTERFLY_ID = 8;
    public static final Map<Integer, ResourceLocation> butterflyTextures = makeAnimalTextureMap("butterfly", List.of(
            TerraEntity.space("gold_butterfly.png"),
            TerraEntity.space("julia_butterfly.png"),
            TerraEntity.space("monarch_butterfly.png"),
            TerraEntity.space("purple_emperor_butterfly.png"),
            TerraEntity.space("red_admiral_butterfly.png"),
            TerraEntity.space("sulphur_butterfly.png"),
            TerraEntity.space("tree_nymph_butterfly.png"),
            TerraEntity.space("ulysses_butterfly.png"),
            TerraEntity.space("zebra_swallowtail_butterfly.png")
    ));
    public static final SimpleWeightedRandomList<Integer> butterflyVariants = WeightedVariantAnimal.createFromRarity(butterflyTextures.size(), GOLD_BUTTERFLY_ID, GOLD_RARITY);

    public static final int BLACK_DRAGONFLY_ID = 0;
    public static final int BLUE_DRAGONFLY_ID = 1;
    public static final int GOLD_DRAGONFLY_ID = 2;
    public static final int GREEN_DRAGONFLY_ID = 3;
    public static final int ORANGE_DRAGONFLY_ID = 4;
    public static final int RED_DRAGONFLY_ID = 5;
    public static final int YELLOW_DRAGONFLY_ID = 6;
    public static final Map<Integer, ResourceLocation> dragonflyTextures = makeAnimalTextureMap("dragonfly", List.of(
            TerraEntity.space("black_dragonfly.png"),
            TerraEntity.space("blue_dragonfly.png"),
            TerraEntity.space("gold_dragonfly.png"),
            TerraEntity.space("green_dragonfly.png"),
            TerraEntity.space("orange_dragonfly.png"),
            TerraEntity.space("red_dragonfly.png"),
            TerraEntity.space("yellow_dragonfly.png")
    ));
    public static final SimpleWeightedRandomList<Integer> dragonflyVariants = WeightedVariantAnimal.createFromRarity(dragonflyTextures.size(), GOLD_DRAGONFLY_ID, GOLD_RARITY);

    public static final int BLUE_FAIRY_ID = 2;
    public static final int GREEN_FAIRY_ID = 1;
    public static final int PINK_FAIRY_ID = 0;
    public static final Map<Integer, ResourceLocation> fairyTextures = makeAnimalTextureMap("fairy", List.of(
            TerraEntity.space("blue_fairy.png"),
            TerraEntity.space("green_fairy.png"),
            TerraEntity.space("pink_fairy.png")
    ));
    public static final SimpleWeightedRandomList<Integer> fairyVariants = WeightedVariantAnimal.createAverage(fairyTextures.size());

    public static final int COMMON_FEALING_ID = 0;
    public static final Map<Integer, ResourceLocation> fealingTextures = makeAnimalTextureMap("fairy", List.of(
            TerraEntity.space("faeling.png")
    ));
    public static final SimpleWeightedRandomList<Integer> fealingVariants = WeightedVariantAnimal.createSingleton();

    public static final int GOLD_GRASSHOPPER_ID = 0;
    public static final int COMMON_GRASSHOPPER_ID = 1;
    public static final Map<Integer, ResourceLocation> grasshopperTextures = makeAnimalTextureMap("grasshopper", List.of(
            TerraEntity.space("gold_grasshopper.png"),
            TerraEntity.space("grasshopper.png")
    ));
    public static final SimpleWeightedRandomList<Integer> grasshopperVariants = WeightedVariantAnimal.createFromRarity(grasshopperTextures.size(), GOLD_GRASSHOPPER_ID, GOLD_RARITY);

    public static final int GOLD_LADYBUG_ID = 0;
    public static final int COMMON_LADYBUG_ID = 1;
    public static final Map<Integer, ResourceLocation> ladybugTextures = makeAnimalTextureMap("ladybug", List.of(
            TerraEntity.space("gold_ladybug.png"),
            TerraEntity.space("ladybug.png")
    ));
    public static final SimpleWeightedRandomList<Integer> ladybugVariants = WeightedVariantAnimal.createFromRarity(ladybugTextures.size(), GOLD_LADYBUG_ID, GOLD_RARITY);

    public static final int BLACK_SCORPION_ID = 0;
    public static final int SCORPION_ID = 1;
    public static final Map<Integer, ResourceLocation> scorpionTextures = makeAnimalTextureMap("scorpion", List.of(
            TerraEntity.space("black_scorpion.png"),
            TerraEntity.space("scorpion.png")
    ));

    public static final int ENCHANTED_NIGHTCRAWLER_ID = 0;
    public static final int GOLD_WORM_ID = 1;
    public static final int COMMON_WORM_ID = 2;
    public static final Map<Integer, ResourceLocation> wormTextures = makeAnimalTextureMap("worm", List.of(
            TerraEntity.space("enchanted_nightcrawler.png"),
            TerraEntity.space("gold_worm.png"),
            TerraEntity.space("worm.png")
    ));
    public static final SimpleWeightedRandomList<Integer> wormVariants = WeightedVariantAnimal.createFromRarity(wormTextures.size(), GOLD_WORM_ID, GOLD_RARITY);
}
