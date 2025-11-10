package org.confluence.terraentity.entity.proj;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.confluence.terraentity.api.item.ILeftClickReceiver;
import org.confluence.terraentity.attachment.WeaponStorage;
import org.confluence.terraentity.entity.summon.AbstractSummonMob;
import org.confluence.terraentity.item.YoyosItem;
import org.confluence.terraentity.registries.hit_effect.IEffectStrategy;
import org.confluence.terraentity.utils.TEUtils;
import software.bernie.geckolib.animation.AnimatableManager;

/**
 * 悠悠球
 */
public class YoyosEntity extends AbstractSummonMob implements ILeftClickReceiver {
    boolean isBacking = false;
    int maxRetrieveTicks = 40;
    int retrieveTicks = 0;
    float maxRange = 10;
    YoyosItem item;
    public ResourceLocation texture;

    protected static final EntityDataAccessor<ItemStack> DATA_WEAPON_ITEM = SynchedEntityData.defineId(YoyosEntity.class, EntityDataSerializers.ITEM_STACK);

    public YoyosEntity(EntityType<? extends YoyosEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Override
    protected void registerGoals() {}

    @Override
    public void tick() {
        super.tick();
        Entity owner = getOwner();
        if (owner == null) {
            discard();
            return;
        }

        // 存在时间
        if (this.tickCount > this.item.getExistTime() * 20) {
            this.isBacking = true;
            this.noPhysics = true;
        }
        Vec3 lookVec = owner.getLookAngle().normalize();
        this.setXRot(0);
        this.setYRot(0);
        this.yBodyRot = 0;
        Vec3 targetPos;

        float speedModifier = 1.0f;
        if (this.isBacking) {
            targetPos = owner.position().add(0, owner.getBbHeight() * 0.5f, 0);
            if (!level().isClientSide) {
                if (targetPos.distanceTo(position()) < 0.5F) {
                    discard();
                }
            }
        } else {
            EntityHitResult result = TEUtils.getEyeTraceHitResult(owner, maxRange);
            if (result != null && this.canAttackTarget(result.getEntity())) {
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
        if (this.isBacking) {
            this.retrieveTicks++;
            Vec3 force = dist.normalize().scale(this.retrieveTicks * 1.0f / this.maxRetrieveTicks);
            this.addDeltaMovement(force);
        }

        // 骗方块让它以为自己被箭击中，用于破罐。
        // 还是建议把悠悠球本身改成射弹而不是召唤物...
        if (!level().isClientSide){
            Vec3 forwardP = this.position().add(this.getOwner().position().vectorTo(this.position()).normalize().multiply(0.5,0.5,0.5));
            BlockPos blockPos = BlockPos.containing(forwardP);
            BlockState blockstate = level().getBlockState(blockPos);
            BlockHitResult blockHitResult = new BlockHitResult(forwardP, this.getDirection(), blockPos, false);
            blockstate.onProjectileHit(level(), blockstate, blockHitResult,
                    new Arrow(level(), this.getOwner(), this.getOwner().getWeaponItem(), this.getOwner().getWeaponItem()));
        }
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    protected void playBlockFallSound() {}

    @Override
    public float summon_getKnockback(Entity attacker, DamageSource damageSource) {
        if (this.getOwner() == null) {
            return 0.0f;
        }
        return (float) getOwner().getAttributeValue(Attributes.ATTACK_KNOCKBACK) + 0.1f;
    }

    @Override
    public float summon_getAttackDamage(Entity entity, ServerLevel serverLevel, DamageSource damageSource) {
        if (this.getOwner() == null || !(getWeaponItem().getItem() instanceof YoyosItem yoyo)) {
            return 0.0f;
        }
        float f = (float) getOwner().getAttributeValue(Attributes.ATTACK_DAMAGE) + yoyo.getAttackDamage();
        f = EnchantmentHelper.modifyDamage(serverLevel, asEntity().getWeaponItem(), entity, damageSource, f);
        return f;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WEAPON_ITEM, ItemStack.EMPTY);
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
        } else if (DATA_OWNERUUID_ID.equals(key)) {
            Entity owner = getOwner();
            WeaponStorage data;
            if (owner != null) {
                data = WeaponStorage.of(owner);
                data.yoyosEntity = this;
            }
        }
    }

    public void setWeaponItem(ItemStack itemStack) {
        this.entityData.set(DATA_WEAPON_ITEM, itemStack);
    }

    @Override
    public ItemStack getWeaponItem() {
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

                if (WeaponStorage.of(player).leftClicking){
                    item.onLeftClick(player, item.getDefaultInstance());
                }
            }
        }
    }

    @Override
    public int getMaxHeadXRot() {
        return 85;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public void onReceiveLeftClick(Player player, ItemStack itemStack) {
        this.isBacking = false;
        this.noPhysics = false;
        this.retrieveTicks = 0;
    }

    @Override
    public void onReceiveLeftRelease(Player player, ItemStack itemStack) {
        this.isBacking = true;
        this.noPhysics = true;
    }

    @Override
    public void onReceiveWhellScroll(Player player, ItemStack itemStack, int scrollAmount) {
        this.maxRange = Mth.clamp(this.maxRange + scrollAmount, 1, ((YoyosItem) itemStack.getItem()).getMaxRange());
    }

    @Override
    public boolean canAttackTarget(Entity target) {
        Entity entity = getOwner();
        // 不能攻击主人
        if (entity == target) return false;

        if (!target.isAttackable()) {
            // 不可攻击的实体
            return false;
        }

        // 不能攻击坐骑
        return entity == null || !entity.isPassengerOfSameVehicle(target);
    }

    @Override
    public boolean shouldDoCollision() {
        return true;
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        if (super.doHurtTarget(entity)) {
            LivingEntity target = null;
            if (entity instanceof LivingEntity living) {
                target = living;
            } else if (entity instanceof PartEntity<?> partEntity && partEntity.getParent() instanceof LivingEntity living) {
                target = living;
            }
            if (target != null) {
                IEffectStrategy effectStrategy = this.item.getEffectStrategy();
                if (effectStrategy != null) {
                    effectStrategy.getEffect().accept(this.getOwner(), target);
                }
                ItemStack stack = getMainHandItem();
                if (getOwner() != null) {
                    stack.hurtAndBreak(1, getOwner(), EquipmentSlot.MAINHAND);
                }
                return true;
            }
        }
        return false;
    }
}
