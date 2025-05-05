package com.bogdan3000.dintegrate.command;

import com.bogdan3000.dintegrate.DonateIntegrate;
import com.bogdan3000.dintegrate.NetworkHandler;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.bogdan3000.dintegrate.config.ModConfig;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;

public class DPICommand extends CommandBase {
    @Override
    public String getName() {
        return "dpi";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return I18n.format("dintegrate.command.usage");
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();
        ModConfig config = ConfigHandler.getConfig();

        try {
            switch (subCommand) {
                case "gui":
                    if (!(sender instanceof EntityPlayerMP)) {
                        throw new CommandException(I18n.format("dintegrate.command.error.player_only"));
                    }
                    EntityPlayerMP player = (EntityPlayerMP) sender;
                    NetworkHandler.INSTANCE.sendTo(new NetworkHandler.OpenGuiMessage(), player);
                    sender.sendMessage(new TextComponentTranslation("dintegrate.command.gui_open").setStyle(new Style().setColor(TextFormatting.GREEN)));
                    break;
                case "set_token":
                    if (args.length < 2) throw new CommandException(I18n.format("dintegrate.command.error.token_usage"));
                    String token = args[1];
                    if (token.length() < 10) throw new CommandException(I18n.format("dintegrate.command.error.token_short"));
                    config.setDonpayToken(token);
                    ConfigHandler.save();
                    sender.sendMessage(new TextComponentTranslation("dintegrate.command.token_updated").setStyle(new Style().setColor(TextFormatting.GREEN)));
                    DonateIntegrate.LOGGER.info("Token updated by {}", sender.getName());
                    DonateIntegrate.startDonationProvider();
                    break;
                case "set_userid":
                    if (args.length < 2) throw new CommandException(I18n.format("dintegrate.command.error.userid_usage"));
                    String userId = args[1];
                    if (!userId.matches("\\d+")) throw new CommandException(I18n.format("dintegrate.command.error.userid_numeric"));
                    config.setUserId(userId);
                    ConfigHandler.save();
                    sender.sendMessage(new TextComponentTranslation("dintegrate.command.userid_set", userId).setStyle(new Style().setColor(TextFormatting.GREEN)));
                    DonateIntegrate.LOGGER.info("User ID set to {} by {}", userId, sender.getName());
                    DonateIntegrate.startDonationProvider();
                    break;
                case "enable":
                    config.setEnabled(true);
                    ConfigHandler.save();
                    sender.sendMessage(new TextComponentTranslation("dintegrate.command.enabled").setStyle(new Style().setColor(TextFormatting.GREEN)));
                    DonateIntegrate.startDonationProvider();
                    break;
                case "disable":
                    config.setEnabled(false);
                    ConfigHandler.save();
                    sender.sendMessage(new TextComponentTranslation("dintegrate.command.disabled").setStyle(new Style().setColor(TextFormatting.GREEN)));
                    DonateIntegrate.stopDonationProvider();
                    break;
                case "status":
                    showStatus(sender, config);
                    break;
                case "reload":
                    DonateIntegrate.startDonationProvider();
                    sender.sendMessage(new TextComponentTranslation("dintegrate.command.reloaded").setStyle(new Style().setColor(TextFormatting.GREEN)));
                    break;
                case "reload_config":
                    ConfigHandler.load();
                    sender.sendMessage(new TextComponentTranslation("dintegrate.command.config_reloaded").setStyle(new Style().setColor(TextFormatting.GREEN)));
                    DonateIntegrate.LOGGER.info("Configuration reloaded by {}", sender.getName());
                    break;
                case "test":
                    if (args.length < 3) throw new CommandException(I18n.format("dintegrate.command.error.test_usage"));
                    String username = args[1];
                    float amount;
                    try {
                        amount = Float.parseFloat(args[2]);
                    } catch (NumberFormatException e) {
                        throw new CommandException(I18n.format("dintegrate.command.error.amount_numeric"));
                    }
                    if (amount <= 0) throw new CommandException(I18n.format("dintegrate.command.error.amount_positive"));
                    String message = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "";
                    testDonation(sender, username, amount, message);
                    break;
                default:
                    showHelp(sender);
                    break;
            }
        } catch (CommandException e) {
            throw e;
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Error executing /dpi {}: {}", subCommand, e.getMessage());
            throw new CommandException(I18n.format("dintegrate.command.error.internal", e.getMessage()));
        }
    }

