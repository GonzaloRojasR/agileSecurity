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
                        sh "sleep 20"
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

            stage('Paso 4: Detener Spring Boot') {
                steps {
                    script {
                        sh '''
                            echo 'Process Spring Boot Java: ' $(pidof java | awk '{print $1}')
                            sleep 20
                            kill -9 $(pidof java | awk '{print $1}')
                        '''
                    }
                }
            }

            stage('Paso 5: OWASP Dependency-Check') {
                steps {
                    script {
                        // Ejecuta Dependency-Check
                        sh '''
                            ./mvnw clean package
                            ./mvnw org.owasp:dependency-check-maven:check \
                            -Ddependency-check-output-directory=dependency-check-report \                                                        
                            -Ddependency-check-report-format=XML
                        '''                        
                    }
                }
            }
            
        }
        post {
            always {
                // Archivar los reportes para visualización
                dependencyCheckPublisher pattern: '**/build/dependency-check-report.html'
            }
        }
}