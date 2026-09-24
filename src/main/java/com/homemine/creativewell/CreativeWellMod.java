package com.homemine.creativewell;

import com.homemine.creativewell.config.CreativeGridConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * 「能开创造就好好开」/ Creative well if you can
 *
 * <p>本模组整体替换创造模式物品栏界面：动态尺寸的取物网格、左侧可滚动的选项卡栏、以及常驻的搜索栏。</p>
 */
@Mod(CreativeWellMod.MOD_ID)
public final class CreativeWellMod {
    public static final String MOD_ID = "creativewell";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreativeWellMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, CreativeGridConfig.SPEC);
        LOGGER.info("[creativewell] Creative well if you can loading");
    }
}
