# Security

## Ephemeral authentication

TG//MEDIA v14 intentionally uses `MemoryStorage` for the Telegram session.

The application does not persist API ID, API Hash, phone number, Telegram login code, 2FA password, session authorization, scan checkpoints or Activity using browser storage.

Closing or reloading the page removes that in-memory state.

## Do not hard-code credentials

Never edit `index.html` to contain a real personal API Hash before committing the file to a public repository.

Every user should enter their own Telegram API ID and API Hash at runtime.

## Third-party runtime

The single-file edition imports pinned browser packages from `esm.sh`.

If your threat model requires zero third-party JavaScript at runtime, create a bundled/self-hosted edition instead of the single-file CDN edition.

## HTTPS

Deploy with HTTPS. GitHub Pages and Cloudflare Pages provide HTTPS automatically.

## Reporting

If you publish a fork publicly, add your preferred security contact or GitHub private vulnerability reporting instructions here.
