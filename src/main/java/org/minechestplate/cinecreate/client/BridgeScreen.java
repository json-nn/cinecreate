package org.minechestplate.cinecreate.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.minechestplate.cinecreate.bridge.BridgeMenu;

/**
 * A plain window for a bridge menu. Normally never seen: the scene stands in for it and Cinematic Interactions cancels
 * it. It exists because a menu without a registered screen is never handed to the client player at all, and it is
 * what shows if the scene ended before the server's menu arrived.
 */
public class BridgeScreen<M extends BridgeMenu> extends AbstractContainerScreen<M> {
    public BridgeScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2B2D30);
        graphics.renderOutline(x, y, imageWidth, imageHeight, 0xFF6E6252);
        for (Slot slot : menu.slots) {
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF1A1B1D);
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF3C3F43);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE0D6C4, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFE0D6C4, false);
    }
}
