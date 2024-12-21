import groovy.json.JsonSlurperClassic
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

pipeline {
    agent any
        stages {
            stage('Paso 0: Download Code and checkout') {
                steps {
                    script {
                        checkout(
                                [$class: 'GitSCM',
                                branches: [[name: 'main' ]],
                                userRemoteConfigs: [[url: 'https://github.com/GonzaloRojasR/agileSecurity.git']]])
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

            stage('Paso 2: Springboot') {
                steps {
                    script {
                        sh 'nohup bash ./mvnw spring-boot:run & >/dev/null'
                        sh "pwd"
                        sh "sleep 20" // Tiempo para asegurar que la aplicación esté arriba
                    }
                }
            }

            stage('Paso 3: test newman maven') {
                steps {
                    script {
                        sh 'newman run ./postman_collection.json'
                    }
                }
            }           

            stage('Paso 5: OWASP Dependency-Check') {
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

            stage('Iniciar OWASP ZAP') {
                steps {
                    script {
                        // Verifica si ZAP está corriendo antes de continuar
                        def zapStatus = sh(script: "curl -s http://localhost:9090", returnStatus: true)
                        if (zapStatus != 0) {
                            error "OWASP ZAP no está disponible en el puerto 9090"
                        }
                    }
                }
            }

            stage('Escaneo OWASP ZAP') {
                steps {
                    script {
                        sh '''
                            curl -X POST "http://localhost:9090/JSON/ascan/action/scan/" \
                            --data "url=http://localhost:8081/" \
                            --data "scanPolicyName=Default Policy"
                        '''
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

            stage('Final: Detener Spring Boot') {
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
                dependencyCheckPublisher pattern: '**/build/dependency-check-report/dependency-check-report.xml'
            }
        }
}
