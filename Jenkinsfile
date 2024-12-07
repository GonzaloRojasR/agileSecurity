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
                                //Acá reemplazar por el nonbre de branch
                                branches: [[name: 'main' ]],
                                //Acá reemplazar por su propio repositorio
                                userRemoteConfigs: [[url: 'https://github.com/GonzaloRojasR/agileSecurity.git']]])
                }
            }
            }
            stage('Paso 1: Build test') {
                steps {
                    script {
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
            stage('Paso 5: Levantar Artefacto Jar en server Jenkins') {
                steps {
                    script {
                        sh 'nohup java -jar build/DevOpsUsach2020-0.0.1.jar & >/dev/null'
                        sh "sleep 20"
                    }
                }
            }
            stage('Paso 6: Testear Artefacto con newman') {
               steps {
                script {
                    sh 'newman run ./postman_collection.json'
                }
                }
            }
            stage('Paso 7:Detener Atefacto jar en Jenkins server') {
                steps {
                    sh '''
                        echo 'Process Java .jar: ' $(pidof java | awk '{print $1}')
                        sleep 20
                        kill -9 $(pidof java | awk '{print $1}')
                    '''
                }
            }
        }
}