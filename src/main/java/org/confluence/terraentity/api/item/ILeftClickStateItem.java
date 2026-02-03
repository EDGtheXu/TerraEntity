package org.confluence.terraentity.api.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/// 当右手持物品左键时触发
///
/// 玩家的鼠标状态存储在[org.confluence.terraentity.attachment.WeaponStorage#leftClicking]
public interface ILeftClickStateItem {
    void onLeftClick(Player player, ItemStack itemStack);

    void onLeftRelease(Player player, ItemStack itemStack);

    default void onWhellScroll(Player player, ItemStack itemStack, int scrollAmount){}

    boolean canSwitchWithoutRelease(Player player, ItemStack itemStack);
}
