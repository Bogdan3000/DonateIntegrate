package com.bogdan3000.dintegrate.command;

import com.bogdan3000.dintegrate.DonateIntegrate;
import com.bogdan3000.dintegrate.NetworkHandler;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.bogdan3000.dintegrate.config.ModConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
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
        return "/dpi [gui|set_token <token>|set_userid <userId>|enable|disable|status|reload|reload_config|test <username> <amount> [message]]";
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
                        throw new CommandException("This command must be run by a player");
                    }
                    EntityPlayerMP player = (EntityPlayerMP) sender;
                    NetworkHandler.INSTANCE.sendTo(new NetworkHandler.OpenGuiMessage(), player);
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Opening DonateIntegrate GUI"));
                    break;
                case "set_token":
                    if (args.length < 2) throw new CommandException("Usage: /dpi set_token <token>");
                    String token = args[1];
                    if (token.length() < 10) throw new CommandException("Token is too short");
                    config.setDonpayToken(token);
                    ConfigHandler.save();
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "DonatePay token updated"));
                    DonateIntegrate.LOGGER.info("Token updated by {}", sender.getName());
                    DonateIntegrate.startDonationProvider();
                    break;
                case "set_userid":
                    if (args.length < 2) throw new CommandException("Usage: /dpi set_userid <userId>");
                    String userId = args[1];
                    if (!userId.matches("\\d+")) throw new CommandException("User ID must be numeric");
                    config.setUserId(userId);
                    ConfigHandler.save();
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "User ID set: " + userId));
                    DonateIntegrate.LOGGER.info("User ID set to {} by {}", userId, sender.getName());
                    DonateIntegrate.startDonationProvider();
                    break;
                case "enable":
                    config.setEnabled(true);
                    ConfigHandler.save();
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "DonateIntegrate enabled"));
                    DonateIntegrate.startDonationProvider();
                    break;
                case "disable":
                    config.setEnabled(false);
                    ConfigHandler.save();
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "DonateIntegrate disabled"));
                    DonateIntegrate.stopDonationProvider();
                    break;
                case "status":
                    showStatus(sender, config);
                    break;
                case "reload":
                    DonateIntegrate.startDonationProvider();
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Donation provider reloaded"));
                    break;
                case "reload_config":
                    ConfigHandler.load();
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Configuration reloaded"));
                    DonateIntegrate.LOGGER.info("Configuration reloaded by {}", sender.getName());
                    break;
                case "test":
                    if (args.length < 3) throw new CommandException("Usage: /dpi test <username> <amount> [message]");
                    String username = args[1];
                    float amount;
                    try {
                        amount = Float.parseFloat(args[2]);
                    } catch (NumberFormatException e) {
                        throw new CommandException("Amount must be a number");
                    }
                    if (amount <= 0) throw new CommandException("Amount must be positive");
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
            throw new CommandException("Internal error: " + e.getMessage());
        }
    }

    private void showHelp(ICommandSender sender) {
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "=== DonateIntegrate Commands ==="));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi gui " + TextFormatting.GRAY + "- Open configuration GUI"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi set_token <token> " + TextFormatting.GRAY + "- Set DonatePay token"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi set_userid <userId> " + TextFormatting.GRAY + "- Set User ID"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi enable " + TextFormatting.GRAY + "- Enable donation processing"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi disable " + TextFormatting.GRAY + "- Disable donation processing"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi status " + TextFormatting.GRAY + "- Show configuration status"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi reload " + TextFormatting.GRAY + "- Reload connection"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi reload_config " + TextFormatting.GRAY + "- Reload configuration"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi test <username> <amount> [message] " + TextFormatting.GRAY + "- Test a donation"));
    }

    private void showStatus(ICommandSender sender, ModConfig config) {
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "=== DonateIntegrate Status ==="));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "Enabled: " +
                (config.isEnabled() ? TextFormatting.GREEN + "Yes" : TextFormatting.RED + "No")));
        String token = config.getDonpayToken();
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "Token: " +
                (token.isEmpty() ? TextFormatting.RED + "Not set" :
                        TextFormatting.GREEN + "Set (" + maskToken(token) + ")")));
        String userId = config.getUserId();
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "User ID: " +
                (userId.isEmpty() ? TextFormatting.RED + "Not set" : TextFormatting.GREEN + userId)));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "Last Donation ID: " + TextFormatting.AQUA + config.getLastDonate()));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "Actions: " + TextFormatting.AQUA + config.getActions().size()));
        for (com.bogdan3000.dintegrate.config.Action action : config.getActions()) {
            sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "- Sum: " + TextFormatting.AQUA + action.getSum() +
                    ", Enabled: " + (action.isEnabled() ? TextFormatting.GREEN + "Yes" : TextFormatting.RED + "No") +
                    ", Priority: " + TextFormatting.AQUA + action.getPriority() +
                    ", Mode: " + TextFormatting.AQUA + action.getExecutionMode() +
                    ", Commands: " + TextFormatting.AQUA + action.getCommands().size()));
        }
    }

    private String maskToken(String token) {
        if (token.isEmpty()) return "<empty>";
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
                    sender.sendMessage(new TextComponentString(TextFormatting.RED + "No commands for sum: " + amount));
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

                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Test donation: " +
                        TextFormatting.YELLOW + username + TextFormatting.GREEN + " donated " +
                        TextFormatting.GOLD + amount + ", message: " + TextFormatting.AQUA + message +
                        ", added " + commandsToExecute.size() + " commands"));
                actionFound = true;
                break;
            }
        }

        if (!actionFound) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "No active actions for sum: " + amount));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4; // Only for operators
    }
}