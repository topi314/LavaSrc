ARG TAG

FROM ghcr.io/lavalink-devs/lavalink:${TAG}

USER root

RUN apk add --no-cache python3 curl && \
	curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/bin/yt-dlp && \
	chmod +x /usr/bin/yt-dlp

USER lavalink
