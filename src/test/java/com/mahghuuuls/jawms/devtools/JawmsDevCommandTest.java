package com.mahghuuuls.jawms.devtools;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionDisposition;
import com.mahghuuuls.jawms.api.ManaContributionExplanation;
import com.mahghuuuls.jawms.api.ManaContributionFailure;
import com.mahghuuuls.jawms.api.ManaContributionSourceKind;
import com.mahghuuuls.jawms.api.ManaMutationFailure;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaProfileAggregation;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;
import com.mahghuuuls.jawms.api.ManaProviderDescriptor;
import com.mahghuuuls.jawms.api.ManaProviderKind;
import com.mahghuuuls.jawms.api.ManaPublicState;
import electroblob.wizardry.constants.Element;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JawmsDevCommandTest {

    @Test
    void rootContractIsPermissionFourAndContainsOnlyApprovedOperations() {
        JawmsDevCommand command = new JawmsDevCommand(mockAccess());

        assertEquals("jawms-dev", command.getName());
        assertEquals(4, command.getRequiredPermissionLevel());
        assertEquals("/jawms-dev state [player]"
                        + " | mana <set|restore|consume|drain> <player> <amount>"
                        + " | providers | explain [player]"
                        + " | cache <invalidate|dirty> <player>",
                command.getUsage(mock(ICommandSender.class)));
    }

    @Test
    void manaCommandUsesTypedPublicResultAndToolCause() throws Exception {
        JawmsDevCommand.PublicApi access = mockAccess();
        IManaService service = mock(IManaService.class);
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        ICommandSender sender = mock(ICommandSender.class);
        MinecraftServer server = mock(MinecraftServer.class);
        ManaPublicState before = state(10, 60);
        ManaPublicState after = state(3, 60);
        ManaMutationResult result = new ManaMutationResult(
                ManaMutationFailure.NONE,
                7,
                -7,
                JawmsDevCommand.TOOL_CAUSE,
                before,
                after
        );
        when(access.service()).thenReturn(service);
        when(access.resolvePlayer(server, sender, "Mage")).thenReturn(player);
        when(player.getName()).thenReturn("Mage");
        when(service.consumeMana(player, 7, JawmsDevCommand.TOOL_CAUSE))
                .thenReturn(result);

        new JawmsDevCommand(access).execute(
                server, sender, new String[]{"mana", "consume", "Mage", "7"}
        );

        verify(service).consumeMana(player, 7, JawmsDevCommand.TOOL_CAUSE);
        ArgumentCaptor<ITextComponent> output = ArgumentCaptor.forClass(ITextComponent.class);
        verify(sender).sendMessage(output.capture());
        assertEquals("[jawms-dev] mana operation=consume player=Mage success=true"
                        + " failure=NONE requested=7 actualDelta=-7"
                        + " cause=jawms_devtools:command old=10/60 new=3/60",
                output.getValue().getUnformattedText());
    }

    @Test
    void invalidSyntaxAndOperationDoNotTouchManaOrCache() {
        JawmsDevCommand.PublicApi access = mockAccess();
        IManaService service = mock(IManaService.class);
        when(access.service()).thenReturn(service);
        JawmsDevCommand command = new JawmsDevCommand(access);
        MinecraftServer server = mock(MinecraftServer.class);
        ICommandSender sender = mock(ICommandSender.class);

        assertThrows(CommandException.class, () -> command.execute(
                server, sender, new String[]{"wand", "mana", "10"}
        ));
        assertThrows(CommandException.class, () -> JawmsDevCommand.mutate(
                service, "unknown", mock(EntityPlayerMP.class), 1
        ));

        verify(service, never()).setCurrentMana(any(), any(Integer.class), any());
        verify(service, never()).restoreMana(any(), any(Integer.class), any());
        verify(service, never()).consumeMana(any(), any(Integer.class), any());
        verify(service, never()).drainMana(any(), any(Integer.class), any());
        verify(access, never()).invalidate(any());
        verify(access, never()).dirty(any());
    }

    @Test
    void mutationDispatcherOwnsAllFourPublicOperations() throws Exception {
        IManaService service = mock(IManaService.class);
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        ManaMutationResult result = new ManaMutationResult(
                ManaMutationFailure.NONE, 1, 0, JawmsDevCommand.TOOL_CAUSE,
                state(10, 60), state(10, 60)
        );
        when(service.setCurrentMana(player, 1, JawmsDevCommand.TOOL_CAUSE))
                .thenReturn(result);
        when(service.restoreMana(player, 1, JawmsDevCommand.TOOL_CAUSE))
                .thenReturn(result);
        when(service.consumeMana(player, 1, JawmsDevCommand.TOOL_CAUSE))
                .thenReturn(result);
        when(service.drainMana(player, 1, JawmsDevCommand.TOOL_CAUSE))
                .thenReturn(result);

        assertSame(result, JawmsDevCommand.mutate(service, "set", player, 1));
        assertSame(result, JawmsDevCommand.mutate(service, "restore", player, 1));
        assertSame(result, JawmsDevCommand.mutate(service, "consume", player, 1));
        assertSame(result, JawmsDevCommand.mutate(service, "drain", player, 1));
    }

    @Test
    void consoleStateWithoutTargetFailsBeforeStateQuery() throws Exception {
        JawmsDevCommand.PublicApi access = mockAccess();
        IManaService service = mock(IManaService.class);
        ICommandSender sender = mock(ICommandSender.class);
        when(access.service()).thenReturn(service);
        when(access.resolveSenderPlayer(sender))
                .thenThrow(new CommandException("Player target required."));

        assertThrows(CommandException.class, () -> new JawmsDevCommand(access).execute(
                mock(MinecraftServer.class), sender, new String[]{"state"}
        ));
        verify(service, never()).getState(any());
    }

    @Test
    void cacheOperationsPreserveImmediateAndDeferredMeanings() throws Exception {
        JawmsDevCommand.PublicApi access = mockAccess();
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        MinecraftServer server = mock(MinecraftServer.class);
        ICommandSender sender = mock(ICommandSender.class);
        when(player.getName()).thenReturn("Mage");
        when(access.resolvePlayer(server, sender, "Mage")).thenReturn(player);
        JawmsDevCommand command = new JawmsDevCommand(access);

        command.execute(server, sender,
                new String[]{"cache", "invalidate", "Mage"});
        command.execute(server, sender,
                new String[]{"cache", "dirty", "Mage"});

        verify(access).invalidate(player);
        verify(access).dirty(player);
    }

    @Test
    void providersAndExplanationFormattingAreDeterministicAndComplete() {
        ManaProviderDescriptor item = new ManaProviderDescriptor(
                new ResourceLocation("addon", "item"),
                ManaProviderKind.ITEM,
                new ResourceLocation("minecraft", "stick")
        );
        assertEquals("[jawms-dev] provider index=2 id=addon:item kind=ITEM"
                        + " target=minecraft:stick",
                JawmsDevCommand.formatProvider(2, item));

        EnumMap<Element, Double> efficiencies = new EnumMap<>(Element.class);
        efficiencies.put(Element.FIRE, 10.0D);
        EnumMap<Element, Double> multipliers = new EnumMap<>(Element.class);
        multipliers.put(Element.FIRE, 0.5D);
        ManaProfileAggregation maximum = new ManaProfileAggregation(
                60.0D, 10.0D, 70.0D, 0.0D, 1.0D, 70.0D
        );
        ManaProfileAggregation regeneration = new ManaProfileAggregation(
                2.4D, 0.6D, 3.0D, 0.0D, 1.0D, 3.0D
        );
        ManaProfileAggregation lockout = new ManaProfileAggregation(
                2.0D, 0.0D, 2.0D, 0.0D, 1.0D, 2.0D
        );
        ManaContribution accepted = ManaContribution.builder()
                .flatMaximumMana(10)
                .flatRegeneration(0.6D)
                .globalSpellEfficiency(5.0D)
                .build();
        ManaContributionExplanation source = new ManaContributionExplanation(
                new ResourceLocation("addon", "provider"),
                ManaContributionSourceKind.CONTEXTUAL_PROVIDER,
                ManaContributionDisposition.APPLIED,
                ManaContributionFailure.NONE,
                null,
                null,
                -1,
                accepted
        );
        ManaProfileExplanation explanation = new ManaProfileExplanation(
                20L, 4L, 60, 2.4D, 2.0D,
                maximum, regeneration, lockout,
                70, 3.0D, 2.0D, 40L,
                5.0D, efficiencies, 1.0D, multipliers,
                false, true, Collections.singletonList(source)
        );

        java.util.List<String> lines = JawmsDevCommand.formatExplanation(
                "Mage", explanation
        );
        String combined = String.join("\n", lines);
        assertTrue(combined.contains("tick=20 rulesRevision=4"));
        assertTrue(combined.contains("maximum=70 regeneration=3.0"));
        assertTrue(combined.contains("aggregation=maximum base=60.0 flat=10.0"));
        assertTrue(combined.contains("elementEfficiency={FIRE=10.0}"));
        assertTrue(combined.contains("elementCostMultipliers={FIRE=0.5}"));
        assertTrue(combined.contains("sources=1"));
        assertTrue(combined.contains("id=addon:provider"));
        assertTrue(combined.contains("disposition=APPLIED failure=NONE"));
        assertTrue(combined.contains("contribution={flatMaximum=10"));
    }

    @Test
    void stateFormattingIncludesEveryPublicScalarAndGate() {
        String line = JawmsDevCommand.formatState("Mage", new ManaPublicState(
                20, 60, 2.4D, 40L, 15L,
                true, false, true, false
        ));

        assertTrue(line.contains("current=20 maximum=60 regeneration=2.4"));
        assertTrue(line.contains("effectiveLockoutTicks=40"));
        assertTrue(line.contains("effectiveLockoutSeconds=2.0"));
        assertTrue(line.contains("remainingLockoutTicks=15"));
        assertTrue(line.contains("remainingLockoutSeconds=0.75"));
        assertTrue(line.contains("postCastPermission=true"));
        assertTrue(line.contains("continuousPermission=false"));
        assertTrue(line.contains("continuousActive=true"));
        assertTrue(line.contains("regenerationEligible=false"));
    }

    private static JawmsDevCommand.PublicApi mockAccess() {
        return mock(JawmsDevCommand.PublicApi.class);
    }

    private static ManaPublicState state(int current, int maximum) {
        return new ManaPublicState(
                current, maximum, 2.4D, 40L, 0L,
                false, false, false, true
        );
    }
}
