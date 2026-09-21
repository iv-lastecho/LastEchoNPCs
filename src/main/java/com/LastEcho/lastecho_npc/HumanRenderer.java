package com.LastEcho.lastecho_npc;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;
import org.lwjgl.opengl.GL11;

public class HumanRenderer extends LivingEntityRenderer {

    public HumanRenderer(EntityModel model, float shadowSize) {
        super(model, shadowSize);
    }

    @Override
    protected void applyScale(LivingEntity entity, float partialTick) {
        if (entity instanceof HumanEntity) {
            float scale = ((HumanEntity) entity).getSizeScale();
            GL11.glScalef(scale, scale, scale);
        }
    }
}
