#!groovy
def workerNode = 'devel11'
def teamEmail = 'de-team@dbc.dk'
def teamSlack = 'de-notifications'
def DOCKER_PUSH_TAG = "${env.BRANCH_NAME}-${env.BUILD_NUMBER}"

pipeline {
    agent { label workerNode }

    environment {
        GITLAB_PRIVATE_TOKEN = credentials("metascrum-gitlab-api-token")
    }

    triggers {
        pollSCM("H/3 * * * *")
        // This project uses the docker.dbc.dk/payara5-micro container
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
                script {
                    def status = sh returnStatus: true, script: """
                        rm -rf \$WORKSPACE/.repo
                        mvn -B -Dmaven.repo.local=\$WORKSPACE/.repo dependency:resolve dependency:resolve-plugins >/dev/null
                        mvn -B -Dmaven.repo.local=\$WORKSPACE/.repo clean
                    """

                    // We want code-coverage and pmd/spotbugs even if unittests fails
                    status += sh returnStatus: true, script: """
                        mvn -B -Dmaven.repo.local=\$WORKSPACE/.repo verify pmd:pmd pmd:cpd spotbugs:spotbugs javadoc:aggregate
                    """

                    junit testResults: '**/target/*-reports/*.xml'

                    def java = scanForIssues tool: [$class: 'Java']
                    def javadoc = scanForIssues tool: [$class: 'JavaDoc']
                    publishIssues issues: [java, javadoc], unstableTotalAll: 1

                    def pmd = scanForIssues tool: [$class: 'Pmd']
                    publishIssues issues: [pmd], unstableTotalAll: 1

                    def spotbugs = scanForIssues tool: [$class: 'SpotBugs']
                    publishIssues issues: [spotbugs], unstableTotalAll: 1

                    if (status != 0) {
                        error("Build failure")
                    } else {
                        docker.image("docker-de.artifacts.dbccloud.dk/ess-payara-service-1.0:${DOCKER_PUSH_TAG}").push()
                        if ("${env.BRANCH_NAME}" == 'master') {
                            docker.image("docker-de.artifacts.dbccloud.dk/ess-payara-service-1.0:${DOCKER_PUSH_TAG}").push('latest')
                        }
                    }
                }
            }
        }
    }

    post {
        fixed {
            script {
                if ("${env.BRANCH_NAME}" == 'master') {
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
                if ("${env.BRANCH_NAME}" == 'master') {
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

        success {
            step([$class: 'JavadocArchiver', javadocDir: 'target/site/apidocs', keepAll: false])
        }
    }
}
