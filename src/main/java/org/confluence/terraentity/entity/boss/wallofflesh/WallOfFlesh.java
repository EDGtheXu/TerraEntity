package org.confluence.terraentity.entity.boss.wallofflesh;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.confluence.terraentity.api.entity.Boss;
import org.confluence.terraentity.api.entity.IExtendedTracking;
import org.confluence.terraentity.effect.harmful.HorrifiedEffect;
import org.confluence.terraentity.entity.animal.VariantsTextureMaps;
import org.confluence.terraentity.entity.animal.WallOfFairy;
import org.confluence.terraentity.entity.boss.AbstractTerraBossBase;
import org.confluence.terraentity.entity.monster.TheHungry;
import org.confluence.terraentity.entity.monster.prefab.AbstractPrefab;
import org.confluence.terraentity.init.TEEffects;
import org.confluence.terraentity.init.TESounds;
import org.confluence.terraentity.init.entity.TEAnimals;
import org.confluence.terraentity.init.entity.TEBossEntities;
import org.confluence.terraentity.init.entity.TEMonsterEntities;
import org.confluence.terraentity.integration.ModChecker;
import org.confluence.terraentity.utils.CameraShakeData;
import org.confluence.terraentity.utils.CameraShakeManager;
import org.confluence.terraentity.utils.TEUtils;
import org.confluence.terraentity.utils.WorldChunksManager;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.confluence.terraentity.init.TEEntityDataSerializers.TUPLET_VEC3_INT_LIST_SERIALIZER;
import static org.confluence.terraentity.init.TEEntityDataSerializers.TUPLE_INT_VEC3_LIST_SERIALIZER;

public class WallOfFlesh extends AbstractTerraBossBase implements Boss, IExtendedTracking {
    boolean genSegments = true;
    int genTick = 20;
    boolean shouldMove = true;
    final float baseMoveSpeed = 0.125f;
    Vec3 InitPos = Vec3.ZERO;

    public AABB insideCollisionBox;
    public AABB outsideCollisionBox;

    private final int gridSizeX = 40;
    private final int gridSizeY = 30;
    public float gridSpacing = 15.0f;

    private static final int summonCDAll = 1200; //饿鬼召唤cd
    private int summonCD = summonCDAll;

    List<LivingEntity> nearbyLivings;

    private static final double FINISH_LINE_DISTANCE = 2000;

    public List<WallOfFleshPart> subEntities = new CopyOnWriteArrayList<>();

    public final List<Tuple<Integer, Vec3>> localOffsets = new CopyOnWriteArrayList<>(); // 存储子实体相对坐标
    public final List<Tuple<Vec3, Integer>> theHungryList = new CopyOnWriteArrayList<>();

    private static final EntityDataAccessor<List<Tuple<Integer, Vec3>>> DATA_LOCAL_OFFSETS =
            SynchedEntityData.defineId(WallOfFlesh.class, TUPLE_INT_VEC3_LIST_SERIALIZER.get());
    private static final EntityDataAccessor<List<Tuple<Vec3, Integer>>> DATA_HUNGRY_OFFSETS =
            SynchedEntityData.defineId(WallOfFlesh.class, TUPLET_VEC3_INT_LIST_SERIALIZER.get());


