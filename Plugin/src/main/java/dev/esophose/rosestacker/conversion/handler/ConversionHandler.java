package dev.esophose.rosestacker.conversion.handler;

import dev.esophose.rosestacker.RoseStacker;
import dev.esophose.rosestacker.conversion.ConversionData;
import dev.esophose.rosestacker.manager.StackManager;
import dev.esophose.rosestacker.nms.NMSHandler;
import dev.esophose.rosestacker.nms.NMSUtil;
import dev.esophose.rosestacker.stack.StackType;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

/**
 * Handles converting data that we weren't able to without having specific locations
 */
public abstract class ConversionHandler {

    protected RoseStacker roseStacker;
    protected StackManager stackManager;

    private StackType requiredDataStackType;
    private boolean alwaysRequire;

    public ConversionHandler(RoseStacker roseStacker, StackType requiredDataStackType, boolean alwaysRequire) {
        this.roseStacker = roseStacker;
        this.stackManager = this.roseStacker.getManager(StackManager.class);
        this.requiredDataStackType = requiredDataStackType;
        this.alwaysRequire = alwaysRequire;
    }

    public abstract void handleConversion(Set<ConversionData> conversionData);

    public StackType getRequiredDataStackType() {
        return this.requiredDataStackType;
    }

    public boolean isDataAlwaysRequired() {
        return this.alwaysRequire;
    }

    /**
     * Used to fill in the missing entity stack nbt data
     *
     * @param entityType The type of entity
     * @param amount The amount of nbt entries to create
     * @param location The location of the main entity
     * @return A list of nbt data
     */
    protected List<byte[]> createEntityStackNBT(EntityType entityType, int amount, Location location) {
        List<byte[]> entityNBT = new LinkedList<>();

        NMSHandler nmsHandler = NMSUtil.getHandler();
        for (int i = 0; i < amount; i++)
            entityNBT.add(nmsHandler.getEntityAsNBT(nmsHandler.createEntityUnspawned(entityType, location)));

        return Collections.synchronizedList(entityNBT);
    }

}