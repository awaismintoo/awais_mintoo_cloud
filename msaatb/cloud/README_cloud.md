# Run MSAATB on a cloud server (works when your phone and home internet are off)

**What runs there:** the MSAATB Python engine (`msaatb_engine.py`). It is paper money unless you switch live on.

## Easiest: Render.com (about 7 USD a month for an always-on worker)
1. Make a free account on render.com and connect your GitHub.
2. New > Blueprint > choose `awaismintoo/awais_mintoo_cloud`. Render reads `msaatb/cloud/render.yaml`.
3. Leave the keys empty for paper money and press Apply. Logs show every trade.

## Live money (only after paper results are good)
In the service's Environment page, fill in:
- `MSAATB_KEY`, `MSAATB_SECRET` (and `MSAATB_PASS` for OKX/Bitget). Use an API key with **trading only, never withdrawals**, and restrict it to the server's IP if the exchange allows it.
- `MSAATB_ARGS` = `--exchange binance --live --manage-manual --risk 0.5 --lev 3`
- `MSAATB_CONFIRM` = `YES` (this replaces typing YES, because a server has no keyboard).

## Any VPS (DigitalOcean, AWS Lightsail, Hetzner)
```
git clone https://github.com/awaismintoo/awais_mintoo_cloud && cd awais_mintoo_cloud/msaatb
docker build -f cloud/Dockerfile -t msaatb . && docker run -d --restart=always -v msaatb:/data --name msaatb msaatb
```
Live: add `-e MSAATB_KEY=... -e MSAATB_SECRET=... -e MSAATB_CONFIRM=YES -e MSAATB_ARGS="--exchange binance --live --manage-manual"`.

**Stop it:** Render: Suspend the service. VPS: `docker stop msaatb`. The exchange-side stop orders stay in place.
Keys live only in the server's settings: never put them in GitHub or in chat.
