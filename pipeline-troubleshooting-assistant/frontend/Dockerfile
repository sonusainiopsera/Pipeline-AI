# Runtime image using a host-built Vite dist.
# Build first: npm install && npm run build
FROM nginx:stable-alpine

RUN rm -f /etc/nginx/conf.d/default.conf

COPY dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --spider -q http://127.0.0.1:80 || exit 1

CMD ["nginx", "-g", "daemon off;"]
