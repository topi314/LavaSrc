package com.github.topi314.lavasrc.search;

import com.github.topi314.lavasrc.protocol.SearchResult;
import kotlin.annotations.jvm.ReadOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface SearchSourceManager {

	@NotNull
	String getSourceName();

	@Nullable
	SearchResult loadSearch(@NotNull String query, @NotNull @ReadOnly List<String> types);

	void shutdown();
}
