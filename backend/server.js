const cors = require("cors");
const crypto = require("crypto");
const express = require("express");
const fs = require("fs");
const multer = require("multer");
const path = require("path");

require("dotenv").config({ path: path.join(__dirname, ".env") });

const app = express();
const port = Number(process.env.PORT || 3000);
const analysisPollAttempts = Number(process.env.VIRUSTOTAL_POLL_ATTEMPTS || 24);
const analysisPollIntervalMs = Number(process.env.VIRUSTOTAL_POLL_INTERVAL_MS || 5000);
const dataDir = path.join(__dirname, "data");
const scanHistoryPath = path.join(dataDir, "scan-history.json");
const scanHistory = loadScanHistory();
let scanHistoryId = nextScanHistoryId(scanHistory);
const scanJobs = new Map();
let scanJobId = 1;
const scanJobTtlMs = 30 * 60 * 1000;
const upload = multer({
  storage: multer.memoryStorage(),
  limits: {
    fileSize: 32 * 1024 * 1024
  }
});

app.use(cors());
app.use(express.json({ limit: "128kb" }));

app.get("/health", (_req, res) => {
  res.json({
    status: "ok",
    virustotalConfigured: Boolean(process.env.VIRUSTOTAL_API_KEY),
    scanHistoryCount: scanHistory.length,
    scanHistoryPersistence: "backend/data/scan-history.json"
  });
});

app.post("/scan", async (req, res) => {
  const sha256 = String(req.body?.sha256 || "").toLowerCase();

  if (!/^[a-f0-9]{64}$/.test(sha256)) {
    return res.status(400).json({
      status: "unknown",
      malicious: 0,
      suspicious: 0,
      harmless: 0,
      undetected: 0,
      error: "sha256 invalido"
    });
  }

  try {
    const result = await scanVirusTotal(sha256);
    addScanHistory({
      scanType: "key_hash",
      sha256,
      result
    });
    res.json(result);
  } catch (error) {
    console.error("Falha ao consultar VirusTotal:", error.message);
    const result = emptyResult("unknown");
    addScanHistory({
      scanType: "key_hash",
      sha256,
      result
    });
    res.json(result);
  }
});

app.post("/scan-file", upload.single("file"), async (req, res) => {
  if (!req.file) {
    return res.status(400).json({
      ...emptyResult("unknown"),
      error: "arquivo ausente"
    });
  }

  try {
    const result = await uploadAndScanFile(req.file);
    addScanHistory({
      scanType: "file",
      fileName: req.file.originalname,
      mimeType: req.file.mimetype || "application/octet-stream",
      sizeBytes: req.file.size,
      sha256: result.sha256,
      result
    });
    res.json(result);
  } catch (error) {
    console.error("Falha ao enviar arquivo ao VirusTotal:", error.message);
    const result = emptyResult("unknown");
    addScanHistory({
      scanType: "file",
      fileName: req.file.originalname,
      mimeType: req.file.mimetype || "application/octet-stream",
      sizeBytes: req.file.size,
      result
    });
    res.status(error.statusCode || 502).json({
      ...result,
      error: error.message || "Falha ao enviar arquivo ao VirusTotal."
    });
  }
});

app.post("/scan-file-jobs", upload.single("file"), (req, res) => {
  if (!req.file) {
    return res.status(400).json({
      error: "arquivo ausente"
    });
  }

  const job = createScanJob(req.file);
  setImmediate(() => runScanJob(job, req.file));
  res.status(202).json(publicScanJob(job));
});

app.get("/scan-file-jobs/:jobId", (req, res) => {
  const job = scanJobs.get(req.params.jobId);
  if (!job) {
    return res.status(404).json({
      error: "scan nao encontrado"
    });
  }

  res.json(publicScanJob(job));
});

app.get("/scan-history", (req, res) => {
  const includeKeyHash = req.query.includeKeyHash === "true";
  const limit = Number(req.query.limit || 0);
  const history = includeKeyHash
    ? scanHistory
    : scanHistory.filter((item) => item.scanType === "file");

  res.json(limit > 0 ? history.slice(0, limit) : history);
});

