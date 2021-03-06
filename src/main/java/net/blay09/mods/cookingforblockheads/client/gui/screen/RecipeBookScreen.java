package net.blay09.mods.cookingforblockheads.client.gui.screen;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import net.blay09.mods.cookingforblockheads.CookingForBlockheads;
import net.blay09.mods.cookingforblockheads.CookingForBlockheadsConfig;
import net.blay09.mods.cookingforblockheads.api.FoodRecipeWithStatus;
import net.blay09.mods.cookingforblockheads.api.ISortButton;
import net.blay09.mods.cookingforblockheads.api.RecipeStatus;
import net.blay09.mods.cookingforblockheads.client.gui.SortButton;
import net.blay09.mods.cookingforblockheads.container.RecipeBookContainer;
import net.blay09.mods.cookingforblockheads.container.slot.FakeSlotCraftMatrix;
import net.blay09.mods.cookingforblockheads.container.slot.FakeSlotRecipe;
import net.blay09.mods.cookingforblockheads.registry.CookingRegistry;
import net.blay09.mods.cookingforblockheads.registry.FoodRecipeWithIngredients;
import net.blay09.mods.cookingforblockheads.registry.RecipeType;
import net.blay09.mods.cookingforblockheads.util.TextUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

// TODO @MouseTweaksIgnore
public class RecipeBookScreen extends ContainerScreen<RecipeBookContainer> {

    private static final int SCROLLBAR_COLOR = 0xFFAAAAAA;
    private static final int SCROLLBAR_Y = 8;
    private static final int SCROLLBAR_WIDTH = 7;
    private static final int SCROLLBAR_HEIGHT = 77;

    private static final ResourceLocation guiTexture = new ResourceLocation(CookingForBlockheads.MOD_ID, "textures/gui/gui.png");
    private static final int VISIBLE_ROWS = 4;

    private final RecipeBookContainer container;
    private boolean isEventHandler;
    private int scrollBarScaledHeight;
    private int scrollBarXPos;
    private int scrollBarYPos;
    private int currentOffset;

    private double mouseClickY = -1;
    private int indexWhenClicked;
    private int lastNumberOfMoves;

    private Button btnNextRecipe;
    private Button btnPrevRecipe;

    private TextFieldWidget searchBar;

    private final List<SortButton> sortButtons = Lists.newArrayList();

    private final String[] noIngredients;
    private final String[] noSelection;

    public RecipeBookScreen(RecipeBookContainer container, PlayerInventory playerInventory, ITextComponent displayName) {
        super(container, playerInventory, displayName);
        this.container = container;

        noIngredients = I18n.format("gui.cookingforblockheads:no_ingredients").split("\\\\n");
        noSelection = I18n.format("gui.cookingforblockheads:no_selection").split("\\\\n");
    }

    @Override
    protected void init() {
        ySize = 174;
        super.init();

        getMinecraft().keyboardListener.enableRepeatEvents(true);

        btnPrevRecipe = new Button(width / 2 - 79, height / 2 - 51, 13, 20, "<", it -> container.nextSubRecipe(-1));
        btnPrevRecipe.visible = false;
        addButton(btnPrevRecipe);

        btnNextRecipe = new Button(width / 2 - 9, height / 2 - 51, 13, 20, ">", it -> container.nextSubRecipe(1));
        btnNextRecipe.visible = false;
        addButton(btnNextRecipe);

        searchBar = new TextFieldWidget(getMinecraft().fontRenderer, guiLeft + xSize - 78, guiTop - 5, 70, 10, searchBar, "searchbar");
//		searchBar.setFocused(true);

        int yOffset = -80;

        for (ISortButton button : CookingRegistry.getSortButtons()) {
            SortButton sortButton = new SortButton(width / 2 + 87, height / 2 + yOffset, button, it -> {
                container.setSortComparator(((SortButton) it).getComparator(Minecraft.getInstance().player));
            });
            addButton(sortButton);
            sortButtons.add(sortButton);

            yOffset += 20;
        }

        if (!isEventHandler) {
            MinecraftForge.EVENT_BUS.register(this);
            isEventHandler = true;
        }

        recalculateScrollBar();
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0) {
            return false;
        }

