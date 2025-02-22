package com.maciej916.indreb.common.client.keys.impl;

import com.maciej916.indreb.common.capabilities.player.IPlayerCapability;
import com.maciej916.indreb.common.client.keys.ModKeys;
import com.maciej916.indreb.common.interfaces.item.IArmorProperties;
import com.maciej916.indreb.common.registries.ModCapabilities;
import com.maciej916.indreb.common.registries.ModSounds;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class NightVisionHandler {

    private static boolean toggleNightVision = false;

    public static void toggleNightVision(Player player) {
        if (ModKeys.NIGHT_VISION_KEY.isDown() && !toggleNightVision) {
            toggleNightVision = true;

            boolean found = false;
            for (ItemStack stack : player.getArmorSlots()) {
                if (stack.getItem() instanceof IArmorProperties armorProperties) {
                    if (armorProperties.supportsNightVision()) {
                        found = true;
                        break;
                    }
                }
            }

            if (found) {
                player.getCapability(ModCapabilities.PLAYER_CAPABILITY).ifPresent(cap -> {
                    toggle(cap, player, !cap.getNightVision());
                });
            }

        }
    }

    public static void checkNightVision(Player player) {
        if (!ModKeys.NIGHT_VISION_KEY.isDown()) {
            toggleNightVision = false;
        }

        player.getCapability(ModCapabilities.PLAYER_CAPABILITY).ifPresent(cap -> {
            boolean found = false;
            for (ItemStack stack : player.getArmorSlots()) {
                if (stack.getItem() instanceof IArmorProperties armorProperties) {
                    if (armorProperties.supportsNightVision()) {
                        found = true;
                        break;
                    }
                }
            }

            if (!found && cap.getNightVision()) {
                toggle(cap, player, false);
            }
        });
    }

    public static void toggle(IPlayerCapability cap, Player player, boolean enable) {
        if (enable) {
            MobEffectInstance effect = new MobEffectInstance(MobEffects.NIGHT_VISION, 1000000, 100, false, false);
            effect.setNoCounter(true);
            player.playSound(ModSounds.NIGHT_VISION, 1F, 0.8F / (player.getRandom().nextFloat() * 0.4F + 0.8F));
            player.addEffect(effect);
        } else {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }

        cap.setNightVision(enable);
    }

}
