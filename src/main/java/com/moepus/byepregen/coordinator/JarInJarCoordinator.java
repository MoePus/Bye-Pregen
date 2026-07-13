package com.moepus.byepregen.coordinator;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


// Wraps NeoForge's built-in Jar-in-Jar locator before it can add selected jars.
final class JarInJarCoordinator {
    private static final String EXCLUDE_PROPERTY = "byepregen.coordinator.exclude";
    private static final String JAR_IN_JAR_LOCATOR = "net.neoforged.fml.loading.moddiscovery.locators.JarInJarDependencyLocator";

    private JarInJarCoordinator() {
    }

    static void install(IDiscoveryPipeline pipeline, Logger logger) {
        try {
            Object discoverer = outerDiscoverer(pipeline);
            Field locatorsField = discoverer.getClass().getDeclaredField("dependencyLocators");
            Unsafe unsafe = unsafe();
            long offset = unsafe.objectFieldOffset(locatorsField);
            Object rawLocators = unsafe.getObject(discoverer, offset);
            if (!(rawLocators instanceof List<?> locatorList)) {
                logger.error("[BYEPREGEN-COORD] dependency locator field has unexpected type {}", rawLocators == null ? "null" : rawLocators.getClass().getName());
                return;
            }

            List<IDependencyLocator> replacement = new ArrayList<>(locatorList.size());
            int wrapped = 0;
            for (Object locator : locatorList) {
                if (locator instanceof IDependencyLocator dependencyLocator && JAR_IN_JAR_LOCATOR.equals(locator.getClass().getName())) {
                    replacement.add(new InterceptingJarInJarLocator(dependencyLocator, logger));
                    wrapped++;
                } else if (locator instanceof IDependencyLocator dependencyLocator) {
                    replacement.add(dependencyLocator);
                } else {
                    logger.warn("[BYEPREGEN-COORD] ignored unexpected dependency locator entry {}", locator);
                }
            }

            if (wrapped == 0) {
                logger.error("[BYEPREGEN-COORD] native Jar-in-Jar locator was not found; no interception installed. Found: {}",
                        locatorList.stream().map(value -> value.getClass().getName()).collect(Collectors.joining(", ")));
                return;
            }

            unsafe.putObject(discoverer, offset, List.copyOf(replacement));
        } catch (Throwable throwable) {
            logger.error("[BYEPREGEN-COORD] failed to install early Jar-in-Jar interceptor; startup will continue unchanged", throwable);
        }
    }

    static boolean shouldExclude(String filename) {
        return excludeRules().contains(filename.toLowerCase(Locale.ROOT));
    }

    private static Set<String> excludeRules() {
        String raw = System.getProperty(EXCLUDE_PROPERTY, "");
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Object outerDiscoverer(IDiscoveryPipeline pipeline) throws ReflectiveOperationException {
        Field outerField = pipeline.getClass().getDeclaredField("this$0");
        Unsafe unsafe = unsafe();
        return unsafe.getObject(pipeline, unsafe.objectFieldOffset(outerField));
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private record InterceptingJarInJarLocator(IDependencyLocator delegate,
                                               Logger logger) implements IDependencyLocator {

        @Override
        public void scanMods(List<IModFile> mods, IDiscoveryPipeline pipeline) {
            delegate.scanMods(mods, new FilteringPipeline(pipeline, logger));
        }

        @Override
        public int getPriority() {
            return delegate.getPriority();
        }

        @Override
        public String toString() {
            return "byepregen-coordinator(" + delegate + ')';
        }
    }

    private record FilteringPipeline(IDiscoveryPipeline delegate, Logger logger) implements IDiscoveryPipeline {

        @Override
        public Optional<IModFile> addPath(List<Path> paths, ModFileDiscoveryAttributes attributes, IncompatibleFileReporting reporting) {
            boolean excluded = paths.stream().map(Path::getFileName).filter(Objects::nonNull).map(Path::toString)
                    .anyMatch(JarInJarCoordinator::shouldExclude);
            if (excluded) {
                logger.warn("[BYEPREGEN-COORD] blocked Jar-in-Jar path candidate {}", paths);
                return Optional.empty();
            }
            return delegate.addPath(paths, attributes, reporting);
        }

        @Override
        public Optional<IModFile> addJarContent(JarContents contents, ModFileDiscoveryAttributes attributes, IncompatibleFileReporting reporting) {
            return delegate.addJarContent(contents, attributes, reporting);
        }

        @Override
        public boolean addModFile(IModFile modFile) {
            if (shouldExclude(modFile.getFileName())) {
                logger.warn("[BYEPREGEN-COORD] blocked selected Jar-in-Jar jar {}", describe(modFile));
                return false;
            }
            return delegate.addModFile(modFile);
        }

        @Override
        public IModFile readModFile(JarContents contents, ModFileDiscoveryAttributes attributes) {
            IModFile modFile = delegate.readModFile(contents, attributes);
            if (modFile == null) {
                return null;
            }
            if (shouldExclude(modFile.getFileName())) {
                logger.warn("[BYEPREGEN-COORD] blocked Jar-in-Jar candidate {} from parent {}", describe(modFile), describe(attributes.parent()));
                return null;
            }
            return modFile;
        }

        @Override
        public void addIssue(ModLoadingIssue issue) {
            delegate.addIssue(issue);
        }
    }

    private static String describe(IModFile modFile) {
        if (modFile == null) {
            return "<none>";
        }
        return modFile.getFileName() + " (" + modFile.getFilePath() + ')';
    }
}
