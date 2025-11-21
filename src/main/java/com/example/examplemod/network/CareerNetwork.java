package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.career.CareerConfigManager;
import com.example.examplemod.career.CareerDefinition;
import com.example.examplemod.career.CareerPlayerData;
import com.example.examplemod.career.CareerService;
import com.example.examplemod.client.CareerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 网络通道，负责打开/选择职业界面的通信。
 */
public final class CareerNetwork {
    private static final String PROTOCOL = "1";
    private static int packetId = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryParse(ExampleMod.MODID + ":main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private CareerNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(id(), RequestCareerScreenPacket.class, RequestCareerScreenPacket::encode, RequestCareerScreenPacket::decode, RequestCareerScreenPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id(), ChooseCareerPacket.class, ChooseCareerPacket::encode, ChooseCareerPacket::decode, ChooseCareerPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id(), CareerDataPacket.class, CareerDataPacket::encode, CareerDataPacket::decode, CareerDataPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static int id() {
        return packetId++;
    }

    public static void sendCareerData(ServerPlayer player) {
        List<CareerDataPacket.CareerSnapshot> careers = new ArrayList<>();
        for (CareerDefinition def : CareerConfigManager.getCareers()) {
            careers.add(CareerDataPacket.CareerSnapshot.from(def));
        }
        String selected = CareerPlayerData.getCareerId(player).orElse(null);
        CHANNEL.sendTo(new CareerDataPacket(careers, selected), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public record RequestCareerScreenPacket() {
        public static void encode(RequestCareerScreenPacket pkt, FriendlyByteBuf buf) {}
        public static RequestCareerScreenPacket decode(FriendlyByteBuf buf) { return new RequestCareerScreenPacket(); }
        public static void handle(RequestCareerScreenPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    if (CareerConfigManager.isLockAfterChoice() && CareerPlayerData.getCareerId(player).isPresent()) {
                        player.sendSystemMessage(Component.literal("已选择职业，面板已锁定"));
                        return;
                    }
                    sendCareerData(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record ChooseCareerPacket(String careerId) {
        public static void encode(ChooseCareerPacket pkt, FriendlyByteBuf buf) {
            buf.writeUtf(pkt.careerId());
        }
        public static ChooseCareerPacket decode(FriendlyByteBuf buf) {
            return new ChooseCareerPacket(buf.readUtf(64));
        }
        public static void handle(ChooseCareerPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null) {
                    return;
                }
                boolean success = CareerService.chooseCareer(player, pkt.careerId());
                if (success) {
                    sendCareerData(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record CareerDataPacket(List<CareerSnapshot> careers, String selectedId) {
        public static void encode(CareerDataPacket pkt, FriendlyByteBuf buf) {
            buf.writeCollection(pkt.careers, (b, snap) -> snap.encode(b));
            buf.writeBoolean(pkt.selectedId != null);
            if (pkt.selectedId != null) {
                buf.writeUtf(pkt.selectedId);
            }
        }

        public static CareerDataPacket decode(FriendlyByteBuf buf) {
            List<CareerSnapshot> snapshots = buf.readList(CareerSnapshot::decode);
            String selected = buf.readBoolean() ? buf.readUtf(64) : null;
            return new CareerDataPacket(snapshots, selected);
        }

        public static void handle(CareerDataPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    CareerScreen.open(pkt);
                }
            });
            ctx.get().setPacketHandled(true);
        }

        public record CareerSnapshot(String id,
                                     String name,
                                     String description,
                                     ResourceLocation icon,
                                     List<AttributePair> attributes,
                                     List<ItemStackEntry> items) {
            static CareerSnapshot from(CareerDefinition def) {
                List<AttributePair> attrs = def.attributes().stream()
                        .map(a -> new AttributePair(a.attributeId(), a.value()))
                        .toList();
                List<ItemStackEntry> items = def.startingItems().stream()
                        .map(i -> new ItemStackEntry(i.itemId(), i.count()))
                        .toList();
                return new CareerSnapshot(def.id(), def.name(), def.description(), def.iconItem(), attrs, items);
            }

            void encode(FriendlyByteBuf buf) {
                buf.writeUtf(id);
                buf.writeUtf(name);
                buf.writeUtf(description);
                buf.writeResourceLocation(icon);
                buf.writeCollection(attributes, (b, a) -> {
                    b.writeResourceLocation(a.id);
                    b.writeDouble(a.value);
                });
                buf.writeCollection(items, (b, i) -> {
                    b.writeResourceLocation(i.itemId);
                    b.writeVarInt(i.count);
                });
            }

            static CareerSnapshot decode(FriendlyByteBuf buf) {
                String id = buf.readUtf(64);
                String name = buf.readUtf(64);
                String desc = buf.readUtf(256);
                ResourceLocation icon = buf.readResourceLocation();
                List<AttributePair> attrs = buf.readList(b -> new AttributePair(b.readResourceLocation(), b.readDouble()));
                List<ItemStackEntry> items = buf.readList(b -> new ItemStackEntry(b.readResourceLocation(), b.readVarInt()));
                return new CareerSnapshot(id, name, desc, icon, attrs, items);
            }
        }

        public record AttributePair(ResourceLocation id, double value) {}

        public record ItemStackEntry(ResourceLocation itemId, int count) {}
    }
}
