package com.moepus.byepregen.coordinator;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.IEnvironment;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.IOrderedProvider;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enters mod discovery before NeoForge resolves Jar-in-Jar dependencies.
 */
public final class JarInJarCoordinatorLocator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final String PAYLOAD_ENTRY = "META-INF/byepregen/payload.jar";

    @Override
    public int getPriority() {
        return IOrderedProvider.HIGHEST_SYSTEM_PRIORITY;
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        if (!INSTALLED.compareAndSet(false, true)) {
            LOGGER.warn("[BYEPREGEN-COORD] interceptor was already installed; leaving discovery unchanged");
            return;
        }
        JarInJarCoordinator.install(pipeline, LOGGER);
        registerAsMod(context, pipeline);
    }

    private void registerAsMod(ILaunchContext context, IDiscoveryPipeline pipeline) {
        try {
            Path sourceJar = resolveOwnJar(context);
            Path payloadJar = materializePayloadJar(context, sourceJar);
            if (pipeline.addPath(payloadJar, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR).isEmpty()) {
                LOGGER.error("[BYEPREGEN-COORD] failed to register embedded payload {} as a normal mod", payloadJar);
            }
        } catch (Exception exception) {
            LOGGER.error("[BYEPREGEN-COORD] failed to resolve the coordinator jar path for normal mod registration", exception);
        }
    }

    private Path resolveOwnJar(ILaunchContext context) throws IOException {
        Path gameDirectory = context.environment().getProperty(IEnvironment.Keys.GAMEDIR.get())
                .orElseThrow(() -> new IllegalStateException("game directory is unavailable during mod discovery"));
        String fmlSource = ServiceLoaderUtil.identifySourcePath(context, this);
        Path fmlPath = Path.of(fmlSource);
        Path resolvedFmlPath = fmlPath.isAbsolute() ? fmlPath : gameDirectory.resolve(fmlPath);
        if (Files.isRegularFile(resolvedFmlPath)) {
            return resolvedFmlPath;
        }

        try (var files = Files.list(gameDirectory.resolve("mods"))) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(JarInJarCoordinatorLocator::containsCoordinatorService)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("could not find the coordinator jar in mods"));
        }
    }

    private static boolean containsCoordinatorService(Path path) {
        try (JarFile jar = new JarFile(path.toFile())) {
            var service = jar.getJarEntry("META-INF/services/net.neoforged.neoforgespi.locating.IModFileCandidateLocator");
            if (service == null) {
                return false;
            }
            try (var input = jar.getInputStream(service)) {
                return new String(input.readAllBytes()).contains(JarInJarCoordinatorLocator.class.getName());
            }
        } catch (IOException ignored) {
            return false;
        }
    }

    private Path materializePayloadJar(ILaunchContext context, Path sourceJar) throws IOException, NoSuchAlgorithmException {
        Path gameDirectory = context.environment().getProperty(IEnvironment.Keys.GAMEDIR.get())
                .orElseThrow(() -> new IllegalStateException("game directory is unavailable during mod discovery"));
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(sourceJar))).substring(0, 16);
        Path cacheDirectory = gameDirectory.resolve("libraries").resolve("byepregen-runtime");
        Path payloadJar = cacheDirectory.resolve("byepregen-payload-" + fingerprint + ".jar");
        if (Files.isRegularFile(payloadJar)) {
            return payloadJar;
        }

        Files.createDirectories(cacheDirectory);
        Path temporaryJar = Files.createTempFile(cacheDirectory, "byepregen-payload-", ".tmp");
        try {
            extractPayloadJar(sourceJar, temporaryJar);
            Files.move(temporaryJar, payloadJar, StandardCopyOption.ATOMIC_MOVE);
            return payloadJar;
        } finally {
            Files.deleteIfExists(temporaryJar);
        }
    }

    private static void extractPayloadJar(Path sourceJar, Path targetJar) throws IOException {
        try (JarFile source = new JarFile(sourceJar.toFile())) {
            JarEntry payload = source.getJarEntry(PAYLOAD_ENTRY);
            if (payload == null) {
                throw new IOException("coordinator jar is missing embedded payload " + PAYLOAD_ENTRY);
            }
            try (var input = source.getInputStream(payload)) {
                Files.copy(input, targetJar, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
