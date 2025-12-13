package org.confluence.terraentity.effect.harmful;

import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.confluence.terraentity.entity.boss.wallofflesh.WallOfFlesh;
import org.confluence.terraentity.entity.boss.wallofflesh.WallOfFleshMouth;
import org.confluence.terraentity.init.TEEffects;


public class TheTongueEffect extends MobEffect {
    private WallOfFleshMouth mouth;

    public TheTongueEffect() {
        super(MobEffectCategory.HARMFUL, 0xAB1122);
    }

    @Override
    public boolean applyEffectTick(LivingEntity living, int amplifier) {
        if(mouth !=null && mouth.isAlive() && mouth.parentMob!=null && mouth.parentMob.isAlive()) {

            WallOfFlesh wall = mouth.parentMob;
            Vec3 mouthPos = mouth.position();

            //当狂卷之舌减益激活时，若玩家和匹配的嘴间的欧几里得距离大于 200(原作187.5) 格，玩家会立即死亡
            double distanceToMouth = living.position().distanceTo(mouthPos);
            if (distanceToMouth > 200.0) {
                living.kill();
                return true;
            }

            if(!living.level().isClientSide && living.getBoundingBox().intersects(wall.getOutsideCollisionBox())&&!living.getBoundingBox().intersects(wall.getInsideBox())) {

                Vec3 targetPos = mouthPos.add(wall.getForward().scale(45));
                Vec3 toTarget = targetPos.subtract(living.position());
                double distance = toTarget.length();
                Vec3 dragDirection = toTarget.normalize();

                double speedFactor = Mth.clamp(distance / 15.0 + wall.getMoveSpeed()+0.35f, wall.getMoveSpeed() + 0.15f, wall.getMoveSpeed() + 0.5);
                Vec3 adjustedForce = dragDirection.scale(speedFactor);

                if ((distance <= 9.0F || !living.isAlive())) {
                    living.getActiveEffectsMap().remove(TEEffects.THE_TONGUE).getEffect();
                } else {
                    living.setDeltaMovement(living.getDeltaMovement().add(adjustedForce));
                    living.hurtMarked = true;

                    if (living.tickCount % 10 == 0) {
                        float damage = 2;
                        living.hurt(living.level().damageSources().mobAttack(mouth.parentMob), damage);
                    }
                }
        }else if(living.tickCount % 60 == 0){
                living.getActiveEffectsMap().remove(TEEffects.THE_TONGUE).getEffect();
            }
        }
       return true;
    }

    public void onEffectStarted(LivingEntity livingEntity, int amplifier) {
        super.onEffectStarted(livingEntity, amplifier);
        if(this.getWallOfFleshMouth() == null||!this.getWallOfFleshMouth().isAlive()) {
            livingEntity.hurt(livingEntity.level().damageSources().mobAttack(livingEntity), 4);
        }else livingEntity.hurt(livingEntity.level().damageSources().mobAttack(mouth.parentMob), 4);
    }

    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    public WallOfFleshMouth getWallOfFleshMouth() {
        return this.mouth;
    }

    public void setWallOfFleshMouth(WallOfFleshMouth mouth) {
        this.mouth = mouth;
    }
}
