def call (Map configMap){
pipeline {
    agent { 
        node { 
            label 'ROBOSHOP' 
        } 
    }

    environment {
         def appVersion = ""
         acc_id = "942701116753"
        project = configMap.get("project")
        component = configMap.get("component")
        org = "vrmohanr"
        ENVIRONMENT = "dev"
    }
    options {
        disableConcurrentBuilds()
        timeout(time: 15, unit: 'MINUTES')
    }
    /*
        parameters {
        string(name: 'PERSON', defaultValue: 'Mr Jenkins', description: 'Who should I say hello to?')
        text(name: 'BIOGRAPHY', defaultValue: '', description: 'Enter some information about the person')
        booleanParam(name: 'DEPLOY', defaultValue: true, description: 'Toggle this value')
        choice(name: 'CHOICE', choices: ['One', 'Two', 'Three'], description: 'Pick something')
        password(name: 'PASSWORD', defaultValue: 'SECRET', description: 'Enter a password')
    } */
   // Following is build section
        // Build
        stages {
            stage('read-version'){
                steps{
                    script {
                        def packageJson = readJSON file: 'package.json'
                        // Extract the version property
                        appVersion = packageJson.version
                        echo "The application version is: ${appVersion}"
                    }
                }
            }

        stage('Dev-Deploy') {
                steps {
                    script {
                        try {
                            withAWS(credentials: 'aws-creds', region: 'us-east-1') {
                                sh """
                                    aws eks update-kubeconfig --name roboshop-dev --region us-east-1

                                    helm upgrade --install ${component} ./helm \
                                        -f ./helm/values-dev.yaml \
                                        --namespace roboshop-dev \
                                        --create-namespace \
                                        --set deployment.imageVersion=${appVersion} \
                                        --wait --timeout 5m

                                    kubectl rollout status deployment/${component} -n roboshop-dev --timeout=120s
                                """
                            }
                            utils.updateCommitStatus('success', 'Deployed to roboshop-dev', 'dev-deploy')
                        }
                        catch (Exception e) {
                            utils.updateCommitStatus('failure', 'Deploy to roboshop-dev failed', 'dev-deploy')
                            throw e
                        }
                    }
                }
            }

            stage('api-tests') {
                steps {
                    script {
                        try {
                            build job: 'ROBOSHOP/catalogue-api-tests', parameters: [
                                string(name: 'NAMESPACE', value: 'roboshop-dev'),
                                string(name: 'COMMIT_ID', value: env.GIT_COMMIT)
                            ], wait: true, propagate: true
                            utils.updateCommitStatus('success', 'catalogue-api-tests passed', 'api-tests')
                        }
                        catch (Exception e) {
                            utils.updateCommitStatus('failure', 'catalogue-api-tests failed', 'api-tests')
                            throw e
                        }
                    }
                }
            }

            stage('promote-image') {
                when {
                    expression { env_ENVIRONMENT == 'dev' }
                }
                steps {
                    script {
                        try {
                            withAWS(credentials: 'aws-creds', region: 'us-east-1') {
                                sh """
                                    aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${acc_id}.dkr.ecr.us-east-1.amazonaws.com

                                    docker pull ${acc_id}.dkr.ecr.us-east-1.amazonaws.com/${project}/${component}:${appVersion}
                                    docker tag ${acc_id}.dkr.ecr.us-east-1.amazonaws.com/${project}/${component}:${appVersion} ${acc_id}.dkr.ecr.us-east-1.amazonaws.com/${project}/${component}:${env.GIT_COMMIT}
                                    docker push ${acc_id}.dkr.ecr.us-east-1.amazonaws.com/${project}/${component}:${env.GIT_COMMIT}
                                """
                            }
                            utils.updateCommitStatus('success', "Promoted image as ${env.GIT_COMMIT}", 'promote-image')
                        }
                        catch (Exception e) {
                            utils.updateCommitStatus('failure', 'Image promotion failed', 'promote-image')
                            throw e
                        }
                    }
                }
            }

/*

          input {
                message "Should we continue?"
                ok "Yes, we should."
                submitter "alice,bob"
                parameters {
                    string(name: 'PERSON', defaultValue: 'Mr Jenkins', description: 'Who should I say hello to?')
                }
            }  
            steps {
            script {
                    sh """
                echo 'Deploy.'
                 """
            }
            }
        }
          */
    } 
  
 //   Post build section
        post { 
        always { 
            echo 'I will always say Hello again!'
        }
            success {
                slackSend(
                    channel: '#jenkins-alert-ram',
                    color: 'good',
                    tokenCredentialId: 'slack-token',
                    message: "✅ *${component}* pipeline succeeded — build #${env.BUILD_NUMBER} (<${env.BUILD_URL}console|console>)"
                )
                echo "success"
            }
        failure { 
                    slackSend(
                    channel: '#jenkins-alert-ram',
                    color: 'danger',
                    tokenCredentialId: 'slack-token',
                    message: "❌ *${component}* pipeline failed — build #${env.BUILD_NUMBER} (<${env.BUILD_URL}console|console>)"
                ) 
                echo "failed"
        }
    }
}
}


