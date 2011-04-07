package de.diddiz.AnyReg;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.util.config.Configuration;

public class Config {
	private Configuration config;
	private Configuration respawnConfig;
	private Configuration regionConfig;
	public String dbDriver;
	public String dbUrl;
	public String dbUsername;
	public String dbPassword;
	public boolean useMySQL;
	public int delay;
	public boolean logExplosions;
	public boolean logFire;
	
	public Config(Configuration config, File dataFolder) {
		this.config = config;;
		respawnConfig = new Configuration(new File(dataFolder, "respawns.yml"));
		regionConfig = new Configuration(new File(dataFolder, "regions.yml"));
	}
	 
	public boolean LoadConfig() {
		config.load();
		List<String> keys = config.getKeys(null);
		if (!keys.contains("delay"))
			config.setProperty("delay", 6);
		if (!keys.contains("logExplosions"))
			config.setProperty("logExplosions", false);
		if (!keys.contains("logFire"))
			config.setProperty("logFire", false);
		if (!keys.contains("driver"))
			config.setProperty("driver", "com.mysql.jdbc.Driver");
		if (!keys.contains("url"))
			config.setProperty("url", "jdbc:mysql://localhost:3306/db");
		if (!keys.contains("username"))
			config.setProperty("username", "user");
		if (!keys.contains("password"))
			config.setProperty("password", "pass");
		if (!keys.contains("useMySQL"))
			config.setProperty("useMySQL", false);
		if (!config.save()){
			AnyReg.log.severe("[AnyRegBlock] Error while writing to config.yml");
			return false;
		}
		delay = config.getInt("delay", 6);
		logExplosions = config.getBoolean("logExplosions", false);
		logFire = config.getBoolean("logFire", false);
		dbDriver = config.getString("driver");
		dbUrl = config.getString("url");
		dbUsername = config.getString("username");
		dbPassword = config.getString("password");
		useMySQL = config.getBoolean("useMySQL", false);
		return true;
	}
	
	public boolean LoadRespawns(List<Respawn> respawns) {
		respawnConfig.load();
		for (String key : respawnConfig.getKeys(null)) {
			Material mat = Material.matchMaterial(key);
			if (mat == null) {
				AnyReg.log.warning("[AnyReg] Can't resolve material " + key + ".");
				continue;
			}
			Respawn respawn = new Respawn(mat.getId(),
					respawnConfig.getInt(key + ".regDelay", 360), 
					respawnConfig.getDouble(key + ".regChance", 0.1), 
					respawnConfig.getBoolean(key + ".useBlacklist", true), 
					new HashSet<Integer>(respawnConfig.getIntList(key + ".canReplace", null)));
			if (!key.equals(mat.toString())) {
				respawnConfig.setProperty(mat.toString() + ".regDelay", respawn.getRegDelay());
				respawnConfig.setProperty(mat.toString() + ".regChance", respawn.getRegChance());
				respawnConfig.setProperty(mat.toString() + ".useBlacklist", respawn.isUseBlacklist());
				respawnConfig.setProperty(mat.toString() + ".canReplace", new ArrayList<Integer>(respawn.getCanReplace()));
				respawnConfig.removeProperty(key);
			}
			for (int id : respawn.getCanReplace()) {
				if (Material.getMaterial(id) == null) {
					respawn.getCanReplace().remove(id);
					AnyReg.log.warning("[AnyReg] " + id + " is not a valid item id");
				}
			}
			if (respawn.getCanReplace().isEmpty()) {
				AnyReg.log.warning("[AnyReg] Must specify canReplace for " + key + ".");
				continue;
			}
			respawns.add(respawn);
		}
		if (!respawnConfig.save()){
			AnyReg.log.severe("[AnyReg] Error while writing to respawns.yml");
			return false;
		}
		if (respawns.isEmpty())
			return false;
		return true;
	}
	
	public boolean LoadRegions(List<Region> regions) {
		regionConfig.load();
		for (String key : regionConfig.getKeys(null)) {
			try {
				regions.add(new Region(key, regionConfig.getString(key + ".region"), new HashSet<Integer>(regionConfig.getIntList(key + ".respawn", null))));
			} catch (Exception ex) {
				AnyReg.log.warning("[AnyReg] " + ex.getMessage());
			}
		}
		if (regions.isEmpty())
			return false;
		return true;
	}
	
	public boolean RegionAdd(List<Region> regions, Region region) {
		try {
			regionConfig.load();
			regionConfig.setProperty(region.getName() + ".region", region.getRegionString());
			regionConfig.setProperty(region.getName() + ".respawn", region.getRespawns());
			regionConfig.save();
			regions.add(region);
		return true;
		} catch (Exception ex) {
			AnyReg.log.log(Level.SEVERE, "[AnyReg] Error while adding region", ex);
			return false;
		}
	}
	
	public boolean RegionRemove(List<Region> regions, Region region) {
		try {
			regionConfig.load();
			regionConfig.removeProperty(region.getName());
			regionConfig.save();
			regions.remove(region.hashCode());
			return true;
		} catch (Exception ex) {
			AnyReg.log.log(Level.SEVERE, "[AnyReg] Error while removing region", ex);
			return false;
		}
	}
	
	public boolean RegionAddRespawn(Region region, int type) {
		try {
			region.getRespawns().add(type);
			regionConfig.load();
			regionConfig.setProperty(region.getName() + ".respawn", new ArrayList<Integer>(region.getRespawns()));
			regionConfig.save();
		return true;
		} catch (Exception ex) {
			AnyReg.log.log(Level.SEVERE, "[AnyReg] Error while adding respawn '" + type + "' to region '" + region.getName() + "'", ex);
			return false;
		}
	}
	
	public boolean RegionRemoveRespawn(Region region, int type) {
		try {
			region.getRespawns().remove(type);
			regionConfig.load();
			regionConfig.setProperty(region.getName() + ".respawn", new ArrayList<Integer>(region.getRespawns()));
			regionConfig.save();
		return true;
		} catch (Exception ex) {
			AnyReg.log.log(Level.SEVERE, "[AnyReg] Error while removing respawn '" + type + "' from region '" + region.getName() + "'", ex);
			return false;
		}
	}
}
