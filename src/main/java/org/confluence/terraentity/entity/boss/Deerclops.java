package org.confluence.terraentity.entity.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.confluence.terraentity.api.entity.Boss;
import org.confluence.terraentity.data.mappeddata.BossSkillMapDatas;
import org.confluence.terraentity.entity.ai.goal.behavior.BTFactory;
import org.confluence.terraentity.entity.ai.goal.behavior.BTNode;
import org.confluence.terraentity.entity.ai.goal.behavior.BTRoot;
import org.confluence.terraentity.entity.ai.goal.behavior.composite.ParallelNode;
import org.confluence.terraentity.entity.ai.goal.behavior.condition.Condition;
import org.confluence.terraentity.entity.ai.goal.behavior.condition.TargetExistCondition;
import org.confluence.terraentity.entity.ai.goal.behavior.leaf.LandRandomStrollAction;
import org.confluence.terraentity.entity.ai.goal.behavior.leaf.MoveToTargetAction;
import org.confluence.terraentity.entity.util.SharedFlagController;
import org.confluence.terraentity.init.entity.TEBossEntities;
import org.confluence.terraentity.init.entity.TEProjectileEntities;
import org.confluence.terraentity.registries.mappeddata.MappedDataTypes;
import org.confluence.terraentity.utils.TEUtils;
import org.confluence.terraentity.utils.TaskScheduler;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

public class Deerclops extends AbstractTerraBossBase implements Boss {

    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("Walk");
    private static final RawAnimation STAND = RawAnimation.begin().thenLoop("Stand");
    private static final RawAnimation ICE = RawAnimation.begin().thenPlay("Ice");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("Roar");
    private static final RawAnimation ROARING = RawAnimation.begin().thenLoop("Roaring");
    public static final EntityDataAccessor<Integer> DATA_SHARE_FLAG = SynchedEntityData.defineId(Deerclops.class, EntityDataSerializers.INT);
    DeerSharedFlagController sharedFlagController;
    BlockPos destroyChestPos;

    TaskScheduler scheduler;

    SkillParams skillParams;

    int attackDamage;
    int attackRange;
    int rangeDamage;
    int thrownIceCount;

    public Deerclops(EntityType<? extends AbstractTerraBossBase> type, Level level) {
        super(type, level);
        this.sharedFlagController = new DeerSharedFlagController(this.entityData, DATA_SHARE_FLAG);
        setNoGravity(false);
        scheduler = new TaskScheduler(0);
        this.skillParams = MappedDataTypes.BOSS_SKILL_MAP_DATAS.get().getData(BossSkillMapDatas.DEERCLOPS_PARAMS);
        this.attackDamage = skillParams.attackDamage;
        this.attackRange = skillParams.attackRange;
        this.rangeDamage = skillParams.rangeDamage;
        this.thrownIceCount = skillParams.thrownIceCount;
        this.xpReward = skillParams.xpReward;
    }

    public Deerclops(Level level) {
        this(TEBossEntities.DEERCLOPS.get(), level);
    }

