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

        stage('Paso 1: Obtener Historia Jira desde Etiqueta') {
            steps {
                script {
                    // Extraer la etiqueta que contiene el número de Jira
                    def jiraIssue = sh(
                        script: '''
                            git tag --contains $(git log -1 --pretty=format:"%H") | grep -oE 'SCRUM-[0-9]+'
                        ''',
                        returnStdout: true
                    ).trim()

                    if (jiraIssue) {
                        echo "Número de la historia Jira encontrado: ${jiraIssue}"
                        env.JIRA_ISSUE = jiraIssue
                    } else {
                        error "No se encontró un número de historia Jira en las etiquetas"
                    }
                }
            }
        }

        stage('Paso 2: Build ') {
            steps {
                script {
                    sh 'chmod +x mvnw'
                    sh './mvnw clean package -e'
                }
            }
        }

        stage('Paso 3: Iniciar Spring Boot') {
            steps {
                script {
                    sh 'nohup bash ./mvnw spring-boot:run & >/dev/null'
                    sh "sleep 20" // Aseguramos tiempo para que la aplicación arranque
                }
            }
        }

        stage('Paso 4: Test API con Newman') {
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

        stage('Paso 6: Comentar en Jira') {
            steps {
                script {
                    def jiraUrl = 'https://agile-security-test.atlassian.net'
                    def comment = "Despliegue relacionado con la historia ${env.JIRA_ISSUE} completado en producción."

                    // Enviar comentario a Jira
                    sh """
                    curl -X POST -u $JIRA_API_EMAIL:$JIRA_API_TOKEN \
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
                        }' \
                        "${jiraUrl}/rest/api/3/issue/${env.JIRA_ISSUE}/comment"
                    """
                    echo "Comentario agregado a la historia Jira: ${env.JIRA_ISSUE}"
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
                        else {
                            echo "No se encontró ningún proceso de Java en ejecución"
                        }
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
