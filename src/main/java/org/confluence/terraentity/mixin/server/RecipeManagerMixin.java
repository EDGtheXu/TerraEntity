package org.confluence.terraentity.mixin.server;

import com.github.edg_thexu.cafelib.data.pack.resources.PreReloader;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {

    @WrapOperation(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z"))
    private boolean apply(String instance, String prefix, Operation<Boolean> original, @Local ResourceLocation resourceLocation) {
        if(PreReloader.getInstance().recipeForbidden.check(resourceLocation.toString())) {
            return true;
        }
        return original.call(instance, prefix);
    }
}
