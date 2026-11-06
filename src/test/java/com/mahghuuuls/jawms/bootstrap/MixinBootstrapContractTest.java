package com.mahghuuuls.jawms.bootstrap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Checks shipped inputs and actual bootstrap classes, not a simulated loader. */
class MixinBootstrapContractTest {

    private static final String EARLY = "mixins.jawms.early.json";
    private static final String LATE = "mixins.jawms.late.json";
    private static final String BOOTSTRAP = "com/mahghuuuls/jawms/bootstrap/";

    @Test
    void admissionMetadataUsesTheTargetMinimum() throws Exception {
        ClassNode main = bytecode("com/mahghuuuls/jawms/JustAnotherWizardryManaSystemMod");
        AnnotationNode mod = main.visibleAnnotations.stream()
                .filter(a -> a.desc.equals("Lnet/minecraftforge/fml/common/Mod;"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing normal mod entry"));
        String dependencies = annotationValue(mod, "dependencies");
        assertEquals("required-after:ebwizardry@[4.3.19,);"
                + "required-after:mixinbooter@[10.7,);after:baubles@[1.5.2,)", dependencies);
        JsonObject metadata = jsonArray("mcmod.info").get(0).getAsJsonObject();
        assertTrue(strings(metadata.getAsJsonArray("requiredMods")).contains("mixinbooter@[10.7,)"));

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Paths.get("gradle.properties"))) {
            properties.load(input);
        }
        assertEquals("11.8", properties.getProperty("mixin_booter_version").trim());
        assertEquals("10.7", properties.getProperty("mixin_booter_min_version").trim());
        assertEquals("1.4.1", metadata.get("version").getAsString());
    }

    @Test
    void requiredConfigurationsCoverEveryCandidateHookOnceOnItsOriginalSide() throws Exception {
        JsonObject early = json(EARLY);
        JsonObject late = json(LATE);
        assertEquals(set("EntityLivingBaseMixin", "PlayerListManaLifecycleMixin", "EntityPlayerManaLifecycleMixin", "EntityPlayerMixin", "ContainerWandPublicationMixin", "EntityPlayerMPWandPublicationMixin", "InventoryPlayerWandPublicationMixin", "NetHandlerWandPublicationServerMixin", "NetworkManagerWandPublicationMixin", "NonNullListWandMutationMixin"),
                strings(early.getAsJsonArray("mixins")));
        assertEquals(set("ItemStackTooltipClientMixin", "MinecraftWandSelectionClientMixin", "NetHandlerWandPublicationClientMixin", "ClientManaLifecycleMixin", "PlayerControllerParticipantUseMixin"), strings(early.getAsJsonArray("client")));
        assertEquals(set("WandHelperBindingMutationMixin", "EntityForceArrowMixin", "ForceArrowMixin", "ItemWandMixin",
                "ItemWizardArmourMixin", "ItemArtefactMixin", "ItemManaFlaskMixin"),
                strings(late.getAsJsonArray("mixins")));
        assertEquals(set("GuiSpellInfoMixin", "ItemWandClientMixin", "ItemManaFlaskClientMixin"),
                strings(late.getAsJsonArray("client")));
        Set<String> all = new HashSet<>();
        int count = 0;
        for (JsonObject config : Arrays.asList(early, late)) {
            assertEquals("com.mahghuuuls.jawms.mixin", config.get("package").getAsString());
            assertTrue(config.get("required").getAsBoolean());
            assertEquals("JAVA_8", config.get("compatibilityLevel").getAsString());
            assertEquals("0.8.5", config.get("minVersion").getAsString());
            assertEquals("@env(DEFAULT)", config.get("target").getAsString());
            assertEquals("mixins.jawms.refmap.json", config.get("refmap").getAsString());
            assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
            assertEquals(0, config.getAsJsonArray("server").size());
            for (String side : Arrays.asList("mixins", "client")) {
                for (com.google.gson.JsonElement name : config.getAsJsonArray(side)) {
                    assertTrue(all.add(name.getAsString()), "Duplicate hook " + name);
                    count++;
                }
            }
        }
        assertEquals(25, count);
        assertNull(getClass().getClassLoader().getResource("mixins.jawms.json"),
                "The former unsplit resource must not remain a fallback");
    }

