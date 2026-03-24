package org.confluence.terraentity.entity.proj;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.EventHooks;
import org.confluence.terraentity.api.entity.ICollisionAttackEntity;
import org.confluence.terraentity.api.item.ILeftClickReceiver;
import org.confluence.terraentity.attachment.WeaponStorage;
import org.confluence.terraentity.item.YoyosItem;
import org.confluence.terraentity.registries.hit_effect.IEffectStrategy;
import org.confluence.terraentity.utils.TEUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/// 悠悠球
public class YoyosEntity extends Projectile implements ILeftClickReceiver, GeoEntity, ICollisionAttackEntity {
    int maxRetrieveTicks = 40;
    int retrieveTicks = 0;
    float maxRange = 10;
    @Nullable YoyosItem item;
    public ResourceLocation texture;

    protected static final EntityDataAccessor<ItemStack> DATA_WEAPON_ITEM = SynchedEntityData.defineId(YoyosEntity.class, EntityDataSerializers.ITEM_STACK);
    protected static final EntityDataAccessor<Boolean> DATA_IS_BAKING = SynchedEntityData.defineId(YoyosEntity.class, EntityDataSerializers.BOOLEAN);

    public YoyosEntity(EntityType<? extends YoyosEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = getOwner();
        if (owner == null || item == null) {
            discard();
            return;
        }

        this.xOld = getX();
        this.yOld = getY();
        this.zOld = getZ();

        // 存在时间
        if (this.tickCount > this.item.getExistTime() * 20) {
            entityData.set(DATA_IS_BAKING, true);
            this.noPhysics = true;
        }
        Vec3 lookVec = owner.getLookAngle().normalize();
        this.setXRot(0);
        this.setYRot(0);
        //this.yBodyRot = 0;
        Vec3 targetPos;

        float speedModifier = 1.0f;
        if (isBaking()) {
            targetPos = owner.position().add(0, owner.getBbHeight() * 0.5f, 0);
            if (!level().isClientSide) {
                if (targetPos.distanceTo(position()) < 0.5F) {
                    discard();
                }
            }
        } else {
            EntityHitResult result = TEUtils.getEyeTraceHitResult(owner, maxRange);
            if (result != null && this.canHitEntity(result.getEntity())) {
                targetPos = result.getEntity().position().add(0, result.getEntity().getBbHeight() * 0.5f, 0);
                if (this.position().distanceTo(targetPos) < 0.5F) {
                    this.noPhysics = true;
                }
                speedModifier = 4f;
            } else {
                targetPos = owner.getEyePosition().add(lookVec.scale(maxRange));
                this.noPhysics = false;
            }
        }
        Vec3 startPos = position();
        Vec3 dist = targetPos.subtract(startPos);


        this.setDeltaMovement(dist.scale(0.2f * speedModifier));
        if (isBaking()) {
            this.retrieveTicks++;
            Vec3 force = dist.normalize().scale(this.retrieveTicks * 1.0f / this.maxRetrieveTicks);
            this.addDeltaMovement(force);
        }

        doCollisionAttack(this::canHitEntity, this::doHurtTarget);

        Entity entity = this.getOwner();
        if (this.level().isClientSide || (entity == null || !entity.isRemoved()) && this.level().hasChunkAt(this.blockPosition())) {

            HitResult hitresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity, ClipContext.Block.COLLIDER);
            if (hitresult.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hitresult)) {
                this.hitTargetOrDeflectSelf(hitresult);
            }

            this.checkInsideBlocks();
            Vec3 vec3 = this.getDeltaMovement();
            double d0 = this.getX() + vec3.x;
            double d1 = this.getY() + vec3.y;
            double d2 = this.getZ() + vec3.z;
            ProjectileUtil.rotateTowardsMovement(this, 0.2F);
            float f;
            if (!this.isInWater()) {
                f = 0.95F;
            } else {
                for (int i = 0; i < 4; ++i) {
                    this.level().addParticle(ParticleTypes.BUBBLE, d0 - vec3.x * 0.25, d1 - vec3.y * 0.25, d2 - vec3.z * 0.25, vec3.x, vec3.y, vec3.z);
                }
                f = 0.8F;
            }
            this.setDeltaMovement(vec3.add(vec3.normalize().scale(0.1)).scale(f));

