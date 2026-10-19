package com.mahghuuuls.jawms.testfixture;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Compiled subscription contract, not transformed Forge event dispatch or an FML launch. */
class FixtureEventRegistrationTest {
    @Test void transportSubscribesOnceToTheSingleForgeBus() throws Exception {
        singleSubscription("com/mahghuuuls/jawms/testfixture/ContinuousTransportFixture");
    }
    @Test void continuousCountersSubscribeOnceToTheSingleForgeBus() throws Exception {
        singleSubscription("example/jawmscast/ContinuousCastFixture");
    }
    private void singleSubscription(String owner) throws Exception {
        assertSame(MinecraftForge.EVENT_BUS, FMLCommonHandler.instance().bus(), "1.12.2 exposes the same bus through both accessors");
            ClassNode type = new ClassNode();
            try (InputStream source = getClass().getClassLoader().getResourceAsStream(owner + ".class")) {
                assertNotNull(source, owner); new ClassReader(source).accept(type, 0);
            }
            MethodNode initialize = type.methods.stream().filter(method -> method.name.equals("initialize")).findFirst().get();
            int subscriptions = 0, forgeBusReads = 0;
            for (AbstractInsnNode instruction : initialize.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.owner.equals("net/minecraftforge/fml/common/eventhandler/EventBus") && call.name.equals("register")) subscriptions++;
                }
                if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) instruction;
                    if (field.getOpcode() == Opcodes.GETSTATIC && field.owner.equals("net/minecraftforge/common/MinecraftForge") && field.name.equals("EVENT_BUS")) forgeBusReads++;
                }
            }
            assertEquals(1, subscriptions, owner + " must not double its annotated connection/counter callbacks");
            assertEquals(1, forgeBusReads, owner);
    }
}
