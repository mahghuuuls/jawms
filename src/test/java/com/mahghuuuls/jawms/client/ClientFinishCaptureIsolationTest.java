package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.internal.casting.ClientUseState;
import com.mahghuuuls.jawms.internal.casting.FinishContinuation;
import com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages;
import com.mahghuuuls.jawms.proxy.ClientProxy;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraftforge.registries.IRegistryDelegate;
import org.junit.jupiter.api.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real guard/proxy capture and close calls; no woven ItemWand, input, network or game runtime. */
class ClientFinishCaptureIsolationTest {
    private final ClientProxy proxy = new ClientProxy();
    private final ResourceLocation spellId = new ResourceLocation("fixture", "finish_isolation");
    private final UUID epoch = UUID.randomUUID();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private ExecutorService server;
    private Minecraft previousMinecraft;
    private IForgeRegistry<Spell> previousSpells;
    private Minecraft minecraft;
    private EntityPlayerSP player;
    private NetHandlerPlayClient handler;
    private ItemStack wand;
    private Spell spell;
    private ClientUseState state;
    private Map<Item, String> upgrades;
    private Map<Item, String> previousUpgrades;

    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @SuppressWarnings("unchecked")
    @BeforeEach void setUp() throws Exception {
        server = Executors.newSingleThreadExecutor(r -> new Thread(r, "simulated-integrated-server"));
        previousMinecraft = Minecraft.getMinecraft();
        previousSpells = Spell.registry;
        minecraft = mock(Minecraft.class);
        Field singleton = Minecraft.class.getDeclaredField("instance"); singleton.setAccessible(true); singleton.set(null, minecraft);
        player = mock(EntityPlayerSP.class);
        player.world = mock(WorldClient.class);
        player.inventory = new InventoryPlayer(player);
        minecraft.player = player;
        handler = mock(NetHandlerPlayClient.class);
        when(minecraft.getConnection()).thenReturn(handler);
        when(handler.getNetworkManager()).thenReturn(mock(NetworkManager.class));
        when(minecraft.addScheduledTask(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return com.google.common.util.concurrent.Futures.immediateFuture(null);
        });
        wand = new ItemStack(Items.STICK);
        NBTTagCompound data = new NBTTagCompound(); data.setIntArray("spells", new int[] {1}); wand.setTagCompound(data);
        player.inventory.mainInventory.set(0, wand);
        when(player.getHeldItem(EnumHand.MAIN_HAND)).thenReturn(wand);
        spell = mock(Spell.class);
        IRegistryDelegate<Spell> delegate = mock(IRegistryDelegate.class);
        when(delegate.name()).thenReturn(spellId);
        Field delegateField = IForgeRegistryEntry.Impl.class.getField("delegate"); delegateField.setAccessible(true); delegateField.set(spell, delegate);
        Field continuous = Spell.class.getField("isContinuous"); continuous.setAccessible(true); continuous.setBoolean(spell, true);
        ForgeRegistry<Spell> registry = mock(ForgeRegistry.class);
        when(registry.getValue(1)).thenReturn(spell); Spell.registry = registry;
        Field upgradeMap = WandHelper.class.getDeclaredField("upgradeMap"); upgradeMap.setAccessible(true);
        upgrades = (Map<Item, String>) upgradeMap.get(null); previousUpgrades = new HashMap<>(upgrades);
        upgrades.put(WizardryItems.cooldown_upgrade, "finish_capture_test");
        clearGuard();
        state = new ClientUseState(); state.capability(epoch);
        state.input(EnumHand.MAIN_HAND, spellId); state.bind(EnumHand.MAIN_HAND, spellId); state.release();
        setGuard("state", state); setGuard("connection", handler);
        Class<?> originType = Class.forName(ClientParticipantUseGuard.class.getName() + "$Origin");
        Constructor<?> constructor = originType.getDeclaredConstructor(EntityPlayer.class, ItemStack.class, EnumHand.class, Spell.class);
        constructor.setAccessible(true); setGuard("origin", constructor.newInstance(player, wand, EnumHand.MAIN_HAND, spell));
    }
    @AfterEach void tearDown() throws Exception {
        try { clearGuard(); }
        finally {
            Spell.registry = previousSpells;
            if (upgrades != null && previousUpgrades != null) { upgrades.clear(); upgrades.putAll(previousUpgrades); }
            Field singleton = Minecraft.class.getDeclaredField("instance"); singleton.setAccessible(true); singleton.set(null, previousMinecraft);
            server.shutdownNow(); assertTrue(server.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void serverEventAndCooldownCannotOverflowAnOpenClientCapture() throws Exception {
        begin();
        assertTrue(proxy.deferParticipantFinishAction(() -> calls.add("client-event")));
        assertTrue(proxy.deferParticipantFinishAction(() -> calls.add("client-body")));
        proxy.captureParticipantBaseCooldown(20);
        ItemStack serverWand = new ItemStack(Items.BLAZE_ROD);
        BiConsumer<ItemStack, Integer> serverCooldown = (stack, value) -> {
            assertSame(serverWand, stack); assertEquals(700, value.intValue()); calls.add("server-cooldown");
        };
        // The owner keeps its two captured operations open until the other thread completes.
        onServer(() -> {
            if (!proxy.deferParticipantFinishAction(() -> calls.add("server-event"))) calls.add("server-event");
            proxy.captureParticipantBaseCooldown(900);
            if (!proxy.deferParticipantCooldown(700, modifiers(2), serverCooldown)) serverCooldown.accept(serverWand, 700);
        });
        assertEquals(Arrays.asList("server-event", "server-cooldown"), calls);
        assertTrue(proxy.isParticipantFinishCaptureActive());
        assertTrue(proxy.deferParticipantCooldown(999, modifiers(.5F), (stack, value) -> {
            assertSame(wand, stack); assertEquals(10, value.intValue()); calls.add("client-cooldown");
        }));
        proxy.endParticipantFinish(true);
        assertEquals(2, calls.size(), "client operations await their actual close");
        close(ParticipantUseMessages.CloseCause.NORMAL_FINISH);
        close(ParticipantUseMessages.CloseCause.NORMAL_FINISH);
        assertEquals(Arrays.asList("server-event", "server-cooldown", "client-event", "client-body", "client-cooldown"), calls);
        assertFalse(proxy.isParticipantFinishCaptureActive());
        assertNull(getGuard("pending"));
    }

    @Test void foreignEndCannotDiscardTheOwnersActiveCapture() throws Exception {
        begin();
        assertTrue(proxy.deferParticipantFinishAction(() -> calls.add("client-event")));
        onServer(() -> { assertFalse(proxy.isParticipantFinishCaptureActive()); proxy.endParticipantFinish(false); });
        assertTrue(proxy.isParticipantFinishCaptureActive());
        assertTrue(proxy.deferParticipantFinishAction(() -> calls.add("client-body")));
        proxy.endParticipantFinish(true);
        close(ParticipantUseMessages.CloseCause.NORMAL_FINISH);
        assertEquals(Arrays.asList("client-event", "client-body"), calls);
    }

    @Test void foreignEndCannotDiscardADeferredContinuationAfterCaptureEnds() throws Exception {
        begin();
        assertTrue(proxy.deferParticipantFinishAction(() -> calls.add("client-event")));
        proxy.endParticipantFinish(true);
        Object pending = getGuard("pending"); assertNotNull(pending);
        onServer(() -> { assertFalse(proxy.isParticipantFinishCaptureActive()); proxy.endParticipantFinish(false); });
        assertSame(pending, getGuard("pending"));
        close(ParticipantUseMessages.CloseCause.NORMAL_FINISH);
        assertEquals(Collections.singletonList("client-event"), calls);
    }

    @Test void failedCaptureDiscardsActionsAndClearsItsActiveScope() throws Exception {
        begin();
        assertTrue(proxy.deferParticipantFinishAction(() -> calls.add("discarded")));
        proxy.endParticipantFinish(false);
        assertFalse(proxy.isParticipantFinishCaptureActive());
        assertNull(getGuard("pending"));
        assertFalse(proxy.deferParticipantFinishAction(() -> calls.add("outside")));
        close(ParticipantUseMessages.CloseCause.NORMAL_FINISH);
        assertTrue(calls.isEmpty());
        onServer(() -> assertFalse(proxy.isParticipantFinishCaptureActive()));
    }

    @Test void disconnectDropsCapturedReferencesAndLateCloseCannotReplayThem() throws Exception {
        begin();
        assertTrue(proxy.deferParticipantFinishAction(() -> calls.add("disconnected")));
        FMLNetworkEvent.ClientDisconnectionFromServerEvent event = mock(FMLNetworkEvent.ClientDisconnectionFromServerEvent.class);
        NetworkManager manager = handler.getNetworkManager();
        when(event.getManager()).thenReturn(manager);
        new ClientParticipantUseGuard().disconnected(event);
        assertFalse(proxy.isParticipantFinishCaptureActive());
        assertNull(getGuard("pending")); assertNull(getGuard("origin")); assertNull(getGuard("connection"));
        close(ParticipantUseMessages.CloseCause.NORMAL_FINISH);
        assertTrue(calls.isEmpty());
    }

    @Test void normalCallbacksRunAfterScopeRetirementAndCannotReenterTheOldFinish() throws Exception {
        begin();
        assertTrue(proxy.deferParticipantFinishAction(() -> {
            assertFalse(proxy.isParticipantFinishCaptureActive());
            assertEquals(FinishContinuation.Capture.SUPPRESSED, proxy.beginParticipantFinish(player, wand, EnumHand.MAIN_HAND, spell));
            assertFalse(proxy.deferParticipantFinishAction(() -> calls.add("leaked")));
            calls.add("once");
        }));
        proxy.endParticipantFinish(true);
        close(ParticipantUseMessages.CloseCause.NORMAL_FINISH);
        close(ParticipantUseMessages.CloseCause.NORMAL_FINISH);
        assertEquals(Collections.singletonList("once"), calls);
    }

    @Test void terminalCloseAndNoCapabilityDoNotExecuteCapturedOrLegacyActions() throws Exception {
        begin();
        assertTrue(proxy.deferParticipantFinishAction(() -> calls.add("terminal")));
        proxy.endParticipantFinish(true);
        close(ParticipantUseMessages.CloseCause.TERMINAL);
        assertTrue(calls.isEmpty());
        clearGuard();
        assertEquals(FinishContinuation.Capture.LEGACY, proxy.beginParticipantFinish(player, wand, EnumHand.MAIN_HAND, spell));
        assertFalse(proxy.deferParticipantFinishAction(() -> calls.add("legacy-intercepted")));
        assertFalse(proxy.deferParticipantCooldown(20, modifiers(1), (stack, value) -> calls.add("legacy-cooldown-intercepted")));
        onServer(() -> assertFalse(proxy.isParticipantFinishCaptureActive()));
        assertTrue(calls.isEmpty());
    }

    private void begin() { assertEquals(FinishContinuation.Capture.CAPTURED, proxy.beginParticipantFinish(player, wand, EnumHand.MAIN_HAND, spell)); }
    private void close(ParticipantUseMessages.CloseCause cause) {
        proxy.acceptParticipantUseClose(new ParticipantUseMessages.Close(epoch, 1, 1, EnumHand.MAIN_HAND, spellId, cause, 1), handler);
    }
    private static SpellModifiers modifiers(float factor) {
        return new SpellModifiers().set("finish_capture_test", factor, false);
    }
    private void onServer(Runnable operation) throws Exception { server.submit(operation).get(5, TimeUnit.SECONDS); }
    private static void setGuard(String name, Object value) throws Exception {
        Field field = ClientParticipantUseGuard.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
    private static Object getGuard(String name) throws Exception {
        Field field = ClientParticipantUseGuard.class.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
    private static void clearGuard() throws Exception {
        Method method = ClientParticipantUseGuard.class.getDeclaredMethod("clear"); method.setAccessible(true); method.invoke(null);
    }
}
