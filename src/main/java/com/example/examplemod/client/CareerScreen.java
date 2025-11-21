package com.example.examplemod.client;

import com.example.examplemod.career.CareerConfigManager;
import com.example.examplemod.network.CareerNetwork;
import com.example.examplemod.network.CareerNetwork.CareerDataPacket;
import com.example.examplemod.client.ClientGuiConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 职业选择/查看界面，展示玩家模型、职业信息与初始物品。
 */
public class CareerScreen extends Screen {
    private final List<ClientCareer> careers;
    private final String lockedId;
    private int currentIndex = 0;
    private Button chooseButton;
    private Button prevButton;
    private Button nextButton;

    public CareerScreen(List<ClientCareer> careers, String lockedId) {
        super(Component.literal("职业面板"));
        this.careers = careers;
        this.lockedId = lockedId;
        if (!careers.isEmpty() && lockedId != null) {
            for (int i = 0; i < careers.size(); i++) {
                if (Objects.equals(careers.get(i).id(), lockedId)) {
                    currentIndex = i;
                    break;
                }
            }
        }
    }

    public static void open(CareerDataPacket packet) {
        List<ClientCareer> careers = packet.careers().stream().map(ClientCareer::fromSnapshot).toList();
        Minecraft.getInstance().setScreen(new CareerScreen(careers, packet.selectedId()));
    }

    @Override
    protected void init() {
        super.init();
        ClientGuiConfig.GuiConfigData cfg = ClientGuiConfig.get();
        this.panelWidth = cfg.panelWidth();
        this.panelHeight = cfg.panelHeight();
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;

        int midY = top + panelHeight / 2 - 10;
        prevButton = Button.builder(Component.literal("<"), b -> selectPrevious())
                .bounds(left - 38, midY, 30, 20).build();
        nextButton = Button.builder(Component.literal(">"), b -> selectNext())
                .bounds(left + panelWidth + 8, midY, 30, 20).build();
        addRenderableWidget(prevButton);
        addRenderableWidget(nextButton);

        boolean hasCareer = lockedId != null;
        chooseButton = Button.builder(Component.literal(hasCareer ? "切换职业" : "选择职业"), b -> submitSelection())
                .bounds(left + panelWidth - 130, top + panelHeight - 28, 120, 20)
                .build();
        chooseButton.active = !careers.isEmpty() && !hasCareer && !CareerConfigManager.isLockAfterChoice();
        addRenderableWidget(chooseButton);
        updateButtonStates();
    }

    private void selectPrevious() {
        if (careers.isEmpty()) return;
        currentIndex = (currentIndex - 1 + careers.size()) % careers.size();
        updateButtonStates();
    }

    private void selectNext() {
        if (careers.isEmpty()) return;
        currentIndex = (currentIndex + 1) % careers.size();
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (prevButton != null) prevButton.active = careers.size() > 1;
        if (nextButton != null) nextButton.active = careers.size() > 1;
        if (chooseButton != null) {
            boolean hasCareer = lockedId != null;
            ClientCareer current = getCurrentCareer();
            chooseButton.active = current != null && (!hasCareer || !current.id().equals(lockedId));
        }
    }

