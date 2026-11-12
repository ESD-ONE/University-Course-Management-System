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
        // 'report': findings mark the build UNSTABLE; 'fail': they fail it. Same
        // report-first rollout as SECURITY_GATE above, since a brand-new SonarQube
        // project starts with an unknown baseline of existing findings.
        SONAR_GATE = 'report'
        // ucms-sonarqube is a standalone container on this same host (not on the app's
        // ucms_data Docker network — it's managed out of band, alongside Postgres/Redis/
        // RabbitMQ/MinIO, from /opt/ucms, not this repo's infra/docker-compose.infra.yml).
        // Jenkins itself runs natively on the host (not in a container), so it reaches
        // SonarQube's published port over loopback, same as any other host process would.
        // Override per-environment if SonarQube ever moves off this host.
        SONAR_HOST_URL = 'http://127.0.0.1:9010'
    }

    stages {
        stage('Build & Test') {
            steps {
                sh './mvnw -B verify'
            }
        }

        stage('Code Quality (SonarQube)') {
            // SonarQube Community Edition — free/open-source static analysis, not a
            // paid SaaS. Backend: sonar-maven-plugin over the whole reactor, picking up
            // each module's target/site/jacoco/jacoco.xml (root pom.xml's jacoco-maven-
            // plugin, bound to every module via inheritance) for coverage. Frontend:
            // sonar-scanner against web/ (web/sonar-project.properties), same
            // no-native-toolchain-on-Jenkins pattern as the Security Scan stage below.
            // Needs a SonarQube user token in the 'sonarqube-token' Jenkins credential.
            // A missing/misconfigured credential is an infra gap, not a quality finding —
            // it always just skips this stage (UNSTABLE) and lets Package/Deploy continue,
            // regardless of SONAR_GATE. A build that failed outright here once already
            // (no credential existed) blocked a real release deploy for no good reason;
            // SONAR_GATE itself still governs the *scan's own* findings once it can run.
            steps {
                script {
                    try {
                        // Credential resolution happens here, before the closure body runs:
                        // a missing/misconfigured 'sonarqube-token' throws
                        // CredentialNotFoundException right at this call, so the catch below
                        // only ever sees THAT failure mode — never a real scan/exit-code
                        // failure from inside the closure, which propagates normally and is
                        // still governed by SONAR_GATE below.
                        withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_TOKEN')]) {
                            def scan = '''
                                status=0
                                ./mvnw -B sonar:sonar -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN" \
                                    -Dsonar.projectKey=ucms-backend -Dsonar.projectName="UCMS Backend" || status=1
                                # --network host, not ucms_data: SonarQube isn't on that network
                                # (see SONAR_HOST_URL above) — sharing the host's network
                                # namespace is what lets this container reach 127.0.0.1:9010.
                                docker run --rm --network host -v "$WORKSPACE/web:/usr/src" -w /usr/src \
                                    sonarsource/sonar-scanner-cli:11.2 \
                                    -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN" || status=1
                                exit $status
                            '''
                            if (env.SONAR_GATE == 'fail') {
                                sh scan
                            } else {
                                catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') { sh scan }
                            }
                        }
                    } catch (org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException err) {
                        echo "Code Quality (SonarQube) skipped: no 'sonarqube-token' Jenkins credential."
                        echo "Add it under Manage Jenkins > Credentials to enable this stage."
                        catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') { error('SonarQube not configured') }
                    }
                }
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
                // release is the only branch that goes to production. develop and main
                // both used to deploy here too — more than one branch independently
                // redeploying the same target doubled load on an already memory-tight
                // host for no benefit, and only a release build should actually go live.
                branch 'release'
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
