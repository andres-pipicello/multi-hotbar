package com.rolandoislas.multihotbar;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Rolando on 6/7/2016.
 */
public class HotbarLogic {
    public static int hotbarIndex = 0;
    public static int[] hotbarOrder = new int[Config.MAX_HOTBARS];
    private static boolean showDefault = false;
    private static WorldJson[] worldJsonArray;
    private String worldAddress;
    private ArrayList<Integer> pickupSlot = new ArrayList<Integer>();
    private boolean isWorldLocal;
    private List<ItemStack> inventory;
    private static int inventoryReorderDelayTicks = 0;
    private int pickedUpAmountThisTick = 0;
    private int waitForItemTicks = 0;
    private static ConcurrentHashMap<Integer, Integer> ignoreSlots = new ConcurrentHashMap<Integer, Integer>();

    /**
     * Checks if the custom hotbar should be shown
     * @return boolean
     */
    static boolean shouldShowDefault() {
        boolean isSpectator = Minecraft.getMinecraft().player != null &&
                Minecraft.getMinecraft().player.isSpectator();
        return showDefault || isSpectator;
    }

    /**
     * Set the state of the hotbar.
     * @param showDefault should the normal hotbar be shown
     */
    private static void setShowDefault(boolean showDefault) {
        HotbarLogic.showDefault = showDefault;
    }

    /**
     * Handles mouse scroll for hotbar
     * @param event mouse event
     */
    public void mouseEvent(MouseEvent event) {
        if (InventoryHelper.waitForInventoryTweaks() || HotbarLogic.shouldShowDefault())
            return;
        // Scrolled
        if (event.getDwheel() != 0) {
            // Handle hotbar selector scroll
            EntityPlayerSP player = Minecraft.getMinecraft().player;
            // Scrolled right
            if (event.getDwheel() < 0) {
                if (KeyBindings.scrollModifier.isKeyDown())
                    moveSelectionToNextHotbar();
                else if (player.inventory.currentItem < InventoryPlayer.getHotbarSize() - 1)
                    player.inventory.currentItem++;
                else {
                    player.inventory.currentItem = 0;
                    moveSelectionToNextHotbar();
                }
            }
            // Scrolled left
            else {
                if (KeyBindings.scrollModifier.isKeyDown())
                    moveSelectionToPreviousHotbar();
                else if (player.inventory.currentItem > 0)
                    player.inventory.currentItem--;
                else {
                    player.inventory.currentItem = InventoryPlayer.getHotbarSize() - 1;
                    moveSelectionToPreviousHotbar();
                }
            }
            event.setCanceled(true);
            resetTooltipTicks();
        }
    }

    /**
     * Go to previous hotbar keeping currently selected item. Loops to last hotbar.
     */
    private void moveSelectionToPreviousHotbar() {
        moveSelection(false);
    }

    /**
     * Move to adjacent hotbar. Loops to first or last hotbar.
     * @param forward move forward instead of backward
     */
    private void moveSelection(boolean forward) {
        if (Config.numberOfHotbars == 1)
            return;
        int previousIndex = hotbarIndex;
        hotbarIndex += forward ? 1 : -1; // Change hotbar
        hotbarIndex = hotbarIndex < 0 ? Config.numberOfHotbars - 1 : hotbarIndex; // Loop from first to last
        hotbarIndex = hotbarIndex >= Config.numberOfHotbars ? 0 : hotbarIndex; // Loop from last to first
        InventoryHelper.swapHotbars(0, hotbarOrder[hotbarIndex]);
        // save swapped position
        int orderFirst = hotbarOrder[previousIndex];
        hotbarOrder[previousIndex] = hotbarOrder[hotbarIndex];
        hotbarOrder[hotbarIndex] = orderFirst;
    }

    /**
     * Go to next hotbar. Loops to first hotbar.
     */
    private void moveSelectionToNextHotbar() {
        moveSelection(true);
    }

