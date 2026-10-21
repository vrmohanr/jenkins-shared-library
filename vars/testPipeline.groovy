def call (Map configMap){
    pipeline {
        agent any
            environment {
            project = configMap.get("project")
            component = configMap.get("component")
        }
        stages {
            stage('Build') {
                steps {
                    script{
                        sh """
                            echo 'Building..'
                            echo "project: ${project}, Component: ${project}"
                            printenv | sort
                        """
                    }
                    
                }
            }
            stage('Test') {
                steps {
                    echo 'Testing..'
                }
            }
            stage('Deploy') {
                steps {
                    echo 'Deploying....'
                }
            }
        }
    }
}