app.delete("/scan-history", (req, res) => {
  const includeKeyHash = req.query.includeKeyHash === "true";
  const previousCount = scanHistory.length;

  if (includeKeyHash) {
    scanHistory.splice(0);
    scanHistoryId = 1;
  } else {
    const fileOnly = scanHistory.filter((item) => item.scanType === "file").length;
    const keyHashHistory = scanHistory.filter((item) => item.scanType !== "file");
    scanHistory.splice(0, scanHistory.length, ...keyHashHistory);
    scanHistoryId = nextScanHistoryId(scanHistory);
    saveScanHistory();
    return res.json({
      status: "ok",
      removed: fileOnly,
      remaining: scanHistory.length
    });
  }

  saveScanHistory();
  res.json({
    status: "ok",
    removed: previousCount,
    remaining: scanHistory.length
  });
});

async function scanVirusTotal(sha256) {
  const apiKey = process.env.VIRUSTOTAL_API_KEY;
  if (!apiKey) {
    return emptyResult("unknown");
  }

  const response = await fetch(`https://www.virustotal.com/api/v3/files/${sha256}`, {
    headers: {
      "x-apikey": apiKey
    }
  });

  if (response.status === 404) {
    return emptyResult("unknown");
  }

  if (!response.ok) {
    return emptyResult("unknown");
  }

  const json = await response.json();
  const stats = json?.data?.attributes?.last_analysis_stats;
  if (!stats || Object.keys(stats).length === 0) {
    return emptyResult("unknown");
  }

  const result = resultFromStats(stats);
  return hasAnalysisStats(result) ? result : emptyResult("unknown");
}

async function uploadAndScanFile(file, progress = () => {}) {
  const apiKey = requireVirusTotalApiKey();
  const sha256 = crypto.createHash("sha256").update(file.buffer).digest("hex");

  console.log(
    `[VirusTotal] Scan manual recebido: ${file.originalname || "arquivo"} ` +
      `(${file.size} bytes, sha256=${shortHash(sha256)})`
  );

  progress({
    status: "hash_lookup",
    statusText: "Consultando hash",
    queuePosition: null
  });

  console.log(`[VirusTotal] Consultando relatorio por hash ${shortHash(sha256)} antes do upload.`);
  const existingResult = await scanVirusTotal(sha256);
  if (existingResult.status !== "unknown" || hasAnalysisStats(existingResult)) {
    const result = {
      ...existingResult,
      sha256
    };
    console.log(`[VirusTotal] Relatorio existente encontrado para ${shortHash(sha256)}. Upload ignorado.`);
    progress({
      status: "completed",
      statusText: "Analise concluida",
      queuePosition: null,
      result
    });
    return result;
  }

  console.log(`[VirusTotal] Relatorio por hash indisponivel para ${shortHash(sha256)}. Enviando arquivo.`);

  const form = new FormData();
  const blob = new Blob([file.buffer], {
    type: file.mimetype || "application/octet-stream"
  });
  form.append("file", blob, file.originalname || "arquivo");

  const uploadResponse = await fetch("https://www.virustotal.com/api/v3/files", {
    method: "POST",
    headers: {
      "x-apikey": apiKey
    },
    body: form
  });

  console.log(`[VirusTotal] Upload enviado. HTTP ${uploadResponse.status}`);

  if (uploadResponse.status === 409) {
    console.log(
      `[VirusTotal] Arquivo ja esta em submissao. ` +
        `Aguardando relatorio por hash ${shortHash(sha256)}.`
    );
    progress({
      status: "in_progress",
      statusText: "Analise em andamento",
      queuePosition: 1
    });
    return waitForHashReport(sha256, progress);
  }

  if (!uploadResponse.ok) {
    throw await virusTotalRequestError(uploadResponse, "upload do arquivo");
  }

  progress({
    status: "uploaded",
    statusText: "Arquivo enviado",
    queuePosition: null
  });

  const uploadJson = await uploadResponse.json();
  const analysisId = uploadJson?.data?.id;
  if (!analysisId) {
    throw backendError(502, "VirusTotal nao retornou o id da analise.");
  }

  console.log(`[VirusTotal] Analise criada: ${analysisId}`);

  for (let attempt = 1; attempt <= analysisPollAttempts; attempt += 1) {
    await sleep(analysisPollIntervalMs);
    const analysisResponse = await fetch(`https://www.virustotal.com/api/v3/analyses/${analysisId}`, {
      headers: {
        "x-apikey": apiKey
      }
    });

    if (!analysisResponse.ok) {
      throw await virusTotalRequestError(analysisResponse, "consulta da analise");
    }

    const analysisJson = await analysisResponse.json();
    const attributes = analysisJson?.data?.attributes || {};
    if (attributes.status === "completed") {
      console.log(`[VirusTotal] Analise concluida: ${analysisId}`);
      const result = {
        ...resultFromStats(attributes.stats || {}),
        sha256
      };
      progress({
        status: "completed",
        statusText: "Analise concluida",
        queuePosition: null,
        result
      });
      return result;
    }

    progress({
      status: "in_progress",
      statusText: "Analise em andamento",
      queuePosition: queuePositionFromAttributes(attributes, remainingQueuePosition(attempt))
    });

    if (attempt === 1 || attempt % 5 === 0) {
      console.log(
        `[VirusTotal] Analise ainda em andamento: ${analysisId} ` +
          `(${attempt}/${analysisPollAttempts}, status=${attributes.status || "unknown"})`
      );
    }
  }

  console.warn(
    `[VirusTotal] Analise nao concluiu dentro do tempo. ` +
      `Tentando buscar relatorio por hash ${shortHash(sha256)}.`
  );

  const hashResult = await scanVirusTotal(sha256);
  if (hashResult.status !== "unknown" || hasAnalysisStats(hashResult)) {
    return {
      ...hashResult,
      sha256
    };
  }

  console.warn(`[VirusTotal] Sem resultado consolidado para ${shortHash(sha256)}.`);
  return {
    ...emptyResult("unknown"),
    sha256
  };
}

