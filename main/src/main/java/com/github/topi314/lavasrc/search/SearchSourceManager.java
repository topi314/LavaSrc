package com.github.topi314.lavasrc.search;

import java.util.List;

public interface SearchSourceManager {

	String getSourceName();

	List<SearchItem> loadSearch(String query);

	void shutdown();
}
