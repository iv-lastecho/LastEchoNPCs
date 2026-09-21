package com.LastEcho.lastecho_npc;

import com.periut.retroapi.entrypoint.RetroClientModInitializer;
import com.periut.retroapi.entity.client.RetroEntityRenderers;
import net.minecraft.client.render.entity.model.BipedEntityModel;

public class LastEchoNPCModClient implements RetroClientModInitializer {
    @Override
    public void initRetroClient() {
        RetroEntityRenderers.register(HumanEntity.class,
            new HumanRenderer(new BipedEntityModel(), 0.5F));
    }
}
