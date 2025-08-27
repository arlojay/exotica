package com.arlojay.exotica;

import com.arlojay.exotica.worldgen.ExoticaZoneGenerator;
import dev.puzzleshq.puzzleloader.cosmic.core.modInitialises.ModInit;
import dev.puzzleshq.puzzleloader.cosmic.game.GameRegistries;
import dev.puzzleshq.puzzleloader.cosmic.game.events.zone.EventRegisterZoneGenerator;
import finalforeach.cosmicreach.util.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExoticaMod implements ModInit {
    public static final String MOD_ID = "exotica";
    public static final Identifier MOD_NAME = Identifier.of(MOD_ID, "Exotica");
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Override
    public void onInit() {
        GameRegistries.COSMIC_EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onEvent(EventRegisterZoneGenerator event) {
        event.registerGenerator(ExoticaZoneGenerator::new);
    }
}
