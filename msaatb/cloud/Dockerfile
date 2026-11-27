# MSAATB engine on a cloud server (Render, DigitalOcean, AWS, any VPS with Docker)
FROM python:3.12-slim
WORKDIR /app
RUN pip install --no-cache-dir ccxt==4.*
COPY msaatb_engine.py .
# paper money by default; see README_cloud.md for live mode
ENV MSAATB_ARGS="--exchange binance --risk 0.5 --lev 3"
CMD ["sh", "-c", "python -u msaatb_engine.py $MSAATB_ARGS --state /data/msaatb_state.json"]