async function waitForHashReport(sha256, progress = () => {}) {
  for (let attempt = 1; attempt <= analysisPollAttempts; attempt += 1) {
    await sleep(analysisPollIntervalMs);
    const result = await scanVirusTotal(sha256);

    if (result.status !== "unknown" || hasAnalysisStats(result)) {
      console.log(`[VirusTotal] Relatorio por hash encontrado: ${shortHash(sha256)}`);
      const completedResult = {
        ...result,
        sha256
      };
      progress({
        status: "completed",
        statusText: "Analise concluida",
        queuePosition: null,
        result: completedResult
      });
      return completedResult;
    }

    progress({
      status: "in_progress",
      statusText: "Analise em andamento",
      queuePosition: remainingQueuePosition(attempt)
    });

    if (attempt === 1 || attempt % 5 === 0) {
      console.log(
        `[VirusTotal] Relatorio por hash ainda indisponivel: ${shortHash(sha256)} ` +
          `(${attempt}/${analysisPollAttempts})`
      );
    }
  }

  console.warn(`[VirusTotal] Relatorio por hash nao ficou pronto: ${shortHash(sha256)}.`);
  return {
    ...emptyResult("unknown"),
    sha256
  };
}

function createScanJob(file) {
  cleanupScanJobs();
  const now = new Date().toISOString();
  const job = {
    id: String(scanJobId),
    status: "uploaded",
    statusText: "Arquivo enviado",
    queuePosition: null,
    fileName: file.originalname || "arquivo",
    mimeType: file.mimetype || "application/octet-stream",
    sizeBytes: file.size,
    result: null,
    error: null,
    createdAt: now,
    updatedAt: now
  };
  scanJobId += 1;
  scanJobs.set(job.id, job);
  return job;
}

async function runScanJob(job, file) {
  try {
    const result = await uploadAndScanFile(file, (progress) => updateScanJob(job, progress));
    addScanHistory({
      scanType: "file",
      fileName: job.fileName,
      mimeType: job.mimeType,
      sizeBytes: job.sizeBytes,
      sha256: result.sha256,
      result
    });
    updateScanJob(job, {
      status: "completed",
      statusText: "Analise concluida",
      queuePosition: null,
      result
    });
  } catch (error) {
    console.error("Falha ao enviar arquivo ao VirusTotal:", error.message);
    const result = emptyResult("unknown");
    addScanHistory({
      scanType: "file",
      fileName: job.fileName,
      mimeType: job.mimeType,
      sizeBytes: job.sizeBytes,
      result
    });
    updateScanJob(job, {
      status: "failed",
      statusText: "Falha na analise",
      queuePosition: null,
      result,
      error: error.message || "Falha ao enviar arquivo ao VirusTotal."
    });
  }
}

function updateScanJob(job, update) {
  Object.assign(job, update, {
    updatedAt: new Date().toISOString()
  });
}