    private void submitSelection() {
        ClientCareer current = getCurrentCareer();
        if (current == null) {
            return;
        }
        CareerNetwork.CHANNEL.sendToServer(new CareerNetwork.ChooseCareerPacket(current.id()));
        chooseButton.active = false;
        Minecraft.getInstance().player.displayClientMessage(Component.literal("已提交职业选择/切换"), true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        ClientGuiConfig.GuiConfigData cfg = ClientGuiConfig.get();
        int left = (this.width - cfg.panelWidth()) / 2;
        int top = (this.height - cfg.panelHeight()) / 2;
        drawPanelBackground(graphics, left, top, cfg.panelWidth(), cfg.panelHeight());
        graphics.drawString(this.font, "职业面板", left + 10, top + 10, 0xFFFFFF);

        renderPlayerModel(graphics, mouseX, mouseY, left + 40, top + cfg.panelHeight() - 20);

        ClientCareer selected = getCurrentCareer();
        if (selected != null) {
            int infoX = left + 100;
            int infoY = top + 30;
            graphics.drawString(this.font, "当前职业: " + selected.name() + " (" + (currentIndex + 1) + "/" + careers.size() + ")", infoX, infoY, ChatFormatting.GOLD.getColor());
            infoY += 7;
            MultiLineLabel desc = MultiLineLabel.create(font, Component.literal(selected.description()), 180);
            desc.renderCentered(graphics, infoX + 60, infoY + 10, 12, 0xDDDDDD);
            infoY += desc.getLineCount() * 12 + 10;

            graphics.drawString(this.font, "属性加成:", infoX, infoY, 0xFFFFFF);
            infoY += 12;
            for (ClientCareer.AttributeLine line : selected.attributes()) {
                String text = line.display();
                graphics.drawString(this.font, text, infoX + 4, infoY, 0xB7F07B);
                infoY += 10;
            }

            infoY += 6;
            graphics.drawString(this.font, "初始物品:", infoX, infoY, 0xFFFFFF);
            infoY += 10;
            int itemX = infoX + 4;
            for (ItemStack stack : selected.items()) {
                graphics.renderItem(stack, itemX, infoY);
                graphics.renderItemDecorations(this.font, stack, itemX, infoY);
                itemX += 20;
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPlayerModel(GuiGraphics graphics, int mouseX, int mouseY, int modelX, int modelY) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int scale = 50;
        float rotX = modelX - mouseX;
        float rotY = modelY - mouseY;
        net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, modelX, modelY, scale, rotX, rotY, player);
    }

    private ClientCareer getCurrentCareer() {
        if (careers.isEmpty() || currentIndex < 0 || currentIndex >= careers.size()) {
            return null;
        }
        return careers.get(currentIndex);
    }

    public record ClientCareer(String id,
                               String name,
                               String description,
                               ItemStack icon,
                               List<AttributeLine> attributes,
                               List<ItemStack> items) {
        static ClientCareer fromSnapshot(CareerDataPacket.CareerSnapshot snap) {
            Item iconItem = ForgeRegistries.ITEMS.getValue(snap.icon());
            ItemStack iconStack = new ItemStack(iconItem != null ? iconItem : net.minecraft.world.item.Items.BOOK);
            List<ItemStack> stacks = new ArrayList<>();
            for (CareerDataPacket.ItemStackEntry entry : snap.items()) {
                Item item = ForgeRegistries.ITEMS.getValue(entry.itemId());
                if (item != null) {
                    stacks.add(new ItemStack(item, entry.count()));
                }
            }
            List<AttributeLine> attrs = snap.attributes().stream()
                    .map(a -> new AttributeLine(a.id(), a.value()))
                    .toList();
            return new ClientCareer(snap.id(), snap.name(), snap.description(), iconStack, attrs, stacks);
        }

        public record AttributeLine(ResourceLocation id, double value) {
            String display() {
                String key = buildKey(id);
                String translated = I18n.exists(key) ? I18n.get(key) : id.toString();
                return translated + " +" + value;
            }

            private static String buildKey(ResourceLocation id) {
                // Vanilla 使用 attribute.name.<path>（不含命名空间）；Forge 自定义通常包含命名空间
                String path = id.getPath().replace('/', '.');
                if ("minecraft".equals(id.getNamespace())) {
                    return "attribute.name." + path;
                }
                return "attribute.name." + id.getNamespace() + "." + path;
            }
        }
    }

    private int panelWidth = 260;
    private int panelHeight = 200;

    private void drawPanelBackground(GuiGraphics graphics, int left, int top, int width, int height) {
        ResourceLocation tex = ClientGuiConfig.getBackgroundTexture();
        if (tex != null) {
            graphics.blit(tex, left, top, 0, 0, width, height, width, height);
        } else {
            int color = 0xCC101010;
            int border = 0xFF444444;
            graphics.fill(left, top, left + width, top + height, color);
            graphics.drawString(this.font, "", 0, 0, 0); // 保留字体渲染状态
            graphics.fill(left, top, left + width, top + 2, border);
            graphics.fill(left, top + height - 2, left + width, top + height, border);
            graphics.fill(left, top, left + 2, top + height, border);
            graphics.fill(left + width - 2, top, left + width, top + height, border);
        }
    }
}
