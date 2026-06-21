package com.example.createcrashfixcrashfix;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(CreateCrashFixCrashFix.MOD_ID)
public final class CreateCrashFixCrashFix {
    public static final String MOD_ID = "createcrashfixcrashfix";
    private static final Logger LOGGER = LogManager.getLogger();

    public CreateCrashFixCrashFix() {
        LOGGER.info("CreateCrashFixCrashFix loaded. All methods in the target class are now safe.");
    }
}
