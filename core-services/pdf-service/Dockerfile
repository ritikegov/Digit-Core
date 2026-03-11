# syntax=docker/dockerfile:1.4
FROM node:10 AS builder

ARG WORK_DIR
ENV npm_config_cache=/tmp
WORKDIR /app
COPY ${WORK_DIR}/ .
RUN npm install

FROM node:10
WORKDIR /app
COPY --from=builder /app /app

ENV PORT=8080
EXPOSE 8080

CMD ["npm", "start"]
