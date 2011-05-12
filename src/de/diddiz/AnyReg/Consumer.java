package de.diddiz.AnyReg;

import java.util.concurrent.LinkedBlockingQueue;
import org.bukkit.block.BlockState;

public abstract class Consumer implements Runnable
{
	protected LinkedBlockingQueue<BlockState> respawningQueue;
	protected LinkedBlockingQueue<BlockState> blacklistedQueue;

	public Consumer(LinkedBlockingQueue<BlockState> respawningQueue, LinkedBlockingQueue<BlockState> blacklistedQueue) {
		this.respawningQueue = respawningQueue;
		this.blacklistedQueue = blacklistedQueue;
	}
}
