import groovy.json.JsonSlurperClassic

def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

pipeline {
    agent any
    environment {
        JIRA_API_TOKEN = credentials('jira-api-token') // Token configurado en Jenkins
        JIRA_API_EMAIL = credentials('jira-api-email') // Email configurado en Jenkins        
        SONAR_HOST_URL = 'http://localhost:9000'
        SONAR_PROJECT_KEY = 'agileSecurity'
        SONAR_PROJECT_NAME = 'agileSecurity'
        SONAR_TOKEN = credentials('sonar-token') // Configura el token en Jenkins Credentials
    }
    stages {
        stage('Descargar Código y Checkout') {
            steps {
                script {
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: 'main']],
                        userRemoteConfigs: [[url: 'https://github.com/GonzaloRojasR/agileSecurity.git']]
                    ])
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

        stage('Análisis SonarQube') {
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

        stage('Test API con Newman') {
            steps {
                script {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                        sh 'newman run ./postman_collection.json'
                    }
                }
            }
        }

        stage('OWASP Dependency-Check') {
            steps {
                script {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                        sh '''
                            mvn org.owasp:dependency-check-maven:check \
                            -Ddependency-check-output-directory=build \
                            -Ddependency-check-report-format=ALL
                        '''
                        def report = readFile('build/dependency-check-report/dependency-check-report.xml')
                        if (report.contains('warning')) {
                            currentBuild.result = 'UNSTABLE'
                            echo "Warnings found in OWASP Dependency-Check"
                        }
                    }
                }
            }
        }

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

        stage('Exploración con Spider en OWASP ZAP') {
            steps {
                script {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                        sh '''
                            curl -X POST "http://localhost:9090/JSON/spider/action/scan/" \
                            --data "url=http://localhost:8081/rest/mscovid/estadoPais" \
                            --data "maxChildren=10"
                            sleep 10
                        '''
                    }
                }
            }
        }

        stage('Generar Reporte OWASP ZAP') {
            steps {
                script {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                        sh '''
                            curl -X GET "http://localhost:9090/OTHER/core/other/htmlreport/" -o zap-report.html
                        '''
                        def report = readFile('zap-report.html')
                        if (report.contains('WARNING')) {
                            currentBuild.result = 'UNSTABLE'
                            echo "Warnings found in OWASP ZAP report"
                        }
                    }
                }
            }
        }

        stage('Publicar Reporte OWASP ZAP') {
            steps {
                script {
                    sh 'rm -f nohup.out'
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

        stage('Obtener Tag de Jira') {
            steps {
                script {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                        sh "git fetch --tags"
                        def jiraTag = sh(
                            script: '''git tag | grep -oE 'SCRUM-[0-9]+' || echo "NoTag"''',
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
        }

        stage('Comentar en Jira') {
            steps {
                script {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                        def jiraUrl = 'https://agile-security-test.atlassian.net'
                        def comment = "Despliegue asociado a la historia ${env.JIRA_TAG}"
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
        }

        stage('Paso Final: Detener Spring Boot') {
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
            dependencyCheckPublisher pattern: '**/build/dependency-check-report/dependency-check-report.xml'
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
