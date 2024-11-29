package dev.rosewood.rosestacker.listener;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.utils.EntitySpawnUtil;
import dev.rosewood.rosestacker.config.SettingKey;
import dev.rosewood.rosestacker.manager.StackManager;
import dev.rosewood.rosestacker.stack.StackedEntity;
import dev.rosewood.rosestacker.stack.settings.EntityStackSettings;
import dev.rosewood.rosestacker.utils.PersistentDataUtils;
import dev.rosewood.rosestacker.utils.StackerUtils;
import dev.rosewood.rosestacker.utils.ThreadUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Colorable;

import java.util.Random;

import static dev.rosewood.rosestacker.config.SettingKey.ENTITY_WHOLE_INVENTORY_BREEDING;

public class BreedingListener implements Listener {

    private final RosePlugin rosePlugin;

    public BreedingListener(RosePlugin rosePlugin) {
        this.rosePlugin = rosePlugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreed(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Animals animal) || !animal.canBreed())
            return;

        StackManager stackManager = this.rosePlugin.getManager(StackManager.class);
        if (!stackManager.isEntityStackingEnabled())
            return;

        StackedEntity stackedEntity = stackManager.getStackedEntity(animal);
        if (stackedEntity == null)
            return;

        Player player = event.getPlayer();
        EntityStackSettings stackSettings = stackedEntity.getStackSettings();

        int breedingItemSize = 0;
        ItemStack breedingItem = player.getInventory().getItem(event.getHand());

        if (ENTITY_WHOLE_INVENTORY_BREEDING.get()) {
            ItemStack[] inv = player.getInventory().getContents();
            for (ItemStack item : inv) {
                if (item == null || item.getType() == Material.AIR) continue;
                if (item.isSimilar(breedingItem)) breedingItemSize += item.getAmount();
            }
        } else {
            breedingItemSize = breedingItem.getAmount();
        }


        if (breedingItem == null || !stackSettings.getEntityTypeData().isValidBreedingMaterial(breedingItem.getType()) || (player.getGameMode() != GameMode.CREATIVE && breedingItem.getAmount() < 2))
            return;

        if (PersistentDataUtils.isAiDisabled(animal) && SettingKey.SPAWNER_DISABLE_MOB_AI_OPTIONS_DISABLE_BREEDING.get()) {
            event.setCancelled(true);
            return;
        }

        if (!SettingKey.ENTITY_CUMULATIVE_BREEDING.get())
            return;

        int stackSize = stackedEntity.getStackSize();
        if (stackSize < 2)
            return;

        Class<? extends Entity> entityClass = animal.getType().getEntityClass();
        if (entityClass == null)
            return;

        event.setCancelled(true);

        // Take the items for breeding
        int totalChildren;
        if (player.getGameMode() != GameMode.CREATIVE) {
            int requiredFood = Math.min(stackSize, breedingItemSize);
            if (ENTITY_WHOLE_INVENTORY_BREEDING.get()) {
                ItemStack[] items = player.getInventory().getContents();
                int result = requiredFood;
                ItemStack validBreedItem = breedingItem.clone();
                System.out.println(items.length);
                for (ItemStack item : items) {
                    if (item == null || (item.getType() == Material.AIR) || (item.getType() != validBreedItem.getType())) continue;
                    if (result <= 0) break;
                    int itemsToTake = Math.min(result, item.getAmount());
                    item.setAmount(item.getAmount() - itemsToTake);
                    result -= itemsToTake;
                }
            } else {
                breedingItem.setAmount(breedingItemSize - requiredFood);
            }
            totalChildren = requiredFood / 2;
        } else {
            // Creative mode should allow the entire stack to breed half as many babies as the total size
            totalChildren = stackSize / 2;
        }

        if (animal instanceof Axolotl) {
            player.getInventory().addItem(new ItemStack(Material.BUCKET, breedingItemSize));
        }
        // Reset breeding timer and play the breeding effect
        animal.setAge(6000);
        animal.setBreedCause(player.getUniqueId());
        animal.playEffect(EntityEffect.LOVE_HEARTS);

        boolean disableAi = PersistentDataUtils.isAiDisabled(animal);


        // Drop experience and spawn entities a few ticks later
        int f_totalChildren = totalChildren;
        ThreadUtils.runSyncDelayed(() -> {
            for (int i = 0; i < f_totalChildren; i++)
                EntitySpawnUtil.spawn(animal.getLocation(), entityClass, x -> {
                    Ageable baby = (Ageable) x;
                    if (stackedEntity.getStackSettings().dontStackIfDifferentColor()) {
                        if (baby instanceof Colorable colorable){
                            colorable.setColor(((Colorable) animal).getColor());
                        }
                        switch (baby.getType()) {
                            case CAT -> ((Cat) baby).setCatType(((Cat) animal).getCatType());
                            case HORSE -> ((Horse) baby).setStyle(((Horse) animal).getStyle());
                            case AXOLOTL -> {
                                if (animal instanceof Axolotl axolotl) {
                                    double blueProbability = 0.00083;
                                    Random random = new Random();
                                    boolean isBlue = random.nextDouble() < blueProbability;
                                    if (isBlue) {
                                        axolotl.setVariant(Axolotl.Variant.BLUE);
                                    } else {
                                        axolotl.setVariant(((Axolotl) animal).getVariant());
                                    }
                                }
                            }
                            case LLAMA -> ((Llama) baby).setColor(((Llama) animal).getColor());
                        }
                    }
                    if (stackedEntity.getStackSettings().dontStackIfDifferentStyle()){
                        if (baby instanceof Horse horse){
                            horse.setStyle(((Horse) animal).getStyle());
                        }
                    }
                    baby.setBaby();
                    if (disableAi)
                        PersistentDataUtils.removeEntityAi(baby);
                });

            StackerUtils.dropExperience(animal.getLocation(), totalChildren, 7 * totalChildren, totalChildren);

            // Increment statistic
            player.incrementStatistic(Statistic.ANIMALS_BRED, totalChildren);
        }, 30);
    }

}
