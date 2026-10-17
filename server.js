const express = require('express');
const cors = require('cors');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Metadata API
app.get('/api/metadata', (req, res) => {
  res.json({
    appName: "Quantum Sovereign Suite",
    version: "1.0.0",
    developer: "Awais Rajpoot",
    email: "awaisrajpoot2@yahoo.com",
    address: "Broadway Street, Greens Avenue, Park Road, Islamabad, Pak",
    copyright: "© 2026 Awais Rajpoot. All Rights Reserved."
  });
});

app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`Quantum Sovereign Suite Server running on port ${PORT}`);
});
