package com.xysov.novacontracts.commands;

import com.xysov.novacontracts.NovaContracts;
import com.xysov.novacontracts.utils.ConfigLoader;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

public class ContractCommand implements CommandExecutor {

    private final NovaContracts plugin;

    public ContractCommand(NovaContracts plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        FileConfiguration messages = ConfigLoader.getMessagesConfig();

        if (args.length == 0) {
            sender.sendMessage(colorMsg("§eUse §b/contract accept <tier>, /contract view, /contract cancel"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(colorMsg(messages.getString("only_players")));
            return true;
        }

        Player player = (Player) sender;
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "accept":
                if (args.length < 2) {
                    player.sendMessage(colorMsg(messages.getString("usage_accept")));
                    return true;
                }
                String tier = args[1].toLowerCase();
                plugin.getContractManager().acceptContract(player, tier);
                break;

            case "view":
                plugin.getContractManager().viewContract(player);
                break;

            case "cancel":
                plugin.getContractManager().cancelContract(player);
                break;

            case "reload":
                if (!player.hasPermission("novacontracts.reload")) {
                    player.sendMessage(colorMsg(messages.getString("no_permission")));
                    return true;
                }
                ConfigLoader.reloadConfigs();
                player.sendMessage(colorMsg("§aNovaContracts configuration reloaded."));
                plugin.getLogger().info("[Contract] Configuration reloaded by " + player.getName());
                break;

            case "resetcooldown":
                if (!player.hasPermission("novacontracts.resetcooldown")) {
                    player.sendMessage(colorMsg(messages.getString("no_permission")));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(colorMsg(messages.getString("usage_resetcd")));
                    return true;
                }
                plugin.getContractManager().resetCooldown(args[1]);
                player.sendMessage(colorMsg(messages.getString("cooldown_reset").replace("%player%", args[1])));
                break;

            default:
                player.sendMessage(colorMsg(messages.getString("unknown_subcommand")));
        }

        return true;
    }

    private String colorMsg(String msg) {
        if (msg == null) return "";
        return msg.replace('&', '§');
    }
}
