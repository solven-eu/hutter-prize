package eu.solven.hutter_prize.reversible;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.solven.hutter_prize.IReversibleCompressor;

public class HeaderArticlesFooter implements IReversibleCompressor {
	private static final Logger LOGGER = LoggerFactory.getLogger(HeaderArticlesFooter.class);

	@Override
	public Object compress(Object input) throws IOException {
		byte[] bytes = (byte[]) input;
		String string = new String(bytes, StandardCharsets.UTF_8);

		LOGGER.info("{}{}{}", "Head of content:", System.lineSeparator(), string.substring(0, 2 * 1024));

		String HEADER_FOOTER = "</siteinfo>";
		int indexOfHeaderEnd = string.indexOf(HEADER_FOOTER) + HEADER_FOOTER.length();

		return Arrays.asList(string.substring(0, indexOfHeaderEnd), string.substring(indexOfHeaderEnd), "");
	}

	@Override
	public Object decompress(Object output) throws IOException {
		List<String> asStrings = (List<String>) output;

		return asStrings.stream().collect(Collectors.joining()).getBytes(StandardCharsets.UTF_8);
	}

}