    private void showHelp(ICommandSender sender) {
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.help.title").setStyle(new Style().setColor(TextFormatting.YELLOW)));
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.help.gui"));
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.help.set_token"));
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.help.set_userid"));
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.help.enable"));
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.help.disable"));
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.help.status"));
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.help.reload"));
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.help.reload_config"));
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.help.test"));
    }

    private void showStatus(ICommandSender sender, ModConfig config) {
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.status.title").setStyle(new Style().setColor(TextFormatting.YELLOW)));
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.status.enabled",
                config.isEnabled() ? I18n.format("dintegrate.gui.value.yes") : I18n.format("dintegrate.gui.value.no"))
                .setStyle(new Style().setColor(config.isEnabled() ? TextFormatting.GREEN : TextFormatting.RED)));
        String token = config.getDonpayToken();
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.status.token",
                token.isEmpty() ? I18n.format("dintegrate.gui.value.not_set") : I18n.format("dintegrate.gui.value.set", maskToken(token)))
                .setStyle(new Style().setColor(token.isEmpty() ? TextFormatting.RED : TextFormatting.GREEN)));
        String userId = config.getUserId();
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.status.user_id",
                userId.isEmpty() ? I18n.format("dintegrate.gui.value.not_set") : userId)
                .setStyle(new Style().setColor(userId.isEmpty() ? TextFormatting.RED : TextFormatting.GREEN)));
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.status.last_donation", config.getLastDonate()));
        sender.sendMessage(new TextComponentTranslation("dintegrate.command.status.actions", config.getActions().size()));
        for (com.bogdan3000.dintegrate.config.Action action : config.getActions()) {
            sender.sendMessage(new TextComponentTranslation("dintegrate.command.status.action",
                    action.getSum(),
                    action.isEnabled() ? I18n.format("dintegrate.gui.value.yes") : I18n.format("dintegrate.gui.value.no"),
                    action.getPriority(),
                    I18n.format("dintegrate.gui.value.mode." + action.getExecutionMode().name().toLowerCase()),
                    action.getCommands().size())
                    .setStyle(new Style().setColor(TextFormatting.WHITE)));
        }
    }

    private String maskToken(String token) {
        if (token.isEmpty()) return I18n.format("dintegrate.gui.value.empty");
        if (token.length() <= 10) return token;
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    private void testDonation(ICommandSender sender, String username, float amount, String message) {
        ModConfig config = ConfigHandler.getConfig();
        boolean actionFound = false;
        java.util.Random random = new java.util.Random();

        for (com.bogdan3000.dintegrate.config.Action action : config.getActions()) {
            if (Math.abs(action.getSum() - amount) < 0.001 && action.isEnabled()) {
                List<String> commandsToExecute = new ArrayList<>();
                List<String> availableCommands = action.getCommands();
                if (availableCommands.isEmpty()) {
                    sender.sendMessage(new TextComponentTranslation("dintegrate.command.error.no_commands", amount)
                            .setStyle(new Style().setColor(TextFormatting.RED)));
                    return;
                }

                switch (action.getExecutionMode()) {
                    case ALL:
                        commandsToExecute.addAll(availableCommands);
                        break;
                    case RANDOM_ONE:
                        commandsToExecute.add(availableCommands.get(random.nextInt(availableCommands.size())));
                        break;
                }

                for (String cmd : commandsToExecute) {
                    String command = cmd.replace("{username}", username)
                            .replace("{message}", message)
                            .replace("{amount}", String.valueOf(amount));
                    DonateIntegrate.addCommand(new DonateIntegrate.CommandToExecute(command, username, action.getPriority()));
                }

                sender.sendMessage(new TextComponentTranslation("dintegrate.command.test_success",
                        username, amount, message, commandsToExecute.size())
                        .setStyle(new Style().setColor(TextFormatting.GREEN)));
                actionFound = true;
                break;
            }
        }

        if (!actionFound) {
            sender.sendMessage(new TextComponentTranslation("dintegrate.command.error.no_action", amount)
                    .setStyle(new Style().setColor(TextFormatting.RED)));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }
}