def call (Map configMap){
    pipeline {
        agent any
        stages {
            stage('Build') {
                steps {
                    script{
                        sh """
                            echo 'Building..'
                            printenv
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