    public WallOfFlesh(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.nearbyLivings = new ArrayList<>();
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(baseMoveSpeed);
        genGridWall();
        explosionResistance = switch (this.level().getDifficulty()) {
            case EASY -> 0.25f;
            case NORMAL -> 0.15f;
            case HARD -> 0.05f;
            default -> 1.0f;
        };
    }
    public WallOfFlesh(Level level,Direction direction) {
        this(TEBossEntities.WALL_OF_FLESH.get(), level);
        this.setForward(direction);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LOCAL_OFFSETS, new CopyOnWriteArrayList<>());
        builder.define(DATA_HUNGRY_OFFSETS, new CopyOnWriteArrayList<>());

    }

    public List<Tuple<Integer, Vec3>> getLocalOffsets() {
        return this.entityData.get(DATA_LOCAL_OFFSETS);
    }

    public void setLocalOffsets(int entityId, Vec3 offset) {
        if (!this.level().isClientSide) {
            this.localOffsets.add(new Tuple<>(entityId, offset));
            this.entityData.set(DATA_LOCAL_OFFSETS, this.localOffsets);
        }
    }

    public List<Tuple<Vec3, Integer>> getHungryOffsets() {
        return this.entityData.get(DATA_HUNGRY_OFFSETS);
    }

    public void setHungryOffsets(Vec3 offset,int entityId) {
        if (!this.level().isClientSide) {
            boolean exists = false;
            for (Tuple<Vec3, Integer> tuple : this.theHungryList) {
                if (tuple.getB().equals(entityId)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                this.theHungryList.add(new Tuple<>(offset, entityId));
                this.entityData.set(DATA_HUNGRY_OFFSETS, this.theHungryList);
            }
        }
    }

    @Override
    public boolean isNoGravity(){
        return true;
    }

    @Override
    protected void registerGoals() {}

    public void addChild(Entity child, Vec3 localOffset) {
        if (child instanceof WallOfFleshPart part) {
            subEntities.add(part);
            if(!this.level().isClientSide) {
                int index = subEntities.size() - 1;
                this.setLocalOffsets(index, localOffset);
            }
        }else if(child instanceof TheHungry hungry && !this.level().isClientSide){
            this.setHungryOffsets(localOffset, hungry.getId());
            // 应用旋转到初始位置
            Vec3 rotatedOffset = rotateLocalOffset(localOffset);
            hungry.setInitPos(this.position().add(rotatedOffset).toVector3f());
        }
        // 应用旋转到子实体位置
        Vec3 rotatedOffset = rotateLocalOffset(localOffset);
        child.setPos(this.position().add(rotatedOffset));
    }

    private void genGridWall() {
        Vec3 baseOffset = new Vec3(0, 0, 7.0F);

        final int MAX_DEPTH = 6;
        final double EYE_CHANCE = 0.4;
        final double MOUTH_CHANCE = 0.2;
        final double HUNGRY_CHANCE = 0.3;
        final double SUBDIVISION_CHANCE = 0.85; // 细分概率

        // 存储生成的位置
        List<Vec3> eyePositions = new ArrayList<>();
        List<Vec3> mouthPositions = new ArrayList<>();
        List<Vec3> hungryPositions = new ArrayList<>();

        // 使用四叉树生成眼睛、嘴巴和饿鬼位置
        generateAllEntitiesQuadTree(0, 0, gridSizeX, gridSizeY, 0, MAX_DEPTH,
                EYE_CHANCE, MOUTH_CHANCE, HUNGRY_CHANCE, SUBDIVISION_CHANCE,
                eyePositions, mouthPositions, hungryPositions, baseOffset);

        // 额外在眼睛之间生成嘴巴
        generateMouthsBetweenEyes(eyePositions, mouthPositions, hungryPositions, baseOffset);

        // 生成眼睛
        for (int i = 0; i < eyePositions.size(); i++) {
            Vec3 pos = eyePositions.get(i);
            WallOfFleshEye eye = new WallOfFleshEye(this, "WallOfFleshEye" + (i + 1), 4.0f, 4.0f);
            addChild(eye, pos);
        }

        // 生成嘴巴
        for (int i = 0; i < mouthPositions.size(); i++) {
            Vec3 pos = mouthPositions.get(i);
            WallOfFleshMouth mouth = new WallOfFleshMouth(this, "WallOfFleshMouth" + i, 3.0f, 4.0f);
            addChild(mouth, pos);
        }

        // 生成饿鬼
        if (this.level() instanceof ServerLevel serverLevel) {
            for (Vec3 pos : hungryPositions) {
                TheHungry hungry = TEUtils.spawnEntity(() -> new TheHungry(TEMonsterEntities.THE_HUNGRY.get(), level(),
                        new AbstractPrefab().getPrefab()) {
                    @Override
                    protected boolean shouldDropLoot() {
                        return false;
                    }
                }, serverLevel, pos);

                if (hungry != null) {
                    addChild(hungry, pos);
                    hungry.minion_setOwner(this);
                }
            }
        }

        this.setId(ENTITY_COUNTER.getAndAdd(this.subEntities.size() + 1) + 1);
    }

    //四叉树
    private void generateAllEntitiesQuadTree(int x, int y, int width, int height, int depth, int maxDepth,
                                             double eyeChance, double mouthChance, double hungryChance, double subdivisionChance,
                                             List<Vec3> eyePositions, List<Vec3> mouthPositions, List<Vec3> hungryPositions, Vec3 baseOffset) {

        if (width <= 0 || height <= 0) {
            return;
        }

        int centerX = x + width / 2;
        int centerY = y + height / 2;

        double maxOffset = gridSpacing * 0.8;
        double offsetX = (random.nextDouble() - 0.5) * maxOffset;
        double offsetY = (random.nextDouble() - 0.5) * maxOffset;

        Vec3 worldPos = new Vec3(
                (centerX - gridSizeX / 2.0) * gridSpacing + offsetX,
                (centerY - gridSizeY / 2.0) * gridSpacing + offsetY,
                0
        ).add(baseOffset);

        boolean shouldSubdivide = depth < maxDepth &&
                width > 1 && height > 1 &&
                random.nextDouble() < subdivisionChance;

        if (depth < 3 && random.nextDouble() < 0.95) {
            shouldSubdivide = true;
        }

        if (shouldSubdivide) {
            int halfWidth = width / 2;
            int halfHeight = height / 2;

            generateAllEntitiesQuadTree(x, y, halfWidth, halfHeight, depth + 1, maxDepth,
                    eyeChance, mouthChance, hungryChance, subdivisionChance * 0.95,
                    eyePositions, mouthPositions, hungryPositions, baseOffset);

            generateAllEntitiesQuadTree(x + halfWidth, y, width - halfWidth, halfHeight, depth + 1, maxDepth,
                    eyeChance, mouthChance, hungryChance, subdivisionChance * 0.95,
                    eyePositions, mouthPositions, hungryPositions, baseOffset);

            generateAllEntitiesQuadTree(x, y + halfHeight, halfWidth, height - halfHeight, depth + 1, maxDepth,
                    eyeChance, mouthChance, hungryChance, subdivisionChance * 0.95,
                    eyePositions, mouthPositions, hungryPositions, baseOffset);

            generateAllEntitiesQuadTree(x + halfWidth, y + halfHeight, width - halfWidth, height - halfHeight, depth + 1, maxDepth,
                    eyeChance, mouthChance, hungryChance, subdivisionChance * 0.95,
                    eyePositions, mouthPositions, hungryPositions, baseOffset);
        } else {
            double rand = random.nextDouble();

            boolean hasConflict = false;
            double conflictDistance = gridSpacing * 0.6;

            for (Vec3 existingPos : eyePositions) {
                if (existingPos.distanceToSqr(worldPos) < conflictDistance * conflictDistance) {
                    hasConflict = true;
                    break;
                }
            }

            if (!hasConflict) {
                for (Vec3 existingPos : mouthPositions) {
                    if (existingPos.distanceToSqr(worldPos) < conflictDistance * conflictDistance) {
                        hasConflict = true;
                        break;
                    }
                }
            }

            if (!hasConflict) {
                for (Vec3 existingPos : hungryPositions) {
                    if (existingPos.distanceToSqr(worldPos) < conflictDistance * conflictDistance) {
                        hasConflict = true;
                        break;
                    }
                }
            }

            if (!hasConflict) {
                if (rand < eyeChance) {
                    eyePositions.add(worldPos);
                } else if (rand < eyeChance + mouthChance) {
                    mouthPositions.add(worldPos);
                } else if (rand < eyeChance + mouthChance + hungryChance) {
                    hungryPositions.add(worldPos);
                }
            }
        }
    }

    /**
     * 在上下相邻且空间足够的眼睛中间生成嘴巴
     */
    private void generateMouthsBetweenEyes(List<Vec3> eyePositions, List<Vec3> mouthPositions, List<Vec3> hungryPositions, Vec3 baseOffset) {
        Map<Integer, List<Vec3>> eyesByGridX = new HashMap<>();

        for (Vec3 eyePos : eyePositions) {
            int gridX = (int) Math.round((eyePos.x - baseOffset.x) / gridSpacing + gridSizeX / 2.0);

            if (gridX >= 0 && gridX < gridSizeX) {
                eyesByGridX.computeIfAbsent(gridX, k -> new ArrayList<>()).add(eyePos);
            }
        }

        eyesByGridX.forEach((gridX, eyesInColumn) -> {
            eyesInColumn.sort(Comparator.comparingDouble(a -> a.y));

            // 检查连续3个眼睛，将中间的眼睛替换为嘴巴
            for (int i = 0; i < eyesInColumn.size() - 2; i++) {
                Vec3 eye1 = eyesInColumn.get(i);
                Vec3 eye2 = eyesInColumn.get(i + 1);
                Vec3 eye3 = eyesInColumn.get(i + 2);

                double distance1 = Math.abs(eye2.y - eye1.y);
                double distance2 = Math.abs(eye3.y - eye2.y);

                // 将Y轴距离判断范围 扩大到 gridSpacing * 3.75
                if (Math.abs(eye2.x - eye1.x) < gridSpacing * 0.1 &&
                        Math.abs(eye2.z - eye1.z) < gridSpacing * 0.1 &&
                        distance1 <= gridSpacing * 3.75 &&
                        distance2 <= gridSpacing * 3.75) {

                    eyePositions.removeIf(existingEye ->
                            existingEye.distanceToSqr(eye2) < gridSpacing * gridSpacing * 0.1);

                    boolean hasExistingMouth = false;
                    for (Vec3 existingMouth : mouthPositions) {
                        if (existingMouth.distanceToSqr(eye2) < gridSpacing * gridSpacing * 0.5) {
                            hasExistingMouth = true;
                            break;
                        }
                    }

                    if (!hasExistingMouth) {
                        mouthPositions.add(eye2);
                    }
                }
            }

            for (int i = 0; i < eyesInColumn.size() - 1; i++) {
                Vec3 eye1 = eyesInColumn.get(i);
                Vec3 eye2 = eyesInColumn.get(i + 1);

                double distance = Math.abs(eye2.y - eye1.y);
                if (distance >= gridSpacing * 1.5) {
                    double midY = (eye1.y + eye2.y) / 2.0;

                    // 统一使用局部坐标系，移除基于移动方向的条件逻辑
                    Vec3 mouthPos = new Vec3(
                            (gridX - gridSizeX / 2.0) * gridSpacing,
                            midY,
                            0
                    ).add(baseOffset);

                    double conflictDistance = gridSpacing * 0.6;

                    boolean hasExistingMouth = false;
                    for (Vec3 existingMouth : mouthPositions) {
                        if (existingMouth.distanceToSqr(mouthPos) < conflictDistance * conflictDistance) {
                            hasExistingMouth = true;
                            break;
                        }
                    }

                    if (hasExistingMouth) {
                        continue;
                    }

                    eyePositions.removeIf(existingEye ->
                            existingEye.distanceToSqr(mouthPos) < conflictDistance * conflictDistance);

                    hungryPositions.removeIf(existingHungry ->
                            existingHungry.distanceToSqr(mouthPos) < conflictDistance * conflictDistance);

                    boolean hasConflict = false;
                    for (Vec3 existingMouth : mouthPositions) {
                        if (existingMouth.distanceToSqr(mouthPos) < gridSpacing * gridSpacing * 0.5) {
                            hasConflict = true;
                            break;
                        }
                    }

                    if (!hasConflict && random.nextDouble() < 0.8) {
                        mouthPositions.add(mouthPos);
                    }
                }
            }
        });
    }

    protected void dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {
        super.dropAllDeathLoot(level, damageSource);

        BlockPos centerPos = this.blockPosition().below(1);

        Block targetBlock = Blocks.OBSIDIAN;
        if (ModChecker.confluence.isLoaded()) {
            // 猩红和魔矿砖对半概率
            String targetResource = this.random.nextBoolean() ?
                    "confluence:demonite_ore_bricks" :
                    "confluence:crimtane_ore_bricks";
            targetBlock = BuiltInRegistries.BLOCK.getOptional(
                    ResourceLocation.parse(targetResource)
            ).orElse(Blocks.OBSIDIAN);
        }
        Block block = targetBlock;

        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos framePos = centerPos.offset(x, y, z);
                    if ((Math.abs(x) == 4 || Math.abs(y) == 4 || Math.abs(z) == 4) && level.getBlockState(framePos).isAir()) {
                        level.setBlockAndUpdate(framePos, block.defaultBlockState());
                    }
                }
            }
        }

        for(LivingEntity nearbyLiving : nearbyLivings) {
            if(nearbyLiving instanceof Player player) {
                double distance = player.distanceToSqr(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                if(distance > 10000) { // 100^2 = 10000
                    WallOfFairy wallOfFairy = new WallOfFairy(
                            TEAnimals.WALL_OF_FAIRY.get(),
                            level,
                            VariantsTextureMaps.fairyTextures,
                            centerPos
                    );
                    wallOfFairy.setPos(player.getX(), player.getY() + 2, player.getZ());
                    level.addFreshEntity(wallOfFairy);
                }
            }
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldDoCollision(){
        return getTarget() != null && this.isAlive();
    }

    public boolean isMovingAlongX() {
        Direction dir = this.getDirection();
        return dir == Direction.EAST || dir == Direction.WEST;
    }

    @Override
    public Vec3 getForward() {
        float yaw = this.getYRot();
        float normalizedYaw = Math.round(yaw / 90.0f) * 90.0f;
        float yawRad = (float) Math.toRadians(normalizedYaw);
        return new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
    }

    public void setForward(Direction direction) {
        switch (direction) {
            case NORTH -> this.setYRot(0.0F);   // 北: 0°
            case EAST -> this.setYRot(90.0F);   // 东: 90°
            case SOUTH -> this.setYRot(180.0F); // 南: 180°
            case WEST -> this.setYRot(270.0F);  // 西: 270°
            default -> throw new IllegalArgumentException("Invalid direction: " + direction);
        }
    }

    /**
     * 根据血肉墙的旋转角度旋转相对位置向量
     * @param localOffset 原始相对位置
     * @return 旋转后的相对位置
     */
    public Vec3 rotateLocalOffset(Vec3 localOffset) {
        Direction direction = this.getDirection();
        return switch (direction) {
            case NORTH -> // 北方向：Z轴负向
                    new Vec3(localOffset.x, localOffset.y, -localOffset.z);
            case EAST -> // 东方向：X轴正向
                    new Vec3(localOffset.z, localOffset.y, localOffset.x);
            case SOUTH -> // 南方向：Z轴正向
                    new Vec3(-localOffset.x, localOffset.y, localOffset.z);
            case WEST -> // 西方向：X轴负向
                    new Vec3(-localOffset.z, localOffset.y, -localOffset.x);
            default -> localOffset; // 默认情况下不进行变换
        };
    }

    private void updateChildPosition(Entity child) {
        List<Tuple<Integer, Vec3>> syncedOffsets = getLocalOffsets();
        List<Tuple<Vec3, Integer>> hungrySyncedOffsets = getHungryOffsets();

        if (child instanceof TheHungry hungry) {
            Vec3 localOffset = Vec3.ZERO;
            for (Tuple<Vec3, Integer> tuple : hungrySyncedOffsets) {
                if (tuple.getB().equals(hungry.getId())) {
                    localOffset = tuple.getA();
                    break;
                }
            }

            Vec3 childPos = this.position().add(rotateLocalOffset(localOffset));
            Vec3 vec3 = hungry.position().subtract(hungry.getInitPos());
            Vec3 summonPos = childPos.add(vec3);
            hungry.setPos(summonPos);
            hungry.setInitPos(childPos.toVector3f());
            // 更新饿鬼的旋转
            hungry.setYRot(this.getYRot());
        } else if (child instanceof WallOfFleshPart part) {
            int childIndex = subEntities.indexOf(child);
            Vec3 localOffset = (childIndex >= 0 && childIndex < syncedOffsets.size()) ? syncedOffsets.get(childIndex).getB() : Vec3.ZERO;

            Vec3 childPos = this.position().add(rotateLocalOffset(localOffset));
            part.moveTo(childPos);
        }
    }

    @Override
    public void onAddedToLevel(){
        super.onAddedToLevel();
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
        double summonDir = 50;
        Vec3 summonPos = new Vec3(this.position().x, this.level().getMinBuildHeight() + (gridSizeY * gridSpacing)/2, this.position().z).add(getForward().scale(-summonDir));
        this.moveTo(summonPos);
        this.InitPos = summonPos;
    }

    @Override
    public boolean shouldBeSaved(){
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();

        for (Entity child : subEntities) {
            updateChildPosition(child);
        }
        for (Tuple<Vec3, Integer> tuple : theHungryList) {
            Entity child = level().getEntity(tuple.getB());
            if (child != null) {
                updateChildPosition(child);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && !this.isDeadOrDying()) {
            if (this.tickCount % 5 == 0) {
                handleChunkLoading();
            }

            if (--summonCD <= 0) {
                summonCD = summonCDAll;
                List<Integer> deadHungryIndices = new ArrayList<>();
                for (int i = 0; i < theHungryList.size(); i++) {
                    Tuple<Vec3, Integer> tuple = theHungryList.get(i);
                    Integer hungryId = tuple.getB();
                    Entity hungry =  level().getEntity(hungryId);
                    if (hungry == null || !hungry.isAlive()) {
                        deadHungryIndices.add(i);
                    }
                }

                for (int index : deadHungryIndices) {
                    // 40%概率重新生成饿鬼
                    if (random.nextFloat() < 0.4f) {
                        Tuple<Vec3, Integer> tuple = theHungryList.get(index);
                        Vec3 theHungryPos = tuple.getA();
                        TheHungry newHungry = TEUtils.spawnEntity(()->new TheHungry(TEMonsterEntities.THE_HUNGRY.get(), level(),new AbstractPrefab().getPrefab()) {
                            @Override
                            protected boolean shouldDropLoot() {
                                return false;
                            }
                        }, (ServerLevel)level(), theHungryPos);

                        if (newHungry != null) {
                            this.theHungryList.set(index, new Tuple<>(theHungryPos, newHungry.getId()));
                            newHungry.minion_setOwner(this);
                            newHungry.setInitPos(this.position().add(theHungryPos).toVector3f());
                            newHungry.setPos(this.position().add(theHungryPos));
                            level().playSound(null, newHungry.blockPosition(), TESounds.WALL_OF_FLESH_SUMMON.get(), SoundSource.HOSTILE, 1, 1);
                        }
                    }
                }
            }

            if (this.tickCount % 5 == 0 && this.getInsideBox() != null && this.getOutsideCollisionBox() != null ) {
                List<Player> nearbyPlayers = level().getEntitiesOfClass(Player.class,
                        this.getOutsideCollisionBox());

                List<Player> nearbyTargets = level().getEntitiesOfClass(Player.class,
                        this.getInsideBox());

                this.nearbyLivings.clear();
                this.nearbyLivings.addAll(nearbyTargets);

                DeferredHolder<MobEffect, HorrifiedEffect> horrifiedHolder = TEEffects.HORRIFIED;
                MobEffectInstance horrifiedEffect = new MobEffectInstance(horrifiedHolder, 200, 3, false, true);

                nearbyPlayers.stream()
                        .filter(LivingEntity::canBeSeenByAnyone)
                        .filter(e -> !(e instanceof Player p && (p.isCreative() || p.isSpectator())))
                        .forEach(player -> {
                            horrifiedHolder.get().setWallOfFlesh(this);
                            player.addEffect(horrifiedEffect);
                        });
            }

            for (LivingEntity nearbyLiving : this.nearbyLivings) {
                for (WallOfFleshPart part : this.subEntities) {
                    double distanceSqr = part.position().distanceToSqr(nearbyLiving.position());
                    if (distanceSqr <= 120 * 120) {
                        Vec3 localOffset = part.position();
                        part.tickPart(localOffset.x, localOffset.y, localOffset.z);
                    }
                }
            }
            if (shouldMove && this.isAlive()) {
                Vec3 forward = this.getForward();
                float yaw = (float) Math.toDegrees(
                        Math.atan2(-forward.x, forward.z)
                );

                float alignedYaw = Math.round(yaw / 90.0f) * 90.0f;

                this.setYRot(Mth.wrapDegrees(alignedYaw));
                Vec3 moveDirection = this.getForward();
                Vec3 currentOffset = this.position().subtract(InitPos);
                double progress = currentOffset.dot(moveDirection);

                // 四方向终点判断
                if ((moveDirection.x > 0 && progress >= FINISH_LINE_DISTANCE) || // 东方向
                        (moveDirection.x < 0 && progress <= -FINISH_LINE_DISTANCE) || // 西方向
                        (moveDirection.z > 0 && progress >= FINISH_LINE_DISTANCE) || // 南方向
                        (moveDirection.z < 0 && progress <= -FINISH_LINE_DISTANCE) || // 北方向
                        !this.level().getWorldBorder().isWithinBounds(this.position())) {
                    //如果血肉墙到达了地图的另一边，它会消失，且所有受到惊恐减益影响的玩家会死亡
                    nearbyLivings.stream().filter(entity -> entity.hasEffect(TEEffects.HORRIFIED)).forEach(LivingEntity::kill);
                    theHungryList.stream().map(tuple -> level().getEntity(tuple.getB())).filter(Objects::nonNull).forEach(Entity::discard);
                    this.discard();
                    return;
                }
                this.addDeltaMovement(getForward().scale(this.getMoveSpeed()).scale(0.125F));
            }
        }

        if (this.tickCount > genTick && genSegments && this.dirty) {
            genSegments = false;

            if (!level().isClientSide) {
                CameraShakeManager.addCameraShake(new CameraShakeData(300, this.position(), 180));
            }
        }
    }

    @Override
    protected void tickDeath() {
        ++this.deathTime;

        if (this.deathTime == this.getMaxDeathTime() && this.level() instanceof ServerLevel) {
            subEntities.forEach(Entity::discard);
            localOffsets.clear();
            theHungryList.stream().map(tuple -> level().getEntity(tuple.getB())).filter(Objects::nonNull).forEach(Entity::discard);
            theHungryList.clear();
            this.remove(RemovalReason.KILLED);
            this.gameEvent(GameEvent.ENTITY_DIE);
        }
    }

    public int getMaxDeathTime() {
        return 120;
    }

    public float getFadeProgress() {
        float deathProgress = (float) this.deathTime / this.getMaxDeathTime();
        return 1.0f - Math.min(deathProgress, 1.0f);
    }

    public AABB getInsideBox() {
        Direction dir = this.getDirection();
        boolean isReverse = dir == Direction.WEST || dir == Direction.SOUTH;
        int completion = isReverse?0:5;
        if(isMovingAlongX()) {
            double x = completion;
            double y = gridSizeY * gridSpacing / 2;
            double z = gridSizeX * gridSpacing / 2;
            insideCollisionBox = new AABB(
                    this.position().subtract(x, y, z + gridSpacing / 2),
                    this.position().add(x + 150 * this.getForward().x, y, z + 150 * this.getForward().z - gridSpacing / 2));
        }else {
            double x = gridSizeX * gridSpacing / 2;
            double y = gridSizeY * gridSpacing / 2;
            double z = completion;
            insideCollisionBox = new AABB(
                    this.position().subtract(x + gridSpacing / 2, y, z),
                    this.position().add(x, y, z + 120 * this.getForward().z - gridSpacing / 2));
        }
        return this.insideCollisionBox;
    }

    public AABB getOutsideCollisionBox() {
        Direction dir = this.getDirection();
        boolean isReverse = dir == Direction.WEST || dir == Direction.SOUTH;
        int completion1 = isReverse?-200:200;
        if(isMovingAlongX()) {
            double x = completion1;
            double y = gridSizeY * gridSpacing / 2 + 150;
            double z = gridSizeX * gridSpacing / 2 + 150;
            outsideCollisionBox = new AABB(
                    this.position().subtract(x, y, z + gridSpacing / 2),
                    this.position().add(x, y, z - gridSpacing / 2));
        }else {
            double x = gridSizeX * gridSpacing / 2 + 120;
            double y = gridSizeY * gridSpacing/2 + 150;
            double z = completion1;
            outsideCollisionBox = new AABB(
                    this.position().subtract(x + gridSpacing / 2, y, z),
                    this.position().add(x - gridSpacing / 2, y, z));
        }
        return this.outsideCollisionBox;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getOutsideCollisionBox();
    }

    @Override
    public void onRemovedFromLevel() {
        subEntities.forEach(Entity::onRemovedFromLevel);
        if(!this.isAlive()){
            subEntities.forEach(Entity::discard);
            localOffsets.clear();
            theHungryList.stream().map(tuple -> level().getEntity(tuple.getB())).filter(Objects::nonNull).forEach(Entity::discard);
            theHungryList.clear();
        }
        this.bossEvent.removeAllPlayers();
        super.onRemovedFromLevel();
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean canAttack(LivingEntity entity) {
        return super.canAttack(entity);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if(source.is(DamageTypeTags.IS_FIRE)||source.is(DamageTypeTags.IS_DROWNING)){
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    public boolean hurt(WallOfFleshPart wallOfFleshPart, @NotNull DamageSource source, float damage) {
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR) && wallOfFleshPart instanceof WallOfFleshMouth) {
            this.hurtArmor(source, damage);
            damage = CombatRules.getDamageAfterAbsorb(this, damage, source, 12, (float)this.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        }
        return this.hurt(source, damage);
    }

    public boolean hurt(DamageSource source, float amount) {

        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
    }

    @Override
    public void changeState(){
        if(this.getStage() == 1 && this.getHealth() / getMaxHealth() < 0.5){
            this.setStage(2);
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.getMoveSpeed() * 1.45F);
            for (WallOfFleshPart part : this.getParts()) {
                part.onParentChangeState(this.getStage());
            }
        }
        this.syncStatus(this.getStage());
    }

    @Override
    protected void initStage(int stage) {
        if (stage == 2) {
            for (WallOfFleshPart part : this.getParts()) {
                part.onParentChangeState(stage);
            }
        }
    }

    @Override
    public boolean hasLineOfSight(Entity entity) {
        double size = this.getOutsideCollisionBox().getSize()*1.5F;
        return distanceToSqr(entity) < size * size;
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public WallOfFleshPart @NotNull [] getParts() {
        return this.subEntities.toArray(new WallOfFleshPart[0]);
    }

    @Override
    public void addSkills() {
    }

    @Override
    protected BossEvent.BossBarColor getBossBarColor(){
        return BossEvent.BossBarColor.RED;
    };

    @Override
    public boolean shouldEscape() {
        return false;
    }

    public boolean shouldRender(double x, double y, double z) {
        return true;
    }

    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    public boolean addEffect(MobEffectInstance effectInstance, @Nullable Entity entity) {
        return false;
    }

    protected boolean canRide(Entity entity) {
        return false;
    }

    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    public int getGridSizeX() {
        return gridSizeX;
    }

    public int getGridSizeY() {
        return gridSizeY;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < this.subEntities.size(); ++i) {
            this.subEntities.get(i).setId(id + i + 1);
        }
    }

    /**
     * 区块加载
     */
    private void handleChunkLoading() {
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) this.level();



        int gridSizeX = this.getGridSizeX();
        float gridSpacing = this.gridSpacing;

        int minX = (int) Math.floor((this.getX() - gridSizeX * gridSpacing / 2) / 16.0);
        int maxX = (int) Math.ceil((this.getX() + gridSizeX * gridSpacing / 2) / 16.0);
        int minZ = (int) Math.floor((this.getZ() - gridSizeX * gridSpacing / 2) / 16.0);
        int maxZ = (int) Math.ceil((this.getZ() + gridSizeX * gridSpacing / 2) / 16.0);

        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
                WorldChunksManager.forceLoadChunk(serverLevel, chunkPos);
            }
        }

        if (this.tickCount % 180 == 0) {
            WorldChunksManager.freeChunks(serverLevel);
        }
    }
}
