package cam72cam.mod.gui;

import cam72cam.mod.ModCore;
import cam72cam.mod.block.BlockEntity;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.gui.container.ClientContainerBuilder;
import cam72cam.mod.gui.container.IContainer;
import cam72cam.mod.gui.container.ServerContainerBuilder;
import cam72cam.mod.math.Vec3i;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;


public class GuiRegistry {
    private static Map<Integer, Function<CreateEvent, Object>> registry = new HashMap<>();

    public GuiRegistry(ModCore.Mod mod) {
        //TODO support for multiple mods using different ID ranges
    }

    public static void registration() {

        NetworkRegistry.INSTANCE.registerGuiHandler(ModCore.instance, new IGuiHandler() {
            @Nullable
            @Override
            public Object getServerGuiElement(int ID, PlayerEntity player, World world, int x, int y, int z) {
                return registry.get(ID).apply(new CreateEvent(true, new Player(player), x, y, z));
            }

            @Nullable
            @Override
            public Object getClientGuiElement(int ID, PlayerEntity player, World world, int x, int y, int z) {
                return registry.get(ID).apply(new CreateEvent(false, new Player(player), x, y, z));
            }
        });
    }

    public GUIType register(String name, Supplier<IScreen> ctr) {
        int id = name.hashCode();
        registry.put(id, event -> {
            if (event.isServer) {
                return null;
            }
            return new ScreenBuilder(ctr.get());
        });
        return new GUIType(id);
    }

    public <T extends BlockEntity> GUIType registerBlock(Class<T> cls, Function<T, IScreen> ctr) {
        int id = cls.toString().hashCode();
        registry.put(id, event -> {
            if (event.isServer) {
                return null;
            }
            T entity = event.player.getWorld().getBlockEntity(new Vec3i(event.entityIDorX, event.y, event.z), cls);
            if (entity == null) {
                return null;
            }
            IScreen screen = ctr.apply(entity);
            if (screen == null) {
                return null;
            }

            return new ScreenBuilder(screen);
        });
        return new GUIType(id);
    }

    public <T extends Entity> GUIType registerEntityContainer(Class<T> cls, Function<T, IContainer> ctr) {
        int id = ("container" + cls.toString()).hashCode();
        registry.put(id, event -> {
            T entity = event.player.getWorld().getEntity(event.entityIDorX, cls);
            if (entity == null) {
                return null;
            }
            ServerContainerBuilder server = new ServerContainerBuilder(event.player.internal.inventory, ctr.apply(entity));
            if (event.isServer) {
                return server;
            }
            return new ClientContainerBuilder(server);
        });
        return new GUIType(id);
    }

    public <T extends BlockEntity> GUIType registerBlockContainer(Class<T> cls, Function<T, IContainer> ctr) {
        int id = ("container" + cls.toString()).hashCode();

        registry.put(id, event -> {
            T entity = event.player.getWorld().getBlockEntity(new Vec3i(event.entityIDorX, event.y, event.z), cls);
            if (entity == null) {
                return null;
            }
            ServerContainerBuilder server = new ServerContainerBuilder(event.player.internal.inventory, ctr.apply(entity));
            if (event.isServer) {
                return server;
            }
            return new ClientContainerBuilder(server);
        });
        return new GUIType(id);
    }

    public void openGUI(Player player, GUIType type) {
        player.internal.openGui(ModCore.instance, type.id, player.getWorld().internal, 0, 0, 0);
    }

    public void openGUI(Player player, Entity ent, GUIType type) {
        player.internal.openGui(ModCore.instance, type.id, player.getWorld().internal, ent.internal.getEntityId(), 0, 0);
    }

    public void openGUI(Player player, Vec3i pos, GUIType type) {
        NetworkHooks.openGui((ServerPlayerEntity) player.internal, new INamedContainerProvider() {
            @Override
            public ITextComponent getDisplayName() {
                return new StringTextComponent( "WAT");
            }

            @Nullable
            @Override
            public Container createMenu(int p_createMenu_1_, PlayerInventory p_createMenu_2_, PlayerEntity p_createMenu_3_) {
                return (ServerContainerBuilder) registry.get(type);
            }
        }, pos.internal);
        player.internal.openContainer()
    }

    public static class GUIType {
        private final int id;

        private GUIType(int id) {
            this.id = id;
        }
    }

    private static class CreateEvent {
        final boolean isServer;
        final Player player;
        final int entityIDorX;
        final int y;
        final int z;

        private CreateEvent(boolean isServer, Player player, int entityIDorX, int y, int z) {
            this.isServer = isServer;
            this.player = player;
            this.entityIDorX = entityIDorX;
            this.y = y;
            this.z = z;
        }
    }
}
