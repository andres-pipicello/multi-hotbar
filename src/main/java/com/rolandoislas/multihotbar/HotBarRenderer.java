package com.rolandoislas.multihotbar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Created by Rolando on 6/6/2016.
 */
public class HotBarRenderer extends Gui {
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int SELECTOR_SIZE = 24;
    public static int tooltipTicks = 128;
    private final ResourceLocation WIDGETS;
    private final Minecraft minecraft;

    public HotBarRenderer() {
        super();
        this.minecraft = Minecraft.getMinecraft();
        this.WIDGETS = new ResourceLocation("minecraft", "textures/gui/widgets.png");
    }

    public void render() {
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glDisable(GL11.GL_LIGHTING);
        if (Config.numberOfHotbars == 1)
            drawSingle(0);
        else if (Config.numberOfHotbars == 2)
            drawDouble(0);
        else if (Config.numberOfHotbars == 3) {
            drawDouble(0);
            drawSingle(2);
        } else if (Config.numberOfHotbars == 4) {
            drawDouble(0);
            drawDouble(2);
        }
        drawSelection();
        drawItems();
        drawTooltip();
    }

    private void drawTooltip() {
        if (tooltipTicks > 0)
            tooltipTicks--;
        int[] coords = getHotbarCoords(Config.numberOfHotbars >= 3 ? 2 : 0);
        ItemStack item = minecraft.thePlayer.inventory.getCurrentItem();
        if (item == null || tooltipTicks == 0)
            return;
        ScaledResolution scaledResolution = new ScaledResolution(minecraft);
        int x = scaledResolution.getScaledWidth() / 2 -
                minecraft.fontRendererObj.getStringWidth(item.getDisplayName()) / 2;
        int y = coords[1] - 37 + (minecraft.playerController.shouldDrawHUD() ? 0 : 14);
        int color = (int) (tooltipTicks * 256f / 10f);
        color = color > 255 ? 255 : color;
        if (color > 0) {
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            minecraft.fontRendererObj.drawStringWithShadow(item.getDisplayName(), x, y, 16777215 + (color << 24));
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        }
    }

    private void drawItems() {
        for (int i = 0; i < Config.numberOfHotbars; i++)
            drawItems(i, HotbarLogic.hotbarOrder[i]);
    }

    private void drawSelection() {
        if (InventoryHelper.waitTicks > 0)
            return;
        // Draw selection indicator
        int slot = minecraft.thePlayer.inventory.currentItem;
        int index = HotbarLogic.hotbarIndex;
        int[] coords = getHotbarCoords(index);
        int x = coords[0];
        int y = coords[1];
        minecraft.getTextureManager().bindTexture(WIDGETS);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        zLevel += 100;
        minecraft.ingameGUI.drawTexturedModalRect(-1 + x + 20 * slot, y - 1, 0, HOTBAR_HEIGHT, SELECTOR_SIZE,
                SELECTOR_SIZE);
    }

    private void drawSingle(int index) {
        int[] coords = getHotbarCoords(index);
        drawHotbar(coords[0], coords[1]);
    }

    private void drawDouble(int index) {
        for (int i = index; i < index + 2; i++) {
            int[] coords = getHotbarCoords(i);
            drawHotbar(coords[0], coords[1]);
        }
    }

    private int[] getHotbarCoords(int index) {
        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution scaledResolution = new ScaledResolution(minecraft);
        int[] coords = new int[2];
        if (Config.numberOfHotbars == 1) {
            coords[0] = scaledResolution.getScaledWidth() / 2 - HOTBAR_WIDTH / 2;
            coords[1] = scaledResolution.getScaledHeight() - HOTBAR_HEIGHT * (index + 1);
        }
        else if (Config.numberOfHotbars == 2 || Config.numberOfHotbars == 4) {
            coords[0] = scaledResolution.getScaledWidth() / 2 - HOTBAR_WIDTH * (index == 0 || index == 2 ? 1 : 0);
            coords[1] = scaledResolution.getScaledHeight() - HOTBAR_HEIGHT * (index == 0 || index == 1 ? 1 : 2);
        }
        else if (Config.numberOfHotbars == 3) {
            coords[0] = (int) (scaledResolution.getScaledWidth() / 2 - HOTBAR_WIDTH *
                    (index == 2 ? .5 : (index == 1 ? 0 : 1)));
            coords[1] = scaledResolution.getScaledHeight() - HOTBAR_HEIGHT * (index == 2 ? 2 : 1);
        }
        return coords;
    }

    private int getXForSlot(int index, int slot) {
        int[] coords = getHotbarCoords(index);
        int x = coords[0] + 3 + 16 * slot + 4 * slot;
        return x;
    }

    private int getYForSlot(int index) {
        int[] coords = getHotbarCoords(index);
        int y = coords[1] + 3;
        return y;
    }

    private void drawHotbar(int x, int y) {
        // Draw hotbar
        minecraft.getTextureManager().bindTexture(WIDGETS);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        minecraft.ingameGUI.drawTexturedModalRect(x, y, 0, 0, HOTBAR_WIDTH, HOTBAR_HEIGHT);
    }

    private void drawItems(int hotbarIndex, int slotIndex) {
        // Draw items on hotbar
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = slotIndex * 9; i < slotIndex * 9 + 9; i++) {
            ItemStack item = minecraft.thePlayer.inventory.getStackInSlot(i);
            if (item != null) {
                int itemX = getXForSlot(hotbarIndex, i - slotIndex * 9);
                int itemY = getYForSlot(hotbarIndex);
                float pickupAnimation = item.animationsToGo - 1;
                if (pickupAnimation > 0.0F) {
                    GL11.glPushMatrix();
                    float scale = 1 + pickupAnimation / 5;
                    GL11.glTranslatef(itemX + 8, itemY + 12, 0);
                    GL11.glScalef(1 / scale, scale + 1 / 2, 1);
                    GL11.glTranslatef(-(itemX + 8), -(itemY + 12), 0);
                }
                minecraft.getRenderItem().renderItemAndEffectIntoGUI(item, itemX, itemY);
                if (pickupAnimation > 0.0F)
                    GL11.glPopMatrix();

                minecraft.getRenderItem().renderItemOverlayIntoGUI(minecraft.fontRendererObj, item, itemX, itemY,
                        item.stackSize > 1 ? String.valueOf(item.stackSize) : "");
            }
        }
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
    }
}
