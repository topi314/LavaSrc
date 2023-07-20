package com.github.topi314.lavasrc.search;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import kotlin.annotation.jvm

import java.util.List;

public interface SearchSourceManager {

	@NotNull
	String getSourceName();

	@Nullable
	SearchResult loadSearch(String query, @ReadOnly List<String> types);

	void shutdown();
}
