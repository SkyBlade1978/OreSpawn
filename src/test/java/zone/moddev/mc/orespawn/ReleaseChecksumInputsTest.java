package zone.moddev.mc.orespawn;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleaseChecksumInputsTest {
	@Test
	void checksumTaskTracksAllThreeJarsForIncrementalBuilds() throws Exception {
		String build = Files.readString(Path.of("gradle/release/artifacts.gradle"));
		int start = build.indexOf("tasks.register('writeReleaseChecksums')");
		int end = build.indexOf("tasks.register(", start + 1);
		String task = build.substring(start, end);
		assertTrue(task.contains("inputs.files("), "changed jars must invalidate saved checksums");
		assertTrue(task.contains("releaseJar.flatMap"));
		assertTrue(task.contains("tasks.named('sourcesJar', Jar)"));
		assertTrue(task.contains("tasks.named('javadocJar', Jar)"));
		assertFalse(task.contains("upToDateWhen"), "track inputs instead of disabling Gradle's checks");
	}
}