    /**
     * Handles button events
     * @param event Key input
     */
    public void keyPressed(InputEvent.KeyInputEvent event) {
        if (InventoryHelper.waitForInventoryTweaks())
            return;
        // Check toggle key
        if (KeyBindings.showDefaultHotbar.isPressed()) {
            setShowDefault(!shouldShowDefault());
            Minecraft.getMinecraft().gameSettings.heldItemTooltips = shouldShowDefault();
        }
        if (HotbarLogic.shouldShowDefault())
            return;
        // Check hotbar keys
        int slot = KeyBindings.isHotbarKeyDown();
        int currentItem = Minecraft.getMinecraft().player.inventory.currentItem;
        // Change hotbar if modifier key is down and a number is presses
        if (slot > -1 && KeyBindings.scrollModifier.isKeyDown() && slot < Config.numberOfHotbars)
            moveSelectionToHotbar(slot);
        // Change hotbars if pressed number matches currently selected slot
        else if (slot > -1 && currentItem == slot && Config.numberOfHotbars > 1) {
            moveSelectionToNextHotbar();
        }
        // Select a slot
        else if (slot > - 1) {
            Minecraft.getMinecraft().player.inventory.currentItem = slot;
            if (!Config.relativeHotbarKeys)
                moveSelectionToHotbar(0);
        }
        if (slot > -1)
            resetTooltipTicks();
    }

    /**
     * Set tooltip ticks to default show time
     */
    private void resetTooltipTicks() {
        HotBarRenderer.tooltipTicks = 128;
    }

    /**
     * Move to a specific hotbar.
     * @param index hotbar index
     */
    private void moveSelectionToHotbar(int index) {
        while (hotbarIndex != index)
            moveSelectionToNextHotbar();
    }

    /**
     * Reset hotbar.
     * Updates index, order, current item, and toggle.
     * @param resetCurrentItem should the current item be reset
     */
    private static void reset(boolean resetCurrentItem) {
        setShowDefault(false);
        updateTooltips();
        hotbarIndex = 0;
        for (int i = 0; i < Config.MAX_HOTBARS; i++)
            hotbarOrder[i] = i;
        try {
            if (resetCurrentItem)
                Minecraft.getMinecraft().player.inventory.currentItem = 0;
        } catch (Exception ignore) {}
    }

    /**
     * @see HotbarLogic#reset(boolean)
     */
    private static void reset() {
        reset(true);
    }

    /**
     * Save the hotbar state for the current world to json.
     */
    private void save() {
        String path = Config.config.getConfigFile().getAbsolutePath().replace("cfg", "json");
        try {
            boolean found = false;
            if (worldJsonArray != null) {
                for (WorldJson worldJson : worldJsonArray) {
                    if (worldJson.getId().equals(getWorldId())) {
                        found = true;
                        worldJson.setIndex(hotbarIndex);
                        worldJson.setOrder(hotbarOrder);
                        break;
                    }
                }
            }
            if ((!found) || worldJsonArray == null){
                if (worldJsonArray == null)
                    worldJsonArray = new WorldJson[1];
                else
                    worldJsonArray = Arrays.copyOf(worldJsonArray, worldJsonArray.length + 1);
                int index = worldJsonArray.length - 1;
                worldJsonArray[index] = new WorldJson();
                worldJsonArray[index].setId(getWorldId());
                worldJsonArray[index].setIndex(hotbarIndex);
                worldJsonArray[index].setOrder(hotbarOrder);
            }
            Gson gson = new Gson();
            FileWriter writer = new FileWriter(path);
            String json = gson.toJson(worldJsonArray);
            writer.write(json);
            writer.close();
        } catch (IOException ignore) {}
    }

