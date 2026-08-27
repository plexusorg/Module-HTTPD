# Caddy fallback page

Use `down.html` when Caddy cannot reach Plex HTTPD. The file contains its own assets, so Caddy can serve it while HTTPD is offline. The browser reloads the page every 30 seconds.

## Install

1. Copy `down.html` to the Caddy server:

   ```sh
   install -m 644 down.html /etc/caddy/pages/down.html
   ```

2. Add an error handler to the site:

   ```caddy
   httpd.example.com {
       reverse_proxy 172.18.0.1:27192

       handle_errors 502 {
           root * /etc/caddy/pages
           rewrite * /down.html
           file_server {
               status 503
           }
       }
   }
   ```

The proxy reports a 502 internally when HTTPD is unavailable. The `status 503` setting changes the response sent to the client because Cloudflare replaces 502 pages with its own error page. This lets Cloudflare pass through `down.html` instead. You can remove `status 503` if you do not use Cloudflare, but you really should use it.

3. Check the Caddyfile and reload Caddy:

   ```sh
   caddy validate --config /etc/caddy/Caddyfile
   systemctl reload caddy
   ```

Change the site address, upstream address, and file path for your server.
