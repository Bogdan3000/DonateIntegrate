package com.bogdan3000.dintegrate.command;

import com.bogdan3000.dintegrate.DonateIntegrate;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.bogdan3000.dintegrate.config.ModConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DPICommand extends CommandBase {
    @Override
    public String getName() {
        return "dpi";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/dpi [set_token <token>|set_userid <userId>|enable|disable|status|reload|test <username> <amount> [message]]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();
        ModConfig config = ConfigHandler.getConfig();

        switch (subCommand) {
            case "set_token":
                if (args.length < 2) {
                    throw new CommandException("Usage: /dpi set_token <token>");
                }
                String token = args[1];
                if (token.length() < 10) {
                    throw new CommandException("Token too short");
                }
                config.setDonpayToken(token);
                ConfigHandler.save();
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "DonatePay token updated"));
                DonateIntegrate.LOGGER.info("Token updated by {}", sender.getName());
                DonateIntegrate.startWebSocketHandler();
                break;

            case "set_userid":
                if (args.length < 2) {
                    throw new CommandException("Usage: /dpi set_userid <userId>");
                }
                String userId = args[1];
                if (!userId.matches("\\d+")) {
                    throw new CommandException("User ID must be numeric");
                }
                config.setUserId(userId);
                ConfigHandler.save();
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "User ID set to " + userId));
                DonateIntegrate.LOGGER.info("User ID set to {} by {}", userId, sender.getName());
                DonateIntegrate.startWebSocketHandler();
                break;

            case "enable":
                config.setEnabled(true);
                ConfigHandler.save();
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "DonateIntegrate enabled"));
                DonateIntegrate.startWebSocketHandler();
                break;

            case "disable":
                config.setEnabled(false);
                ConfigHandler.save();
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "DonateIntegrate disabled"));
                DonateIntegrate.stopWebSocketHandler();
                break;

            case "status":
                showStatus(sender, config);
                break;

            case "reload":
                DonateIntegrate.startWebSocketHandler();
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "WebSocket handler reloaded"));
                break;

            case "test":
                if (args.length < 3) {
                    throw new CommandException("Usage: /dpi test <username> <amount> [message]");
                }
                String username = args[1];
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    throw new CommandException("Amount must be a number");
                }
                String message = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "";
                testDonation(sender, username, amount, message);
                break;

            default:
                showHelp(sender);
                break;
        }
    }

    private void showHelp(ICommandSender sender) {
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "=== DonateIntegrate Commands ==="));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi set_token <token> " + TextFormatting.GRAY + "- Set DonatePay API token"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi set_userid <userId> " + TextFormatting.GRAY + "- Set DonatePay user ID"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi enable " + TextFormatting.GRAY + "- Enable donation processing"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi disable " + TextFormatting.GRAY + "- Disable donation processing"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi status " + TextFormatting.GRAY + "- Show configuration status"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi reload " + TextFormatting.GRAY + "- Reload WebSocket connection"));
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

        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "Last donation ID: " + TextFormatting.AQUA + config.getLastDonate()));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "Configured actions: " + TextFormatting.AQUA + config.getActions().size()));
    }

    private String maskToken(String token) {
        if (token.isEmpty()) return "<empty>";
        if (token.length() <= 10) return token;
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    private void testDonation(ICommandSender sender, String username, int amount, String message) {
        ModConfig config = ConfigHandler.getConfig();
        boolean actionFound = false;
        Random random = new Random();

        for (com.bogdan3000.dintegrate.config.Action action : config.getActions()) {
            if (action.getSum() == amount) {
                List<String> commandsToExecute = new ArrayList<>();
                String title = action.getMessage()
                        .replace("{username}", username)
                        .replace("{message}", message);

                List<String> availableCommands = action.getCommands();
                switch (action.getExecutionMode()) {
                    case SEQUENTIAL:
                        commandsToExecute.addAll(availableCommands);
                        break;
                    case RANDOM_ONE:
                        if (!availableCommands.isEmpty()) {
                            commandsToExecute.add(availableCommands.get(random.nextInt(availableCommands.size())));
                        }
                        break;
                    case RANDOM_MULTIPLE:
                        if (!availableCommands.isEmpty()) {
                            int count = random.nextInt(availableCommands.size()) + 1;
                            List<String> shuffled = new ArrayList<>(availableCommands);
                            Collections.shuffle(shuffled, random);
                            commandsToExecute.addAll(shuffled.subList(0, Math.min(count, shuffled.size())));
                        }
                        break;
                    case ALL:
                        commandsToExecute.addAll(availableCommands);
                        break;
                }

                for (String cmd : commandsToExecute) {
                    String command = cmd.replace("{username}", username).replace("{message}", message);
                    DonateIntegrate.commands.add(new DonateIntegrate.CommandToExecute(command, username));
                }

                if (!title.isEmpty()) {
                    DonateIntegrate.commands.add(new DonateIntegrate.CommandToExecute("title @a title \"" + title + "\"", username));
                }

                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Test donation triggered: " +
                        TextFormatting.YELLOW + username + TextFormatting.GREEN + " donated " +
                        TextFormatting.GOLD + amount + ", executed " + commandsToExecute.size() + " commands"));
                actionFound = true;
                break;
            }
        }

        if (!actionFound) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "No action configured for amount: " + amount));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4; // Только для операторов
    }
}