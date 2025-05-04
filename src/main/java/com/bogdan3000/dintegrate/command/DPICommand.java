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

public class DPICommand extends CommandBase {
    @Override
    public String getName() {
        return "dpi";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/dpi [set_token <token>|set_userid <userId>|status|reload|test <username> <amount>]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();
        ModConfig config = ConfigHandler.load();

        switch (subCommand) {
            case "set_token":
                if (args.length < 2) {
                    throw new CommandException("Usage: /dpi set_token <token>");
                }
                String token = args[1];
                config.setDonpayToken(token);
                ConfigHandler.save(config);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "DonatePay token set!"));
                DonateIntegrate.LOGGER.info("DonatePay token updated by {}", sender.getName());

                // Restart the WebSocket client to use the new token
                DonateIntegrate.startWebSocketClient();
                break;

            case "set_userid":
                if (args.length < 2) {
                    throw new CommandException("Usage: /dpi set_userid <userId>");
                }
                String userId = args[1];
                config.setUserId(userId);
                ConfigHandler.save(config);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "User ID set to " + userId));
                DonateIntegrate.LOGGER.info("User ID updated to {} by {}", userId, sender.getName());

                // Restart the WebSocket client to use the new user ID
                DonateIntegrate.startWebSocketClient();
                break;

            case "status":
                showStatus(sender, config);
                break;

            case "reload":
                DonateIntegrate.startWebSocketClient();
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "WebSocket client restarted!"));
                break;

            case "test":
                if (args.length < 3) {
                    throw new CommandException("Usage: /dpi test <username> <amount>");
                }
                String username = args[1];
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    throw new CommandException("Amount must be a number");
                }

                testDonation(sender, username, amount);
                break;

            default:
                showHelp(sender);
                break;
        }
    }

    private void showHelp(ICommandSender sender) {
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "=== DonateIntegrate Commands ==="));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi set_token <token> " + TextFormatting.GRAY + "- Set your DonatePay API token"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi set_userid <userId> " + TextFormatting.GRAY + "- Set your DonatePay user ID"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi status " + TextFormatting.GRAY + "- Show current configuration status"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi reload " + TextFormatting.GRAY + "- Restart the WebSocket connection"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi test <username> <amount> " + TextFormatting.GRAY + "- Test a donation"));
    }

    private void showStatus(ICommandSender sender, ModConfig config) {
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "=== DonateIntegrate Status ==="));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "Enabled: " +
                (config.isEnabled() ? TextFormatting.GREEN + "Yes" : TextFormatting.RED + "No")));

        String token = config.getDonpayToken();
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "Token: " +
                (token.isEmpty() ? TextFormatting.RED + "Not set" :
                        TextFormatting.GREEN + "Set (" + token.substring(0, 4) + "..." + token.substring(token.length() - 4) + ")")));

        String userId = config.getUserId();
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "User ID: " +
                (userId.isEmpty() ? TextFormatting.RED + "Not set" : TextFormatting.GREEN + userId)));

        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "Last donation ID: " + TextFormatting.AQUA + config.getLastDonate()));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "Configured actions: " + TextFormatting.AQUA + config.getActions().size()));
    }

    private void testDonation(ICommandSender sender, String username, int amount) {
        ModConfig config = ConfigHandler.load();
        boolean actionFound = false;

        for (com.bogdan3000.dintegrate.config.Action action : config.getActions()) {
            if (action.getSum() == amount) {
                String command = action.getCommand().replace("{username}", username);
                String title = action.getMessage().replace("{username}", username);

                DonateIntegrate.commands.add("title @a title \"" + title + "\"");
                DonateIntegrate.commands.add(command);

                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Test donation triggered! " +
                        TextFormatting.YELLOW + username + TextFormatting.GREEN + " donated " +
                        TextFormatting.GOLD + amount));
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