pipeline {
    agent any

    environment {
        DEPLOY_ROOT = '/opt/ucms-app'
        // The Jenkins snap's process environment doesn't export HOME, which the
        // Maven/npm wrapper scripts and tool caches (~/.m2, npm config) require.
        HOME = '/root'
        // 'report': vulnerability findings mark the build UNSTABLE (reports archived).
        // 'fail': they fail it. Switch to 'fail' once the dependency upgrades listed in
        // docs/remaining-issues.md have landed and the scans come back clean.
        SECURITY_GATE = 'report'
        TRIVY_IMAGE = 'aquasec/trivy:0.58.1'
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

        stage('Security Scan') {
            // Everything that ships: the backend jars (every transitive dependency) and
            // the frontend's production npm packages. HIGH/CRITICAL with a fix available.
            steps {
                script {
                    def scan = '''
                        status=0
                        docker run --rm -v "$WORKSPACE/services:/src:ro" -v ucms-trivy-cache:/root/.cache \
                            "$TRIVY_IMAGE" rootfs --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed \
                            --exit-code 1 /src > trivy-backend.txt 2>&1 || status=1
                        docker run --rm -v "$WORKSPACE/web:/app" -w /app node:20-alpine \
                            npm audit --omit=dev --audit-level=high > npm-audit.txt 2>&1 || status=1
                        exit $status
                    '''
                    if (env.SECURITY_GATE == 'fail') {
                        sh scan
                    } else {
                        catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') { sh scan }
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-backend.txt,npm-audit.txt', allowEmptyArchive: true
                }
            }
        }

        stage('Deploy') {
            // Jenkins runs on the same Contabo host this deploys to, so this stage
            // is plain local shell — no SSH hop needed. The infra stack (Postgres,
            // Redis, RabbitMQ, MinIO) and /opt/ucms-app/infra/.env (secrets) already
            // exist on this host and are not managed by this pipeline.
            when {
                // develop deliberately excluded: it shares this same production target
                // with release/main, and two branches redeploying it independently was
                // doubling load on an already memory-tight host for no benefit — only a
                // release/main merge should actually go live.
                anyOf { branch 'release'; branch 'main' }
            }
            steps {
                sh '''
                    set -e
                    for svc in identity-access-service base-service academic-structure-service \
                               enrollment-service assignment-grading-service notification-service \
                               analytics-reporting-service gateway; do
                        mkdir -p "$DEPLOY_ROOT/services/$svc/target"
                        cp "services/$svc/Dockerfile" "$DEPLOY_ROOT/services/$svc/"
                        cp services/$svc/target/$svc-*.jar "$DEPLOY_ROOT/services/$svc/target/"
                    done

                    mkdir -p "$DEPLOY_ROOT/web/dist/web"
                    rm -rf "$DEPLOY_ROOT/web/dist/web/browser" "$DEPLOY_ROOT/web/nginx"
                    cp -r web/dist/web/browser "$DEPLOY_ROOT/web/dist/web/"
                    cp -r web/Dockerfile web/nginx.conf web/nginx "$DEPLOY_ROOT/web/"

                    mkdir -p "$DEPLOY_ROOT/infra"
                    cp infra/docker-compose.deploy.yml infra/rolling-deploy.sh "$DEPLOY_ROOT/infra/"

                    cd "$DEPLOY_ROOT/infra"
                    docker compose --env-file .env -f docker-compose.deploy.yml build
                '''
                // The images about to go live: OS packages and JRE/nginx, CRITICAL only.
                script {
                    def imageScan = '''
                        cd "$DEPLOY_ROOT/infra"
                        status=0
                        for image in $(docker compose --env-file .env -f docker-compose.deploy.yml config --images | sort -u); do
                            docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v ucms-trivy-cache:/root/.cache \
                                "$TRIVY_IMAGE" image --scanners vuln --severity CRITICAL --ignore-unfixed \
                                --exit-code 1 "$image" >> "$WORKSPACE/trivy-images.txt" 2>&1 || status=1
                        done
                        exit $status
                    '''
                    if (env.SECURITY_GATE == 'fail') {
                        sh imageScan
                    } else {
                        catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') { sh imageScan }
                    }
                }
                archiveArtifacts artifacts: 'trivy-images.txt', allowEmptyArchive: true
                // Zero downtime: new replicas start and pass their health checks before
                // the old ones are retired (infra/rolling-deploy.sh).
                sh '"$DEPLOY_ROOT/infra/rolling-deploy.sh"'
            }
        }
    }

    post {
        always {
            junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
        }
    }
}
