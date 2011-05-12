package de.diddiz.AnyReg.SQLite;

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

public class SQLiteRegTask extends RegTask
{
	public SQLiteRegTask(Server server, Respawn respawn) {
		super(server, respawn);
	}

	@Override
	public void run() {
		PreparedStatement psDel = null;
		PreparedStatement psBlack = null;
		final Connection conn = AnyReg.getConnection();
		ResultSet rs = null;
		Block block;
		if (conn == null)
			return;
		try {
			final long start = System.currentTimeMillis();
			int counter = 0;
			conn.setAutoCommit(false);
			psDel = conn.prepareStatement("DELETE FROM `ar-respawning` WHERE rowid = ?");
			psBlack = conn.prepareStatement("SELECT type FROM `ar-blacklisted` WHERE worldid = ? AND x = ? AND y = ? AND z = ?");
			rs = conn.createStatement().executeQuery(("SELECT data, x, y, z, worldname, worldid, `ar-respawning`.rowid FROM `ar-respawning` INNER JOIN `ar-worlds` ON `ar-respawning`.worldid = `ar-worlds`.rowid WHERE type = '" + respawn.getType() + "'"));
			while (rs.next()) {
				counter++;
				boolean delete = false;
				try {
					if (respawn.isUseBlacklist()) {
						psBlack.setInt(1, rs.getInt("worldid"));
						psBlack.setInt(2, rs.getInt("x"));
						psBlack.setInt(3, rs.getInt("y"));
						psBlack.setInt(4, rs.getInt("z"));
						if (psBlack.executeQuery().next()) {
							delete = true;
							continue;
						}
					}
					final World world = server.getWorld(rs.getString("worldname"));
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
						psDel.setInt(1, rs.getInt("rowid"));
						psDel.execute();
					}
				}
			}
			conn.commit();
			if (counter > 0)
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
