ARG TAG

FROM ghcr.io/lavalink-devs/lavalink:${TAG}

USER root

RUN apt-get update && \
	apt-get install -y python3 curl && \
	curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/bin/yt-dlp && \
	chmod +x /usr/bin/yt-dlp

USER lavalink
