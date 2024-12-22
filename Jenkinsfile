import groovy.json.JsonSlurperClassic
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

pipeline {
    agent any
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

        stage('Paso 1: Build ') {
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
                    sh "sleep 20" // Aseguramos tiempo para que la aplicación arranque
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

        stage('Paso 6: Esperar a que Spider termine') {
            steps {
                script {
                    def status = ""
                    def maxAttempts = 30  // Número máximo de intentos
                    def attempt = 0
        
                    while (status != "100" && attempt < maxAttempts) {
                        echo "Esperando a que Spider alcance 100% (Intento: ${attempt + 1})"
                        status = sh(
                            script: 'curl -X POST "http://localhost:9090/JSON/spider/action/scan/" --data "url=http://localhost:8081" --data "maxChildren=10" | jq -r .status',
                            returnStdout: true
                        ).trim()
                        if (status != "100") {
                            sleep(5)  // Espera 5 segundos antes de volver a intentar
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

        stage('Paso Final: Detener Spring Boot') {
            steps {
                script {
                    sh '''
                        echo 'Deteniendo Spring Boot: ' $(pidof java | awk '{print $1}')
                        kill -9 $(pidof java | awk '{print $1}')
                    '''
                }
            }
        }
    }
    post {
        always {
            // Publica los reportes de OWASP Dependency-Check
            dependencyCheckPublisher pattern: '**/build/dependency-check-report/dependency-check-report.xml'
        }
    }
}
