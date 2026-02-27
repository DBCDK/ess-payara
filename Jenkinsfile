#!groovy

@Library('dependency-track')

def workerNode = 'devel12'
def teamEmail = 'de-team@dbc.dk'
def teamSlack = 'de-notifications'

pipeline {
    agent { label workerNode }

    triggers {
        upstream('/Docker-payara6-bump-trigger')
    }

    options {
        buildDiscarder(logRotator(artifactDaysToKeepStr: "", artifactNumToKeepStr: "", daysToKeepStr: "30", numToKeepStr: "30"))
        timestamps()
    }

    stages {
        stage("Ready the Workspace") {
            steps {
                deleteDir()
                checkout scm

                script {
                if (env.BRANCH_NAME == 'master') {
                    DOCKER_PUSH_TAG = "${env.BUILD_NUMBER}"
                    }
                }
            }
        }

        stage("Build & Push") {
            steps {
                withSonarQubeEnv(installationName: 'sonarqube.dbc.dk') {
                    script {
                        def status = sh returnStatus: true, script: """
                        rm -rf \$WORKSPACE/.repo
                        mvn -B -Dmaven.repo.local=\$WORKSPACE/.repo dependency:resolve dependency:resolve-plugins >/dev/null
                        mvn -B -Dmaven.repo.local=\$WORKSPACE/.repo clean
                    """
                        status += sh returnStatus: true, script: """
                        mvn -B -Dmaven.repo.local=\$WORKSPACE/.repo --no-transfer-progress verify
                    """
                        def sonarOptions = "-Dsonar.branch.name=$BRANCH_NAME"
                        if (env.BRANCH_NAME != 'master') {
                            sonarOptions += " -Dsonar.newCode.referenceBranch=master"
                        }
                        status += sh returnStatus: true, script: """
                        mvn -B -Dmaven.repo.local=$WORKSPACE/.repo --no-transfer-progress $sonarOptions sonar:sonar
                    """
                        junit testResults: '**/target/*-reports/*.xml'

                        if (status != 0) {
                            error("Build failure")
                        }
                    }
                }
            }
        }
        stage("quality gate") {
            steps {
                timeout(time: 1, unit: 'HOURS') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        stage("supply-chain gate") {
            steps {
                script {
                    dependencyTrackGate(
                        projectBom:  'target/sbom-java.json',
                        projectTeam: 'de-team',
                        projectType: 'java'
                    )
                }
            }
        }
        stage("docker push") {
            steps {
                script {
                    def dockerImageName = readFile(file: 'docker.out')
                    docker.image(dockerImageName).push()
                }
            }
        }

    }

    post {
        fixed {
            script {
                if (env.BRANCH_NAME == 'master') {
                    emailext(
                            recipientProviders: [developers(), culprits()],
                            to: teamEmail,
                            subject: "[Jenkins] ${env.JOB_NAME} #${env.BUILD_NUMBER} back to normal",
                            mimeType: 'text/html; charset=UTF-8',
                            body: "<p>The master is back to normal.</p><p><a href=\"${env.BUILD_URL}\">Build information</a>.</p>",
                            attachLog: false)
                    slackSend(channel: teamSlack,
                            color: 'good',
                            message: "${env.JOB_NAME} #${env.BUILD_NUMBER} back to normal: ${env.BUILD_URL}",
                            tokenCredentialId: 'slack-global-integration-token')
                }
            }
        }
        failure {
            script {
                if (env.BRANCH_NAME == 'master') {
                    emailext(
                            recipientProviders: [developers(), culprits()],
                            to: teamEmail,
                            subject: "[Jenkins] ${env.JOB_NAME} #${env.BUILD_NUMBER} failed",
                            mimeType: 'text/html; charset=UTF-8',
                            body: "<p>The master build failed. Log attached.</p><p><a href=\"${env.BUILD_URL}\">Build information</a>.</p>",
                            attachLog: true
                    )
                    slackSend(channel: teamSlack,
                            color: 'warning',
                            message: "${env.JOB_NAME} #${env.BUILD_NUMBER} failed and needs attention: ${env.BUILD_URL}",
                            tokenCredentialId: 'slack-global-integration-token')

                } else {
                    emailext(
                            recipientProviders: [developers()],
                            subject: "[Jenkins] ${env.BUILD_TAG} failed and needs your attention",
                            mimeType: 'text/html; charset=UTF-8',
                            body: "<p>${env.BUILD_TAG} failed and needs your attention. </p><p><a href=\"${env.BUILD_URL}\">Build information</a>.</p>",
                            attachLog: false
                    )
                }
            }
        }
    }
}
