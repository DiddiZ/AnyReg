package de.diddiz.AnyReg;

import org.bukkit.Server;

public abstract class RegTask implements Runnable
{
	protected Server server;
	protected Respawn respawn;

	public RegTask(Server server, Respawn respawn) {
		this.server = server;
		this.respawn = respawn;
	}
}
