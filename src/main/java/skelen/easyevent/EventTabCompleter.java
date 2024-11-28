package skelen.easyevent;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class EventTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("event")) {
            if (args.length == 1) {
                suggestions.add("start");
                suggestions.add("lb");
                suggestions.add("vyhra");
                suggestions.add("end");
                suggestions.add("revive");
                suggestions.add("tp");
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("lb")) {
                    suggestions.add("on");
                    suggestions.add("off");
                } else if (args[0].equalsIgnoreCase("revive") || args[0].equalsIgnoreCase("tp")) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            suggestions.add(player.getName());
                        }
                    }
                }
            }
        }
        return suggestions;
    }
}
