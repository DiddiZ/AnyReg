package de.diddiz.AnyReg;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.block.Block;

public class Region
{
	private final String name;
	private int[] min;
	private int[] max;
	private String worldName;
	private Set<Integer> respawn;
	
	public Region(String name, String regionStr, Set<Integer> respawn) throws Exception {
		if (name == null || regionStr == null || respawn == null)
			throw new Exception("You may have a syntax error in regions.yml");
		this.name = name;
		this.respawn = respawn;
		min = new int[3];
		max = new int[3];
		String[] split = regionStr.split(":");
		if (split.length != 4)
			throw new Exception("Failed to read region " + name + ". You have some odd ':' here: '" + regionStr + "'");
		if (split[0].equals("*"))
			worldName = null;
		else
			worldName = split[0];
		for (int i = 1; i < 4; i++)	{
			if (split[i].equals("*")) {
				min[i-1] = Integer.MIN_VALUE;
				max[i-1] = Integer.MAX_VALUE;
			} else if (split[i].indexOf(' ') == -1) {
				if (!isInt(split[i]))
					throw new Exception("Failed to read region " + name + ". Value isn't a number: '" + split[i] + "'");
				min[i-1] = Integer.parseInt(split[i]);
				max[i-1] = min[i];
			} else {
				String[] split2 = split[i].split(" ");
				if (split2.length != 2)
					throw new Exception("Failed to read region " + name + ". You have some odd spaces here: '" + split[i] + "'");
				if (!isInt(split2[0]))
					throw new Exception("Failed to read region " + name + ". Value isn't a number: '" + split2[0] + "'");
				if (!isInt(split2[1]))
					throw new Exception("Failed to read region " + name + ". Value isn't a number: '" + split2[1] + "'");
				min[i-1] = Integer.parseInt(split2[0]);
				max[i-1] = Integer.parseInt(split2[1]);
			}
		}
	}
	
	public Region(String name, Location loc1, Location loc2) {
		this.name = name;
		if (loc1.getWorld() != loc2.getWorld())
			worldName = null;
		else
			worldName = loc1.getWorld().getName();
		min = new int[3];
		max = new int[3];
		min[0] = Math.min(loc1.getBlockX(), loc2.getBlockX());
		max[0] = Math.max(loc1.getBlockX(), loc2.getBlockX());
		min[1] = Math.min(loc1.getBlockY(), loc2.getBlockY());
		max[1] = Math.max(loc1.getBlockY(), loc2.getBlockY());
		min[2] = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
		max[2] = Math.max(loc1.getBlockZ(), loc2.getBlockZ());
		respawn = new HashSet<Integer>();
	}
	
	public Region(String name) {
		if (name == null)
			AnyReg.log.info("[Region] name == null");
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public boolean Respawns(int type) {
		return respawn.contains(type);
	}
	
	public String getRegionString() {
		String result;
		if (worldName == null)
			result = "*";
		else
			result = worldName;
		for (int i = 0; i < 3; i++) {
			if (min[i] == Integer.MIN_VALUE && max[i] == Integer.MAX_VALUE)
				result += ":*";
			else if (min[i] == max[i])
				result +=  ":" + min[i];
			else 
				result += ":" + min[i] + " " + max[i];
		}
		return result;
	}
	
	public Set<Integer> getRespawns() {
		return respawn;
	}
	
	public boolean isCognizantFor(Block block) {
		if (!respawn.contains(block.getTypeId()))
			return false;
		if (worldName != null && !block.getWorld().getName().equals(worldName))
			return false;
		if (block.getX() < min[0] || block.getX() > max[0])
			return false;
		if (block.getY() < min[1] || block.getY() > max[1])
			return false;
		if (block.getZ() < min[2] || block.getZ() > max[2])
			return false;
		return true;
	}
	
    private boolean isInt(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!name.equals(((Region)obj).name)) {
			return false;
		}
		return true;
	}
}
