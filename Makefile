.DEFAULT_GOAL := help

MVN ?= mvn

# JDBC settings used by the migrate-* targets. Override on the CLI:
#   make migrate-up DB_JDBC_URL=jdbc:postgresql://host:5432/db DB_USER=user DB_PASSWORD=pass
DB_JDBC_URL ?= jdbc:postgresql://localhost:5432/marketplace
DB_USER ?= marketplace
DB_PASSWORD ?= marketplace

# The running app and database the contract suite talks to.
CONTRACT_BASE_URL ?= http://localhost:8080
CONTRACT_DATABASE_URL ?= jdbc:postgresql://localhost:5432/marketplace

.PHONY: help build run test test-unit cover migrate-up migrate-info docker-up docker-down contract

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

build: ## Build the application jar (target/marketplace.jar)
	$(MVN) -B package -DskipTests

run: ## Run the server locally (reads DATABASE_URL and PORT)
	$(MVN) spring-boot:run

test: ## Run all tests, unit and Testcontainers (needs Docker)
	$(MVN) -B verify

test-unit: ## Run only tests that don't need Docker
	$(MVN) -B test

cover: ## Run all tests and print total line coverage (needs Docker)
	$(MVN) -B verify
	@awk -F, 'NR > 1 { missed += $$8; covered += $$9 } \
		END { printf "total line coverage: %.1f%%\n", 100 * covered / (missed + covered) }' \
		target/site/jacoco/jacoco.csv

migrate-up: ## Apply pending migrations to DB_JDBC_URL
	@$(MVN) -B flyway:migrate -Dflyway.url=$(DB_JDBC_URL) -Dflyway.user=$(DB_USER) -Dflyway.password=$(DB_PASSWORD)

migrate-info: ## Show applied and pending migrations for DB_JDBC_URL
	@$(MVN) -B flyway:info -Dflyway.url=$(DB_JDBC_URL) -Dflyway.user=$(DB_USER) -Dflyway.password=$(DB_PASSWORD)

docker-up: ## Start Postgres + app via docker compose
	docker compose up --build

docker-down: ## Stop and remove docker compose resources
	docker compose down -v

contract: ## Run the black-box contract suite against CONTRACT_BASE_URL
	cd contract-tests && CONTRACT_BASE_URL=$(CONTRACT_BASE_URL) \
		CONTRACT_DATABASE_URL=$(CONTRACT_DATABASE_URL) $(MVN) -B test
