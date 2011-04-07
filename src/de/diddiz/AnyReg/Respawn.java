package de.diddiz.AnyReg;

import java.util.Set;

public class Respawn
{
	private final int type;
	private final int regDelay;
	private final double regChance;
	private final boolean useBlacklist;
	private final Set<Integer> canReplace;
	
	public Respawn(int type, int regDelay, double regChance, boolean useBlacklist, Set<Integer> canReplace) {
		this.type = type;
		this.regDelay = regDelay;
		this.regChance = regChance;
		this.useBlacklist = useBlacklist;
		this.canReplace = canReplace;
	}
	
	public int getType() {
		return type;
	}

	public int getRegDelay() {
		return regDelay;
	}

	public double getRegChance() {
		return regChance;
	}

	public boolean isUseBlacklist() {
		return useBlacklist;
	}

	public Set<Integer> getCanReplace() {
		return canReplace;
	}
	
	@Override
	public int hashCode() {
		return type;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (type == ((Respawn)obj).type) {
			return false;
		}
		return true;
	}
}
