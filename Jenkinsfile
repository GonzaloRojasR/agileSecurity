import groovy.json.JsonSlurperClassic
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

pipeline {
    agent any
    environment {
        JIRA_API_TOKEN = credentials('jira-api-token') // Token configurado en Jenkins
        JIRA_API_EMAIL = credentials('jira-api-email') // Email configurado en Jenkins
    }
    stages {
        stage('Paso 0: Descargar Código y Checkout') {
            steps {
                script {
                    checkout(
                        [$class: 'GitSCM',
                        branches: [[name: 'main']],
                        userRemoteConfigs: [[url: 'https://github.com/GonzaloRojasR/agileSecurity.git']]]
                    )
                }
            }
        }

        stage('Paso 1: Build') {
            steps {
                script {
                    sh 'chmod +x mvnw'
                    sh './mvnw clean package -e'
                }
            }
        }

        stage('Paso 2: Iniciar Spring Boot') {
            steps {
                script {
                    sh 'nohup bash ./mvnw spring-boot:run & >/dev/null'
                    sh "sleep 20"
                }
            }
        }

        stage('Paso 3: Test API con Newman') {
            steps {
                script {
                    sh 'newman run ./postman_collection.json'
                }
            }
        }

        stage('Paso 4: OWASP Dependency-Check') {
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

        stage('Paso 5: Iniciar OWASP ZAP') {
            steps {
                script {
                    def zapStatus = sh(script: "curl -s http://localhost:9090", returnStatus: true)
                    if (zapStatus != 0) {
                        error "OWASP ZAP no está disponible en el puerto 9090"
                    }
                }
            }
        }

        stage('Paso 6.1: Exploración con Spider en OWASP ZAP') {
            steps {
                script {
                    sh '''
                        curl -X POST "http://localhost:9090/JSON/spider/action/scan/" \
                        --data "url=http://localhost:8081/rest/mscovid/estadoPais" \
                        --data "maxChildren=10"
                        sleep 10
                        curl -X POST "http://localhost:9090/JSON/spider/action/scan/" \
                        --data "url=http://localhost:8081/rest/mscovid/test" \
                        --data "maxChildren=10"
                        sleep 10
                    '''
                }
            }
        }

        stage('Paso 6.2: Esperar a que Spider termine') {
            steps {
                script {
                    def status = ""
                    def maxAttempts = 30
                    def attempt = 0

                    while (status != "100" && attempt < maxAttempts) {
                        echo "Esperando a que Spider alcance 100% (Intento: ${attempt + 1})"
                        
                        status = sh(
                            script: '''curl -s "http://localhost:9090/JSON/spider/view/status/" | sed -E 's/.*"status":"([0-9]+)".*/\\1/' ''',
                            returnStdout: true
                        ).trim()
        
                        echo "Estado actual del Spider: ${status}"
        
                        if (status != "100") {
                            sleep(5)
                        }
                        attempt++
                    }
        
                    if (status != "100") {
                        error "El Spider no alcanzó el 100% después de ${maxAttempts} intentos"
                    } else {
                        echo "Spider completado con éxito (100%)"
                    }
                }
            }
        }

        stage('Paso 7: Escaneo Activo con OWASP ZAP') {
            steps {
                script {
                    sh '''
                        curl -X POST "http://localhost:9090/JSON/ascan/action/scan/" \
                        --data "url=http://localhost:8081/rest/mscovid/test" \
                        --data "scanPolicyName=Default Policy"
                        sleep 30
                    '''
                }
            }
        }

        stage('Paso 8: Generar Reporte OWASP ZAP') {
            steps {
                script {
                    sh '''
                        curl -X GET "http://localhost:9090/OTHER/core/other/htmlreport/" -o zap-report.html
                    '''
                }
            }
        }

        stage('Paso 9: Publicar Reporte OWASP ZAP') {
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

        
        stage('Paso 10: Comentar en Jira') {
            steps {
                script {
                    def issueKey = 'SCRUM-5'
                    def jiraUrl = 'https://agile-security-test.atlassian.net'
        
                    def authorEmail = sh(
                        script: "git log -1 --pretty=format:'%ae'",
                        returnStdout: true
                    ).trim()
        
                    def comment = "Despliegue en producción completado por ${authorEmail}"
        
                    sh """
                        curl -X POST -u ${JIRA_API_EMAIL}:${JIRA_API_TOKEN} \
                            "${jiraUrl}/rest/api/3/issue/${issueKey}/comment" \
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
