package eu.solven.hutter_prize;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

public class HPUtils {

	// http://mattmahoney.net/dc/enwik8.zip
	// http://mattmahoney.net/dc/enwik9.zip
	final static Resource zipped = new FileSystemResource("/Users/blacelle/workspace3/enwik8.zip");

	public static String nameAndSize(Object resource) {
		if (resource instanceof FileSystemResource) {
			FileSystemResource fileResource = (FileSystemResource) resource;
			try {
				return fileResource.getURI() + "=" + Files.size(fileResource.getFile().toPath());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		} else if (resource instanceof byte[]) {
			return "byte[].length=" + ((byte[]) resource).length;
		} else if (resource instanceof char[]) {
			return "char[].length=" + ((char[]) resource).length;
		} else if (resource instanceof String) {
			String string = (String) resource;
			return "String.length()=" + string.length() + " " + nameAndSize(string.toCharArray());
		} else if (resource instanceof List<?>) {
			List<?> list = (List<?>) resource;
			return "List.size()=" + list.size()
					+ " "
					+ list.stream().map(o -> nameAndSize(o)).limit(10).collect(Collectors.joining(" "));
		} else if (resource instanceof Map<?, ?>) {
			Map<?, ?> string = (Map<?, ?>) resource;
			return "Map.length()=" + string.size()
					+ " "
					+ string.entrySet()
							.stream()
							.limit(10)
							.map(e -> e.getKey() + " -> " + nameAndSize(e.getValue()))
							.collect(Collectors.joining(" "));
		} else {
			return String.valueOf(resource);
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
			StringBuilder sb = new StringBuilder();
			while (m.find()) {
				String group = m.group();
				String sanitizedWhitespace = group
						// ` ` is replaced by a placeholder, to be restored later
						.replace(' ', '_')
						.replace("\r", Matcher.quoteReplacement("\\r"))
						.replace("\n", Matcher.quoteReplacement("\\n"))
						.replaceAll(whitespacesRegex, "[ ]")
						.replace('_', ' ');
				m.appendReplacement(sb, sanitizedWhitespace);
			}
			m.appendTail(sb);
			humanFriendlySeparator = sb.toString();
		}
		return humanFriendlySeparator;
	}
}
