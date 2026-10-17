pipeline {
    agent any

    environment {
        DEPLOY_ROOT = '/opt/ucms-app'
    }

    stages {
        stage('Build & Test') {
            steps {
                sh './mvnw -B verify'
            }
        }

        stage('Package') {
            steps {
                sh './mvnw -B package -DskipTests'
            }
        }

        stage('Frontend Build') {
            steps {
                // No Node toolchain on the Jenkins host itself — build inside a
                // throwaway node container instead (Docker is available; see
                // docs/architecture). Output lands in web/dist/web/browser, which the
                // Deploy stage below copies out, same as the backend jars.
                sh '''
                    set -e
                    docker run --rm -v "$WORKSPACE/web:/app" -w /app node:20-alpine \
                        sh -c "npm ci && npm run build -- --configuration production"
                '''
            }
        }

        stage('Deploy') {
            // Jenkins runs on the same Contabo host this deploys to, so this stage
            // is plain local shell — no SSH hop needed. The infra stack (Postgres,
            // Redis, RabbitMQ, MinIO) and /opt/ucms-app/infra/.env (secrets) already
            // exist on this host and are not managed by this pipeline.
            when {
                anyOf { branch 'develop'; branch 'release'; branch 'main' }
            }
            steps {
                sh '''
                    set -e
                    for svc in identity-access-service reference-data-service academic-structure-service gateway; do
                        mkdir -p "$DEPLOY_ROOT/services/$svc/target"
                        cp "services/$svc/Dockerfile" "$DEPLOY_ROOT/services/$svc/"
                        cp services/$svc/target/$svc-*.jar "$DEPLOY_ROOT/services/$svc/target/"
                    done

                    mkdir -p "$DEPLOY_ROOT/web/dist/web"
                    cp -r web/dist/web/browser "$DEPLOY_ROOT/web/dist/web/"
                    cp web/Dockerfile web/nginx.conf "$DEPLOY_ROOT/web/"

                    mkdir -p "$DEPLOY_ROOT/infra"
                    cp infra/docker-compose.deploy.yml "$DEPLOY_ROOT/infra/"

                    cd "$DEPLOY_ROOT/infra"
                    docker compose --env-file .env -f docker-compose.deploy.yml up -d --build
                '''
            }
        }
    }

    post {
        always {
            junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
        }
    }
}
