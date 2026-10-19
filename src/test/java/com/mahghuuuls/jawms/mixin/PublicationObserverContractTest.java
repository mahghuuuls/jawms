package com.mahghuuuls.jawms.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.selectors.ElementNode;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelector;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Compiled package/annotation and real selector-parser contracts, not a transformed game bootstrap. */
class PublicationObserverContractTest {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String WRAP = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String SLOT = "net/minecraft/network/play/server/SPacketSetSlot";
    private static final String WINDOW = "net/minecraft/network/play/server/SPacketWindowItems";

    @Test void reservedPackageContainsOnlyMixinDefinitionsNotCallableHelpers() throws Exception {
        String root = config().get("package").getAsString().replace('.', '/') + "/";
        Path classes = Paths.get("build/classes/java/developmentFixture");
        int count = 0;
        try (Stream<Path> paths = Files.walk(classes.resolve(root))) {
            for (Path path : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".class"))::iterator) {
                ClassNode node = read(Files.newInputStream(path));
                assertNotNull(annotation(node.invisibleAnnotations, MIXIN),
                        "Ordinary callable class is forbidden in reserved Mixin package: " + node.name);
                count++;
            }
        }
        assertTrue(count >= 9, "Must inspect actual compiled observer definitions, not an empty directory");
    }

    @Test void packetCopySelectorsResolveOnlyTheParameterizedCopyConstructors() throws Exception {
        JsonObject config = config();
        String root = config.get("package").getAsString().replace('.', '/') + "/";
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put(SLOT, "(IILnet/minecraft/item/ItemStack;)V");
        expected.put(WINDOW, "(ILnet/minecraft/util/NonNullList;)V");
        Set<String> inspected = new HashSet<>();
        for (com.google.gson.JsonElement name : config.getAsJsonArray("mixins")) {
            ClassNode observer = resource(root + name.getAsString());
            AnnotationNode mixin = annotation(observer.invisibleAnnotations, MIXIN);
            assertNotNull(mixin, observer.name);
            @SuppressWarnings("unchecked") List<Type> targets = (List<Type>) value(mixin, "value");
            if (targets == null) continue;
            for (Type type : targets) {
                String target = type.getInternalName();
                if (!expected.containsKey(target)) continue;
                for (MethodNode handler : observer.methods) {
                    AnnotationNode wrap = annotation(handler.visibleAnnotations, WRAP);
                    if (wrap == null) continue;
                    @SuppressWarnings("unchecked") List<String> selectors = (List<String>) value(wrap, "method");
                    if (selectors == null || selectors.stream().noneMatch(s -> s.startsWith("<init>"))) continue;
                    assertTrue(inspected.add(target), "Exactly one constructor copy observer per packet class");
                    assertEquals(1, selectors.size());
                    MemberInfo selector = MemberInfo.parse(selectors.get(0), null).validate();
                    assertEquals(expected.get(target), selector.getDesc(),
                            "Bare <init> can select the empty constructor; require the exact copied constructor");
                    ITargetSelector member = selector.configure(ITargetSelector.Configure.SELECT_MEMBER);
                    ClassNode packet = resource(target);
                    int selected = 0;
                    for (MethodNode method : packet.methods) {
                        if (!member.match(ElementNode.of(packet, method)).isExactMatch()) continue;
                        selected++;
                        assertEquals("<init>", method.name);
                        assertEquals(expected.get(target), method.desc);
                        int copies = 0;
                        for (AbstractInsnNode instruction : method.instructions.toArray()) {
                            if (instruction instanceof MethodInsnNode) {
                                MethodInsnNode call = (MethodInsnNode) instruction;
                                if (call.owner.equals("net/minecraft/item/ItemStack") && call.name.equals("copy")
                                        && call.desc.equals("()Lnet/minecraft/item/ItemStack;")) copies++;
                            }
                        }
                        assertEquals(1, copies, "Selected constructor must reach the actual copy boundary");
                    }
                    assertEquals(1, selected, "Real Mixin parser must match one exact packet constructor");
                    @SuppressWarnings("unchecked") List<AnnotationNode> points = (List<AnnotationNode>) value(wrap, "at");
                    assertNotNull(points);
                    assertEquals(1, points.size());
                    AnnotationNode at = points.get(0);
                    assertEquals("INVOKE", value(at, "value"));
                    assertEquals("Lnet/minecraft/item/ItemStack;copy()Lnet/minecraft/item/ItemStack;", value(at, "target"));
                    Object require = value(wrap, "require");
                    assertTrue(require == null ? config.getAsJsonObject("injectors").get("defaultRequire").getAsInt() >= 1
                            : ((Integer) require) >= 1, "Do not conceal a missing hook with require=0");
                }
            }
        }
        assertEquals(expected.keySet(), inspected, "Both actual packet paths must retain their observer");
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations != null) for (AnnotationNode annotation : annotations)
            if (descriptor.equals(annotation.desc)) return annotation;
        return null;
    }
    private static JsonObject config() throws Exception {
        try (InputStream input = PublicationObserverContractTest.class.getResourceAsStream("/mixins.jawms-publication-observer.json")) {
            assertNotNull(input);
            return new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static ClassNode resource(String type) throws Exception {
        return read(PublicationObserverContractTest.class.getResourceAsStream("/" + type + ".class"));
    }
    private static ClassNode read(InputStream input) throws Exception {
        assertNotNull(input);
        try (InputStream stream = input) {
            ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
}
