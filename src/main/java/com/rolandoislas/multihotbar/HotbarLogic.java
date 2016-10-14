package com.rolandoislas.multihotbar;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import cpw.mods.fml.common.gameevent.InputEvent;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.MouseEvent;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Rolando on 6/7/2016.
 */
public class HotbarLogic {
    public static int hotbarIndex = 0;
    public static int[] hotbarOrder = new int[Config.MAX_HOTBARS];
    public static boolean showDefault = false;
    private static WorldJson[] worldJsonArray;
    private World world;
    private String worldAddress;
    private World dimWorld;

    public void mouseEvent(MouseEvent event) {
        if (InventoryHelper.waitTicks > 0 || HotbarLogic.showDefault)
            return;
        // Scrolled
        if (event.dwheel != 0) {
            // Handle hotbar selector scroll
            EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            // Scrolled right
            if (event.dwheel < 0) {
                if (KeyBindings.scrollModifier.getIsKeyPressed())
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
                if (KeyBindings.scrollModifier.getIsKeyPressed())
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

    private void moveSelectionToPreviousHotbar() {
        moveSelection(false);
    }

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

    private void moveSelectionToNextHotbar() {
        moveSelection(true);
    }

    public void keyPressed(InputEvent.KeyInputEvent event) {
        if (InventoryHelper.waitTicks > 0)
            return;
        // Check toggle key
        if (KeyBindings.showDefaultHotbar.isPressed()) {
            showDefault = !showDefault;
            Minecraft.getMinecraft().gameSettings.heldItemTooltips = showDefault;
        }
        if (HotbarLogic.showDefault)
            return;
        // Check hotbar keys
        int slot = KeyBindings.isHotbarKeyDown();
        int currentItem = Minecraft.getMinecraft().thePlayer.inventory.currentItem;
        // Change hotbars
        if (slot > -1 && currentItem == slot && Config.numberOfHotbars > 1) {
            moveSelectionToNextHotbar();
        }
        // Select a slot
        else if (slot > - 1) {
            Minecraft.getMinecraft().thePlayer.inventory.currentItem = slot;
            if (!Config.relativeHotbarKeys)
                moveSelectionToHotbar(0);
        }
        if (slot > -1)
            resetTooltipTicks();
    }

    private void resetTooltipTicks() {
        HotBarRenderer.tooltipTicks = 128;
    }

    private void moveSelectionToHotbar(int index) {
        if (hotbarIndex == index)
            return;
        InventoryHelper.swapHotbars(hotbarOrder[index], hotbarOrder[hotbarIndex]);
        hotbarOrder[hotbarIndex] = hotbarOrder[index];
        hotbarOrder[index] = 0;
        hotbarIndex = index;
    }

    public static void reset() {
        showDefault = false;
        updateTooltips();
        hotbarIndex = 0;
        for (int i = 0; i < Config.MAX_HOTBARS; i++)
            hotbarOrder[i] = i;
    }

    public void save() {
        if (this.world == null)
            return;
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
        this.dimWorld = this.world; // Backup incase it was just a dimension change
        this.world = null;
        updateTooltips();
    }

    public void load(World world) {
        if (this.world != null)
            return;
        this.world = world;
        String path = Config.config.getConfigFile().getAbsolutePath().replace("cfg", "json");
        try {
            Gson gson = new Gson();
            JsonReader reader = new JsonReader(new FileReader(path));
            worldJsonArray = gson.fromJson(reader, WorldJson[].class);
            if (worldJsonArray != null) {
                for (WorldJson worldJson : worldJsonArray) {
                    if (worldJson.getId().equals(getWorldId(world))) {
                        hotbarIndex = worldJson.getIndex();
                        hotbarOrder = worldJson.getOrder();
                        updateTooltips();
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

    private static void updateTooltips() {
        Minecraft.getMinecraft().gameSettings.heldItemTooltips = showDefault;
    }

    private String getWorldId() {
        return getWorldId(this.world);
    }

    private String getWorldId(World world) {
        String id = "";
        if (!world.isRemote) {
            id += world.getWorldInfo().getSeed() + world.getWorldInfo().getWorldName() + world.isRemote +
                    world.getSaveHandler().getWorldDirectory().getAbsolutePath();
        }
        else {
            id = worldAddress;
        }
        return id;
    }


    public void setWorldAddress(String worldAddress) {
        this.worldAddress = worldAddress;
    }

    public void playerChangedDimension() {
        load(this.dimWorld);
    }

    public void inputEvent(InputEvent event) {
        // Pick block
        while (KeyBindings.isPickBlockPressed())
            pickBlock();
    }

    private void pickBlock() {
        Minecraft minecraft = Minecraft.getMinecraft();
        double distance = minecraft.playerController.getBlockReachDistance();
        MovingObjectPosition target = minecraft.renderViewEntity.rayTrace(distance, 1);
        Vec3 position = minecraft.renderViewEntity.getPosition(1);
        Vec3 look = minecraft.renderViewEntity.getLook(1);
        Vec3 pick = position.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        List entities = minecraft.theWorld.getEntitiesWithinAABBExcludingEntity(minecraft.renderViewEntity,
                minecraft.renderViewEntity.boundingBox.addCoord(look.xCoord * distance,
                        look.yCoord * distance, look.zCoord * distance).expand(1, 1, 1));
        for (Object e : entities) {
            Entity entity = (Entity) e;
            if (entity.canBeCollidedWith()) {
                float borderSize = entity.getCollisionBorderSize();
                AxisAlignedBB axisalignedbb = entity.boundingBox.expand(borderSize, borderSize, borderSize);
                MovingObjectPosition movingobjectposition = axisalignedbb.calculateIntercept(position, pick);
                if (movingobjectposition != null)
                    target = new MovingObjectPosition(entity, movingobjectposition.hitVec);
            }
        }

        ItemStack result;
        boolean isCreative = minecraft.thePlayer.capabilities.isCreativeMode;

        if (target.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            int x = target.blockX;
            int y = target.blockY;
            int z = target.blockZ;
            Block block = minecraft.theWorld.getBlock(x, y, z);

            if (block.isAir(minecraft.theWorld, x, y, z))
                return;

            result = block.getPickBlock(target, minecraft.theWorld, x, y, z, minecraft.thePlayer);
        }
        else {
            if (target.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY || target.entityHit == null ||
                    !isCreative)
                return;
            result = target.entityHit.getPickedResult(target);
        }

        if (result == null)
            return;

        for (int i = 0; i < Config.numberOfHotbars; i++) {
            for (int j = 0; j < 9; j++) {
                ItemStack stack = minecraft.thePlayer.inventory.getStackInSlot(hotbarOrder[i] * 9 + j);
                if (stack != null && stack.isItemEqual(result) && ItemStack.areItemStackTagsEqual(stack, result)) {
                    minecraft.thePlayer.inventory.currentItem = j;
                    moveSelectionToHotbar(i);
                    return;
                }
            }
        }

        if (!isCreative)
            return;

        int slot = getFirstEmptyStack();
        if (slot < 0)
            slot = minecraft.thePlayer.inventory.currentItem;
        int hotbarIndexRaw = (int) Math.floor(slot / 9);
        int hotbarIndex = 0;
        for (int i = 0; i < Config.numberOfHotbars; i++) {
            if (hotbarOrder[i] == hotbarIndexRaw) {
                hotbarIndex = i;
                break;
            }
        }
        minecraft.thePlayer.inventory.currentItem = slot - hotbarIndexRaw * 9;
        minecraft.thePlayer.inventory.setInventorySlotContents(slot, result);
        minecraft.playerController.sendSlotPacket(result, slot >= 9 ? slot :
                minecraft.thePlayer.inventoryContainer.inventorySlots.size() - 9 + slot);
        moveSelectionToHotbar(hotbarIndex);
    }

    private int getFirstEmptyStack() {
        for (int i = 0; i < Config.numberOfHotbars; i++) {
            for (int j = 0; j < 9; j++) {
                int index = hotbarOrder[i] * 9 + j;
                ItemStack stack = Minecraft.getMinecraft().thePlayer.inventory.getStackInSlot(index);
                if (stack == null)
                    return index;
            }
        }
        return -1;
    }
}
