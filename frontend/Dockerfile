# --- build stage ---
FROM node:20-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

# --- production stage ---
FROM nginx:alpine

# ビルド成果物をnginxへ
COPY --from=builder /app/dist /usr/share/nginx/html

# ポート
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]