package dev.synm.mixins;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onPlayerDeath(net.minecraft.entity.damage.DamageSource damageSource, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        
        var playerLogger = dev.synm.SynM.getPlayerLogger();
        if (playerLogger != null) {
            String deathMessage = damageSource.getDeathMessage(player).getString();
            playerLogger.logPlayerDeath(player, deathMessage);
        }
    }
}
