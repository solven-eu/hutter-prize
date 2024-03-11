package eu.solven.hutter_prize.reversible.utilities;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.solven.hutter_prize.IReversibleCompressor;
import eu.solven.hutter_prize.kanzi_only.KanziCompressor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
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
public class PersistingInterceptor implements IReversibleCompressor {
	private static final Logger LOGGER = LoggerFactory.getLogger(PersistingInterceptor.class);

	@Override
	public Object compress(Object input) throws IOException {
		Path tmpFolder = Files.createTempDirectory("hutter_prize");
		LOGGER.info("Persisting into {}", tmpFolder);

		if (input instanceof byte[]) {
			// Happens after Kanzi
			byte[] bytes = (byte[]) input;

			Files.write(tmpFolder.resolve("whole.knz"), bytes);
		} else {
			Map<String, Object> map = (Map<String, Object>) input;

			try (ZipOutputStream zipOutputStream =
					new ZipOutputStream(new FileOutputStream(tmpFolder.resolve("whole.zip").toFile()))) {
				persistMap(tmpFolder, map, (p, bytes) -> {
					try {
						// Write the plain file
						Files.write(p, bytes);

						// And accumulate in a ZIP
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
		}

		LOGGER.info("Done persisting");

		return input;
	}

	private void persistMap(Path parentFolder, Map<String, Object> map, BiConsumer<Path, byte[]> consume) {
		parentFolder.toFile().mkdirs();
		map.forEach((k, v) -> {
			Path outputPath = parentFolder.resolve(k);

			if (v instanceof String) {
				consume.accept(Paths.get(outputPath.toString() + ".str"),
						v.toString().getBytes(StandardCharsets.UTF_8));
			} else if (v instanceof IntList) {
				IntList intList = (IntList) v;
				persistCollection(k, outputPath, intList, consume);
			} else if (v instanceof int[]) {
				IntList intList = new IntArrayList((int[]) v);
				persistCollection(k, outputPath, intList, consume);
			} else if (v instanceof Collection<?>) {
				Collection<?> c = (Collection<?>) v;
				persistCollection(k, outputPath, c, consume);
			} else if (v instanceof byte[]) {
				byte[] c = (byte[]) v;
				consume.accept(Paths.get(outputPath.toString() + ".bin"), c);
			} else if (v instanceof Map<?, ?>) {
				Map<String, Object> vAsMap = (Map<String, Object>) v;

				if (vAsMap.isEmpty()) {
					LOGGER.debug("We skip empty maps");
				} else if (vAsMap.values().iterator().next() instanceof String) {
					// We assume the whole Map is String -> String
					persistCollection(k, outputPath, vAsMap.entrySet(), consume);
				} else if (vAsMap.getClass().getName().startsWith("it.unimi.dsi.fastutil.")) {
					consume.accept(Paths.get(outputPath.toString() + ".txt"),
							vAsMap.toString().getBytes(StandardCharsets.UTF_8));
				} else {
					persistMap(outputPath, vAsMap, consume);
				}
			} else {
				// We log `parentFolder` to have access to parent keys
				LOGGER.warn("Not managed: {} {} given {}", k, v.getClass().getSimpleName(), parentFolder);
			}
		});

		if (parentFolder.toFile().list().length == 0) {
			// Not a single file has been written: remove the empty folder
			parentFolder.toFile().delete();
		}
	}

	private void persistCollection(String k, Path outputPath, Collection<?> intList, BiConsumer<Path, byte[]> consume) {
		String separator = findSeparator(intList);

		String asString = intList.stream().map(String::valueOf).collect(Collectors.joining(separator));

		consume.accept(Paths.get(outputPath.toString() + ".col"), asString.getBytes(StandardCharsets.UTF_8));
	}

	private String findSeparator(Collection<?> intList) {
		return Stream.of(" ", "\n", "\n\n", "\n-\n", "\n~\n", "\n!\n", "\n-----\n", "\n-1-2-3-4-5-\n")
				.filter(separator -> intList.stream()
						.map(String::valueOf)
						.noneMatch(subString -> subString.contains(separator)))
				.findFirst()
				.orElseThrow(() -> {
					return new IllegalStateException("NoValidSeparator");
				});
	}

	@Override
	public Object decompress(Object output) throws IOException {
		return output;
	}

}
