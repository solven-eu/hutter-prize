package eu.solven.hutter_prize;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import eu.solven.pepper.logging.PepperLogHelper;
import it.unimi.dsi.fastutil.ints.IntList;

public class HPUtils {

	// http://mattmahoney.net/dc/enwik8.zip
	// http://mattmahoney.net/dc/enwik9.zip
	public static final Resource zipped = new FileSystemResource("/Users/blacelle/workspace3/enwik8.zip");

	public static String nameAndSize(Object resource) {
		if (resource instanceof FileSystemResource) {
			FileSystemResource fileResource = (FileSystemResource) resource;
			try {
				return fileResource.getURI() + "="
						+ PepperLogHelper.humanBytes(Files.size(fileResource.getFile().toPath()));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		} else if (resource instanceof byte[]) {
			return "byte[].length=" + PepperLogHelper.humanBytes(size(resource));
		} else if (resource instanceof char[]) {
			return "char[].length=" + PepperLogHelper.humanBytes(size(resource));
		} else if (resource instanceof String) {
			String string = (String) resource;
			return "String.length()=" + string.length() + " " + PepperLogHelper.humanBytes(size(resource));
		} else if (resource instanceof List<?>) {
			List<?> list = (List<?>) resource;

			if (list.stream().anyMatch(Map.class::isInstance)) {
				// This is a small List with structured data
				return "List.size()=" + list.size()
						+ " "
						+ list.stream().map(o -> nameAndSize(o)).collect(Collectors.joining(" "));
			} else {
				// this is a large List with raw data into
				return "List.size()=" + list.size() + " byte=" + PepperLogHelper.humanBytes(size(resource));
			}

		} else if (resource instanceof Map<?, ?>) {
			Map<String, ?> string = (Map<String, ?>) resource;
			return "Map.length()=" + string.size()
					+ " "
					+ string.entrySet()
							.stream()
							.sorted(Map.Entry.comparingByKey())
							.map(e -> e.getKey() + " -> " + PepperLogHelper.humanBytes(size(e.getValue())))
							.collect(Collectors.joining(" "));
		} else {
			return String.valueOf(resource);
		}
	}

	public static long size(Object resource) {
		if (resource instanceof FileSystemResource) {
			FileSystemResource fileResource = (FileSystemResource) resource;
			try {
				return Files.size(fileResource.getFile().toPath());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		} else if (resource instanceof byte[]) {
			return ((byte[]) resource).length;
		} else if (resource instanceof char[]) {
			return 4 * ((char[]) resource).length;
		} else if (resource instanceof String) {
			String string = (String) resource;
			return size(string.getBytes(StandardCharsets.UTF_8));
		} else if (resource instanceof IntList) {
			List<?> list = (List<?>) resource;
			return 4 * list.size();
		} else if (resource instanceof List<?>) {
			List<?> list = (List<?>) resource;
			return 8 * list.size() + list.stream().mapToLong(o -> size(o)).sum();
		} else if (resource instanceof Map<?, ?>) {
			Map<?, ?> string = (Map<?, ?>) resource;
			return 8 * string.size() + string.entrySet().stream().mapToLong(e -> size(e.getValue())).sum();
		} else if (resource == null) {
			return 0;
		} else {
			return Long.MAX_VALUE;
		}
	}

	public static String encodeWhitespaceCharacters(String separator) {
		// if (separator.contains("\r") || separator.contains("\n")) {
		// System.out.println();
		// }

		String humanFriendlySeparator;
		// https://stackoverflow.com/questions/4731055/whitespace-matching-regex-java
		String whitespacesRegex = "\\s+";
		// https://stackoverflow.com/questions/19737653/what-is-the-equivalent-of-regex-replace-with-function-evaluation-in-java-7
		{
			Pattern p = Pattern.compile(whitespacesRegex);
			Matcher m = p.matcher(separator);

			humanFriendlySeparator = m.replaceAll(mr -> {
				String group = mr.group();
				String sanitizedWhitespace = group
						// ` ` is replaced by a placeholder, to be restored later
						.replace(' ', '_')
						.replace("\r", Matcher.quoteReplacement("\\r"))
						.replace("\n", Matcher.quoteReplacement("\\n"))
						.replaceAll(whitespacesRegex, "[ ]")
						.replace('_', ' ');
				return sanitizedWhitespace;
			});

			Pattern p2 = Pattern.compile("\\p{C}");
			humanFriendlySeparator = p2.matcher(separator).replaceAll(mr -> {
				String group = mr.group();
				int[] codePoints = group.codePoints().toArray();

				if (codePoints.length != 1) {
					throw new IllegalArgumentException("Arg: " + Arrays.toString(codePoints));
				}

				return "{" + codePoints[0] + "}";
			});

		}
		return humanFriendlySeparator;
	}
}
