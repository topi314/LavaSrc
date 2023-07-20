package com.github.topi314.lavasrc.search;

import java.util.List;

public class SearchManager {

	private final List<SearchSourceManager> sourceManagers;

	public SearchManager(List<SearchSourceManager> sourcesManagers) {
		this.sourceManagers = sourcesManagers;
	}

	public void registerSourceManager(SearchSourceManager sourceManager) {
		sourceManagers.add(sourceManager);
	}

	public <T extends SearchSourceManager> T source(Class<T> klass) {
		for (SearchSourceManager sourceManager : sourceManagers) {
			if (klass.isAssignableFrom(sourceManager.getClass())) {
				return (T) sourceManager;
			}
		}

		return null;
	}

	public List<SearchSourceManager> getSourceManagers() {
		return this.sourceManagers;
	}

	public void shutdown() {
		for (SearchSourceManager sourceManager : this.sourceManagers) {
			sourceManager.shutdown();
		}
	}

	public List<SearchItem> loadSearch(String query) {
		for (SearchSourceManager sourceManager : this.sourceManagers) {
			List<SearchItem> searchItems = sourceManager.loadSearch(query);
			if (searchItems != null) {
				return searchItems;
			}
		}
		return null;
	}


}