    /**
     * Load the hotbar state for current world from json. Defaults set if entry not found.
     */
    private void load() {
        String path = Config.config.getConfigFile().getAbsolutePath().replace("cfg", "json");
        try {
            Gson gson = new Gson();
            JsonReader reader = new JsonReader(new FileReader(path));
            worldJsonArray = gson.fromJson(reader, WorldJson[].class);
            if (worldJsonArray != null) {
                for (WorldJson worldJson : worldJsonArray) {
                    if (worldJson.getId().equals(getWorldId())) {
                        reset(false);
                        hotbarIndex = worldJson.getIndex();
                        hotbarOrder = worldJson.getOrder();
                        break;
                    } else
                        reset();
                }
            }
            else
                reset();
        } catch (FileNotFoundException ignore) {
            reset();
        }
    }

    /**
     * Updates minecraft game config to show or hide vanilla item tooltips. Based on if the vanilla hotbar is shown.
     */
    private static void updateTooltips() {
        Minecraft.getMinecraft().gameSettings.heldItemTooltips = shouldShowDefault();
    }

    /**
     * Get the unique world ID.
     * Local server uses seed, world name, and directory path.
     * IP address if remote server.
     * @return MD5 of world ID
     */
    private String getWorldId() {
        // Construct unique id or use world address if remote
        String id;
        if (isWorldLocal) {
            World world = Minecraft.getMinecraft().getIntegratedServer().getEntityWorld();
            id = world.getWorldInfo().getSeed() + world.getWorldInfo().getWorldName() +
                    world.getSaveHandler().getWorldDirectory().getAbsolutePath();
        }
        else {
            id = worldAddress;
        }
        // MD5 because the raw id looks horrible with an escaped path and spaces in it
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] array = md.digest(id.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte anArray : array)
                sb.append(Integer.toHexString((anArray & 0xFF) | 0x100).substring(1, 3));
            id = sb.toString();
        } catch (NoSuchAlgorithmException ignore) {}
        return id;
    }

    /**
     * Get the first empty stack following the long hotbar order.
     * @return
     */
    private int getFirstEmptyStack() {
        for (int i = 0; i < Config.numberOfHotbars; i++) {
            for (int j = 0; j < 9; j++) {
                int index = hotbarOrder[i] * 9 + j;
                ItemStack stack = Minecraft.getMinecraft().player.inventory.getStackInSlot(index);
                if (stack.isEmpty())
                    return index;
            }
        }
        return -1;
    }

    /**
     * Determines if the picked up item needs to be reordered. If it does, it is put into the reorder queue.
     * @param event pickup event
     */
    public void pickupEvent(EntityItemPickupEvent event) {
        if (shouldShowDefault() || Config.relativeHotbarPickups)
            return;
        // Check if compatible stack is in inventory
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null)
            return;
        int slot = getFirstCompatibleStack(event.getItem().getEntityItem());
        if (slot >= 0) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() + event.getItem().getEntityItem().getCount() <= stack.getMaxStackSize())
                return;
        }
        // Get the first empty stack
        slot = getFirstEmptyStackVanilla(pickedUpAmountThisTick);
        // No space in inventory
        if (slot < 0)
            return;
        // Does not need a move
        if (slot == getFirstEmptyStack())
            return;
        this.pickupSlot.add(slot);
        addInventoryReorderDelay(5);
        pickedUpAmountThisTick++;
    }

    /**
     * Get the first stack with vanilla ordering.
     * @param skip Empty slots to skip
     * @return slot index (0-35)
     */
    private int getFirstEmptyStackVanilla(int skip) {
        NonNullList<ItemStack> mainInventory = Minecraft.getMinecraft().player.inventory.mainInventory;
        for (int i = 0; i < mainInventory.size(); ++i)
            if (mainInventory.get(i).isEmpty())
                if (skip-- == 0)
                    return i;
        return -1;
    }

    /**
     * Finds first non full stack of the same item type
     * @param itemStack item type to find
     * @return slot index (0-35)
     */
    private int getFirstCompatibleStack(ItemStack itemStack) {
        for (int i = 0; i < Config.numberOfHotbars; i++) {
            for (int j = 0; j < 9; j++) {
                int index = hotbarOrder[i] * 9 + j;
                ItemStack stack = Minecraft.getMinecraft().player.inventory.getStackInSlot(index);
                if (!stack.isEmpty() && stack.isStackable() && stack.isItemEqual(itemStack) &&
                        ItemStack.areItemStackTagsEqual(stack, itemStack) &&
                        stack.getCount() < stack.getMaxStackSize()) {
                    return index;
                }
            }
        }
        return -1;
    }

    /**
     * Try to reoder the slots in the queue.
     * Will wait a few ticks for item to appear in inventory.
     * After too many ticks top item in queue is removed.
     */
    private void reorderPickedupItem() {
        if (shouldShowDefault() || Config.relativeHotbarPickups)
            return;
        // Update item tick counters
        pickedUpAmountThisTick = 0;
        if (inventoryReorderDelayTicks > 0) {
            inventoryReorderDelayTicks--;
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        // Nothing to move
        if (this.pickupSlot.isEmpty())
            return;
        // Wait for item to appear after an uncertain number of ticks
        if (waitForItemTicks >= 40) {
            this.pickupSlot.remove(0);
            waitForItemTicks = 0;
            return;
        }
        else
            waitForItemTicks++;
        if (player.inventory.getStackInSlot(pickupSlot.get(0)).isEmpty())
            return;
        // Move the picked up item to the correct slot
        int clickSlotFirst = InventoryHelper.mainInventoryToFullInventory(this.pickupSlot.get(0));
        int clickSlotSecond = InventoryHelper.mainInventoryToFullInventory(getFirstEmptyStack());
        InventoryHelper.swapSlot(clickSlotFirst, clickSlotSecond);
        if (this.pickupSlot.size() > 0)
            this.pickupSlot.remove(0);
    }

    /**
     * Sets world address and determines of world is local.
     * Calls load()
     * @param event client event
     */
    void connectedToServer(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        worldAddress = event.getManager().getRemoteAddress().toString();
        isWorldLocal = event.isLocal();
        load();
    }

    /**
     * Calls save()
     * @param event client event
     */
    void disconnectedFromServer(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        save();
    }

    /**
     * Checks if the player has died and resets if keepinventory game rule is disabled.
     * @param event death event
     */
    void deathEvent(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntity();
            if (!player.getUniqueID().equals(Minecraft.getMinecraft().player.getUniqueID()))
                return;
            if (!player.world.getGameRules().getBoolean("keepInventory"))
                HotbarLogic.reset();
        }
    }

    /**
     * Handles item reorder.
     * Handles deadh and pickup event checks when connected to remote server.
     * @param event
     */
    public void playerTick(TickEvent.PlayerTickEvent event) {
        reorderPickedupItem();
        updateIgnoredSlotTicks();
        if (isWorldLocal)
            return;
        checkPlayerDeath();
        checkItemPickedUp();
    }

    /**
     * Decrements the ignored slots tick time every tick. Removes ignored slots when there time has expired.
     */
    private void updateIgnoredSlotTicks() {
        for (Map.Entry<Integer, Integer> slot : ignoreSlots.entrySet()) {
            if (slot.getValue() == 0)
                ignoreSlots.remove(slot.getKey());
            else
                slot.setValue(slot.getValue() - 1);
        }
    }

    /**
     * Check if the player has picked uo an item.
     * Item pickup event is not called when connected to remote servers.
     * Let the pickup event handler handle this when it can.
     */
    private void checkItemPickedUp() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        // Set the inventory
        if (inventory == null ||
                // Ignore if inventory is open TODO check inventory movement better
                Minecraft.getMinecraft().currentScreen instanceof GuiInventory) {
            inventory = new ArrayList<ItemStack>(player.inventory.mainInventory);
        }
        // Find the changed item
        ArrayList<EntityItem> changed = new ArrayList<EntityItem>();
        ArrayList<Integer> changedSlot = new ArrayList<Integer>();
        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            if (
                    // Check if slot should be ignored
                    (!ignoreSlots.containsKey(i)) && (
                    // Check if there is an item in a slot that was empty
                    (!player.inventory.mainInventory.get(i).isEmpty() && inventory.get(i).isEmpty()) ||
                    // Make sure the slots are equal
                    !player.inventory.mainInventory.get(i).isEmpty() &&
                    !(player.inventory.mainInventory.get(i).isItemEqual(inventory.get(i)) &&
                    ItemStack.areItemStackTagsEqual(player.inventory.mainInventory.get(i), inventory.get(i))))) {
                ItemStack changedStack = player.inventory.mainInventory.get(i).copy();
                changed.add(new EntityItem(player.world, player.posX, player.posY, player.posY, changedStack));
                changedSlot.add(i);
            }
        }
        // If no item changed it was probably just an item removal.
        if (!changed.isEmpty()) {
            int size = changed.size();
            if (size > 1 && areInventoryItemsSame(player.inventory.mainInventory, inventory))
                return;
            // Save inventory copy
            ItemStack[] inventoryUntouched = new ItemStack[player.inventory.mainInventory.size()];
            for (int slot = 0; slot < player.inventory.mainInventory.size(); slot++)
                inventoryUntouched[slot] = player.inventory.mainInventory.get(slot).copy();
            // Call the event handler
            for (int i = 0; i < size; i++) {
                int slot = changedSlot.get(0);
                // Remove item from inventory to emulate the inventory state that the event handler expects
                if (inventory.get(slot).isEmpty())
                    player.inventory.mainInventory.get(slot).setCount(0);
                else
                    player.inventory.mainInventory.get(slot).setCount(
                            player.inventory.mainInventory.get(slot).getCount() - inventory.get(slot).getCount());;
                if (player.inventory.mainInventory.get(slot).getCount() == 0)
                    player.inventory.removeStackFromSlot(slot);
                // Create the pickup event
                pickupEvent(new EntityItemPickupEvent(player, changed.get(0)));
                // Remove from array lists
                changed.remove(0);
                changedSlot.remove(0);
            }
            // Add item back to inventory to emulate the event having already taking place and the tick handler will
            // move it
            for (int slot = 0; slot < inventoryUntouched.length; slot++)
                player.inventory.mainInventory.set(slot, inventoryUntouched[slot]);
        }
        // Update cached inventory
        inventory = new ArrayList<ItemStack>(player.inventory.mainInventory);
    }

    /**
     * Compares two inventories, ignoring order, to see if the items are the same.
     * @param inventory first inventory
     * @param inventory2 second inventory
     * @return do the have the same items
     */
    private boolean areInventoryItemsSame(NonNullList<ItemStack> inventory, List<ItemStack> inventory2) {
        for (ItemStack item : inventory) {
            boolean found = false;
            for (ItemStack item2 : inventory2)
                if ((item.isEmpty() && item2.isEmpty()) || (!item.isEmpty() && !item2.isEmpty() && item.isItemEqual(item2) &&
                        ItemStack.areItemStackTagsEqual(item, item2)))
                    found = true;
            if (!found)
                return false;
        }
        return true;
    }

    /***
     * Check for a player death on remote servers.
     * The player death event is not called.
     * Let the death event handler evoke the reset if possible.
     */
    private void checkPlayerDeath() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (!player.isEntityAlive()) {
            for (ItemStack slot : player.inventoryContainer.getInventory())
                if (slot != null)
                    return;
            reset();
        }
    }

    /**
     * Add a tick delay to the item reordered.
     * @param delay ticks
     */
    private static void addInventoryReorderDelay(int delay) {
        inventoryReorderDelayTicks += delay;
    }

    /**
     * Ignore a slot for a few ticks
     * @param slot slot index (0-35)
     */
    static void ignoreSlot(int slot) {
        ignoreSlots.put(slot, 5);
    }
}
