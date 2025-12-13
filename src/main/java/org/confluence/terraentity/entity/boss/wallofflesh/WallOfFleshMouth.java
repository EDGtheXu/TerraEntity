package org.confluence.terraentity.entity.boss.wallofflesh;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.confluence.terraentity.entity.ai.goal.MutableRangeNearestAttackableTargetGoal;
import org.confluence.terraentity.entity.monster.BaseWorm;
import org.confluence.terraentity.entity.monster.BaseWormPart;
import org.confluence.terraentity.entity.monster.prefab.AbstractPrefab;
import org.confluence.terraentity.init.TETags;
import org.confluence.terraentity.init.entity.TEMonsterEntities;

@SuppressWarnings("all")
public class WallOfFleshMouth extends WallOfFleshPart {
    private int pendingSpawns = 0;
    private int spawnInterval = 0;

    private static final int BASE_SUMMON_CD = 800;
    private int summonCDAll = BASE_SUMMON_CD + random.nextInt(400) - 200;
    private int summonCD = summonCDAll;


    public WallOfFleshMouth(WallOfFlesh parentMob, String name, float width, float height) {
        super(parentMob, name, width, height);
    }

    @Override
    public float getYRot() {
        if(this.parentMob!=null)return this.parentMob.getYRot();
        return super.getYRot();
    }

    @Override
    public float getXRot() {
        if(this.parentMob!=null)return this.parentMob.getXRot();
        return super.getXRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void tickPart(double offsetX, double offsetY, double offsetZ) {
        this.findTarget();
        if(this.target == null || !this.target.isAlive() || this.parentMob == null || !this.parentMob.isAlive())return;
        if (spawnInterval > 0) {
            if (--spawnInterval <= 0 && pendingSpawns > 0) {
                spawnLeech(target);
                pendingSpawns--;
                spawnInterval = pendingSpawns > 0 ? 10 : 0; // 恢复固定间隔
            }
        }
        if (--summonCD <= 0) {
            summonCD = summonCDAll + random.nextInt(200) - 100;
            float healthPercent = parentMob.getHealthPercentage();
            int count;
            if (healthPercent > 0.5F) {
                count = 1;
            } else {
                float scaleFactor = Mth.clamp((0.5F - healthPercent) / 0.5F, 0.0F, 1.0F);

                count = 1 + (int) (scaleFactor * 4);
            }
            count = Mth.clamp(count, 1, 5);

            if (pendingSpawns == 0) {
                pendingSpawns = count;
                spawnInterval = 10; // 恢复固定间隔
            }
        }
    }

    private void spawnLeech(LivingEntity target) {
        if (level() instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel) level();
            BaseWorm warm = new BaseWorm(TEMonsterEntities.LEECH.get(), this.level(), AbstractPrefab.WARM_BUILDER.get()){
                @Override
                protected BaseWormPart createPart(int index) {
                    return new BaseWormPart(this, index);
                }

                @Override
                protected int getSegmentCount() {
                    return 6;
                }

                @Override
                public boolean hurt(DamageSource source, float amount) {
                    if(source.getEntity() != null && (source.getEntity().getType().is(TETags.EntityTypes.FLESH_ALLIANCE)))
                        return false;
                    return super.hurt(source, amount);
                }
                @Override
                public boolean canAttack(LivingEntity entity) {
                    return WallOfFleshMouth.this.parentMob.canAttack(entity);
                }
                @Override
                protected void registerGoals() {
                    super.registerGoals();
                    this.targetSelector.addGoal(2, new MutableRangeNearestAttackableTargetGoal<>(this, Player.class, false, LivingEntity::canBeSeenAsEnemy));
                }

                @Override
                protected float getMoveSpeedModifier() {
                    return 2.0f;
                }

                @Override
                public boolean fireImmune() {
                    return true;
                }
            };
            warm.setPos(position().add(getForward().normalize().scale(1)));
            warm.setTarget(target);
            serverLevel.addFreshEntity(warm);
        }
    }

    @Override
    public boolean isNoGravity(){ return true; }

    @Override
    public boolean shouldBeSaved(){
        if(this.parentMob != null)return this.parentMob.shouldBeSaved();
        return  false;
    }
}