            this.setPos(this.position().add(this.getDeltaMovement()));
        } else {
            this.discard();
        }

    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return (!(getOwner() instanceof LivingEntity living) || !target.isInvulnerableTo(getDamageSource(living))) &&
                TEUtils.projectileCanHurtEntityTest.test(this, target);
    }

    private boolean isBaking() {
        return entityData.get(DATA_IS_BAKING);
    }

    private void setBaking(boolean is) {
        entityData.set(DATA_IS_BAKING, is);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!isBaking()) {
            this.playSound(SoundEvents.WOOD_PLACE, 0.5f, 1.5f);
            Vec3 normal = Vec3.atLowerCornerOf(result.getDirection().getNormal()).normalize();
            this.setDeltaMovement(this.getDeltaMovement().add(normal.multiply(this.getDeltaMovement().multiply(normal)).multiply(-1, -1, -1)));
        }
        super.onHitBlock(result);
        if (level().isClientSide) {
            BlockPos blockpos = result.getBlockPos();
            BlockState blockstate = this.level().getBlockState(blockpos);
            Vec3 dir = this.getDeltaMovement().normalize().scale(2);
            Vec3 mid = new Vec3(blockpos.getX() + 0.5f, blockpos.getY() + 0.5f, blockpos.getZ() + 0.5f).add(Vec3.atLowerCornerOf(result.getDirection().getNormal()));
            this.level().addParticle((new BlockParticleOption(ParticleTypes.BLOCK, blockstate)).setPos(blockpos), mid.x, mid.y, mid.z, -dir.x, -dir.y, -dir.z);
            this.level().addParticle((new BlockParticleOption(ParticleTypes.BLOCK, blockstate)).setPos(blockpos), mid.x, mid.y, mid.z, -dir.x, -dir.y, -dir.z);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_WEAPON_ITEM, ItemStack.EMPTY).define(DATA_IS_BAKING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_WEAPON_ITEM.equals(key)) {
            if (getWeaponItem().getItem() instanceof YoyosItem yoyo) {
                this.item = yoyo;
                this.texture = item.getTexture();
                this.maxRange = item.getMaxRange();
            }
        }
    }

    public void setWeaponItem(ItemStack itemStack) {
        this.entityData.set(DATA_WEAPON_ITEM, itemStack);
    }

    @Override
    public @NotNull ItemStack getWeaponItem() {
        return this.entityData.get(DATA_WEAPON_ITEM);
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        Entity owner = getOwner();
        if (owner != null) {
            WeaponStorage data = WeaponStorage.of(owner);
            data.yoyosEntity = null;
            if (owner instanceof Player player) {
                player.getCooldowns().removeCooldown(item);

                if (WeaponStorage.of(player).leftClicking) {
                    item.onLeftClick(player, item.getDefaultInstance());
                }
            }
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /* Geo API */

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public void onReceiveLeftClick(Player player, ItemStack itemStack) {
        setBaking(false);
        this.noPhysics = false;
        this.retrieveTicks = 0;
    }

    @Override
    public void onReceiveLeftRelease(Player player, ItemStack itemStack) {
        setBaking(true);
        this.noPhysics = true;
    }

    @Override
    public void onReceiveWhellScroll(Player player, ItemStack itemStack, int scrollAmount) {
        this.maxRange = Mth.clamp(this.maxRange + scrollAmount, 1, ((YoyosItem) itemStack.getItem()).getMaxRange());
    }

    /* Collision Attack API */

    CollisionProperties collisionProperties = new CollisionProperties(5, 5, 0.75f);

    public CollisionProperties getCollisionProperties() {
        return collisionProperties;
    }

    @Override
    public boolean shouldDoCollision() {
        return true;
    }

    public void doCollisionAttack(Predicate<Entity> filter, Consumer<Entity> attackCallback) {
        if (!this.shouldDoCollision() || this.collision$getSelf().level().isClientSide) return;
        CollisionProperties properties = this.getCollisionProperties();
        properties.reduceAttackInterval();
        if (this.canCollisionHurt() && !this.collision$getSelf().level().isClientSide && properties.canAttack()) {
            // 包围盒检测造成伤害
            List<Entity> entities = this.collision$getSelf().level().getEntities(this.collision$getSelf(), this.collision$getSelf().getBoundingBox().inflate(properties.attackRangeExtent), e -> e != this.collision$getSelf());
            if (!entities.isEmpty()) {
                for (var e : entities) {
                    if (filter.test(e)) {
                        attackCallback.accept(e);
                        properties.rewind();
                    }
                }
            } else {
                properties.reDetect();
            }
        }
    }

    public boolean doHurtTarget(Entity entity) {
        if (summon_doHurtTarget(entity)) {
            LivingEntity target = null;
            if (entity instanceof LivingEntity living) {
                target = living;
            } else if (entity instanceof PartEntity<?> partEntity && partEntity.getParent() instanceof LivingEntity living) {
                target = living;
            }
            if (target != null) {
                IEffectStrategy effectStrategy = this.item.getEffectStrategy();
                if (effectStrategy != null) {
                    effectStrategy.getEffect().accept((LivingEntity) this.getOwner(), target);
                }
                ItemStack stack = getWeaponItem();
                if (getOwner() != null && getOwner() instanceof LivingEntity owner && stack != null) {
                    stack.hurtAndBreak(1, owner, EquipmentSlot.MAINHAND);
                }
                return true;
            }
        }
        return false;
    }

    public boolean summon_doHurtTarget(Entity entity) {
        if (getOwner() instanceof LivingEntity owner) {

            float f = 0;
            DamageSource damagesource = getDamageSource(owner);
            Level var5 = this.level();
            if (var5 instanceof ServerLevel) {
                f = item.getAttackDamage();
            }
            // 事件统一处理
//        f += (float) summon_getOwner().getAttributeValue(TEAttributes.MARK_DAMAGE);
            boolean flag = entity.hurt(damagesource, f);
            if (flag) {
                float f1 = (float) (owner.getAttributeValue(Attributes.ATTACK_KNOCKBACK) + 0.1f);
                if (f1 > 0.0F && entity instanceof LivingEntity livingentity) {
                    livingentity.knockback(f1 * 0.5F, Mth.sin(this.getYRot() * 0.017453292F), -Mth.cos(this.getYRot() * 0.017453292F));
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1.0, 0.6));
                }

                if (this.level() instanceof ServerLevel level) {
                    EnchantmentHelper.doPostAttackEffects(level, entity, damagesource);
                }

                owner.setLastHurtMob(entity);
//            asEntity().playAttackSound();
            }
            return flag;
        }
        return false;
    }

    protected DamageSource getDamageSource(LivingEntity owner) {
        return damageSources().mobAttack(owner);
    }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) {
        return false;
    }
}
