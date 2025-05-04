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
import java.util.List;

public class DPICommand extends CommandBase {
    @Override
    public String getName() {
        return "dpi";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/dpi [set_token <token>|set_userid <userId>|enable|disable|status|reload|reload_config|test <username> <amount> [message]]";
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
                case "set_token":
                    if (args.length < 2) throw new CommandException("Использование: /dpi set_token <token>");
                    String token = args[1];
                    if (token.length() < 10) throw new CommandException("Токен слишком короткий");
                    config.setDonpayToken(token);
                    ConfigHandler.save();
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Токен DonatePay обновлён"));
                    DonateIntegrate.LOGGER.info("Токен обновлён пользователем {}", sender.getName());
                    DonateIntegrate.startDonationProvider();
                    break;

                case "set_userid":
                    if (args.length < 2) throw new CommandException("Использование: /dpi set_userid <userId>");
                    String userId = args[1];
                    if (!userId.matches("\\d+")) throw new CommandException("User ID должен быть числовым");
                    config.setUserId(userId);
                    ConfigHandler.save();
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "User ID установлен: " + userId));
                    DonateIntegrate.LOGGER.info("User ID установлен на {} пользователем {}", userId, sender.getName());
                    DonateIntegrate.startDonationProvider();
                    break;

                case "enable":
                    config.setEnabled(true);
                    ConfigHandler.save();
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "DonateIntegrate включён"));
                    DonateIntegrate.startDonationProvider();
                    break;

                case "disable":
                    config.setEnabled(false);
                    ConfigHandler.save();
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "DonateIntegrate отключён"));
                    DonateIntegrate.stopDonationProvider();
                    break;

                case "status":
                    showStatus(sender, config);
                    break;

                case "reload":
                    DonateIntegrate.startDonationProvider();
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Провайдер донатов перезагружен"));
                    break;

                case "reload_config":
                    ConfigHandler.load();
                    sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Конфигурация перезагружена"));
                    DonateIntegrate.LOGGER.info("Конфигурация перезагружена пользователем {}", sender.getName());
                    break;

                case "test":
                    if (args.length < 3) throw new CommandException("Использование: /dpi test <username> <amount> [message]");
                    String username = args[1];
                    float amount;
                    try {
                        amount = Float.parseFloat(args[2]);
                    } catch (NumberFormatException e) {
                        throw new CommandException("Сумма должна быть числом");
                    }
                    if (amount <= 0) throw new CommandException("Сумма должна быть положительной");
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
            DonateIntegrate.LOGGER.error("Ошибка выполнения команды /dpi {}: {}", subCommand, e.getMessage());
            throw new CommandException("Внутренняя ошибка: " + e.getMessage());
        }
    }

    private void showHelp(ICommandSender sender) {
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "=== Команды DonateIntegrate ==="));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi set_token <token> " + TextFormatting.GRAY + "- Установить токен DonatePay"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi set_userid <userId> " + TextFormatting.GRAY + "- Установить User ID"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi enable " + TextFormatting.GRAY + "- Включить обработку донатов"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi disable " + TextFormatting.GRAY + "- Отключить обработку донатов"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi status " + TextFormatting.GRAY + "- Показать статус конфигурации"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi reload " + TextFormatting.GRAY + "- Перезагрузить подключение"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi reload_config " + TextFormatting.GRAY + "- Перезагрузить конфигурацию"));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "/dpi test <username> <amount> [message] " + TextFormatting.GRAY + "- Тестировать донат"));
    }

    private void showStatus(ICommandSender sender, ModConfig config) {
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "=== Статус DonateIntegrate ==="));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "Включено: " +
                (config.isEnabled() ? TextFormatting.GREEN + "Да" : TextFormatting.RED + "Нет")));
        String token = config.getDonpayToken();
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "Токен: " +
                (token.isEmpty() ? TextFormatting.RED + "Не установлен" :
                        TextFormatting.GREEN + "Установлен (" + maskToken(token) + ")")));
        String userId = config.getUserId();
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "User ID: " +
                (userId.isEmpty() ? TextFormatting.RED + "Не установлен" : TextFormatting.GREEN + userId)));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "Последний донат ID: " + TextFormatting.AQUA + config.getLastDonate()));
        sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "Действия: " + TextFormatting.AQUA + config.getActions().size()));
        for (com.bogdan3000.dintegrate.config.Action action : config.getActions()) {
            sender.sendMessage(new TextComponentString(TextFormatting.WHITE + "- Сумма: " + TextFormatting.AQUA + action.getSum() +
                    ", Включено: " + (action.isEnabled() ? TextFormatting.GREEN + "Да" : TextFormatting.RED + "Нет") +
                    ", Приоритет: " + TextFormatting.AQUA + action.getPriority() +
                    ", Режим: " + TextFormatting.AQUA + action.getExecutionMode() +
                    ", Команды: " + TextFormatting.AQUA + action.getCommands().size()));
        }
    }

    private String maskToken(String token) {
        if (token.isEmpty()) return "<пусто>";
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
                    sender.sendMessage(new TextComponentString(TextFormatting.RED + "Нет команд для суммы: " + amount));
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

                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Тестовый донат: " +
                        TextFormatting.YELLOW + username + TextFormatting.GREEN + " пожертвовал " +
                        TextFormatting.GOLD + amount + ", сообщение: " + TextFormatting.AQUA + message +
                        ", добавлено " + commandsToExecute.size() + " команд"));
                actionFound = true;
                break;
            }
        }

        if (!actionFound) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Нет активных действий для суммы: " + amount));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4; // Только для операторов
    }
}