        if (container.getSelection() != null && mouseX >= guiLeft + 7 && mouseY >= guiTop + 17 && mouseX < guiLeft + 92 && mouseY < guiTop + 95) {
            Slot slot = getSlotUnderMouse();
            if (slot instanceof FakeSlotCraftMatrix && ((FakeSlotCraftMatrix) slot).getVisibleStacks().size() > 1) {
                ((FakeSlotCraftMatrix) slot).scrollDisplayList(delta > 0 ? -1 : 1);
            }
        } else {
            setCurrentOffset(delta > 0 ? currentOffset - 1 : currentOffset + 1);
        }

        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int state) {
        boolean result = super.mouseReleased(mouseX, mouseY, state);

        if (state != -1 && mouseClickY != -1) {
            mouseClickY = -1;
            indexWhenClicked = 0;
            lastNumberOfMoves = 0;
        }

        return result;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);

        if (button == 1 && mouseX >= searchBar.x && mouseX < searchBar.x + searchBar.getWidth() && mouseY >= searchBar.y && mouseY < searchBar.y + searchBar.getHeight()) {
            searchBar.setText("");
            container.search(null);
            container.populateRecipeSlots();
            setCurrentOffset(currentOffset);
            return true;
        } else {
            if (searchBar.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (mouseX >= scrollBarXPos && mouseX <= scrollBarXPos + SCROLLBAR_WIDTH && mouseY >= scrollBarYPos && mouseY <= scrollBarYPos + scrollBarScaledHeight) {
            mouseClickY = mouseY;
            indexWhenClicked = currentOffset;
        }

        Slot mouseSlot = getSlotUnderMouse();
        if (mouseSlot instanceof FakeSlotCraftMatrix) {
            if (button == 0) {
                ItemStack itemStack = mouseSlot.getStack();
                FoodRecipeWithStatus recipe = container.findAvailableRecipe(itemStack);
                if (recipe != null) {
                    container.setSelectedRecipe(recipe, false);
                    setCurrentOffset(container.getSelectedRecipeIndex());
                } else if (!CookingRegistry.getFoodRecipes(itemStack).isEmpty()) {
                    container.setSelectedRecipe(new FoodRecipeWithStatus(itemStack, RecipeStatus.MISSING_INGREDIENTS), true);
                }
            } else if (button == 1) {
                ((FakeSlotCraftMatrix) mouseSlot).setLocked(!((FakeSlotCraftMatrix) mouseSlot).isLocked());
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBar.keyPressed(keyCode, scanCode, modifiers)) {
            container.search(searchBar.getText());
            container.populateRecipeSlots();
            setCurrentOffset(currentOffset);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        if (container.isDirty()) {
            setCurrentOffset(currentOffset);
            container.setDirty(false);
        }

        GlStateManager.color4f(1f, 1f, 1f, 1f);
        getMinecraft().getTextureManager().bindTexture(guiTexture);
        blit(guiLeft, guiTop - 10, 0, 0, xSize, ySize + 10);

        if (mouseClickY != -1) {
            float pixelsPerFilter = (SCROLLBAR_HEIGHT - scrollBarScaledHeight) / (float) Math.max(1, (int) Math.ceil(container.getItemListCount() / 3f) - VISIBLE_ROWS);
            if (pixelsPerFilter != 0) {
                int numberOfFiltersMoved = (int) ((mouseY - mouseClickY) / pixelsPerFilter);
                if (numberOfFiltersMoved != lastNumberOfMoves) {
                    setCurrentOffset(indexWhenClicked + numberOfFiltersMoved);
                    lastNumberOfMoves = numberOfFiltersMoved;
                }
            }
        }

        btnPrevRecipe.visible = container.hasVariants();
        btnPrevRecipe.active = container.getSelectionIndex() > 0;
        btnNextRecipe.visible = container.hasVariants();
        btnNextRecipe.active = container.getSelectionIndex() < container.getRecipeCount() - 1;

        boolean hasRecipes = container.getItemListCount() > 0;

        for (Button sortButton : sortButtons) {
            sortButton.active = hasRecipes;
        }

        GlStateManager.color4f(1f, 1f, 1f, 1f);

        if (CookingForBlockheadsConfig.CLIENT.showIngredientIcon.get()) {
            int prevZLevel = blitOffset;
            blitOffset = 300;
            for (Slot slot : container.inventorySlots) {
                if (slot instanceof FakeSlotRecipe) {
                    if (CookingRegistry.isNonFoodRecipe(slot.getStack())) {
                        blit(guiLeft + slot.xPos, guiTop + slot.yPos, 176, 76, 16, 16);
                    }

                    FoodRecipeWithStatus recipe = ((FakeSlotRecipe) slot).getRecipe();
                    if (recipe != null && recipe.getStatus() == RecipeStatus.MISSING_TOOLS) {
                        blit(guiLeft + slot.xPos, guiTop + slot.yPos, 176, 92, 16, 16);
                    }
                }
            }

            blitOffset = prevZLevel;
        }

        FontRenderer fontRenderer = getMinecraft().fontRenderer;
        FoodRecipeWithIngredients selection = container.getSelection();
        if (selection == null) {
            int curY = guiTop + 79 / 2 - noSelection.length / 2 * fontRenderer.FONT_HEIGHT;
            for (String s : noSelection) {
                fontRenderer.drawStringWithShadow(s, guiLeft + 23 + 27 - fontRenderer.getStringWidth(s) / 2f, curY, 0xFFFFFFFF);
                curY += fontRenderer.FONT_HEIGHT + 5;
            }
        } else if (selection.getRecipeType() == RecipeType.SMELTING) {
            blit(guiLeft + 23, guiTop + 19, 54, 184, 54, 54);
        } else {
            blit(guiLeft + 23, guiTop + 19, 0, 184, 54, 54);
        }

        if (selection != null) {
            for (FakeSlotCraftMatrix slot : container.getCraftingMatrixSlots()) {
                if (slot.isLocked() && slot.getVisibleStacks().size() > 1) {
                    blit(guiLeft + slot.xPos, guiTop + slot.yPos, 176, 60, 16, 16);
                }
            }
        }

        fill(scrollBarXPos, scrollBarYPos, scrollBarXPos + SCROLLBAR_WIDTH, scrollBarYPos + scrollBarScaledHeight, SCROLLBAR_COLOR);

        if (container.getItemListCount() == 0) {
            fill(guiLeft + 97, guiTop + 7, guiLeft + 168, guiTop + 85, 0xAA222222);
            int curY = guiTop + 79 / 2 - noIngredients.length / 2 * fontRenderer.FONT_HEIGHT;
            for (String s : noIngredients) {
                fontRenderer.drawStringWithShadow(s, guiLeft + 97 + 36 - fontRenderer.getStringWidth(s) / 2f, curY, 0xFFFFFFFF);
                curY += fontRenderer.FONT_HEIGHT + 5;
            }
        }


        searchBar.renderButton(mouseX, mouseY, partialTicks);
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        this.renderBackground();

        super.render(mouseX, mouseY, partialTicks);

        int prevZLevel = blitOffset;
        blitOffset = 300;
        for (Slot slot : container.inventorySlots) {
            if (slot instanceof FakeSlotCraftMatrix) {
                if (!((FakeSlotCraftMatrix) slot).isAvailable() && !slot.getStack().isEmpty()) {
                    fillGradient(guiLeft + slot.xPos, guiTop + slot.yPos, guiLeft + slot.xPos + 16, guiTop + slot.yPos + 16, 0x77FF4444, 0x77FF5555);
                }
            }
        }
        blitOffset = prevZLevel;

        container.updateSlots(partialTicks);

        for (Button sortButton : this.sortButtons) {
            if (sortButton instanceof SortButton && sortButton.isMouseOver(mouseX, mouseY) && sortButton.active) {
                renderTooltip(((SortButton) sortButton).getTooltipLines(), mouseX, mouseY);
            }
        }

        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    public void removed() {
        super.removed();

        getMinecraft().keyboardListener.enableRepeatEvents(false);

        if (isEventHandler) {
            MinecraftForge.EVENT_BUS.unregister(this);
            isEventHandler = false;
        }
    }

    public void recalculateScrollBar() {
        int scrollBarTotalHeight = SCROLLBAR_HEIGHT - 1;
        this.scrollBarScaledHeight = (int) (scrollBarTotalHeight * Math.min(1f, ((float) VISIBLE_ROWS / (Math.ceil(container.getItemListCount() / 3f)))));
        this.scrollBarXPos = guiLeft + xSize - SCROLLBAR_WIDTH - 9;
        this.scrollBarYPos = guiTop + SCROLLBAR_Y + ((scrollBarTotalHeight - scrollBarScaledHeight) * currentOffset / Math.max(1, (int) Math.ceil((container.getItemListCount() / 3f)) - VISIBLE_ROWS));
    }

    public void setCurrentOffset(int currentOffset) {
        this.currentOffset = Math.max(0, Math.min(currentOffset, (int) Math.ceil(container.getItemListCount() / 3f) - VISIBLE_ROWS));

        container.setScrollOffset(this.currentOffset);

        recalculateScrollBar();
    }

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        Slot hoverSlot = getSlotUnderMouse();
        if (hoverSlot instanceof FakeSlotRecipe && event.getItemStack() == hoverSlot.getStack()) {
            FakeSlotRecipe slotRecipe = (FakeSlotRecipe) hoverSlot;
            if (container.isSelectedSlot(slotRecipe) && container.isAllowCrafting()) {
                FoodRecipeWithIngredients subRecipe = container.getSelection();
                if (subRecipe == null) {
                    return;
                }

                if (subRecipe.getRecipeType() == RecipeType.SMELTING) {
                    if (!container.hasOven()) {
                        event.getToolTip().add(TextUtils.coloredTextComponent("tooltip.cookingforblockheads:missing_oven", TextFormatting.RED));
                    } else {
                        if (hasShiftDown()) {
                            event.getToolTip().add(TextUtils.coloredTextComponent("tooltip.cookingforblockheads:click_to_smelt_stack", TextFormatting.GREEN));
                        } else {
                            event.getToolTip().add(TextUtils.coloredTextComponent("tooltip.cookingforblockheads:click_to_smelt_one", TextFormatting.GREEN));
                        }
                    }
                } else {
                    if (subRecipe.getRecipeStatus() == RecipeStatus.MISSING_TOOLS) {
                        event.getToolTip().add(TextUtils.coloredTextComponent("tooltip.cookingforblockheads:missing_tools", TextFormatting.RED));
                    } else if (subRecipe.getRecipeStatus() == RecipeStatus.MISSING_INGREDIENTS) {
                        event.getToolTip().add(TextUtils.coloredTextComponent("tooltip.cookingforblockheads:missing_ingredients", TextFormatting.RED));
                    } else {
                        if (hasShiftDown()) {
                            event.getToolTip().add(TextUtils.coloredTextComponent("tooltip.cookingforblockheads:click_to_craft_stack", TextFormatting.GREEN));
                        } else {
                            event.getToolTip().add(TextUtils.coloredTextComponent("tooltip.cookingforblockheads:click_to_craft_one", TextFormatting.GREEN));
                        }
                    }
                }
            } else {
                event.getToolTip().add(TextUtils.coloredTextComponent("tooltip.cookingforblockheads:click_to_see_recipe", TextFormatting.YELLOW));
            }
        } else if (hoverSlot instanceof FakeSlotCraftMatrix && event.getItemStack() == hoverSlot.getStack()) {
            if (((FakeSlotCraftMatrix) hoverSlot).getVisibleStacks().size() > 1) {
                if (((FakeSlotCraftMatrix) hoverSlot).isLocked()) {
                    event.getToolTip().add(TextUtils.coloredTextComponent("tooltip.cookingforblockheads:click_to_unlock", TextFormatting.GREEN));
                } else {
                    event.getToolTip().add(TextUtils.coloredTextComponent("tooltip.cookingforblockheads:click_to_lock", TextFormatting.GREEN));
                }
                event.getToolTip().add(TextUtils.coloredTextComponent("tooltip.cookingforblockheads:scroll_to_switch", TextFormatting.YELLOW));
            }
        }
    }

    public Button[] getSortingButtons() {
        return sortButtons.toArray(new SortButton[0]);
    }

}