function publicScanJob(job) {
  return {
    id: job.id,
    status: job.status,
    statusText: job.statusText,
    queuePosition: job.queuePosition,
    fileName: job.fileName,
    mimeType: job.mimeType,
    sizeBytes: job.sizeBytes,
    result: job.result,
    error: job.error,
    createdAt: job.createdAt,
    updatedAt: job.updatedAt
  };
}

function cleanupScanJobs() {
  const now = Date.now();
  for (const [id, job] of scanJobs.entries()) {
    if (now - Date.parse(job.updatedAt) > scanJobTtlMs) {
      scanJobs.delete(id);
    }
  }
}

function requireVirusTotalApiKey() {
  const apiKey = process.env.VIRUSTOTAL_API_KEY;
  if (!apiKey) {
    throw backendError(503, "VIRUSTOTAL_API_KEY ausente no backend.");
  }
  return apiKey;
}

function backendError(statusCode, message) {
  const error = new Error(message);
  error.statusCode = statusCode;
  return error;
}

async function virusTotalRequestError(response, action) {
  const body = await response.text();
  let detail = "";
  try {
    const json = JSON.parse(body);
    detail = json?.error?.message || json?.message || "";
  } catch (_error) {
    detail = body.slice(0, 160);
  }

  const suffix = detail ? `: ${detail}` : "";
  return backendError(
    502,
    `VirusTotal recusou a ${action} (HTTP ${response.status})${suffix}`
  );
}

function resultFromStats(stats) {
  const malicious = Number(stats.malicious || 0);
  const suspicious = Number(stats.suspicious || 0);
  const harmless = Number(stats.harmless || 0);
  const undetected = Number(stats.undetected || 0);

  let status = "clean";
  if (malicious > 0) {
    status = "malicious";
  } else if (suspicious > 0) {
    status = "suspicious";
  }

  return { status, malicious, suspicious, harmless, undetected };
}

function hasAnalysisStats(result) {
  return result.malicious + result.suspicious + result.harmless + result.undetected > 0;
}

function queuePositionFromAttributes(attributes, fallback) {
  const position = Number(
    attributes.queue_position ||
      attributes.queuePosition ||
      attributes.position ||
      fallback
  );
  return Number.isFinite(position) && position > 0 ? position : fallback;
}

function remainingQueuePosition(attempt) {
  return Math.max(analysisPollAttempts - attempt + 1, 1);
}

function emptyResult(status) {
  return {
    status,
    malicious: 0,
    suspicious: 0,
    harmless: 0,
    undetected: 0
  };
}

function addScanHistory({ scanType, fileName, mimeType, sizeBytes, sha256, result }) {
  scanHistory.unshift({
    id: scanHistoryId,
    scanType,
    fileName,
    mimeType,
    sizeBytes,
    sha256,
    status: result.status,
    malicious: result.malicious,
    suspicious: result.suspicious,
    harmless: result.harmless,
    undetected: result.undetected,
    checkedAt: new Date().toISOString()
  });
  scanHistoryId += 1;
  scanHistory.splice(100);
  saveScanHistory();
}

function loadScanHistory() {
  try {
    if (!fs.existsSync(scanHistoryPath)) {
      return [];
    }

    const parsed = JSON.parse(fs.readFileSync(scanHistoryPath, "utf8"));
    if (!Array.isArray(parsed)) {
      console.warn("[ScanHistory] Arquivo de historico invalido. Iniciando vazio.");
      return [];
    }

    return parsed.slice(0, 100);
  } catch (error) {
    console.warn("[ScanHistory] Nao foi possivel carregar historico:", error.message);
    return [];
  }
}

function saveScanHistory() {
  try {
    fs.mkdirSync(dataDir, { recursive: true });
    const tempPath = `${scanHistoryPath}.tmp`;
    fs.writeFileSync(tempPath, JSON.stringify(scanHistory, null, 2), "utf8");
    fs.renameSync(tempPath, scanHistoryPath);
  } catch (error) {
    console.warn("[ScanHistory] Nao foi possivel salvar historico:", error.message);
  }
}

function nextScanHistoryId(history) {
  const maxId = history.reduce((currentMax, item) => {
    const id = Number(item?.id || 0);
    return Number.isFinite(id) && id > currentMax ? id : currentMax;
  }, 0);
  return maxId + 1;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function shortHash(sha256) {
  return `${sha256.slice(0, 12)}...`;
}

app.listen(port, "0.0.0.0", () => {
  console.log(`Super Encrypter backend rodando em http://localhost:${port}`);
});
