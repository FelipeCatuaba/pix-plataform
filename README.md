# Plataforma PIX Assíncrona

Plataforma distribuída que recebe solicitações PIX por uma API REST e processa a comunicação com o parceiro de forma assíncrona.

## Visão geral

```text
Cliente
  -> pix-api
  -> PostgreSQL + Outbox
  -> Kafka
  -> pix-processor
  -> parceiro PIX
```

- `pix-api`: recebe solicitações, garante idempotência e disponibiliza a consulta de status.
- `pix-processor`: consome eventos, chama o parceiro, executa retentativas e atualiza o resultado.
- PostgreSQL: fonte da verdade para transações, eventos e tentativas.
- Kafka: comunicação assíncrona entre os serviços.
- Redis: cache de idempotência e status.
- WireMock: simulação do parceiro PIX.
- Datadog/OpenTelemetry: logs, métricas e traces.

## Estrutura

```text
pix-plataform/
|-- services/
|   |-- pix-api/
|   `-- pix-processor/
|-- infra/
|   |-- flyway/sql/
|   `-- wiremock/mappings/
|-- tests/k6/
|-- docs/
|-- docker-compose.yml
`-- pom.xml
```

## Pré-requisitos

- Docker Desktop com containers Linux.
- Acesso à internet no primeiro build.
- API key do Datadog, caso queira enviar telemetria ao Datadog SaaS.

## Como executar

Na raiz do projeto:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Para usar o Datadog, configure no `.env`:

```env
DD_API_KEY=sua_api_key
DD_SITE=datadoghq.com
DD_ENV=local
DD_VERSION=1.0.0
```

Para recriar todo o ambiente:

```powershell
docker compose down -v
docker compose up --build
```

> `docker compose down -v` remove os dados locais do PostgreSQL, Kafka e Redis.

Para subir somente as dependências:

```powershell
docker compose up -d postgres flyway redis kafka kafka-init kafka-ui wiremock datadog-agent
```

## Acessos locais

| Serviço | Endereço |
|---|---|
| API PIX | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Kafka UI | http://localhost:8085 |
| WireMock | http://localhost:8089 |
| Actuator | http://localhost:8080/actuator |

## Verificação rápida

```powershell
curl.exe http://localhost:8080/actuator/health

curl.exe -X POST http://localhost:8080/pix `
  -H "Content-Type: application/json" `
  -H "X-Correlation-Id: teste-sucesso-001" `
  -d '{\"transactionId\":\"tx-sucesso-001\",\"amount\":150.75,\"pixKey\":\"cliente@email.com\",\"description\":\"Cenario de sucesso\"}'

curl.exe http://localhost:8080/pix/tx-sucesso-001
```

O `POST /pix` retorna `202 Accepted` com status inicial `PROCESSING`. A consulta deve chegar ao estado `COMPLETED`.

## Build e testes

```powershell
.\mvnw.cmd clean test
docker compose config --quiet
```

## Documentação

- [Decisões arquiteturais e trade-offs](docs/architecture-decisions.md)
- [Cenários de teste e carga](docs/testing.md)
- [Observabilidade e operação](docs/observability.md)
- [Cobertura de testes](docs/coverage-tracker-full.md)

