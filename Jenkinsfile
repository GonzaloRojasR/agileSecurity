pipeline {
    agent any
    environment {
        JIRA_API_TOKEN = credentials('jira-api-token') // Token configurado en Jenkins
        JIRA_API_EMAIL = credentials('jira-api-email') // Email configurado en Jenkins        
        SONAR_HOST_URL = 'http://localhost:9000'
        SONAR_PROJECT_KEY = 'agileSecurity'
        SONAR_PROJECT_NAME = 'agileSecurity'
        SONAR_TOKEN = credentials('sonar-token')
    }
    stages {
        // Ejemplo para demostración commit, merge y pipeline
        stage('Debug Branch Name') {
            steps {
                echo "Branch name detected: ${env.BRANCH_NAME}"
            }
        }
        stage('Descargar Código y Checkout') {
            steps {
                script {
                    checkout scm // Multibranch Pipeline automáticamente usa el SCM configurado
                }
            }
        }

        stage('Build') {
            steps {
                script {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                        sh 'chmod +x mvnw'
                        sh './mvnw clean package -e'
                    }
                }
            }
        }

        stage('Iniciar Spring Boot') {
            steps {
                script {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                        sh 'nohup bash ./mvnw spring-boot:run & >/dev/null'
                        sh "sleep 20"
                    }
                }
            }
        }

        stage('Análisis SonarQube') {
            when {
                expression { env.BRANCH_NAME != 'main' && env.BRANCH_NAME != 'test' } // Solo dev
            }
            steps {
                script {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                        withSonarQubeEnv('SonarQube') {
                            sh """
                            ./mvnw clean compile sonar:sonar \
                              -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                              -Dsonar.projectName=${SONAR_PROJECT_NAME} \
                              -Dsonar.host.url=${SONAR_HOST_URL} \
                              -Dsonar.login=${env.SONAR_TOKEN}
                            """
                        }
                    }
                }
            }
        }

        stage('OWASP Dependency-Check') {
            when {
                expression { env.BRANCH_NAME != 'main' && env.BRANCH_NAME != 'test' } // Solo dev
            }
            steps {
                script {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                        sh '''
                            mvn org.owasp:dependency-check-maven:check \
                            -Ddependency-check-output-directory=build \
                            -Ddependency-check-report-format=ALL
                        '''
                    }
                }
            }
        }

        stage('Test API con Newman') {
            when {
                expression { env.BRANCH_NAME == 'test' } // Solo test
            }
            steps {
                script {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                        sh 'newman run ./postman_collection.json'
                    }
                }
            }
        }

        stage('OWASP ZAP') {
            when {
                expression { env.BRANCH_NAME == 'test' } // Solo test
            }
            stages {
                stage('Iniciar OWASP ZAP') {
                    steps {
                        script {
                            catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                                def zapStatus = sh(script: "curl -s http://localhost:9090", returnStatus: true)
                                if (zapStatus != 0) {
                                    currentBuild.result = 'UNSTABLE'
                                    echo "OWASP ZAP no está disponible en el puerto 9090"
                                }
                            }
                        }
                    }
                }

                stage('Exploración con Spider') {
                    steps {
                        script {
                            // Realiza el escaneo Spider
                            sh '''
                                    curl -X POST "http://localhost:9090/JSON/spider/action/scan/" \
                                    --data "url=http://localhost:8081/rest/mscovid/estadoPais" \
                                    --data "maxChildren=10"
                                    sleep 2
                                    curl -X POST "http://localhost:9090/JSON/spider/action/scan/" \
                                    --data "url=http://localhost:8081/rest/mscovid/test" \
                                    --data "maxChildren=10"
                                '''        
                            // Espera el estado 100 antes de continuar
                            def scanComplete = false
                            while (!scanComplete) {
                                def status = sh(
                                    script: "curl -s http://localhost:9090/JSON/spider/view/status/ | jq -r '.status'",
                                    returnStdout: true
                                ).trim()
                                echo "Estado del escaneo Spider: ${status}%"
                                if (status == '100') {
                                    scanComplete = true
                                } else {
                                    sleep 5 // Espera 5 segundos antes de volver a consultar
                                }
                            }
                        }
                    }
                }

                stage('Publicar Reporte OWASP ZAP') {
                    steps {
                        script {                            
                                sh '''
                                    curl -X GET "http://localhost:9090/OTHER/core/other/htmlreport/" -o zap-report.html
                                '''                            
                        }
                        publishHTML(target: [
                            allowMissing: false,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: '.',
                            reportFiles: 'zap-report.html',
                            reportName: 'OWASP ZAP Report'
                        ])
                    }
                }
            }
        }

        stage('Obtener Tag de Jira') {
            steps {
                script {
                    sh "git fetch --tags"
                    def jiraTag = sh(
                        script: '''git tag --sort=-creatordate | grep -oE '^SCRUM-[0-9]+$' | head -n 1 || echo "NoTag"""''',
                        returnStdout: true
                    ).trim()
                    if (jiraTag == "NoTag") {
                        error "No se encontró ninguna etiqueta con el formato SCRUM-# en el repositorio"
                    } else {
                        echo "Etiqueta de Jira detectada: ${jiraTag}"
                        env.JIRA_TAG = jiraTag
                    }
                }
            }
        }
        
        stage('Comentar en Jira') {
            steps {
                script {
                    def jiraUrl = 'https://agile-security-test.atlassian.net'
                    def comment = "[MENSAJE AUTOMATICO] Despliegue realizado en ambiente [${env.BRANCH_NAME}]"
                    sh """
                        curl -X POST -u $JIRA_API_EMAIL:$JIRA_API_TOKEN "${jiraUrl}/rest/api/3/issue/${env.JIRA_TAG}/comment" \
                            -H "Content-Type: application/json" \
                            -d '{
                                  "body": {
                                    "type": "doc",
                                    "version": 1,
                                    "content": [
                                      {
                                        "type": "paragraph",
                                        "content": [
                                          {
                                            "text": "${comment}",
                                            "type": "text"
                                          }
                                        ]
                                      }
                                    ]
                                  }
                                }'
                    """
                }
            }
        }

        stage('Detener Spring Boot') {
            when {
                expression { env.BRANCH_NAME != 'main' } // Dev y Test
            }
            steps {
                script {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                        sh '''
                            PID=$(pidof java | awk '{print $1}')
                            if [ -n "$PID" ]; then
                                echo "Deteniendo Spring Boot: $PID"
                                sudo kill -9 $PID
                            else
                                echo "No se encontró ningún proceso de Java en ejecución"
                            fi
                        '''
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                if (env.BRANCH_NAME == 'dev') {
                    dependencyCheckPublisher pattern: '**/build/dependency-check-report/dependency-check-report.xml'
                } else {
                    echo "Skipping Dependency Check Publisher for branch: ${env.BRANCH_NAME}"
                }
            }
        }
        unstable {
            echo 'Pipeline completed with warnings'
        }
        success {
            echo 'Pipeline completed successfully'
        }
        failure {
            echo 'Pipeline failed'
        }
    }
}
