# Panel screenshots

Captured by `web/scripts/web-shots.mjs` in the **Web UI screenshots** workflow:
it boots the panel on a throwaway Postgres, logs in with a real browser and
publishes the PNGs to the `web-screenshots` branch.

| file | page |
| --- | --- |
| `01-login.png` | sign in |
| `02-dashboard.png` | dashboard |
| `03-keys.png` | key list + search |
| `04-generate.png` | generate keys |
| `05-key-detail.png` | one key |
| `06-server.png` | server control |
| `07-users.png` | user management |
| `07b-api-docs.png` | the public `/docs/api` reference |
| `08-mobile-dashboard.png` | dashboard at 390 px |
| `09-mobile-nav.png` | navigation drawer |
| `10-mobile-api-docs.png` | API reference at 390 px |
| `11-mobile-server.png` | server control at 390 px |

`overview.png` is the desktop pages in two columns, then the four phone captures
in a row. The panel is dark by default.

To run it by hand against a local panel:

    cd web && npm run db:dev &        # embedded Postgres
    npm run db:push && npm run dev    # schema + panel on :3000
    npx playwright install chromium
    node scripts/web-shots.mjs

Or point it at the deployment: `BASE_URL=https://kuro-panel-psi.vercel.app node web/scripts/web-shots.mjs`.
