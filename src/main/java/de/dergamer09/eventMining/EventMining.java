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
        Objects.requireNonNull(getCommand("event")).setExecutor(this);
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
            return String.join(", ", plugin.getDescription().getAuthors());
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
}
