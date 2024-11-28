package skelen.easyevent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

public final class EasyEvent extends JavaPlugin implements Listener {

    private Team aliveTeam;
    private Team deadTeam;
    private Team opTeam;
    private Objective eventObjective;
    private boolean leaderboardEnabled = false;
    private String reward = "";

    @Override
    public void onEnable() {
        createTeams();
        getServer().getPluginManager().registerEvents(this, this);

        PluginCommand eventCommand = getCommand("event");
        if (eventCommand != null) {
            eventCommand.setTabCompleter(new EventTabCompleter());
        }

        loadConfig();
        loadTeamStateFromConfig();

        initializeTeamsForOnlinePlayers();
    }

    @Override
    public void onDisable() {
        saveTeamStateToConfig();
        clearTeams();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("event")) {
            if (args.length == 0) {
                showHelp(sender);
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "start":
                    startEvent();
                    return true;
                case "lb":
                    if (args.length > 1) {
                        if (args[1].equalsIgnoreCase("on")) {
                            enableLeaderboard();
                            return true;
                        } else if (args[1].equalsIgnoreCase("off")) {
                            disableLeaderboard();
                            return true;
                        }
                    }
                    break;
                case "vyhra":
                    if (args.length > 1) {
                        setReward(String.join(" ", args).substring(args[0].length() + 1));
                        return true;
                    }
                    break;
                case "end":
                    if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                        endEvent();
                    } else {
                        sender.sendMessage(ChatColor.RED + "Pokud chcete ukončit event, napište /event end confirm.");
                    }
                    return true;
                case "revive":
                    if (args.length > 1) {
                        revivePlayer(args[1], sender);
                        return true;
                    }
                    break;
                case "tp":
                    if (args.length > 1) {
                        if (args[1].equalsIgnoreCase("alive")) {
                            teleportTeam(sender, aliveTeam);
                            return true;
                        } else if (args[1].equalsIgnoreCase("dead")) {
                            teleportTeam(sender, deadTeam);
                            return true;
                        }
                    }
                    break;
            }
        }
        return false;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "Event Commands:");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/event start - Event začne");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/event lb on - Zapne to scoreboard");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/event lb off - Vypne to scoreboard");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/event vyhra <vyhra> - Nastaví to výhru na event");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/event end - Ukončí to event");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/event revive <player> - Oživí to hráče");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/event tp alive - Teleportuje všechny hráče naživu k tobě");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/event tp dead - Teleportuje všechny mrtvé hráče k tobě");
    }

    private void startEvent() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOp()) {
                aliveTeam.addEntry(player.getName());
                player.sendMessage(ChatColor.GREEN + "Jsi připojen k eventu!");
            } else {
                opTeam.addEntry(player.getName());
            }
        }
        updateLeaderboard();
        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "EVENT ZAPOČAL!");
    }

    private void enableLeaderboard() {
        if (eventObjective == null) {
            eventObjective = Bukkit.getScoreboardManager().getMainScoreboard().registerNewObjective("event", "dummy", ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "RILY EVENT");
            eventObjective.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
        }
        updateLeaderboard();
        leaderboardEnabled = true;
    }

    private void disableLeaderboard() {
        if (eventObjective != null) {
            eventObjective.unregister();
            eventObjective = null;
        }
        leaderboardEnabled = false;
    }

    private void setReward(String reward) {
        this.reward = reward;
        if (leaderboardEnabled) {
            updateLeaderboard();
        }
    }

    private void endEvent() {
        if (aliveTeam.getEntries().size() == 1) {
            Player winner = Bukkit.getPlayer(aliveTeam.getEntries().iterator().next());
            if (winner != null) {
                announceWinner(winner);
            }
        } else {
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "Event se ukončil a vyresetoval.");
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOp()) {
                aliveTeam.removeEntry(player.getName());
                deadTeam.addEntry(player.getName());
            }
        }

        setReward("");
        updateLeaderboard();

        clearTeams();
    }

    private void revivePlayer(String playerName, CommandSender sender) {
        Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            if (deadTeam.hasEntry(player.getName())) {
                deadTeam.removeEntry(player.getName());
            }
            aliveTeam.addEntry(player.getName());
            player.sendMessage(ChatColor.GREEN + "Byl si oživen!");
            player.teleport(((Player) sender).getLocation());
            if (leaderboardEnabled) {
                updateLeaderboard();
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Hráč nebyl nalezen.");
        }
    }

    private void teleportTeam(CommandSender sender, Team team) {
        if (sender instanceof Player) {
            Player admin = (Player) sender;
            for (String playerName : team.getEntries()) {
                Player player = Bukkit.getPlayer(playerName);
                if (player != null) {
                    player.teleport(admin.getLocation());
                }
            }
            admin.sendMessage(ChatColor.GREEN + "Teleportuji všechny " + team.getName() + " hráče k tobě.");
        } else {
            sender.sendMessage(ChatColor.RED + "Pouze hráči mohou použít tento příkaz.");
        }
    }

    private void createTeams() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        aliveTeam = scoreboard.getTeam("alive");
        if (aliveTeam == null) {
            aliveTeam = scoreboard.registerNewTeam("alive");
        }
        aliveTeam.setColor(ChatColor.GREEN);

        deadTeam = scoreboard.getTeam("dead");
        if (deadTeam == null) {
            deadTeam = scoreboard.registerNewTeam("dead");
        }
        deadTeam.setColor(ChatColor.GRAY);

        opTeam = scoreboard.getTeam("op");
        if (opTeam == null) {
            opTeam = scoreboard.registerNewTeam("op");
        }
        opTeam.setColor(ChatColor.RED);
    }

    private void clearTeams() {
        if (aliveTeam != null) {
            aliveTeam.unregister();
        }
        if (deadTeam != null) {
            deadTeam.unregister();
        }
        if (opTeam != null) {
            opTeam.unregister();
        }
    }

    private void updateLeaderboard() {
        if (eventObjective != null) {
            Scoreboard scoreboard = eventObjective.getScoreboard();
            for (String entry : scoreboard.getEntries()) {
                scoreboard.resetScores(entry);
            }

            eventObjective.getScore(" ").setScore(6);
            eventObjective.getScore(ChatColor.GRAY + "Hráči naživu: " + ChatColor.WHITE + aliveTeam.getSize()).setScore(5);
            eventObjective.getScore("  ").setScore(4);
            eventObjective.getScore(ChatColor.GRAY + "Výhra: " + ChatColor.WHITE + reward).setScore(3);
            eventObjective.getScore("   ").setScore(2);
            eventObjective.getScore(ChatColor.AQUA + "mc.rilyevent.eu").setScore(1);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) {
            opTeam.addEntry(player.getName());
        } else if (aliveTeam.hasEntry(player.getName())) {
            aliveTeam.addEntry(player.getName());
        } else {
            deadTeam.addEntry(player.getName());
        }
        if (leaderboardEnabled) {
            updateLeaderboard();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        aliveTeam.removeEntry(player.getName());
        deadTeam.removeEntry(player.getName());
        opTeam.removeEntry(player.getName());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (aliveTeam.hasEntry(player.getName())) {
            aliveTeam.removeEntry(player.getName());
            deadTeam.addEntry(player.getName());
            if (leaderboardEnabled) {
                updateLeaderboard();
            }

            if (aliveTeam.getSize() == 1) {
                Player winner = Bukkit.getPlayer(aliveTeam.getEntries().iterator().next());
                if (winner != null) {
                    announceWinner(winner);
                }
            }
        }
    }


    private void announceWinner(Player winner) {
        String winnerName = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + winner.getName();
        String titleMessage = winnerName + ChatColor.YELLOW + " Vyhrál EVENT!";
        String subtitleMessage = ChatColor.GREEN + "Výhra: " + reward;

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(titleMessage, subtitleMessage, 10, 70, 20);
        }

        Bukkit.broadcastMessage(winnerName + ChatColor.YELLOW + " Vyhrál EVENT!");
        if (!reward.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "Výhra: " + reward);
        }

        Bukkit.getServer().getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), "entity.ender_dragon.death", 1, 1));
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (aliveTeam.hasEntry(player.getName())) {
            event.setFormat(ChatColor.GREEN + player.getName() + ChatColor.WHITE + ": " + event.getMessage());
        } else if (deadTeam.hasEntry(player.getName())) {
            event.setFormat(ChatColor.GRAY + player.getName() + ChatColor.WHITE + ": " + event.getMessage());
        } else if (opTeam.hasEntry(player.getName())) {
            event.setFormat(ChatColor.RED + player.getName() + ChatColor.WHITE + ": " + event.getMessage());
        } else {
            event.setFormat(ChatColor.WHITE + player.getName() + ": " + event.getMessage());
        }
    }

    private void initializeTeamsForOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                opTeam.addEntry(player.getName());
            } else if (aliveTeam.hasEntry(player.getName())) {
                aliveTeam.addEntry(player.getName());
            } else {
                deadTeam.addEntry(player.getName());
            }
        }
    }

    private void loadConfig() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
    }

    private void loadTeamStateFromConfig() {
        FileConfiguration config = getConfig();
        List<String> alivePlayers = config.getStringList("teams.alive");
        List<String> deadPlayers = config.getStringList("teams.dead");

        for (String playerName : alivePlayers) {
            aliveTeam.addEntry(playerName);
        }
        for (String playerName : deadPlayers) {
            deadTeam.addEntry(playerName);
        }
        getLogger().info("Loaded team state from config: Alive - " + alivePlayers + ", Dead - " + deadPlayers);
    }

    private void saveTeamStateToConfig() {
        FileConfiguration config = getConfig();
        List<String> aliveEntries = new ArrayList<>(aliveTeam.getEntries());
        List<String> deadEntries = new ArrayList<>(deadTeam.getEntries());

        config.set("teams.alive", aliveEntries);
        config.set("teams.dead", deadEntries);
        saveConfig();

        getLogger().info("Saved team state to config: Alive - " + aliveEntries + ", Dead - " + deadEntries);
    }
}