    public record SkillParams(int xpReward, int attackDamage, int attackRange, int rangeDamage, int thrownIceCount) {
        public static Codec<SkillParams> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("xp_reward").forGetter(SkillParams::xpReward),
                Codec.INT.fieldOf("attack_damage").forGetter(SkillParams::attackDamage),
                Codec.INT.fieldOf("attack_range").forGetter(SkillParams::attackRange),
                Codec.INT.fieldOf("range_damage").forGetter(SkillParams::rangeDamage),
                Codec.INT.fieldOf("thrown_ice_count").forGetter(SkillParams::thrownIceCount)
        ).apply(instance, SkillParams::new));

        public static SkillParams getDefaultParams(){
            return new SkillParams(1500,10, 10, 10, 2);
        }
    }


    private static class DeerSharedFlagController extends SharedFlagController {
        SharedFlag attackFlag = this.registerFlag();
        SharedFlag roarFlag = this.registerFlag();
        SharedFlag roaringFlag = this.registerFlag();

        public DeerSharedFlagController(SynchedEntityData entityData, EntityDataAccessor<Integer> DATA_SHARE_FLAG) {
            super(entityData, DATA_SHARE_FLAG);
        }
        private boolean isAttacking(){
            return this.getFlag(attackFlag);
        }
        private boolean isRoar(){
            return this.getFlag(roarFlag);
        }
        private boolean isRoaring(){
            return this.getFlag(roaringFlag);
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(5, new BTGoal(this));

    }

    @Override
    public void tick(){
        super.tick();
        if(!level().isClientSide) {
            this.scheduler.tick(1L);
        }

    }

    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        return new GroundPathNavigation(this, level){
            @Override
            public boolean isStableDestination(@NotNull BlockPos pos) {
                BlockPos blockpos = pos.below();
                BlockState state = this.level.getBlockState(blockpos);
                return state.isSolidRender(this.level, blockpos) || state.is(Tags.Blocks.GLASS_BLOCKS);
            }
        };
    }

    @Override
    protected void registerRandomStrollGoal(){
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 10, 1f));
    }

    @Override
    protected MoveControl createMoveControl() {
        return new MoveControl(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SHARE_FLAG, 0);

    }


    private static class WalkToChestGoal extends BTNode {
        Deerclops mob;
        BlockPos targetPos;

        private WalkToChestGoal(Deerclops mob) {
            this.mob = mob;
        }

        @Override
        public BTStatus execute() {
            if(this.mob.getTarget() != null) {
                return BTStatus.FAILURE;
            }

            if(this.targetPos != null) {
                // 当有攻击目标时
                if(!(this.mob.level().getBlockEntity(targetPos) instanceof ChestBlockEntity)) {
                    // 当箱子被销毁
                    this.targetPos = null;
                    return BTStatus.FAILURE;
                }

                // 正在向别的目标移动时，向目标移动
                this.mob.navigation.moveTo(this.targetPos.getX(), this.targetPos.getY(), this.targetPos.getZ(), 1.0);

                double distance = this.mob.distanceToSqr(this.targetPos.getX(), this.targetPos.getY(), this.targetPos.getZ());
                if(distance <= 20) {
                    mob.destroyChestPos = this.targetPos;
                    this.targetPos = null;
                    return BTStatus.SUCCESS;
                }
                return BTStatus.RUNNING;
            }

            this.targetPos = TEUtils.findNearbyBlockEntity(this.mob.level(), this.mob.blockPosition(), 1,(pos, entity)-> entity instanceof ChestBlockEntity);
            if(this.targetPos != null) {
                return BTStatus.RUNNING;
            }

            return BTStatus.FAILURE;
        }

    }

    private static class DestroyChestGoal extends BTNode {
        Deerclops mob;
        int animTick;
        private DestroyChestGoal(Deerclops mob) {
            this.mob = mob;
        }

        @Override
        public BTStatus execute() {
            if(mob.destroyChestPos == null) {
                return BTStatus.FAILURE;
            }
            animTick++;
            if(animTick == 7) {
                // TODO 技能
                if(this.mob.level().getBlockEntity(this.mob.destroyChestPos) instanceof ChestBlockEntity) {
                    this.mob.level().setBlock(this.mob.destroyChestPos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            if(animTick > 15) {
                return BTStatus.SUCCESS;
            }
            return BTStatus.RUNNING;
        }

        @Override
        public void start() {
            super.start();
            animTick = 0;
            mob.sharedFlagController.setFlag(mob.sharedFlagController.attackFlag, true);
            mob.triggerAnim("Controller", "Ice");
        }

        @Override
        public void stop() {
            super.stop();
            animTick = 0;
            mob.sharedFlagController.setFlag(mob.sharedFlagController.attackFlag, false);
            this.mob.destroyChestPos = null;
            mob.stopTriggeredAnim("Controller", "Ice");
        }
    }


    private static class BTGoal extends BTRoot {

        Deerclops mob;
        public BTGoal(Deerclops mob) {
            this.mob = mob;

        }

        @Override
        protected @NotNull BTNode createBehaviorTree() {
            return BTFactory.infinite(BTFactory.selector()
                    .addWithCondition(new TargetExistCondition(mob), BTFactory.sequence()
                            .addChild(BTFactory.withTimer(10, new RoarPre()))
                            .addChild(BTFactory.withTimer(10, new Roaring()))
                            .addChild(BTFactory.condition(new TargetExistCondition(mob), BTFactory.infinite(BTFactory.sequence()
                                    .addChild(BTFactory.parallel(ParallelNode.Policy.REQUIRE_ONE, ParallelNode.Policy.REQUIRE_ONE)
                                            .addChild(new MoveToTargetAction(this.mob, 7, 20))
                                            .addChild(BTFactory.wait(100)))
                                    .addChild(BTFactory.withTimer(15, new IceAttack()))
                            )))
                    )
                    .addWithCondition(Condition.not(new TargetExistCondition(mob)), BTFactory.infinite(BTFactory.selector()
                            .addChild(BTFactory.sequence()
                                    .addChild(new WalkToChestGoal(mob))
                                    .addChild(new DestroyChestGoal(mob))
                            )
                            .addChild(new LandRandomStrollAction(mob, 1.0f, 50))
                    ))
            );
        }

        private class IceAttack extends BTNode {

            int tick = 0;
            @Override
            public void start() {
                super.start();
                BTGoal.this.mob.sharedFlagController.setFlag(BTGoal.this.mob.sharedFlagController.attackFlag, true);
                mob.navigation.stop();
                mob.triggerAnim("Controller", "Ice");
                tick = 0;
            }

            @Override
            public void stop() {
                super.stop();
                BTGoal.this.mob.sharedFlagController.setFlag(BTGoal.this.mob.sharedFlagController.attackFlag, false);
                mob.stopTriggeredAnim("Controller", "Ice");
                tick = 0;
            }

            @Override
            public BTStatus execute() {
                tick++;

                if(mob.getTarget() == null) {
                    return BTStatus.SUCCESS;
                }

                if(tick == 12 ) {
                    if(mob.getTarget().distanceTo(mob) >= mob.attackRange) {
                        // 远程攻击
                        for(int i=0;i<mob.thrownIceCount;i++) {
                            var entity = TEProjectileEntities.THROWN_ICE_PROJECTILE.get().create(mob.level());
                            if(entity != null) {
                                entity.setPos(
                                        mob.getX() + mob.getRandom().nextDouble() * 2 - 1,
                                        mob.getRandom().nextDouble() * 2 + 1,
                                        mob.getZ()+ mob.getRandom().nextDouble() * 2 - 1);
                                entity.setOwner(mob);
                                entity.setDamage(mob.rangeDamage);
                                mob.level().addFreshEntity(entity);
                            }
                        }
                    }else{
                        // 近战技能
                        for(int i=0;i<mob.attackRange;i++) {
                            int finalI = i;
                            mob.scheduler.schedule(()->{
                                for(int j=0;j < finalI * 2 + 1;j++){

                                    mob.createMeleeIcePillar(j-3, Math.max(j, 5), mob.position(), TEUtils.rotToDir(mob.getYRot(), mob.getXRot()).multiply(1,0,1));
                                }
                            }, i);
                        }

                    }

                }

                return BTStatus.RUNNING;
            }
        }


        private class RoarPre extends BTNode {

            @Override
            public void start() {
                super.start();
                BTGoal.this.mob.sharedFlagController.setFlag(BTGoal.this.mob.sharedFlagController.roarFlag, true);
                mob.triggerAnim("Controller", "Roar");
            }

            @Override
            public void stop() {
                super.stop();
                BTGoal.this.mob.sharedFlagController.setFlag(BTGoal.this.mob.sharedFlagController.roarFlag, false);
                mob.stopTriggeredAnim("Controller", "Roar");
            }

            @Override
            public BTStatus execute() {
                return BTStatus.RUNNING;
            }
        }

        private class Roaring extends BTNode {

            @Override
            public void start() {
                super.start();
                BTGoal.this.mob.sharedFlagController.setFlag(BTGoal.this.mob.sharedFlagController.roaringFlag, true);
                mob.triggerAnim("Controller", "Roaring");
            }

            @Override
            public void stop() {
                super.stop();
                BTGoal.this.mob.sharedFlagController.setFlag(BTGoal.this.mob.sharedFlagController.roaringFlag, false);
                mob.stopTriggeredAnim("Controller", "Roaring");
            }

            @Override
            public BTStatus execute() {
                return BTStatus.RUNNING;
            }
        }

    }

    protected void createMeleeIcePillar(int i,int horizon,  Vec3 center, Vec3 direction){
        var entity = TEProjectileEntities.ICE_PILLAR.get().create(this.level());
        if(entity != null) {
            Vec3 end = center.add(direction.scale(i)).offsetRandom(this.random, 0.5f);
            Vec3 horizontal = direction.cross(new Vec3(0,1,0)).normalize();
            end = end.add(horizontal.scale((this.random.nextDouble() - 0.5) * horizon));
            entity.setPos(end);
            entity.setOwner(this);
            entity.setDamage(this.attackDamage);
            this.level().addFreshEntity(entity);
        }

    }

    @Override
    public void addSkills() {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "Controller", 5,
                state -> state.setAndContinue(state.isMoving() ? WALK : STAND))
                .triggerableAnim("Ice", ICE)
                .triggerableAnim("Roar", ROAR)
                .triggerableAnim("Roaring", ROARING)
        );

    }
}
