package com.nyfaria.awcapi;


import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.*;
import net.neoforged.neoforge.registries.*;

@Mod(Constants.MODID)
public class AdvancedWallClimberAPI {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Constants.MODID);

    public AdvancedWallClimberAPI(IEventBus eventBus) {
        Constants.LOG.info("Advanced Wall Climber API initialized!");
        CommonClass.init();
    }
}