# Этот Dockerfile нужен исключительно для того, чтобы пройти стадию 
# 'Build & Push Image' в вашей Jenkins Shared Library (dockerComposePipeline), 
# которая ожидает наличие Dockerfile в проекте. 
# На самом деле docker-compose.yml использует внешний образ itzg/minecraft-server.

FROM alpine:latest
RUN echo "Minecraft pipeline dummy image"
