# RSS Cloud Sync Website — aaPanel deployment

The website is a static site and can be hosted directly by aaPanel's Nginx or Apache website service.

## Recommended document root

Set the aaPanel site document root to the repository's `website` directory after cloning the repository.

Example:

`/www/wwwroot/rss-cloud-sync/website`

## Deploy from the Oracle Cloud instance

Clone or pull the repository on the server:

```bash
cd /www/wwwroot
if [ -d rss-cloud-sync ]; then
  cd rss-cloud-sync
  git pull origin main
else
  git clone https://github.com/riyazamra1/RSS-Cloud-Sync.git rss-cloud-sync
fi
```

Then set the aaPanel website root to:

`/www/wwwroot/rss-cloud-sync/website`

No PHP runtime or database is required for the current site.

## SSL

In aaPanel, open the website → SSL → Let's Encrypt, select the domain, issue the certificate, and enable HTTPS redirect.

## Important production values to configure later

Before publishing download/purchase actions, replace the placeholder navigation targets with the real Google Play, Premium purchase, account and support endpoints for RSS Cloud Sync v1/v2.

Do not place API keys, payment secrets, cloud-provider secrets or database credentials in these static files.
