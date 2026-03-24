package org.confluence.terraentity.entity.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.util.AirRandomPos;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.confluence.lib.api.entity.Boss;
import org.confluence.terraentity.client.buffer.DebugBlocksHelper;
import org.confluence.terraentity.entity.ai.goal.behavior.BTBossTwoStageRoot;
import org.confluence.terraentity.entity.ai.goal.behavior.BTFactory;
import org.confluence.terraentity.entity.ai.goal.behavior.BTNode;
import org.confluence.terraentity.entity.ai.goal.behavior.BTRoot;
import org.confluence.terraentity.entity.ai.goal.behavior.composite.ParallelNode;
import org.confluence.terraentity.entity.ai.goal.behavior.composite.SequenceNode;
import org.confluence.terraentity.entity.ai.goal.behavior.condition.AngleLowerThanCondition;
import org.confluence.terraentity.entity.ai.goal.behavior.condition.Condition;
import org.confluence.terraentity.entity.ai.goal.behavior.condition.DistanceLowerThanCondition;
import org.confluence.terraentity.entity.ai.goal.behavior.decoration.RepeatUntilNode;
import org.confluence.terraentity.entity.ai.goal.behavior.leaf.ConditionAction;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.List;

public class PrimeEnderDragon extends BaseBehaviorTreeMob implements FlyingAnimal, Boss {
    public final double[][] positions = new double[64][6];
    public int posPointer = -1;

    private static float _turnSpeedBase = 0.7f;
    private static float _turnSpeedInertia = 0.2f;
    private float turnSpeed = _turnSpeedBase;

    public float yRotA;
    Vec3 targetPos;
    static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.model.new");
    static final RawAnimation FLY = RawAnimation.begin().thenLoop("fly");
    static final RawAnimation FLY2 = RawAnimation.begin().thenLoop("fly2");

    static final RawAnimation DOWN = RawAnimation.begin().thenPlay("down");
    static final RawAnimation DOWN2 = RawAnimation.begin().thenPlay("down2");

    static final RawAnimation SKILL1 = RawAnimation.begin().thenPlay("skill1");


    public PrimeEnderDragon(EntityType<? extends PrimeEnderDragon> type, Level level) {
        super(type, level);
        this.noPhysics = true;

    }

    @Override
    protected double getDefaultGravity() {
        return 0;
    }

