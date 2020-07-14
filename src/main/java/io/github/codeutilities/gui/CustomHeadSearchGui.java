package io.github.codeutilities.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.codeutilities.CodeUtilities;
import io.github.codeutilities.commands.item.CustomHeadCommand;
import io.github.codeutilities.util.Webutil;
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription;
import io.github.cottonmc.cotton.gui.widget.WButton;
import io.github.cottonmc.cotton.gui.widget.WGridPanel;
import io.github.cottonmc.cotton.gui.widget.WPanel;
import io.github.cottonmc.cotton.gui.widget.WScrollBar;
import io.github.cottonmc.cotton.gui.widget.WScrollPanel;
import io.github.cottonmc.cotton.gui.widget.WText;
import io.github.cottonmc.cotton.gui.widget.WWidget;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;

public class CustomHeadSearchGui extends LightweightGuiDescription {

   static List<JsonObject> allheads = new ArrayList<>();
   List<JsonObject> heads = new ArrayList<>();
   int headIndex = 0;

   public CustomHeadSearchGui() {
      WGridPanel root = new WGridPanel(1);
      setRootPanel(root);
      root.setSize(256, 240);

      CTextField searchbox = new CTextField(new LiteralText("Search..."));
      searchbox.setMaxLength(100);

      root.add(searchbox, 0, 0, 256, 0);

      WText loading = new WText(new LiteralText("Loading... (0%)"));
      root.add(loading, 100, 25, 100, 1);

      new Thread(() -> {
         try {
            if (allheads.size() < 30000) {
               allheads.clear();
               String[] categories = {"alphabet", "animals", "blocks", "decoration", "humans",
                   "humanoid", "miscellaneous", "monsters", "plants", "food-drinks"};
               int progress = 0;
               for (String cat : categories) {
                  String response = Webutil
                      .getString("https://minecraft-heads.com/scripts/api.php?cat=" + cat);
                  JsonArray headlist = new Gson().fromJson(response, JsonArray.class);
                  for (JsonElement head : headlist) {
                     allheads.add((JsonObject) head);
                  }

                  progress++;
                  loading.setText(new LiteralText(
                      "Loading... (" + (progress * 100 / categories.length) + "%)"));
               }
            }

            allheads.sort(Comparator.comparing(x -> x.get("name").getAsString()));

            heads = new ArrayList<>(allheads);

            root.remove(loading);

            WGridPanel panel = new WGridPanel(1);
            WScrollPanel scrollPanel = new WScrollPanel(panel);
            scrollPanel.setScrollingVertically(TriState.TRUE);
            scrollPanel.setScrollingHorizontally(TriState.FALSE);

            for (JsonObject head : heads) {
               ItemStack item = new ItemStack(Items.PLAYER_HEAD);

               String name = head.get("name").getAsString();
               String value = head.get("value").getAsString();
               item.setTag(StringNbtReader.parse(
                   "{display:{Name:\"{\\\"text\\\":\\\"" + name + "\\\"}\"},SkullOwner:{Id:"
                       + CustomHeadCommand.genId()
                       + ",Properties:{textures:[{Value:\"" + value + "\"}]}}}"));
               CItem i = new CItem(item);
               i.hover = name;
               i.setClickListener(() -> {
                  CodeUtilities.giveCreativeItem(item);
                  assert MinecraftClient.getInstance().player != null;
                  MinecraftClient.getInstance().player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 2, 1);
               });
               panel.add(i, (int) (headIndex % 14 * 17.8), headIndex / 14 * 18, 17, 18);
               headIndex++;
               if (headIndex > 153) {
                  break;
               }
            }

            WButton button = new WButton(new LiteralText("Load more"));
            button.setOnClick(() -> {
               int oldIndex = headIndex;
               do {
                  if (headIndex >= heads.size()) {
                     break;
                  }
                  JsonObject head = heads.get(headIndex);
                  ItemStack item = new ItemStack(Items.PLAYER_HEAD);

                  String name = head.get("name").getAsString();
                  String value = head.get("value").getAsString();
                  try {
                     item.setTag(StringNbtReader.parse(
                         "{display:{Name:\"{\\\"text\\\":\\\"" + name
                             + "\\\"}\"},SkullOwner:{Id:" + CustomHeadCommand.genId()
                             + ",Properties:{textures:[{Value:\"" + value + "\"}]}}}"));
                  } catch (CommandSyntaxException e) {
                     e.printStackTrace();
                  }
                  CItem i = new CItem(item);
                  i.hover = name;
                  i.setClickListener(() -> {
                     CodeUtilities.giveCreativeItem(item);
                     assert MinecraftClient.getInstance().player != null;
                     MinecraftClient.getInstance().player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 2, 1);
                  });
                  panel.add(i, (int) (headIndex % 14 * 17.8), headIndex / 14 * 18, 17, 18);
                  headIndex++;
               } while (headIndex <= 41 + oldIndex);
               panel.remove(button);
               if (headIndex < heads.size()) {
                  panel.add(button, 75, (int) (Math.ceil(((double) headIndex) / 14) * 18), 100, 18);
               }

               Field f = null;
               try {
                  f = scrollPanel.getClass().getDeclaredField("verticalScrollBar");
               } catch (NoSuchFieldException e) {
                  e.printStackTrace();
               }
               assert f != null;
               f.setAccessible(true);
               try {
                  WScrollBar bar = ((WScrollBar) f.get(scrollPanel));
                  bar.onMouseDrag(0, 0, 0);
                  scrollPanel.layout();
                  bar.onMouseDrag(0, 999, 0);
               } catch (IllegalAccessException e) {
                  e.printStackTrace();
               }

            });
            if (headIndex < heads.size()) {
               panel.add(button, 75, (int) (Math.ceil(((double) headIndex) / 14) * 18), 100, 18);
            }
            root.add(scrollPanel, 0, 25, 256, 220);

            searchbox.setChangedListener(query -> {
               root.remove(scrollPanel);

               if (query.isEmpty()) {
                  heads = new ArrayList<>(allheads);
               } else {
                  heads.clear();

                  query = query.toLowerCase();
                  for (JsonObject head : allheads) {
                     String name = head.get("name").getAsString();
                     name = name.toLowerCase();

                     if (name.contains(query)) {
                        heads.add(head);
                     }
                  }

               }

               Field f = null;
               try {
                  f = WPanel.class.getDeclaredField("children");
               } catch (NoSuchFieldException e) {
                  e.printStackTrace();
               }
               assert f != null;
               f.setAccessible(true);
               try {
                  List<WWidget> children = ((List) f.get(panel));
                  children.clear();
               } catch (IllegalAccessException e) {
                  e.printStackTrace();
               }
               headIndex = 0;

               for (JsonObject head : heads) {
                  ItemStack item = new ItemStack(Items.PLAYER_HEAD);

                  String name = head.get("name").getAsString();
                  String value = head.get("value").getAsString();
                  try {
                     item.setTag(StringNbtReader.parse(
                         "{display:{Name:\"{\\\"text\\\":\\\"" + name + "\\\"}\"},SkullOwner:{Id:"
                             + CustomHeadCommand.genId()
                             + ",Properties:{textures:[{Value:\"" + value + "\"}]}}}"));
                  } catch (CommandSyntaxException e) {
                     e.printStackTrace();
                  }
                  CItem i = new CItem(item);
                  i.hover = name;
                  i.setClickListener(() -> {
                     CodeUtilities.giveCreativeItem(item);
                     assert MinecraftClient.getInstance().player != null;
                     MinecraftClient.getInstance().player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 2, 1);
                  });
                  panel.add(i, (int) (headIndex % 14 * 17.8), headIndex / 14 * 18, 17, 18);
                  headIndex++;
                  if (headIndex > 153) {
                     break;
                  }
               }
               panel.remove(button);
               if (headIndex < heads.size()) {
                  panel.add(button, 75, (int) (Math.ceil(((double) headIndex) / 14) * 18), 100, 18);
               }

               root.add(scrollPanel, 0, 25, 256, 220);
            });
            if (!searchbox.getText().isEmpty()) {
               searchbox.setText(searchbox.getText());
            }
         } catch (Exception e) {
            loading.setText(new LiteralText("§cFailed to load!"));
            e.printStackTrace();
         }
      }).start();

      root.validate(this);
   }

}