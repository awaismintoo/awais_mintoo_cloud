#!/bin/bash
# One-command installer for a small Ubuntu/Debian server (run as root):  bash install_server.sh
set -e
[ "$(id -u)" = 0 ] || { echo "Run as root (sudo bash install_server.sh)"; exit 1; }
DIR=/opt/mintoo-alerts
apt-get update -y && apt-get install -y python3 python3-pip
id mintoo >/dev/null 2>&1 || useradd --system --home $DIR --shell /usr/sbin/nologin mintoo
mkdir -p $DIR && cp mintoo_server.py mintoo_push.py verify_purchase.py requirements.txt $DIR/
pip3 install --break-system-packages -r requirements.txt 2>/dev/null || pip3 install -r requirements.txt
if [ ! -f $DIR/fcm_service_account.json ]; then
  echo "Now paste your Firebase service-account JSON file here as $DIR/fcm_service_account.json (see README_SERVER.md step 3), then run:  systemctl restart mintoo-alerts"
fi
chown -R mintoo:mintoo $DIR; chmod 600 $DIR/fcm_service_account.json 2>/dev/null || true
cp mintoo-alerts.service /etc/systemd/system/ && systemctl daemon-reload && systemctl enable --now mintoo-alerts
echo "Installed. Watch it:  journalctl -u mintoo-alerts -f     Health:  cat $DIR/health.json"
