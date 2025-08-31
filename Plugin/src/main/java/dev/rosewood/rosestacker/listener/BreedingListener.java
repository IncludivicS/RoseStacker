package dev.rosewood.rosestacker.listener;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.utils.EntitySpawnUtil;
import dev.rosewood.rosegarden.utils.NMSUtil;
import dev.rosewood.rosestacker.config.SettingKey;
import dev.rosewood.rosestacker.manager.StackManager;
import dev.rosewood.rosestacker.stack.StackedEntity;
import dev.rosewood.rosestacker.stack.settings.EntityStackSettings;
import dev.rosewood.rosestacker.utils.PersistentDataUtils;
import dev.rosewood.rosestacker.utils.StackerUtils;
import dev.rosewood.rosestacker.utils.ThreadUtils;
import io.papermc.paper.entity.CollarColorable;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.*;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Colorable;

import java.util.Random;

import static dev.rosewood.rosestacker.config.SettingKey.ENTITY_WHOLE_INVENTORY_BREEDING;
import org.bukkit.material.Colorable;

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
                for (ItemStack item : items) {
                    if (item == null || (item.getType() == Material.AIR) || (item.getType() != validBreedItem.getType()))
                        continue;
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
            // Creative mode should allow the entire stack to breed half as many babies as the max stack size of the
            // item they are holding, without actually taking any items
            totalChildren = Math.max(1, breedingItem.getMaxStackSize() / 2);
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
        ThreadUtils.runSyncDelayed(() -> {
            ItemStack breedingItemCopy = breedingItem.clone();
            breedingItemCopy.setAmount(1);
            boolean callEvents = SettingKey.ENTITY_CUMULATIVE_BREEDING_TRIGGER_BREED_EVENT.get();
            int totalExperience = callEvents ? 0 : totalChildren * 7;
            boolean modern = NMSUtil.getVersionNumber() >= 21 && NMSUtil.isPaper();
            for (int i = 0; i < totalChildren; i++) {
                LivingEntity child;
                if (modern) {
                    child = (LivingEntity) animal.getLocation().getWorld().spawn(animal.getLocation(), entityClass, CreatureSpawnEvent.SpawnReason.BREEDING, x -> {
                        Ageable baby = (Ageable) x;
                        baby.setBaby();
                        this.transferEntityProperties(animal, baby);
                        if (disableAi)
                            PersistentDataUtils.removeEntityAi(baby);
                    });
                } else {
                    child = (LivingEntity) EntitySpawnUtil.spawn(animal.getLocation(), entityClass, x -> {
                        Ageable baby = (Ageable) x;
                        baby.setBaby();
                        this.transferEntityProperties(animal, baby);
                        if (disableAi)
                            PersistentDataUtils.removeEntityAi(baby);
                    });
                }

                if (callEvents) {
                    EntityBreedEvent breedEvent = new EntityBreedEvent(child, animal, animal, player, breedingItemCopy.clone(), 7);
                    Bukkit.getPluginManager().callEvent(breedEvent);
                    if (breedEvent.isCancelled()) {
                        child.remove();
                        breedingItem.setAmount(breedingItem.getAmount() + 2);
                    } else {
                        totalExperience += breedEvent.getExperience();
                    }
                }
            }

            StackerUtils.dropExperience(animal.getLocation(), totalChildren, totalExperience, totalChildren);
            EntitySpawnUtil.spawn(animal.getLocation(), entityClass, x -> {
                Ageable baby = (Ageable) x;
                if (baby instanceof Tameable tameable) {
                    if (animal instanceof Tameable animalTameable) {
                        if (animalTameable.isTamed()) {
                            tameable.setOwner(player);
                        }
                    }
                };

                if (stackedEntity.getStackSettings().dontStackIfDifferentColor() || stackedEntity.getStackSettings().dontStackIfDifferentType()) {
                    if (baby instanceof Colorable colorable) {
                        colorable.setColor(((Colorable) animal).getColor());
                    }
                    switch (baby.getType()) {
                        case CAT -> {
                            if (baby instanceof Cat cat && animal instanceof Cat parent)
                                cat.setCatType(parent.getCatType());
                        }
                        case HORSE -> {
                            if (baby instanceof Horse horse && animal instanceof Horse parent) {
                                horse.setColor(parent.getColor());
                            }
                        }
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
                        case LLAMA -> {
                            if (baby instanceof Llama llama && animal instanceof Llama parent)
                                llama.setColor(parent.getColor());
                        }
                        case SHEEP -> {
                            if (baby instanceof Sheep sheep)
                                sheep.setColor(((Sheep) animal).getColor());
                        }
                        case TRADER_LLAMA -> {
                            if (baby instanceof TraderLlama traderLlama && animal instanceof TraderLlama parent)
                                traderLlama.setColor((parent).getColor());
                        }
                        case MOOSHROOM -> {
                            if (baby instanceof MushroomCow mushroomCow && animal instanceof MushroomCow parent)
                                mushroomCow.setVariant(parent.getVariant());
                        }
                        case PARROT -> {
                            if (baby instanceof Parrot parrot && animal instanceof Parrot parent)
                                parrot.setVariant(parent.getVariant());
                        }
                        case RABBIT -> {
                            if (baby instanceof Rabbit rabbit && animal instanceof Rabbit parent)
                                rabbit.setRabbitType(parent.getRabbitType());
                        }
                        case WOLF -> {
                            if (baby instanceof Wolf wolf && animal instanceof Wolf parent) {
                                wolf.setVariant(parent.getVariant());
                            }
                        }
                    }
                }
                if (stackedEntity.getStackSettings().dontStackIfDifferentStyle()) {
                    if (baby instanceof Horse horse) {
                        horse.setStyle(((Horse) animal).getStyle());
                    }
                }
                stackManager.setEntityStackingTemporarilyDisabled(true);
                StackedEntity stackedBaby = stackManager.createEntityStack((LivingEntity) x, false);
                assert stackedBaby != null;
                stackedBaby.increaseStackSize(f_totalChildren - 1, true);
                baby.setBaby();
                if (disableAi)
                    PersistentDataUtils.removeEntityAi(baby);
            });
            stackManager.setEntityStackingTemporarilyDisabled(false);
            StackerUtils.dropExperience(animal.getLocation(), totalChildren, 7 * totalChildren, totalChildren);

            // Increment statistic
            player.incrementStatistic(Statistic.ANIMALS_BRED, totalChildren);
        }, 30);
    }

    private void transferEntityProperties(LivingEntity parent, LivingEntity child) {
        if (parent instanceof Colorable colorableParent && child instanceof Colorable colorableChild)
            colorableChild.setColor(colorableParent.getColor());

        if (parent instanceof Tameable tameableParent && child instanceof Tameable tameableChild)
            tameableChild.setOwner(tameableParent.getOwner());

        if (NMSUtil.isPaper() && NMSUtil.getVersionNumber() >= 19 && parent instanceof CollarColorable collarColorableParent && child instanceof CollarColorable collarColorableChild)
            collarColorableChild.setCollarColor(collarColorableParent.getCollarColor());

        if (parent instanceof Cat parentCat && child instanceof Cat childCat)
            childCat.setCatType(parentCat.getCatType());

        if (parent instanceof Fox parentFox && child instanceof Fox childFox)
            childFox.setFoxType(parentFox.getFoxType());

        if (NMSUtil.getVersionNumber() >= 19 && parent instanceof Frog parentFrog && child instanceof Frog childFrog)
            childFrog.setVariant(parentFrog.getVariant());

        if (parent instanceof Horse parentHorse && child instanceof Horse childHorse) {
            childHorse.setStyle(parentHorse.getStyle());
            childHorse.setColor(parentHorse.getColor());
        }

        if (parent instanceof MushroomCow parentMooshroom && child instanceof MushroomCow childMooshroom)
            childMooshroom.setVariant(parentMooshroom.getVariant());

        if (parent instanceof Panda parentPanda && child instanceof Panda childPanda)
            childPanda.setMainGene(parentPanda.getMainGene());

        if (parent instanceof Rabbit parentRabbit && child instanceof Rabbit childRabbit)
            childRabbit.setRabbitType(parentRabbit.getRabbitType());

        if (parent instanceof Llama parentLlama && child instanceof Llama childLlama)
            childLlama.setColor(parentLlama.getColor());

        if ((NMSUtil.getVersionNumber() > 20 || (NMSUtil.getVersionNumber() == 20 && NMSUtil.getMinorVersionNumber() >= 6)) && parent instanceof Wolf parentWolf && child instanceof Wolf childWolf)
            childWolf.setVariant(parentWolf.getVariant());
    }

}
