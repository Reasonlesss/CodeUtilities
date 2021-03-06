package io.github.codeutilities.mixin.messages;

import io.github.codeutilities.CodeUtilities;
import io.github.codeutilities.config.ModConfig;
import io.github.codeutilities.dfrpc.DFDiscordRPC;
import io.github.codeutilities.events.ChatReceivedEvent;
import io.github.codeutilities.keybinds.FlightspeedToggle;
import io.github.codeutilities.util.DFInfo;
import io.github.codeutilities.gui.CPU_UsageText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.MessageType;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.text.Text;
import org.apache.logging.log4j.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinGameMessageListener {
    private MinecraftClient minecraftClient = MinecraftClient.getInstance();

    @Inject(method = "onGameMessage", at = @At("HEAD"), cancellable = true)
    private void onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        if (DFInfo.isOnDF()) {
            if (packet.getLocation() == MessageType.CHAT || packet.getLocation() == MessageType.SYSTEM) {
                if (Thread.currentThread().getName().equals("Render thread")) {
                    ChatReceivedEvent.onMessage(packet.getMessage(), ci);
                    String text = packet.getMessage().getString();
                    try {
                        this.updateVersion(packet.getMessage());
                        this.updateState(packet.getMessage());
                    }catch (Exception e) {
                        e.printStackTrace();
                        CodeUtilities.log(Level.ERROR, "Error while trying to parse the chat text!");
                    }
                }
            }
        }
    }

    @Inject(method = "onTitle", at = @At("HEAD"), cancellable = true)
    private void onTitle(TitleS2CPacket packet, CallbackInfo ci) {
        TitleS2CPacket.Action action = packet.getAction();
        if (minecraftClient.player == null) return;
        if (action == TitleS2CPacket.Action.ACTIONBAR) {
            if (packet.getText().getString().equals("CPU Usage: [▮▮▮▮▮▮▮▮▮▮▮▮▮▮▮▮▮▮▮▮]")) {
                if(ModConfig.getConfig().cpuOnScreen) {
                    CPU_UsageText.updateCPU(packet);
                    ci.cancel();
                }
			}
        }
    }

    private void updateVersion(Text component) {
        if (minecraftClient.player == null) return;

        String text = component.getString();

        if (text.matches("Current patch: .*\\. See the patch notes with \\/patch!")) {
            try {
                long time = System.currentTimeMillis() / 1000L;
                if (time - lastPatchCheck > 2) {
                    String patchText = text.replaceAll("Current patch: (.*)\\. See the patch notes with \\/patch!", "$1");

                    DFInfo.isPatchNewer(patchText, "0"); //very lazy validation lol
                    DFInfo.patchId = patchText;
                    DFInfo.currentState = null;
                    CodeUtilities.log(Level.INFO, "DiamondFire Patch " + DFInfo.patchId + " detected!");

                    lastPatchCheck = time;

                    // update rpc on server join
                    DFDiscordRPC.delayRPC = true;
                    DFDiscordRPC.supportSession = false;
                }
            }catch (Exception e) {
                CodeUtilities.log(Level.INFO, "Error on parsing patch number!");
                e.printStackTrace();
            }
        }
    }

    private void updateState(Text component) {
        if (minecraftClient.player == null) return;

        String text = component.getString();

        // Flight speed
        if (text.matches("^Set flight speed to: \\d+% of default speed\\.$") && !text.matches("^Set flight speed to: 100% of default speed\\.$")) {
            FlightspeedToggle.fs_is_normal = false;
        }

        // Play Mode
        if (text.matches("Joined game: .* by .*") && text.startsWith("Joined game: ")) {
            DFInfo.currentState = DFInfo.State.PLAY;

            // Auto LagSlayer
            System.out.println(CPU_UsageText.lagSlayerEnabled);
            if (!CPU_UsageText.lagSlayerEnabled && ModConfig.getConfig().autolagslayer) {
                minecraftClient.player.sendChatMessage("/lagslayer");
                ChatReceivedEvent.cancelLagSlayerMsg = true;
            }

            // fs toggle
            FlightspeedToggle.fs_is_normal = true;
        }

        // Enter Session
        if (text.matches("^You have entered a session with .*\\.$")) {
            if (!DFDiscordRPC.supportSession) {
                DFDiscordRPC.supportSession = true;
                if (ModConfig.getConfig().discordRPC) {
                    new Thread(() -> {
                        DFDiscordRPC.getThread().locateRequest();
                    }).start();
                }
            }
        }

        // End Session
        if (text.matches("^" + minecraftClient.player.getName().asString() + " finished a session with .*\\. ▶ .*$")) {
            if (DFDiscordRPC.supportSession) {
                DFDiscordRPC.supportSession = false;
                if (ModConfig.getConfig().discordRPC) {
                    new Thread(() -> {
                        DFDiscordRPC.getThread().locateRequest();
                    }).start();
                }
            }
        }
        if (text.matches("^Your session with .* has ended\\.$")) {
            if (DFDiscordRPC.supportSession) {
                DFDiscordRPC.supportSession = false;
                if (ModConfig.getConfig().discordRPC) {
                    new Thread(() -> {
                        DFDiscordRPC.getThread().locateRequest();
                    }).start();
                }
            }
        }

        // Build Mode
        if (minecraftClient.player.isCreative() && text.matches("^» You are now in build mode\\.$")) {
            if (DFInfo.currentState != DFInfo.State.BUILD) {
                DFInfo.currentState = DFInfo.State.BUILD;
            }

            // Auto LagSlayer
            if (!CPU_UsageText.lagSlayerEnabled && ModConfig.getConfig().autolagslayer) {
                minecraftClient.player.sendChatMessage("/lagslayer");
                ChatReceivedEvent.cancelLagSlayerMsg = true;
            }

            // fs toggle
            FlightspeedToggle.fs_is_normal = true;

            long time = System.currentTimeMillis() / 1000L;
            if (time - lastBuildCheck > 1) {
                new Thread(() -> {
                    try {
                        Thread.sleep(20);
                        if(ModConfig.getConfig().autotime) {
                            minecraftClient.player.sendChatMessage("/time " + ModConfig.getConfig().autotimeval);
                            ChatReceivedEvent.cancelTimeMsg = true;
                        }
                        if(ModConfig.getConfig().autonightvis) {
                            minecraftClient.player.sendChatMessage("/nightvis");
                            ChatReceivedEvent.cancelNVisionMsg = true;
                        }
                    }catch (Exception e) {
                        CodeUtilities.log(Level.ERROR, "Error while executing the task!");
                        e.printStackTrace();
                    }
                }).start();

                lastBuildCheck = time;
            }
        }
        
        // Dev Mode (more moved to MixinItemSlotUpdate)
        if (minecraftClient.player.isCreative() && text.matches("^» You are now in dev mode\\.$")) {
            // fs toggle
            FlightspeedToggle.fs_is_normal = true;
        }
    }

    private static long lastPatchCheck = 0;
    private static long lastBuildCheck = 0;
}