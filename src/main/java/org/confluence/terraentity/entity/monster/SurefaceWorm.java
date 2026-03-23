package org.confluence.terraentity.entity.monster;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.confluence.lib.api.entity.IDiscardWhenRespawnEntity;
import org.confluence.terraentity.entity.ai.goal.MutableRangeNearestAttackableTargetGoal;
import org.confluence.terraentity.entity.monster.prefab.AttributeBuilder;
import org.confluence.terraentity.init.TESounds;
import org.jetbrains.annotations.NotNull;

public class SurefaceWorm<S extends BaseWormPart> extends BaseWorm<S> implements IDiscardWhenRespawnEntity {

    public SurefaceWorm(EntityType<? extends SurefaceWorm> type, Level level, AttributeBuilder builder) {
        super(type, level, builder);
    }
    @Override
    protected S createPart(int index) {
        return (S) createSimplePart(this, index);
    }
    @Override
    public boolean hasLineOfSight(Entity entity) {
        return true;
    }

    @Override
    public double wrapWanderHeight(Vec3 pos){
        double y = Math.max(pos.y, 63);
        return y + 2;
    }
}
