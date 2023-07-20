package com.github.topi314.lavasrc.plugin.search;

import com.github.topi314.lavasrc.protocol.SearchResult;
import com.github.topi314.lavasrc.search.SearchManager;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
public class SearchRestHandler {

	private static final Logger log = LoggerFactory.getLogger(SearchRestHandler.class);

	private final SearchManager searchManager;

	public SearchRestHandler(SearchManager searchManager) {
		this.searchManager = searchManager;
		log.info("SearchRestHandler created");
	}

	@GetMapping("/v4/loadsearch")
	public ResponseEntity<SearchResult> loadSearch(HttpServletRequest request, @RequestParam String query, @RequestParam(required = false) String rawTypes) {
		log.info("loadSearch: query={}", query);
		var types = rawTypes == null ? null : Arrays.asList(rawTypes.split(","));
		var result = searchManager.loadSearch(query, types);
		if (result != null) {
			return ResponseEntity.ok(result);
		}
		return ResponseEntity.notFound().build();
	}
}
