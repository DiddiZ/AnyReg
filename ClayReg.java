//Author: DiddiZ
//Date: 2010-12-30

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClayReg extends Plugin
{
	static Logger minecraftLog = Logger.getLogger("Minecraft");
    private Listener listener = new Listener();
    private String name = "ClayReg";
    private String version = "0.1";
    private ArrayList<Position> clays = new ArrayList<Position>();
    private Timer timer = new Timer();
    private RegTimerTask task = new RegTimerTask();
    private long regDelay;
    private double regChance;
    
    public void enable()
    {
    	ReadBlockFile();
    	LoadProperties();
    }
    
    public void disable()
    {
    	SaveBlockFile();
    }

    public void initialize()
    {
    	timer.scheduleAtFixedRate(task, regDelay, regDelay);
    	minecraftLog.info(name + " v" + version + " loaded");
    	etc.getLoader().addListener(PluginLoader.Hook.SERVERCOMMAND, listener, this, PluginListener.Priority.MEDIUM);
    	etc.getLoader().addListener(PluginLoader.Hook.BLOCK_DESTROYED, listener, this, PluginListener.Priority.MEDIUM);
    }
    
	private class Listener extends PluginListener
	{ 
		public boolean onConsoleCommand(String[] split) 		
		{
			if (split[0].equalsIgnoreCase("stop"))
				SaveBlockFile();
			return false;
		}
		
		public boolean onBlockDestroy(Player player, Block block) 
		{
			if (block.getType() != 82)
				return false;
			if (block.getStatus() != 3)
				return false;
			Position pos = new Position(block.getX(), block.getY(), block.getZ());
			if (!clays.contains(pos))
				clays.add(new Position(block.getX(), block.getY(), block.getZ()));
			return false;
		}
	}
	
    private void LoadProperties()
    {
    	PropertiesFile properties = new PropertiesFile("clayReg.properties");
		try
		{
			regDelay = properties.getInt("regDelay", 360000);
			regChance = properties.getDouble("regChance", 0.1);
        }
		catch (Exception e)
		{
        	minecraftLog.log(Level.SEVERE, "Exception while reading from measuringTape.properties", e);
		}
	}
	
	private class RegTimerTask extends TimerTask
	{
	    public void run() 
	    {
	    	Server server = etc.getServer();
	    	for (int i = 0; i < clays.size(); i++)
	        {
	        	int now = server.getBlockAt(clays.get(i).X, clays.get(i).Y, clays.get(i).Z).getType();
	    		if (now == 0 || now == 8 || now == 9)
	    		{
		    		if (Math.random() < regChance)
		        	{
		        		server.setBlockAt(82, clays.get(i).X, clays.get(i).Y, clays.get(i).Z);
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

	
	private class Position
	{
		public int X, Y, Z;
		
		Position (int x, int y, int z)
		{
			X = x;
			Y = y;
			Z = z;
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
			ostream = new FileOutputStream("ClayReg.data");
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
			minecraftLog.log(Level.SEVERE, "Exception while writing ClayReg.data", e);
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
        		minecraftLog.log(Level.SEVERE, "Exception while closing writer for ClayReg.data");
        	}
		}
	}
	
	private void ReadBlockFile()
	{
		if (!new File ("ClayReg.data").exists())
			return;
		FileInputStream istream = null;
		ObjectInputStream p = null;
		try
		{
			istream = new FileInputStream("ClayReg.data");
			p = new ObjectInputStream(istream);
	        int cycles = (istream.available() - 2) / 12;
			for (int i = 1; i <= cycles; i++)
	        {
				clays.add(new Position(p.readInt(), p.readInt(), p.readInt()));
	        }
		}
		catch (Exception e)
		{
			minecraftLog.log(Level.SEVERE, "Exception while reading ClayReg.data", e);
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
        		minecraftLog.log(Level.SEVERE, "Exception while closing reader for ClayReg.data");
        	}
		}
	}
}