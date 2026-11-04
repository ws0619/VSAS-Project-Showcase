
pipeline {
    agent any

    tools {
        jdk 'JDK21'
    }

    triggers {
        pollSCM('H/5 * * * *')
    }

    stages {
        stage('Java Version') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'java -version'
                    } else {
                        bat 'java -version'
                    }
                }
            }
        }

        stage('Build') {
            steps {
                script {
                    if (isUnix()) {
                        sh './gradlew clean assemble'
                    } else {
                        bat 'gradlew.bat clean assemble'
                    }
                }
            }
        }

        stage('Unit Tests and Coverage') {
            steps {
                script {
                    if (isUnix()) {
                        sh './gradlew test jacocoTestReport jacocoTestCoverageVerification'
                    } else {
                        bat 'gradlew.bat test jacocoTestReport jacocoTestCoverageVerification'
                    }
                    for (report in [
                        'app/build/reports/tests/test/index.html',
                        'app/build/reports/jacoco/test/html/index.html',
                        'app/build/reports/jacoco/test/jacocoTestReport.xml'
                    ]) {
                        if (!fileExists(report)) {
                            error("Missing required report: ${report}")
                        }
                    }
                }
            }
        }

        stage('Publish Coverage') {
            steps {
                recordCoverage(
                    tools: [[
                        parser: 'JACOCO',
                        pattern: 'app/build/reports/jacoco/test/jacocoTestReport.xml'
                    ]],
                    name: 'JaCoCo Coverage',
                    sourceDirectories: [[path: 'app/src/main/java']],
                    skipPublishingChecks: true
                )
            }
        }
    }

    post {
        always {
            junit(
                testResults: 'app/build/test-results/test/TEST-*.xml',
                allowEmptyResults: false
            )

            archiveArtifacts(
                artifacts: 'app/build/reports/tests/test/**,app/build/reports/jacoco/test/**',
                allowEmptyArchive: true
            )
        }
    }
}
