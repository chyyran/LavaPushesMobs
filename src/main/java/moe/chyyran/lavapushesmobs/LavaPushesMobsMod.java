package moe.chyyran.lavapushesmobs;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
	modid = LavaPushesMobsMod.MODID,
	name = LavaPushesMobsMod.NAME,
	version = LavaPushesMobsMod.VERSION,
	acceptableRemoteVersions = "*"
)
public class LavaPushesMobsMod {
	public static final String MODID = "lavapushesmobs";
	public static final String NAME = "LavaPushesMobs";
	public static final String VERSION = "1.0";
	
	public static final Logger LOGGER = LogManager.getLogger(MODID);
	
	@Mod.EventHandler
	public void preinit(FMLPreInitializationEvent preinit) {
		LOGGER.info("Welcome back 1.16 lava pushing mobs");
	}
}
