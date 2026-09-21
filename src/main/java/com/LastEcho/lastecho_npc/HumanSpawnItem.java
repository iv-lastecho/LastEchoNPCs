package com.LastEcho.lastecho_npc;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class HumanSpawnItem extends Item {
    public HumanSpawnItem(int id) {
        super(id);
        this.setMaxCount(1); // the correct method in beta
    }

    @Override
    public ItemStack use(ItemStack stack, World world, PlayerEntity player) {
        if (!world.isRemote) {
            HumanEntity human = new HumanEntity(world);
            human.setPositionAndAngles(player.x, player.y + 1, player.z, player.yaw, 0.0F);
            world.spawnEntity(human);
        }
        return stack;
    }
}
