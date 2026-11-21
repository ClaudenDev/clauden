FROM node:22-alpine

WORKDIR /app

COPY package.json ./
COPY src ./src
COPY config ./config
COPY scripts ./scripts

ENV CLAUDEN_HOST=0.0.0.0 \
    CLAUDEN_PORT=8788 \
    CLAUDEN_DATA_DIR=/data

RUN mkdir -p /data && chown -R node:node /data
USER node

EXPOSE 8788
VOLUME ["/data"]

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD wget -qO- http://127.0.0.1:8788/health || exit 1

CMD ["node", "src/server.mjs"]
