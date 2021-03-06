package io.github.codeutilities.mixin.item;

import io.github.codeutilities.CodeUtilities;
import io.github.codeutilities.config.ModConfig;
import io.github.codeutilities.events.ChatReceivedEvent;
import io.github.codeutilities.gui.CPU_UsageText;
import io.github.codeutilities.keybinds.FlightspeedToggle;
import io.github.codeutilities.mixin.messages.MixinGameMessageListener;
import io.github.codeutilities.template.TemplateStorageHandler;
import io.github.codeutilities.util.DFInfo;
import io.github.codeutilities.util.TemplateUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.util.registry.*;
import org.apache.logging.log4j.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinItemSlotUpdate {

    MinecraftClient mc = MinecraftClient.getInstance();

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD"))
    public void onScreenHandlerSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        if (packet.getSyncId() == 0) {
            ItemStack stack = packet.getItemStack();
            if (TemplateUtils.isTemplate(stack)) {
                TemplateStorageHandler.addTemplate(stack);
            }

            CompoundTag nbt = stack.getOrCreateTag();
            CompoundTag display = nbt.getCompound("display");
            ListTag lore = display.getList("Lore", 8);
            if (mc.player == null) {
                return;
            }

            if (DFInfo.isOnDF() && stack.getName().getString().contains("◇ Game Menu ◇")
                && lore.toText().getString().contains("\"Click to open the Game Menu.\"")
                && lore.toText().getString().contains("\"Hold and type in chat to search.\"")) {

                if (DFInfo.currentState != DFInfo.State.LOBBY) {
                    DFInfo.currentState = DFInfo.State.LOBBY;

                    // Auto fly
                    if (ModConfig.getConfig().autofly) {
                        mc.player.sendChatMessage("/fly");
                        ChatReceivedEvent.cancelFlyMsg = true;
                    }

                    // Auto LagSlayer
                    if (CPU_UsageText.lagSlayerEnabled && ModConfig.getConfig().autolagslayer) {
                        mc.player.sendChatMessage("/lagslayer");
                        ChatReceivedEvent.cancelLagSlayerMsg = true;
                    }

                    // fs toggle
                    FlightspeedToggle.fs_is_normal = true;
                }
            }

            if (DFInfo.isOnDF() && mc.player.isCreative() && stack.getName().getString().contains("Player Event")
                && lore.toText().getString().contains("\"Used to execute code when something\"")
                && lore.toText().getString().contains("\"is done by (or happens to) a player.\"")
                && lore.toText().getString().contains("\"Example:\"")) {

                if (DFInfo.currentState != DFInfo.State.DEV) {
                    DFInfo.currentState = DFInfo.State.DEV;
                    DFInfo.plotCorner = mc.player.getPos().add(10, -50, -10);

                    // Auto LagSlayer
                    if (!CPU_UsageText.lagSlayerEnabled && ModConfig.getConfig().autolagslayer) {
                        mc.player.sendChatMessage("/lagslayer");
                        ChatReceivedEvent.cancelLagSlayerMsg = true;
                    }

                    // fs toggle
                    FlightspeedToggle.fs_is_normal = true;

                    long time = System.currentTimeMillis() / 1000L;
                    if (time - lastDevCheck > 1) {

                        new Thread(() -> {
                            try {
                                Thread.sleep(10);
                                if(ModConfig.getConfig().autoRC) {
                                    mc.player.sendChatMessage("/rc");
                                }
                                if(ModConfig.getConfig().autotime) {
                                    mc.player.sendChatMessage("/time " + ModConfig.getConfig().autotimeval);
                                    ChatReceivedEvent.cancelTimeMsg = true;
                                }
                                if(ModConfig.getConfig().autonightvis) {
                                    mc.player.sendChatMessage("/nightvis");
                                    ChatReceivedEvent.cancelNVisionMsg = true;
                                }
                            } catch (Exception e) {
                                CodeUtilities.log(Level.ERROR, "Error while executing the task!");
                                e.printStackTrace();
                            }
                        }).start();

                        lastDevCheck = time;
                    }
                }
            }
        }
    }

    private long lastDevCheck = 0;

}
