package eu.solven.hutter_prize.reversible.enwik;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import eu.solven.hutter_prize.IReversibleCompressor;

public class HeaderArticlesFooter implements IReversibleCompressor {
	private static final Logger LOGGER = LoggerFactory.getLogger(HeaderArticlesFooter.class);

	@Override
	public Object compress(Object input) throws IOException {
		byte[] bytes = (byte[]) input;
		String string = new String(bytes, StandardCharsets.UTF_8);

		LOGGER.info("{}{}{}", "Head of content:", System.lineSeparator(), string.substring(0, 16 * 1024));

		String HEADER_FOOTER = "</siteinfo>";
		int indexOfHeaderEnd = string.indexOf(HEADER_FOOTER) + HEADER_FOOTER.length();

		return ImmutableMap.builder()
				.put("header", string.substring(0, indexOfHeaderEnd))
				.put("body", string.substring(indexOfHeaderEnd))
				.put("footer", "")
				.build();
	}

	@Override
	public Object decompress(Object output) throws IOException {
		Map<String, ?> asStrings = (Map<String, ?>) output;

		String asString = Stream.of("header", "body", "footer")
				.map(k -> (String) asStrings.get(k))
				.filter(s -> null != s)
				.collect(Collectors.joining());

		return asString.getBytes(StandardCharsets.UTF_8);
	}

}
