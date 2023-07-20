package com.github.topi314.lavasrc.plugin.search;

import com.github.topi314.lavasrc.search.SearchManager;
import com.github.topi314.lavasrc.search.SearchManagerConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Collection;

public class SearchManagerBean {

	@Bean
	public SearchManager SearchManagerSupplier(Collection<SearchManagerConfiguration> searchManagerConfigurations) {
		var manager = new SearchManager();

		for (var config : searchManagerConfigurations) {
			manager = config.configure(manager);
		}

		return manager;
	}
}
