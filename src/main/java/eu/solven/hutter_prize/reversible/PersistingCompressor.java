package eu.solven.hutter_prize.reversible;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.solven.hutter_prize.IReversibleCompressor;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * This persist the data used for decompressing. It can be used for unittest purposes, and testing not-Java tools over
 * some of our inputs (e.g. cmix).
 * 
 * This will also generate a zip file, demonstrating how naive compression behave on our reorganized files
 * 
 * @author Benoit Lacelle
 *
 */
public class PersistingCompressor implements IReversibleCompressor {
	private static final Logger LOGGER = LoggerFactory.getLogger(PersistingCompressor.class);

	@Override
	public Object compress(Object input) throws IOException {
		Path tmpFolder = Files.createTempDirectory("hutter_prize");
		LOGGER.info("Persisting into {}", tmpFolder);

		Map<String, Object> map = (Map<String, Object>) input;

		try (ZipOutputStream zipOutputStream =
				new ZipOutputStream(new FileOutputStream(tmpFolder.resolve("whole.zip").toFile()))) {
			persistMap(tmpFolder, map, (p, bytes) -> {
				try {
					Files.write(p, bytes);

					{
						Path filePath = tmpFolder.relativize(p);
						zipOutputStream.putNextEntry(new ZipEntry(filePath.toString()));

						zipOutputStream.write(bytes);

						zipOutputStream.closeEntry();
					}
				} catch (IOException e) {
					throw new UncheckedIOException("Issue persisting " + p, e);
				}

			});
		}

		LOGGER.info("Done persisting");

		return input;
	}

	private void persistMap(Path parentFolder, Map<String, Object> map, BiConsumer<Path, byte[]> consume) {
		parentFolder.toFile().mkdirs();
		map.forEach((k, v) -> {
			Path outputPath = parentFolder.resolve(k);

			if (v instanceof String) {
				consume.accept(outputPath, v.toString().getBytes(StandardCharsets.UTF_8));
			} else if (v instanceof IntList) {
				IntList intList = (IntList) v;
				String asString = intList.intStream().mapToObj(Integer::toString).collect(Collectors.joining("\r\n"));

				consume.accept(outputPath, asString.getBytes(StandardCharsets.UTF_8));
			} else if (v instanceof Collection<?>) {
				Collection<?> intList = (Collection<?>) v;
				persistCollection(k, outputPath, intList, consume);
			} else if (v instanceof Map<?, ?>) {
				Map<String, Object> vAsMap = (Map<String, Object>) v;

				if (vAsMap.isEmpty()) {
					LOGGER.debug("We skip empty maps");
				} else if (vAsMap.values().iterator().next() instanceof String) {
					// We assume the whole Map is String -> String
					persistCollection(k, outputPath, vAsMap.entrySet(), consume);
				} else {
					persistMap(outputPath, vAsMap, consume);
				}

			} else {
				// We log `parentFolder` to have access to parent keys
				LOGGER.warn("Not managed: {} {} given {}", k, v.getClass().getSimpleName(), parentFolder);
			}
		});
	}

	private void persistCollection(String k, Path outputPath, Collection<?> intList, BiConsumer<Path, byte[]> consume) {
		String asString = intList.stream().map(String::valueOf).collect(Collectors.joining("\r\n"));

		consume.accept(outputPath, asString.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public Object decompress(Object output) throws IOException {
		return output;
	}

}
