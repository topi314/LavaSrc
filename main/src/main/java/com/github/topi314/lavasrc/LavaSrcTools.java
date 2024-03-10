package com.github.topi314.lavasrc;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class LavaSrcTools {

	private static final Logger log = LoggerFactory.getLogger(LavaSrcTools.class);

	@Nullable
	public static JsonBrowser fetchResponseAsJson(HttpInterface httpInterface, HttpUriRequest request) throws IOException {
		try (CloseableHttpResponse response = httpInterface.execute(request)) {
			int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode == HttpStatus.SC_NOT_FOUND) {
				var data = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
				log.error("Server responded with not found to '{}': {}", request.getURI(), data);
				return null;
			} else if (statusCode == HttpStatus.SC_NO_CONTENT) {
				log.error("Server responded with not content to '{}'", request.getURI());
				return null;
			} else if (!HttpClientTools.isSuccessWithContent(statusCode)) {
				var data = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
				log.error("Server responded with an error to '{}': {}", request.getURI(), data);
				throw new FriendlyException("Server responded with an error.", SUSPICIOUS,
					new IllegalStateException("Response code from channel info is " + statusCode));
			}

			var data = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
			log.debug("Response from '{}' was successful: {}", request.getURI(), data);
			return JsonBrowser.parse(data);
		}
	}
}
