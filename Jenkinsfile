if (env.BRANCH_NAME == 'master') {
    properties([
        disableConcurrentBuilds(),
        pipelineTriggers([
            triggers: [
                [
                    $class: 'jenkins.triggers.ReverseBuildTrigger',
                    upstreamProjects: "Docker-payara5-bump-trigger", threshold: hudson.model.Result.SUCCESS
                ]
    	    ]
        ]),
    ])
}
pipeline {
    agent { label "devel11" }
    tools {
        maven "Maven 3"
    }
    triggers {
        pollSCM("H/3 * * * *")
    }
    options {
        buildDiscarder(logRotator(artifactDaysToKeepStr: "", artifactNumToKeepStr: "", daysToKeepStr: "30", numToKeepStr: "30"))
        timestamps()
    }
    stages {
        stage("clear workspace") {
            steps {
                deleteDir()
                checkout scm
            }
        }
        stage("build") {
            steps {
                script {
                    def status = sh returnStatus: true, script:  """
                        rm -rf \$WORKSPACE/.repo
                        mvn -B -Dmaven.repo.local=\$WORKSPACE/.repo dependency:resolve dependency:resolve-plugins >/dev/null || true
                        mvn -B -Dmaven.repo.local=\$WORKSPACE/.repo clean
                    """

                    // We want code-coverage and pmd/spotbugs even if unittests fails
                    status += sh returnStatus: true, script:  """
                        ./scripts/build
                    """

                    junit testResults: '**/target/*-reports/*.xml'

                    def java = scanForIssues tool: [$class: 'Java']
                    def javadoc = scanForIssues tool: [$class: 'JavaDoc']
                    publishIssues issues:[java, javadoc], unstableTotalAll:1

                    def pmd = scanForIssues tool: [$class: 'Pmd']
                    publishIssues issues:[pmd]

                    def spotbugs = scanForIssues tool: [$class: 'SpotBugs']
                    publishIssues issues:[spotbugs]

                    if (status != 0) {
                        error("build failed")
                    } else {
                        def dockerImageName = readFile(file: 'service/docker.out')
                        docker.image(dockerImageName).push()
                    }
                }
            }
        }
    }
    post {
        failure {
            script {
                if ("${env.BRANCH_NAME}".equals('master')) {
                    emailext(
                            recipientProviders: [developers(), culprits()],
                            to: "de-team@dbc.dk",
                            subject: "[Jenkins] ${env.JOB_NAME} #${env.BUILD_NUMBER} failed",
                            mimeType: 'text/html; charset=UTF-8',
                            body: "<p>The master build failed. Log attached. </p><p><a href=\"${env.BUILD_URL}\">Build information</a>.</p>",
                            attachLog: true,
                    )
                    slackSend(channel: 'search',
                            color: 'warning',
                            message: "${env.JOB_NAME} #${env.BUILD_NUMBER} failed and needs attention: ${env.BUILD_URL}",
                            tokenCredentialId: 'slack-global-integration-token')

                } else {
                    // this is some other branch, only send to developer
                    emailext(
                            recipientProviders: [developers()],
                            subject: "[Jenkins] ${env.BUILD_TAG} failed and needs your attention",
                            mimeType: 'text/html; charset=UTF-8',
                            body: "<p>${env.BUILD_TAG} failed and needs your attention. </p><p><a href=\"${env.BUILD_URL}\">Build information</a>.</p>",
                            attachLog: false,
                    )
                }
            }
        }
        success {
            step([$class: 'JavadocArchiver', javadocDir: 'target/site/apidocs', keepAll: false])
        }
    }
}
