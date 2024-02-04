package dev.rosewood.rosestacker.listener;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosestacker.manager.ConfigurationManager.Setting;
import dev.rosewood.rosestacker.manager.StackManager;
import dev.rosewood.rosestacker.stack.StackedEntity;
import dev.rosewood.rosestacker.stack.settings.EntityStackSettings;
import dev.rosewood.rosestacker.utils.EntitySpawnUtil;
import dev.rosewood.rosestacker.utils.PersistentDataUtils;
import dev.rosewood.rosestacker.utils.StackerUtils;
import dev.rosewood.rosestacker.utils.ThreadUtils;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class BreedingListener implements Listener {

    private final RosePlugin rosePlugin;

    public BreedingListener(RosePlugin rosePlugin) {
        this.rosePlugin = rosePlugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreed(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Animals animal) || !animal.canBreed() || (entity instanceof Tameable tameable && !tameable.isTamed()))
            return;

        StackManager stackManager = this.rosePlugin.getManager(StackManager.class);
        if (!stackManager.isEntityStackingEnabled())
            return;

        StackedEntity stackedEntity = stackManager.getStackedEntity(animal);
        if (stackedEntity == null)
            return;

        Player player = event.getPlayer();
        EntityStackSettings stackSettings = stackedEntity.getStackSettings();
        ItemStack breedingItem = player.getInventory().getItem(event.getHand());
        int entireInventoryBreedItems = getEntireInventoryLootItems(player, player.getInventory().getItem(event.getHand()));

        if (breedingItem == null || !stackSettings.getEntityTypeData().isValidBreedingMaterial(breedingItem.getType()) || (player.getGameMode() != GameMode.CREATIVE && breedingItem.getAmount() < 2))
            return;

        if (PersistentDataUtils.isAiDisabled(animal) && Setting.SPAWNER_DISABLE_MOB_AI_OPTIONS_DISABLE_BREEDING.getBoolean()) {
            event.setCancelled(true);
            return;
        }

        if (!Setting.ENTITY_CUMULATIVE_BREEDING.getBoolean())
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
            int inHandItemsAmount = breedingItem.getAmount();
            int requiredFood = Math.min(stackSize, entireInventoryBreedItems);


            if (requiredFood %2 != 0)
                requiredFood--;


            if (requiredFood == 0)
                return;


            if (requiredFood >= inHandItemsAmount) {
                player.getInventory().setItem(event.getHand(), null);
                if (requiredFood > inHandItemsAmount)
                    removeItemFromHand(player.getInventory(), breedingItem, requiredFood - inHandItemsAmount);
            } else {
                ItemStack newItem = breedingItem.clone();
                newItem.setAmount(inHandItemsAmount - requiredFood);
                player.getInventory().setItem(event.getHand(), newItem);
            }


            totalChildren = requiredFood / 2;
        } else {
            // Creative mode should allow the entire stack to breed half as many babies as the total size
            totalChildren = stackSize / 2;
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
                EntitySpawnUtil.spawn(animal.getLocation(), entityClass , x -> {
                    Ageable baby = (Ageable) x;
                    if(baby instanceof Tameable tameable) {
                        tameable.setOwner(player);
                        if (tameable instanceof Cat cat && stackedEntity.getStackSettings().getSettingValue(EntityStackSettings.CAT_DONT_STACK_IF_DIFFERENT_TYPE).getBoolean()){
                            assert animal instanceof Cat;
                            cat.setCatType(((Cat) animal).getCatType());
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

    private int getEntireInventoryLootItems(Player player, ItemStack itemstack) {

        int amount = 0;
        for (ItemStack inventoryItemStack : player.getInventory().getContents()) {
            if (itemstack.isSimilar(inventoryItemStack)) {
                assert inventoryItemStack != null;
                amount += inventoryItemStack.getAmount();
            }
        }
        return amount;
    }

    private void removeItemFromHand(Inventory inventory, ItemStack itemStack, int amount) {
        int amountRemoved = 0;

        for (int i = 0; i < inventory.getSize() && amountRemoved < amount; i++) {
            ItemStack _itemStack = inventory.getItem(i);
            if (_itemStack != null && _itemStack.isSimilar(itemStack)) {
                if (amountRemoved + _itemStack.getAmount() <= amount) {
                    amountRemoved += _itemStack.getAmount();
                    inventory.setItem(i, new ItemStack(Material.AIR));
                } else {
                    _itemStack.setAmount(_itemStack.getAmount() - amount + amountRemoved);
                    amountRemoved = amount;
                }
            }
        }
    }

}
