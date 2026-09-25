package com.company.incidentdesk.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackagedApplicationTest {
    private static final int MACH_O_64_MAGIC = 0xfeedfacf;
    private static final int CPU_TYPE_ARM64 = 0x0100000c;
    private static final int CPU_TYPE_X86_64 = 0x01000007;
    private static final long STARTUP_TIMEOUT_SECONDS = 45;

    @TempDir
    Path temporaryDirectory;

    @Test
    void packagesSeparateMacArchitecturesWithoutTestCode() throws Exception {
        verifyMacLibraries(packagedJar("armJar"), CPU_TYPE_ARM64, true);
        verifyMacLibraries(packagedJar("intelJar"), CPU_TYPE_X86_64, false);
    }

    @Test
    void launchesActualJarAndShowsApplicationWindow() throws Exception {
        String architecture = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        boolean arm = architecture.equals("aarch64") || architecture.equals("arm64");
        boolean mac = System.getProperty("os.name").startsWith("Mac");
        assumeTrue(!arm || mac, "No ARM Linux/Windows packaged artifact is distributed");
        Path jar = packagedJar(arm ? "armJar" : "intelJar");
        Path log = temporaryDirectory.resolve("startup.log");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Process process = new ProcessBuilder(java.toString(),
                "-Dincidentdesk.dataDir=" + temporaryDirectory.resolve("data"),
                "-Djavafx.cachedir=" + temporaryDirectory.resolve("javafx-cache"),
                "-javaagent:" + packagedJar("probeJar"), "-jar", jar.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Packaged startup timed out");
            String output = Files.readString(log);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains(PackagedStartupProbe.SUCCESS_MARKER), output);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Packaged process did not terminate");
            }
        }
    }

    private static Path packagedJar(String propertySuffix) {
        String path = System.getProperty("incidentdesk.test." + propertySuffix);
        assertNotNull(path, "Run this packaging test through Gradle");
        return Path.of(path);
    }

    private static void verifyMacLibraries(Path path, int expectedCpu, boolean macOnly) throws Exception {
        try (JarFile jar = new JarFile(path.toFile())) {
            assertEquals("com.company.incidentdesk.startup.IncidentDeskLauncher",
                    jar.getManifest().getMainAttributes().getValue("Main-Class"));
            assertNotNull(jar.getEntry("libglass.dylib"));
            assertNotNull(jar.getEntry("libprism_es2.dylib"));
            assertNotNull(jar.getEntry("libprism_sw.dylib"));
            for (var entry : jar.stream().toList()) {
                assertFalse(entry.getName().contains("PackagedStartupProbe"), "Test agent must not ship");
                if (macOnly) {
                    assertFalse(entry.getName().endsWith(".dll") || entry.getName().endsWith(".so"),
                            "Apple Silicon artifact must not contain other platforms' natives");
                }
                if (entry.getName().endsWith(".dylib")) {
                    try (var input = jar.getInputStream(entry)) {
                        ByteBuffer header = ByteBuffer.wrap(input.readNBytes(8)).order(ByteOrder.LITTLE_ENDIAN);
                        assertEquals(MACH_O_64_MAGIC, header.getInt(), entry.getName());
                        assertEquals(expectedCpu, header.getInt(), entry.getName());
                    }
                }
            }
        }
    }
}
