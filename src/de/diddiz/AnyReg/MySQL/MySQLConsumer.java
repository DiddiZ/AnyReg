package de.diddiz.AnyReg.MySQL;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import org.bukkit.block.BlockState;

import de.diddiz.AnyReg.AnyReg;
import de.diddiz.AnyReg.Consumer;

public class MySQLConsumer extends Consumer
{
	public MySQLConsumer(LinkedBlockingQueue<BlockState> respawningQueue, LinkedBlockingQueue<BlockState> blacklistedQueue) {
		super(respawningQueue, blacklistedQueue);
	}
	
	public void run() {
		Connection conn = AnyReg.getConnection();
		if (conn == null)
			return;
		PreparedStatement ps = null;
		BlockState b;
		int count = 0;
		if (respawningQueue.size() > 100)
			AnyReg.log.info("[AnyReg] Placed queue overloaded. Size: " + respawningQueue.size());	
		try {
			conn.setAutoCommit(false);
			ps = conn.prepareStatement("INSERT INTO `ar-respawning` (type, data, x, y, z, worldid) SELECT ?, ?, ?, ?, ? , worldid FROM `ar-worlds` WHERE worldname = ?");
			while (count < 100 && !respawningQueue.isEmpty()) {
				b = respawningQueue.poll();
				if (b == null)
					continue;
				ps.setInt(1, b.getTypeId());
				ps.setByte(2, b.getRawData());
				ps.setInt(3, b.getX());
				ps.setInt(4, b.getY());
				ps.setInt(5, b.getZ());
				ps.setString(6, b.getWorld().getName());
				ps.execute();
				count++;
			}
			conn.commit();
			ps = conn.prepareStatement("INSERT INTO `ar-blacklisted` (type, x, y, z, worldid) SELECT ?, ?, ?, ? , worldid FROM `ar-worlds` WHERE worldname = ?");
			while (count < 100 && !blacklistedQueue.isEmpty()) {
				b = blacklistedQueue.poll();
				if (b == null)
					continue;
				ps.setInt(1, b.getTypeId());
				ps.setInt(2, b.getX());
				ps.setInt(3, b.getY());
				ps.setInt(4, b.getZ());
				ps.setString(5, b.getWorld().getName());
				ps.execute();
				count++;
			}
			conn.commit();
		} catch (SQLException ex) {
			AnyReg.log.log(Level.SEVERE, "[AnyReg Consumer] SQL exception", ex);
		} finally {
			try {
				if (ps != null)
					ps.close();
				if (conn != null)
					conn.close();
			} catch (SQLException ex) {
				AnyReg.log.log(Level.SEVERE, "[AnyReg Consumer] SQL exception", ex);
			}
		}
	}
}
