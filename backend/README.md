# Backend local do Super Encrypter

Este backend existe para manter a chave do VirusTotal fora do app Android. O aplicativo envia o SHA-256 do arquivo-chave para `POST /scan`, e scans manuais de arquivos completos passam por `POST /scan-file`.

## Como rodar

```bash
npm install
cp .env.example .env
npm start
```

No arquivo `.env`, defina:

```env
VIRUSTOTAL_API_KEY=sua_chave_do_virustotal
PORT=3000
```

## Endpoints

- `GET /health`: retorna status do backend, se o VirusTotal esta configurado e a contagem do historico local.
- `POST /scan`: recebe `{ "sha256": "..." }` e devolve um status simplificado para o hash do arquivo-chave.
- `POST /scan-file`: recebe multipart com o campo `file`, envia o arquivo ao VirusTotal e devolve um status simplificado.
- `POST /scan-file-jobs`: recebe multipart com o campo `file`, cria um job de scan e devolve `jobId` com o status inicial.
- `GET /scan-file-jobs/:jobId`: retorna o status atual do scan manual, incluindo texto de progresso, posicao na fila quando disponivel e resultado final.
- `GET /scan-history`: retorna o historico persistido de scans de arquivos feitos em `POST /scan-file`.
  - Use `?limit=3` para trazer apenas os 3 mais recentes.
  - Use `?includeKeyHash=true` para incluir tambem consultas feitas em `POST /scan`.
- `DELETE /scan-history`: limpa o historico persistido de scans de arquivos. Use `?includeKeyHash=true` para limpar tambem scans de hash.

## Historico de scans

O backend salva automaticamente os ultimos 100 registros em:

```text
backend/data/scan-history.json
```

Esse arquivo e carregado quando o servidor inicia, entao o historico continua disponivel depois de reiniciar o backend. Ele fica no `.gitignore` porque contem dados locais de uso.

Para emulador Android, use `http://10.0.2.2:3000` no app. Para celular fisico, use o IP do notebook na mesma rede, por exemplo `http://192.168.0.20:3000`.
