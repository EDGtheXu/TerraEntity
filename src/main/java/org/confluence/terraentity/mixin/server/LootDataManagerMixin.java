package org.confluence.terraentity.mixin.server;

import com.github.edg_thexu.cafelib.data.pack.resources.PreReloader;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

@Mixin(SimpleJsonResourceReloadListener.class)
public class LootDataManagerMixin {
    @WrapOperation(method = "scanDirectory", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static <K, V> V forbidRecipeMixin(Map<K, V> instance, K k, V v, Operation<V> original, @Local(argsOnly = true) String name) {
        if(name.equals("loot_table")) {
            if(PreReloader.getInstance().lootTableForbidden.check(k.toString())) {
                return null;
            }
        }
        return original.call(instance, k, v);
    }
}
