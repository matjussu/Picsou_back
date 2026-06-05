# Picsou — Back (API)

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Deployed on Render](https://img.shields.io/badge/Deployed%20on-Render-46E3B7?logo=render&logoColor=white)

API REST du gestionnaire de budget personnel **Picsou** : authentification, agrégation de comptes, transactions, coloc en temps réel, insights IA et OCR de tickets.

> Projet final M1 MIAGE — Université Paris Dauphine-PSL, 2026.

## 🔗 Accès

- **API en ligne** : https://picsou-back.onrender.com
- **Documentation Swagger** : https://picsou-back.onrender.com/swagger-ui.html (OpenAPI : `/v3/api-docs`)
  > ⚠️ Hébergé sur Render (plan gratuit) : le serveur se met en veille après inactivité, le **premier** appel peut prendre **~1–3 min** (cold-start).
- **Front** : [Picsou-front](https://github.com/matjussu/Picsou-front) · démo en ligne : https://picsou-front.vercel.app

## ✨ Fonctionnalités (domaines)

- **Auth** — inscription / connexion, JWT access + refresh.
- **Comptes & transactions** — multi-comptes, catégorisation.
- **Dashboard** — solde, projection de fin de mois, anomalies, répartition par catégorie.
- **Coloc** — groupes, dépenses partagées, soldes & settle-up, notifications **temps réel (WebSocket / STOMP)**.
- **Objectifs** d'épargne.
- **Insights IA** — résumé mensuel et Q&A libre (Claude / Anthropic).
- **OCR** — extraction de tickets.
- **Open banking (mock)** — agrégation simulée.
- **Prédiction** de fin de mois.

## 🛠 Stack

- **Spring Boot 3.5** · **Java 21**
- **Spring Security + JWT** (`jjwt` 0.12)
- **PostgreSQL** (Supabase) + **Flyway** (migrations versionnées)
- **springdoc-openapi** (Swagger UI)
- **IA** : API Anthropic (Claude)
- Build **Maven** · déploiement **Render**

## 🚀 Lancer en local

Prérequis : **Java 21** et une base **PostgreSQL**.

```bash
./mvnw spring-boot:run
```

Configurer les variables d'environnement (aucun secret n'est versionné) :

| Variable | Rôle |
|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Connexion PostgreSQL (Supabase) |
| `APP_JWT_SECRET` | Secret de signature JWT (HS512, ≥ 512 bits) |
| `APP_JWT_ACCESS_EXPIRATION_MS` / `_REFRESH_EXPIRATION_MS` | Durées de vie des tokens |
| `APP_CORS_ORIGINS` | Origines autorisées (front) |
| `ANTHROPIC_API_KEY` / `APP_AI_BASE_URL` / `APP_AI_MODEL` | Configuration IA |
| `PORT` | Port d'écoute |

Flyway applique automatiquement les migrations au démarrage. La documentation interactive est exposée sur `/swagger-ui.html`.

---

*Binôme repo : [Picsou-front](https://github.com/matjussu/Picsou-front) — client Angular 19.*
