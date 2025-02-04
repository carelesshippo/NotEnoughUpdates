package io.github.moulberry.notenoughupdates.mixins;

import io.github.moulberry.notenoughupdates.NEUEventListener;
import io.github.moulberry.notenoughupdates.NEUOverlay;
import io.github.moulberry.notenoughupdates.miscfeatures.BetterContainers;
import io.github.moulberry.notenoughupdates.miscfeatures.EnchantingSolvers;
import io.github.moulberry.notenoughupdates.NotEnoughUpdates;
import io.github.moulberry.notenoughupdates.miscfeatures.PetInfoOverlay;
import io.github.moulberry.notenoughupdates.miscfeatures.SlotLocking;
import io.github.moulberry.notenoughupdates.miscgui.StorageOverlay;
import io.github.moulberry.notenoughupdates.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(GuiContainer.class)
public abstract class MixinGuiContainer extends GuiScreen {

    @Inject(method="drawSlot", at=@At("RETURN"))
    public void drawSlotRet(Slot slotIn, CallbackInfo ci) {
        SlotLocking.getInstance().drawSlot(slotIn);
    }

    @Inject(method="drawSlot", at=@At("HEAD"), cancellable = true)
    public void drawSlot(Slot slot, CallbackInfo ci) {
        if(slot == null) return;

        if(slot.getStack() == null && NotEnoughUpdates.INSTANCE.overlay.searchMode && NEUEventListener.drawingGuiScreen) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 100 + Minecraft.getMinecraft().getRenderItem().zLevel);
            GlStateManager.depthMask(false);
            Gui.drawRect(slot.xDisplayPosition, slot.yDisplayPosition,
                    slot.xDisplayPosition+16, slot.yDisplayPosition+16, NEUOverlay.overlayColourDark);
            GlStateManager.depthMask(true);
            GlStateManager.popMatrix();
        }

        GuiContainer $this = (GuiContainer)(Object)this;
        ItemStack stack = slot.getStack();

        if(stack != null) {
            if(EnchantingSolvers.onStackRender(stack, slot.inventory, slot.getSlotIndex(), slot.xDisplayPosition, slot.yDisplayPosition)) {
                ci.cancel();
                return;
            }
        }

        RenderHelper.enableGUIStandardItemLighting();

        if(BetterContainers.isOverriding() && !BetterContainers.shouldRenderStack(stack)) {
            ci.cancel();
        }
    }

    @Inject(method="isMouseOverSlot", at=@At("HEAD"), cancellable = true)
    public void isMouseOverSlot(Slot slotIn, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        StorageOverlay.getInstance().overrideIsMouseOverSlot(slotIn, mouseX, mouseY, cir);
    }

    @Redirect(method="drawScreen", at=@At(value="INVOKE", target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGradientRect(IIIIII)V"))
    public void drawScreen_drawGradientRect(GuiContainer container, int left, int top, int right, int bottom, int startColor, int endColor) {
        if(startColor == 0x80ffffff && endColor == 0x80ffffff &&
                theSlot != null && SlotLocking.getInstance().isSlotLocked(theSlot)) {
            int col = 0x80ff8080;
            drawGradientRect(left, top, right, bottom, col, col);
        } else {
            drawGradientRect(left, top, right, bottom, startColor, endColor);
        }
    }

    @Shadow
    private Slot theSlot;

    @Inject(method="drawScreen", at=@At("RETURN"))
    public void drawScreen(CallbackInfo ci) {
        if(theSlot != null && SlotLocking.getInstance().isSlotLocked(theSlot)) {
            theSlot = null;
        }
    }

    private static final String TARGET_GETSTACK = "Lnet/minecraft/inventory/Slot;getStack()Lnet/minecraft/item/ItemStack;";
    @Redirect(method="drawScreen", at=@At(value="INVOKE", target=TARGET_GETSTACK))
    public ItemStack drawScreen_getStack(Slot slot) {
        if(theSlot != null && theSlot == slot && theSlot.getStack() != null) {
            ItemStack newStack = EnchantingSolvers.overrideStack(theSlot.inventory, theSlot.getSlotIndex(), theSlot.getStack());
            if(newStack != null) {
                return newStack;
            }
        }
        return slot.getStack();
    }

    @Redirect(method="drawSlot", at=@At(value="INVOKE", target=TARGET_GETSTACK))
    public ItemStack drawSlot_getStack(Slot slot) {
        GuiContainer $this = (GuiContainer)(Object)this;

        ItemStack stack = slot.getStack();

        if(stack != null) {
            ItemStack newStack = EnchantingSolvers.overrideStack(slot.inventory, slot.getSlotIndex(), stack);
            if(newStack != null) {
                stack = newStack;
            }
        }

        if($this instanceof GuiChest) {
            Container container = ((GuiChest)$this).inventorySlots;
            if(container instanceof ContainerChest) {
                IInventory lower = ((ContainerChest)container).getLowerChestInventory();
                int size = lower.getSizeInventory();
                if(slot.slotNumber >= size) {
                    return stack;
                }
                if(System.currentTimeMillis() - BetterContainers.lastRenderMillis < 300 && stack == null) {
                    for(int index=0; index<size; index++) {
                        if(lower.getStackInSlot(index) != null) {
                            BetterContainers.itemCache.put(slot.slotNumber, null);
                            return null;
                        }
                    }
                    return BetterContainers.itemCache.get(slot.slotNumber);
                } else {
                    BetterContainers.itemCache.put(slot.slotNumber, stack);
                }
            }
        }
        return stack;
    }

    private static final String TARGET_CANBEHOVERED = "Lnet/minecraft/inventory/Slot;canBeHovered()Z";
    @Redirect(method="drawScreen", at=@At(value="INVOKE", target=TARGET_CANBEHOVERED))
    public boolean drawScreen_canBeHovered(Slot slot) {
        if(NotEnoughUpdates.INSTANCE.config.improvedSBMenu.hideEmptyPanes &&
                BetterContainers.isOverriding() && BetterContainers.isBlankStack(slot.getStack())) {
            return false;
        }
        return slot.canBeHovered();
    }

    @Inject(method="handleMouseClick", at=@At(value="HEAD"), cancellable = true)
    public void handleMouseClick(Slot slotIn, int slotId, int clickedButton, int clickType, CallbackInfo ci) {
        GuiContainer $this = (GuiContainer)(Object)this;

        AtomicBoolean ret = new AtomicBoolean(false);
        SlotLocking.getInstance().onWindowClick(slotIn, slotId, clickedButton, clickType, (tuple) -> {
            ci.cancel();

            if(tuple == null) {
                ret.set(true);
            } else {
                int newSlotId = tuple.getLeft();
                int newClickedButton = tuple.getMiddle();
                int newClickedType = tuple.getRight();

                ret.set(true);
                $this.mc.playerController.windowClick($this.inventorySlots.windowId, newSlotId, newClickedButton, newClickedType, $this.mc.thePlayer);
            }
        });
        if(ret.get()) return;

        if(slotIn != null && slotIn.getStack() != null) {
            if(EnchantingSolvers.onStackClick(slotIn.getStack(), $this.inventorySlots.windowId,
                    slotId, clickedButton, clickType)) {
                ci.cancel();
            } else {
                PetInfoOverlay.onStackClick(slotIn.getStack(), $this.inventorySlots.windowId,
                        slotId, clickedButton, clickType);
            }
        }
        if(slotIn != null && BetterContainers.isOverriding() && (BetterContainers.isBlankStack(slotIn.getStack()) ||
                BetterContainers.isButtonStack(slotIn.getStack()))) {
            BetterContainers.clickSlot(slotIn.getSlotIndex());

            if(BetterContainers.isBlankStack(slotIn.getStack())) {
                $this.mc.playerController.windowClick($this.inventorySlots.windowId, slotId, 2, clickType, $this.mc.thePlayer);
                ci.cancel();
            } else {
                Utils.playPressSound();
            }
        }
    }

}
