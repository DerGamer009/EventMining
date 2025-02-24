package de.dergamer09.eventMining;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public final class EventMining extends JavaPlugin implements Listener, CommandExecutor {

    private boolean eventActive = false;
    private final Map<UUID, Integer> playerPoints = new HashMap<>();
    private final Random random = new Random();
    private Connection connection;
    private Location pos1;
    private Location pos2;
    private BossBar bossBar;
    private final long eventDuration = 7 * 24 * 60 * 60 * 1000; // 7 Tage in Millisekunden
    private long eventEndTime;

    private final Map<Material, Integer> blockPoints = Map.of(
            Material.STONE, 0,
            Material.COAL_ORE, 1,
            Material.IRON_ORE, 2,
            Material.GOLD_ORE, 3,
            Material.LAPIS_ORE, 4,
            Material.REDSTONE_ORE, 4,
            Material.DIAMOND_ORE, 6,
            Material.ANCIENT_DEBRIS, 10
    );

    private final List<Material> weightedBlocks = Arrays.asList(
            Material.STONE, Material.STONE, Material.STONE, Material.STONE, Material.STONE, // Sehr häufig
            Material.COAL_ORE, Material.COAL_ORE, Material.COAL_ORE, // Häufig
            Material.IRON_ORE, Material.IRON_ORE, Material.GOLD_ORE, // Weniger häufig
            Material.LAPIS_ORE, Material.REDSTONE_ORE, // Seltener
            Material.DIAMOND_ORE, // Sehr selten
            Material.ANCIENT_DEBRIS // Extrem selten
    );

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("event").setExecutor(this);
        createDatabaseFolder();
        connectDatabase();
        setupBossBar();
        new MiningMadnessPlaceholder(this).register();
    }

    private void createDatabaseFolder() {
        File databaseFolder = new File(getDataFolder(), "MiningMadness");
        if (!databaseFolder.exists()) {
            databaseFolder.mkdirs();
        }
    }

    private void connectDatabase() {
        try {
            File dbFile = new File(getDataFolder(), "MiningMadness/points.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS player_points (uuid TEXT PRIMARY KEY, points INTEGER)");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupBossBar() {
        bossBar = Bukkit.createBossBar("§eMining Event - Läuft jetzt!", BarColor.BLUE, BarStyle.SOLID);
        bossBar.setVisible(false);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!eventActive) return;
        Player player = event.getPlayer();
        Material block = event.getBlock().getType();

        int points = blockPoints.getOrDefault(block, 0);
        playerPoints.put(player.getUniqueId(), playerPoints.getOrDefault(player.getUniqueId(), 0) + points);
        savePlayerPoints(player.getUniqueId(), playerPoints.get(player.getUniqueId()));
    }

    private void savePlayerPoints(UUID uuid, int points) {
        try {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO player_points (uuid, points) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET points = ?");
            statement.setString(1, uuid.toString());
            statement.setInt(2, points);
            statement.setInt(3, points);
            statement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class MiningMadnessPlaceholder extends PlaceholderExpansion {
        private final EventMining plugin;

        public MiningMadnessPlaceholder(EventMining plugin) {
            this.plugin = plugin;
        }

        @Override
        public String getIdentifier() {
            return "event";
        }

        @Override
        public String getAuthor() {
            return plugin.getDescription().getAuthors().toString();
        }

        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean canRegister() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, String identifier) {
            if (player == null) {
                return "0";
            }

            if (identifier.equals("points")) {
                return String.valueOf(playerPoints.getOrDefault(player.getUniqueId(), 0));
            }
            return null;
        }
    }

    private Material getRandomWeightedBlock() {
        return weightedBlocks.get(random.nextInt(weightedBlocks.size()));
    }

    private Material getRandomOre() {
        return getRandomWeightedBlock();
    }

    private void generateRandomMine() {
        Bukkit.broadcastMessage("§eDie zufällige Mine wird generiert...");
        if (pos1 == null || pos2 == null) return;
        World world = pos1.getWorld();
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int x = Math.min(pos1.getBlockX(), pos2.getBlockX()); x <= Math.max(pos1.getBlockX(), pos2.getBlockX()); x++) {
                    for (int y = Math.min(pos1.getBlockY(), pos2.getBlockY()); y <= Math.max(pos1.getBlockY(), pos2.getBlockY()); y++) {
                        for (int z = Math.min(pos1.getBlockZ(), pos2.getBlockZ()); z <= Math.max(pos1.getBlockZ(), pos2.getBlockZ()); z++) {
                            Location blockLoc = new Location(world, x, y, z);
                            world.getBlockAt(blockLoc).setType(getRandomOre());
                        }
                    }
                }
                Bukkit.broadcastMessage("§eDie Mining-Zone wurde zufällig generiert!");
            }
        }.runTask(this);
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;

        if (label.equalsIgnoreCase("event") && args.length == 1) {
            if (args[0].equalsIgnoreCase("pos1")) {
                pos1 = player.getLocation();
                player.sendMessage("§aPosition 1 für das Event gesetzt!");
                return true;
            }
            if (args[0].equalsIgnoreCase("pos2")) {
                pos2 = player.getLocation();
                player.sendMessage("§aPosition 2 für das Event gesetzt!");
                return true;
            }
            if (args[0].equalsIgnoreCase("set")) {
                if (pos1 == null || pos2 == null) {
                    player.sendMessage("§cSetze zuerst Position 1 und 2 mit /event pos1 und /event pos2!");
                    return false;
                }
                player.sendMessage("§aDas Mining-Gebiet wurde gesetzt! Die Mine wird generiert...");
                eventActive = true;
                eventEndTime = System.currentTimeMillis() + eventDuration;
                bossBar.setVisible(true);
                bossBar.setTitle("§eMining Event - Läuft jetzt!");
                generateRandomMine();
                return true;
            }
            if (args[0].equalsIgnoreCase("top10")) {
                showTop10(player);
                return true;
            }
        }
        return false;
    }

    private void showTop10(Player player) {
        try {
            PreparedStatement statement = connection.prepareStatement("SELECT uuid, points FROM player_points ORDER BY points DESC LIMIT 10");
            ResultSet rs = statement.executeQuery();
            player.sendMessage("§e--- Top 10 Mining Madness Spieler ---");
            int rank = 1;
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                int points = rs.getInt("points");
                Player p = Bukkit.getPlayer(uuid);
                player.sendMessage("§bPlatz " + rank + ": " + (p != null ? p.getName() : uuid) + " - " + points + " Punkte");
                rank++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}