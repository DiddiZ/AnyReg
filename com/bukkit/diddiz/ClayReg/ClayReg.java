//Author: DiddiZ
//Date: 2011-01-23
package com.bukkit.diddiz.ClayReg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ClayReg extends JavaPlugin
{
	private Logger logger = Logger.getLogger("Minecraft");
	private ClayRegBlockListener clayRegBlockListener = new ClayRegBlockListener();
	private ClayRegPlayerListener clayRegPlayerListener = new ClayRegPlayerListener();
    private ArrayList<Position> clays = new ArrayList<Position>();
    private Timer timer = new Timer();
    private ClayRegTimerTask task = new ClayRegTimerTask();
    private long regDelay;
    private double regChance;
    
	public ClayReg(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File folder, File plugin, ClassLoader cLoader)
    {
		super(pluginLoader, instance, desc, folder, plugin, cLoader);
	}
    
	@Override
	public void onEnable()
	{
	    LoadProperties();
	    ReadBlockFile();
	    PluginManager pm = getServer().getPluginManager();
		pm.registerEvent(Event.Type.BLOCK_DAMAGED, this.clayRegBlockListener, Event.Priority.Monitor, this);
		pm.registerEvent(Event.Type.PLAYER_COMMAND, this.clayRegPlayerListener, Event.Priority.Monitor, this);
    	timer.scheduleAtFixedRate(task, regDelay, regDelay);
        logger.info("ClayReg v" + this.getDescription().getVersion() + " by DiddiZ enabled");
	}
    
	@Override
	public void onDisable()
	{
		SaveBlockFile();
		logger.info("ClayReg disabled");
	}
    
	private class ClayRegBlockListener extends BlockListener
	{ 
	    public void onBlockDamage(BlockDamageEvent event)
	    {
	    	if (event.isCancelled())
	    		return;
	    	Block block = event.getBlock();
	    	if (block.getTypeId() == 82 && event.getDamageLevel().getLevel() == 3)
	    	{
	    		Position pos = new Position(block);
	    		if (clays.contains(pos))
					clays.add(pos);
	    	}
	    }
	}
	
	private class ClayRegPlayerListener extends PlayerListener
	{ 
		public void onPlayerCommand(PlayerChatEvent event)
		{
			if (event.isCancelled())
				return;
			if (event.getMessage().equalsIgnoreCase("/#stop"))
			{
				logger.info("Saved clays");
				SaveBlockFile();
			}
		}
	}
		
    private void LoadProperties()
    {
		try
		{
			File file = new File (getDataFolder(), "config.yml");
			if (!file.exists())
			{
				file.getParentFile().mkdirs();
				FileWriter writer = new FileWriter(file);
				writer.write("regDelay : 360000" + System.getProperty("line.separator"));
				writer.write("regChance : 0.1");
				writer.close();
				logger.info("ClayReg config created");
			}
			getConfiguration().load();
			regDelay = getConfiguration().getInt("regDelay", 360000);
			regChance = getConfiguration().getDouble("regChance", 0.1);
			logger.info("Delay: " + regDelay + " Chance: " + regChance);
        }
		catch (Exception e)
		{
        	logger.log(Level.SEVERE, "Exception while reading from clayReg.properties", e);
		}
	}
	
	private class ClayRegTimerTask extends TimerTask
	{
	    public void run() 
	    {
	    	if (clays.size() > 0)
	    	{
		    	World world = getServer().getWorlds()[0];
		    	Block block;
		    	for (int i = 0; i < clays.size(); i++)
		        {
		        	
		    		int now = world.getBlockAt(clays.get(i).X, clays.get(i).Y, clays.get(i).Z).getTypeId();
		    		if (now == 0 || now == 8 || now == 9)
		    		{
			    		if (Math.random() < regChance)
			        	{
			        		block = world.getBlockAt(clays.get(i).X, clays.get(i).Y, clays.get(i).Z);
			        		block.setTypeId(82);
			        		clays.remove(i);
			        		i--;
			        	}
		    		}
		    		else
		    		{
		    			clays.remove(i);
		    			i--;
		    		}
		        }
		    	SaveBlockFile();
	    	}
	    }
	}

	private class Position
	{
		public int X, Y, Z;
		
		Position (int x, int y, int z)
		{
			X = x;
			Y = y;
			Z = z;
		}
		
		Position (Block block)
		{
			X = block.getX();
			Y = block.getY();
			Z = block.getZ();
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj == null)
				return false;
			Position pos = (Position) obj;
			if (X != pos.X)
				return false;
			if (Y != pos.Y)
				return false;
			if (Z != pos.Z)
				return false;
			return true;
		}
	}
	
    private void SaveBlockFile()
	{
		FileOutputStream ostream = null;
		ObjectOutputStream p = null;
		try
		{
			ostream = new FileOutputStream(new File(getDataFolder(), "ClayReg.data"));
			p = new ObjectOutputStream(ostream);
	        for (Position pos : clays)
	        {
	        	p.writeInt(pos.X);
	        	p.writeInt(pos.Y);
	        	p.writeInt(pos.Z);
	        }
		}
		catch (Exception e)
		{
			logger.log(Level.SEVERE, "Exception while writing ClayReg.data", e);
		}
		finally
		{
			try 
			{
				p.close();
				ostream.close();
			}
			catch (Exception e)
			{
        		logger.log(Level.SEVERE, "Exception while closing writer for ClayReg.data");
        	}
		}
	}
	
	private void ReadBlockFile()
	{
		if (!new File (getDataFolder(), "ClayReg.data").exists())
			return;
		FileInputStream istream = null;
		ObjectInputStream p = null;
		try
		{
			istream = new FileInputStream(new File(getDataFolder(), "ClayReg.data"));
			p = new ObjectInputStream(istream);
	        int cycles = (istream.available() - 2) / 12;
			for (int i = 1; i <= cycles; i++)
	        {
				clays.add(new Position(p.readInt(), p.readInt(), p.readInt()));
	        }
		}
		catch (Exception e)
		{
			logger.log(Level.SEVERE, "Exception while reading ClayReg.data", e);
		}
		finally
		{
			try 
			{
				p.close();
				istream.close();
			}
			catch (Exception e)
			{
        		logger.log(Level.SEVERE, "Exception while closing reader for ClayReg.data");
        	}
		}
	}
}