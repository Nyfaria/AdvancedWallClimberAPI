package com.nyfaria.awcapi;


import net.minecraftforge.fml.common.*;

@Mod(Constants.MODID)
public class AdvancedWallClimberAPI {
    public AdvancedWallClimberAPI() {
        Constants.LOG.info("Advanced Wall Climber API initialized!");
        CommonClass.init();
    }
}