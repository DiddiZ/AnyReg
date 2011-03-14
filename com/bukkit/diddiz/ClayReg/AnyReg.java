//Author: DiddiZ
//Date: 2011-02-10

package com.bukkit.diddiz.AnyReg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class AnyReg extends JavaPlugin
{
	private Logger logger = Logger.getLogger("Minecraft");
	private AnyRegBlockListener RegBlockListener = new AnyRegBlockListener();
    private Timer timer = new Timer();
    private RegTimerTask[] regTasks;
    private List<Integer> respawn;
    
    
	public AnyReg(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File folder, File plugin, ClassLoader cLoader)
    {
		super(pluginLoader, instance, desc, folder, plugin, cLoader);
	}
    
	@Override
	public void onEnable()
	{
		int saveDelay = 0;
		try
		{
			File file = new File (getDataFolder(), "config.yml");
			if (!file.exists())
			{
				file.getParentFile().mkdirs();
				FileWriter writer = new FileWriter(file);
				String crlf = System.getProperty("line.separator");
				writer.write("saveDelay : 300000" + crlf
						+ "respawn : [82, 86]" + crlf
						+ "\"82\" :" + crlf
						+ "  regDelay : 360000" + crlf
						+ "  regChance : 0.1" + crlf
						+ "  useBlacklist : false" + crlf
						+ "  canReplace : [0, 8, 9]" + crlf
						+ "\"86\" :" + crlf
						+ "  regDelay : 4320000" + crlf
						+ "  regChance : 0.05" + crlf
						+ "  useBlacklist : true" + crlf
						+ "  canReplace : [0]");
				writer.close();
				logger.info("[AnyReg] Config created");
			}
			getConfiguration().load();
			saveDelay = getConfiguration().getInt("saveDelay", 0);
			respawn = getConfiguration().getIntList("respawn", new ArrayList<Integer>());
			regTasks = new RegTimerTask[respawn.size()];
			for (int i = 0; i < respawn.size(); i++)
			{
				regTasks[i] = new RegTimerTask (respawn.get(i),
						getConfiguration().getInt(respawn.get(i) + ".regDelay", Integer.MAX_VALUE),
						getConfiguration().getDouble(respawn.get(i) + ".regChance", 0),
						getConfiguration().getBoolean(respawn.get(i) + ".useBlacklist", false),
						getConfiguration().getIntList(respawn.get(i) + ".canReplace", new ArrayList<Integer>()));
			}
		}
		catch (Exception e)
		{
        	logger.log(Level.SEVERE, "[AnyReg] Exception while reading from config.yml", e);
        	getServer().getPluginManager().disablePlugin(this);
		}
	    for (int i = 0; i < regTasks.length; i++)
	    	timer.scheduleAtFixedRate(regTasks[i], regTasks[i].regDelay, regTasks[i].regDelay);
	    if (saveDelay > 0)
	    	timer.scheduleAtFixedRate(new SaveTimerTask(), saveDelay, saveDelay);
	    PluginManager pm = getServer().getPluginManager();
		pm.registerEvent(Event.Type.BLOCK_DAMAGED, RegBlockListener, Event.Priority.Monitor, this);
		pm.registerEvent(Event.Type.BLOCK_PLACED, RegBlockListener, Event.Priority.Monitor, this);
        logger.info("AnyReg v" + getDescription().getVersion() + " by DiddiZ enabled");
	}
    
	@Override
	public void onDisable()
	{
		SaveAllBlockFiles();
		logger.info("AnyReg disabled");
	}
    
	private class AnyRegBlockListener extends BlockListener
	{ 
	    public void onBlockDamage(BlockDamageEvent event)
	    {
	    	if (event.isCancelled())
	    		return;
	    	Block block = event.getBlock();
	    	if (respawn.contains(block.getTypeId()) && event.getDamageLevel().getLevel() == 3 && getServer().getWorlds().indexOf(block.getWorld()) == 0)
	    	{
	    		RegTimerTask task = regTasks[respawn.indexOf(block.getTypeId())];
	    		Position pos = new Position(block);
	    		if ((!task.useBlacklist || !task.blacklistedBlocks.contains(pos)) && !task.blocks.contains(pos))
	    			task.blocks.add(pos);
	    		else if ((task.useBlacklist && task.blacklistedBlocks.contains(pos)))
	    			task.blacklistedBlocks.remove(pos);
	    	}
	    }
	    
	    public void onBlockPlace(BlockPlaceEvent event)
	    {
	    	if (event.isCancelled())
	    		return;
	    	Block block = event.getBlock();
	    	if (respawn.contains(block.getTypeId()) && getServer().getWorlds().indexOf(block.getWorld()) == 0)
	    	{
	    		RegTimerTask task = regTasks[respawn.indexOf(block.getTypeId())];
	    		Position pos = new Position(block);
	    		if ((task.useBlacklist && !task.blacklistedBlocks.contains(pos)))
	    			task.blacklistedBlocks.add(pos);
	    	}
	    }
	}
	
	private class RegTimerTask extends TimerTask
	{
	    public int blockType;
		public long regDelay;
	    public double regChance;
	    public boolean useBlacklist;
	    public List<Integer> canReplace;
	    public ArrayList<Position> blocks;
	    public ArrayList<Position> blacklistedBlocks;
	    
	    RegTimerTask(int blockType, long regDelay, double regChance, boolean useBlacklist, List<Integer> canReplace)
	    {
	    	this.blockType = blockType;
	    	this.regDelay = regDelay;
	    	this.regChance = regChance;
	    	this.useBlacklist = useBlacklist;
	    	this.canReplace = canReplace;
	    	this.blocks = ReadBlockFile(new File(getDataFolder(), blockType + "blocks.data"));
	    	if (this.useBlacklist)
	    		this.blacklistedBlocks = ReadBlockFile(new File(getDataFolder(), blockType + "blacklist.data"));
	    }
	    
		public void run() 
	    {
			if (blocks.size() > 0)
	    	{
				World world = getServer().getWorlds().get(0);
		    	Block block;
		    	for (int i = 0; i < blocks.size(); i++)
		        {
		    		block = world.getBlockAt(blocks.get(i).X, blocks.get(i).Y, blocks.get(i).Z);
		    		if (canReplace.contains(block.getTypeId()))
		    		{
			    		if (Math.random() < regChance)
			        	{
			        		block.setTypeId(blockType);
			        		blocks.remove(i);
			        		i--;
			        	}
		    		}
		    		else
		    		{
		    			blocks.remove(i);
		    			i--;
		    		}
		        }
				SaveBlockFile(blocks, new File(getDataFolder(), blockType + "blocks.data"));
				if (useBlacklist)
					SaveBlockFile(blacklistedBlocks, new File(getDataFolder(), blockType + "blacklist.data"));
	    	}
	    }
	}
	
	private class SaveTimerTask extends TimerTask
	{
		public void run() 
	    {
			SaveAllBlockFiles();
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
    
	private void SaveAllBlockFiles()
	{
		for (int i = 0; i < regTasks.length; i++)
		{
			SaveBlockFile(regTasks[i].blocks, new File(getDataFolder(), respawn.get(i) + "blocks.data"));
			if (regTasks[i].useBlacklist)
				SaveBlockFile(regTasks[i].blacklistedBlocks, new File(getDataFolder(), respawn.get(i) + "blacklist.data"));
		}
	}
	
    private void SaveBlockFile(ArrayList<Position> blocks, File file)
	{
		FileOutputStream ostream = null;
		ObjectOutputStream p = null;
		try
		{
			ostream = new FileOutputStream(file);
			p = new ObjectOutputStream(ostream);
	        for (Position pos : blocks)
	        {
	        	p.writeInt(pos.X);
	        	p.writeInt(pos.Y);
	        	p.writeInt(pos.Z);
	        }
		}
		catch (Exception e)
		{
			logger.log(Level.SEVERE, "Exception while writing Reg.data", e);
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
        		logger.log(Level.SEVERE, "Exception while closing writer for Reg.data");
        	}
		}
	}
    
	private ArrayList<Position> ReadBlockFile(File file)
	{
		if (!file.exists())
			return new ArrayList<Position>();
		FileInputStream istream = null;
		ObjectInputStream p = null;
		ArrayList<Position> blocks = new ArrayList<Position>();
		try
		{
			istream = new FileInputStream(file);
			p = new ObjectInputStream(istream);
	        int cycles = (istream.available() - 2) / 12;
			for (int i = 1; i <= cycles; i++)
	        {
				blocks.add(new Position(p.readInt(), p.readInt(), p.readInt()));
	        }
		}
		catch (Exception e)
		{
			logger.log(Level.SEVERE, "Exception while reading Reg.data", e);
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
        		logger.log(Level.SEVERE, "Exception while closing reader for Reg.data");
        	}
		}
		return blocks;
	}
}