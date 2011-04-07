package de.diddiz.AnyReg;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import JDCBPool.JDCConnectionDriver;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.CuboidSelection;
import com.sk89q.worldedit.bukkit.selections.Selection;

import de.diddiz.AnyReg.MySQL.MySQLConsumer;
import de.diddiz.AnyReg.MySQL.MySQLRegTask;
import de.diddiz.AnyReg.SQLite.SQLiteConsumer;
import de.diddiz.AnyReg.SQLite.SQLiteRegTask;
import de.diddiz.util.Download;

public class AnyReg extends JavaPlugin
{
	public static Logger log;
	static Config config;
	private LinkedBlockingQueue<BlockState> regQueue = new LinkedBlockingQueue<BlockState>();
	private LinkedBlockingQueue<BlockState> blackQueue = new LinkedBlockingQueue<BlockState>();
	private List<Region> regions = new LinkedList<Region>();
	private List<Respawn> respawns = new LinkedList<Respawn>();
	private Set<Integer> respawningBlocks = new HashSet<Integer>();
	private Set<Integer> blacklistedBlocks = new HashSet<Integer>();

	@Override
	public void onEnable() {
		log = getServer().getLogger();
		config = new Config(getConfiguration(), getDataFolder());
		config.LoadConfig();
		File file = new File("lib/mysql-connector-java-bin.jar");
		try {
			if (!file.exists() || file.length() == 0) {
				log.info("[LogBlock] Downloading " + file.getName() + "...");
				Download.download(new URL("http://diddiz.insane-architects.net/download/mysql-connector-java-bin.jar"), file);
			}
			if (!file.exists() || file.length() == 0)
				throw new FileNotFoundException(file.getAbsolutePath() + file.getName());
			file = new File("lib/sqlitejdbc.jar");
			if (!file.exists() || file.length() == 0) {
				log.info("[LogBlock] Downloading " + file.getName() + "...");
				Download.download(new URL("http://diddiz.insane-architects.net/download/sqlitejdbc.jar"), file);
			}
			if (!file.exists() || file.length() == 0)
				throw new FileNotFoundException(file.getAbsolutePath() + file.getName());
		} catch (Exception e) {
			log.log(Level.SEVERE, "[AnyReg] Error while downloading " + file.getName() + ".");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		try {
			if (config.useMySQL)
				new JDCConnectionDriver(config.dbDriver, config.dbUrl, config.dbUsername, config.dbPassword);
			Connection conn = getConnection();
			conn.close();
		} catch (Exception ex) {
			log.log(Level.SEVERE, "[AnyReg] Can't get a connection to the database.", ex);
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		if (!checkTables()) {
			log.severe("[AnyReg] Error while checking tables. They may not exist and/or resist creation.");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		if (config.useMySQL && new File(getDataFolder(), "anyreg.db").exists()) {
			log.info("[AnyReg] Importing from SQLite ...");
			if (importFromSQLite())
				log.info("[AnyReg] Import successfull.");
			else
				log.severe("[AnyReg] SQLite import failed! Remove the anyreg.db manually to prevent corruption!");
		}
		if (!config.LoadRespawns(respawns)) {
			log.severe("[AnyReg] No respawns defined.");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		if (!config.LoadRegions(regions)) {
			log.severe("[AnyReg] No regions defined.");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		for (Respawn respawn : respawns) {
			respawningBlocks.add(respawn.getType());
			if (respawn.isUseBlacklist())
				blacklistedBlocks.add(respawn.getType());
			RegTask regtask;
			if (config.useMySQL)
				regtask = new MySQLRegTask(getServer(), respawn);
			else
				regtask = new SQLiteRegTask(getServer(), respawn);
			if (getServer().getScheduler().scheduleSyncRepeatingTask(this, regtask, respawn.getRegDelay()*20, respawn.getRegDelay()*20) == -1)
				log.severe("[AnyReg] Failed to schedule reg task for " + Material.getMaterial(respawn.getType()));
		}
		PluginManager pm = getServer().getPluginManager();
		AnyRegBlockListener blockListener = new AnyRegBlockListener();
		pm.registerEvent(Event.Type.BLOCK_BREAK, blockListener, Event.Priority.Monitor, this);
		pm.registerEvent(Event.Type.BLOCK_PLACE, blockListener, Event.Priority.Monitor, this);
		if (config.logFire)
			pm.registerEvent(Event.Type.BLOCK_BURN, blockListener, Event.Priority.Monitor, this);
		if (config.logExplosions)
			pm.registerEvent(Event.Type.ENTITY_EXPLODE, new AnyRegEntityListener(), Event.Priority.Monitor, this);
		Consumer consumer;
		if (config.useMySQL)
			consumer = new MySQLConsumer(regQueue, blackQueue);
		else
			consumer = new SQLiteConsumer(regQueue, blackQueue);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, consumer, config.delay*20, config.delay*20);
		log.info("AnyReg v" + getDescription().getVersion() + " by DiddiZ enabled");
	}

	@Override
	public void onDisable()
	{
		getServer().getScheduler().cancelTasks(this);
		log.info("AnyReg disabled");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)	{
		if (cmd.getName().equalsIgnoreCase("ar")) {
			if ((sender instanceof Player)) {
				Player player = (Player)sender;
				if (args.length == 0) {
					player.sendMessage(ChatColor.LIGHT_PURPLE + "AnyReg v" + getDescription().getVersion() + " by DiddiZ");
					player.sendMessage(ChatColor.LIGHT_PURPLE + "Type /ar help for help");
				} else if (args[0].equalsIgnoreCase("create")) {
					if (args.length == 2) {
						if (!regions.contains(new Region(args[1]))) {
							Plugin we = getServer().getPluginManager().getPlugin("WorldEdit");
							if (we != null) {
								Selection sel = ((WorldEditPlugin)we).getSelection(player);
								if (sel != null) {
									if (sel instanceof CuboidSelection)
										if (config.RegionAdd(regions, new Region(args[2], sel.getMinimumPoint(), sel.getMaximumPoint())))
											player.sendMessage(ChatColor.GREEN + "Created region '" + args[1] + "'");
										else
											player.sendMessage(ChatColor.RED + "Error while creating region '" + args[1] + "'");
									else
										player.sendMessage(ChatColor.RED + "You have to define a cuboid selection");
								} else
									player.sendMessage(ChatColor.RED + "No selection defined");
							} else
								player.sendMessage(ChatColor.RED + "WorldEdit plugin not found");
						} else
							player.sendMessage(ChatColor.RED + "There is alredy a region called '" + args[1] + "'");	
					} else 
						player.sendMessage(ChatColor.RED + "Usage: /ar region create [name]");
				} else if (args[0].equalsIgnoreCase("remove")) {
					if (args.length == 2) {
						if (regions.contains(new Region(args[1]))) {
							if (config.RegionRemove(regions, new Region(args[1])))
								player.sendMessage(ChatColor.GREEN + "Removed region '" + args[1] + "'");
							else
								player.sendMessage(ChatColor.RED + "Error while removing region '" + args[1] + "'");
						} else
							player.sendMessage(ChatColor.RED + "There is no region called '" + args[1] + "'");	
					} else 
						player.sendMessage(ChatColor.RED + "Usage: /ar region remove [name]");
				} else if (args[0].equalsIgnoreCase("modify")) {
					if (args.length == 3) {
						Region region = regions.get(regions.indexOf(new Region(args[1])));
						if (region != null) {
							Material mat = Material.matchMaterial(args[2]);
							if (mat != null) {
								if (respawningBlocks.contains(mat.getId())) {
									if (region.getRespawns().contains(mat.getId())) {
										if (config.RegionRemoveRespawn(region, mat.getId()))
											player.sendMessage(ChatColor.GREEN + "Removed respawn '" + mat + "' from region '" + args[1] + "'");
										else
											player.sendMessage(ChatColor.RED + "Error while removing respawn '" + mat + "' from region '" + args[1] + "'");
									} else {
										if (config.RegionAddRespawn(region, mat.getId()))
											player.sendMessage(ChatColor.GREEN + "Added respawn '" + mat + "' to region '" + args[1] + "'");
										else
											player.sendMessage(ChatColor.RED + "Error while adding respawn '" + mat + "' to region '" + args[1] + "'");
									}
								} else
									player.sendMessage(ChatColor.RED + "There is no respawn defined for '" + mat + "'");	
							} else
								player.sendMessage(ChatColor.RED + "There is no material called '" + args[2] + "'");	
						} else
							player.sendMessage(ChatColor.RED + "There is no region called '" + args[1] + "'");	
					} else 
						player.sendMessage(ChatColor.RED + "Usage: /ar region modify [name] [material]");
				} else if (args[0].equalsIgnoreCase("help")) {
					player.sendMessage(ChatColor.LIGHT_PURPLE + "AnyReg Commands:");
					player.sendMessage(ChatColor.LIGHT_PURPLE + "/ar create [name] //Creates a region");
					player.sendMessage(ChatColor.LIGHT_PURPLE + "/ar remove [name] //Removes a region");
					player.sendMessage(ChatColor.LIGHT_PURPLE + "/ar modify [name] [material] //Add/removes a respawning material to/from a region");
				} else
					player.sendMessage(ChatColor.RED + "Wrong argument. Type /ar help for help");
			} else
				sender.sendMessage("You aren't a player");
			return true;
		} else
			return false;
	}

	private boolean checkTables() {
		Connection conn = getConnection();
		Statement state = null;
		if (conn == null)
			return false;
		try {
			DatabaseMetaData dbm = conn.getMetaData();
			state = conn.createStatement();
			if (!dbm.getTables(null, null, "ar-worlds", null).next()) {
				log.log(Level.INFO, "[AnyReg] Crating table ar-worlds.");
				if (config.useMySQL)
					state.execute("CREATE TABLE `ar-worlds` (worldid SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT, worldname varchar(32) NOT NULL DEFAULT '-', PRIMARY KEY (worldid), UNIQUE (worldname))");
				else
					state.execute("CREATE TABLE `ar-worlds` (worldname TEXT NOT NULL, UNIQUE (worldname))");
				if (!dbm.getTables(null, null, "ar-worlds", null).next()) {
					log.severe("[AnyReg] Failed to create table worlds");
					return false;
				}
			}
			for (World world : getServer().getWorlds()) {
				if (config.useMySQL)
					state.execute("INSERT IGNORE INTO `ar-worlds` (`worldname`) VALUES ('" + world.getName() + "');");
				else
					state.execute("INSERT OR IGNORE INTO `ar-worlds` (`worldname`) VALUES ('" + world.getName() + "');");
			}
			if (!dbm.getTables(null, null, "ar-respawning", null).next()) {
				log.log(Level.INFO, "[AnyReg] Crating table ar-respawning.");
				if (config.useMySQL)
					state.execute("CREATE TABLE `ar-respawning` (id INT NOT NULL AUTO_INCREMENT, type TINYINT UNSIGNED NOT NULL DEFAULT '0', data TINYINT UNSIGNED NOT NULL DEFAULT '0', x SMALLINT NOT NULL DEFAULT '0', y TINYINT UNSIGNED NOT NULL DEFAULT '0', z SMALLINT NOT NULL DEFAULT '0', worldid SMALLINT UNSIGNED NOT NULL DEFAULT '0', PRIMARY KEY (id))");
				else
					state.execute("CREATE TABLE `ar-respawning` (type INTEGER NOT NULL, data INTEGER NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL,z INTEGER NOT NULL, worldid INTEGER NOT NULL)");
				if (!dbm.getTables(null, null, "ar-respawning", null).next()) {
					log.severe("[AnyReg] Failed to create table respawning");
					return false;
				}
			}
			if (!dbm.getTables(null, null, "ar-blacklisted", null).next()) {
				log.log(Level.INFO, "[AnyReg] Crating table ar-blacklisted.");
				if (config.useMySQL)
					state.execute("CREATE TABLE `ar-blacklisted` (id INT NOT NULL AUTO_INCREMENT, type TINYINT UNSIGNED NOT NULL DEFAULT '0', x SMALLINT NOT NULL DEFAULT '0', y TINYINT UNSIGNED NOT NULL DEFAULT '0', z SMALLINT NOT NULL DEFAULT '0', worldid SMALLINT UNSIGNED NOT NULL DEFAULT '0', PRIMARY KEY (id))");
				else
					state.execute("CREATE TABLE `ar-blacklisted` (type INTEGER NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, worldid INTEGER NOT NULL)");
				if (!dbm.getTables(null, null, "ar-blacklisted", null).next()) {
					log.severe("[AnyReg] Failed to create table blacklisted");
					return false;
				}
			}
			return true;
		} catch (SQLException ex) {
			log.log(Level.SEVERE, "[AnyReg] SQL exception", ex);
		} finally {
			try {
				if (state != null)
					state.close();
				if (conn != null)
					conn.close();
			} catch (SQLException ex) {
				AnyReg.log.log(Level.SEVERE, "[AnyReg] SQL exception", ex);
			}
		}
		return false;
	}

	private boolean importFromSQLite() {
		Connection mysql = getConnection(true);
		Connection sqlite = getConnection(false);
		ResultSet rs = null;
		Statement state = null;
		PreparedStatement ps = null;
		if (mysql == null || sqlite == null)
			return false;
		try {
			mysql.setAutoCommit(false);
			state = sqlite.createStatement();
			rs = state.executeQuery("SELECT type, data, x, y, z, worldid FROM `ar-respawning` ORDER BY rowid ASC");
			ps = mysql.prepareStatement("INSERT INTO `ar-respawning` (type, data, x, y, z, worldid) VALUES (?, ?, ?, ?, ?, ?)");
			while (rs.next()) {
				ps.setInt(1, rs.getInt(1));
				ps.setInt(2, rs.getInt(2));
				ps.setInt(3, rs.getInt(3));
				ps.setInt(4, rs.getInt(4));
				ps.setInt(5, rs.getInt(5));
				ps.setInt(6, rs.getInt(6));
				ps.executeUpdate();
			}
			rs = state.executeQuery("SELECT type, x, y, z, worldid FROM `ar-blacklisted` ORDER BY rowid ASC");
			ps = mysql.prepareStatement("INSERT INTO `ar-blacklisted` (type, x, y, z, worldid) VALUES (?, ?, ?, ?, ?)");
			while (rs.next()) {
				ps.setInt(1, rs.getInt(1));
				ps.setInt(2, rs.getInt(2));
				ps.setInt(3, rs.getInt(3));
				ps.setInt(4, rs.getInt(4));
				ps.setInt(5, rs.getInt(5));
				ps.executeUpdate();
			}
			mysql.commit();
		} catch (Exception ex) {
			AnyReg.log.log(Level.SEVERE, "[AnyReg] SQL exception while sqlite import", ex);
			return false;
		} finally {
			try {
				if (rs != null)
					rs.close();
				if (sqlite != null)
					sqlite.close();
				if (mysql != null)
					mysql.close();
			} catch (SQLException ex) {
				AnyReg.log.log(Level.SEVERE, "[AnyReg] SQL exception", ex);
			}
		}
		if (new File(getDataFolder(), "anyreg.db").delete())
			return true;
		return false;
	}

	private class AnyRegBlockListener extends BlockListener
	{
		public void onBlockBreak(BlockBreakEvent event) {
			if (!event.isCancelled())
				AddRegQueue(event.getBlock());
		}

		public void onBlockPlace(BlockPlaceEvent event) {
			if (!event.isCancelled())
				AddBlackQueue(event.getBlock());
		}

		public void onBlockBurn(BlockBurnEvent event) {
			if (!event.isCancelled())
				AddRegQueue(event.getBlock());
		}
	}

	private class AnyRegEntityListener extends EntityListener
	{
		public void onEntityExplode(EntityExplodeEvent event) {
			if (!event.isCancelled()) {
				for (Block block : event.blockList()) {
					AddRegQueue(block);
				}
			}
		}
	}

	private void AddRegQueue(Block block) {
		if (respawningBlocks.contains(block.getTypeId())) {
			for (Region region : regions) {
				if (region.isCognizantFor(block)) {
					regQueue.add(block.getState());
					break;
				}
			}
		}
	}

	private void AddBlackQueue(Block block) {
		if (blacklistedBlocks.contains(block.getTypeId())) {
			for (Region region : regions) {
				if (region.isCognizantFor(block)) {
					blackQueue.add(block.getState());
					break;
				}
			}
		}
	}

	public static Connection getConnection() {
		return getConnection(config.useMySQL);
	}

	public static Connection getConnection(boolean useMySQL) {
		if (useMySQL) {
			try {
				return DriverManager.getConnection("jdbc:jdc:jdcpool");
			} catch (SQLException ex) {
				log.log(Level.SEVERE, "[AnyReg] SQL exception", ex);
				return null;
			}
		} else {
			try	{
				Class.forName("org.sqlite.JDBC");
				return DriverManager.getConnection("jdbc:sqlite:plugins/AnyReg/anyreg.db");
			} catch (ClassNotFoundException ex) {
				log.log(Level.SEVERE, "[AnyReg] sqlitejdbc-v056.jar not found. Make sure, you placed it next to craftbukkit.jar", ex);
			} catch (SQLException ex) {
				log.log(Level.SEVERE, "[AnyReg] SQLExeption:", ex);
			}
		}
		return null;
	}
}
