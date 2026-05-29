# SSL Certificates

Place your SSL certificate files here:

- `fullchain.pem` — full certificate chain (cert + intermediates)
- `privkey.pem`   — private key

## Quick setup with Let's Encrypt (Certbot)

```bash
# Install certbot
sudo apt install certbot

# Get certificate (stop nginx first if port 80 is busy)
sudo certbot certonly --standalone -d api.yourdomain.com

# Copy to this directory
cp /etc/letsencrypt/live/api.yourdomain.com/fullchain.pem ./fullchain.pem
cp /etc/letsencrypt/live/api.yourdomain.com/privkey.pem ./privkey.pem
chmod 600 ./privkey.pem
```

## Start with nginx (production mode)

```bash
docker compose --profile production up -d
```

Without `--profile production`, nginx is skipped and only the backend services start.
