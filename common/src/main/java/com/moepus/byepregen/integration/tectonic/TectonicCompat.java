package com.moepus.byepregen.integration.tectonic;

import com.mojang.logging.LogUtils;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import dev.worldgen.tectonic.Tectonic;
import java.util.Objects;
import org.slf4j.Logger;

public final class TectonicCompat {
    public static final String MOD_ID = "tectonic";

    private TectonicCompat() {
    }

    public static boolean canUseRawSave() {
        return State.AVAILABILITY != Availability.INCOMPATIBLE;
    }

    public static void writeChunkData(NbtWriter writer) {
        if (State.AVAILABILITY == Availability.AVAILABLE) {
            Integration.writeChunkData(writer);
        }
    }

    static void writeBlendingVersion(NbtWriter writer, byte[] key, int version) {
        if (version != 0) {
            writer.putInt(key, version);
        }
    }

    private static Availability detectAvailability() {
        try {
            if (!ModEnvironment.isModLoaded(MOD_ID)) {
                return Availability.ABSENT;
            }
            Integration.validate();
            return Availability.AVAILABLE;
        } catch (RuntimeException | LinkageError throwable) {
            State.LOGGER.warn("Unable to establish Tectonic chunk-save compatibility; disabling raw chunk save",
                    throwable);
            return Availability.INCOMPATIBLE;
        }
    }

    private enum Availability {
        ABSENT,
        AVAILABLE,
        INCOMPATIBLE
    }

    private static final class State {
        private static final Logger LOGGER = LogUtils.getLogger();
        private static final Availability AVAILABILITY = detectAvailability();

        private State() {
        }
    }

    private static final class Integration {
        private static final byte[] BLENDING_KEY = NbtWriter.asciiName(
                Objects.requireNonNull(Tectonic.BLENDING_KEY, "Tectonic.BLENDING_KEY")
        );

        private Integration() {
        }

        private static void validate() {
            int ignored = Tectonic.BLENDING_VERSION;
        }

        private static void writeChunkData(NbtWriter writer) {
            writeBlendingVersion(writer, BLENDING_KEY, Tectonic.BLENDING_VERSION);
        }
    }
}
