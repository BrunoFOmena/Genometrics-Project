# 🧬 GENOMETRICS — Plataforma de QC e Métricas NGS

**Plataforma local-first para análise de qualidade e variantes em arquivos FASTQ/VCF.**

Desenvolvido por **Bruno Omena** · monólito modular pensado para rodar em um **laptop Windows de 16 GB RAM**.

![Status](https://img.shields.io/badge/Status-Ativo-success)
![Angular](https://img.shields.io/badge/Angular-19-DD0031?logo=angular&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Database](https://img.shields.io/badge/Database-H2%20%7C%20PostgreSQL-336791?logo=postgresql&logoColor=white)

---

## 📖 Sobre o Projeto

O **GENOMETRICS** é uma aplicação **Full-Stack** focada em **Next-Generation Sequencing (NGS)**. Diferente de pipelines pesados que exigem clusters ou cloud, este projeto entrega um **dashboard analítico completo** rodando localmente — com parsers Java em streaming, jobs assíncronos in-process e armazenamento de arquivos no disco.

O objetivo é oferecer **visibilidade imediata sobre qualidade de sequenciamento (FASTQ)** e **métricas de variantes (VCF)**, com upload, análise automática, gráficos interativos e exportação de relatórios — tudo acessível via SPA moderna.

---

## ✨ Funcionalidades

### 📁 Gestão de Projetos e Amostras
- Criação de **projetos** e **amostras** organizados por workspace.
- Upload de arquivos **FASTQ** (single e paired-end) e **VCF** (até 2 GB).
- Lista unificada de arquivos e análises com status em tempo real (`QUEUED` → `RUNNING` → `DONE`).

### 🔬 QC FASTQ
- Métricas de qualidade: **Phred**, **GC content**, **duplicação**, **N-content**.
- Detecção de **adaptadores** e sequências **over-represented** (com link BLAST).
- Suporte a **paired-end** com detecção automática de mates (`R1`/`R2`).
- Avaliação **PASS / WARN / FAIL** com recomendações acionáveis.
- Heatmaps 2D, gráficos por posição e abas **Charts · Detailed · Overrepresented**.

### 🧪 Métricas VCF
- Contagem de variantes por tipo, qualidade e distribuição cromossômica.
- Visualizações ECharts integradas ao painel da amostra.

### 📊 Dashboard & Comparativo
- **Overview** com KPIs animados (GSAP): projetos, amostras, análises em fila e concluídas.
- **Compare** side-by-side entre amostras/análises.
- **History** com jobs recentes e navegação rápida.

### 📄 Relatórios & Export
- Download de métricas em **JSON**, **CSV** e **PDF** (FASTQ QC report).
- Swagger UI para exploração da API REST.

### 🔐 Autenticação *(opcional em dev)*
- Login/cadastro com JWT (Spring Security).
- **Auth desabilitado por padrão em dev** — basta abrir a UI e começar.

---

## 🛠️ Tecnologias Utilizadas

### Frontend
| Tecnologia | Uso |
|------------|-----|
| **Angular 19** | SPA standalone, routing, animações |
| **PrimeNG** | Cards, tabs, tabelas, chips, progress |
| **Apache ECharts** | Gráficos de QC e variantes |
| **GSAP** | Contadores animados no Overview |
| **Lucide Angular** | Ícones |
| **Tailwind CSS** | Utilitários de layout |
| **TypeScript** | Tipagem estática |
| **Cypress** | Testes E2E |

### Backend
| Tecnologia | Uso |
|------------|-----|
| **Java 21** | Runtime |
| **Spring Boot 3.3** | API REST, segurança, JPA |
| **FastqParser / VcfParser** | Parsers streaming in-process |
| **SpringDoc OpenAPI** | Documentação Swagger |
| **JWT (JJWT)** | Autenticação |
| **H2** | Banco em dev/testes |
| **PostgreSQL** | Banco em prod/staging |
| **Testcontainers** | Smoke tests opcionais com Postgres |

### DevOps
| Tecnologia | Uso |
|------------|-----|
| **GitHub Actions** | CI (`backend`, `frontend`, `e2e`) |
| **GHCR** | Imagem Docker da API no merge em `main` |
| **Docker Compose** | Postgres local (opcional) |

---

## 🚀 Como Rodar o Projeto

### Pré-requisitos

- **JDK 21**
- **Maven 3.9+**
- **Node.js 22+** e npm
- *(Opcional)* Docker — apenas para Postgres em prod/staging local

### 1. Clone o repositório

```bash
git clone https://github.com/BrunoFOmena/Genometrics-Project.git
cd Genometrics-Project
```

### 2. Inicie a API (H2 — padrão)

```bash
cd backend
mvn spring-boot:run
```

Sem Docker ou Postgres. O profile `dev-h2` é o default.

| Serviço | URL |
|---------|-----|
| API | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |

### 3. Inicie a UI

```bash
cd frontend
npm install
npm start
```

| Serviço | URL |
|---------|-----|
| Dashboard | http://localhost:4200 |

### 4. Teste com fixtures

1. Abra a UI (sem login em dev).
2. Crie um **projeto** + **amostra**.
3. Faça upload de [`datasets/sample.fastq`](datasets/sample.fastq) e [`datasets/sample.vcf`](datasets/sample.vcf) — ou paired-end: [`sample_R1.fastq`](datasets/sample_R1.fastq) + [`sample_R2.fastq`](datasets/sample_R2.fastq).
4. Aguarde o status `DONE` e explore os gráficos e relatórios.

---

## 🐘 PostgreSQL (prod / staging — opcional)

```bash
cd docker
docker compose up -d
cd ../backend
mvn spring-boot:run "-Dspring-boot.run.profiles=prod"
```

Variáveis para cloud/staging (`prod`):

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

| Serviço | Usuário | Senha |
|---------|---------|-------|
| PostgreSQL (Compose) | `ngs` | `ngs` |

---

## 🧪 Testes

```bash
# Backend (H2 in-memory)
cd backend && mvn test

# Frontend (headless)
cd frontend && npm test -- --watch=false --browsers=ChromeHeadless

# E2E (API + UI rodando)
cd frontend && npm run e2e
```

Smoke Postgres com Docker: `RUN_TESTCONTAINERS=true mvn -Dtest=PostgresContainerIT test`

---

## 🔀 Git & CI (GitHub Flow)

**`main`** é a única branch de integração — sempre deployável. Todo trabalho entra via **Pull Request**.

```bash
git checkout main && git pull
git checkout -b feature/<nome>
# ... commits ...
git push -u origin HEAD
# Abrir PR → main · aguardar CI · merge · deletar branch
```

| Evento | Testes | Imagem GHCR |
|--------|--------|-------------|
| PR → `main` | ✅ | ❌ |
| Push em `feature/*`, `fix/*`, `hotfix/*` | ✅ | ❌ |
| Push em `main` (após merge) | ✅ | ✅ |

Proteja **`main`**: exigir PR, checks `backend` / `frontend` / `e2e`, bloquear force-push.

Agentes de IA: contexto em [`AGENTS.md`](AGENTS.md) e [`.cursor/rules/`](.cursor/rules/).

---

## 💻 Notas de Hardware

| Recurso | Orientação |
|---------|------------|
| **RAM** | Heap da API ~1 GB; Postgres Compose ≤ 512 MB |
| **Disco** | Use fixtures em `datasets/`; limpe `data/uploads` periodicamente |
| **GPU** | Não utilizada |

---

<p align="center">
  <sub>GENOMETRICS · Bruno Omena · Local-first NGS analytics</sub>
</p>
