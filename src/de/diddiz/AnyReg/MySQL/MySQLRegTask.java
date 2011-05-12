package de.diddiz.AnyReg.MySQL;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import de.diddiz.AnyReg.AnyReg;
import de.diddiz.AnyReg.RegTask;
import de.diddiz.AnyReg.Respawn;

public class MySQLRegTask extends RegTask
{
	public MySQLRegTask(Server server, Respawn respawn) {
		super(server, respawn);
	}

	@Override
	public void run() {
		final Connection conn = AnyReg.getConnection();
		PreparedStatement psDel = null;
		ResultSet rs = null;
		Block block;
		try {
			final long start = System.currentTimeMillis();
			int counter = 0;
			conn.setAutoCommit(false);
			psDel = conn.prepareStatement("DELETE FROM `ar-respawning` WHERE id = ?");
			if (respawn.isUseBlacklist())
				rs = conn.createStatement().executeQuery(("SELECT r.id, data, x, y, z, worldname, `ar-blacklisted`.type IS NOT NULL AS blacklisted FROM `ar-worlds` INNER JOIN (SELECT id, data, x, y, z, worldid FROM `ar-respawning` WHERE type = '" + respawn.getType() + "' ORDER BY worldid) AS r USING (worldid) LEFT JOIN `ar-blacklisted` USING(x, y, z, worldid)"));
			else
				rs = conn.createStatement().executeQuery(("SELECT id, data, x, y, z, worldname FROM `ar-respawning` INNER JOIN `ar-worlds` USING (worldid) WHERE type = '" + respawn.getType() + "'"));
			AnyReg.log.info("[AnyReg RegTask " + Material.getMaterial(respawn.getType()) + "] Query Took " + (System.currentTimeMillis() - start) + "ms");
			String lastWorld = "";
			World world = null;
			while (rs.next()) {
				counter++;
				boolean delete = false;
				try {
					if (respawn.isUseBlacklist() && rs.getBoolean("blacklisted")) {
						delete = true;
						continue;
					}
					if (!lastWorld.equals(rs.getString("worldname"))) {
						lastWorld = rs.getString("worldname");
						world = server.getWorld(lastWorld);
					}
					if (world != null) {
						block = world.getBlockAt(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
						if (!world.isChunkLoaded(block.getChunk()))
							world.loadChunk(block.getChunk());
						if (respawn.getCanReplace().contains(block.getTypeId())) {
							if (Math.random() < respawn.getRegChance()) {
								block.setTypeIdAndData(respawn.getType(), rs.getByte("data"), true);
								delete = true;
							}
						} else
							delete = true;
					} else
						delete = true;
				} finally {
					if (delete) {
						psDel.setInt(1, rs.getInt("id"));
						psDel.execute();
					}
				}
			}
			conn.commit();
			AnyReg.log.info("[AnyReg RegTask " + Material.getMaterial(respawn.getType()) + "] Took " + (System.currentTimeMillis() - start) + "ms for " + counter + " blocks");
		} catch (final SQLException ex) {
			AnyReg.log.log(Level.SEVERE, "[AnyReg RegTask] SQL exception", ex);
		} catch (final Exception ex) {
			AnyReg.log.log(Level.SEVERE, "[AnyReg RegTask] Exception", ex);
		} finally {
			try {
				if (rs != null)
					rs.close();
				if (psDel != null)
					psDel.close();
				if (conn != null)
					conn.close();
			} catch (final SQLException ex) {
				AnyReg.log.log(Level.SEVERE, "[AnyReg] SQL exception", ex);
			}
		}
	}
}
