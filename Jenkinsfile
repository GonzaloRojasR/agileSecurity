import groovy.json.JsonSlurperClassic

def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

pipeline {
    agent any
    environment {
        JIRA_API_TOKEN = credentials('jira-api-token') // Token configurado en Jenkins
        JIRA_API_EMAIL = credentials('jira-api-email') // Email configurado en Jenkins
        SONARQUBE_SERVER = 'SonarQubeServerName' // Nombre del servidor SonarQube configurado en Jenkins
    }
    stages {
        stage('Paso 0: Descargar Código y Checkout') {
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

        stage('Paso 1: Build ') {
            steps {
                script {
                    sh 'chmod +x mvnw'
                    sh './mvnw clean package -e'
                }
            }
        }

        stage('Paso 2: Análisis SonarQube') {
            steps {
                withSonarQubeEnv('SonarQubeServerName') { // Configuración del servidor
                    script {
                        sh '''
                            ./mvnw sonar:sonar \
                                -Dsonar.projectKey=agileSecurity \
                                -Dsonar.host.url=$SONAR_HOST_URL \
                                -Dsonar.login=$SONAR_AUTH_TOKEN
                        '''
                    }
                }
            }
        }

        stage('Paso 3: Esperar Quality Gate de SonarQube') {
            steps {
                timeout(time: 5, unit: 'MINUTES') { // Configuración de tiempo máximo para Quality Gate
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Paso 4: Iniciar Spring Boot') {
            steps {
                script {
                    sh 'nohup bash ./mvnw spring-boot:run & >/dev/null'
                    sh "sleep 20" // Aseguramos tiempo para que la aplicación arranque
                }
            }
        }

        stage('Paso 5: Test API con Newman') {
            steps {
                script {
                    sh 'newman run ./postman_collection.json'
                }
            }
        }

        stage('Paso 6: OWASP Dependency-Check') {
            steps {
                script {
                    sh '''
                        mvn org.owasp:dependency-check-maven:check \
                        -Ddependency-check-output-directory=build \
                        -Ddependency-check-report-format=ALL
                    '''
                }
            }
        }

        stage('Paso 7: Iniciar OWASP ZAP') {
            steps {
                script {
                    def zapStatus = sh(script: "curl -s http://localhost:9090", returnStatus: true)
                    if (zapStatus != 0) {
                        error "OWASP ZAP no está disponible en el puerto 9090"
                    }
                }
            }
        }

        stage('Paso 8: Exploración con Spider en OWASP ZAP') {
            steps {
                script {
                    sh '''
                        curl -X POST "http://localhost:9090/JSON/spider/action/scan/" \
                        --data "url=http://localhost:8081/rest/mscovid/estadoPais" \
                        --data "maxChildren=10"
                        sleep 10
                    '''
                }
            }
        }

        stage('Paso 9: Generar Reporte OWASP ZAP') {
            steps {
                script {
                    sh '''
                        curl -X GET "http://localhost:9090/OTHER/core/other/htmlreport/" -o zap-report.html
                    '''
                }
            }
        }

        stage('Paso 10: Publicar Reporte OWASP ZAP') {
            steps {
                script {
                    sh 'rm -f nohup.out' // Limpia nohup.out si existe
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
        
        stage('Comentar en Jira') {
            steps {
                script {
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

        stage('Paso Final: Detener Spring Boot') {
            steps {
                script {
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
    post {
        always {
            dependencyCheckPublisher pattern: '**/build/dependency-check-report/dependency-check-report.xml'
        }
    }
}