    @Test
    void discoveredAdaptersReturnOnlyTheirOwnedConfiguration() throws Exception {
        assertAdapter("JawmsEarlyMixinLoader", EARLY,
                "net/minecraftforge/fml/relauncher/IFMLLoadingPlugin", "zone/rong/mixinbooter/IEarlyMixinLoader");
        assertAdapter("JawmsLateMixinLoader", LATE, "zone/rong/mixinbooter/ILateMixinLoader");
    }

    @Test
    void compiledAdaptersLinkAndReturnConfigsAgainstRealTenSevenInterfaces() throws Exception {
        URL legacy = Paths.get(System.getProperty("jawms.test.legacyMixinBooter")).toUri().toURL();
        URL output = Class.forName("com.mahghuuuls.jawms.bootstrap.JawmsEarlyMixinLoader")
                .getProtectionDomain().getCodeSource().getLocation();
        URL forge = Class.forName("net.minecraftforge.fml.relauncher.IFMLLoadingPlugin", false,
                getClass().getClassLoader()).getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{output, legacy, forge}, null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if ((name.startsWith("com.mahghuuuls.jawms.") && !name.startsWith("com.mahghuuuls.jawms.bootstrap."))
                        || name.startsWith("net.minecraft.client.") || name.startsWith("electroblob.")) {
                    throw new AssertionError("Bootstrap attempted gameplay/client initialization: " + name);
                }
                return super.loadClass(name, resolve);
            }
        }) {
            for (String phase : Arrays.asList("Early", "Late")) {
                Class<?> spi = loader.loadClass("zone.rong.mixinbooter.I" + phase + "MixinLoader");
                assertEquals(legacy, spi.getProtectionDomain().getCodeSource().getLocation());
                Class<?> adapter = loader.loadClass("com.mahghuuuls.jawms.bootstrap.Jawms" + phase + "MixinLoader");
                Object instance = adapter.getConstructor().newInstance();
                assertTrue(spi.isInstance(instance));
                String config = phase.equals("Early") ? EARLY : LATE;
                assertEquals(Arrays.asList(config), spi.getMethod("getMixinConfigs").invoke(instance));
                assertEquals(true, spi.getMethod("shouldMixinConfigQueue", String.class).invoke(instance, config));
            }
        }
    }

    @Test
    void bootstrapDoesNotInitializeClientOrGameplayServicesOrRegisterDirectly() throws Exception {
        for (String name : Arrays.asList("JawmsEarlyMixinLoader", "JawmsLateMixinLoader")) {
            ClassNode node = bytecode(BOOTSTRAP + name);
            assertEquals(52, node.version);
            node.fields.forEach(field -> assertEquals(Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    field.access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL), "No mutable bootstrap state"));
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode) {
                        String owner = ((MethodInsnNode) instruction).owner;
                        assertTrue(owner.startsWith("java/") || owner.startsWith(BOOTSTRAP),
                                "Bootstrap must only construct/return its Java config list: " + owner);
                    }
                }
            }
            if (node.visibleAnnotations != null) {
                assertTrue(node.visibleAnnotations.stream().noneMatch(a -> a.desc.equals(
                        "Lzone/rong/mixinbooter/MixinLoader;")), "No second annotation discovery route");
            }
        }
    }

    @Test
    void forgeLifecycleAnchorsKeepCancellationAndVanillaPacketOrdering() throws Exception {
        String playerType = "net/minecraft/entity/player/EntityPlayerMP";
        String teleporter = "Lnet/minecraftforge/common/util/ITeleporter;";
        MethodNode travel = method(bytecode(playerType), "changeDimension",
                "(I" + teleporter + ")Lnet/minecraft/entity/Entity;");
        int cancellation = callIndex(travel, "onTravelToDimension");
        AbstractInsnNode branch = nextInstruction(travel.instructions.get(cancellation));
        assertEquals(Opcodes.IFNE, branch.getOpcode());
        assertEquals(Opcodes.ARETURN, nextInstruction(nextInstruction(branch)).getOpcode());
        int continuing = travel.instructions.indexOf(((JumpInsnNode) branch).label);
        assertTrue(continuing < callIndex(travel, "removeEntity"));
        assertTrue(continuing < callIndex(travel, "transferPlayerToDimension"));

        MethodNode transfer = method(bytecode("net/minecraft/server/management/PlayerList"),
                "transferPlayerToDimension", "(L" + playerType + ";I" + teleporter + ")V");
        int write = -1;
        for (AbstractInsnNode instruction : transfer.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.PUTFIELD && instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) instruction;
                if (field.owner.equals(playerType) && field.name.equals("dimension")) {
                    assertEquals(-1, write, "The native mutation anchor must be unique");
                    write = transfer.instructions.indexOf(instruction);
                }
            }
        }
        int respawn = callIndex(transfer, "sendPacket");
        assertTrue(write >= 0 && write < respawn);
        assertTrue(respawn < callIndex(transfer, "firePlayerChangedDimensionEvent"));

        ClassNode handler = bytecode("net/minecraft/client/network/NetHandlerPlayClient");
        for (String[] endpoint : new String[][] {
                {"handleCombatEvent", "SPacketCombatEvent"},
                {"handleChangeGameState", "SPacketChangeGameState"},
                {"handleRespawn", "SPacketRespawn"}
        }) {
            MethodNode incoming = method(handler, endpoint[0], "(Lnet/minecraft/network/play/server/" + endpoint[1] + ";)V");
            assertTrue(callIndex(incoming, "checkThreadAndEnqueue") < 10);
        }
    }

    @Test
    void newCommonLifecycleHooksHaveNoClientLinkage() throws Exception {
        for (String name : Arrays.asList("mixin/PlayerListManaLifecycleMixin", "mixin/EntityPlayerManaLifecycleMixin",
                "internal/mana/ManaLifecycle")) {
            ClassNode type = bytecode("com/mahghuuuls/jawms/" + name);
            for (MethodNode method : type.methods) {
                assertFalse(method.desc.contains("net/minecraft/client/"), name);
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode) {
                        assertFalse(((MethodInsnNode) instruction).owner.startsWith("net/minecraft/client/"), name);
                    }
                }
            }
        }
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst().orElseThrow(() -> new AssertionError("Missing native target " + owner.name + "." + name));
    }

    private static int callIndex(MethodNode method, String name) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals(name)) {
                return method.instructions.indexOf(instruction);
            }
        }
        throw new AssertionError("Missing call " + method.name + "." + name);
    }

    private static AbstractInsnNode nextInstruction(AbstractInsnNode instruction) {
        do {
            instruction = instruction.getNext();
        } while (instruction.getOpcode() < 0);
        return instruction;
    }

    private void assertAdapter(String name, String config, String... expectedInterfaces) throws Exception {
        ClassNode node = bytecode(BOOTSTRAP + name);
        assertEquals(set(expectedInterfaces), new HashSet<>(node.interfaces));
        Class<?> type = Class.forName((BOOTSTRAP + name).replace('/', '.'));
        Object instance = type.getConstructor().newInstance();
        Object configs = type.getMethod("getMixinConfigs").invoke(instance);
        assertEquals(Arrays.asList(config), configs);
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) configs).clear());
    }

    private static String annotationValue(AnnotationNode node, String key) {
        for (int i = 0; i < node.values.size(); i += 2) {
            if (key.equals(node.values.get(i))) return (String) node.values.get(i + 1);
        }
        throw new AssertionError("Missing annotation value " + key);
    }

    private ClassNode bytecode(String name) throws Exception {
        try (InputStream input = resource(name + ".class")) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private JsonObject json(String path) throws Exception {
        try (InputStream input = resource(path)) {
            return new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private JsonArray jsonArray(String path) throws Exception {
        try (InputStream input = resource(path)) {
            return new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonArray();
        }
    }

    private InputStream resource(String path) {
        InputStream input = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(input, "Missing production resource " + path);
        return input;
    }

    private static Set<String> strings(JsonArray values) {
        Set<String> result = new HashSet<>();
        values.forEach(value -> assertTrue(result.add(value.getAsString()), "Duplicate value " + value));
        return result;
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
