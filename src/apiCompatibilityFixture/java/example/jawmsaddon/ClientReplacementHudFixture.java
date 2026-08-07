package example.jawmsaddon;

import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.api.client.ManaHudRenderEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** A deliberately conspicuous replacement HUD implemented only through public API. */
public final class ClientReplacementHudFixture extends CommandBase {

    static final ClientReplacementHudFixture INSTANCE = new ClientReplacementHudFixture();

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 7;
    private static volatile boolean replacementEnabled;

    private ClientReplacementHudFixture() {
    }

    @Override
    public String getName() {
        return "jawms-api-hud";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/jawms-api-hud replacement <on|off>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] arguments)
            throws CommandException {
        if (arguments.length != 2 || !"replacement".equalsIgnoreCase(arguments[0])) {
            throw new CommandException(getUsage(sender));
        }
        if ("on".equalsIgnoreCase(arguments[1])) {
            replacementEnabled = true;
        } else if ("off".equalsIgnoreCase(arguments[1])) {
            replacementEnabled = false;
        } else {
            throw new CommandException(getUsage(sender));
        }
        sender.sendMessage(new TextComponentString(
                "JAWMS API replacement HUD " + (replacementEnabled ? "enabled." : "disabled.")
        ));
    }

    @SubscribeEvent
    public void renderReplacement(ManaHudRenderEvent event) {
        if (!replacementEnabled) {
            return;
        }

        // This call is the launched-runtime proof that Forge transformed the event.
        event.setCanceled(true);

        ClientManaSnapshot snapshot = event.getSnapshot();
        int left = 4;
        int top = event.getScaledHeight() - 58;
        int fill = (int) Math.round(
                (BAR_WIDTH - 2) * snapshot.getCurrentMana()
                        / (double) snapshot.getMaximumMana()
        );
        Gui.drawRect(left, top, left + BAR_WIDTH, top + BAR_HEIGHT, 0xCC210028);
        if (fill > 0) {
            Gui.drawRect(left + 1, top + 1, left + 1 + fill,
                    top + BAR_HEIGHT - 1, 0xFFEA43FF);
        }
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
                "API MANA " + snapshot.getCurrentMana() + "/" + snapshot.getMaximumMana(),
                left,
                top - 10,
                0xFFFF66FF
        );
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
