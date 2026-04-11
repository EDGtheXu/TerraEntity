package org.confluence.terraentity.init;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.confluence.lib.ConfluenceMagicLib;
import org.confluence.terraentity.TerraEntity;

@Deprecated
public final class TEAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, TerraEntity.MODID);

    /// 仆从容量
    public static final Holder<Attribute> MINION_CAPACITY = ConfluenceMagicLib.MINION_CAPACITY;
    /// 哨兵容量
    public static final Holder<Attribute> SENTRY_CAPACITY = ConfluenceMagicLib.SENTRY_CAPACITY;
    /// 召唤伤害
    public static final Holder<Attribute> SUMMON_DAMAGE = ConfluenceMagicLib.SUMMON_DAMAGE;
    /// 仆从击退
    public static final Holder<Attribute> SUMMON_KNOCKBACK = ConfluenceMagicLib.SUMMON_KNOCKBACK;
    // 鞭速度 同 近战攻击速度，故不注册
    /// 鞭范围
    public static final Holder<Attribute> WHIP_RANGE = ConfluenceMagicLib.WHIP_RANGE;
    /// 仆从标记伤害
    public static final Holder<Attribute> MARK_DAMAGE = ConfluenceMagicLib.MARK_DAMAGE;
}