    @Override
    protected void registerRandomStrollGoal() {
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 10, 1f));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);

    }

    @Override
    public void addSkills() {}

    @Override
    public boolean isFlying() {
        return false;
    }

    protected BTRoot<PrimeEnderDragon> createBT(){
        return new PrimeEnderDragonBT(this);
    }


    private static class PrimeEnderDragonBT extends BTBossTwoStageRoot<PrimeEnderDragon> {

        public PrimeEnderDragonBT(PrimeEnderDragon mob) {
            super(mob);
        }

        @Override
        protected BTNode createWonderBehavior() {
            return BTFactory.sequence()

                    .addChild(new RandomStrollAction())
                    .addChild(BTFactory.waitRandom(100, 200));

        }

        @Override
        protected Condition createStageCondition() {
            return ()->false;
        }

        @Override
        protected SequenceNode switchPre(SequenceNode sequence) {
            return BTFactory.sequence();
        }

        @Override
        protected SequenceNode switchPost(SequenceNode sequence) {
            return BTFactory.sequence();
        }

        @Override
        protected BTNode createStageOneAttack() {
            return BTFactory.sequence()
                    .addChild(BTFactory.wait(20))
                    // 技能一：冲向目标
                    .addChild(BTFactory.parallel(ParallelNode.Policy.REQUIRE_ONE, ParallelNode.Policy.REQUIRE_ALL)
                            // 每隔一段时间重新定位目标位置
                            .addChild(BTFactory.infinite(BTFactory.sequence()
                                    .addChild(BTFactory.wait(20))
                                    .addChild(new DashAction())
                            ))
                            // 直到满足条件后退出技能一
                            .addChild(new RepeatUntilNode(BTStatus.SUCCESS, new ConditionAction(
                                    Condition.and(new AngleLowerThanCondition(mob, Math.PI / 4), new DistanceLowerThanCondition(mob, 8))
                            )))
                    )
                    // 惯性冲刺一段时间
                    .addChild(new SetTurnSpeedAction(_turnSpeedInertia))
                    .addChild(BTFactory.wait(20))
                    .addChild(new SetTurnSpeedAction(_turnSpeedBase))
                    .addChild(new RandomStrollAction())
                    .addChild(BTFactory.wait(20))
                    // TODO 技能二：发射龙息弹
                    .addChild(new ShootDragonFireAction())


                    ;
        }

        @Override
        protected BTNode createStageTwoAttack() {
            return BTFactory.sequence();
        }

        private class DashAction extends BTNode {
            @Override
            public BTStatus execute() {
                if(PrimeEnderDragonBT.this.mob.getTarget() != null) {
                    PrimeEnderDragonBT.this.mob.targetPos = PrimeEnderDragonBT.this.mob.getTarget().position();
                    return BTStatus.SUCCESS;
                }

                return BTStatus.FAILURE;
            }
        }

        private class ShootDragonFireAction extends BTNode {
            @Override
            public BTStatus execute() {
                if(PrimeEnderDragonBT.this.mob.getTarget() != null) {
                    var proj = EntityType.DRAGON_FIREBALL.create(PrimeEnderDragonBT.this.mob.level());
                    if(proj != null) {
                        proj.setOwner(PrimeEnderDragonBT.this.mob);
                        proj.moveTo(PrimeEnderDragonBT.this.mob.position());
                        Vec3 targetPos = PrimeEnderDragonBT.this.mob.getTarget().getBoundingBox().getCenter();
                        proj.shoot(targetPos.x - proj.getX(), targetPos.y - proj.getY(), targetPos.z - proj.getZ(), 1, 0);
                        PrimeEnderDragonBT.this.mob.level().addFreshEntity(proj);
                    }
                    return BTStatus.SUCCESS;
                }
                return BTStatus.FAILURE;
            }
        }

        private class RandomStrollAction extends BTNode {
            @Override
            public BTStatus execute() {
                PrimeEnderDragonBT.this.mob.targetPos = AirRandomPos.getPosTowards(PrimeEnderDragonBT.this.mob, 30, 5, 1, PrimeEnderDragonBT.this.mob.blockPosition().getBottomCenter(), Mth.PI * 0.1f);
                return BTStatus.SUCCESS;
            }
        }

        private class SetTurnSpeedAction extends BTNode {
            private final float turnSpeed;
            public SetTurnSpeedAction(float turnSpeed) {
                this.turnSpeed = turnSpeed;
            }

            @Override
            public BTStatus execute() {
                PrimeEnderDragonBT.this.mob.turnSpeed = turnSpeed;
                return BTStatus.SUCCESS;
            }
        }

    }



    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isDeadOrDying()) {
            float f7 = (this.random.nextFloat() - 0.5F) * 8.0F;
            float f9 = (this.random.nextFloat() - 0.5F) * 4.0F;
            float f10 = (this.random.nextFloat() - 0.5F) * 8.0F;
            this.level()
                    .addParticle(ParticleTypes.EXPLOSION, this.getX() + (double)f7, this.getY() + 2.0 + (double)f9, this.getZ() + (double)f10, 0.0, 0.0, 0.0);
        } else {
            if (this.isNoAi()) {

            } else {
                if (this.posPointer < 0) {
                    for (int i = 0; i < this.positions.length; i++) {
                        this.positions[i][0] = this.getYRot();
                        this.positions[i][1] = this.getX();
                        this.positions[i][2] = this.getY();
                        this.positions[i][3] = this.getZ();
                    }
                }

                if (++this.posPointer == this.positions.length) {
                    this.posPointer = 0;
                }

                this.positions[this.posPointer][0] = this.getYRot();
                this.positions[this.posPointer][1] = this.getX();
                this.positions[this.posPointer][2] = this.getY();
                this.positions[this.posPointer][3] = this.getZ();

                if (this.level().isClientSide) {
//                    if (this.lerpSteps > 0) {
//                        this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, this.lerpXRot);
//                        this.lerpSteps--;
//                    }

                } else {


                    if (targetPos != null) {
                        DebugBlocksHelper.Singleton().addDebugBlock(new BlockPos((int) targetPos.x,  (int) targetPos.y, (int) targetPos.z));
                        this.dragonMovement(targetPos);

                    }
                }
            }
        }

    }

    // 改自末影龙
    public void dragonMovement(Vec3 targetPos) {
        double deltaX = targetPos.x - this.getX();
        double deltaY = targetPos.y - this.getY();
        double deltaZ = targetPos.z - this.getZ();
        double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
        float flySpeed = 1.0f; // fly speed
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (horizontalDistance > 0.0) {
            deltaY = Mth.clamp(deltaY / horizontalDistance, -flySpeed, flySpeed);
        }

        // 应用垂直速度
        this.setDeltaMovement(this.getDeltaMovement().add(0.0, deltaY * 0.01, 0.0));
        this.setYRot(Mth.wrapDegrees(this.getYRot()));

        // 计算目标方向向量
        Vec3 directionToTarget = targetPos.subtract(this.getX(), this.getY(), this.getZ()).normalize();

        // 计算当前朝向向量
        Vec3 forwardDirection = new Vec3(
                Mth.sin(this.getYRot() * (float) (Math.PI / 180.0)),
                this.getDeltaMovement().y,
                Mth.cos(this.getYRot() * (float) (Math.PI / 180.0))
        ).normalize();

        // 计算朝向匹配度（0-1之间）
        float alignmentFactor = Math.max(((float) forwardDirection.dot(directionToTarget) + 0.5F) / 1.5F, 0.0F);

        // 水平方向转向控制
        if (Math.abs(deltaX) > 1.0E-5F || Math.abs(deltaZ) > 1.0E-5F) {
            float targetYaw = - (float)Mth.atan2(deltaX, deltaZ) * (180.0F / (float)Math.PI);

            float yawError = Mth.clamp(Mth.wrapDegrees(targetYaw - this.getYRot()), -50.0F, 50.0F);
            this.yRotA *= 0.8F;
            this.yRotA = this.yRotA + yawError * this.getTurnSpeed();
            this.setYRot(this.getYRot() + this.yRotA * 0.1F);
        }

        // 动态移动速度系数（距离目标越近，移动越慢）
        float proximityFactor = (float)(2.0 / (distanceSquared + 1.0));
        float baseSpeed = 0.06F;
//        float movementSpeed = baseSpeed * (alignmentFactor * proximityFactor + (1.0F - proximityFactor));
        float movementSpeed = baseSpeed * (alignmentFactor + 1.5F );
        this.moveRelative(movementSpeed, new Vec3(0.0, 0.0, 1.0));

        if (this.isInWall()) {
            this.move(MoverType.SELF, this.getDeltaMovement().scale(0.8F));
        } else {
            this.move(MoverType.SELF, this.getDeltaMovement());
        }

        Vec3 velocityDirection = this.getDeltaMovement().normalize();
        double directionMatch = 0.8 + 0.15 * (velocityDirection.dot(forwardDirection) + 1.0) / 2.0;
        this.setDeltaMovement(this.getDeltaMovement().multiply(directionMatch, 0.91F, directionMatch));

    }
    // 改自末影龙
    /**
     * 返回带有移动偏移的双[3]阵列，用于计算尾巴/颈部后方位置。[0] = 偏航偏移，[1] = y 偏移，[2] = 未使用，始终为0。参数：缓冲区索引偏移、部分刻。
     */
    public double[] getLatencyPos(int bufferIndexOffset, float partialTicks) {
        if (this.isDeadOrDying()) {
            partialTicks = 0.0F;
        }

        partialTicks = 1.0F - partialTicks;
        int i = this.posPointer - bufferIndexOffset & 63;
        int j = this.posPointer - bufferIndexOffset - 1 & 63;

        double[] adouble = new double[6];
        double d0 = this.positions[i][0];
        double d1 = Mth.wrapDegrees(this.positions[j][0] - d0);
        adouble[0] = d0 + d1 * (double)partialTicks;

//        d0 = this.positions[i][1];
//        d1 = this.positions[j][1] - d0;
//        adouble[1] = d0 + d1 * (double)partialTicks;
//
//        d0 = this.positions[i][2];
//        d1 = this.positions[j][2] - d0;
//        adouble[2] = d0 + d1 * (double)partialTicks;
//
//        d0 = this.positions[i][3];
//        d1 = this.positions[j][3] - d0;
//        adouble[3] = d0 + d1 * (double)partialTicks;
//
//        // 计算XZ平面法向量
//        d0 = this.positions[i][4];
//        d1 = this.positions[j][4] - d0;
//        adouble[4] = d0 + d1 * (double)partialTicks;
//
//        d0 = this.positions[i][5];
//        d1 = this.positions[j][5] - d0;
//        adouble[5] = d0 + d1 * (double)partialTicks;


//        adouble[3] = Mth.lerp(partialTicks, this.positions[i][2], this.positions[j][2]);

        return adouble;
    }
    public float getTurnSpeed() {
        float f = (float)this.getDeltaMovement().horizontalDistance() + 1.0F;
        float f1 = Math.min(f, 40.0F);
        return 0.2F / f1 / f;
    }


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5, state->{
            return state.setAndContinue(FLY);
        }));
    }
}
