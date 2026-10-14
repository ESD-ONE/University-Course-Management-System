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
                    mkdir -p "$DEPLOY_ROOT/services/identity-access-service/target"
                    mkdir -p "$DEPLOY_ROOT/services/gateway/target"
                    mkdir -p "$DEPLOY_ROOT/infra"

                    cp services/identity-access-service/Dockerfile "$DEPLOY_ROOT/services/identity-access-service/"
                    cp services/identity-access-service/target/identity-access-service-*.jar "$DEPLOY_ROOT/services/identity-access-service/target/"
                    cp services/gateway/Dockerfile "$DEPLOY_ROOT/services/gateway/"
                    cp services/gateway/target/gateway-*.jar "$DEPLOY_ROOT/services/gateway/target/"
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
