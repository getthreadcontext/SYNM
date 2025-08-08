package dev.synm;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class SynMClient implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        // Client-side initialization
        // The main mod logic will run when an integrated server starts
        SynM.LOGGER.info("SynM client-side initialized");
    }
}
